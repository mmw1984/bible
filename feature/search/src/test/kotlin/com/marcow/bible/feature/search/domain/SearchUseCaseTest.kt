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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The two halves an [AiSearchUseCase] is built from, each asked directly.
 *
 * `AiSearchUseCaseTest` covers them through the orchestration, which cannot see which of the two
 * request shapes went on the wire or what each half refused with. Both of those matter more than
 * they look:
 *
 *  - The model id is read from [OpenRouterModelId], and that port is the only thing Phase 4 swaps to
 *    read a user's stored model. If a half stopped going through it, Phase 4 would compile, pass
 *    every test here, and quietly keep asking `openrouter/free` for users who chose something else.
 *  - The two prompts are load-bearing and must not be reworded, but the *shape* each one is sent on
 *    is a choice the model can feel: the overview wants no schema at all, the references want
 *    `bible_search`, and reasoning is suppressed on the two paths in different ways.
 */
class SearchUseCaseTest {
    private val modelId = object : OpenRouterModelId {
        override suspend fun modelId(): String = MODEL
    }

    @Test
    fun `the overview half asks the model the port names`() = runTest {
        val client = ScriptedClient(OVERVIEW_ANSWER)

        val request = capture(client) { overview(client).invoke(QUERY, "", LANGUAGE) }

        assertEquals(MODEL, request.model)
        assertEquals(MODEL, request.body.string("model"))
    }

    @Test
    fun `the references half asks the same model`() = runTest {
        val client = ScriptedClient(REFERENCES_ANSWER)

        val request = capture(client) { references(client).invoke(QUERY, "", LANGUAGE) }

        assertEquals(MODEL, request.model)
        assertEquals(MODEL, request.body.string("model"))
    }

    @Test
    fun `the overview half asks for prose, with no schema to satisfy`() = runTest {
        // A JSON answer here would be prose the panel shows as raw braces, so this half asks for no
        // schema rather than for one it would not use.
        val client = ScriptedClient(OVERVIEW_ANSWER)

        val request = capture(client) { overview(client).invoke(QUERY, "", LANGUAGE) }

        assertEquals(OpenRouterSearchRequests.OVERVIEW_MAX_TOKENS, request.body.int("max_tokens"))
        assertFalse(request.body.containsKey("response_format"))
    }

    @Test
    fun `the references half asks against the search schema`() = runTest {
        val client = ScriptedClient(REFERENCES_ANSWER)

        val request = capture(client) { references(client).invoke(QUERY, "", LANGUAGE) }

        assertEquals(OpenRouterSearchRequests.REFERENCES_MAX_TOKENS, request.body.int("max_tokens"))
        assertEquals("bible_search", request.body.schemaName())
    }

    @Test
    fun `reasoning is suppressed differently on the two paths`() = runTest {
        // `{enabled: false, exclude: true}` for the overview, `{max_tokens: 32, exclude: true}` for
        // the references. Both drop the reasoning block; only the second spends tokens on it, because
        // only the second needs the model to read a table of verses before answering.
        val overviewClient = ScriptedClient(OVERVIEW_ANSWER)
        val referencesClient = ScriptedClient(REFERENCES_ANSWER)

        val overviewBody = capture(overviewClient) { overview(overviewClient).invoke(QUERY, "", LANGUAGE) }.body
        val referencesBody =
            capture(referencesClient) { references(referencesClient).invoke(QUERY, "", LANGUAGE) }.body

        assertEquals(OpenRouterSearchRequests.OVERVIEW_MAX_TOKENS, overviewBody.int("max_tokens"))
        assertEquals(false, overviewBody.reasoning().boolean("enabled"))
        assertEquals(OpenRouterSearchRequests.REASONING_MAX_TOKENS, referencesBody.reasoning().int("max_tokens"))
    }

    @Test
    fun `each half is sent its own prompt`() = runTest {
        // The prompts must not be reworded, but which one a half sends is the choice that would let
        // an overview answer come back as JSON and a references answer come back as prose.
        val overviewClient = ScriptedClient(OVERVIEW_ANSWER)
        val referencesClient = ScriptedClient(REFERENCES_ANSWER)

        overview(overviewClient).invoke(QUERY, MEMORY, LANGUAGE)
        references(referencesClient).invoke(QUERY, MEMORY, LANGUAGE)

        assertEquals(searchOverviewPrompt(QUERY, MEMORY, LANGUAGE), overviewClient.lastPrompt())
        assertEquals(searchReferencesPrompt(QUERY, MEMORY, LANGUAGE), referencesClient.lastPrompt())
    }

    @Test
    fun `the overview half publishes the prose the model wrote, cleaned`() = runTest {
        // The panel shows this directly, so the stripper is the only thing between the user and a
        // provider's reasoning block. `ModelOutputTest` covers what it strips; this covers that it
        // is still on the path to the panel.
        val client = ScriptedClient("<think>先分析關鍵字。</think>\n神的愛貫穿救恩。")

        assertEquals("神的愛貫穿救恩。", overview(client).invoke(QUERY, "", LANGUAGE))
    }

    @Test
    fun `the overview half refuses an empty answer with the message Dart threw`() = runTest {
        // `FormatException('AI overview returned no text.')`. Reproduced rather than folded into one
        // text with the references half's, because it is what a crash report carries.
        val failure = assertThrows<AiSearchFormatException> {
            overview(ScriptedClient("")).invoke(QUERY, "", LANGUAGE)
        }

        assertEquals(EMPTY_OVERVIEW_MESSAGE, failure.message)
        assertEquals("AI overview returned no text.", failure.message)
    }

    @Test
    fun `an overview that is only provider metadata is refused the same way`() = runTest {
        // Not empty going in, empty after the clean-up — which is why the stripper strips as much as
        // it does, and why this is worth a case of its own rather than only `""`.
        val failure = assertThrows<AiSearchFormatException> {
            overview(ScriptedClient("Content Safety: blocked")).invoke(QUERY, "", LANGUAGE)
        }

        assertEquals(EMPTY_OVERVIEW_MESSAGE, failure.message)
    }

    @Test
    fun `the references half publishes what it validated`() = runTest {
        val client = ScriptedClient(REFERENCES_ANSWER)

        val parsed = references(client).invoke(QUERY, "", LANGUAGE)

        val reference = parsed.scriptures.single()
        assertEquals("JHN", reference.bookId)
        assertEquals(3, reference.chapter)
        assertEquals(16, reference.verseStart)
        assertEquals("耶穌降生", reference.reason)
        assertEquals(listOf("神的愛從哪裡來？"), parsed.suggestedQuestions)
    }

    @Test
    fun `the references half refuses a payload the schema does not describe`() = runTest {
        // `FormatException('AI scripture search returned invalid JSON.')`: a model that ignored the
        // schema cannot be partly trusted, so nothing in it resolves.
        val failure = assertThrows<AiSearchFormatException> {
            references(ScriptedClient("here you go: JHN 3:16")).invoke(QUERY, "", LANGUAGE)
        }

        assertEquals(INVALID_REFERENCES_JSON_MESSAGE, failure.message)
        assertEquals("AI scripture search returned invalid JSON.", failure.message)
    }

    @Test
    fun `the two refusals are told apart by their message alone`() = runTest {
        // The sheet answers them identically, so the only thing separating the two causes is the
        // text — which is why folding them into one message would have cost a test its subject.
        val overviewFailure = assertThrows<AiSearchFormatException> {
            overview(ScriptedClient("")).invoke(QUERY, "", LANGUAGE)
        }
        val referencesFailure = assertThrows<AiSearchFormatException> {
            references(ScriptedClient("not JSON")).invoke(QUERY, "", LANGUAGE)
        }

        assertEquals("AI overview returned no text.", overviewFailure.message)
        assertEquals("AI scripture search returned invalid JSON.", referencesFailure.message)
    }

    private fun overview(client: OpenRouterChatClient) = SearchOverviewUseCase(client, modelId)

    private fun references(client: OpenRouterChatClient) =
        SearchReferencesUseCase(client, modelId, BibleRepository(FakeBibleDao(), FakeReadingProgressDao()))

    /** Runs one half and hands back the request it put on the wire. */
    private suspend fun capture(client: ScriptedClient, call: suspend () -> Unit): ChatCompletionRequest {
        call()
        return client.lastRequest()
    }
}

private const val MODEL = "openrouter/free"

private const val QUERY = "上帝的愛"

private const val MEMORY = "likes psalms"

private const val LANGUAGE = "natural Traditional Chinese"

private const val OVERVIEW_ANSWER = "神的愛貫穿救恩。"

private val REFERENCES_ANSWER = """
    {"scriptures":[
      {"bookId":"jhn","chapter":3,"verseStart":16,"verseEnd":16,"reason":"耶穌降生"}],
     "suggestedQuestions":["神的愛從哪裡來？"]}
""".trimIndent()

/** A transport that answers with one fixed string and remembers what it was asked. */
private class ScriptedClient(private val answer: String) : OpenRouterChatClient {
    private var request: ChatCompletionRequest? = null

    override suspend fun complete(request: ChatCompletionRequest): String {
        this.request = request
        return answer
    }

    fun lastRequest(): ChatCompletionRequest = requireNotNull(request)

    fun lastPrompt(): String = lastRequest().prompt()
}

/** The single user message's content, which is the prompt `_send` interpolated. */
private fun ChatCompletionRequest.prompt(): String {
    val messages = body["messages"] as? JsonArray ?: error("a search request carries its prompt as one message")
    val message = messages.single() as? JsonObject ?: error("the message is an object")
    return (message["content"] as? JsonPrimitive)?.content.orEmpty()
}

private fun JsonObject.reasoning(): JsonObject = this["reasoning"] as? JsonObject ?: error("the reasoning block")

/** `response_format.json_schema.name`, which is what the provider is told to validate against. */
private fun JsonObject.schemaName(): String? = (this["response_format"] as? JsonObject)
        ?.let { it["json_schema"] as? JsonObject }
        ?.let { it["name"] as? JsonPrimitive }
        ?.content

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private val GENESIS = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)

private val JOHN = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)

/** The canon the references half validates against; the search SQL itself is `BibleSearchTest`'s. */
private class FakeBibleDao : BibleDao {
    private val canon = listOf(GENESIS, JOHN)

    override suspend fun books(): List<BookEntity> = canon

    override suspend fun book(book: String): BookEntity? = canon.firstOrNull { it.id == book }

    override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> = emptyList()

    override suspend fun versesByTestament(testament: Int): List<VerseEntity> = emptyList()

    override suspend fun booksByTestament(testament: Int): List<BookEntity> = canon.filter { it.testament == testament }

    override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
}

private class FakeReadingProgressDao : ReadingProgressDao {
    override suspend fun progress(book: String): ReadingProgressEntity? = null

    override suspend fun allProgress(): List<ReadingProgressEntity> = emptyList()

    override suspend fun upsert(progress: ReadingProgressEntity) = Unit

    override suspend fun delete(book: String) = Unit
}
