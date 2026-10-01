package com.marcow.bible.core.network.openrouter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `WebResearchResponse` of `legacy/flutter/lib/openrouter_service.dart:636`, and the three
 * `StateError`s `research()` throws before returning one.
 *
 * It is a value rather than a `Flow<ChatEvent>` because a research answer is not streamed: the
 * provider runs the search itself and answers whole, with the evidence attached to the message. That
 * is also why it is not on `AiProvider` — see the port's own note in `ChatEvent.kt` — and why this
 * lives beside `AiRequestOptions.Research` rather than in the chat.
 *
 * The two checks are the load-bearing part and both are preserved exactly:
 *
 *  - A model that quietly answered from its own memory instead of searching carries no annotations and
 *    a `web_search_requests` of zero, so the answer is refused rather than shown. The Flutter build
 *    threw 'OpenRouter web search did not run.' for exactly that, and a chat that looks researched
 *    without being researched is worse than an error.
 *  - Content and sources together have to be something: 'OpenRouter web search returned no evidence.'
 *
 * The `searchRequests < 1 && sources.isEmpty` in the first check is Dart's, not a shortcut: evidence
 * that *did* arrive is proof the search ran even when the provider did not count it, so the sources
 * alone satisfy the check. [searchRequests] is then floored at 1 for the same reason — the value is
 * how many searches were spent, and a call that returned sources spent one.
 */
data class WebResearchResponse(
    /** `_contentText(message['content']).trim()`, the model's own brief. */
    val summary: String,
    /** The annotations, normalized and capped — [researchSources]'s work. */
    val sources: List<WebResearchSource>,
    /** `usage.server_tool_use.web_search_requests`, floored at 1 when sources arrived. */
    val searchRequests: Int,
) {
    /**
     * `String get compactEvidence`: the brief followed by the evidence, for a prompt that wants both.
     *
     * Each excerpt is cut to [EXCERPT_LIMIT] characters and a source whose entry would be blank is
     * dropped rather than printed as a bare `[title]`, and an answer with no usable evidence is
     * returned as the summary alone instead of a summary followed by nothing.
     */
    val compactEvidence: String
        get() {
            val evidence = sources
                .map { source -> "[${source.title}] ${source.excerpt.take(EXCERPT_LIMIT).trim()}" }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n")
            return if (evidence.isEmpty()) summary else "$summary\n\n$evidence"
        }

    private companion object {
        /** `substring(0, 1800)`, the ceiling `compactEvidence` put on one excerpt. */
        const val EXCERPT_LIMIT = 1800
    }
}

/**
 * `WebResearchSource` of `legacy/flutter/lib/openrouter_service.dart:624`: one page the model's search
 * actually read.
 *
 * [url] is the normalized URL rather than the one the provider sent, because that normalized form is
 * what dedupes — two citations of the same article through two tracking URLs are one source, not two
 * — and it is also what is shown, since the tracking parameters are of no use to a reader.
 */
data class WebResearchSource(val url: String, val title: String, val excerpt: String)

/**
 * `research()`'s three refusals, kept apart as types rather than as the strings a caller would have to
 * compare.
 *
 * Dart threw a bare `StateError` for all three and let the message carry the whole meaning; here each
 * is its own class so a screen can say something different for "the model did not search" and "there
 * was nothing to show".
 */
sealed class WebResearchException(message: String) : Exception(message) {
    /** The 2xx body was not the object `research()` parses. */
    class InvalidResponse : WebResearchException(INVALID_RESPONSE_MESSAGE)

    /** Neither annotations nor a counted search request: the model answered without searching. */
    class SearchDidNotRun : WebResearchException(SEARCH_DID_NOT_RUN_MESSAGE)

    /** The search ran and returned neither a brief nor a source. */
    class NoEvidence : WebResearchException(NO_EVIDENCE_MESSAGE)
}

/**
 * `research()` of `legacy/flutter/lib/openrouter_service.dart:236`, minus the socket: the 2xx body
 * parsed into a [WebResearchResponse], and every refusal thrown.
 *
 * The transport is the same one the chat answers through, so what a status code means — including the
 * two that drop the stored key — is settled once, in the same place, and this only has to read a body
 * that already arrived.
 *
 * A body that is not JSON at all throws `SerializationException`, as it threw `FormatException` in
 * Dart: it is a protocol-level failure rather than one of the three refusals, and
 * [WebResearchException.InvalidResponse] is reserved for the answer that parsed and was still not the
 * object this reads.
 */
internal fun readWebResearch(raw: String): WebResearchResponse {
    // `jsonDecode` in Dart, and its `is! Map` check: a JSON array or a bare scalar is an answer of
    // some other shape, not a malformed one.
    val payload = Json.parseToJsonElement(raw) as? JsonObject ?: throw WebResearchException.InvalidResponse()

    val message = ((payload["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
    val content = contentOf(message?.get("content"))
    val sources = researchSources(message?.get("annotations"))
    val searchRequests = webSearchRequestCount(payload)

    if (searchRequests < 1 && sources.isEmpty()) throw WebResearchException.SearchDidNotRun()
    if (content.isBlank() && sources.isEmpty()) throw WebResearchException.NoEvidence()

    return WebResearchResponse(
        summary = content.trim(),
        sources = sources,
        searchRequests = if (searchRequests < 1) 1 else searchRequests,
    )
}

/**
 * `_researchSources(annotations)` of `legacy/flutter/lib/openrouter_service.dart:485`.
 *
 * Four rules, and each answers a shape the provider actually sends:
 *
 *  - The citation is read out of `url_citation` when it is there and off the annotation itself when it
 *    is not. OpenRouter has sent both, and the second is not the deprecated one — it is the same shape
 *    with the wrapper omitted.
 *  - A URL with no scheme or no host is dropped, which is what takes `javascript:` and a bare path out.
 *  - **Two sources per host.** A search that returns ten results from one site is not ten sources, and
 *    the evidence is quoted into a prompt; the cap is Dart's and is what keeps one site from filling it.
 *  - A duplicate URL is dropped, and the duplicate check runs before the host count is read — so a URL
 *    seen twice does not also spend a second slot on the host the first copy took.
 *
 * Annotations that are not objects, and a non-string `url`, are skipped rather than failed: the
 * provider's citation types that are not web citations belong here in the list too, and one of them is
 * not a reason to refuse an answer that has real sources in it.
 */
internal fun researchSources(annotations: JsonElement?): List<WebResearchSource> {
    if (annotations !is JsonArray) return emptyList()
    val sources = mutableListOf<WebResearchSource>()
    val seenUrls = mutableSetOf<String>()
    val hostCounts = mutableMapOf<String, Int>()
    for (annotation in annotations.filterIsInstance<JsonObject>()) {
        val citation = annotation["url_citation"] as? JsonObject ?: annotation
        val url = (citation["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        val normalized = normalizeResearchUrl(url) ?: continue
        val host = hostOf(normalized) ?: continue
        if (!seenUrls.add(normalized) || (hostCounts[host] ?: 0) >= MAX_SOURCES_PER_HOST) continue
        hostCounts[host] = (hostCounts[host] ?: 0) + 1
        val title = (citation["title"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() } ?: host
        sources += WebResearchSource(url = normalized, title = title, excerpt = excerptOf(citation["content"]))
    }
    return sources
}

/**
 * `usage.server_tool_use.web_search_requests`, or 0 when the provider sent no count.
 *
 * A non-number is 0 rather than a read failure: `(rawRequests is num ? … : 0)` in Dart, and the zero
 * is what makes the "did not run" check fire rather than being silently treated as evidence.
 */
private fun webSearchRequestCount(payload: JsonObject): Int {
    val serverToolUse = (payload["usage"] as? JsonObject)?.get("server_tool_use") as? JsonObject
    val raw = (serverToolUse?.get("web_search_requests") as? JsonPrimitive)?.content?.toDoubleOrNull()
    return raw?.toInt() ?: 0
}

/**
 * `_normalizeResearchUri(uri)` of `legacy/flutter/lib/openrouter_service.dart:518`, and the
 * `Uri.tryParse` guard in front of it: the normalized URL, or null for a URL that is not one.
 *
 * Tracking parameters are dropped and the scheme, host and query-key case are normalized, because a
 * citation of `https://One.test/a?utm_source=x` and one of `https://one.test/a` are the same page and
 * the first is not what a reader should be shown. The fragment goes for the same reason: it is a
 * position inside a page, not a different page. A default port goes because `Uri` elides it and so
 * must this, or every citation of a `https://…:443/` URL would fail to match its unported twin.
 *
 * Null is what takes `Uri.tryParse` having no opinion on a relative path out of the sources, and a URL
 * whose host is empty out of them as well.
 *
 * A kept parameter is re-joined as it arrived rather than decoded and re-encoded, which is what Dart's
 * `queryParameters` round trip does. It is also the reason a `+` or a `%20` inside a value survives
 * this unchanged instead of being normalized to one spelling of itself.
 */
internal fun normalizeResearchUrl(url: String): String? {
    val schemeEnd = url.indexOf(SCHEME_SEPARATOR)
    if (schemeEnd <= 0) return null
    val scheme = url.substring(0, schemeEnd).lowercase()
    val afterScheme = url.substring(schemeEnd + SCHEME_SEPARATOR.length)
    val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    if (authorityEnd < 0) return null

    val authority = afterScheme.substring(0, authorityEnd)
    val hostAndPort = authority.substringAfterLast('@')
    val host = hostAndPort.substringBefore(':').lowercase()
    if (host.isEmpty()) return null
    val port = hostAndPort.substringAfter(':', missingDelimiterValue = "")
        .takeIf { hostAndPort.contains(':') && it.isNotEmpty() }
        ?.takeIf { it != defaultPortFor(scheme) }

    val remainder = afterScheme.substring(authorityEnd)
    val path = remainder.substringBefore('?').substringBefore('#')
    val query = remainder.substringAfter('?', missingDelimiterValue = "").substringBefore('#')

    val builder = StringBuilder(scheme).append(SCHEME_SEPARATOR)
    // `userInfo` is kept by Dart's rebuild, so `https://user@host/` normalizes to itself rather than
    // to a URL that silently names a different resource.
    builder.append(authority.substringBeforeLast('@', missingDelimiterValue = ""))
    builder.append(host)
    port?.let { builder.append(':').append(it) }
    builder.append(path)
    val kept = query
        .split('&')
        .filter { pair -> pair.isNotEmpty() && !isTrackingKey(pair.substringBefore('=')) }
    if (kept.isNotEmpty()) builder.append('?').append(kept.joinToString("&"))
    return builder.toString()
}

/**
 * The host of an already-normalized URL, which [researchSources] counts by.
 *
 * Read back out of the normalized form rather than carried alongside it, so the thing being capped and
 * the thing being deduped can never be two different strings.
 */
private fun hostOf(normalizedUrl: String): String? = normalizedUrl
    .substringAfter(SCHEME_SEPARATOR, missingDelimiterValue = "")
    .substringBeforeLast('@')
    .substringBefore('/')
    .substringBefore('?')
    .substringBefore(':')
    .takeIf { it.isNotEmpty() }

/** The port `Uri` elides for a scheme, and so this does — a non-default one is kept. */
private fun defaultPortFor(scheme: String): String? = when (scheme) {
    "http" -> DEFAULT_HTTP_PORT
    "https" -> DEFAULT_HTTPS_PORT
    else -> null
}

/** `content is String ? content.trim() : ''`, so an excerpt is never a number's digits. */
private fun excerptOf(content: JsonElement?): String =
    (content as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

/**
 * `key.startsWith('utm_') || trackingKeys.contains(key)`, both compared in lower case.
 *
 * The six named keys are Dart's. `utm_` is a prefix rather than a key because the family is open —
 * `utm_medium`, `utm_campaign` and the rest — and each one is a different name for "this link came
 * from somewhere". A key that is kept keeps the spelling it arrived with, as Dart's did: only the
 * comparison is case-folded.
 */
private fun isTrackingKey(rawKey: String): Boolean {
    val key = rawKey.lowercase()
    return key.startsWith(UTM_PREFIX) || key in TRACKING_KEYS
}

/** `StateError('OpenRouter returned an invalid research response.')`. */
private const val INVALID_RESPONSE_MESSAGE = "OpenRouter returned an invalid research response."

/** `StateError('OpenRouter web search did not run.')`. */
private const val SEARCH_DID_NOT_RUN_MESSAGE = "OpenRouter web search did not run."

/** `StateError('OpenRouter web search returned no evidence.')`. */
private const val NO_EVIDENCE_MESSAGE = "OpenRouter web search returned no evidence."

/** `hostCounts[host] >= 2`: two sources from one site, and no more. */
private const val MAX_SOURCES_PER_HOST = 2

private const val SCHEME_SEPARATOR = "://"

/** The prefix of the open family of `utm_` tracking keys. */
private const val UTM_PREFIX = "utm_"

/** The two ports `Uri` drops, because they are their own scheme's. */
private const val DEFAULT_HTTP_PORT = "80"

private const val DEFAULT_HTTPS_PORT = "443"

private val TRACKING_KEYS = setOf("fbclid", "gclid", "mc_cid", "mc_eid", "ref", "source")
