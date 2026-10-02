package com.marcow.bible.feature.search

import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.network.openrouter.OpenRouterSession
import com.marcow.bible.feature.search.domain.AiSearch
import com.marcow.bible.feature.search.domain.AiSearchUpdate
import com.marcow.bible.feature.search.domain.BlankAiSearchMemory
import com.marcow.bible.feature.search.domain.TraditionalSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The sheet's sign-in button as [SearchRoute] wires it, and the only thing about that route a plain
 * JVM test can reach.
 *
 * `SearchRoute` is a `@Composable` over a state it does not own, and the sheet's layout is already
 * covered below it by `SearchSheetStateTest`, `SearchRowsTest` and `SearchDialogTest`. What is left
 * is the one decision the route makes that is not a pass-through, and `SearchViewModelTest` cannot
 * see it: the route *builds* the button's callback, out of two calls where the order is the whole of
 * it.
 *
 * The order matters because the button makes a promise — *the query you typed runs once a key lands*
 * — and that promise is one boolean on the view model, set by [SearchViewModel.beginSignIn] and
 * cleared by the signed-in watcher. A host that opened the browser first would race the flag against
 * a sign-in that lands immediately, and a lost race here is a search that silently never runs, with
 * the sheet sitting on its sign-in panel the whole time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRouteTest {
    /** One dispatcher for the test body and for `viewModelScope`, so nothing runs off-scheduler. */
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * `_beginOpenRouterLogin`'s two statements at `legacy/flutter/lib/main.dart:2316`, in Dart's
     * order: the `pendingCloudSearch` flag first, `await widget.ai.beginOpenRouterLogin()` second.
     *
     * Asserted as one ordered list rather than as two booleans, because two booleans do not have an
     * order. A lambda that ran the host's half first would still arm, still sign in, and still leave
     * both flags saying what they should — it would just have lost any sign-in that arrived inside the
     * window, which is what the test below actually runs.
     */
    @Test
    fun `the query is armed before the host is asked to sign in`() {
        val calls = mutableListOf<String>()

        armQueryThenSignIn(
            arm = { calls += "arm" },
            signIn = { calls += "signIn" },
        )()

        assertEquals(listOf("arm", "signIn"), calls)
    }

    /**
     * The race the order above exists for, driven through the route's own lambda rather than by
     * calling the view model twice from a test body — which is the only difference, and the only
     * reason this is not a restatement of `SearchViewModelTest`'s pending-query case. Those tests
     * write `beginSignIn()` and then `signIn()` as two statements of their own, so the order they
     * exercise is the order the test file happens to be written in and says nothing about the route.
     *
     * The host's callback finishes the sign-in *synchronously* here, which is the fastest thing it
     * can do and the one case a Custom Tab cannot win: `OpenRouterAuthManager.beginSignIn` throws
     * `Could not open OpenRouter sign in.` when nothing can take the URI, and a caller that turns
     * that into a sign-in — or a session already in the store, read on the way past — lands the key
     * with no browser round trip at all. Each of those runs the signed-in watcher inside the button.
     *
     * The arming has to have happened by then. Armed afterwards it sets a flag nothing is left to
     * clear, and that is what the assertions below would see: a submitted AI query nobody could
     * answer yet, a session that becomes signed in, and no search.
     */
    @Test
    fun `a sign-in landing inside the button still runs the query that was typed`() = runTest(dispatcher) {
        val aiSearch = RecordingAiSearch()
        val session = RouteFakeOpenRouterSession(initial = false)
        val viewModel = searchViewModel(aiSearch = aiSearch, session = session)

        // What the sheet does on a tap: pick AI mode, then submit a box nobody can answer yet. The
        // submit is part of the scenario rather than setup — it is what puts `query` on the sheet and
        // leaves it on the sign-in panel instead of the hint, which is the panel the button lives on.
        viewModel.changeMode(SearchMode.AI)
        viewModel.onQueryChanged("love")
        viewModel.search()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.requiresLogin)

        armQueryThenSignIn(arm = viewModel::beginSignIn, signIn = session::signIn)()
        advanceUntilIdle()

        assertEquals("love", aiSearch.lastQuery)
        assertTrue(viewModel.state.value.referencesSearching)
    }

    /**
     * The sheet's state machine over the fakes below, with the signed-in watcher already run so
     * `aiReady` reflects [session] the way it does on a real screen.
     */
    private fun TestScope.searchViewModel(aiSearch: AiSearch, session: OpenRouterSession): SearchViewModel {
        val viewModel = SearchViewModel(
            traditionalSearch = NoTextSearch,
            aiSearch = aiSearch,
            aiMemory = BlankAiSearchMemory(),
            settingsRepository = SettingsRepository(InMemorySettingsDataStore()),
            session = session,
        )
        advanceUntilIdle()
        return viewModel
    }
}

/**
 * A text search that answers nothing. The route is what is under test and the sign-in button only
 * exists on the AI panel, so nothing here ever asks this half a question.
 */
private val NoTextSearch = object : TraditionalSearch {
    override suspend fun invoke(query: String): List<ScriptureHit> = emptyList()
}

/** An AI search that never answers, and remembers the query it was given. */
private class RecordingAiSearch : AiSearch {
    var lastQuery: String? = null

    override fun search(query: String, memory: String, aiLanguage: String): Flow<AiSearchUpdate> {
        lastQuery = query
        return MutableSharedFlow()
    }
}

/** A session whose sign-in the host's callback can complete, which is all the race needs. */
private class RouteFakeOpenRouterSession(initial: Boolean) : OpenRouterSession {
    private val state = MutableStateFlow(initial)

    override val signedIn: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun apiKey(): String? = null

    override suspend fun signOut() {
        state.value = false
    }

    /** Stands in for Phase 4's PKCE sign-in completing. */
    fun signIn() {
        state.value = true
    }
}
