package com.marcow.bible.feature.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The numbers the library chose against a window, and the list the segmented control filters.
 *
 * [libraryPanelWidth] is the one worth pinning: the panel is a sheet over the reader, and the sheet
 * has to stay a sheet on a tablet as well as on a phone. Flutter clamped it at 440, and that clamp is
 * the difference between a panel and a full-screen takeover.
 *
 * The widths are compared with a [DpTolerance] rather than exactly, because 92% of a dp is not a
 * representable float: 400 × 0.92 is 368.0000067, and a test that demanded 368.0 would be asserting
 * the rounding of the arithmetic rather than the width.
 */
class LibraryLayoutTest {
    @Test
    fun `the panel takes 92 percent of a phone`() {
        assertWidth(368.dp, libraryPanelWidth(400.dp))
    }

    @Test
    fun `the panel never grows past 440`() {
        // Flutter's `math.min(size.width * .92, 440)`. On a tablet, or a phone in landscape, the cap
        // is what stops the sheet becoming the screen.
        assertWidth(440.dp, libraryPanelWidth(1024.dp))
    }

    @Test
    fun `the cap takes over just below 480 wide`() {
        // 440 / .92 is where the two halves of the `min` cross over: a hair under it the sheet is
        // still 92% of the window, a hair over it is 440.
        assertWidth(439.dp, libraryPanelWidth(478.dp))
        assertWidth(440.dp, libraryPanelWidth(479.dp))
    }

    @Test
    fun `a book is listed under its place in the whole canon, padded to two digits`() {
        assertEquals("01", libraryRowOrdinal(ordinal = 1))
        assertEquals("09", libraryRowOrdinal(ordinal = 9))
        assertEquals("10", libraryRowOrdinal(ordinal = 10))
        // Malachi is the 39th book, so a New Testament list starts at 40 — the numbering counts the
        // whole Bible, which is why the number is in the row at all.
        assertEquals("39", libraryRowOrdinal(ordinal = 39))
        assertEquals("66", libraryRowOrdinal(ordinal = 66))
    }

    @Test
    fun `each testament shows its own books in canon order`() {
        assertEquals(listOf("GEN", "EXO", "PSA"), booksInTestament(canon, Testament.OLD).map { it.id })
        assertEquals(listOf("MAT", "JHN", "REV"), booksInTestament(canon, Testament.NEW).map { it.id })
    }

    @Test
    fun `the state filters the books the same way`() {
        val state = LibraryUiState(books = canon)

        assertEquals(listOf("MAT", "JHN", "REV"), state.booksIn(Testament.NEW).map { it.id })
    }

    @Test
    fun `a book is named after the reading mode, not the list it is in`() {
        val state = LibraryUiState(books = canon)
        val john = canon.first { it.id == "JHN" }

        // Chinese reading names it in Chinese even though the list it is in is the New Testament.
        assertEquals("約翰福音", state.displayName(john, ReadingMode.CHINESE))
        // English reading names it in English whatever the interface is set to, so a bilingual
        // reader is not reading one name and looking at another. `BibleBookTest` covers the rule.
        assertEquals("John", state.displayName(john, ReadingMode.ENGLISH))
    }

    private val canon = listOf(
        BibleBook("GEN", 1, "創世記", "Genesis", 50, Testament.OLD),
        BibleBook("EXO", 2, "出埃及記", "Exodus", 40, Testament.OLD),
        BibleBook("PSA", 19, "詩篇", "Psalms", 150, Testament.OLD),
        BibleBook("MAT", 40, "馬太福音", "Matthew", 28, Testament.NEW),
        BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW),
        BibleBook("REV", 66, "啟示錄", "Revelation", 22, Testament.NEW),
    )
}

/**
 * A hundredth of a dp: a hundredth is far below anything that could be seen, and far above the
 * float error in `width * .92`.
 */
private val DpTolerance = 0.01f

private fun assertWidth(expected: Dp, actual: Dp) =
    assertEquals(expected.value, actual.value, DpTolerance)
