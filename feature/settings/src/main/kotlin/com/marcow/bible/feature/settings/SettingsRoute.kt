package com.marcow.bible.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.core.model.AppSettings

/**
 * The settings destination as the navigation host sees it: it owns the [SettingsViewModel] and
 * hands the screen nothing but state and callbacks, so the page stays testable without a Hilt graph.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    Box(modifier = modifier) {
        SettingsScreen(
            settings = settings,
            onThemeModeChange = viewModel::setThemeMode,
            onNavbarStyleChange = viewModel::setNavbarStyle,
            onLocaleChange = viewModel::setLocale,
            onShowNavbarChange = viewModel::setShowNavbar,
            onShowDevotionChange = viewModel::setShowDevotion,
            onBack = onBack,
        )
    }
}
