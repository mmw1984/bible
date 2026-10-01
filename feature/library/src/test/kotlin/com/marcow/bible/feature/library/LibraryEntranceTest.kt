package com.marcow.bible.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The books' stagger, from `_ScrollAwareEntrance` at `legacy/flutter/lib/main.dart:1624` — the same
 * numbers `ScrollAwareEntranceTest` pins for the reader's verses, and pinned here for a reason:
 * `feature/reader` and `feature/library` each carry their own copy, because features do not depend on
 * each other (`NATIVE_PLAN.md` §2.2). If one of them is ever retuned, this fails and says which list
 * changed.
 */
class LibraryEntranceTest {
    @Test
    fun `the first book arrives in 260 ms and does not wait`() {
        assertEquals(260, rowEntranceDurationMillis(staggerIndex = 0))
        assertEquals(0, rowEntranceDelayMillis(staggerIndex = 0))
    }

    @Test
    fun `the stagger stops growing at the twelfth book`() {
        // 260 + 12 * 18, and no further: Malachi is 39th in the list and arrives in the same time as
        // the twelfth, or a long testament would take four seconds to stop moving.
        assertEquals(476, rowEntranceDurationMillis(staggerIndex = 12))
        assertEquals(476, rowEntranceDurationMillis(staggerIndex = 38))
    }

    @Test
    fun `the wait stops growing at the tenth book`() {
        // 0.035 of its own duration, and the tenth book's is 440 ms, so 15 ms.
        assertEquals(154, rowEntranceDelayMillis(staggerIndex = 10))
        assertEquals(154, rowEntranceDelayMillis(staggerIndex = 38))
    }

    @Test
    fun `a book always has most of its duration left to fade in`() {
        (0..38).forEach { index ->
            val wait = rowEntranceDelayMillis(index)
            val total = rowEntranceDurationMillis(index)
            assertTrue(wait < total, "book $index waits $wait ms of $total ms")
        }
    }
}
