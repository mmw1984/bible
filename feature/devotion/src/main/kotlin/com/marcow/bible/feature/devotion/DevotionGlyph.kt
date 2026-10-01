package com.marcow.bible.feature.devotion

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icons Flutter drew on this screen out of Lucide rather than out of `AppGlyph`.
 *
 * The refresh button, an image that failed to load, the SoundCloud fallback and the video thumbnail
 * each named a `LucideIcons.*`, and none of them has a shape in the shared `AppGlyph` language — the
 * closest is `AppGlyph.REFRESH`, which is the app's own arc-and-arrowhead, not Lucide's two-arrow
 * cycle. That set lives in `core/design-system`, outside this feature's scope, so these five are
 * vendored here instead: the same 24×24 grid, the same 2 px round-capped stroke Lucide draws with, at
 * the sizes the Flutter widgets asked for. [DevotionGlyphView] tints them the way `Icon` tints any
 * vector, so a caller only says which shape and which colour.
 */
internal enum class DevotionGlyph {
    /** `LucideIcons.refreshCw` — the masthead's refresh button. */
    REFRESH_CW,

    /** `LucideIcons.externalLink` — the SoundCloud card's button and the video's open-in-browser row. */
    EXTERNAL_LINK,

    /** `LucideIcons.audioLines` — the leading mark on the SoundCloud card. */
    AUDIO_LINES,

    /** `Icons.play_arrow`, Material's filled triangle rather than Lucide's — the video's play mark. */
    PLAY,

    /** `LucideIcons.imageOff` — the 20 px mark on an article image that would not load. */
    IMAGE_OFF,
}

/**
 * Draws [glyph] at [size] in [color].
 *
 * [contentDescription] is `null` because every call site is a decoration inside a control that already
 * names itself: the refresh button and the SoundCloud card are one tap target each, and a failed
 * image is not actionable at all.
 */
@Composable
internal fun DevotionGlyphView(
    glyph: DevotionGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Icon(
        imageVector = remember(glyph) { glyph.imageVector() },
        contentDescription = null,
        tint = color,
        modifier = modifier.size(size),
    )
}

private fun DevotionGlyph.imageVector(): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = LucideGrid.dp,
    defaultHeight = LucideGrid.dp,
    viewportWidth = LucideGrid.toFloat(),
    viewportHeight = LucideGrid.toFloat(),
).apply {
    when (this@imageVector) {
        DevotionGlyph.REFRESH_CW -> lucidePath(
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )

        DevotionGlyph.EXTERNAL_LINK -> lucidePath(
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )

        DevotionGlyph.AUDIO_LINES -> lucidePath(
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3",
        )

        // Material's `Icons.play_arrow` on the 24 grid Flutter drew it on: a filled triangle, and so
        // the one shape here that paints rather than strokes.
        DevotionGlyph.PLAY -> lucidePath(
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )

        DevotionGlyph.IMAGE_OFF -> lucidePath(
            "M2 2 22 22",
            "M10.41 10.41a2 2 0 1 1-2.83-2.83",
            "M13.5 13.5 6 21",
            "M18 18 21 21",
            "M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59",
            "M21 15V5a2 2 0 0 0-2-2H9",
        )
    }
}.build()

/**
 * Adds Lucide's own path data to this vector.
 *
 * `addPathNodes` takes the SVG `d` grammar whole, which matters here: `image-off` and `play` are
 * written with elliptical arcs, and re-typing those as line segments is how an icon quietly changes
 * shape. Lucide strokes on 2 px round caps and joins with no fill, so the fill is transparent and the
 * stroke is a black brush [Icon] re-tints.
 */
private fun ImageVector.Builder.lucidePath(vararg paths: String) {
    paths.forEach { pathData ->
        addPathNodes(
            pathData = pathData,
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LucideStrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

/** Lucide's grid: every icon is authored on 24×24 and scaled by the caller. */
private const val LucideGrid = 24

/** Lucide's stroke, in grid units: 2 of 24, a shade finer than `AppGlyph`'s 8.5% and not a visible one. */
private const val LucideStrokeWidth = 2f
