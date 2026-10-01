package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.database.DevotionCacheDao
import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.core.network.devotion.DEVOTION_ORIGIN
import com.marcow.bible.feature.devotion.domain.DevotionPost
import com.marcow.bible.feature.devotion.domain.parseDevotionBlocks
import com.marcow.bible.feature.devotion.domain.parseDevotionTitleDate
import com.marcow.bible.feature.devotion.domain.parseIsoDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `devotion_cache_v1`, the offline copy of the last fetch that worked, ported from
 * `readDevotionCache` / `writeDevotionCache` / `encodeDevotionPostsCache` in
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * **Why raw post data and not rendered blocks.** The Flutter cache stored `id`, `date`, `title`,
 * `link` and `content` — everything but the parsed blocks — and rebuilt the blocks on every read, so
 * an improved parser re-reads yesterday's cache with today's parser instead of serving what an older
 * one made of it. The same five fields are the row here, and [read] runs the blocks through
 * `parseDevotionBlocks` again for the same reason. That is also why the row holds no
 * [DevotionPost.devotionDate]: the day a post is for is read back out of the title on every load,
 * which is what lets a title-date parser improvement take effect on cached posts too.
 *
 * One row holds the whole feed, under the fixed id `feed`, which is what `SharedPreferences.getString`
 * did. The blog answers with the last twenty posts; a row per post would buy nothing but a list query
 * and a way for the cache to hold posts belonging to two different fetches.
 *
 * The cache is best-effort in both directions: a read that cannot be decoded is no cache, and a write
 * that the database refuses is swallowed. Flutter caught every error on both, so a full disk never
 * turned into a failed load.
 */
@Singleton
class DevotionCache @Inject constructor(private val dao: DevotionCacheDao) {
    /**
     * The cached posts, newest day first, re-parsed with the current parser.
     *
     * Empty when there is no cache, when it cannot be decoded and when every entry was skipped — all
     * three are "nothing to show first", which is what the caller needs to know.
     */
    suspend fun read(): List<DevotionPost> = decodeDevotionPostCache(readRawPost())

    /** Replaces the cache with [posts]. A failure here is not a failed load, so it is not reported. */
    suspend fun write(posts: List<DevotionPost>) {
        if (posts.isEmpty()) return
        try {
            dao.upsert(
                DevotionCacheEntity(
                    id = FEED_ID,
                    rawPost = encodeDevotionPostCache(posts),
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Best-effort: the feed is already in hand, and a cache that cannot be written is not a
            // load that failed.
        }
    }

    private suspend fun readRawPost(): String? = try {
        dao.entry(FEED_ID)?.rawPost
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // `catch (_)` in `readDevotionCache`: no cache is the same answer as an unreadable one.
        null
    }

    private companion object {
        /** The one row the feed lives in; the Flutter cache had exactly one `SharedPreferences` key. */
        const val FEED_ID = "feed"
    }
}

/**
 * `encodeDevotionPostsCache`: the raw fields of every post, as JSON.
 *
 * Only the five fields the Flutter cache wrote, in the same names and the same order, so a cache row
 * means what it meant there. `publishedAt` is written without a zone for the same reason
 * `post.date.toIso8601String()` was: the site publishes local wall-clock time and reinterpreting it
 * against the device's zone would move a post by a day.
 */
internal fun encodeDevotionPostCache(posts: List<DevotionPost>): String = buildJsonArray {
    for (post in posts) {
        add(
            buildJsonObject {
                put(KEY_ID, post.id)
                put(KEY_DATE, post.publishedAt.toString())
                put(KEY_TITLE, post.title)
                put(KEY_LINK, post.link)
                put(KEY_CONTENT, post.contentHtml)
            },
        )
    }
}.toString()

/**
 * `decodeDevotionPostsCache`: the row turned back into posts.
 *
 * An entry with no body is skipped rather than surfaced as an empty article, which is the
 * `if (contentHtml.isEmpty()) continue;` of the Dart decoder. An unreadable row decodes to nothing
 * rather than throwing, because `readDevotionCache` caught every error the decoder could raise and
 * this is that call site.
 */
internal fun decodeDevotionPostCache(raw: String?): List<DevotionPost> {
    if (raw.isNullOrEmpty()) return emptyList()
    val items = try {
        Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
    } catch (_: IllegalArgumentException) {
        return emptyList()
    }
    val posts = items.mapNotNull { item ->
        val entry = item as? JsonObject ?: return@mapNotNull null
        entry.toPost()
    }
    // The blog's publish order is not the devotion calendar's, so the cached posts are re-sorted the
    // way `_decodeRestPosts` sorted the fetched ones: a 7月22日 devotion published on 8月21日 has to
    // still file under 7月22日 after a week in the cache.
    return posts.sortedByDescending { it.devotionDate }
}

/** One cached entry, or `null` for the two ways Dart's decoder skipped one. */
private fun JsonObject.toPost(): DevotionPost? {
    val contentHtml = string(KEY_CONTENT)
    if (contentHtml.isEmpty()) return null
    val title = string(KEY_TITLE).trim()
    val publishedAt = parseIsoDateTime(string(KEY_DATE)) ?: LocalDateTime.now()
    return DevotionPost(
        id = long(KEY_ID),
        publishedAt = publishedAt,
        devotionDate = parseDevotionTitleDate(title) ?: publishedAt.toLocalDate(),
        title = title,
        link = string(KEY_LINK).ifEmpty { DEVOTION_ORIGIN },
        contentHtml = contentHtml,
        blocks = parseDevotionBlocks(contentHtml),
    )
}

private fun JsonObject.string(field: String): String =
    (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** `(item['id'] as num?)?.toInt() ?? 0`, with anything that is not a whole number counted as absent. */
private fun JsonObject.long(field: String): Long = (this[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

private const val KEY_ID = "id"
private const val KEY_DATE = "date"
private const val KEY_TITLE = "title"
private const val KEY_LINK = "link"
private const val KEY_CONTENT = "content"