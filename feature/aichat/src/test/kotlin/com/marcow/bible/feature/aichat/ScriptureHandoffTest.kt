package com.marcow.bible.feature.aichat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the chat takes from a request the reader made, as `_openAiChat` assembled it at
 * `legacy/flutter/lib/main.dart:969`.
 *
 * `AiChatViewModelTest` pins what the chat *does* with a handoff once it holds one. What arrives before
 * that is the mapping itself, and it is pinned here because the two features cannot share a type: the
 * request is `feature/reader`'s and this module cannot see it, so the four names cross as arguments and
 * the renaming is a decision this file makes visible rather than one every call site repeats.
 */
class ScriptureHandoffTest {
    @Test
    fun `the reader's four values are carried over unchanged`() {
        val context = "JHN 3:16\n中文：神愛世人\n耶穌說："
        val verse = "JHN 3:16\n「神愛世人，甚至將他的獨生子賜給他們。」"
        val question = "請用簡單的語言解釋「JHN 3:16」的意思。"

        assertEquals(
            ScriptureHandoff(
                reference = "JHN 3:16",
                context = context,
                attachment = verse,
                question = question,
                autoSend = true,
            ),
            ScriptureHandoff.of(
                reference = "JHN 3:16",
                text = verse,
                chapterContext = context,
                question = question,
            ),
        )
    }

    @Test
    fun `「解釋經文」 asks for the reader, 「問 AI」 leaves the composer to them`() {
        // Explain is the only action Flutter passed `autoSend: true` for, and it is the only one that
        // carried a question — the pair the reader can never produce on its own is a question attached
        // to a chat that waits, so the flag is read off the question rather than taken as an argument.
        val explain = ScriptureHandoff.of(
            reference = "JHN 3:16",
            text = "JHN 3:16",
            chapterContext = "JHN 3:16",
            question = "請用簡單的語言解釋「JHN 3:16」的意思。",
        )
        val ask = ScriptureHandoff.of(
            reference = "JHN 3:16",
            text = "JHN 3:16",
            chapterContext = "JHN 3:16",
        )

        assertTrue(explain.autoSend)
        assertEquals(explain.question, explain.trimmedQuestion)
        assertFalse(ask.autoSend)
        assertNull(ask.question)
        assertNull(ask.trimmedQuestion)
    }

    @Test
    fun `a blank chapter is carried as it was and attaches nothing`() {
        // The chapter is the one of the four Flutter judged at the far end rather than here: on
        // `launchScriptureContext?.trim().isNotEmpty`, gating both the chip and the prompt's context.
        // So the mapping must not tidy it — a blank chapter has to arrive blank for that flag to be
        // false, and a chapter with trailing whitespace has to keep it.
        val handoff = ScriptureHandoff.of(
            reference = "JHN 3:16",
            text = "JHN 3:16",
            chapterContext = "   ",
        )

        assertFalse(handoff.contextAttached)
        assertEquals("   ", handoff.context)
        assertEquals("JHN 3:16", handoff.attachment)
    }

    @Test
    fun `the same request maps to an equal handoff, so a re-delivered one asks nothing twice`() {
        // `openFromReader` compares the handoff it holds with the one arriving and returns if they are
        // equal, which is what makes a chapter rebuild's re-delivery a no-op. Data equality is the only
        // thing that decides it, so a mapped handoff has to keep comparing equal to itself.
        val first = ScriptureHandoff.of(
            reference = "JHN 3:16",
            text = "JHN 3:16",
            chapterContext = "JHN 3:16",
            question = "解釋這節經文",
        )
        val second = ScriptureHandoff.of(
            reference = "JHN 3:16",
            text = "JHN 3:16",
            chapterContext = "JHN 3:16",
            question = "解釋這節經文",
        )

        assertEquals(first, second)
    }
}