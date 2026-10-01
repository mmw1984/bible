package com.marcow.bible.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import com.marcow.bible.core.designsystem.theme.AppTheme
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * The reader's pixels, which is the one part of it no other test can reach.
 *
 * Everything else in this module asserts arithmetic: where the picker anchors, what a scroll ratio
 * means, what a chapter request turns into. Those hold whatever the type looks like. What they cannot
 * hold is the thing the reader is *for* — a Chinese verse at 17 dp, a verse number above its text, a
 * skeleton that reads as a page of scripture arriving — and those are exactly the values that drift
 * silently, because a font size that grows by one dp still compiles and still passes every other
 * test in this directory.
 *
 * Paparazzi renders one frame, at `t = 0`, and `Animatable` anchors to the first frame it is given.
 * So every entrance in this module would be captured at its invisible start value and the goldens
 * would show an empty page — which is why the entrances, and not the goldens, are what
 * [LocalInspectionMode] now settles. Paparazzi deliberately does not set that flag itself, so the
 * harness below sets it the same way its README recommends for anything that would otherwise
 * short-circuit for a `@Preview`.
 *
 * The goldens are recorded by CI, not from a workstation, and compared with
 * `maxPercentDifference = 0.0` — a golden that tolerates a one percent drift is a golden nobody reads
 * the diff of. The two failures this is here to catch are a change someone meant and a change they
 * did not, and they are only distinguishable in the second case.
 */
class ReaderGoldenTest {
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

    /** A chapter being read, in the reading the app opens in. */
    @Test
    fun `a chapter in a Chinese reading`() {
        paparazzi.snapshot(name = "ReaderScreen_chinese") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(mode = ReadingMode.CHINESE),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * The same chapter bilingual, which is the reading that exercises both columns at once: the
     * Chinese at 17 dp over the English at 13, the gap between them, and the verse number sitting
     * above the pair rather than beside it. A Chinese-only golden cannot see any of that.
     */
    @Test
    fun `a chapter in a bilingual reading`() {
        paparazzi.snapshot(name = "ReaderScreen_bilingual") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(mode = ReadingMode.BILINGUAL),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * A chapter on its way, which is the skeleton. It is a golden rather than an assertion because
     * "six bars pulsing between two alphas" is a claim about how the page looks while it waits, and
     * the pulse is the reason: at `t = 0` the bars sit at `.38`, the dim end of Flutter's range, so
     * this pins the start of the pulse as well as the bar geometry.
     */
    @Test
    fun `a chapter on its way`() {
        paparazzi.snapshot(name = "ReaderScreen_loading") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(verses = emptyList(), loading = true),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * A chapter that would not load. The state is one boolean away from the loading golden, so the
     * pair is what shows that a failed chapter says so rather than pulsing forever.
     */
    @Test
    fun `a chapter that failed to load`() {
        paparazzi.snapshot(name = "ReaderScreen_failed") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(verses = emptyList(), loading = false, failed = true),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * The action sheet a long press opens.
     *
     * It is a golden of [VerseActionSheetContent] rather than of [VerseActionSheet], for two reasons
     * that point the same way. A snapshot has no input to press with, so the sheet cannot be opened
     * the way a reader opens it; and the sheet lives in a `Popup`, which composes into its own window
     * and so is not part of the view hierarchy layoutlib draws. The content is what the reader
     * actually sees, and drawing it directly pins this sheet's pixels rather than the harness's
     * ability to composite a popup — see `VerseActionSheetContent`'s own note.
     *
     * `progress` is 1 and `onAction` is a sink, so all three rows are drawn: Ask and Explain appear
     * only when the caller can service them, and a sheet captured with the dead rows hidden would be
     * a golden of the wrong sheet.
     */
    @Test
    fun `the action sheet on a verse`() {
        paparazzi.snapshot(name = "VerseActionSheet") {
            ReaderGoldenHarness {
                val reference = "約翰福音 1:1"
                VerseActionSheetContent(
                    request = scriptureRequest(
                        action = VerseAction.ASK_AI,
                        reference = reference,
                        verse = JOHN_ONE_VERSE_1,
                        chapterContext = JOHN_CONTEXT,
                        // The composable asks for the localised frame around the reference itself,
                        // so what is handed here is what Ask sends: the request with no question.
                        explainQuestion = "",
                    ),
                    progress = 1f,
                    onAction = { _, _ -> },
                    onDismiss = {},
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
private fun ReaderGoldenHarness(content: @Composable () -> Unit) {
    AppTheme.BibleTheme(settings = AppSettings(), isSystemDark = false) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Nobody is pressing anything in a snapshot, and nothing here has to assert that they did not. */
private object SilentNavigator : ReaderNavigator {
    override fun selectMode(mode: ReadingMode) = Unit

    override fun selectChapter(chapter: Int) = Unit

    override fun selectNextChapter() = Unit

    override fun selectPreviousChapter() = Unit
}

/** Likewise for the scroll sink, which is told about a ratio on every frame of a real scroll. */
private object SilentScrollSink : ReaderScrollSink {
    override fun onScrolled(ratio: Float) = Unit

    override fun onScrollRestored() = Unit

    override fun onChapterMeasured(maxScrollPx: Float) = Unit
}

/** John 1:1–5, the five verses a golden can hold whole. */
private val JOHN_1 = ReaderUiState(
    book = JOHN,
    chapter = 1,
    mode = ReadingMode.CHINESE,
    verses = listOf(
        VersePair(
            1,
            "太初有道，道與神同在，道就是神。",
            "In the beginning was the Word, and the Word was with God, and the Word was God.",
        ),
        VersePair(
            2,
            "這道起初與神同在。",
            "He was in the beginning with God.",
        ),
        VersePair(
            3,
            "萬物是藉着他造的；凡被造的，沒有一樣不是藉着他造的。",
            "All things were made through him, and without him nothing was made that has been made.",
        ),
        VersePair(
            4,
            "生命在他裡頭，這生命就是人的光。",
            "In him was life, and the life was the light of all people.",
        ),
        VersePair(
            5,
            "光照在黑暗裡，黑暗卻沒有領受光。",
            "The light shines in the darkness, and the darkness has not overcome it.",
        ),
    ),
    loading = false,
)

private val JOHN = BibleBook(
    id = "john",
    ordinal = 43,
    nameZh = "約翰福音",
    nameEn = "John",
    chapters = 21,
    testament = Testament.NEW,
)

private val JOHN_ONE_VERSE_1 = JOHN_1.verses.first()

/** What the host's navigation bar takes, from `AppNavBarHeight` + `AppNavBarBottomGap`. */
private val NAV_BAR_CLEARANCE = 70.dp

/** The chapter's verses as the action sheet quotes them around a selected verse. */
private const val JOHN_CONTEXT = "約翰福音 1:1-5"
