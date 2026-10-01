package com.marcow.bible.feature.devotion.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The block parser against real WordPress markup saved from devotion.wkphc.org, ported from
 * `legacy/flutter/test/devotion_fixtures_test.dart`.
 *
 * These fixtures are the posts that previously rendered title-only when the parser gave up, so they
 * cover every shape the blog publishes: nested wrappers, a nine-image gallery, list items, a
 * SoundCloud player and a ten-section long-form article.
 */
class DevotionFixturesTest {
    @Test
    fun `觀畫 nested dove over verse over explain keeps video, five sections and bodies`() {
        val blocks = parseDevotionBlocks(fixture("devotion_guanhua.html"))

        assertTrue(blocks.first() is DevotionVideo, "blocks=$blocks")
        assertEquals(
            listOf("安靜", "經文：創世記12:10-20(和修版)", "觀畫", "思想／祈禱", "結束祈禱"),
            sectionTitles(blocks),
        )

        val sections = sectionsOf(blocks)
        // 安靜 keeps only its intro; the nested 經文 is lifted out beside it.
        assertEquals(1, sections[0].blocks.filterIsInstance<DevotionParagraph>().size)
        assertEquals(3, sections[1].blocks.filterIsInstance<DevotionParagraph>().size)
    }

    @Test
    fun `觀畫 carries the painting gallery interleaved with commentary`() {
        val painting = sectionsOf(parseDevotionBlocks(fixture("devotion_guanhua.html")))
            .first { it.title == "觀畫" }

        // Eleven img tags collapse to the eight distinct originals WordPress uploaded.
        assertTrue(painting.blocks.filterIsInstance<DevotionImage>().size >= 7)

        val texts = painting.blocks.filterIsInstance<DevotionParagraph>().map { it.text }
        assertTrue(texts.any { it.contains("穆齊奧利") }, texts.toString())
        assertTrue(texts.any { it.contains("神並沒有向亞伯蘭失信") }, texts.toString())
    }

    @Test
    fun `CJK screenshot URLs are percent-encoded`() {
        val blocks = parseDevotionBlocks(fixture("devotion_guanhua.html"))

        // Images live one level down inside a titled section.
        val urls = blocks.flatMap { block ->
            when (block) {
                is DevotionImage -> listOf(block.url)
                is DevotionSection -> block.blocks.filterIsInstance<DevotionImage>().map { it.url }
                else -> emptyList()
            }
        }

        assertTrue(urls.isNotEmpty())
        assertTrue(urls.none { NON_ASCII.containsMatchIn(it) }, urls.toString())
    }

    @Test
    fun `詩歌 flat sections render video, four sections and song lyrics`() {
        val blocks = parseDevotionBlocks(fixture("devotion_shige.html"))

        assertTrue(blocks.first() is DevotionVideo, "blocks=$blocks")
        assertEquals(listOf("詩歌", "分享", "思想／祈禱", "結束祈禱"), sectionTitles(blocks))

        val lyrics = sectionsOf(blocks).first().blocks
            .filterIsInstance<DevotionParagraph>()
            .joinToString("\n") { it.text }
        assertTrue(lyrics.contains("蝶舞於花間"), lyrics.take(120))
        assertTrue(lyrics.contains("副歌"), lyrics.take(120))
    }

    @Test
    fun `詩歌 emits no phantom empty paragraphs`() {
        assertNoEmptyText(parseDevotionBlocks(fixture("devotion_shige.html")))
    }

    @Test
    fun `詩歌 turns list items into bullets inside 思想／祈禱`() {
        val section = sectionsOf(parseDevotionBlocks(fixture("devotion_shige.html")))
            .first { it.title == "思想／祈禱" }

        val bullets = section.blocks.filterIsInstance<DevotionParagraph>()
            .filter { it.text.startsWith("• ") }

        assertEquals(2, bullets.size, "bullets=$bullets")
    }

    @Test
    fun `靈修默想 keeps the four titled sections`() {
        val blocks = parseDevotionBlocks(fixture("devotion_moxiang.html"))

        assertEquals(
            listOf("王上十二9～11（和修版）", "淺釋", "默想／祈禱", "禱文"),
            sectionTitles(blocks),
        )
        // The SoundCloud attribution is prose, not an embed.
        assertTrue(
            blocks.filterIsInstance<DevotionParagraph>().any { it.text.contains("永光e電園") },
            "blocks=$blocks",
        )
        // The SoundCloud player never becomes a video, however it is marked up.
        assertTrue(blocks.filterIsInstance<DevotionVideo>().isEmpty())
    }

    @Test
    fun `傳道書 keeps every titled section in order`() {
        val titles = sectionTitles(parseDevotionBlocks(fixture("devotion_chuandao.html")))

        assertEquals(
            listOf(
                "聆聽聖言:《傳道書 12:8-14》",
                "回歸起點︰虛空的虛空，凡事虛空",
                "傳道者的角色︰誠實追問的智慧導師",
                "刺棍與釘子︰智慧言語的刺痛與穩固",
                "書本的局限︰知識不能取代敬畏",
                "最終的結論︰敬畏神，謹守誡命，這是人所當盡的本分",
                "一切隱藏的事︰活在神的眼光之下",
                "思考課題",
                "默想生命",
                "禱文",
            ),
            titles,
        )
    }

    @Test
    fun `傳道書 preserves the download link paragraph`() {
        val blocks = parseDevotionBlocks(fixture("devotion_chuandao.html"))

        assertTrue(
            blocks.filterIsInstance<DevotionParagraph>().any { it.text.contains("PDF 文字版下載") },
            "blocks=$blocks",
        )
    }

    @Test
    fun `圖片 renders video, map image, prose and two sections`() {
        val blocks = parseDevotionBlocks(fixture("devotion_tupian.html"))

        assertTrue(blocks.first() is DevotionVideo, "blocks=$blocks")
        assertTrue(blocks[1] is DevotionImage, "blocks=$blocks")
        assertEquals(5, blocks.filterIsInstance<DevotionParagraph>().size)
        assertEquals(listOf("思想／祈禱", "結束祈禱"), sectionTitles(blocks))
    }

    @Test
    fun `every fixture parses without throwing`() {
        for (name in listOf("chuandao", "guanhua", "moxiang", "shige", "tupian")) {
            val blocks = parseDevotionBlocks(fixture("devotion_$name.html"))
            assertFalse(blocks.isEmpty(), name)
            assertNoEmptyText(blocks)
        }
    }

    @Test
    fun `觀畫 exercises video, section and image blocks in one post`() {
        val blocks = parseDevotionBlocks(fixture("devotion_guanhua.html"))

        assertTrue(blocks.any { it is DevotionVideo })
        assertTrue(blocks.any { it is DevotionSection })
        assertTrue(sectionsOf(blocks).any { it.blocks.any { it is DevotionImage } })
        assertFalse(blocks.any { it is DevotionEmbed }, "no embeds in the 觀畫 fixture")
    }
}
