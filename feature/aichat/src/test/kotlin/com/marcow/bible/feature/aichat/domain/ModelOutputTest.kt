package com.marcow.bible.feature.aichat.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `_cleanModelOutput`, `sanitizeReasoningForDisplay` and `_appendWithoutDuplicate` from
 * `legacy/flutter/lib/ai_service.dart`, on the answers a chat round actually receives.
 *
 * The chat draws the model's text as it arrives, so everything these two do is what the reader ends
 * up looking at: a `[[END]]` left on screen, a `Sources:` list under the answer, or a second copy of
 * the title where the continuation started again. [appendWithoutDuplicate] is the fiddliest of the
 * three, so each of its branches is pinned by name — the heuristics are ordered, and reordering two
 * of them changes the answer for some inputs.
 */
internal class ModelOutputTest {
    @Test
    fun `a continuation marker is not part of the answer`() {
        assertEquals("A complete answer", cleanModelOutput("A complete answer [[END]]"))
        assertEquals("A cut answer", cleanModelOutput("A cut answer [[MORE]]"))
    }

    @Test
    fun `a marker the token ceiling cut short goes with the rest`() {
        // `[[`, `[[E`, `[[M` and so on are the same instruction as `[[END]]` arriving in pieces; the
        // answer must not end with the stub of one.
        assertEquals("A cut answer", cleanModelOutput("A cut answer [["))
        assertEquals("A cut answer", cleanModelOutput("A cut answer [[MOR"))
    }

    @Test
    fun `a marker anywhere in the answer goes, not only the one at the end`() {
        // `replaceAll`, not a strip of the trailing marker: a model writing *about* the protocol has
        // its brackets removed too, and the double space is the price of the parity.
        assertEquals("The prompt says  when it is done", cleanModelOutput("The prompt says [[END]] when it is done"))
    }

    @Test
    fun `OpenRouter's own citation markers never reach the answer`() {
        assertEquals("答案", cleanModelOutput("答案 \uE200cite\uE202turn0search0\uE201"))
    }

    @Test
    fun `a bare citation marker and a fullwidth one are dropped as well`() {
        assertEquals("See for more", cleanModelOutput("See [1] for more"))
        assertEquals("答案", cleanModelOutput("答案【1†, 第二頁】"))
    }

    @Test
    fun `a trailing source list never reaches the answer`() {
        val source = "答案內容\n\n**來源**\n- https://example.com\n- https://example.org"

        assertEquals("答案內容", cleanModelOutput(source))
    }

    @Test
    fun `a source list in the middle of the answer is left alone`() {
        // The pattern is anchored to the end because a list mid-answer is the model quoting one.
        val source = "答案內容\n\n**來源**\n- https://example.com\n\n然後是結論。"

        assertEquals(source, cleanModelOutput(source))
    }

    @Test
    fun `a provider's safety preamble goes with the blank line after it`() {
        val source = "Safety classification: safe\n\n神的愛貫穿救恩。"

        assertEquals("神的愛貫穿救恩。", cleanModelOutput(source))
    }

    @Test
    fun `a preamble the token ceiling cut off mid-word is dropped whole`() {
        assertEquals("", cleanModelOutput("safety: sa"))
    }

    @Test
    fun `a metadata line in the middle of the answer is part of the answer`() {
        // Only the *leading* preamble is stripped, because only a preamble is something the provider
        // put in front of the answer.
        val source = "第一段。\nSafety classification: safe\n第三段。"

        assertEquals(source, cleanModelOutput(source))
    }

    @Test
    fun `the reasoning channel loses a classification wherever it sits`() {
        val reasoning = "The question is about love.\nsafety: safe\nSo I look at 1 John 4:8."

        assertEquals("The question is about love.\nSo I look at 1 John 4:8.", sanitizeReasoningForDisplay(reasoning))
    }

    @Test
    fun `a truncated classification at the very end of the reasoning goes too`() {
        val reasoning = "The question is about love.\nsafety: sa"

        assertEquals("The question is about love.", sanitizeReasoningForDisplay(reasoning))
    }

    @Test
    fun `reasoning the model wrote is left exactly as it wrote it`() {
        val reasoning = "Line one.\nLine two.\n"

        assertEquals(reasoning.trim(), sanitizeReasoningForDisplay(reasoning))
    }

    @Test
    fun `a continuation with nothing before it is the whole answer`() {
        assertEquals("The answer.", appendWithoutDuplicate("", "The answer."))
    }

    @Test
    fun `a continuation the model repeated as a suffix changes nothing`() {
        assertEquals("The whole answer.", appendWithoutDuplicate("The whole answer.", "answer."))
    }

    @Test
    fun `a continuation that restates the answer replaces it`() {
        assertEquals(
            "The answer, and more of it.",
            appendWithoutDuplicate("The answer", "The answer, and more of it."),
        )
    }

    @Test
    fun `a continuation that only quotes the start is dropped unless the answer was finished`() {
        assertEquals("The answer goes on.", appendWithoutDuplicate("The answer goes on.", "The answer"))
        assertEquals(
            "The answer",
            appendWithoutDuplicate("The answer goes on.", "The answer", additionComplete = true),
        )
    }

    @Test
    fun `a continuation that swallowed the answer replaces it`() {
        assertEquals("God is love indeed", appendWithoutDuplicate("love", "God is love indeed"))
    }

    @Test
    fun `a continuation quoted out of the middle is dropped unless the answer was finished`() {
        assertEquals("love of God is love", appendWithoutDuplicate("love of God is love", "of God"))
        assertEquals(
            "of God",
            appendWithoutDuplicate("love of God is love", "of God", additionComplete = true),
        )
    }

    @Test
    fun `a continuation opening with punctuation is glued straight on`() {
        assertEquals("The answer，然後是結論。", appendWithoutDuplicate("The answer", "，然後是結論。"))
    }

    @Test
    fun `an overlap of eight characters or more is taken out of the continuation`() {
        // The model repeated the last line of its own answer; only what it added after that is kept.
        assertEquals(
            "the answer begins here and finishes",
            appendWithoutDuplicate("the answer begins here", "begins here and finishes"),
        )
    }

    @Test
    fun `an overlap shorter than eight characters is a new paragraph instead`() {
        assertEquals("The answer here\n\nhere we go on", appendWithoutDuplicate("The answer here", "here we go on"))
    }

    @Test
    fun `a continuation that opens the same way is a restart rather than a second copy`() {
        val first = "## The love of God\n\nA body that ran on for a while."
        val restarted = "## The love of God\n\nShort."

        assertEquals(first, appendWithoutDuplicate(first, restarted))
        assertEquals(restarted, appendWithoutDuplicate(first, restarted, additionComplete = true))
    }

    @Test
    fun `a restart longer than what came before wins even when the answer was unfinished`() {
        val first = "Short opening.\n\nA body."
        val restarted = "Short opening.\n\nA much longer body that replaces the short one."

        assertEquals(restarted, appendWithoutDuplicate(first, restarted))
    }

    @Test
    fun `two answers with nothing in common are joined by a blank line`() {
        assertEquals(
            "First part.\n\nSecond part.",
            appendWithoutDuplicate("First part.", "Second part."),
        )
    }
}
