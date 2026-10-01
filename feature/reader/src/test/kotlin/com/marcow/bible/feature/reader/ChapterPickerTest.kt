package com.marcow.bible.feature.reader

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Where the chapter picker sits, which is the only part of it that can be checked without a golden.
 *
 * Flutter computed all four of these numbers inside the `pageBuilder` of a `showGeneralDialog`, with
 * no way at to test them except by pumping a dialog at a chosen window size — so nothing pinned them,
 * and the bubble's position was the one thing in the picker that could not drift silently. They are
 * worth keeping as functions: the arithmetic is three lines of `min` and `clamp` each, and the parity
 * is exact.
 */
class ChapterPickerTest {
    @Test
    fun `the bubble is 330 wide, or the window less its margins`() {
        assertEquals(330.dp, chapterPickerWidth(PhoneWidth))
        assertEquals(330.dp, chapterPickerWidth(1024.dp))
        assertEquals(268.dp, chapterPickerWidth(300.dp))
        assertEquals(218.dp, chapterPickerWidth(250.dp))
    }

    @Test
    fun `the gap to the right edge is 16 when the anchor is against it and 28 otherwise`() {
        // A control hard against the right edge would push the bubble off-screen at 28, so Flutter
        // clamped the gap the other way too: never closer than 16, never further than 28.
        assertEquals(16.dp, chapterPickerRightMargin(anchorRight = PhoneWidth, windowWidth = PhoneWidth))
        assertEquals(16.dp, chapterPickerRightMargin(anchorRight = 399.dp, windowWidth = PhoneWidth))
        assertEquals(28.dp, chapterPickerRightMargin(anchorRight = 383.dp, windowWidth = PhoneWidth))
        assertEquals(28.dp, chapterPickerRightMargin(anchorRight = 20.dp, windowWidth = PhoneWidth))
    }

    @Test
    fun `an anchor that is not against the right edge keeps its exact gap`() {
        assertEquals(100.dp, chapterPickerRightMargin(anchorRight = 311.dp, windowWidth = PhoneWidth))
    }

    @Test
    fun `the bubble hangs ten below the anchor`() {
        assertEquals(90.dp, chapterPickerTop(anchorBottom = 80.dp, windowHeight = 844.dp))
    }

    @Test
    fun `a chapter button low in the window cannot drag the bubble past a third of the way down`() {
        // 844 * .36 = 303.84, so an anchor at 600 would put the bubble at 610 without the cap.
        val top = chapterPickerTop(anchorBottom = 600.dp, windowHeight = 844.dp)

        assertEquals(303.84f, top.value, Tolerance)
    }

    @Test
    fun `the bubble is 400 tall, or whatever the window below it has left`() {
        // A phone: 844 - 90 - the 24 dp navigation bar - 16 leaves 714, so the 400 cap wins.
        assertEquals(400.dp, chapterPickerMaxHeight(top = 90.dp, windowHeight = 844.dp, bottomInset = 24.dp))
        // A short window, where the cap does not.
        assertEquals(284.dp, chapterPickerMaxHeight(top = 200.dp, windowHeight = 520.dp, bottomInset = 20.dp))
    }

    @Test
    fun `the bubble never runs past the left edge, whatever the window and the anchor`() {
        for (windowWidth in listOf(240.dp, 320.dp, 411.dp, 600.dp, 1024.dp)) {
            for (anchorRight in listOf(0.dp, windowWidth / 3, windowWidth / 2, windowWidth)) {
                val right = chapterPickerRightMargin(anchorRight, windowWidth)
                val left = windowWidth - right - chapterPickerWidth(windowWidth)

                assertTrue(left >= 0.dp, "left edge $left on a $windowWidth window with the anchor at $anchorRight")
            }
        }
    }

    @Test
    fun `the bubble always leaves the navigation bar and a margin below it`() {
        for (windowHeight in listOf(360.dp, 640.dp, 844.dp, 1024.dp)) {
            for (anchorBottom in listOf(0.dp, 40.dp, windowHeight / 2, windowHeight)) {
                val top = chapterPickerTop(anchorBottom, windowHeight)
                val maxHeight = chapterPickerMaxHeight(top, windowHeight, BottomInset)

                assertTrue(
                    top + maxHeight <= windowHeight - BottomInset,
                    "$maxHeight below $top overruns a $windowHeight window with the anchor at $anchorBottom",
                )
            }
        }
    }

    private companion object {
        /** The width the Flutter widget tests drove the reader at. */
        val PhoneWidth = 411.dp

        val BottomInset = 24.dp

        /** `Dp` is a float, so a product is compared with a tolerance rather than exactly. */
        const val Tolerance = 0.01f
    }
}
