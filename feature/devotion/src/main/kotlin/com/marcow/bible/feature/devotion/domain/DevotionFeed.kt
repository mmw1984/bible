package com.marcow.bible.feature.devotion.domain

import com.marcow.bible.core.network.devotion.DevotionPost as RawDevotionPost

/**
 * Where the raw post data `core/network/devotion` hands back becomes a parsed devotion.
 *
 * The split is deliberate: `core/network` moves bytes and knows about WordPress JSON, while decoding a
 * title, reading the devotion day out of it and parsing the post HTML are all content work that belong
 * next to the reader. The raw type is aliased because both layers have a post — one is what the wire
 * delivered, the other is what the screen renders.
 */

/** Parses every post and orders them by the day the devotion is for, newest first. */
internal fun List<RawDevotionPost>.toDevotionPosts(): List<DevotionPost> =
    map { it.toDevotionPost() }.sortedByDescending { it.devotionDate }

/**
 * The same mapping for the RSS tier, whose `<link>` is XML text rather than a JSON string.
 *
 * `decodeHtmlEntities(tag('link'))` in `_devotionPostFromRssItem`
 * (`legacy/flutter/lib/devotion_content.dart:806`) — a feed written with `&amp;` in a permalink is
 * an entity reference the way a browser reads it, so it has to become `&` before the reader opens it.
 * The REST tier does not decode: JSON hands back exactly the characters the blog wrote, and a link
 * there that reads `&amp;` is the blog's own text.
 */
internal fun List<RawDevotionPost>.toRssDevotionPosts(): List<DevotionPost> =
    map { it.toDevotionPost().copy(link = decodeHtmlEntities(it.link).trim()) }
        .sortedByDescending { it.devotionDate }

internal fun RawDevotionPost.toDevotionPost(): DevotionPost {
    val title = decodeHtmlEntities(stripHtmlTags(titleHtml)).trim()
    return DevotionPost(
        id = id,
        publishedAt = publishedAt,
        // The blog's publish order is not the devotion calendar's: a 7月22日 devotion goes out on 8月21日,
        // so the title date is what a post is filed under and sorted by.
        devotionDate = parseDevotionTitleDate(title) ?: publishedAt.toLocalDate(),
        title = title,
        link = link,
        contentHtml = contentHtml,
        blocks = parseDevotionBlocks(contentHtml),
    )
}
