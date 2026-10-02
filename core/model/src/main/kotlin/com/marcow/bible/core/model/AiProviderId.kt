package com.marcow.bible.core.model

/**
 * Which provider Bible AI answers through, the `ai_provider` setting of `NATIVE_PLAN.md` §4 Phase 4.
 *
 * It lives here rather than beside `AiProvider` in `core/network/ai` for the same reason
 * `AppLocale.aiLanguage` does: the value is a *setting*, so the screen that renders the selector, the
 * screen that shows the sign-in and every screen that asks a question all have to be able to name it,
 * and the feature modules that must not depend on `core/network` still can. `core/model` is the one
 * module every one of them already has.
 *
 * [fromStorage] defaults to [OpenRouter] on a missing or unrecognised value, and that default is a
 * requirement rather than a convenience: the setting is new, so every existing install has no value
 * for it, and the users who already signed in to OpenRouter must keep the provider they signed in
 * to. An on-device default would silently move them off the key they had.
 */
enum class AiProviderId(val storageValue: String) {
    /** The default: needs a sign-in, and is the only one with web search. */
    OpenRouter("openrouter"),

    /** On-device via Play services for AI Edge; no sign-in, and gated on the hardware. */
    GeminiNano("gemini_nano"),
    ;

    /**
     * Whether this provider can answer a web search at all, which the settings screen asks.
     *
     * §4 Phase 4 asks for the difference to be settled here rather than leaking into the UI: a
     * provider with no web tool walks the no-tool path directly and the chat hides the affordance.
     */
    val supportsWebSearch: Boolean
        get() = this == OpenRouter

    companion object {
        /** Reads `ai_provider`, falling back to [OpenRouter] exactly as §4 Phase 4 requires. */
        fun fromStorage(value: String?): AiProviderId = entries.firstOrNull { it.storageValue == value } ?: OpenRouter
    }
}