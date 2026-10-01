package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.network.devotion.DevotionFetchException
import com.marcow.bible.core.network.devotion.DevotionPageClient
import com.marcow.bible.core.network.devotion.DevotionRestClient
import com.marcow.bible.core.network.devotion.DevotionRssClient
import com.marcow.bible.feature.devotion.domain.DevotionPost
import com.marcow.bible.feature.devotion.domain.extractPostLinksFromPage
import com.marcow.bible.feature.devotion.domain.parseArticlePage
import com.marcow.bible.feature.devotion.domain.toDevotionPosts
import kotlinx.coroutines.CancellationException

/**
 * One way of reading the blog, tried in order until one of them answers.
 *
 * The tiers exist because the site can be half-unreachable in three different ways — the REST API
 * switched off, the feed blocked, everything but the pages refused — and the Flutter build degraded
 * through them one at a time inside `fetchDevotionPosts`
 * (`legacy/flutter/lib/devotion_content.dart:664`). Naming each tier is what makes the failure
 * reportable: the reader sees one generic message and the tier that got furthest under it, which is
 * what a screenshot of an on-device network problem has to carry.
 */
interface DevotionTier {
    /** Short enough for a log line and for the detail under the reader's failure message. */
    val name: String

    /**
     * The posts this tier can see, newest devotion day first.
     *
     * @throws com.marcow.bible.core.network.devotion.DevotionFetchException when the tier cannot be
     *   read at all. A tier that answers with nothing is a failure too, which is what sends the fetch
     *   on to the next one.
     */
    suspend fun posts(): List<DevotionPost>
}

/**
 * `_fetchFromRestApi`: the only tier that carries more than today's post.
 *
 * `per_page=20`, and the transport already tries both `/wp-json` and `index.php?rest_route=…`, so
 * anything that reaches here is either the API or nothing.
 */
class RestDevotionTier(private val client: DevotionRestClient) : DevotionTier {
    override val name: String = "rest"

    override suspend fun posts(): List<DevotionPost> = client.posts().toDevotionPosts()
}

/**
 * `_fetchFromRssFeed`: today's post only.
 *
 * The feed stays open when a security plugin blocks the API, so a reader that lands here shows one
 * devotion and nothing else — the same thing the Flutter build showed.
 */
class RssDevotionTier(private val client: DevotionRssClient) : DevotionTier {
    override val name: String = "rss"

    override suspend fun posts(): List<DevotionPost> = client.posts().toDevotionPosts()
}

/**
 * `_fetchFromSitePages`: the last resort, and the only tier that reads HTML rather than a feed.
 *
 * The homepage's post permalinks are the newest devotions, and each article page is parsed with the
 * same engine as API and RSS content, so a reader cannot tell which tier answered.
 */
class SitePageDevotionTier(private val client: DevotionPageClient) : DevotionTier {
    override val name: String = "site"

    override suspend fun posts(): List<DevotionPost> {
        val links = extractPostLinksFromPage(client.homepage())
        if (links.isEmpty()) throw DevotionFetchException("devotion site listed no posts")
        val posts = mutableListOf<DevotionPost>()
        for (link in links.take(ARTICLE_LIMIT)) {
            val post = try {
                parseArticlePage(link.url, client.article(link.url), link.title)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // `catch (_)` around one article: one unreachable page is not a dead tier.
                null
            }
            if (post != null) posts.add(post)
        }
        if (posts.isEmpty()) throw DevotionFetchException("devotion site pages produced no posts")
        // Sorted here rather than by `toDevotionPosts`, because this tier builds its posts from
        // pages instead of from the wire type that helper converts.
        return posts.sortedByDescending { it.devotionDate }
    }

    private companion object {
        /** `links.take(12)`: twelve pages is what the blog lists, and each one is a full request. */
        const val ARTICLE_LIMIT = 12
    }
}
