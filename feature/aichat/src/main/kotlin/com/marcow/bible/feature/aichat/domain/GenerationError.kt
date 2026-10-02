package com.marcow.bible.feature.aichat.domain

/**
 * `_generationErrorMessage` of `legacy/flutter/lib/ai_service.dart:1135`, returned whole rather than
 * as a half the screen has to finish.
 *
 * Flutter returned one of two sentences: the failure's own text behind 「未能完成回覆：」, or the fixed
 * 「未能完成回覆，內容已保留，請稍後再試。」. Which one is the whole behaviour, and it is decided
 * by three rules in order — strip the `Type: ` prefix a Dart `toString` adds, hide a bearer token if
 * the message happens to contain one, and refuse anything long enough to be a stack trace or a
 * response body rather than a reason. A reader is shown a failure of their question, not the transport
 * that carried it.
 *
 * The result is never null: the generic sentence *is* the answer for a message the reader must not
 * be shown, so there is one thing for the screen to render and no length rule of its own to get
 * wrong. Both sentences are the Flutter build's, unlocalised there and unlocalised here — they are
 * the wording the translation files already carry.
 *
 * Dart's first branch, `error is PlatformException`, has no counterpart to port: Flutter's method
 * channel had a `code` to show next to a `message`, and the network port here raises
 * `OpenRouterException` and `AiAnswerException`, whose messages are already the reader-facing text.
 * Those therefore fall to the general branch, which prefixes them — the same way a `StateError`
 * did.
 */
internal fun generationErrorMessage(error: Throwable): String {
    val raw = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName.orEmpty()
    val withoutPrefix = raw.replaceFirst(LEADING_ERROR_TYPE, "")
    val hidden = withoutPrefix.replace(BEARER_TOKEN, "Bearer [hidden]").trim()
    if (hidden.isEmpty() || hidden.length > MAX_DETAIL_LENGTH) return GENERIC
    return "$DETAILED$hidden"
}

/** `RegExp(r'^\w+(?:Error)?:\s*')`: the class name Dart's `toString()` put in front of the message. */
private val LEADING_ERROR_TYPE = Regex("^\\w+(?:Error)?:\\s*")

/** `Bearer\s+\S+`, case-insensitively: a key must never reach the error panel. */
private val BEARER_TOKEN = Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE)

/**
 * `280`, the length past which the message is a body or a stack trace rather than a reason.
 *
 * Flutter compared against this after hiding the token, so a long header cannot smuggle a key past the
 * check by being replaced — the check is on what would be shown.
 */
private const val MAX_DETAIL_LENGTH = 280

/** `'未能完成回覆：'`, the prefix in front of a detail the reader can act on. */
private const val DETAILED = "未能完成回覆："

/** `'未能完成回覆，內容已保留，請稍後再試。'`, said instead of a detail that would not be safe. */
private const val GENERIC = "未能完成回覆，內容已保留，請稍後再試。"
