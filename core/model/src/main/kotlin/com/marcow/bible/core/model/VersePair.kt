package com.marcow.bible.core.model

/**
 * One verse with both translations, mirroring `VersePair` in
 * `legacy/flutter/lib/bible_data.dart`.
 *
 * The pairing rule is fixed by the Flutter reader: a chapter has `max(cuv.size, web.size)` rows
 * and a missing index inside a translation becomes an empty string, never a dropped row. Native
 * code goes through [com.marcow.bible.core.database.VerseEntity.toDomain] so that behaviour is
 * applied once, in the database layer.
 */
data class VersePair(val number: Int, val zh: String, val en: String)

/** A single search result, mirroring `ScriptureHit` in the Flutter `bible_data.dart`. */
data class ScriptureHit(val book: BibleBook, val chapter: Int, val verse: VersePair)

/** Reading mode, mirroring `ReadingMode` in `legacy/flutter/lib/main.dart:33`. */
enum class ReadingMode(val storageValue: String) {
    CHINESE("chinese"),
    ENGLISH("english"),
    BILINGUAL("bilingual"),
    ;

    /** Flutter shows the Chinese column when this is false. */
    val showsChinese: Boolean
        get() = this != ENGLISH

    /** Flutter shows the English column when this is false. */
    val showsEnglish: Boolean
        get() = this != CHINESE

    /** Matches the legacy preference read in `legacy/flutter/lib/main.dart:501`. */
    companion object {
        fun fromStorage(value: String?): ReadingMode = entries.firstOrNull { it.storageValue == value } ?: CHINESE
    }
}
