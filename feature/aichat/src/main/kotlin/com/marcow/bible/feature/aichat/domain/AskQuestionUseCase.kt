package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The answering loop of `_answerExistingMessage` in `legacy/flutter/lib/ai_service.dart:421`–`583`,
 * moved across: one question asked of a model, and everything that happens between the question and
 * the message the reader keeps.
 *
 * Four things happen here, and each is bounded rather than negotiated:
 *
 *  1. The question is asked **with** the web-search tool, and asked again without it when that fails
 *     (`:453`). Some routed OpenRouter models refuse the optional server tool alongside reasoning, and
 *     `NATIVE_PLAN.md` §4.5 is explicit that a tool compatibility issue must not throw the whole
 *     response away.
 *  2. A `get_scripture` request in the answer is answered out of the app's own Bible and the question
 *     asked again, at most [MAX_TOOL_ROUNDS] times (`:471`), and then once more with the asking
 *     stopped (`:490`).
 *  3. An answer that reached the ceiling without `[[END]]` is continued, at most [MAX_CONTINUATIONS]
 *     times (`:507`), with the tail of what was written so the model picks up where it stopped.
 *  4. The result says whether the answer is complete (`:554`), so the message can be marked
 *     「回覆已停止或尚未完整」.
 *
 * The provider is a parameter and not a field because `AiProvider` has no binding to inject: the
 * `ai_provider` setting is what chooses it, and whoever holds the setting is the one that can.
 *
 * Public rather than internal because `AiChatModule` names it in an `@Binds` signature, which a public
 * module cannot do with an internal type. The [scriptureToolRunner] it is built from stays internal,
 * because a private constructor property is not part of what the class exposes.
 */
@Singleton
class AskQuestionUseCase @Inject constructor(private val scriptureToolRunner: ScriptureToolRunner) : AskAiQuestion {

    /**
     * [question] asked of [provider], with the text as it arrives handed to [onProgress].
     *
     * [isStopped] is the reader's stop button, consulted between rounds and at the end of a round.
     * Cancelling the collection of this call is what actually ends the request; a flag alone lets
     * the answer that has already arrived be returned whole rather than abandoned, which is what
     * `_stopRequested` did for a subscription it could also cancel.
     */
    override suspend fun ask(
        provider: AiProvider,
        question: AskQuestion,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): ChatAnswer {
        val prompt = chatPrompt(
            question.question,
            question.scriptureContext,
            question.memory,
            question.recent,
            question.aiLanguage,
        )
        // `rawReasoning` of `_streamModelAnswer`, carried from one round into the next: the thinking of
        // an answer is the thinking of all of its segments, not of the last one.
        val reasoning = StringBuilder()
        val first = askFirstRound(provider, prompt, reasoning, onProgress, isStopped)
        // `if (streamed.stopped && answer.trim().isEmpty) return ''` — a stop before any text arrived
        // leaves no message at all, rather than an empty one.
        if (first.stopped && first.answer.isBlank()) return STOPPED_BEFORE_ANY_TEXT
        val tools = answerToolRequests(provider, prompt, reasoning, first, onProgress, isStopped)
        val forced = forceAnAnswer(provider, prompt, reasoning, tools, onProgress, isStopped)
        val continued = continueTheAnswer(provider, prompt, reasoning, forced, onProgress, isStopped)
        val round = continued.round
        return ChatAnswer(
            text = round.answer,
            reasoning = sanitizeReasoningForDisplay(reasoning.toString()),
            incomplete = (round.stopped || !round.complete || continued.failed) &&
                answerJsonOrNull(round.answer) == null,
            stopped = round.stopped,
        )
    }

    /**
     * The first round, and the retry without the web-search tool (`:439` and `:453`).
     *
     * The failed attempt is taken off the screen before the retry — `_removeTrailingProvisionalAnswer`
     * — so the answer the reader ends up with is the retry's alone. The empty [AnswerProgress] is
     * that removal, and it is the only time this loop publishes nothing: an ordinary round stays
     * silent while it has no text and no thinking to show.
     */
    private suspend fun askFirstRound(
        provider: AiProvider,
        prompt: String,
        reasoning: StringBuilder,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): Round = try {
        streamRound(provider, prompt, AiRequestOptions.ChatWithWebSearch, reasoning, "", onProgress, isStopped)
    } catch (cancelled: CancellationException) {
        // Cancelling the collection is what ends a request, so a cancelled answer is the caller's to
        // abandon and must not be asked again on their behalf.
        throw cancelled
    } catch (_: Exception) {
        onProgress(AnswerProgress(text = "", reasoning = ""))
        reasoning.clear()
        streamRound(provider, prompt, AiRequestOptions.ChatWithoutWebSearch, reasoning, "", onProgress, isStopped)
    }

    /**
     * The tool loop of `:471`–`489`: what the app answered the model, and the round it ended on.
     *
     * The result of every request is kept, because the follow-up prompt resends all of them rather
     * than only the newest — a model that asked for two passages is answered from both.
     */
    private suspend fun answerToolRequests(
        provider: AiProvider,
        prompt: String,
        reasoning: StringBuilder,
        streamed: Round,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): ToolRounds {
        var round = streamed
        val results = mutableListOf<String>()
        repeat(MAX_TOOL_ROUNDS) {
            val request = if (round.stopped) null else scriptureToolRequest(round.answer)
            if (request == null) return ToolRounds(round, results)
            results.add(scriptureToolRunner.run(request))
            // `_removeProvisional(visibleIndex); visibleIndex = null;` — the JSON request the model
            // wrote is not part of the answer, and neither is the thinking that went with it, so the
            // round that follows starts from nothing rather than from the protocol text.
            reasoning.clear()
            round = streamRound(
                provider,
                toolFollowUpPrompt(prompt, results),
                DEFAULT_ROUND_OPTIONS,
                reasoning,
                "",
                onProgress,
                isStopped,
            )
        }
        return ToolRounds(round, results)
    }

    /**
     * The forced last round of `:490`, asked only when the model is still asking for a passage after
     * [MAX_TOOL_ROUNDS] requests have been spent.
     */
    private suspend fun forceAnAnswer(
        provider: AiProvider,
        prompt: String,
        reasoning: StringBuilder,
        tools: ToolRounds,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): Round {
        if (scriptureToolRequest(tools.round.answer) == null) return tools.round
        reasoning.clear()
        return streamRound(
            provider,
            finalFollowUpPrompt(prompt, tools.results),
            DEFAULT_ROUND_OPTIONS,
            reasoning,
            "",
            onProgress,
            isStopped,
        )
    }

    /**
     * The continuation loop of `:507`–`539`, and whether one of the continuations failed.
     *
     * A continuation is asked only of an answer that is unfinished, unstopped, and not a tool request
     * — the three conditions Dart's `for` header spelled out. A continuation that fails is not a
     * failed answer: the text the model wrote is kept and the message is marked incomplete, which is
     * what `continuationFailed` did.
     */
    private suspend fun continueTheAnswer(
        provider: AiProvider,
        prompt: String,
        reasoning: StringBuilder,
        streamed: Round,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): Continuation {
        var round = streamed
        var failed = false
        repeat(MAX_CONTINUATIONS) {
            if (round.complete || round.stopped || answerJsonOrNull(round.answer) != null) {
                return Continuation(round, failed)
            }
            val next = try {
                streamRound(
                    provider,
                    continuationPrompt(prompt, round.answer),
                    DEFAULT_ROUND_OPTIONS,
                    reasoning,
                    round.answer,
                    onProgress,
                    isStopped,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            if (next == null) return Continuation(round, failed)
            round = next
        }
        return Continuation(round, failed)
    }

    /**
     * One `_streamModelAnswer` call (`:585`–`727`): the events of one round collected, the answer
     * built from them, and what the round says about finishing and stopping.
     *
     * [initialAnswer] is the answer the earlier rounds produced, and [reasoning] is the thinking of
     * all of them, because a continuation is joined onto the answer already written rather than
     * replacing it. A round that answered a tool request passes an empty [initialAnswer]: the JSON is
     * not part of the answer and must not be carried into the next one.
     */
    private suspend fun streamRound(
        provider: AiProvider,
        prompt: String,
        options: AiRequestOptions,
        reasoning: StringBuilder,
        initialAnswer: String,
        onProgress: (AnswerProgress) -> Unit,
        isStopped: () -> Boolean,
    ): Round {
        val segment = StringBuilder()
        var finishReason: FinishReason? = null
        var failure: String? = null
        var publishedAt: TimeSource.Monotonic.ValueMark? = null

        // `_appendWithoutDuplicate(initialAnswer, _cleanModelOutput(rawSegment), additionComplete: …)`,
        // which both the provisional message and the round's return value are built from.
        fun answerSoFar(): String = appendWithoutDuplicate(
            initialAnswer,
            cleanModelOutput(segment.toString()),
            additionComplete = segment.contains(END_MARKER),
        )

        fun publish() {
            // A leading JSON object is an app-side tool request, and protocol text must never reach
            // the screen. It is withheld only while the answer is *just* the request, which is why the
            // guard reads the first round's empty `initialAnswer`.
            if (initialAnswer.isEmpty() && segment.toString().trimStart().startsWith(OPEN_BRACE)) return
            val answer = answerSoFar()
            val thinking = sanitizeReasoningForDisplay(reasoning.toString())
            if (answer.isEmpty() && thinking.isEmpty()) return
            onProgress(AnswerProgress(text = answer, reasoning = thinking))
        }

        // `updateProvisional`'s 50ms throttle. Publishing re-cleans the whole answer, so doing it per
        // delta would spend a quadratic amount of work on a long answer to redraw it faster than a
        // reader can read it.
        fun readyToPublish(): Boolean {
            val now = TimeSource.Monotonic.markNow()
            val last = publishedAt
            if (last != null && now - last < PROGRESS_INTERVAL) return false
            publishedAt = now
            return true
        }

        // A delta the reader has already stopped for is not part of the answer, and once stopped the
        // round is not asked to carry on — which is `if (_stopRequested) return;` on the stream's
        // listener.
        fun append(raw: StringBuilder, delta: String) {
            if (isStopped()) return
            raw.append(delta)
            if (readyToPublish()) publish()
        }

        try {
            provider.stream(AiRequest.Chat(prompt), options).collect { event ->
                when (event) {
                    is ChatEvent.ContentDelta -> append(segment, event.text)
                    is ChatEvent.ReasoningDelta -> append(reasoning, event.text)
                    is ChatEvent.Finished -> finishReason = event.reason
                    is ChatEvent.Failure -> failure = event.message
                    is ChatEvent.WebCitation -> Unit
                }
            }
        } finally {
            // The last publish of the round, however the round ended: the text so far is what the
            // reader keeps, which is the promise behind 「內容已保留」.
            if (segment.isNotEmpty() || reasoning.isNotEmpty()) publish()
        }
        val stopped = isStopped()
        if (segment.isBlank()) {
            if (stopped) return Round(answer = initialAnswer.trim(), complete = false, stopped = true)
            failure?.let { throw AiAnswerException(it) }
            throw IllegalStateException(NO_RESPONSE_MESSAGE)
        }
        val answer = answerSoFar()
        return Round(
            answer = answer.trim(),
            complete = segment.contains(END_MARKER) || finishReason == FinishReason.STOP,
            stopped = stopped,
        )
    }

    private companion object {
        /** `const OpenRouterRequestOptions()` — what every round after the first is sent. */
        val DEFAULT_ROUND_OPTIONS = AiRequestOptions()

        /** `StateError('AI model returned no response.')`, a round that carried no text at all. */
        const val NO_RESPONSE_MESSAGE = "AI model returned no response."

        /** `'[[END]]'`, the marker that says the answer is finished. Read by the loop, not only cut. */
        const val END_MARKER = "[[END]]"

        /** `'{'`: a segment that opens with one is a tool request until it stops looking like one. */
        const val OPEN_BRACE = "{"

        /** The 50ms of `updateProvisional`'s timer, which is what the Flutter chat throttled at. */
        val PROGRESS_INTERVAL = 50.milliseconds

        /**
         * What a stop before any text arrived produces, and what `return ''` produced: no message, no
         * thinking, and nothing for the caller to keep. [ChatAnswer.incomplete] is stated rather than
         * left false because nothing was completed.
         */
        val STOPPED_BEFORE_ANY_TEXT = ChatAnswer(
            text = "",
            reasoning = "",
            incomplete = true,
            stopped = true,
        )
    }
}

/**
 * One question, the five things `_chatPrompt` is given.
 *
 * [scriptureContext] is the chapter the reader is on, in the reader's own `chapterContextText` shape
 * — `book.chapter:verse`, then `中文：`, then `English: ` — so a passage that arrived with the
 * question and one that arrived through the pseudo-tool read the same to the model. It is null when
 * the chat was opened without a chapter, which is what the prompt's `(none supplied)` says.
 *
 * [aiLanguage] is `AppLocale.aiLanguage` (`natural Traditional Chinese` / `natural English`), stated
 * rather than defaulted: an answer in the wrong language is not a thing to discover afterwards.
 */
data class AskQuestion(
    val question: String,
    val aiLanguage: String,
    val scriptureContext: String? = null,
    val memory: String = "",
    val recent: String = "",
)

/**
 * What the reader is looking at while a round streams: the `AiMessage` `publishProvisional` replaced
 * in the message list and the UI redrew.
 *
 * Published at most every 50ms and never with both halves empty, so a caller can tell a still-empty
 * answer from the one exception — a first attempt that failed and is about to be asked again.
 */
data class AnswerProgress(val text: String, val reasoning: String)

/**
 * The answer a question produced, and the two flags the `AiMessage` it becomes carries.
 *
 * [incomplete] is what the chat shows as 「回覆已停止或尚未完整」, and it is stated rather than left to a
 * reader of the flags to work out: an answer that was cut, stopped or left half-continued is
 * incomplete, and one that came back as a tool request is not, whatever the provider said.
 */
data class ChatAnswer(val text: String, val reasoning: String, val incomplete: Boolean, val stopped: Boolean)

/** What one `_streamModelAnswer` call produced, the record Dart returned. */
private data class Round(val answer: String, val complete: Boolean, val stopped: Boolean)

/** The round the tool loop ended on, and every result the app gave the model along the way. */
private data class ToolRounds(val round: Round, val results: List<String>)

/** The round the continuation loop ended on, and whether a continuation of it failed. */
private data class Continuation(val round: Round, val failed: Boolean)

/**
 * A round that carried a [ChatEvent.Failure] and no text at all, thrown with the provider's own
 * message because there is nothing to show and nothing to continue.
 *
 * A failure *after* text has arrived is not this: the round ends on the text that came, which is the
 * answer the reader keeps, and the failure is the caller's to report.
 */
class AiAnswerException(message: String) : Exception(message)
