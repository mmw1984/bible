package com.marcow.bible.feature.search

import com.marcow.bible.feature.search.ui.ProgressSegment
import com.marcow.bible.feature.search.ui.ProgressSegments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two travelling segments of `AppProgressLine`, which is the only signal the sheet gives that a
 * search is running at all.
 *
 * `_ProgressLinePainter` (`legacy/flutter/lib/app_ui.dart:841`) draws two rectangles per frame from
 * four numbers and nothing else, so the numbers are the whole of it. Nothing in this repo can render
 * a composable, and the property worth pinning here is that there *are* two segments: the second
 * starting 54% of a cycle behind the first is what makes the line read as travelling rather than as
 * one bar sliding along. Collapse it onto the first and the line still fills the width, still animates
 * and still looks like a working progress bar in a screenshot.
 */
class SearchIndicatorsTest {
    @Test
    fun `the line carries the two segments the Flutter painter drew`() {
        // `_drawSegment(canvas, size, progress, .38, 1)` then `(progress + .54) % 1, .22, .62`.
        assertEquals(
            listOf(
                ProgressSegment(baseWidth = 0.38f, opacity = 1f, offset = 0f),
                ProgressSegment(baseWidth = 0.22f, opacity = 0.62f, offset = 0.54f),
            ),
            ProgressSegments,
        )
    }

    @Test
    fun `the two segments are never at the same point in the cycle`() {
        // The first runs on the raw progress and the second 54% of a cycle behind it, so every frame
        // has two segments in two places. Equal offsets would pass every other test in this file and
        // draw a single bar that pulses.
        val first = ProgressSegments[0]
        val second = ProgressSegments[1]

        FRAMES.forEach { progress ->
            assertNotEquals(first.phase(progress), second.phase(progress), "both segments at $progress")
        }
    }

    @Test
    fun `the second segment wraps a full cycle and the first needs not to`() {
        // Dart wrote `(progress + .54) % 1` by hand and passed `progress` through untouched, so this
        // is where the two calls stop looking alike — and where dropping the `% 1` would send the
        // second segment off the end of the line for the whole pass.
        assertEquals(0.24f, ProgressSegments[1].phase(0.7f), TOLERANCE)
        assertEquals(0.3f, ProgressSegments[0].phase(0.3f), TOLERANCE)
    }

    @Test
    fun `every phase a frame lands on is inside the cycle`() {
        // `sin` and `%` are the two places a phase can go wrong, and both would only ever show up as
        // a segment drawn somewhere it should not be.
        ProgressSegments.forEach { segment ->
            FRAMES.forEach { progress ->
                val phase = segment.phase(progress)

                assertTrue(phase >= 0f, "$phase below the cycle at $progress")
                assertTrue(phase < 1f, "$phase past the cycle at $progress")
            }
        }
    }

    @Test
    fun `a segment is never thinner than its base width, nor fatter than its breath`() {
        // `baseWidth + sin(phase * pi) * .16`, and `sin` over [0, pi] never leaves that band. A sign
        // error or an unwrapped angle would leave the line two static blocks instead of two bars that
        // inhale once per pass.
        ProgressSegments.forEach { segment ->
            FRAMES.forEach { progress ->
                val phase = segment.phase(progress)
                // Only the width is read, so the eased value is whatever the frame happened to be.
                val width = segment.placement(phase, phase).widthFraction

                assertTrue(width >= segment.baseWidth - TOLERANCE, "$width under $segment at $progress")
                assertTrue(
                    width <= segment.baseWidth + BREATH + TOLERANCE,
                    "$width over $segment at $progress",
                )
            }
        }
    }

    @Test
    fun `a segment enters from the left edge and leaves past the right one`() {
        // `-widthFactor + eased * (1 + widthFactor)`: at the start of the eased phase the segment sits
        // entirely off the left, at the end entirely off the right. A segment popping into existence
        // mid-line is the tell that this formula is wrong, and it is invisible in a still.
        val entering = ProgressSegments[0].placement(phase = 0f, easedPhase = 0f)
        val leaving = ProgressSegments[0].placement(phase = 1f, easedPhase = 1f)

        assertEquals(0f, entering.leftFraction + entering.widthFraction, TOLERANCE)
        assertEquals(1f, leaving.leftFraction, TOLERANCE)
    }
}

/** Every frame of one pass of the line, to a thousandth of a cycle. */
private val FRAMES = (0 until 1000).map { it / 1000f }

/** Float phases are summed rather than exact, so widths and wraps are compared to a rounding. */
private const val TOLERANCE = 1e-6f

/** `math.sin(phase * math.pi) * .16`, private to `SearchIndicators.kt`. */
private const val BREATH = 0.16f
