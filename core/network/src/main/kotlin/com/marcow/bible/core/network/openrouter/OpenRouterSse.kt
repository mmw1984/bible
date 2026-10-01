package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The `data:` frames of `generateStream` in `legacy/flutter/lib/openrouter_service.dart:290` as a
 * [Flow] of [ChatEvent], replacing the `Stream<String>` plus its two `void Function` callbacks.
 *
 * The lines are the response body read one at a time and nothing else, so this is the whole of the
 * server-sent-events handling in a form that can be pinned without a socket — `mockwebserver` is not
 * on the version catalog and there is no network in CI, the same constraint
 * `OpenRouterChatClientTest` works under.
 *
 * The callbacks become events because the two of them are not the same kind of thing and the chat
 * draws them differently: `onReasoning` fed the foldable thinking block and `onFinishReason` decided
 * whether the answer was marked incomplete. `NATIVE_PLAN.md` §4.1 names `ContentDelta` and
 * `Finished`, so the finish reason is a [ChatEvent] of its own rather than a side channel that a
 * collector has to remember to register before it can see anything at all.
 *
 * What the frames mean is unchanged:
 *  - A line that does not start with `data:` is skipped, which is how the `event:` and `:` keep-alive
 *    lines of the format pass by, and `[DONE]` ends nothing — the body simply stops.
 *  - A frame that is not an object is skipped, and so is a `choices`-less one: several OpenRouter
 *    models send one to carry provider-side metadata.
 *  - A frame carrying `error` fails the whole answer with the provider's own message, including
 *    after content has already arrived. Dart threw there too, and the chat's promise that the text so
 *    far is kept is made by the caller holding the deltas it has already collected.
 *  - A stream that never carried content is `OpenRouter returned no response.`, the check Dart made
 *    once the body was exhausted.
 *
 * Reasoning is read from `reasoning`, `reasoning_content` and `analysis` and then from
 * `reasoning_details`, in Dart's order, because different models spell it differently and the Flutter
 * chat had grown all three keys. A model with none simply never emits [ChatEvent.ReasoningDelta].
 *
 * One deliberate difference: a `data:` frame that is not valid JSON fails the answer here as well as
 * it did in Dart, but it surfaces as a `SerializationException` rather than a `FormatException`. Both
 * are programming errors at the protocol level rather than provider errors, and neither is a message
 * the user is shown — the chat's error panel reports the failure, not its class.
 */
internal fun openRouterChatEvents(lines: Flow<String>): Flow<ChatEvent> = flow {
    var receivedContent = false
    lines.collect { line ->
        if (!line.startsWith(DATA_PREFIX)) return@collect
        val data = line.substring(DATA_PREFIX.length).trim()
        if (data.isEmpty() || data == DONE_SENTINEL) return@collect
        // `jsonDecode` in Dart, and its `is! Map` skip: a frame of some other shape is not an answer.
        val payload = Json.parseToJsonElement(data) as? JsonObject ?: return@collect

        payload["error"]?.let { throw OpenRouterException.RequestFailed(openRouterErrorMessage(it)) }

        val choice = (payload["choices"] as? JsonArray)?.firstOrNull() as? JsonObject

        // `onFinishReason` fired for any non-empty reason, known or not, and the unknown ones are
        // complete answers rather than failures.
        finishReasonOf(choice?.get("finish_reason"))?.let { emit(ChatEvent.Finished(it)) }

        val delta = choice?.get("delta") as? JsonObject
        if (delta != null) {
            val reasoning = reasoningText(delta)
            if (reasoning.isNotEmpty()) emit(ChatEvent.ReasoningDelta(reasoning))
        }

        val content = contentOf(delta?.get("content"))
        if (content.isNotEmpty()) {
            receivedContent = true
            emit(ChatEvent.ContentDelta(content))
        }
    }
    if (!receivedContent) throw OpenRouterException.EmptyResponse()
}

/** Dart's `finishReason is String && finishReason.isNotEmpty` guard, mapped to the enum. */
private fun finishReasonOf(raw: JsonElement?): FinishReason? =
    (raw as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotEmpty() }
        ?.let(FinishReason::of)

/**
 * `_reasoningText` in `legacy/flutter/lib/openrouter_service.dart:544`.
 *
 * The three single keys are tried in order and the first one that carries text wins, then
 * `reasoning_details`, and then the content parts whose type says they are thinking. That last step
 * is why `OpenRouterChatRequests` has to send the excluded-reasoning block to get a model's thinking
 * at all: with reasoning off there is no `reasoning` key to read, and the only thing left is a part
 * inside `content`.
 */
private fun reasoningText(delta: JsonObject): String {
    for (key in REASONING_KEYS) {
        val text = reasoningValue(delta[key])
        if (text.isNotEmpty()) return text
    }
    val details = reasoningValue(delta["reasoning_details"])
    if (details.isNotEmpty()) return details
    val content = delta["content"] as? JsonArray ?: return ""
    return content
        .filterIsInstance<JsonObject>()
        .filter { part ->
            val type = (part["type"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
            type.contains("reasoning") || type.contains("analysis")
        }
        .joinToString(separator = "") { reasoningValue(it) }
}

/**
 * `_reasoningValue`: a string as itself, a list flattened, and an object read through the keys that
 * have held the text.
 *
 * A number is not text, and returning its digits would put a token count where a sentence belongs.
 */
private fun reasoningValue(value: JsonElement?): String = when (value) {
    is JsonPrimitive -> if (value.isString) value.content else ""
    is JsonArray -> value.joinToString(separator = "") { reasoningValue(it) }
    is JsonObject -> REASONING_VALUE_KEYS
        .firstNotNullOfOrNull { key -> reasoningValue(value[key]).takeIf { it.isNotEmpty() } }
        .orEmpty()

    null -> ""
}

/** `'data:'`, the only prefix `generateStream` acted on. */
private const val DATA_PREFIX = "data:"

/** `'[DONE]'`, the frame the format ends with and which carries nothing to parse. */
private const val DONE_SENTINEL = "[DONE]"

/** The keys `_reasoningText` read the thinking from, in Dart's order. */
private val REASONING_KEYS = listOf("reasoning", "reasoning_content", "analysis")

/** The keys a reasoning *object* carries its text in, in Dart's order. */
private val REASONING_VALUE_KEYS = listOf("text", "summary", "content", "reasoning", "data")
