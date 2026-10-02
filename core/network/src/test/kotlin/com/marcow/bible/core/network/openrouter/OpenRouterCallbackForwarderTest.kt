package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `MainActivity`'s two calls into `OpenRouterAuth`, as `AppLinks` answered them in
 * `legacy/flutter/lib/openrouter_service.dart:48`–`86`.
 *
 * `test/openrouter_oauth_test.dart` pinned the parts of a sign-in that never leave `OpenRouterAuth` —
 * the challenge, the callback's shape, the exchange. What is pinned here is the half Dart got from the
 * platform and Android still has to be handed: that a callback arriving as a launch intent and one
 * arriving as a new intent both reach the manager, that an intent which is not the redirect is not
 * mistaken for one, and that the retry after each entry point is what finishes a code the last run
 * parked.
 */
class OpenRouterCallbackForwarderTest {
    @Test
    fun `the launch callback is exchanged for a key`() = runTest {
        val forwarding = forwarding()

        val handled = forwarding.forwarder.forward("$OPENROUTER_CALLBACK_URI?code=the-code")

        assertTrue(handled)
        assertEquals("the-key", forwarding.store.read(OPENROUTER_API_KEY))
        assertEquals(listOf("the-code" to THE_VERIFIER), forwarding.exchanges)
        assertNull(forwarding.store.read(OPENROUTER_PENDING_CODE))
        assertNull(forwarding.auth.lastError.first())
    }

    @Test
    fun `a callback arriving twice is exchanged once, because Android delivers the launch intent twice`() = runTest {
        val forwarding = forwarding()
        val callback = "$OPENROUTER_CALLBACK_URI?code=the-code"

        // `onCreate`, then `onNewIntent` for the same link: a rotation or a task re-entry hands the
        // launch intent back, and a second exchange would be a second key for the same code.
        forwarding.forwarder.forward(callback)
        val second = forwarding.forwarder.forward(callback)

        assertTrue(second)
        assertEquals(listOf("the-code" to THE_VERIFIER), forwarding.exchanges)
        assertEquals("the-key", forwarding.store.read(OPENROUTER_API_KEY))
    }

    @Test
    fun `an intent that is not the redirect is left alone`() = runTest {
        val forwarding = forwarding()

        assertFalse(forwarding.forwarder.forward("bible://other/callback?code=the-code"))
        assertFalse(forwarding.forwarder.forward("https://openrouter.ai/auth?code=the-code"))
        assertTrue(forwarding.exchanges.isEmpty())
        assertNull(forwarding.auth.lastError.first())
    }

    @Test
    fun `a launch with no data still finishes the exchange the last run parked`() = runTest {
        // `getInitialLink()` was null, and the code from a run that was killed is on disk. Dart's
        // `await retryPendingExchange()` ran anyway, and this is that leg: the reader reopens the app
        // from the launcher icon and is signed in without asking again.
        val forwarding = forwarding()
        forwarding.store.write(OPENROUTER_PENDING_CODE, "the-code")

        assertFalse(forwarding.forwarder.forward(null))

        assertEquals("the-key", forwarding.store.read(OPENROUTER_API_KEY))
        assertEquals(listOf("the-code" to THE_VERIFIER), forwarding.exchanges)
    }

    @Test
    fun `a callback that reports an error is shown rather than exchanged`() = runTest {
        val forwarding = forwarding()

        // The reader backed out of the provider's page. Dart read `error_description` before `code` and
        // never posted the exchange, so no key is asked for and the panel says what happened.
        val handled = forwarding.forwarder.forward("$OPENROUTER_CALLBACK_URI?error=access_denied")

        assertTrue(handled)
        assertTrue(forwarding.exchanges.isEmpty())
        assertEquals(
            "OpenRouter login was not completed: access_denied",
            forwarding.auth.lastError.first(),
        )
    }

    @Test
    fun `a failed exchange keeps the code parked and leaves its message on the panel`() = runTest {
        val forwarding = forwarding(exchangeAnswer = { throw IllegalStateException("boom") })
        forwarding.store.write(OPENROUTER_PENDING_CODE, "the-code")

        forwarding.forwarder.forward(null)

        // Nothing throws out of the activity's callback, and the retry on the next entry point still has
        // a code and a verifier to work with — which is the difference between a retry and a sign-in
        // the reader has to start again.
        assertEquals("boom", forwarding.auth.lastError.first())
        assertEquals("the-code", forwarding.store.read(OPENROUTER_PENDING_CODE))
        assertEquals(THE_VERIFIER, forwarding.store.read(OPENROUTER_PKCE_VERIFIER))
        assertNull(forwarding.store.read(OPENROUTER_API_KEY))
        assertEquals(listOf("the-code" to THE_VERIFIER), forwarding.exchanges)
    }

    @Test
    fun `a callback with no code is reported rather than retried forever`() = runTest {
        val forwarding = forwarding()

        forwarding.forwarder.forward(OPENROUTER_CALLBACK_URI)

        assertTrue(forwarding.exchanges.isEmpty())
        assertEquals(
            "OpenRouter callback did not contain an authorization code.",
            forwarding.auth.lastError.first(),
        )
    }
}

private const val THE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

/**
 * The manager and the forwarder with a map for the store and a script for the exchange.
 *
 * The browser is not stubbed here because no test here opens one: [OpenRouterAuthorizeLauncher] exists
 * for [OpenRouterAuthManager.beginSignIn], and forwarding is what happens *after* a page was opened.
 */
private fun forwarding(exchangeAnswer: () -> String = { "the-key" }): Forwarding = Forwarding(exchangeAnswer)

/** The observable surface of a forwarded link: what was exchanged, and what the store now holds. */
private class Forwarding(exchangeAnswer: () -> String) {
    private val entries = mutableMapOf(OPENROUTER_PKCE_VERIFIER to THE_VERIFIER)

    /** `code` to `code_verifier`, one entry per exchange that was attempted. */
    val exchanges = mutableListOf<Pair<String, String>>()

    /** The store as the test reaches it, so a test can park a code itself. */
    val store: OpenRouterSecureStore = object : OpenRouterSecureStore {
        override suspend fun read(key: String): String? = entries[key]

        override suspend fun write(key: String, value: String) {
            entries[key] = value
        }

        override suspend fun delete(key: String) {
            entries.remove(key)
        }
    }

    val auth = OpenRouterAuthManager(
        store = store,
        launcher = object : OpenRouterAuthorizeLauncher {
            override fun launch(uri: String): Boolean = true
        },
        exchange = object : OpenRouterTokenExchange {
            override suspend fun exchange(code: String, codeVerifier: String, codeChallengeMethod: String): String {
                exchanges += code to codeVerifier
                return exchangeAnswer()
            }
        },
    )

    val forwarder = OpenRouterCallbackForwarder(auth)
}
