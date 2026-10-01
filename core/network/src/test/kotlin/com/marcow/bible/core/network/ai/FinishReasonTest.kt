package com.marcow.bible.core.network.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `choices[0].finish_reason`, and the one branch of it the chat actually reacts to.
 *
 * §4.2 says a `length` sets `AiMessage.incomplete` and the UI shows 「回覆已停止或尚未完整」, which is the
 * same thing the `[[MORE]]` marker in the chat prompt asks the model to emit. Every other reason is a
 * complete answer: Dart did not treat `content_filter` as a failure either, it published whatever text
 * came back with it.
 */
class FinishReasonTest {
    @Test
    fun `the token ceiling is the only reason that leaves an answer incomplete`() {
        assertTrue(FinishReason.LENGTH.incomplete)
        FinishReason.entries.filter { it != FinishReason.LENGTH }.forEach { reason ->
            assertFalse(reason.incomplete, "'${reason.wireValue}' must not mark an answer incomplete")
        }
    }

    @Test
    fun `a reason this build has not heard of is not a broken answer`() {
        assertEquals(FinishReason.UNKNOWN, FinishReason.of("some_new_reason"))
        assertEquals(FinishReason.UNKNOWN, FinishReason.of(null))
    }

    @Test
    fun `the four reasons a provider sends are read back`() {
        assertEquals(FinishReason.STOP, FinishReason.of("stop"))
        assertEquals(FinishReason.LENGTH, FinishReason.of("length"))
        assertEquals(FinishReason.TOOL_CALLS, FinishReason.of("tool_calls"))
        assertEquals(FinishReason.CONTENT_FILTER, FinishReason.of("content_filter"))
    }
}
