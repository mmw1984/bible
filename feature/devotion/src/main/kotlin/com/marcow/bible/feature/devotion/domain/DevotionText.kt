package com.marcow.bible.feature.devotion.domain

import java.time.LocalDate

/**
 * The text helpers the HTML parser leans on, ported from `legacy/flutter/lib/devotion_content.dart`
 * (its "Small utilities" section).
 *
 * They are deliberately narrow: every one of them exists because WordPress output needs it, and each
 * one keeps the Dart behaviour exactly, because the blog's markup and the reader's golden output
 * were captured against it.
 */

/** `&#x27;` / `&#8217;` style references, decoded first so `&amp;#160;` survives the named pass. */
private val NUMERIC_ENTITY = Regex("&#x([0-9a-fA-F]+);|&#(\\d+);")

/**
 * Named references in the order Dart replaced them, which is not the same as any other order:
 * `&amp;` has to run before `&lsquo;` so `&amp;lsquo;` decodes the way a browser renders it.
 *
 * The four quote marks map to the CJK brackets the Chinese posts actually use, which is what the
 * typography in `legacy/flutter` showed; a plain `'` typed in the post is untouched.
 */
private val NAMED_ENTITIES: List<Pair<String, String>> = listOf(
    "&nbsp;" to " ",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
    "&hellip;" to "…",
    "&mdash;" to "—",
    "&ndash;" to "–",
    "&lsquo;" to "『",
    "&rsquo;" to "』",
    "&ldquo;" to "「",
    "&rdquo;" to "」",
)

/**
 * Decodes the references WordPress emits, mirroring `decodeHtmlEntities`.
 *
 * A browser resolves every entity in the HTML5 table; this resolves the ones the blog uses, because
 * the rest of the parser reads text that has already been through here and an unresolved `&` would
 * be indistinguishable from markup.
 */
internal fun decodeHtmlEntities(input: String): String {
    var output = NUMERIC_ENTITY.replace(input) { match ->
        val hex = match.groupValues[1]
        val code = if (hex.isNotEmpty()) hex.toLongOrNull(16) else match.groupValues[2].toLongOrNull()
        if (code == null || code <= 0L || code > 0x10FFFFL) "" else String(Character.toChars(code.toInt()))
    }
    for ((entity, replacement) in NAMED_ENTITIES) {
        output = output.replace(entity, replacement)
    }
    return output
}

/** Drops tags the way `stripHtmlTags` did, so an `<em>`-wrapped title reads as one string. */
internal fun stripHtmlTags(html: String): String = TAG.replace(html, " ")

private val TAG = Regex("<[^>]*>")

/**
 * Percent-encodes the non-ASCII characters WordPress leaves raw inside `<img src>` — CJK filenames
 * like `螢幕截圖-2026-08-20-下午5.16.35.png`.
 *
 * The bytes a URI request may carry are ASCII-only, and the server expects RFC 3986 encoding. Only
 * characters above U+007F are touched, so an already-encoded URL comes back unchanged instead of
 * having its `%` escaped into `%25`.
 */
internal fun normalizeDevotionImageUrl(url: String): String =
    NON_ASCII.replace(url.trim()) { match -> encodeUriComponent(match.value) }

/** Shared with the tests that assert a parsed URL is ASCII-only. */
internal val NON_ASCII = Regex("[^\\x00-\\x7F]")

/** Dart's `Uri.encodeComponent`, which keeps the RFC 3986 sub-delimiters unescaped. */
private fun encodeUriComponent(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        if (byte >= 0 && byte.toChar() in UNRESERVED) {
            out.append(byte.toChar())
        } else {
            out.append('%').append(HEX_DIGITS[(byte shr 4) and 0xF]).append(HEX_DIGITS[byte and 0xF])
        }
    }
    return out.toString()
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

private const val HEX_DIGITS = "0123456789ABCDEF"

/** The three ways the blog links a YouTube player, mirroring `parseYouTubeId`. */
private val YOUTUBE_ID = Regex(
    "youtube\\.com/embed/([\\w-]{6,})|youtu\\.be/([\\w-]{6,})|[?&]v=([\\w-]{6,})",
    RegexOption.IGNORE_CASE,
)

/** The video id inside a YouTube embed or watch URL, `null` for any other iframe. */
internal fun parseYouTubeId(url: String): String? =
    YOUTUBE_ID.find(url)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }

private val DEVOTION_TITLE_DATE = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日")

/** The title's `YYYY年M月D日` triple as numbers, `null` when the title carries no such triple. */
private fun parseTitleNumbers(title: String): List<Int>? {
    val groups = DEVOTION_TITLE_DATE.find(title)?.groupValues?.drop(1) ?: return null
    val numbers = groups.mapNotNull { it.toIntOrNull() }
    return if (numbers.size == groups.size) numbers else null
}

/**
 * The `YYYY年M月D日` day a devotion is for, read out of its title — every post title ends with it,
 * so it is the only calendar the site publishes.
 */
internal fun parseDevotionTitleDate(title: String): LocalDate? {
    val numbers = parseTitleNumbers(title) ?: return null
    val year = numbers[0]
    val month = numbers[1]
    val day = numbers[2]
    val monthOutOfRange = month < 1 || month > 12
    val dayOutOfRange = day < 1 || day > 31
    if (monthOutOfRange || dayOutOfRange) return null
    // Dart's `DateTime(y, m, d)` rolled an out-of-range day into the next month; `LocalDate.of` throws.
    return LocalDate.of(year, month, 1).plusDays((day - 1).toLong())
}
