package com.marcow.bible.core.network.openrouter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which OpenRouter model a request goes to.
 *
 * The model id is a user setting: `AiSettingsStore.write('openrouter_model', …)` in
 * `legacy/flutter/lib/ai_service.dart` kept it in `flutter_secure_storage`, and `NATIVE_PLAN.md` §5
 * Phase 4 item 8 is the point where the native build reads it back. It lives in the same secure store
 * as the four OAuth keys because that is where Flutter kept it, and a setting that has to survive a
 * reinstall-less upgrade next to the key that authorises it is easier to reason about in one file.
 */
interface OpenRouterModelId {
    /** The `model` field of the request body, e.g. `openrouter/free`. */
    suspend fun modelId(): String
}

/**
 * The model the chat asks for: whatever the user saved, otherwise `openrouter/free`.
 *
 * `String modelId = 'openrouter/free'` was the Flutter default and `_settings.read('openrouter_model')
 * ?? 'openrouter/free'` the read, so a fresh install asks the free router exactly as it did.
 */
@Singleton
class StoredOpenRouterModelId @Inject constructor(private val store: OpenRouterSecureStore) : OpenRouterModelId {
    override suspend fun modelId(): String =
        store.read(OPENROUTER_MODEL)?.takeIf { it.isNotBlank() } ?: FREE_ROUTER_MODEL_ID
}

/** The key `openrouter_model` was filed under, in the same store as the OAuth secrets. */
const val OPENROUTER_MODEL = "openrouter_model"

/** `'openrouter/free'`, the router's default and the Flutter build's default. */
const val FREE_ROUTER_MODEL_ID = "openrouter/free"
