package com.marcow.bible.feature.search

import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.core.network.openrouter.OpenRouterSession
import com.marcow.bible.feature.search.domain.AiSearch
import com.marcow.bible.feature.search.domain.AiSearchHit
import com.marcow.bible.feature.search.domain.AiSearchMemory
import com.marcow.bible.feature.search.domain.AiSearchUpdate
import com.marcow.bible.feature.search.domain.ReferenceFailure
import com.marcow.bible.feature.search.domain.TraditionalSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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

    @Test
    fun `a blank box searches nothing`() = runTest(dispatcher) {
        val traditional = FakeTraditionalSearch(hits = listOf(johnThreeSixteen()))
        val viewModel = searchViewModel(traditional = traditional)

        viewModel.onQueryChanged("   ")
        viewModel.search()
        advanceUntilIdle()

        assertNull(traditional.lastQuery)
        assertEquals("", viewModel.state.value.query)
        assertTrue(viewModel.state.value.showsHint)
    }

    @Test
    fun `text search shows the hits and stops searching`() = runTest(dispatcher) {
        val hits = listOf(johnThreeSixteen(), johnThreeSeventeen())
        val traditional = FakeTraditionalSearch(hits = hits)
        val viewModel = searchViewModel(traditional = traditional)

        viewModel.onQueryChanged("  God loves  ")
        viewModel.search()
        advanceUntilIdle()

        assertEquals("God loves", traditional.lastQuery)
        assertEquals(hits, viewModel.state.value.traditionalHits)
        assertFalse(viewModel.state.value.traditionalSearching)
        assertFalse(viewModel.state.value.traditionalFailed)
    }

    @Test
    fun `a failed text search keeps its own panel`() = runTest(dispatcher) {
        val traditional = FakeTraditionalSearch(failure = IllegalStateException("no such column"))
        val viewModel = searchViewModel(traditional = traditional)

        viewModel.onQueryChanged("God")
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.traditionalFailed)
        assertFalse(viewModel.state.value.traditionalSearching)
    }

    @Test
    fun `the next query starts from an empty list`() = runTest(dispatcher) {
        val traditional = FakeTraditionalSearch(hits = listOf(johnThreeSixteen()))
        val viewModel = searchViewModel(traditional = traditional)

        viewModel.onQueryChanged("love")
        viewModel.search()
        advanceUntilIdle()
        traditional.hits = listOf(mattFourOne())
        viewModel.onQueryChanged("temptation")
        viewModel.search()
        advanceUntilIdle()

        assertEquals("temptation", traditional.lastQuery)
        assertEquals(listOf(mattFourOne()), viewModel.state.value.traditionalHits)
    }

    @Test
    fun `both halves of an AI search spin until each of them lands`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(updates))
        searchAi(viewModel)

        assertTrue(viewModel.state.value.overviewSearching)
        assertTrue(viewModel.state.value.referencesSearching)
        assertTrue(viewModel.state.value.searching)

        updates.tryEmit(AiSearchUpdate.OverviewReady("A short overview."))
        advanceUntilIdle()

        assertEquals("A short overview.", viewModel.state.value.overview)
        // The overview row is done and the scripture row is still spinning: they are independent.
        assertFalse(viewModel.state.value.overviewSearching)
        assertTrue(viewModel.state.value.referencesSearching)

        updates.tryEmit(AiSearchUpdate.ReferencesReady(listOf(aiHit(johnThreeSixteen(), "God so loved"))))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.aiHits.size)
        assertFalse(viewModel.state.value.referencesSearching)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `a failed overview leaves the references that did arrive`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(updates))
        searchAi(viewModel)

        updates.tryEmit(AiSearchUpdate.OverviewFailed)
        updates.tryEmit(AiSearchUpdate.ReferencesReady(listOf(aiHit(johnThreeSixteen(), "God so loved"))))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.overviewFailed)
        assertNull(viewModel.state.value.overview)
        assertEquals(1, viewModel.state.value.aiHits.size)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `the two reference failures are different panels`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(updates))

        searchAi(viewModel)
        updates.tryEmit(AiSearchUpdate.OverviewReady("An overview."))
        updates.tryEmit(AiSearchUpdate.ReferencesFailed(ReferenceFailure.REQUEST))
        advanceUntilIdle()

        assertEquals(ReferenceFailure.REQUEST, viewModel.state.value.referencesFailure)
        assertFalse(viewModel.state.value.referencesSearching)

        // The next query clears the panel rather than leaving it under the new results.
        searchAi(viewModel, query = "faith")
        advanceUntilIdle()
        assertNull(viewModel.state.value.referencesFailure)

        updates.tryEmit(AiSearchUpdate.ReferencesFailed(ReferenceFailure.VERSES))
        advanceUntilIdle()

        assertEquals(ReferenceFailure.VERSES, viewModel.state.value.referencesFailure)
    }

    @Test
    fun `a signed out AI search asks for a sign-in instead of failing`() = runTest(dispatcher) {
        val aiSearch = FakeAiSearch(MutableSharedFlow())
        val viewModel = searchViewModel(
            aiSearch = aiSearch,
            session = FakeOpenRouterSession(initial = false),
        )

        searchAi(viewModel)

        assertTrue(viewModel.state.value.requiresLogin)
        assertNull(aiSearch.lastQuery)
        assertFalse(viewModel.state.value.searching)
        // The box keeps what was typed, so the panel above it can promise the query will be run.
        assertEquals("love", viewModel.state.value.input)
    }

    @Test
    fun `a query waiting for a sign-in runs when the sign-in lands`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val aiSearch = FakeAiSearch(updates)
        val session = FakeOpenRouterSession(initial = false)
        val viewModel = searchViewModel(aiSearch = aiSearch, session = session)

        searchAi(viewModel)
        viewModel.beginSignIn()
        assertNull(aiSearch.lastQuery)

        session.signIn()
        advanceUntilIdle()

        assertEquals("love", aiSearch.lastQuery)
        assertTrue(viewModel.state.value.referencesSearching)
    }

    @Test
    fun `an empty box waits for nothing`() = runTest(dispatcher) {
        val aiSearch = FakeAiSearch(MutableSharedFlow())
        val session = FakeOpenRouterSession(initial = false)
        val viewModel = searchViewModel(aiSearch = aiSearch, session = session)

        viewModel.onQueryChanged("   ")
        viewModel.beginSignIn()
        session.signIn()
        advanceUntilIdle()

        assertNull(aiSearch.lastQuery)
    }

    @Test
    fun `the prompt is asked in the language the user reads in`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val aiSearch = FakeAiSearch(updates)
        val viewModel = searchViewModel(
            aiSearch = aiSearch,
            locale = AppLocale.EN,
            memory = "likes psalms",
        )

        searchAi(viewModel)
        advanceUntilIdle()

        assertEquals("natural English", aiSearch.lastAiLanguage)
        assertEquals("likes psalms", aiSearch.lastMemory)
    }

    @Test
    fun `a search that ends without answering is panels rather than spinners`() = runTest(dispatcher) {
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(emptyFlow()))
        searchAi(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.searching)
        assertTrue(viewModel.state.value.overviewFailed)
        assertEquals(ReferenceFailure.REQUEST, viewModel.state.value.referencesFailure)
    }

    @Test
    fun `a search that throws is panels rather than spinners`() = runTest(dispatcher) {
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(flow { throw IllegalStateException("no key") }))
        searchAi(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.searching)
        assertTrue(viewModel.state.value.overviewFailed)
        assertEquals(ReferenceFailure.REQUEST, viewModel.state.value.referencesFailure)
    }

    @Test
    fun `an answer that arrived is not overwritten when the search ends`() = runTest(dispatcher) {
        val updates = flowOf(
            AiSearchUpdate.OverviewReady("An overview."),
            AiSearchUpdate.ReferencesReady(listOf(aiHit(johnThreeSixteen(), "God so loved"))),
        )
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(updates))
        searchAi(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.searching)
        assertFalse(state.overviewFailed)
        assertNull(state.referencesFailure)
        assertEquals("An overview.", state.overview)
        assertEquals(1, state.aiHits.size)
    }

    @Test
    fun `a search already running cannot be replaced or have its mode changed`() = runTest(dispatcher) {
        val updates = MutableSharedFlow<AiSearchUpdate>(extraBufferCapacity = 8)
        val viewModel = searchViewModel(aiSearch = FakeAiSearch(updates))

        searchAi(viewModel)
        viewModel.onQueryChanged("faith")
        viewModel.search()
        viewModel.changeMode(SearchMode.TRADITIONAL)

        assertEquals(SearchMode.AI, viewModel.state.value.mode)
        assertEquals("love", viewModel.state.value.query)
    }

    @Test
    fun `switching mode clears the results but keeps the query box`() = runTest(dispatcher) {
        val viewModel = searchViewModel(FakeTraditionalSearch(hits = listOf(johnThreeSixteen())))

        viewModel.onQueryChanged("love")
        viewModel.search()
        advanceUntilIdle()
        viewModel.changeMode(SearchMode.AI)

        val state = viewModel.state.value
        assertEquals(SearchMode.AI, state.mode)
        assertEquals("love", state.input)
        assertEquals("", state.query)
        assertTrue(state.traditionalHits.isEmpty())
        assertTrue(state.showsHint)
    }

    /** Submits the box in AI mode, the way the sheet's forward button does. */
    private fun searchAi(viewModel: SearchViewModel, query: String = "love") {
        viewModel.changeMode(SearchMode.AI)
        viewModel.onQueryChanged(query)
        viewModel.search()
    }

    /**
     * The sheet's state machine over the given fakes, with the sign-in watcher already run so
     * `aiReady` reflects [session] the way it does on a real screen.
     */
    private fun TestScope.searchViewModel(
        traditional: TraditionalSearch = FakeTraditionalSearch(),
        aiSearch: AiSearch = FakeAiSearch(MutableSharedFlow()),
        session: OpenRouterSession = FakeOpenRouterSession(initial = true),
        locale: AppLocale = AppLocale.ZH_HANT,
        memory: String = "",
    ): SearchViewModel {
        val viewModel = SearchViewModel(
            traditionalSearch = traditional,
            aiSearch = aiSearch,
            aiMemory = FixedAiSearchMemory(memory),
            settingsRepository = settingsRepository(locale),
            session = session,
        )
        advanceUntilIdle()
        return viewModel
    }
}

/** A text search that answers with a fixed list, or fails, and remembers what it was asked. */
private class FakeTraditionalSearch(
    var hits: List<ScriptureHit> = emptyList(),
    private val failure: Throwable? = null,
) : TraditionalSearch {
    var lastQuery: String? = null

    override suspend fun invoke(query: String): List<ScriptureHit> {
        lastQuery = query
        failure?.let { throw it }
        return hits
    }
}

/** An AI search whose updates the test emits by hand, so their order is the test's to choose. */
private class FakeAiSearch(private val updates: Flow<AiSearchUpdate>) : AiSearch {
    var lastQuery: String? = null
    var lastMemory: String? = null
    var lastAiLanguage: String? = null

    override fun search(query: String, memory: String, aiLanguage: String): Flow<AiSearchUpdate> {
        lastQuery = query
        lastMemory = memory
        lastAiLanguage = aiLanguage
        return updates
    }
}

private class FixedAiSearchMemory(private val block: String) : AiSearchMemory {
    override suspend fun promptMemory(): String = block
}

private class FakeOpenRouterSession(initial: Boolean) : OpenRouterSession {
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

private fun settingsRepository(locale: AppLocale) = SettingsRepository(
    InMemorySettingsDataStore(Settings.newBuilder().setLocaleTag(locale.storageValue).build()),
)

private val John = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)

private val Matthew = BibleBook("MAT", 40, "馬太福音", "Matthew", 28, Testament.NEW)

private fun johnThreeSixteen() = hit(John, 3, 16)

private fun johnThreeSeventeen() = hit(John, 3, 17)

private fun mattFourOne() = hit(Matthew, 4, 1)

private fun hit(book: BibleBook, chapter: Int, verse: Int): ScriptureHit = ScriptureHit(
    book = book,
    chapter = chapter,
    verse = VersePair(number = verse, zh = "神愛世人", en = "For God so loved"),
)

private fun aiHit(hit: ScriptureHit, reason: String) = AiSearchHit(hit = hit, reason = reason)
