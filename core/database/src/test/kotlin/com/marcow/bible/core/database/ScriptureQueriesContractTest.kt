package com.marcow.bible.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure logic checks that need no database: the LIKE escaping and the range cap,
 * which are the two places where the SQL implementation could silently drift
 * from the Dart original.
 */
class ScriptureQueriesContractTest {

    @Test
    @DisplayName("the search cap matches the Flutter limit")
    fun searchLimit() {
        assertEquals(80, ScriptureQueries.DEFAULT_SEARCH_LIMIT)
    }

    @Test
    @DisplayName("the scripture tool range cap matches _runScriptureTool")
    fun rangeLimit() {
        assertEquals(100, ScriptureQueries.MAX_RANGE_VERSES)
    }

    @Test
    fun likeMetacharactersAreEscaped() {
        assertEquals("100\\%\\_done\\\\x", escapeLike("100%_done\\x"))
        assertEquals("plain text", escapeLike("plain text"))
        assertEquals("", escapeLike(""))
        // Chinese is never affected, which matters most for this app.
        assertEquals("起初神", escapeLike("起初神"))
    }
}