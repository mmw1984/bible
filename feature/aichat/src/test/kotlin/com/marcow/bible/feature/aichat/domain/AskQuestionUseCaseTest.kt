package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.model.AiProviderId
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import com.marcow.bible.core.network.ai.AiAvailability
import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.AiResponse
import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import com.marcow.bible.core.network.openrouter.OpenRouterException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `_answerExistingMessage` of `legacy/flutter/lib/ai_service.dart:421`–`583`, driven by a provider
 * that answers exactly as each test scripts it.
 *
 * What is worth pinning here is the order of the four things the loop does, because each one only
 * happens because the one before it did not: the retry without the web tool is there because the
 * first attempt failed, the forced answer is there because the model is still asking for a passage
 * after three requests, the continuation is there because the answer never said `[[END]]`, and the
 * incomplete flag is what is left when none of them finished the job. A test that scripted a single
 * round and asserted only its text would pass for a loop that had none of them.
 */
internal class AskQuestionUseCaseTest {
    private val bibleRepository = mockk<BibleRepository>()
    private val useCase = AskQuestionUseCase(ScriptureToolRunner(bibleRepository))

    @Test
    fun `the answer is cleaned of its marker, and the first round asks with the web-search tool`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("神愛世人 [[EN"),
                        ChatEvent.ContentDelta("D]]"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )
        val progress = mutableListOf<AnswerProgress>()

        val answer = useCase.ask(provider, QUESTION, onProgress = { progress += it })

        assertEquals("神愛世人", answer.text)
        assertFalse(answer.incomplete)
        assertEquals(PROMPT, provider.promptAt(0))
        assertEquals(AiRequestOptions.ChatWithWebSearch, provider.optionsAt(0))
        assertEquals("神愛世人", progress.last().text)
    }

    @Test
    fun `a first attempt that fails is asked again without the web-search tool`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Throws("this routed model refuses the tool"),
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("不用搜尋也能回答。"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )
        val progress = mutableListOf<AnswerProgress>()

        val answer = useCase.ask(provider, QUESTION, onProgress = { progress += it })

        assertEquals("不用搜尋也能回答。", answer.text)
        assertEquals(2, provider.requests.size)
        assertEquals(AiRequestOptions.ChatWithoutWebSearch, provider.optionsAt(1))
        // The same question, and nothing else changed: what failed was the combination, not the search.
        assertEquals(provider.promptAt(0), provider.promptAt(1))
        // What the failed attempt had already shown is taken away again.
        assertEquals(AnswerProgress(text = "", reasoning = ""), progress.first())
    }

    @Test
    fun `a get_scripture request is answered out of the app's own Bible`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN
        coEvery { bibleRepository.chapter("JHN", 3) } returns listOf(VERSE_16)
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(listOf(ChatEvent.ContentDelta(TOOL_REQUEST))),
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("約翰福音 3:16 說神愛世人。[[END]]"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )
        val progress = mutableListOf<AnswerProgress>()

        val answer = useCase.ask(provider, QUESTION, onProgress = { progress += it })

        assertEquals("約翰福音 3:16 說神愛世人。", answer.text)
        assertEquals(toolFollowUpPrompt(PROMPT, listOf(VERSE_16_TEXT)), provider.promptAt(1))
        assertEquals(AiRequestOptions(), provider.optionsAt(1))
        // The JSON the model wrote is a protocol exchange, and the reader never sees one.
        assertTrue(progress.none { it.text.contains("get_scripture") }, "the tool request was shown")
        assertEquals("約翰福音 3:16 說神愛世人。", progress.last().text)
    }

    @Test
    fun `a passage the app cannot supply is reported to the model rather than failing the answer`() = runTest {
        coEvery { bibleRepository.book(any()) } returns null
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(ChatEvent.ContentDelta(UNKNOWN_BOOK_REQUEST)),
                ),
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("我找不到這卷書，只能按已有的經文回答。[[END]]"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("我找不到這卷書，只能按已有的經文回答。", answer.text)
        assertEquals(toolFollowUpPrompt(PROMPT, listOf("Tool error: unknown bookId XYZ")), provider.promptAt(1))
    }

    @Test
    fun `a model that is still asking after three requests is given one forced answer`() = runTest {
        coEvery { bibleRepository.book(any()) } returns JOHN
        coEvery { bibleRepository.chapter(any(), any()) } returns listOf(VERSE_16)
        val provider = ScriptedProvider(
            List(4) { Scripted.Events(listOf(ChatEvent.ContentDelta(TOOL_REQUEST))) } +
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("神愛世人。[[END]]"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("神愛世人。", answer.text)
        assertFalse(answer.incomplete)
        assertEquals(5, provider.requests.size)
        assertEquals(finalFollowUpPrompt(PROMPT, List(3) { VERSE_16_TEXT }), provider.promptAt(4))
    }

    @Test
    fun `a tool request as the last thing the model said is not an incomplete answer`() = runTest {
        coEvery { bibleRepository.book(any()) } returns JOHN
        coEvery { bibleRepository.chapter(any(), any()) } returns listOf(VERSE_16)
        val provider = ScriptedProvider(
            List(5) { Scripted.Events(listOf(ChatEvent.ContentDelta(TOOL_REQUEST))) },
        )

        val answer = useCase.ask(provider, QUESTION)

        // The forced round asked again and got a request back, which is not an answer the reader was
        // left waiting on — so the loop stops and the message is not marked incomplete.
        assertEquals(5, provider.requests.size)
        assertEquals(TOOL_REQUEST, answer.text)
        assertFalse(answer.incomplete)
    }

    @Test
    fun `an answer cut at the ceiling is continued from its own tail`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("第一段。"),
                        ChatEvent.Finished(FinishReason.LENGTH),
                    ),
                ),
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("第二段。"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("第一段。\n\n第二段。", answer.text)
        assertFalse(answer.incomplete)
        assertEquals(2, provider.requests.size)
        assertEquals(continuationPrompt(PROMPT, "第一段。"), provider.promptAt(1))
        assertEquals(AiRequestOptions(), provider.optionsAt(1))
    }

    @Test
    fun `an answer is continued at most twice, and is then left marked incomplete`() = runTest {
        val provider = ScriptedProvider(
            List(3) { index ->
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("第${index + 1}段。"),
                        ChatEvent.Finished(FinishReason.LENGTH),
                    ),
                )
            },
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("第1段。\n\n第2段。\n\n第3段。", answer.text)
        assertTrue(answer.incomplete)
        assertEquals(3, provider.requests.size)
    }

    @Test
    fun `a continuation that fails keeps what the model wrote and marks the answer incomplete`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("第一段。"),
                        ChatEvent.Finished(FinishReason.LENGTH),
                    ),
                ),
                Scripted.Throws("the stream ended without a finish reason"),
            ),
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("第一段。", answer.text)
        assertTrue(answer.incomplete)
        assertEquals(2, provider.requests.size)
    }

    @Test
    fun `the thinking of every segment is kept, and its metadata is not`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(
                        ChatEvent.ReasoningDelta("safety: safe\n先想清楚這節經文。"),
                        ChatEvent.ContentDelta("第一段。"),
                        ChatEvent.Finished(FinishReason.LENGTH),
                    ),
                ),
                Scripted.Events(
                    listOf(
                        ChatEvent.ReasoningDelta("再想第二段。"),
                        ChatEvent.ContentDelta("第二段。"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )

        val answer = useCase.ask(provider, QUESTION)

        assertEquals("先想清楚這節經文。再想第二段。", answer.reasoning)
    }

    @Test
    fun `a stop before any text arrived leaves nothing to keep`() = runTest {
        val provider = ScriptedProvider(
            listOf(
                Scripted.Events(
                    listOf(
                        ChatEvent.ContentDelta("半句"),
                        ChatEvent.Finished(FinishReason.STOP),
                    ),
                ),
            ),
        )
        val progress = mutableListOf<AnswerProgress>()

        val answer = useCase.ask(provider, QUESTION, onProgress = { progress += it }, isStopped = { true })

        assertEquals("", answer.text)
        assertTrue(answer.stopped)
        assertTrue(progress.isEmpty(), "a stopped answer showed ${progress.size} updates")
        // Nothing is asked again: the reader stopped, so the loop is over rather than paused.
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun `a round that carried no text at all is asked once more, and then it is an error`() = runTest {
        val provider = ScriptedProvider(
            List(2) { Scripted.Events(listOf(ChatEvent.Finished(FinishReason.STOP))) },
        )

        val failure = runCatching { useCase.ask(provider, QUESTION) }.exceptionOrNull()

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
        assertEquals("AI model returned no response.", failure?.message)
        assertEquals(2, provider.requests.size)
    }

    @Test
    fun `a round that reported a failure and no text is failed with the provider's own message`() = runTest {
        val provider = ScriptedProvider(
            List(2) { Scripted.Events(listOf(ChatEvent.Failure("quota exceeded"))) },
        )

        val failure = runCatching { useCase.ask(provider, QUESTION) }.exceptionOrNull()

        assertEquals(AiAnswerException::class.java, failure?.javaClass)
        assertEquals("quota exceeded", failure?.message)
    }

    private companion object {
        const val TOOL_REQUEST = """{"tool":"get_scripture","bookId":"JHN","chapter":3,"verseStart":16}"""

        const val UNKNOWN_BOOK_REQUEST =
            """{"tool":"get_scripture","bookId":"XYZ","chapter":1,"verseStart":1}"""

        val VERSE_16 = VersePair(number = 16, zh = "神愛世人", en = "For God so loved the world")

        const val VERSE_16_TEXT = "約翰福音 3:16\n中文：神愛世人\nEnglish: For God so loved the world"

        val JOHN = BibleBook(
            id = "JHN",
            ordinal = 43,
            nameZh = "約翰福音",
            nameEn = "John",
            chapters = 21,
            testament = Testament.NEW,
        )

        val QUESTION = AskQuestion(
            question = "神愛世人是什么意思？",
            aiLanguage = "natural Traditional Chinese",
        )

        val PROMPT = chatPrompt(
            question = QUESTION.question,
            scriptureContext = null,
            memory = "",
            recent = "",
            aiLanguage = QUESTION.aiLanguage,
        )
    }
}

/** One scripted round, or the way a request fails on the way in. */
private sealed interface Scripted {
    /** The events the provider hands over for this round. */
    data class Events(val events: List<ChatEvent>) : Scripted

    /** The request is refused before any of it arrives, which is what the first attempt meets. */
    data class Throws(val message: String) : Scripted
}

/**
 * A provider that answers round by round, in the order it was given, and remembers what it was asked.
 *
 * Asking for a round that was not scripted is an error rather than an empty answer: a loop that runs
 * one round more than a test expected is a loop the test should fail on, not a loop that quietly
 * returned nothing.
 */
private class ScriptedProvider(private val rounds: List<Scripted>) : AiProvider {
    val requests: MutableList<Pair<AiRequest, AiRequestOptions>> = mutableListOf()

    override val id = AiProviderId.OpenRouter

    override suspend fun availability(): AiAvailability = AiAvailability.Available

    override fun stream(request: AiRequest, options: AiRequestOptions): Flow<ChatEvent> {
        val round = rounds.getOrNull(requests.size)
            ?: error("the use case asked for round ${requests.size + 1} and the test scripted ${rounds.size}")
        requests += request to options
        return when (round) {
            is Scripted.Events -> round.events.asFlow()
            is Scripted.Throws -> flow { throw OpenRouterException.RequestFailed(round.message) }
        }
    }

    override suspend fun complete(request: AiRequest, options: AiRequestOptions): AiResponse =
        error("the chat always streams")

    fun promptAt(index: Int): String = (requests[index].first as AiRequest.Chat).prompt

    fun optionsAt(index: Int): AiRequestOptions = requests[index].second
}
