package com.marcow.bible.feature.aichat.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `recent` block of `_chatPrompt`, and the two rules that decide which turns are in it.
 *
 * It is the whole of the conversation history the model is given, so what the block leaves out is as
 * much a part of the answer as what it keeps: the turn being answered is stated separately at the end
 * of the prompt and must not be repeated here, and a long conversation has to keep the turns that are
 * about it rather than the ones that started it.
 */
internal class RecentConversationTest {
    @Test
    fun `each turn is a role-prefixed line`() {
        val messages = listOf(
            AiMessage(role = AiMessageRole.USER, text = "神是愛嗎？"),
            AiMessage(role = AiMessageRole.ASSISTANT, text = "是。"),
        )

        assertEquals("user: 神是愛嗎？\nassistant: 是。", recentConversationBlock(messages, question = "再問"))
    }

    @Test
    fun `the turn being answered is not repeated at the end of the block`() {
        // `send` appends the question before the prompt is built, and the prompt states the question
        // again under `User question:`. Counting it here would spend one of the twelve slots saying
        // the same thing twice.
        val messages = listOf(
            AiMessage(role = AiMessageRole.ASSISTANT, text = "先前的答案"),
            AiMessage(role = AiMessageRole.USER, text = "約翰福音三章十六節？"),
        )

        assertEquals("assistant: 先前的答案", recentConversationBlock(messages, "約翰福音三章十六節？"))
    }

    @Test
    fun `a last turn that is not the question is history, not a repeat`() {
        // The block is also built for a regeneration, where the last turn is the answer being redone.
        val messages = listOf(
            AiMessage(role = AiMessageRole.USER, text = "神是愛嗎？"),
            AiMessage(role = AiMessageRole.ASSISTANT, text = "是。"),
        )

        assertEquals("user: 神是愛嗎？\nassistant: 是。", recentConversationBlock(messages, "神是愛嗎？"))
    }

    @Test
    fun `a last user turn whose text differs from the question is kept`() {
        // Dart compared the text rather than the position, so an edit-then-ask is two turns of history
        // and both are told to the model.
        val messages = listOf(
            AiMessage(role = AiMessageRole.USER, text = "先前的問題"),
            AiMessage(role = AiMessageRole.USER, text = "改寫後的問題"),
        )

        assertEquals("user: 先前的問題\nuser: 改寫後的問題", recentConversationBlock(messages, "新問題"))
    }

    @Test
    fun `only the last twelve turns are carried`() {
        val messages = (1..15).map { AiMessage(role = AiMessageRole.USER, text = "問題 $it") }

        val block = recentConversationBlock(messages, question = "新問題")

        assertTrue(block.startsWith("user: 問題 4"), "the window starts at the fourth of fifteen: $block")
        assertEquals(12, block.lines().size)
        assertTrue(block.endsWith("user: 問題 15"))
    }

    @Test
    fun `a turn longer than its ceiling is cut and marked`() {
        val long = "字".repeat(4001)
        val messages = listOf(AiMessage(role = AiMessageRole.ASSISTANT, text = long))

        val block = recentConversationBlock(messages, question = "再問")

        assertTrue(block.endsWith("\n[內容已按上下文限制截短]"))
        assertTrue(block.contains("字".repeat(4000)))
    }

    @Test
    fun `no messages means no block at all`() {
        assertEquals("", recentConversationBlock(emptyList(), question = "神是愛嗎？"))
    }
}
