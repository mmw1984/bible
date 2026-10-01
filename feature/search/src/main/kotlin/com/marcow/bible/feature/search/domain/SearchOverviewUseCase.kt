package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.network.openrouter.OpenRouterChatClient
import com.marcow.bible.core.network.openrouter.OpenRouterModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI Overview half of an AI search, mirroring `_searchOverview` in
 * `legacy/flutter/lib/ai_service.dart`.
 *
 * Three steps, in the order Dart took them and for the same reasons:
 *
 *  1. build the prompt ([searchOverviewPrompt]),
 *  2. post it as the `shortSearchOverview` request ([OpenRouterSearchRequests.overview]),
 *  3. run the answer through [cleanSearchOverview] and refuse an empty one.
 *
 * The emptiness check is the step that is easy to lose. The overview *is* the panel — there is
 * nowhere to hide a blank — so the Flutter build threw rather than publishing `''`, and the sheet
 * draws `overview_failed` instead. A model that answers with nothing but stripped metadata hits
 * exactly this path, which is the reason the clean-up strips as much as it does.
 *
 * Public because it is a constructor parameter of the public `AiSearchUseCase`; see
 * [AiScriptureReference] for why that chain has to be public rather than module-visible.
 */
@Singleton
class SearchOverviewUseCase @Inject constructor(
    private val chatClient: OpenRouterChatClient,
    private val modelId: OpenRouterModelId,
) {
    /** The cleaned overview prose for [query]. */
    suspend operator fun invoke(query: String, memory: String, aiLanguage: String): String {
        val raw = chatClient.complete(
            OpenRouterSearchRequests.overview(
                prompt = searchOverviewPrompt(query, memory, aiLanguage),
                model = modelId.modelId(),
            ),
        )
        val overview = cleanSearchOverview(raw)
        if (overview.isEmpty()) throw AiSearchFormatException(EMPTY_OVERVIEW_MESSAGE)
        return overview
    }
}
