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
) {
    /**
     * The name to show this book under, replacing `_bookName` at `legacy/flutter/lib/main.dart:40`.
     *
     * The reading mode decides first: English or bilingual reading names its books in English even
     * when the interface language is Chinese. Failing that, the interface language does, so a
     * Chinese reader who set the app to English is not left with a list of names they cannot read.
     *
     * It lives here rather than in either feature that draws it, because the reader's title and the
     * library's list are two surfaces of the same list of books and must not be able to disagree
     * about what a book is called.
     */
    fun displayName(mode: ReadingMode, usesEnglishUi: Boolean): String =
        if (mode != ReadingMode.CHINESE || usesEnglishUi) nameEn else nameZh
}

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
