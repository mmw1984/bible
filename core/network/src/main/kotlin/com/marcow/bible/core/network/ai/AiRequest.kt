package com.marcow.bible.core.network.ai

/**
 * Which tuned prompt a request carries, the explicit enum `NATIVE_PLAN.md` §4.3 asks for in place of
 * sniffing the prompt text.
 *
 * Dart derived the same three answers by looking for `BIBLE_SEARCH_REFERENCES_JSON`,
 * `BIBLE_SEARCH_OVERVIEW`, and the `"overview"` / `"scriptures"` / `Return ONLY valid JSON` triple
 * inside `_send` (`legacy/flutter/lib/openrouter_service.dart:345`–`353`). The plan calls that a
 * smell — a prompt edit could silently change which request shape a search sends — so the caller
 * names the kind and the mapping layer reads [kind]. The markers stay in the prompt text, because
 * the model reads them too.
 */
enum class PromptKind {
    /** The conversation system prompt, `ChatKind` of §4.3. */
    Chat,

    /** `BIBLE_SEARCH_OVERVIEW`: prose only, reasoning excluded, `max_tokens 1200`. */
    SearchOverview,

    /** `BIBLE_SEARCH_REFERENCES_JSON`: `json_schema` output, `max_tokens 2400`. */
    SearchReferences,
}

/**
 * One thing to ask a model, in the shape every provider is asked it.
 *
 * The two search cases carry no prompt text because the query and the memory are part of the prompt
 * and the prompt is the caller's — the search feature already owns `SearchPrompts.kt`, and having
 * the same text in two places would be a second copy to keep verbatim. They are spelled out here
 * anyway because the *request shape* genuinely differs per kind: that is the whole reason
 * [PromptKind] exists.
 */
sealed interface AiRequest {
    /**
     * The chat path: [prompt] is the assembled system prompt plus the user's question.
     *
     * [reasoning] is the `options.reasoning` of `OpenRouterRequestOptions`, kept on the request
     * rather than only on the options because it is the one flag the chat turns off per call site
     * (`_answerExistingMessage` streams with reasoning on, `_searchOverview` with it off).
     */
    data class Chat(val prompt: String, val reasoning: Boolean = true) : AiRequest

    /** `BIBLE_SEARCH_OVERVIEW`. */
    data object SearchOverview : AiRequest

    /** `BIBLE_SEARCH_REFERENCES_JSON`. */
    data object SearchReferences : AiRequest
}

/** The [PromptKind] of a request, which is the only thing the mapping layer needs from it. */
val AiRequest.kind: PromptKind
    get() = when (this) {
        is AiRequest.Chat -> PromptKind.Chat
        is AiRequest.SearchOverview -> PromptKind.SearchOverview
        is AiRequest.SearchReferences -> PromptKind.SearchReferences
    }

/**
 * How hard a provider should look on the web, replacing `OpenRouterWebSearch`.
 *
 * The three cases are Dart's `disabled` / `forced` / `automatic`, and the difference between
 * [Forced] and [Automatic] is only the `tool_choice` of the request body: forced names the tool,
 * automatic leaves the model to decide. Both still put the tool on the wire.
 */
enum class WebSearchMode {
    /** No `tools` block at all — what the search paths and an on-device model both send. */
    Disabled,

    /** `tool_choice: {"type": "openrouter:web_search"}`, which is what `research()` asks for. */
    Forced,

    /** `tool_choice: "auto"`, which is what the chat's first attempt asks for. */
    Automatic,
}

/**
 * How much thinking a provider is allowed, replacing the `reasoning` block's two shapes.
 *
 * Dart wrote three different objects into `reasoning` depending on the path
 * (`legacy/flutter/lib/openrouter_service.dart:367`–`373`), and `NATIVE_PLAN.md` §4.1 asks for
 * each to have a defined behaviour on both providers. They are named here rather than left as a
 * `JsonObject`, because the on-device provider has no such block and needs to recognise which of the
 * three a request means in order to decline it the way the Flutter build did.
 */
sealed interface ReasoningBudget {
    /**
     * `{'max_tokens': <maxTokens>, 'exclude': false}` — the chat's default.
     *
     * `exclude: false` is what puts the thinking in a channel of its own, which is the block the UI
     * folds away (`NATIVE_PLAN.md` §4.2).
     */
    data class Visible(val maxTokens: Int = DEFAULT_MAX_TOKENS) : ReasoningBudget

    /**
     * `{'max_tokens': 32, 'exclude': true}` — the `structuredSearch` branch.
     *
     * A 32-token budget is still written on the wire even though the content is excluded, so a
     * schema-constrained answer gets a little room to look before it commits to the shape.
     */
    data class Excluded(val maxTokens: Int = 32) : ReasoningBudget

    /**
     * Off rather than small, which is the two ways Dart asked for no thinking.
     *
     * As the `shortSearchOverview` branch's own override it is `{'enabled': false, 'exclude': true}`:
     * the provider is told to stop. Stated instead by a caller on [AiRequestOptions] it is
     * `options.reasoning: false`, which Dart's conditional turned into *no* `reasoning` key — so the
     * mapping leaves the key off for a chat and the two arrive at the provider as the different
     * requests they were.
     */
    data object Off : ReasoningBudget

    companion object {
        /** `reasoningMaxTokens = 8192`, the `OpenRouterRequestOptions` default. */
        const val DEFAULT_MAX_TOKENS = 8192
    }
}

/**
 * The per-call knobs of a request, replacing `OpenRouterRequestOptions` in
 * `legacy/flutter/lib/openrouter_service.dart:600`.
 *
 * Every field defaults to null, which is not the same as Dart's defaults and is deliberately so. Dart
 * declared each web-search ceiling as a non-nullable `int` defaulting to `0` and
 * `webSearchContextSize` as `'low'`, so `research()` sent `max_results: 0` without ever meaning to
 * bound anything — a null that Dart's `jsonEncode` dropped and a stated `0` are different requests
 * to the provider, and the Flutter build happened to send the `0`. Null here means "unstated", and
 * `OpenRouterChatRequests` substitutes [DEFAULT_WEB_SEARCH_CEILING] and
 * [DEFAULT_WEB_SEARCH_CONTEXT_SIZE] so the bytes are what they were; a caller that states a real
 * ceiling now says so explicitly instead of by accident.
 *
 * [reasoning] is null rather than a `Boolean` because Dart's `options.reasoning` was a `bool` that
 * was *not* what decided the `reasoning` block: the two search paths overrode it unconditionally. A
 * null default says "the request decides", and the mapping resolves it per
 * [com.marcow.bible.core.network.ai.PromptKind].
 */
data class AiRequestOptions(
    val webSearch: WebSearchMode = WebSearchMode.Disabled,
    val maxToolCalls: Int? = null,
    val maxWebSearchUses: Int? = null,
    val maxWebResults: Int? = null,
    val maxTotalWebResults: Int? = null,
    val webSearchContextSize: String? = null,
    val reasoning: ReasoningBudget? = null,
    val maxTokens: Int? = null,
) {
    companion object {
        /**
         * The chat's first attempt, from `_answerExistingMessage` in `legacy/flutter/lib/ai_service.dart:444`.
         *
         * Spelled out because the numbers are the tuning: two tool calls, two web uses, five results
         * per use, ten in total, and a `'low'` context size so a chat answer does not arrive wrapped
         * in more quoted web text than it has of its own.
         */
        val ChatWithWebSearch = AiRequestOptions(
            webSearch = WebSearchMode.Automatic,
            maxToolCalls = 2,
            maxWebSearchUses = 2,
            maxWebResults = 5,
            maxTotalWebResults = 10,
            webSearchContextSize = "low",
        )

        /**
         * The chat's retry, from the `catch` in the same method.
         *
         * The tool goes and nothing else changes — same prompt, same ceilings — because what failed
         * was the combination, not the search. `NATIVE_PLAN.md` §4.5 is emphatic that this retry
         * exists: without it `openrouter/free` fails constantly.
         */
        val ChatWithoutWebSearch = AiRequestOptions(webSearch = WebSearchMode.Disabled)

        /**
         * `research()` of `legacy/flutter/lib/openrouter_service.dart:229`: forced, one tool call, a
         * low context size and a long ceiling.
         *
         * The three per-search ceilings are stated even though [DEFAULT_WEB_SEARCH_CEILING] is what
         * an unstated one becomes, because `research()` was the one path that did not take the `0`:
         * Dart passed one web use, six results per use and six in total, so a research call left to
         * the default would have asked OpenRouter for *no* web results at all — a forced tool with an
         * empty budget, which is not the request the Flutter build made.
         *
         * [reasoning] is [ReasoningBudget.Off] for `reasoning: false`, and that one is not a default
         * either: Dart's `if (… && options.reasoning)` left the `reasoning` key off the body
         * entirely, whereas a caller that stated nothing would get the visible 8192-token default.
         */
        val Research = AiRequestOptions(
            webSearch = WebSearchMode.Forced,
            maxToolCalls = 1,
            maxWebSearchUses = 1,
            maxWebResults = 6,
            maxTotalWebResults = 6,
            webSearchContextSize = "low",
            reasoning = ReasoningBudget.Off,
            maxTokens = RESEARCH_MAX_TOKENS,
        )

        /** `max_tokens: 8192` of the `research()` path. */
        const val RESEARCH_MAX_TOKENS = 8192

        /**
         * The `0` that every web-search ceiling defaulted to in `OpenRouterRequestOptions`.
         *
         * Substituted by `OpenRouterChatRequests` for a field left null, because Dart's `jsonEncode`
         * wrote the `0` when the option was simply at its default. A web search with no stated ceiling
         * is not the same request as one with a ceiling of zero, and the number reaches the provider's
         * own budget.
         */
        const val DEFAULT_WEB_SEARCH_CEILING = 0

        /** `webSearchContextSize = 'low'`, the default of `OpenRouterRequestOptions`. */
        const val DEFAULT_WEB_SEARCH_CONTEXT_SIZE = "low"
    }
}
