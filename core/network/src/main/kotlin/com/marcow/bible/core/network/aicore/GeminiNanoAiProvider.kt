package com.marcow.bible.core.network.aicore

import com.marcow.bible.core.model.AiProviderId
import com.marcow.bible.core.network.ai.AiAvailability
import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.AiResponse
import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano answering as an [AiProvider], the on-device half of the pair
 * `NATIVE_PLAN.md` §4 Phase 4 requires and not a replacement for OpenRouter.
 *
 * Everything the two providers disagree about is settled here rather than in the chat, which is the
 * whole point of the port and the reason this class exists at all. Four differences, each with the
 * behaviour §4 asks for:
 *
 *  - **No web search.** [AiRequestOptions.webSearch] is read and then ignored, and every request goes
 *    down the no-tool path. A forced search against a model with no browser would either be silently
 *    dropped or answered from weights, so the first is what happens and the affordance is hidden
 *    upstream from [AiProviderId.supportsWebSearch].
 *  - **No reasoning channel.** The client returns one text stream, so no [ChatEvent.ReasoningDelta] is
 *    ever emitted and the thinking block stays empty. §4.2 asks for that to be defined rather than
 *    treated as an error, and an absent block is what the Flutter chat already drew for a model that
 *    did not think out loud.
 *  - **A truncated answer is `incomplete`.** [NanoFinishReason.MaxTokens] becomes [FinishReason.LENGTH],
 *    which is the only [FinishReason] the chat branches on, so a Nano answer that ran out of tokens is
 *    marked 「回覆已停止或尚未完整」 exactly as an OpenRouter one is.
 *  - **No usage.** The client reports no token counts, so [ChatEvent.Finished] carries a null [AiUsage]
 *    rather than a zero — §4.1 calls usage optional and nothing draws it.
 *
 * The prompt goes over untouched. §4.3 forbids reshaping a tuned prompt for the on-device model, so
 * there is no summarising, no truncation and no "make it shorter so it fits" — [NanoModel] is handed
 * the same string OpenRouter is, and if a phone cannot answer it, the answer is the gate in
 * [availability] rather than a shorter question.
 */
@Singleton
class GeminiNanoAiProvider @Inject constructor(private val models: NanoModelFactory) : AiProvider {
    override val id = AiProviderId.GeminiNano

    /**
     * The device and hardware gate, as the settings row shows it.
     *
     * The gate is asked for afresh on every call rather than cached, because the phone's answer can
     * change while the app is running: Play services for AI Edge gets installed, or a model finishes
     * downloading, and a cached "unavailable" would leave the row stale until the process restarts.
     */
    override suspend fun availability(): AiAvailability = when (val availability = models.availability()) {
        is NanoAvailability.Ready -> AiAvailability.Available
        is NanoAvailability.Gated ->
            AiAvailability.Unavailable(
                reason = availability.gate.message,
                // No button is offered here: unlike OpenRouter's sign-in there is nothing the reader
                // can press inside the app to fix a phone that is too old or too small.
                signInRequired = false,
            )
    }

    /**
     * The answer as it arrives.
     *
     * The prompt is resolved and the model opened when the flow is *collected*, not when it is
     * assembled, for the reason `OpenRouterAiProvider` does the same: a chat screen that is built and
     * never listened to must not have prepared a model.
     *
     * Stopping the answer cancels the collection, which cancels the client's flow, and the `finally`
     * then closes the model — so a stopped answer does not leave a service binding behind waiting for
     * a question that is never coming.
     */
    override fun stream(request: AiRequest, options: AiRequestOptions): Flow<ChatEvent> = flow {
        val model = models.open()
        try {
            model.prepare()
            var stopped = false
            model.stream(promptOf(request)).collect { chunk ->
                if (chunk.text.isNotEmpty()) emit(ChatEvent.ContentDelta(chunk.text))
                val reason = chunk.finishReason?.let { finishReasonOf(it) }
                if (reason != null && !stopped) {
                    stopped = true
                    emit(ChatEvent.Finished(reason))
                }
            }
            // A stream that ended without a stop reason is an answer that ran out of frames rather
            // than out of tokens, and the only honest thing to say about it is that it finished.
            if (!stopped) emit(ChatEvent.Finished(FinishReason.STOP))
        } finally {
            model.close()
        }
    }

    /**
     * [stream] joined, which is what `generate()` was: `generateStream(…).join()` in the Flutter build.
     *
     * Joined from [stream] rather than through a second port call, so a request that worked in the chat
     * cannot answer differently here — one path means one set of provider differences, which is the
     * thing §4 Phase 4 asks for. The text is not trimmed, for the reason
     * `OpenRouterAiProvider.complete` does not trim either: the chat appends deltas as they arrive and
     * never trimmed, and trimming here would change what the reader sees.
     */
    override suspend fun complete(request: AiRequest, options: AiRequestOptions): AiResponse {
        val text = StringBuilder()
        var incomplete = false
        stream(request, options).collect { event ->
            when (event) {
                is ChatEvent.ContentDelta -> text.append(event.text)
                is ChatEvent.Finished -> incomplete = event.reason.incomplete
                is ChatEvent.ReasoningDelta, is ChatEvent.WebCitation, is ChatEvent.Failure -> Unit
            }
        }
        return AiResponse(text = text.toString(), incomplete = incomplete)
    }

    /**
     * The text to send, unchanged.
     *
     * The two search kinds carry no prompt — `feature/search` owns those texts and a second copy of a
     * tuned prompt is a second thing to keep verbatim — so a blank prompt stands in, exactly as
     * `OpenRouterChatRequests` sends one. It is unreachable through the search feature, which builds
     * its own request shape rather than coming through here, and a mistake shows up as an obviously
     * empty answer instead of a question the model was never asked.
     */
    private fun promptOf(request: AiRequest): String = when (request) {
        is AiRequest.Chat -> request.prompt
        is AiRequest.SearchOverview, is AiRequest.SearchReferences -> ""
    }
}

/**
 * The client's two named stops onto the port's four, and nothing else.
 *
 * [NanoFinishReason.MaxTokens] to [FinishReason.LENGTH] is the load-bearing line: it is what sets
 * `AiMessage.incomplete`, and §4.2 asks for that flag to mean the same thing on both providers.
 */
private fun finishReasonOf(reason: NanoFinishReason): FinishReason = when (reason) {
    NanoFinishReason.Stop -> FinishReason.STOP
    NanoFinishReason.MaxTokens -> FinishReason.LENGTH
    NanoFinishReason.Safety -> FinishReason.CONTENT_FILTER
    NanoFinishReason.Other -> FinishReason.UNKNOWN
}