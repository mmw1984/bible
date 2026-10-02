package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.database.BookEntity
import com.marcow.bible.core.database.ReadingProgressDao
import com.marcow.bible.core.database.ReadingProgressEntity
import com.marcow.bible.core.database.ScriptureSearchRow
import com.marcow.bible.core.database.VerseEntity
import com.marcow.bible.core.network.openrouter.ChatCompletionRequest
import com.marcow.bible.core.network.openrouter.OpenRouterChatClient
import com.marcow.bible.core.network.openrouter.OpenRouterModelId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `BibleAiController.search` in `legacy/flutter/lib/ai_service.dart`: the two halves of an AI search,
 * and the four ways one of them can end.
 *
 * This is the whole of the `Future.wait([loadOverview(), loadReferences()])`, so it is the one place the
 * sheet's two independent loading rows and its four failure panels are decided. The view model's test
 * stubs [AiSearch] out entirely, so nothing below it was covered — neither that the two requests really
 * are in flight at once, nor that each branch publishes its own verdict instead of letting a failure
 * escape and hide a peer that succeeded.
 *
 * The updates are built from the real use cases over a fake transport rather than from stubs, because
 * the wiring between them is the thing worth testing: a fake `AiSearch` would only prove that a flow
 * emits what a flow was told to emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiSearchUseCaseTest {
    @Test
    fun `the references request goes out while the overview is still out`() = runTest {
        // The overview is held open on a gate. Awaited in sequence the references request could not
        // exist yet, so seeing both prompts recorded is the `Future.wait` rather than a `then` — and
        // the references half landing while its peer is still outstanding is the two loading rows
        // being independent, which is the whole reason they are two.
        val overview = CompletableDeferred<String>()
        val client = FakeChatClient(overview = { overview.await() }, references = { REFERENCES })
        val updates = mutableListOf<AiSearchUpdate>()

        backgroundScope.launch { aiSearch(client).search(QUERY, "", LANGUAGE).toList(updates) }
        runCurrent()

        assertEquals(setOf(OVERVIEW_MARKER, REFERENCES_MARKER), client.promptMarkers().toSet())
        assertEquals(1, updates.resolved().size)
        assertEquals(emptyList<String>(), updates.overviews())

        overview.complete(OVERVIEW_PROSE)
        runCurrent()

        assertEquals("An overview.", updates.overviews().single())
    }

    @Test
    fun `the verses come from this device's database and the reason from the model`() = runTest {
        val updates = search(FakeChatClient(overview = { OVERVIEW_PROSE }, references = { REFERENCES }))

        val hit = updates.resolved().single().hits.single()
        assertEquals(VERSE_16_ZH, hit.hit.verse.zh)
        assertEquals("耶穌降生", hit.reason)
    }

    @Test
    fun `the query, the memory and the language reach both prompts`() = runTest {
        val client = FakeChatClient(overview = { OVERVIEW_PROSE }, references = { REFERENCES })

        aiSearch(client).search(QUERY, MEMORY, LANGUAGE).toList()

        client.prompts.forEach { prompt ->
            assertTrue(prompt.contains("Search query: $QUERY"), prompt)
            assertTrue(prompt.contains(MEMORY), prompt)
            assertTrue(prompt.contains(LANGUAGE), prompt)
        }
    }

    @Test
    fun `an overview the model left empty is a failure rather than an empty panel`() = runTest {
        // The overview *is* the panel, so there is nowhere to hide a blank: Flutter threw rather than
        // publishing `''`, and the sheet drew `overview_failed` instead.
        val updates = search(FakeChatClient(overview = { "" }, references = { REFERENCES }))

        assertEquals(1, updates.overviewFailures())
        // The references half is a separate request and is untouched by it.
        assertEquals(1, updates.resolved().size)
    }

    @Test
    fun `an overview that failed leaves the references that arrived`() = runTest {
        val updates = search(FakeChatClient(overview = { fail() }, references = { REFERENCES }))

        assertEquals(1, updates.overviewFailures())
        assertEquals(1, updates.resolved().size)
    }

    @Test
    fun `a references request that failed is the request half and the overview stays`() = runTest {
        val updates = search(FakeChatClient(overview = { OVERVIEW_PROSE }, references = { fail() }))

        assertEquals(OVERVIEW_PROSE, updates.overviews().single())
        assertEquals(ReferenceFailure.REQUEST, updates.referenceFailures().single().failure)
    }

    @Test
    fun `an answer the schema does not describe is the request half`() = runTest {
        // A payload that is not the promised shape means nothing in it can be trusted, so it fails the
        // whole request rather than resolving to an empty tile list.
        val updates = search(FakeChatClient(overview = { OVERVIEW_PROSE }, references = { "not JSON at all" }))

        assertEquals(ReferenceFailure.REQUEST, updates.referenceFailures().single().failure)
    }

    @Test
    fun `a chapter that will not load is the verses half and not the request one`() = runTest {
        // The distinction the two `ai_scripture_results` panels exist for: the model did its job and
        // this device's database is what would not answer, so the copy points at the device.
        val updates = search(
            FakeChatClient(overview = { OVERVIEW_PROSE }, references = { REFERENCES }),
            chapterFailure = IllegalStateException("no such column: text_cuv"),
        )

        assertEquals(ReferenceFailure.VERSES, updates.referenceFailures().single().failure)
        assertEquals(OVERVIEW_PROSE, updates.overviews().single())
    }

    @Test
    fun `a model that proposed nothing resolves to no tiles rather than to a failure`() = runTest {
        // An empty list is an answer, and the sheet draws `no_results` under it rather than an error.
        val updates = search(FakeChatClient(overview = { OVERVIEW_PROSE }, references = { NO_REFERENCES }))

        assertEquals(emptyList<AiSearchHit>(), updates.resolved().single().hits)
    }

    @Test
    fun `both halves failing publishes two failures and lets none escape`() = runTest {
        // Neither branch may throw out of the flow: the sheet's `finally` sweep is a backstop, and the
        // Flutter build needed it only because `Future.wait` could return with a callback unfired.
        val updates = search(FakeChatClient(overview = { fail() }, references = { fail() }))

        assertEquals(1, updates.overviewFailures())
        assertEquals(ReferenceFailure.REQUEST, updates.referenceFailures().single().failure)
    }

    @Test
    fun `a cancelled search publishes nothing at all`() = runTest {
        // The one deliberate difference from Dart, and the reason the sheet cannot be left showing a
        // failure panel belonging to a query the user has already replaced. Dart had no cancellation —
        // a stale callback only had to notice `query != value` — so this branch did not exist there.
        val gate = CompletableDeferred<String>()
        val client = FakeChatClient(overview = { gate.await() }, references = { gate.await() })
        val updates = mutableListOf<AiSearchUpdate>()

        val job = backgroundScope.launch { aiSearch(client).search(QUERY, "", LANGUAGE).toList(updates) }
        runCurrent()
        job.cancel()
        runCurrent()

        assertEquals(emptyList<AiSearchUpdate>(), updates)
    }

    /** Collects the updates of one search against the given transport. */
    private suspend fun search(client: FakeChatClient, chapterFailure: Throwable? = null): List<AiSearchUpdate> =
        aiSearch(client, chapterFailure).search(QUERY, MEMORY, LANGUAGE).toList()

    /** The real use cases over a fake transport, which is the wiring worth testing. */
    private fun aiSearch(client: FakeChatClient, chapterFailure: Throwable? = null): AiSearchUseCase {
        val repository = BibleRepository(AiSearchFakeBibleDao(chapterFailure), AiSearchFakeReadingProgressDao())
        val modelId = object : OpenRouterModelId {
            override suspend fun modelId(): String = MODEL
        }
        return AiSearchUseCase(
            searchOverview = SearchOverviewUseCase(client, modelId),
            searchReferences = SearchReferencesUseCase(client, modelId, repository),
            resolveReferences = ResolveReferencesUseCase(repository),
        )
    }
}

/** The overview that arrived, which carries the prose its panel shows. */
private fun List<AiSearchUpdate>.overviews(): List<String> =
    filterIsInstance<AiSearchUpdate.OverviewReady>().map { it.overview }

private fun List<AiSearchUpdate>.overviewFailures(): Int = count { it == AiSearchUpdate.OverviewFailed }

/** The references that resolved to tiles, which is what `ReferencesReady` carries. */
private fun List<AiSearchUpdate>.resolved(): List<AiSearchUpdate.ReferencesReady> =
    filterIsInstance<AiSearchUpdate.ReferencesReady>()

/** The references that failed, told apart by [AiSearchUpdate.ReferencesFailed.failure]. */
private fun List<AiSearchUpdate>.referenceFailures(): List<AiSearchUpdate.ReferencesFailed> =
    filterIsInstance<AiSearchUpdate.ReferencesFailed>()

/**
 * A transport that answers whichever of the two search prompts it is handed, and records both.
 *
 * The prompts carry the markers the request builders key on, and those same markers are what tell this
 * fake which half is asking — so the exact text the provider would have seen is what decides the answer.
 */
private class FakeChatClient(
    private val overview: suspend (String) -> String,
    private val references: suspend (String) -> String,
) : OpenRouterChatClient {
    val prompts = mutableListOf<String>()

    override suspend fun complete(request: ChatCompletionRequest): String {
        val prompt = request.prompt()
        prompts += prompt
        return if (prompt.contains(OVERVIEW_MARKER)) overview(prompt) else references(prompt)
    }

    fun promptMarkers(): List<String> = prompts.map { prompt ->
        if (prompt.contains(OVERVIEW_MARKER)) OVERVIEW_MARKER else REFERENCES_MARKER
    }
}

/** The single user message's content, which is the prompt `_send` interpolated. */
private fun ChatCompletionRequest.prompt(): String {
    val messages = body["messages"] as? JsonArray ?: error("a search request carries its prompt as one message")
    val message = messages.single() as? JsonObject ?: error("the message is an object")
    return (message["content"] as? JsonPrimitive)?.content.orEmpty()
}

/** What the transport throws for a 429, which is the failure the `catch` on each branch exists for. */
private suspend fun fail(): Nothing = error("OpenRouter request failed (429): rate limited")

private const val OVERVIEW_MARKER = "BIBLE_SEARCH_OVERVIEW"

private const val REFERENCES_MARKER = "BIBLE_SEARCH_REFERENCES_JSON"

private const val OVERVIEW_PROSE = "An overview."

private const val VERSE_16_ZH = "神愛世人，甚至將他的獨生子賜給他們。"

private const val MODEL = "openrouter/free"

private const val QUERY = "上帝的愛"

private const val MEMORY = "likes psalms"

private const val LANGUAGE = "natural Traditional Chinese"

private val REFERENCES = """
    {"scriptures":[
      {"bookId":"jhn","chapter":3,"verseStart":16,"verseEnd":16,"reason":"耶穌降生"}],
     "suggestedQuestions":["神的愛從哪裡來？"]}
""".trimIndent()

private const val NO_REFERENCES = """{"scriptures":[],"suggestedQuestions":[]}"""

private val GENESIS = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)

private val JOHN = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)

/** The books and verses the references half resolves against; the search SQL is `BibleSearchTest`'s. */
private class AiSearchFakeBibleDao(private val chapterFailure: Throwable? = null) : BibleDao {
    private val canon = listOf(GENESIS, JOHN)

    private val rows = listOf(
        VerseEntity("JHN", 3, 16, VERSE_16_ZH, "For God so loved the world"),
        VerseEntity("JHN", 3, 17, "神差他的兒來", "For God did not send his Son"),
    )

    override suspend fun books(): List<BookEntity> = canon

    override suspend fun book(book: String): BookEntity? = canon.firstOrNull { it.id == book }

    override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> {
        chapterFailure?.let { throw it }
        return rows.filter { it.bookId == book && it.chapter == chapter }.sortedBy { it.verse }
    }

    override suspend fun versesByTestament(testament: Int): List<VerseEntity> =
        rows.filter { verse -> canon.any { it.id == verse.bookId && it.testament == testament } }

    override suspend fun booksByTestament(testament: Int): List<BookEntity> = canon.filter { it.testament == testament }

    override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
}

private class AiSearchFakeReadingProgressDao : ReadingProgressDao {
    override suspend fun progress(book: String): ReadingProgressEntity? = null

    override suspend fun allProgress(): List<ReadingProgressEntity> = emptyList()

    override suspend fun upsert(progress: ReadingProgressEntity) = Unit

    override suspend fun delete(book: String) = Unit
}
