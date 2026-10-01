package com.marcow.bible.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The reader's position and the verses under it, replacing the `_BibleHomeState` that
 * `legacy/flutter/lib/main.dart:33` kept in a `StatefulWidget`.
 *
 * Two Flutter details are load-bearing here and are kept as they were:
 *
 * **Writes are queued, not immediate.** Flutter dropped every scroll event into a `scrollSaveQueue`
 * of `Future`s, replaced the pending 180 ms timer on each one, and flushed the queue whenever the
 * reader navigated away or the app was backgrounded. Writing on every scroll frame instead would turn
 * reading into a write storm — one `reading_progress` upsert per frame of finger movement — and the
 * debounce is what makes a chapter's position durable once the reader stops.
 *
 * **Both halves of a move are written.** `_beforeUserNavigation()` saved the pixel offset of the
 * chapter being read and `_load(persist: true)` stored the book, chapter and mode being opened, so
 * [stepTo] writes the chapter being left before the one being opened. Flutter kept those in two
 * places — `reader_scroll_<BOOK>-<CHAPTER>` and `reader_book` — where this is one row per book, which
 * is why the chapter order and its offset travel together.
 *
 * It declares [ReaderNavigator] and [ReaderScrollSink] rather than being adapted to them: the seven
 * methods the screen calls already exist with these exact names and meanings, so the screen is wired
 * by handing it the view model, and a test can hand it something smaller instead.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel(), ReaderNavigator, ReaderScrollSink {
    /** Canon order, loaded once: the reader navigates by index and every direction depends on it. */
    private val books = mutableListOf<BibleBook>()
    private val writes = Channel<ReaderPosition>(Channel.UNLIMITED)
    private var position = ReaderPosition(FIRST_BOOK_INDEX, FIRST_CHAPTER, ReadingMode.CHINESE, 0f)

    /** The live offset, which outruns [position] until it settles. */
    private var scrollRatio = 0f
    private var scrollSave: Job? = null

    /** A write the repository refused, retried by the next flush rather than dropped. */
    private var pendingWrite: ReaderPosition? = null

    /** Whether the imported Flutter pixel offset has been converted, so it is only ever applied once. */
    private var legacyScrollApplied = false

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        // A single writer for the lifetime of the reader: Room's writes are serialised by the
        // transaction executor anyway, and one consumer means the queue's order is the order written.
        viewModelScope.launch {
            for (write in writes) save(write)
        }
        viewModelScope.launch { openLastPosition() }
        viewModelScope.launch { watchInterfaceLanguage() }
    }

    /**
     * Opens [bookId] at its first chapter in Chinese, which is what selecting a book in the library
     * did — `chapter: 1, mode: ReadingMode.CHINESE` in `_BibleHomeState`.
     */
    fun selectBook(bookId: String) {
        val index = books.indexOfFirst { it.id == bookId }
        if (index < 0 || index == position.bookIndex) return
        stepTo(position.movedTo(index, FIRST_CHAPTER).copy(mode = ReadingMode.CHINESE))
    }

    /**
     * Opens [chapter], clamped to the book's chapter count.
     *
     * Clamping rather than ignoring keeps the chapter links at the ends of a book — and the chapter
     * picker itself, which cannot offer one — from leaving the reader on the chapter they are already
     * on.
     */
    override fun selectChapter(chapter: Int) {
        val book = books.getOrNull(position.bookIndex) ?: return
        val clamped = clampChapter(book.chapters, chapter)
        if (clamped == position.chapter) return
        stepTo(position.movedTo(position.bookIndex, clamped))
    }

    override fun selectNextChapter() = stepTo(nextPosition(books, position))

    override fun selectPreviousChapter() = stepTo(previousPosition(books, position))

    /**
     * Switches between Chinese, English and bilingual without reloading the chapter.
     *
     * Both translations come out of the same row, so the Flutter reader only re-rendered — but it
     * did persist the position, since the mode is part of where the reader was.
     */
    override fun selectMode(mode: ReadingMode) {
        if (mode == position.mode) return
        // Flutter wrote twice here: the outgoing pixel offset, then `reader_mode`. Both are the same
        // row now, so one write carries the mode and the offset together.
        position = position.copy(mode = mode, scrollRatio = scrollRatio)
        _state.update { it.copy(mode = mode) }
        writes.trySend(position)
    }

    /**
     * Reports where the reader is now, debounced into a single write.
     *
     * Called on every scroll frame and does nothing else: the ratio is remembered in [scrollRatio]
     * so a navigation that arrives mid-gesture still writes the position actually being left.
     */
    override fun onScrolled(ratio: Float) {
        scrollRatio = ratio.coerceIn(0f, 1f)
        scrollSave?.cancel()
        scrollSave = viewModelScope.launch {
            delay(SCROLL_SAVE_DEBOUNCE_MS)
            writes.trySend(position.copy(scrollRatio = scrollRatio))
        }
    }

    /** Consumes the one-shot scroll instruction once the screen has jumped to it. */
    override fun onScrollRestored() {
        _state.update { it.copy(scrollToRatio = null) }
    }

    /**
     * Writes the position now, for the cases Flutter flushed on: navigating away (handled here by
     * [stepTo]) and the app going to the background, which the host reports by calling this from
     * `Lifecycle.Event.ON_PAUSE` — the same event Flutter's `AppLifecycleListener` listened for.
     *
     * Not written from `onCleared`: by then the view model's scope has already been cancelled, so a
     * queued write would never reach the database.
     */
    fun savePosition() {
        scrollSave?.cancel()
        val write = pendingWrite ?: position.copy(scrollRatio = scrollRatio)
        pendingWrite = null
        writes.trySend(write)
    }

    /**
     * Converts the pixel offset an upgraded Flutter install left behind, now that this chapter's
     * scrollable extent is known.
     *
     * A pixel offset cannot be converted without the viewport it was measured in, so this is called
     * after the chapter has been laid out and is ignored until then. The offset is cleared as soon as
     * it has been turned into a ratio, which is what `LegacyPrefsImporter` relies on to apply it
     * exactly once.
     */
    override fun onChapterMeasured(maxScrollPx: Float) {
        if (legacyScrollApplied || maxScrollPx <= 0f) return
        legacyScrollApplied = true
        viewModelScope.launch {
            val pixels = settingsRepository.pendingLegacyScrollPx() ?: return@launch
            _state.update { it.copy(scrollToRatio = legacyScrollRatio(pixels, maxScrollPx)) }
            settingsRepository.clearPendingLegacyScroll()
        }
    }

    /**
     * Moves to [target]: writes the chapter being left, writes the one being opened, then loads it.
     *
     * The two writes are Flutter's `_beforeUserNavigation()` and `_load(persist: true)` — an offset
     * for the chapter that had been read and a position for the one now open. Merged into a single
     * row per book they land as one flush plus one queued write, in that order, and the channel
     * keeps them there.
     */
    private fun stepTo(target: ReaderPosition?) {
        if (target == null) return
        savePosition()
        position = target
        scrollRatio = target.scrollRatio
        writes.trySend(target)
        viewModelScope.launch { open(target) }
    }

    /**
     * Watches the interface language, mirroring the ambient settings lookup behind Flutter's
     * `_bookName`.
     *
     * Watched rather than read once because the language can be changed from Settings while the
     * reader is open, and Flutter's title followed it without a reload. Nothing is written: a
     * language is not a position, so the row the reader is on stays as it was left.
     */
    private suspend fun watchInterfaceLanguage() {
        settingsRepository.settings
            .map { it.locale == AppLocale.EN }
            .distinctUntilChanged()
            .collect { english ->
                _state.update {
                    if (it.usesEnglishUi == english) it else it.copy(usesEnglishUi = english)
                }
            }
    }

    /**
     * Reopens the most recently read position, or the beginning of the Bible when there is none.
     *
     * Flutter read `reader_book` / `reader_chapter` / `reader_mode` from `SharedPreferences`; the
     * equivalent here is the newest `reading_progress` row, since the repository stores one row per
     * book. That costs one primary-key read per book, once, at open.
     */
    private suspend fun openLastPosition() {
        books.clear()
        books += bibleRepository.books()
        if (books.isEmpty()) {
            _state.update { it.copy(loading = false, failed = true) }
            return
        }
        val target = mostRecentPosition(books) { bibleRepository.progress(it.id) } ?: position
        open(target, restoreScroll = true)
    }

    private suspend fun open(target: ReaderPosition, restoreScroll: Boolean = false) {
        val book = books.getOrNull(target.bookIndex) ?: return
        position = target
        scrollRatio = target.scrollRatio
        _state.update {
            it.copy(
                book = book,
                chapter = target.chapter,
                mode = target.mode,
                // The previous chapter is dropped before the next is fetched: the title above the
                // verses has already moved, and Flutter's `FutureBuilder` did the same by handing
                // the state a new future.
                verses = emptyList(),
                loading = true,
                failed = false,
                scrollToRatio = null,
            )
        }
        try {
            val verses = bibleRepository.chapter(book.id, target.chapter)
            _state.update {
                it.copy(
                    verses = verses,
                    loading = false,
                    scrollToRatio = if (restoreScroll) target.scrollRatio.takeIf { it > 0f } else null,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, failed = true) }
        }
    }

    private suspend fun save(write: ReaderPosition) {
        val book = books.getOrNull(write.bookIndex) ?: return
        try {
            bibleRepository.saveProgress(progressFor(book, write, System.currentTimeMillis()))
            pendingWrite = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Flutter's queue reported the failure and kept the write for the next flush rather than
            // losing the reader's place, so the row is retried by the next scroll or navigation.
            pendingWrite = write
        }
    }

    private companion object {
        /** Genesis 1, the position a fresh install opens at. */
        const val FIRST_BOOK_INDEX = 0
        const val FIRST_CHAPTER = 1

        /** Flutter's `Timer(180)`, long enough to outlast a fling without writing every frame. */
        const val SCROLL_SAVE_DEBOUNCE_MS = 180L
    }
}
