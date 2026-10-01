package com.marcow.bible.feature.reader

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The measurements `_Reader` chose between two widths, and the name a book is read under.
 *
 * The Flutter numbers are all still there — 20 or 46 of padding, 45 or 68 of title, 920 as the line
 * between the two — but they were literals inside one `build` method, where the only way to check
 * them was to look at a screen. What is worth pinning is the pair that has to agree: the width that
 * gets the sidebar also gets the larger verse text.
 */
class ReaderLayoutTest {
    @Test
    fun `a narrow window uses the narrow padding and title`() {
        val layout = readerLayout(screenWidth = PhoneWidth, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(20.dp, layout.horizontal)
        assertEquals(45.sp, layout.titleSize)
        assertEquals(17.sp, layout.verseSize)
        assertEquals(112.dp, layout.headerHeight)
    }

    @Test
    fun `a wide window uses the wide padding and title`() {
        val layout = readerLayout(screenWidth = 1024.dp, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(46.dp, layout.horizontal)
        assertEquals(68.sp, layout.titleSize)
        assertEquals(20.sp, layout.verseSize)
        assertEquals(94.dp, layout.headerHeight)
    }

    @Test
    fun `the width that gets the sidebar is the width that gets the larger verse`() {
        // Both came from `constraints.maxWidth >= 920`; if they ever drift apart, the sidebar and the
        // text beside it stop agreeing about what "wide" means.
        val threshold = readerLayout(screenWidth = WideThreshold, topInset = 0.dp, bottomClearance = 0.dp)
        val wider = readerLayout(screenWidth = WideThreshold + 1.dp, topInset = 0.dp, bottomClearance = 0.dp)
        val narrow = readerLayout(screenWidth = WideThreshold - 1.dp, topInset = 0.dp, bottomClearance = 0.dp)
        val phone = readerLayout(screenWidth = PhoneWidth, topInset = 0.dp, bottomClearance = 0.dp)

        assertEquals(threshold, wider)
        assertTrue(threshold.verseSize > narrow.verseSize)
        assertEquals(narrow.verseSize, phone.verseSize)
    }

    @Test
    fun `the title clears the status bar and the top bar Flutter drew over the reader`() {
        val narrow = readerLayout(screenWidth = PhoneWidth, topInset = 44.dp, bottomClearance = 102.dp)
        val wide = readerLayout(screenWidth = 1024.dp, topInset = 44.dp, bottomClearance = 102.dp)

        assertEquals(152.dp, narrow.top)
        assertEquals(116.dp, wide.top)
    }

    @Test
    fun `the chapter links clear the navigation bar`() {
        val layout = readerLayout(screenWidth = PhoneWidth, topInset = 24.dp, bottomClearance = 102.dp)

        assertEquals(134.dp, layout.bottom)
    }

    @Test
    fun `an English or bilingual reading names its books in English`() {
        assertEquals("Genesis", readerBookName(genesis, ReadingMode.CHINESE, usesEnglishUi = true))
        assertEquals("Genesis", readerBookName(genesis, ReadingMode.ENGLISH, usesEnglishUi = false))
        assertEquals("Genesis", readerBookName(genesis, ReadingMode.BILINGUAL, usesEnglishUi = false))
    }

    @Test
    fun `a Chinese reading follows the interface language`() {
        assertEquals("創世記", readerBookName(genesis, ReadingMode.CHINESE, usesEnglishUi = false))
    }

    private companion object {
        /** The width the Flutter widget tests drove the reader at. */
        val PhoneWidth = 411.dp

        val genesis = BibleBook("GEN", 1, "創世記", "Genesis", 50, Testament.OLD)
    }
}
