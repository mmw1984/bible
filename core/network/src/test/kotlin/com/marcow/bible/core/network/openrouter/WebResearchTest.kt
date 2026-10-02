package com.marcow.bible.core.network.openrouter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `research()` of `legacy/flutter/lib/openrouter_service.dart:236` checked without a socket, the two
 * halves of it that are not the request body.
 *
 * The request half is already pinned by `OpenRouterChatRequestsTest` — the forced tool, the
 * `max_tool_calls 1`, the `low` context size, the 8192-token ceiling and the absence of `plugins` —
 * so what is here is the answer: which annotations become sources, and when the whole thing is
 * refused. Both refusals are checked, because a research answer that looks researched but was not is
 * the failure mode they exist to prevent.
 */
class WebResearchTest {
    @Test
    fun `a searched answer carries its brief, its sources and the search it spent`() {
        val response = readWebResearch(researchPayload(searchRequests = JsonPrimitive(1)))

        assertEquals("Evidence brief", response.summary)
        assertEquals(1, response.searchRequests)
    }

    @Test
    fun `the Flutter fixture's four citations come back as three normalized sources`() {
        // The same annotations as `legacy/flutter/test/openrouter_service_test.dart:20`, and the same
        // expectation: `https://one.test/a?id=1`, `https://one.test/b`, `https://two.test/a`. The
        // first loses its `utm_source` and its `#part`, the second loses its `fbclid`, the third is
        // dropped by the per-host cap, and the fourth loses its `gclid`.
        val response = readWebResearch(researchPayload(annotations = dartFixture()))

        assertEquals(
            listOf("https://one.test/a?id=1", "https://one.test/b", "https://two.test/a"),
            response.sources.map { it.url },
        )
        assertEquals(1, response.searchRequests)
    }

    @Test
    fun `a citation whose wrapper is missing is read off the annotation itself`() {
        // `annotation['url_citation'] is Map ? … : annotation`. OpenRouter has sent the citation's own
        // keys at the top level, so that shape is a source and not a citation with nothing in it.
        val sources = researchSources(
            citations(
                buildJsonObject {
                    put("type", "url_citation")
                    put("url", "https://one.test/a")
                    put("title", "One A")
                    put("content", "A")
                },
            ),
        )

        assertEquals(listOf("https://one.test/a"), sources.map { it.url })
        assertEquals(listOf("One A"), sources.map { it.title })
        assertEquals(listOf("A"), sources.map { it.excerpt })
    }

    @Test
    fun `a host that already gave two sources does not get a third`() {
        // `_researchSources` caps each host at two. A search that returns ten results from one site is
        // not ten sources, and the evidence is quoted into a prompt — the cap is what stops one site
        // filling it. `one.test/c` is dropped here for exactly that reason, and `two.test/a` survives
        // because the cap is per host rather than a total.
        val sources = researchSources(citations(*dartFixture().toTypedArray()))

        assertEquals(listOf("One A", "One B", "Two"), sources.map { it.title })
        assertEquals(listOf("A", "B", "D"), sources.map { it.excerpt })
    }

    @Test
    fun `a source with no title is named after its host`() {
        // Dart's `title is String && title.trim().isNotEmpty ? title.trim() : host`. A blank title and a
        // missing one are the same case, because `[title]` with nothing after it is a line of noise in
        // the evidence rather than a source.
        val sources = researchSources(
            citations(
                citation("https://one.test/a", content = "A"),
                citation("https://two.test/b", title = "  ", content = "B"),
                citation("https://three.test/c", title = "Three C", content = "C"),
            ),
        )

        assertEquals(listOf("one.test", "two.test", "Three C"), sources.map { it.title })
        assertEquals(listOf("A", "B", "C"), sources.map { it.excerpt })
    }

    @Test
    fun `a url that is not one is not a source`() {
        // `Uri.tryParse(url) == null || !uri.hasScheme || uri.host.isEmpty`. A `javascript:` URL has a
        // scheme and no host, a relative path has neither, and neither is something a reader could be
        // shown or the evidence could quote.
        val sources = researchSources(
            citations(
                citation("javascript:alert(1)", title = "Script"),
                citation("/relative/path", title = "Relative"),
                citation("https://", title = "No host"),
                citation("https://ok.test/a", title = "Fine"),
            ),
        )

        assertEquals(listOf("https://ok.test/a"), sources.map { it.url })
    }

    @Test
    fun `a repeated url is one source and spends one host slot`() {
        // `!seenUrls.add(normalizedUrl) || hostCounts[host] >= 2`. The duplicate is caught by the
        // `add`, which returns false, and the short-circuit is what stops it also being counted as a
        // second source from a host that has only given one — otherwise a page cited twice under two
        // tracking URLs would use up the cap and push out a real second source.
        val sources = researchSources(
            citations(
                citation("https://one.test/a?utm_source=x", title = "One A"),
                citation("https://one.test/a", title = "One A again"),
                citation("https://one.test/b", title = "One B"),
            ),
        )

        assertEquals(listOf("One A", "One B"), sources.map { it.title })
    }

    @Test
    fun `an annotation that is not a web citation is skipped rather than failed`() {
        // The provider's annotation list holds non-web citation types too, and one of them is not a
        // reason to refuse an answer that has real sources in it. A non-string url and a non-object
        // annotation are the same case: `annotations.whereType<Map>()` and `url is String`.
        val sources = researchSources(
            buildJsonArray {
                add(citation("https://one.test/a", title = "One A"))
                add(buildJsonObject { put("type", "file_citation") })
                add(buildJsonObject { put("url", 42) })
                add("not an object")
            },
        )

        assertEquals(listOf("One A"), sources.map { it.title })
    }

    @Test
    fun `a model that answered without searching is refused`() {
        // `searchRequests < 1 && sources.isEmpty`. The message says as much, and the point is the
        // refusal: a brief with no annotations and a zero count is a model answering from its own
        // memory, and showing it as a searched answer is the one outcome worse than an error.
        val failure = assertThrows(WebResearchException.SearchDidNotRun::class.java) {
            readWebResearch(
                researchPayload(
                    summary = "unsupported fallback",
                    annotations = emptyList(),
                    searchRequests = JsonPrimitive(0),
                ),
            )
        }

        assertEquals("OpenRouter web search did not run.", failure.message)
    }

    @Test
    fun `a search that returned nothing at all is refused`() {
        // `content.trim().isEmpty && sources.isEmpty`, which is the other half: the tool ran, so the
        // first check passes, and there is still nothing to show.
        val failure = assertThrows(WebResearchException.NoEvidence::class.java) {
            readWebResearch(
                researchPayload(summary = "   ", annotations = emptyList(), searchRequests = JsonPrimitive(1)),
            )
        }

        assertEquals("OpenRouter web search returned no evidence.", failure.message)
    }

    @Test
    fun `sources alone are proof the search ran even when nothing was counted`() {
        // `searchRequests: searchRequests < 1 ? 1 : searchRequests`. The check above accepted these
        // annotations as evidence that the search happened, so the count is then floored at one: a
        // call that returned sources spent a search, and reporting zero next to a non-empty evidence
        // list would be the answer contradicting itself.
        val response = readWebResearch(
            researchPayload(
                summary = "brief",
                annotations = sourcesOf(TRIPLE_ONE_A),
                searchRequests = JsonPrimitive(0),
            ),
        )

        assertEquals(1, response.searchRequests)
        assertEquals(listOf("One A"), response.sources.map { it.title })
    }

    @Test
    fun `a count that is not a number counts as no search at all`() {
        // `(rawRequests is num ? rawRequests.toInt() : 0)`. A provider that sends the key as a string
        // has not told us a search ran, and reading its digits as a count would be a number invented
        // out of a value that is not one.
        val response = readWebResearch(
            researchPayload(
                summary = "brief",
                annotations = sourcesOf(TRIPLE_ONE_A),
                searchRequests = JsonPrimitive("one"),
            ),
        )

        assertEquals(1, response.searchRequests)
    }

    @Test
    fun `no usage object at all is the same as no count`() {
        // `serverToolUse is Map ? … : null`, so a provider that sends no `usage` is not a provider that
        // searched — unless the annotations say otherwise, which they do here.
        val response = readWebResearch(
            researchPayload(
                summary = "brief",
                annotations = sourcesOf(TRIPLE_ONE_A),
                searchRequests = null,
            ),
        )

        assertEquals(1, response.searchRequests)
    }

    @Test
    fun `a brief with no sources is still an answer`() {
        // The counted search satisfies the first check and the brief satisfies the second, which is
        // what makes this a legitimate result rather than a thin one: the model searched, said what it
        // found, and cited nothing it was willing to stand behind.
        val response = readWebResearch(
            researchPayload(summary = "brief", annotations = emptyList(), searchRequests = JsonPrimitive(2)),
        )

        assertEquals("brief", response.summary)
        assertEquals(2, response.searchRequests)
    }

    @Test
    fun `a body that is not the object research parses is not a research answer`() {
        // `payload is! Map` in Dart. A JSON array is a well-formed answer of some other shape, so it
        // is this refusal rather than the parse failure a non-JSON body raises.
        val failure = assertThrows(WebResearchException.InvalidResponse::class.java) {
            readWebResearch("[]")
        }

        assertEquals("OpenRouter returned an invalid research response.", failure.message)
    }

    @Test
    fun `content parts are read the same way the chat reads them`() {
        // `_contentText` is shared with the streamed path, and a model that answers a completed call in
        // parts would otherwise produce a blank brief here and be refused for having no evidence.
        val response = readWebResearch(
            researchPayload(
                summary = null,
                parts = listOf("reasoning.text" to "first ", null to "second"),
                annotations = emptyList(),
                searchRequests = JsonPrimitive(1),
            ),
        )

        assertEquals("first second", response.summary)
    }

    @Test
    fun `the evidence is the brief and then the sources, each excerpt cut`() {
        val long = "x".repeat(2000)
        val response = readWebResearch(
            researchPayload(
                summary = "brief",
                annotations = sourcesOf(
                    Triple("https://one.test/a", "One A", long),
                    Triple("https://two.test/b", "Two B", "short"),
                ),
                searchRequests = null,
            ),
        )

        val compact = response.compactEvidence
        assertTrue(compact.startsWith("brief\n\n[One A] xxx"), compact)
        assertTrue(compact.contains("[Two B] short"), compact)
        // 1800 characters of the 2000 that were sent, and not one more: an excerpt is a quote in a
        // prompt, and a whole page is how the evidence stops being about the question.
        assertEquals("brief\n\n[One A] " + "x".repeat(1800) + "\n\n[Two B] short", compact)
    }

    @Test
    fun `a source with no excerpt still counts as evidence, named after its host`() {
        // Dart's `'[${source.title}] ${excerpt.trim()}'` and `where((item) => item.trim().isNotEmpty)`.
        // A blank excerpt is not a dropped source: the entry is `[one.test] `, which trims to
        // `[one.test]` and is not empty. The title can never be blank either, because
        // `researchSources` falls back to the host — so the filter is belt and braces, and the only
        // way to reach the summary-alone branch is a response with no sources at all.
        val response = readWebResearch(
            researchPayload(
                summary = "brief",
                annotations = sourcesOf(BLANK_SOURCE),
                searchRequests = null,
            ),
        )

        assertEquals("brief\n\n[one.test]", response.compactEvidence)
    }

    @Test
    fun `an answer with no sources at all is the brief on its own`() {
        // Dart's `evidence.isEmpty ? summary : '$summary\n\n$evidence'`, which is the brief rather
        // than a brief followed by two newlines and nothing: this text is quoted into a prompt, and a
        // trailing gap there is a hint that reads as missing evidence rather than as an absence of it.
        val response = readWebResearch(
            researchPayload(summary = "brief", annotations = emptyList(), searchRequests = JsonPrimitive(1)),
        )

        assertEquals("brief", response.compactEvidence)
    }

    @Test
    fun `a port number that is the scheme's own is not part of the url`() {
        // `port: uri.hasPort ? uri.port : null` and `Uri`'s own elision of a default port. If `:443`
        // were kept, a citation of `https://one.test:443/a` would fail to match its unported twin and
        // the same page would be counted twice against the per-host cap.
        assertEquals("https://one.test/a", normalizeResearchUrl("https://one.test:443/a"))
        assertEquals("http://one.test/a", normalizeResearchUrl("http://one.test:80/a"))
        assertEquals("https://one.test:8443/a", normalizeResearchUrl("https://one.test:8443/a"))
    }

    @Test
    fun `only the scheme and the host are lower cased`() {
        // Dart lower-cases `scheme` and `host` and nothing else: the path is case-sensitive on most
        // servers and a kept query key keeps the spelling it arrived with, so only the *comparison*
        // against the tracking names is case-folded.
        assertEquals("https://one.test/A/B", normalizeResearchUrl("HTTPS://One.test/A/B"))
        assertEquals("https://one.test/a?ID=1", normalizeResearchUrl("https://one.test/a?ID=1"))
        // A tracking key is recognized whatever its case, and the parameter it names is gone.
        assertEquals("https://one.test/a", normalizeResearchUrl("https://one.test/a?UTM_Source=x"))
    }

    @Test
    fun `only the tracking names themselves are dropped`() {
        // `key.startsWith('utm_')` is a prefix match with an underscore in it, and the six names are
        // matched whole — so `utm`, `refs` and `sourced` are parameters a page asked for and are
        // evidence a reader would lose if they were treated as tracking.
        assertEquals("https://one.test/a?utm", normalizeResearchUrl("https://one.test/a?utm"))
        assertEquals(
            "https://one.test/a?refs=1&sourced=2",
            normalizeResearchUrl("https://one.test/a?refs=1&sourced=2&utm_medium=3"),
        )
    }

    @Test
    fun `a query of nothing but tracking parameters leaves no question mark`() {
        // Dart's `queryParameters: query.isEmpty ? null : query`, and `Uri` writes no `?` for a null
        // query. A URL left as `https://one.test/a?` would not match the untracked one it is the same
        // page as, and the duplicate check would let it through as a second source.
        assertEquals("https://one.test/a", normalizeResearchUrl("https://one.test/a?utm_source=x&fbclid=y"))
        assertEquals("https://one.test/a", normalizeResearchUrl("https://one.test/a?"))
    }

    @Test
    fun `the six named tracking keys are the ones that are dropped`() {
        // Dart's `trackingKeys` set, verbatim. `utm_` is handled separately as a prefix because the
        // family is open.
        val query = listOf("fbclid", "gclid", "mc_cid", "mc_eid", "ref", "source", "id")
            .joinToString("&") { "$it=$it" }

        assertEquals("https://one.test/a?id=id", normalizeResearchUrl("https://one.test/a?$query"))
    }

    /**
     * The research answer, built the way the provider sends one.
     *
     * Every key is optional because the parser's decisions are all about what the provider *left
     * out*: a missing `annotations`, a missing `usage`, a count that is not a number.
     *
     * @param summary the brief as a plain string, or null to send it as content parts instead.
     * @param annotations the `message.annotations` entries, or null for the key's absence.
     * @param searchRequests the count, or null to send no `usage` at all.
     */
    private fun researchPayload(
        summary: String? = "Evidence brief",
        parts: List<Pair<String?, String>?> = emptyList(),
        annotations: List<JsonObject>? = null,
        searchRequests: JsonPrimitive? = JsonPrimitive(1),
    ): String {
        val message = buildJsonObject {
            put("content", if (summary != null) JsonPrimitive(summary) else partsArray(parts))
            annotations?.let { put("annotations", citations(*it.toTypedArray())) }
        }
        return buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("message", message) }) })
            searchRequests?.let { count -> put("usage", buildJsonObject { put("server_tool_use", webSearch(count)) }) }
        }.toString()
    }

    /**
     * A null entry is a part that carries neither a type nor any text, which is what
     * [contentPart] writes when both of its arguments are null.
     */
    private fun partsArray(parts: List<Pair<String?, String>?>): JsonArray =
        buildJsonArray { parts.forEach { part -> add(contentPart(part?.first, part?.second)) } }

    /** `{"web_search_requests": <count>}`, the one key read out of `usage`. */
    private fun webSearch(count: JsonPrimitive): JsonObject = buildJsonObject { put("web_search_requests", count) }

    /** Sources given as `(url, title, excerpt)`, the shape a test thinks in. */
    private fun sourcesOf(vararg entries: Triple<String, String, String>): List<JsonObject> =
        entries.map { (url, title, excerpt) -> citation(url, title, excerpt) }

    /** A content part, which is how several OpenRouter models answer a completed call. */
    private fun contentPart(type: String?, text: String?): JsonObject = buildJsonObject {
        type?.let { put("type", it) }
        text?.let { put("text", it) }
    }

    /** One `url_citation` annotation, which is the shape OpenRouter has sent. */
    private fun citation(url: String, title: String? = null, content: String? = null): JsonObject = buildJsonObject {
        put("type", "url_citation")
        put(
            "url_citation",
            buildJsonObject {
                put("url", url)
                title?.let { put("title", it) }
                content?.let { put("content", it) }
            },
        )
    }

    /** A `message.annotations` array, or the absent key when there are none. */
    private fun citations(vararg entries: JsonObject): JsonArray = buildJsonArray { entries.forEach { add(it) } }

    /**
     * The four annotations of `legacy/flutter/test/openrouter_service_test.dart:20`, in the same order
     * so that the per-host cap is exercised where the Dart test exercised it.
     */
    private fun dartFixture(): List<JsonObject> = listOf(
        citation("https://one.test/a?utm_source=x&id=1#part", "One A", "A"),
        citation("https://one.test/b?fbclid=x", "One B", "B"),
        citation("https://one.test/c", "One C", "C"),
        citation("https://two.test/a?gclid=x", "Two", "D"),
    )

    private companion object {
        /** The one source most of the refusals are checked with: a brief's worth of evidence. */
        val TRIPLE_ONE_A = Triple("https://one.test/a", "One A", "A")

        /** A source the provider sent with neither a title nor an excerpt. */
        val BLANK_SOURCE = Triple("https://one.test/a", "", "")
    }
}
