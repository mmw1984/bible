package com.marcow.bible.core.database

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.ScriptureRef
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair

/**
 * Where the reader reads from and writes progress to.
 *
 * The immutable scripture is served out of the prepackaged `bible.db` asset,
 * so this interface is the only way any module touches scripture data. Swapping
 * the implementation later (for example to Room) stays inside core/database.
 * Every call is blocking; callers are expected to be on `Dispatchers.IO`.
 */
interface ScriptureQueries {

    /** All 66 books in canonical order. */
    fun books(): List<BibleBook>

    fun book(bookId: String): BibleBook?

    /** Book ids and names for the library drawer, split by testament. */
    fun booksByTestament(testament: Testament): List<BibleBook>

    /**
     * One chapter with both translations aligned, `max(cuv, web)` verses long.
     * Returns an empty list for a chapter outside the book.
     */
    fun chapter(bookId: String, chapter: Int): List<VersePair>

    /**
     * Case-insensitive substring search over both translations, in canonical
     * order, capped at [limit]. Blank queries return nothing.
     */
    fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<ScriptureHit>

    /** A single verse, or null when it does not exist. */
    fun verse(bookId: String, chapter: Int, verse: Int): VersePair?

    /**
     * The verses in [ref] rendered the way the AI layer expects them, one block
     * per verse: `"<book.zh> <chapter>:<verse>\n中文：<zh>\nEnglish: <en>"`, blocks
     * joined by a blank line. Verbatim from `_runScriptureTool` in
     * `legacy/flutter/lib/ai_service.dart`, because those strings are fed back
     * to the model as authoritative results.
     */
    fun rangeText(ref: ScriptureRef): String

    fun progress(bookId: String): ReadingProgress?

    fun saveProgress(progress: ReadingProgress)

    companion object {
        /** Matches the Flutter search cap (`legacy/flutter/lib/bible_data.dart`). */
        const val DEFAULT_SEARCH_LIMIT = 80

        /** Range cap, the same guard `_runScriptureTool` applies to tool calls. */
        const val MAX_RANGE_VERSES = 100
    }
}

/**
 * A book's reading state.
 *
 * [scrollRatio] is the persisted scroll position as a fraction of the scroll
 * extent, which survives a different screen size or font scale. The Flutter
 * build stored absolute pixels instead (`reader_scroll_<BOOK_ID>-<chapter>`);
 * the migration in core/legacy-migration converts those on first read.
 */
data class ReadingProgress(
    val bookId: String,
    val chapter: Int,
    val verse: Int?,
    val scrollRatio: Float,
    val mode: ReadingMode,
    val updatedAtMillis: Long,
)