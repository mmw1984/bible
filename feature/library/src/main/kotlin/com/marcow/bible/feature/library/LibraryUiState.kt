package com.marcow.bible.feature.library

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament

/**
 * Everything the library panel renders, mirroring the fields `_LibraryPanel` was handed in
 * `legacy/flutter/lib/main.dart:1944`.
 *
 * The panel is given its books and its language rather than reaching for them, which is what lets a
 * preview draw the sheet with no database behind it.
 *
 * [usesEnglishUi] is the second half of what a book is called — the first is the reading mode, which
 * the host passes to the panel rather than the state, because it is the reader's setting and the
 * reader already holds it. See [BibleBook.displayName].
 */
data class LibraryUiState(
    /** Canon order, all 66 books, as `BibleRepository.books()` returns them. */
    val books: List<BibleBook> = emptyList(),
    val usesEnglishUi: Boolean = false,
    val loading: Boolean = true,
) {
    /** The books of [testament], which is the half of the list the segmented control is showing. */
    fun booksIn(testament: Testament): List<BibleBook> = booksInTestament(books, testament)

    /** What a book is called under the current reading mode and interface language. */
    fun displayName(book: BibleBook, readingMode: ReadingMode): String = book.displayName(readingMode, usesEnglishUi)
}
