package com.marcow.bible.core.network.devotion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * The RSS tier's decoder, checked against `parseDevotionRssItems` /
 * `_devotionPostFromRssItem` in `legacy/flutter/lib/devotion_content.dart`.
 *
 * The XML is the one `test/devotion_parser_test.dart` used: two items, `content:encoded` CDATA, and
 * an RFC-822 `pubDate` — the three shapes the feed actually serves.
 */
class DevotionRssClientTest {
    private val feed = """
        <rss><channel>
        <item>
        <title>[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日</title>
        <link>https://devotion.wkphc.org/25436</link>
        <pubDate>Wed, 20 Aug 2026 09:42:24 +0000</pubDate>
        <content:encoded><![CDATA[<p>觀畫內文</p>]]></content:encoded>
        </item>
        <item>
        <title><![CDATA[[詩歌靈修] 頌主奇恩 &amp;ndash;2026年8月20日]]></title>
        <link>https://devotion.wkphc.org/25432</link>
        <pubDate>Wed, 19 Aug 2026 11:37:55 +0000</pubDate>
        <content:encoded><![CDATA[<p>詩歌內文</p>]]></content:encoded>
        </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `every item becomes a post in the order the feed lists them`() {
        val posts = parseDevotionRssItems(feed)

        assertEquals(2, posts.size)
        assertEquals("https://devotion.wkphc.org/25436", posts[0].link)
        assertEquals("https://devotion.wkphc.org/25432", posts[1].link)
        // CDATA is unwrapped and left exactly as it was written: `&amp;ndash;` inside `<![CDATA[…]]>`
        // is a literal ampersand-ndash, and decoding entities is HTML work the domain does.
        assertEquals("[詩歌靈修] 頌主奇恩 &amp;ndash;2026年8月20日", posts[1].titleHtml)
    }

    @Test
    fun `the encoded body is the article, not the excerpt`() {
        assertEquals("<p>觀畫內文</p>", parseDevotionRssItems(feed).first().contentHtml)
    }

    @Test
    fun `a feed with only a description still yields the post`() {
        val post = parseDevotionRssItems(
            """
            <rss><channel><item>
            <title>今日靈修</title>
            <link>https://devotion.wkphc.org/25447</link>
            <description><![CDATA[<p>摘要</p>]]></description>
            </item></channel></rss>
            """.trimIndent(),
        ).single()

        assertEquals("<p>摘要</p>", post.contentHtml)
        // The feed carries no post id, which is why `DevotionFeed` keys these by permalink.
        assertEquals(0L, post.id)
    }

    @Test
    fun `an item with no body at all is skipped`() {
        val posts = parseDevotionRssItems(
            """
            <rss><channel>
            <item><title>空</title><link>https://devotion.wkphc.org/1</link></item>
            <item><title>有</title><link>https://devotion.wkphc.org/2</link>
            <content:encoded><![CDATA[<p>內文</p>]]></content:encoded></item>
            </channel></rss>
            """.trimIndent(),
        )

        assertEquals(listOf("https://devotion.wkphc.org/2"), posts.map { it.link })
    }

    @Test
    fun `a feed with no items yields nothing for the client to reject`() {
        assertTrue(parseDevotionRssItems("<rss><channel></channel></rss>").isEmpty())
    }

    @Test
    fun `an rfc 822 pubDate is read without its zone`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 20, 9, 42, 24),
            parseRfc822Date("Wed, 20 Aug 2026 09:42:24 +0000"),
        )
    }

    @Test
    fun `an rfc 822 date may leave the time out and may carry a two-digit year`() {
        assertEquals(LocalDateTime.of(2026, 8, 20, 0, 0), parseRfc822Date("Thu, 20 Aug 26"))
        assertEquals(LocalDateTime.of(2026, 8, 20, 9, 42), parseRfc822Date("20 Aug 2026 09:42 GMT"))
    }

    @Test
    fun `an unreadable date falls back to now instead of throwing`() {
        val now = LocalDateTime.now()
        val parsed = parseRfc822Date("yesterday")

        assertEquals(now.year, parsed.year)
        // `DateTime(2026, 2, 31)` rolled over in Dart; a local date-time refuses, so it lands on now.
        assertEquals(now.year, parseRfc822Date("31 Feb 2026 10:00:00 +0000").year)
    }
}
