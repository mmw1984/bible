package com.marcow.bible.feature.reader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The reader's measurements, resolved from the two things Flutter resolved them from.
 *
 * Every number in `_Reader` was a literal chosen against one of two widths — `wide` at 920 px — and
 * against the window's insets, so they are worked out here in one place instead of being spread
 * through the composable as ternaries. The values are the Flutter ones to the dp.
 */
data class ReaderLayout(
    /** Left and right padding of the title, the verses and the chapter links. */
    val horizontal: Dp,
    /** Above the title: the status bar, plus the top bar the Flutter shell drew over the reader. */
    val top: Dp,
    /** Height of the title row, which the chapter control is bottom-aligned against. */
    val headerHeight: Dp,
    /** The chapter heading, `Exposure` at a weight of 500. */
    val titleSize: TextUnit,
    /** The Chinese text of a verse, which is the larger of the two. */
    val verseSize: TextUnit,
    /** Below the chapter links: the navigation bar's clearance, plus the gap Flutter left. */
    val bottom: Dp,
)

/**
 * The layout for a window of [screenWidth], with [topInset] above it and [bottomClearance] below.
 *
 * [bottomClearance] is passed in rather than read from the navigation feature, because features do
 * not depend on each other (`NATIVE_PLAN.md` §2.2): the reader leaves room for the bar without
 * knowing what draws it. The host gets that number from `feature:navigation` and passes it down.
 *
 * The 920 dp threshold is the one Flutter used for both the sidebar and the verse size, so a window
 * that gets the sidebar also gets the larger type.
 */
fun readerLayout(screenWidth: Dp, topInset: Dp, bottomClearance: Dp): ReaderLayout {
    val wide = screenWidth >= WideThreshold
    return ReaderLayout(
        horizontal = if (wide) 46.dp else 20.dp,
        top = topInset + if (wide) 72.dp else 108.dp,
        headerHeight = if (wide) 94.dp else 112.dp,
        titleSize = if (wide) 68.sp else 45.sp,
        verseSize = if (wide) 20.sp else 17.sp,
        bottom = bottomClearance + 32.dp,
    )
}

/** `constraints.maxWidth >= 920` in `legacy/flutter/lib/main.dart:591`. */
val WideThreshold: Dp = 920.dp

/**
 * Every measurement inside the chapter column that Flutter chose between two widths or against a
 * window's insets, kept in one place rather than spread through the composables as literals.
 *
 * Only the numbers that do *not* depend on the window live here. [readerLayout] resolves the ones
 * that do, and the two [verseSize] cases are the only per-width values below it, because the
 * Chinese and English verse sizes are the one pair Flutter resolved from the *window* width rather
 * than from the reader's own constraints.
 *
 * One number Flutter chose is deliberately absent: `CustomScrollView(scrollCacheExtent: 1200 px)`
 * built a chapter a fixed 1200 pixels beyond the viewport. A `LazyColumn` has no pixel budget to set —
 * it keeps whole items, and how many is `beyondBoundsPageCount` — so there is nothing to reproduce and
 * carrying the figure over would be a claim the list could not keep.
 */
object ReaderChrome {
    /** Below the header's rule, above the verses, for the reading-mode control. */
    val modeControlHeight: Dp = 36.dp
    val modeControlWidth: Dp = 180.dp
    val belowModeControl: Dp = 18.dp
    val belowTitle: Dp = 24.dp
    val belowRule: Dp = 18.dp
    val ruleThickness: Dp = 1.dp

    /** The chapter links' own top padding, above the navigation bar's clearance. */
    val aboveChapterLinks: Dp = 28.dp

    /** Inside the chapter control that opens the picker. */
    val chapterControlHorizontal: Dp = 12.dp
    val chapterControlVertical: Dp = 8.dp
    val chapterNumberSize: TextUnit = 23.sp
    const val chapterNumberLineHeight: Float = 1f

    /** Inside a verse: the number, then the text. */
    val verseVerticalPadding: Dp = 18.dp
    val verseNumberSize: TextUnit = 10.sp
    val verseNumberGap: Dp = 6.dp
    val verseGap: Dp = 4.dp
    const val verseLineHeight: Float = 1.9f
    val verseEnglishGap: Dp = 8.dp
    val verseEnglishSize: TextUnit = 14.sp
    const val verseEnglishLineHeight: Float = 1.7f
    val verseOnlyEnglishSize: TextUnit = 17.sp
    const val verseOnlyEnglishLineHeight: Float = 1.78f

    /** The title's own line box, which bounds the two lines Flutter allowed it. */
    const val titleLineHeight: Float = 1.04f

    /** The failing chapter's message, which Flutter centred in the space a chapter would take. */
    val failureVerticalPadding: Dp = 70.dp

    /** One skeleton bar, the gap to the next, and the padding under the last one. */
    val skeletonBarHeight: Dp = 54.dp
    val skeletonBarGap: Dp = 14.dp
    val skeletonBottomPadding: Dp = 24.dp
    const val skeletonBars: Int = 6
    const val skeletonPulseMillis: Int = 1_100
}
