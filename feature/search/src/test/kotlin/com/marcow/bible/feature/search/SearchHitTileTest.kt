package com.marcow.bible.feature.search

import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.feature.search.ui.bookNameFor
import com.marcow.bible.feature.search.ui.searchReferenceLabel
import com.marcow.bible.feature.search.ui.verseTextFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The two helpers `_SearchHitTile` built its reference and its verse text from, which are the only
 * parts of the tile that depend on anything but the hit.
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
    fun `a full text search says how many it cut off`() {
        assertEquals("80+", traditionalResultCount(80))
        assertEquals("79", traditionalResultCount(79))
        assertEquals("0", traditionalResultCount(0))
    }
}

private val BOOK = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)

private fun hit(): ScriptureHit = ScriptureHit(
    book = BOOK,
    chapter = 3,
    verse = VersePair(number = 16, zh = "神愛世人", en = "For God so loved"),
)
