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
 * Where a chat completion is asked for, replacing `OpenRouterClient` in
 * `legacy/flutter/lib/openrouter_service.dart`.
 *
 * The search feature asks for the text of a completion and nothing else: what the body says is
 * [ChatCompletionRequest]'s business, what a status code means is the transport's, and the key is
 * nobody's but [OpenRouterSession]'s. Keeping that behind a port is what lets the use cases be
 * exercised without a socket, and it is the same reason [OpenRouterSession] and [OpenRouterModelId]
 * are ports rather than classes.
 */
interface OpenRouterChatClient {
    /**
     * The text of one completion.
     *
     * @throws OpenRouterException.LoginRequired when there is no key, or the provider rejected it.
     * @throws OpenRouterException.RequestFailed for any other non-2xx answer.
     * @throws OpenRouterException.EmptyResponse when the answer carried no content.
     */
    suspend fun complete(request: ChatCompletionRequest): String
}

/**
 * The HTTP client behind [OpenRouterChatClient], replacing `generate()` in
 * `legacy/flutter/lib/openrouter_service.dart`.
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
 *
 * Everything except the socket is [readOpenRouterExchange] and [openRouterBearer], so the decisions
 * `_send` made about an answer — which statuses sign you out, which text an error carries, what a
 * completion's content may be — are exercised without a network, the way `core/network`'s devotion
 * decoders are.
 */
@Singleton
class HttpOpenRouterChatClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val session: OpenRouterSession,
) : OpenRouterChatClient {
    override suspend fun complete(request: ChatCompletionRequest): String {
        val bearer = openRouterBearer(session.apiKey()) ?: throw OpenRouterException.LoginRequired()

        val httpRequest = openRouterCompletionRequest(bearer, request.body)

        val response = withContext(Dispatchers.IO) { httpClient.newCall(httpRequest).execute() }
        return response.use {
            // Read before branching rather than only on the failure path: every branch below consumes
            // the body, so the connection is released rather than left half-read when the window is
            // closed, which is what `_send` did by draining the 401 before signing out.
            when (val exchange = readOpenRouterExchange(it.code, it.body?.string().orEmpty())) {
                is OpenRouterExchange.Answered -> exchange.text
                is OpenRouterExchange.KeyRejected -> {
                    session.signOut()
                    throw OpenRouterException.LoginRequired()
                }

                is OpenRouterExchange.Refused -> throw OpenRouterException.RequestFailed(exchange.message)
                is OpenRouterExchange.Empty -> throw OpenRouterException.EmptyResponse()
            }
        }
    }
}

/**
 * What one exchange of `_send` amounted to, given everything except the socket.
 *
 * The two sign-in cases are kept apart from the ordinary failure because they are the two the sheet
 * answers differently: [KeyRejected] drops the stored key before the refusal is reported, so a
 * revoked key cannot wedge the app in a state where every retry fails the same way, and it is not a
 * message the user is asked to read.
 */
internal sealed interface OpenRouterExchange {
    /** The 401 / 403 pair: the key is gone, not the request. */
    data object KeyRejected : OpenRouterExchange

    /** A non-2xx answer, carrying `'OpenRouter request failed (<status>): <message>'`. */
    data class Refused(val message: String) : OpenRouterExchange

    /** A 2xx answer whose content was empty, the `OpenRouter returned no response.` of `_send`. */
    data object Empty : OpenRouterExchange

    /** The one completed answer's text. */
    data class Answered(val text: String) : OpenRouterExchange
}

/**
 * `'Bearer $key'`, or nothing at all when there is no key.
 *
 * Dart's `_send` took the key from secure storage, threw `StateError(OPENROUTER_LOGIN_REQUIRED)`
 * when it came back empty, and only then built the request. An empty string counts as no key here
 * because a blank bearer is what a wiped store hands back, and asking the provider with it would
 * turn a sign-in prompt into a 401.
 */
internal fun openRouterBearer(key: String?): String? = key?.takeIf { it.isNotEmpty() }

/**
 * What a completed HTTP exchange amounts to, which is the branch of `_send` after the answer is in.
 *
 * The order is Dart's: the two statuses that mean the key is wrong come before the general non-2xx
 * case, so a revoked key signs out instead of reporting a message that would not fix anything.
 */
internal fun readOpenRouterExchange(code: Int, raw: String): OpenRouterExchange {
    if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) return OpenRouterExchange.KeyRejected
    if (code !in HTTP_SUCCESS_RANGE) return OpenRouterExchange.Refused(failureMessage(code, raw))
    val content = contentText(raw)
    return if (content.isEmpty()) OpenRouterExchange.Empty else OpenRouterExchange.Answered(content)
}

/**
 * The `Request` `_send` built: the URL, the three headers and the body.
 *
 * The endpoint, the `Bearer` header, `Content-Type` and `X-OpenRouter-Title: Bible` are the same
 * whether the answer arrives all at once or a frame at a time, so the streamed client builds its
 * request here rather than repeating the header set — a second copy is a second thing to get wrong
 * when the provider adds a requirement.
 */
internal fun openRouterCompletionRequest(bearer: String, body: JsonObject): Request = Request.Builder()
    .url(OPENROUTER_CHAT_COMPLETIONS)
    .header("Authorization", "Bearer $bearer")
    .header("Content-Type", "application/json")
    .header("X-OpenRouter-Title", TITLE)
    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
    .build()

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
    val choice = ((payload as? JsonObject)?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
        ?: return ""
    val message = choice["message"] as? JsonObject ?: return ""
    return contentOf(message["content"])
}

/**
 * `_contentText`: a string, or the `text` of every object part of a content-part list, which is the
 * shape several OpenRouter models answer in.
 *
 * Shared with the streamed path because a delta is the same decision on a smaller frame: the same
 * models that answer a completed call in content parts stream them as parts too, and a parser that
 * only read strings would answer those models with an empty screen.
 */
internal fun contentOf(raw: JsonElement?): String = when (raw) {
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

/** `Response.isSuccessful`, which OkHttp defines as this range rather than as `code < 400`. */
private val HTTP_SUCCESS_RANGE = 200..299

/** The two statuses `_send` answered by signing out rather than by reporting a message. */
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

/** `'X-OpenRouter-Title': 'Bible'`, so the provider attributes the traffic to this app. */
private const val TITLE = "Bible"

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
