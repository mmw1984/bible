package com.marcow.bible.feature.reader

/**
 * How far down the chapter a scroll is, as a ratio, mirroring what Flutter stored as an absolute
 * pixel offset.
 *
 * Flutter kept `reader_scroll_GEN-1` = pixels and restored it by jumping to that offset, which is
 * only meaningful on the screen that wrote it: a different viewport height, a different font scale
 * or a rotation lands the reader somewhere else entirely. A ratio is the same information without
 * the screen in it, which is what `reading_progress.scroll_ratio` holds.
 *
 * The unit is the chapter's own item count rather than its pixel extent, because that is the number
 * a `LazyColumn` already knows: [firstVisibleIndex] is the item at the top of the viewport and
 * [firstVisibleOffset] how far into it the viewport has scrolled. One verse therefore moves the
 * ratio by the same amount whether it is two lines or fourteen, which is what a reader means by
 * "further down".
 */
fun scrollRatioFor(firstVisibleIndex: Int, firstVisibleOffset: Float, viewportHeight: Float, itemCount: Int): Float {
    if (itemCount <= 0) return 0f
    val withinItem = if (viewportHeight > 0f) (firstVisibleOffset / viewportHeight).coerceIn(0f, 1f) else 0f
    val visible = firstVisibleIndex.coerceAtLeast(0) + withinItem
    return (visible / itemCount).coerceIn(0f, 1f)
}

/**
 * The item a [scrollRatioFor] ratio was taken from, for restoring it.
 *
 * Truncation rather than rounding, because rounding sends a ratio taken at the very end of a
 * chapter to one item past the end and clamping it back would land the reader on the last verse
 * rather than at the bottom of the page.
 */
fun scrollItemForRatio(ratio: Float, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return (ratio.coerceIn(0f, 1f) * itemCount).toInt().coerceIn(0, itemCount - 1)
}

/**
 * The Flutter pixel offset turned into the ratio that replaces it, which is the one conversion
 * `LegacyPrefsImporter` parks `legacy_scroll_px` for.
 *
 * Flutter's offset cannot be resolved without the viewport it was measured against, so it is divided
 * by the extent of the chapter as it is *now* laid out. That is an approximation and is deliberately
 * clamped: an offset past the end of a chapter that is shorter on this screen lands on the last
 * verse rather than nowhere, and an offset from a chapter that has not been measured yet lands on the
 * top. The pending pixel offset is cleared the moment it is consumed, so it is never applied twice.
 */
fun legacyScrollRatio(pixels: Double, maxScrollPx: Float): Float {
    if (pixels <= 0.0 || maxScrollPx <= 0f) return 0f
    return (pixels / maxScrollPx).coerceIn(0.0, 1.0).toFloat()
}
