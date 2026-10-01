package com.marcow.bible.feature.aichat

import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.domain.AiMessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the chat screen draws, mirroring the fields of `BibleAiController` plus
 * `_AiChatPageState` in `legacy/flutter/lib/ai_service.dart` and `legacy/flutter/lib/ai_chat_page.dart`.
 *
 * One value rather than the dozen fields Flutter held across a `ChangeNotifier` and a `State`, for
 * the same reason `SearchSheetState` is one value: an answer is written into the list in two places
 * while it streams — the provisional message and then the finished one — and a reader that could see
 * the list between the two would show a message that no longer exists.
 *
 * The two flags that are *not* folded into [generating] are [initializationError] and
 * [generationError], because both are sentences the screen shows rather than booleans it branches
 * on — and [initializationError] in particular is the one case where the chat is not answering
 * because it could not read the conversation back.
 */
data class AiChatState(
    /** `List<AiMessage> _messages`, the whole conversation, oldest first. */
    val messages: List<AiMessage> = emptyList(),
    /** `bool generating`: a question is in flight. */
    val generating: Boolean = false,
    /** `bool openRouterSignedIn`, for the sign-in panel and the toolbar. */
    val signedIn: Boolean = false,
    /** `String? initializationError`, which is `initialized == false` with a reason. */
    val initializationError: String? = null,
    /** `bool initialized`: the transcript has been read back at least once. */
    val initialized: Boolean = false,
    /**
     * `String? generationError`, the panel under a failed answer: already the whole sentence the
     * reader is shown, and null when nothing has failed.
     *
     * `generationErrorMessage` resolves the choice between the failure's own words and the generic
     * sentence, so a screen never sees a raw throwable, a half-built string, or a blank standing in
     * for "no error yet" — the null is the only absence.
     */
    val generationError: String? = null,
    /** `String? authError`, `openRouterAuthError`: the last sign-in failure. */
    val authError: String? = null,
    /** `AppLocale responseLocale`, which is the language the prompt asks the answer in. */
    val responseLocale: AppLocale = AppLocale.ZH_HANT,
    /** `String? attachedScriptureContext`, the chapter the chat was opened on. */
    val attachedScriptureContext: String? = null,
    /** `String? attachedScriptureReference`, the passage shown as the attachment chip. */
    val attachedScriptureReference: String? = null,
) {
    /**
     * `bool get requiresLogin`: the chat cannot answer without a provider, and OpenRouter needs a key.
     *
     * Flutter's getter was `_overrideModel == null && !openRouterSignedIn`, where the override was
     * the on-device model. There is no override here, so the sign-in alone decides — which is what
     * `NATIVE_PLAN.md` §4.1 means by making OpenRouter the default provider.
     */
    val requiresLogin: Boolean
        get() = !signedIn

    /**
     * `bool get isReady`: what `_send` checked before it would add a question.
     *
     * Both halves, and the order matters: an uninitialized chat has no transcript, so answering
     * before the restore lands would write an answer the restore then replaces.
     */
    val isReady: Boolean
        get() = initialized && signedIn

    /**
     * `bool get contextAttached`: whether the reader's chapter is still attached.
     *
     * The chip is removable and the context goes with it, so this is not "was a chapter supplied" —
     * it is "is one attached right now", which is what decides whether a *later* question in the same
     * chat is asked with the chapter context.
     */
    val contextAttached: Boolean
        get() = !attachedScriptureContext.isNullOrBlank()

    /**
     * The `AiMessage` the regenerate action applies to, which is the last thing the reader asked.
     *
     * `lastIndexWhere(role == 'user')` in Dart, and the reason it is not simply the last message: a
     * failed answer leaves the list ending on the reader's turn, while a completed one ends on the
     * model's. Regenerate has to find the question either way, and it has to keep the turns *after*
     * that question rather than dropping them, because a reader who edited an earlier question meant
     * to redo that exchange and nothing after it.
     */
    val lastQuestion: AiMessage?
        get() = messages.lastOrNull { it.role == AiMessageRole.USER }
}

/** Flutter's `_messages` mutation, as the three cases the controller actually performs. */
internal fun List<AiMessage>.withProvisional(index: Int?, message: AiMessage): List<AiMessage> =
    if (index == null || index !in indices) this + message else toMutableList().apply { this[index] = message }

/** Mutable state holder the view model writes, kept beside the state it publishes. */
internal class AiChatStateHolder(initial: AiChatState = AiChatState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AiChatState> = _state.asStateFlow()
    val current: AiChatState
        get() = _state.value

    fun update(transform: (AiChatState) -> AiChatState) {
        _state.value = transform(_state.value)
    }
}
