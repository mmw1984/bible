package com.marcow.bible.core.model

/** One search result: where a match was found and the verse that matched. */
data class ScriptureHit(
    val book: BibleBook,
    val chapter: Int,
    val verse: VersePair,
)