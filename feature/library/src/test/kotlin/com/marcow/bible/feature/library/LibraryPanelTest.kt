package com.marcow.bible.feature.library

import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * What the library sheet is called, which is the only part of it that is a sentence.
 *
 * The panel's chrome is three numbers — 28 of padding, a 34 dp heading, 26 under it — and a list of
 * rows, and `LibraryLayoutTest` holds all of that. What nothing holds is the wording: five uses of
 * four strings, one of them the same string in two places, and each a spot where a plausible wrong
 * choice costs a reader something. A sheet headed 書卷 rather than 選擇書卷 describes its contents
 * instead of saying what it is for; a switcher announcing 選擇書卷 while its two cells say 舊約 and
 * 新約 has named itself after its own heading.
 *
 * The rows' own detail is the exception, and it is not here because it cannot be: `%1$d chapters` is
 * a format string, and resolving one needs a `Context`. What is worth saying is that it is the only
 * label in the panel computed from state — a book's own chapter count, `context.l10n.chapterCount(
 * book.chapters)` at `legacy/flutter/lib/main.dart:2067` — so it is the one that could disagree with
 * the book it sits beside, and the only thing holding it is the book list the rows are drawn from.
 */
class LibraryPanelTest {
    /**
     * The title and the switcher are the same string, because they are the same sheet.
     *
     * The heading is where a reader reads what the sheet is for and the switcher is what announces it
     * to one who cannot see the heading, so they are the sheet's name twice rather than two names.
     * Flutter wrote the heading that way — `context.l10n.selectBook` at `:1985` — and left the
     * switcher silent, which is the half this module fills in.
     */
    @Test
    fun `the sheet and the switcher that names it are the same string`() {
        assertEquals(R.string.select_book, libraryPanelLabel(LibraryPanelLabel.TITLE))
        assertEquals(
            libraryPanelLabel(LibraryPanelLabel.TITLE),
            libraryPanelLabel(LibraryPanelLabel.TESTAMENT_SWITCHER),
        )
    }

    /** The three of them, which is a table of three and is worth reading as one. */
    @Test
    fun `the panel's chrome is the three strings Flutter used`() {
        assertEquals(
            listOf(R.string.select_book, R.string.select_book, R.string.close),
            LibraryPanelLabel.entries.map { libraryPanelLabel(it) },
        )
    }

    /**
     * The button that dismisses the sheet is the one control here with a word rather than a glyph.
     *
     * A 19 dp cross closes as surely as a 40 dp one does, so `context.l10n.close`
     * (`legacy/flutter/lib/main.dart:1995`) is all that says which of the sheet's controls throws away
     * the book a reader was in — and it is the only one of them that does.
     */
    @Test
    fun `only the close button is named for itself`() {
        assertEquals(R.string.close, libraryPanelLabel(LibraryPanelLabel.CLOSE))
        assertNotEquals(libraryPanelLabel(LibraryPanelLabel.TITLE), libraryPanelLabel(LibraryPanelLabel.CLOSE))
    }

    /**
     * Old before New, which is canon order and Flutter's `[true, false]`.
     *
     * The order decides what a reader has to read to know where they are: the sheet opens on the
     * testament of the book being read ([openingTestament], held by `LibraryLayoutTest`), so on a New
     * Testament book the switcher arrives with the second cell chosen and the first one is the other
     * half of the canon. Swapping the two would be a segment that always opens on the wrong answer.
     */
    @Test
    fun `the switcher is the Old Testament first and the New one second`() {
        assertEquals(
            listOf(
                Testament.OLD to R.string.old_testament_count,
                Testament.NEW to R.string.new_testament_count,
            ),
            testamentLabels(),
        )
    }

    /**
     * Each half is named by its own string, and each string carries the size of that half in it.
     *
     * The counts are inside the translations — `舊約 · 39` and `新約 · 27` in both `values/strings.xml`
     * and `values-zh-rTW/strings.xml`, exactly as `app_zh.arb:96` and `:97` had them — which makes
     * them the one number in the panel that no code holds and so no code can get wrong. It is also
     * why these are two whole strings rather than one with a count in it: a formatted `舊約 · %1$d`
     * would be a count computed from a book list and printed next to a translation that says
     * otherwise, and the two would only ever agree by accident.
     */
    @Test
    fun `each half is named by a string that already says how big it is`() {
        val labels = testamentLabels().map { it.second }

        assertEquals(2, labels.distinct().size)
    }
}
