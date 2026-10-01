package com.marcow.bible.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings screen's state, replacing the Flutter `AppSettingsController` this page used to
 * reach for directly.
 *
 * The screen is a pure function of [settings] and the setters below, so a tap has exactly one
 * effect: a write to [SettingsRepository], which is where the rest of the app — the theme, the
 * locale and the navbar style — reads from.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            AppSettings(),
        )

    fun setThemeMode(themeMode: ThemeMode) = write { settingsRepository.setThemeMode(themeMode) }

    fun setNavbarStyle(navbarStyle: NavBarStyle) = write { settingsRepository.setNavbarStyle(navbarStyle) }

    fun setLocale(locale: AppLocale) = write { settingsRepository.setLocale(locale) }

    fun setShowNavbar(showNavbar: Boolean) = write { settingsRepository.setShowNavbar(showNavbar) }

    fun setShowDevotion(showDevotion: Boolean) = write { settingsRepository.setShowDevotion(showDevotion) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        /** Long enough to survive a rotation, short enough that a backgrounded app stops reading. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
