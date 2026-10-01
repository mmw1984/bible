package com.marcow.bible.feature.settings

import com.marcow.bible.core.model.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsScreenTest {
    @Test
    fun `a stored theme is the theme shown`() {
        assertEquals(ThemeMode.DARK, resolveThemeMode(ThemeMode.DARK, isSystemDark = false))
        assertEquals(ThemeMode.LIGHT, resolveThemeMode(ThemeMode.LIGHT, isSystemDark = true))
    }

    @Test
    fun `a system theme shows the brightness the platform reports`() {
        assertEquals(ThemeMode.DARK, resolveThemeMode(ThemeMode.SYSTEM, isSystemDark = true))
        assertEquals(ThemeMode.LIGHT, resolveThemeMode(ThemeMode.SYSTEM, isSystemDark = false))
    }
}
