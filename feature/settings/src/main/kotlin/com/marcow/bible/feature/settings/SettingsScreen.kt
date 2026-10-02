package com.marcow.bible.feature.settings

import android.content.Context
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppChoice
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppSegmented
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ThemeMode

/**
 * The app settings page, ported from `AiSettingsPage` + `_AppearanceSettings` in
 * `legacy/flutter/lib/ai_chat_page.dart`.
 *
 * Layout is kept in the Flutter order and with the Flutter gaps (8 below a label, 22 between
 * controls, 16 between the two visibility rows, 24 above the version) so the two builds stay
 * recognisable side by side. What is missing from the Flutter page — the OpenRouter block, the
 * danger zone, the model id field — belongs to the AI feature and arrives with it (Phase 4).
 *
 * One callback per control, so each write says which setting it changes and nothing else.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNavbarStyleChange: (NavBarStyle) -> Unit,
    onLocaleChange: (AppLocale) -> Unit,
    onShowNavbarChange: (Boolean) -> Unit,
    onShowDevotionChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = appColors
    // The window is edge to edge, so the two insets `SafeArea(bottom: false)` used to provide are
    // applied by hand: the status bar pushes the top bar down, the navigation bar extends the
    // scroll container instead of the page (Flutter padded the list with it).
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize().background(colors.canvas)) {
        SettingsTopBar(onBack = onBack, modifier = Modifier.padding(top = statusBarInset))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, top = 22.dp, end = 12.dp, bottom = navigationBarInset + 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            ) {
                SettingsLabel(stringResource(R.string.appearance))
                Spacer(Modifier.height(8.dp))
                AppSegmented(
                    choices = appearanceChoices(),
                    // Flutter resolved an absent `app_theme_mode` to a concrete brightness before it
                    // ever drew this control, so it always had one of the two lit. `SYSTEM` is the
                    // native stand-in for that state — it is what `LegacyPrefsImporter` stores when a
                    // Flutter user had neither `app_theme_mode` nor `appearance_dark` — so what is
                    // shown as selected is the brightness it resolves to, same as Flutter showed.
                    selected = resolvedThemeMode(settings.themeMode),
                    onChanged = onThemeModeChange,
                )
                Spacer(Modifier.height(22.dp))
                SettingsLabel(stringResource(R.string.navbar_style))
                Spacer(Modifier.height(8.dp))
                AppSegmented(
                    choices = navbarStyleChoices(),
                    selected = settings.navbarStyle,
                    onChanged = onNavbarStyleChange,
                )
                Spacer(Modifier.height(22.dp))
                SettingsLabel(stringResource(R.string.app_language))
                Spacer(Modifier.height(8.dp))
                AppSegmented(
                    choices = localeChoices(),
                    selected = settings.locale,
                    onChanged = onLocaleChange,
                )
                Spacer(Modifier.height(22.dp))
                SettingsLabel(stringResource(R.string.layout_section))
                Spacer(Modifier.height(8.dp))
                SettingsLabel(stringResource(R.string.navbar_visibility))
                Spacer(Modifier.height(8.dp))
                AppSegmented(
                    choices = visibilityChoices(),
                    selected = settings.showNavbar,
                    onChanged = onShowNavbarChange,
                )
                Spacer(Modifier.height(16.dp))
                SettingsLabel(stringResource(R.string.devotion_visibility))
                Spacer(Modifier.height(8.dp))
                AppSegmented(
                    choices = visibilityChoices(),
                    selected = settings.showDevotion,
                    onChanged = onShowDevotionChange,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.app_version),
                        color = colors.faint,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = appVersionName(),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** `_SettingsLabel` in the Flutter page: a small muted heading above every control. */
@Composable
private fun SettingsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = appColors.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.W600,
    )
}

/** The `SafeArea` + bordered row Flutter wrapped the page in, with its back button. */
@Composable
private fun SettingsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 58.dp)
                .padding(start = 10.dp, top = 5.dp, end = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyphButton(
                glyph = AppGlyph.BACK,
                label = stringResource(R.string.back),
                onClick = onBack,
                fill = colors.surfaceRaised.copy(alpha = 0.72f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings),
                color = colors.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
private fun appearanceChoices(): List<AppChoice<ThemeMode>> = listOf(
    AppChoice(ThemeMode.LIGHT, stringResource(R.string.light)),
    AppChoice(ThemeMode.DARK, stringResource(R.string.dark)),
)

@Composable
private fun navbarStyleChoices(): List<AppChoice<NavBarStyle>> = listOf(
    AppChoice(NavBarStyle.MATERIAL, stringResource(R.string.navbar_style_solid)),
    AppChoice(NavBarStyle.MATERIAL_BLUR, stringResource(R.string.navbar_style_blur)),
)

/**
 * Flutter hardcoded `'中文'` and `'English'` here, so a Chinese UI offered a Chinese label beside
 * an English one. The ARB has both words, so they are localized like the rest of the page.
 */
@Composable
private fun localeChoices(): List<AppChoice<AppLocale>> = listOf(
    AppChoice(AppLocale.ZH_HANT, stringResource(R.string.chinese)),
    AppChoice(AppLocale.EN, stringResource(R.string.english)),
)

@Composable
private fun visibilityChoices(): List<AppChoice<Boolean>> = listOf(
    AppChoice(true, stringResource(R.string.option_show)),
    AppChoice(false, stringResource(R.string.option_hide)),
)

@Composable
private fun resolvedThemeMode(themeMode: ThemeMode): ThemeMode = resolveThemeMode(themeMode, isSystemInDarkTheme())

/**
 * The brightness [themeMode] means on this device, which is what the appearance control shows as
 * selected: a `SYSTEM` theme lights the segment matching the platform, the way Flutter's resolved
 * theme did.
 */
internal fun resolveThemeMode(themeMode: ThemeMode, isSystemDark: Boolean): ThemeMode = when (themeMode) {
    ThemeMode.SYSTEM -> if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.LIGHT
    ThemeMode.DARK -> ThemeMode.DARK
}

/**
 * `versionName` of the installed package. The Flutter page printed a hardcoded `1.6.1` that had
 * already drifted from `pubspec.yaml`, so the real one is read instead — a library module cannot
 * see the app's `BuildConfig`, but it can always ask the package manager.
 */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) { installedVersionName(context) } ?: UNKNOWN_VERSION
}

/** `getPackageInfo(pkg, flags)` replaced the `int` flag overload in API 33. */
private fun installedVersionName(context: Context): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageInfoFlags.of(0L)).versionName
    } else {
        legacyVersionName(context)
    }
}.getOrNull()

@Suppress("DEPRECATION")
private fun legacyVersionName(context: Context): String? =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName

/** Shown when the platform cannot report the version, which should not happen. */
private const val UNKNOWN_VERSION = "—"
