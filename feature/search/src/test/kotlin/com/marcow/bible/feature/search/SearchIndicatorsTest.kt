package com.marcow.bible.feature.search

import com.marcow.bible.feature.search.ui.ProgressSegment
import com.marcow.bible.feature.search.ui.ProgressSegments
import com.marcow.bible.feature.search.ui.SpinnerArc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

/**
 * The two indicators the sheet draws while a search is out: the hairline that says *something* is
 * running, and the arc the two AI loading rows each turn.
 *
 * The line is `_ProgressLinePainter`'s two rectangles (`legacy/flutter/lib/app_ui.dart:816`), drawn
 * per frame from four numbers and nothing else, so the numbers are the whole of it. Nothing in this
 * repo can render a composable, and the property worth pinning there is that there *are* two
 * segments: the second starting 54% of a cycle behind the first is what makes the line read as
 * travelling rather than as one bar sliding along. Collapse it onto the first and the line still
 * fills the width, still animates and still looks like a working progress bar in a screenshot.
 *
 * The arc is `_SpinnerPainter`'s one `drawArc` (`legacy/flutter/lib/app_ui.dart:884`), and it matters
 * for the opposite reason: it is the *only* thing an `AiSearchLoading` row draws besides its label,
 * and a spinner that is the wrong thickness still spins. The two sizes that reach it are the 20 dp
 * default and the 15 dp the loading rows ask for, and they sit on either side of the point where the
 * stroke stops following the widget.
 */
class SearchIndicatorsTest {
    @Test
    fun `the spinner draws the one arc the Flutter painter drew`() {
        // `canvas.drawArc((Offset.zero & size).deflate(stroke / 2), -.9, math.pi * 1.35, false, …)`:
        // an arc that starts just short of the top and sweeps a little under half a turn, so the gap
        // it leaves is the part that makes the rotation legible.
        val arc = SpinnerArc.forSide(side = 20f)

        assertEquals(SPINNER_START_RADIANS, arc.startRadians, TOLERANCE)
        assertEquals(SPINNER_SWEEP_RADIANS, arc.sweepRadians, TOLERANCE)
    }

    @Test
    fun `the sweep is short of a full turn, which is what leaves the gap`() {
        // `1.35 * pi` against a full turn: a sweep of a full turn or more closes on itself and the
        // arc reads as a ring that is spinning rather than as a hand that is, and a sweep of half
        // or less stops reading as a spinner at all.
        val sweep = SpinnerArc.forSide(side = 20f).sweepRadians

        assertTrue(sweep < FULL_TURN_RADIANS, "a full circle leaves no gap: $sweep")
        assertTrue(sweep > HALF_TURN_RADIANS, "too little arc to read as a spinner: $sweep")
    }

    @Test
    fun `a spinner the size the loading rows ask for is drawn at the ratio, not the floor`() {
        // `AiSearchLoading` asks for a 15 dp spinner, and 15 * .12 is 1.8 — just over the 1.6 floor,
        // so the smallest spinner the sheet draws is one the ratio is still deciding. A floor read as
        // the ceiling instead would draw it thinner than the 20 dp default beside it.
        val arc = SpinnerArc.forSide(side = 15f)

        assertEquals(1.8f, arc.stroke, TOLERANCE)
        assertNotEquals(MIN_SPINNER_STROKE_DP, arc.stroke)
    }

    @Test
    fun `a spinner below the floor keeps the same thickness`() {
        // `math.max(1.6, size.shortestSide * .12)`: the ratio only takes over above 13.33 dp, so a
        // spinner smaller than that holds the same thickness rather than thinning out. Reading the
        // floor as a multiplier *of* the ratio instead of a floor under it — `side * .12 * 1.6` —
        // would agree on the 20 dp default and draw the loading row's 15 dp spinner at 2.88, a fifth
        // heavier than the spinner beside it.
        assertEquals(MIN_SPINNER_STROKE_DP, SpinnerArc.forSide(side = 13f).stroke, TOLERANCE)
        assertEquals(1.6f, SpinnerArc.forSide(side = 8f).stroke, TOLERANCE)
    }

    @Test
    fun `a bigger spinner is drawn thicker, not just the same arc in a bigger box`() {
        // The default 20 dp against the loading row's 15: if the stroke did not follow the side, the
        // two spinners would be the same weight and the only thing telling them apart would be size.
        assertEquals(2.4f, SpinnerArc.forSide(side = 20f).stroke, TOLERANCE)
        assertTrue(SpinnerArc.forSide(side = 20f).stroke > SpinnerArc.forSide(side = 15f).stroke)
    }

    @Test
    fun `the arc is drawn inside the spinner own bounds`() {
        // `(Offset.zero & size).deflate(stroke / 2)` for the box and `stroke / 2` for the cap: a
        // square cap runs half a stroke past the arc, so the box is deflated by the same amount and
        // the cap's outer corners land on the widget's own edges. A round cap would want no inset at
        // all, and one of a whole stroke would draw the arc half a stroke inside where it belongs.
        SIDES.forEach { side ->
            val arc = SpinnerArc.forSide(side)

            assertEquals(arc.stroke / 2f, arc.inset, TOLERANCE)
            assertEquals(side - arc.stroke, arc.boxWidth(side), TOLERANCE)
        }
    }

    @Test
    fun `the arc box is inside the spinner at every size`() {
        // A box this small comes from a side this small, and a negative one draws nothing at all —
        // there is no exception to notice that by, the spinner just is not there.
        SIDES.forEach { side ->
            assertTrue(SpinnerArc.forSide(side).boxWidth(side) > 0f, "no room for an arc at $side")
        }
    }

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

/** `math.max(1.6, …)` in `_SpinnerPainter.paint`, private to `SearchIndicators.kt`. */
private const val MIN_SPINNER_STROKE_DP = 1.6f

/** `-.9`, the `drawArc` start angle in `_SpinnerPainter.paint`. */
private const val SPINNER_START_RADIANS = -0.9f

/** `math.pi * 1.35`, the `drawArc` sweep in `_SpinnerPainter.paint`. */
private const val SPINNER_SWEEP_RADIANS = 1.35f * PI.toFloat()

/** A full turn, so the sweep above can be said to fall short of it. */
private const val FULL_TURN_RADIANS = 2f * PI.toFloat()

/** A half turn, below which an arc stops reading as a spinner. */
private const val HALF_TURN_RADIANS = PI.toFloat()

/** Every spinner side worth drawing: the 20 dp default, the 15 dp the rows ask for, and below both. */
private val SIDES = listOf(4f, 8f, 13f, 15f, 20f, 48f)
