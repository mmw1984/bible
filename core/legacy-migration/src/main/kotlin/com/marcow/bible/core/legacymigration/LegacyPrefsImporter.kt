package com.marcow.bible.core.legacymigration

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.DevotionCacheDao
import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.core.database.ReadingProgressDao
import com.marcow.bible.core.database.ReadingProgressEntity
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ReadingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot import of everything the Flutter app left in `FlutterSharedPreferences.xml`.
 *
 * The native app keeps the same `applicationId` and signing key, so it installs over the Flutter
 * build and has to read that file instead of starting from scratch. The import is idempotent and
 * deliberately leaves the legacy file in place, so rolling back to the Flutter build still finds
 * the user's settings.
 *
 * `NATIVE_PLAN.md` §3.6 lists the key mapping. Two decisions are worth calling out:
 *
 *  - `app_theme_mode` is missing on Flutter builds older than the theming work, so `appearance_dark`
 *    is consulted next, and a user who has neither key follows the system, which is the native
 *    default and what `AppSettingsController` ended up doing anyway.
 *  - the reader's scroll offset is an absolute pixel count, which is meaningless without the old
 *    screen's viewport, so it is parked in [Settings.legacyScrollPx] for the reader to turn into a
 *    `scroll_ratio` once Compose has measured the chapter.
 */
@Singleton
class LegacyPrefsImporter @Inject constructor(
    private val preferences: LegacyPreferenceSource,
    private val settingsRepository: SettingsRepository,
    private val bibleDao: BibleDao,
    private val readingProgressDao: ReadingProgressDao,
    private val devotionCacheDao: DevotionCacheDao,
) {
    /**
     * @return true when this call performed the import, false when a previous launch already did.
     */
    suspend fun migrate(): Boolean {
        if (settingsRepository.hasCompletedLegacyImport()) return false
        // Every call below is already a suspend call into Room or DataStore, which own their own
        // dispatchers, so there is no blocking work left to wrap in a `withContext`.
        importSettings()
        importReadingPosition()
        importDevotionCache()
        // Set after the writes, so a crash halfway through is retried on the next launch rather
        // than leaving a half-imported state marked as done.
        settingsRepository.markLegacyImportCompleted()
        return true
    }

    private suspend fun importSettings() {
        settingsRepository.replace(
            Settings.newBuilder()
                .setThemeMode(themeMode())
                .setNavbarStyle(navbarStyle().toProto())
                .setLocaleTag(locale().storageValue)
                .setGlassPerfBlocked(preferences.bool(KEY_GLASS_PERF_BLOCKED) ?: false)
                .setShowNavbar(preferences.bool(KEY_SHOW_NAVBAR) ?: true)
                .setShowDevotion(preferences.bool(KEY_SHOW_DEVOTION) ?: true)
                .build(),
        )
    }

    private fun themeMode(): Settings.ThemeMode = when (preferences.string(KEY_THEME_MODE)) {
        "light" -> Settings.ThemeMode.THEME_MODE_LIGHT
        "dark" -> Settings.ThemeMode.THEME_MODE_DARK
        // Missing or unrecognised: older Flutter builds only had the dark-mode switch, and a user
        // with neither key is better served by following the system than by being pinned to light.
        else -> appearanceDarkThemeMode() ?: Settings.ThemeMode.THEME_MODE_SYSTEM
    }

    private fun appearanceDarkThemeMode(): Settings.ThemeMode? = when (preferences.bool(KEY_APPEARANCE_DARK)) {
        true -> Settings.ThemeMode.THEME_MODE_DARK
        false -> Settings.ThemeMode.THEME_MODE_LIGHT
        null -> null
    }

    private fun navbarStyle(): NavBarStyle = NavBarStyle.fromStorage(preferences.string(KEY_NAVBAR_STYLE))

    private fun locale(): AppLocale = AppLocale.fromStorage(preferences.string(KEY_LOCALE))

    /**
     * Only the book the user was last reading can be resumed: Flutter kept one offset per
     * (book, chapter) while `reading_progress` holds one row per book, so the remaining offsets
     * have no home, and `SharedPreferences` stores no write order to say which one was newest.
     */
    private suspend fun importReadingPosition() {
        val bookId = preferences.string(KEY_READER_BOOK)?.takeIf { it.isNotBlank() } ?: return
        val book = bibleDao.book(bookId) ?: return
        val chapter = (preferences.int(KEY_READER_CHAPTER) ?: 1).coerceIn(1, book.chapters)
        readingProgressDao.upsert(
            ReadingProgressEntity(
                bookId = bookId,
                chapter = chapter,
                verse = null,
                // Left at 0 on purpose: the true value depends on the viewport, so a guess is never
                // persisted. The reader replaces it from `legacyScrollPx` on first layout.
                scrollRatio = 0f,
                mode = ReadingMode.fromStorage(preferences.string(KEY_READER_MODE)).storageValue,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        preferences.double(scrollKey(bookId, chapter))?.takeIf { it > 0 }?.let { scrollPx ->
            settingsRepository.rememberLegacyScrollPx(scrollPx)
        }
    }

    /**
     * Copied verbatim, exactly like the Flutter cache: a parser improvement re-reads the same raw
     * posts on the next launch instead of serving the blocks the old parser produced.
     */
    private suspend fun importDevotionCache() {
        val raw = preferences.string(KEY_DEVOTION_CACHE)?.takeIf { it.isNotBlank() } ?: return
        devotionCacheDao.upsert(
            DevotionCacheEntity(
                id = DEVOTION_CACHE_ID,
                rawPost = raw,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    internal companion object {
        const val KEY_THEME_MODE = "app_theme_mode"
        const val KEY_APPEARANCE_DARK = "appearance_dark"
        const val KEY_LOCALE = "app_locale"
        const val KEY_NAVBAR_STYLE = "navbar_style"
        const val KEY_GLASS_PERF_BLOCKED = "glass_perf_blocked"
        const val KEY_SHOW_NAVBAR = "show_navbar"
        const val KEY_SHOW_DEVOTION = "show_devotion"
        const val KEY_READER_BOOK = "reader_book"
        const val KEY_READER_CHAPTER = "reader_chapter"
        const val KEY_READER_MODE = "reader_mode"
        const val KEY_DEVOTION_CACHE = "devotion_cache_v1"
        const val DEVOTION_CACHE_ID = "devotion_cache_v1"

        /** Mirrors `_scrollKey` in `legacy/flutter/lib/main.dart`, which is `"$bookId-$chapter"`. */
        fun scrollKey(bookId: String, chapter: Int) = "reader_scroll_$bookId-$chapter"
    }
}

private fun NavBarStyle.toProto(): Settings.NavBarStyle = when (this) {
    NavBarStyle.MATERIAL -> Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL
    NavBarStyle.MATERIAL_BLUR -> Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL_BLUR
}
