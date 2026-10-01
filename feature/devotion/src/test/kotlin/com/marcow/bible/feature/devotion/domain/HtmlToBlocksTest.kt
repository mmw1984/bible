package com.marcow.bible.feature.devotion.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The block parser against hand-written markup, ported from `legacy/flutter/test/devotion_parser_test.dart`.
 *
 * These are the shapes the posts are assembled from — titled sections, nested wrappers, media inside a
 * paragraph, list items, lazy-loaded images — and the malformed markup the blog genuinely ships.
 */
class HtmlToBlocksTest {
    @Test
    fun `parses the 8月20日 poetry devotion with video and sections`() {
        val html = """
            <p><iframe title="YouTube video player"
            src="https://www.youtube.com/embed/8IwFu8d7aYE?si=uyCeuFdDLRCYo0Uk"
            width="560" height="315" frameborder="0" allowfullscreen="allowfullscreen"></iframe><br />
            歡迎大家收聽靈食傳說這個靈修頻道。</p>
            <div class="dove">
            <div class="title">詩歌</div>
            <p>讓我們聆聽詩歌。</p>
            <p style="text-align: center;"><strong>《頌主奇恩》</strong></p>
            </div>
            <div class="explain">
            <div class="title">分享</div>
            <p>這首詩歌帶我們走進一幅極其豐富的大自然畫卷。</p>
            <p>詩篇 19:1 說：</p>
            <blockquote>諸天述說神的榮耀。</blockquote>
            </div>
            <div class="pray">
            <div class="title">結束祈禱</div>
            <p>親愛的天父，感謝祢。</p>
            </div>
        """.trimIndent()

        val blocks = parseDevotionBlocks(html)

        val videos = blocks.filterIsInstance<DevotionVideo>()
        assertEquals(1, videos.size, "blocks=$blocks")
        assertEquals("8IwFu8d7aYE", videos.single().videoId)

        val sections = sectionsOf(blocks)
        assertEquals(listOf("詩歌", "分享", "結束祈禱"), sections.map { it.title })
        assertTrue(sections[0].blocks.filterIsInstance<DevotionParagraph>().isNotEmpty())
        assertEquals(1, sections[1].blocks.filterIsInstance<DevotionQuote>().size)
        // The section titles must not leak through as stray headings.
        assertTrue(blocks.filterIsInstance<DevotionHeading>().isEmpty())
    }

    @Test
    fun `plain paragraphs without sections still parse`() {
        val blocks = parseDevotionBlocks(
            """<p>第一段</p><p>第二段</p><figure><img src="https://x/y.jpg" /></figure>""",
        )
        assertEquals(2, blocks.filterIsInstance<DevotionParagraph>().size)
        assertEquals(1, blocks.filterIsInstance<DevotionImage>().size)
    }

    @Test
    fun `parses nested titled sections and flattens them into siblings`() {
        // The real 觀畫靈修 markup: 安靜 wraps 經文, which wraps 觀畫.
        val html = """
            <p>弟兄姊妹，歡迎收聽2026年8月21日的靈修默想。</p>
            <div class="dove">
            <div class="title">安靜</div>
            <p>讓我們先找一個安靜的空間。</p>
            <div class="verse">
            <div class="title">經文：創世記12:10-20(和修版)</div>
            <p>10 那地遭遇饑荒。</p>
            <div class="explain">
            <div class="title">觀畫</div>
            <p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting.jpg"
            alt="" /></p>
            <p>弟兄姊妹，今日讓我們觀賞這幅畫作。</p>
            </div>
            <p>19 為甚麼說『她是我的妹妹』？</p>
            </div>
            </div>
            <div class="pray">
            <div class="title">結束祈禱</div>
            <p>親愛的天父，感謝祢。</p>
            </div>
        """.trimIndent()

        val topLevel = sectionsOf(parseDevotionBlocks(html))

        // Container sections 安靜/經文 are flattened to siblings.
        assertEquals(
            listOf("安靜", "經文：創世記12:10-20(和修版)", "觀畫", "結束祈禱"),
            topLevel.map { it.title },
        )
        // 安靜 keeps its intro paragraph.
        assertTrue(topLevel[0].blocks.filterIsInstance<DevotionParagraph>().isNotEmpty())
        // 經文 keeps its verses, including the paragraph after the 觀畫 section.
        val verse = topLevel.first { it.title.startsWith("經文") }
        assertEquals(2, verse.blocks.filterIsInstance<DevotionParagraph>().size)
        // 觀畫 is now a top-level sibling, and it carries the painting.
        val painting = topLevel.first { it.title == "觀畫" }
        assertEquals(1, painting.blocks.filterIsInstance<DevotionImage>().size)
    }

    @Test
    fun `deduplicates repeated WordPress image sizes`() {
        val html = """
            <div class="explain">
            <div class="title">觀畫</div>
            <p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting-1024x768.jpg" /></p>
            <p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting-300x200.jpg" /></p>
            <p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting.jpg" /></p>
            <p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/detail-2026-08-20-1.jpg" /></p>
            </div>
        """.trimIndent()

        val section = sectionsOf(parseDevotionBlocks(html)).single()

        // The three sizes share a base URL and dedup to one; the detail image is a second painting.
        assertEquals(2, section.blocks.filterIsInstance<DevotionImage>().size)
    }

    @Test
    fun `title date parsing`() {
        assertEquals(
            LocalDate.of(2026, 8, 21),
            parseDevotionTitleDate("[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日"),
        )
        assertNull(parseDevotionTitleDate("[詩歌靈修] 頌主奇恩"))
    }

    @Test
    fun `image URLs with raw CJK are percent-encoded at parse time`() {
        val html = "<p><img src=\"https://devotion.wkphc.org/wp-content/uploads/2026/08/" +
            "螢幕截圖-2026-08-20-下午5.16.35-1024x714.png\" /></p>"

        val url = parseDevotionBlocks(html).filterIsInstance<DevotionImage>().single().url

        assertFalse(NON_ASCII.containsMatchIn(url), url)
        assertTrue(url.contains("%E8%9E%A2"), url) // 螢
        // Idempotent: an already-encoded URL comes back untouched.
        assertEquals(url, normalizeDevotionImageUrl(url))
    }

    @Test
    fun `SoundCloud iframes are kept as tappable embeds, not dropped`() {
        val src = "https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com%2Ftracks%2F2378777798" +
            "&amp;color=%23ff5500&amp;auto_play=false"
        val html = """
            <p><iframe width="100%" height="166" scrolling="no" frameborder="no"
            allow="autoplay; encrypted-media" src="$src">
            </iframe></p>
        """.trimIndent()

        val embed = parseDevotionBlocks(html).filterIsInstance<DevotionEmbed>().single()

        assertTrue(embed.url.startsWith("https://w.soundcloud.com/player/"), embed.url)
        // Entities inside the src are decoded; nothing is lost.
        assertFalse(embed.url.contains("&amp;"), embed.url)
    }

    @Test
    fun `lazy-loaded images fall back to data-src placeholders`() {
        // WP-Smush parks a 1×1 SVG in `src` and moves the real image into `data-src`.
        val html = """
            <p><img decoding="async" class="alignnone wp-image-1 lazyload"
            data-src="https://example.com/gallery/painting-1024x714.png"
            width="600" height="420"
            src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMSIgaGVpZ2h0PSIxIj48L3N2Zz4+"
            style="--smush-placeholder-width: 600px;" /></p>
        """.trimIndent()

        val url = parseDevotionBlocks(html).filterIsInstance<DevotionImage>().single().url

        assertEquals("https://example.com/gallery/painting-1024x714.png", url)
    }

    @Test
    fun `eager images still win over their lazy duplicates`() {
        val html = """
            <p><img decoding="async" class="lazyload"
            data-src="https://example.com/real.jpg" src="https://example.com/eager.jpg" /></p>
        """.trimIndent()

        val url = parseDevotionBlocks(html).filterIsInstance<DevotionImage>().single().url

        assertEquals("https://example.com/eager.jpg", url)
    }

    @Test
    fun `br keeps an in-paragraph newline instead of a space`() {
        val paragraphs = parseDevotionBlocks("<p>第一行。\n   <br />\n   第二行。</p>")
            .filterIsInstance<DevotionParagraph>()

        assertEquals(1, paragraphs.size, "must stay one block")
        assertEquals("第一行。\n第二行。", paragraphs.single().text)
    }

    @Test
    fun `a paragraph of only br markers does not become a blank block`() {
        val blocks = parseDevotionBlocks("<p><br /></p><p><img src=\"https://x/y.jpg\" /></p>")

        assertTrue(blocks.filterIsInstance<DevotionParagraph>().isEmpty(), "blocks=$blocks")
        assertEquals(1, blocks.filterIsInstance<DevotionImage>().size)
    }

    @Test
    fun `unbalanced divs never throw or duplicate sections`() {
        // Both wrappers are left open, which is how the blog ends a post that ran out of `</div>`.
        val html = """
            <div class="dove"><div class="title">詩歌</div><p>第一段</p>
            <div class="explain"><div class="title">分享</div><p>第二段</p></div>
        """.trimIndent()

        val titles = mutableListOf<String>()
        fun collect(blocks: List<DevotionBlock>) {
            for (block in blocks) {
                if (block is DevotionSection) {
                    titles.add(block.title)
                    collect(block.blocks)
                }
            }
        }

        collect(parseDevotionBlocks(html))

        assertEquals(1, titles.count { it == "詩歌" }, "titles=$titles")
        assertEquals(1, titles.count { it == "分享" }, "titles=$titles")
    }

    @Test
    fun `stray close tags are ignored`() {
        val blocks = parseDevotionBlocks("<p>文字</p></div></div><p>更多文字</p>")

        assertEquals(2, blocks.filterIsInstance<DevotionParagraph>().size)
    }

    @Test
    fun `a truncated document yields what parsed so far`() {
        val blocks = parseDevotionBlocks("""<div class="pray"><div class="title">結束祈禱</div><p>阿們""")

        assertEquals("結束祈禱", sectionsOf(blocks).single().title)
    }

    @Test
    fun `empty input yields no blocks`() {
        assertTrue(parseDevotionBlocks("").isEmpty())
        assertTrue(parseDevotionBlocks("   \n ").isEmpty())
    }

    @Test
    fun `list items become bullets`() {
        val blocks = parseDevotionBlocks("<ul><li>第一點</li><li>第二點</li></ul>")

        assertEquals(
            listOf("• 第一點", "• 第二點"),
            blocks.filterIsInstance<DevotionParagraph>().map { it.text },
        )
    }

    @Test
    fun `entities decode to the CJK punctuation the posts are written with`() {
        val blocks = parseDevotionBlocks("<p>&lsquo;她是我的妹妹&rsquo;&mdash;&hellip;&nbsp;&#160;&amp;</p>")

        // `&nbsp;` and `&#160;` are both whitespace once decoded, so they collapse to one space.
        assertEquals("『她是我的妹妹』—… &", blocks.filterIsInstance<DevotionParagraph>().single().text)
    }

    @Test
    fun `script bodies never reach a block`() {
        val blocks = parseDevotionBlocks("<div><script>var a = 1;</script><p>正文</p></div>")

        assertEquals(listOf("正文"), blocks.filterIsInstance<DevotionParagraph>().map { it.text })
    }

    @Test
    fun `a titled section keeps its body and drops nothing but the title`() {
        val blocks = parseDevotionBlocks(
            """<div class="dove"><div class="title">詩歌</div><p>讓我們聆聽詩歌。</p></div>""",
        )

        val section = sectionsOf(blocks).single()

        assertEquals("詩歌", section.title)
        assertEquals(
            listOf("讓我們聆聽詩歌。"),
            section.blocks.filterIsInstance<DevotionParagraph>().map { it.text },
        )
    }
}
