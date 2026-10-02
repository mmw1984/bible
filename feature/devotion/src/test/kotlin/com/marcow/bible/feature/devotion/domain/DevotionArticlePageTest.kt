package com.marcow.bible.feature.devotion.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The tier-3 site-page fallback — post discovery on the homepage and article parsing straight from the
 * public pages — ported from `legacy/flutter/test/devotion_site_fallback_test.dart`.
 *
 * This is the only tier that still works when both the REST API and the feed are unreachable, so it is
 * tested against real saved pages rather than markup shaped to suit the parser.
 */
class DevotionArticlePageTest {
    @Test
    fun `finds the newest numeric permalinks on the homepage in document order`() {
        val links = extractPostLinksFromPage(fixture("devotion_site_homepage.html"))

        assertEquals("https://devotion.wkphc.org/25447", links.first().url)
        val newest = links.take(3).map { it.url }
        assertTrue(newest.contains("https://devotion.wkphc.org/25436"), newest.toString())
        assertTrue(newest.contains("https://devotion.wkphc.org/25432"), newest.toString())
        // Newest first, no duplicates.
        assertEquals(links.map { it.url }.toSet().size, links.size)
    }

    @Test
    fun `homepage anchor text carries the full title including the devotion date`() {
        val first = extractPostLinksFromPage(fixture("devotion_site_homepage.html")).first()

        assertTrue(first.title.contains("[圖片靈修]"), first.title)
        assertEquals(LocalDate.of(2026, 8, 22), parseDevotionTitleDate(first.title))
    }

    @Test
    fun `normalizePostHref handles absolute, relative, query and trailing slash forms`() {
        assertEquals(
            "https://devotion.wkphc.org/25447",
            normalizePostHref("https://devotion.wkphc.org/25447"),
        )
        assertEquals("https://devotion.wkphc.org/25447", normalizePostHref("/25447/"))
        assertEquals(
            "https://devotion.wkphc.org/25447",
            normalizePostHref("https://devotion.wkphc.org/25447?utm_source=rss&amp;utm_medium=x"),
        )
    }

    @Test
    fun `normalizePostHref rejects pages, uploads and the feed`() {
        assertNull(normalizePostHref("https://devotion.wkphc.org/feed"))
        assertNull(normalizePostHref("/wp-content/uploads/a.png"))
        assertNull(normalizePostHref("/25"), "two digits is not a post id")
        assertNull(normalizePostHref("/about"))
    }

    @Test
    fun `an article page yields its title, day and id from the entry heading`() {
        val post = checkNotNull(
            parseArticlePage("https://devotion.wkphc.org/25436", fixture("devotion_site_article.html")),
        )

        assertTrue(post.title.contains("[觀畫靈修]"), post.title)
        assertEquals(LocalDate.of(2026, 8, 21), post.devotionDate)
        assertEquals(25436L, post.id)
        assertEquals("https://devotion.wkphc.org/25436", post.link)
    }

    @Test
    fun `the article content container yields the video and the painting gallery`() {
        val post = checkNotNull(
            parseArticlePage("https://devotion.wkphc.org/25436", fixture("devotion_site_article.html")),
        )

        assertTrue(post.blocks.first() is DevotionVideo, "blocks=${post.blocks.take(3)}")

        val images = post.blocks.flatMap { block ->
            when (block) {
                is DevotionImage -> listOf(block)
                is DevotionSection -> block.blocks.filterIsInstance<DevotionImage>()
                else -> emptyList()
            }
        }
        assertTrue(images.size >= 7, "images=${images.size}")
        assertTrue(sectionTitles(post.blocks).contains("觀畫"), sectionTitles(post.blocks).toString())
    }

    @Test
    fun `an article id survives a trailing slash or query on its url`() {
        // `pathSegments.lastWhere((s) => s.isNotEmpty)`: the id is the last non-empty segment,
        // so a permalink that was never canonicalised still resolves instead of becoming 0.
        val html = """
            <html><head><title>x</title></head>
            <body><article><h1>[圖片靈修] 地圖 －2026年8月22日</h1>
            <div class="entry-content"><p>正文</p></div></article></body></html>
        """.trimIndent()

        assertEquals(25436L, checkNotNull(parseArticlePage("https://devotion.wkphc.org/25436/", html)).id)
        assertEquals(
            25436L,
            checkNotNull(parseArticlePage("https://devotion.wkphc.org/25436?utm_source=rss", html)).id,
        )
    }

    @Test
    fun `a page with no content container is rejected instead of cached empty`() {
        assertNull(parseArticlePage("https://devotion.wkphc.org/25436", ""))
        assertNull(parseArticlePage("https://devotion.wkphc.org/25436", "<html><body></body></html>"))
    }

    @Test
    fun `a homepage anchor title overrides the page heading`() {
        val html = """
            <html><head><title>不該被使用 － 靈修默想</title></head>
            <body><article><div class="entry-content"><p>正文</p></div></article></body></html>
        """.trimIndent()

        val post = checkNotNull(parseArticlePage("https://devotion.wkphc.org/25440", html, "[詩歌靈修] 標題"))

        assertEquals("[詩歌靈修] 標題", post.title)
        assertEquals(1, paragraphsOf(post.blocks).size)
    }

    @Test
    fun `the page heading wins when there is no anchor text`() {
        val html = """
            <html><head><title>不該被使用 － 靈修默想</title></head>
            <body><article><h1>[圖片靈修] 地圖 －2026年8月22日</h1>
            <div class="entry-content"><p>正文</p></div></article></body></html>
        """.trimIndent()

        val post = checkNotNull(parseArticlePage("https://devotion.wkphc.org/25447", html))

        assertEquals("[圖片靈修] 地圖 －2026年8月22日", post.title)
        assertEquals(LocalDate.of(2026, 8, 22), post.devotionDate)
    }

    @Test
    fun `serializeHtmlElement round-trips through parse to parse`() {
        val html = """
            <div class="dove"><div class="title">詩歌</div>
            <p>第一段 <b>粗體</b> 文字</p>
            <img src="https://example.com/a.png" />
            <iframe src="https://www.youtube.com/embed/abc1234"></iframe></div>
        """.trimIndent()

        val document = parseHtmlDocument(html)
        val first = parseDevotionBlocksFromElement(document)
        val second = parseDevotionBlocks(serializeHtmlElement(document.children.first() as HtmlElement))

        val original = sectionsOf(first).single()
        val restored = sectionsOf(second).single()
        assertEquals(original.title, restored.title)
        assertEquals("第一段 粗體 文字", paragraphsOf(restored.blocks).single().text)
        assertEquals("https://example.com/a.png", restored.blocks.filterIsInstance<DevotionImage>().single().url)
        assertEquals("abc1234", restored.blocks.filterIsInstance<DevotionVideo>().single().videoId)
    }
}
