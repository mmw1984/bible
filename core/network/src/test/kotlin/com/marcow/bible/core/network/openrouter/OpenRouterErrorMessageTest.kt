package com.marcow.bible.core.network.openrouter

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `_errorMessage` in `legacy/flutter/lib/openrouter_service.dart`, which is what a failed search
 * reports.
 *
 * Dart's version is private to the client, and `test/openrouter_service_test.dart` only ever saw it
 * through a thrown `StateError`, so the shapes it accepts are pinned here instead: the `error`
 * envelope OpenRouter answers with, the bare string body, and the three things Dart refused to call
 * a message. That last part is the one worth writing down — `message is String` skipped a number,
 * and reporting `402` as an error text is a message the Flutter build never showed.
 */
class OpenRouterErrorMessageTest {
    @Test
    fun `the error envelope is unwrapped to the message inside it`() {
        // The payload `test/openrouter_service_test.dart` streamed to make a search report an error.
        val payload = json("""{"error":{"message":"Selected model exhausted its token budget"}}""")

        assertEquals("Selected model exhausted its token budget", openRouterErrorMessage(payload))
    }

    @Test
    fun `envelopes nest as deep as the provider nests them`() {
        val payload = json("""{"error":{"error":{"message":"no free model"}}}""")

        assertEquals("no free model", openRouterErrorMessage(payload))
    }

    @Test
    fun `a body that is the message itself is the message`() {
        val payload = json("\"Selected model is not available\"")

        assertEquals("Selected model is not available", openRouterErrorMessage(payload))
    }

    @Test
    fun `a message is trimmed, because the provider indents it`() {
        val payload = json("""{"message":"  Provider returned an error  "}""")

        assertEquals("Provider returned an error", openRouterErrorMessage(payload))
    }

    @Test
    fun `a message that is only whitespace is no message`() {
        val payload = json("""{"message":"   "}""")

        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(payload))
    }

    @Test
    fun `a number is not a message`() {
        // `value is String` in Dart, so a provider answering `{"message":402}` reported nothing.
        val payload = json("""{"message":402}""")

        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(payload))
    }

    @Test
    fun `an envelope with nothing but a code is no message`() {
        val payload = json("""{"error":{"code":402,"metadata":{"raw":"provider said no"}}}""")

        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(payload))
    }

    @Test
    fun `a body that is neither an object nor a string is unknown`() {
        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(json("""["nope"]""")))
        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(json("{}")))
        assertEquals(UNKNOWN_ERROR, openRouterErrorMessage(null))
    }

    private fun json(raw: String) = Json.parseToJsonElement(raw)
}

/** `_errorMessage`'s last line, spelled out here because it is the text the sheet ends up showing. */
private const val UNKNOWN_ERROR = "Unknown OpenRouter error."
