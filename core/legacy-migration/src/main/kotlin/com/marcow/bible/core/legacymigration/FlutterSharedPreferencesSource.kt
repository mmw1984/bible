package com.marcow.bible.core.legacymigration

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the `FlutterSharedPreferences.xml` file that `shared_preferences_android` wrote for the
 * Flutter app, so the native app can be installed over it without losing the user's choices.
 *
 * The `applicationId` and signing key are unchanged between the two apps, so this is the file the
 * Flutter app was already using. Two things about its format matter: the plugin stores every entry
 * under a `flutter.` prefix, and it has no way to store a double, so a double is written as a
 * *string* carrying a base64 marker (see [parseLegacyDouble]).
 */
@Singleton
class FlutterSharedPreferencesSource @Inject constructor(@ApplicationContext context: Context) :
    LegacyPreferenceSource {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun string(key: String): String? = preferences.all[prefixed(key)] as? String

    override fun int(key: String): Int? = when (val value = preferences.all[prefixed(key)]) {
        is Int -> value
        // `setInt` round-trips through a long, so an int written by the plugin arrives as a Long.
        is Long -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    override fun bool(key: String): Boolean? = preferences.all[prefixed(key)] as? Boolean

    override fun double(key: String): Double? = parseLegacyDouble(preferences.all[prefixed(key)])

    private fun prefixed(key: String) = "$KEY_PREFIX$key"

    private companion object {
        /** `SHARED_PREFERENCES_NAME` in shared_preferences_android. */
        const val SHARED_PREFERENCES_NAME = "FlutterSharedPreferences"
    }
}

/** The prefix `SharedPreferences` in the Dart package adds to every key it writes. */
private const val KEY_PREFIX = "flutter."

/**
 * `DOUBLE_PREFIX` in shared_preferences_android: the base64 of "This is the prefix for a Double.",
 * followed by the decimal text of the value.
 */
private const val DOUBLE_MARKER = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

/**
 * Decodes the several shapes a `SharedPreferences` entry can have into a double.
 *
 * The plugin's `setDouble` writes a string, which is why a plain `getFloat` finds nothing, and a
 * number is accepted as well for the case where something wrote the key natively. Returns null for
 * anything unparseable, so a corrupt entry is skipped instead of silently becoming 0.
 */
internal fun parseLegacyDouble(value: Any?): Double? = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Int -> value.toDouble()
    is Long -> value.toDouble()
    is String -> value.removePrefix(DOUBLE_MARKER).toDoubleOrNull() ?: value.toDoubleOrNull()
    else -> null
}
