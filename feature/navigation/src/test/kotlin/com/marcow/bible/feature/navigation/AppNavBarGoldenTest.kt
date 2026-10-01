package com.marcow.bible.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.marcow.bible.core.model.NavBarStyle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * The pill's pixels, which is the one part of it no other test can reach.
 *
 * The eighteen tests in [AppNavBarTest] are all arithmetic: the clearance shown or hidden, the pill's
 * width against its cap and minimum, the tab a tap or a released drag lands on, the tint at each of
 * its endpoints. Those hold whatever the bar looks like. What they cannot hold is what the bar is
 * *for* — a 58 dp white pill inset 16 from each edge, a white indicator under the selected tab, a
 * label that darkens with its icon, and the difference between a blurred pill and a solid one — and
 * the difference between the two styles is the one thing that changed most recently and silently:
 * the geometry is identical in both, so an `AppControlSurface` handed the wrong tint still passes
 * every assertion in this directory.
 *
 * Paparazzi renders one frame at `t = 0`, which the pill is built for: `animateFloatAsState` is
 * initialised to its target, so the indicator is where it belongs rather than at the start of a
 * 240 ms slide, and nothing here has to be taught to settle the way the reader's and the library's
 * entrances were. [LocalInspectionMode] is set anyway, so that a future entrance in this module
 * lands open rather than photographing itself at `alpha = 0` — and so these goldens are the same
 * kind of picture an `@Preview` of the bar would be.
 *
 * `maxPercentDifference = 0.0`. A golden that tolerates a one percent drift is one whose diff nobody
 * reads, and the only failures worth having here are a change someone meant and a change they did not.
 *
 * The images are not in the repository yet, which is worth saying here rather than leaving to be
 * found: `./gradlew :feature:navigation:recordPaparazziDebug` is the task that draws them, they land
 * in `src/test/snapshots/images` for the commit that changes them to carry, and
 * `./gradlew :feature:navigation:verifyPaparazziDebug` is what compares them. `./gradlew test` on its
 * own writes an HTML report and compares nothing.
 */
class AppNavBarGoldenTest {
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
     * The bar as the app opens it: the blurred style, the Bible tab selected, Devotions on.
     *
     * This is the default reading of the bar — `navbarStyle` is `materialBlur` when the key is
     * missing — so it is the one a golden that only had one would pick anyway, and it is the one
     * that pins the translucent `surface` at .42 against the solid fill below.
     */
    @Test
    fun `the pill with all three tabs, blurred`() {
        paparazzi.snapshot(name = "NavBar_blur") {
            NavBarGoldenHarness {
                AppFloatingNavBar(
                    items = navBarItems(showDevotion = true),
                    selectedIndex = 0,
                    onSelected = {},
                    style = NavBarStyle.MATERIAL_BLUR,
                )
            }
        }
    }

    /**
     * The same bar with Devotions turned off, which is the setting rather than a style.
     *
     * `navBarItems` drops the last entry, and the pill is scaled by the tab count rather than
     * stretched, so the two remaining tabs are *wider* than they were with three — the one thing in
     * this bar that a reader sees as a consequence of a setting and that the arithmetic test only
     * reaches as a number.
     */
    @Test
    fun `the pill with Devotions hidden`() {
        paparazzi.snapshot(name = "NavBar_noDevotion") {
            NavBarGoldenHarness {
                AppFloatingNavBar(
                    items = navBarItems(showDevotion = false),
                    selectedIndex = 0,
                    onSelected = {},
                )
            }
        }
    }

    /**
     * The solid style, which is the setting that had no golden and had stopped working.
     *
     * [AppFloatingNavBar] used to pass `style` to `AppControlSurface` only as `emphasized`, which
     * chose the *tint* and left the blur to `LocalAppBlurEnabled`'s default of true. So
     * `NavBarStyle.MATERIAL` drew a blurred, half-transparent pill: neither Flutter style, in the
     * geometry the eighteen arithmetic tests could not tell apart from either. The bar now asks the
     * one question Flutter asked — `navbarStyle == materialBlur` — and this golden is what says it
     * got the answer right, opaque `surfaceRaised` and no blur, against the `.42` `surface` above.
     */
    @Test
    fun `the pill in the solid style`() {
        paparazzi.snapshot(name = "NavBar_solid") {
            NavBarGoldenHarness {
                AppFloatingNavBar(
                    items = navBarItems(showDevotion = true),
                    selectedIndex = 0,
                    onSelected = {},
                    style = NavBarStyle.MATERIAL,
                )
            }
        }
    }

    /**
     * The Ask and Devotions buttons that stand in for the bar when it is hidden.
     *
     * This is the `!showNavbar` branch of the reader's top bar, and it is the *only* way to reach
     * Ask and Devotions once the bar is off — so a pair of buttons that stops being drawn is a pair
     * of destinations with no way in, and nothing else in this module would notice. `showDevotion`
     * is false here so the golden shows the surviving single button on its own rather than centred in
     * a gap, which is the case the composable has to get right.
     */
    @Test
    fun `the shortcuts that stand in for a hidden bar`() {
        paparazzi.snapshot(name = "HiddenNavBarShortcuts") {
            NavBarGoldenHarness {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HiddenNavBarShortcuts(
                        showDevotion = false,
                        onAsk = {},
                        onDevotion = {},
                    )
                }
            }
        }
    }
}

/**
 * The theme, the inspection flag and a full-bleed box, which is all the bar needs around it to be
 * drawn the way the app draws it.
 *
 * `AppTheme.BibleTheme` with the default [AppSettings] is the light palette and the fallback radii
 * — the same arguments `MainActivity` hands the real theme with a light system setting, and the
 * same default `navbarStyle` the bar reads when the stored key is missing.
 *
 * The bar draws itself against the bottom edge, so the harness gives it the whole device: the goldens
 * above are the bar in the position a reader sees it, over the canvas rather than over a chapter,
 * because the pill is the only thing on screen in any of them.
 */
@Composable
private fun NavBarGoldenHarness(content: @Composable () -> Unit) {
    AppTheme.BibleTheme(settings = AppSettings(), isSystemDark = false) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}
