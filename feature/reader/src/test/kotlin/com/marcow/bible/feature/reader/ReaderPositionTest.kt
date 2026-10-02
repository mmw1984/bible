package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ReadingProgress
import com.marcow.bible.core.model.Testament
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Where the reader moves to, as arithmetic.
 *
 * Flutter kept this inside `setState` callbacks in `legacy/flutter/lib/main.dart:523`, where the
 * only way to check the book boundaries was to read the screen it had just left. The decisions are
 * pulled out here because they are the ones the chapter links, the library and the search sheet all
 * depend on, and because the ends of the Bible are the cases worth pinning.
 */
class ReaderPositionTest {
    @Test
    fun `next chapter stays inside the book`() {
        val next = nextPosition(books, position(bookIndex = 0, chapter = 2))

        assertEquals(position(bookIndex = 0, chapter = 3), next)
    }

    @Test
    fun `next chapter carries into the first chapter of the next book`() {
        val next = nextPosition(books, position(bookIndex = 0, chapter = 50))

        assertEquals(position(bookIndex = 1, chapter = 1), next)
    }

    @Test
    fun `next stops at the last chapter of the last book`() {
        assertNull(nextPosition(books, position(bookIndex = 2, chapter = 21)))
    }

    @Test
    fun `previous chapter stays inside the book`() {
        val previous = previousPosition(books, position(bookIndex = 1, chapter = 7))

        assertEquals(position(bookIndex = 1, chapter = 6), previous)
    }

    @Test
    fun `previous chapter carries back into the last chapter of the previous book`() {
        val previous = previousPosition(books, position(bookIndex = 1, chapter = 1))

        assertEquals(position(bookIndex = 0, chapter = 50), previous)
    }

    @Test
    fun `previous stops at Genesis 1`() {
        assertNull(previousPosition(books, position(bookIndex = 0, chapter = 1)))
    }

    @Test
    fun `both directions stop rather than move when the book is not in the list`() {
        val missing = position(bookIndex = 9, chapter = 1)

        assertNull(nextPosition(books, missing))
        assertNull(previousPosition(books, missing))
    }

    @Test
    fun `moving to another chapter starts at the top of it`() {
        val moved = position(bookIndex = 0, chapter = 2, scrollRatio = 0.8f).movedTo(bookIndex = 0, chapter = 3)

        assertEquals(0f, moved.scrollRatio)
    }

    @Test
    fun `a chapter is clamped into the book rather than refused`() {
        assertEquals(1, clampChapter(chapterCount = 50, chapter = 0))
        assertEquals(1, clampChapter(chapterCount = 50, chapter = -3))
        assertEquals(50, clampChapter(chapterCount = 50, chapter = 99))
        // A book that somehow reports no chapters still has a chapter one to be on.
        assertEquals(1, clampChapter(chapterCount = 0, chapter = 4))
    }

    @Test
    fun `a stored row opens the book it names, with its chapter, mode and offset`() {
        val stored = positionForProgress(
            books,
            progress("EXO", chapter = 7, mode = ReadingMode.BILINGUAL, scrollRatio = 0.3f),
        )

        val expected = ReaderPosition(
            bookIndex = 1,
            chapter = 7,
            mode = ReadingMode.BILINGUAL,
            scrollRatio = 0.3f,
        )
        assertEquals(expected, stored)
    }

    @Test
    fun `a stored chapter or offset the book no longer supports is pulled back into range`() {
        // A book that lost chapters, or a row written by an older build, can name one that is gone.
        val beyond = positionForProgress(books, progress("GEN", chapter = 90, scrollRatio = 4f))

        val expected = ReaderPosition(bookIndex = 0, chapter = 50, mode = ReadingMode.CHINESE, scrollRatio = 1f)
        assertEquals(expected, beyond)
    }

    @Test
    fun `a stored row for a book that is not in the catalogue is not opened`() {
        assertNull(positionForProgress(books, progress("XYZ", chapter = 1, updatedAt = 99)))
    }

    @Test
    fun `the newest row is where the app reopens`() = runTest {
        val stored = mapOf(
            "GEN" to progress("GEN", chapter = 3, updatedAt = 10),
            "EXO" to progress("EXO", chapter = 7, updatedAt = 20),
            "JHN" to progress("JHN", chapter = 2, updatedAt = 5),
        )

        val resumed = mostRecentPosition(books) { stored[it.id] }

        assertEquals(position(bookIndex = 1, chapter = 7), resumed)
    }

    @Test
    fun `two rows written in the same millisecond open the earlier book`() = runTest {
        val tie = mapOf(
            "GEN" to progress("GEN", chapter = 3, updatedAt = 20),
            "EXO" to progress("EXO", chapter = 7, updatedAt = 20),
        )

        val resumed = mostRecentPosition(books) { tie[it.id] }

        assertEquals(position(bookIndex = 0, chapter = 3), resumed)
    }

    @Test
    fun `a device with no stored row to resume leaves the choice to the caller`() = runTest {
        // Genesis 1 is the view model's fallback rather than this function's: it has no books to
        // index into, so the caller is the one that knows what to open. `ReaderViewModelTest` holds
        // that half down.
        assertNull(mostRecentPosition(books) { null })
    }

    @Test
    fun `a position is stored as a row with its ratio and no verse`() {
        val row = progressFor(genesis, position(bookIndex = 0, chapter = 4, scrollRatio = 0.6f), updatedAt = UPDATED_AT)

        assertEquals("GEN", row.bookId)
        assertEquals(4, row.chapter)
        assertEquals(0.6f, row.scrollRatio)
        assertEquals(ReadingMode.CHINESE, row.mode)
        assertEquals(UPDATED_AT, row.updatedAt)
        // Nothing has produced a verse number worth resuming from; the Flutter reader stored a
        // pixel offset, not a verse, and this row carries the same information as a ratio.
        assertNull(row.verse)
    }

    private fun position(
        bookIndex: Int,
        chapter: Int,
        mode: ReadingMode = ReadingMode.CHINESE,
        scrollRatio: Float = 0f,
    ) = ReaderPosition(bookIndex, chapter, mode, scrollRatio)

    /**
     * A stored row. [updatedAt] only decides which row wins a resume, so the tests that are about the
     * chapter or the ratio leave it at the epoch rather than inventing a timestamp for it — and
     * Kotlin wants the default, because a parameter with no default cannot follow ones that have
     * them and still be left out at a call site.
     */
    private fun progress(
        bookId: String,
        chapter: Int,
        mode: ReadingMode = ReadingMode.CHINESE,
        scrollRatio: Float = 0f,
        updatedAt: Long = 0L,
    ) = ReadingProgress(
        bookId = bookId,
        chapter = chapter,
        verse = null,
        scrollRatio = scrollRatio,
        mode = mode,
        updatedAt = updatedAt,
    )

    private companion object {
        const val UPDATED_AT = 1_700_000_000_000L

        val genesis = BibleBook("GEN", 1, "創世記", "Genesis", 50, Testament.OLD)
        val exodus = BibleBook("EXO", 2, "出埃及記", "Exodus", 40, Testament.OLD)
        val john = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)
        val books = listOf(genesis, exodus, john)
    }
}
