package com.marcow.bible.core.network.devotion

import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.time.DateTimeException
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The RSS tier of the devotion fetch: `_fetchFromRssFeed` in
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * The feed is the fallback because the REST API can be switched off or blocked by a security
 * plugin while the classic feed stays open — the site still publishes both from the same
 * WordPress install. It only ever carries the newest post, so a reader that opens on it shows today
 * and nothing else, which is what the Flutter build did too.
 *
 * Parsing is Jsoup in XML mode rather than `android.util.Xml`: a pull parser would be the smaller
 * dependency on a device, but it is not available to a JVM unit test, and this file is where the
 * feed's two awkward shapes live — `<content:encoded>` CDATA and RFC-822 dates.
 */
@Singleton
class DevotionRssClient @Inject constructor(private val httpClient: OkHttpClient) {
    /**
     * The items the feed carries, in the order the feed lists them.
     *
     * @throws DevotionFetchException when the feed is unreachable or holds no usable item.
     */
    suspend fun posts(): List<DevotionPost> {
        val items = parseDevotionRssItems(httpClient.devotionGetText(DEVOTION_ORIGIN + DEVOTION_FEED_PATH))
        if (items.isEmpty()) throw DevotionFetchException("devotion rss had no items")
        return items
    }
}

/**
 * `parseDevotionRssItems`: every `<item>` of the WordPress feed as a post.
 *
 * An item with neither `content:encoded` nor `description` has no body to read, so it is skipped
 * rather than surfaced as an empty article — `_devotionPostFromRssItem` would have produced one.
 */
internal fun parseDevotionRssItems(xml: String): List<DevotionPost> {
    val feed = Jsoup.parse(xml, "", Parser.xmlParser())
    return feed.getElementsByTag("item").mapNotNull { item -> item.toPost() }
}

/** `_devotionPostFromRssItem`. `content:encoded` is the full body; `description` is the excerpt. */
private fun Element.toPost(): DevotionPost? {
    val title = tagText("title")
    val body = tagText("content:encoded").ifEmpty { tagText("description") }
    if (body.isEmpty()) return null
    val publishedAt = parseRfc822Date(tagText("pubDate"))
    return DevotionPost(
        // The feed carries no post id — `_devotionPostFromRssItem` used 0 for the same reason — so
        // `DevotionFeed` keys these by their permalink instead.
        id = 0L,
        publishedAt = publishedAt,
        titleHtml = title,
        link = tagText("link").ifEmpty { DEVOTION_ORIGIN },
        contentHtml = body,
    )
}

/**
 * `tag(name)` of `_devotionPostFromRssItem`: the first `<name>` child's content, unwrapped from
 * CDATA and trimmed.
 *
 * Child nodes are walked rather than matched by selector because `content:encoded` is not a
 * selector — the colon reads as a pseudo-class — and because WordPress wraps every one of these
 * fields in `<![CDATA[ … ]]>`: Jsoup keeps CDATA in a `DataNode` beside the text nodes, and both
 * have to contribute to the same value.
 */
private fun Element.tagText(name: String): String {
    val element = children().firstOrNull { it.tagName().equals(name, ignoreCase = true) } ?: return ""
    return element.childNodes().joinToString(separator = "") { node ->
        when (node) {
            // Text and CDATA are the value; a nested element stays markup, which is what the regex
            // captured in Dart and what `parseDevotionBlocks` then reads.
            is DataNode, is TextNode -> node.text()
            else -> node.outerHtml()
        }
    }.trim()
}

/**
 * `parseRfc822Date`: "Fri, 21 Aug 2026 09:46:05 +0000" without ever throwing.
 *
 * `DateTime.tryParse` has no RFC-822 shape and `HttpDate` is not on Android, so the three pieces
 * this needs are read with one regex and the offset is ignored — the title date is what orders
 * posts, and an unreadable date falls back to now exactly as it did in Dart.
 */
internal fun parseRfc822Date(raw: String): LocalDateTime {
    val match = RFC_822.find(raw.trim()) ?: return LocalDateTime.now()
    val (day, monthName, yearText, hour, minute, second) = match.destructured
    val month = MONTHS[monthName.lowercase()] ?: 1
    val parsedYear = yearText.toIntOrNull() ?: LocalDateTime.now().year
    return try {
        LocalDateTime.of(
            // A two-digit year is this century, as Dart's `if (year < 100) year += 2000` did.
            if (parsedYear < 100) parsedYear + 2000 else parsedYear,
            month,
            day.toIntOrNull() ?: 1,
            hour.toIntOrNull() ?: 0,
            minute.toIntOrNull() ?: 0,
            second.toIntOrNull() ?: 0,
        )
    } catch (_: DateTimeException) {
        // `DateTime(2026, 2, 31)` rolled over to 3 March in Dart; a local date-time refuses instead,
        // so an impossible day is treated as the unreadable date it is.
        LocalDateTime.now()
    }
}

/** The `months` table of `parseRfc822Date`, lower-cased so the lookup is case-insensitive. */
private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
)

/** The pattern of `parseRfc822Date`, with the two optional groups left empty when absent. */
private val RFC_822 = Regex("""(\d{1,2})\s+([A-Za-z]{3})[a-z]*\s+(\d{2,4})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
