package com.marcow.bible.core.model

/**
 * How a chapter renders: Chinese only, English only, or both.
 *
 * [storageKey] is persisted (in DataStore and in `reading_progress.mode`), so
 * the strings are part of the on-disk format.
 */
enum class ReadingMode(val storageKey: String) {
    CHINESE("chinese"),
    ENGLISH("english"),
    BILINGUAL("bilingual"),
    ;

    companion object {
        /** Chinese-only is the Flutter default (`legacy/flutter/lib/main.dart:228`). */
        val DEFAULT = CHINESE

        fun fromStorageKey(key: String?): ReadingMode =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
