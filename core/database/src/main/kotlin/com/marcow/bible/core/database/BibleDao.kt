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
