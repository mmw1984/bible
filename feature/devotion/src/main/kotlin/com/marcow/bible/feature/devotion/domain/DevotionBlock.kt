package com.marcow.bible.feature.devotion.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One renderable piece of a devotion, mirroring `DevotionBlock` in
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * Every shape the blog publishes lands in exactly one of these, so the reader renders a
 * [LazyColumn] of them without knowing anything about WordPress.
 */
sealed interface DevotionBlock

/** A run of text, including a list item, which is prefixed with `• ` when it came from one. */
data class DevotionParagraph(val text: String) : DevotionBlock

/** `<h1>`…`<h6>` flattened to one level — the blog has no outline worth preserving. */
data class DevotionHeading(val text: String) : DevotionBlock

/** `<blockquote>`, which carries 詩歌 1:1–2 and the 經文 quotes. */
data class DevotionQuote(val text: String) : DevotionBlock

/** An `<img>`, with [url] already percent-encoded and lazy-load placeholders resolved. */
data class DevotionImage(val url: String) : DevotionBlock

/**
 * A YouTube embed (`<iframe src="…youtube.com/embed/…">`).
 *
 * [videoId] is the `v=` the embed route carried. [watchUrl] and [thumbnailUrl] are derived rather than
 * parsed, because the Dart build derived them too: the watch URL is what a double-tap's "open in
 * browser" row targets, and the thumbnail is only reached when the inline player fails.
 */
data class DevotionVideo(val videoId: String) : DevotionBlock {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}

/**
 * Any other `<iframe>` embed — the SoundCloud player that opens several 靈修默想 posts.
 *
 * [url] is played inline, by a `WebView` loading SoundCloud's own player widget, with links out of
 * that frame escalated to the browser so a tap cannot take the reader off the article. If the frame
 * reports a main-frame error the block falls back to a card carrying the host and an open button,
 * which is the same branch the Dart reader took on the web.
 */
data class DevotionEmbed(val url: String) : DevotionBlock

/** A titled section such as `<div class="dove"><div class="title">詩歌</div>…`. */
data class DevotionSection(val title: String, val blocks: List<DevotionBlock>) : DevotionBlock

/**
 * A devotion with its parsed blocks, mirroring `DevotionPost` in the Dart build.
 *
 * [contentHtml] is the WordPress HTML [blocks] came from, kept verbatim: the offline cache stores
 * raw post data so a parser improvement re-parses on next launch instead of serving old blocks.
 */
data class DevotionPost(
    val id: Long,
    /** Publish time as the API reported it, local time, without a zone of its own. */
    val publishedAt: LocalDateTime,
    /**
     * The calendar day the devotion is for, parsed out of the title suffix (`2026年8月21日`).
     *
     * This is what orders and "today" means: the blog's publish order does not follow the devotion
     * calendar, so a 7月22日 devotion can go out on 8月21日.
     */
    val devotionDate: LocalDate,
    val title: String,
    val link: String,
    val contentHtml: String,
    val blocks: List<DevotionBlock>,
)
