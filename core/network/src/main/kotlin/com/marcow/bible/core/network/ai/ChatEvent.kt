package com.marcow.bible.core.network.ai

import kotlinx.coroutines.flow.Flow

/**
 * One piece of a streamed answer, replacing the `StreamedResponse` Dart handed around and the
 * `ChatEvent` sealed list in `NATIVE_PLAN.md` §4.1.
 *
 * The three deltas `_streamModelAnswer` separated by hand are separate cases here, because the UI
 * draws them in different places: [ReasoningDelta] goes into the foldable thinking block, [ContentDelta]
 * into the Markdown body, and [Finished] is what decides whether the message is marked incomplete.
 * A provider that cannot think simply never emits [ReasoningDelta] — `NATIVE_PLAN.md` §4 Phase 4 asks
 * for that to be a defined behaviour rather than an error, so the chat looks the same either way.
 */
sealed interface ChatEvent {
    /** `choices[0].delta.content`, the visible answer's next fragment. */
    data class ContentDelta(val text: String) : ChatEvent

    /** `choices[0].delta.reasoning`, the next fragment of the model's thinking. */
    data class ReasoningDelta(val text: String) : ChatEvent

    /** The stream's last event, carrying why it stopped. */
    data class Finished(val reason: FinishReason, val usage: AiUsage? = null) : ChatEvent

    /** One `message.annotations` entry: evidence the model gathered. Never shown to the reader. */
    data class WebCitation(val url: String, val title: String) : ChatEvent

    /**
     * The stream failed.
     *
     * This is a value rather than a thrown exception so a failure part-way through an answer arrives
     * the same way whatever stopped it: the text so far stays on screen and the error panel appears,
     * which is what `_generationErrorMessage` and "內容已保留" in the Flutter chat both promise.
     */
    data class Failure(val message: String) : ChatEvent
}

/**
 * Why a provider stopped, replacing the raw `choices[0].finish_reason` string.
 *
 * [incomplete] is the only thing the chat actually branches on — `NATIVE_PLAN.md` §4.2 says a
 * `finish_reason` of `length` sets `AiMessage.incomplete` and the UI shows 「回覆已停止或尚未完整」.
 * Every other reason is a complete answer, including [ContentFilter], which Flutter did not treat
 * as a failure either: it produced a finished message with whatever text came back.
 */
enum class FinishReason(val wireValue: String, val incomplete: Boolean) {
    /** `stop`, the ordinary end of an answer. */
    STOP("stop", false),

    /** `length`, the ceiling was reached mid-answer — the `[[MORE]]` case. */
    LENGTH("length", true),

    /** `tool_calls`, the model asked for a tool rather than answering. */
    TOOL_CALLS("tool_calls", false),

    /** `content_filter`, the provider stopped the answer itself. */
    CONTENT_FILTER("content_filter", false),

    /** Anything this build has not heard of, which is not a reason to call the answer broken. */
    UNKNOWN("", false),
    ;

    companion object {
        /** Reads a provider's `finish_reason`, unknown values becoming [UNKNOWN] rather than failing. */
        fun of(wireValue: String?): FinishReason = entries.firstOrNull { it.wireValue == wireValue } ?: UNKNOWN
    }
}

/**
 * What a provider spent, replacing the `usage` object Dart read off the response.
 *
 * `stream_options: {include_usage: true}` is what puts this on the wire; `NATIVE_PLAN.md` §4.1 calls
 * it an optional enhancement, so nothing in the chat has to draw it and [ChatEvent.Finished] leaves
 * it null when the provider did not send one.
 */
data class AiUsage(val promptTokens: Int = 0, val completionTokens: Int = 0, val totalTokens: Int = 0)

/**
 * A whole answer, which is what a provider gives back when the caller did not ask for a stream.
 *
 * The Flutter chat always streamed; the search paths called `generate()`, which was
 * `generateStream(…).join()`. [text] is the concatenation of every [ChatEvent.ContentDelta] and
 * [incomplete] is the [Finished] flag, so a non-streaming answer and a streamed one that is joined
 * are the same value — which is what lets a provider without a streaming API answer the same
 * request.
 */
data class AiResponse(
    val text: String,
    val reasoning: String = "",
    val incomplete: Boolean = false,
    val usage: AiUsage? = null,
    val citations: List<ChatEvent.WebCitation> = emptyList(),
)

/**
 * The port every provider answers through, the abstraction `NATIVE_PLAN.md` §4 Phase 4 asks for:
 * `AiRequest` / `AiResponse` / `ChatEvent` hold for both OpenRouter and Gemini Nano, and the
 * provider differences are settled here rather than in the UI.
 *
 * It is deliberately narrow. `research()` is *not* here: a forced web search with a server-side
 * budget is an OpenRouter capability, and putting it on this port would have Gemini Nano answer
 * with an empty citation list. The chat hides the affordance instead, which is what §4 Phase 4
 * prescribes for a provider with no web tool.
 */
interface AiProvider {
    /** Which provider this is, for the settings selector and the stored `ai_provider` value. */
    val id: AiProviderId

    /**
     * Whether this provider can answer right now, for `AiAvailability` in the settings screen.
     *
     * OpenRouter reports whether a key is held, so the settings row can say "not signed in" rather
     * than letting the first question fail. Gemini Nano reports the device gate, which is why this
     * is a `suspend` call: both answers come from somewhere that has to be asked.
     */
    suspend fun availability(): AiAvailability

    /**
     * The answer as it arrives.
     *
     * Cancelling the collection cancels the request, which is what "stop" in the chat's toolbar does
     * — the partial text stays and no [ChatEvent.Finished] is delivered, because a stopped answer is
     * not a finished one.
     */
    fun stream(request: AiRequest, options: AiRequestOptions = AiRequestOptions()): Flow<ChatEvent>

    /** [stream] joined into one [AiResponse], for the paths that only want the text. */
    suspend fun complete(request: AiRequest, options: AiRequestOptions = AiRequestOptions()): AiResponse
}

/**
 * Which provider the user picked, the `ai_provider` setting of `NATIVE_PLAN.md` §4 Phase 4.
 *
 * [fromStorage] defaults to [OpenRouter] on a missing or unrecognised value, and that default is a
 * requirement rather than a convenience: the setting is new, so every existing install has no value
 * for it, and the users who already signed in to OpenRouter must keep the provider they signed in
 * to. An on-device default would silently move them off the key they had.
 */
enum class AiProviderId(val storageValue: String) {
    /** The default: needs a sign-in, and is the only one with web search. */
    OpenRouter("openrouter"),

    /** On-device via Play services for AI Edge; no sign-in, and gated on the hardware. */
    GeminiNano("gemini_nano"),
    ;

    /** Whether this provider can answer a web search at all, which the settings screen asks. */
    val supportsWebSearch: Boolean
        get() = this == OpenRouter

    companion object {
        /** Reads `ai_provider`, falling back to [OpenRouter] exactly as §4 Phase 4 requires. */
        fun fromStorage(value: String?): AiProviderId = entries.firstOrNull { it.storageValue == value } ?: OpenRouter
    }
}

/**
 * Whether a provider can answer, and if not why.
 *
 * A sealed pair rather than a `Boolean` because the settings row has to say something: Gemini Nano
 * on a device that cannot run it is not the same as a provider nobody has signed in to, and the
 * first is a fact about the phone while the second is a button the user can press.
 */
sealed interface AiAvailability {
    /** Ready to answer. */
    data object Available : AiAvailability

    /** Not ready, with a reason to show. [signInRequired] is the one that offers a button. */
    data class Unavailable(val reason: String, val signInRequired: Boolean = false) : AiAvailability
}
