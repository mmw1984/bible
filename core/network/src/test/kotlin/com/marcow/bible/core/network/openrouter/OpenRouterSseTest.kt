package com.marcow.bible.core.network.openrouter

import com.marcow.bible.core.network.ai.ChatEvent
import com.marcow.bible.core.network.ai.FinishReason
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `generateStream` in `legacy/flutter/lib/openrouter_service.dart:286`, with the socket replaced by
 * the list of lines the response body would have been read into.
 *
 * This is the only server-sent-events handling in the app and until now nothing pinned it, which left
 * three decisions of `_generateStream` resting on nothing but a reading of the Dart:
 *
 *  - the **reasoning ladder**, which is the whole reason the excluded-reasoning block has to be on
 *    the wire. Four different spellings of the same thinking are tried in a fixed order and a fifth
 *    reads it out of the content parts, so an off-by-one in that ladder is invisible in a passing
 *    chat and shows up as a thinking block that is empty for one family of models.
 *  - **which frames are not answers**. `event:`, `:` keep-alives, provider metadata carrying no
 *    `choices`, and a frame that is not an object at all all have to pass by without ending the
 *    stream, and the last of those is the difference between a model that reasons quietly and a
 *    stream that dies mid-answer.
 *  - **`[DONE]` and the empty body**. `[DONE]` carries nothing to parse, so it ends nothing, and a
 *    body that carried no content at all is `OpenRouter returned no response.` rather than an empty
 *    answer — the check the Flutter build made once the body was exhausted, and the one that keeps a
 *    silent provider from being shown as an empty message.
 *
 * The tests below are written as the frames arrive rather than as one batch, because the ordering is
 * load-bearing: a frame's finish reason is reported *before* its delta, and a frame carrying an error
 * fails the answer even after content has already arrived.
 */
class OpenRouterSseTest {
    @Test
    fun `content deltas arrive in order and concatenate into one answer`() = runTest {
        val events = eventsOf(
            frame(content = "神"),
            frame(content = "愛"),
            frame(content = "世人"),
        )

        assertEquals(
            listOf(
                ChatEvent.ContentDelta("神"),
                ChatEvent.ContentDelta("愛"),
                ChatEvent.ContentDelta("世人"),
            ),
            events,
        )
    }

    @Test
    fun `the finish reason is reported before the delta of the frame that carried it`() = runTest {
        // Dart read `finish_reason` off the choice before it read the delta, and `onFinishReason`
        // settled the incomplete flag before the last token was ever collected — so a frame that ends
        // an answer says why it ended first.
        val events = eventsOf(frame(content = "完", finishReason = "stop"))

        assertEquals(
            listOf(ChatEvent.Finished(FinishReason.STOP), ChatEvent.ContentDelta("完")),
            events,
        )
    }

    @Test
    fun `a token ceiling is a finished event that marks the answer incomplete`() = runTest {
        // The `[[MORE]]` case of `NATIVE_PLAN.md` §4.2: the provider stopped because it ran out of
        // budget, which is the one finish reason the chat flags to the reader.
        val events = eventsOf(frame(content = "未完", finishReason = "length"))

        assertEquals(FinishReason.LENGTH, (events.first() as ChatEvent.Finished).reason)
        assertTrue(FinishReason.LENGTH.incomplete, "a truncated answer is an incomplete one")
    }

    @Test
    fun `a reason this build has not heard of is still a finished event`() = runTest {
        // Dart called `onFinishReason` for *any* non-empty string, known or not, and a collector
        // treating an unknown reason as an error would turn a provider's new spelling into a failure.
        val events = eventsOf(frame(content = "答", finishReason = "some_new_reason"))

        assertEquals(ChatEvent.Finished(FinishReason.UNKNOWN), events.first())
    }

    @Test
    fun `an empty finish reason reports nothing at all`() = runTest {
        // `finishReason is String && finishReason.isNotEmpty`: every frame but the last carries `""`,
        // so a finished event per frame would be one per token.
        val events = eventsOf(
            frame(content = "一", finishReason = ""),
            frame(content = "二"),
        )

        assertEquals(listOf(ChatEvent.ContentDelta("一"), ChatEvent.ContentDelta("二")), events)
    }

    @Test
    fun `the three single reasoning keys are tried in Dart's order`() = runTest {
        // One frame carrying all three at once, so the assertion is about which key wins rather than
        // about which key is present.
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning":"一","reasoning_content":"二","analysis":"三"}""",
            ),
        )

        assertEquals(
            listOf(ChatEvent.ReasoningDelta("一"), ChatEvent.ContentDelta("答")),
            events,
        )
    }

    @Test
    fun `a frame with no reasoning key falls through to the next spelling`() = runTest {
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning":"","reasoning_content":"二","analysis":"三"}""",
            ),
        )

        assertEquals(ChatEvent.ReasoningDelta("二"), events.first())
    }

    @Test
    fun `analysis is the last of the three single keys`() = runTest {
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning":"","reasoning_content":"","analysis":"三"}""",
            ),
        )

        assertEquals(ChatEvent.ReasoningDelta("三"), events.first())
    }

    @Test
    fun `reasoning_details answers for a model that nests its thinking`() = runTest {
        // Past the three single keys, `reasoning_details` is read as one value — which is why it is
        // passed to the same reader rather than to a key lookup: providers put a list or an object
        // there and the ladder has to flatten whatever shape arrives.
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning_details":[{"type":"reasoning.text","text":"四"}]}""",
            ),
        )

        assertEquals(ChatEvent.ReasoningDelta("四"), events.first())
    }

    @Test
    fun `a reasoning object is read through the keys that have held the text`() = runTest {
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning_details":{"summary":"五","text":"六"}}""",
            ),
        )

        // `text` is tried before `summary` in Dart's list, so the text is what shows.
        assertEquals(ChatEvent.ReasoningDelta("六"), events.first())
    }

    @Test
    fun `a reasoning list is flattened rather than taken whole`() = runTest {
        val events = eventsOf(
            frame(
                content = "答",
                reasoning = """{"reasoning_details":["七","八"]}""",
            ),
        )

        assertEquals(ChatEvent.ReasoningDelta("七八"), events.first())
    }

    @Test
    fun `thinking inside the content parts is still thinking`() = runTest {
        // The last step of the ladder, and the reason the excluded-reasoning block has to be sent at
        // all: with reasoning off there is no `reasoning` key to read and the only thing left is a
        // content part whose type says it is thinking. Without this the thinking block would be empty
        // for exactly the models that stream their reasoning as parts.
        val events = eventsOf(
            frame(
                contentParts = """[{"type":"reasoning.text","text":"先想"},{"type":"text","text":"答"}]""",
            ),
        )

        // The thinking is reported as its own event *and* again as part of the answer, because
        // `_contentText` joined the `text` of every part without looking at its type. That is the
        // Flutter build's behaviour and it is reproduced rather than tidied: a fix belongs in the
        // Dart or in a deliberate decision about this port, not in a test that quietly differs.
        assertEquals(
            listOf(
                ChatEvent.ReasoningDelta("先想"),
                ChatEvent.ContentDelta("先想答"),
            ),
            events,
        )
    }

    @Test
    fun `an analysis part is read by its type the same way a reasoning one is`() = runTest {
        val events = eventsOf(
            frame(contentParts = """[{"type":"analysis","text":"九"},{"type":"text","text":"答"}]"""),
        )

        assertEquals(ChatEvent.ReasoningDelta("九"), events.first())
    }

    @Test
    fun `a number in a reasoning key is not text`() = runTest {
        // `_reasoningValue`'s catch-all returned `''` for anything that was not a string, list or map.
        // Reading the digits instead would put a token count where a sentence belongs.
        val events = eventsOf(frame(content = "答", reasoning = """{"reasoning":128}"""))

        assertEquals(listOf(ChatEvent.ContentDelta("答")), events)
    }

    @Test
    fun `frames that are not content are skipped without ending the answer`() = runTest {
        // The three shapes the format itself produces: an `event:` line, a `:` keep-alive, and
        // `data: [DONE]`. None starts with a frame's payload, and Dart's `continue` past all three
        // meant the stream carried on to the next line rather than finishing.
        val events = eventsOf(
            "event: message",
            ": keep-alive",
            "data: ",
            frame(content = "答"),
            "data: [DONE]",
        )

        assertEquals(listOf(ChatEvent.ContentDelta("答")), events)
    }

    @Test
    fun `a frame of some other shape is not an answer`() = runTest {
        // `jsonDecode(data)` followed by `decoded is! Map<String, dynamic> → continue`. Several models
        // send a bare array or a bare string on a frame the reader has to pass by.
        val events = eventsOf("data: [1,2,3]", """data: "just a string"""", frame(content = "答"))

        assertEquals(listOf(ChatEvent.ContentDelta("答")), events)
    }

    @Test
    fun `a frame with no choices carries provider metadata, not an answer`() = runTest {
        // OpenRouter sends these for rate-limit headers and provider ids. Dart read
        // `decoded['choices'] as List?`, so a frame without the key contributed nothing at all.
        val events = eventsOf(
            """data: {"id":"gen-1","usage":{"total_tokens":42}}""",
            frame(content = "答"),
        )

        assertEquals(listOf(ChatEvent.ContentDelta("答")), events)
    }

    @Test
    fun `an error frame fails the answer with the provider's own message`() = runTest {
        val failure = runCatching {
            eventsOf(frame(error = """{"message":"Selected model exhausted its token budget"}"""))
        }.exceptionOrNull()

        assertEquals(
            "Selected model exhausted its token budget",
            assertInstanceOf(OpenRouterException.RequestFailed::class.java, failure).message,
        )
    }

    @Test
    fun `an error after content has arrived still fails, and the text so far is kept`() = runTest {
        // Dart threw there too, mid-stream. The chat's promise that the text so far stays on screen is
        // made by the caller holding the deltas it has already collected, so this is about the throw
        // happening at all rather than about anything being rolled back.
        val collected = mutableListOf<ChatEvent>()
        val failure = runCatching {
            openRouterChatEvents(
                listOf(frame(content = "半"), frame(error = """{"message":"provider fell over"}""")).asFlow(),
            ).collect { collected += it }
        }.exceptionOrNull()

        assertEquals(
            "provider fell over",
            assertInstanceOf(OpenRouterException.RequestFailed::class.java, failure).message,
        )
        assertEquals(listOf(ChatEvent.ContentDelta("半")), collected)
    }

    @Test
    fun `an error envelope is unwrapped the same way a completed call unwraps it`() = runTest {
        val failure = runCatching {
            eventsOf(frame(error = """{"error":{"message":"no free model"}}"""))
        }.exceptionOrNull()

        assertEquals(
            "no free model",
            assertInstanceOf(OpenRouterException.RequestFailed::class.java, failure).message,
        )
    }

    @Test
    fun `a body that carried no content is no response`() = runTest {
        // The check Dart made once the body was exhausted. It is the difference between a provider
        // that failed quietly and an answer the reader is shown as empty.
        val failure = runCatching { eventsOf("data: [DONE]") }.exceptionOrNull()

        assertEquals(
            EMPTY_RESPONSE_MESSAGE,
            assertInstanceOf(OpenRouterException.EmptyResponse::class.java, failure).message,
        )
    }

    @Test
    fun `a body of only thinking is still no response`() = runTest {
        // The flag tracks *content*, not events: a model that streams a long chain of thought and is
        // cut off before answering produced reasoning deltas and no answer, and Dart's
        // `receivedContent` was set by the content branch alone.
        val failure = runCatching {
            eventsOf(frame(reasoning = """{"reasoning":"想"}"""))
        }.exceptionOrNull()

        assertInstanceOf(OpenRouterException.EmptyResponse::class.java, failure)
    }

    @Test
    fun `the space after the prefix is trimmed before the frame is parsed`() = runTest {
        // `line.substring(5).trim()`: the format puts a space after `data:`, and `jsonDecode` of a
        // leading space is a parse failure rather than a frame.
        val events = eventsOf("data:   " + payload(content = "答"))

        assertEquals(listOf(ChatEvent.ContentDelta("答")), events)
    }

    @Test
    fun `only the first choice of a frame is read`() = runTest {
        // `choices?.firstOrNull`: a frame carrying several choices is the provider's `n` machinery, and
        // the app never asked for more than one.
        val events = eventsOf(
            """data: {"choices":[{"delta":{"content":"一"}},{"delta":{"content":"二"}}]}""",
        )

        assertEquals(listOf(ChatEvent.ContentDelta("一")), events)
    }

    private suspend fun eventsOf(vararg lines: String): List<ChatEvent> =
        openRouterChatEvents(lines.asFlow()).toList()

    /** One `data:` frame carrying a single choice, with only the parts a test names. */
    private fun frame(
        content: String? = null,
        contentParts: String? = null,
        reasoning: String? = null,
        finishReason: String? = null,
        error: String? = null,
    ): String = "data: " + payload(content, contentParts, reasoning, finishReason, error)

    /**
     * The JSON behind a frame, so a test can hang it off a differently spelled `data:` line.
     *
     * Every part is optional and the members are joined rather than concatenated, because a frame
     * carrying one reasoning key and no content still has to be a valid object — a trailing comma
     * would be a parse failure rather than the frame the test meant to describe.
     */
    private fun payload(
        content: String? = null,
        contentParts: String? = null,
        reasoning: String? = null,
        finishReason: String? = null,
        error: String? = null,
    ): String {
        if (error != null) return """{"error":$error}"""
        val delta = listOfNotNull(
            content?.let { """"content":${quote(it)}""" },
            contentParts?.let { """"content":$it""" },
            // The reasoning keys belong to the delta itself, so the braces they arrive wrapped in are
            // the ones this splices between.
            reasoning?.let { it.removePrefix("{").removeSuffix("}") },
        ).joinToString(",")
        val choice = listOfNotNull(
            """"delta":{$delta}""",
            finishReason?.let { """"finish_reason":${quote(it)}""" },
        ).joinToString(",")
        return """{"choices":[{ $choice }]}"""
    }

    /** Dart string interpolation of a frame's text fields, which are already valid JSON literals. */
    private fun quote(value: String) = "\"$value\""
}