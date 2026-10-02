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
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icons Flutter drew on this screen out of Lucide rather than out of `AppGlyph`.
 *
 * The refresh button, the reader's reload, an image that failed to load, the SoundCloud fallback and
 * the video thumbnail each named a `LucideIcons.*`, and none of them has a shape in the shared
 * `AppGlyph` language — the closest is `AppGlyph.REFRESH`, which is the app's own arc-and-arrowhead,
 * not Lucide's two-arrow cycle. That set lives in `core/design-system`, outside this feature's scope,
 * so these are vendored here instead: the same 24×24 grid, the same 2 px round-capped stroke Lucide
 * draws with, at the sizes the Flutter widgets asked for. [DevotionGlyphView] tints them the way
 * `Icon` tints any vector, so a caller only says which shape and which colour.
 */
internal enum class DevotionGlyph {
    /** `LucideIcons.refreshCw` — the masthead's refresh button. */
    REFRESH_CW,

    /**
     * `LucideIcons.rotateCw` — the web reader's reload button.
     *
     * A separate glyph from [REFRESH_CW] because Lucide draws the two differently: this is one arrow
     * following a three-quarter arc, where `refreshCw` is two arrows following two halves in a closed
     * cycle. The reader's button reloads one page, so it is the one Flutter drew there.
     */
    ROTATE_CW,

    /** `LucideIcons.externalLink` — the SoundCloud card's button and the video's open-in-browser row. */
    EXTERNAL_LINK,

    /** `LucideIcons.audioLines` — the leading mark on the SoundCloud card. */
    AUDIO_LINES,

    /** `Icons.play_arrow`, Material's filled triangle rather than Lucide's — the video's play mark. */
    PLAY,

    /**
     * `Icons.fast_rewind` and `Icons.fast_forward`, on the seek flash.
     *
     * Material's filled double triangles rather than Lucide's stroked ones, like [PLAY], because they
     * sit in white on a 55%-black disc — a stroked mark at that size reads as a smudge.
     */
    REWIND,
    FORWARD,

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
internal fun DevotionGlyphView(glyph: DevotionGlyph, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
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

        DevotionGlyph.ROTATE_CW -> lucidePath(
            "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
            "M21 3v5h-5",
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

        // `Icons.play_arrow` on the 24 grid Flutter drew it on: a filled triangle, and so
        // the one shape here that paints rather than strokes.
        DevotionGlyph.PLAY -> materialFilled(
            "M8 5v14l11-7z",
        )

        // `Icons.fast_rewind`: two filled left-pointing triangles, meeting at x≈11.
        DevotionGlyph.REWIND -> materialFilled(
            "M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z",
        )

        // `Icons.fast_forward`: the same two triangles mirrored about x=12.
        DevotionGlyph.FORWARD -> materialFilled(
            "M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z",
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
 * [addPathNodes] takes the SVG `d` grammar whole and hands back the nodes [addPath] wants, which
 * matters here: `image-off` and `play` are written with elliptical arcs, and re-typing those as line
 * segments is how an icon quietly changes shape. Those two functions are the pair the generated
 * `Icons.kt` uses, so the arcs are read by the same parser the platform icons are. Lucide strokes on
 * 2 px round caps and joins with no fill, so the fill is transparent and the stroke is a black brush
 * [Icon] re-tints.
 */
private fun ImageVector.Builder.lucidePath(vararg paths: String) {
    paths.forEach { pathData ->
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LucideStrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

/**
 * Material's filled icons, which are the opposite of [lucidePath]: paint with no stroke.
 *
 * [PLAY], [REWIND] and [FORWARD] are `Icons.*` rather than `LucideIcons.*`, so Flutter drew them as
 * solid shapes. Reusing the Lucide helper for them would have left the play triangle hollow and the
 * transport marks as outlines — visible at 22 dp on a black disc, which is the one place these three
 * are drawn.
 *
 * The path data is Material's own rather than something drawn to match, because these three are the
 * glyphs a reader has seen in YouTube itself and the eye is very good at them. They are kept as paths
 * instead of pulled in as `material-icons-extended`, which no module here depends on and which would
 * add a library for three marks.
 *
 * The black brush is what [DevotionGlyphView]'s `Icon(tint = …)` re-tints, the same arrangement the
 * Lucide paths use for their stroke.
 */
private fun ImageVector.Builder.materialFilled(vararg paths: String) {
    paths.forEach { pathData ->
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
            stroke = null,
        )
    }
}

/** Lucide's grid: every icon is authored on 24×24 and scaled by the caller. */
private const val LucideGrid = 24

/** Lucide's stroke, in grid units: 2 of 24, a shade finer than `AppGlyph`'s 8.5% and not a visible one. */
private const val LucideStrokeWidth = 2f
