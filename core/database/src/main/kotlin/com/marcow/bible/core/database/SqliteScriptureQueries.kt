package com.marcow.bible.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.ScriptureRef
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the prepackaged scripture out of SQLite.
 *
 * Every method blocks; callers run on `Dispatchers.IO`.
 */
@Singleton
class SqliteScriptureQueries @Inject constructor(
    private val connection: ScriptureConnection,
) : ScriptureQueries {

    override fun books(): List<BibleBook> =
        connection.read { db -> db.query(ALL_BOOKS_SQL).use(::readBooks) }

    override fun book(bookId: String): BibleBook? =
        connection.read { db ->
            db.query(BOOKS_SQL, arrayOf(bookId)).use { cursor -> readBooks(cursor).firstOrNull() }
        }

    override fun booksByTestament(testament: Testament): List<BibleBook> =
        connection.read { db ->
            db.query(BOOKS_BY_TESTAMENT_SQL, arrayOf(testament.ordinal.toString()))
                .use(::readBooks)
        }

    override fun chapter(bookId: String, chapter: Int): List<VersePair> {
        if (chapter < 1) return emptyList()
        return connection.read { db ->
            db.query(VERSES_SQL, arrayOf(bookId, chapter.toString())).use(::readVerses)
        }
    }

    override fun verse(bookId: String, chapter: Int, verse: Int): VersePair? =
        chapter(bookId, chapter).firstOrNull { it.number == verse }

    override fun search(query: String, limit: Int): List<ScriptureHit> {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        // SQLite LIKE is case-insensitive for ASCII, which is what the Dart
        // build got from `toLowerCase().contains(...)` on both translations.
        val pattern = "%${escapeLike(needle)}%"
        return connection.read { db ->
            db.rawQuery(SEARCH_SQL, arrayOf(pattern, pattern, limit.toString())).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ScriptureHit(
                                book = readBook(cursor),
                                chapter = cursor.getInt(CHAPTER),
                                verse = readVerse(cursor, VERSE),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun rangeText(ref: ScriptureRef): String {
        val book = book(ref.bookId) ?: return ""
        val end = minOf(ref.verseEnd, ref.verseStart + ScriptureQueries.MAX_RANGE_VERSES - 1)
        return chapter(ref.bookId, ref.chapter)
            .filter { it.number in ref.verseStart..end }
            .joinToString("\n\n") { verse ->
                "${book.nameZh} ${ref.chapter}:${verse.number}\n" +
                    "中文：${verse.zh}\nEnglish: ${verse.en}"
            }
    }

    override fun progress(bookId: String): ReadingProgress? =
        connection.read { db ->
            db.query(PROGRESS_SQL, arrayOf(bookId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    ReadingProgress(
                        bookId = bookId,
                        chapter = cursor.getInt(0),
                        verse = cursor.intOrNull(PROGRESS_VERSE),
                        scrollRatio = cursor.getFloat(2),
                        mode = ReadingMode.fromStorageKey(cursor.getString(3)),
                        updatedAtMillis = cursor.getLong(4),
                    )
                }
            }
        }

    override fun saveProgress(progress: ReadingProgress) {
        connection.write { db ->
            db.execSQL(
                UPSERT_PROGRESS_SQL,
                arrayOf(
                    progress.bookId,
                    progress.chapter,
                    progress.verse?.toString(),
                    progress.scrollRatio.coerceIn(0f, 1f).toString(),
                    progress.mode.storageKey,
                    progress.updatedAtMillis,
                ),
            )
        }
    }

    private fun readBooks(cursor: Cursor): List<BibleBook> = buildList {
        while (cursor.moveToNext()) {
            add(readBook(cursor))
        }
    }

    private fun readVerses(cursor: Cursor): List<VersePair> = buildList {
        while (cursor.moveToNext()) {
            add(readVerse(cursor, VERSE))
        }
    }

    /** Both queries that reach this point select the `books` columns in schema order. */
    private fun readBook(cursor: Cursor) = BibleBook(
        id = cursor.getString(0),
        ordinal = cursor.getInt(1),
        nameZh = cursor.getString(2),
        nameEn = cursor.getString(3),
        chapters = cursor.getInt(4),
        testament = Testament.fromOrdinal(cursor.getInt(5)),
    )

    private fun readVerse(cursor: Cursor, firstIndex: Int) = VersePair(
        number = cursor.getInt(firstIndex),
        // A translation short of this verse stores NULL; the Flutter build showed
        // that as an empty string, so keep doing that.
        zh = cursor.stringOrEmpty(firstIndex + 1),
        en = cursor.stringOrEmpty(firstIndex + 2),
    )

    private fun Cursor.intOrNull(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private fun Cursor.stringOrEmpty(index: Int): String =
    if (isNull(index)) "" else getString(index)

/**
 * Escapes the SQL LIKE wildcards in a user query.
 *
 * The Dart search was a plain `String.contains`, where `%` and `_` are literal.
 * SQLite LIKE would treat them as wildcards, so searching for "100%" would match
 * every row, hence the ESCAPE clause in SEARCH_SQL.
 */
internal fun escapeLike(value: String): String = buildString(value.length) {
    for (character in value) {
        if (character == '%' || character == '_' || character == '\\') append('\\')
        append(character)
    }
}

    private companion object {
        /** Column offsets of the joined result sets. */
        const val CHAPTER = 6
        const val VERSE = 7

        /** Offset of `verse` inside PROGRESS_SQL. */
        const val PROGRESS_VERSE = 1

        const val BOOKS_SQL = "SELECT id, ordinal, name_zh, name_en, chapters, testament FROM books WHERE id = ?"
        const val BOOKS_BY_TESTAMENT_SQL =
            "SELECT id, ordinal, name_zh, name_en, chapters, testament FROM books " +
                "WHERE testament = ? ORDER BY ordinal"
        const val ALL_BOOKS_SQL =
            "SELECT id, ordinal, name_zh, name_en, chapters, testament FROM books ORDER BY ordinal"

        const val VERSES_SQL =
            "SELECT verse, text_cuv, text_web FROM verses " +
                "WHERE book_id = ? AND chapter = ? ORDER BY verse"

        const val PROGRESS_SQL =
            "SELECT chapter, verse, scroll_ratio, mode, updated_at FROM reading_progress " +
                "WHERE book_id = ?"

        const val UPSERT_PROGRESS_SQL = """
            INSERT INTO reading_progress (book_id, chapter, verse, scroll_ratio, mode, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(book_id) DO UPDATE SET
                chapter = excluded.chapter,
                verse = excluded.verse,
                scroll_ratio = excluded.scroll_ratio,
                mode = excluded.mode,
                updated_at = excluded.updated_at
        """

        /** Canonical order, matching the Dart loop over `bibleBooks`. */
        const val SEARCH_SQL = """
            SELECT b.id, b.ordinal, b.name_zh, b.name_en, b.chapters, b.testament,
                   v.chapter, v.verse, v.text_cuv, v.text_web
            FROM verses v JOIN books b ON b.id = v.book_id
            WHERE v.text_cuv LIKE ? ESCAPE '\' OR v.text_web LIKE ? ESCAPE '\'
            ORDER BY b.ordinal, v.chapter, v.verse
            LIMIT ?
        """
    }
}