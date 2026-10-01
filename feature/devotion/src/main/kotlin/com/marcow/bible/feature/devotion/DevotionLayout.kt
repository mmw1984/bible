package com.marcow.bible.feature.devotion

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The devotion screen's measurements, resolved from the two things Flutter resolved them from.
 *
 * `_DevotionPageState.build` (`legacy/flutter/lib/devotion_page.dart:188`) wrote every number as a
 * literal chosen against one of two widths — `wide` at 920 px — or against the window's insets, so
 * they are worked out here in one place instead of being spread through the composables as ternaries.
 * The values are the Flutter ones to the dp; the structure is the reader's, which resolved the same
 * two widths the same way.
 */
data class DevotionLayout(
    /**
     * Left and right padding of the masthead: the oversized title, the controls, the rule and the
     * date chips.
     */
    val horizontal: Dp,
    /** Above the masthead: the status bar, plus Flutter's own gap. */
    val top: Dp,
    /** Height of the title row, which the controls are not part of — the title is bottom-aligned in it. */
    val mastheadHeight: Dp,
    /** The oversized 靈修默想 heading, `Exposure` at a weight of 500. */
    val titleSize: TextUnit,
    /**
     * The further inset every block in the article carries on a wide window.
     *
     * Flutter's `indent`, applied as the left and right of each block's own padding. It is zero on a
     * phone because the article already sits inside its own 20 dp.
     */
    val blockIndent: Dp,
    /** Left and right padding of the article column, which Flutter kept at 20 on every width. */
    val articleHorizontal: Dp,
    /** Above the article: Flutter's `4`, between the date chips and the post's day. */
    val articleTop: Dp,
    /** Below the last block: the navigation bar's clearance, plus Flutter's `28`. */
    val articleBottom: Dp,
)

/**
 * The layout for a window of [screenWidth], with [topInset] above it and [bottomClearance] below.
 *
 * [bottomClearance] is passed in rather than read from the navigation feature, because features do
 * not depend on each other (`NATIVE_PLAN.md` §2.2): the devotion page leaves room for the bar
 * without knowing what draws it. The host gets that number from `feature:navigation` and passes it
 * down, the same way it does for the reader.
 *
 * The 920 dp threshold is Flutter's, read off `MediaQuery.sizeOf(context).width` rather than off the
 * reader's own constraints — so a window wide enough for the reader's larger type also indents the
 * article's blocks.
 */
fun devotionLayout(screenWidth: Dp, topInset: Dp, bottomClearance: Dp): DevotionLayout {
    val wide = screenWidth >= DevotionWideThreshold
    return DevotionLayout(
        horizontal = if (wide) 46.dp else 20.dp,
        top = topInset + if (wide) 8.dp else 14.dp,
        mastheadHeight = if (wide) 94.dp else 104.dp,
        titleSize = if (wide) 68.sp else 45.sp,
        blockIndent = if (wide) 46.dp else 0.dp,
        articleHorizontal = 20.dp,
        articleTop = 4.dp,
        articleBottom = bottomClearance + 28.dp,
    )
}

/** `MediaQuery.sizeOf(context).width >= 920` in `legacy/flutter/lib/devotion_page.dart:201`. */
val DevotionWideThreshold: Dp = 920.dp

/**
 * Every measurement inside the masthead and the article that Flutter chose between two widths or
 * against a window's insets, kept in one place rather than spread through the composables as
 * literals — the same split `ReaderChrome` makes in the reader.
 *
 * [devotionLayout] resolves the ones that depend on the window; everything below is a single number
 * on both widths. Where a figure is a type size rather than a distance it is a `TextUnit`, and where
 * Flutter wrote a `height` multiplier there is one here too, so the line height stays the ratio it
 * was rather than a second size someone could drift apart from the first.
 */
object DevotionChrome {
    /** The title's own line box, which bounds the single line Flutter allowed it. */
    const val TITLE_LINE_HEIGHT: Float = 1.04f

    /** Below the title row, above the controls. */
    val BELOW_TITLE: Dp = 16.dp

    /** The controls row, the rule under it, and the gap under the rule. */
    val CONTROL_GAP: Dp = 9.dp
    val CONTROL_SIZE: Dp = 40.dp
    val CONTROL_GLYPH_SIZE: Dp = 18.dp
    val BELOW_CONTROLS: Dp = 18.dp
    val RULE_THICKNESS: Dp = 1.dp
    val BELOW_RULE: Dp = 14.dp

    /** Today's date in the corner of the controls row, drawn only after a fetch has succeeded. */
    val TODAY_SIZE: TextUnit = 10.sp

    /** The date chips: their height, the gap between them, and the gap under them. */
    val CHIP_HEIGHT: Dp = 40.dp
    val CHIP_GAP: Dp = 7.dp
    val CHIP_HORIZONTAL_PADDING: Dp = 12.dp
    val BELOW_CHIPS: Dp = 10.dp
    val CHIP_SIZE: TextUnit = 11.sp

    /** The post's own day and title, above the blocks. */
    val POST_DATE_BELOW: Dp = 4.dp
    val POST_DATE_SIZE: TextUnit = 11.sp
    val POST_TITLE_SIZE: TextUnit = 21.sp
    const val POST_TITLE_LINE_HEIGHT: Float = 1.5f
    val POST_TITLE_BELOW: Dp = 18.dp

    /**
     * Each block's own padding, as the top and bottom Flutter gave it.
     *
     * The left and right of every one of these is `layout.blockIndent`, which is why it is not here:
     * it is the one number that changes with the window, and `DevotionLayout` holds it.
     */
    val PARAGRAPH_BELOW: Dp = 14.dp
    val PARAGRAPH_SIZE: TextUnit = 16.sp
    const val PARAGRAPH_LINE_HEIGHT: Float = 1.85f

    val HEADING_ABOVE: Dp = 18.dp
    val HEADING_BELOW: Dp = 8.dp
    val HEADING_SIZE: TextUnit = 17.sp
    const val HEADING_LINE_HEIGHT: Float = 1.55f

    val QUOTE_ABOVE: Dp = 6.dp
    val QUOTE_BELOW: Dp = 6.dp
    val QUOTE_PADDING: Dp = 13.dp
    val QUOTE_PADDING_VERTICAL: Dp = 10.dp
    val QUOTE_BAR_WIDTH: Dp = 2.dp
    val QUOTE_SIZE: TextUnit = 15.sp
    const val QUOTE_LINE_HEIGHT: Float = 1.75f

    val IMAGE_ABOVE: Dp = 10.dp
    val IMAGE_BELOW: Dp = 10.dp

    /**
     * The two `<iframe>` blocks, YouTube and SoundCloud, which Flutter padded alike at 10 above and
     * 14 below and drew through its own two players.
     *
     * [MEDIA_HEIGHT] is the SoundCloud frame's: `SizedBox(height: 166)`, written once for the WebView
     * and again for the spinner Flutter drew over it. YouTube is 16:9 of the width instead, so it has
     * no height of its own.
     */
    val MEDIA_ABOVE: Dp = 10.dp
    val MEDIA_BELOW: Dp = 14.dp
    val MEDIA_HEIGHT: Dp = 166.dp

    /**
     * YouTube's thumbnail card, from `_ThumbnailFallback` in `devotion_youtube_player.dart`.
     *
     * The 52 dp circle is Material's play button sitting on a 55%-black disc ringed in white, under
     * a scrim that fades from clear to 45% black so the mark reads over any painting.
     */
    const val VIDEO_ASPECT_RATIO: Float = 16f / 9f
    val VIDEO_PLAY_SIZE: Dp = 52.dp
    val VIDEO_PLAY_GLYPH: Dp = 24.dp
    val VIDEO_PLAY_RING: Dp = 1.6.dp
    const val VIDEO_PLAY_ALPHA: Float = 0.55f
    const val VIDEO_SCRIM_ALPHA: Float = 0.45f

    /**
     * The double-tap seek flash, from `_SeekFlash` in `devotion_youtube_player.dart`.
     *
     * A 64 dp translucent disc carrying the transport glyph and the stacked seconds, set in the
     * opposite corner to the tap that asked for it — the same shape YouTube uses for this, and the same
     * numbers, so a reader who has used the app proper sees the same gesture here.
     */
    val SEEK_FLASH_SIZE: Dp = 64.dp
    val SEEK_FLASH_GLYPH: Dp = 22.dp
    val SEEK_FLASH_GAP: Dp = 2.dp
    val SEEK_FLASH_TEXT_SIZE: TextUnit = 12.sp

    /** The "open in browser" row under the video: 6 above it, 12 px glyph, 5 beside it, 11 px label. */
    val VIDEO_OPEN_ABOVE: Dp = 6.dp
    val VIDEO_OPEN_HORIZONTAL: Dp = 6.dp
    val VIDEO_OPEN_VERTICAL: Dp = 4.dp
    val VIDEO_OPEN_GLYPH: Dp = 12.dp
    val VIDEO_OPEN_GAP: Dp = 5.dp
    val VIDEO_OPEN_SIZE: TextUnit = 11.sp

    /**
     * The card an embed falls back to, from `_ExternalFallback` in `devotion_soundcloud_player.dart`.
     *
     * A bordered panel with the audio-lines mark, the label, the host, and a bordered button. It is
     * what Flutter showed when its WebView could not run — which is every native reader today, since
     * the inline SoundCloud widget is a web page.
     */
    val EMBED_PADDING: Dp = 13.dp
    val EMBED_PADDING_VERTICAL: Dp = 12.dp
    val EMBED_BORDER: Dp = 1.dp
    val EMBED_GLYPH: Dp = 18.dp
    val EMBED_GLYPH_GAP: Dp = 10.dp
    val EMBED_LABEL_SIZE: TextUnit = 13.sp
    val EMBED_HOST_ABOVE: Dp = 2.dp
    val EMBED_HOST_SIZE: TextUnit = 11.sp
    val EMBED_BUTTON_GAP: Dp = 8.dp
    val EMBED_BUTTON_HORIZONTAL: Dp = 10.dp
    val EMBED_BUTTON_VERTICAL: Dp = 6.dp
    val EMBED_BUTTON_GLYPH: Dp = 14.dp

    /**
     * `_ExternalChip`, the black pill in the top-right corner of the SoundCloud frame.
     *
     * A chip of the embed's own rather than the app's: it has to read against whatever the artwork
     * underneath is, so it carries its own fill instead of borrowing `surfaceRaised`.
     */
    val EMBED_CHIP_HORIZONTAL: Dp = 8.dp
    val EMBED_CHIP_VERTICAL: Dp = 4.dp
    val EMBED_CHIP_SIZE: TextUnit = 11.sp

    /** `surfaceRaised` at these alphas: the quote and the embed cards, and the failed image's panel. */
    const val RAISED_FILL_ALPHA: Float = 0.5f
    const val IMAGE_PANEL_ALPHA: Float = 0.4f

    /** The quote's 2 dp bar, drawn in `ink` at half the weight. */
    const val QUOTE_BAR_ALPHA: Float = 0.5f

    val SECTION_ABOVE: Dp = 8.dp
    val SECTION_BELOW: Dp = 4.dp
    val SECTION_BAR_WIDTH: Dp = 3.dp
    val SECTION_BAR_HEIGHT: Dp = 13.dp
    val SECTION_BAR_RADIUS: Dp = 2.dp
    val SECTION_TITLE_GAP: Dp = 8.dp
    val SECTION_TITLE_BELOW: Dp = 6.dp
    val SECTION_TITLE_SIZE: TextUnit = 14.sp
    const val SECTION_TITLE_SPACING: Float = 0.5f

    /** The loading spinner and the line under it, centred in the space an article would take. */
    val LOADING_GAP: Dp = 12.dp
    val LOADING_SIZE: TextUnit = 12.sp
    val SPINNER_SIZE: Dp = 20.dp
    const val SPINNER_MILLIS: Int = 820

    /**
     * The failure screen: the glyph, the two lines of message, and the row of two buttons.
     *
     * The message is the localised one and the detail under it is not — see `DevotionUiState` for why
     * the screen draws both. [FAILURE_DETAIL_LINES] is Flutter's `maxLines: 5`: a long socket error is
     * truncated rather than allowed to push the buttons off the page.
     */
    val FAILURE_GLYPH_SIZE: Dp = 22.dp
    val FAILURE_GLYPH_GAP: Dp = 10.dp
    val FAILURE_SIZE: TextUnit = 12.sp
    val FAILURE_DETAIL_ABOVE: Dp = 8.dp
    val FAILURE_DETAIL_SIZE: TextUnit = 10.sp
    const val FAILURE_DETAIL_LINE_HEIGHT: Float = 1.4f
    const val FAILURE_DETAIL_LINES: Int = 5
    val FAILURE_BUTTONS_ABOVE: Dp = 16.dp
    val FAILURE_BUTTON_GAP: Dp = 10.dp
    val FAILURE_BUTTON_HORIZONTAL: Dp = 18.dp
    val FAILURE_BUTTON_VERTICAL: Dp = 9.dp
    val FAILURE_BUTTON_SIZE: TextUnit = 12.sp

    /**
     * A network image's own placeholder, from `buildDevotionImage` in
     * `legacy/flutter/lib/devotion_image_io.dart`.
     *
     * The blog's paintings are 3:2, and Flutter reserved that shape in all three states rather than
     * only while the bytes were in flight — the column does not jump once they arrive. It is therefore
     * the *frame's* ratio and not the placeholder's: see `DevotionImage` for why the difference is
     * invisible in the Dart and load-bearing here.
     */
    const val IMAGE_ASPECT_RATIO: Float = 3f / 2f
    val IMAGE_GLYPH_SIZE: Dp = 20.dp

    /** The copy confirmation, Flutter's `SnackBar(duration: Duration(seconds: 2))`. */
    const val COPY_FEEDBACK_MILLIS: Int = 2_000
}
