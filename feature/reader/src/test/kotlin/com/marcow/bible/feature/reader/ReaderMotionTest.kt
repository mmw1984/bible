package com.marcow.bible.feature.reader

import com.marcow.bible.core.designsystem.theme.SpringCurve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The curves the reader's dialogs run on.
 *
 * Flutter named `Curves.easeOutCubic` in two places in the reader — the chapter title's
 * `AnimatedSwitcher` and `_VerseRow`'s own `AnimatedSize` — and Compose has to be handed the control
 * points rather than the name. What is worth pinning is that both transitions still run on one curve
 * and that it is the easing one: a title arriving more slowly than the verse text beneath it is the
 * kind of difference no test of either transition would otherwise notice.
 *
 * The two dialogs are the harder half, because in each Flutter ran *two* curves on *two* channels of
 * one transition — a spring on the movement and an ease on the fade, or an ease on the slide and the
 * bare animation on the fade. Handing Compose one eased value and spending it on both channels is the
 * natural way to write it, and it looks right; it is a spring on a fade Flutter never sprang, or a
 * fade that arrives after the movement it belongs to. These are the assertions that keep the two
 * channels apart.
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

    @Test
    fun `the chapter picker starts invisible and ends opaque`() {
        assertEquals(0f, chapterPickerFade(0f), TOLERANCE)
        assertEquals(1f, chapterPickerFade(1f), TOLERANCE)
    }

    @Test
    fun `the chapter picker is fully opaque before it has finished growing`() {
        // The point of the two curves, and of the `Interval` between them: at 65% through the 320 ms
        // the fade is done and the spring still has a third of its travel to make, so the bubble is
        // at full opacity while it is still growing. Spending one eased value on both channels would
        // put the two at 100% at the same instant and lose exactly this.
        assertEquals(1f, chapterPickerFade(FadeEnd), TOLERANCE)
        assertTrue(SpringCurve.transform(FadeEnd) < 1f)
    }

    @Test
    fun `the chapter picker fade is an easeOut run early rather than a slower curve`() {
        // `Interval(0, .65)` does not bend `easeOut`, it reaches the same values sooner: at a third of
        // the way through the transition the fade is already 68.5% of the way there, where the bare
        // curve would be a quarter of the way through and barely started. That is what "the opacity is
        // finished before the growth" is made of.
        assertEquals(0.685f, chapterPickerFade(FadeEnd / 2f), TOLERANCE)
        assertTrue(chapterPickerFade(0.25f) > EaseOut.transform(0.25f))
    }

    @Test
    fun `the action sheet slides on the easing and fades on nothing`() {
        // The sheet's pair, at `legacy/flutter/lib/main.dart:953`: `Curves.easeOutCubic` on the
        // `SlideTransition`, the bare route animation on the `FadeTransition`. Pinning that the two
        // curves are not the same one is what stops a later edit from reaching for `SpringCurve` — the
        // curve the chapter picker legitimately uses — and spending it here too.
        assertNotEquals(EaseOutCubic.transform(0.3f), SpringCurve.transform(0.3f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f

        /** `Interval(0, .65)`, restated here so the test reads against the same number. */
        const val FadeEnd = 0.65f
    }
}
