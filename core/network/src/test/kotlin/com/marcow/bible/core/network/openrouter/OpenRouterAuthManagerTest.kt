package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `OpenRouterAuth` in `legacy/flutter/lib/openrouter_service.dart`, with the browser and the socket
 * replaced by the two ports the manager takes.
 *
 * `test/openrouter_service_test.dart` covered the happy path, a refused callback, a callback with no
 * code and a repeated code. What is pinned here as well are the four steps that decide whether a user
 * ends up signed in at all:
 *
 *  - the parked code survives the app being killed, so `initialize` finishes the exchange on the next
 *    run rather than demanding a second sign-in,
 *  - a verifier that never made it to disk reports the re-sign-in message instead of exchanging with an
 *    empty one,
 *  - a failed exchange leaves the code and the verifier parked, so it is retried rather than restarted,
 *  - signing out drops the verifier too, because a verifier without a key is worthless and a stale one
 *    would offer the next sign-in a challenge it never created.
 */
class OpenRouterAuthManagerTest {
    @Test
    fun `begin sign in parks a verifier and opens the authorize page`() = runTest {
        val signIn = signIn()

        signIn.auth.beginSignIn()

        val verifier = signIn.store.read(OPENROUTER_PKCE_VERIFIER)
        assertTrue(!verifier.isNullOrEmpty(), "a verifier must be parked before the browser opens")
        assertEquals(OPENROUTER_PKCE_METHOD, signIn.store.read(OPENROUTER_PKCE_METHOD_KEY))
        // The challenge is the verifier's SHA-256, not the verifier itself: the page has to be able to
        // check the exchange without ever having seen what is sent back.
        assertEquals(
            listOf(
                "https://openrouter.ai/auth?callback_url=bible%3A%2F%2Fopenrouter%2Fcallback" +
                    "&code_challenge=${openRouterPkceChallenge(requireNotNull(verifier))}" +
                    "&code_challenge_method=S256",
            ),
            signIn.launched,
        )
    }

    @Test
    fun `a callback exchanges the code for a key and clears the parking`() = runTest {
        val signIn = signIn()
        signIn.auth.beginSignIn()

        signIn.auth.handleCallback("bible://openrouter/callback?code=the-code")

        assertEquals("the-key", signIn.store.read(OPENROUTER_API_KEY))
        assertEquals(listOf("the-code" to THE_VERIFIER), signIn.exchanges)
        assertNull(signIn.store.read(OPENROUTER_PENDING_CODE))
        assertNull(signIn.store.read(OPENROUTER_PKCE_VERIFIER))
        assertNull(signIn.store.read(OPENROUTER_PKCE_METHOD_KEY))
        assertTrue(signIn.auth.signedIn.first())
        assertNull(signIn.auth.lastError.first())
    }

    @Test
    fun `an initialize finishes an exchange the last run left parked`() = runTest {
        // The app was killed between the deep link and the exchange, which is what the parked code is
        // for: Dart retried it on every `initialize`, and so does this.
        val signIn = signIn()
        signIn.store.write(OPENROUTER_PENDING_CODE, "the-code")

        signIn.auth.initialize()

        assertEquals("the-key", signIn.store.read(OPENROUTER_API_KEY))
        assertTrue(signIn.auth.signedIn.first())
    }

    @Test
    fun `a parked code with no verifier asks for a sign in again`() = runTest {
        val signIn = signIn(verifier = null)
        signIn.store.write(OPENROUTER_PENDING_CODE, "the-code")

        signIn.auth.initialize()

        assertNull(signIn.store.read(OPENROUTER_API_KEY))
        assertEquals("OpenRouter PKCE verifier is missing. Please sign in again.", signIn.error())
        assertTrue(signIn.exchanges.isEmpty(), "an exchange with no verifier must not be attempted")
    }

    @Test
    fun `a failed exchange leaves the code parked so it can be retried`() = runTest {
        val signIn = signIn(key = null)
        signIn.store.write(OPENROUTER_PENDING_CODE, "the-code")

        // `initialize` swallows the failure, as Dart did, so the throw is pinned on the retry the user
        // or the next run actually reaches for.
        signIn.auth.initialize()
        assertThrows<IllegalStateException> { signIn.auth.retryPendingExchange() }

        assertEquals("the-code", signIn.store.read(OPENROUTER_PENDING_CODE))
        assertEquals(THE_VERIFIER, signIn.store.read(OPENROUTER_PKCE_VERIFIER))
        assertEquals("OpenRouter returned no API key.", signIn.error())
    }

    @Test
    fun `a refused callback reports the reason and exchanges nothing`() = runTest {
        val signIn = signIn()

        signIn.auth.handleCallback("bible://openrouter/callback?error_description=User%20said%20no")

        assertEquals("OpenRouter login was not completed: User said no", signIn.error())
        assertTrue(signIn.exchanges.isEmpty(), "a refusal must not be exchanged")
        assertNull(signIn.store.read(OPENROUTER_PENDING_CODE))
    }

    @Test
    fun `a callback with neither code nor error reports the missing code`() = runTest {
        val signIn = signIn()

        signIn.auth.handleCallback("bible://openrouter/callback")

        assertEquals("OpenRouter callback did not contain an authorization code.", signIn.error())
        assertTrue(signIn.exchanges.isEmpty())
    }

    @Test
    fun `a callback that is not ours is ignored`() = runTest {
        // `MainActivity` is also the launcher, so this is handed whatever intent the app was started with.
        val signIn = signIn()

        signIn.auth.handleCallback("bible://something-else/callback?code=the-code")
        signIn.auth.handleCallback("https://openrouter.ai/settings")

        assertNull(signIn.error())
        assertTrue(signIn.exchanges.isEmpty())
    }

    @Test
    fun `the same code arriving twice exchanges once`() = runTest {
        // Android delivers the launch intent again as `onNewIntent`, so one link can be seen twice.
        val signIn = signIn()
        signIn.auth.beginSignIn()

        signIn.auth.handleCallback("bible://openrouter/callback?code=the-code")
        signIn.auth.handleCallback("bible://openrouter/callback?code=the-code")

        assertEquals(1, signIn.exchanges.size)
    }

    @Test
    fun `sign out drops the key, the verifier and the parked code`() = runTest {
        val signIn = signIn()
        signIn.store.write(OPENROUTER_API_KEY, "the-key")
        signIn.store.write(OPENROUTER_PENDING_CODE, "the-code")
        signIn.auth.initialize()

        signIn.auth.signOut()

        assertNull(signIn.store.read(OPENROUTER_API_KEY))
        assertNull(signIn.store.read(OPENROUTER_PKCE_VERIFIER))
        assertNull(signIn.store.read(OPENROUTER_PKCE_METHOD_KEY))
        assertNull(signIn.store.read(OPENROUTER_PENDING_CODE))
        assertFalse(signIn.auth.signedIn.first())
    }

    @Test
    fun `a browser that cannot be opened fails the sign in`() = runTest {
        val signIn = signIn(launchable = false)

        val failure = assertThrows<IllegalStateException> { signIn.auth.beginSignIn() }

        assertEquals("Could not open OpenRouter sign in.", failure.message)
    }

    @Test
    fun `the model id is the saved one, and the free router when there is none`() = runTest {
        val signIn = signIn()
        val modelId = StoredOpenRouterModelId(signIn.store)

        assertEquals(FREE_ROUTER_MODEL_ID, modelId.modelId())

        signIn.auth.setModel("  anthropic/claude-sonnet-4  ")
        assertEquals("anthropic/claude-sonnet-4", modelId.modelId())

        // An emptied field is dropped rather than stored, so the app keeps asking the free router.
        signIn.auth.setModel("   ")
        assertEquals("anthropic/claude-sonnet-4", modelId.modelId())
    }
}

private const val THE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

/** What the manager said most recently, which is what the chat's error panel would show. */
private suspend fun SignIn.error(): String? = auth.lastError.first()

private fun signIn(verifier: String? = THE_VERIFIER, key: String? = "the-key", launchable: Boolean = true): SignIn =
    SignIn(verifier = verifier, key = key, launchable = launchable)

/**
 * The manager with all three collaborators hand-written, so a sign-in runs without a browser, a socket
 * or Android.
 *
 * The store is a map and the two ports are lists of what they were asked for, which is the whole
 * observable surface of a sign-in: nothing else about the manager is visible from outside it.
 */
private class SignIn(verifier: String?, private val key: String?, private val launchable: Boolean) {
    private val entries = mutableMapOf<String, String>()

    /** Every authorize URI a Custom Tab was asked for, in order. */
    val launched = mutableListOf<String>()

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
            override fun launch(uri: String): Boolean {
                launched += uri
                return launchable
            }
        },
        exchange = object : OpenRouterTokenExchange {
            override suspend fun exchange(code: String, codeVerifier: String, codeChallengeMethod: String): String {
                exchanges += code to codeVerifier
                return key ?: error("OpenRouter returned no API key.")
            }
        },
    )

    init {
        if (verifier != null) entries[OPENROUTER_PKCE_VERIFIER] = verifier
    }
}
