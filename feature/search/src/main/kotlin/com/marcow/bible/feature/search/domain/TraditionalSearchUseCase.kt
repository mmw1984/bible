package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.SEARCH_RESULT_LIMIT
import com.marcow.bible.core.database.searchPattern
import com.marcow.bible.core.model.ScriptureHit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The text half of the search sheet, mirroring `BibleRepository.search` in
 * `legacy/flutter/lib/bible_data.dart`.
 *
 * The Flutter build walked the books in canon order and stopped at 80 hits; [BibleDao.searchContains]
 * is that walk expressed as one `ORDER BY b.ordinal, v.chapter, v.verse` plus a `LIMIT`, so the same
 * first rows come back in the same order. The two things that walk could not express in SQL —
 * escaping the `LIKE` wildcards and refusing an empty query — are [searchPattern]'s job.
 *
 * A blank query is not an error and not a result: it returns nothing, which is the Flutter build's
 * `return const []` for an empty needle. The sheet never calls it with one (`_search` returns early
 * on an empty box), so the distinction only matters to the tests.
 */
@Singleton
internal class TraditionalSearchUseCase @Inject constructor(
    private val bibleDao: BibleDao,
) {
    /** At most [SEARCH_RESULT_LIMIT] hits in canon order. */
    suspend operator fun invoke(query: String): List<ScriptureHit> {
        val pattern = searchPattern(query) ?: return emptyList()
        return bibleDao.searchContains(pattern, SEARCH_RESULT_LIMIT).map { it.toDomain() }
    }
}