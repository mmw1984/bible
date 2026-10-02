package com.marcow.bible.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
 * `maxPercentDifference = 0.0` — a golden that tolerates a one percent drift is a golden nobody
 * reads the diff of. The two failures this is here to catch are a change someone meant and a change
 * they did not, and only the second shows up in a diff.
 *
 * The images are not in the repository yet, which is worth saying here rather than leaving to be
 * found: `./gradlew :feature:reader:recordPaparazziDebug` is the task that draws them, they land in
 * `src/test/snapshots/images` for the commit that changes them to carry, and
 * `./gradlew :feature:reader:verifyPaparazziDebug` is what compares them. `./gradlew test` on its
 * own writes an HTML report and compares nothing, so a run of it says the composables still draw.
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
     * A chapter in an English-only reading, which is the third of the three and the one the other two
     * cannot stand in for.
     *
     * English is its own branch all the way down — its own 17 px, its own 1.78 line height instead of
     * the bilingual pair's 17 over 14, and no Chinese above it to share a row with — so it is the
     * only reading in which the verse number's neighbour changes. A font size moved by one dp on that
     * branch compiles, passes every arithmetic test in this module and passes the other two goldens.
     */
    @Test
    fun `a chapter in an English reading`() {
        paparazzi.snapshot(name = "ReaderScreen_english") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(mode = ReadingMode.ENGLISH),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * The last row of the reader: the links to the chapters either side, which nothing above had ever
     * put a pixel in.
     *
     * It takes a shorter chapter than [JOHN_1] to be in frame at all. The links come after the verses,
     * so five verses of John carry them off the bottom of a 393 x 851 window and three bring them back
     * onto it — which is why this is its own fixture rather than a sixth verse on the existing one.
     *
     * Chapter one is the chapter worth looking at: there is nothing before it, so the left link is
     * Flutter's dash on a disabled row and the right one is live. A book in the middle would show the
     * two named links and never the end the reader actually reaches.
     */
    @Test
    fun `the ends of a book`() {
        paparazzi.snapshot(name = "ReaderScreen_chapterLinks") {
            ReaderGoldenHarness {
                ReaderScreen(
                    state = JOHN_1.copy(verses = JOHN_1.verses.take(3)),
                    navigator = SilentNavigator,
                    scroll = SilentScrollSink,
                    bottomClearance = NAV_BAR_CLEARANCE,
                    onVerseAction = null,
                )
            }
        }
    }

    /**
     * The chapter picker: the one control the reader has that no golden had reached, because it opens
     * in a `Popup` and a snapshot has no tap to open it with.
     *
     * It is a golden of [ChapterPickerContent] for the same reason the action sheet's is — the popup
     * composes into a window layoutlib does not draw, so the bubble would come out an empty frame — and
     * it is handed the width and the height cap [ChapterPickerBubble] computes on this device, so the
     * bubble is drawn at the size it is really given. Where it sits in the window is left out, since
     * that is the four functions' arithmetic and `ChapterPickerTest` holds it down.
     *
     * John at chapter three: 21 chapters over five columns leaves the last cell of the last row empty,
     * which is the shape most of the shorter books have, and the cell being read is the one inverted.
     * Whether the grid scrolls is left to that test too, because a grid taller than its cap is clipped
     * to the cap either way and a picture could not tell the two apart.
     */
    @Test
    fun `the chapter picker over the chapter`() {
        paparazzi.snapshot(name = "ChapterPicker") {
            ReaderGoldenHarness {
                val configuration = LocalConfiguration.current
                val windowWidth = configuration.screenWidthDp.dp

                // A top of zero leaves the cap itself — `min(400, height - top - bottom - 16)` — which
                // is the largest the bubble is ever handed, and the width below is the one a 393 dp
                // window gets, `min(330, width - 32)`: the two numbers the bubble is really given.
                val maxHeight = chapterPickerMaxHeight(
                    top = 0.dp,
                    windowHeight = configuration.screenHeightDp.dp,
                    bottomInset = 0.dp,
                )
                ChapterPickerContent(
                    chapter = 3,
                    chapterCount = JOHN.chapters,
                    maxHeight = maxHeight,
                    onChapterSelected = {},
                    onDismiss = {},
                    modifier = Modifier
                        .width(chapterPickerWidth(windowWidth))
                        .heightIn(max = maxHeight),
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
     * `progress` is 1 and `onAction` is a sink, so all three rows are drawn: this is the sheet as
     * Phase 3 will show it, with something to send the two question rows to. The sheet as this build
     * shows it is the golden below, and it is one row rather than a mistake in this one.
     */
    @Test
    fun `the action sheet on a verse`() {
        paparazzi.snapshot(name = "VerseActionSheet") {
            ReaderGoldenHarness {
                VerseActionSheetContent(
                    request = verseRequest(),
                    progress = 1f,
                    onAction = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
    }

    /**
     * The same sheet with nothing to send a question to, which is what a long press opens today.
     *
     * `onAction` is null, so [verseActionRows] gives the sheet the one row that needs no host and
     * [VerseActionSheetTest] holds that decision; this is what the decision *looks* like. The sheet is
     * under half the height it will be in Phase 3 — one tile under a grabber — and the reader is
     * visible through the scrim above it instead of behind two more rows, which is the one thing about
     * this state no assertion can say and the thing a reader long-pressing a verse sees.
     *
     * A sheet with two dead rows in it was the alternative and is worse: it offers a question the app
     * cannot answer, in a tile the reader has already learned to tap.
     */
    @Test
    fun `the action sheet with nothing to send a question to`() {
        paparazzi.snapshot(name = "VerseActionSheet_copyOnly") {
            ReaderGoldenHarness {
                VerseActionSheetContent(
                    request = verseRequest(),
                    progress = 1f,
                    onAction = null,
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

/**
 * John 1:1 as a long press hands it over: the reference, the verse, and the chapter behind it.
 *
 * Both action-sheet goldens draw the same verse, because the sheet is a function of what was pressed
 * and not of which of its rows are on screen — so one request, named once, and the two goldens differ
 * only in the `onAction` they are given. The composable asks for the localised frame around the
 * reference itself, so what is handed here is what Ask sends: the request with no question.
 */
private fun verseRequest(): ScriptureRequest = scriptureRequest(
    action = VerseAction.ASK_AI,
    reference = "約翰福音 1:1",
    verse = JOHN_ONE_VERSE_1,
    chapterContext = JOHN_CONTEXT,
    explainQuestion = "",
)

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

/**
 * John, the book every golden in this class reads from.
 *
 * Declared before [JOHN_1] because Kotlin initialises a file's top-level properties in order and
 * refuses to read one that has not been initialised yet — so a fixture that names its book has to
 * come after the book, not before it.
 */
private val JOHN = BibleBook(
    id = "JHN",
    ordinal = 43,
    nameZh = "約翰福音",
    nameEn = "John",
    chapters = 21,
    testament = Testament.NEW,
)

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

private val JOHN_ONE_VERSE_1 = JOHN_1.verses.first()

/** What the host's navigation bar takes, from `AppNavBarHeight` + `AppNavBarBottomGap`. */
private val NAV_BAR_CLEARANCE = 70.dp

/** The chapter's verses as the action sheet quotes them around a selected verse. */
private const val JOHN_CONTEXT = "約翰福音 1:1-5"
