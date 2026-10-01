package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The OpenRouter sign-in, replacing `OpenRouterAuth` in `legacy/flutter/lib/openrouter_service.dart`.
 *
 * It is the [OpenRouterSession] the search sheet and the chat both hold, so a signed-in key reaches a
 * request without either of them learning where it came from. The four steps are Dart's, in Dart's
 * order:
 *
 *  1. [beginSignIn] makes a PKCE verifier, parks it with its method, and opens the authorize page.
 *  2. [handleCallback] takes the `bible://openrouter/callback` deep link `MainActivity` is given and
 *     parks the code.
 *  3. [retryPendingExchange] trades code plus verifier for the API key and stores it.
 *  4. [signOut] drops all four keys.
 *
 * Step 3 runs off the parked code rather than off the deep link itself, which is the reason
 * [OPENROUTER_PENDING_CODE] exists: the browser handing the link back is not the same moment as the app
 * being in the foreground, so the code is written to disk first and the exchange is retried on the next
 * [initialize]. `NATIVE_PLAN.md` §4.7 keeps that.
 *
 * Two deliberate differences from the Dart, both about being a `StateFlow` rather than a
 * `ChangeNotifier`:
 *
 *  - Dart guarded the link and the exchange with `??=` fields, so a second callback arriving while the
 *    first was in flight joined the first one's future. The [Mutex]es below serialise instead, which
 *    reaches the same place: [handledCodes] drops the second copy of a code and the pending exchange
 *    reads one code from the store.
 *  - Dart's `onChanged?.call()` is [signedIn] and [lastError] being read; nothing polls.
 */
@Singleton
class OpenRouterAuthManager @Inject constructor(
    private val store: OpenRouterSecureStore,
    private val launcher: OpenRouterAuthorizeLauncher,
    private val exchange: OpenRouterTokenExchange,
) : OpenRouterSession {
    private val signedInState = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)

    /**
     * The codes already exchanged, `_handledCodes`.
     *
     * Android delivers `onNewIntent` for a deep link that is also the activity's launch intent, so the
     * same code can arrive twice; without this set the second arrival would find the pending code
     * already gone and the store would be read twice for nothing.
     */
    private val handledCodes = mutableSetOf<String>()

    private val linkLock = Mutex()
    private val exchangeLock = Mutex()

    override val signedIn: StateFlow<Boolean> = signedInState.asStateFlow()

    /** `_lastError`: what the chat's error panel shows, or null when there is nothing wrong. */
    val lastError: StateFlow<String?> = errorState.asStateFlow()

    /**
     * `initialize()`: read what is already stored, then finish any exchange the last run left parked.
     *
     * The failing retry is swallowed, as it was in Dart: "A failed exchange remains pending and can be
     * retried without blocking AI", and the reason is left in [lastError] for the panel to show.
     */
    suspend fun initialize() {
        publishSignedIn()
        try {
            retryPendingExchange()
        } catch (_: Exception) {
            // The pending code stays where it is, so the next initialize or a manual retry picks it up.
        }
    }

    /** `beginSignIn()`: a fresh verifier, a cleared parked code, and the authorize page in a Custom Tab. */
    suspend fun beginSignIn() {
        store.delete(OPENROUTER_PENDING_CODE)
        val verifier = openRouterPkceVerifier()
        store.write(OPENROUTER_PKCE_VERIFIER, verifier)
        store.write(OPENROUTER_PKCE_METHOD_KEY, OPENROUTER_PKCE_METHOD)
        errorState.value = null
        if (!launcher.launch(openRouterAuthorizeUri(openRouterPkceChallenge(verifier)))) {
            throw IllegalStateException(LAUNCH_FAILED_MESSAGE)
        }
    }

    /**
     * The `onNewIntent` leg: `bible://openrouter/callback?code=…`, or the error the provider sends
     * instead when the user backs out.
     *
     * A URI that is not the callback is ignored rather than refused, because `MainActivity` is also
     * the launcher activity and hands this whatever intent it was started or resumed with.
     */
    suspend fun handleCallback(uri: String) {
        if (!isOpenRouterCallback(uri)) return
        linkLock.withLock { handleLink(uri) }
    }

    /** `retryPendingExchange()`: the same trade Dart retried on every entry to the app. */
    suspend fun retryPendingExchange() {
        exchangeLock.withLock { performPendingExchange() }
    }

    override suspend fun apiKey(): String? = store.read(OPENROUTER_API_KEY)

    /**
     * `signOut()`: the API key, the verifier, its method and the parked code all go.
     *
     * The other three are dropped as well as the key because they are worthless without it, and
     * leaving a verifier behind would let a later sign-in offer a stale challenge.
     */
    override suspend fun signOut() {
        store.delete(OPENROUTER_API_KEY)
        store.delete(OPENROUTER_PKCE_VERIFIER)
        store.delete(OPENROUTER_PKCE_METHOD_KEY)
        store.delete(OPENROUTER_PENDING_CODE)
        handledCodes.clear()
        errorState.value = null
        publishSignedIn()
    }

    /**
     * `setModel()`: the user's OpenRouter model id, stored under `openrouter_model` as Flutter did.
     *
     * A blank value is dropped rather than stored, so an emptied text field cannot leave the app with no
     * model to ask.
     */
    suspend fun setModel(model: String) {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) return
        store.write(OPENROUTER_MODEL, trimmed)
    }

    /** `_handleLink(uri)`. */
    private suspend fun handleLink(uri: String) {
        val parsed = OpenRouterCallbackUri.parse(uri) ?: return
        val callbackError = parsed.error
        if (callbackError != null) {
            errorState.value = "OpenRouter login was not completed: $callbackError"
            return
        }
        val code = parsed.code
        if (code == null) {
            errorState.value = MISSING_CODE_MESSAGE
            return
        }
        if (!handledCodes.add(code)) return
        store.write(OPENROUTER_PENDING_CODE, code)
        retryPendingExchange()
    }

    /** `_performPendingExchange()`. */
    private suspend fun performPendingExchange() {
        val code = store.read(OPENROUTER_PENDING_CODE) ?: return
        val verifier = store.read(OPENROUTER_PKCE_VERIFIER)
        if (verifier.isNullOrEmpty()) {
            errorState.value = MISSING_VERIFIER_MESSAGE
            return
        }
        val method = store.read(OPENROUTER_PKCE_METHOD_KEY) ?: OPENROUTER_PKCE_METHOD
        try {
            val key = exchange.exchange(code = code, codeVerifier = verifier, codeChallengeMethod = method)
            store.write(OPENROUTER_API_KEY, key)
            store.delete(OPENROUTER_PKCE_VERIFIER)
            store.delete(OPENROUTER_PKCE_METHOD_KEY)
            store.delete(OPENROUTER_PENDING_CODE)
            errorState.value = null
            publishSignedIn()
        } catch (failure: Exception) {
            // The parked code and the verifier both stay, so this is retried rather than restarted.
            errorState.value = failure.message ?: failure.toString()
            throw failure
        }
    }

    /** Reads the stored key into [signedIn], which is what `isSignedIn` answered by reading the store. */
    private suspend fun publishSignedIn() {
        signedInState.value = !store.read(OPENROUTER_API_KEY).isNullOrEmpty()
    }
}

/** `StateError('Could not open OpenRouter sign in.')`. */
private const val LAUNCH_FAILED_MESSAGE = "Could not open OpenRouter sign in."

/** `'OpenRouter callback did not contain an authorization code.'`. */
private const val MISSING_CODE_MESSAGE = "OpenRouter callback did not contain an authorization code."

/** `'OpenRouter PKCE verifier is missing. Please sign in again.'`. */
private const val MISSING_VERIFIER_MESSAGE = "OpenRouter PKCE verifier is missing. Please sign in again."
