package com.marcow.bible.feature.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the reader's top bar is showing, which is the half of it that is a decision rather than a
 * picture.
 *
 * The controls are four identical frosted squares, so the arithmetic below is what a reader actually
 * notices: which destinations are reachable from the top of the reader, and how far apart they sit.
 * Both are wrong in ways that compile — a shortcut left in after the bar came back duplicates a tab
 * it was only ever standing in for; a gap attached to the wrong control leaves two buttons touching
 * the moment a third appears — and neither shows up in a screenshot unless somebody happens to take
 * one of that arrangement.
 *
 * Every arrangement of the two settings is covered, because each is a state the app can be launched
 * into: the bar on with Devotions on, the bar on with Devotions off, and each of those with the bar
 * turned off in Settings.
 */
class ReaderTopBarTest {
    @Test
    fun `the bottom bar on screen leaves the top bar with search and settings`() {
        assertEquals(
            listOf(ReaderTopBarControl.SEARCH, ReaderTopBarControl.SETTINGS),
            controls(showNavBar = true, showDevotion = true),
        )
    }

    @Test
    fun `devotions off changes nothing while the bottom bar is on`() {
        assertEquals(
            controls(showNavBar = true, showDevotion = true),
            controls(showNavBar = true, showDevotion = false),
        )
    }

    @Test
    fun `the shortcuts that stand in for a hidden bar arrive in Flutter's order`() {
        assertEquals(
            listOf(
                ReaderTopBarControl.ASK,
                ReaderTopBarControl.DEVOTION,
                ReaderTopBarControl.SEARCH,
                ReaderTopBarControl.SETTINGS,
            ),
            controls(showNavBar = false, showDevotion = true),
        )
    }

    @Test
    fun `devotions off leaves Ask alone rather than holding the place devotions had`() {
        assertEquals(
            listOf(ReaderTopBarControl.ASK, ReaderTopBarControl.SEARCH, ReaderTopBarControl.SETTINGS),
            controls(showNavBar = false, showDevotion = false),
        )
    }

    @Test
    fun `search carries no gap when there is no shortcut in front of it`() {
        // The gap in front of Search is the one Flutter put *outside* its `!showNavbar` branch, so it
        // is paid by Ask. With no Ask there is nothing to separate, and an unconditional 9 would leave
        // a hole between the book button and the search control.
        assertEquals(0.dp, gapBefore(control = SEARCH, showNavBar = true, showDevotion = true))
        assertEquals(GAP, gapBefore(control = SEARCH, showNavBar = false, showDevotion = true))
    }

    @Test
    fun `the gear keeps its gap in every arrangement of the two settings`() {
        // Settings is the control that has to keep working: it is the way out of the reader on a
        // window too wide for the book button, and the 9 in front of it was unconditional in Flutter.
        assertEquals(GAP, gapBefore(control = SETTINGS, showNavBar = true, showDevotion = true))
        assertEquals(GAP, gapBefore(control = SETTINGS, showNavBar = true, showDevotion = false))
        assertEquals(GAP, gapBefore(control = SETTINGS, showNavBar = false, showDevotion = true))
        assertEquals(GAP, gapBefore(control = SETTINGS, showNavBar = false, showDevotion = false))
    }

    @Test
    fun `devotions is the one control the two settings decide between them`() {
        // Neither setting alone puts it on the bar: `showNavbar` decides whether the shortcuts stand
        // in for a hidden bar at all, and `showDevotion` whether that bar had a Devotions tab in it —
        // so of the four arrangements, exactly one has Devotions on the top bar.
        assertTrue(controls(showNavBar = false, showDevotion = true).contains(DEVOTION))
        assertFalse(controls(showNavBar = false, showDevotion = false).contains(DEVOTION))
        assertFalse(controls(showNavBar = true, showDevotion = true).contains(DEVOTION))
        assertFalse(controls(showNavBar = true, showDevotion = false).contains(DEVOTION))
    }

    private fun controls(showNavBar: Boolean, showDevotion: Boolean): List<ReaderTopBarControl> =
        readerTopBarSlots(showNavBar, showDevotion).map { it.control }

    /**
     * The gap in front of [control], or 0 for a control that is not on the bar at all.
     *
     * Folded in rather than asserted through `firstOrNull` so a control that should be absent fails as
     * the same zero a leading control draws with, instead of as a null.
     */
    private fun gapBefore(control: ReaderTopBarControl, showNavBar: Boolean, showDevotion: Boolean): Dp =
        readerTopBarSlots(showNavBar, showDevotion).firstOrNull { it.control == control }?.gapBefore ?: 0.dp

    private companion object {
        /** The `SizedBox(width: 9)` Flutter put between any two of the trailing controls. */
        val GAP: Dp = 9.dp

        val SEARCH = ReaderTopBarControl.SEARCH
        val SETTINGS = ReaderTopBarControl.SETTINGS
        val DEVOTION = ReaderTopBarControl.DEVOTION
    }
}
