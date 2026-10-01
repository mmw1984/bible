package com.marcow.bible.core.database

/**
 * The `LIKE` pattern [BibleDao.searchContains] matches, built from what the user typed.
 *
 * The Flutter build lowercased both sides and used `String.contains`, so a `%` or a `_` in the query
 * was a character to find rather than a wildcard. `LIKE` would read them as wildcards and return
 * the whole Bible, so they are escaped here and the query declares `ESCAPE '\'`.
 *
 * Returns `null` for a query with nothing in it, which is the Flutter build's `return const []`
 * for an empty needle: searching for nothing is not a search that finds everything.
 */
fun searchPattern(query: String): String? {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return null
    val escaped = needle
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

/**
 * How many hits one search keeps: the `int limit = 80` of `BibleRepository.search` in
 * `legacy/flutter/lib/bible_data.dart`.
 *
 * The sheet labels a full page with a `+` (`traditionalResultCount`) because 80 is the cut-off, not
 * a promise that nothing else matched.
 */
const val SEARCH_RESULT_LIMIT = 80
