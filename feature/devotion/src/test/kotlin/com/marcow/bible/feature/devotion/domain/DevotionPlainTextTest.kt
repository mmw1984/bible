package com.marcow.bible.feature.devotion.domain

import com.marcow.bible.core.model.AppLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The one-tap copy export, ported from `legacy/flutter/test/devotion_parser_test.dart` and pinned
 * against the real 觀畫 post as well.
 *
 * This is the only place the reader gets a devotion *out* of the app, so it is the one where a silent
 * change is felt by someone pasting the text into a message: the order of the lines, the `【】` a
 * section is marked with, and which media survives as a URL are all part of the contract.
 */
class DevotionPlainTextTest {
    @Test
    fun `the export opens with the title and the day, then a blank line`() {
        val post = postOf(
            blocks = listOf(
                DevotionParagraph("開場白。"),
                DevotionParagraph("第二段。"),
            ),
        )

        assertEquals(
            """
            今日靈修
            2026年9月22日

            開場白。
            第二段。
            """.trimIndent(),
            devotionPostToPlainText(post, AppLocale.ZH_HANT),
        )
    }

    @Test
    fun `the day is written the way the English page writes it`() {
        val post = postOf(blocks = listOf(DevotionParagraph("Opening.")))

        assertEquals(
            """
            Today's devotion
            Sep 22, 2026

            Opening.
            """.trimIndent(),
            devotionPostToPlainText(post, AppLocale.EN),
        )
    }

    @Test
    fun `a section is a marker line and its blocks follow it, nested sections included`() {
        val post = postOf(
            blocks = listOf(
                DevotionParagraph("開場白。"),
                DevotionSection(
                    title = "分享",
                    blocks = listOf(
                        DevotionHeading("小標"),
                        DevotionParagraph("分享內容。"),
                        DevotionSection(
                            title = "詩歌",
                            blocks = listOf(DevotionQuote("經文金句。")),
                        ),
                    ),
                ),
                DevotionParagraph("結語。"),
            ),
        )

        assertEquals(
            """
            今日靈修
            2026年9月22日

            開場白。
            【分享】
            小標
            分享內容。
            【詩歌】
            經文金句。
            結語。
            """.trimIndent(),
            devotionPostToPlainText(post, AppLocale.ZH_HANT),
        )
    }

    @Test
    fun `a video keeps its watch URL, while an image and an embed leave nothing behind`() {
        val post = postOf(
            blocks = listOf(
                DevotionParagraph("開場白。"),
                DevotionSection(
                    title = "分享",
                    blocks = listOf(
                        DevotionHeading("小標"),
                        DevotionParagraph("分享內容。"),
                        DevotionQuote("經文金句。"),
                    ),
                ),
                DevotionVideo("abc123"),
                DevotionImage("https://example.com/pic.jpg"),
                DevotionEmbed("https://w.soundcloud.com/player/?url=x"),
            ),
        )

        val text = devotionPostToPlainText(post, AppLocale.ZH_HANT)

        assertTrue(text.startsWith("今日靈修\n"), text.take(40))
        assertTrue(text.contains("2026年9月22日"), text)
        assertTrue(text.contains("開場白。"), text)
        assertTrue(text.contains("【分享】"), text)
        assertTrue(text.contains("小標"), text)
        assertTrue(text.contains("分享內容。"), text)
        assertTrue(text.contains("經文金句。"), text)
        assertTrue(text.contains("https://www.youtube.com/watch?v=abc123"), text)
        assertFalse(text.contains("https://example.com/pic.jpg"), "an image has no text form")
        assertFalse(text.contains("w.soundcloud.com"), "an embed is skipped, only a video keeps a URL")
    }

    @Test
    fun `a post ending in media does not end in blank lines`() {
        val post = postOf(
            blocks = listOf(
                DevotionParagraph("開場白。"),
                DevotionImage("https://example.com/pic.jpg"),
                DevotionEmbed("https://w.soundcloud.com/player/?url=x"),
            ),
        )

        // The dropped blocks leave an empty line each if the export does not trim, and a reader pasting
        // the text would get them at the end of the message.
        assertEquals(
            """
            今日靈修
            2026年9月22日

            開場白。
            """.trimIndent(),
            devotionPostToPlainText(post, AppLocale.ZH_HANT),
        )
    }

    @Test
    fun `a post with no blocks is the title and the day and nothing else`() {
        assertEquals(
            "今日靈修\n2026年9月22日",
            devotionPostToPlainText(postOf(blocks = emptyList()), AppLocale.ZH_HANT),
        )
    }

    @Test
    fun `a real 觀畫 post exports every section marker, its video link and none of its pictures`() {
        val blocks = parseDevotionBlocks(fixture("devotion_guanhua.html"))
        val post = postOf(title = "觀畫靈修", blocks = blocks)

        val text = devotionPostToPlainText(post, AppLocale.ZH_HANT)

        // One marker per section, in the order the sections were parsed, nested ones included.
        val written = text.lines().filter { it.startsWith("【") }.map { it.removePrefix("【").removeSuffix("】") }
        assertEquals(blocks.allSectionTitles(), written)
        assertTrue(written.contains("觀畫"), written.toString())
        for (video in blocks.filterIsInstance<DevotionVideo>()) {
            assertTrue(text.contains(video.watchUrl), "the export dropped the video ${video.videoId}")
        }
        for (image in blocks.imageUrls()) {
            assertFalse(text.contains(image), "the export kept the picture $image")
        }
    }
}

/** The post the tests above copy, with the day and publish time the Flutter test gave it. */
private fun postOf(
    blocks: List<DevotionBlock>,
    title: String = "今日靈修",
    date: LocalDate = LocalDate.of(2026, 9, 22),
): DevotionPost = DevotionPost(
    id = 1,
    publishedAt = LocalDateTime.of(date, LocalTime.MIN),
    devotionDate = date,
    title = title,
    link = "https://devotion.wkphc.org/25447",
    contentHtml = "",
    blocks = blocks,
)

/** Every section title in the tree, in the order they were written. */
private fun List<DevotionBlock>.allSectionTitles(): List<String> = flatMap { block ->
    when (block) {
        is DevotionSection -> listOf(block.title) + block.blocks.allSectionTitles()
        else -> emptyList()
    }
}

/** Every picture in the tree, at any depth, so a test can prove none of them reached the export. */
private fun List<DevotionBlock>.imageUrls(): List<String> = flatMap { block ->
    when (block) {
        is DevotionImage -> listOf(block.url)
        is DevotionSection -> block.blocks.imageUrls()
        else -> emptyList()
    }
}
