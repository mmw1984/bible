package com.marcow.bible.feature.devotion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The double-tap seek arithmetic, ported from `legacy/flutter/test/youtube_seek_test.dart`.
 *
 * Every case here is a position on the video's own clock, and every one of them was a bug report in
 * a YouTube player at some point: a tap that ran off the end of a live video, a rewind that ran off
 * the start, and stacked taps that each read the pre-tap position instead of the post-tap one.
 */
class DevotionSeekTest {
    @Test
    fun `seeks forward within bounds`() {
        assertEquals(40.0, clampSeekTarget(current = 30.0, total = 300.0, delta = 10.0))
    }

    @Test
    fun `seeks backward within bounds`() {
        assertEquals(20.0, clampSeekTarget(current = 30.0, total = 300.0, delta = -10.0))
    }

    @Test
    fun `clamps at the end of the video`() {
        assertEquals(300.0, clampSeekTarget(current = 295.0, total = 300.0, delta = 10.0))
    }

    @Test
    fun `clamps at the start of the video`() {
        assertEquals(0.0, clampSeekTarget(current = 5.0, total = 300.0, delta = -10.0))
    }

    @Test
    fun `stacked double-taps accumulate then clamp`() {
        var position = 290.0
        repeat(3) {
            position = clampSeekTarget(current = position, total = 300.0, delta = 10.0)
        }

        assertEquals(300.0, position)
    }

    @Test
    fun `unknown duration yields 0 instead of NaN`() {
        assertEquals(0.0, clampSeekTarget(current = 10.0, total = 0.0, delta = 10.0))
    }
}
