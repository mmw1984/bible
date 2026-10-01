package com.marcow.bible.feature.search.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.theme.appColors
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The rotating arc the search sheet draws while a request is out, mirroring `AppSpinner` in
 * `legacy/flutter/lib/app_ui.dart`.
 *
 * `AppSpinner` lives in `core:design-system`, which is outside this feature's scope and has not
 * carried it yet, so the sheet carries its own copy: the same arc (start `-0.9` rad, sweep
 * `1.35 * PI`), the same square cap, the same `max(1.6, shortestSide * .12)` stroke, and the same
 * 820 ms constant rotation. When the design-system module is next in scope this should become that
 * widget rather than a second implementation.
 */
@Composable
internal fun SearchSpinner(color: Color, modifier: Modifier = Modifier, diameter: Dp = 20.dp) {
    val turn by spinnerTurn()
    Canvas(modifier = modifier.size(diameter).rotate(turn)) {
        val stroke = max(MIN_SPINNER_STROKE_DP, size.minDimension * SPINNER_STROKE_RATIO)
        drawArc(
            color = color,
            startAngle = SPINNER_START_RADIANS,
            sweepAngle = SPINNER_SWEEP_RADIANS,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Square),
        )
    }
}

/** `_AppSpinnerState.controller`: `repeat()` over 820 ms, restarted from 0 on every recomposition. */
@Composable
private fun spinnerTurn(): State<Float> = rememberInfiniteTransition(label = "searchSpinner").animateFloat(
    initialValue = 0f,
    targetValue = FULL_TURN,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = SPINNER_MILLIS, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = "searchSpinnerTurn",
)

/**
 * The hairline under the sheet's header that says *something* is still running, mirroring
 * `AppProgressLine` in `legacy/flutter/lib/app_ui.dart`.
 *
 * One line rather than two because this is the sheet's single "a search is running" signal — the two
 * independent per-request rows are `AiSearchLoading` in `SearchSections.kt`, which is what says
 * *which* half is still out. The two travelling segments, their widths, opacities and 1450 ms cycle
 * are `_IndeterminateLine`'s.
 */
@Composable
internal fun SearchProgressLine(color: Color, modifier: Modifier = Modifier) {
    val track = appColors.surfaceRaised
    val progress by progressLine()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_LINE_HEIGHT)
            .clip(RoundedCornerShape(PROGRESS_LINE_RADIUS)),
    ) {
        drawRect(color = track)
        ProgressSegments.forEach { segment ->
            val phase = segment.phase(progress)
            val placement = segment.placement(phase, EaseInOutCubic.transform(phase))
            drawRect(
                color = color.copy(alpha = segment.opacity),
                topLeft = Offset(placement.leftFraction * size.width, 0f),
                size = Size(placement.widthFraction * size.width, size.height),
            )
        }
    }
}

/** `_IndeterminateLineState.controller`: 1450 ms per pass, looping. */
@Composable
private fun progressLine(): State<Float> = rememberInfiniteTransition(label = "searchProgress").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = PROGRESS_MILLIS),
        repeatMode = RepeatMode.Restart,
    ),
    label = "searchProgressPhase",
)

/**
 * One travelling segment of [SearchProgressLine], which is `_ProgressLinePainter`'s two `_drawSegment`
 * calls as data: a base width, an alpha, and how far into the cycle it starts.
 */
internal data class ProgressSegment(val baseWidth: Float, val opacity: Float, val offset: Float) {
    /** `(progress + .54) % 1` for the second segment; the first one wraps at the same place. */
    fun phase(progress: Float): Float = (progress + offset) % 1f

    /**
     * The rectangle this segment draws at [phase], as fractions of the line's width.
     *
     * `-widthFactor + eased * (1 + widthFactor)`, with the width breathing as
     * `baseWidth + sin(phase * pi) * .16` — `_drawSegment`'s two lines, unchanged.
     *
     * [easedPhase] is that phase through `Curves.easeInOutCubic` and is a parameter rather than
     * applied here, so this is the arithmetic and nothing else: the curve belongs to the draw pass,
     * and what is worth knowing about two segments — that they are never at the same point in the
     * cycle — is a property of the offsets alone, which is what lets it be checked without a Canvas.
     */
    fun placement(phase: Float, easedPhase: Float): ProgressSegmentPlacement {
        val width = baseWidth + sin(phase * PI).toFloat() * SEGMENT_BREATH
        return ProgressSegmentPlacement(leftFraction = -width + easedPhase * (1 + width), widthFraction = width)
    }
}

/** One segment's box on [SearchProgressLine], in fractions of the line's width. */
internal data class ProgressSegmentPlacement(val leftFraction: Float, val widthFraction: Float)

internal val ProgressSegments = listOf(
    ProgressSegment(baseWidth = 0.38f, opacity = 1f, offset = 0f),
    ProgressSegment(baseWidth = 0.22f, opacity = 0.62f, offset = 0.54f),
)

/** `Curves.easeInOutCubic`, which the Flutter painter applied to each segment's phase. */
private val EaseInOutCubic = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private const val SPINNER_MILLIS = 820
private const val SPINNER_START_RADIANS = -0.9f
private const val SPINNER_SWEEP_RADIANS = 1.35f * PI.toFloat()
private const val SPINNER_STROKE_RATIO = 0.12f
private const val MIN_SPINNER_STROKE_DP = 1.6f
private const val FULL_TURN = 360f

private const val PROGRESS_MILLIS = 1450
private const val PROGRESS_LINE_HEIGHT = 3.dp
private const val PROGRESS_LINE_RADIUS = 50
private const val SEGMENT_BREATH = 0.16f
