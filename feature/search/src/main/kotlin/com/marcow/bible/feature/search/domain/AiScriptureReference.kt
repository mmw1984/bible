package com.marcow.bible.feature.search.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * One scripture reference the model proposed, mirroring `AiScriptureReference` in
 * `legacy/flutter/lib/ai_service.dart`.
 *
 * The model never returns verse text — the prompt says so and the schema has no field for it — so a
 * reference is only a pointer. [bookId] is canonicalised to upper case by [fromJson] because
 * `_searchReferences` compares it against `bibleBooks`, whose ids are already upper case; a model
 * answering `"jhn"` therefore still resolves, exactly as it did in Dart.
 *
 * Public rather than `internal` because [SearchReferencesUseCase] hands these out and [AiSearchUseCase]
 * takes that use case as a constructor parameter: Kotlin will not let a public signature name an
 * internal type, so the chain from the sheet's one public entry point down to a parsed reference has
 * to be public all the way. The rest of the domain stays module-visible — see [SearchReferences].
 */
data class AiScriptureReference(
    val bookId: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val reason: String,
) {
    companion object {
        /** `AiScriptureReference.fromJson`, field for field. */
        fun fromJson(json: Map<String, JsonElement>): AiScriptureReference = AiScriptureReference(
            bookId = json.stringOrEmpty("bookId").uppercase(),
            chapter = json.numberOrDefault("chapter", DEFAULT_NUMBER),
            verseStart = json.numberOrDefault("verseStart", DEFAULT_NUMBER),
            verseEnd = json.numberOrDefault("verseEnd", json.numberOrDefault("verseStart", DEFAULT_NUMBER)),
            reason = json.stringOrEmpty("reason"),
        )

        /** The `int fallback = 1` default of `fromJson`'s local `number`. */
        private const val DEFAULT_NUMBER = 1
    }
}

/**
 * `(json['bookId'] as String? ?? '')`.
 *
 * Dart threw a `TypeError` on a value that was neither a string nor null, and that propagated out of
 * `_searchReferences` to fail the whole request. A missing key and an explicit JSON null both take
 * the default here; anything else of the wrong type still fails the request, because silently
 * reading `"3"` as a chapter number would let a malformed answer resolve to a real verse and show
 * up in the results list as if the app had asked for it.
 */
private fun Map<String, JsonElement>.stringOrEmpty(key: String): String = when (val raw = this[key]) {
    null, is JsonNull -> ""
    is JsonPrimitive ->
        if (raw.isString) raw.content else throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)

    else -> throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
}

/**
 * `(json[key] as num?)?.toInt() ?? fallback`.
 *
 * `toInt()` truncates toward zero in Dart and in Kotlin alike, so a model answering `3.7` yields
 * the same chapter `3` on both sides.
 */
private fun Map<String, JsonElement>.numberOrDefault(key: String, fallback: Int): Int {
    val raw = this[key]
    if (raw == null || raw is JsonNull) return fallback
    val primitive = raw as? JsonPrimitive ?: throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
    if (primitive.isString) throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
    return primitive.content.toDoubleOrNull()?.toInt()
        ?: throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
}
