package com.marcow.bible.feature.devotion.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

import com.marcow.bible.core.network.devotion.DevotionPost as RawDevotionPost

/**
 * The bridge between the wire and the reader, ported from the *'parses every RSS item and sorts by
 * title date'* case of `legacy/flutter/test/devotion_parser_test.dart:120`.
 *
 * `toDevotionPosts` is the only place the two layers meet, and it is where three rules that decide
 * what the reader actually sees are decided: the title becomes text, the day a post is filed under
 * comes out of that title rather than out of the publish time, and the feed is ordered by that day.
 * Those three are what the date chips and the "today" index are built from, and nothing else in the
 * suite pins them — the REST and RSS tests stop at the wire and assert *that* order is whatever the
 * site used.
 */
class DevotionFeedTest {
    @Test
    fun `the title arrives as text, with its tags gone and its entities decoded`() {
        // `decodeHtmlEntities(stripHtmlTags(rawTitle)).trim()`, in that order: `&amp;ndash;` has to
        // decode the way a browser renders it, which is why the named pass runs `&amp;` first.
        val post = rawPost(titleHtml = "[觀畫靈修] <em>亞伯蘭</em>與撒萊 &amp;ndash;2026年8月21日")
            .toDevotionPost()

        assertEquals("[觀畫靈修] 亞伯蘭與撒萊 –2026年8月21日", post.title)
    }

    @Test
    fun `posts are ordered by the day in the title, not by the order the blog published them`() {
        // The published first, but its devotion is for the *earlier* day — the exact shape the blog
        // runs when it posts ahead. Ordering by publish time would put 頌主奇恩 on top here.
        val posts = listOf(
            rawPost(
                id = 25432,
                titleHtml = "[詩歌靈修] 頌主奇恩 －2026年8月20日",
                publishedAt = LocalDateTime.of(2026, 8, 21, 11, 37, 55),
            ),
            rawPost(
                id = 25436,
                titleHtml = "[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日",
                publishedAt = LocalDateTime.of(2026, 8, 20, 9, 42, 24),
            ),
        ).toDevotionPosts()

        assertEquals(listOf(25436L, 25432L), posts.map { it.id })
        assertEquals(
            listOf("[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日", "[詩歌靈修] 頌主奇恩 －2026年8月20日"),
            posts.map { it.title },
        )
    }

    @Test
    fun `a title date overrides the publish day`() {
        val post = rawPost(
            titleHtml = "[圖片靈修] 地圖 －2026年7月22日",
            publishedAt = LocalDateTime.of(2026, 8, 21, 9, 0),
        ).toDevotionPost()

        // The blog publishes ahead, so a post's publish day says when it went up and nothing about
        // which day it is for.
        assertEquals(LocalDate.of(2026, 7, 22), post.devotionDate)
    }

    @Test
    fun `a title with no day files under the day it was published`() {
        val post = rawPost(
            titleHtml = "[詩歌靈修] 頌主奇恩",
            publishedAt = LocalDateTime.of(2026, 8, 20, 11, 37),
        ).toDevotionPost()

        assertEquals(LocalDate.of(2026, 8, 20), post.devotionDate)
    }

    @Test
    fun `the body is parsed into blocks and kept verbatim for the cache`() {
        val html = "<p>觀畫內文</p><iframe src=\"https://www.youtube.com/embed/9rJm0Nq6TB0\"></iframe>"

        val post = rawPost(contentHtml = html).toDevotionPost()

        assertEquals("9rJm0Nq6TB0", post.blocks.filterIsInstance<DevotionVideo>().single().videoId)
        // Verbatim is the property `DevotionCache` is built on: the row stores the post so a parser
        // improvement re-reads it, and that only holds if nothing was rewritten on the way through.
        assertEquals(html, post.contentHtml)
    }

    @Test
    fun `the wire fields reach the post untouched`() {
        val post = rawPost(id = 25436).toDevotionPost()

        // `id` and `link` are what a silent refresh matches on to keep the reader on their post.
        assertEquals(25436L, post.id)
        assertEquals("https://devotion.wkphc.org/25436", post.link)
        assertEquals(LocalDateTime.of(2026, 8, 21, 1, 42, 24), post.publishedAt)
    }

    @Test
    fun `a feed of nothing is an empty day rather than a failure`() {
        assertEquals(emptyList<DevotionPost>(), emptyList<RawDevotionPost>().toDevotionPosts())
    }

    private fun rawPost(
        id: Long = 25436,
        titleHtml: String = "[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日",
        link: String = "https://devotion.wkphc.org/$id",
        contentHtml: String = "<p>觀畫內文</p>",
        publishedAt: LocalDateTime = LocalDateTime.of(2026, 8, 21, 1, 42, 24),
    ) = RawDevotionPost(
        id = id,
        publishedAt = publishedAt,
        titleHtml = titleHtml,
        link = link,
        contentHtml = contentHtml,
    )
}
