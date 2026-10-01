package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts one chat completion to OpenRouter and hands back the text, replacing `OpenRouterClient` +
 * `generate()` in `legacy/flutter/lib/openrouter_service.dart`.
 *
 * The body is already the exact object for the wire (see [ChatCompletionRequest]), so this class
 * only does what `_send` did around it: read the key, attach the three headers, decide what a
 * status code means, and pull the content out of the answer. Everything that decides *what* to ask
 * lives with the feature that asks it.
 *
 * The one deliberate difference is `stream`. `generate()` in Dart is
 * `generateStream(…).join()`, so the Flutter search asked for a streamed response and concatenated
 * the deltas; this client asks for a single completed answer instead, which is what the sheet needs
 * (it publishes one overview and one reference set) and what the Phase 4 SSE client replaces. The
 * prompt, the token ceiling, the temperature and the excluded reasoning are unchanged, so the model
 * is asked the same question either way.
 */
@Singleton
class OpenRouterChatClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val session: OpenRouterSession,
) {
    /**
     * The text of one completion.
     *
     * @throws OpenRouterException.LoginRequired when there is no key, or the provider rejected it.
     * @throws OpenRouterException.RequestFailed for any other non-2xx answer.
     * @throws OpenRouterException.EmptyResponse when the answer carried no content.
     */
    suspend fun complete(request: ChatCompletionRequest): String {
        val key = session.apiKey()
        if (key.isNullOrEmpty()) throw OpenRouterException.LoginRequired()

        val httpRequest = Request.Builder()
            .url(OPENROUTER_CHAT_COMPLETIONS)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("X-OpenRouter-Title", TITLE)
            .post(request.body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(httpRequest).execute() }
        response.use {
            if (it.code == 401 || it.code == 403) {
                // Drain before signing out, exactly as `_send` did, so the connection is released
                // rather than left half-read when the socket is closed.
                it.body?.bytes()
                session.signOut()
                throw OpenRouterException.LoginRequired()
            }
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw OpenRouterException.RequestFailed(failureMessage(it.code, raw))
            val content = contentText(raw)
            if (content.isEmpty()) throw OpenRouterException.EmptyResponse()
            return content
        }
    }
}

/** `'OpenRouter request failed (${response.statusCode}): ${_errorMessage(payload)}'`. */
private fun failureMessage(statusCode: Int, raw: String): String =
    "OpenRouter request failed ($statusCode): ${openRouterErrorMessage(payloadOrRaw(raw))}"

/**
 * `jsonDecode(rawBody)` with the body as the fallback: a body that is not JSON at all is reported
 * as it arrived, which is what `_send` did when `jsonDecode` threw.
 */
private fun payloadOrRaw(raw: String): JsonElement = try {
    Json.parseToJsonElement(raw)
} catch (_: SerializationException) {
    JsonPrimitive(raw)
}

/** The non-streaming shape: `choices[0].message.content`. */
private fun contentText(raw: String): String {
    val payload = try {
        Json.parseToJsonElement(raw)
    } catch (_: SerializationException) {
        return ""
    }
    val choice = (payload["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
    val message = choice["message"] as? JsonObject ?: return ""
    return contentOf(message["content"])
}

/**
 * `_contentText`: a string, or the `text` of every object part of a content-part list, which is the
 * shape several OpenRouter models answer in.
 */
private fun contentOf(raw: JsonElement?): String = when (raw) {
    is JsonPrimitive -> if (raw.isString) raw.content else ""
    is JsonArray ->
        raw
            .mapNotNull { part -> (part as? JsonObject)?.get("text") as? JsonPrimitive }
            .filter { it.isString }
            .joinToString(separator = "") { it.content }
    else -> ""
}

/**
 * `_errorMessage` in `legacy/flutter/lib/openrouter_service.dart`.
 *
 * A provider error arrives either as `{"error": {"message": …}}` or as the message itself, and the
 * two are told apart here rather than at each call site. Only a JSON string counts as a message:
 * Dart's `message is String` skipped a number, and a number reported as its digits would be a
 * different error text than the Flutter build showed.
 */
internal fun openRouterErrorMessage(value: JsonElement?): String {
    if (value is JsonObject) {
        val nested = value["error"]
        if (nested != null && nested != value) return openRouterErrorMessage(nested)
        messageText(value["message"])?.let { return it }
    }
    if (value is JsonPrimitive && value.isString) {
        messageText(value)?.let { return it }
    }
    return UNKNOWN_ERROR_MESSAGE
}

private fun messageText(raw: JsonElement?): String? = (raw as? JsonPrimitive)
    ?.takeIf { it.isString }
    ?.content
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/** `Uri.parse('https://openrouter.ai/api/v1/chat/completions')` of `_send`. */
private const val OPENROUTER_CHAT_COMPLETIONS = "https://openrouter.ai/api/v1/chat/completions"

/** `'X-OpenRouter-Title': 'Bible'`, so the provider attributes the traffic to this app. */
private const val TITLE = "Bible"

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
