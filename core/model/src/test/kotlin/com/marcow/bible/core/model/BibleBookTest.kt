package com.marcow.bible.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The one rule that decides what a book is called, from `_bookName` in `legacy/flutter/lib/main.dart:40`.
 *
 * It is tested here rather than in a feature because it is the sort of thing that has to be said once
 * and then believed by everybody: the reader's title, the library's list and the sidebar all read it,
 * and the reader and the library cannot see each other's tests.
 */
class BibleBookTest {
    @Test
    fun `an English or bilingual reading names its books in English`() {
        assertEquals("Genesis", genesis.displayName(ReadingMode.ENGLISH, usesEnglishUi = false))
        assertEquals("Genesis", genesis.displayName(ReadingMode.BILINGUAL, usesEnglishUi = false))
    }

    @Test
    fun `a Chinese reading follows the interface language`() {
        assertEquals("創世記", genesis.displayName(ReadingMode.CHINESE, usesEnglishUi = false))
        assertEquals("Genesis", genesis.displayName(ReadingMode.CHINESE, usesEnglishUi = true))
    }

    private companion object {
        val genesis = BibleBook("GEN", 1, "創世記", "Genesis", 50, Testament.OLD)
    }
}
