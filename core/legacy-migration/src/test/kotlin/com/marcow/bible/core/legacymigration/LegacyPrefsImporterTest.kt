package com.marcow.bible.core.legacymigration

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.BookEntity
import com.marcow.bible.core.database.DevotionCacheDao
import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.core.database.ReadingProgressDao
import com.marcow.bible.core.database.ReadingProgressEntity
import com.marcow.bible.core.database.ScriptureSearchRow
import com.marcow.bible.core.database.VerseEntity
import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ReadingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyPrefsImporterTest {
    @Test
    fun `imports every Flutter setting and marks the import done`() = runTest {
        val harness = harness(
            FakePreferences(
                strings = mapOf(
                    LegacyPrefsImporter.KEY_THEME_MODE to "dark",
                    LegacyPrefsImporter.KEY_LOCALE to "en",
                    LegacyPrefsImporter.KEY_NAVBAR_STYLE to "material",
                ),
                bools = mapOf(
                    LegacyPrefsImporter.KEY_GLASS_PERF_BLOCKED to true,
                    LegacyPrefsImporter.KEY_SHOW_NAVBAR to false,
                ),
            ),
        )

        assertTrue(harness.importer.migrate())

        val settings = harness.settings.current()
        assertEquals(Settings.ThemeMode.THEME_MODE_DARK, settings.themeMode)
        assertEquals("en", settings.localeTag)
        assertEquals(Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL, settings.navbarStyle)
        assertTrue(settings.glassPerfBlocked)
        assertFalse(settings.showNavbar)
        // A key the user never set has to keep the Flutter default, not proto3's implicit false.
        assertTrue(settings.showDevotion)
        assertTrue(settings.legacyImported)
    }

    @Test
    fun `a missing theme mode falls back to appearance_dark`() = runTest {
        val harness = harness(
            FakePreferences(bools = mapOf(LegacyPrefsImporter.KEY_APPEARANCE_DARK to true)),
        )

        harness.importer.migrate()

        assertEquals(Settings.ThemeMode.THEME_MODE_DARK, harness.settings.current().themeMode)
    }

    @Test
    fun `appearance_dark false maps to light rather than to the system default`() = runTest {
        val harness = harness(
            FakePreferences(bools = mapOf(LegacyPrefsImporter.KEY_APPEARANCE_DARK to false)),
        )

        harness.importer.migrate()

        assertEquals(Settings.ThemeMode.THEME_MODE_LIGHT, harness.settings.current().themeMode)
    }

    @Test
    fun `a user with neither theme key follows the system`() = runTest {
        val harness = harness(FakePreferences())

        harness.importer.migrate()

        assertEquals(Settings.ThemeMode.THEME_MODE_SYSTEM, harness.settings.current().themeMode)
    }

    @Test
    fun `an unrecognised theme value still consults appearance_dark`() = runTest {
        val harness = harness(
            FakePreferences(
                strings = mapOf(LegacyPrefsImporter.KEY_THEME_MODE to "sepia"),
                bools = mapOf(LegacyPrefsImporter.KEY_APPEARANCE_DARK to true),
            ),
        )

        harness.importer.migrate()

        assertEquals(Settings.ThemeMode.THEME_MODE_DARK, harness.settings.current().themeMode)
    }

    @Test
    fun `an empty legacy file resolves to the Flutter controller defaults`() = runTest {
        val harness = harness(FakePreferences())

        harness.importer.migrate()

        val domain = harness.settings.settings.first()
        assertEquals(AppLocale.ZH_HANT, domain.locale)
        assertEquals(NavBarStyle.MATERIAL_BLUR, domain.navbarStyle)
        assertFalse(domain.glassPerfBlocked)
        assertTrue(domain.showNavbar)
        assertTrue(domain.showDevotion)
    }

    @Test
    fun `resumes the last read position and hands the pixel offset to the reader`() = runTest {
        val harness = harness(
            FakePreferences(
                strings = mapOf(
                    LegacyPrefsImporter.KEY_READER_BOOK to "PSA",
                    LegacyPrefsImporter.KEY_READER_MODE to "bilingual",
                ),
                ints = mapOf(LegacyPrefsImporter.KEY_READER_CHAPTER to 23),
                // Flutter's key is `reader_scroll_<bookId>-<chapter>`, and the offset arrives as a
                // double rather than a string.
                doubles = mapOf(LegacyPrefsImporter.scrollKey("PSA", 23) to 1420.5),
            ),
        )

        harness.importer.migrate()

        val progress = harness.progress.rows.single()
        assertEquals("PSA", progress.bookId)
        assertEquals(23, progress.chapter)
        assertNull(progress.verse)
        assertEquals(ReadingMode.BILINGUAL.storageValue, progress.mode)
        // The ratio depends on a viewport that does not exist yet, so it must not be guessed.
        assertEquals(0f, progress.scrollRatio)
        assertTrue(progress.updatedAt > 0)
        assertEquals(1420.5, harness.settings.pendingLegacyScrollPx())
    }

    @Test
    fun `an unknown book id is dropped instead of breaking the foreign key`() = runTest {
        val harness = harness(
            FakePreferences(
                strings = mapOf(LegacyPrefsImporter.KEY_READER_BOOK to "XYZ"),
                ints = mapOf(LegacyPrefsImporter.KEY_READER_CHAPTER to 1),
            ),
        )

        assertTrue(harness.importer.migrate())

        assertTrue(harness.progress.rows.isEmpty())
        assertNull(harness.settings.pendingLegacyScrollPx())
    }

    @Test
    fun `a chapter past the end of the book is clamped into range`() = runTest {
        val harness = harness(
            FakePreferences(
                strings = mapOf(LegacyPrefsImporter.KEY_READER_BOOK to "PSA"),
                ints = mapOf(LegacyPrefsImporter.KEY_READER_CHAPTER to 5000),
            ),
        )

        harness.importer.migrate()

        assertEquals(150, harness.progress.rows.single().chapter)
        // The offset was saved for chapter 5000, which is not the chapter now open, so it must not
        // be applied to chapter 150.
        assertNull(harness.settings.pendingLegacyScrollPx())
    }

    @Test
    fun `the devotion cache is copied as raw json`() = runTest {
        val raw = """[{"id":7,"content":"<p>x</p>"}]"""
        val harness = harness(
            FakePreferences(strings = mapOf(LegacyPrefsImporter.KEY_DEVOTION_CACHE to raw)),
        )

        harness.importer.migrate()

        val entry = harness.devotion.rows.single()
        assertEquals(LegacyPrefsImporter.DEVOTION_CACHE_ID, entry.id)
        assertEquals(raw, entry.rawPost)
        assertTrue(entry.fetchedAt > 0)
    }

    @Test
    fun `a second launch does not overwrite what the user changed in the native app`() = runTest {
        val harness = harness(
            FakePreferences(strings = mapOf(LegacyPrefsImporter.KEY_LOCALE to "en")),
        )

        assertTrue(harness.importer.migrate())
        harness.settings.setLocale(AppLocale.ZH_HANT)

        assertFalse(harness.importer.migrate())
        assertEquals("zh-Hant", harness.settings.current().localeTag)
    }

    private fun harness(preferences: LegacyPreferenceSource): Harness {
        // `DataStoreFactory.create` has no file-less overload — it wants a `produceFile` alongside
        // the `serializer` — so the in-memory store is `InMemorySettingsDataStore`, which this
        // module now builds from core:datastore's test fixtures. It is the real DataStore interface
        // and the real protobuf `Serializer`, minus the file and the background actor.
        val settings = SettingsRepository(InMemorySettingsDataStore())
        val progress = FakeReadingProgressDao()
        val devotion = FakeDevotionCacheDao()
        return Harness(
            settings = settings,
            progress = progress,
            devotion = devotion,
            importer = LegacyPrefsImporter(
                preferences = preferences,
                settingsRepository = settings,
                bibleDao = FakeBibleDao,
                readingProgressDao = progress,
                devotionCacheDao = devotion,
            ),
        )
    }

    private class Harness(
        val settings: SettingsRepository,
        val progress: FakeReadingProgressDao,
        val devotion: FakeDevotionCacheDao,
        val importer: LegacyPrefsImporter,
    )
}

private class FakePreferences(
    private val strings: Map<String, String> = emptyMap(),
    private val ints: Map<String, Int> = emptyMap(),
    private val bools: Map<String, Boolean> = emptyMap(),
    private val doubles: Map<String, Double> = emptyMap(),
) : LegacyPreferenceSource {
    override fun string(key: String): String? = strings[key]

    override fun int(key: String): Int? = ints[key]

    override fun bool(key: String): Boolean? = bools[key]

    override fun double(key: String): Double? = doubles[key]
}

private object FakeBibleDao : BibleDao {
    private val books = listOf(
        BookEntity(id = "GEN", ordinal = 1, nameZh = "創", nameEn = "Gen", chapters = 50, testament = 0),
        BookEntity(id = "PSA", ordinal = 19, nameZh = "詩", nameEn = "Psa", chapters = 150, testament = 0),
    )

    override suspend fun books(): List<BookEntity> = books

    override suspend fun book(book: String): BookEntity? = books.firstOrNull { it.id == book }

    override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> = emptyList()

    override suspend fun versesByTestament(testament: Int): List<VerseEntity> = emptyList()

    override suspend fun booksByTestament(testament: Int): List<BookEntity> = books.filter { it.testament == testament }

    override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
}

private class FakeReadingProgressDao : ReadingProgressDao {
    val rows = mutableListOf<ReadingProgressEntity>()

    override suspend fun progress(book: String): ReadingProgressEntity? = rows.firstOrNull { it.bookId == book }

    override suspend fun allProgress(): List<ReadingProgressEntity> = rows.toList()

    override suspend fun upsert(progress: ReadingProgressEntity) {
        rows.removeAll { it.bookId == progress.bookId }
        rows += progress
    }

    override suspend fun delete(book: String) {
        rows.removeAll { it.bookId == book }
    }
}

private class FakeDevotionCacheDao : DevotionCacheDao {
    val rows = mutableListOf<DevotionCacheEntity>()

    override suspend fun entry(id: String): DevotionCacheEntity? = rows.firstOrNull { it.id == id }

    override suspend fun upsert(entry: DevotionCacheEntity) {
        rows.removeAll { it.id == entry.id }
        rows += entry
    }

    override suspend fun clear() {
        rows.clear()
    }
}
