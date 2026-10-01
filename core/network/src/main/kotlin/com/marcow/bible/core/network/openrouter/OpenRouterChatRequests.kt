package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.PromptKind
import com.marcow.bible.core.network.ai.ReasoningBudget
import com.marcow.bible.core.network.ai.WebSearchMode
import com.marcow.bible.core.network.ai.kind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `AiRequest` to an OpenRouter request body, the pure function `NATIVE_PLAN.md` §4.3 asks for in
 * place of `_send`'s three booleans.
 *
 * `_send` (`legacy/flutter/lib/openrouter_service.dart:335`) derived `structuredReferences`,
 * `shortSearchOverview` and `structuredSearch` by looking *inside* the prompt, and §4.3 calls that a
 * smell: a reworded prompt could silently change the request shape. This mapper is handed
 * [PromptKind] through [AiRequest.kind] and never reads the prompt text, so that cannot happen. What
 * it writes is what Dart wrote, because the model's behaviour depends on these values and not on how
 * they were arrived at.
 *
 * The three branches Dart kept apart, kept apart here:
 *
 *  - `max_tokens` is 2400 for a structured search, 1200 for a short overview, and the caller's own
 *    ceiling otherwise. Dart's `'?maxTokens'` meant the key was written only when it had a value, so
 *    a chat sends no `max_tokens` at all unless one was asked for.
 *  - `reasoning` is decided by the kind first and the caller's flag second. A search overrides the
 *    flag entirely — `{max_tokens: 32, exclude: true}` for the references and
 *    `{enabled: false, exclude: true}` for the overview — and only a chat request consults
 *    [AiRequest.Chat.reasoning], because that is the `options.reasoning` of `OpenRouterRequestOptions`
 *    gating the `{max_tokens: 8192, exclude: false}` block.
 *  - the `tools` / `tool_choice` / `max_tool_calls` trio appears if and only if web search is not
 *    [WebSearchMode.Disabled], which is what left the Flutter search requests with no tool block.
 *
 * `feature/search`'s `OpenRouterSearchRequests` builds the same two search bodies and is deliberately
 * left alone: it belongs to the search module, which this pass does not touch. The two agree because
 * both read the §4.1 table, the search side is pinned by `OpenRouterSearchRequestsTest`, and this
 * side is pinned by `OpenRouterChatRequestsTest` — [bibleSearchResponseFormat] is the shared copy of
 * the schema so there is one shape to check, even while two modules hold it.
 *
 * `stream_options: {include_usage: true}` is in the §4.1 table but not in the Flutter build, so it is
 * not here. §4.1 marks it an optional enhancement, and adding it now would change the bytes on the
 * wire for no visible gain; an [AiUsage][com.marcow.bible.core.network.ai.AiUsage] is filled from
 * whichever answer happens to carry a `usage` object.
 */
internal object OpenRouterChatRequests {
    /**
     * The body for [request], asking for [stream] frames or for one completed answer.
     *
     * @param model the user's `openrouter_model`, already read through `OpenRouterModelId`.
     */
    fun build(
        request: AiRequest,
        model: String,
        options: AiRequestOptions = AiRequestOptions(),
        stream: Boolean = false,
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = model,
        body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", promptOf(request))
                    },
                )
            }
            put("temperature", TEMPERATURE)
            maxTokensOf(request.kind, options)?.let { put("max_tokens", it) }
            put("stream", stream)
            reasoningOf(request, options)?.let { put("reasoning", it) }
            if (options.webSearch != WebSearchMode.Disabled) {
                put("tools", webSearchTools(options))
                put("tool_choice", toolChoiceOf(options.webSearch))
                put("max_tool_calls", options.maxToolCalls ?: AiRequestOptions.DEFAULT_WEB_SEARCH_CEILING)
            }
            if (request.kind == PromptKind.SearchReferences) {
                put("response_format", bibleSearchResponseFormat())
            }
        },
    )

    /**
     * The prompt text a request carries.
     *
     * Only a chat request has one here. The two search kinds name themselves rather than holding
     * their prompt because `feature/search` already owns those texts in `SearchPrompts.kt`, and a
     * second copy of a tuned prompt is a second thing to keep verbatim. The sentinel is unreachable
     * through the search feature, which reaches `_send`'s shape through its own builder.
     */
    private fun promptOf(request: AiRequest): String = when (request) {
        is AiRequest.Chat -> request.prompt
        is AiRequest.SearchOverview, is AiRequest.SearchReferences -> MISSING_PROMPT_SENTINEL
    }

    /** `maxTokens = structuredSearch ? 2400 : shortSearchOverview ? 1200 : options.maxTokens`. */
    private fun maxTokensOf(kind: PromptKind, options: AiRequestOptions): Int? = when (kind) {
        PromptKind.SearchReferences -> REFERENCES_MAX_TOKENS
        PromptKind.SearchOverview -> OVERVIEW_MAX_TOKENS
        PromptKind.Chat -> options.maxTokens
    }

    /**
     * The `reasoning` object, or null when the key is left off entirely.
     *
     * Null is a real outcome rather than a fallback: Dart's
     * `if (!structuredSearch && !shortSearchOverview && options.reasoning)` meant a chat that turned
     * reasoning off sent no `reasoning` key at all, which is a different request from one that sends
     * a key disabling it. The two search kinds always send a key, because their overrides were not
     * conditional.
     *
     * A chat reaches null two ways, and both are Dart's `options.reasoning: false` arriving by a
     * different route. [AiRequest.Chat.reasoning] is the per-call-site flag — `_answerExistingMessage`
     * streams with it on and `_searchOverview` with it off. [AiRequestOptions.reasoning] is the
     * caller's own statement, and [ReasoningBudget.Off] is how `research()` says the same thing; a
     * chat that states it therefore also sends no key, rather than sending `{enabled: false}` where
     * Dart sent nothing. A chat that states no budget at all gets Dart's `reasoning: true` default,
     * which is the visible 8192-token block.
     */
    private fun reasoningOf(request: AiRequest, options: AiRequestOptions): JsonObject? = when (request) {
        is AiRequest.SearchReferences -> reasoningBlock(ReasoningBudget.Excluded())
        is AiRequest.SearchOverview -> reasoningBlock(ReasoningBudget.Off)
        is AiRequest.Chat -> {
            val budget = options.reasoning
            when {
                !request.reasoning -> null
                budget == null -> reasoningBlock(ReasoningBudget.Visible())
                budget == ReasoningBudget.Off -> null
                else -> reasoningBlock(budget)
            }
        }
    }

    /** The three `reasoning` bodies, one per [ReasoningBudget] case. */
    private fun reasoningBlock(budget: ReasoningBudget): JsonObject = buildJsonObject {
        when (budget) {
            is ReasoningBudget.Visible -> {
                put("max_tokens", budget.maxTokens)
                put("exclude", false)
            }

            is ReasoningBudget.Excluded -> {
                put("max_tokens", budget.maxTokens)
                put("exclude", true)
            }

            // The only shape with `enabled` rather than `max_tokens`: the overview asks the provider
            // not to think at all, where the references ask it to think 32 tokens and hide them.
            ReasoningBudget.Off -> {
                put("enabled", false)
                put("exclude", true)
            }
        }
    }

    /**
     * The `tools` array, with the ceilings the caller stated and Dart's defaults for the rest.
     *
     * `engine` is always `'auto'`, which Dart fixed. The four numbers are substituted rather than
     * omitted, because `OpenRouterRequestOptions` defaulted every one of them to `0` and
     * `webSearchContextSize` to `'low'`: a web search with no stated ceiling is not the same request
     * as one with a ceiling of zero, and the numbers reach the provider's own budget.
     */
    private fun webSearchTools(options: AiRequestOptions): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("type", WEB_SEARCH_TOOL)
                putJsonObject("parameters") {
                    put("engine", WEB_SEARCH_ENGINE)
                    put("max_results", options.maxWebResults ?: AiRequestOptions.DEFAULT_WEB_SEARCH_CEILING)
                    put("max_uses", options.maxWebSearchUses ?: AiRequestOptions.DEFAULT_WEB_SEARCH_CEILING)
                    put(
                        "max_total_results",
                        options.maxTotalWebResults ?: AiRequestOptions.DEFAULT_WEB_SEARCH_CEILING,
                    )
                    put(
                        "search_context_size",
                        options.webSearchContextSize ?: AiRequestOptions.DEFAULT_WEB_SEARCH_CONTEXT_SIZE,
                    )
                }
            },
        )
    }

    /**
     * `forced` names the tool; `automatic` hands the choice to the model.
     *
     * The `'auto'` case is a bare JSON string, not an object — that is the literal OpenRouter reads,
     * and the difference from the forced object is the whole of [WebSearchMode].
     */
    private fun toolChoiceOf(mode: WebSearchMode): JsonElement = when (mode) {
        WebSearchMode.Forced -> buildJsonObject { put("type", WEB_SEARCH_TOOL) }
        WebSearchMode.Automatic, WebSearchMode.Disabled -> JsonPrimitive(TOOL_CHOICE_AUTO)
    }
}

/**
 * The `response_format` of the `structuredSearch` branch of `_send`, schema included.
 *
 * `strict: true` makes the provider reject anything that does not fit, so this schema is the
 * contract `SearchReferences` is parsed against: `additionalProperties: false` and an explicit
 * `required` at every level. The search feature's own builder carries the same object for the same
 * reason — see [OpenRouterChatRequests] — and this is the copy both are checked against when the
 * search module is next touched.
 */
internal fun bibleSearchResponseFormat(): JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", SCHEMA_NAME)
        put("strict", true)
        putJsonObject("schema") {
            put("type", "object")
            put("additionalProperties", false)
            putJsonArray("required") {
                add("scriptures")
                add("suggestedQuestions")
            }
            putJsonObject("properties") {
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
}

/** `max_tokens` of the `structuredSearch` branch of `_send`. */
internal const val REFERENCES_MAX_TOKENS = 2400

/** `max_tokens` of the `shortSearchOverview` branch of `_send`. */
internal const val OVERVIEW_MAX_TOKENS = 1200

/** The `temperature` of `_send`, the one value it fixed for every request. */
private const val TEMPERATURE = 0.2

/** `'openrouter:web_search'`, the server-side tool OpenRouter offers. */
private const val WEB_SEARCH_TOOL = "openrouter:web_search"

/** `'auto'`, the only engine the Flutter build asked for. */
private const val WEB_SEARCH_ENGINE = "auto"

/** `'auto'`, the `tool_choice` that leaves the decision to the model. */
private const val TOOL_CHOICE_AUTO = "auto"

/** `'bible_search'`, the schema's name. */
private const val SCHEMA_NAME = "bible_search"

/**
 * What a search request would carry if it came through here without a prompt.
 *
 * The two search kinds hold no text — the search feature owns their prompts — so this stands in if
 * one is ever built here. It is a blank prompt rather than an exception so that a mistake in the
 * search wiring shows up as an obviously empty answer instead of a request the provider accepts.
 */
private const val MISSING_PROMPT_SENTINEL = ""
