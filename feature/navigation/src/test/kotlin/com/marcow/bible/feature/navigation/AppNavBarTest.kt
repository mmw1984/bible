package com.marcow.bible.feature.navigation

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppNavBarTest {
    @Test
    fun `the bar clears its own height plus the system inset`() {
        assertEquals(102.dp, appNavBarClearance(showNavbar = true, bottomInset = 32.dp))
    }

    @Test
    fun `hiding the bar leaves the breathing gap Flutter left`() {
        assertEquals(48.dp, appNavBarClearance(showNavbar = false, bottomInset = 32.dp))
    }

    @Test
    fun `three tabs take the full pill width`() {
        assertEquals(260.dp, navBarPillWidth(availableWidth = 400.dp, itemCount = 3))
    }

    @Test
    fun `the pill never grows past the space it was given`() {
        assertEquals(200.dp, navBarPillWidth(availableWidth = 200.dp, itemCount = 3))
    }

    @Test
    fun `hiding devotions shrinks the pill instead of stretching the remaining tabs`() {
        assertEquals(173.33f, navBarPillWidth(availableWidth = 400.dp, itemCount = 2).value, 0.01f)
    }

    @Test
    fun `a pill narrower than the minimum is widened to it`() {
        assertEquals(120.dp, navBarPillWidth(availableWidth = 60.dp, itemCount = 1))
    }

    @Test
    fun `an empty bar has no width to lay out`() {
        assertEquals(0.dp, navBarPillWidth(availableWidth = 400.dp, itemCount = 0))
        assertEquals(0.dp, navBarItemWidth(totalWidth = 260.dp, itemCount = 0))
    }

    @Test
    fun `tab width leaves the pill's own padding out of the split`() {
        assertEquals(84.dp, navBarItemWidth(totalWidth = 260.dp, itemCount = 3))
    }

    @Test
    fun `a tab reads fully selected under the indicator and not at all a tab away`() {
        assertEquals(1f, navBarSelectedness(indicatorPosition = 1f, index = 1))
        assertEquals(0f, navBarSelectedness(indicatorPosition = 1f, index = 0))
    }

    @Test
    fun `a tab half way reads half selected, which is what makes a drag fade the labels`() {
        assertEquals(0.5f, navBarSelectedness(indicatorPosition = 0.5f, index = 0))
        assertEquals(0.5f, navBarSelectedness(indicatorPosition = 1.5f, index = 1))
    }

    @Test
    fun `a tap picks the tab it landed in`() {
        assertEquals(0, navBarIndexForTap(localX = 40f, itemWidth = 84f, itemCount = 3))
        assertEquals(1, navBarIndexForTap(localX = 130f, itemWidth = 84f, itemCount = 3))
        assertEquals(2, navBarIndexForTap(localX = 240f, itemWidth = 84f, itemCount = 3))
    }

    @Test
    fun `a tap past either end still lands on a tab`() {
        assertEquals(0, navBarIndexForTap(localX = -50f, itemWidth = 84f, itemCount = 3))
        assertEquals(2, navBarIndexForTap(localX = 900f, itemWidth = 84f, itemCount = 3))
    }

    @Test
    fun `a drag past the end pins the indicator to the last tab`() {
        assertEquals(2f, navBarOffsetForDrag(0f, deltaX = 5000f, itemWidth = 84f, itemCount = 3))
        assertEquals(0f, navBarOffsetForDrag(1f, deltaX = -5000f, itemWidth = 84f, itemCount = 3))
    }

    @Test
    fun `a released drag settles on the nearest tab`() {
        assertEquals(1, navBarIndexForRelease(indicatorPosition = 0.4f, itemCount = 3))
        assertEquals(1, navBarIndexForRelease(indicatorPosition = 0.6f, itemCount = 3))
        assertEquals(2, navBarIndexForRelease(indicatorPosition = 1.75f, itemCount = 3))
    }

    @Test
    fun `the indicator sits inside the pill's padding at either end`() {
        assertEquals(8f, navBarIndicatorOffset(indicatorPosition = 0f, itemWidth = 84f))
        assertEquals(92f, navBarIndicatorOffset(indicatorPosition = 1f, itemWidth = 84f))
    }
}
