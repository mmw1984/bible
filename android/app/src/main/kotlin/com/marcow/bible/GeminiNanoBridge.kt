package com.marcow.bible

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bridges the ML Kit GenAI Prompt API (Gemini Nano on the AICore system
 * service) to Dart. Everything here stays on-device: no API key, no network
 * call, and the model keeps working in airplane mode once downloaded.
 *
 * Dart talks to two channels, both separate from the app's existing
 * `bible/android` channel:
 *  - `bible/gemini_nano` (method) — `checkAvailability`, `prepareFeature`,
 *    `generate`, `generateStream`, `cancel`.
 *  - `bible/gemini_nano/stream` (events) — the streamed deltas of one
 *    `generateStream` call, tagged with the request `id` the caller supplied.
 *
 * Only one generation runs at a time, matching the Dart controller, which keeps
 * a single active subscription. Starting a new stream cancels the previous job.
 */
internal class GeminiNanoBridge : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var streamJob: Job? = null
    private var sink: EventChannel.EventSink? = null

    /**
     * AICore is a system service, so reaching it can fail even on a device that
     * otherwise qualifies. The handle is created lazily and a failure is treated
     * the same way as an explicit "not supported" answer instead of crashing.
     */
    private var model: GenerativeModel? = null
    private var modelFailure: String? = null

    private fun client(): GenerativeModel? {
        model?.let { return it }
        if (modelFailure != null) return null
        return try {
            val created = Generation.getClient()
            model = created
            created
        } catch (error: Throwable) {
            modelFailure = error.toString()
            Log.w(TAG, "AICore client unavailable: $modelFailure")
            null
        }
    }

    fun dispose() {
        streamJob?.cancel()
        streamJob = null
        sink = null
        scope.cancel()
    }

    // region MethodChannel

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkAvailability" -> checkAvailability(result)
            "prepareFeature" -> prepareFeature(result)
            "generate" -> generate(call, result)
            "generateStream" -> generateStream(call, result)
            "cancel" -> {
                streamJob?.cancel()
                streamJob = null
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun checkAvailability(result: MethodChannel.Result) {
        val client = client()
        if (client == null) {
            result.success(
                mapOf("status" to STATUS_UNSUPPORTED, "modelName" to null),
            )
            return
        }
        scope.launch {
            try {
                val status = client.checkStatus()
                val modelName = runCatching { client.getBaseModelName() }.getOrNull()
                if (modelName != null) {
                    Log.i(TAG, "AICore model: $modelName (status=$status)")
                }
                result.success(
                    mapOf(
                        "status" to statusName(status),
                        "modelName" to modelName,
                    ),
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "checkStatus failed", error)
                result.success(
                    mapOf("status" to STATUS_TEMPORARILY_UNAVAILABLE, "modelName" to null),
                )
            }
        }
    }

    /**
     * Runs the model download AICore needs before the first generation. Resolves
     * only once the download finished (or failed), so Dart can re-check the
     * availability straight afterwards.
     */
    private fun prepareFeature(result: MethodChannel.Result) {
        val client = client()
        if (client == null) {
            result.error(ERROR_UNSUPPORTED, "Gemini Nano is not supported on this device.", null)
            return
        }
        scope.launch {
            try {
                client.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted ->
                            Log.i(TAG, "Gemini Nano download started: ${status.bytesToDownload} bytes")
                        is DownloadStatus.DownloadProgress ->
                            Log.d(TAG, "Gemini Nano download: ${status.totalBytesDownloaded} bytes")
                        is DownloadStatus.DownloadCompleted ->
                            Log.i(TAG, "Gemini Nano download completed")
                        is DownloadStatus.DownloadFailed ->
                            Log.w(TAG, "Gemini Nano download failed", status.e)
                        else -> Unit
                    }
                }
                client.warmup()
                Log.i(TAG, "AICore model: ${runCatching { client.getBaseModelName() }.getOrNull()}")
                result.success(mapOf("status" to statusName(client.checkStatus())))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "prepareFeature failed", error)
                result.error(errorCode(error), errorMessage(error), null)
            }
        }
    }

    private fun generate(call: MethodCall, result: MethodChannel.Result) {
        val prompt = call.argument<String>("prompt")
        if (prompt.isNullOrEmpty()) {
            result.error(ERROR_BAD_ARGUMENTS, "Prompt is empty.", null)
            return
        }
        val client = client()
        if (client == null) {
            result.error(ERROR_UNSUPPORTED, "Gemini Nano is not supported on this device.", null)
            return
        }
        scope.launch {
            try {
                val response = client.generateContent(prompt)
                result.success(response.candidates.firstNotNullOfOrNull { it.text } ?: "")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "generate failed", error)
                result.error(errorCode(error), errorMessage(error), null)
            }
        }
    }

    /**
     * Starts a streamed answer. Returns as soon as the flow is being collected;
     * deltas arrive on the event channel tagged with [id].
     */
    private fun generateStream(call: MethodCall, result: MethodChannel.Result) {
        val id = call.argument<Number>("id")?.toInt()
        val prompt = call.argument<String>("prompt")
        if (id == null || prompt.isNullOrEmpty()) {
            result.error(ERROR_BAD_ARGUMENTS, "Missing request id or prompt.", null)
            return
        }
        val client = client()
        if (client == null) {
            result.error(ERROR_UNSUPPORTED, "Gemini Nano is not supported on this device.", null)
            return
        }
        streamJob?.cancel()
        streamJob = scope.launch {
            var finishReason = FINISH_OTHER
            try {
                client.generateContentStream(prompt).collect { response ->
                    for (thought in response.thoughtProcess) {
                        val text = thought.text
                        if (!text.isNullOrEmpty()) emit(id, EVENT_THOUGHT, text)
                    }
                    for (candidate in response.candidates) {
                        val text = candidate.text
                        if (!text.isNullOrEmpty()) emit(id, EVENT_TEXT, text)
                        val reason = finishReasonOf(candidate)
                        if (reason != null) finishReason = reason
                    }
                }
                emitDone(id, finishReason)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    emitDone(id, FINISH_OTHER)
                    throw error
                }
                Log.w(TAG, "generateContentStream failed", error)
                emitError(id, error)
            }
        }
        result.success(null)
    }

    // endregion

    // region EventChannel

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
    }

    override fun onCancel(arguments: Any?) {
        streamJob?.cancel()
        streamJob = null
        sink = null
    }

    private fun emit(id: Int, type: String, text: String) {
        sink?.success(mapOf("id" to id, "type" to type, "text" to text))
    }

    private fun emitDone(id: Int, finishReason: String) {
        sink?.success(mapOf("id" to id, "type" to EVENT_DONE, "finishReason" to finishReason))
    }

    private fun emitError(id: Int, error: Throwable) {
        sink?.success(
            mapOf(
                "id" to id,
                "type" to EVENT_ERROR,
                "code" to errorCode(error),
                "message" to errorMessage(error),
            ),
        )
    }

    // endregion

    private fun statusName(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> STATUS_READY
        FeatureStatus.DOWNLOADABLE -> STATUS_DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> STATUS_DOWNLOADING
        FeatureStatus.UNAVAILABLE -> STATUS_UNSUPPORTED
        else -> STATUS_TEMPORARILY_UNAVAILABLE
    }

    private fun finishReasonOf(candidate: Candidate): String? =
        when (candidate.finishReason) {
            Candidate.FinishReason.STOP -> FINISH_STOP
            Candidate.FinishReason.MAX_TOKENS -> FINISH_MAX_TOKENS
            Candidate.FinishReason.OTHER -> FINISH_OTHER
            else -> null
        }

    private fun errorCode(error: Throwable): String =
        if (error is GenAiException && error.errorCode == GenAiException.ErrorCode.NOT_SUPPORTED) {
            ERROR_UNSUPPORTED
        } else if (error is GenAiException &&
            error.errorCode == GenAiException.ErrorCode.AICORE_INCOMPATIBLE
        ) {
            ERROR_UNSUPPORTED
        } else {
            ERROR_GENERATION
        }

    private fun errorMessage(error: Throwable): String {
        val detail = if (error is GenAiException) {
            "${error.errorCode}: ${error.message ?: error.toString()}"
        } else {
            error.message ?: error.toString()
        }
        return detail.take(400)
    }

    companion object {
        private const val TAG = "BibleGeminiNano"

        const val METHOD_CHANNEL = "bible/gemini_nano"
        const val STREAM_CHANNEL = "bible/gemini_nano/stream"

        const val STATUS_READY = "ready"
        const val STATUS_DOWNLOADABLE = "downloadable"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_UNSUPPORTED = "unsupported"
        const val STATUS_TEMPORARILY_UNAVAILABLE = "temporarilyUnavailable"

        private const val EVENT_TEXT = "text"
        private const val EVENT_THOUGHT = "thought"
        private const val EVENT_DONE = "done"
        private const val EVENT_ERROR = "error"

        private const val FINISH_STOP = "stop"
        private const val FINISH_MAX_TOKENS = "maxTokens"
        private const val FINISH_OTHER = "other"

        private const val ERROR_BAD_ARGUMENTS = "bad_arguments"
        private const val ERROR_UNSUPPORTED = "unsupported"
        private const val ERROR_GENERATION = "gemini_nano"
    }
}
