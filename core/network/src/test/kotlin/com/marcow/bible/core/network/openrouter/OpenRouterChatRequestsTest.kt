package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.AiRequest
import com.marcow.bible.core.network.ai.AiRequestOptions
import com.marcow.bible.core.network.ai.PromptKind
import com.marcow.bible.core.network.ai.ReasoningBudget
import com.marcow.bible.core.network.ai.WebSearchMode
import com.marcow.bible.core.network.ai.kind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `AiRequest` to the bytes `_send` put on the wire, which is the mapping `NATIVE_PLAN.md` §4.3 asks
 * for as "a pure function, unit-tested over the four combinations".
 *
 * §4.3's four combinations are the three [com.marcow.bible.core.network.ai.PromptKind]s plus the
 * web-search fallback of §4.5. The cases below are those, plus the reasoning shapes and the two
 * things Dart did that are easy to get wrong:
 *
 *  - `max_tokens` and `reasoning` are *absent*, not null, on a chat that asks for neither. Dart's
 *    `'?maxTokens'` and its conditional `reasoning` entry both mean "leave the key out", and a body
 *    carrying `"max_tokens": null` is a different request.
 *  - a web search with no ceilings stated sends `0`, not nothing, because
 *    `OpenRouterRequestOptions` defaulted every ceiling to `0` and `jsonEncode` wrote what it held.
 *
 * The prompts themselves are a different file's business — `feature/search`'s `SearchPrompts.kt` for
 * the search ones — so no case here asserts on prompt text beyond the chat one it passes in.
 */
class OpenRouterChatRequestsTest {
    @Test
    fun `a chat request is one user message at the fixed temperature`() {
        val body = OpenRouterChatRequests.build(AiRequest.Chat("the question"), MODEL).body

        assertEquals(MODEL, body.string("model"))
        assertEquals(0.2, body.double("temperature"))
        assertEquals(
            listOf("user" to "the question"),
            body.arrayAt("messages").map { message ->
                val fields = message.jsonObject
                fields.string("role") to fields.string("content")
            },
        )
        assertFalse(body.boolean("stream"))
    }

    @Test
    fun `a chat request leaves max_tokens out unless one was asked for`() {
        assertNull(
            OpenRouterChatRequests.build(AiRequest.Chat("q"), MODEL).body["max_tokens"],
            "a chat must not invent a token ceiling",
        )

        val withCeiling = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions(maxTokens = 512))
            .body
        assertEquals(512, withCeiling.int("max_tokens"))
    }

    @Test
    fun `a chat thinks out loud by default and drops the whole key when told not to`() {
        val thinking = OpenRouterChatRequests.build(AiRequest.Chat("q"), MODEL).body
        assertEquals(8192, thinking.objectAt("reasoning").int("max_tokens"))
        assertFalse(thinking.objectAt("reasoning").boolean("exclude"))

        // Not `{"enabled": false}`: Dart's conditional left the key out entirely, which is a
        // different request from one that carries a key switching reasoning off.
        assertNull(OpenRouterChatRequests.build(AiRequest.Chat("q", reasoning = false), MODEL).body["reasoning"])
    }

    @Test
    fun `a chat can be given a narrower reasoning budget`() {
        val reasoning = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions(reasoning = ReasoningBudget.Visible(maxTokens = 64)))
            .body
            .objectAt("reasoning")

        assertEquals(64, reasoning.int("max_tokens"))
        assertFalse(reasoning.boolean("exclude"))
    }

    @Test
    fun `the search overview asks for short prose with reasoning switched off entirely`() {
        val body = OpenRouterChatRequests.build(AiRequest.SearchOverview, MODEL).body

        assertEquals(1200, body.int("max_tokens"))
        assertFalse(body.objectAt("reasoning").boolean("enabled"), "the overview asks the provider not to think")
        assertTrue(body.objectAt("reasoning").boolean("exclude"))
        assertNull(body["response_format"], "only the references path is schema-constrained")
        assertNull(body["tools"])
    }

    @Test
    fun `the search references are schema-constrained with a small hidden budget`() {
        val body = OpenRouterChatRequests.build(AiRequest.SearchReferences, MODEL).body

        assertEquals(2400, body.int("max_tokens"))
        assertEquals(32, body.objectAt("reasoning").int("max_tokens"))
        assertTrue(body.objectAt("reasoning").boolean("exclude"))
        assertEquals("json_schema", body.objectAt("response_format").string("type"))
    }

    @Test
    fun `a search kind ignores a reasoning budget the caller set`() {
        // Both search overrides in `_send` were unconditional, so a caller cannot talk a search into
        // thinking: `SearchOverviewUseCase` never got the chance in Flutter either.
        val body = OpenRouterChatRequests
            .build(
                AiRequest.SearchOverview,
                MODEL,
                AiRequestOptions(reasoning = ReasoningBudget.Visible(maxTokens = 9999)),
            )
            .body
            .objectAt("reasoning")

        assertNull(body["max_tokens"], "the overview's override is `enabled`, never `max_tokens`")
        assertFalse(body.boolean("enabled"))
    }

    @Test
    fun `no web search means no tool block at all`() {
        val body = OpenRouterChatRequests
            .build(
                AiRequest.Chat("q"),
                MODEL,
                AiRequestOptions.ChatWithWebSearch.copy(webSearch = WebSearchMode.Disabled),
            )
            .body

        assertNull(body["tools"])
        assertNull(body["tool_choice"])
        assertNull(body["max_tool_calls"])
    }

    @Test
    fun `the chat's web search carries the tuned ceilings and lets the model decide`() {
        val body = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions.ChatWithWebSearch)
            .body

        val tool = body.arrayAt("tools").single().jsonObject
        assertEquals("openrouter:web_search", tool.string("type"))
        val parameters = tool.objectAt("parameters")
        assertEquals("auto", parameters.string("engine"))
        assertEquals(5, parameters.int("max_results"))
        assertEquals(2, parameters.int("max_uses"))
        assertEquals(10, parameters.int("max_total_results"))
        assertEquals("low", parameters.string("search_context_size"))
        assertEquals("auto", body.string("tool_choice"), "automatic hands the choice to the model")
        assertEquals(2, body.int("max_tool_calls"))
    }

    @Test
    fun `a forced web search names the tool instead`() {
        val body = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions.Research)
            .body

        assertEquals("openrouter:web_search", body.objectAt("tool_choice").string("type"))
        assertEquals(1, body.int("max_tool_calls"))
        assertEquals(8192, body.int("max_tokens"))
    }

    @Test
    fun `unstated web search ceilings fall back to the Flutter defaults`() {
        // `OpenRouterRequestOptions` defaulted every ceiling to 0 and the context size to 'low', so a
        // web search that stated none still bounded itself. Omitting them would be an unbounded
        // search the Flutter build never made.
        val body = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions(webSearch = WebSearchMode.Automatic))
            .body

        val parameters = body.arrayAt("tools").single().jsonObject.objectAt("parameters")
        assertEquals(0, parameters.int("max_results"))
        assertEquals(0, parameters.int("max_uses"))
        assertEquals(0, parameters.int("max_total_results"))
        assertEquals("low", parameters.string("search_context_size"))
        assertEquals(0, body.int("max_tool_calls"))
    }

    @Test
    fun `streaming changes only the stream flag`() {
        val streaming = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions.ChatWithWebSearch, stream = true)
        val still = OpenRouterChatRequests
            .build(AiRequest.Chat("q"), MODEL, AiRequestOptions.ChatWithWebSearch)

        assertTrue(streaming.body.boolean("stream"))
        assertEquals(still.body["tools"], streaming.body["tools"])
        assertEquals(still.body["reasoning"], streaming.body["reasoning"])
        assertEquals(still.body["max_tokens"], streaming.body["max_tokens"])
    }

    @Test
    fun `the model id is both the request's and the body's`() {
        val request = OpenRouterChatRequests.build(AiRequest.Chat("q"), "anthropic/claude-sonnet-4")

        assertEquals("anthropic/claude-sonnet-4", request.model)
        assertEquals("anthropic/claude-sonnet-4", request.body.string("model"))
    }

    @Test
    fun `each request names its own prompt kind`() {
        assertEquals(PromptKind.Chat, AiRequest.Chat("q").kind)
        assertEquals(PromptKind.SearchOverview, AiRequest.SearchOverview.kind)
        assertEquals(PromptKind.SearchReferences, AiRequest.SearchReferences.kind)
    }

    @Test
    fun `the schema demands both keys and forbids anything else`() {
        val schema = bibleSearchResponseFormat().objectAt("json_schema").objectAt("schema")

        assertEquals(
            listOf("scriptures", "suggestedQuestions"),
            schema.arrayAt("required").strings(),
        )
        assertFalse(schema.boolean("additionalProperties"))
        assertFalse(
            schema.objectAt("properties").containsKey("overview"),
            "the references prompt has no overview field, so the schema must not ask for one",
        )
    }
}

private const val MODEL = "anthropic/claude-sonnet-4.5"

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double

private fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject

private fun JsonObject.arrayAt(key: String): JsonArray = getValue(key).jsonArray

/** A string array — `required`, whose order is the order the schema was written in. */
private fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }
