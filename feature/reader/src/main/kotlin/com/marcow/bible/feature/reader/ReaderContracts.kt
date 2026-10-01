package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.ReadingMode

/**
 * The four ways the reader moves, which is everything [ReaderScreen] asks of whatever holds the state.
 *
 * [ReaderViewModel] is one implementation, and implements it by declaring `override` on the four
 * methods that are already its own: wiring the screen needs no adapter, and a test can stand in
 * something smaller. Grouping them keeps [ReaderScreen] inside the parameter count detekt allows, and
 * states the contract in one place instead of scattering seven lambdas over its signature.
 */
interface ReaderNavigator {
    fun selectMode(mode: ReadingMode)

    fun selectChapter(chapter: Int)

    fun selectNextChapter()

    fun selectPreviousChapter()
}

/**
 * Where the reader is scrolled to, which [ReaderScreen] reports and [ReaderNavigator] does not carry.
 *
 * The three calls are the view model's `onScrolled`, `onScrollRestored` and `onChapterMeasured`
 * unchanged, and the split is deliberate: how a chapter is scrolled is a property of the surface it is
 * drawn on, while how the reader moves between chapters is a property of the reader.
 */
interface ReaderScrollSink {
    /** Called on every scroll frame; the view model debounces it into a single write. */
    fun onScrolled(ratio: Float)

    /** Called once the one-shot instruction in [ReaderUiState.scrollToRatio] has been jumped to. */
    fun onScrollRestored()

    /** Called with the chapter's scrollable extent, which is what converts a Flutter pixel offset. */
    fun onChapterMeasured(maxScrollPx: Float)
}
