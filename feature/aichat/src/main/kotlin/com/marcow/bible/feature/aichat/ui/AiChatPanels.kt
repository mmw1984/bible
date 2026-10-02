package com.marcow.bible.feature.aichat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppButton
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import kotlin.math.PI
import kotlin.math.max

/**
 * `_InitializationState` (`legacy/flutter/lib/ai_chat_page.dart:1236`): the spinner, or the reason the
 * transcript could not be read, with a retry that runs initialization again.
 *
 * It replaces the transcript rather than sitting under it, because until the stored messages have been
 * read there is nothing to show behind it and a spinner over an empty canvas is what Dart drew.
 */
@Composable
internal fun AiChatInitializing(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(PANEL_PADDING), contentAlignment = Alignment.Center) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppGlyphView(glyph = AppGlyph.CLOUD_OFF, color = appColors.muted, size = FAILURE_GLYPH_SIZE)
                Spacer(modifier = Modifier.height(FAILURE_TITLE_GAP))
                Text(
                    text = stringResource(R.string.ai_initializing_failed),
                    color = appColors.ink,
                    fontFamily = AppFonts.OpenRunde,
                    fontSize = FAILURE_TITLE_SIZE,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(FAILURE_ACTION_GAP))
                AiChatActionButton(
                    label = stringResource(R.string.retry),
                    glyph = AppGlyph.REFRESH,
                    onClick = onRetry,
                )
            }
        } else {
            AiChatSpinner(color = appColors.ink)
        }
    }
}

/**
 * `_EmptyConversation` (`ai_chat_page.dart:1277`): the book and, when the chat was opened on a chapter,
 * that chapter's reference rather than the usual words — a reader who came here from Genesis 1 is not
 * looking at "no conversation yet".
 *
 * Dart drew Lucide's `bookOpenCheck`. The design system has no glyph of that name and adding one is a
 * change to a module this feature cannot make, so [AppGlyph.BOOK] stands in for it: it is the same
 * open-book shape at the same 26.
 */
@Composable
internal fun AiChatEmpty(scriptureReference: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(PANEL_PADDING), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppGlyphView(glyph = AppGlyph.BOOK, color = appColors.faint, size = EMPTY_GLYPH_SIZE)
            Spacer(modifier = Modifier.height(EMPTY_LABEL_GAP))
            Text(
                text = scriptureReference ?: stringResource(R.string.no_conversation),
                color = appColors.muted,
                fontFamily = AppFonts.OpenRunde,
                fontSize = EMPTY_LABEL_SIZE,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `_OpenRouterNotice` and its `_NoticeBar` (`ai_chat_page.dart:1311`): why the chat cannot answer yet,
 * and the one button that fixes it.
 *
 * Dart had two widgets because it had a generic bar; only this notice ever used it, so the bar is folded
 * into the notice rather than left as a private widget with a single caller. The detail is the sign-in
 * error when there is one and the standing explanation when there is not, which is why it takes the
 * error rather than a string: a failed attempt should say what failed.
 */
@Composable
internal fun AiChatSignInNotice(error: String?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    AppButton(
        onClick = onConnect,
        modifier = modifier
            .testTag(SIGN_IN_NOTICE_TAG)
            .padding(horizontal = NOTICE_MARGIN_HORIZONTAL)
            .padding(top = NOTICE_MARGIN_TOP),
        shape = RoundedCornerShape(appRadii.control),
        color = colors.surface,
        borderColor = colors.line,
        horizontalPadding = NOTICE_PADDING_HORIZONTAL,
        verticalPadding = NOTICE_PADDING_VERTICAL,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyphView(
                glyph = AppGlyph.CLOUD,
                color = colors.ink,
                size = NOTICE_GLYPH_SIZE,
                contentDescription = stringResource(R.string.open_router_not_connected),
            )
            Spacer(modifier = Modifier.width(NOTICE_GLYPH_GAP))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.open_router_not_connected),
                    color = colors.ink,
                    fontFamily = AppFonts.OpenRunde,
                    fontSize = NOTICE_TITLE_SIZE,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(NOTICE_DETAIL_GAP))
                Text(
                    text = error ?: stringResource(R.string.open_router_connect_body),
                    color = colors.muted,
                    fontFamily = AppFonts.OpenRunde,
                    fontSize = NOTICE_DETAIL_SIZE,
                    lineHeight = NOTICE_DETAIL_LINE_HEIGHT,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(NOTICE_ACTION_GAP))
            AiChatSmallAction(label = stringResource(R.string.login), onClick = onConnect)
        }
    }
}

/**
 * `_SmallAction` (`ai_chat_page.dart:1840`): the notice's own "Sign in", a label rather than a glyph.
 */
@Composable
internal fun AiChatSmallAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(appRadii.compact),
        color = appColors.surfaceRaised.copy(alpha = SMALL_ACTION_SURFACE_ALPHA),
    ) {
        // The 32 and the 7 either side sit on the content rather than on the button, because Flutter put
        // both inside the button's own container: the minimum is of the padded box, not of the label.
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = SMALL_ACTION_MIN_HEIGHT)
                .padding(
                    horizontal = SMALL_ACTION_PADDING_HORIZONTAL,
                    vertical = SMALL_ACTION_PADDING_VERTICAL,
                ),
            color = appColors.ink,
            fontFamily = AppFonts.OpenRunde,
            fontSize = SMALL_ACTION_FONT_SIZE,
            maxLines = 1,
        )
    }
}

/**
 * `_ActionButton` (`ai_chat_page.dart:1870`): the retry's and the clear dialog's buttons.
 *
 * [emphasized] is the one that clears rather than the one that dismisses, and it is also the one drawn on
 * a raised surface with an ink border — the difference Flutter made between "the thing you want" and "the
 * thing that cancels", kept here so the dialog does not offer two identical buttons.
 */
@Composable
internal fun AiChatActionButton(
    label: String,
    onClick: () -> Unit,
    glyph: AppGlyph? = null,
    emphasized: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    AppButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(appRadii.control),
        emphasized = emphasized,
        color = if (emphasized) {
            colors.surfaceRaised.copy(alpha = ACTION_SURFACE_ALPHA)
        } else {
            colors.surface.copy(alpha = ACTION_QUIET_ALPHA)
        },
        borderColor = if (emphasized) {
            colors.ink.copy(alpha = ACTION_BORDER_ALPHA)
        } else {
            colors.line.copy(alpha = ACTION_BORDER_ALPHA)
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = ACTION_MIN_HEIGHT)
                .padding(horizontal = ACTION_PADDING_HORIZONTAL, vertical = ACTION_PADDING_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph carries no name of its own: the label beside it is the button's name, and two
            // names in one merged node would read as "Refresh, Retry" out loud.
            if (glyph != null) {
                AppGlyphView(glyph = glyph, color = colors.ink, size = ACTION_GLYPH_SIZE)
                Spacer(modifier = Modifier.width(ACTION_GLYPH_GAP))
            }
            Text(
                text = label,
                color = colors.ink,
                fontFamily = AppFonts.OpenRunde,
                fontSize = ACTION_FONT_SIZE,
                fontWeight = FontWeight.W600,
                maxLines = 1,
            )
        }
    }
}

/**
 * `AppSpinner` from `legacy/flutter/lib/app_ui.dart:732`, painted with the same numbers as Flutter's and
 * the same numbers as `DevotionSpinner`.
 *
 * It is a third copy of that painter and not a shared one: `DevotionSpinner` lives in the devotion
 * feature and `core:design-system` is outside this feature's scope, so the arc is written out again here
 * rather than either reached across a module boundary for or added to a module this work cannot touch.
 * When the design system grows a spinner, this and that one are the two that go.
 */
@Composable
internal fun AiChatSpinner(color: Color, modifier: Modifier = Modifier, size: Dp = SPINNER_SIZE) {
    val turn by rememberInfiniteTransition(label = SPINNER_LABEL).animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPINNER_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = SPINNER_LABEL,
    )
    Canvas(modifier = modifier.size(size).rotate(turn)) {
        val box = this.size
        val stroke = max(1.6f, box.minDimension * SPINNER_STROKE_FRACTION)
        drawArc(
            color = color,
            startAngle = SPINNER_START_RADIANS,
            sweepAngle = SPINNER_SWEEP_RADIANS,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(box.width - stroke, box.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Square),
        )
    }
}

/** The `EdgeInsets.all(24)` both centred panels sit in. */
private val PANEL_PADDING = 24.dp

/** The failed-initialization panel's 28 cloud-off, the 12 and 14 around it, and its 16 at w600. */
private val FAILURE_GLYPH_SIZE = 28.dp
private val FAILURE_TITLE_GAP = 12.dp
private val FAILURE_ACTION_GAP = 14.dp
private val FAILURE_TITLE_SIZE = 16.sp

/** The empty panel's 26 book, the 12 under it and its 13 at w600. */
private val EMPTY_GLYPH_SIZE = 26.dp
private val EMPTY_LABEL_GAP = 12.dp
private val EMPTY_LABEL_SIZE = 13.sp

/** The notice's `EdgeInsets.fromLTRB(10, 10, 10, 0)` and its `EdgeInsets.symmetric(horizontal: 11, vertical: 9)`. */
private val NOTICE_MARGIN_HORIZONTAL = 10.dp
private val NOTICE_MARGIN_TOP = 10.dp
private val NOTICE_PADDING_HORIZONTAL = 11.dp
private val NOTICE_PADDING_VERTICAL = 9.dp

/** Its 17 cloud, the 9 and 8 beside it, the 12 title, the 1 and the 10 detail at `height: 1.35`. */
private val NOTICE_GLYPH_SIZE = 17.dp
private val NOTICE_GLYPH_GAP = 9.dp
private val NOTICE_ACTION_GAP = 8.dp
private val NOTICE_TITLE_SIZE = 12.sp
private val NOTICE_DETAIL_GAP = 1.dp
private val NOTICE_DETAIL_SIZE = 10.sp
private val NOTICE_DETAIL_LINE_HEIGHT = 13.5.sp

/** `_SmallAction`: the `.68` surface, the `minHeight: 32`, and `EdgeInsets.symmetric(horizontal: 11, vertical: 7)`. */
private const val SMALL_ACTION_SURFACE_ALPHA = 0.68f
private val SMALL_ACTION_MIN_HEIGHT = 32.dp
private val SMALL_ACTION_PADDING_HORIZONTAL = 11.dp
private val SMALL_ACTION_PADDING_VERTICAL = 7.dp
private val SMALL_ACTION_FONT_SIZE = 11.sp

/** `_ActionButton`: the `.86`/`.68` surfaces, the `.74`/`.72` borders, and its `EdgeInsets` of 14 by 8. */
private const val ACTION_SURFACE_ALPHA = 0.86f
private const val ACTION_QUIET_ALPHA = 0.68f
private const val ACTION_BORDER_ALPHA = 0.74f
private val ACTION_PADDING_HORIZONTAL = 14.dp
private val ACTION_PADDING_VERTICAL = 8.dp
private val ACTION_MIN_HEIGHT = 40.dp
private val ACTION_GLYPH_SIZE = 15.dp
private val ACTION_GLYPH_GAP = 7.dp
private val ACTION_FONT_SIZE = 12.sp

/** `AppSpinner`'s own defaults: the 20 it was given here and the 820 ms it turns in. */
private val SPINNER_SIZE = 20.dp
private const val SPINNER_MILLIS = 820
private const val SPINNER_LABEL = "aiChatSpinner"

/** `_SpinnerPainter`: `math.max(1.6, size.shortestSide * .12)`, the `-.9` start and the `pi * 1.35` sweep. */
private const val SPINNER_STROKE_FRACTION = 0.12f
private const val SPINNER_START_RADIANS = -0.9f
private val SPINNER_SWEEP_RADIANS = (PI * 1.35).toFloat()

/** Flutter's `ValueKey('openrouter-login-notice')`. */
private const val SIGN_IN_NOTICE_TAG = "openrouter-login-notice"
