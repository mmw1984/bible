package com.marcow.bible.core.network.aicore

import kotlinx.coroutines.flow.Flow

/**
 * Why the on-device model cannot answer, as a fact about the phone rather than a string.
 *
 * `NATIVE_PLAN.md` §4 Phase 4 asks for the "device / hardware 閘門" to land in `AiAvailability`, and it
 * is an enum rather than a free message because each case has to be decided separately and none of
 * them can be repaired by asking the question again: a phone that is too old stays too old, a
 * missing Play services component has to be installed, and a model that has not been downloaded has
 * to be downloaded. The one thing none of them is is a question the reader asked badly.
 *
 * Ordered by how early they are decided, because that is the order the adapter checks them in.
 */
enum class NanoGate(val message: String) {
    /**
     * The phone is below the API level the Play services for AI Edge client asks for.
     *
     * The client declares `minSdkVersion 31`, and `NATIVE_PLAN.md` §6 R1 puts the app on `minSdk 26`
     * for the variable fonts, so the two cannot both be unconditional — which is why this case has to
     * exist rather than being caught by a `Build.VERSION` check buried in the adapter.
     */
    UnsupportedOsVersion("This phone's Android version is older than the on-device model needs."),

    /** Play services for AI Edge is not installed, so the model has nothing to run on. */
    ServiceMissing("Play services for AI Edge is not installed on this phone."),

    /** The phone is too small or too slow for the model, which the client reports per device. */
    HardwareUnsupported("This phone cannot run the on-device model."),

    /** The model is supported but is not on the phone yet. */
    ModelNotDownloaded("The on-device model has to be downloaded before it can answer."),

    /** The download was refused or broke, which the client's download callback reports as it happens. */
    ModelUnavailable("The on-device model is not available right now."),
    ;

    /** True for the cases where the reader's next move is to wait, rather than to change a setting. */
    val transient: Boolean
        get() = this == ModelNotDownloaded || this == ModelUnavailable
}

/**
 * Whether the on-device model can answer, and if not which [NanoGate] closed it.
 *
 * Separate from [com.marcow.bible.core.network.ai.AiAvailability] because this is the phone's answer
 * and that is the settings screen's: the provider maps one to the other, and keeping them apart means
 * the gate survives without dragging the settings copy into the adapter.
 */
sealed interface NanoAvailability {
    /** The phone can answer. The model still has to be prepared before the first question. */
    data object Ready : NanoAvailability

    /** It cannot, and this is why. */
    data class Gated(val gate: NanoGate) : NanoAvailability
}

/**
 * Why the model stopped, mirroring `com.google.ai.edge.aicore.Candidate.FinishReason`.
 *
 * The client's enum is two named values over an `Int` wire code, so [Other] is the honest landing
 * spot for a code this build has not heard of — the same reasoning as
 * [com.marcow.bible.core.network.ai.FinishReason.UNKNOWN].
 */
enum class NanoFinishReason {
    /** The model finished its answer. */
    Stop,

    /**
     * It hit `maxOutputTokens` mid-answer.
     *
     * This is the one that matters: it is the only stop a reader has to be told about, because
     * `NATIVE_PLAN.md` §4.2 says a truncated answer is marked incomplete and the chat shows
     * 「回覆已停止或尚未完整」 rather than presenting half a paragraph as the whole reply.
     */
    MaxTokens,

    /** The model stopped itself. Not a failure — Flutter did not treat it as one either. */
    Safety,

    /** A stop this build does not have a name for. */
    Other,
}

/**
 * One streamed piece of an answer: the next fragment of text, and the stop reason when the last one
 * carried it.
 *
 * `text` is empty rather than absent for a final frame that only carries a reason, because the client
 * puts the reason on the last candidate rather than in a field of its own.
 */
data class NanoChunk(val text: String = "", val finishReason: NanoFinishReason? = null)

/**
 * The on-device model, as [GeminiNanoAiProvider] needs it.
 *
 * This is the whole Play services for AI Edge surface the provider touches, restated in this package's
 * own types: `GenerativeModel.generateContentStream` returns a `Flow<GenerateContentResponse>` whose
 * candidates carry `content.parts` and a `finishReason`, and nothing else. Naming that here rather
 * than against the client keeps three things true at once:
 *
 *  - the provider is testable without a Play services install, a download and an emulator image,
 *  - the `aicore` artifact stays an implementation detail that [NanoModelFactory] picks at runtime,
 *    which matters because its AAR declares `minSdkVersion 31` while the app is on `minSdk 26`, and
 *  - cancelling the collection cancels the client's flow, which is what "stop" in the chat's toolbar
 *    does — the same contract [com.marcow.bible.core.network.ai.AiProvider.stream] promises.
 *
 * A model is one prepared instance rather than a factory: [NanoModelFactory.open] is asked for one
 * per question and the answer closes it, which is the same per-request lifetime an HTTP call has.
 */
interface NanoModel {
    /**
     * Binds the service and makes sure the weights are there, or throws.
     *
     * Separate from [open] because it is the part that can take seconds and the part that can fail for
     * reasons the reader cannot act on mid-question — the chat shows a spinner while this runs rather
     * than a half-drawn answer.
     */
    suspend fun prepare()

    /**
     * The answer as it arrives, one [NanoChunk] per frame the client emits.
     *
     * The client's own non-streaming `generateContent` is deliberately not on this port:
     * `GeminiNanoAiProvider.complete` joins this flow instead, so the search paths that only want text
     * and the chat share one code path and cannot drift apart.
     */
    fun stream(prompt: String): Flow<NanoChunk>

    /** Releases the service binding. Called by the provider when the answer is over or was stopped. */
    fun close()
}

/**
 * Opens the on-device model, and answers whether it can be opened at all.
 *
 * A factory rather than a model because the phone's answer changes while the app is running: a reader
 * installs Play services for AI Edge in another app, or a model finishes downloading, and the settings
 * row has to be able to notice. Holding one [NanoModel] for the process would make that impossible.
 */
interface NanoModelFactory {
    /**
     * The device and hardware gate, asked afresh every time.
     *
     * It is a `suspend` call because the client reaches another process to answer it — a `PackageManager`
     * lookup for the service is cheap, but a client that reports the hardware gate may have to ask the
     * service itself.
     */
    suspend fun availability(): NanoAvailability

    /** A model for one question. Throws [NanoException] when the gate is shut. */
    suspend fun open(): NanoModel
}

/**
 * The ways an on-device call fails that are not the model's fault.
 *
 * Split from [NanoFinishReason] on purpose: [NanoFinishReason] is what came back, and these are what
 * stopped it arriving. [gate] is a [NanoGate] rather than a message so the chat can offer the same
 * fix the settings row offers — wait for a download, install a service — instead of only printing a
 * sentence.
 */
sealed class NanoException(message: String) : Exception(message) {
    /** The gate shut between the settings row being drawn and the question being asked. */
    class Gated(val gate: NanoGate) : NanoException(gate.message)

    /** The model was prepared but the request itself failed. */
    class RequestFailed(message: String) : NanoException(message)
}