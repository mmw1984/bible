package com.marcow.bible.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The curve the reader's switches run on.
 *
 * Flutter named `Curves.easeOutCubic` in two places in the reader — the chapter title's
 * `AnimatedSwitcher` and `_VerseRow`'s own `AnimatedSize` — and Compose has to be handed the control
 * points rather than the name. What is worth pinning is that both transitions still run on one curve
 * and that it is the easing one: a title arriving more slowly than the verse text beneath it is the
 * kind of difference no test of either transition would otherwise notice.
 */
class ReaderMotionTest {
    @Test
    fun `it starts and ends where every transition has to start and end`() {
        assertEquals(0f, EaseOutCubic.transform(0f), TOLERANCE)
        assertEquals(1f, EaseOutCubic.transform(1f), TOLERANCE)
    }

    @Test
    fun `most of the distance is covered at the start`() {
        // The shape of `easeOutCubic` rather than its four numbers: half way through the duration a
        // text swap is already 87.5% of the way there, which is what makes it read as one movement
        // rather than a wait and a jump.
        assertEquals(0.875f, EaseOutCubic.transform(0.5f), TOLERANCE)
        assertTrue(EaseOutCubic.transform(0.25f) < EaseOutCubic.transform(0.75f))
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
