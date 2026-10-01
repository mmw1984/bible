package com.marcow.bible.core.model

/** Mirrors `ThemeMode` in the Flutter app, plus a native-only `SYSTEM` option. */
enum class ThemeMode(val storageValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: LIGHT
    }
}

/** Mirrors `AppNavBarStyle` in `legacy/flutter/lib/app_settings.dart`. */
enum class NavBarStyle(val storageValue: String) {
    MATERIAL("material"),
    MATERIAL_BLUR("materialBlur"),
    ;

    companion object {
        /** Flutter's default when the key is missing. */
        fun fromStorage(value: String?): NavBarStyle = entries.firstOrNull { it.storageValue == value } ?: MATERIAL_BLUR
    }
}

/** Mirrors `AppLocale` in `legacy/flutter/lib/app_settings.dart`. */
enum class AppLocale(val storageValue: String) {
    ZH_HANT("zh-Hant"),
    EN("en"),
    ;

    /**
     * `zh-Hant` needs script and region for Android to pick `values-zh-rTW` and the right font
     * fallback chain; `zh-TW` alone resolves to Simplified resources on some devices.
     */
    val languageTag: String
        get() = if (this == ZH_HANT) "zh-Hant-TW" else storageValue

    /**
     * The language a model is asked to answer in, mirroring `AppLocale.aiLanguage` in
     * `legacy/flutter/lib/app_settings.dart`.
     *
     * This is prompt text, not a UI language: the search prompts are English and name the target
     * language in prose (`answer in concise natural Traditional Chinese`), which is the tuned
     * wording the prompts expect.
     */
    val aiLanguage: String
        get() = when (this) {
            ZH_HANT -> "natural Traditional Chinese"
            EN -> "natural English"
        }

    companion object {
        fun fromStorage(value: String?): AppLocale = entries.firstOrNull { it.storageValue == value } ?: ZH_HANT
    }
}

/**
 * The settings the UI renders, mirroring `AppSettingsState` in the Flutter app.
 *
 * Every field here has already been resolved against a default, so a screen never has to know
 * whether a value came from the user, from `LegacyPrefsImporter` or from the default.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val locale: AppLocale = AppLocale.ZH_HANT,
    val navbarStyle: NavBarStyle = NavBarStyle.MATERIAL_BLUR,
    val glassPerfBlocked: Boolean = false,
    val showNavbar: Boolean = true,
    val showDevotion: Boolean = true,
)
