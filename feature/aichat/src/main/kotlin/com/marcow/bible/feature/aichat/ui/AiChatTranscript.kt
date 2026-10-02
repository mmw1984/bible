package com.marcow.bible.feature.aichat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.domain.AiMessageRole

/**
 * The conversation: `_MessageList` in `legacy/flutter/lib/ai_chat_page.dart:600`, the turns themselves
 * already being [AiChatTurn].
 *
 * The list runs upside down, which is Dart's `reverse: true` and the reason the newest turn is the first
 * item. Reversed means the conversation grows at the top and stays pinned to the newest answer while one
 * is streaming, so the reader never watches their own question move as the answer under it grows — which
 * is the whole reason a chat reads as a chat rather than as a log.
 *
 * [bottomPadding] is the composer dock's height plus whatever the keyboard is taking, so the last answer
 * can be scrolled clear of both; [topInset] is the header's, so the first answer of a restored
 * conversation can be scrolled up under it.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AiChatTranscript(
    messages: List<AiMessage>,
    generating: Boolean,
    bottomPadding: Dp,
    topInset: Dp,
    listState: LazyListState,
    onCopy: (AiMessage) -> Unit,
    onRegenerate: () -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dart read both of these off `MediaQuery`: the measure out of the screen's width, and the insets it
    // handed the composer and the header.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gutter = max(LIST_GUTTER_MIN, (screenWidth - min(screenWidth, MEASURE_MAX)) / 2 + LIST_GUTTER_MIN)
    // `generating && (messages.isEmpty || messages.last.role != 'assistant')`: the indicator belongs to the
    // answer that has not arrived, so a turn already on its way above it is drawn as itself.
    val thinking = generating && (messages.isEmpty() || messages.last().role != AiMessageRole.ASSISTANT)
    // Dart asked every turn whether it was the last one the model spoke, which is the same question as
    // asking where the last of them is.
    val latestAssistant = messages.indexOfLast { it.role == AiMessageRole.ASSISTANT }
    val keyboard = LocalSoftwareKeyboardController.current

    LazyColumn(
        state = listState,
        modifier = modifier
            .testTag(TRANSCRIPT_TAG)
            // `ScrollViewKeyboardDismissBehavior.onDrag`, done without taking the drag: the list is
            // already scrolling under this pointer, so the gesture is only watched, never consumed, and the
            // keyboard is put away as soon as the reader scrolls back through the answers.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        if (event.changes.any { it.positionChanged() }) keyboard?.hide()
                    }
                }
            },
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = gutter,
            top = LIST_PADDING_TOP,
            end = gutter,
            bottom = max(LIST_PADDING_BOTTOM, bottomPadding),
        ),
    ) {
        if (thinking) {
            item(key = THINKING_KEY) {
                ThinkingIndicator()
            }
        }
        items(count = messages.size, key = { offset -> turnKey(messages.size - 1 - offset, messages) }) { offset ->
            val index = messages.size - 1 - offset
            AiChatTurn(
                message = messages[index],
                streaming = generating && index == latestAssistant,
                showRegenerate = index == latestAssistant && !generating,
                onCopy = { onCopy(messages[index]) },
                onRegenerate = onRegenerate,
                onLink = onLink,
            )
        }
        // `topSpacerIndex`: Dart gave the oldest end of the list a hole the height of the status bar plus
        // 64, so a restored conversation starts below the header instead of under it.
        item(key = TOP_SPACER_KEY) {
            Spacer(modifier = Modifier.height(topInset + LIST_TOP_GAP))
        }
    }
}

/**
 * `_ThinkingIndicator` (`ai_chat_page.dart:1150`): the same three dots the answer's own reasoning block
 * uses, with the word beside them.
 *
 * The dots are the ones in `AiChatTurn` rather than a second set: Flutter had one `_ThinkingDots` for both
 * callers, and two sets here would be two clocks that drift apart within a second of each other.
 */
@Composable
private fun ThinkingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(bottom = THINKING_MARGIN_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingDots(compact = false)
        Spacer(modifier = Modifier.width(THINKING_GAP))
        Text(
            text = stringResource(R.string.thinking),
            color = appColors.muted,
            fontFamily = AppFonts.OpenRunde,
            fontSize = THINKING_FONT_SIZE,
        )
    }
}

/** Dart's `ValueKey('ai-message-$messageIndex-${message.role}')`, which is what keeps a streaming turn's state. */
private fun turnKey(index: Int, messages: List<AiMessage>): String = "ai-message-$index-${messages[index].role}"

/** The measure the turns are laid out in: `math.min(MediaQuery.sizeOf(context).width, 760.0)`. */
private val MEASURE_MAX = 760.dp

/** The `max(12, (width - measure) / 2 + 12)` either side, and the 16 above and below. */
private val LIST_GUTTER_MIN = 12.dp
private val LIST_PADDING_TOP = 16.dp
private val LIST_PADDING_BOTTOM = 16.dp

/** The 64 the top spacer adds to the header's height. */
private val LIST_TOP_GAP = 64.dp

/** The 18 below the indicator, the 9 beside it and its 11. */
private val THINKING_MARGIN_BOTTOM = 18.dp
private val THINKING_GAP = 9.dp
private val THINKING_FONT_SIZE = 11.sp

/** Flutter's `ValueKey('ai-message-list')`, kept so a test or a screenshot can find the same list. */
private const val TRANSCRIPT_TAG = "ai-message-list"

/** Item keys for the two items that are not turns, so their positions never shift under a rebuild. */
private const val THINKING_KEY = "ai-thinking"
private const val TOP_SPACER_KEY = "ai-top-spacer"
