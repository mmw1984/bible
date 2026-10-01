package com.marcow.bible.core.legacymigration

/**
 * Read-only view of the legacy Flutter `SharedPreferences` file.
 *
 * The importer talks to this instead of `SharedPreferences` directly so the key mapping can be unit
 * tested on the JVM: `SharedPreferences` needs a `Context`, and CI has no emulator to run it on.
 */
interface LegacyPreferenceSource {
    fun string(key: String): String?

    fun int(key: String): Int?

    fun bool(key: String): Boolean?

    fun double(key: String): Double?
}
