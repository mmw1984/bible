package com.marcow.bible.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The list of books the panel draws, replacing the lookups `_LibraryPanelState` did in its `init`
 * and `_bookName` did on every build.
 *
 * It is much smaller than [com.marcow.bible.feature.reader.ReaderViewModel] on purpose. The panel has
 * no position of its own to keep: which testament is showing is one boolean of composition state, and
 * the selected book is a parameter the host already holds, because it is the one the reader is on.
 * All that is left is a read of the 66 books and of the interface language, and both are already
 * established patterns — the reader loads its books from [BibleRepository] in canon order, and reads
 * the same locale out of [SettingsRepository] so a name repaints when the language changes.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bibleRepository: BibleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        loadBooks()
        watchInterfaceLanguage()
    }

    /**
     * Loads all 66 books rather than one testament.
     *
     * The panel filters in memory, so a switch between the Old and New Testament is instant — Flutter's
     * `bibleBooks` was a compile-time constant and the `where` ran on every build. Room's `books()`
     * is a single ordered read of a table that is seeded once and never written again, so paying for
     * it here rather than per testament costs nothing that matters.
     */
    private fun loadBooks() {
        viewModelScope.launch {
            val books = try {
                bibleRepository.books()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Flutter's panel could not fail, because its list was a constant in the binary. A
                // panel with nothing in it is still a panel the reader can close, so the failure is
                // a state rather than a crash: an empty list under the title, not a blank screen.
                return@launch
            }
            _state.update { it.copy(books = books, loading = false) }
        }
    }

    /**
     * Whether the interface is in English, so a book is named in the language the reader is reading
     * the rest of the screen in.
     *
     * This is the same subscription the reader keeps, and for the same reason: Flutter read the
     * ambient settings on every build, so changing the language in Settings relabelled the books
     * without anything else moving. Only the name changes here — the list is not refetched, because
     * a name is not text.
     */
    private fun watchInterfaceLanguage() {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.locale == AppLocale.EN }
                .distinctUntilChanged()
                .collect { english ->
                    _state.update {
                        if (it.usesEnglishUi == english) it else it.copy(usesEnglishUi = english)
                    }
                }
        }
    }
}
