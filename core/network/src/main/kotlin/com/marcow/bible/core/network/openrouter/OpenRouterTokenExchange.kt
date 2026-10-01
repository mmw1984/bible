package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one POST of the sign-in, split out of `OpenRouterAuthManager` for the reason
 * [OpenRouterChatClient] is a port: the exchange is the only part of a sign-in that needs a socket,
 * so a JVM test can hand the manager a scripted answer and pin every state transition around it.
 */
interface OpenRouterTokenExchange {
    /**
     * Trades an authorization [code] for an API key.
     *
     * @throws IllegalStateException on a non-2xx answer, or a 2xx one carrying no key, with the
     * wording `_performPendingExchange` reported.
     */
    suspend fun exchange(code: String, codeVerifier: String, codeChallengeMethod: String): String
}

/**
 * `POST https://openrouter.ai/api/v1/auth/keys` of `_performPendingExchange` in
 * `legacy/flutter/lib/openrouter_service.dart`.
 *
 * The body is the same three keys Dart sent and the answer is read the same way — a `key` field, or a
 * failure whose text is truncated to 240 characters so an HTML error page from a proxy cannot become
 * an unreadable exception message.
 */
@Singleton
class HttpOpenRouterTokenExchange @Inject constructor(private val httpClient: OkHttpClient) : OpenRouterTokenExchange {
    override suspend fun exchange(code: String, codeVerifier: String, codeChallengeMethod: String): String {
        val payload = buildJsonObject {
            put("code", code)
            put("code_verifier", codeVerifier)
            put("code_challenge_method", codeChallengeMethod)
        }.toString()
        val request = Request.Builder()
            .url(OPENROUTER_AUTH_KEYS)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IllegalStateException(exchangeFailure(it.code, raw))
            val key = keyOf(raw)
            if (key == null) throw IllegalStateException(NO_KEY_MESSAGE)
            return key
        }
    }
}

/**
 * `'OpenRouter OAuth exchange failed (${response.statusCode})'` plus `': <detail>'` when there was one.
 *
 * Dart cut the detail with `detail.substring(0, min(240, detail.length))`, so a short body reads as
 * `…failed (500): boom` and an empty one as `…failed (500).`.
 */
private fun exchangeFailure(statusCode: Int, raw: String): String {
    val detail = raw.trim()
    return "OpenRouter OAuth exchange failed ($statusCode)" +
        if (detail.isEmpty()) "." else ": ${detail.take(DETAIL_LIMIT)}"
}

/** `jsonDecode(response.body)['key'] as String?`, ignoring a body that is not JSON at all. */
private fun keyOf(raw: String): String? {
    val payload = try {
        Json.parseToJsonElement(raw)
    } catch (_: SerializationException) {
        return null
    }
    val key = (payload as? JsonObject)?.get("key") as? JsonPrimitive ?: return null
    return key.content.takeIf { key.isString && it.isNotEmpty() }
}

/** `Uri.parse('https://openrouter.ai/api/v1/auth/keys')`. */
private const val OPENROUTER_AUTH_KEYS = "https://openrouter.ai/api/v1/auth/keys"

/** `min(240, detail.length)` in Dart. */
private const val DETAIL_LIMIT = 240

/** `StateError('OpenRouter returned no API key.')`. */
private const val NO_KEY_MESSAGE = "OpenRouter returned no API key."

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
