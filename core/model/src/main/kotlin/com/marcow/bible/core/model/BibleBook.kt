package com.marcow.bible.core.model

/**
 * Domain model for a Bible book, mirroring `BibleBook` in `legacy/flutter/lib/bible_data.dart`.
 *
 * [id] is the stable key used by the `books` / `verses` tables (`'GEN'` … `'REV'`). [ordinal] is the
 * 1-based canon position and is what every "ORDER BY" in the DAO uses, so it must stay stable.
 */
data class BibleBook(
    val id: String,
    val ordinal: Int,
    val nameZh: String,
    val nameEn: String,
    val chapters: Int,
    val testament: Testament,
)

enum class Testament {
    OLD,
    NEW,
    ;

    /** Wire value stored in the `books.testament` column (`0` = 舊約, `1` = 新約). */
    val storageValue: Int
        get() = if (this == OLD) 0 else 1

    companion object {
        fun fromStorage(value: Int): Testament = if (value == 1) NEW else OLD
    }
}
