package com.marcow.bible.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import com.marcow.bible.core.designsystem.theme.AppTheme
import com.marcow.bible.core.model.AppSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * The top bar's pixels, which is the one part of it [ReaderTopBarTest] cannot reach.
 *
 * The seven tests there are all decisions: which controls are on the bar, and what gap sits in front
 * of each. Those hold whatever the controls look like. What they cannot hold is the thing the bar is
 * *for* — the book button hard against the left edge, the search and gear hard against the right, 9 dp
 * between any two of them and nowhere else — and every one of those is a number that compiles and
 * still reads wrong.
 *
 * The three arrangements below are the three that matter. With the bar on, there are only two trailing
 * controls and the whole right-hand side is a gap; with the bar off there are four and the same 9 dp
 * has to hold between all of them; and on a wide window the book button is gone entirely, which is the
 * one arrangement in which a bar that still drew it would look plausible rather than broken.
 *
 * Paparazzi renders one frame at `t = 0` and deliberately does not set [LocalInspectionMode], so the
 * harness sets it: nothing in this bar animates, and the flag is here for the same reason it is in the
 * reader's and the library's goldens — so that a future control in this module does not photograph
 * itself at the invisible start of its own entrance.
 */
class ReaderTopBarGoldenTest {
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
     * The bar as the app opens it: the bottom bar on, so the top bar is only the book button on one
     * side and the two controls that work on any window on the other.
     */
    @Test
    fun `the bar with the bottom bar on`() {
        paparazzi.snapshot(name = "ReaderTopBar_navBarOn") {
            ReaderTopBarGoldenHarness {
                ReaderTopBar(
                    visibility = ReaderTopBarVisibility(libraryButton = true, navBar = true, devotion = true),
                    onOpenLibrary = {},
                    onSearch = {},
                    onSettings = {},
                    onAsk = {},
                    onDevotion = {},
                )
            }
        }
    }

    /**
     * The same bar with the bottom bar turned off in Settings, which is what puts four controls on it
     * instead of two and makes the Ask and Devotions buttons the only way to reach either destination.
     */
    @Test
    fun `the bar standing in for a hidden bottom bar`() {
        paparazzi.snapshot(name = "ReaderTopBar_navBarOff") {
            ReaderTopBarGoldenHarness {
                ReaderTopBar(
                    visibility = ReaderTopBarVisibility(libraryButton = true, navBar = false, devotion = true),
                    onOpenLibrary = {},
                    onSearch = {},
                    onSettings = {},
                    onAsk = {},
                    onDevotion = {},
                )
            }
        }
    }

    /**
     * A window wide enough for the sidebar, where the book button is gone because the sidebar beside
     * the reader already names the book and opens the library.
     *
     * This is the arrangement a bug in `libraryButton` hides best: the remaining controls are all
     * right-aligned, so a bar that wrongly kept the button still reads as a top bar, just one with a
     * control nobody should have been able to press.
     */
    @Test
    fun `the bar on a window that has the sidebar`() {
        paparazzi.snapshot(name = "ReaderTopBar_wide") {
            ReaderTopBarGoldenHarness {
                ReaderTopBar(
                    visibility = ReaderTopBarVisibility(libraryButton = false, navBar = true, devotion = true),
                    onOpenLibrary = {},
                    onSearch = {},
                    onSettings = {},
                    onAsk = {},
                    onDevotion = {},
                )
            }
        }
    }
}

/**
 * The theme, the inspection flag and a box aligned to the top edge, which is all the bar needs around
 * it to be drawn the way the app draws it.
 *
 * The bar draws itself against the top of the window and is not full height, so the harness gives it
 * the whole device at the top rather than the bottom the pill in [AppNavBarGoldenTest] uses — the two
 * bars are the two edges of the same screen, and neither golden should be a picture of the other one.
 */
@Composable
private fun ReaderTopBarGoldenHarness(content: @Composable () -> Unit) {
    AppTheme.BibleTheme(settings = AppSettings(), isSystemDark = false) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}
