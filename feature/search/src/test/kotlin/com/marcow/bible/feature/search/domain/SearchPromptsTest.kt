package com.marcow.bible.feature.search.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two prompts, written out in full and compared character for character against the two raw
 * strings in `legacy/flutter/lib/ai_service.dart`.
 *
 * `NATIVE_PLAN.md` §4.3 calls these tuned rather than merely correct — the line breaks, the example
 * references and the `BIBLE_SEARCH_*` markers are load-bearing — so the expected text is pasted in
 * whole rather than asserted in fragments. A reworded line, a reflowed sentence or a lost blank line
 * then fails here instead of quietly changing what the model is asked.
 *
 * Each expected value carries a newline at both ends because Dart's `'''` strings do, which is why
 * the blocks below are wrapped rather than compared on their own.
 */
class SearchPromptsTest {
    @Test
    fun `the overview prompt is the Flutter one word for word`() {
        assertEquals(
            "\n$OVERVIEW\n",
            searchOverviewPrompt(query = QUERY, memory = MEMORY, aiLanguage = LANGUAGE),
        )
    }

    @Test
    fun `the references prompt is the Flutter one word for word`() {
        assertEquals(
            "\n$REFERENCES\n",
            searchReferencesPrompt(query = QUERY, memory = MEMORY, aiLanguage = LANGUAGE),
        )
    }

    @Test
    fun `each prompt opens with the marker its request is built from`() {
        // `_send` worked out which request it was sending from these two strings, so neither may move
        // off the first line or lose its prefix.
        val overview = searchOverviewPrompt(query = QUERY, memory = MEMORY, aiLanguage = LANGUAGE)
        val references = searchReferencesPrompt(query = QUERY, memory = MEMORY, aiLanguage = LANGUAGE)

        assertTrue(overview.startsWith("\n$OVERVIEW_MARKER\n"))
        assertTrue(references.startsWith("\n$REFERENCES_MARKER\n"))
    }

    @Test
    fun `an empty memory still leaves the block the prompt promises`() {
        // `AiSearchMemory` answers '' until Phase 4 brings `memory.md` across, which is the state the
        // Flutter build was in on a fresh install: the prompt still asks for the memory it has none of.
        val prompt = searchOverviewPrompt(query = QUERY, memory = "", aiLanguage = LANGUAGE)

        assertTrue(prompt.contains("Persistent user memory:\n\nSearch query: $QUERY"))
    }
}

private const val QUERY = "神的愛"
private const val MEMORY = "MEMORY"
private const val LANGUAGE = "繁體中文"
private const val OVERVIEW_MARKER = "BIBLE_SEARCH_OVERVIEW"
private const val REFERENCES_MARKER = "BIBLE_SEARCH_REFERENCES_JSON"

private val OVERVIEW = """
    BIBLE_SEARCH_OVERVIEW
    You write the AI Overview inside a Bible reading app. Answer the search query by
    meaning in concise 繁體中文. Carefully distinguish what the
    Bible says from interpretation. Never invent or quote verse text. Do not return
    JSON, scripture references, a heading, or follow-up questions. Return only the
    short overview prose. Never output analysis, reasoning, thinking steps, or
    provider metadata.

    Persistent user memory:
    MEMORY

    Search query: 神的愛
""".trimIndent()

private val REFERENCES = """
    BIBLE_SEARCH_REFERENCES_JSON
    You find scripture references for semantic search inside a Bible reading app.
    Never invent or quote verse text; the app resolves every reference from its
    local Bible database.

    Return ONLY valid JSON in exactly this shape:
    {"scriptures":[{"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":17,"reason":"why relevant"}],"suggestedQuestions":["follow-up"]}
    Use canonical 3-letter book IDs. Return up to 16 relevant references for a broad
    query and up to 8 for a narrow query. Return 3 questions in
    繁體中文.
    Every reason must accurately describe the referenced verses. For example,
    MAT 4:1-11 is Jesus' temptation; the Samaritan woman is JHN 4. Be concise.

    Persistent user memory:
    MEMORY

    Search query: 神的愛
""".trimIndent()
