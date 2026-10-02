package com.marcow.bible.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.feature.aichat.domain.AiChatSignIn
import com.marcow.bible.feature.aichat.domain.AiMemoryStore
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.domain.AiMessageKind
import com.marcow.bible.feature.aichat.domain.AiMessageRole
import com.marcow.bible.feature.aichat.domain.AnswerProgress
import com.marcow.bible.feature.aichat.domain.AskAiQuestion
import com.marcow.bible.feature.aichat.domain.AskQuestion
import com.marcow.bible.feature.aichat.domain.ChatAnswer
import com.marcow.bible.feature.aichat.domain.generationErrorMessage
import com.marcow.bible.feature.aichat.domain.memoryBlock
import com.marcow.bible.feature.aichat.domain.recentConversationBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The chat, replacing `BibleAiController` in `legacy/flutter/lib/ai_service.dart:296` for the five
 * things a reader does in it: restore, send, stop, regenerate and clear.
 *
 * What is *not* here is the answering loop. [AskQuestionUseCase] already does the rounds, the tool
 * requests and the continuations, and it publishes progress as a callback rather than as state, so
 * this class is the conversation the reader is looking at and the use case is the answer being written
 * into it. That split is what makes a stop honest: cancelling [generation] ends the collection the use
 * case is inside, and the text that already arrived is what stays on screen.
 *
 * The order of a turn is Flutter's and it matters:
 *
 *  1. The question is appended and recorded *before* the answer is asked (`:390`–`:399`), so a turn
 *     survives the app being killed mid-answer.
 *  2. Each published fragment replaces one provisional message rather than appending (`:617`–`:632`).
 *  3. The finished message replaces that same provisional one, so the list never grows a second copy
 *     (`:549`–`:560`).
 *  4. A failure removes the provisional message and leaves the reader's question alone (`:573`).
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val askQuestion: AskAiQuestion,
    private val memoryStore: AiMemoryStore,
    private val settingsRepository: SettingsRepository,
    private val signIn: AiChatSignIn,
    private val provider: AiProvider,
) : ViewModel() {
    private val holder = AiChatStateHolder()
    val state: StateFlow<AiChatState> = holder.state

    /**
     * The answer in flight, so stop can cancel it and a second question can be refused.
     *
     * Flutter held a `StreamSubscription` and a `Completer` in two nullable fields; one [Job] is both,
     * and cancelling it is what releases the connection — the provider's own contract says a cancelled
     * collection delivers no `ChatEvent.Finished`.
     */
    private var generation: Job? = null

    /**
     * `bool stopRequested`: the reader pressed stop, as opposed to the request having ended.
     *
     * The two are not the same and the loop needs both. Cancelling the job throws out of the stream,
     * while this flag is what [AskQuestionUseCase] reads between rounds, so it stops *asking* rather
     * than only stopping *listening* — and it is what makes the answer come back marked incomplete
     * rather than finished.
     */
    @Volatile
    private var stopRequested = false

    /** `_performInitialization`, and the job it runs in, so a second caller can wait for it. */
    private var initializing: Job? = null

    /**
     * The reader's handoff, held until the chat is ready to act on it.
     *
     * Kept as a field rather than read once in the constructor because Flutter's payload could
     * arrive after the page was built — `deliverLaunchPayload` and `getLaunchPayload` both called
     * `_applyLaunchPayload`, which reset `sentInitial` and tried again — and a reader tapping a
     * second verse while the chat is still restoring is the same case.
     */
    private var handoff: ScriptureHandoff? = null

    /**
     * `bool sentInitial`: the handoff's question has been asked, or is deliberately not going to be.
     *
     * Set before the question is handed to [send] rather than after, because [send] can decline it —
     * an answer already in flight, a question that trims to nothing — and a declined launch would
     * otherwise be retried on every state change and fire the moment the chat went quiet.
     */
    private var sentInitial = false

    /**
     * `pendingQuestion` / `pendingKind`: a question asked before there was a provider to answer it.
     *
     * Flutter held both on the page and sent them from `_controllerChanged` the moment
     * `openRouterSignedIn` went true, having opened the sign-in sheet in between. Dropping the
     * question instead would lose 「解釋經文」 outright for any reader who opened it while signed
     * out, which is precisely the reader the feature is for.
     */
    private var pendingQuestion: Pair<String, AiMessageKind>? = null

    init {
        viewModelScope.launch {
            signIn.signedIn.collect { signedIn ->
                holder.update { it.copy(signedIn = signedIn) }
                // `_controllerChanged` re-ran both of these on every notification, and signing in is
                // the one that unblocks a question asked before a provider was there.
                flushPendingQuestion()
                maybeSendInitial()
            }
        }
        viewModelScope.launch {
            signIn.lastError.collect { error -> holder.update { it.copy(authError = error) } }
        }
        viewModelScope.launch {
            // `String modelId`, read once per launch and written through `setModel`. It is the settings
            // field's value, so it is a flow rather than something read at construction: the field is
            // drawn after the restore lands, and a saved model read too early would be seeded over
            // with the free router.
            signIn.modelId.collect { model -> holder.update { it.copy(modelId = model) } }
        }
        viewModelScope.launch {
            // `setResponseLocale`: the language is a prompt input rather than the chat's own, so it
            // follows the app's locale and a reader who changes it re-asks in the new language.
            settingsRepository.settings.collect { settings ->
                holder.update { it.copy(responseLocale = settings.locale) }
            }
        }
    }

    /**
     * `initialize()`: read the transcript back, then report ready.
     *
     * The restore has to land before anything can send, which is what [AiChatState.initialized] is
     * for — Dart did it in `_performInitialization` and every entry point checked it first, because an
     * answer written before the restore arrives is one the restore then overwrites.
     */
    @Suppress("TooGenericExceptionCaught")
    fun initialize() {
        if (holder.current.initialized || initializing != null) return
        initializing = viewModelScope.launch {
            holder.update { it.copy(initializationError = null) }
            try {
                signIn.initialize()
                val restored = memoryStore.transcript()
                holder.update { it.copy(messages = restored, initialized = true, initializationError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                holder.update { it.copy(initialized = false, initializationError = error.describe()) }
            } finally {
                initializing = null
                // The launch question is asked against a restored transcript, so this is the first
                // moment it can be asked at all.
                maybeSendInitial()
            }
        }
    }

    /**
     * `send(...)`: the reader's turn, then the model's.
     *
     * [kind] is [AiMessageKind.EXPLANATION] for a passage sent from the reader and
     * [AiMessageKind.CHAT] for anything typed in the chat, and it is not a formality: it decides which
     * section of the memory document the turn is filed under, so a verse explanation is still in the
     * memory when the reader comes back to it days later.
     *
     * The passage comes from the state's attachment rather than a parameter, for the reason Flutter
     * held it on the page: the reader supplied it once, and every question typed after that is asked
     * with it until the chip is removed.
     */
    fun send(question: String, kind: AiMessageKind = AiMessageKind.CHAT) {
        val text = question.trim()
        if (text.isEmpty() || holder.current.generating) return
        if (!holder.current.initialized) {
            // `_send` awaited initialization and reported its failure itself. A question asked before
            // the restore lands cannot be answered without losing the restore.
            initialize()
            viewModelScope.launch {
                if (awaitInitialized()) send(text, kind)
            }
            return
        }
        if (!holder.current.signedIn) {
            // `_send` put the question on `pendingQuestion` and opened the sign-in sheet rather than
            // dropping it, so it is asked as soon as there is a provider to ask.
            pendingQuestion = text to kind
            return
        }
        pendingQuestion = null
        val question0 = AiMessage(
            role = AiMessageRole.USER,
            text = text,
            scripture = holder.current.attachedScriptureReference,
            kind = kind,
            // `webSearch: true` on the reader's own turn, unconditionally: the flag records that the
            // turn was asked with the search tool, and a chat question is always offered it.
            webSearch = true,
        )
        holder.update { it.copy(messages = it.messages + question0, generationError = null) }
        answer(text, kind, question0.scripture, record = question0)
    }

    /**
     * `regenerateLast()`: ask the last question again, dropping everything the reader has read since.
     *
     * The turns *after* that question go, and only those. A reader who pressed regenerate meant to redo
     * that exchange, and keeping the answers that followed would leave a conversation whose answers do
     * not follow the questions above them. The question itself stays — it is the thing being re-asked.
     */
    fun regenerate() {
        if (holder.current.generating) return
        val state = holder.current
        val question = state.lastQuestion ?: return
        val kept = state.messages.take(state.messages.indexOfLast { it.role == AiMessageRole.USER } + 1)
        holder.update { it.copy(messages = kept, generationError = null) }
        answer(question.text, question.kind, question.scripture, record = null, transcript = kept)
    }

    /**
     * `stopGeneration()`: the reader pressed stop.
     *
     * The flag is set before the cancel so the rounds the use case is between see it, and the job is
     * cancelled after. A stop that only stopped listening would let the loop ask its next round against
     * a question the reader has already walked away from.
     *
     * The answer's text is not touched here: the partial answer was written into the list by the last
     * publish and 「內容已保留」 is what the chat promises when an answer does not finish.
     */
    fun stop() {
        if (!holder.current.generating) return
        stopRequested = true
        generation?.cancel()
        generation = null
        holder.update { it.copy(generating = false) }
    }

    /** `clearConversation()`: the reader asked for the history to go. */
    fun clear() {
        stopRequested = true
        generation?.cancel()
        generation = null
        holder.update { it.copy(messages = emptyList(), generating = false, generationError = null) }
        viewModelScope.launch { memoryStore.clear() }
    }

    /**
     * `refreshOpenRouterLogin()`: what `didChangeAppLifecycleState` called on every resume.
     *
     * This is the leg that makes the Custom Tab round trip work at all, and it is separate from
     * [initialize] because [initialize] is guarded by [AiChatState.initialized] — the transcript restore
     * must happen once, while the sign-in has to be re-read every time the chat comes back to the
     * foreground, because coming back from the browser is exactly when a code is parked and waiting for
     * its exchange (`NATIVE_PLAN.md` §4.7).
     *
     * `OpenRouterAuthManager.initialize` is safe to run again: it re-reads the key, re-reads the model and
     * retries the parked exchange, all of which are no-ops once the exchange has been done. A failure is
     * left in [AiChatState.authError] by the manager rather than thrown, so a network failure on resume
     * shows in the sign-in panel and does not take the chat down with it.
     *
     * Nothing here has to re-ask a held question: the [signIn.signedIn] observer in `init` already does
     * that, which is `_controllerChanged`'s half of the same behaviour.
     */
    fun refreshSignIn() {
        viewModelScope.launch { signIn.initialize() }
    }

    /** `beginOpenRouterLogin()`: the sign-in panel's button. */
    @Suppress("TooGenericExceptionCaught")
    fun beginSignIn() {
        viewModelScope.launch {
            try {
                signIn.beginSignIn()
            } catch (error: Exception) {
                holder.update { it.copy(authError = error.describe()) }
            }
        }
    }

    /**
     * `signOutOpenRouter()`: the same button, the other way round.
     *
     * Nothing is held across it — the conversation stays and the saved model stays — because Flutter
     * dropped the key and nothing else, and a reader who signs back in should find the conversation
     * they left. [AiChatState.signedIn] follows on its own: it is watched rather than set here, so the
     * button cannot claim a sign-out that the store has not taken yet.
     */
    @Suppress("TooGenericExceptionCaught")
    fun signOut() {
        viewModelScope.launch {
            try {
                signIn.signOut()
            } catch (error: Exception) {
                holder.update { it.copy(authError = error.describe()) }
            }
        }
    }

    /**
     * `setModel(value)`: the settings field's save button and its keyboard action.
     *
     * Reported as [AiChatState.authError] on a failure rather than silently dropped, because a model
     * that was not saved is one the next request will not use, and the field would still be showing it.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setModel(model: String) {
        viewModelScope.launch {
            try {
                signIn.setModel(model)
            } catch (error: Exception) {
                holder.update { it.copy(authError = error.describe()) }
            }
        }
    }

    /** The reader removed the attached passage, so later questions are asked without it. */
    fun detachScripture() {
        holder.update {
            it.copy(attachedScriptureContext = null, attachedScriptureReference = null, attachedScriptureText = null)
        }
    }

    /**
     * `_applyLaunchPayload` and `_maybeSendInitial`: a verse action opened the chat with a passage,
     * and maybe a question already written.
     *
     * Applying and asking are separate because the chat is not ready when the reader taps: the
     * transcript has not been read back and there may be no provider yet. So the payload is recorded
     * and the question is asked by [maybeSendInitial] from whichever of those arrives first.
     *
     * A handoff with no question — 「問 AI」 — attaches the passage and stops there, which is what
     * left the reader an empty composer to type into.
     */
    fun openFromReader(handoff: ScriptureHandoff) {
        // A re-delivery of the same payload is a re-render, not a second question. Flutter re-asked
        // on every `_applyLaunchPayload` because the page rebuilt instead, which posted the question
        // twice whenever `getLaunchPayload` and `deliverLaunchPayload` both answered; only a
        // genuinely new handoff re-arms the auto-send here.
        if (this.handoff == handoff) {
            maybeSendInitial()
            return
        }
        this.handoff = handoff
        sentInitial = false
        val attached = handoff.contextAttached
        holder.update {
            it.copy(
                // Flutter passed `contextAttached ? launchScripture… : null` to all three — the chip,
                // `send`'s `scriptureContext` and `send`'s `scriptureReference` — so a handoff
                // carrying a blank chapter shows no chip and asks with no chapter at all.
                attachedScriptureContext = handoff.context.takeIf { attached },
                attachedScriptureReference = handoff.reference.takeIf { attached },
                attachedScriptureText = handoff.attachment.takeIf { attached },
            )
        }
        // `AiChatPage.initState` initialized the controller however the page was opened, and the
        // question cannot be asked until it has. `initialize()` returns early if it is already run or
        // running, so this is not a second restore.
        initialize()
        maybeSendInitial()
    }

    /**
     * `_maybeSendInitial`: ask the handoff's question, once, as soon as it can be asked.
     *
     * Every guard is one Flutter had. The chat must be restored, because a question asked against an
     * empty transcript is one the restore then overwrites; nothing may be generating, because the
     * answer would queue behind a turn the reader can see; and there must be something to ask, since
     * an empty question is not a question. It is not attempted at all without [ScriptureHandoff.autoSend].
     *
     * The kind is [AiMessageKind.EXPLANATION] even though the reader never chose it, because
     * `_send(supplied: question, kind: 'explanation')` filed every launch question as an explanation
     * — that is what puts it under 「解釋經文」 in the memory document days later.
     */
    private fun maybeSendInitial() {
        val launch = handoff ?: return
        if (sentInitial || !launch.autoSend) return
        if (holder.current.generating || !holder.current.isReady) return
        val question = launch.trimmedQuestion ?: return
        sentInitial = true
        send(question, AiMessageKind.EXPLANATION)
    }

    /**
     * `pendingQuestion`, asked as soon as there is a provider.
     *
     * Guarded rather than unconditional so the held question cannot start an answer on top of one
     * already in flight, and so a restore that has not landed still gets it sent afterwards — which
     * is the same ordering [send] itself applies.
     */
    private fun flushPendingQuestion() {
        val held = pendingQuestion ?: return
        if (holder.current.generating || !holder.current.initialized || !holder.current.signedIn) return
        pendingQuestion = null
        send(held.first, held.second)
    }

    /**
     * `_answerExistingMessage`: the prompt is built, the question is asked, and every fragment is written
     * over one provisional message until the finished answer replaces it.
     *
     * [provisional] is the index of the message being written into, and it is null until the first
     * fragment that has something to show. The use case publishes an empty [AnswerProgress] exactly
     * once — when the first attempt failed and the question is about to be asked again — and that is
     * the removal of the failed attempt rather than a message with no text, which is why an empty
     * publication takes the provisional *away* instead of writing it.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun answer(
        question: String,
        kind: AiMessageKind,
        scripture: String?,
        record: AiMessage?,
        transcript: List<AiMessage>? = null,
    ) {
        stopRequested = false
        holder.update { it.copy(generationError = null, generating = true) }
        // Launched lazily and started by hand so the body cannot finish before `generation` is set:
        // a stub that returns in the same tick would otherwise leave a completed job in the field that
        // the next stop cancels instead of the one that is actually running. The `generation === job`
        // checks in the body are the other half — a `clear()` mid-answer lets a new question start
        // while the old job is still unwinding, and the old job must not take the new one down with it.
        var job: Job? = null
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var provisional: Int? = null
            fun publish(progress: AnswerProgress) {
                if (progress.text.isEmpty() && progress.reasoning.isEmpty()) {
                    val removed = provisional ?: return
                    holder.update { it.copy(messages = it.messages.withoutAt(removed)) }
                    provisional = null
                    return
                }
                val index = provisional
                val message = answerMessage(progress, kind, scripture)
                holder.update { state2 -> state2.copy(messages = state2.messages.withProvisional(index, message)) }
                if (index == null) provisional = holder.current.messages.lastIndex
            }

            try {
                // `send` awaited `recordMessage` before `_answerExistingMessage` built the prompt, and
                // the order is load-bearing rather than tidy: the prompt reads the memory document, so
                // a turn written after that read is a turn the model is not told about. [record] is
                // null for a regenerate, which replaced the transcript rather than adding to it.
                if (record != null) memoryStore.recordMessage(record)
                if (transcript != null) memoryStore.replaceTranscript(transcript)
                val state = holder.current
                val ask = AskQuestion(
                    question = question,
                    aiLanguage = state.responseLocale.aiLanguage,
                    scriptureContext = state.attachedScriptureContext,
                    memory = memoryBlock(
                        memoryStore.promptMemory(AiMemoryStore.DEFAULT_MEMORY_CHARACTERS),
                        AiMemoryStore.DEFAULT_MEMORY_CHARACTERS,
                    ),
                    recent = recentConversationBlock(state.messages, question),
                )
                val answer = askQuestion.ask(
                    provider = provider,
                    question = ask,
                    onProgress = ::publish,
                    isStopped = { stopRequested },
                )
                // A stop before any text arrived produces no message at all, rather than an empty one:
                // `if (streamed.stopped && answer.trim().isEmpty) return ''`.
                if (answer.text.isEmpty() && answer.stopped) return@launch
                val index = provisional
                holder.update { state2 ->
                    state2.copy(
                        messages = state2.messages.withProvisional(
                            index,
                            answerMessage(
                                AnswerProgress(text = answer.text, reasoning = answer.reasoning),
                                kind,
                                scripture,
                                incomplete = answer.incomplete,
                            ),
                        ),
                    )
                }
                memoryStore.recordMessage(answerMessage(answer, kind, scripture))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // `_removeProvisional(visibleIndex)`: a failed answer goes, the reader's question stays.
                val failed = provisional
                if (failed != null) {
                    holder.update { it.copy(messages = it.messages.withoutAt(failed)) }
                }
                holder.update { it.copy(generationError = generationErrorMessage(error)) }
            } finally {
                if (generation === job) {
                    stopRequested = false
                    holder.update { it.copy(generating = false) }
                    generation = null
                    // A handoff that arrived mid-answer was declined rather than queued, and the
                    // moment the chat goes quiet is the moment it can be asked.
                    maybeSendInitial()
                }
            }
        }
        generation = job
        job.start()
    }

    /** Waits for the restore to land, reporting whether it arrived. */
    private suspend fun awaitInitialized(): Boolean {
        if (holder.current.initialized) return true
        initialize()
        initializing?.join()
        return holder.current.initialized
    }
}

/**
 * `AiMessage(role: 'assistant', …)`, the three places the chat writes one.
 *
 * A function rather than a constructor call in each because the three differ only in
 * [ChatAnswer.incomplete] and getting that wrong on the provisional — showing 「回覆已停止或尚未完整」
 * on an answer that is still arriving — is the sort of difference that is invisible in a diff.
 */
private fun answerMessage(
    progress: AnswerProgress,
    kind: AiMessageKind,
    scripture: String?,
    incomplete: Boolean = false,
): AiMessage = AiMessage(
    role = AiMessageRole.ASSISTANT,
    text = progress.text,
    scripture = scripture,
    kind = kind,
    reasoning = progress.reasoning.takeIf { it.isNotEmpty() },
    webSearch = true,
    incomplete = incomplete,
)

/** The finished answer, which is the same record with the flag the answer came back with. */
private fun answerMessage(answer: ChatAnswer, kind: AiMessageKind, scripture: String?): AiMessage =
    answerMessage(AnswerProgress(text = answer.text, reasoning = answer.reasoning), kind, scripture, answer.incomplete)

/** `_messages.removeAt(index)`, guarded the way `_removeProvisional` guarded it. */
private fun List<AiMessage>.withoutAt(index: Int): List<AiMessage> =
    if (index in indices) filterIndexed { position, _ -> position != index } else this

/** `error.toString()` for the two panels that show one. */
private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()
