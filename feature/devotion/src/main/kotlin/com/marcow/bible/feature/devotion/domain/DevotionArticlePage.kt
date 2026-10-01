package com.marcow.bible.feature.devotion.domain

import com.marcow.bible.core.network.devotion.DEVOTION_ORIGIN
import java.time.LocalDateTime

/**
 * The site-page tier, ported from the "Site-page fallback" section of
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * This is what still works when the REST API and the feed are both unreachable: read the public pages
 * themselves. Permalinks come off the homepage list, each article page's content container is located
 * and parsed with the same engine as API and RSS content, and the result is indistinguishable from a
 * normal fetch.
 */

/** A permalink discovered on the homepage, with the anchor text the blog filled it with. */
internal data class ScrapedPostLink(
    val url: String,
    val title: String,
)

/** `https://devotion.wkphc.org/25436` — a numeric permalink, the blog's only post URL shape. */
private val ABSOLUTE_POST_URL = Regex("^https?://devotion\\.wkphc\\.org/(\\d{3,})/?$")

/** The same permalink as the homepage writes it, relative to the origin. */
private val RELATIVE_POST_URL = Regex("^/(\\d{3,})/?$")

/**
 * Normalizes a homepage `href` into a canonical permalink, or `null` when it points somewhere else —
 * pages, categories, uploads and the feed all have to be told apart from posts here.
 */
internal fun normalizePostHref(rawHref: String): String? {
    var href = decodeHtmlEntities(rawHref).trim()
    val hash = href.indexOf('#')
    if (hash >= 0) href = href.substring(0, hash)
    val query = href.indexOf('?')
    if (query >= 0) href = href.substring(0, query)
    if (ABSOLUTE_POST_URL.containsMatchIn(href) || RELATIVE_POST_URL.containsMatchIn(href)) {
        if (href.endsWith('/')) href = href.dropLast(1)
        return "$DEVOTION_ORIGIN/${href.substringAfterLast('/')}"
    }
    return null
}

/**
 * Every post permalink on a homepage, in document order so "newest first" survives without dates —
 * which is why this cannot fall back to the parse order of an unordered selector.
 */
internal fun extractPostLinksFromPage(html: String): List<ScrapedPostLink> {
    val seen = mutableSetOf<String>()
    val links = mutableListOf<ScrapedPostLink>()

    fun visit(node: HtmlNode) {
        if (node !is HtmlElement) return
        if (node.tag == "a") {
            val url = normalizePostHref(node.attributes["href"].orEmpty())
            if (url != null && seen.add(url)) links.add(ScrapedPostLink(url, node.text.trim()))
            return // Anchors never nest.
        }
        node.children.forEach { visit(it) }
    }

    visit(parseHtmlDocument(html))
    return links
}

/**
 * Container classes WordPress themes use for the post body, most likely first. The live theme ships
 * `entry-content`; the rest are the names the other themes in the fixture history have used.
 */
private val CONTENT_CLASS_NAMES = setOf("entry-content", "post-content", "article-content", "the-content")

/** The article body inside a whole page, or the page itself when nothing matches. */
internal fun findContentRoot(document: HtmlElement): HtmlElement {
    var found: HtmlElement? = null

    fun visit(element: HtmlElement) {
        if (element.hasAnyClass(CONTENT_CLASS_NAMES)) {
            found = element
            return
        }
        for (child in element.children) {
            if (child !is HtmlElement) continue
            visit(child)
            if (found != null) return
        }
    }

    visit(document)
    return found ?: document.firstByTag("article") ?: document.firstByTag("body") ?: document
}

/** The trailing ` － 靈修默想` WordPress puts on every `<title>`. */
private val TITLE_SUFFIX = Regex("\\s*[-–—]\\s*靈修默想\\s*$")

/**
 * A page's title: the entry heading first, then `<title>` minus the site suffix. Only the date in it
 * has to be readable, so an imperfect fallback stays harmless.
 */
internal fun extractArticleTitle(document: HtmlElement): String? {
    val headingText = document.firstByTag("h1")?.text?.trim().orEmpty()
    if (headingText.isNotEmpty()) return headingText
    val titleText = document.firstByTag("title")?.text?.trim().orEmpty()
    if (titleText.isEmpty()) return null
    return titleText.replaceFirst(TITLE_SUFFIX, "").trim()
}

/**
 * A devotion built from a whole article page, or `null` when the page has no usable container — the
 * caller skips those rather than caching an empty post.
 */
internal fun parseArticlePage(url: String, html: String, titleHint: String? = null): DevotionPost? {
    val document = parseHtmlDocument(html)
    val content = findContentRoot(document)
    if (content.children.isEmpty()) return null
    val title = titleHint?.takeIf { it.isNotEmpty() } ?: extractArticleTitle(document).orEmpty()
    if (title.isEmpty()) return null
    // Page markup carries no machine-readable publish time; the title date is what orders posts, and
    // the fetch time only feeds the cache.
    val fetchedAt = LocalDateTime.now()
    return DevotionPost(
        id = postIdFromUrl(url),
        publishedAt = fetchedAt,
        devotionDate = parseDevotionTitleDate(title) ?: fetchedAt.toLocalDate(),
        title = title,
        link = url,
        contentHtml = serializeHtmlElement(content),
        blocks = parseDevotionBlocksFromElement(content),
    )
}

private fun postIdFromUrl(url: String): Long =
    url.substringAfterLast('/').substringBefore('?').substringBefore('#').toLongOrNull() ?: 0L
