package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.VersePair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The text a long-pressed verse hands to the clipboard and to the Ask tab.
 *
 * All three strings are composed inside `_openVerseActions` in `legacy/flutter/lib/main.dart:868`,
 * a function that awaited a chapter, built three strings and then pushed a dialog — so the Flutter
 * tests could only reach the text by opening the sheet and reading it back off the clipboard. That
 * left the exact wording unpinned, and the wording is the contract: a model asked to explain a verse
 * is given the reference, the verse and the chapter, and a reference written in the wrong script is
 * an answer about the wrong passage.
 *
 * The two deliberate oddities are asserted here rather than tidied away, because they are what
 * Flutter did: the copied text always carries *both* translations whatever is on screen, and the
 * chapter context is always labelled in Chinese even for an English reading.
 */
class ScriptureRequestTest {
    @Test
    fun `a reference is the book, the chapter and the verse`() {
        assertEquals("創世記 1:1", scriptureReference("創世記", chapter = 1, verseNumber = 1))
        assertEquals("John 3:16", scriptureReference("John", chapter = 3, verseNumber = 16))
    }

    @Test
    fun `the copied text leads with the reference and then both translations`() {
        val text = verseSelectionText("創世記 1:1", verse = VersePair(1, "起初", "In the beginning"))

        assertEquals("創世記 1:1\n起初\nIn the beginning", text)
    }

    @Test
    fun `the copied text carries both translations even though only one is on screen`() {
        // Flutter built `selected` out of `verse.zh` and `verse.en` unconditionally, so a reader
        // copying out of an English-only chapter still got the pairing. The reading mode never
        // reached this string, which is why the sheet can be the only place that knows the mode.
        val text = verseSelectionText("Genesis 1:1", VersePair(1, "起初", "In the beginning"))

        assertTrue(text.contains("起初"))
        assertTrue(text.contains("In the beginning"))
    }

    @Test
    fun `a translation the row does not have becomes an empty line, not a missing one`() {
        // `bible_data.dart` pads the shorter translation with '', so a chapter that ships without an
        // English column still has one row per verse and the reference line count stays the same.
        val text = verseSelectionText("創世記 1:2", VersePair(2, "地是空的", ""))

        assertEquals("創世記 1:2\n地是空的\n", text)
    }

    @Test
    fun `the chapter context names every verse, labelled in Chinese`() {
        val context = chapterContextText("創世記", chapter = 1, verses = listOf(FirstVerse, SecondVerse))

        assertEquals(
            "創世記 1:1\n中文：起初\nEnglish: In the beginning\n\n" +
                "創世記 1:2\n中文：地是空的\nEnglish: ",
            context,
        )
    }

    @Test
    fun `the chapter context is blank-line separated rather than newline separated`() {
        // `join('\n\n')` in Flutter: one blank line between verses, so a model reads them as separate
        // passages rather than as one run-on paragraph.
        val context = chapterContextText("創世記", chapter = 1, verses = listOf(FirstVerse, SecondVerse))

        assertEquals(1, Regex("\\n\\n").findAll(context).count())
    }

    @Test
    fun `an empty chapter is an empty context rather than a dangling separator`() {
        // `join` on an empty list is an empty string, so an open sheet on a chapter that returned no
        // rows sends no context at all instead of a leading blank line.
        assertEquals("", chapterContextText("創世記", chapter = 1, verses = emptyList()))
    }

    @Test
    fun `ask AI carries the verse but no question`() {
        val request = scriptureRequest(
            action = VerseAction.ASK_AI,
            reference = "創世記 1:1",
            verse = FirstVerse,
            chapterContext = "context",
            explainQuestion = "Explain 創世記 1:1.",
        )

        // Flutter's Ask opened the composer empty and let the reader type; the prompt is Explain's.
        assertNull(request.question)
        assertEquals("創世記 1:1\n起初\nIn the beginning", request.text)
        assertEquals("context", request.chapterContext)
    }

    @Test
    fun `explain carries the question with the same verse`() {
        val request = scriptureRequest(
            action = VerseAction.EXPLAIN,
            reference = "創世記 1:1",
            verse = FirstVerse,
            chapterContext = "context",
            explainQuestion = "Explain 創世記 1:1.",
        )

        assertEquals("Explain 創世記 1:1.", request.question)
        assertEquals("創世記 1:1\n起初\nIn the beginning", request.text)
    }

    @Test
    fun `only Explain opens with a question already asked`() {
        assertTrue(VerseAction.EXPLAIN.carriesQuestion)
        assertFalse(VerseAction.ASK_AI.carriesQuestion)
    }

    @Test
    fun `an explain prompt with nothing in it is not sent as an empty question`() {
        // The prompt is a localised string, so it is never blank in practice — but a null is the
        // honest value when it is, and an empty composer is what Ask shows anyway.
        val request = scriptureRequest(
            action = VerseAction.EXPLAIN,
            reference = "創世記 1:1",
            verse = FirstVerse,
            chapterContext = "context",
            explainQuestion = "",
        )

        assertNull(request.question)
    }

    private companion object {
        val FirstVerse = VersePair(1, "起初", "In the beginning")
        val SecondVerse = VersePair(2, "地是空的", "")
    }
}
