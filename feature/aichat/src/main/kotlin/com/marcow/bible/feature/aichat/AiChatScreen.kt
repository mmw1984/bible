package com.marcow.bible.feature.aichat

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.ui.AiChatComposer
import com.marcow.bible.feature.aichat.ui.AiChatEmpty
import com.marcow.bible.feature.aichat.ui.AiChatInitializing
import com.marcow.bible.feature.aichat.ui.AiChatSignInNotice
import com.marcow.bible.feature.aichat.ui.AiChatTranscript
import kotlinx.coroutines.launch

/**
 * The chat page, replacing `_AiChatPageState.build` at `legacy/flutter/lib/ai_chat_page.dart:264`.
 *
 * Flutter built a `Stack` over a `ColoredBox`: the body filling the scene, the header pinned to the top
 * of it and the composer dock pinned to the bottom. The header has no background of its own, so the
 * conversation scrolls *under* it — which is why [AiChatTranscript] is given a [AiChatScreen]'s
 * [topInset] rather than a top padding: the hole at the old end of the reversed list is the header's
 * height plus 64, and putting it in the list is what lets the reader scroll a restored conversation up
 * out from beneath the buttons.
 *
 * Every input is a callback, as in `DevotionScreen`: this holds no view model, no clipboard and no
 * browser. [AiChatRoute] is what turns a tap into a send.
 *
 * Three pieces of Flutter's page state live here because they are about the window rather than about
 * the conversation, and the window is what a screen draws:
 *
 *  - **[followLatest]**, which decides both the auto-scroll and whether the 「跳到最新」 button is
 *    offered. Dart recomputed it from every scroll notification and compared against 96 px of distance
 *    from the newest answer; the same 96 is [FOLLOW_THRESHOLD] here, and the list is read through
 *    [snapshotFlow] instead of a listener because a Compose scroll is not a notification stream.
 *  - **[dockHeight]**, the composer's measured height, which the body pads itself by so the newest
 *    answer can be scrolled clear of the dock. Flutter's `_SizeObserver` reported the dock's size after
 *    layout; `onSizeChanged` is that.
 *  - **[dockOffset]**, how far the dock is lifted. `keyboard > 0 || !embedded ? keyboard :
 *    appNavBottomClearance(context)` is Dart's own expression: an embedded page sits inside somebody
 *    else's navigation and gets out of its way, a full page owes the clearance itself.
 *
 * The header is a back button and a settings button and nothing else. `_ChatHeader` was handed an
 * `onClear` and never drew it — 「清除對話」 lives in the AI settings panel instead (`NATIVE_PLAN.md` §4
 * item 8) — so no clear dialog is drawn here either, and [AiChatViewModel.clear] has no caller on this
 * screen.
 *
 * [bottomClearance] is the host's navigation bar, the same arrangement the reader and the devotion page
 * take, because features do not depend on each other (`NATIVE_PLAN.md` §2.2).
 */
@Suppress("LongParameterList")
@Composable
fun AiChatScreen(
    state: AiChatState,
    question: String,
    onQuestionChange: (String) -> Unit,
    embedded: Boolean,
    bottomClearance: Dp,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onConnect: () -> Unit,
    onRetryInitialize: () -> Unit,
    onCopy: (AiMessage) -> Unit,
    onOpenLink: (String) -> Unit,
    autofocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    var dockHeight by remember { mutableStateOf(DOCK_HEIGHT_FALLBACK) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val keyboard = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    // Flutter's `AnimatedPadding(bottom: dockOffset, duration: 220, curve: easeOutCubic)`. Animating the
    // number and padding by it is the same animation, and it is this window's rather than the search's.
    val dockOffset by animateDpAsState(
        targetValue = if (keyboard > 0.dp || !embedded) keyboard else bottomClearance,
        animationSpec = tween(DOCK_OFFSET_MILLIS, easing = EaseOutCubic),
        label = DOCK_OFFSET_LABEL,
    )
    val followThreshold = with(density) { FOLLOW_THRESHOLD.toPx() }

    // Dart's `NotificationListener<ScrollNotification>`: any scroll that moves the viewport off the newest
    // answer by more than 96 stops following, and coming back inside it starts following again. Read
    // through `snapshotFlow` so the flag settles once per change rather than once per frame of a fling.
    LaunchedEffect(listState, followThreshold) {
        snapshotFlow { listState.awayFromLatest(followThreshold) }
            .collect { away ->
                val next = !away
                if (followLatest != next) followLatest = next
            }
    }
    // `_controllerChanged` → `if (followLatest) _scheduleScroll(animated: false)`, and that jumped rather
    // than animated. Keyed on the messages themselves rather than their count, because a streaming answer
    // grows its own text without adding a turn — which is the case Dart's listener covered and a count
    // would have missed.
    LaunchedEffect(followLatest, state.messages, dockHeight, dockOffset) {
        if (followLatest && state.messages.isNotEmpty()) listState.scrollToItem(0)
    }
    // `_scrollToLatest`: the button's own tap is the one place Dart animated, over 280 ms.
    // `animateScrollToItem` takes no `animationSpec` — it snaps when the target is more than three
    // items away and springs when it is not, which is neither the curve nor the length Dart ran — so
    // the jump is the list's own scroll animated by the tween instead. On a reversed list `value` is 0
    // at item 0 settled, which is the same destination `scrollToItem(0)` gives above.
    val goToLatest: () -> Unit = {
        followLatest = true
        scope.launch {
            listState.animateScrollBy(
                value = -listState.value,
                animationSpec = tween(SCROLL_TO_LATEST_MILLIS, easing = EaseOutCubic),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        AiChatBody(
            state = state,
            bottomPadding = dockHeight + dockOffset,
            topInset = topInset,
            listState = listState,
            onRegenerate = onRegenerate,
            onCopy = onCopy,
            onOpenLink = onOpenLink,
            onConnect = onConnect,
            onRetryInitialize = onRetryInitialize,
            modifier = Modifier.fillMaxSize(),
        )
        AiChatHeader(
            showBack = !embedded,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = dockOffset),
        ) {
            AiChatDock(
                state = state,
                question = question,
                onQuestionChange = onQuestionChange,
                onSend = onSend,
                onStop = onStop,
                onRemoveAttachment = onRemoveAttachment,
                onGoToLatest = goToLatest,
                showGoToLatest = !followLatest && state.messages.isNotEmpty(),
                autofocus = autofocus,
                onDockMeasured = { dockHeight = it },
            )
        }
    }
}

/**
 * `_body` (`ai_chat_page.dart:366`): the transcript, or the reason there is not one yet.
 *
 * The three cases never coexist in Flutter and do not here. [AiChatState.initialized] is false until the
 * stored conversation has been read back, and until then there is nothing behind the spinner to show;
 * [AiChatState.requiresLogin] puts the sign-in notice above the transcript rather than instead of it,
 * because a reader who is not signed in can still read what they asked yesterday.
 */
@Suppress("LongParameterList")
@Composable
private fun AiChatBody(
    state: AiChatState,
    bottomPadding: Dp,
    topInset: Dp,
    listState: LazyListState,
    onRegenerate: () -> Unit,
    onCopy: (AiMessage) -> Unit,
    onOpenLink: (String) -> Unit,
    onConnect: () -> Unit,
    onRetryInitialize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.initialized) {
        AiChatInitializing(
            failed = state.initializationError != null,
            onRetry = onRetryInitialize,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier) {
        if (state.requiresLogin) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HEADER_CLEARANCE),
            ) {
                AiChatSignInNotice(error = state.authError, onConnect = onConnect)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.messages.isEmpty()) {
                // `launchScriptureReference` ungated, as Dart passed it: a reader who arrived on Genesis 1
                // and has not asked anything yet is looking at Genesis 1, not at "no conversation yet".
                AiChatEmpty(scriptureReference = state.attachedScriptureReference)
            } else {
                AiChatTranscript(
                    messages = state.messages,
                    generating = state.generating,
                    bottomPadding = bottomPadding,
                    topInset = topInset,
                    listState = listState,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onLink = onOpenLink,
                )
            }
        }
    }
}

/**
 * `_ChatComposerDock` and the `_SizeObserver` around it (`ai_chat_page.dart:885`): the composer, with the
 * 「跳到最新」 button floating above its right-hand end.
 *
 * The button's row is 48 tall whether it is there or not, so the composer does not jump as the flag
 * flips while an answer is on its way — and because the dock's own height is what [onDockMeasured]
 * reports, the transcript's bottom padding grows with it, which is the arrangement `_SizeObserver`
 * measured in Dart.
 */
@Suppress("LongParameterList")
@Composable
private fun AiChatDock(
    state: AiChatState,
    question: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onGoToLatest: () -> Unit,
    showGoToLatest: Boolean,
    autofocus: Boolean,
    onDockMeasured: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onDockMeasured(with(density) { it.height.toDp() }) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GO_TO_LATEST_ROW),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (showGoToLatest) {
                AppGlyphButton(
                    glyph = AppGlyph.CHEVRON_DOWN,
                    label = stringResource(R.string.go_to_latest),
                    onClick = onGoToLatest,
                    modifier = Modifier
                        .padding(end = GO_TO_LATEST_END)
                        .testTag(GO_TO_LATEST_TAG),
                    size = GO_TO_LATEST_SIZE,
                    fill = appColors.surfaceRaised,
                )
            }
        }
        AiChatComposer(
            question = question,
            onQuestionChange = onQuestionChange,
            // `error` in Dart was one field the page set from four places; here the failure sentence is
            // already resolved into the state, so the composer is handed the one that applies.
            error = state.generationError,
            scriptureReference = state.attachedScriptureReference.takeIf { state.contextAttached },
            scriptureAttachment = state.attachmentChip,
            generating = state.generating,
            // `controller.isReady || controller.requiresLogin`: a reader who is not signed in may still
            // press send, because that press is what opens the sign-in.
            canSend = state.isReady || state.requiresLogin,
            onSend = onSend,
            onStop = onStop,
            onRemoveAttachment = onRemoveAttachment,
            autofocus = autofocus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * `_ChatHeader` (`ai_chat_page.dart:520`): the back button on the left, the settings button on the right,
 * and nothing between them.
 *
 * `SafeArea(bottom: false)` is [WindowInsets.statusBars] read as a padding rather than a modifier,
 * because the header is not what consumes the inset — the transcript's top spacer is, and [topInset]
 * carries the same number to it.
 */
@Composable
private fun AiChatHeader(
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HEADER_PADDING_HORIZONTAL,
                top = HEADER_PADDING_TOP,
                end = HEADER_PADDING_HORIZONTAL,
                bottom = HEADER_PADDING_BOTTOM,
            )
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `Expanded(Row[back?, SizedBox(4), Spacer()])` then the settings button: the left group takes
        // the slack through its own trailing spacer, which is what puts back on the left edge and
        // settings on the right one. An embedded page drops the back button and the slack still does.
        if (showBack) {
            AiChatHeaderButton(glyph = AppGlyph.BACK, label = stringResource(R.string.back), onClick = onBack)
            Spacer(modifier = Modifier.width(HEADER_BUTTON_GAP))
        }
        Spacer(modifier = Modifier.weight(1f))
        AiChatHeaderButton(
            glyph = AppGlyph.SETTINGS,
            label = stringResource(R.string.ai_settings),
            onClick = onOpenSettings,
        )
    }
}

/**
 * `_ChatHeaderButton` (`ai_chat_page.dart:572`): a 40 square of surface with a 19 muted glyph in it.
 *
 * The name goes on the glyph, for the reason `AnswerAction` puts its label there: the native `AppTap`
 * takes no label to hand, and an unlabelled button is an unlabelled button to a screen reader.
 */
@Composable
private fun AiChatHeaderButton(glyph: AppGlyph, label: String, onClick: () -> Unit) {
    AppControlSurface {
        AppTap(onClick = onClick) {
            Box(modifier = Modifier.size(HEADER_BUTTON_SIZE), contentAlignment = Alignment.Center) {
                AppGlyphView(
                    glyph = glyph,
                    color = appColors.muted,
                    size = HEADER_GLYPH_SIZE,
                    contentDescription = label,
                )
            }
        }
    }
}

/**
 * Whether the reader has scrolled past the newest answer by more than [threshold].
 *
 * Dart's `notification.metrics.pixels - notification.metrics.minScrollExtent < 96` (`ai_chat_page.dart:392`),
 * read off the reversed list instead: that subtraction is the distance from the newest end, which here is
 * item 0's scroll offset. The list runs upside down, so the newest turn is item 0 and scrolling away from
 * it is a *larger* index rather than a smaller one — which is the whole reason the reversed layout reads
 * like a chat. Being on any item past the first is therefore already past the threshold, and only the
 * tail of item 0 is measured against it.
 */
private fun LazyListState.awayFromLatest(threshold: Float): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold

/**
 * `max(10, max(radius, bottomInset) * .24)` is the composer's own gutter, and this is the 64 the header
 * adds under the status bar before the sign-in notice, so a notice in a signed-out chat clears the
 * buttons above it.
 */
private val HEADER_CLEARANCE = 64.dp

/** `EdgeInsets.fromLTRB(10, 4, 10, 8)` around the header's row, and the 4 or 6 beside a button. */
private val HEADER_PADDING_HORIZONTAL = 10.dp
private val HEADER_PADDING_TOP = 4.dp
private val HEADER_PADDING_BOTTOM = 8.dp
private val HEADER_BUTTON_GAP = 4.dp

/** `_ChatHeaderButton`'s 40 square and its 19 glyph. */
private val HEADER_BUTTON_SIZE = 40.dp
private val HEADER_GLYPH_SIZE = 19.dp

/** The 220 ms the dock's `AnimatedPadding` ran for, and the 280 of an explicit jump to the latest. */
private const val DOCK_OFFSET_MILLIS = 220
private const val SCROLL_TO_LATEST_MILLIS = 280
private const val DOCK_OFFSET_LABEL = "aiChatDockOffset"

/** The 96 `notification.metrics.pixels - minScrollExtent < 96` was comparing against. */
private val FOLLOW_THRESHOLD = 96.dp

/**
 * `double composerDockHeight = 116`: the dock's height before it has ever been measured.
 *
 * Flutter seeded the field with the same number for the same reason — a first frame with no measurement
 * still has to reserve room for the composer rather than draw the transcript under it.
 */
private val DOCK_HEIGHT_FALLBACK = 116.dp

/** The row the floating button sits in, its 48, the 14 off the right edge, and the button's own 42. */
private val GO_TO_LATEST_ROW = 48.dp
private val GO_TO_LATEST_END = 14.dp
private val GO_TO_LATEST_SIZE = 42.dp

/** A tag rather than Flutter's `ValueKey`, so the button can be found the way the rest of them are. */
private const val GO_TO_LATEST_TAG = "ai-go-to-latest"
