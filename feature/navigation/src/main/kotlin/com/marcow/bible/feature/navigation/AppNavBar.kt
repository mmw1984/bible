package com.marcow.bible.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.icons.AppGlyph
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The floating bottom navigation bar, replacing `AppNavBar` in `legacy/flutter/lib/app_navbar.dart`.
 *
 * It lives in its own feature because it is the app shell's one shared navigation surface rather
 * than part of any screen, and because features never depend on each other
 * (`NATIVE_PLAN.md` §2.2): the reader and the library both sit *under* this bar and must not know
 * it exists. A screen learns about the bar only through the clearance this module hands it, which is
 * what [appNavBarClearance] is for.
 */

/**
 * Height of the floating bar pill itself, `kAppNavBarHeight` in `app_navbar.dart`.
 *
 * The pill is fully rounded (`height / 2` on every corner), so this is also its corner radius.
 */
val AppNavBarHeight: Dp = 58.dp

/** Space between the pill and the bottom system-gesture inset, `kAppNavBarBottomGap`. */
val AppNavBarBottomGap: Dp = 12.dp

/** Horizontal breathing room around the pill, `kAppNavBarHorizontalInset`. */
val AppNavBarHorizontalInset: Dp = 16.dp

/** Full-width cap of the pill when all three tabs are shown, `kAppNavBarMaxWidth`. */
const val AppNavBarMaxWidthDp = 260.0

/**
 * The gap a screen has to leave under its content so the floating bar never covers it,
 * `appNavBottomClearance` in `app_navbar.dart`.
 *
 * Flutter computed this from an ambient `AppSettingsScope`; native keeps it a pure function of the
 * two numbers that actually decide it, so the host passes the stored setting and the window inset.
 * With the bar hidden in Settings only a small breathing gap remains, exactly as in Flutter.
 */
fun appNavBarClearance(showNavbar: Boolean, bottomInset: Dp): Dp = when {
    showNavbar -> bottomInset + AppNavBarBottomGap + AppNavBarHeight
    else -> bottomInset + AppNavBarHiddenGap
}

/** The breathing gap left instead of the bar when the user turned it off, the `16.0` in Flutter. */
private val AppNavBarHiddenGap: Dp = 16.dp

/** The bottom tabs, mirroring `AppNavTab` in `legacy/flutter/lib/app_navbar.dart:36`. */
enum class AppNavTab {
    BIBLE,
    ASK,
    DEVOTION,
}

/**
 * One entry in the bar, mirroring `NavBarItem` in `app_navbar.dart:38`.
 *
 * [glyph] is an [AppGlyph] rather than the Lucide icon Flutter drew: using Material icons instead
 * would change the icon language of the whole app, which is the one thing Phase 1 must not do.
 * That is also why the Ask tab shows the chat glyph and Devotions the sun — the app's own glyph set
 * has no `sparkles` / `sunrise`, and adding them is a design-system change rather than a shell one.
 */
data class AppNavBarItem(val tab: AppNavTab, val label: String, val glyph: AppGlyph)

/**
 * The bar's entries, mirroring `navBarItems` in `app_navbar.dart:50` plus the Devotions filter the
 * Flutter shell applied at `legacy/flutter/lib/main.dart:586`.
 *
 * Devotion is the last entry on purpose: filtering it leaves the Bible / Ask indices — and
 * therefore any stored tab index — unchanged, which is what the Flutter build relied on.
 */
@Composable
fun navBarItems(showDevotion: Boolean): List<AppNavBarItem> {
    val bible = AppNavBarItem(AppNavTab.BIBLE, stringResource(R.string.tab_bible), AppGlyph.BOOK)
    val ask = AppNavBarItem(AppNavTab.ASK, stringResource(R.string.tab_ask), AppGlyph.CHAT)
    if (!showDevotion) return listOf(bible, ask)
    val devotion = AppNavBarItem(AppNavTab.DEVOTION, stringResource(R.string.tab_devotion), AppGlyph.SUN)
    return listOf(bible, ask, devotion)
}

/**
 * Width of the pill, `math.max(120.0, math.min(constraints.maxWidth, kAppNavBarMaxWidth * n / 3))`
 * of `app_navbar.dart`.
 *
 * Flutter sized the pill off the local constraint rather than the window so that a zero-width first
 * frame (a real problem on web) could never hand it a negative width, and scaled the cap by the tab
 * count so hiding Devotions *shrinks* the pill instead of stretching the remaining tabs.
 */
fun navBarPillWidth(availableWidth: Dp, itemCount: Int): Dp {
    if (itemCount <= 0) return 0.dp
    val scaledCap = (AppNavBarMaxWidthDp * itemCount / 3).dp
    return max(PILL_MIN_WIDTH_DP.dp, minOf(availableWidth, scaledCap))
}

/** Width of one tab inside the pill, `(totalWidth - horizontalPadding * 2) / itemCount`. */
fun navBarItemWidth(totalWidth: Dp, itemCount: Int): Dp =
    if (itemCount <= 0) 0.dp else (totalWidth - PillHorizontalPadding.dp * 2) / itemCount

/**
 * How selected one tab reads, `1 - (indicatorPosition - index).abs().clamp(0, 1)` in `_buildItem`.
 *
 * This is what lets a label and an icon darken continuously while the indicator is being dragged,
 * instead of snapping when the drag ends.
 */
fun navBarSelectedness(indicatorPosition: Float, index: Int): Float =
    1f - abs(indicatorPosition - index).coerceIn(0f, 1f)

/** The tab a tap at [localX] inside the pill selects, the `onTapUp` arithmetic in `app_navbar.dart`. */
fun navBarIndexForTap(localX: Float, itemWidth: Float, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    if (itemWidth <= 0f) return 0
    val raw = ((localX - PillHorizontalPadding) / itemWidth).toInt()
    return raw.coerceIn(0, itemCount - 1)
}

/** The indicator position a drag from [startPosition] by [deltaX] pixels asks for. */
fun navBarOffsetForDrag(startPosition: Float, deltaX: Float, itemWidth: Float, itemCount: Int): Float {
    if (itemCount <= 0) return 0f
    if (itemWidth <= 0f) return startPosition
    return (startPosition + deltaX / itemWidth).coerceIn(0f, (itemCount - 1).toFloat())
}

/**
 * The tab a released drag selects: the indicator rounds to the nearest whole tab, which is what
 * `clampedPosition.round()` did on `onHorizontalDragEnd`.
 */
fun navBarIndexForRelease(indicatorPosition: Float, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return indicatorPosition.roundToInt().coerceIn(0, itemCount - 1)
}

/** Left edge of the sliding indicator, `horizontalPadding + clampedPosition * itemWidth + 4`. */
fun navBarIndicatorOffset(indicatorPosition: Float, itemWidth: Float): Float =
    PillHorizontalPadding + indicatorPosition * itemWidth + IndicatorInset

/**
 * The colour a tab's glyph and label are drawn in, `Color.lerp(colors.muted, Colors.black87, t)`.
 *
 * The order of the two ends is the whole behaviour: [muted] is where an untouched tab sits and
 * `black87` is where the selected one sits, so a tab *darkens* as the indicator reaches it. The dark
 * end is the literal `Colors.black87` rather than the theme's `ink` because Flutter interpolated to
 * that constant in both themes — it is the one place in the bar where a dark theme does not invert.
 *
 * Lives here, rather than inline in the tab, so the endpoint order is something a test can pin: the
 * two colours are close enough in the light theme (near-black and `0xFF191918`) for a swapped pair
 * to look almost right, and only the muted end of it is visible.
 */
fun navBarTabTint(muted: Color, selectedness: Float): Color = lerp(muted, SelectedInk, selectedness)

/** `Colors.black87`, the end of the tab interpolation the selected tab sits on. */
private val SelectedInk = Color(0xDD000000)

/** Padding inside the pill before the tabs start, `horizontalPadding = 4.0` in `app_navbar.dart`. */
const val PillHorizontalPadding = 4f

/** The indicator is inset from the pill's own edge, the `4` on its top and bottom. */
const val IndicatorInset = 4f

/** `math.max(120.0, …)` — the pill never narrows past a readable single tab. */
private val PILL_MIN_WIDTH_DP = 120.0
