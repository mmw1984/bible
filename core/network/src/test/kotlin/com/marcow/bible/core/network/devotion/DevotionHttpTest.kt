package com.marcow.bible.core.network.devotion

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The one request every tier of the devotion fetch makes, against `devotionGetText` in
 * `DevotionHttp.kt` and the `_kDevotionHeaders` it was ported from.
 *
 * No socket is opened: an application interceptor answers from memory, which is what lets the three
 * things that can only be seen on the wire be asserted in CI — the headers the blog requires, the
 * status that turns a tier off, and the charset the body is decoded with.
 */
class DevotionHttpTest {
    @Test
    fun `every tier's request carries the browser headers the blog requires`() = runTest {
        val stub = StubInterceptor(bytes = "<rss/>".toByteArray())

        client(stub).devotionGetText(FEED_URL)

        // A regression here is invisible until the blog answers 403 to all three tiers at once, which
        // is the whole reason `_kDevotionHeaders` exists.
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
            stub.request?.header("User-Agent"),
        )
        assertEquals(DEVOTION_HEADERS.keys, stub.request?.headers?.names(), "the request's whole header set")
        assertEquals(FEED_URL, stub.request?.url.toString())
    }

    @Test
    fun `a status the site refused becomes a tier failure naming it`() = runTest {
        val stub = StubInterceptor(code = 403)

        val thrown = assertThrows<DevotionFetchException> { client(stub).devotionGetText(FEED_URL) }

        // The status is the only thing a reader can act on from the detail line under the message.
        assertTrue(thrown.message!!.contains("403"), thrown.message)
    }

    @Test
    fun `the body is decoded as utf-8 whatever the response declares`() = runTest {
        // The feed serves CJK. `ResponseBody.string()` honours the declared charset, so a page that
        // answers `charset=ISO-8859-1` — or names none at all — would arrive as replacement characters.
        val stub = StubInterceptor(
            bytes = "靈修默想".toByteArray(Charsets.UTF_8),
            contentType = "text/xml; charset=ISO-8859-1",
        )

        val text = client(stub).devotionGetText(FEED_URL)

        assertEquals("靈修默想", text)
    }

    @Test
    fun `a response with no body at all is empty text rather than a failure`() = runTest {
        assertEquals("", client(StubInterceptor()).devotionGetText(FEED_URL))
    }

    private fun client(stub: StubInterceptor) = OkHttpClient.Builder().addInterceptor(stub).build()

    /**
     * Answers from memory, recording the request it was given.
     *
     * [contentType] is settable because the charset regression is only observable when the server
     * declares the wrong one — a body that declares UTF-8 proves nothing about which of the two
     * decoders ran.
     */
    private class StubInterceptor(
        private val code: Int = 200,
        private val contentType: String = "text/xml; charset=utf-8",
        private val bytes: ByteArray = ByteArray(0),
    ) : Interceptor {
        var request: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val sent = chain.request()
            request = sent
            return Response.Builder()
                .request(sent)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body(bytes.toResponseBody(contentType.toMediaType()))
                .build()
        }
    }

    private companion object {
        /** The feed URL, standing in for whichever tier is being exercised. */
        const val FEED_URL = "$DEVOTION_ORIGIN$DEVOTION_FEED_PATH"
    }
}
