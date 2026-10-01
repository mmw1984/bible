package com.marcow.bible.core.datastore

import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    private val repo = SettingsRepository(InMemorySettingsDataStore())

    @Test
    fun `an empty store yields the Flutter defaults`() = runTest {
        val settings = repo.settings.first()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(AppLocale.ZH_HANT, settings.locale)
        assertEquals(NavBarStyle.MATERIAL_BLUR, settings.navbarStyle)
        assertFalse(settings.glassPerfBlocked)
        assertTrue(settings.showNavbar)
        assertTrue(settings.showDevotion)
    }

    @Test
    fun `each setter round-trips`() = runTest {
        repo.setThemeMode(ThemeMode.DARK)
        repo.setLocale(AppLocale.EN)
        repo.setNavbarStyle(NavBarStyle.MATERIAL)
        repo.setGlassPerfBlocked(true)
        repo.setShowNavbar(false)
        repo.setShowDevotion(false)

        val settings = repo.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLocale.EN, settings.locale)
        assertEquals(NavBarStyle.MATERIAL, settings.navbarStyle)
        assertTrue(settings.glassPerfBlocked)
        assertFalse(settings.showNavbar)
        assertFalse(settings.showDevotion)
    }

    @Test
    fun `picking a navbar style clears glassPerfBlocked like the Flutter app`() = runTest {
        repo.setGlassPerfBlocked(true)
        repo.setNavbarStyle(NavBarStyle.MATERIAL)
        assertFalse(repo.settings.first().glassPerfBlocked)
    }

    @Test
    fun `one setter leaves the other fields untouched`() = runTest {
        repo.setThemeMode(ThemeMode.DARK)
        repo.setLocale(AppLocale.EN)
        repo.setShowDevotion(false)

        val settings = repo.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLocale.EN, settings.locale)
        // Never written, so it must still read as the Flutter default rather than proto's false.
        assertTrue(settings.showNavbar)
        assertFalse(settings.showDevotion)
    }

    @Test
    fun `the legacy import only runs once`() = runTest {
        assertFalse(repo.hasCompletedLegacyImport())
        repo.markLegacyImportCompleted()
        assertTrue(repo.hasCompletedLegacyImport())
    }

    @Test
    fun `the system theme mode is native only and survives a round-trip`() = runTest {
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
    }

    @Test
    fun `settings survive a proto round-trip`() = runTest {
        val store = InMemorySettingsDataStore()
        val repo = SettingsRepository(store)
        repo.setThemeMode(ThemeMode.SYSTEM)
        repo.setLocale(AppLocale.EN)
        repo.setNavbarStyle(NavBarStyle.MATERIAL)
        repo.setGlassPerfBlocked(true)
        repo.setShowNavbar(false)
        repo.setShowDevotion(false)
        repo.markLegacyImportCompleted()

        // Presence matters here: `showNavbar = false` and "never written" both serialize to the
        // same bytes without `optional`, and the domain mapping would read the former as the
        // latter's default.
        val restored = store.roundTrip()
        assertEquals(repo.settings.first(), restored.toDomain())
    }

    @Test
    fun `unknown stored values fall back to the defaults`() {
        val garbage = Settings.newBuilder()
            .setThemeModeValue(999)
            .setNavbarStyleValue(999)
            .setLocaleTag("fr-FR")
            .build()

        val settings = garbage.toDomain()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(NavBarStyle.MATERIAL_BLUR, settings.navbarStyle)
        assertEquals(AppLocale.ZH_HANT, settings.locale)
    }
}
