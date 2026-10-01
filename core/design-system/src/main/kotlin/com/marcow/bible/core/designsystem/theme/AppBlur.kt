package com.marcow.bible.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the app draws its frosted surfaces with a real blur.
 *
 * `AppControlSurface` consults this, and it is the native equivalent of the
 * `AppSettingsScope.state.navbarStyle == AppNavBarStyle.materialBlur` check in `AppControlSurface`
 * on the Flutter side. The host supplies it from the stored setting; the default is on, because
 * blur is the Flutter default (`AppNavBarStyle.materialBlur`).
 */
val LocalAppBlurEnabled = staticCompositionLocalOf { true }

val appBlurEnabled: Boolean
    @Composable
    get() = LocalAppBlurEnabled.current
