package com.marcow.bible.core.network.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `ai_provider` setting, whose default is a requirement rather than a convenience.
 *
 * `NATIVE_PLAN.md` §4 Phase 4 says the setting falls back to OpenRouter so that the existing OpenRouter
 * users' behaviour is unchanged: the setting is new, so every install that has not chosen has no value
 * for it, and the users who already signed in to OpenRouter must keep the provider they signed in to. A
 * default of Gemini Nano would move them off the key they had and onto a model that may not run on
 * their phone.
 */
class AiProviderIdTest {
    @Test
    fun `no stored value means OpenRouter`() {
        assertEquals(AiProviderId.OpenRouter, AiProviderId.fromStorage(null))
        assertEquals(AiProviderId.OpenRouter, AiProviderId.fromStorage(""))
    }

    @Test
    fun `a value this build does not know falls back rather than failing`() {
        // A downgrade must not leave the chat with no provider to ask.
        assertEquals(AiProviderId.OpenRouter, AiProviderId.fromStorage("some_future_provider"))
    }

    @Test
    fun `each provider round-trips through its stored value`() {
        AiProviderId.entries.forEach { provider ->
            assertEquals(provider, AiProviderId.fromStorage(provider.storageValue))
        }
    }

    @Test
    fun `OpenRouter is the only provider with a web search`() {
        // `NATIVE_PLAN.md` §4 Phase 4: Gemini Nano has no web tool, so the settings screen and the chat
        // hide the affordance rather than offering a search that returns nothing.
        assertTrue(AiProviderId.OpenRouter.supportsWebSearch)
        assertFalse(AiProviderId.GeminiNano.supportsWebSearch)
    }
}
