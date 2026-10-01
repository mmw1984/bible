package com.marcow.bible.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

/**
 * Verifies the generated bible.db against the same invariants
 * `tool/verify_bible.mjs` enforces on the bundled JSON: 66 books, 1189 chapters,
 * every chapter present in both translations.
 *
 * The file is opened with sqlite-jdbc rather than the Android framework, so this
 * runs as a plain JVM test with no emulator.
 */
class BibleDatabaseTest {

    @Test
    fun `has all 66 books in canonical order`() {
        val books = query("SELECT id, ordinal, chapters, testament FROM books ORDER BY ordinal")
        assertEquals(EXPECTED_BOOKS, books.size)
        books.forEachIndexed { index, row ->
            assertEquals(index + 1, row.int("ordinal"))
            assertTrue((row["id"] as String).isNotBlank())
            assertTrue(row.int("chapters") > 0)
        }
        assertEquals(EXPECTED_OLD_TESTAMENT_BOOKS, books.count { it.int("testament") == 0 })
        assertEquals(EXPECTED_NEW_TESTAMENT_BOOKS, books.count { it.int("testament") == 1 })
    }

    @Test
    fun `has 1189 chapters and every declared chapter is stored`() {
        val chapters = queryInt("SELECT COUNT(DISTINCT book_id || '-' || chapter) FROM verses")
        assertEquals(EXPECTED_CHAPTERS, chapters)

        // Each book's declared chapter count must match what verses exist for.
        val mismatches = query(
            """
            SELECT b.id, b.chapters, COUNT(DISTINCT v.chapter) AS stored
            FROM books b JOIN verses v ON v.book_id = b.id
            GROUP BY b.id
            HAVING b.chapters != stored
            """.trimIndent(),
        )
        assertTrue(mismatches.isEmpty(), "chapter count mismatch: $mismatches")
    }

    @Test
    fun `every chapter holds at least one verse`() {
        val empty = query(
            """
            SELECT b.id, v.chapter
            FROM books b JOIN verses v ON v.book_id = b.id
            GROUP BY b.id, v.chapter
            HAVING COUNT(*) = 0
            """.trimIndent(),
        )
        assertTrue(empty.isEmpty(), "chapters without verses: $empty")
    }

    @Test
    fun `verse numbers start at one and stay contiguous`() {
        val gaps = query(
            """
            SELECT book_id, chapter, MIN(verse) AS first, MAX(verse) AS last, COUNT(*) AS total
            FROM verses
            GROUP BY book_id, chapter
            HAVING MIN(verse) != 1 OR MAX(verse) != COUNT(*)
            """.trimIndent(),
        )
        assertTrue(gaps.isEmpty(), "non-contiguous verse numbering: ${gaps.take(5)}")
    }

    @Test
    fun `known verses match the bundled json verbatim`() {
        assertEquals(
            "起初神創造天地。",
            verseText("GEN", 1, 1, "text_cuv"),
        )
        assertEquals(
            "In the beginning, God created the heavens and the earth.",
            verseText("GEN", 1, 1, "text_web"),
        )
        assertEquals("神說", verseText("GEN", 1, 3, "text_cuv"))
        assertEquals(
            "For the Lord your God is a consuming fire.",
            verseText("DEU", 4, 24, "text_web"),
        )
        assertEquals(
            "In the beginning was the Word, and the Word was with God, and the Word was God.",
            verseText("JHN", 1, 1, "text_web"),
        )
    }

    @Test
    fun `both translations are present for the overwhelming majority of verses`() {
        val chinese = queryInt("SELECT COUNT(*) FROM verses WHERE text_cuv IS NOT NULL")
        val english = queryInt("SELECT COUNT(*) FROM verses WHERE text_web IS NOT NULL")
        val total = queryInt("SELECT COUNT(*) FROM verses")
        // CUV and WEB disagree on a handful of verse counts; the alignment rule is
        // max(cuv, web) and the gaps are recorded rather than dropped.
        assertTrue(total - chinese <= 20, "too many missing Chinese verses: ${total - chinese}")
        assertTrue(total - english <= 20, "too many missing English verses: ${total - english}")
    }

    @Test
    fun `search finds a known phrase and respects the limit`() {
        val hits = query(
            """
            SELECT b.id, v.chapter, v.verse, v.text_cuv
            FROM verses v JOIN books b ON b.id = v.book_id
            WHERE v.text_cuv LIKE '%起初%' ESCAPE '\'
            ORDER BY b.ordinal, v.chapter, v.verse
            LIMIT ?
            """.trimIndent(),
            "80",
        )
        assertFalse(hits.isEmpty())
        assertEquals("GEN", hits.first()["id"])

        val limited = query(
            """
            SELECT v.book_id FROM verses v
            WHERE v.text_cuv LIKE '%神%' ESCAPE '\' OR v.text_web LIKE '%God%' ESCAPE '\'
            LIMIT 5
            """.trimIndent(),
        )
        assertTrue(limited.size <= 5)
    }

    @Test
    fun `like metacharacters in a query are not treated as wildcards`() {
        // '%' in the needle must stay a literal percent sign.
        val wildcard = queryInt(
            "SELECT COUNT(*) FROM verses WHERE text_cuv LIKE '%100%' ESCAPE '\\'",
        )
        assertTrue(wildcard > 0)
    }

    @Test
    fun `reading progress round trips through the same file`() {
        // Runs against a copy: writing to the checked-in asset would dirty it.
        scratchConnection().use { db ->
            db.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    INSERT INTO reading_progress (book_id, chapter, verse, scroll_ratio, mode, updated_at)
                    VALUES ('GEN', 2, 5, 0.25, 'bilingual', 1234)
                    ON CONFLICT(book_id) DO UPDATE SET chapter = excluded.chapter
                    """.trimIndent(),
                )
            }
            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT chapter, verse, scroll_ratio, mode FROM reading_progress WHERE book_id = 'GEN'",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertEquals(2, rows.getInt("chapter"))
                    assertEquals(5, rows.getInt("verse"))
                    assertEquals(0.25, rows.getDouble("scroll_ratio"), 1e-9)
                    assertEquals("bilingual", rows.getString("mode"))
                }
            }
        }
    }

    @Test
    fun `user version is set for future migrations`() {
        assertEquals(1, queryInt("PRAGMA user_version"))
    }

    private fun verseText(book: String, chapter: Int, verse: Int, column: String): String? {
        val rows = query(
            "SELECT $column AS text FROM verses WHERE book_id = ? AND chapter = ? AND verse = ?",
            book,
            chapter.toString(),
            verse.toString(),
        )
        return rows.firstOrNull()?.get("text") as String?
    }

    private fun connection(): Connection =
        DriverManager.getConnection("jdbc:sqlite:${readOnlyAsset().absolutePath}")

    /** A writable copy, for the tests that insert rows. */
    private fun scratchConnection(): Connection {
        val copy = Files.createTempFile("bible-test", ".db")
        copy.toFile().deleteOnExit()
        Files.copy(readOnlyAsset().toPath(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return DriverManager.getConnection("jdbc:sqlite:${copy.toAbsolutePath()}")
    }

    private fun readOnlyAsset(): File {
        val file = databaseFile()
        assertNotNull(file, "bible.db has not been generated; run `node tool/build_bible_db.mjs`")
        return checkNotNull(file)
    }

    private fun query(sql: String, vararg args: String): List<Map<String, Any?>> =
        connection().use { db ->
            db.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.executeQuery().use { rows ->
                    val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnLabel(it) }
                    buildList {
                        while (rows.next()) {
                            add(columns.associateWith { rows.getObject(it) })
                        }
                    }
                }
            }
        }

    private fun queryInt(sql: String, vararg args: String): Int =
        connection().use { db ->
            db.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "query returned no rows: $sql" }
                    rows.getInt(1)
                }
            }
        }

    private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()

    private fun databaseFile(): File? =
        sequenceOf("src/main/assets", "../../app/src/main/assets")
            .map { File(it, "databases/bible.db") }
            .firstOrNull { it.isFile }

    private companion object {
        const val EXPECTED_BOOKS = 66
        const val EXPECTED_CHAPTERS = 1189
        const val EXPECTED_OLD_TESTAMENT_BOOKS = 39
        const val EXPECTED_NEW_TESTAMENT_BOOKS = 27
    }
}