package com.marcow.bible.core.designsystem.icons

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The compact icon language from `AppGlyph` in `legacy/flutter/lib/app_ui.dart`, drawn by the app
 * rather than loaded from an icon font.
 *
 * The Flutter app shipped no icon font — every glyph is a handful of lines on a normalised 0..1
 * grid, painted by `_GlyphPainter`. [drawAppGlyph] is that painter with the same coordinates, so a
 * native icon is the same shape at the same optical weight. Using Material icons instead would
 * change the icon language of the whole app, which is the one thing Phase 1 must not do.
 */
enum class AppGlyph {
    MENU,
    CHAT,
    SEARCH,
    SUN,
    MOON,
    COPY,
    BOOK,
    CLOSE,
    CHEVRON_DOWN,
    CHEVRON_RIGHT,
    BACK,
    FORWARD,
    SETTINGS,
    DELETE,
    CLOUD,
    CLOUD_OFF,
    MEMORY,
    CHECK,
    LOGIN,
    REFRESH,
    SEND,
    STOP,
    VERIFIED,
}

/**
 * The stroke width `_GlyphPainter` used: a floor of 1.45 px, otherwise 8.5% of the shorter side.
 *
 * Exposed because the segments and the glyphs have to agree, and because a caller that sizes a glyph
 * down to a thumbnail needs the same proportional weight.
 */
fun appGlyphStrokeWidth(size: Float): Float = max(1.45f, size * 0.085f)

/**
 * Draws [glyph] in [color] into the current [DrawScope].
 *
 * The scope is expected to be the icon's own square (see `AppGlyphView`), because the coordinates
 * are fractions of the width and height exactly as they were in Flutter.
 */
fun DrawScope.drawAppGlyph(glyph: AppGlyph, color: Color) {
    val stroke = appGlyphStrokeWidth(size.minDimension)
    val paint = Stroke(
        width = stroke,
        cap = StrokeCap.Square,
        join = StrokeJoin.Round,
    )
    val w = size.width
    val h = size.height

    fun point(x: Float, y: Float) = Offset(w * x, h * y)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = stroke, cap = StrokeCap.Square)
    fun rect(x: Float, y: Float, rw: Float, rh: Float) = drawRect(
        color = color,
        topLeft = point(x, y),
        size = Size(w * rw, h * rh),
        style = paint,
    )
    fun circle(x: Float, y: Float, r: Float) = drawCircle(color, radius = w * r, center = point(x, y), style = paint)
    fun path(points: List<Offset>) {
        val value = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(value, color, style = paint)
    }

    when (glyph) {
        AppGlyph.MENU -> {
            line(0.16f, 0.27f, 0.84f, 0.27f)
            line(0.16f, 0.5f, 0.84f, 0.5f)
            line(0.16f, 0.73f, 0.84f, 0.73f)
        }

        AppGlyph.CHAT -> {
            drawRoundRect(
                color = color,
                topLeft = point(0.14f, 0.18f),
                size = Size(w * 0.72f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.12f),
                style = paint,
            )
            path(listOf(point(0.34f, 0.74f), point(0.29f, 0.9f), point(0.48f, 0.74f)))
        }

        AppGlyph.SEARCH -> {
            circle(0.44f, 0.44f, 0.27f)
            line(0.64f, 0.64f, 0.86f, 0.86f)
        }

        AppGlyph.SUN -> {
            circle(0.5f, 0.5f, 0.2f)
            // Eight rays, round-capped, on a separate paint in Flutter too: `_GlyphPainter` builds
            // `rayPaint` because the round cap would otherwise apply to the body of the glyph.
            val center = point(0.5f, 0.5f)
            val radius = size.minDimension
            for (index in 0 until 8) {
                val angle = (PI.toFloat() * 2 * index / 8) - (PI.toFloat() / 2)
                val direction = Offset(cos(angle), sin(angle))
                drawLine(
                    color = color,
                    start = center + direction * (radius * 0.33f),
                    end = center + direction * (radius * 0.44f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        AppGlyph.MOON -> {
            val moon = Path().apply {
                moveTo(w * 0.69f, h * 0.16f)
                cubicTo(w * 0.42f, h * 0.18f, w * 0.22f, h * 0.39f, w * 0.22f, h * 0.62f)
                cubicTo(w * 0.22f, h * 0.84f, w * 0.43f, h * 0.91f, w * 0.63f, h * 0.83f)
                cubicTo(w * 0.75f, h * 0.78f, w * 0.83f, h * 0.68f, w * 0.86f, h * 0.57f)
                cubicTo(w * 0.75f, h * 0.65f, w * 0.59f, h * 0.66f, w * 0.48f, h * 0.56f)
                cubicTo(w * 0.35f, h * 0.43f, w * 0.43f, h * 0.23f, w * 0.69f, h * 0.16f)
                close()
            }
            // Filled rather than stroked: the crescent is a solid shape in the Flutter painter too.
            drawPath(moon, color, style = Fill)
        }

        AppGlyph.COPY -> {
            rect(0.32f, 0.14f, 0.48f, 0.58f)
            rect(0.18f, 0.28f, 0.48f, 0.58f)
        }

        AppGlyph.BOOK -> {
            path(
                listOf(
                    point(0.12f, 0.2f),
                    point(0.47f, 0.13f),
                    point(0.47f, 0.83f),
                    point(0.12f, 0.9f),
                    point(0.12f, 0.2f),
                ),
            )
            path(
                listOf(
                    point(0.88f, 0.2f),
                    point(0.53f, 0.13f),
                    point(0.53f, 0.83f),
                    point(0.88f, 0.9f),
                    point(0.88f, 0.2f),
                ),
            )
        }

        AppGlyph.CLOSE -> {
            line(0.24f, 0.24f, 0.76f, 0.76f)
            line(0.76f, 0.24f, 0.24f, 0.76f)
        }

        AppGlyph.CHEVRON_DOWN -> path(listOf(point(0.2f, 0.36f), point(0.5f, 0.66f), point(0.8f, 0.36f)))

        AppGlyph.CHEVRON_RIGHT -> path(listOf(point(0.36f, 0.2f), point(0.66f, 0.5f), point(0.36f, 0.8f)))

        AppGlyph.BACK -> {
            line(0.84f, 0.5f, 0.17f, 0.5f)
            path(listOf(point(0.42f, 0.2f), point(0.17f, 0.5f), point(0.42f, 0.8f)))
        }

        AppGlyph.FORWARD -> {
            line(0.16f, 0.5f, 0.83f, 0.5f)
            path(listOf(point(0.58f, 0.2f), point(0.83f, 0.5f), point(0.58f, 0.8f)))
        }

        AppGlyph.SETTINGS -> {
            circle(0.5f, 0.5f, 0.12f)
            circle(0.5f, 0.5f, 0.29f)
            line(0.5f, 0.1f, 0.5f, 0.22f)
            line(0.5f, 0.78f, 0.5f, 0.9f)
            line(0.1f, 0.5f, 0.22f, 0.5f)
            line(0.78f, 0.5f, 0.9f, 0.5f)
        }

        AppGlyph.DELETE -> {
            line(0.18f, 0.25f, 0.82f, 0.25f)
            line(0.4f, 0.14f, 0.6f, 0.14f)
            rect(0.26f, 0.25f, 0.48f, 0.61f)
            line(0.42f, 0.4f, 0.42f, 0.72f)
            line(0.58f, 0.4f, 0.58f, 0.72f)
        }

        AppGlyph.CLOUD -> path(
            listOf(
                point(0.18f, 0.7f),
                point(0.18f, 0.57f),
                point(0.29f, 0.47f),
                point(0.42f, 0.47f),
                point(0.5f, 0.31f),
                point(0.67f, 0.31f),
                point(0.78f, 0.45f),
                point(0.84f, 0.46f),
                point(0.9f, 0.57f),
                point(0.9f, 0.7f),
                point(0.18f, 0.7f),
            ),
        )

        AppGlyph.CLOUD_OFF -> {
            path(
                listOf(
                    point(0.18f, 0.7f),
                    point(0.18f, 0.57f),
                    point(0.29f, 0.47f),
                    point(0.42f, 0.47f),
                    point(0.5f, 0.31f),
                    point(0.67f, 0.31f),
                    point(0.78f, 0.45f),
                    point(0.84f, 0.46f),
                    point(0.9f, 0.57f),
                    point(0.9f, 0.7f),
                    point(0.18f, 0.7f),
                ),
            )
            line(0.14f, 0.14f, 0.86f, 0.86f)
        }

        AppGlyph.MEMORY -> {
            rect(0.25f, 0.25f, 0.5f, 0.5f)
            circle(0.5f, 0.5f, 0.1f)
            for (value in floatArrayOf(0.18f, 0.42f, 0.66f)) {
                line(value, 0.1f, value, 0.25f)
                line(value, 0.75f, value, 0.9f)
                line(0.1f, value, 0.25f, value)
                line(0.75f, value, 0.9f, value)
            }
        }

        AppGlyph.CHECK -> path(listOf(point(0.18f, 0.52f), point(0.41f, 0.75f), point(0.84f, 0.24f)))

        AppGlyph.LOGIN -> {
            rect(0.14f, 0.15f, 0.42f, 0.7f)
            line(0.43f, 0.5f, 0.88f, 0.5f)
            path(listOf(point(0.66f, 0.28f), point(0.88f, 0.5f), point(0.66f, 0.72f)))
        }

        AppGlyph.REFRESH -> {
            drawArc(
                color = color,
                startAngle = -0.9f,
                sweepAngle = (PI.toFloat() * 1.54f),
                useCenter = false,
                topLeft = point(0.18f, 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                style = paint,
            )
            path(listOf(point(0.79f, 0.18f), point(0.84f, 0.44f), point(0.59f, 0.35f)))
        }

        AppGlyph.SEND -> {
            path(
                listOf(
                    point(0.15f, 0.16f),
                    point(0.87f, 0.5f),
                    point(0.15f, 0.84f),
                    point(0.3f, 0.5f),
                    point(0.15f, 0.16f),
                ),
            )
            line(0.3f, 0.5f, 0.64f, 0.5f)
        }

        AppGlyph.STOP -> rect(0.25f, 0.25f, 0.5f, 0.5f)

        AppGlyph.VERIFIED -> {
            circle(0.5f, 0.5f, 0.34f)
            path(listOf(point(0.31f, 0.52f), point(0.45f, 0.66f), point(0.7f, 0.38f)))
        }
    }
}
