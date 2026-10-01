package com.marcow.bible.feature.aichat.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `AiMessage.fromJson` / `toJson` from `legacy/flutter/lib/ai_service.dart:110`, on the lines a
 * transcript actually holds.
 *
 * The round trip is the behaviour that matters: a message is written when a turn completes and read
 * back the next time the app starts, so anything the writer omits has to be the same after the read
 * as it was before. Each case below is a field the Dart `??` chain defaulted, because those defaults
 * are what keep a transcript written by another build from throwing away the history around it.
 */
internal class AiMessageTest {
    @Test
    fun `a message survives the transcript round trip`() {
        val message = AiMessage(
            role = AiMessageRole.USER,
            text = "約翰福音三章十六節是怎樣回事？",
            scripture = "JHN 3:16",
            kind = AiMessageKind.EXPLANATION,
            reasoning = "先看上下文",
            webSearch = true,
            incomplete = false,
        )

        assertEquals(message, aiMessageFromJson(message.toJson()))
    }

    @Test
    fun `a message with no passage and no thinking round trips`() {
        val message = AiMessage(role = AiMessageRole.ASSISTANT, text = "答案")

        val json = message.toJson()
        assertFalse("scripture" in json, "an absent scripture is not a null one: $json")
        assertFalse("reasoning" in json, "an absent thinking channel is not an empty one: $json")
        assertEquals(message, aiMessageFromJson(json))
    }

    @Test
    fun `a line from a build that predates a field comes back with that field defaulted`() {
        val json = aiMessageFromJson(buildJsonObject { put("text", "只有文字") })

        assertEquals(AiMessageRole.ASSISTANT, json.role, "role defaulted to assistant")
        assertEquals(AiMessageKind.CHAT, json.kind, "kind defaulted to chat")
        assertEquals("", json.scripture)
        assertNull(json.reasoning)
        assertEquals(false, json.webSearch)
        assertEquals(false, json.incomplete)
    }

    @Test
    fun `reasoning is sanitized on the way in, not on the way out`() {
        // A transcript is written from text that was already cleaned, so this only bites on a file
        // written by a build that did not — and the model is told to keep these lines out, so one that
        // is on disk is not something the reader should be shown.
        val json = buildJsonObject {
            put("text", "答案")
            put("reasoning", "safety: safe\n想了一下")
        }

        assertEquals("想了一下", aiMessageFromJson(json).reasoning)
    }

    @Test
    fun `blank reasoning is dropped rather than kept as empty text`() {
        val json = buildJsonObject {
            put("text", "答案")
            put("reasoning", "   ")
        }

        assertNull(aiMessageFromJson(json).reasoning)
    }

    @Test
    fun `a role a future build wrote is kept as itself, and a kind is filed as a chat turn`() {
        val json = buildJsonObject {
            put("text", "答案")
            put("role", "tool")
            put("kind", "prayer")
        }

        val message = aiMessageFromJson(json)
        assertEquals(AiMessageRole.UNKNOWN, message.role)
        assertEquals(AiMessageKind.CHAT, message.kind)
        assertEquals("", message.role.promptPrefix, "and is not rendered as a speaker it is not")
    }

    @Test
    fun `a boolean that is not a boolean is false rather than a lost message`() {
        // Dart's `as bool?` would have thrown here. The line came off disk, possibly edited by hand,
        // and one bad field is not worth the message it belongs to.
        val json = buildJsonObject {
            put("text", "答案")
            put("webSearch", "yes")
        }

        assertFalse(aiMessageFromJson(json).webSearch)
    }

    @Test
    fun `each kind is filed under the section the memory document gave it`() {
        assertEquals("Important events and conversation", AiMessageKind.CHAT.memoryHeading)
        assertEquals("Explained scripture", AiMessageKind.EXPLANATION.memoryHeading)
        assertEquals("Search history and conclusions", AiMessageKind.SEARCH.memoryHeading)
    }

    @Test
    fun `the seeded document holds a section for every kind`() {
        // A turn filed under a heading the document has no section for would put its entry at the top
        // of the file, so the seed and the kinds are one thing and are checked as one.
        AiMessageKind.entries.forEach { kind ->
            assertTrue(
                MEMORY_DOCUMENT_SEED.contains("## ${kind.memoryHeading}"),
                "memory.md has no section for ${kind.storageValue}",
            )
        }
    }
}
