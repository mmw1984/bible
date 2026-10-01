package com.marcow.bible.core.network.devotion

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The WordPress REST tier of the devotion fetch: `_fetchFromRestApi` in
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * This is the tier that works most of the time and the only one that carries more than the latest
 * post — `per_page=20`. The second route (`index.php?rest_route=…`) exists because a blog behind a
 * security plugin can have `/wp-json` blocked while the front controller still serves the same API,
 * so both are tried before the tier counts as a failure.
 *
 * **What moved out of here.** `_decodeRestPosts` also decoded the title's HTML and sorted the posts
 * by the devotion date embedded in it. Both are HTML and calendar work, so both belong to
 * `feature/devotion/domain` now: the transport hands back what the site said and the reader decides
 * what it means. The order the posts arrive in is therefore the blog's, and `DevotionFeed` re-sorts
 * them exactly the way `_decodeRestPosts` did.
 *
 * The JSON is read through the `kotlinx.serialization` tree API rather than `@Serializable` classes
 * for the same reason `ChatCompletionRequest` is not serializable: the payload is a projection of
 * WordPress' API, and reading the six fields the request asks for keeps `_fields` and this decoder
 * as one readable pair.
 */
@Singleton
class DevotionRestClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    /**
     * The posts the API has, in the order the blog published them.
     *
     * @throws DevotionFetchException when neither route answered with a usable list.
     */
    suspend fun posts(): List<DevotionPost> {
        var failure: DevotionFetchException? = null
        for (uri in DEVOTION_REST_URIS) {
            try {
                return decodeDevotionPosts(httpClient.devotionGetText(uri))
                    .ifEmpty { throw DevotionFetchException("devotion api returned no posts") }
            } catch (rejected: DevotionFetchException) {
                // `lastError = error` in Dart: whichever route got furthest is the one worth
                // reporting, and the next route may still work.
                failure = rejected
            }
        }
        throw failure ?: DevotionFetchException("devotion api unreachable")
    }
}

/**
 * `_decodeRestPosts`: the answered body turned into posts.
 *
 * A body that is not a JSON array fails the tier. An entry that is not an object is skipped, which
 * is the `if (item is! Map<String, dynamic>) continue;` of the Dart decoder.
 */
internal fun decodeDevotionPosts(body: String): List<DevotionPost> {
    val payload = try {
        Json.parseToJsonElement(body)
    } catch (malformed: SerializationException) {
        throw DevotionFetchException("devotion api answered with unreadable JSON", malformed)
    }
    val items = payload as? JsonArray ?: throw DevotionFetchException("devotion api answered with $payload")
    return items.mapNotNull { item ->
        val entry = item as? JsonObject ?: return@mapNotNull null
        DevotionPost(
            id = entry.long("id"),
            publishedAt = entry.publishDate(),
            titleHtml = entry.rendered("title"),
            link = entry.string("link").ifEmpty { DEVOTION_ORIGIN },
            contentHtml = entry.rendered("content"),
        )
    }
}

/** `date_gmt` with `date` behind it, parsed leniently and falling back to now, as Dart did. */
private fun JsonObject.publishDate(): LocalDateTime =
    parseIsoDateTime(string("date_gmt").ifEmpty { string("date") }) ?: LocalDateTime.now()

/** `DateTime.tryParse(raw)?.toLocal() ?? DateTime.now()`: a local date-time with no zone attached. */
internal fun parseIsoDateTime(raw: String): LocalDateTime? = try {
    LocalDateTime.parse(raw)
} catch (_: DateTimeParseException) {
    null
}

/** `item['title']['rendered']`, or the empty string `as String? ?? ''` produced. */
private fun JsonObject.rendered(field: String): String = (this[field] as? JsonObject)
    ?.string("rendered")
    .orEmpty()

private fun JsonObject.string(field: String): String =
    (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** `(item['id'] as num?)?.toInt() ?? 0`, with a value that is not an integer counted as absent. */
private fun JsonObject.long(field: String): Long =
    (this[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L