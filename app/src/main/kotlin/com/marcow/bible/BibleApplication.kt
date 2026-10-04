package com.marcow.bible

import android.app.Application
import com.marcow.bible.startup.LegacyImportStartup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point and Hilt dependency graph root.
 *
 * Replaces the Flutter embedding's `android:name="${applicationName}"` placeholder.
 */
@HiltAndroidApp
class BibleApplication : Application() {
    @Inject
    lateinit var legacyImportStartup: LegacyImportStartup

    override fun onCreate() {
        super.onCreate()
        legacyImportStartup.start()
    }
}
