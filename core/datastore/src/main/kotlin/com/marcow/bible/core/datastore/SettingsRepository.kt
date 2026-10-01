package com.marcow.bible.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.marcow.bible.core.datastore.proto.Settings
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.NavBarStyle
import com.marcow.bible.core.model.ThemeMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton

private const val SETTINGS_FILE = "settings.preferences_pb"

private val SettingsSerializer = object : Serializer<Settings> {
    // An empty message, not a throw: a fresh install and a wiped file both land on the defaults,
    // which is what Settings.toDomain() then resolves against.
    override val defaultValue: Settings = Settings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Settings =
        runCatching { Settings.parseFrom(input) }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        t.writeTo(output)
    }
}

private val CorruptSettingsHandler = ReplaceFileCorruptionHandler { Settings.getDefaultInstance() }

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Settings> =
        DataStoreFactory.create(
            // 1.2.x dropped the `create(context = …, fileName = …)` overload from
            // `datastore-core` (the `androidx.datastore:datastore` artifact is now an empty
            // relocation), so the path is spelled out here. `filesDir/datastore` is the same
            // location `preferencesDataStoreFile` uses.
            produceFile = { File(context.filesDir, "datastore/$SETTINGS_FILE") },
            serializer = SettingsSerializer,
            corruptionHandler = CorruptSettingsHandler,
        )

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Settings>): SettingsRepository =
        SettingsRepository(dataStore)
}

/**
 * Reads and writes the app settings, replacing the Flutter `AppSettingsController`.
 *
 * The exposed [settings] flow always carries a fully resolved [AppSettings]: proto3 leaves unset
 * fields at their zero value, so the mapping from the generated [Settings] message lives in one
 * place ([toDomain]) and every caller sees the same defaults the Flutter app used.
 */
class SettingsRepository(private val dataStore: DataStore<Settings>) {
    val settings: Flow<AppSettings> = dataStore.data.map { it.toDomain() }

    val rawSettings: Flow<Settings> = dataStore.data

    suspend fun current(): Settings = dataStore.data.first()

    /** Merges [transform] into the stored message, leaving untouched fields alone. */
    private suspend fun update(transform: (Settings) -> Settings) {
        dataStore.updateData(transform)
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { current ->
        current.toBuilder()
            .setThemeMode(
                when (mode) {
                    ThemeMode.LIGHT -> Settings.ThemeMode.THEME_MODE_LIGHT
                    ThemeMode.DARK -> Settings.ThemeMode.THEME_MODE_DARK
                    ThemeMode.SYSTEM -> Settings.ThemeMode.THEME_MODE_SYSTEM
                },
            )
            .build()
    }

    suspend fun setLocale(locale: AppLocale) = update { current ->
        current.toBuilder().setLocaleTag(locale.storageValue).build()
    }

    /**
     * Picking a style also clears [AppSettings.glassPerfBlocked], matching
     * `AppSettingsController.setNavbarStyle` in the Flutter app: the flag means "blur is not
     * wanted", so choosing a style is the user overriding whatever the runtime decided.
     */
    suspend fun setNavbarStyle(style: NavBarStyle) = update { current ->
        current.toBuilder()
            .setNavbarStyle(
                when (style) {
                    NavBarStyle.MATERIAL -> Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL
                    NavBarStyle.MATERIAL_BLUR -> Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL_BLUR
                },
            )
            .setGlassPerfBlocked(false)
            .build()
    }

    suspend fun setGlassPerfBlocked(blocked: Boolean) = update { current ->
        current.toBuilder().setGlassPerfBlocked(blocked).build()
    }

    suspend fun setShowNavbar(show: Boolean) = update { current ->
        current.toBuilder().setShowNavbar(show).build()
    }

    suspend fun setShowDevotion(show: Boolean) = update { current ->
        current.toBuilder().setShowDevotion(show).build()
    }

    /** Writes the whole message. Used by `LegacyPrefsImporter` and by tests. */
    suspend fun replace(settings: Settings) = update { settings }

    suspend fun hasCompletedLegacyImport(): Boolean = current().legacyImported

    suspend fun markLegacyImportCompleted() = update { current ->
        current.toBuilder().setLegacyImported(true).build()
    }

    /**
     * The absolute pixel offset the Flutter reader last used for the imported chapter, waiting to be
     * turned into a `scroll_ratio` once the viewport is measured, or null when there is nothing to
     * convert.
     */
    suspend fun pendingLegacyScrollPx(): Double? = current().takeIf { it.hasLegacyScrollPx() }?.legacyScrollPx

    suspend fun rememberLegacyScrollPx(scrollPx: Double) = update { current ->
        current.toBuilder().setLegacyScrollPx(scrollPx).build()
    }

    suspend fun clearPendingLegacyScroll() = update { current ->
        current.toBuilder().clearLegacyScrollPx().build()
    }
}

/** Resolves the proto message against the defaults the Flutter app used. */
fun Settings.toDomain(): AppSettings = AppSettings(
    themeMode = when (themeMode) {
        Settings.ThemeMode.THEME_MODE_DARK -> ThemeMode.DARK
        Settings.ThemeMode.THEME_MODE_SYSTEM -> ThemeMode.SYSTEM
        // UNSPECIFIED and LIGHT both fall back to light, which is `AppSettingsController`'s
        // default state.
        else -> ThemeMode.LIGHT
    },
    locale = AppLocale.fromStorage(localeTag.takeIf { it.isNotEmpty() }),
    navbarStyle = when (navbarStyle) {
        Settings.NavBarStyle.NAV_BAR_STYLE_MATERIAL -> NavBarStyle.MATERIAL
        else -> NavBarStyle.MATERIAL_BLUR
    },
    glassPerfBlocked = glassPerfBlocked,
    // `hasShowNavbar()` is false when the field was never written, which is exactly the case where
    // the Flutter default of true should apply.
    showNavbar = if (hasShowNavbar()) showNavbar else true,
    showDevotion = if (hasShowDevotion()) showDevotion else true,
)
