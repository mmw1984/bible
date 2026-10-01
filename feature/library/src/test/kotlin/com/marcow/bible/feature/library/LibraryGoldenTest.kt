package com.marcow.bible.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import com.marcow.bible.core.designsystem.theme.AppTheme
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * The library's pixels, which is the one part of it no other test can reach.
 *
 * The four tests in this module assert arithmetic: the panel's width against its 440 cap, the canon
 * ordinal's zero padding, which books a testament holds. Those hold whatever the panel looks like.
 * What they cannot hold is what the panel is *for* — a two-digit canon number down the side, a name
 * that follows the reading mode, a segmented control reading 舊約 · 39, the five-column chapter grid
 * with the current chapter filled — and those drift silently, because a row that loses its rule
 * still compiles and still passes every assertion above.
 *
 * Paparazzi renders one frame, at `t = 0`, so the panel's two entrances are what
 * [LocalInspectionMode] settles: the stagger gate opens closed and the testament switcher is skipped
 * outright. Without that this file would photograph an empty list under a title, which is a golden
 * nobody would read the diff of. Paparazzi deliberately does not set the flag itself, so the harness
 * below sets it the way its README recommends for anything that would otherwise short-circuit for a
 * `@Preview`.
 *
 * The goldens are recorded by CI and compared with `maxPercentDifference = 0.0` — a golden that
 * tolerates a one percent drift is a golden whose diff is never read. The two failures this is here
 * to catch are a change someone meant and a change they did not, and only the second is visible in
 * the second.
 */
class LibraryGoldenTest {
    private lateinit var paparazzi: Paparazzi

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5,
            // A platform theme is enough, and the lowest-risk thing to ask for: the composable
            // supplies the real Material 3 scheme through `AppTheme` a line below, so this only has
            // to be a theme that exists in the SDK the layoutlib ships with.
            theme = "android:Theme.Material.Light.NoActionBar",
            maxPercentDifference = 0.0,
        )
        paparazzi.setup(
            TestName(
                packageName = testInfo.testClass.get().packageName,
                className = testInfo.testClass.get().simpleName,
                methodName = testInfo.testMethod.get().name,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        paparazzi.teardown()
    }

    /**
     * The sheet over the reader, in the reading the app opens in.
     *
     * It is the sheet rather than the panel because the scrim is half of what dismissing this costs
     * the reader: a 62% black over the chapter behind it. A golden of the panel alone could not say
     * whether that scrim is 62% or 40%, and it is the panel's `ScrimColor` — not the reader's — so
     * nothing else would.
     */
    @Test
    fun `the sheet over a reader in Genesis`() {
        paparazzi.snapshot(name = "LibrarySheet_old") {
            LibraryGoldenHarness {
                LibrarySheet(
                    state = OLD_TESTAMENT_STATE,
                    readingMode = ReadingMode.CHINESE,
                    selectedBookId = GENESIS.id,
                    progress = 1f,
                    onBookSelected = {},
                    onDismiss = {},
                )
            }
        }
    }

    /**
     * The same sheet reached from the New Testament, which is the half of the canon most readers
     * live in, and which is the golden that pins the panel's one piece of remembered state.
     *
     * `_LibraryPanel` opened on the testament of the book being read — `late bool old =
     * bibleBooks[widget.selected].old` — so this is not Genesis renamed: selecting 約翰福音 and
     * reopening has to arrive on 新約 with 27 books behind the segment. That behaviour is asserted
     * by `widget_test.dart` in Flutter and by nothing here in arithmetic, and it is invisible in a
     * golden that opens on the Old Testament.
     */
    @Test
    fun `the sheet over a reader in John`() {
        paparazzi.snapshot(name = "LibrarySheet_new") {
            LibraryGoldenHarness {
                LibrarySheet(
                    state = NEW_TESTAMENT_STATE,
                    readingMode = ReadingMode.CHINESE,
                    selectedBookId = JOHN.id,
                    progress = 1f,
                    onBookSelected = {},
                    onDismiss = {},
                )
            }
        }
    }

    /**
     * The column beside the reader on a window wide enough for it.
     *
     * It is drawn at [SidebarWidth] because that is the width the host gives it and the sidebar
     * itself knows nothing about the 920 dp threshold that decides whether it appears — so a golden
     * at any other width would be pinning a layout no reader ever sees.
     */
    @Test
    fun `the sidebar beside the reader`() {
        paparazzi.snapshot(name = "LibrarySidebar") {
            LibraryGoldenHarness {
                LibrarySidebar(
                    book = JOHN,
                    chapter = 1,
                    readingMode = ReadingMode.CHINESE,
                    usesEnglishUi = false,
                    onLibraryClick = {},
                    onChapterSelected = {},
                    modifier = Modifier.width(SidebarWidth),
                )
            }
        }
    }
}

/**
 * The theme, the inspection flag and a full-bleed box, which is all a screen needs around it to be
 * drawn the way the app draws it.
 *
 * `AppTheme.BibleTheme` with the default [AppSettings] is the light palette and the fallback radii
 * — the same arguments `MainActivity` hands the real theme with a light system setting.
 */
@Composable
private fun LibraryGoldenHarness(content: @Composable () -> Unit) {
    AppTheme.BibleTheme(settings = AppSettings(), isSystemDark = false) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

private val GENESIS = BibleBook("GEN", 1, "創世記", "Genesis", 50, Testament.OLD)

private val JOHN = BibleBook("JHN", 43, "約翰福音", "John", 21, Testament.NEW)

/**
 * The Old Testament, truncated after the twelfth book.
 *
 * Truncated on purpose and not a lie about the panel: the visible rows are all real books at their
 * real canon positions, and the panel scrolls — the segmented control's 舊約 · 39 comes from a
 * string resource rather than from the length of this list, so the row the golden shows above the
 * fold is the row a reader sees. A list of all thirty-nine would only move the fold.
 */
private val OLD_TESTAMENT_STATE = LibraryUiState(
    books = listOf(
        GENESIS,
        BibleBook("EXO", 2, "出埃及記", "Exodus", 40, Testament.OLD),
        BibleBook("LEV", 3, "利未記", "Leviticus", 27, Testament.OLD),
        BibleBook("NUM", 4, "民數記", "Numbers", 36, Testament.OLD),
        BibleBook("DEU", 5, "申命記", "Deuteronomy", 34, Testament.OLD),
        BibleBook("JOS", 6, "約書亞記", "Joshua", 24, Testament.OLD),
        BibleBook("JDG", 7, "士師記", "Judges", 21, Testament.OLD),
        BibleBook("RUT", 8, "路得記", "Ruth", 4, Testament.OLD),
        BibleBook("1SA", 9, "撒母耳記上", "1 Samuel", 31, Testament.OLD),
        BibleBook("2SA", 10, "撒母耳記下", "2 Samuel", 24, Testament.OLD),
        BibleBook("1KI", 11, "列王紀上", "1 Kings", 22, Testament.OLD),
        BibleBook("2KI", 12, "列王紀下", "2 Kings", 25, Testament.OLD),
    ),
    usesEnglishUi = false,
    loading = false,
)

/** The New Testament, truncated the same way, with John first so the selected row is on screen. */
private val NEW_TESTAMENT_STATE = LibraryUiState(
    books = listOf(
        BibleBook("MAT", 40, "馬太福音", "Matthew", 28, Testament.NEW),
        BibleBook("MRK", 41, "馬可福音", "Mark", 16, Testament.NEW),
        BibleBook("LUK", 42, "路加福音", "Luke", 24, Testament.NEW),
        JOHN,
        BibleBook("ACT", 44, "使徒行傳", "Acts", 28, Testament.NEW),
        BibleBook("ROM", 45, "羅馬書", "Romans", 16, Testament.NEW),
        BibleBook("1CO", 46, "哥林多前書", "1 Corinthians", 16, Testament.NEW),
        BibleBook("2CO", 47, "哥林多後書", "2 Corinthians", 13, Testament.NEW),
        BibleBook("GAL", 48, "加拉太書", "Galatians", 6, Testament.NEW),
        BibleBook("EPH", 49, "以弗所書", "Ephesians", 6, Testament.NEW),
    ),
    usesEnglishUi = false,
    loading = false,
)
