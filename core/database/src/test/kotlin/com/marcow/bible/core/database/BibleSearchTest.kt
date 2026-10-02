package com.marcow.bible.core.database

import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Runs the search SQL of [BibleDao.searchContains] against the committed pre-packaged database.
 *
 * The SQL is repeated here instead of being extracted, because Room needs Android to run and CI has
 * no emulator — the same trade `PrepackagedBibleDbTest` makes. What is under test is the part that
 * is easy to get wrong and impossible to notice by reading: that a `LIKE` search with the wildcards
 * escaped returns the rows `BibleRepository.search` returned in Flutter, in the same order, capped
 * at the same count, and that the flattened projection still describes the book.
 */
class BibleSearchTest {
    private val asset: Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve("app/src/main/assets/databases/bible.db") }
            .firstOrNull(Files::exists)
            ?: error("pre-packaged asset not found from ${System.getProperty("user.dir")}")

    /** The query of [BibleDao.searchContains], verbatim, against a plain JDBC connection. */
    private fun search(pattern: String, limit: Int): List<ScriptureSearchRow> = withDb { db ->
        db.prepareStatement(
            """
            SELECT v.book_id AS book_id, v.chapter AS chapter, v.verse AS verse,
                   v.text_cuv AS text_cuv, v.text_web AS text_web,
                   b.ordinal AS book_ordinal, b.name_zh AS book_name_zh,
                   b.name_en AS book_name_en, b.chapters AS book_chapters,
                   b.testament AS book_testament
            FROM verses v
            JOIN books b ON b.id = v.book_id
            WHERE v.text_cuv LIKE ? ESCAPE '\'
               OR v.text_web LIKE ? ESCAPE '\'
            ORDER BY b.ordinal, v.chapter, v.verse
            LIMIT ?
            """,
        ).use { statement ->
            statement.setString(1, pattern)
            statement.setString(2, pattern)
            statement.setInt(3, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.toRow())
                }
            }
        }
    }

    @Test
    fun `a Chinese query finds the verse the Flutter build found`() {
        val pattern = searchPattern("神愛世人")!!

        val hits = search(pattern, SEARCH_RESULT_LIMIT)

        assertEquals(1, hits.size)
        val hit = hits.single().toDomain()
        assertEquals("JHN", hit.book.id)
        assertEquals(3, hit.chapter)
        assertEquals(16, hit.verse.number)
        assertTrue(hit.verse.zh.contains("神愛世人"), "got ${hit.verse.zh}")
    }

    @Test
    fun `an English query ignores case as the lowercased Flutter search did`() {
        val needle = "God so loved".lowercase()

        val hits = search(searchPattern(needle)!!, SEARCH_RESULT_LIMIT)

        assertEquals(listOf("JHN" to 16), hits.map { it.bookId to it.verse })
    }

    @Test
    fun `results arrive in canon order and stop at the limit`() {
        val pattern = searchPattern("的")!!

        val page = search(pattern, SEARCH_RESULT_LIMIT)
        val everything = search(pattern, SEARCH_RESULT_LIMIT * 10)

        assertEquals(SEARCH_RESULT_LIMIT, page.size)
        assertTrue(everything.size > SEARCH_RESULT_LIMIT, "the query should overflow one page")
        // Flutter walked the books in canon order and stopped at 80, so a limited page is a prefix
        // of the same walk — not a cheaper ordering.
        assertEquals(everything.take(SEARCH_RESULT_LIMIT), page)
        val ordinals = page.map { it.bookOrdinal }
        assertEquals(ordinals.sorted(), ordinals, "canon order")
    }

    @Test
    fun `a wildcard in the query is a character to find, not a wildcard`() {
        // Unescaped, `LIKE '%%%'` would match all 31107 verses.
        assertTrue(search(searchPattern("%")!!, SEARCH_RESULT_LIMIT).isEmpty())
        assertTrue(search(searchPattern("_")!!, SEARCH_RESULT_LIMIT).isEmpty())
        assertTrue(search(searchPattern("\\")!!, SEARCH_RESULT_LIMIT).isEmpty())
    }

    @Test
    fun `a query with nothing in it searches for nothing`() {
        assertNull(searchPattern(""))
        assertNull(searchPattern("   "))
    }

    @Test
    fun `the projection still describes the book`() {
        val row = search(searchPattern("神愛世人")!!, SEARCH_RESULT_LIMIT).single()

        assertEquals(43, row.bookOrdinal)
        assertEquals("約翰福音", row.bookNameZh)
        assertEquals("John", row.bookNameEn)
        assertEquals(21, row.bookChapters)
        assertEquals(Testament.NEW, row.toDomain().book.testament)
    }

    /**
     * `DriverManager` rather than `org.sqlite.JDBC.createConnection`, whose only overload wants a
     * `Properties` as its second argument and so has no one-argument form to call. sqlite-jdbc
     * registers itself as a JDBC driver, so the URL opens the same database either way.
     */
    private fun <T> withDb(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$asset").use(block)

    private fun ResultSet.toRow(): ScriptureSearchRow = ScriptureSearchRow(
        bookId = getString("book_id"),
        chapter = getInt("chapter"),
        verse = getInt("verse"),
        textCuv = getString("text_cuv"),
        textWeb = getString("text_web"),
        bookOrdinal = getInt("book_ordinal"),
        bookNameZh = getString("book_name_zh"),
        bookNameEn = getString("book_name_en"),
        bookChapters = getInt("book_chapters"),
        bookTestament = getInt("book_testament"),
    )
}
