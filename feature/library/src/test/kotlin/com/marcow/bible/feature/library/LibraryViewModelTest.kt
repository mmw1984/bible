package com.marcow.bible.feature.library

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.database.BookEntity
import com.marcow.bible.core.database.ReadingProgressDao
import com.marcow.bible.core.database.ReadingProgressEntity
import com.marcow.bible.core.database.ScriptureSearchRow
import com.marcow.bible.core.database.VerseEntity
import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The two things the panel reads, over a faked database.
 *
 * The books are the interesting half. Flutter's panel filtered a list compiled into the binary, so
 * the sheet could not be empty and could not be late; here it can be both, and the two questions a
 * reader would actually notice are whether the list arrives in canon order and whether a failure
 * leaves something closable rather than a blank screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    /** One dispatcher for the test body and for `viewModelScope`, so nothing runs off-scheduler. */
    private val dispatcher = StandardTestDispatcher()
    private val bibleDao = FakeBibleDao()
    private val progressDao = FakeReadingProgressDao()
    private val settingsRepository = SettingsRepository(InMemorySettingsDataStore())

    @BeforeEach
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
        bibleDao.bookRows += genesis
        bibleDao.bookRows += exodus
        bibleDao.bookRows += john
    }

    @AfterEach
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads every book in canon order rather than one testament`() = runTest(dispatcher) {
        val viewModel = library()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        // All three, not the Old Testament's two: the panel filters in memory, so switching between
        // the two halves is instant and costs nothing to have loaded.
        assertEquals(listOf("GEN", "EXO", "JHN"), state.books.map { it.id })
        assertEquals(listOf("GEN", "EXO"), state.booksIn(Testament.OLD).map { it.id })
        assertEquals(listOf("JHN"), state.booksIn(Testament.NEW).map { it.id })
    }

    @Test
    fun `a book that fails to load leaves a panel the reader can close`() = runTest(dispatcher) {
        bibleDao.failing = true
        val viewModel = library()
        advanceUntilIdle()

        val state = viewModel.state.value
        // Flutter's panel could not fail, because its list was a constant. An empty list under the
        // title is the equivalent here: something to look at, something to dismiss, no crash.
        assertEquals(emptyList<BibleBook>(), state.books)
        assertFalse(state.loading)
    }

    @Test
    fun `the book name follows the stored interface language`() = runTest(dispatcher) {
        val viewModel = library()
        advanceUntilIdle()

        // The default locale is zh-Hant, so the list starts on the Chinese names.
        assertFalse(viewModel.state.value.usesEnglishUi)

        settingsRepository.setLocale(AppLocale.EN)
        advanceUntilIdle()

        // Flutter's `_bookName` reached for the ambient settings on every build, so a language change
        // relabelled the list without the reader having moved. A name is not text, so nothing is
        // refetched and the books stay exactly where they were.
        val state = viewModel.state.value
        assertTrue(state.usesEnglishUi)
        assertEquals(listOf("GEN", "EXO", "JHN"), state.books.map { it.id })
    }

    private fun library() = LibraryViewModel(
        BibleRepository(bibleDao, progressDao),
        settingsRepository,
    )

    private class FakeBibleDao : BibleDao {
        val bookRows = mutableListOf<BookEntity>()

        /** Set to make [books] throw, standing in for a database that will not open. */
        var failing = false

        override suspend fun books(): List<BookEntity> {
            check(!failing) { "no such table: books" }
            return bookRows.sortedBy { it.ordinal }
        }

        override suspend fun book(book: String): BookEntity? = bookRows.firstOrNull { it.id == book }

        override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> = emptyList()

        override suspend fun versesByTestament(testament: Int): List<VerseEntity> = emptyList()

        override suspend fun booksByTestament(testament: Int): List<BookEntity> =
            bookRows.filter { it.testament == testament }.sortedBy { it.ordinal }

        override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
    }

    private class FakeReadingProgressDao : ReadingProgressDao {
        private val rows = mutableMapOf<String, ReadingProgressEntity>()

        override suspend fun progress(book: String): ReadingProgressEntity? = rows[book]

        override suspend fun allProgress(): List<ReadingProgressEntity> = emptyList()

        override suspend fun upsert(progress: ReadingProgressEntity) {
            rows[progress.bookId] = progress
        }

        override suspend fun delete(book: String) {
            rows.remove(book)
        }
    }

    private companion object {
        val genesis = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)
        val exodus = BookEntity("EXO", 2, "出埃及記", "Exodus", 40, 0)
        val john = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)
    }
}
