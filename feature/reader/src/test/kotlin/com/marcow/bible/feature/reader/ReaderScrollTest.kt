package com.marcow.bible.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The arithmetic behind the scroll position: a ratio instead of Flutter's pixel offset.
 *
 * The offset could not be checked without a laid-out scroll view, which is exactly why it is being
 * replaced. These cases are the ones a ratio can get wrong on its own — the top of a chapter, the
 * bottom of one, a chapter shorter than the viewport, and an offset imported from a screen that is
 * no longer the screen.
 */
class ReaderScrollTest {
    @Test
    fun `the top of a chapter is zero`() {
        assertEquals(
            0f,
            scrollRatioFor(
                firstVisibleIndex = 0,
                firstVisibleOffset = 0f,
                viewportHeight = 800f,
                itemCount = 31,
            ),
        )
    }

    @Test
    fun `a partly scrolled verse counts as part of the way into the chapter`() {
        // A third of a viewport into the first of thirty-one verses.
        val ratio = scrollRatioFor(
            firstVisibleIndex = 0,
            firstVisibleOffset = 260f,
            viewportHeight = 780f,
            itemCount = 31,
        )

        assertEquals((0 + 260f / 780f) / 31, ratio, TOLERANCE)
    }

    @Test
    fun `the last verse is not the whole chapter`() {
        // The end of the chapter is reached partway into the last item: the reader has to be able to
        // scroll further than "all the verses are on screen", which is what the footer's offsets buy.
        val ratio = scrollRatioFor(
            firstVisibleIndex = 30,
            firstVisibleOffset = 0f,
            viewportHeight = 800f,
            itemCount = 31,
        )

        assertEquals(30f / 31, ratio, TOLERANCE)
    }

    @Test
    fun `scrolling past the end of a chapter is still the end of it`() {
        val ratio = scrollRatioFor(
            firstVisibleIndex = 99,
            firstVisibleOffset = 900f,
            viewportHeight = 800f,
            itemCount = 31,
        )

        assertEquals(1f, ratio)
    }

    @Test
    fun `a chapter with nothing in it has no position to restore`() {
        assertEquals(
            0f,
            scrollRatioFor(
                firstVisibleIndex = 0,
                firstVisibleOffset = 0f,
                viewportHeight = 800f,
                itemCount = 0,
            ),
        )
    }

    @Test
    fun `an unmeasured viewport leaves the position at the top`() {
        // The first layout pass has a height of zero; a ratio divided by it would be infinity.
        val ratio = scrollRatioFor(firstVisibleIndex = 4, firstVisibleOffset = 0f, viewportHeight = 0f, itemCount = 31)

        assertEquals(4f / 31, ratio, TOLERANCE)
    }

    @Test
    fun `a ratio restores the item it was taken from`() {
        assertEquals(15, scrollItemForRatio(ratio = 0.5f, itemCount = 31))
        assertEquals(0, scrollItemForRatio(ratio = 0f, itemCount = 31))
        assertEquals(30, scrollItemForRatio(ratio = 1f, itemCount = 31))
    }

    @Test
    fun `a ratio outside the chapter is pulled back to one of its items`() {
        assertEquals(0, scrollItemForRatio(ratio = -2f, itemCount = 31))
        assertEquals(30, scrollItemForRatio(ratio = 4f, itemCount = 31))
        assertEquals(0, scrollItemForRatio(ratio = 0.5f, itemCount = 0))
    }

    @Test
    fun `an imported pixel offset becomes the ratio of the chapter as it is laid out now`() {
        assertEquals(0.5f, legacyScrollRatio(pixels = 400.0, maxScrollPx = 800f))
    }

    @Test
    fun `an imported offset from a longer chapter lands on the last verse`() {
        // The Flutter chapter was taller on the screen that measured it — a rotation, a smaller
        // font, a different device. Clamping puts the reader at the end rather than nowhere.
        assertEquals(1f, legacyScrollRatio(pixels = 9_000.0, maxScrollPx = 800f))
    }

    @Test
    fun `an imported offset that cannot be resolved yet waits at the top of the chapter`() {
        assertEquals(0f, legacyScrollRatio(pixels = 400.0, maxScrollPx = 0f))
        assertEquals(0f, legacyScrollRatio(pixels = -12.0, maxScrollPx = 800f))
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
