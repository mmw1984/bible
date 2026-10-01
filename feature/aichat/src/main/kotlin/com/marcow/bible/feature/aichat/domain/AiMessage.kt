package com.marcow.bible.feature.aichat.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `AiMessage` of `legacy/flutter/lib/ai_service.dart:91`, the record a chat turn is kept as and the
 * only thing the transcript file and the message list hold.
 *
 * The roles and kinds are enums rather than the strings Dart used, because the three places that read
 * them all *switch* on the value: the prompt renders `user:` / `assistant:` prefixes, the memory entry
 * heading is decided by the kind, and the JSON round-trip has to write back exactly what it read. A
 * string there is a place where a typo becomes a silently different prompt. [UNKNOWN] on the role and
 * the kind's [fromStorage] default are what keep a transcript written by a future build readable.
 */
data class AiMessage(
    val role: AiMessageRole = AiMessageRole.ASSISTANT,
    val text: String = "",
    val scripture: String? = null,
    val kind: AiMessageKind = AiMessageKind.CHAT,
    /** The thinking channel, cleaned on the way in: `_fromJson` ran it through the display sanitizer. */
    val reasoning: String? = null,
    val webSearch: Boolean = false,
    /** `[[MORE]]`, a stop, or a failed continuation — what 「回覆已停止或尚未完整」 is shown for. */
    val incomplete: Boolean = false,
) {
    /**
     * `toJson`: the transcript line, and the record the message list is restored from.
     *
     * The two nulls are Dart's `?scripture` / `?reasoning`, which omit the key rather than write a
     * `null`: an empty reasoning string is a state that never happened, and a transcript full of
     * `"reasoning": null` would be a different file from the one the Flutter build left behind.
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("role", role.storageValue)
        put("text", text)
        put("kind", kind.storageValue)
        scripture?.let { put("scripture", it) }
        reasoning?.let { put("reasoning", it) }
        put("webSearch", webSearch)
        put("incomplete", incomplete)
    }
}

/**
 * Who said it, mirroring the two strings `_chatPrompt` writes as the recent block's prefixes.
 *
 * [UNKNOWN] is what Dart's `json['role'] as String? ?? 'assistant'` produced for anything it did not
 * recognise, and it is kept as its own case rather than folded into [ASSISTANT] so a transcript is
 * never quietly rewritten to say the wrong speaker did.
 */
enum class AiMessageRole(val storageValue: String, val promptPrefix: String) {
    /** The reader, whose turns the recent block prefixes as `user:`. */
    USER("user", "user"),

    /** The model, whose turns the recent block prefixes as `assistant:`. */
    ASSISTANT("assistant", "assistant"),

    /** A role a future build wrote, rendered with its own text rather than one of the two above. */
    UNKNOWN("", ""),
    ;

    companion object {
        /** Reads `role`, defaulting to `assistant` exactly as `AiMessage.fromJson` did. */
        fun fromStorage(value: String?): AiMessageRole =
            entries.firstOrNull { it.storageValue == value } ?: ASSISTANT
    }
}

/**
 * What a turn is for, the `kind` of `AiMessage` and the three values `recordMessage` switched on.
 *
 * [memoryHeading] is the `##` section of `memory.md` the turn is filed under, and it is a property of
 * the kind rather than a `when` in the store because the three are a fixed pairing: a verse
 * explanation belongs under 「已解釋的經文」, a search under 「搜尋紀錄與結論」, and a plain chat turn
 * under 「重要事件與對話」. A fourth kind that appears later then has to say which section it means.
 */
enum class AiMessageKind(val storageValue: String, val memoryHeading: String) {
    /** `chat`, the ordinary conversation. */
    CHAT("chat", "Important events and conversation"),

    /** `explanation`, a verse the reader asked about. */
    EXPLANATION("explanation", "Explained scripture"),

    /** `search`, a reference lookup whose conclusions are worth keeping. */
    SEARCH("search", "Search history and conclusions"),
    ;

    companion object {
        /** Reads `kind`, defaulting to `chat` as `AiMessage.fromJson` did. */
        fun fromStorage(value: String?): AiMessageKind =
            entries.firstOrNull { it.storageValue == value } ?: CHAT
    }
}

/**
 * `AiMessage.fromJson`: one transcript line read back, with a line that is not an object rejected by
 * the caller rather than here.
 *
 * Every field is defaulted rather than required, which is Dart's `?? ` chain and not a shortcut: a
 * transcript line written by a build that predates a field, or truncated at a token ceiling, has to
 * come back as a message rather than throw away the history around it. [reasoning] is the one field
 * with a rule — Dart sanitized it on the way in, so that a transcript restored from disk never shows
 * a `safety: safe` line the model emitted and the prompt had asked it to leave out.
 */
fun aiMessageFromJson(json: JsonObject): AiMessage {
    fun string(name: String): String? = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val reasoning = string("reasoning")?.takeIf { it.isNotBlank() }?.let(::sanitizeReasoningForDisplay)
    return AiMessage(
        role = AiMessageRole.fromStorage(string("role")),
        text = string("text").orEmpty(),
        scripture = string("scripture"),
        kind = AiMessageKind.fromStorage(string("kind")),
        reasoning = reasoning,
        webSearch = json.boolean("webSearch"),
        incomplete = json.boolean("incomplete"),
    )
}

/**
 * `json['webSearch'] as bool? ?? false`: absent is false, and a non-boolean is false too.
 *
 * Dart's cast would have thrown on a string here; the store reads lines a user may have edited, and
 * one malformed boolean is not worth losing the message it belongs to.
 */
private fun JsonObject.boolean(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true
