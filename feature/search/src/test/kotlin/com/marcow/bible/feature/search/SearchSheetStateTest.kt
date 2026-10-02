package com.marcow.bible.feature.search

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.feature.search.domain.AiSearchHit
import com.marcow.bible.feature.search.domain.ReferenceFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The state sheet's `if` conditions, which are the parts of the Flutter search that are easiest to
 * get subtly wrong and impossible to see from the Kotlin.
 */
class SearchSheetStateTest {
    @Test
    fun `an untried sheet shows the hint, not an empty result list`() {
        val state = SearchSheetState(input = "half-typed")

        assertTrue(state.showsHint)
        assertFalse(state.showsNoResults)
    }

    @Test
    fun `the sheet is searching while either half of an AI search is still out`() {
        val overviewOnly = SearchSheetState(mode = SearchMode.AI, overviewSearching = true)
        val referencesOnly = SearchSheetState(mode = SearchMode.AI, referencesSearching = true)

        assertTrue(overviewOnly.searching)
        assertTrue(referencesOnly.searching)
    }

    @Test
    fun `a text search that found nothing says so`() {
        val state = SearchSheetState(query = "nothing here")

        assertTrue(state.showsNoResults)
    }

    @Test
    fun `a text search that found something does not`() {
        val state = SearchSheetState(query = "love", traditionalHits = listOf(hit()))

        assertFalse(state.showsNoResults)
    }

    @Test
    fun `an AI search with an overview but no verses does not claim to have found nothing`() {
        val state = SearchSheetState(mode = SearchMode.AI, query = "love", overview = "An overview.")

        assertFalse(state.showsNoResults)
    }

    @Test
    fun `a signed out AI search says nothing about results`() {
        val state = SearchSheetState(mode = SearchMode.AI, query = "love")

        assertTrue(state.requiresLogin)
        assertFalse(state.showsNoResults)
    }

    @Test
    fun `a failed AI search says nothing about results`() {
        val overview = SearchSheetState(mode = SearchMode.AI, query = "love", overviewFailed = true)
        val references = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            referencesFailure = ReferenceFailure.VERSES,
        )

        assertFalse(overview.showsNoResults)
        assertFalse(references.showsNoResults)
    }

    @Test
    fun `a failed text search still shows the empty-result line under its panel`() {
        // `legacy/flutter/lib/main.dart:2551` only excuses the message while nothing is running,
        // whether or not the search worked, so the panel and the line are drawn together.
        val state = SearchSheetState(query = "love", traditionalFailed = true)

        assertTrue(state.showsNoResults)
    }

    @Test
    fun `an AI search that is ready is one with a session behind it`() {
        assertFalse(SearchSheetState(signedIn = true).requiresLogin)
        assertTrue(SearchSheetState(signedIn = true).aiReady)
    }

    @Test
    fun `a new query is a new results list, so it opens at the top`() {
        // The one thing `ValueKey('${mode.name}-$query')` did: a different key threw the old
        // `ScrollController` away, so the list Flutter built next started at index 0 rather than
        // wherever the previous one had been scrolled to.
        val first = SearchSheetState(mode = SearchMode.AI, query = "love")
        val second = first.copy(query = "joy")

        assertNotEquals(first.resultsIdentity, second.resultsIdentity)
    }

    @Test
    fun `a new mode is a new results list too`() {
        val traditional = SearchSheetState(mode = SearchMode.TRADITIONAL, query = "love")
        val ai = traditional.copy(mode = SearchMode.AI)

        assertNotEquals(traditional.resultsIdentity, ai.resultsIdentity)
    }

    @Test
    fun `the same query in the same mode is still the same results list`() {
        // The other half of it: answering from the model replaces every tile, and the user has to stay
        // where they were reading while it happens. Only the identity changing should reset.
        val searching = SearchSheetState(mode = SearchMode.AI, query = "love", referencesSearching = true)
        val answered = searching.copy(referencesSearching = false, aiHits = listOf(AiSearchHit(hit(), "why")))

        assertEquals(searching.resultsIdentity, answered.resultsIdentity)
    }

    @Test
    fun `the verses an AI search returned are not the verses a text search returned`() {
        val aiIgnoresTraditional = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            traditionalHits = listOf(hit()),
        )
        val traditionalIgnoresAi = SearchSheetState(
            mode = SearchMode.TRADITIONAL,
            query = "love",
            traditionalHits = emptyList(),
            aiHits = listOf(AiSearchHit(hit = hit(), reason = "God so loved")),
        )

        assertTrue(aiIgnoresTraditional.showsNoResults)
        assertTrue(traditionalIgnoresAi.showsNoResults)
    }
}

private fun hit(): ScriptureHit = ScriptureHit(
    book = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW),
    chapter = 3,
    verse = VersePair(number = 16, zh = "神愛世人", en = "For God so loved"),
)
