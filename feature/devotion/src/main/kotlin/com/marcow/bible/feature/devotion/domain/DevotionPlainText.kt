package com.marcow.bible.feature.devotion.domain

import com.marcow.bible.core.model.AppLocale
import java.time.LocalDate

/**
 * Date formatting and the one-tap copy export, ported from the tail of
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * The CJK dates are built by hand rather than through `DateTimeFormatter`, because the blog writes
 * `2026年8月21日` with no leading zeros and `DateTimeFormatter.ofPattern("uuuu年M月d日")` would agree —
 * but the English form needs `Aug 21, 2026`, which needs a different pattern per locale, and the two
 * locales this app ships are exactly these two.
 */

private val ENGLISH_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

internal fun formatDevotionDate(date: LocalDate, locale: AppLocale): String = when (locale) {
    AppLocale.EN -> "${ENGLISH_MONTHS[date.monthValue - 1]} ${date.dayValue}, ${date.year}"
    AppLocale.ZH_HANT -> "${date.year}年${date.monthValue}月${date.dayValue}日"
}

internal fun formatDevotionDateShort(date: LocalDate, locale: AppLocale): String = when (locale) {
    AppLocale.EN -> "${ENGLISH_MONTHS[date.monthValue - 1]} ${date.dayValue}"
    AppLocale.ZH_HANT -> "${date.monthValue}月${date.dayValue}日"
}

/**
 * Flattens a post into shareable plain text: title, devotion day, then one line per text block with
 * `【section】` markers.
 *
 * Media has no text form — a video keeps its watch URL, because a link to the song is what the reader
 * would have used — while images and embeds are dropped, since a URL on its own says nothing.
 */
internal fun devotionPostToPlainText(post: DevotionPost, locale: AppLocale): String {
    val lines = mutableListOf(post.title, formatDevotionDate(post.devotionDate, locale), "")

    fun writeBlocks(blocks: List<DevotionBlock>) {
        for (block in blocks) {
            when (block) {
                is DevotionParagraph -> lines.add(block.text)
                is DevotionHeading -> lines.add(block.text)
                is DevotionQuote -> lines.add(block.text)
                is DevotionSection -> {
                    lines.add("【${block.title}】")
                    writeBlocks(block.blocks)
                }

                is DevotionVideo -> lines.add(block.watchUrl)
                is DevotionImage, is DevotionEmbed -> Unit
            }
        }
    }

    writeBlocks(post.blocks)
    return lines.joinToString("\n").trimEnd()
}
