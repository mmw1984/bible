package com.marcow.bible.feature.search.ui

import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.feature.search.SearchMode
import com.marcow.bible.feature.search.SearchSheetState
import com.marcow.bible.feature.search.domain.ReferenceFailure

/**
 * How many AI tiles the sheet draws.
 *
 * `aiHits.take(80)`: the references prompt asks for up to sixteen ranges and a range can cover several
 * verses, so a broad query resolves to more tiles than a scrolling list is for. Flutter's count label
 * read `aiHits.length` rather than the truncated list, so the label can promise more rows than are
 * drawn — kept as it is, because the label counts what the model found and the cap is only about
 * drawing it.
 */
internal const val AI_HIT_TILE_LIMIT = 80

/**
 * The bordered boxes the sheet's result list can draw, in the order `_results` drew them.
 *
 * An enum rather than the Flutter conditions themselves so that the conditions live in one place
 * ([searchRows]) with a test, and so that a row carries the one value it needs — [ScriptureFailed]
 * has to say *which* of the two reference failures it was, because the two have different copy.
 */
internal sealed interface SearchPanel {
    /** `localFailed`: the `search_status` box saying the text search could not be completed. */
    data object SearchStatus : SearchPanel

    /** `mode == ai && requiresLogin`: the `login_to_search` box with its sign-in button. */
    data object SignIn : SearchPanel

    /** `mode == ai && overviewSearching`: the overview half is still out. */
    data object OverviewLoading : SearchPanel

    /** `mode == ai && overviewFailed`: the overview half gave up. */
    data object OverviewFailed : SearchPanel

    /** `mode == ai && overview != null`: the overview prose. */
    data object Overview : SearchPanel

    /** `mode == ai && referencesSearching`: the references half is still out. */
    data object ScriptureLoading : SearchPanel

    /** `mode == ai && referencesFailure != null`: the references half gave up. */
    data class ScriptureFailed(val failure: ReferenceFailure) : SearchPanel
}

/**
 * One line of the sheet's result list, mirroring the children of the `ListView` in `_results`
 * (`legacy/flutter/lib/main.dart:2439`).
 *
 * The list is the whole of `_results`, not just its panels: the count labels and the tiles are rows
 * too, which is what lets the sheet hand the whole result to a `LazyColumn` and build nothing until
 * a row scrolls into view.
 */
internal sealed interface SearchRow {
    /** One of the bordered boxes. */
    data class Panel(val panel: SearchPanel) : SearchRow

    /** The `traditional_result_count` heading above the text tiles. */
    data object TraditionalCount : SearchRow

    /** The `ai_result_count` heading above the AI tiles. */
    data object AiCount : SearchRow

    /** One hit, with the AI half's [reason] or nothing for a text search. */
    data class Tile(val hit: ScriptureHit, val reason: String?) : SearchRow

    /** The `no_results` paragraph, drawn last, under whatever panel explains it. */
    data object NoResults : SearchRow
}

/**
 * Every row [state] calls for, in the order `_results` drew them.
 *
 * The whole of the Flutter `if` chain as one list, because the order and the exclusions are the part
 * that is easy to get wrong and impossible to see once it is spread over composables: a signed-out
 * AI search shows *only* the sign-in panel, a failed overview has no prose row, an overview that
 * arrived alongside references that failed is two rows rather than one failure, and the AI rows never
 * appear at all in text mode.
 */
internal fun searchRows(state: SearchSheetState): List<SearchRow> = buildList {
    if (state.traditionalFailed) add(SearchRow.Panel(SearchPanel.SearchStatus))
    if (state.mode == SearchMode.AI) {
        // The sign-in panel replaces the results rather than sitting under them: it is the reason
        // there are none, and Flutter drew it inside the results list so it kept the query it explains.
        if (state.requiresLogin) {
            add(SearchRow.Panel(SearchPanel.SignIn))
            return@buildList
        }
        if (state.overviewSearching) add(SearchRow.Panel(SearchPanel.OverviewLoading))
        if (state.overviewFailed) add(SearchRow.Panel(SearchPanel.OverviewFailed))
        if (state.overview != null) add(SearchRow.Panel(SearchPanel.Overview))
        if (state.referencesSearching) add(SearchRow.Panel(SearchPanel.ScriptureLoading))
        state.referencesFailure?.let { add(SearchRow.Panel(SearchPanel.ScriptureFailed(it))) }
        if (state.aiHits.isNotEmpty()) {
            add(SearchRow.AiCount)
            state.aiHits.take(AI_HIT_TILE_LIMIT).forEach { add(SearchRow.Tile(it.hit, it.reason)) }
        }
    } else if (state.mode == SearchMode.TRADITIONAL && state.traditionalHits.isNotEmpty()) {
        add(SearchRow.TraditionalCount)
        state.traditionalHits.forEach { add(SearchRow.Tile(it, null)) }
    }
    if (state.showsNoResults) add(SearchRow.NoResults)
}
