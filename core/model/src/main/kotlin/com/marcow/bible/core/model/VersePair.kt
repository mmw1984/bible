package com.marcow.bible.core.model

/**
 * One verse with both translations aligned.
 *
 * Alignment is verbatim from the Flutter build (`BibleRepository.chapter()`):
 * a chapter has `max(cuv.size, web.size)` verses, and a translation that is
 * short for that verse gets an empty string, not null, so every caller can
 * treat the text as non-null.
 */
data class VersePair(
    val number: Int,
    val zh: String,
    val en: String,
) {
    val isEmpty: Boolean get() = zh.isEmpty() && en.isEmpty()
}
