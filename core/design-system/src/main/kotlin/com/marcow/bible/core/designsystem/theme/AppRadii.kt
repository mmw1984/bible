package com.marcow.bible.core.designsystem.theme

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner radii, mirroring `AppRadii` in `legacy/flutter/lib/app_theme.dart`.
 *
 * On a device with rounded display corners the Flutter build read the real corner radii through a
 * method channel and scaled the app's radii off them, so a control's rounding always matched the
 * screen it sat on. [fromDevice] reproduces that from `WindowInsets` directly — the channel existed
 * only because Dart cannot reach the window metrics.
 */
@Immutable
data class AppRadii(
    val compact: Dp,
    val control: Dp,
    val surface: Dp,
    val screen: Dp,
    val topLeft: Dp,
    val topRight: Dp,
    val bottomLeft: Dp,
    val bottomRight: Dp,
) {
    companion object {
        /**
         * The values the Flutter build fell back to when the device reported no rounded corners,
         * which is every pre-Android 12 device and any phone with square corners.
         */
        val Fallback = AppRadii(
            compact = 8.dp,
            control = 12.dp,
            surface = 18.dp,
            screen = 28.dp,
            topLeft = 28.dp,
            topRight = 28.dp,
            bottomLeft = 28.dp,
            bottomRight = 28.dp,
        )

        /**
         * Derives the radii from the device's real display corner radii, in dp.
         *
         * The scale factors and clamps are copied from `AppRadii.load`, so a device that produced
         * `control = 14` under Flutter produces `14` here too. A device whose corners are all square
         * gets [Fallback] rather than a zero radius, because a zero would make every control a sharp
         * rectangle instead of what the Flutter build fell back to.
         */
        fun fromCornerRadii(topLeftDp: Float, topRightDp: Float, bottomLeftDp: Float, bottomRightDp: Float): AppRadii {
            val radius = maxOf(topLeftDp, topRightDp, bottomLeftDp, bottomRightDp)
            if (radius <= 0f) return Fallback
            return AppRadii(
                compact = (radius * COMPACT_SCALE).coerceIn(COMPACT_MIN, COMPACT_MAX).dp,
                control = (radius * CONTROL_SCALE).coerceIn(CONTROL_MIN, CONTROL_MAX).dp,
                surface = (radius * SURFACE_SCALE).coerceIn(SURFACE_MIN, SURFACE_MAX).dp,
                screen = radius.dp,
                topLeft = topLeftDp.dp,
                topRight = topRightDp.dp,
                bottomLeft = bottomLeftDp.dp,
                bottomRight = bottomRightDp.dp,
            )
        }

        /**
         * Reads the display's corner radii off [insets] and scales from them, or returns [Fallback]
         * where the platform has no such API.
         */
        fun fromDevice(insets: android.view.WindowInsets?, density: Float): AppRadii {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || insets == null || density <= 0f) {
                return Fallback
            }
            return fromCornerRadii(
                topLeftDp = radiusDp(insets, RoundedCorner.POSITION_TOP_LEFT, density),
                topRightDp = radiusDp(insets, RoundedCorner.POSITION_TOP_RIGHT, density),
                bottomLeftDp = radiusDp(insets, RoundedCorner.POSITION_BOTTOM_LEFT, density),
                bottomRightDp = radiusDp(insets, RoundedCorner.POSITION_BOTTOM_RIGHT, density),
            )
        }

        private fun radiusDp(insets: android.view.WindowInsets, position: Int, density: Float): Float {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
            return (insets.getRoundedCorner(position)?.radius ?: 0) / density
        }

        // `radius * .24`, `radius * .34`, `radius * .48` and their clamps, from `AppRadii.load`.
        private const val COMPACT_SCALE = 0.24f
        private const val CONTROL_SCALE = 0.34f
        private const val SURFACE_SCALE = 0.48f
        private const val COMPACT_MIN = 7f
        private const val COMPACT_MAX = 11f
        private const val CONTROL_MIN = 10f
        private const val CONTROL_MAX = 16f
        private const val SURFACE_MIN = 14f
        private const val SURFACE_MAX = 22f
    }
}

/** The radii for the current theme, or [AppRadii.Fallback] outside a themed tree. */
val LocalAppRadii = staticCompositionLocalOf { AppRadii.Fallback }

val appRadii: AppRadii
    @Composable
    get() = LocalAppRadii.current
