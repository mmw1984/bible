package com.marcow.bible.core.network.aicore

import com.marcow.bible.core.model.AiProviderId
import com.marcow.bible.core.network.ai.AiAvailability
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import com.marcow.bible.core.network.ai.WebSearchMode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

/**
 * The provider differences `NATIVE_PLAN.md` §4 Phase 4 asks to be settled in the mapping layer rather
 * than leaking into the chat.
 *
 * The fake is a [NanoModel] rather than a Play services install because the two things under test are
 * decisions — which prompt goes over, which stop becomes `incomplete`, what happens to a web search
 * that has no tool to run — and none of them need a downloaded model to be observed.
 */
class GeminiNanoAiProviderTest {
    private val factory = FakeNanoModelFactory()
    private val provider = GeminiNanoAiProvider(factory)

    @Test
    fun `it is the on-device provider and the only one without a web search`() {
        assertEquals(AiProviderId.GeminiNano, provider.id)
        // The settings selector and the chat both ask the enum rather than the provider, so the flag
        // that hides the web affordance has to agree with what the provider actually does — and the
        // test below is what it does.
        assertFalse(AiProviderId.GeminiNano.supportsWebSearch)
    }

    @Test
    fun `the prompt goes over exactly as it was assembled`() = runTest {
        factory.chunks = listOf(NanoChunk("ans", NanoFinishReason.Stop))
        provider.stream(AiRequest.Chat(PROMPT)).toList()

        // Verbatim, and only once. §4.3 forbids reshaping a tuned prompt for the on-device model, so
        // there is no truncation, no summarising and no "shorter so it fits on a phone" step.
        assertEquals(listOf(PROMPT), factory.model.prompts)
    }

    @Test
    fun `a web search is dropped rather than sent as text`() = runTest {
        factory.chunks = listOf(NanoChunk("ans", NanoFinishReason.Stop))
        provider.stream(AiRequest.Chat(PROMPT), AiRequestOptions.ChatWithWebSearch).toList()

        // The same prompt, and one request: Nano has no tool to run, so `Automatic` and `Forced` both
        // take the no-tool path. The chat's own web-search retry is what turns the mode off, and it is
        // not a provider's business to retry.
        assertEquals(listOf(PROMPT), factory.model.prompts)
    }

    @Test
    fun `deltas arrive as content and never as reasoning`() = runTest {
        factory.chunks = listOf(NanoChunk("a"), NanoChunk("b"), NanoChunk("c", NanoFinishReason.Stop))

        val events = provider.stream(AiRequest.Chat(PROMPT)).toList()

        // The client returns one text stream, so the thinking block stays empty rather than erroring —
        // §4.2 asks for that to be a defined behaviour and §4 Phase 4 for it not to reach the UI.
        assertEquals(
            listOf(
                ChatEvent.ContentDelta("a"),
                ChatEvent.ContentDelta("b"),
                ChatEvent.ContentDelta("c"),
                ChatEvent.Finished(FinishReason.STOP),
            ),
            events,
        )
        assertFalse(events.any { it is ChatEvent.ReasoningDelta })
    }

    @Test
    fun `a truncated answer is marked incomplete`() = runTest {
        factory.chunks = listOf(NanoChunk("half an ans", NanoFinishReason.MaxTokens))

        val finished = provider.stream(AiRequest.Chat(PROMPT)).toList().last()

        // The one stop the chat branches on: 「回覆已停止或尚未完整」 rather than half a paragraph
        // presented as the whole reply.
        assertEquals(ChatEvent.Finished(FinishReason.LENGTH), finished)
    }

    @Test
    fun `a truncated answer is incomplete through complete too`() = runTest {
        factory.chunks = listOf(NanoChunk("half an ans", NanoFinishReason.MaxTokens))

        val response = provider.complete(AiRequest.Chat(PROMPT))

        assertEquals("half an ans", response.text)
        assertTrue(response.incomplete)
    }

    @Test
    fun `a safety stop is finished rather than failed`() = runTest {
        factory.chunks = listOf(NanoChunk("what I can say", NanoFinishReason.Safety))

        // Flutter did not treat the provider stopping itself as a failure either: the message was
        // finished with whatever text came back.
        assertEquals(
            listOf(ChatEvent.ContentDelta("what I can say"), ChatEvent.Finished(FinishReason.CONTENT_FILTER)),
            provider.stream(AiRequest.Chat(PROMPT)).toList(),
        )
    }

    @Test
    fun `a stream that stops without a reason still finishes`() = runTest {
        factory.chunks = listOf(NanoChunk("all of it"))

        // The frames ran out rather than the tokens, and the only honest thing to say about that is
        // that the answer finished — reporting `LENGTH` would invent a truncation that did not happen.
        assertEquals(
            listOf(ChatEvent.ContentDelta("all of it"), ChatEvent.Finished(FinishReason.STOP)),
            provider.stream(AiRequest.Chat(PROMPT)).toList(),
        )
    }

    @Test
    fun `a repeated stop reason is only reported once`() = runTest {
        // The client's candidates can carry a reason more than once, and a chat reads each `Finished`
        // as an answer ending — two of them would end the same message twice.
        factory.chunks = listOf(
            NanoChunk("a"),
            NanoChunk("", NanoFinishReason.Stop),
            NanoChunk("", NanoFinishReason.MaxTokens),
        )

        val events = provider.stream(AiRequest.Chat(PROMPT)).toList()

        // Only the first, because the second would overwrite it with a truncation that never happened.
        assertEquals(listOf(ChatEvent.ContentDelta("a"), ChatEvent.Finished(FinishReason.STOP)), events)
    }

    @Test
    fun `a finished answer carries no usage`() = runTest {
        factory.chunks = listOf(NanoChunk("ans", NanoFinishReason.Stop))

        // The client reports no token counts. A null is "unstated" and a zero would be a claim that
        // zero tokens were spent, which is a different thing.
        val finished = assertInstanceOf<ChatEvent.Finished>(provider.stream(AiRequest.Chat(PROMPT)).toList().last())
        assertEquals(null, finished.usage)
    }

    @Test
    fun `a shut gate is reported without offering a button`() = runTest {
        factory.availability = NanoAvailability.Gated(NanoGate.HardwareUnsupported)

        // Unlike OpenRouter's "not signed in" there is nothing the reader can press inside the app to
        // make a phone able to run a model, so the settings row must not draw a button.
        assertEquals(
            AiAvailability.Unavailable(NanoGate.HardwareUnsupported.message, signInRequired = false),
            provider.availability(),
        )
    }

    @Test
    fun `each gate reaches the settings row with its own reason`() = runTest {
        NanoGate.entries.forEach { gate ->
            factory.availability = NanoAvailability.Gated(gate)
            val availability = provider.availability()
            assertEquals(gate.message, assertInstanceOf<AiAvailability.Unavailable>(availability).reason)
        }
    }

    @Test
    fun `a phone that can answer is available`() = runTest {
        factory.availability = NanoAvailability.Ready
        assertEquals(AiAvailability.Available, provider.availability())
    }

    @Test
    fun `the gate is asked for every time rather than cached`() = runTest {
        factory.availability = NanoAvailability.Gated(NanoGate.ModelNotDownloaded)
        assertInstanceOf<AiAvailability.Unavailable>(provider.availability())

        // Play services for AI Edge gets installed and the model finishes downloading while the app is
        // running, so a cached "unavailable" would leave the row stale until the process restarted.
        factory.availability = NanoAvailability.Ready
        assertEquals(AiAvailability.Available, provider.availability())
    }

    @Test
    fun `a shut gate fails the question rather than asking an empty one`() {
        factory.availability = NanoAvailability.Gated(NanoGate.ServiceMissing)

        val thrown = assertThrows(NanoException.Gated::class.java) {
            runBlocking { provider.stream(AiRequest.Chat(PROMPT)).toList() }
        }

        assertEquals(NanoGate.ServiceMissing, thrown.gate)
        assertEquals(emptyList<String>(), factory.model.prompts)
    }

    @Test
    fun `the model is prepared and closed around the answer`() = runTest {
        factory.chunks = listOf(NanoChunk("ans", NanoFinishReason.Stop))
        provider.stream(AiRequest.Chat(PROMPT)).toList()

        // Prepared before the first chunk, because the weights have to be there before there is
        // anything to stream, and closed after, because leaving a service binding behind is what makes
        // a chat hold the radio open.
        assertEquals(listOf("prepare", "stream", "close"), factory.model.calls)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `an answer the reader stops still closes the model`() = runTest {
        // A model that keeps talking: stopping is what the chat's toolbar does, so a half-read answer
        // is the common case rather than the rare one, and leaving the model open keeps a Play services
        // binding alive for a question that is never coming.
        factory.supplyFrames = {
            emit(NanoChunk("more"))
            awaitCancellation()
        }
        val job = launch { provider.stream(AiRequest.Chat(PROMPT)).toList() }
        advanceUntilIdle()
        job.cancelAndJoin()

        // Only the first chunk was read, and the close still ran.
        assertEquals(listOf("prepare", "stream", "close"), factory.model.calls)
    }

    @Test
    fun `a search request sends no prompt rather than one it was never given`() = runTest {
        factory.chunks = listOf(NanoChunk("ans", NanoFinishReason.Stop))

        listOf(AiRequest.SearchOverview, AiRequest.SearchReferences).forEach { request ->
            factory.model.prompts.clear()
            provider.stream(request).toList()
            // Blank, as `OpenRouterChatRequests` sends, because those two kinds hold no text —
            // `feature/search` owns the prompts. A mistake in the search wiring shows up as an
            // obviously empty answer rather than a question the model was never asked.
            assertEquals(listOf(""), factory.model.prompts)
        }
    }

    @Test
    fun `the gate a phone cannot fix is not marked transient`() {
        // Only the two download states are "wait and it will work"; the rest need the reader to change
        // something, and a settings row that says otherwise sends them looking for a spinner.
        assertTrue(NanoGate.ModelNotDownloaded.transient)
        assertTrue(NanoGate.ModelUnavailable.transient)
        assertFalse(NanoGate.UnsupportedOsVersion.transient)
        assertFalse(NanoGate.ServiceMissing.transient)
        assertFalse(NanoGate.HardwareUnsupported.transient)
    }

    @Test
    fun `the dropped web search really was asked for`() {
        // Guards the provider's own claim rather than asserting a tautology: the chat's first attempt
        // asks for a tool, so if that ever stopped being true, dropping it silently would stop being
        // the right answer and the test above would keep passing for the wrong reason.
        assertEquals(WebSearchMode.Automatic, AiRequestOptions.ChatWithWebSearch.webSearch)
        assertEquals(WebSearchMode.Disabled, AiRequestOptions.ChatWithoutWebSearch.webSearch)
    }
}

private const val PROMPT = "你是聖經助手。\n<system>the tuned system prompt, verbatim</system>"

/** A [NanoModelFactory] that records what it was asked, so the assertions read as decisions. */
private class FakeNanoModelFactory : NanoModelFactory {
    val model = FakeNanoModel(this)
    var chunks: List<NanoChunk> = emptyList()
    var availability: NanoAvailability = NanoAvailability.Ready

    /** Set instead of [chunks] by the one test whose answer never ends on its own. */
    var supplyFrames: (suspend FlowCollector<NanoChunk>.() -> Unit)? = null

    override suspend fun availability(): NanoAvailability = availability

    override suspend fun open(): NanoModel {
        // Read through a local, because [availability] is a `var` and Kotlin will not smart-cast it.
        val gated = availability
        if (gated is NanoAvailability.Gated) throw NanoException.Gated(gated.gate)
        return model
    }
}

private class FakeNanoModel(private val factory: FakeNanoModelFactory) : NanoModel {
    val calls = mutableListOf<String>()
    val prompts = mutableListOf<String>()

    override suspend fun prepare() {
        calls += "prepare"
    }

    override fun stream(prompt: String): Flow<NanoChunk> {
        calls += "stream"
        prompts += prompt
        return flow {
            factory.supplyFrames?.invoke(this) ?: factory.chunks.forEach { emit(it) }
        }
    }

    override fun close() {
        calls += "close"
    }
}
