package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.database.BibleRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `get_scripture` pseudo-tool of `NATIVE_PLAN.md` §4.4, moved across from `_runScriptureTool` and
 * `_jsonObject` in `legacy/flutter/lib/ai_service.dart`.
 *
 * It is not a real tool: nothing is sent back to the model as a tool result, the app answers the
 * request itself out of its own Bible database and appends the text to the next prompt. That is why
 * the whole loop is bounded — [MAX_TOOL_ROUNDS] requests, then one forced answer — rather than
 * negotiated, and why [toolFollowUpPrompt] and [finalFollowUpPrompt] carry English sentences the
 * model was tuned on.
 *
 * Every error is returned as a `Tool error: …` line rather than thrown. The model asked for a
 * passage and the app could not supply one, so the model is the one that has to hear about it and
 * decide what to say; throwing would fail the whole answer instead.
 */
@Singleton
internal class ScriptureToolRunner @Inject constructor(
    private val bibleRepository: BibleRepository,
) {
    /**
     * The verses of the requested range, or the `Tool error: …` line explaining why there are none.
     *
     * The range is capped at 100 verses because that is where Dart capped it: the result is appended
     * to the next prompt verbatim, and a model that asked for a whole chapter would otherwise push
     * the conversation past what the request carries.
     */
    suspend fun run(request: ScriptureToolRequest): String {
        val book = bibleRepository.book(request.bookId)
            ?: return "Tool error: unknown bookId ${request.bookId}"
        val chapter = request.chapter
        if (chapter < 1 || chapter > book.chapters) {
            return "Tool error: ${book.id} has no chapter $chapter"
        }
        val start = request.verseStart
        // Dart's `(request['verseEnd'] as num?)?.toInt() ?? start` defaulted to `verseStart`, not to 1.
        val end = request.verseEnd ?: start
        if (start < 1 || end < start || end - start > MAX_VERSE_SPAN) {
            return "Tool error: invalid verse range $start-$end"
        }
        return bibleRepository.chapter(book.id, chapter)
            .filter { it.number in start..end }
            .joinToString(separator = "\n\n") {
                "${book.nameZh} $chapter:${it.number}\n中文：${it.zh}\nEnglish: ${it.en}"
            }
    }
}

/**
 * One `get_scripture` request, the `{"tool":"get_scripture",…}` shape of the prompt's instruction.
 *
 * A field the model left out keeps its Dart reading rather than becoming an error: [chapter] is 1 and
 * [verseStart] is 1, and [verseEnd] stays `null` until [ScriptureToolRunner.run] turns it into
 * [verseStart] — `(request['verseEnd'] as num?)?.toInt() ?? start`, one verse rather than a range.
 *
 * The `language` key the prompt's example carries is not read: the app answers bilingually whatever
 * it is asked for, which is what `_runScriptureTool` did by always formatting both.
 */
internal data class ScriptureToolRequest(
    val bookId: String,
    val chapter: Int = 1,
    val verseStart: Int = 1,
    val verseEnd: Int? = null,
)

/**
 * `_jsonObject(answer)?['tool'] == 'get_scripture'`, and nothing else.
 *
 * Two Dart behaviours are load-bearing here:
 *
 *  - The JSON is cut from the answer's *first* `{` to its *last* `}`, so a model that wrapped its
 *    request in prose still resolves. Anything outside the braces is discarded, which is also why a
 *    plain sentence comes back as no request at all rather than as a parse failure.
 *  - A frame that is not an object, or does not decode, is skipped rather than thrown, so a malformed
 *    answer ends the tool loop and the model is asked to answer from what it already has.
 *
 * `bookId` is upper-cased because that is what the canon ids are, and the prompt's own example sends
 * `"JHN"` — a model answering `"jhn"` still resolves, exactly as in Dart.
 */
internal fun scriptureToolRequest(answer: String): ScriptureToolRequest? {
    val start = answer.indexOf('{')
    val end = answer.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val element = runCatching { Json.parseToJsonElement(answer.substring(start, end + 1)) }.getOrNull()
    val payload = element as? JsonObject ?: return null
    if (payload["tool"]?.contentOrNull != TOOL_NAME) return null
    return ScriptureToolRequest(
        bookId = payload.stringOrEmpty("bookId").uppercase(),
        chapter = payload.intOrDefault("chapter", 1),
        verseStart = payload.intOrDefault("verseStart", 1),
        verseEnd = payload["verseEnd"]?.intOrNull,
    )
}

/**
 * The prompt of the next round, from the `_streamModelAnswer` call inside the loop.
 *
 * Every result so far is resent rather than only the newest one, so the model can still quote a
 * passage it asked for two rounds ago. Copied word for word, including the space after the JSON
 * request: it is part of the text the model was tuned against.
 */
internal fun toolFollowUpPrompt(prompt: String, toolResults: List<String>): String =
    "$prompt\n\nThe app executed get_scripture. Use this authoritative result " +
        "to answer the user. If another passage is essential, you may issue " +
        "one more get_scripture JSON request.\n\n${toolResults.joinToString(separator = "\n\n")}"

/**
 * The prompt of the forced last round, once [MAX_TOOL_ROUNDS] requests have been spent.
 *
 * The empty case has its own sentence, also copied word for word: a model that kept asking is told
 * the tool is unavailable, which is a different instruction from being told to stop asking. Both
 * answer from the chapter already in the prompt.
 */
internal fun finalFollowUpPrompt(prompt: String, toolResults: List<String>): String {
    val available = toolResults.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n\n")
        ?: "The scripture tool is unavailable. Answer only from the " +
        "authoritative chapter reference already supplied."
    return "$prompt\n\nUse the authoritative tool results below and answer now. " +
        "Do not request another tool.\n\n$available"
}

/** `3`, the bound `for (var toolCall = 0; toolCall < 3 …)` put on the loop. */
internal const val MAX_TOOL_ROUNDS = 3

/** `100`, the span beyond which `_runScriptureTool` refused a range. */
private const val MAX_VERSE_SPAN = 100

/** `'get_scripture'`, the only tool name this build answers. */
private const val TOOL_NAME = "get_scripture"

/**
 * `request['bookId'] as String? ?? ''`, then upper-cased by the caller.
 *
 * Read through [contentOrNull] rather than `jsonPrimitive`, which throws when the value is an object
 * or an array. Dart's `as String?` simply failed the cast and carried on with `''`; a model that sent
 * `{"bookId":{"a":1}}` gets an unknown book here rather than a crash.
 */
private fun JsonObject.stringOrEmpty(key: String): String = this[key]?.contentOrNull?.toString() ?: ""

/**
 * `(request[key] as num?)?.toInt() ?? fallback`.
 *
 * A JSON string where a number belongs is not a number, so `intOrNull` returning null falls through
 * to the default — the same reading the `as num?` cast produced.
 */
private fun JsonObject.intOrDefault(key: String, fallback: Int): Int = this[key]?.intOrNull ?: fallback
