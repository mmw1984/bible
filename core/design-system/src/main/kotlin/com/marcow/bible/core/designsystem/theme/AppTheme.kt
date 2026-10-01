package com.marcow.bible.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.ThemeMode

/** The brightness a [ThemeMode] resolves to on this device. */
enum class ResolvedBrightness {
    LIGHT,
    DARK,
    ;

    val isDark: Boolean get() = this == DARK
}

/** `selectionColor: const Color(0xFF376996)`, pinned in the Flutter theme for both brightnesses. */
private val SelectionColor = Color(0xFF376996)

/**
 * The app theme, mirroring `_BibleAppState._theme` in `legacy/flutter/lib/main.dart`.
 *
 * The Flutter build built one `ThemeData` per brightness up front and let `MaterialApp` pick between
 * them, so both palettes are resolved here rather than being recomputed per frame. The two
 * structures are identical apart from the colour values, so there is nothing to interpolate and the
 * crossfade is left to the caller.
 */
object AppTheme {
    val light: ColorScheme = AppColors.Light.toColorScheme()
    val dark: ColorScheme = AppColors.Dark.toColorScheme()

    @Composable
    @Immutable
    private fun rememberTextColors(ink: Color): Typography = remember(ink) {
        val base = appTypography()
        // `theme.textTheme.apply(bodyColor: …, displayColor: …)`: every style in the scale takes the
        // ink colour and nothing else is re-tinted.
        fun TextStyle.inked() = copy(color = ink)
        Typography(
            displayLarge = base.displayLarge.inked(),
            displayMedium = base.displayMedium.inked(),
            displaySmall = base.displaySmall.inked(),
            headlineLarge = base.headlineLarge.inked(),
            headlineMedium = base.headlineMedium.inked(),
            headlineSmall = base.headlineSmall.inked(),
            titleLarge = base.titleLarge.inked(),
            titleMedium = base.titleMedium.inked(),
            titleSmall = base.titleSmall.inked(),
            bodyLarge = base.bodyLarge.inked(),
            bodyMedium = base.bodyMedium.inked(),
            bodySmall = base.bodySmall.inked(),
            labelLarge = base.labelLarge.inked(),
            labelMedium = base.labelMedium.inked(),
            labelSmall = base.labelSmall.inked(),
        )
    }

    @Composable
    private fun rememberSelectionColors(): TextSelectionColors = remember {
        TextSelectionColors(handleColor = SelectionColor, backgroundColor = SelectionColor)
    }

    /**
     * Applies the theme for [settings] over [content].
     *
     * @param isSystemDark what the platform reports for [ThemeMode.SYSTEM]; ignored otherwise.
     */
    @Composable
    fun BibleTheme(
        settings: AppSettings,
        isSystemDark: Boolean,
        radii: AppRadii = AppRadii.Fallback,
        content: @Composable () -> Unit,
    ) {
        val brightness = remember(settings.themeMode, isSystemDark) {
            when (settings.themeMode) {
                ThemeMode.LIGHT -> ResolvedBrightness.LIGHT
                ThemeMode.DARK -> ResolvedBrightness.DARK
                ThemeMode.SYSTEM ->
                    if (isSystemDark) ResolvedBrightness.DARK else ResolvedBrightness.LIGHT
            }
        }
        val colors = if (brightness.isDark) AppColors.Dark else AppColors.Light
        val shapes: Shapes = remember(radii) { appShapes(radii) }
        val selection = rememberSelectionColors()

        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppRadii provides radii,
            LocalTextSelectionColors provides selection,
        ) {
            MaterialTheme(
                colorScheme = if (brightness.isDark) dark else light,
                typography = rememberTextColors(colors.ink),
                shapes = shapes,
                content = content,
            )
        }
    }
}

/** The screen-corner shape for a set of [AppRadii], matching the Flutter `screen` radius. */
fun screenShape(radii: AppRadii): RoundedCornerShape = RoundedCornerShape(
    topStart = radii.topLeft,
    topEnd = radii.topRight,
    bottomStart = radii.bottomLeft,
    bottomEnd = radii.bottomRight,
)
