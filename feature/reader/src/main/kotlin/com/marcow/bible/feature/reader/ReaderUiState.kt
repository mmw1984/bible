package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.VersePair

/**
 * Everything the reader screen renders, mirroring the fields `_Reader` was handed in
 * `legacy/flutter/lib/main.dart:1063`.
 *
 * [verses] is empty while [loading] is true, even though a chapter is already loaded, and that is
 * deliberate rather than incidental: Flutter's `FutureBuilder` dropped the previous chapter's data
 * the moment a replacement future was handed to it, because painting Genesis under the title
 * "Exodus 2" is worse than painting a skeleton. The same rule applies here, so [book] and [chapter]
 * have moved on while [verses] has not.
 *
 * [scrollToRatio] is a one-shot instruction rather than state to hold: the screen jumps to it once
 * the chapter has been laid out and tells the view model it has been consumed, which is what keeps a
 * rotation from throwing the reader back to the top of the chapter they had scrolled down.
 */
data class ReaderUiState(
    val book: BibleBook? = null,
    val chapter: Int = 1,
    val mode: ReadingMode = ReadingMode.CHINESE,
    val verses: List<VersePair> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val scrollToRatio: Float? = null,
) {
    /** How many chapters the current book has, which is what the chapter picker offers. */
    val chapterCount: Int
        get() = book?.chapters ?: 0
}