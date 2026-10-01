package com.marcow.bible.core.network.devotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The one request every tier of the devotion fetch makes, matching the shape
 * `client.get(uri, headers: _kDevotionHeaders).timeout(const Duration(seconds: 20))` had.
 *
 * The bound is Flutter's 20 s and not the shared client's, because that client also serves AI
 * completions that legitimately run for minutes: one client cannot carry one timeout for both, so
 * the devotion bound is applied per call here. The body is read inside the timeout so a stalled
 * connection is closed rather than left half-read when the coroutine is cancelled.
 *
 * The bytes are decoded as UTF-8 explicitly instead of through `ResponseBody.string()`, which
 * honours the response's declared charset — `utf8.decode(response.bodyBytes)` did not, and CJK feed
 * text served without a charset header would otherwise arrive as replacement characters.
 *
 * Every way this can go wrong comes out as a [DevotionFetchException], so a tier's contract is one
 * type: a DNS failure, a refused connection and a 503 are all "this tier did not deliver".
 */
internal suspend fun OkHttpClient.devotionGetText(url: String): String = withTimeoutOrNull(DEVOTION_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            try {
                newCall(
                    Request.Builder()
                        .url(url)
                        .headers(DEVOTION_HEADERS.toHeaders())
                        .build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw DevotionFetchException("devotion request $url failed (${response.code})")
                    }
                    response.body?.bytes()?.toString(Charsets.UTF_8).orEmpty()
                }
            } catch (unreachable: IOException) {
                throw DevotionFetchException("devotion request $url failed: ${unreachable.message}", unreachable)
            }
        }
    } ?: throw DevotionFetchException("devotion request $url timed out")

/** `.timeout(const Duration(seconds: 20))` on every `_kDevotionHeaders` request. */
private const val DEVOTION_TIMEOUT_MS = 20_000L
