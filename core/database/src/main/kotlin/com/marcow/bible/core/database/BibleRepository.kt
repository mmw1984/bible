package com.marcow.bible.core.database

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingProgress
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scripture and reading position, replacing the Flutter `BibleRepository` in
 * `legacy/flutter/lib/bible_data.dart` plus the `SharedPreferences` scroll cache the reader kept.
 *
 * It lives here rather than in a feature because both the reader and the library need it and
 * features never depend on each other (`NATIVE_PLAN.md` §2.2): the reader reads a chapter and writes
 * the position, the library lists the books. Every read returns domain models rather than rows, so
 * the pairing rule below is applied exactly once.
 */
@Singleton
class BibleRepository @Inject constructor(
    private val bibleDao: BibleDao,
    private val readingProgressDao: ReadingProgressDao,
) {
    /** All 66 books in canon order, which is the order the library and the chapter picker list them. */
    suspend fun books(): List<BibleBook> = bibleDao.books().map { it.toDomain() }

    suspend fun book(bookId: String): BibleBook? = bibleDao.book(bookId)?.toDomain()

    /** One testament's books in canon order, for the library's 舊約 / 新約 segments. */
    suspend fun booksByTestament(testament: Testament): List<BibleBook> =
        bibleDao.booksByTestament(testament.storageValue).map { it.toDomain() }

    /**
     * The verses of one chapter, paired across the two translations.
     *
     * The rows are already one per verse index of the longer translation, so [VerseEntity.toDomain]
     * reproduces the Flutter pairing rule (`legacy/flutter/lib/bible_data.dart:33`) — a missing
     * index in one translation becomes an empty string rather than a dropped row. An out-of-range
     * chapter yields an empty list, where the Flutter reader threw.
     */
    suspend fun chapter(bookId: String, chapter: Int): List<VersePair> =
        bibleDao.chapter(bookId, chapter).map { it.toDomain() }

    /** The stored position for [bookId], or null when the book was never opened. */
    suspend fun progress(bookId: String): ReadingProgress? = readingProgressDao.progress(bookId)?.toDomain()

    suspend fun saveProgress(progress: ReadingProgress) = readingProgressDao.upsert(
        ReadingProgressEntity.fromDomain(progress),
    )

    suspend fun deleteProgress(bookId: String) = readingProgressDao.delete(bookId)
}
