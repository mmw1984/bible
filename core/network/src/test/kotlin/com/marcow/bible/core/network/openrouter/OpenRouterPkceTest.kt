package com.marcow.bible.core.network.openrouter

import java.security.SecureRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `openrouter_oauth.dart` in `legacy/flutter/lib`, which is the piece of the sign-in that can be
 * checked without a browser or a device.
 *
 * `test/openrouter_oauth_test.dart` pinned four things: the RFC 7636 challenge, which callback URIs
 * count, which do not, and the web callback origin. The last of those is gone here — the native
 * install is only ever handed `bible://openrouter/callback`, so [isOpenRouterCallback] has no web
 * leg to get wrong — and the three others are pinned below. The loopback leg is kept because it is
 * the same classifier Dart had, and a deep link arriving from a desktop debug session takes it.
 */
class OpenRouterPkceTest {
    @Test
    fun `creates the RFC 7636 S256 challenge`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", openRouterPkceChallenge(verifier))
    }

    @Test
    fun `the challenge is unpadded base64url of the digest`() {
        // 32 digest bytes encode to 44 base64 characters, the last of which is the padding Dart
        // stripped and Java's encoder never added, so the two agree on 43.
        val challenge = openRouterPkceChallenge(VERIFIER)

        assertEquals(43, challenge.length)
        assertFalse(challenge.contains('='), "the challenge must not be padded: $challenge")
        assertTrue(challenge.none { it == '+' || it == '/' }, "the challenge must be base64url: $challenge")
    }

    @Test
    fun `a verifier is 64 random bytes as unpadded base64url`() {
        // `_randomVerifier()`: 64 bytes from `Random.secure()`, then `base64Url` without padding.
        val verifier = openRouterPkceVerifier(FixedSecureRandom())

        assertEquals(86, verifier.length)
        assertFalse(verifier.contains('='), "the verifier must not be padded: $verifier")
        assertTrue(verifier.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'-9' || it == '-' || it == '_' })
    }

    @Test
    fun `a verifier is not a constant`() {
        // A verifier that repeated would let a second sign-in reuse the first one's challenge.
        assertNotEquals(openRouterPkceVerifier(), openRouterPkceVerifier())
    }

    @Test
    fun `the authorize URI carries the callback, the challenge and S256, in Dart's order`() {
        val uri = openRouterAuthorizeUri("challenge-value")

        assertEquals(
            "https://openrouter.ai/auth?callback_url=bible%3A%2F%2Fopenrouter%2Fcallback" +
                "&code_challenge=challenge-value&code_challenge_method=S256",
            uri,
        )
    }

    @Test
    fun `the callback the provider is sent is the one the intent-filter claims`() {
        assertEquals("bible://openrouter/callback", OPENROUTER_CALLBACK_URI)
        assertEquals("S256", OPENROUTER_PKCE_METHOD)
    }

    @Test
    fun `accepts only the exact native callback path`() {
        assertTrue(isOpenRouterCallback("bible://openrouter/callback?code=returned-code"))
        assertFalse(isOpenRouterCallback("bible://openrouter/other?code=returned-code"))
    }

    @Test
    fun `accepts the loopback leg of the same sign in`() {
        assertTrue(isOpenRouterCallback("http://127.0.0.1:54123/callback?code=returned-code"))
        assertTrue(isOpenRouterCallback("http://localhost/callback?code=returned-code"))
        assertFalse(isOpenRouterCallback("http://127.0.0.1:54123/other?code=returned-code"))
    }

    @Test
    fun `rejects codes from another origin`() {
        assertFalse(isOpenRouterCallback("https://example.com/callback?code=returned-code"))
        assertFalse(isOpenRouterCallback("bible://elsewhere/callback?code=returned-code"))
        // Neither shape the Dart web build recognised: there is no web build here.
        assertFalse(isOpenRouterCallback("http://127.0.0.1:54123/?oauth=openrouter&code=returned-code"))
        assertFalse(isOpenRouterCallback("http://127.0.0.1:54123/?code=returned-code"))
    }

    @Test
    fun `rejects what is not a URI at all`() {
        assertFalse(isOpenRouterCallback("returned-code"))
        assertFalse(isOpenRouterCallback("bible:openrouter/callback"))
        assertFalse(isOpenRouterCallback("://openrouter/callback"))
        assertFalse(isOpenRouterCallback("bible:///callback"))
        assertFalse(isOpenRouterCallback(""))
    }

    @Test
    fun `reads the code out of the callback`() {
        val parsed = OpenRouterCallbackUri.parse("bible://openrouter/callback?code=abc123")

        assertEquals("abc123", parsed?.code)
        assertNull(parsed?.error)
        assertTrue(parsed?.isAppLink == true)
    }

    @Test
    fun `an error outranks the code, because the provider sends both when it refuses`() {
        // `_handleLink` reads `error_description ?? error` first and returns without exchanging.
        val described = OpenRouterCallbackUri.parse(
            "bible://openrouter/callback?error=access_denied&error_description=User%20said%20no&code=abc123",
        )
        val plain = OpenRouterCallbackUri.parse(
            "bible://openrouter/callback?error=access_denied&code=abc123",
        )

        assertEquals("User said no", described?.error)
        assertEquals("access_denied", plain?.error)
    }

    @Test
    fun `an empty query value is no value, which is what _handleLink rejected`() {
        // `code == null || code.isEmpty` and `callbackError.isNotEmpty` are both Dart guards, so an
        // empty `code=` must read as absent rather than as the empty string being a valid code.
        val emptyCode = OpenRouterCallbackUri.parse("bible://openrouter/callback?code=")
        val emptyError = OpenRouterCallbackUri.parse("bible://openrouter/callback?error=&code=abc123")

        assertNull(emptyCode?.code)
        assertNull(emptyError?.error)
    }

    @Test
    fun `parses the query the way Dart's queryParameters did`() {
        val parsed = OpenRouterCallbackUri.parse(
            "bible://openrouter/callback?error_description=too+many+requests&flag&code=a%2Fb",
        )

        assertEquals("too many requests", parsed?.query?.get("error_description"))
        assertEquals("", parsed?.query?.get("flag"))
        assertEquals("a/b", parsed?.query?.get("code"))
    }

    @Test
    fun `the scheme and host are case folded, the way a URI parser folds them`() {
        val parsed = OpenRouterCallbackUri.parse("BIBLE://OpenRouter/Callback?code=abc123")

        assertEquals("bible", parsed?.scheme)
        assertEquals("openrouter", parsed?.host)
        assertEquals("/Callback", parsed?.path)
        assertTrue(parsed?.isAppLink == true)
    }

    private companion object {
        const val VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    }
}

/**
 * A [SecureRandom] that hands back a known sequence, so the verifier a test sees is a fixed string
 * rather than a shape. Dart's test did not need this because it pinned the challenge of a verifier it
 * wrote out itself; here the verifier is generated inside `openRouterPkceVerifier`, so the only way
 * to pin its bytes is to pin the source they come from.
 */
private class FixedSecureRandom : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        for (index in bytes.indices) bytes[index] = index.toByte()
    }
}
