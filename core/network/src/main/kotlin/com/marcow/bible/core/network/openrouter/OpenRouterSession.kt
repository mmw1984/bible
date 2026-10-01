package com.marcow.bible.core.network.openrouter

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
 */
@Singleton
class SignedOutOpenRouterSession @Inject constructor() : OpenRouterSession {
    override suspend fun apiKey(): String? = null

    override suspend fun signOut() = Unit
}
