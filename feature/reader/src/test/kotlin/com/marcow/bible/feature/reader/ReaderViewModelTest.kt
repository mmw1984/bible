package com.marcow.bible.feature.reader

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
import com.marcow.bible.core.model.ReadingMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What the reader does with a position, over faked DAOs.
 *
 * Every case here is one the Flutter reader's behaviour made observable: which position the app
 * reopened at, that a navigation carries across a book boundary, that the chapter being left keeps
 * the offset it had reached, and that a chapter which fails to load is never replaced by the
 * chapter that was on screen a moment earlier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
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
        bibleDao.verseRows += verse("GEN", 1, 1)
        bibleDao.verseRows += verse("GEN", 2, 1)
        bibleDao.verseRows += verse("EXO", 1, 1)
        bibleDao.verseRows += verse("EXO", 7, 1)
        bibleDao.verseRows += verse("JHN", 21, 1)
    }

    @AfterEach
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens the most recently read position rather than the first book`() = runTest(dispatcher) {
        progressDao.rows["GEN"] = progress("GEN", chapter = 3, updatedAt = 10)
        progressDao.rows["EXO"] = progress(
            bookId = "EXO",
            chapter = 7,
            mode = ReadingMode.ENGLISH,
            scrollRatio = 0.25f,
            updatedAt = 20,
        )
        val viewModel = reader()
        advanceUntilIdle()

        val state = viewModel.state.value

        assertEquals("EXO", state.book?.id)
        assertEquals(7, state.chapter)
        assertEquals(ReadingMode.ENGLISH, state.mode)
        assertFalse(state.loading)
        // The stored ratio is handed to the screen once the chapter is laid out, not before.
        assertEquals(0.25f, state.scrollToRatio)
    }

    @Test
    fun `a fresh install opens Genesis 1 in Chinese`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        val state = viewModel.state.value

        assertEquals("GEN", state.book?.id)
        assertEquals(1, state.chapter)
        assertEquals(ReadingMode.CHINESE, state.mode)
        assertEquals(listOf("起初"), state.verses.map { it.zh })
        assertNull(state.scrollToRatio)
        // Opening is not a write: Flutter only wrote a position once the reader had moved.
        assertEquals(0, progressDao.writes.size)
    }

    @Test
    fun `next chapter carries on into the following book`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.selectNextChapter()
        advanceUntilIdle()
        assertEquals("GEN", viewModel.state.value.book?.id)
        assertEquals(2, viewModel.state.value.chapter)

        // Fifty chapters later the direction has to become the next book, or "next" stops at a
        // boundary the reader does not know is there.
        viewModel.selectChapter(50)
        advanceUntilIdle()
        viewModel.selectNextChapter()
        advanceUntilIdle()

        assertEquals("EXO", viewModel.state.value.book?.id)
        assertEquals(1, viewModel.state.value.chapter)
    }

    @Test
    fun `previous chapter carries back into the end of the previous book`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        viewModel.selectBook("EXO")
        viewModel.selectChapter(7)
        advanceUntilIdle()

        viewModel.selectPreviousChapter()
        advanceUntilIdle()
        assertEquals(6, viewModel.state.value.chapter)

        // Rolling back from chapter one lands on the last chapter of the previous book, which is
        // what makes the two directions read as one continuous sequence.
        viewModel.selectChapter(1)
        advanceUntilIdle()
        viewModel.selectPreviousChapter()
        advanceUntilIdle()

        assertEquals("GEN", viewModel.state.value.book?.id)
        assertEquals(50, viewModel.state.value.chapter)
    }

    @Test
    fun `the directions stop at the ends of the Bible`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        // Genesis 1 is the first chapter there is, and John stands in for the last book here.
        viewModel.selectPreviousChapter()
        advanceUntilIdle()
        assertEquals("GEN", viewModel.state.value.book?.id)
        assertEquals(1, viewModel.state.value.chapter)

        viewModel.selectBook("JHN")
        viewModel.selectChapter(21)
        advanceUntilIdle()
        viewModel.selectNextChapter()
        advanceUntilIdle()

        assertEquals("JHN", viewModel.state.value.book?.id)
        assertEquals(21, viewModel.state.value.chapter)
    }

    @Test
    fun `choosing a book opens its first chapter in Chinese`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        viewModel.selectMode(ReadingMode.BILINGUAL)
        advanceUntilIdle()

        viewModel.selectBook("JHN")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("JHN", state.book?.id)
        assertEquals(1, state.chapter)
        assertEquals(ReadingMode.CHINESE, state.mode)
    }

    @Test
    fun `a chapter outside the book is clamped rather than refused`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.selectChapter(99)
        advanceUntilIdle()

        // Genesis has 50. Flutter clamped with `clamp(1, book.chapters)` when it restored a chapter.
        assertEquals(50, viewModel.state.value.chapter)
        assertTrue(viewModel.state.value.verses.isEmpty())
        assertFalse(viewModel.state.value.failed)
    }

    @Test
    fun `switching reading mode keeps the chapter and records the mode`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.selectMode(ReadingMode.BILINGUAL)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ReadingMode.BILINGUAL, state.mode)
        assertEquals(1, state.chapter)
        // Both translations come from the same rows, so the chapter does not have to be fetched
        // again — which is what Flutter did with a `setState`.
        assertEquals(listOf("起初"), state.verses.map { it.zh })
        assertEquals(ReadingMode.BILINGUAL.storageValue, progressDao.rows["GEN"]?.mode)
    }

    @Test
    fun `a chapter that fails to load is an error rather than the verses already on screen`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        bibleDao.failing += "GEN:2"

        viewModel.selectChapter(2)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertTrue(state.failed)
        // The failed chapter is empty rather than the previous chapter's verses under a new title.
        assertTrue(state.verses.isEmpty())
    }

    @Test
    fun `the chapter being replaced is dropped before the next one arrives`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        // Park the next query so the state can be read in the window Flutter's `FutureBuilder` had
        // between being given a new future and its data arriving.
        val chapterArrived = CompletableDeferred<Unit>()
        bibleDao.gate = chapterArrived

        viewModel.selectChapter(2)
        advanceUntilIdle()

        val loading = viewModel.state.value
        assertEquals(2, loading.chapter)
        assertTrue(loading.loading)
        // Genesis 1's verses are gone while the title already says chapter 2.
        assertTrue(loading.verses.isEmpty())

        chapterArrived.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("起初"), viewModel.state.value.verses.map { it.zh })
    }

    @Test
    fun `scrolling writes one row once the reader settles`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.onScrolled(0.4f)
        // A millisecond short of the 180 ms Flutter waited before writing.
        advanceTimeBy(179)
        assertEquals(0, progressDao.writes.size)

        advanceUntilIdle()

        assertEquals(0.4f, progressDao.rows["GEN"]?.scrollRatio)
        assertEquals(1, progressDao.writes.size)
    }

    @Test
    fun `leaving a chapter writes it where it was left, before the new one`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        viewModel.onScrolled(0.5f)

        viewModel.selectChapter(2)
        advanceUntilIdle()

        assertEquals(
            listOf(1 to 0.5f, 2 to 0f),
            progressDao.writes.map { it.chapter to it.scrollRatio },
        )
    }

    /**
     * Flutter's `dispose`, which flushed the queue before the 180 ms debounce had fired.
     *
     * [ReaderRoute] calls [ReaderViewModel.savePosition] on `ON_PAUSE`, so this is the same flush
     * Flutter's `tester.pumpWidget(const SizedBox.shrink())` triggered: the reader is leaving while
     * an offset is still waiting to be written, and the offset it left at is the one that has to
     * reach the row.
     */
    @Test
    fun `leaving while the debounce is still pending writes the offset now`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        viewModel.onScrolled(0.62f)
        advanceTimeBy(179)
        assertEquals(0, progressDao.writes.size)

        viewModel.savePosition()
        advanceUntilIdle()

        assertEquals(0.62f, progressDao.rows["GEN"]?.scrollRatio)
        assertEquals(1, progressDao.writes.size)

        // The debounce was cancelled rather than left to run, so it cannot follow the flush with a
        // second write of a row that is already correct.
        advanceTimeBy(180)
        assertEquals(1, progressDao.writes.size)
    }

    /** And the row that flush wrote is where the next launch opens, rather than the top of the chapter. */
    @Test
    fun `an offset flushed on the way out is where the app reopens`() = runTest(dispatcher) {
        val leaving = reader()
        advanceUntilIdle()
        leaving.onScrolled(0.62f)
        advanceTimeBy(179)
        leaving.savePosition()
        advanceUntilIdle()

        val reopened = reader()
        advanceUntilIdle()

        assertEquals("GEN", reopened.state.value.book?.id)
        assertEquals(0.62f, reopened.state.value.scrollToRatio)
    }

    /**
     * A write the repository refused is kept for the next flush rather than dropped.
     *
     * Flutter's queue reported the failure and carried the write into the next one, because the
     * alternative is a reader who closes the app on a full disk and comes back to the top of a
     * chapter they had scrolled halfway through.
     */
    @Test
    fun `a write the database refused is retried by the next flush`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        progressDao.refuse = true

        viewModel.onScrolled(0.4f)
        advanceUntilIdle()
        assertTrue(progressDao.rows.isEmpty())

        progressDao.refuse = false
        viewModel.savePosition()
        advanceUntilIdle()

        assertEquals(0.4f, progressDao.rows["GEN"]?.scrollRatio)
    }

    @Test
    fun `an imported Flutter pixel offset becomes a ratio and is not applied twice`() = runTest(dispatcher) {
        settingsRepository.rememberLegacyScrollPx(400.0)
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.onChapterMeasured(maxScrollPx = 800f)
        advanceUntilIdle()

        assertEquals(0.5f, viewModel.state.value.scrollToRatio)
        assertNull(settingsRepository.pendingLegacyScrollPx())

        // The offset was cleared, and the flag keeps a later layout pass from applying it to a
        // chapter the reader has already scrolled in.
        viewModel.onScrollRestored()
        viewModel.onChapterMeasured(maxScrollPx = 1600f)
        advanceUntilIdle()

        assertNull(viewModel.state.value.scrollToRatio)
    }

    @Test
    fun `an offset beyond the chapter lands on the last verse rather than past it`() = runTest(dispatcher) {
        settingsRepository.rememberLegacyScrollPx(9_000.0)
        val viewModel = reader()
        advanceUntilIdle()

        viewModel.onChapterMeasured(maxScrollPx = 800f)
        advanceUntilIdle()

        assertEquals(1f, viewModel.state.value.scrollToRatio)
    }

    @Test
    fun `a catalogue with no books is a failure rather than an endless skeleton`() = runTest(dispatcher) {
        bibleDao.bookRows.clear()
        val viewModel = reader()
        advanceUntilIdle()

        val state = viewModel.state.value

        assertFalse(state.loading)
        assertTrue(state.failed)
        assertNull(state.book)
    }

    @Test
    fun `the book name follows the stored interface language`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()

        // The default locale is zh-Hant, so the state starts on the Chinese name.
        assertFalse(viewModel.state.value.usesEnglishUi)

        settingsRepository.setLocale(AppLocale.EN)
        advanceUntilIdle()

        // Flutter's `_bookName` reached for the ambient `AppSettingsScope` on every build, so a
        // language change in Settings repainted the title without the reader having moved. The one
        // boolean in the state is what carries that here; `ReaderLayoutTest` covers the naming.
        assertTrue(viewModel.state.value.usesEnglishUi)
    }

    @Test
    fun `switching the language does not disturb where the reader is`() = runTest(dispatcher) {
        val viewModel = reader()
        advanceUntilIdle()
        viewModel.selectChapter(2)
        advanceUntilIdle()
        viewModel.onScrolled(0.3f)
        advanceUntilIdle()
        val writesBefore = progressDao.writes.size

        settingsRepository.setLocale(AppLocale.EN)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("GEN", state.book?.id)
        assertEquals(2, state.chapter)
        assertEquals(ReadingMode.CHINESE, state.mode)
        assertEquals(listOf("起初"), state.verses.map { it.zh })
        // A language is not a navigation: the chapter is not refetched and the position is not
        // rewritten, so the row the reader left stays exactly as it was written.
        assertEquals(writesBefore, progressDao.writes.size)
    }

    private fun reader() = ReaderViewModel(
        BibleRepository(bibleDao, progressDao),
        settingsRepository,
    )

    private fun progress(
        bookId: String,
        chapter: Int = 1,
        mode: ReadingMode = ReadingMode.CHINESE,
        scrollRatio: Float = 0f,
        updatedAt: Long,
    ) = ReadingProgressEntity(
        bookId = bookId,
        chapter = chapter,
        verse = null,
        scrollRatio = scrollRatio,
        mode = mode.storageValue,
        updatedAt = updatedAt,
    )

    private fun verse(bookId: String, chapter: Int, number: Int) = VerseEntity(
        bookId = bookId,
        chapter = chapter,
        verse = number,
        textCuv = "起初",
        textWeb = "In the beginning",
    )

    private companion object {
        val genesis = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)
        val exodus = BookEntity("EXO", 2, "出埃及記", "Exodus", 40, 0)
        val john = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)
    }

    private class FakeBibleDao : BibleDao {
        val bookRows = mutableListOf<BookEntity>()
        val verseRows = mutableListOf<VerseEntity>()

        /** Chapters to refuse, as `BOOK:CHAPTER`, standing in for a query that throws. */
        val failing = mutableSetOf<String>()

        /** Held by [chapter] while set, so a test can look at the state before a chapter lands. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun books(): List<BookEntity> = bookRows.sortedBy { it.ordinal }

        override suspend fun book(book: String): BookEntity? = bookRows.firstOrNull { it.id == book }

        override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> {
            gate?.await()
            check("$book:$chapter" !in failing) { "no such chapter" }
            return verseRows.filter { it.bookId == book && it.chapter == chapter }.sortedBy { it.verse }
        }

        override suspend fun versesByTestament(testament: Int): List<VerseEntity> =
            verseRows.filter { verse -> bookRows.any { it.id == verse.bookId && it.testament == testament } }

        override suspend fun booksByTestament(testament: Int): List<BookEntity> =
            bookRows.filter { it.testament == testament }.sortedBy { it.ordinal }

        override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
    }

    private class FakeReadingProgressDao : ReadingProgressDao {
        val rows = mutableMapOf<String, ReadingProgressEntity>()

        /** Every upsert in order, which is the only way to see that a flush kept its ordering. */
        val writes = mutableListOf<ReadingProgressEntity>()

        /** Whether to refuse an upsert, standing in for a database that has failed a write. */
        var refuse = false

        override suspend fun progress(book: String): ReadingProgressEntity? = rows[book]

        override suspend fun allProgress(): List<ReadingProgressEntity> =
            rows.values.sortedByDescending { it.updatedAt }

        override suspend fun upsert(progress: ReadingProgressEntity) {
            check(!refuse) { "the database refused the write" }
            rows[progress.bookId] = progress
            writes += progress
        }

        override suspend fun delete(book: String) {
            rows.remove(book)
        }
    }
}
