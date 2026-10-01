package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.AiAvailability
import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.ai.AiProviderId
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.AiResponse
import com.marcow.bible.core.network.ai.ChatEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenRouter answering as an [AiProvider], replacing `OpenRouterClient.generateStream` in
 * `legacy/flutter/lib/openrouter_service.dart:284` behind the provider-agnostic port of
 * `NATIVE_PLAN.md` §4.3.
 *
 * The class is thin on purpose. What to ask is [OpenRouterChatRequests]' business, what a frame means
 * is [openRouterChatEvents]' business, what a status means is [readOpenRouterExchange]'s, and the key
 * is [OpenRouterSession]'s — so what is left here is the part the Flutter build had no name for,
 * because there was only one provider: keeping a request open and handing its lines over as they
 * arrive. That is also the whole reason the chat is portable to Gemini Nano without touching the
 * chat: the events a reader of this port sees do not mention OpenRouter at all.
 *
 * Two things deliberately are *not* here:
 *  - The web-search retry. `_answerExistingMessage` in `legacy/flutter/lib/ai_service.dart` sent the
 *    same question again with the tool off when the first attempt failed, because the failure was the
 *    combination rather than the search. That is the chat's policy — it knows the question can be
 *    asked again, and `AiRequestOptions.ChatWithoutWebSearch` is the second attempt spelled out — and
 *    putting it in a transport would have Gemini Nano retry too, with nothing to turn off.
 *  - `research()`. A forced web search with a server-side budget and a citation list is an
 *    OpenRouter capability that `NATIVE_PLAN.md` §4 Phase 4 keeps off the port, so it arrives with its
 *    own use case rather than as a method only one of the two providers could implement honestly.
 */
@Singleton
class OpenRouterAiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val session: OpenRouterSession,
    private val modelId: OpenRouterModelId,
) : AiProvider {
    override val id = AiProviderId.OpenRouter

    /**
     * Whether a key is held, so the settings row can say so instead of letting the first question
     * fail.
     *
     * This is the one place the port asked for a `suspend` call that OpenRouter answers from memory:
     * the session already holds the answer as a [kotlinx.coroutines.flow.StateFlow], so there is
     * nothing to wait for. The `suspend` is on the port because Gemini Nano's answer is a device gate
     * that has to be asked for.
     */
    override suspend fun availability(): AiAvailability = if (session.signedIn.value) {
        AiAvailability.Available
    } else {
        AiAvailability.Unavailable(reason = SIGN_IN_REASON, signInRequired = true)
    }

    /**
     * The answer as it arrives.
     *
     * The request is built when the flow is *collected* rather than when it is assembled, which is
     * what keeps a paused or retried answer from sending a key that was read before the user signed
     * in: [flow]'s builder does not run until something listens.
     *
     * Stopping the answer cancels the collection, which cancels the coroutine inside the builder and
     * so closes the response through [use] — the connection is released instead of being held open by
     * a half-read body.
     */
    override fun stream(request: AiRequest, options: AiRequestOptions): Flow<ChatEvent> = flow {
        val bearer = openRouterBearer(session.apiKey()) ?: throw OpenRouterException.LoginRequired()
        val completion = OpenRouterChatRequests.build(request, modelId.modelId(), options, stream = true)
        val httpRequest = openRouterCompletionRequest(bearer, completion.body)

        val response = httpClient.newCall(httpRequest).execute()
        response.use {
            if (!it.isSuccessful) {
                // The same branch `_send` took. A streamed failure body is not a completion, so it is
                // read as text and only its status decides; the key is dropped on the two statuses
                // that mean it was the key, and never on a rate limit, which a retry can still answer.
                throw when (
                    val exchange = readOpenRouterExchange(it.code, it.body?.string().orEmpty())
                ) {
                    is OpenRouterExchange.KeyRejected -> {
                        session.signOut()
                        OpenRouterException.LoginRequired()
                    }

                    is OpenRouterExchange.Refused -> OpenRouterException.RequestFailed(exchange.message)

                    // Unreachable for a non-2xx: only a successful status reads as an answer.
                    is OpenRouterExchange.Answered, is OpenRouterExchange.Empty ->
                        OpenRouterException.EmptyResponse()
                }
            }
            // Read lazily, one line at a time: a buffered read of the whole body would hold the answer
            // until the model was done, which is the opposite of what a stream is for.
            val lines = it.body?.source()?.lineSequence()?.asFlow() ?: emptyFlow()
            openRouterChatEvents(lines).collect { event -> emit(event) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * [stream] joined, which is what `generate()` was: `generateStream(…).join()` in the Flutter
     * build.
     *
     * The text is *not* trimmed, where `generate()` trimmed for the search sheet. The chat appends
     * deltas as they arrive and never trimmed, and trimming a streamed answer here would change what
     * the reader sees for the provider that already works.
     */
    override suspend fun complete(request: AiRequest, options: AiRequestOptions): AiResponse {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var incomplete = false
        stream(request, options).collect { event ->
            when (event) {
                is ChatEvent.ContentDelta -> text.append(event.text)
                is ChatEvent.ReasoningDelta -> reasoning.append(event.text)
                is ChatEvent.Finished -> incomplete = event.reason.incomplete
                is ChatEvent.WebCitation, is ChatEvent.Failure -> Unit
            }
        }
        return AiResponse(
            text = text.toString(),
            reasoning = reasoning.toString(),
            incomplete = incomplete,
        )
    }
}

/**
 * Why the provider cannot answer, for the settings row that offers the button next to it.
 *
 * English, like every other user-facing string in this package: the localised copy is the settings
 * screen's, which is where `NATIVE_PLAN.md` §4.8 puts it.
 */
private const val SIGN_IN_REASON = "Sign in to OpenRouter to ask a question."
