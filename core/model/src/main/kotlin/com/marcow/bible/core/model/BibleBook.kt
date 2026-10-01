package com.marcow.bible.core.model

/**
 * One of the 66 canonical books.
 *
 * Mirrors `BibleBook` in the Flutter build (`legacy/flutter/lib/bible_data.dart`).
 * [nameZh] and [nameEn] come from that list rather than from the bundled
 * JSON, because the JSON headers disagree with the UI in places (for example
 * GEN is `創世紀` in `cuv/GEN.json` but `創世記` in the app).
 */
data class BibleBook(
    val id: String,
    val ordinal: Int,
    val nameZh: String,
    val nameEn: String,
    val chapters: Int,
    val testament: Testament,
) {
    fun chapterOrNull(chapter: Int): Int? = chapter.takeIf { it in 1..chapters }
}
