@file:Suppress("MatchingDeclarationName")

package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.model.BibleBook
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the references prompt answered, mirroring `AiSearchReferences` in
 * `legacy/flutter/lib/ai_service.dart`.
 *
 * Only the [scriptures] reach the sheet; [suggestedQuestions] came back in the same payload because
 * the schema asks for it, and dropping it here would be the one place the two builds disagree about
 * what the model was asked for.
 *
 * Public because it is [SearchReferencesUseCase]'s return type, and that use case is a constructor
 * parameter of the public `AiSearchUseCase` — see [AiScriptureReference] for why the chain is public.
 */
data class AiSearchReferences(val scriptures: List<AiScriptureReference>, val suggestedQuestions: List<String>)

/**
 * The parse half of `_searchReferences`: the model's text in, validated references out.
 *
 * Everything that can be dropped is dropped rather than reported, because the prompt asks for up to
 * 16 references and a model that invents `PTT 5:99` should lose that one row and not the other
 * fifteen. Only the shape of the payload itself — not an object, or a missing `scriptures` /
 * `suggestedQuestions` list — fails the whole request, because that means the model ignored the
 * schema and nothing in it can be trusted.
 *
 * [books] is the canon, which is what the two filters check a reference against: an unknown book id,
 * or a chapter past the end of the book, is dropped here rather than at resolution time. That split
 * is the Flutter build's and it matters, because the filters below need `chapters` and resolution
 * only has the id.
 */
@Suppress("ThrowsCount")
internal fun parseSearchReferences(raw: String, books: List<BibleBook>): AiSearchReferences {
    val json = jsonObjectOrNull(raw) ?: throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
    val scriptures = json["scriptures"] as? JsonArray
        ?: throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)
    val suggestedQuestions = json["suggestedQuestions"] as? JsonArray
        ?: throw AiSearchFormatException(INVALID_REFERENCES_JSON_MESSAGE)

    val canon = books.associateBy { it.id }
    val references = scriptures
        // `whereType<Map>()`: an entry that is not an object is skipped rather than fatal.
        .mapNotNull { it as? JsonObject }
        .map { AiScriptureReference.fromJson(it) }
        // The two `where` clauses of `_searchReferences`, in order.
        .filter { canon.containsKey(it.bookId) }
        .filter { reference ->
            val chapters = canon.getValue(reference.bookId).chapters
            reference.chapter in 1..chapters &&
                reference.verseStart >= 1 &&
                reference.verseEnd >= reference.verseStart
        }

    return AiSearchReferences(
        scriptures = references,
        // `whereType<String>().take(3)`: the schema caps this at three, and `take` is what kept a
        // model that ignored the cap from growing the transcript with its own follow-ups.
        suggestedQuestions = suggestedQuestions.mapNotNull { it.stringOrNull() }.take(SUGGESTED_QUESTION_LIMIT),
    )
}

/** `json['suggestedQuestions'] as List).whereType<String>()`. */
private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * `_jsonObject` in `legacy/flutter/lib/ai_service.dart`.
 *
 * The answer is sliced from the first `{` to the last `}` before it is decoded, which is what let the
 * Flutter build survive a model that wrapped its JSON in a sentence. The `end <= start` guard is
 * Dart's and is kept: an answer with a single `{}` is not an object with nothing in it.
 */
internal fun jsonObjectOrNull(source: String): JsonObject? {
    val start = source.indexOf('{')
    val end = source.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try {
        Json.parseToJsonElement(source.substring(start, end + 1)) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}

/** The `maxItems: 3` of the `suggestedQuestions` schema, applied a second time by `take(3)`. */
private const val SUGGESTED_QUESTION_LIMIT = 3
