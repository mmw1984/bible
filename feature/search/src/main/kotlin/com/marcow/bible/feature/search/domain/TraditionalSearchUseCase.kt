package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.SEARCH_RESULT_LIMIT
import com.marcow.bible.core.database.searchPattern
import com.marcow.bible.core.model.ScriptureHit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The port the sheet searches through.
 *
 * The use case below is the only implementation, but the sheet takes the port rather than the class
 * so its state machine can be exercised against a stub — there is no OpenRouter and no SQLite in a
 * JVM test, and the states worth testing here (the two independent progress flags, the four failure
 * panels) are exactly the ones a live search makes impossible to reach.
 */
interface TraditionalSearch {
    /** The hits for [query], at most [SEARCH_RESULT_LIMIT] of them, in canon order. */
    suspend operator fun invoke(query: String): List<ScriptureHit>
}

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
class TraditionalSearchUseCase @Inject constructor(private val bibleDao: BibleDao) : TraditionalSearch {
    /** At most [SEARCH_RESULT_LIMIT] hits in canon order. */
    override suspend operator fun invoke(query: String): List<ScriptureHit> {
        val pattern = searchPattern(query) ?: return emptyList()
        return bibleDao.searchContains(pattern, SEARCH_RESULT_LIMIT).map { it.toDomain() }
    }
}
