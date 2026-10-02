package com.marcow.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.feature.search.domain.SearchSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The app shell's own state, replacing the settings half of `_BibleHomeState` in
 * `legacy/flutter/lib/main.dart:213`.
 *
 * Flutter read `AppSettingsController.instance` ambiently from every widget; the shell reads it once
 * here and hands it down: [settings] drives the theme, the tab list, the top bar and the search
 * sheet's language, so a change in Settings repaints the whole shell without any screen reaching for
 * the repository itself.
 *
 * [searchSignIn] is exposed rather than injected at the sheet because `SearchHost` takes the port as
 * a parameter (`feature/search` owns the port, `core/network` owns the manager, and the shell owns
 * neither). The shell holding the singleton keeps the sheet's sign-in button on the same instance the
 * rest of the app publishes to.
 */
@HiltViewModel
class BibleShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    val searchSignIn: SearchSignIn,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )
}
