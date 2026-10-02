package com.marcow.bible.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The stagger behind the verses arriving, from `_ScrollAwareEntrance` at `legacy/flutter/lib/main.dart:1624`.
 *
 * These are the only two numbers the entrance has, and both of them are clamps: Flutter stopped the
 * delay's *share* of the duration growing at ten verses and the duration itself at twelve, so a
 * chapter of 176 does not spend eight seconds fading in. A port that "simplified" the two into one
 * proportional ramp would look right on the first three verses and wrong on every chapter anyone
 * actually reads, which is why the caps are pinned here.
 */
class ScrollAwareEntranceTest {
    @Test
    fun `the first verse arrives in 260 ms and does not wait`() {
        assertEquals(260, entranceDurationMillis(staggerIndex = 0))
        assertEquals(0, entranceDelayMillis(staggerIndex = 0))
    }

    @Test
    fun `each of the first twelve verses takes 18 ms longer than the one before`() {
        assertEquals(278, entranceDurationMillis(staggerIndex = 1))
        assertEquals(476, entranceDurationMillis(staggerIndex = 12))
    }

    @Test
    fun `the duration stops growing at the twelfth verse`() {
        // Psalm 119 is 176 verses long. Without the cap the last one would take over three seconds.
        assertEquals(entranceDurationMillis(staggerIndex = 12), entranceDurationMillis(staggerIndex = 175))
    }

    @Test
    fun `a verse waits three and a half percent of its own duration`() {
        // The tenth verse: 440 ms long, 154 ms of it waiting (0.35 * 440, floored).
        assertEquals(440, entranceDurationMillis(staggerIndex = 10))
        assertEquals(154, entranceDelayMillis(staggerIndex = 10))
    }

    @Test
    fun `the wait keeps growing while the duration does, and stops where it stops`() {
        // Flutter clamped the fraction, at the tenth verse, rather than the wait: `Interval`'s begin is
        // a share of the verse's own duration, and the duration carries on to the twelfth. So the
        // eleventh and twelfth each wait a little longer than the tenth, and from the twelfth on — 176
        // verses of Psalm 119 — the wait holds at 166 ms. Reading the cap as a cap on the wait would
        // have pinned every verse from the tenth on to 154 ms, which is not what Flutter did.
        assertEquals(154, entranceDelayMillis(staggerIndex = 10))
        assertEquals(160, entranceDelayMillis(staggerIndex = 11))
        assertEquals(166, entranceDelayMillis(staggerIndex = 12))
        assertEquals(entranceDelayMillis(staggerIndex = 12), entranceDelayMillis(staggerIndex = 175))
    }

    @Test
    fun `a verse always has some of its duration left to fade in`() {
        // The two clamps are independent, so the delay is a fraction of a duration that has itself
        // stopped growing: the longest wait still leaves most of the animation to run.
        (0..200).forEach { index ->
            val delay = entranceDelayMillis(index)
            assertTrue(
                delay < entranceDurationMillis(index),
                "verse $index waits $delay ms of ${entranceDurationMillis(index)} ms",
            )
        }
    }

    @Test
    fun `a negative index is treated as the first verse`() {
        // The verses are numbered from one and the list is keyed on them, so this cannot happen
        // today — but the clamps are what makes it harmless rather than an exception waiting for
        // the first caller that gets an index wrong.
        assertEquals(entranceDurationMillis(staggerIndex = 0), entranceDurationMillis(staggerIndex = -1))
        assertEquals(0, entranceDelayMillis(staggerIndex = -1))
    }
}
