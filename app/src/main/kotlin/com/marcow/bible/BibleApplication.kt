package com.marcow.bible

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt dependency graph root.
 *
 * Replaces the Flutter embedding's `android:name="${applicationName}"` placeholder.
 */
@HiltAndroidApp
class BibleApplication : Application()
