package com.marcow.bible.feature.search

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SearchDialogTest {
    /**
     * The order Flutter's `onVerse` had at `legacy/flutter/lib/main.dart:847`.
     *
     * Two separate callbacks that both fire for one tap is as much of a tile's jump as a test without
     * a screen can see, and the order is the part with a bug in it: a host that navigates first has
     * its new destination popped instead of this window.
     */
    @Test
    fun `a verse tap closes the window before it hands the verse over`() {
        val calls = mutableListOf<String>()
        val hit = johnThreeSixteen()
        var jumped: ScriptureHit? = null

        jumpToVerse(dismiss = { calls += "dismiss" }, openVerse = { handed ->
            calls += "${handed.book.id} ${handed.chapter}:${handed.verse.number}"
            jumped = handed
        })(hit)

        assertEquals(listOf("dismiss", "JHN 3:16"), calls)
        // The hit is handed over as it was, so the host reads the chapter and verse the row drew.
        assertSame(hit, jumped)
    }
}

private val John = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)

private fun johnThreeSixteen() = ScriptureHit(
    book = John,
    chapter = 3,
    verse = VersePair(number = 16, zh = "神愛世人", en = "For God so loved"),
)
