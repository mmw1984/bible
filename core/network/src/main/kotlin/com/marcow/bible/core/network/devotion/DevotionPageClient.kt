package com.marcow.bible.core.network.devotion

import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The third tier of the devotion fetch: `_fetchFromSitePages` in
 * `legacy/flutter/lib/devotion_content.dart`, minus everything that reads the page.
 *
 * Even the feed can be unreachable — a plugin quirk, a captive network — and at that point the only
 * thing left is what any browser reads: the public pages themselves. This client is the transport
 * for that tier and nothing more; discovering the permalinks and locating an article's content
 * container are HTML questions, so they live in `feature/devotion/domain` with the rest of the
 * parser rather than here.
 */
@Singleton
class DevotionPageClient @Inject constructor(private val httpClient: OkHttpClient) {
    /**
     * The homepage, whose links are the recent posts.
     *
     * @throws DevotionFetchException when the page is unreachable.
     */
    suspend fun homepage(): String = httpClient.devotionGetText(DEVOTION_ORIGIN)

    /**
     * One article page, verbatim.
     *
     * @throws DevotionFetchException when the page is unreachable.
     */
    suspend fun article(url: String): String = httpClient.devotionGetText(url)
}
