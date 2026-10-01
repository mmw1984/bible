package com.marcow.bible.feature.search.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `_cleanSearchOverview` from `legacy/flutter/lib/ai_service.dart`, on the answers the two Flutter
 * tests used (`legacy/flutter/test/ai_service_test.dart:298` and `:364`).
 *
 * The overview *is* the panel, so there is nowhere to hide what the cleaner misses — a model that
 * answered with reasoning tags, a citation or a safety preamble would show all of it. The cases are
 * therefore the shapes a provider actually answers in, plus the two edges the helper names describe:
 * a thinking block and a preamble that the token ceiling cut off mid-way.
 */
class ModelOutputTest {
    @Test
    fun `thinking tags go before the overview is published`() {
        val source = "<think>先分析關鍵字同相關主題。</think>\n神的愛貫穿救恩。"

        assertEquals("神的愛貫穿救恩。", cleanSearchOverview(source))
    }

    @Test
    fun `a thinking block the token ceiling cut off is dropped whole`() {
        // The closing tag never arrived, so the second pattern has to catch the unterminated block
        // too: half a reasoning block would be the first thing on screen.
        assertEquals("", cleanSearchOverview("<think>先分析關鍵字"))
        assertEquals("", cleanSearchOverview("<thinking>先分析"))
    }

    @Test
    fun `an answer that is only a continuation marker is empty rather than marked`() {
        assertEquals("", cleanSearchOverview("[[END]]"))
        assertEquals("", cleanSearchOverview("[[MORE]]"))
    }

    @Test
    fun `a continuation marker is not part of the answer`() {
        assertEquals("網頁回答", cleanSearchOverview("網頁回答 [[END]]"))
    }

    @Test
    fun `a citation marker and a trailing source list never reach the overview`() {
        // `legacy/flutter/test/ai_service_test.dart:364`, with OpenRouter's own citation markers in
        // the private use area spelled out as the escapes rather than pasted in.
        val source = "答案內容 [1] \uE200cite\uE202turn0search0\uE201\n\n" +
            "**來源**\n- https://example.com [[END]]"

        assertEquals("答案內容", cleanSearchOverview(source))
    }

    @Test
    fun `a fullwidth citation marker is dropped as well`() {
        assertEquals("答案內容", cleanSearchOverview("答案內容【1】"))
        assertEquals("答案內容", cleanSearchOverview("答案內容 【1†, 第二頁】"))
    }

    @Test
    fun `a provider's safety preamble goes with the blank line after it`() {
        val source = "Safety classification: safe\n\n神的愛貫穿救恩。"

        assertEquals("神的愛貫穿救恩。", cleanSearchOverview(source))
    }

    @Test
    fun `a preamble the token ceiling cut off mid-word is dropped whole`() {
        // `safety: sa` matches no completed classification, so the prefix check is what removes it;
        // leaving it would open the panel with `safety: sa`.
        assertEquals("", cleanSearchOverview("safety: sa"))
        assertEquals("", cleanSearchOverview("*User Safety: uns"))
    }

    @Test
    fun `an overview that was only metadata comes back empty`() {
        // The emptiness `SearchOverviewUseCase` refuses to publish, and the reason the sheet draws
        // `overview_failed` rather than an empty panel.
        assertEquals("", cleanSearchOverview("Content Safety: blocked"))
        assertEquals("", cleanSearchOverview("Safety: unsafe\n"))
    }

    @Test
    fun `ordinary prose is left exactly as the model wrote it`() {
        val source = "神的愛貫穿救恩。\n"

        assertEquals(source.trim(), cleanSearchOverview(source))
    }
}
