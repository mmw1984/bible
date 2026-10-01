package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A forced web search and the evidence it came back with, replacing `research()` in
 * `legacy/flutter/lib/openrouter_service.dart:236` behind a port of its own.
 *
 * It is not on [com.marcow.bible.core.network.ai.AiProvider] because Gemini Nano cannot answer it
 * honestly: the port's `research()` is a server-side search with a budget and a citation list, and an
 * on-device model has neither. Leaving it here means the chat can keep depending on the
 * provider-agnostic [com.marcow.bible.core.network.ai.AiProvider] and a screen that genuinely needs
 * research can depend on this one, naming the capability rather than a transport.
 *
 * The options are not the caller's to state: the request half of `research()` fixed every one of them
 * (forced tool, `max_tool_calls 1`, one web use, six results, `low` context, 8192 tokens, no
 * reasoning), and a caller that could vary them would be able to ask for a request the Flutter build
 * never made. [AiRequestOptions.Research] is that fixed shape, already pinned by
 * `OpenRouterChatRequestsTest`.
 */
interface OpenRouterResearch {
    /**
     * [prompt] asked with a forced web search, and the evidence the model gathered.
     *
     * @throws OpenRouterException.LoginRequired when there is no key, or the provider rejected it.
     * @throws OpenRouterException.RequestFailed for any other non-2xx answer.
     * @throws WebResearchException when the answer was not a searched one — see the three subclasses.
     */
    suspend fun research(prompt: String): WebResearchResponse
}

/**
 * The HTTP client behind [OpenRouterResearch], which is `_send` with `stream: false` and a different
 * reader of the answer.
 *
 * Everything except the socket is already elsewhere and is used rather than repeated: the body is
 * [OpenRouterChatRequests]'s, the request is [openRouterCompletionRequest]'s, the status handling is
 * [readOpenRouterExchange]'s — including dropping a key the provider has revoked, which is the one
 * piece of transport policy that would be easy to miss here and would leave a dead key in the store —
 * and the answer is [readWebResearch]'s.
 *
 * The one thing this adds is that the content emptiness check does *not* apply. A research answer can
 * legitimately be all evidence and no prose, and `readOpenRouterExchange` would call that empty; the
 * two refusals in [readWebResearch] are the ones that mean something for a research answer.
 */
@Singleton
class HttpOpenRouterResearch @Inject constructor(
    private val httpClient: OkHttpClient,
    private val session: OpenRouterSession,
    private val modelId: OpenRouterModelId,
) : OpenRouterResearch {
    override suspend fun research(prompt: String): WebResearchResponse {
        val bearer = openRouterBearer(session.apiKey()) ?: throw OpenRouterException.LoginRequired()
        val completion = OpenRouterChatRequests.build(
            AiRequest.Chat(prompt),
            modelId.modelId(),
            AiRequestOptions.Research,
            stream = false,
        )

        // Only the socket is on `Dispatchers.IO`; reading and parsing the body is CPU work and belongs
        // on the caller's dispatcher, as the chat's own path does.
        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(openRouterCompletionRequest(bearer, completion.body)).execute()
        }
        return response.use {
            // Read once, because a body is a stream: the two decisions below both need it and only one
            // of them happens.
            val raw = it.body?.string().orEmpty()
            when (val exchange = readOpenRouterExchange(it.code, raw)) {
                is OpenRouterExchange.KeyRejected -> {
                    session.signOut()
                    throw OpenRouterException.LoginRequired()
                }

                is OpenRouterExchange.Refused -> throw OpenRouterException.RequestFailed(exchange.message)

                // The two success cases are the same outcome for a research answer, and deliberately so:
                // `readOpenRouterExchange` calls a 2xx with no content empty, which a research answer
                // may legitimately be — all evidence and no prose — so what it says about the *content*
                // is not consulted and the parser that knows this shape reads the body instead.
                is OpenRouterExchange.Answered, is OpenRouterExchange.Empty -> readWebResearch(raw)
            }
        }
    }
}
