package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.network.ai.AiProvider

/**
 * The port the chat asks a question through, the counterpart of `AiSearch` in `feature/search`.
 *
 * The view model takes this rather than [AskQuestionUseCase] for the reason `SearchViewModel` takes
 * `AiSearch`: the state machine — a fragment replacing a provisional message, a stop landing between
 * two rounds, a failure taking the answer off the screen — is what the chat *is*, and none of it can
 * be provoked reliably against a live model. Against a stub, a stream can be made to stop halfway.
 *
 * [provider] stays a parameter rather than becoming a field, which is the same decision
 * `AskQuestionUseCase` documents: `AiProvider` has no binding to inject because the `ai_provider`
 * setting is what chooses it, and whoever holds the setting is the one that can.
 */
interface AskAiQuestion {
    /**
     * [question] asked, with the text as it arrives handed to [onProgress].
     *
     * Cancelling the calling coroutine is what ends the request; [isStopped] is the softer version,
     * consulted between rounds so a stop stops the *asking* and not only the listening.
     */
    suspend fun ask(
        provider: AiProvider,
        question: AskQuestion,
        onProgress: (AnswerProgress) -> Unit = {},
        isStopped: () -> Boolean = { false },
    ): ChatAnswer
}
