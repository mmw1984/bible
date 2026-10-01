package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ReadingProgress

/**
 * Where the reader is: which book, which chapter, in which reading mode, and how far down it is.
 *
 * This is the reader's own vocabulary rather than the row it ends up stored as, because two of the
 * four fields mean something different while the app is running. [scrollRatio] is the *live* scroll
 * position, which the screen reports on every scroll and which is only written out when it settles;
 * and the whole record is expressed against a list the screen can index into, where
 * `ReadingProgress` is expressed against a stored `book_id`.
 *
 * Flutter kept the same four values across three homes — `_BibleHomeState`'s fields, the
 * `ReaderLocation` it handed to search, and the `reader_book` / `reader_chapter` / `reader_mode`
 * preferences — and every one of them moved together on a navigation, so they stay together here.
 */
data class ReaderPosition(val bookIndex: Int, val chapter: Int, val mode: ReadingMode, val scrollRatio: Float) {
    /** Flutter reset the offset on every navigation; a ratio is the same decision. */
    fun movedTo(bookIndex: Int, chapter: Int): ReaderPosition =
        copy(bookIndex = bookIndex, chapter = chapter, scrollRatio = 0f)
}

/**
 * The next chapter, mirroring `_next` in `legacy/flutter/lib/main.dart:545`.
 *
 * Flutter stepped within the book first and only rolled over to the first chapter of the next book
 * at the end of this one, so the order of books is what makes "next" continue through the Bible
 * rather than stop at a chapter boundary.
 *
 * null at the last chapter of the last book is the end Flutter reached too: its `setState` body
 * simply changed nothing and reloaded the chapter underneath, so the reader stayed where they were.
 * Here there is no reload to do, which is the same visible outcome without the skeleton in between.
 */
fun nextPosition(books: List<BibleBook>, current: ReaderPosition): ReaderPosition? {
    val book = books.getOrNull(current.bookIndex) ?: return null
    return when {
        current.chapter < book.chapters -> current.movedTo(current.bookIndex, current.chapter + 1)
        current.bookIndex < books.lastIndex -> current.movedTo(current.bookIndex + 1, 1)
        else -> null
    }
}

/**
 * The previous chapter, mirroring `_previous` in `legacy/flutter/lib/main.dart:556`.
 *
 * Rolling back from chapter one lands on the *last* chapter of the previous book, which is what
 * makes the pair of directions read as one continuous sequence, and stops at Genesis 1 the same way
 * [nextPosition] stops at the end.
 */
fun previousPosition(books: List<BibleBook>, current: ReaderPosition): ReaderPosition? {
    val book = books.getOrNull(current.bookIndex) ?: return null
    return when {
        current.chapter > 1 -> current.movedTo(current.bookIndex, current.chapter - 1)
        current.bookIndex > 0 -> current.movedTo(current.bookIndex - 1, books[current.bookIndex - 1].chapters)
        else -> null
    }
}

/**
 * A chapter number inside `1..chapterCount`, the `clamp(1, book.chapters)` Flutter applied when it
 * restored a stored chapter.
 *
 * Applied on every chapter the reader is asked for rather than only on restore, because the one
 * caller that can produce an out-of-range number is the search sheet jumping to a chapter (Phase 3),
 * and an empty chapter is a worse failure than a clamped one.
 */
fun clampChapter(chapterCount: Int, chapter: Int): Int = chapter.coerceIn(1, maxOf(1, chapterCount))

/**
 * The position a stored row opens at, or null when the row's book is not in [books].
 *
 * The chapter is clamped rather than trusted because the row outlives any single schema: a book that
 * lost chapters, or a row imported from the Flutter preferences, can name one that no longer exists.
 */
fun positionForProgress(books: List<BibleBook>, progress: ReadingProgress): ReaderPosition? {
    val index = books.indexOfFirst { it.id == progress.bookId }
    return if (index < 0) null else positionAt(books, index, progress)
}

/**
 * The position the app reopens at: the most recently updated row, mirroring `_restorePosition`
 * reading the `reader_book` / `reader_chapter` / `reader_mode` preferences Flutter wrote.
 *
 * Flutter had exactly one row for the position because `SharedPreferences` has one `reader_book`
 * key. `reading_progress` has one row per *book*, so "the position" is the newest of them, and
 * [progressFor] is called per book — 66 primary-key lookups, once, at open.
 *
 * A tie goes to the earlier book in canon order, which is the one Flutter would have shown had two
 * writes landed inside the same millisecond and only one of them survived.
 */
fun mostRecentPosition(books: List<BibleBook>, progressFor: suspend (BibleBook) -> ReadingProgress?): ReaderPosition? {
    var newest: ReadingProgress? = null
    var newestIndex = -1
    books.forEachIndexed { index, book ->
        val progress = progressFor(book) ?: return@forEachIndexed
        val current = newest
        if (current == null || progress.updatedAt > current.updatedAt) {
            newest = progress
            newestIndex = index
        }
    }
    val progress = newest ?: return null
    return if (newestIndex < 0) null else positionAt(books, newestIndex, progress)
}

/** [positionForProgress] for a book whose index is already known. */
private fun positionAt(books: List<BibleBook>, index: Int, progress: ReadingProgress): ReaderPosition = ReaderPosition(
    bookIndex = index,
    chapter = clampChapter(books[index].chapters, progress.chapter),
    mode = progress.mode,
    scrollRatio = progress.scrollRatio.coerceIn(0f, 1f),
)

/** The row [position] is stored as, which is what `BibleRepository.saveProgress` writes. */
fun progressFor(book: BibleBook, position: ReaderPosition, updatedAt: Long): ReadingProgress = ReadingProgress(
    bookId = book.id,
    chapter = position.chapter,
    // The verse is deliberately null: the Flutter reader stored a pixel offset, not a verse, and
    // nothing has yet produced a verse number worth resuming from.
    verse = null,
    scrollRatio = position.scrollRatio.coerceIn(0f, 1f),
    mode = position.mode,
    updatedAt = updatedAt,
)
