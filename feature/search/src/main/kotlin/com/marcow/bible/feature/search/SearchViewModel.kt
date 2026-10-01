package com.marcow.bible.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.network.openrouter.OpenRouterSession
import com.marcow.bible.feature.search.domain.AiSearch
import com.marcow.bible.feature.search.domain.AiSearchMemory
import com.marcow.bible.feature.search.domain.AiSearchUpdate
import com.marcow.bible.feature.search.domain.ReferenceFailure
import com.marcow.bible.feature.search.domain.TraditionalSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The search sheet's state, replacing `_SearchDialogState` in `legacy/flutter/lib/main.dart:2126`.
 *
 * The Flutter widget held seven booleans, four lists and a mode, and every search rewrote them from
 * four different callbacks. All of that is [SearchSheetState] here and this class is the only thing
 * that writes it, so what the sheet shows is always one consistent value.
 *
 * Three behaviours are worth stating because they are what the sheet's four failure panels and its
 * two independent loading rows are made of:
 *
 *  - A search does not replace the other mode's results, it clears them: the Flutter `setState` at
 *    the top of `_search` emptied the overview and the AI hits on every text search, and vice versa.
 *  - Each half of an AI search turns its own spinner into its own panel, so an overview that failed
 *    never takes the references down with it.
 *  - A sign-in that lands while an AI query is waiting runs that query, which is Flutter's
 *    `pendingCloudSearch`.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val traditionalSearch: TraditionalSearch,
    private val aiSearch: AiSearch,
    private val aiMemory: AiSearchMemory,
    private val settingsRepository: SettingsRepository,
    private val session: OpenRouterSession,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchSheetState())
    val state: StateFlow<SearchSheetState> = _state.asStateFlow()

    /**
     * The in-flight search, cancelled when a newer one starts.
     *
     * Flutter had nothing to cancel — a stale callback only had to notice `query != value` — and the
     * guard in [runTraditional] / [runAiSearch] is kept anyway, because it is what stops a slow
     * answer from writing its panel over the results of the query that replaced it.
     */
    private var searchJob: Job? = null

    /** Flutter's `bool pendingCloudSearch`: an AI query waiting for a sign-in to land. */
    private var pendingCloudSearch = false

    init {
        viewModelScope.launch {
            session.signedIn.collect { signedIn ->
                _state.update { it.copy(signedIn = signedIn) }
                // `_aiChanged`: the sign-in finished and nothing is running, so the query the user
                // typed before signing in is worth one more try.
                //
                // The *box*, not the last submitted query. `_aiChanged` at
                // `legacy/flutter/lib/main.dart:2161` calls `_searchAi()` with no argument, and
                // `_searchAi([String? supplied])` opens with `(supplied ?? input.text).trim()` — so
                // Dart re-read the box, and a query edited while the sign-in was in flight is the
                // one that runs. `query` would have replayed the text that was in the box when the
                // user pressed search, which is a different query whenever the two differ, and an
                // emptied box would have replayed it instead of running nothing.
                if (pendingCloudSearch && signedIn && !_state.value.searching) {
                    pendingCloudSearch = false
                    runAiSearch(_state.value.input.trim())
                }
            }
        }
    }

    /** `TextEditingController.addListener`: what is in the box, not yet a search. */
    fun onQueryChanged(value: String) {
        _state.update { it.copy(input = value) }
    }

    /** `_search`: submit the box, or the keyboard's search key. */
    fun search() {
        val current = _state.value
        val value = current.input.trim()
        if (value.isEmpty() || current.searching) return
        if (current.mode == SearchMode.AI) {
            _state.update { it.clearedForSearch(value) }
            // Flutter's `if (widget.ai.isReady) await _searchAi(value)`: signed out, the box is left
            // cleared and the sheet draws the sign-in panel instead of a failed search.
            if (_state.value.aiReady) runAiSearch(value)
            return
        }
        // Flutter's text branch: `traditional = const []` sat in the same setState as the spinner, so
        // the hits of the query before this one were gone before the first row of this one was drawn.
        _state.update { it.clearedForSearch(value).copy(traditionalSearching = true, traditionalHits = emptyList()) }
        runTraditional(value)
    }

    /**
     * `_changeMode`: switch mode and start over.
     *
     * The two refusals are Flutter's. A search that is still running is not interrupted, and picking
     * the mode already in control does nothing at all — so a user cannot clear the results out from
     * under a request that is on its way back.
     *
     * Flutter's setState listed seven fields to reset and the session was not one of them: it read
     * `openRouterSignedIn` off the controller on every build, so no mode switch could sign anyone
     * out. Everything here starts from a fresh state, and the sign-in is the one value that has to
     * survive it.
     */
    fun changeMode(mode: SearchMode) {
        val current = _state.value
        if (current.mode == mode || current.searching) return
        _state.value = SearchSheetState(mode = mode, input = current.input, signedIn = current.signedIn)
    }

    /**
     * `_beginOpenRouterLogin`: remember that a query is waiting, so signing in runs it.
     *
     * The sign-in itself belongs to Phase 4's `OpenRouterAuthManager` and to whoever hosts the
     * sheet, so the host calls this and then opens its own sign-in; the flag is all the sheet owns.
     */
    fun beginSignIn() {
        pendingCloudSearch = _state.value.input.isNotBlank()
    }

    /** Flutter's `_search` half for text search: the hits, or the `search_status` failure panel. */
    private fun runTraditional(value: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val hits = try {
                traditionalSearch(value)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (_state.value.query != value) return@launch
            _state.update {
                if (hits == null) {
                    it.copy(traditionalFailed = true, traditionalSearching = false)
                } else {
                    it.copy(traditionalHits = hits, traditionalSearching = false)
                }
            }
        }
    }

    /**
     * Flutter's `_searchAi`: both halves spin, then each publishes what it has.
     *
     * The memory block and the language are read here, before the search starts, because Dart read
     * them inside `BibleAiController.search` — one memory read and one settings read per search
     * rather than one per half of it.
     */
    private fun runAiSearch(value: String) {
        // `_searchAi` guards the same three things as `_search` does, because both the signed-in
        // watcher and the submit button reach this: a query the watcher is holding on to can have
        // been emptied from the box in the meantime.
        if (value.isBlank() || _state.value.searching || !_state.value.aiReady) return
        _state.update {
            it.copy(
                query = value,
                overviewSearching = true,
                referencesSearching = true,
                traditionalFailed = false,
                overviewFailed = false,
                referencesFailure = null,
                overview = null,
                aiHits = emptyList(),
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val memory = aiMemory.promptMemory()
                val aiLanguage = settingsRepository.settings.first().locale.aiLanguage
                aiSearch.search(query = value, memory = memory, aiLanguage = aiLanguage).collect { update ->
                    if (_state.value.query != value) return@collect
                    _state.update { it.withUpdate(update) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Each request publishes its own error without hiding a successful peer, so there is
                // nothing to add here; the block below turns whatever is still spinning into one.
            } finally {
                // Dart's `finally` in `_searchAi`: a half that is still spinning when the search is
                // over never answered, and a sheet that cannot stop spinning is worse than a panel.
                if (_state.value.query == value) _state.update { it.finishedWithoutAnswering() }
            }
        }
    }
}

/**
 * The common `setState` at the top of both Flutter search paths.
 *
 * Every failure and every AI result is cleared here, which is what stops a text search from showing the
 * AI panel that failed three queries ago, and an AI search from showing hits the user never asked
 * for. `query` is set from the submitted box because the results list is keyed on it.
 *
 * The text hits are the one list left out, because that is where Flutter's two branches differ: its AI
 * branch said nothing about `traditional` and its text branch emptied it at
 * `legacy/flutter/lib/main.dart:2183`, so a search drops what it is replacing.
 */
private fun SearchSheetState.clearedForSearch(value: String): SearchSheetState = copy(
    query = value,
    traditionalFailed = false,
    overviewFailed = false,
    referencesFailure = null,
    overview = null,
    aiHits = emptyList(),
)

/**
 * One [AiSearchUpdate] applied, which is where Flutter's four `onOverview` / `onReferences` callbacks
 * are now.
 *
 * Each update clears exactly the one spinner it belongs to, so the two loading rows run independently
 * and a half that has landed stays landed while the other is still going.
 */
private fun SearchSheetState.withUpdate(update: AiSearchUpdate): SearchSheetState = when (update) {
    is AiSearchUpdate.OverviewReady -> copy(overview = update.overview, overviewSearching = false)
    is AiSearchUpdate.OverviewFailed -> copy(overviewFailed = true, overviewSearching = false)
    is AiSearchUpdate.ReferencesReady -> copy(aiHits = update.hits, referencesSearching = false)
    is AiSearchUpdate.ReferencesFailed -> copy(referencesFailure = update.failure, referencesSearching = false)
}

/**
 * The end of an AI search, whether it ended by answering, by throwing, or by never answering at all.
 *
 * A half still spinning at this point is one the search will not report on, so it becomes the
 * failure it is — the overview its own `overview_failed` panel, the references the request failure,
 * because a request that was never answered is what `ReferenceFailure.REQUEST` means. A failure that
 * has already been published is left alone, which is what keeps a successful peer from being
 * overwritten by the half that gave up.
 */
private fun SearchSheetState.finishedWithoutAnswering(): SearchSheetState = copy(
    overviewFailed = overviewFailed || overviewSearching,
    referencesFailure = referencesFailure ?: ReferenceFailure.REQUEST.takeIf { referencesSearching },
    overviewSearching = false,
    referencesSearching = false,
)
