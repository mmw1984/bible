package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.BookEntity
import com.marcow.bible.core.database.SEARCH_RESULT_LIMIT
import com.marcow.bible.core.database.ScriptureSearchRow
import com.marcow.bible.core.database.VerseEntity
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `BibleRepository.search` in `legacy/flutter/lib/bible_data.dart`, as the text half of the sheet.
 *
 * The Flutter build walked the books in canon order and stopped at 80 hits; the DAO query this asks
 * is that walk as one `ORDER BY` plus a `LIMIT`, and `BibleSearchTest` runs that SQL against the
 * shipped database. What is left is the seam between them, and it is worth pinning on its own
 * because two of its three behaviours decide what the sheet draws:
 *
 *  - the limit the sheet's `80+` label is about, which is the number this passes down;
 *  - a blank box, which is not a search that finds everything;
 *  - a failing lookup, which must *escape* — the sheet's `search_status` panel is drawn by catching
 *    it, so swallowing it here would leave a failed search looking like an empty one.
 */
class TraditionalSearchUseCaseTest {
    @Test
    fun `a query with nothing in it asks the database for nothing`() = runTest {
        val dao = RecordingBibleDao()

        assertEquals(emptyList<ScriptureHit>(), run(dao, "   "))
        assertNull(dao.lastPattern)
        // `return const []` for an empty needle: searching for nothing is not a search that finds
        // everything, which is what an unescaped `%%` would have done.
        assertEquals(0, dao.callCount)
    }

    @Test
    fun `the cut-off the label promises is the limit the query is given`() = runTest {
        val dao = RecordingBibleDao()

        run(dao, "愛")

        assertEquals(SEARCH_RESULT_LIMIT, dao.lastLimit)
    }

    @Test
    fun `the pattern reaches the query with the wildcards escaped`() = runTest {
        val dao = RecordingBibleDao()

        run(dao, "  100% Love  ")

        // Lower-cased and wrapped, because SQLite's `LIKE` is already case-insensitive for ASCII and
        // CJK has no case. `BibleSearchTest` owns the escaping itself; this is the query arriving.
        assertEquals("%100\\% love%", dao.lastPattern)
    }

    @Test
    fun `a row becomes a tile carrying the book the projection flattened into it`() = runTest {
        val dao = RecordingBibleDao(rows = listOf(SEARCH_ROW))

        val hit = run(dao, "愛").single()

        assertEquals("JHN", hit.book.id)
        assertEquals("約翰福音", hit.book.nameZh)
        assertEquals("John", hit.book.nameEn)
        assertEquals(Testament.NEW, hit.book.testament)
        assertEquals(3, hit.chapter)
        assertEquals(16, hit.verse.number)
    }

    @Test
    fun `a verse one translation has and the other does not keeps its row`() = runTest {
        // The search SQL projects the two texts as separate nullable columns, so a row the English
        // side does not have must still draw — with an empty side, not as a dropped tile.
        val dao = RecordingBibleDao(rows = listOf(SEARCH_ROW.copy(textWeb = null)))

        val hit = run(dao, "愛").single()

        assertEquals(VERSE_16_ZH, hit.verse.zh)
        assertEquals("", hit.verse.en)
    }

    @Test
    fun `a lookup that fails escapes rather than coming back empty`() = runTest {
        // The sheet's `search_status` panel is drawn by catching this. Returning an empty list instead
        // would show `no_results` for a database that could not answer, which is a lie about the app.
        val dao = RecordingBibleDao(failure = IllegalStateException("no such column: text_cuv"))

        val failure = assertThrows<IllegalStateException> { run(dao, "愛") }

        assertEquals("no such column: text_cuv", failure.message)
    }

    @Test
    fun `nothing that matches is an empty list and not a failure`() = runTest {
        assertEquals(emptyList<ScriptureHit>(), run(RecordingBibleDao(), "找不到的字"))
    }

    private suspend fun run(dao: BibleDao, query: String) = TraditionalSearchUseCase(dao).invoke(query)
}

private const val VERSE_16_ZH = "神愛世人，甚至將他的獨生子賜給他們。"

private const val VERSE_16_EN = "For God so loved the world that he gave his only Son."

private val SEARCH_ROW = ScriptureSearchRow(
    bookId = "JHN",
    chapter = 3,
    verse = 16,
    textCuv = VERSE_16_ZH,
    textWeb = VERSE_16_EN,
    bookOrdinal = 43,
    bookNameZh = "約翰福音",
    bookNameEn = "John",
    bookChapters = 21,
    bookTestament = 1,
)

/**
 * The search projection, hand-built rather than queried.
 *
 * `BibleSearchTest` runs [BibleDao.searchContains]'s SQL against the committed database and is where
 * the `ORDER BY`, the `LIKE` and the cap are proved; this fake is here so the seam above it can be
 * checked without one.
 */
private class RecordingBibleDao(
    private val rows: List<ScriptureSearchRow> = emptyList(),
    private val failure: Throwable? = null,
) : BibleDao {
    var lastPattern: String? = null

    var lastLimit: Int? = null

    var callCount = 0

    override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> {
        callCount++
        lastPattern = pattern
        lastLimit = limit
        failure?.let { throw it }
        return rows
    }

    override suspend fun books(): List<BookEntity> = emptyList()

    override suspend fun book(book: String): BookEntity? = null

    override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> = emptyList()

    override suspend fun versesByTestament(testament: Int): List<VerseEntity> = emptyList()

    override suspend fun booksByTestament(testament: Int): List<BookEntity> = emptyList()
}
