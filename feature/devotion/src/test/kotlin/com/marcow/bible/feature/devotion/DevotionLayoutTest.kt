package com.marcow.bible.feature.devotion

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The measurements `_DevotionPageState.build` chose between two widths.
 *
 * The Flutter numbers are all still there — 20 or 46 of masthead padding, 45 or 68 of title, 920 as
 * the line between the two — but they were literals inside one `build` method, where the only way to
 * check them was to look at a screen.
 *
 * What is worth pinning is the pair that has to agree and the two that do not. The masthead padding,
 * the title size and the block indent all switched on the same `wide` at 920 px, so a window that
 * indents the article's blocks is a window that widens the masthead. The article's own 20 dp did not
 * switch, and it is easy to "tidy" that into agreement with the masthead and change every line on a
 * tablet — so it is pinned as deliberately narrow.
 */
class DevotionLayoutTest {
    @Test
    fun `a narrow window uses the narrow masthead`() {
        val layout = devotionLayout(screenWidth = PhoneWidth, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(20.dp, layout.horizontal)
        assertEquals(104.dp, layout.mastheadHeight)
        assertEquals(45.sp, layout.titleSize)
    }

    @Test
    fun `a wide window uses the wide masthead`() {
        val layout = devotionLayout(screenWidth = 1024.dp, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(46.dp, layout.horizontal)
        assertEquals(94.dp, layout.mastheadHeight)
        assertEquals(68.sp, layout.titleSize)
    }

    @Test
    fun `the width that indents the blocks is the width that widens the masthead`() {
        val threshold = devotionLayout(screenWidth = DevotionWideThreshold, topInset = 0.dp, bottomClearance = 0.dp)
        val wider = devotionLayout(screenWidth = DevotionWideThreshold + 1.dp, topInset = 0.dp, bottomClearance = 0.dp)
        val narrow = devotionLayout(screenWidth = DevotionWideThreshold - 1.dp, topInset = 0.dp, bottomClearance = 0.dp)

        assertEquals(threshold, wider)
        assertEquals(46.dp, threshold.blockIndent)
        assertEquals(0.dp, narrow.blockIndent)
        assertTrue(threshold.horizontal > narrow.horizontal)
    }

    @Test
    fun `the article column keeps its own 20 dp on every width`() {
        val narrow = devotionLayout(screenWidth = PhoneWidth, topInset = 0.dp, bottomClearance = 0.dp)
        val wide = devotionLayout(screenWidth = 1024.dp, topInset = 0.dp, bottomClearance = 0.dp)

        // Flutter's article padding was a literal 20 while its masthead was `wide ? 46 : 20`. A wide
        // window therefore indents a block by 46 *inside* a 20 dp column; "fixing" either number to
        // match the other would move every line on a tablet.
        assertEquals(20.dp, narrow.articleHorizontal)
        assertEquals(20.dp, wide.articleHorizontal)
        assertEquals(4.dp, wide.articleTop)
    }

    @Test
    fun `the masthead clears the status bar`() {
        val narrow = devotionLayout(screenWidth = PhoneWidth, topInset = 44.dp, bottomClearance = 102.dp)
        val wide = devotionLayout(screenWidth = 1024.dp, topInset = 44.dp, bottomClearance = 102.dp)

        // 44 + 14 and 44 + 8: Flutter's `MediaQuery.paddingOf(context).top + (wide ? 8 : 14)`.
        assertEquals(58.dp, narrow.top)
        assertEquals(52.dp, wide.top)
    }

    @Test
    fun `the article clears the navigation bar`() {
        val layout = devotionLayout(screenWidth = PhoneWidth, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(130.dp, layout.articleBottom)
    }

    @Test
    fun `the post's own day and title keep the sizes Flutter set them at`() {
        // These never switched on the width, so they are pinned against a drift towards the block
        // sizes rather than against the Flutter source: the post's 21 dp title and its 11 dp day sit
        // either side of the article's 16 dp body.
        assertEquals(11.sp, DevotionChrome.POST_DATE_SIZE)
        assertEquals(21.sp, DevotionChrome.POST_TITLE_SIZE)
        assertEquals(16.sp, DevotionChrome.PARAGRAPH_SIZE)
        assertEquals(11.sp, DevotionChrome.CHIP_SIZE)
    }

    @Test
    fun `a block's line height stays the ratio Flutter wrote rather than a second size`() {
        // Flutter wrote `fontSize: 16, height: 1.85`. Keeping the multiplier is what stops the two
        // numbers from being edited independently.
        assertEquals(1.85f, DevotionChrome.PARAGRAPH_LINE_HEIGHT)
        assertEquals(16.sp * DevotionChrome.PARAGRAPH_LINE_HEIGHT, 29.6.sp)
        assertEquals(1.04f, DevotionChrome.TITLE_LINE_HEIGHT)
    }

    @Test
    fun `the failure detail is bounded so a long error cannot push the buttons off`() {
        assertEquals(5, DevotionChrome.FAILURE_DETAIL_LINES)
        assertTrue(DevotionChrome.FAILURE_DETAIL_SIZE < DevotionChrome.FAILURE_SIZE)
    }

    private companion object {
        /** The width the Flutter widget tests drove the devotion page at. */
        val PhoneWidth = 411.dp
    }
}
