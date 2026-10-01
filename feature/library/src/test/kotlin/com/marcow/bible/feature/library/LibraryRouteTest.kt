package com.marcow.bible.feature.library

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The panel's own entrance, as opposed to [LibraryEntranceTest]'s stagger over the books inside it.
 *
 * Flutter chose these on the pushed route — `transitionDuration` 420, `reverseTransitionDuration`
 * 300, `Offset(-.12, 0)` — and the native sheet has two ways of playing them back: the
 * navigation-compose destination, whose transitions are declared in `LibraryRoute.kt`, and the
 * self-drawn sheet, which drives an `Animatable` over the same curve. Nothing holds the two together
 * but the numbers being written down once, in [LibraryLayout.kt], and that both read the same
 * [libraryPanelSlideOffset] for how far the panel travels.
 *
 * So the assertions below are about the tokens rather than about the animations, which is as much as
 * a plain JVM test can see: a test that rendered the destination would be asserting layoutlib, not
 * Flutter.
 */
class LibraryRouteTest {
    @Test
    fun `the sheet is registered under a route of its own`() {
        // A host navigates to this by name, so the string is part of the route's contract rather than
        // a label: the reader's destination must not be what opens the library.
        assertEquals("library", LibraryDestination)
    }

    @Test
    fun `the panel takes 420 ms to arrive and 300 to leave`() {
        // The two are not symmetric, and that is Flutter's asymmetry: 300 ms of the reader being
        // uncovered is quick enough to feel like a dismissal, while 420 ms of it arriving is slow
        // enough to show where the panel came from. Reversing them would make the panel feel sticky
        // on the way in and abrupt on the way out.
        assertEquals(420, LIBRARY_ARRIVE_MILLIS)
        assertEquals(300, LIBRARY_DISMISS_MILLIS)
    }

    @Test
    fun `the panel is at rest once it has arrived`() {
        assertEquals(0f, libraryPanelSlideOffset(1080f, 1f), Tolerance)
    }

    @Test
    fun `the panel starts a twelfth of its own width to the left`() {
        // `Offset(-.12, 0)`, and a twelfth of the panel rather than of the window, so the books keep
        // their columns while they travel. 360 px is a phone panel; 440 px is the cap on a tablet.
        assertEquals(-43.2f, libraryPanelSlideOffset(360f, 0f), Tolerance)
        assertEquals(-52.8f, libraryPanelSlideOffset(440f, 0f), Tolerance)
    }

    @Test
    fun `the panel is slid the same fraction of any width it is measured against`() {
        // The one place the two arrival paths are allowed to differ is what they measure: the sheet
        // slides within its own width, the destination within the width of the page it crosses. What
        // they must not differ in is the fraction, or the sheet would travel further as a destination
        // than it does drawn over the reader.
        for (step in 0..10) {
            val at = step / 10f
            val panel = libraryPanelSlideOffset(440f, at)
            val page = libraryPanelSlideOffset(1000f, at)
            assertEquals(panel / 440f, page / 1000f, Tolerance)
        }
    }

    @Test
    fun `a half arrived panel is half way through its travel`() {
        assertEquals(-26.4f, libraryPanelSlideOffset(440f, 0.5f), Tolerance)
    }

    @Test
    fun `the destination slides a twelfth of the page, to the left and never to the right`() {
        // The destination's own transition, as opposed to the sheet's: a `NavHost` page is the window
        // width, so on a 1080 px phone the panel starts 129 px off the left edge. 0.12 × 1080 is
        // 129.6, and a translation is whole pixels, so the fraction is floored rather than rounded.
        assertEquals(-129, libraryPageSlideOffset(1080))
        assertEquals(-172, libraryPageSlideOffset(1440))
        assertEquals(IntOffset(-129, 0), offPanelLeft(IntSize(1080, 2280)))
    }

    @Test
    fun `the scrim is 62 percent black`() {
        // `Color(0, 0, 0, .62)` over the reader. Flutter's barrier sat on `Curves.ease` while the page
        // ran on the spring curve, and one progress cannot run two, so this scrim holds its full
        // strength for the whole of the arrival instead.
        assertEquals(0.62f, LIBRARY_SCRIM_ALPHA, Tolerance)
    }

    @Test
    fun `the panel leaves more slowly than it arrives`() {
        // `Curves.easeInOutCubic`, against `SpringCurve`'s cubic of `(0.16, 1, 0.3, 1)`, which is
        // most of the way in by a quarter of the time. The exit is its mirror: a quarter of the way
        // through it the panel has barely moved. Two curves rather than one is the whole of Flutter's
        // asymmetry here, and the scrim is the one thing both are collapsed into.
        assertEquals(0.087f, LibraryDismissCurve.transform(0.25f), Tolerance)
        assertEquals(0.931f, LibraryDismissCurve.transform(0.75f), Tolerance)
    }

    @Test
    fun `the dismiss curve starts and ends at its ends`() {
        assertEquals(0f, LibraryDismissCurve.transform(0f), Tolerance)
        assertEquals(1f, LibraryDismissCurve.transform(1f), Tolerance)
    }
}

/**
 * A hundredth of a pixel: far below anything that could be seen, and far above the float error in
 * `width * .12`. The same tolerance [LibraryLayoutTest] uses for dp, for the same reason.
 */
private const val Tolerance = 0.01f
