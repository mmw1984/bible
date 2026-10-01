package com.marcow.bible.feature.search.domain

/**
 * The `FormatException`s the two search prompts can produce, kept as one type because the sheet
 * answers them identically.
 *
 * `legacy/flutter/lib/ai_service.dart` threw `FormatException('AI overview returned no text.')` and
 * `FormatException('AI scripture search returned invalid JSON.')`. Both were caught by the same
 * handler in `_searchAi`, which drew `overview_failed` / `references_failed` — so what distinguishes
 * them is the *message*, and the messages are reproduced verbatim below rather than folded into a
 * single text, because they are what a test asserts on and what a crash report would carry.
 *
 * Dart had no sealed class for this and Kotlin has no `FormatException` in the standard library the
 * same shape; this is the same idea under a name that says which of the two it came from.
 */
internal class AiSearchFormatException(message: String) : Exception(message)

/** `FormatException('AI overview returned no text.')`, thrown by `_searchOverview`. */
internal const val EMPTY_OVERVIEW_MESSAGE = "AI overview returned no text."

/** `FormatException('AI scripture search returned invalid JSON.')`, thrown by `_searchReferences`. */
internal const val INVALID_REFERENCES_JSON_MESSAGE = "AI scripture search returned invalid JSON."