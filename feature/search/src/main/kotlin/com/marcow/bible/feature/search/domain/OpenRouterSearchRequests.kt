package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.network.openrouter.ChatCompletionRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The two request bodies the search sends, moved across from `_send` in
 * `legacy/flutter/lib/openrouter_service.dart`.
 *
 * `_send` derived three things from the prompt text: `structuredReferences` (the prompt carries the
 * `BIBLE_SEARCH_REFERENCES_JSON` marker), `shortSearchOverview` (the `BIBLE_SEARCH_OVERVIEW`
 * marker) and, failing both, a `structuredSearch` guess. `NATIVE_PLAN.md` §4.3 calls that sniffing
 * a smell and asks for an explicit choice instead, so each builder below states which of the two it
 * is. Each still produces the bytes the Flutter build produced, because the model's behaviour
 * depends on them.
 *
 * Two consequences worth keeping straight:
 *
 *  - The token ceilings are the search ones, not a caller-supplied `maxTokens`: 1200 for the
 *    overview (`shortSearchOverview`), 2400 for the references (`structuredSearch`). Neither is
 *    null in Dart, so `?maxTokens` always wrote the key.
 *  - `reasoning` is suppressed on both paths, differently: `{enabled: false, exclude: true}` for
 *    the overview and `{max_tokens: 32, exclude: true}` for the references.
 *
 * There is no `tools` / `tool_choice` / `max_tool_calls` block. Flutter reached `_send` with the
 * default `OpenRouterRequestOptions`, whose `webSearch` is `disabled`, so the block never went on
 * the wire for a search. Web search belongs to the chat path, which is Phase 4.
 */
internal object OpenRouterSearchRequests {
    /** The `shortSearchOverview` branch of `_send`. */
    fun overview(prompt: String, model: String): ChatCompletionRequest = chatCompletion(
        prompt = prompt,
        model = model,
        maxTokens = OVERVIEW_MAX_TOKENS,
        // `if (shortSearchOverview) 'reasoning': {'enabled': false, 'exclude': true}`
        reasoning = buildJsonObject {
            put("enabled", false)
            put("exclude", true)
        },
    )

    /** The `structuredSearch` branch of `_send`. */
    fun references(prompt: String, model: String): ChatCompletionRequest = chatCompletion(
        prompt = prompt,
        model = model,
        maxTokens = REFERENCES_MAX_TOKENS,
        // `if (structuredSearch) 'reasoning': {'max_tokens': 32, 'exclude': true}`
        reasoning = buildJsonObject {
            put("max_tokens", REASONING_MAX_TOKENS)
            put("exclude", true)
        },
        responseFormat = jsonSchemaFormat(),
    )

    /** `max_tokens` of the `shortSearchOverview` branch of `_send`. */
    const val OVERVIEW_MAX_TOKENS = 1200

    /** `max_tokens` of the `structuredSearch` branch of `_send`. */
    const val REFERENCES_MAX_TOKENS = 2400

    /** `reasoning.max_tokens` of the `structuredSearch` branch of `_send`. */
    const val REASONING_MAX_TOKENS = 32

    /** The `temperature` of `_send`, the one value it fixed for every request. */
    private const val TEMPERATURE = 0.2

    /**
     * The body every search request shares: one user message, a fixed temperature, no stream.
     *
     * `stream` is `false` because the Flutter search collected its answer through `generate()`,
     * which is `generateStream(…).join()` — it asked for a stream and concatenated the deltas. The
     * sheet publishes one overview and one reference set rather than tokens, so the single completed
     * answer is what it wants; Phase 4's `SseChatSource` is where the streamed form comes back for
     * the chat path.
     */
    private fun chatCompletion(
        prompt: String,
        model: String,
        maxTokens: Int,
        reasoning: JsonObject,
        responseFormat: JsonObject? = null,
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = model,
        body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    },
                )
            }
            put("temperature", TEMPERATURE)
            put("max_tokens", maxTokens)
            put("stream", false)
            put("reasoning", reasoning)
            if (responseFormat != null) put("response_format", responseFormat)
        },
    )

    /**
     * `response_format` of the `structuredSearch` branch, including the schema itself.
     *
     * `strict: true` means the provider rejects anything that does not fit, so this schema is the
     * contract the references are parsed against — hence `additionalProperties: false` and an
     * explicit `required` at every level.
     */
    private fun jsonSchemaFormat(includeOverview: Boolean = false): JsonObject = buildJsonObject {
        put("type", "json_schema")
        putJsonObject("json_schema") {
            put("name", "bible_search")
            put("strict", true)
            put("schema", bibleSearchSchema(includeOverview))
        }
    }

    /**
     * The `bible_search` schema object, key for key.
     *
     * [includeOverview] is `false` for the search references prompt, which is why `overview` is
     * absent from both `required` and `properties`. Dart wrote the same two conditionals for the
     * combined shape the chat path uses (`if (!structuredReferences) 'overview'`), so the parameter
     * stays rather than the branch being deleted.
     */
    private fun bibleSearchSchema(includeOverview: Boolean): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            if (!includeOverview) add("overview")
            add("scriptures")
            add("suggestedQuestions")
        }
        putJsonObject("properties") {
            if (!includeOverview) putJsonObject("overview") { put("type", "string") }
            putJsonObject("scriptures") {
                put("type", "array")
                put("maxItems", 16)
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonArray("required") {
                        add("bookId")
                        add("chapter")
                        add("verseStart")
                        add("verseEnd")
                        add("reason")
                    }
                    putJsonObject("properties") {
                        putJsonObject("bookId") { put("type", "string") }
                        putJsonObject("chapter") { put("type", "integer") }
                        putJsonObject("verseStart") { put("type", "integer") }
                        putJsonObject("verseEnd") { put("type", "integer") }
                        putJsonObject("reason") { put("type", "string") }
                    }
                }
            }
            putJsonObject("suggestedQuestions") {
                put("type", "array")
                put("maxItems", 3)
                putJsonArray("items") { add(buildJsonObject { put("type", "string") }) }
            }
        }
    }
}
