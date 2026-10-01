package com.marcow.bible.feature.reader

import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.icons.AppGlyph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * How many rows the action sheet has, which is the only decision it makes.
 *
 * The sheet is three 54 dp tiles and a grabber, and `ReaderGoldenTest` draws it — all three rows, at
 * `progress = 1`, which is what the reader sees once a host can service the two question rows. What
 * nothing held was the other state, and it is the state the app is in: Phase 2 has no Ask feature, so
 * `onAction` is null and every long press opens a sheet with a single row in it. That is a branch
 * that compiled, and the one that is easiest to get wrong in the direction that looks fine — a sheet
 * offering a question nobody can answer.
 *
 * So the rows are a table and the sheet is a map over it. Copy is the row that survives, and the two
 * that need a host come and go together, because Flutter wrote two `if (ai.isSupported)`s that
 * agreed and one flag is what they were.
 */
class VerseActionSheetTest {
    /**
     * One row when there is nowhere to send a question.
     *
     * Flutter's condition was `if (ai.isSupported)`, read here the other way round: no host means no
     * question rows. It is the sheet a reader in this build gets on every long press, and it is not
     * an empty one — the row that is left is the one that needed nothing to begin with.
     */
    @Test
    fun `a sheet with no host has the one row that needs none`() {
        assertEquals(listOf(VerseActionRow.COPY), verseActionRows(askSupported = false))
    }

    /**
     * Three rows when there is a host, in Flutter's order.
     *
     * Copy first, then the two that open the Ask tab. The order is a reader's: the one action that
     * completes where they are sitting is the one nearest the top, and the two that leave the reader
     * are below it in the order Flutter listed its tiles.
     */
    @Test
    fun `a sheet with a host has copy then the two that leave the reader`() {
        assertEquals(
            listOf(VerseActionRow.COPY, VerseActionRow.ASK_AI, VerseActionRow.EXPLAIN),
            verseActionRows(askSupported = true),
        )
    }

    /**
     * Copy is never the row that goes.
     *
     * The three rows are not three copies of one decision: the two question rows are the same
     * question sent two ways, so they appear and disappear together, while the clipboard needs no
     * feature at all. A sheet that lost the wrong row would be one where the only thing on it is a
     * question with nowhere to go.
     */
    @Test
    fun `the two question rows are the two that are asked for`() {
        val withHost = verseActionRows(askSupported = true)
        val without = verseActionRows(askSupported = false)

        assertEquals(withHost.first(), without.single())
        assertEquals(listOf(VerseActionRow.ASK_AI, VerseActionRow.EXPLAIN), withHost.drop(1))
    }

    /**
     * Each row is named by the string Flutter gave it, and each name says what it opens.
     *
     * `copyScripture`, `askAi` and `explainScripture` at `legacy/flutter/lib/main.dart:912`, `:921`
     * and `:934`. These are the only sentences in the sheet, so a row that borrowed another's name
     * would be two controls that announce themselves the same way — and a copy row called "Copy
     * verse" beside an Explain row called "Explain verse" is a naming convention Flutter did not have.
     */
    @Test
    fun `each row is named by its own string`() {
        assertEquals(R.string.copy_scripture, label(VerseActionRow.COPY))
        assertEquals(R.string.ask_ai, label(VerseActionRow.ASK_AI))
        assertEquals(R.string.explain_scripture, label(VerseActionRow.EXPLAIN))
    }

    /**
     * The glyphs are Flutter's, because a sheet is the one place a second icon language shows.
     *
     * `AppGlyph.copy`, `.chat` and `.book` at `:911`, `:920` and `:933`. Unlike the reader's Ask and
     * Devotions buttons — which stand in for tabs and carry the app's glyphs — these three were
     * already the app's own, so there is nothing here to decide and nothing to hold beyond the fact
     * that all three rows are glyph rows.
     */
    @Test
    fun `each row carries its own glyph`() {
        assertEquals(AppGlyph.COPY, glyph(VerseActionRow.COPY))
        assertEquals(AppGlyph.CHAT, glyph(VerseActionRow.ASK_AI))
        assertEquals(AppGlyph.BOOK, glyph(VerseActionRow.EXPLAIN))
    }

    /** The string [row] is named by, folded in so the assertions read the resource and not the call. */
    private fun label(row: VerseActionRow): Int = verseActionLabel(row)

    private fun glyph(row: VerseActionRow): AppGlyph = verseActionGlyph(row)
}
