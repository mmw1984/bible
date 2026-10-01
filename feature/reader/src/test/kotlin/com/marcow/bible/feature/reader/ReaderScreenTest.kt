package com.marcow.bible.feature.reader

import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.model.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * What the reader's own controls are called, which nothing else in this module holds down.
 *
 * The rest of the reader is well covered, and it is worth saying what by: [ReaderLayoutTest] holds
 * the measurements, [ChapterPickerTest] holds where the bubble goes, [ReaderScrollTest] holds what a
 * ratio means, [ReaderViewModelTest] holds what a tap does to the chapter. All of that is the reader
 * *working*. None of it can see whether the reading a reader picks is offered under its own name, in
 * the order their finger expects, or whether the two links at the foot of every chapter still name
 * the chapters they go to.
 *
 * That is the gap `widget_test.dart:20` closes on the Flutter side — `reader exposes core controls`
 * looks for 創世記, 中文, 英文, 雙語 and the two labelled controls above the reader — and it is a gap
 * that costs nothing to be wrong about: a mode renamed, or two rows swapped, compiles, reads
 * perfectly well in a diff, and is only visible once the screen is on a device.
 *
 * The two controls above the reader belong to `feature/navigation`, which cannot see this module,
 * and the chapter button is held by [ChapterPickerTest]. What is left for the reader itself is the
 * reading control and the two links at the foot of the chapter.
 */
class ReaderScreenTest {
    /**
     * The three readings, in the order a reader's finger crosses them.
     *
     * The order is the part that is easy to lose and impossible to notice: `AppSegmented` puts each
     * choice where it is in the list, so swapping two of them would put the reading someone is in
     * under a different finger every time they came back to it, and the goldens would all still pass
     * because every golden in this module draws the same three cells.
     */
    @Test
    fun `the three readings are named in the order Flutter listed them`() {
        assertEquals(
            listOf(
                ReadingMode.CHINESE to R.string.chinese,
                ReadingMode.ENGLISH to R.string.english,
                ReadingMode.BILINGUAL to R.string.bilingual,
            ),
            readingModeLabels(),
        )
    }

    /**
     * Each reading is named in its own language, not in one string with a mode substituted into it.
     *
     * They are three languages rather than three values, and Flutter's 180 dp control was sized for
     * the longest of them — so a cell that borrowed another's label would be both wrong and the
     * width it was given would stop meaning anything.
     */
    @Test
    fun `each reading is a name of its own`() {
        val labels = readingModeLabels().map { it.second }

        assertEquals(3, labels.distinct().size)
        // The control's own accessibility label, which wraps all three rather than being one of them.
        assertNotEquals(R.string.reading_language, labels[0])
        assertNotEquals(R.string.reading_language, labels[1])
        assertNotEquals(R.string.reading_language, labels[2])
    }

    /**
     * A link names the chapter on the other side of the one being read.
     *
     * Flutter built both labels out of the same two pieces at `legacy/flutter/lib/main.dart:1295`, so
     * a chapter in the middle of a book is named twice over, once each way — and the two are the only
     * statement anywhere that the reader is somewhere rather than nowhere in particular.
     */
    @Test
    fun `the links either side name the chapters they go to`() {
        assertEquals("John 1", chapterLinkLabel(bookName = "John", chapter = 1, exists = true))
        assertEquals("John 3", chapterLinkLabel(bookName = "John", chapter = 3, exists = true))
    }

    /**
     * The first chapter of a book has nothing behind it, which is the dash and not a missing row.
     *
     * Both ends reach this, and they are different ends of the library: John 1 is the first chapter a
     * new reader reaches and Revelation 22 is the last chapter of the last book there is. Flutter kept
     * the glyph, the caption and the padding on the row either way and swapped only the label, so the
     * links do not shift under the reader at either end.
     */
    @Test
    fun `the ends of a book are the dash Flutter showed`() {
        assertEquals("—", chapterLinkLabel(bookName = "John", chapter = 0, exists = false))
        assertEquals("—", chapterLinkLabel(bookName = "Revelation", chapter = 23, exists = false))
    }

    /**
     * A link is named in the reading the reader is in, which is the name the title above it uses.
     *
     * The chapter links are the reader's one piece of chrome that leaves and comes back as they read,
     * so they are read between chapters rather than at one. Flutter named them from the same
     * `_bookName` the title used, and a link that came back in the interface language under a chapter
     * written in the reading would be the one string on the page in the wrong language.
     */
    @Test
    fun `a link is named in the reading the reader is in`() {
        assertEquals("約翰福音 2", chapterLinkLabel(bookName = "約翰福音", chapter = 2, exists = true))
        assertEquals("John 2", chapterLinkLabel(bookName = "John", chapter = 2, exists = true))
    }
}
