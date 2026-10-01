package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.network.openrouter.ChatCompletionRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two request bodies, which are the only part of the search the wire ever sees.
 *
 * `_send` in `legacy/flutter/lib/openrouter_service.dart` assembled these field by field, so what is
 * worth pinning down is the field set itself: the overview asks for no schema and switches reasoning
 * off, the references ask for the `bible_search` schema and cap reasoning rather than disabling it,
 * and neither carries the web-search block the chat path will bring in Phase 4. A key that appears
 * here is a key the provider is told about, and the schema is the contract the references are parsed
 * against, so both are asserted whole rather than on the fields that happen to be interesting.
 */
class OpenRouterSearchRequestsTest {
    @Test
    fun `the overview request is one user message at the fixed temperature`() {
        val request = OpenRouterSearchRequests.overview(prompt = PROMPT, model = MODEL)

        assertEquals(MODEL, request.model)
        assertEquals(MODEL, request.body.string("model"))

        val message = request.body.arrayAt("messages").single().jsonObject
        assertEquals("user", message.string("role"))
        assertEquals(PROMPT, message.string("content"))
        assertEquals(0.2, request.body.double("temperature"))
        assertFalse(request.body.boolean("stream"))
    }

    @Test
    fun `the overview request disables reasoning and asks for no schema`() {
        val body = OpenRouterSearchRequests.overview(prompt = PROMPT, model = MODEL).body

        assertEquals(1200, body.int("max_tokens"))
        assertEquals(
            buildJsonObject {
                put("enabled", false)
                put("exclude", true)
            },
            body.objectAt("reasoning"),
        )
        assertFalse(body.containsKey("response_format"))
    }

    @Test
    fun `neither request carries the chat path's web search block`() {
        // Flutter reached `_send` with `webSearch: disabled`, so no `tools` / `tool_choice` /
        // `max_tool_calls` went on the wire for a search, and a search that asked for web results
        // would change both the answer and the bill.
        val bodies = listOf(overviewRequest().body, referencesRequest().body)

        bodies.forEach { body ->
            assertFalse(body.containsKey("tools"))
            assertFalse(body.containsKey("tool_choice"))
            assertFalse(body.containsKey("max_tool_calls"))
        }
    }

    @Test
    fun `the references request caps the reasoning rather than disabling it`() {
        val body = referencesRequest().body

        assertEquals(MODEL, body.string("model"))
        assertEquals(2400, body.int("max_tokens"))
        assertEquals(
            buildJsonObject {
                put("max_tokens", 32)
                put("exclude", true)
            },
            body.objectAt("reasoning"),
        )
    }

    @Test
    fun `the references request pins a strict bible_search schema`() {
        assertEquals("json_schema", referencesFormat().string("type"))

        val schema = referencesSchema()
        assertEquals("bible_search", schema.string("name"))
        assertTrue(schema.boolean("strict"))
        assertFalse(referencesShape().boolean("additionalProperties"))
    }

    @Test
    fun `the schema asks for exactly what the references parser reads`() {
        // `overview` is written only for the chat path's combined shape (`if (!structuredReferences)`),
        // so its absence here is what tells the model this answer is parsed for references alone.
        val shape = referencesShape()

        assertEquals(listOf("scriptures", "suggestedQuestions"), shape.stringsAt("required"))

        val properties = shape.propertiesAt()
        assertEquals(setOf("scriptures", "suggestedQuestions"), properties.keys)
        assertEquals(16, properties.objectAt("scriptures").int("maxItems"))
        assertEquals(3, properties.objectAt("suggestedQuestions").int("maxItems"))
    }

    @Test
    fun `a scripture entry has the five fields a reference is made of`() {
        val scripture = referencesShape().propertiesAt().objectAt("scriptures").objectAt("items")
        val fields = listOf("bookId", "chapter", "verseStart", "verseEnd", "reason")

        assertFalse(scripture.boolean("additionalProperties"))
        assertEquals(fields, scripture.stringsAt("required"))
        assertEquals(fields.toSet(), scripture.objectAt("properties").keys)
    }

    @Test
    fun `a suggested question is a string and nothing else`() {
        val questions = referencesShape().propertiesAt().objectAt("suggestedQuestions")

        assertEquals(listOf("string"), questions.arrayAt("items").map { it.jsonObject.string("type") })
    }
}

private const val PROMPT = "BIBLE_SEARCH_OVERVIEW\nYou write the AI Overview."
private const val MODEL = "anthropic/claude-sonnet-4.5"

private fun overviewRequest(): ChatCompletionRequest = OpenRouterSearchRequests.overview(PROMPT, MODEL)

private fun referencesRequest(): ChatCompletionRequest = OpenRouterSearchRequests.references(PROMPT, MODEL)

private fun referencesFormat(): JsonObject = referencesRequest().body.objectAt("response_format")

private fun referencesSchema(): JsonObject = referencesFormat().objectAt("json_schema")

private fun referencesShape(): JsonObject = referencesSchema().objectAt("schema")

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double

private fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject

private fun JsonObject.arrayAt(key: String): JsonArray = getValue(key).jsonArray

private fun JsonObject.propertiesAt(): JsonObject = objectAt("properties")

/** A string array — `required`, whose order is the order the schema was written in. */
private fun JsonObject.stringsAt(key: String): List<String> = arrayAt(key).map { it.jsonPrimitive.content }
