package com.marcow.bible.core.network.openrouter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which OpenRouter model a request goes to.
 *
 * The model id is a user setting, but the setting lives in `flutter_secure_storage` and the
 * sign-in that gives the store a key is Phase 4 (`NATIVE_PLAN.md` §5 Phase 4 items 1 and 8). This
 * seam is where that read lands, for the same reason [OpenRouterSession] is one: the search feature
 * asks for a model and never learns where the answer came from, so Phase 4 swaps the
 * implementation without touching a use case.
 */
interface OpenRouterModelId {
    /** The `model` field of the request body, e.g. `openrouter/free`. */
    suspend fun modelId(): String
}

/**
 * The model the search asks for before Phase 4: `openrouter/free`.
 *
 * That is the `String modelId = 'openrouter/free'` default of `BibleAiController` in
 * `legacy/flutter/lib/ai_service.dart:294`, kept here rather than written at the call site so the
 * one place that changes in Phase 4 is the same place the Flutter build changed it. Reading a
 * stored model id needs the secure store, which needs a sign-in, so a fresh Phase 3 install and the
 * Flutter build's fresh install behave the same way here.
 */
@Singleton
class DefaultOpenRouterModelId @Inject constructor() : OpenRouterModelId {
    override suspend fun modelId(): String = FREE_ROUTER_MODEL_ID
}

/** `'openrouter/free'`, the router's default and the Flutter build's default. */
const val FREE_ROUTER_MODEL_ID = "openrouter/free"