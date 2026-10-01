package com.marcow.bible.feature.devotion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.max

/**
 * The three-quarter arc that Flutter's `AppSpinner` painted, turning once every 820 ms.
 *
 * `AppSpinner` is the app's spinner, not a local one — the loading body, a loading image and a
 * loading SoundCloud frame all used it, and a second spinner here would be the same shape with a
 * different rotation period on the same screen. The numbers are the painter's: a stroke of
 * `max(1.6, shortestSide * .12)` with square caps, an arc that starts at −0.9 rad and sweeps
 * `pi * 1.35`, and the whole canvas turned once per revolution.
 */
@Composable
fun DevotionSpinner(color: Color, modifier: Modifier = Modifier, size: Dp = DevotionChrome.SPINNER_SIZE) {
    val turn by rememberInfiniteTransition(label = DevotionSpinnerLabel).animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(DevotionChrome.SPINNER_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = DevotionSpinnerLabel,
    )
    Canvas(modifier = modifier.size(size).rotate(turn)) {
        val box = this.size
        val stroke = max(1.6f, box.minDimension * SpinnerStrokeFraction)
        drawArc(
            color = color,
            startAngle = SpinnerStartRadians,
            sweepAngle = SpinnerSweepRadians,
            useCenter = false,
            // Flutter deflated the box by half the stroke, so the arc's own width stays inside it
            // instead of being clipped at the canvas edge.
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(box.width - stroke, box.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Square),
        )
    }
}

private const val DevotionSpinnerLabel = "devotionSpinner"

/** `_SpinnerPainter`: `math.max(1.6, size.shortestSide * .12)`. */
private const val SpinnerStrokeFraction = 0.12f

/** In radians, because the painter wrote them in radians: `canvas.drawArc(..., -.9, pi * 1.35, ...)`. */
private const val SpinnerStartRadians = -0.9f

private val SpinnerSweepRadians = (PI * 1.35).toFloat()
