package com.marcow.bible.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, mirroring `AppColors` in `legacy/flutter/lib/app_theme.dart`.
 *
 * These are theme *tokens*, not Material colours: `AppTheme` maps them onto a [ColorScheme] for the
 * Material components, but every app-drawn surface reads the palette directly, exactly the way the
 * Flutter build read `AppColors.of(context)`. Keeping both is deliberate — the Flutter build used
 * `ColorScheme.fromSeed` for components and the palette for everything it drew itself, so a native
 * screen that only read `MaterialTheme.colorScheme` would not match what the user saw before.
 *
 * The hex values are copied verbatim, not re-picked.
 */
@Immutable
data class AppColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val danger: Color,
    val dangerSurface: Color,
) {
    companion object {
        val Dark = AppColors(
            canvas = Color(0xFF090909),
            surface = Color(0xFF111111),
            surfaceRaised = Color(0xFF171717),
            ink = Color(0xFFF1EFE9),
            muted = Color(0xFF8C8B86),
            faint = Color(0xFF5E5E5A),
            line = Color(0xFF292927),
            danger = Color(0xFFE3A6A6),
            dangerSurface = Color(0xFF241717),
        )

        val Light = AppColors(
            canvas = Color(0xFFF5F2EA),
            surface = Color(0xFFFEFBF4),
            surfaceRaised = Color(0xFFECE8DE),
            ink = Color(0xFF191918),
            muted = Color(0xFF696760),
            faint = Color(0xFF969289),
            line = Color(0xFFD8D3C8),
            danger = Color(0xFF8F4141),
            dangerSurface = Color(0xFFF2DEDA),
        )
    }
}

/**
 * The Material 3 scheme for the same palette.
 *
 * `ColorScheme.fromSeed` is what the Flutter build used, but it is a Dart API with no Compose
 * equivalent that takes a seed *and* an explicit surface pair, so the roles that actually render are
 * filled in from the palette and the remaining roles come from the default scheme for that
 * brightness. Only the roles the app draws with are worth matching; the rest have no counterpart in
 * the Flutter build to be wrong about.
 */
fun AppColors.toColorScheme(): ColorScheme {
    val base = if (isDark()) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = canvas,
        secondary = muted,
        onSecondary = canvas,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = muted,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        surfaceContainerLow = surface,
        surfaceContainerLowest = canvas,
        outline = line,
        outlineVariant = line,
        error = danger,
        onError = surface,
        errorContainer = dangerSurface,
        onErrorContainer = danger,
        scrim = ink,
    )
}

private fun AppColors.isDark(): Boolean = this == AppColors.Dark

/**
 * Fallback for a composable rendered outside [AppTheme] (a unit-test root, a preview). Mirrors
 * `AppColors.of(context)`, which falls back to the palette for the current brightness.
 */
val LocalAppColors = staticCompositionLocalOf { AppColors.Light }

/** The palette for the current theme, or the light one outside a themed tree. */
val appColors: AppColors
    @Composable
    get() = LocalAppColors.current
