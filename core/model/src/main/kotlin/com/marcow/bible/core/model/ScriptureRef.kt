package com.marcow.bible.core.model

/**
 * A scripture range handed to the AI layer, either as a chat attachment or as
 * the arguments of the `get_scripture` pseudo tool.
 *
 * The field names are the prompt contract: the model is asked to emit
 * `bookId`, `chapter`, `verseStart` and `verseEnd`.
 */
data class ScriptureRef(
    val bookId: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int = verseStart,
) {
    val isSingleVerse: Boolean get() = verseStart == verseEnd
}