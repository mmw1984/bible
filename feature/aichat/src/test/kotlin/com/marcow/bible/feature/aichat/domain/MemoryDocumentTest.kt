package com.marcow.bible.feature.aichat.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `memory.md` as `AiMemoryStore` used it: the seeded document, the block the prompt gets, and the
 * entry one turn is filed as.
 *
 * These are the pieces a Room store would still need verbatim, which is why they are pure functions
 * rather than part of the store: the memory *document* is prompt text the model reads, so §4.6 can
 * change where it lives without changing a character of it.
 */
internal class MemoryDocumentTest {
    @Test
    fun `a document within the ceiling is given to the prompt as it is`() {
        assertEquals(MEMORY_DOCUMENT_SEED, memoryBlock(MEMORY_DOCUMENT_SEED, maxCharacters = 7000))
    }

    @Test
    fun `a document past the ceiling keeps its tail and says so in the title`() {
        val content = "頭".repeat(100) + "尾".repeat(100)

        val block = memoryBlock(content, maxCharacters = 100)

        assertEquals("# Bible AI Memory (latest entries)\n\n" + "尾".repeat(100), block)
    }

    @Test
    fun `the tail is the end of the document, not the beginning`() {
        // The file is append-only, so its last entries are the ones about the conversation in hand.
        val block = memoryBlock(MEMORY_DOCUMENT_SEED + "- 最新的對話", maxCharacters = 10)

        assertTrue(block.endsWith("新的對話"), "the most recent entry is the one kept: $block")
    }

    @Test
    fun `a turn is filed under its kind with its role`() {
        val entry = memoryEntry(
            AiMessage(role = AiMessageRole.USER, text = "約翰福音三章十六節？"),
            timestamp = "2026-01-02T03:04:05.000000Z",
        )

        assertEquals(
            "\n### 2026-01-02T03:04:05.000000Z - Important events and conversation\n" +
                "- Role: user\n- Content: 約翰福音三章十六節？\n",
            entry,
        )
    }

    @Test
    fun `a turn with a passage records it as a second bullet`() {
        val entry = memoryEntry(
            AiMessage(
                role = AiMessageRole.USER,
                text = "解釋一下",
                scripture = "JHN 3:16",
                kind = AiMessageKind.EXPLANATION,
            ),
            timestamp = "2026-01-02T03:04:05.000000Z",
        )

        assertTrue(entry.contains("### 2026-01-02T03:04:05.000000Z - Explained scripture\n"))
        assertTrue(entry.contains("- Role: user\n- Scripture: JHN 3:16\n"), entry)
    }

    @Test
    fun `a turn with no passage has no reference bullet at all`() {
        val entry = memoryEntry(AiMessage(role = AiMessageRole.ASSISTANT, text = "答案"), timestamp = "t")

        assertFalse("- Scripture:" in entry)
    }

    @Test
    fun `multi-line content is indented so it stays inside its bullet`() {
        // memory.md is a document the model is shown; an unindented continuation line would render as
        // a new paragraph and read as a separate thought.
        val entry = memoryEntry(
            AiMessage(role = AiMessageRole.ASSISTANT, text = "第一段\n第二段"),
            timestamp = "t",
        )

        assertTrue(entry.contains("- Content: 第一段\n  第二段\n"), entry)
    }

    @Test
    fun `a truncated block is not titled as though it were the whole memory`() {
        val block = memoryBlock("x".repeat(200), maxCharacters = 50)

        assertTrue(block.startsWith("# Bible AI Memory (latest entries)"))
        assertFalse(block.contains("# Bible AI Memory\n"))
    }
}
