package com.marcow.bible.core.database

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
