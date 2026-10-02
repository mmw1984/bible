package com.marcow.bible.feature.aichat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.feature.aichat.domain.AiMessage

/**
 * The chat as the host draws it, replacing the `_AiChatPageState` that owned the page's widget in
 * `legacy/flutter/lib/ai_chat_page.dart`.
 *
 * This is the only place in the feature that knows about [AiChatViewModel], the clipboard or the browser.
 * [AiChatScreen] takes a state and a list of callbacks so that it can be drawn from a fixed
 * [AiChatState] without any of them — which is what lets a preview or a golden draw it.
 *
 * Four things Flutter did on the page and this does too, none of them visible in the screen:
 *
 *  - **The chat initializes on the way in and re-reads its sign-in on every way back.**
 *    `controller.initialize()` in `initState`, and `controller.refreshOpenRouterLogin()` in
 *    `didChangeAppLifecycleState` for `resumed`. They are different calls on purpose: the transcript
 *    restore happens once, while coming back from the Custom Tab is exactly when an authorization code is
 *    parked waiting for its exchange, so that leg is [AiChatViewModel.refreshSignIn] rather than
 *    [AiChatViewModel.initialize] — the latter is guarded and would do nothing.
 *  - **A question typed while signed out is not lost.** [AiChatViewModel.send] holds it, the sign-in
 *    panel's button opens the Custom Tab, and the question is asked from the `signedIn` observer the
 *    moment there is a provider. Flutter held the question on the page as `pendingQuestion`; the view
 *    model holds it now, because the panel that shows it is the view model's own state.
 *  - **The question is cleared when it is sent, and put back when the answer fails.** `_send` cleared the
 *    controller and, in its `catch`, restored the text and the caret to the end. The failure sentence
 *    comes from [AiChatViewModel]'s `generationError`, which `generationErrorMessage` has already
 *    resolved, so the screen never sees a throwable.
 *  - **A handoff from the reader is applied whenever it is delivered.** `openFromReader` is called from a
 *    [LaunchedEffect] keyed on the handoff rather than once, because Dart's payload could arrive after the
 *    page was built — `getLaunchPayload` and `deliverLaunchPayload` both answered, and a reader tapping a
 *    second verse while the chat was still restoring is the same case. [AiChatViewModel.openFromReader]
 *    ignores a re-delivery of a handoff it already holds, so re-running it is safe.
 *
 * [autofocus] is `!launchAutoSend`: a question written for the reader is asked for them and the composer
 * must not steal the keyboard from the answer, while 「問 AI」 opens an empty box that wants one.
 *
 * [bottomClearance] is the host's navigation bar, which the dock is lifted by when no keyboard is up.
 */
@Composable
fun AiChatRoute(
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    handoff: ScriptureHandoff? = null,
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    // The composer's text is the screen's own, as `TextEditingController` was the page's: the ViewModel is
    // told about a question only once it is sent, so a question waiting on a sign-in survives the frame
    // that opened the Custom Tab.
    var question by remember { mutableStateOf(handoff?.question.orEmpty()) }

    LaunchedEffect(Unit) { viewModel.initialize() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSignIn() }
    LaunchedEffect(handoff) { handoff?.let(viewModel::openFromReader) }
    // An embedded page is somebody else's destination and has no back button of its own; a full page has
    // one, and takes the press for it. `_close` was `Navigator.maybePop(context) ?? SystemNavigator.pop()`,
    // so with no callback the press goes to the back dispatcher rather than nowhere.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val leave: () -> Unit = onBack ?: { dispatcher?.onBackPressed() ?: Unit }
    BackHandler(enabled = !embedded, onBack = leave)

    AiChatScreen(
        state = state,
        question = question,
        onQuestionChange = { question = it },
        embedded = embedded,
        bottomClearance = bottomClearance,
        onBack = leave,
        onOpenSettings = onOpenSettings,
        onSend = {
            viewModel.send(question)
            // `_send` parked the question on `pendingQuestion` and returned before `input.clear()` when
            // there was no provider to answer it, so a reader sent to the sign-in panel finds their
            // question still in the box. The view model holds it meanwhile and asks it once there is a
            // key, which is the leg that page did not have to think about twice.
            if (!state.requiresLogin) question = ""
        },
        onStop = viewModel::stop,
        onRegenerate = viewModel::regenerate,
        onRemoveAttachment = viewModel::detachScripture,
        onConnect = viewModel::beginSignIn,
        onRetryInitialize = viewModel::initialize,
        // `Clipboard.setData(ClipboardData(text: message.text))` — the answer as it is drawn, reasoning
        // and all, which is what a reader copying a citation wants and what the Dart copied.
        onCopy = { message: AiMessage -> clipboard.setText(AnnotatedString(message.text)) },
        onOpenLink = uriHandler::openUri,
        autofocus = handoff?.autoSend != true,
        modifier = modifier,
    )
}