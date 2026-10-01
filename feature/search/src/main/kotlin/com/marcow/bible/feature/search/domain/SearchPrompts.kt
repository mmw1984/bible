package com.marcow.bible.feature.search.domain

/**
 * The two search prompts, moved across word for word from `_searchOverview` / `_searchReferences` in
 * `legacy/flutter/lib/ai_service.dart`.
 *
 * `NATIVE_PLAN.md` §4.3 is explicit that these are tuned: the line breaks, the wording, the example
 * references and the `BIBLE_SEARCH_*` markers the request builder sniffs for are all load-bearing,
 * so nothing here is reworded, reordered or re-indented. The markers are the only thing the
 * transport keys on (see [OpenRouterSearchRequests]), which is why they stay in the text.
 *
 * The memory block and the query are interpolated exactly where Dart interpolated them, trailing
 * newline included, because a prompt that differs by a blank line is a different prompt.
 */
internal fun searchOverviewPrompt(query: String, memory: String, aiLanguage: String): String = """
BIBLE_SEARCH_OVERVIEW
You write the AI Overview inside a Bible reading app. Answer the search query by
meaning in concise $aiLanguage. Carefully distinguish what the
Bible says from interpretation. Never invent or quote verse text. Do not return
JSON, scripture references, a heading, or follow-up questions. Return only the
short overview prose. Never output analysis, reasoning, thinking steps, or
provider metadata.

Persistent user memory:
$memory

Search query: $query
"""

internal fun searchReferencesPrompt(query: String, memory: String, aiLanguage: String): String = """
BIBLE_SEARCH_REFERENCES_JSON
You find scripture references for semantic search inside a Bible reading app.
Never invent or quote verse text; the app resolves every reference from its
local Bible database.

Return ONLY valid JSON in exactly this shape:
{"scriptures":[{"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":17,"reason":"why relevant"}],"suggestedQuestions":["follow-up"]}
Use canonical 3-letter book IDs. Return up to 16 relevant references for a broad
query and up to 8 for a narrow query. Return 3 questions in
$aiLanguage.
Every reason must accurately describe the referenced verses. For example,
MAT 4:1-11 is Jesus' temptation; the Samaritan woman is JHN 4. Be concise.

Persistent user memory:
$memory

Search query: $query
"""
