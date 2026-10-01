package com.marcow.bible.feature.devotion

import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.feature.devotion.domain.DevotionPost

/**
 * Everything the devotion screen renders, mirroring the fields `_DevotionPageState` held in
 * `legacy/flutter/lib/devotion_page.dart:27`.
 *
 * [failed] is a flag rather than the message Flutter carried, because the message is a localised string
 * and this screen resolves strings where it draws them; [failureDetail] is the half that is not
 * localised — the exception text, shown under the generic line so that an on-device network problem is
 * diagnosable from a screenshot, which was the reason the Dart build kept `errorDetail` at all.
 *
 * The one field Flutter held that is deliberately missing is its `DateTime? _lastFetchTime`. The screen
 * only ever asked whether it was null — to draw today's date in the corner — and the view model asks
 * the real question, which is whether the feed is stale.
 */
data class DevotionUiState(
    val posts: List<DevotionPost> = emptyList(),
    /** Which post is open, an index into [posts]. */
    val selected: Int = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val failureDetail: String? = null,
    /**
     * Whether a fetch has ever succeeded, which is what puts today's date in the corner.
     *
     * A feed that has only ever been read out of the cache has not been fetched, so it shows nothing
     * there — the same line Flutter guarded with `if (_lastFetchTime != null)`.
     */
    val loadedOnce: Boolean = false,
    val usesEnglishUi: Boolean = false,
) {
    /** The post being read, or null when there is nothing to show. */
    val post: DevotionPost? get() = posts.getOrNull(selected)

    /** Whether there is more than one day to choose between, which is what draws the date chips. */
    val hasDateChips: Boolean get() = posts.size > 1
}

/**
 * The language the dates — and a copied article — are written in, `AppLocale.zhHant` being Flutter's
 * fallback for a screen whose settings scope was missing.
 *
 * Shared rather than written twice because the page draws dates and the route copies one: a chip and a
 * pasted paragraph that disagreed about the month would be the same bug in two places.
 */
internal fun DevotionUiState.devotionLocale(): AppLocale = if (usesEnglishUi) AppLocale.EN else AppLocale.ZH_HANT
