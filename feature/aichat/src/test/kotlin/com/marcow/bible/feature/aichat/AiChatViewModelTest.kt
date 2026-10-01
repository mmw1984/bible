package com.marcow.bible.feature.aichat

import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.network.ai.AiAvailability
import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.ai.AiProviderId
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.AiResponse
import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.feature.aichat.domain.AiChatSignIn
import com.marcow.bible.feature.aichat.domain.AiMemoryStore
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.domain.AiMessageKind
import com.marcow.bible.feature.aichat.domain.AiMessageRole
import com.marcow.bible.feature.aichat.domain.AnswerProgress
import com.marcow.bible.feature.aichat.domain.AskAiQuestion
import com.marcow.bible.feature.aichat.domain.AskQuestion
import com.marcow.bible.feature.aichat.domain.ChatAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The chat's state machine, over fakes rather than a model.
 *
 * What is worth writing down here is that an answer arriving in pieces is *one* message the whole
 * time, that a stop keeps the text that came and drops the rest, and that a failure takes the answer
 * away while leaving the reader's question where it was. None of that can be provoked reliably
 * against a live model — a stream that stops halfway is a timing coincidence — so the loop is stubbed
 * and told what to publish.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AiChatViewModelTest {
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
    fun `initializing restores the transcript and reports ready`() = runTest(dispatcher) {
        val stored = listOf(
            AiMessage(role = AiMessageRole.USER, text = "上次的問題"),
            AiMessage(role = AiMessageRole.ASSISTANT, text = "上次的答案"),
        )
        val viewModel = viewModel(memory = RecordingMemoryStore(messages = stored))
        advanceUntilIdle()

        viewModel.initialize()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.initialized)
        assertEquals(stored, viewModel.state.value.messages)
        assertNull(viewModel.state.value.initializationError)
    }

    @Test
    fun `a question is kept and the answer is appended to it`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("神是愛嗎？")
        advanceUntilIdle()

        assertEquals(listOf("user", "assistant"), viewModel.state.value.messages.map { it.role.promptPrefix })
        assertEquals("神是愛嗎？", viewModel.state.value.messages.first().text)
        assertEquals("答案", viewModel.state.value.messages.last().text)
    }

    @Test
    fun `a blank question is not asked`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("   ")
        advanceUntilIdle()

        assertTrue(ask.questions.isEmpty())
        assertTrue(viewModel.state.value.messages.isEmpty())
    }

    @Test
    fun `an answer streaming in pieces is one message the whole time`() = runTest(dispatcher) {
        val ask = ScriptedAsk(
            answers = listOf(chatAnswer("完整答案")),
            progress = listOf(
                AnswerProgress("完", "想"),
                AnswerProgress("完整", "想了一"),
                AnswerProgress("完整答案", "想了一下"),
            ),
        )
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals(2, messages.size, "the fragments are one message: $messages")
        assertEquals("完整答案", messages.last().text)
        assertEquals("想了一下", messages.last().reasoning)
    }

    @Test
    fun `a reasoning-only answer is still a message`() = runTest(dispatcher) {
        val ask = ScriptedAsk(
            answers = listOf(chatAnswer("", reasoning = "只有思考")),
            progress = listOf(AnswerProgress("", "只有思考")),
        )
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.last().text)
        assertEquals("只有思考", viewModel.state.value.messages.last().reasoning)
    }

    @Test
    fun `a stop before any text leaves the question and no answer`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(ChatAnswer("", "", incomplete = true, stopped = true)))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.size, "an empty answer is not a message")
        assertEquals("問題", viewModel.state.value.messages.first().text)
        assertFalse(viewModel.state.value.generating)
    }

    @Test
    fun `a failure takes the answer off the screen and leaves the question`() = runTest(dispatcher) {
        val ask = ScriptedAsk(failure = "AI model returned no response.")
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals(1, messages.size, "the failed answer went: $messages")
        assertEquals("user", messages.first().role.promptPrefix)
        assertNotNull(viewModel.state.value.generationError)
        assertFalse(viewModel.state.value.generating)
    }

    @Test
    fun `a bearer token never reaches the error panel`() = runTest(dispatcher) {
        val ask = ScriptedAsk(failure = "401: Bearer sk-secret-value")
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        assertEquals("未能完成回覆：Bearer [hidden]", viewModel.state.value.generationError)
    }

    @Test
    fun `an error long enough to be a response body is not shown`() = runTest(dispatcher) {
        val ask = ScriptedAsk(failure = "x".repeat(281))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        assertEquals("未能完成回覆，內容已保留，請稍後再試。", viewModel.state.value.generationError)
    }

    @Test
    fun `regenerate drops the answer and asks the same question again`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("第一個答案"), chatAnswer("第二個答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()
        viewModel.regenerate()
        advanceUntilIdle()

        assertEquals("第二個答案", viewModel.state.value.messages.last().text)
        assertEquals(2, viewModel.state.value.messages.size, "the old answer is gone, the question is not")
        assertEquals(2, ask.questions.size)
        assertEquals("問題", ask.questions.last().question)
    }

    @Test
    fun `regenerate with no question does nothing`() = runTest(dispatcher) {
        val ask = ScriptedAsk()
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.regenerate()
        advanceUntilIdle()

        assertTrue(ask.questions.isEmpty())
    }

    @Test
    fun `clearing empties the conversation and the memory behind it`() = runTest(dispatcher) {
        val memory = RecordingMemoryStore()
        val viewModel = viewModel(ask = ScriptedAsk(answers = listOf(chatAnswer("答案"))), memory = memory)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()
        viewModel.clear()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertTrue(memory.cleared)
    }

    @Test
    fun `a question is not asked while one is in flight`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("第一個問題")
        viewModel.send("第二個問題")
        advanceUntilIdle()

        assertEquals(1, ask.questions.size)
    }

    @Test
    fun `a signed-out chat holds the question rather than failing it`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask, signIn = FakeSignIn(signedIn = false))
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()

        assertTrue(ask.questions.isEmpty())
        assertTrue(viewModel.state.value.requiresLogin)
    }

    @Test
    fun `the attached passage is recorded on the reader's turn and reaches the prompt`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.openFromReader(
            ScriptureHandoff(reference = "JHN 3:16", context = "JHN 3:16\n中文：…\nEnglish: …"),
        )
        viewModel.send("解釋一下", kind = AiMessageKind.EXPLANATION)
        advanceUntilIdle()

        assertEquals("JHN 3:16", viewModel.state.value.messages.first().scripture)
        assertEquals(AiMessageKind.EXPLANATION, viewModel.state.value.messages.first().kind)
        assertTrue(ask.questions.last().scriptureContext?.startsWith("JHN 3:16") == true)
    }

    @Test
    fun `removing the attachment takes the context out of the next question`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案"), chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.openFromReader(ScriptureHandoff(reference = "JHN 3:16", context = "JHN 3:16"))
        viewModel.detachScripture()
        viewModel.send("再問")
        advanceUntilIdle()

        assertNull(ask.questions.last().scriptureContext)
        assertNull(viewModel.state.value.attachedScriptureReference)
    }

    @Test
    fun `a handoff with a question asks it without the reader touching the composer`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.openFromReader(
            ScriptureHandoff(
                reference = "JHN 3:16",
                context = "JHN 3:16\n中文：…\nEnglish: …",
                attachment = "JHN 3:16\n中文：…\nEnglish: …",
                question = "解釋這節經文",
                autoSend = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("解釋這節經文"), ask.questions.map { it.question })
        val turn = viewModel.state.value.messages.first()
        assertEquals(AiMessageKind.EXPLANATION, turn.kind)
        assertEquals("JHN 3:16", turn.scripture)
        assertTrue(ask.questions.single().scriptureContext?.startsWith("JHN 3:16") == true)
    }

    @Test
    fun `a handoff without a question only attaches the passage`() = runTest(dispatcher) {
        val ask = ScriptedAsk()
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.openFromReader(
            ScriptureHandoff(reference = "JHN 3:16", context = "JHN 3:16\n中文：…", attachment = "JHN 3:16"),
        )
        advanceUntilIdle()

        assertTrue(ask.questions.isEmpty())
        assertEquals("JHN 3:16", viewModel.state.value.attachmentChip)
    }

    @Test
    fun `the handoff's question is asked once, however often the chat is disturbed`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案"), chatAnswer("追答")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        val handoff = ScriptureHandoff(
            reference = "JHN 3:16",
            context = "JHN 3:16",
            question = "解釋這節經文",
            autoSend = true,
        )
        viewModel.openFromReader(handoff)
        advanceUntilIdle()
        viewModel.openFromReader(handoff)
        advanceUntilIdle()
        viewModel.send("追問")
        advanceUntilIdle()

        assertEquals(listOf("解釋這節經文", "追問"), ask.questions.map { it.question })
    }

    @Test
    fun `a handoff arriving before sign-in is asked once the provider arrives`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val signIn = FakeSignIn(signedIn = false)
        val viewModel = viewModel(ask = ask, signIn = signIn)
        viewModel.initialize()
        advanceUntilIdle()

        viewModel.openFromReader(
            ScriptureHandoff(
                reference = "JHN 3:16",
                context = "JHN 3:16",
                question = "解釋這節經文",
                autoSend = true,
            ),
        )
        advanceUntilIdle()
        assertTrue(ask.questions.isEmpty())

        signIn.signIn()
        advanceUntilIdle()

        assertEquals(listOf("解釋這節經文"), ask.questions.map { it.question })
    }

    @Test
    fun `a question typed before sign-in survives being asked too early`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val signIn = FakeSignIn(signedIn = false)
        val viewModel = viewModel(ask = ask, signIn = signIn)
        advanceUntilIdle()

        viewModel.send("我還沒登入")
        advanceUntilIdle()
        assertTrue(ask.questions.isEmpty())

        signIn.signIn()
        advanceUntilIdle()

        assertEquals(listOf("我還沒登入"), ask.questions.map { it.question })
    }

    @Test
    fun `a handoff with a blank chapter attaches nothing to ask with`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.openFromReader(
            ScriptureHandoff(
                reference = "JHN 3:16",
                context = "   ",
                attachment = "JHN 3:16",
                question = "解釋這節經文",
                autoSend = true,
            ),
        )
        advanceUntilIdle()

        assertNull(ask.questions.single().scriptureContext)
        assertNull(viewModel.state.value.attachmentChip)
        assertNull(viewModel.state.value.attachedScriptureReference)
    }

    @Test
    fun `the prompt is asked in the language the app is set to`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("answer")))
        val viewModel = viewModel(ask = ask, locale = AppLocale.EN)
        advanceUntilIdle()

        viewModel.send("Is God love?")
        advanceUntilIdle()

        assertEquals(AppLocale.EN.aiLanguage, ask.questions.last().aiLanguage)
    }

    @Test
    fun `the earlier turns are told to the model with the question being asked`() = runTest(dispatcher) {
        val ask = ScriptedAsk(answers = listOf(chatAnswer("答案"), chatAnswer("答案")))
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("第一個問題")
        advanceUntilIdle()
        viewModel.send("第二個問題")
        advanceUntilIdle()

        val recent = ask.questions.last().recent
        assertTrue(recent.contains("user: 第一個問題"), recent)
        assertTrue(recent.contains("assistant: 答案"), recent)
        assertFalse(recent.contains("第二個問題"), "the question is not repeated in the block: $recent")
    }

    @Test
    fun `a stop ends the question and leaves what had arrived`() = runTest(dispatcher) {
        val ask = ScriptedAsk(
            answers = listOf(chatAnswer("半個答案", incomplete = true, stopped = true)),
            progress = listOf(AnswerProgress("半個答案", "")),
        )
        val viewModel = viewModel(ask = ask)
        advanceUntilIdle()

        viewModel.send("問題")
        advanceUntilIdle()
        // The answer is already written, so the stop the reader presses now has nothing in flight —
        // which is the state the button is hidden in. The flag itself is what the loop reads, and
        // this is the case where the reader stops *during* the first round.
        assertFalse(viewModel.state.value.generating)
        viewModel.stop()

        assertEquals("半個答案", viewModel.state.value.messages.last().text)
    }

    /** The chat over fakes, with the sign-in and locale watchers already run. */
    private fun TestScope.viewModel(
        ask: ScriptedAsk = ScriptedAsk(),
        memory: RecordingMemoryStore = RecordingMemoryStore(),
        signIn: FakeSignIn = FakeSignIn(signedIn = true),
        locale: AppLocale = AppLocale.ZH_HANT,
    ): AiChatViewModel = AiChatViewModel(
        askQuestion = ask,
        memoryStore = memory,
        settingsRepository = SettingsRepository(
            InMemorySettingsDataStore(Settings.newBuilder().setLocaleTag(locale.storageValue).build()),
        ),
        signIn = signIn,
        provider = UnusedProvider,
    ).also { advanceUntilIdle() }
}

/** One finished answer, which is all a stubbed question returns. */
private fun chatAnswer(text: String, reasoning: String = "", incomplete: Boolean = false) =
    ChatAnswer(text = text, reasoning = reasoning, incomplete = incomplete, stopped = false)

/**
 * The answering loop, stubbed.
 *
 * [progress] is published before the answer is returned, so a test can say how the text arrived
 * without a stream: the controller's behaviour is the same either way, because the only thing it sees
 * is the callback.
 *
 * [failure] is thrown from [ask] rather than being a [ChatAnswer] value, because a failed turn is not
 * a turn — that is the whole difference the chat's error panel is for, and returning one would put a
 * message on the screen that the reader never receives.
 */
private class ScriptedAsk(
    private val answers: List<ChatAnswer> = emptyList(),
    private val progress: List<AnswerProgress> = emptyList(),
    private val failure: String? = null,
) : AskAiQuestion {
    val questions: MutableList<AskQuestion> = mutableListOf()

    override suspend fun ask(
        provider: AiProvider,
        question: AskQuestion,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): ChatAnswer {
        questions += question
        progress.forEach(onProgress)
        failure?.let { throw IllegalStateException(it) }
        val next = answers.getOrNull(questions.size - 1)
            ?: error("the chat asked ${questions.size} questions and the test scripted ${answers.size}")
        if (next.stopped && next.text.isEmpty()) return ChatAnswer("", "", incomplete = true, stopped = true)
        return next
    }
}

/** The memory behind the chat, recording what was written to it. */
private class RecordingMemoryStore(
    private val messages: List<AiMessage> = emptyList(),
    private val memory: String = "",
) : AiMemoryStore {
    val recorded: MutableList<AiMessage> = mutableListOf()
    var replaced: List<AiMessage>? = null
    var cleared = false

    override suspend fun promptMemory(maxCharacters: Int): String = memory

    override suspend fun transcript(limit: Int): List<AiMessage> = messages

    override suspend fun recordMessage(message: AiMessage) {
        recorded += message
    }

    override suspend fun replaceTranscript(messages: List<AiMessage>) {
        replaced = messages
    }

    override suspend fun clear() {
        cleared = true
    }
}

/**
 * The sign-in, as a change the view model can watch.
 *
 * [signedIn] is a method as well as a constructor argument because the reader can ask a question
 * before a provider exists, and the behaviour worth pinning is what happens *after* they sign in.
 */
private class FakeSignIn(signedIn: Boolean) : AiChatSignIn {
    private val state = MutableStateFlow(signedIn)
    private val errors = MutableStateFlow<String?>(null)

    override val signedIn: StateFlow<Boolean> = state.asStateFlow()
    override val lastError: StateFlow<String?> = errors.asStateFlow()

    var initialized = false
    var signInRequested = false

    /** The reader finished signing in, which is what releases a question held for want of a provider. */
    fun signedIn() {
        state.value = true
    }

    override suspend fun initialize() {
        initialized = true
    }

    override suspend fun beginSignIn() {
        signInRequested = true
    }
}

/** A provider the stub never asks, standing in for the binding so the view model can be built. */
private object UnusedProvider : AiProvider {
    override val id = AiProviderId.OpenRouter
    override suspend fun availability(): AiAvailability = AiAvailability.Available
    override fun stream(request: AiRequest, options: AiRequestOptions): Flow<ChatEvent> = emptyFlow()
    override suspend fun complete(request: AiRequest, options: AiRequestOptions): AiResponse = AiResponse("")
}
