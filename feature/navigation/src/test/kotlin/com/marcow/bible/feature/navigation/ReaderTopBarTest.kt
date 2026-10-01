package com.marcow.bible.feature.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.icons.AppGlyph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
 * The same goes for what the four are called. A bar of glyphs with no words on it says its
 * destinations only through the labels, and those are the one part of a control a diff cannot review:
 * `R.string.search` and `R.string.search_whole_bible` are both plausible on the same line and mean
 * different things, and the two are told apart here by the field the button opens rather than by
 * looking at the button.
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

    @Test
    fun `each control is named by the string Flutter used`() {
        // `legacy/flutter/lib/main.dart:657`, `:664`, `:672` and `:680` — the four `label:`s of
        // Flutter's `Row` over the reader, in the same order.
        assertEquals(R.string.tab_ask, label(ASK))
        assertEquals(R.string.tab_devotion, label(DEVOTION))
        assertEquals(R.string.search_whole_bible, label(SEARCH))
        assertEquals(R.string.settings, label(SETTINGS))
    }

    @Test
    fun `no two controls answer to the same name`() {
        // Nothing on the bar carries a word, so the label is the whole of what a screen reader says
        // about it. Two controls sharing one would be announced the same way and do different things.
        val labels = ReaderTopBarControl.entries.map { label(it) }

        assertEquals(4, labels.distinct().size)
    }

    @Test
    fun `the two shortcuts are named after the tabs they stand in for`() {
        // Ask and Devotions are on the top bar only because the bottom bar is switched off, and they
        // go to the same two destinations — so they say what the tabs say. `AppNavBar` reads the same
        // two resources at `AppNavBar.kt:84` and `:86`, and that is a fact this module cannot assert
        // for itself: `navBarItems` is a composable, for the same reason the labels here are read
        // through a table instead of `stringResource`.
        assertEquals(R.string.tab_ask, label(ASK))
        assertEquals(R.string.tab_devotion, label(DEVOTION))
    }

    @Test
    fun `search says the whole Bible and not the shorter Search`() {
        // The button says what the reader is about to type into: the field the sheet opens is given
        // this same string as its hint (`legacy/flutter/lib/main.dart:2356`), while the sheet's own
        // title is the short one (`SearchSheet.kt:203`). The shorter name is for the sheet, because
        // inside the sheet there is nothing else it could be.
        assertEquals(R.string.search_whole_bible, label(SEARCH))
        assertNotEquals(R.string.search, label(SEARCH))
    }

    @Test
    fun `each control carries the glyph of the tab it duplicates`() {
        // A 19 dp chat bubble and a 19 dp rising sun are the same shape at the size a control is
        // drawn, so this is the one thing that can catch the two being swapped, and the four goldens
        // below are the other.
        assertEquals(AppGlyph.CHAT, glyph(ASK))
        assertEquals(AppGlyph.SUN, glyph(DEVOTION))
        assertEquals(AppGlyph.SEARCH, glyph(SEARCH))
        assertEquals(AppGlyph.SETTINGS, glyph(SETTINGS))
    }

    @Test
    fun `the book button is named for the library it opens`() {
        // `legacy/flutter/lib/main.dart:650`, and the same string as the sidebar's book row at
        // `:1765` — two controls for one destination, so one name. The wide layout drops this button
        // (`!wide`), and the sidebar is there to carry the name instead, which is the only thing in
        // the bar that changes where its one name is written down.
        assertEquals(R.string.select_book, ReaderLibraryButtonLabel)
    }

    private fun controls(showNavBar: Boolean, showDevotion: Boolean): List<ReaderTopBarControl> =
        readerTopBarSlots(showNavBar, showDevotion).map { it.control }

    /** The string [control] is named by, folded in so a test reads the resource rather than the call. */
    private fun label(control: ReaderTopBarControl): Int = readerTopBarLabel(control)

    private fun glyph(control: ReaderTopBarControl): AppGlyph = readerTopBarGlyph(control)

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
        val ASK = ReaderTopBarControl.ASK
    }
}
