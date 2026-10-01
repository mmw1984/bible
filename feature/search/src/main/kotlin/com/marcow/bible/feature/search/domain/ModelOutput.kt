package com.marcow.bible.feature.search.domain

/**
 * The model output clean-up the search overview goes through, moved across from
 * `_cleanSearchOverview` and the four helpers it calls in `legacy/flutter/lib/ai_service.dart`.
 *
 * The overview prompt asks the model for prose and nothing else, but models still emit the
 * continuations, citation markers and safety preambles the chat path was already stripping — and
 * the sheet has nowhere to hide them, because the overview *is* the whole panel. The patterns below
 * are the ones the Flutter build used, character for character; a new pattern here is a visible
 * change in what the user reads, which is why none is added.
 *
 * Phase 4's chat path runs the same helpers on streamed answers, and this file is where the shared
 * copies live.
 */
internal fun cleanSearchOverview(source: String): String {
    var cleaned = cleanModelOutput(source)
    for (tag in listOf("think", "thinking", "analysis", "reasoning")) {
        cleaned = cleaned.replace(Regex("<$tag>[\\s\\S]*?</$tag>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replaceFirst(Regex("^\\s*<$tag>[\\s\\S]*\$", RegexOption.IGNORE_CASE), "")
    }
    return stripLeadingInternalMetadata(cleaned).trim()
}

/** `_cleanModelOutput`: drop the continuation markers, then the citations. */
private fun cleanModelOutput(source: String): String {
    val withoutMarkers = source
        .replace("[[END]]", "")
        .replace("[[MORE]]", "")
        .replaceFirst(INCOMPLETE_CONTINUATION_MARKER, "")
    return stripWebCitations(stripLeadingInternalMetadata(withoutMarkers)).trim()
}

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
 * still has to go — otherwise the overview opens with `safety: sa`.
 */
@Suppress("LoopWithTooManyJumpStatements")
private fun stripLeadingInternalMetadata(source: String): String {
    val lines = source.split("\n")
    var firstVisible = 0
    var removedMetadata = false
    while (firstVisible < lines.size) {
        val line = lines[firstVisible].trim()
        // Both exits are one step, so this reads as the scan it is: skip the blank line the preamble
        // leaves behind once one has been removed, and stop at the first line that is not metadata.
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

/** `\[\[(?:E(?:N(?:D)?)?|M(?:O(?:R(?:E)?)?)?)?$` — a continuation marker the ceiling cut short. */
private val INCOMPLETE_CONTINUATION_MARKER = Regex("\\[\\[(?:E(?:N(?:D)?)?|M(?:O(?:R(?:E)?)?)?)?\$")

/**
 * `\s*<U+E200>cite<U+E202>[^<U+E201>]*<U+E201>` — OpenRouter's own citation markers. They sit in
 * the private use area, so they are written as escapes rather than pasted into the source.
 */
private val OPENROUTER_CITATION = Regex("\\s*\uE200cite\uE202[^\uE201]*\uE201")

/** `\s*【\s*\d+(?:\s*[†,]\s*[^】]+)?】` — the fullwidth citation form models write. */
private val FULLWIDTH_CITATION = Regex("\\s*【\\s*\\d+(?:\\s*[†,]\\s*[^】]+)?】")

/** `\s*\[(?:\d+|source)\](?=[\s.,，。]|$)` — a bare marker such as `[1]` or `[source]`. */
private val BARE_CITATION = Regex("\\s*\\[(?:\\d+|source)\\](?=[\\s.,，。]|\$)", RegexOption.IGNORE_CASE)

/** A trailing `來源:` / `Sources:` list, which only ever ends a model answer. */
private val SOURCES_BLOCK = Regex(
    "\\n{0,2}(?:\\*{0,2})(?:來源|資料來源|sources?|references?)(?:\\*{0,2})\\s*:?\\s*\\n" +
        "(?:\\s*[-*]\\s+[^\\n]+\\n?)+\\s*\$",
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
        "(?:\\*{0,2})?[.!]?\$",
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
