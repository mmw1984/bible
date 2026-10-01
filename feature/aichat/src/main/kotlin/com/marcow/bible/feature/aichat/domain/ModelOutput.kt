package com.marcow.bible.feature.aichat.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * `_cleanModelOutput`: the markers first, then a leading metadata preamble, then citations, then a
 * trim — what the chat does with what the model sent back, moved across from `_cleanModelOutput`,
 * `sanitizeReasoningForDisplay` and `_appendWithoutDuplicate` in `legacy/flutter/lib/ai_service.dart`.
 *
 * None of this is tidying for its own sake. Every function in this file exists because the chat shows
 * the text raw and the model does not always send only text: the continuation markers are an
 * instruction to the model and not to the reader, citation markers and source lists are the web
 * tool's business, and a safety preamble the provider prepended is not part of the answer.
 *
 * The order is Dart's and it matters: the markers are whole answer tokens, the metadata check reads
 * the *lines* it is left with, and the citation patterns need the answer's paragraphs in place to
 * recognise a trailing `Sources:` list. A different order leaves a half-stripped answer on screen.
 *
 * The same patterns are copied in `feature/search/domain/ModelOutput.kt` for the search overview —
 * features never depend on each other, so the two copies stay in step by hand and a change to one is
 * a change to the other.
 */
internal fun cleanModelOutput(source: String): String {
    val withoutMarkers = source
        .replace("[[END]]", "")
        .replace("[[MORE]]", "")
        .replaceFirst(INCOMPLETE_CONTINUATION_MARKER, "")
    return stripWebCitations(stripLeadingInternalMetadata(withoutMarkers)).trim()
}

/**
 * `sanitizeReasoningForDisplay`: the thinking channel loses the same metadata lines, wherever they
 * are rather than only at the front.
 *
 * Exported because the UI shows this text, and the prompt tells the model to keep safety
 * classifications out of it: a provider that ignores that instruction still emits them, and the
 * reader should not see `safety: safe` where the reasoning is supposed to be.
 */
internal fun sanitizeReasoningForDisplay(source: String): String {
    val lines = source.split("\n")
    val cleaned = lines.filterIndexed { index, line ->
        val trimmed = line.trim()
        if (REASONING_METADATA_LINE.containsMatchIn(trimmed)) {
            false
        } else {
            val incompleteLastLine = index == lines.size - 1 && !source.endsWith("\n")
            !(incompleteLastLine && looksLikeReasoningMetadataPrefix(trimmed))
        }
    }
    return cleaned.joinToString("\n").trim()
}

/**
 * `_appendWithoutDuplicate`: [addition] joined onto [existing] without the repeat a continued answer
 * usually starts with.
 *
 * The continuation round asks the model to pick up after the last characters it wrote, and a model
 * that ignores that opens its continuation with the same title it already used. This decides what to
 * do about that, and it is all heuristics because there is no signal that says "the model restarted":
 * whether the addition *contains* or *ends with* what came before, whether the two openings are the
 * same line, and how much of the front the addition repeats.
 *
 * [additionComplete] is `rawSegment.contains('[[END]]')` from the caller: a marker-free answer that
 * repeats itself is a restart worth keeping when it is finished, and a duplicate paragraph to drop
 * when it is not.
 */
internal fun appendWithoutDuplicate(existing: String, addition: String, additionComplete: Boolean = false): String {
    val left = existing.trimEnd()
    val right = addition.trimStart()
    if (left.isEmpty()) return right
    return when {
        right.isEmpty() || left.endsWith(right) -> left
        right.startsWith(left) -> right
        left.startsWith(right) -> if (additionComplete) right else left
        right.contains(left) -> right
        left.contains(right) -> if (additionComplete) right else left
        LEADING_PUNCTUATION.containsMatchIn(right) -> left + right
        else -> mergeAcrossOverlap(left, right, additionComplete)
    }
}

/**
 * The last half of [appendWithoutDuplicate]: glue the two together around whatever they share.
 *
 * Two ways to glue, in Dart's order. First the overlap — up to [MIN_OVERLAP] characters the end of
 * the answer may already be the start of the continuation, in which case only what is new is kept.
 * Failing that, a restart is judged by the opening line repeating or by enough of the front being
 * common to be a restart rather than an overlap; a restart replaces the answer instead of following
 * it, and which of the two wins is [additionComplete] or the longer one.
 */
private fun mergeAcrossOverlap(left: String, right: String, additionComplete: Boolean): String {
    val shorterLength = min(left.length, right.length)
    for (overlap in shorterLength downTo MIN_OVERLAP) {
        if (left.endsWith(right.substring(0, overlap))) return left + right.substring(overlap)
    }
    val commonPrefix = commonPrefixLength(left, right, shorterLength)
    val restartThreshold = if (shorterLength < SHORT_ANSWER_LENGTH) {
        restartThresholdFor(shorterLength)
    } else {
        RESTART_PREFIX_LENGTH
    }
    val leftOpening = left.lineSequence().first().trim()
    val rightOpening = right.lineSequence().first().trim()
    val repeatedOpening = leftOpening.length >= REPEATED_OPENING_MINIMUM && leftOpening == rightOpening
    return if (repeatedOpening || commonPrefix >= restartThreshold) {
        if (additionComplete || right.length >= left.length) right else left
    } else {
        "$left\n\n$right"
    }
}

/**
 * How much of the front the two answers agree on, compared code unit by code unit the way Dart's
 * `codeUnitAt` loop did — so a pair differing only by a surrogate half counts as different, which is
 * what a non-BMP character in the answer would do.
 */
private fun commonPrefixLength(left: String, right: String, shorterLength: Int): Int {
    var commonPrefix = 0
    while (commonPrefix < shorterLength && left[commonPrefix] == right[commonPrefix]) {
        commonPrefix++
    }
    return commonPrefix
}

/**
 * `(shorterLength * .4).ceil().clamp(6, 12)`: the restart threshold for a short answer.
 *
 * A shorter answer needs a proportionally smaller amount of repeated text to count as a restart, but
 * never fewer than six characters — three characters of coincidence is not a restart — and never more
 * than twelve, or a two-line answer would need more agreement than a long one.
 */
private fun restartThresholdFor(shorterLength: Int): Int =
    min(max(ceil(shorterLength * 0.4).toInt(), MIN_RESTART_PREFIX), MAX_RESTART_PREFIX)

/** `_stripWebCitations`: the markers OpenRouter and the models put around their own sources. */
private fun stripWebCitations(source: String): String {
    var cleaned = source.replace(OPENROUTER_CITATION, "")
    cleaned = cleaned.replace(FULLWIDTH_CITATION, "")
    cleaned = cleaned.replace(BARE_CITATION, "")
    cleaned = cleaned.replace(SOURCES_BLOCK, "")
    return cleaned
}

/**
 * `_stripLeadingInternalMetadata`: safety classification preambles the provider prepends, up to and
 * including the first line that is not one.
 *
 * The check on the last line is for a preamble that was cut off mid-word by a token ceiling, which
 * still has to go — otherwise the answer opens with `safety: sa`.
 *
 * The loop keeps both of its jumps on purpose: the blank line only a removed preamble leaves behind,
 * and the first line that is none. They are two endings of one forward scan, so folding them away
 * would mean deriving the same stopping point twice.
 */
@Suppress("LoopWithTooManyJumpStatements")
private fun stripLeadingInternalMetadata(source: String): String {
    val lines = source.split("\n")
    var firstVisible = 0
    var removedMetadata = false
    while (firstVisible < lines.size) {
        val line = lines[firstVisible].trim()
        if (line.isEmpty() && removedMetadata) {
            firstVisible++
            continue
        }
        val incompleteLastLine = firstVisible == lines.size - 1 && !source.endsWith("\n")
        val isMetadata = REASONING_METADATA_LINE.containsMatchIn(line) ||
            (incompleteLastLine && looksLikeReasoningMetadataPrefix(line))
        if (!isMetadata) break
        removedMetadata = true
        firstVisible++
    }
    return if (removedMetadata) lines.drop(firstVisible).joinToString("\n") else source
}

/**
 * `_looksLikeReasoningMetadataPrefix`: a truncated preamble, judged by the completed preambles it
 * could still become.
 */
private fun looksLikeReasoningMetadataPrefix(source: String): Boolean {
    if (source.isEmpty()) return false
    val normalized = source
        .replace(LEADING_METADATA_MARKS, "")
        .replace("*", "")
        .lowercase()
    if (normalized.length < 4) return false
    return COMPLETED_METADATA_LINES.any { it.startsWith(normalized) }
}

/** `^[，。；：！？、,.!?;:]` — an addition opening with punctuation continues the sentence before it. */
private val LEADING_PUNCTUATION = Regex("^[，。；：！？、,.!?;:]")

/**
 * `\[\[(?:E(?:N(?:D)?)?|M(?:O(?:R(?:E)?)?)?)?\z` — a continuation marker the ceiling cut short.
 *
 * The three patterns that end at the end of the answer — this one, [BARE_CITATION] and
 * [SOURCES_BLOCK] — end at `\z` rather than at Java's `$`. Dart wrote `$`, which matches only the
 * very end; Java's `$` also matches on the line before a final newline, and a citation list or a
 * truncated marker is followed by nothing at all.
 */
private val INCOMPLETE_CONTINUATION_MARKER = Regex("\\[\\[(?:E(?:N(?:D)?)?|M(?:O(?:R(?:E)?)?)?)?\\z")

/**
 * `\s*<U+E200>cite<U+E202>[^<U+E201>]*<U+E201>` — OpenRouter's own citation markers. They sit in
 * the private use area, so they are written as escapes rather than pasted into the source.
 */
private val OPENROUTER_CITATION = Regex("\\s*\uE200cite\uE202[^\uE201]*\uE201")

/** `\s*【\s*\d+(?:\s*[†,]\s*[^】]+)?】` — the fullwidth citation form models write. */
private val FULLWIDTH_CITATION = Regex("\\s*【\\s*\\d+(?:\\s*[†,]\\s*[^】]+)?】")

/** `\s*\[(?:\d+|source)\](?=[\s.,，。]|\z)` — a bare marker such as `[1]` or `[source]`. */
private val BARE_CITATION = Regex("\\s*\\[(?:\\d+|source)\\](?=[\\s.,，。]|\\z)", RegexOption.IGNORE_CASE)

/** A trailing `來源:` / `Sources:` list, which only ever ends a model answer. */
private val SOURCES_BLOCK = Regex(
    "\\n{0,2}(?:\\*{0,2})(?:來源|資料來源|sources?|references?)(?:\\*{0,2})\\s*:?\\s*\\n" +
        "(?:\\s*[-*]\\s+[^\n]+\\n?)+\\s*\\z",
    RegexOption.IGNORE_CASE,
)

/** The lead-in marks a metadata line may start with. */
private val LEADING_METADATA_MARKS = Regex("^[-*#>\\s]+")

/**
 * `_reasoningMetadataLine`: a whole safety classification line.
 *
 * The alternation and every `\s+` are the ones the Flutter build compiled, so a provider that
 * changes its wording fails the same way here as it did there.
 */
private val REASONING_METADATA_LINE = Regex(
    "^(?:[-*#>\\s]*)(?:\\*{0,2})?" +
        "(?:user\\s+safety|assistant\\s+safety|content\\s+safety|" +
        "safety(?:\\s+classification)?|classification)" +
        "(?:\\*{0,2})?\\s*:\\s*(?:\\*{0,2})?" +
        "(?:safe|unsafe|allowed|blocked|benign|none|low|medium|high)" +
        "(?:\\*{0,2})?[.!]?$",
    RegexOption.IGNORE_CASE,
)

/** The completed preambles a truncated one is compared against, lowest-case as Dart compared them. */
private val COMPLETED_METADATA_LINES = listOf(
    "user safety: safe",
    "user safety: unsafe",
    "assistant safety: safe",
    "assistant safety: unsafe",
    "content safety: safe",
    "content safety: unsafe",
    "safety: safe",
    "safety: unsafe",
    "safety classification: safe",
    "safety classification: unsafe",
    "classification: safe",
    "classification: unsafe",
)

/** `8`, the shortest tail of a continuation that may be treated as an overlap. */
private const val MIN_OVERLAP = 8

/** `32`, below which a restart threshold is scaled down from [RESTART_PREFIX_LENGTH]. */
private const val SHORT_ANSWER_LENGTH = 32

/** `14`, the common prefix that reads as a restart once the answer is not short. */
private const val RESTART_PREFIX_LENGTH = 14

/** `6`, the floor of the scaled restart threshold. */
private const val MIN_RESTART_PREFIX = 6

/** `12`, the ceiling of the scaled restart threshold. */
private const val MAX_RESTART_PREFIX = 12

/** `4`, the shortest opening line that can count as repeated. */
private const val REPEATED_OPENING_MINIMUM = 4
