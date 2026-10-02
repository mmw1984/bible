package com.marcow.bible.feature.aichat.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppButton
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.feature.aichat.domain.AiMessage
import com.marcow.bible.feature.aichat.domain.AiMessageRole
import com.marcow.bible.feature.aichat.domain.sanitizeReasoningForDisplay
import kotlin.math.ceil
import kotlin.math.sin

/**
 * One turn of the conversation — `_MessageRow` in `legacy/flutter/lib/ai_chat_page.dart:663` — as the
 * reader's bubble or the model's, chosen by [AiMessage.role].
 *
 * The two look nothing alike because they came from different parts of the Dart: the reader's is an
 * ink pill that hugs its own text, and the model's is a bordered card inset from the right that carries
 * the turn's actions underneath it. An [AiMessageRole.UNKNOWN] is drawn as the model's, which is what
 * `message.role == 'user'` asked for — anything that is not the reader is not the reader.
 */
@Composable
internal fun AiChatTurn(
    message: AiMessage,
    streaming: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.role == AiMessageRole.USER) {
        UserTurn(message = message, modifier = modifier)
    } else {
        AssistantTurn(
            message = message,
            streaming = streaming,
            showRegenerate = showRegenerate,
            onCopy = onCopy,
            onRegenerate = onRegenerate,
            onLink = onLink,
            modifier = modifier,
        )
    }
}

/**
 * The reader's turn: an ink pill, right-aligned, sized to the text it holds.
 *
 * Dart measured the question with a throwaway `TextPainter` before laying it out, so a short one got a
 * short pill and a long one got the full measure (`ai_chat_page.dart:695-709`). The pill is measured
 * here instead of guessed, because guessing it is what makes a chat read wrong: a pill at full measure
 * for a two-word question leaves a hole of canvas between it and the question.
 *
 * The measure comes from the screen rather than from the transcript, because that is what Dart's
 * `MediaQuery` gave it. At 76% of a tablet the pill is 430 wide, and narrowing it to the transcript's
 * own measure would make a long question wrap where it did not.
 */
@Composable
private fun UserTurn(message: AiMessage, modifier: Modifier = Modifier) {
    val colors = appColors
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val measure: Dp = (screenWidth * BUBBLE_SCREEN_SHARE).coerceAtMost(BUBBLE_MAX_WIDTH) - BUBBLE_PADDING_HORIZONTAL * 2
    val style = TextStyle(
        color = colors.canvas,
        fontFamily = AppFonts.OpenRunde,
        fontSize = BUBBLE_FONT_SIZE,
        lineHeight = BUBBLE_FONT_SIZE * BUBBLE_LINE_HEIGHT,
    )
    // Dart asked whether the question fitted on one line and what that line was wide; both are asked of
    // the question that has just been laid out instead, which is the same question asked later rather
    // than before. [oneLine] is only ever set, never cleared, so a settled pill is not asked twice.
    var oneLine by remember(message.text, measure) { mutableStateOf(false) }
    var natural by remember(message.text, measure) { mutableStateOf(0f) }
    val forcedWrap = message.text.contains('\n')
    val width: Dp = if (oneLine) {
        val naturalDp = with(density) { natural.toDp() }
        measure.coerceAtMost(maxOf(BUBBLE_WIDTH_FLOOR, ceil(naturalDp.value).dp + BUBBLE_WIDTH_SLACK))
    } else {
        measure
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .padding(bottom = TURN_MARGIN_BOTTOM)
                .background(colors.ink, RoundedCornerShape(appRadii.surface))
                .padding(horizontal = BUBBLE_PADDING_HORIZONTAL, vertical = BUBBLE_PADDING_VERTICAL),
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = style,
                    modifier = Modifier.width(width),
                    // Unlimited while the question is being measured, because one that does not fit has to
                    // wrap over several lines rather than be cut short; pinned to one once it is known to
                    // fit, so that a question which later grows is ellipsised instead of re-wrapping and
                    // resizing the pill under the reader's finger.
                    maxLines = if (oneLine) 1 else Int.MAX_VALUE,
                    overflow = if (oneLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                    onTextLayout = { layout ->
                        if (!oneLine && !forcedWrap && layout.lineCount == 1) {
                            natural = layout.size.width
                            oneLine = true
                        }
                    },
                )
            }
        }
    }
}

/**
 * The model's turn: a bordered card, left-aligned, holding the thinking block, the answer, the
 * incomplete note and the turn's two actions.
 *
 * The card is inset 22 from the right rather than filling the measure, so a model's answer and the
 * reader's question beside it never share an edge. The actions sit inside the card rather than under it,
 * which is why its bottom padding is 7 where its top is 12.
 */
@Composable
private fun AssistantTurn(
    message: AiMessage,
    streaming: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val reasoning = sanitizeReasoningForDisplay(message.reasoning.orEmpty())
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Column(
            modifier = Modifier
                .padding(end = ASSISTANT_MARGIN_END, bottom = TURN_MARGIN_BOTTOM)
                .background(colors.surface, RoundedCornerShape(appRadii.surface))
                .border(
                    width = 1.dp,
                    color = colors.line.copy(alpha = ASSISTANT_BORDER_ALPHA),
                    shape = RoundedCornerShape(appRadii.surface),
                )
                .padding(
                    start = ASSISTANT_PADDING_HORIZONTAL,
                    top = ASSISTANT_PADDING_TOP,
                    end = ASSISTANT_PADDING_HORIZONTAL,
                    bottom = ASSISTANT_PADDING_BOTTOM,
                ),
        ) {
            if (reasoning.isNotEmpty()) {
                ReasoningDisclosure(text = reasoning, streaming = streaming)
                Spacer(modifier = Modifier.height(REASONING_GAP))
            }
            if (message.text.isNotEmpty()) {
                AppMarkdownBlocks(
                    data = message.text,
                    // Dart's `Semantics(liveRegion: streaming)` around the answer, so a screen reader
                    // hears it arriving — and only while it is arriving, as the flag says.
                    modifier = Modifier.semantics {
                        liveRegion = if (streaming) LiveRegionMode.Polite else LiveRegionMode.None
                    },
                    onLink = onLink,
                )
            }
            if (message.incomplete) {
                Spacer(modifier = Modifier.height(INCOMPLETE_GAP))
                Text(
                    text = stringResource(R.string.answer_incomplete),
                    color = colors.muted,
                    fontFamily = AppFonts.OpenRunde,
                    fontSize = INCOMPLETE_FONT_SIZE,
                )
            }
            Spacer(modifier = Modifier.height(ACTIONS_GAP))
            Row(horizontalArrangement = Arrangement.spacedBy(ACTIONS_GAP)) {
                AnswerAction(
                    glyph = AppGlyph.COPY,
                    label = stringResource(R.string.copy_answer),
                    onClick = onCopy,
                )
                if (showRegenerate) {
                    AnswerAction(
                        glyph = AppGlyph.REFRESH,
                        label = stringResource(R.string.regenerate),
                        onClick = onRegenerate,
                    )
                }
            }
        }
    }
}

/**
 * `_ReasoningDisclosure` (`ai_chat_page.dart:796`): the thinking block's header, and the thought itself
 * underneath it while it is open.
 *
 * Open while streaming and closed once the answer lands, and open again the moment another answer
 * starts. Dart reached that by expanding on the `false → true` edge of [streaming] in `didUpdateWidget`
 * and by seeding the field from the flag on the way in; the effect here watches the same flag, and
 * because it only ever sets the field to true it cannot reopen the block on an unrelated recomposition.
 *
 * No link handler is passed to [AppMarkdownBlocks], so a link in a thought is drawn and does nothing,
 * which is what `AppMarkdown` with no `onLink` did.
 */
@Composable
private fun ReasoningDisclosure(text: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(streaming) }
    LaunchedEffect(streaming) {
        if (streaming) expanded = true
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_QUARTER_TURN else 0f,
        animationSpec = tween(durationMillis = CHEVRON_DURATION_MILLIS),
        label = "reasoning-chevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        AppControlSurface(
            shape = RoundedCornerShape(appRadii.compact),
            color = appColors.surfaceRaised.copy(alpha = REASONING_SURFACE_ALPHA),
        ) {
            AppTap(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(
                    start = REASONING_PADDING_START,
                    top = REASONING_PADDING_VERTICAL,
                    end = REASONING_PADDING_END,
                    bottom = REASONING_PADDING_VERTICAL,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppGlyphView(
                        glyph = AppGlyph.CHEVRON_RIGHT,
                        color = appColors.muted,
                        size = CHEVRON_SIZE,
                        // Dart labelled the whole tap `collapseThinking` / `expandThinking`, and the
                        // native `AppTap` takes no label to hand, so the name goes on the glyph that
                        // turns — which is where [AnswerAction] puts its label for the same reason.
                        contentDescription = stringResource(
                            if (expanded) R.string.collapse_thinking else R.string.expand_thinking,
                        ),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                    Spacer(modifier = Modifier.width(CHEVRON_GAP))
                    Text(
                        text = stringResource(
                            if (streaming) R.string.thinking else R.string.thinking_content,
                        ),
                        color = appColors.muted,
                        fontFamily = AppFonts.OpenRunde,
                        fontSize = REASONING_FONT_SIZE,
                        fontWeight = FontWeight.W600,
                    )
                    if (streaming) {
                        Spacer(modifier = Modifier.width(DOTS_GAP))
                        ThinkingDots(compact = true)
                    }
                }
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(REASONING_BODY_GAP))
            AppMarkdownBlocks(
                data = text,
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = REASONING_SIZE_MILLIS, easing = EaseOutCubic),
                ),
                compact = true,
                foreground = appColors.muted,
                secondary = appColors.muted,
            )
        }
    }
}

/**
 * `_InlineAction` (`ai_chat_page.dart:1425`): the copy and regenerate buttons under an answer.
 *
 * The label is on the button and nowhere else, which is what keeps the row two glyphs wide; it is still
 * the button's accessible name, so it is handed to the glyph rather than dropped. Dart's `AppButton` took
 * the label as a parameter and the native one takes only a click, so the name goes on the glyph — which
 * is where [AppGlyphView] puts it everywhere else in the app.
 */
@Composable
private fun AnswerAction(glyph: AppGlyph, label: String, onClick: () -> Unit) {
    AppButton(
        onClick = onClick,
        shape = RoundedCornerShape(appRadii.compact),
        color = appColors.surfaceRaised.copy(alpha = ACTION_SURFACE_ALPHA),
    ) {
        Box(
            modifier = Modifier.size(width = ACTION_WIDTH, height = ACTION_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphView(glyph = glyph, color = appColors.muted, size = ACTION_GLYPH_SIZE, contentDescription = label)
        }
    }
}

/**
 * `_ThinkingDots` (`ai_chat_page.dart:1178`): the three dots that rise and fade while a thought streams.
 *
 * One clock drives all three and each dot reads it at its own 0.16 offset, so they chase each other
 * rather than pulsing together — Dart's `((animation.value - index * .16) % 1)`. The modulo is written
 * with the add-and-take again because Dart's `%` never comes back negative for a positive divisor and
 * Kotlin's does, and the first half of a cycle is exactly when it would.
 */
@Composable
private fun ThinkingDots(compact: Boolean) {
    val clock by rememberInfiniteTransition(label = "thinking-dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = DOTS_CYCLE_MILLIS, easing = LinearEasing)),
        label = "thinking-dots-clock",
    )
    Row(
        modifier = Modifier.size(width = if (compact) DOTS_WIDTH_COMPACT else DOTS_WIDTH, height = DOTS_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DOTS_COUNT) { index ->
            val phase = ((clock - index * DOTS_PHASE_STEP) % 1f + 1f) % 1f
            val pulse = (sin(phase * TWO_PI) + 1f) / 2f
            Box(
                modifier = Modifier
                    .testTag(THINKING_DOT_TAG_PREFIX + index)
                    .offset(y = -(DOTS_RISE * pulse).dp)
                    .size(if (compact) DOTS_SIZE_COMPACT else DOTS_SIZE)
                    .alpha(DOTS_MIN_ALPHA + pulse * DOTS_ALPHA_RANGE)
                    .background(appColors.muted, CircleShape),
            )
        }
    }
}

/** `math.min(MediaQuery.sizeOf(context).width * .76, 430.0)` — the pill's share of the screen. */
private const val BUBBLE_SCREEN_SHARE = 0.76f

/** The 430 dp cap on that share. */
private val BUBBLE_MAX_WIDTH = 430.dp

/** The pill's `EdgeInsets.symmetric(horizontal: 12, vertical: 6)`. */
private val BUBBLE_PADDING_HORIZONTAL = 12.dp
private val BUBBLE_PADDING_VERTICAL = 6.dp

/** The pill's 14 at `1.4`. */
private val BUBBLE_FONT_SIZE = 14.sp
private const val BUBBLE_LINE_HEIGHT = 1.4f

/** The 8 a one-line pill adds to the question, and the 8 it never shrinks below. */
private val BUBBLE_WIDTH_SLACK = 8.dp
private val BUBBLE_WIDTH_FLOOR = 8.dp

/** The 18 under every turn. */
private val TURN_MARGIN_BOTTOM = 18.dp

/** The model's 22 from the right, and its `fromLTRB(14, 12, 14, 7)`. */
private val ASSISTANT_MARGIN_END = 22.dp
private val ASSISTANT_PADDING_HORIZONTAL = 14.dp
private val ASSISTANT_PADDING_TOP = 12.dp
private val ASSISTANT_PADDING_BOTTOM = 7.dp

/** `Border.all(color: colors.line.withValues(alpha: .75))` around the model's card. */
private const val ASSISTANT_BORDER_ALPHA = 0.75f

/** The 10 between the thinking block and the answer it precedes. */
private val REASONING_GAP = 10.dp

/** `surfaceRaised.withValues(alpha: .64)` behind the thinking block's header. */
private const val REASONING_SURFACE_ALPHA = 0.64f

/** The header's `EdgeInsets.fromLTRB(8, 4, 9, 4)`. */
private val REASONING_PADDING_START = 8.dp
private val REASONING_PADDING_END = 9.dp
private val REASONING_PADDING_VERTICAL = 4.dp

/** The 15 chevron, a quarter turn when open, and the 6 after it. */
private val CHEVRON_SIZE = 15.dp
private const val CHEVRON_QUARTER_TURN = 90f
private const val CHEVRON_DURATION_MILLIS = 180
private val CHEVRON_GAP = 6.dp

/** The header's `w600` 11, the 7 before the dots, and the 5 above the thought. */
private val REASONING_FONT_SIZE = 11.sp
private val DOTS_GAP = 7.dp
private val REASONING_BODY_GAP = 5.dp
private const val REASONING_SIZE_MILLIS = 220

/** The 3 above the incomplete note, its 10, and the 4 between it and the actions. */
private val INCOMPLETE_GAP = 3.dp
private val INCOMPLETE_FONT_SIZE = 10.sp
private val ACTIONS_GAP = 4.dp

/** The action's 34 by 32, its `.66` surface, and its 15 glyph. */
private val ACTION_WIDTH = 34.dp
private val ACTION_HEIGHT = 32.dp
private const val ACTION_SURFACE_ALPHA = 0.66f
private val ACTION_GLYPH_SIZE = 15.dp

/** The dots: a 22 or 28 box, 14 tall, three of 4 or 5, rising 2 and fading from `.28` to `1`. */
private val DOTS_WIDTH_COMPACT = 22.dp
private val DOTS_WIDTH = 28.dp
private val DOTS_HEIGHT = 14.dp
private val DOTS_SIZE_COMPACT = 4.dp
private val DOTS_SIZE = 5.dp
private const val DOTS_COUNT = 3
private const val DOTS_PHASE_STEP = 0.16f
private const val DOTS_RISE = 2f
private const val DOTS_MIN_ALPHA = 0.28f
private const val DOTS_ALPHA_RANGE = 0.72f
private const val DOTS_CYCLE_MILLIS = 1050
private const val TWO_PI = 6.2831855f

/** Flutter's `ValueKey('thinking-dot-$index')`, as something a test can find. */
private const val THINKING_DOT_TAG_PREFIX = "thinking-dot-"
