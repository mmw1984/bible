package com.marcow.bible.feature.settings

import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    /** One dispatcher for the test body and for `viewModelScope`, so nothing runs off-scheduler. */
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `every control writes the setting the Flutter controller wrote`() = runTest(dispatcher) {
        val repository = SettingsRepository(InMemorySettingsDataStore())
        val viewModel = SettingsViewModel(repository)

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setNavbarStyle(NavBarStyle.MATERIAL)
        viewModel.setLocale(AppLocale.EN)
        viewModel.setShowNavbar(false)
        viewModel.setShowDevotion(false)
        advanceUntilIdle()

        val settings = repository.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(NavBarStyle.MATERIAL, settings.navbarStyle)
        assertEquals(AppLocale.EN, settings.locale)
        assertFalse(settings.showNavbar)
        assertFalse(settings.showDevotion)
    }

    @Test
    fun `the page renders what is stored`() = runTest(dispatcher) {
        val repository = SettingsRepository(InMemorySettingsDataStore())
        repository.setThemeMode(ThemeMode.DARK)
        repository.setLocale(AppLocale.EN)

        val settings = SettingsViewModel(repository).settings.first()

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLocale.EN, settings.locale)
    }

    @Test
    fun `an untouched store starts on the Flutter defaults`() = runTest(dispatcher) {
        val repository = SettingsRepository(InMemorySettingsDataStore())

        val settings = SettingsViewModel(repository).settings.first()

        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(AppLocale.ZH_HANT, settings.locale)
        assertEquals(NavBarStyle.MATERIAL_BLUR, settings.navbarStyle)
    }
}
