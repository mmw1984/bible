package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the OpenRouter API key comes from.
 *
 * The search use cases never see a key: they ask for one, and [OpenRouterChatClient] turns "no key"
 * into [OpenRouterException.LoginRequired]. Phase 4 replaces the implementation with the PKCE
 * sign-in in `OpenRouterAuthManager` (`NATIVE_PLAN.md` §5 Phase 4 item 1); the seam is here now so
 * the search feature does not have to move when that arrives.
 */
interface OpenRouterSession {
    /**
     * Whether a key is held, for the UI to watch.
     *
     * `BibleAiController.openRouterSignedIn` in `legacy/flutter/lib/ai_service.dart` was a
     * `ChangeNotifier` field, and the search sheet answered it twice: `requiresLogin` decided whether
     * to draw the `login_to_search` panel instead of results, and the sheet's listener re-ran the AI
     * search the moment a sign-in landed (`pendingCloudSearch`). Both are reactions to a *change*, so
     * the value is a flow here rather than something each caller polls for.
     *
     * `apiKey()` stays the answer to "what do I send", because a caller about to make a request
     * wants the key rather than a yes/no about it.
     */
    val signedIn: StateFlow<Boolean>

    /** The key to authenticate with, or null when nobody is signed in. */
    suspend fun apiKey(): String?

    /**
     * Drops the stored key.
     *
     * `_send` in `legacy/flutter/lib/openrouter_service.dart` signs out when the provider answers
     * 401 or 403, so a revoked key cannot wedge the app in a state where every retry fails the same
     * way.
     */
    suspend fun signOut()
}

/**
 * The session before Phase 4: no key, so every AI request fails with
 * [OpenRouterException.LoginRequired] and the sheet shows its sign-in panel.
 *
 * Text search is unaffected — it never asks for a key — which is the same split the Flutter build
 * had, where `login_to_search` promises that "Text search works without signing in".
 *
 * `OpenRouterModule` binds [OpenRouterAuthManager] now that the sign-in exists; this stays as the null
 * object a test can bind when it wants the signed-out answers without a store.
 */
@Singleton
class SignedOutOpenRouterSession @Inject constructor() : OpenRouterSession {
    private val state = MutableStateFlow(false)

    override val signedIn: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun apiKey(): String? = null

    override suspend fun signOut() = Unit
}
