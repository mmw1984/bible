package com.marcow.bible.feature.search

import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.feature.search.domain.AiSearchHit
import com.marcow.bible.feature.search.domain.ReferenceFailure

/**
 * Everything the search sheet draws, mirroring the fields of `_SearchDialogState` in
 * `legacy/flutter/lib/main.dart:2127`.
 *
 * One immutable value rather than a dozen `setState` flags: the Flutter state changed the flags of a
 * half-finished search from several callbacks at once, and the two halves of an AI search land in
 * whatever order the provider answers in. A single value that is replaced atomically is what keeps
 * "the overview arrived while the references are still spinning" from being observed half applied.
 *
 * Every field is named for the Flutter field it replaces, so the two can be read side by side.
 */
data class SearchSheetState(
    /** `_SearchMode mode`. */
    val mode: SearchMode = SearchMode.TRADITIONAL,
    /** The query box's contents, which Flutter held in a `TextEditingController` next to `query`. */
    val input: String = "",
    /** `String query`: what the last submitted search was, and what the results list is for. */
    val query: String = "",
    /** `BibleAiController.openRouterSignedIn`. */
    val signedIn: Boolean = false,
    /** `List<ScriptureHit> traditional`. */
    val traditionalHits: List<ScriptureHit> = emptyList(),
    /** `bool localSearching`. */
    val traditionalSearching: Boolean = false,
    /** `bool localFailed`. */
    val traditionalFailed: Boolean = false,
    /** `String? overview`. */
    val overview: String? = null,
    /** `bool overviewSearching`. */
    val overviewSearching: Boolean = false,
    /** `bool overviewFailed`. */
    val overviewFailed: Boolean = false,
    /** `List<(ScriptureHit, String)> aiHits`. */
    val aiHits: List<AiSearchHit> = emptyList(),
    /** `bool referencesSearching`. */
    val referencesSearching: Boolean = false,
    /** `_ReferenceFailure? referencesFailure`. */
    val referencesFailure: ReferenceFailure? = null,
) {
    /**
     * `bool get searching`.
     *
     * The three flags rather than the one that matters: Flutter kept a text search, an overview and
     * a references request apart, and the search line at the top of the sheet and the guard on
     * `_search` both read all three. Merging them would hide which of the two AI requests is still
     * running, which is the one thing the two independent loading rows exist to show.
     */
    val searching: Boolean
        get() = traditionalSearching || overviewSearching || referencesSearching

    /**
     * `BibleAiController.isReady`, which with no on-device model is the sign-in alone.
     *
     * Flutter's getter read `_overrideModel == null ? openRouterSignedIn : availability == ready`;
     * the on-device provider arrives with Phase 4, so until then this is the second half of that
     * expression — the sheet's sign-in panel and this flag are two readings of the same state.
     */
    val aiReady: Boolean
        get() = signedIn

    /** `BibleAiController.requiresLogin`: what the `login_to_search` panel stands in for. */
    val requiresLogin: Boolean
        get() = !aiReady

    /**
     * Whether the sheet opens on the `search_hint_body` paragraph rather than a result list.
     *
     * `query` rather than `input`, exactly as `_results` checked it: a half-typed query has no
     * results to show, and the hint is what the Flutter sheet showed while one was being typed.
     */
    val showsHint: Boolean
        get() = query.isEmpty()

    /**
     * `ValueKey('${mode.name}-$query')`, the key `_results` gave its `ListView`.
     *
     * This is the whole of what the key was for. Flutter's key is not a cache key or an equality test
     * — a widget key is an identity, so changing it throws the old element and its `ScrollController`
     * away and builds a new list that starts at the top. Submitting a query while scrolled halfway
     * down a long list therefore opened the new results at their beginning, and so did switching mode.
     *
     * Compose has no widget identity to key on, and a `LazyColumn` keeps its `LazyListState` across
     * recomposition like any remembered value, so without this the second search of a session opened
     * wherever the first one had been scrolled to — with nothing but the new tiles' tail on screen. It
     * is kept as a value rather than being read off `mode` and `query` at the call site because both
     * the crossfade and the scroll position have to key on the same thing, and one place that says so
     * cannot be half-ported.
     */
    val resultsIdentity: String
        get() = "${mode.name}-$query"

    /**
     * The `no_results` paragraph, kept for Flutter's exact condition.
     *
     * Read it against `legacy/flutter/lib/main.dart:2556`: the sheet shows "no matching scripture"
     * when nothing is running, the AI half is not waiting on a sign-in, neither AI failure panel is
     * up, and the half that did run came back with nothing. The two consequences are deliberate and
     * are the Flutter behaviour, not oversights — a signed-out AI search does not say "no results"
     * because the sign-in panel above it explains why there are none, and a *failed* search does say
     * it, under its own failure panel.
     */
    val showsNoResults: Boolean
        get() = !searching &&
            !(mode == SearchMode.AI && !aiReady) &&
            !overviewFailed &&
            referencesFailure == null &&
            when (mode) {
                SearchMode.TRADITIONAL -> traditionalHits.isEmpty()
                SearchMode.AI -> aiHits.isEmpty() && overview == null
            }
}
