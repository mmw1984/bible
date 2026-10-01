package com.marcow.bible.core.database

import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ReadingProgress
import com.marcow.bible.core.model.Testament
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The repository layer over the two DAOs, with the DAOs faked.
 *
 * What is worth pinning down here is the behaviour the reader depends on rather than the SQL: the
 * cross-translation pairing rule that `VerseEntity.toDomain` implements, the canon order the library
 * renders, and a progress row surviving a round trip through the entity mapper. The SQL itself is
 * covered by [PrepackagedBibleDbTest] against the shipped asset.
 */
class BibleRepositoryTest {
    private val bibleDao = FakeBibleDao()
    private val progressDao = FakeReadingProgressDao()
    private val repository = BibleRepository(bibleDao, progressDao)

    @Test
    fun `lists every book in canon order`() = runTest {
        bibleDao.bookRows += genesis
        bibleDao.bookRows += exodus

        assertEquals(listOf("GEN", "EXO"), repository.books().map { it.id })
    }

    @Test
    fun `maps a book row onto the domain model`() = runTest {
        bibleDao.bookRows += genesis

        val book = repository.book("GEN")

        requireNotNull(book)
        assertEquals("GEN", book.id)
        assertEquals("創世記", book.nameZh)
        assertEquals("Genesis", book.nameEn)
        assertEquals(50, book.chapters)
        assertEquals(Testament.OLD, book.testament)
    }

    @Test
    fun `returns null for a book that is not in the table`() = runTest {
        assertNull(repository.book("GEN"))
    }

    @Test
    fun `filters the library by testament`() = runTest {
        bibleDao.bookRows += genesis
        bibleDao.bookRows += john

        assertEquals(listOf("GEN"), repository.booksByTestament(Testament.OLD).map { it.id })
        assertEquals(listOf("JHN"), repository.booksByTestament(Testament.NEW).map { it.id })
    }

    @Test
    fun `pairs a verse that only one translation has without dropping the row`() = runTest {
        // CUV and WEB differ in length in some chapters, so the longer one decides the row count and
        // the shorter one's gap becomes an empty string: `legacy/flutter/lib/bible_data.dart:33`.
        bibleDao.verseRows += verse(1, textCuv = "神說", textWeb = "God said")
        bibleDao.verseRows += verse(2, textCuv = null, textWeb = "let there be light")

        val chapter = repository.chapter("GEN", 1)

        assertEquals(listOf("神說", ""), chapter.map { it.zh })
        assertEquals(listOf("God said", "let there be light"), chapter.map { it.en })
        assertEquals(listOf(1, 2), chapter.map { it.number })
    }

    @Test
    fun `returns an empty chapter rather than throwing on an unknown one`() = runTest {
        bibleDao.verseRows += verse(1, textCuv = "起初", textWeb = "In the beginning")

        assertEquals(0, repository.chapter("GEN", 51).size)
    }

    @Test
    fun `round trips a reading position through the row`() = runTest {
        repository.saveProgress(
            ReadingProgress(
                bookId = "GEN",
                chapter = 2,
                verse = 7,
                scrollRatio = 0.42f,
                mode = ReadingMode.BILINGUAL,
                updatedAt = 1_700_000_000_000,
            ),
        )

        val stored = repository.progress("GEN")
        requireNotNull(stored)
        assertEquals(2, stored.chapter)
        assertEquals(7, stored.verse)
        assertEquals(0.42f, stored.scrollRatio)
        assertEquals(ReadingMode.BILINGUAL, stored.mode)
        assertEquals(1_700_000_000_000, stored.updatedAt)
    }

    @Test
    fun `saves over a previous position instead of failing on the primary key`() = runTest {
        repository.saveProgress(progress(scrollRatio = 0.1f))
        repository.saveProgress(progress(scrollRatio = 0.9f))

        assertEquals(1, progressDao.rows.size)
        assertEquals(0.9f, repository.progress("GEN")?.scrollRatio)
    }

    @Test
    fun `deletes a position`() = runTest {
        repository.saveProgress(progress())

        repository.deleteProgress("GEN")

        assertNull(repository.progress("GEN"))
    }

    private fun progress(scrollRatio: Float = 0f) = ReadingProgress(
        bookId = "GEN",
        chapter = 1,
        verse = null,
        scrollRatio = scrollRatio,
        mode = ReadingMode.CHINESE,
        updatedAt = 0L,
    )

    private fun verse(number: Int, textCuv: String?, textWeb: String?) =
        VerseEntity(bookId = "GEN", chapter = 1, verse = number, textCuv = textCuv, textWeb = textWeb)

    private companion object {
        val genesis = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)
        val exodus = BookEntity("EXO", 2, "出埃及記", "Exodus", 40, 0)
        val john = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)
    }

    private class FakeBibleDao : BibleDao {
        val bookRows = mutableListOf<BookEntity>()
        val verseRows = mutableListOf<VerseEntity>()

        override suspend fun books(): List<BookEntity> = bookRows.sortedBy { it.ordinal }

        override suspend fun book(book: String): BookEntity? = bookRows.firstOrNull { it.id == book }

        override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> =
            verseRows.filter { it.bookId == book && it.chapter == chapter }.sortedBy { it.verse }

        override suspend fun versesByTestament(testament: Int): List<VerseEntity> =
            verseRows.filter { verse -> bookRows.any { it.id == verse.bookId && it.testament == testament } }

        override suspend fun booksByTestament(testament: Int): List<BookEntity> =
            bookRows.filter { it.testament == testament }.sortedBy { it.ordinal }

        // The search SQL is [BibleSearchTest]'s, against the shipped asset; this fake is here for
        // the repository's own reads.
        override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
    }

    private class FakeReadingProgressDao : ReadingProgressDao {
        val rows = mutableMapOf<String, ReadingProgressEntity>()

        override suspend fun progress(book: String): ReadingProgressEntity? = rows[book]

        override suspend fun allProgress(): List<ReadingProgressEntity> =
            rows.values.sortedByDescending { it.updatedAt }

        override suspend fun upsert(progress: ReadingProgressEntity) {
            rows[progress.bookId] = progress
        }

        override suspend fun delete(book: String) {
            rows.remove(book)
        }
    }
}
