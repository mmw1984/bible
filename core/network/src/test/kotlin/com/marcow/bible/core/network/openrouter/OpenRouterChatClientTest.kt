package com.marcow.bible.core.network.openrouter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * What `_send` in `legacy/flutter/lib/openrouter_service.dart` decided about an answer, checked
 * without a socket.
 *
 * The exchange itself is not driven over a network: `mockwebserver` is not on the version catalog
 * and there is no network in CI, the same reason `DevotionRestClientTest` pins a payload rather than
 * a response. What is pinned here is the half that has to be right for either caller — the search
 * sheet and, from Phase 4, the chat — and that half is not the HTTP framing:
 *
 *  - Which statuses mean *the key is wrong* rather than *the request was refused*, because only the
 *    first drops the stored key. Answering a revoked key as an ordinary failure is how an app ends
 *    up retrying a key the provider will never accept again.
 *  - What a refusal says, because that text is what the user is shown and what a crash report
 *    carries.
 *  - Which shapes of `message.content` count as the answer, since several OpenRouter models answer
 *    in content parts rather than in a string.
 */
class OpenRouterChatClientTest {
    @Test
    fun `no key means there is no bearer to send`() {
        // `_send` threw before building the request at all, so a signed-out search never reaches the
        // provider: the sheet draws `login_to_search` instead of a failure it could not act on.
        assertEquals(null, openRouterBearer(null))
        assertEquals(null, openRouterBearer(""))
        assertEquals("sk-or-v1-abc", openRouterBearer("sk-or-v1-abc"))
    }

    @Test
    fun `a rejected key is not an ordinary failure`() {
        // Both statuses are the one case that signs out, and neither may reach the failure branch:
        // that branch reports a message, and a message cannot fix a key the provider has revoked.
        assertInstanceOf(OpenRouterExchange.KeyRejected::class.java, readOpenRouterExchange(401, ""))
        assertInstanceOf(OpenRouterExchange.KeyRejected::class.java, readOpenRouterExchange(403, ""))
        // The body is never read on this path, so a provider that answers 401 with an HTML error
        // page signs out exactly as one that answers with a JSON envelope does.
        assertInstanceOf(
            OpenRouterExchange.KeyRejected::class.java,
            readOpenRouterExchange(403, "<html>forbidden</html>"),
        )
    }

    @Test
    fun `a refusal carries the status and the message the provider sent`() {
        val exchange = readOpenRouterExchange(429, """{"error":{"message":"Rate limit exceeded"}}""")

        assertEquals(
            "OpenRouter request failed (429): Rate limit exceeded",
            assertInstanceOf(OpenRouterExchange.Refused::class.java, exchange).message,
        )
    }

    @Test
    fun `a refusal with no readable message still names the status`() {
        // Dart's `_errorMessage` had a last line for exactly this, and losing it would leave the user
        // with a status code and nothing to do about it.
        assertEquals(
            "OpenRouter request failed (500): Unknown OpenRouter error.",
            assertInstanceOf(OpenRouterExchange.Refused::class.java, readOpenRouterExchange(500, "")).message,
        )
    }

    @Test
    fun `the answer is the first choice's content`() {
        val exchange = readOpenRouterExchange(
            200,
            """
            {"choices":[
              {"message":{"role":"assistant","content":"The first answer."}},
              {"message":{"role":"assistant","content":"A later one."}}
            ]}
            """.trimIndent(),
        )

        assertEquals("The first answer.", assertInstanceOf(OpenRouterExchange.Answered::class.java, exchange).text)
    }

    @Test
    fun `content parts are concatenated in order`() {
        // Several OpenRouter models answer `content` as a list of typed parts rather than as a
        // string, and `_contentText` joined their `text` fields with nothing between them.
        val exchange = readOpenRouterExchange(
            200,
            """
            {"choices":[{"message":{"content":[
              {"type":"text","text":"God so loved "},
              {"type":"text","text":"the world."}
            ]}}]}
            """.trimIndent(),
        )

        assertEquals(
            "God so loved the world.",
            assertInstanceOf(OpenRouterExchange.Answered::class.java, exchange).text,
        )
    }

    @Test
    fun `a part that carries no text contributes nothing`() {
        val exchange = readOpenRouterExchange(
            200,
            """
            {"choices":[{"message":{"content":[
              {"type":"reasoning","summary":"ignored"},
              {"type":"text","text":"The answer."}
            ]}}]}
            """.trimIndent(),
        )

        assertEquals("The answer.", assertInstanceOf(OpenRouterExchange.Answered::class.java, exchange).text)
    }

    @Test
    fun `a 2xx answer with nothing in it is empty rather than text`() {
        // `OpenRouter returned no response.` rather than an empty overview: the search sheet refuses
        // an empty overview and draws `overview_failed`, which is the panel that tells the user
        // something went wrong.
        assertInstanceOf(OpenRouterExchange.Empty::class.java, readOpenRouterExchange(200, ""))
        assertInstanceOf(
            OpenRouterExchange.Empty::class.java,
            readOpenRouterExchange(200, """{"choices":[{"message":{"content":""}}]}"""),
        )
        assertInstanceOf(OpenRouterExchange.Empty::class.java, readOpenRouterExchange(200, "<html>hi</html>"))
        assertInstanceOf(OpenRouterExchange.Empty::class.java, readOpenRouterExchange(200, """{"choices":[]}"""))
    }

    @Test
    fun `content that is a number is not an answer`() {
        // Dart's `message is String` skipped a number, and a number rendered as its digits would be
        // an answer the model never wrote.
        assertInstanceOf(
            OpenRouterExchange.Empty::class.java,
            readOpenRouterExchange(200, """{"choices":[{"message":{"content":42}}]}"""),
        )
    }

    @Test
    fun `every 2xx status is an answer rather than a failure`() {
        // `Response.isSuccessful` is the whole 200..299 range; reading only 200 would have turned a
        // 204 into the "request failed (204)" message the user cannot do anything about.
        assertInstanceOf(OpenRouterExchange.Empty::class.java, readOpenRouterExchange(204, ""))
        assertInstanceOf(OpenRouterExchange.Empty::class.java, readOpenRouterExchange(299, ""))
        assertInstanceOf(OpenRouterExchange.Refused::class.java, readOpenRouterExchange(199, ""))
        assertInstanceOf(OpenRouterExchange.Refused::class.java, readOpenRouterExchange(300, ""))
    }
}