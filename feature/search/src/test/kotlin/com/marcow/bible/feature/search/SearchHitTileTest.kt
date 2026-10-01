package com.marcow.bible.feature.search

import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.feature.search.ui.bookNameFor
import com.marcow.bible.feature.search.ui.searchReferenceLabel
import com.marcow.bible.feature.search.ui.tileReasonLine
import com.marcow.bible.feature.search.ui.verseTextFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * The four helpers `_SearchHitTile` built its reference, its verse text and its third line from,
 * which are the only parts of the tile that depend on anything but the hit.
 */
class SearchHitTileTest {
    @Test
    fun `a tile is headed by the book, chapter and verse`() {
        val label = searchReferenceLabel(hit(), ReadingMode.CHINESE, AppLocale.ZH_HANT)

        assertEquals("約翰福音 3:16", label)
    }

    @Test
    fun `book names follow the reading mode before the app language`() {
        // `_bookName` in `legacy/flutter/lib/main.dart:40`: a Chinese or bilingual reader reading in
        // English sees English names even when the interface is in Chinese, and the interface wins
        // only for a reader who is reading Chinese.
        assertEquals("John", bookNameFor(BOOK, ReadingMode.BILINGUAL, AppLocale.ZH_HANT))
        assertEquals("John", bookNameFor(BOOK, ReadingMode.ENGLISH, AppLocale.ZH_HANT))
        assertEquals("約翰福音", bookNameFor(BOOK, ReadingMode.CHINESE, AppLocale.ZH_HANT))
        assertEquals("John", bookNameFor(BOOK, ReadingMode.CHINESE, AppLocale.EN))
    }

    @Test
    fun `the verse text follows the app language, not the reading mode`() {
        // `_usesEnglishUi` decided the verse, while `_bookName` decided the heading above it, so a
        // bilingual reader in a Chinese interface is shown a Chinese verse under an English reference.
        assertEquals("For God so loved", verseTextFor(hit(), AppLocale.EN))
        assertEquals("神愛世人", verseTextFor(hit(), AppLocale.ZH_HANT))
    }

    @Test
    fun `a text search gives the tile two lines and no third`() {
        // `_SearchHitTile`'s `reason` is optional and a traditional hit never passes one, so null is
        // the ordinary case rather than the edge: two lines, no gap, nothing to say about the verse.
        assertNull(tileReasonLine(null))
    }

    @Test
    fun `an AI hit whose model gave no reason still gets no third line`() {
        // The other way of arriving with nothing: `AiScriptureReference.reason` is a non-null String
        // read through `json.stringOrEmpty`, so a model that returned the reference and dropped the
        // explanation publishes `''`. Flutter guarded on `isNotEmpty` rather than on presence for
        // exactly this, and a guard on presence alone would draw an empty line and its 7 dp.
        assertNull(tileReasonLine(""))
    }

    @Test
    fun `a reason is shown as the model wrote it`() {
        // Passed straight through, the same instance rather than a copy: the tile sets the size, the
        // colour and the leading, and never rewrites, truncates, flattens or ellipsises what came
        // back. A reason spanning two lines is two lines in the tile too.
        val reason = "耶穌在曠野受試探，仍然信靠天父。"

        assertSame(reason, tileReasonLine(reason))
        assertSame(MULTILINE_REASON, tileReasonLine(MULTILINE_REASON))
    }

    @Test
    fun `a reason that is only whitespace still gets its line`() {
        // `isNotEmpty` does not trim, so a space and a tab are both reasons and both are drawn: a
        // blank third line under the verse, with the 7 dp that goes with it. Reproduced rather than
        // tidied, because the prompt asks for a reason on every reference and this is the model
        // declining to write one.
        assertSame(BLANK_REASON, tileReasonLine(BLANK_REASON))
        assertSame(TABBED_REASON, tileReasonLine(TABBED_REASON))
    }

    @Test
    fun `a full text search says how many it cut off`() {
        assertEquals("80+", traditionalResultCount(80))
        assertEquals("79", traditionalResultCount(79))
        assertEquals("0", traditionalResultCount(0))
    }
}

/** A reason long enough that the tile's 11 sp text wraps it onto a second line. */
private const val MULTILINE_REASON = "耶穌在曠野受試探，仍然信靠天父，沒有向石頭要餅食。"

/** One space: a reason the model wrote as nothing at all. */
private const val BLANK_REASON = " "

/** A tab rather than a space, so the second whitespace flavour is the one actually pinned. */
private const val TABBED_REASON = "\t"

private val BOOK = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)

private fun hit(): ScriptureHit = ScriptureHit(
    book = BOOK,
    chapter = 3,
    verse = VersePair(number = 16, zh = "神愛世人", en = "For God so loved"),
)
