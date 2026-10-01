package com.marcow.bible.feature.devotion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.network.devotion.DevotionFetchException
import com.marcow.bible.feature.devotion.data.DevotionRepository
import com.marcow.bible.feature.devotion.domain.DevotionPost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * What the devotion page knows, replacing `_DevotionPageState` in
 * `legacy/flutter/lib/devotion_page.dart`.
 *
 * The Dart state was a widget's `setState`, and every rule it carried came out of it being able to
 * read `context` and `mounted`. Here the same decisions live in [load], and the two that are worth
 * naming are about *which* load is happening:
 *
 *  - A **silent** load is the background one — the poll every thirty minutes, and the refresh on
 *    resume. It never turns a readable page into a spinner, never clears an error the reader is
 *    already looking at, and never moves the reader off the post they are on. Everything else is a
 *    load they asked for.
 *  - The cache is painted before the network is touched, and again if the network fails, which is why
 *    a device that is offline since yesterday still opens on yesterday's devotion instead of an error.
 */
@HiltViewModel
class DevotionViewModel @Inject constructor(
    private val repository: DevotionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DevotionUiState())
    val state: StateFlow<DevotionUiState> = _state.asStateFlow()

    /**
     * When the feed was last fetched successfully, kept here rather than in the state.
     *
     * The screen never wanted the timestamp, only whether there was one, and [DevotionUiState.loadedOnce]
     * is that. What the thirty-minute rule needs is the value.
     */
    private var lastFetch: LocalDateTime? = null

    init {
        refresh()
        watchInterfaceLanguage()
    }

    /** Opens the post at [index], as a date chip tap did. */
    fun select(index: Int) {
        _state.update { it.copy(selected = clamp(index, it.posts.size)) }
    }

    /**
     * Fetches because the reader asked: pull to refresh, the refresh button, the retry button.
     *
     * This is the only load that shows a spinner over content the reader can already read.
     */
    fun refresh() = load(silent = false)

    /**
     * Fetches if the feed has gone stale, as the thirty-minute poll and the resume handler did.
     *
     * Deliberately silent: this fires while the reader is reading, and a spinner or an error appearing
     * under an article that is already on screen would be worse than the staleness.
     */
    fun refreshIfStale(now: LocalDateTime = LocalDateTime.now()) {
        if (shouldRefresh(now)) load(silent = true)
    }

    /**
     * Whether the feed is due a background refresh.
     *
     * Two rules, both from `_shouldRefresh`. Half an hour is how long a day's devotion can sit before
     * it is worth asking again, and a different calendar day always is — the blog posts one devotion
     * per day, so at midnight the answer changes even if the last fetch was a minute ago.
     */
    internal fun shouldRefresh(now: LocalDateTime): Boolean {
        val last = lastFetch ?: return true
        if (Duration.between(last, now) > STALE_AFTER) return true
        return last.toLocalDate() != now.toLocalDate()
    }

    /** `_load`, with [silent] being the `silent:` argument it was called with. */
    private fun load(silent: Boolean) {
        viewModelScope.launch {
            val hasContent = _state.value.posts.isNotEmpty()
            if (!silent || !hasContent) {
                _state.update { it.copy(loading = true, failed = false, failureDetail = null) }
            }
            // Cold start: the last fetch that worked is painted before the network is asked at all, so
            // the page is readable even if the round-trip never returns.
            if (!hasContent && !silent) paintCachedPosts()
            val fetched = try {
                repository.fetchPosts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DevotionFetchException) {
                onFetchFailed(hasContent, silent, failure)
                return@launch
            }
            val previousId = if (hasContent) {
                val before = _state.value
                before.posts.getOrNull(before.selected)?.id
            } else {
                null
            }
            // A silent refresh that succeeds must leave the reader on the post they were reading: the
            // new feed is for the same day but not necessarily in the same place.
            val index = if (silent && previousId != null) {
                fetched.indexOfFirst { it.id == previousId }.takeIf { it >= 0 } ?: todayIndex(fetched)
            } else {
                todayIndex(fetched)
            }
            lastFetch = LocalDateTime.now()
            _state.update {
                it.copy(
                    posts = fetched,
                    selected = clamp(index, fetched.size),
                    loading = false,
                    failed = false,
                    failureDetail = null,
                    loadedOnce = true,
                )
            }
        }
    }

    /** The cold-start paint, and the `if (cached.isNotEmpty && mounted && posts.isEmpty)` guard on it. */
    private suspend fun paintCachedPosts() {
        val cached = repository.cachedPosts()
        if (cached.isNotEmpty() && _state.value.posts.isEmpty()) {
            _state.update { it.copy(posts = cached, selected = todayIndex(cached), loading = false) }
        }
    }

    /**
     * What a failed fetch does, which is the whole of the `catch` in `_load`.
     *
     * Cached devotions beat an error: the reader keeps what they were reading and the cause is reported
     * under it, rather than the article being replaced by a dead end. Only a reader with nothing at all
     * is shown the failure screen, and a silent failure with content on screen is not reported at all.
     */
    private suspend fun onFetchFailed(hasContent: Boolean, silent: Boolean, failure: DevotionFetchException) {
        val cached = repository.cachedPosts()
        if (cached.isNotEmpty()) {
            if (!hasContent) {
                _state.update {
                    it.copy(posts = cached, selected = todayIndex(cached), loading = false, failed = false)
                }
            } else if (!silent) {
                _state.update { it.copy(loading = false, failureDetail = failure.detail()) }
            }
            return
        }
        if (silent && hasContent) return
        _state.update { it.copy(loading = false, failed = true, failureDetail = failure.detail()) }
    }

    /**
     * The short technical cause shown under the failure message.
     *
     * The message rather than `toString()`, which on the JVM prefixes a fully qualified class name that
     * would eat a whole line of a ten-point detail line and tell a reader nothing. Dart's
     * `ClientException.toString()` was already the message with a short prefix; this is the same text
     * without one, and an exception that carries no message still reports as itself.
     */
    private fun DevotionFetchException.detail(): String = message ?: toString()

    /**
     * Which post opens by default: today's, or failing that the newest one that is not in the future.
     *
     * The fallback is what keeps a scheduled post from opening on its own. The blog dates the next few
     * days in advance, so a reader opening the page on the 20th must land on the 20th's devotion, not on
     * the 22nd that happens to be first in the feed.
     */
    internal fun todayIndex(posts: List<DevotionPost>, today: LocalDate = LocalDate.now()): Int {
        if (posts.isEmpty()) return 0
        val exact = posts.indexOfFirst { it.devotionDate == today }
        if (exact >= 0) return exact
        val latestPast = posts.indexOfFirst { !it.devotionDate.isAfter(today) }
        return if (latestPast >= 0) latestPast else posts.lastIndex
    }

    /**
     * The interface language, so a date and a copied article change with the rest of the screen.
     *
     * The same subscription the library panel keeps, and for the same reason: Flutter read the ambient
     * settings on every build, so changing the language in Settings relabelled the devotion days without
     * anything else moving. Only the labels change — the posts are not refetched.
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

    /** `index.clamp(0, list.isEmpty ? 0 : list.length - 1)`, which Dart's `clamp` does on an empty list. */
    private fun clamp(index: Int, size: Int): Int = if (size == 0) 0 else index.coerceIn(0, size - 1)

    private companion object {
        /** `const Duration(minutes: 30)`. */
        val STALE_AFTER: Duration = Duration.ofMinutes(30)
    }
}