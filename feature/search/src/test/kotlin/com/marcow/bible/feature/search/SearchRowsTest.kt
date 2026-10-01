package com.marcow.bible.feature.search

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.feature.search.domain.AiSearchHit
import com.marcow.bible.feature.search.domain.ReferenceFailure
import com.marcow.bible.feature.search.ui.AI_HIT_TILE_LIMIT
import com.marcow.bible.feature.search.ui.SearchPanel
import com.marcow.bible.feature.search.ui.SearchRow
import com.marcow.bible.feature.search.ui.searchRows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The order and the exclusions of the sheet's result list, which are the `if` chain in `_results`
 * (`legacy/flutter/lib/main.dart:2439`) lifted into something that can be read.
 *
 * The row list rather than the drawn pixels, because the row list is where the decisions are: which
 * panel is up, what is above it, what is missing from it.
 */
class SearchRowsTest {
    @Test
    fun `a failed text search leads with its panel`() {
        val rows = searchRows(SearchSheetState(query = "love", traditionalFailed = true))

        assertEquals(SearchRow.Panel(SearchPanel.SearchStatus), rows.first())
    }

    @Test
    fun `a signed out AI search draws the sign-in panel and nothing else`() {
        val state = SearchSheetState(mode = SearchMode.AI, query = "love", signedIn = false)
        val rows = searchRows(state)

        assertEquals(listOf(SearchRow.Panel(SearchPanel.SignIn)), rows)
    }

    @Test
    fun `the sign-in panel replaces the results even on the frame the spinner would be on`() {
        // Flutter drew it with `if` rather than `else if`, so this cannot happen in the Flutter build
        // only because nothing sets both; the sheet must not depend on that ordering to be correct.
        val state = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            overviewSearching = true,
            referencesSearching = true,
        )
        val rows = searchRows(state)

        assertEquals(listOf(SearchRow.Panel(SearchPanel.SignIn)), rows)
    }

    @Test
    fun `both halves of an AI search spin in their own sections`() {
        val state = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            overviewSearching = true,
            referencesSearching = true,
        )

        assertEquals(
            listOf(
                SearchRow.Panel(SearchPanel.OverviewLoading),
                SearchRow.Panel(SearchPanel.ScriptureLoading),
            ),
            searchRows(state),
        )
    }

    @Test
    fun `an overview that arrived while the references are still out stays put`() {
        val state = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            overview = "Love in John.",
            referencesSearching = true,
        )

        assertEquals(
            listOf(
                SearchRow.Panel(SearchPanel.Overview),
                SearchRow.Panel(SearchPanel.ScriptureLoading),
            ),
            searchRows(state),
        )
    }

    @Test
    fun `a failed overview has no prose row`() {
        val state = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            overviewFailed = true,
        )

        assertEquals(listOf(SearchRow.Panel(SearchPanel.OverviewFailed)), searchRows(state))
    }

    @Test
    fun `the two reference failures are distinguishable rows`() {
        val request = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            referencesFailure = ReferenceFailure.REQUEST,
        )
        val verses = request.copy(referencesFailure = ReferenceFailure.VERSES)

        val requestRow = searchRows(request).first()
        val versesRow = searchRows(verses).first()

        assertEquals(SearchRow.Panel(SearchPanel.ScriptureFailed(ReferenceFailure.REQUEST)), requestRow)
        assertEquals(SearchRow.Panel(SearchPanel.ScriptureFailed(ReferenceFailure.VERSES)), versesRow)
        assertTrue(requestRow != versesRow)
    }

    @Test
    fun `a failed overview and failed references are two panels rather than one`() {
        val state = SearchSheetState(
            mode = SearchMode.AI,
            query = "love",
            signedIn = true,
            overviewFailed = true,
            referencesFailure = ReferenceFailure.VERSES,
        )

        assertEquals(
            listOf(
                SearchRow.Panel(SearchPanel.OverviewFailed),
                SearchRow.Panel(SearchPanel.ScriptureFailed(ReferenceFailure.VERSES)),
            ),
            searchRows(state),
        )
    }

    @Test
    fun `AI hits are labelled with the whole count and capped when drawn`() {
        // The label reads `aiHits.length` and the list takes 80, so a broad query can promise more
        // rows than there are. Flutter did the same, and the label is about what the model found.
        val hits = (1..AI_HIT_TILE_LIMIT + 10).map { AiSearchHit(hit = hit(it), reason = "because") }
        val rows = searchRows(
            SearchSheetState(mode = SearchMode.AI, query = "love", signedIn = true, aiHits = hits),
        )
        val tiles = rows.filterIsInstance<SearchRow.Tile>()

        assertEquals(SearchRow.AiCount, rows.first())
        assertEquals(AI_HIT_TILE_LIMIT, tiles.size)
    }

    @Test
    fun `an AI tile carries the model's reason`() {
        val rows = searchRows(
            SearchSheetState(
                mode = SearchMode.AI,
                query = "love",
                signedIn = true,
                aiHits = listOf(AiSearchHit(hit = hit(), reason = "The wedding at Cana")),
            ),
        )

        assertEquals(SearchRow.Tile(hit(), "The wedding at Cana"), rows[1])
    }

    @Test
    fun `text hits are labelled and carry no reason`() {
        val rows = searchRows(
            SearchSheetState(query = "love", traditionalHits = listOf(hit(), hit(3))),
        )

        assertEquals(SearchRow.TraditionalCount, rows.first())
        assertEquals(SearchRow.Tile(hit(3), null), rows[2])
        assertEquals(3, rows.size)
    }

    @Test
    fun `the two modes never draw each other's rows`() {
        val state = SearchSheetState(
            mode = SearchMode.TRADITIONAL,
            query = "love",
            aiHits = listOf(AiSearchHit(hit = hit(), reason = "because")),
            overview = "An overview.",
        )

        assertFalse(searchRows(state).any { it == SearchRow.AiCount })
        assertFalse(searchRows(state).any { it is SearchRow.Panel && it.panel == SearchPanel.Overview })
    }

    @Test
    fun `the empty-result line is last, under whatever explains it`() {
        val rows = searchRows(SearchSheetState(query = "love", traditionalFailed = true))

        assertEquals(SearchRow.NoResults, rows.last())
    }

    @Test
    fun `the empty-result line is not drawn while a search is running`() {
        val rows = searchRows(
            SearchSheetState(
                mode = SearchMode.AI,
                query = "love",
                signedIn = true,
                overviewSearching = true,
                referencesSearching = true,
            ),
        )

        assertFalse(SearchRow.NoResults in rows)
    }
}

private fun hit(verse: Int = 16): ScriptureHit = ScriptureHit(
    book = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW),
    chapter = 3,
    verse = VersePair(number = verse, zh = "神愛世人", en = "For God so loved"),
)
