package com.marcow.bible.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the palette and radii to the values in `legacy/flutter/lib/app_theme.dart`.
 *
 * These are the numbers that decide whether the native app looks like the Flutter one, and nothing
 * in the app can catch a mistake in them: a wrong hex value still compiles, still runs, and still
 * looks like a deliberate choice. So the values are asserted instead.
 */
class AppTokensTest {
    @Test
    fun `dark palette matches app_theme dart`() {
        val colors = AppColors.Dark
        assertEquals(Color(0xFF090909), colors.canvas)
        assertEquals(Color(0xFF111111), colors.surface)
        assertEquals(Color(0xFF171717), colors.surfaceRaised)
        assertEquals(Color(0xFFF1EFE9), colors.ink)
        assertEquals(Color(0xFF8C8B86), colors.muted)
        assertEquals(Color(0xFF5E5E5A), colors.faint)
        assertEquals(Color(0xFF292927), colors.line)
        assertEquals(Color(0xFFE3A6A6), colors.danger)
        assertEquals(Color(0xFF241717), colors.dangerSurface)
    }

    @Test
    fun `light palette matches app_theme dart`() {
        val colors = AppColors.Light
        assertEquals(Color(0xFFF5F2EA), colors.canvas)
        assertEquals(Color(0xFFFEFBF4), colors.surface)
        assertEquals(Color(0xFFECE8DE), colors.surfaceRaised)
        assertEquals(Color(0xFF191918), colors.ink)
        assertEquals(Color(0xFF696760), colors.muted)
        assertEquals(Color(0xFF969289), colors.faint)
        assertEquals(Color(0xFFD8D3C8), colors.line)
        assertEquals(Color(0xFF8F4141), colors.danger)
        assertEquals(Color(0xFFF2DEDA), colors.dangerSurface)
    }

    @Test
    fun `fallback radii match app_theme dart`() {
        val radii = AppRadii.Fallback
        assertEquals(8.dp, radii.compact)
        assertEquals(12.dp, radii.control)
        assertEquals(18.dp, radii.surface)
        assertEquals(28.dp, radii.screen)
        assertEquals(28.dp, radii.topLeft)
        assertEquals(28.dp, radii.topRight)
        assertEquals(28.dp, radii.bottomLeft)
        assertEquals(28.dp, radii.bottomRight)
    }

    @Test
    fun `rounded device scales the radii and keeps the screen radius`() {
        // The Flutter build read these off the display corners; a 40 dp corner produced
        // compact 9.6, control 13.6, surface 19.2, screen 40.
        val radii = AppRadii.fromCornerRadii(topLeft = 40f, topRight = 40f, bottomLeft = 40f, bottomRight = 40f)
        assertEquals(9.6f.dp, radii.compact)
        assertEquals(13.6f.dp, radii.control)
        assertEquals(19.2f.dp, radii.surface)
        assertEquals(40f.dp, radii.screen)
    }

    @Test
    fun `radius scale clamps at both ends`() {
        // A very square-ish corner would push compact below its 7 dp floor and control below 10.
        val tight = AppRadii.fromCornerRadii(topLeft = 12f, topRight = 12f, bottomLeft = 12f, bottomRight = 12f)
        assertEquals(7f.dp, tight.compact)
        assertEquals(10f.dp, tight.control)
        assertEquals(14f.dp, tight.surface)

        // A very round corner would push surface above its 22 dp ceiling and control above 16.
        val round = AppRadii.fromCornerRadii(topLeft = 90f, topRight = 90f, bottomLeft = 90f, bottomRight = 90f)
        assertEquals(11f.dp, round.compact)
        assertEquals(16f.dp, round.control)
        assertEquals(22f.dp, round.surface)
    }

    @Test
    fun `square corners fall back rather than collapsing to zero`() {
        // `RoundedCorner` reports a real zero on a device with square corners, and a zero radius
        // would make every control a sharp rectangle, which is not what the Flutter build did.
        assertEquals(AppRadii.Fallback, AppRadii.fromCornerRadii(0f, 0f, 0f, 0f))
    }

    @Test
    fun `only the largest corner drives the scale`() {
        // A display with only the top corners rounded: the scale follows the largest, but the
        // per-corner values are what a sheet's own shape uses, so they are not smoothed.
        val radii = AppRadii.fromCornerRadii(topLeft = 50f, topRight = 50f, bottomLeft = 0f, bottomRight = 0f)
        assertEquals(50f.dp, radii.screen)
        assertEquals(50f.dp, radii.topLeft)
        assertEquals(0f.dp, radii.bottomLeft)
        assertEquals(17f.dp, radii.control)
    }

    @Test
    fun `the two palettes are distinguishable`() {
        assertNotEquals(AppColors.Light.canvas, AppColors.Dark.canvas)
        assertTrue(AppColors.Light.canvas.luminance() > AppColors.Dark.canvas.luminance())
        assertTrue(AppColors.Light.ink.luminance() < AppColors.Dark.ink.luminance())
    }
}
