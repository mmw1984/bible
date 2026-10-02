package com.marcow.bible.feature.devotion

import com.marcow.bible.core.designsystem.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * What the devotion page calls its own seven sentences, which is the only part of it that is a choice
 * rather than a measurement.
 *
 * The rest of this module is well covered, and it is worth saying what by: [DevotionLayoutTest] holds
 * the measurements, [DevotionWebReaderTest] holds the frame's two navigation rules, [DevotionSeekTest]
 * holds where a video resumes, [DevotionViewModelTest] holds what a fetch does to the state, and
 * `DevotionPlainTextTest` holds the exact bytes of a copied article. All of that is the page
 * *working*. None of it can see what the page is called.
 *
 * That is the gap `legacy/flutter/test/widget_test.dart` closed on the Flutter side — `devotion
 * exposes core controls` looked for 靈修默想, 複製全文 and 網頁版 among others — and it is a gap that
 * costs nothing to be wrong about. A label swapped for a neighbouring string compiles, reads perfectly
 * well in a diff, and is visible only once the screen is on a device; a golden would not catch it
 * either, since a golden holds the pixels of one language and says nothing about which resource those
 * pixels came from.
 *
 * What is deliberately not here is the article itself. Its day, title and blocks are the blog's own
 * words, arriving in `DevotionPost`, and `DevotionPlainTextTest` already pins them against the real
 * 觀畫 post.
 */
class DevotionScreenTest {
    /**
     * The masthead's three controls, each named for itself.
     *
     * Three 40 dp squares with a glyph each, laid out in a row: a refresh, a copy and a book. A reader
     * with a screen reader hears only these names, so a label that named two of them at once would
     * leave two controls that announce themselves identically and do different things — and which of
     * the two is the one that throws away a week of reading to fetch one day is not a thing anyone
     * would work out from a shape.
     */
    @Test
    fun `each of the masthead's three controls is named for itself`() {
        val labels = listOf(
            DevotionScreenLabel.REFRESH,
            DevotionScreenLabel.COPY_ARTICLE,
            DevotionScreenLabel.WEB_READER,
        ).map { devotionScreenLabel(it) }

        assertEquals(3, labels.distinct().size)
        assertEquals(
            listOf(R.string.devotion_refresh, R.string.devotion_copy_article, R.string.devotion_open_web_reader),
            labels,
        )
    }

    /**
     * The page's heading is the same word its navigation tab carries, because it is the same page.
     *
     * `tab_devotion` is what `AppNavBar` writes on the tab at
     * `feature/navigation/src/main/kotlin/com/marcow/bible/feature/navigation/AppNavBar.kt:86` and what
     * `ReaderTopBar` writes on the shortcut over the reader at `:122`. Three controls and three places
     * naming one destination is the point: a reader who arrives from the reader's top bar and lands on
     * a page headed something other than the button they pressed has been told two names for one
     * screen.
     */
    @Test
    fun `the heading is the tab's own word`() {
        assertEquals(R.string.tab_devotion, devotionScreenLabel(DevotionScreenLabel.TITLE))
    }

    /**
     * The embedded frame and the system browser are named apart, in both places they appear.
     *
     * `devotion_open_web_reader` is 網頁版 and `devotion_open_in_browser` is 在瀏覽器開啟, both in
     * `values-zh-rTW/strings.xml:112` and `:113`, and the two name different destinations: the frame
     * this page opens over the article, and the browser the frame's own top bar hands out to
     * (`DevotionWebReader.kt:246`). The masthead's third control is the first of those, so borrowing
     * the second's string for it would send a reader out of the app on a tap that looked like it was
     * staying in it.
     */
    @Test
    fun `the embedded reader is not the system browser`() {
        assertNotEquals(R.string.devotion_open_in_browser, devotionScreenLabel(DevotionScreenLabel.WEB_READER))
    }

    /**
     * The failure screen offers the same reader the masthead does, under the same name.
     *
     * [DevotionScreenLabel.WEB_READER] is one entry for the two buttons that open it — the masthead's
     * third and the failure screen's second — and it is one entry because Flutter reached for
     * `devotionOpenWebReader` in both places, at `:284` and `:402`. The second is reached precisely when
     * there is no article to load, which makes it the one button a reader meets at the worst moment, and
     * a version of it that disagreed with the button beside it would be the last thing on the page to
     * tell them what to do.
     */
    @Test
    fun `the failure screen's escape is the same reader the masthead offers`() {
        assertEquals(
            R.string.devotion_open_web_reader,
            devotionScreenLabel(DevotionScreenLabel.WEB_READER),
        )
    }

    /**
     * The failure screen's two buttons are named for what they do, and are not each other's.
     *
     * Retry is the emphasised one (`emphasized: true` at `:383`, held by `FailureButton`), and it is the
     * only control on the page that fetches anything by itself. The web reader is the way out. Two
     * buttons one string between them would leave a reader choosing between two identical words at the
     * moment they least want to read.
     */
    @Test
    fun `retry and the web reader are two different offers`() {
        assertEquals(R.string.devotion_retry, devotionScreenLabel(DevotionScreenLabel.RETRY))
        assertNotEquals(
            devotionScreenLabel(DevotionScreenLabel.RETRY),
            devotionScreenLabel(DevotionScreenLabel.WEB_READER),
        )
    }

    /**
     * The spinner and the failure say opposite things, and neither is the other's word.
     *
     * Flutter set `error = context.l10n.devotionLoadFailed` at `:181` and drew it at `:359`, one below
     * the spinner that says 正在載入靈修… at `:342`. They are the two halves of a fetch the reader is
     * watching, and a failure that announced itself as still loading — or a spinner that announced
     * failure — would tell someone on a train tunnel to keep waiting for something that was never
     * coming.
     */
    @Test
    fun `loading and having failed are different sentences`() {
        assertEquals(R.string.devotion_loading, devotionScreenLabel(DevotionScreenLabel.LOADING))
        assertEquals(R.string.devotion_load_failed, devotionScreenLabel(DevotionScreenLabel.LOAD_FAILED))
        assertNotEquals(
            devotionScreenLabel(DevotionScreenLabel.LOADING),
            devotionScreenLabel(DevotionScreenLabel.LOAD_FAILED),
        )
    }

    /**
     * The seven of them, in the order the page reaches them.
     *
     * Reading order rather than enum order would be the stronger claim, and it is not made here: which
     * body Flutter draws is a `when` over three booleans and no test can see it. What this holds is
     * that the table is a table of seven, none of them forgotten and none of them twice by accident —
     * a sixth string added and never given an entry of its own would compile as a non-exhaustive
     * `when` only if it had been added to the enum, so this is the line that says the screen has seven
     * sentences and not eight.
     */
    @Test
    fun `the page says seven things, and the heading is not one of the controls`() {
        assertEquals(7, DevotionScreenLabel.entries.size)
        assertEquals(
            listOf(
                R.string.tab_devotion,
                R.string.devotion_refresh,
                R.string.devotion_copy_article,
                R.string.devotion_open_web_reader,
                R.string.devotion_loading,
                R.string.devotion_load_failed,
                R.string.devotion_retry,
            ),
            DevotionScreenLabel.entries.map { devotionScreenLabel(it) },
        )
    }

    /**
     * The copy button says it copies the whole article, not that it copies something.
     *
     * 複製全文 rather than 複製, and `copy_article` rather than `copy`, because this is the only way a
     * devotion leaves the app: it hands over the title, the day, every section marker and every video
     * link as plain text, which `DevotionPlainTextTest` pins byte for byte. The string is the promise
     * that none of that is dropped, so a label shortened to 複製 would be a control that quietly kept
     * only the part a reader can see.
     */
    @Test
    fun `the copy control names the whole article`() {
        assertEquals(R.string.devotion_copy_article, devotionScreenLabel(DevotionScreenLabel.COPY_ARTICLE))
    }

    /**
     * Nothing the page says for itself is the browser's own string.
     *
     * The check is across the whole table because the two browser strings are the pair this module
     * holds and the temptation is to reach for `devotion_open_in_browser` wherever the word 開啟 appears
     * — it is the longer of the two and the more obviously descriptive. Only the frame's own top bar
     * wants it, and that is in another file with its own table to answer from.
     */
    @Test
    fun `no label on the page borrows the browser's string`() {
        for (label in DevotionScreenLabel.entries) {
            assertNotEquals(R.string.devotion_open_in_browser, devotionScreenLabel(label), "$label")
        }
    }
}
