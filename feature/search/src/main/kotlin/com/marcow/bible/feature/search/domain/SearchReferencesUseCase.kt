package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.network.openrouter.OpenRouterChatClient
import com.marcow.bible.core.network.openrouter.OpenRouterModelId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The scripture-references half of an AI search, mirroring `_searchReferences` in
 * `legacy/flutter/lib/ai_service.dart`.
 *
 * Same three steps as [SearchOverviewUseCase] — prompt, request, parse — with the `structuredSearch`
 * request instead of the overview one, so the answer is JSON against the `bible_search` schema
 * rather than prose.
 *
 * [BibleRepository] is here for one thing only: the canon the parser validates book ids and chapter
 * numbers against, which in Dart was the top-level `bibleBooks` list. Reading it from the database
 * rather than a hardcoded table is what lets the parser drop `PTT 5:99` for the same reason Flutter
 * did — that chapter does not exist — without the search feature carrying its own copy of the canon.
 *
 * Public because it is a constructor parameter of the public `AiSearchUseCase`, and its own return
 * type is [AiSearchReferences], which has to be public for the same reason. See [AiScriptureReference].
 */
@Singleton
class SearchReferencesUseCase @Inject constructor(
    private val chatClient: OpenRouterChatClient,
    private val modelId: OpenRouterModelId,
    private val bibleRepository: BibleRepository,
) {
    /** The validated references for [query]. */
    suspend operator fun invoke(query: String, memory: String, aiLanguage: String): AiSearchReferences {
        val raw = chatClient.complete(
            OpenRouterSearchRequests.references(
                prompt = searchReferencesPrompt(query, memory, aiLanguage),
                model = modelId.modelId(),
            ),
        )
        return parseSearchReferences(raw, bibleRepository.books())
    }
}
