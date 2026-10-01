package com.marcow.bible.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Scripture reads. Every query is ordered by `ordinal` / `verse` so results match the order the
 * Flutter reader produced when it walked `bibleBooks` in canon order.
 */
@Dao
interface BibleDao {
    @Query("SELECT * FROM books ORDER BY ordinal")
    suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :book")
    suspend fun book(book: String): BookEntity?

    @Query("SELECT * FROM verses WHERE book_id = :book AND chapter = :chapter ORDER BY verse")
    suspend fun chapter(book: String, chapter: Int): List<VerseEntity>

    @Query(
        """
        SELECT v.* FROM verses v
        JOIN books b ON b.id = v.book_id
        WHERE b.testament = :testament
        ORDER BY b.ordinal, v.chapter, v.verse
        """,
    )
    suspend fun versesByTestament(testament: Int): List<VerseEntity>

    @Query("SELECT * FROM books WHERE testament = :testament ORDER BY ordinal")
    suspend fun booksByTestament(testament: Int): List<BookEntity>

    /**
     * Verses whose Chinese or English text contains [pattern], in canon order, capped at [limit].
     *
     * `BibleRepository.search` in `legacy/flutter/lib/bible_data.dart` walked the books in canon
     * order and stopped as soon as it had `limit` hits, so the same `ORDER BY` and `LIMIT` return
     * the same first rows. SQLite's `LIKE` is already case-insensitive for ASCII, which is what the
     * Flutter build's `toLowerCase().contains` achieved; CJK has no case, so it matches as-is.
     *
     * [pattern] comes from [searchPattern], which escapes the wildcards `LIKE` would otherwise read.
     */
    @Query(
        """
        SELECT v.book_id AS book_id, v.chapter AS chapter, v.verse AS verse,
               v.text_cuv AS text_cuv, v.text_web AS text_web,
               b.ordinal AS book_ordinal, b.name_zh AS book_name_zh,
               b.name_en AS book_name_en, b.chapters AS book_chapters,
               b.testament AS book_testament
        FROM verses v
        JOIN books b ON b.id = v.book_id
        WHERE v.text_cuv LIKE :pattern ESCAPE '\'
           OR v.text_web LIKE :pattern ESCAPE '\'
        ORDER BY b.ordinal, v.chapter, v.verse
        LIMIT :limit
        """,
    )
    suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow>
}

/** Reading position. One row per book, written by the reader from Phase 2 on. */
@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE book_id = :book")
    suspend fun progress(book: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY updated_at DESC")
    suspend fun allProgress(): List<ReadingProgressEntity>

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE book_id = :book")
    suspend fun delete(book: String)
}

/** Raw WordPress payloads for the devotion cache; consumed in Phase 5. */
@Dao
interface DevotionCacheDao {
    @Query("SELECT * FROM devotion_cache WHERE id = :id")
    suspend fun entry(id: String): DevotionCacheEntity?

    @Upsert
    suspend fun upsert(entry: DevotionCacheEntity)

    @Query("DELETE FROM devotion_cache")
    suspend fun clear()
}
