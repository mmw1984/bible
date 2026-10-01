package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `get_scripture` as `_runScriptureTool` and `_jsonObject` behaved in `legacy/flutter/lib/ai_service.dart`.
 *
 * The JSON cases matter more than they look: the model is asked for one request shape in the prompt
 * but is a language model, and every way it can answer "nearly" has a behaviour to preserve. The
 * error strings are asserted literally because they are what the model reads when the app cannot
 * help, and a reworded error is a differently-tuned model.
 */
internal class ScriptureToolRunnerTest {
    private val bibleRepository = mockk<BibleRepository>()
    private val runner = ScriptureToolRunner(bibleRepository)

    @Test
    fun `the request is cut from the first brace to the last, so prose around it still resolves`() {
        val answer = """
            Here is the passage you asked for.
            {"tool":"get_scripture","bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":17}
        """.trimIndent()

        assertEquals(
            ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 16, verseEnd = 17),
            scriptureToolRequest(answer),
        )
    }

    @Test
    fun `text without braces is not a request`() {
        assertNull(scriptureToolRequest("John 3:16 is about God's love for the world."))
    }

    @Test
    fun `a brace in the wrong order is not a request`() {
        assertNull(scriptureToolRequest("} then {"))
    }

    @Test
    fun `brackets around the object do not matter, because only the braces are read`() {
        assertEquals(
            ScriptureToolRequest(bookId = "JHN", chapter = 1, verseStart = 1, verseEnd = null),
            scriptureToolRequest("""[{"tool":"get_scripture","bookId":"JHN"}]"""),
        )
    }

    @Test
    fun `undecodable json is not a request rather than a throw`() {
        assertNull(scriptureToolRequest("""{"tool":"get_scripture", "bookId":}"""))
    }

    @Test
    fun `another tool is not a request`() {
        assertNull(scriptureToolRequest("""{"tool":"web_search","query":"love of God"}"""))
    }

    @Test
    fun `bookId is upper-cased so a lowercase answer still resolves`() {
        val request = scriptureToolRequest("""{"tool":"get_scripture","bookId":"jhn"}""")

        assertEquals("JHN", request?.bookId)
    }

    @Test
    fun `missing fields fall back to chapter 1 verse 1 and an open-ended verse`() {
        val request = scriptureToolRequest("""{"tool":"get_scripture","bookId":"JHN"}""")

        assertEquals(ScriptureToolRequest(bookId = "JHN", chapter = 1, verseStart = 1, verseEnd = null), request)
    }

    @Test
    fun `a string where a number belongs is a missing field, not a crash`() {
        val request = scriptureToolRequest(
            """{"tool":"get_scripture","bookId":"JHN","chapter":"three","verseStart":2}""",
        )

        assertEquals(ScriptureToolRequest(bookId = "JHN", chapter = 1, verseStart = 2, verseEnd = null), request)
    }

    @Test
    fun `an unknown book is reported to the model as a tool error`() = runTest {
        coEvery { bibleRepository.book(any()) } returns null

        assertEquals("Tool error: unknown bookId XYZ", runner.run(ScriptureToolRequest(bookId = "XYZ")))
    }

    @Test
    fun `a chapter the book does not have is reported to the model`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN
        coEvery { bibleRepository.chapter(any(), any()) } returns emptyList()

        assertEquals(
            "Tool error: JHN has no chapter 90",
            runner.run(ScriptureToolRequest(bookId = "JHN", chapter = 90)),
        )
    }

    @Test
    fun `an inverted or zero range is reported to the model`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN

        assertEquals(
            "Tool error: invalid verse range 5-4",
            runner.run(ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 5, verseEnd = 4)),
        )
        assertEquals(
            "Tool error: invalid verse range 0-2",
            runner.run(ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 0, verseEnd = 2)),
        )
    }

    @Test
    fun `a range wider than the cap is refused, which is 100 verses not 99`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN

        assertEquals(
            "Tool error: invalid verse range 1-102",
            runner.run(ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 1, verseEnd = 102)),
        )
    }

    @Test
    fun `a range of exactly the cap is allowed`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN
        coEvery { bibleRepository.chapter("JHN", 3) } returns emptyList()

        val result = runner.run(ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 1, verseEnd = 101))

        assertEquals("", result)
    }

    @Test
    fun `the verses come back paired, labelled, and separated by a blank line`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN
        coEvery { bibleRepository.chapter("JHN", 3) } returns listOf(
            VersePair(number = 16, zh = "神愛世人", en = "For God so loved the world"),
            VersePair(number = 17, zh = "甚至將他的獨生子", en = "that he gave his only Son"),
            VersePair(number = 18, zh = "不在這裡", en = "not here"),
        )

        val result = runner.run(
            ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 16, verseEnd = 17),
        )

        assertEquals(
            "約翰福音 3:16\n中文：神愛世人\nEnglish: For God so loved the world\n\n" +
                "約翰福音 3:17\n中文：甚至將他的獨生子\nEnglish: that he gave his only Son",
            result,
        )
    }

    @Test
    fun `a missing verseEnd reads the single verse the model asked for`() = runTest {
        coEvery { bibleRepository.book("JHN") } returns JOHN
        coEvery { bibleRepository.chapter("JHN", 3) } returns listOf(
            VersePair(number = 16, zh = "神愛世人", en = "For God so loved the world"),
            VersePair(number = 17, zh = "甚至將他的獨生子", en = "that he gave his only Son"),
        )

        val result = runner.run(
            ScriptureToolRequest(bookId = "JHN", chapter = 3, verseStart = 16, verseEnd = null),
        )

        assertEquals("約翰福音 3:16\n中文：神愛世人\nEnglish: For God so loved the world", result)
    }

    @Test
    fun `the follow-up prompt resends every result so far, not just the newest`() {
        val prompt = toolFollowUpPrompt("BASE", listOf("first", "second"))

        assertEquals(
            "BASE\n\nThe app executed get_scripture. Use this authoritative result " +
                "to answer the user. If another passage is essential, you may issue " +
                "one more get_scripture JSON request.\n\nfirst\n\nsecond",
            prompt,
        )
    }

    @Test
    fun `the final prompt stops the asking and shows the results`() {
        val prompt = finalFollowUpPrompt("BASE", listOf("first", "second"))

        assertEquals(
            "BASE\n\nUse the authoritative tool results below and answer now. " +
                "Do not request another tool.\n\nfirst\n\nsecond",
            prompt,
        )
    }

    @Test
    fun `a final prompt with no results says the tool is unavailable, which is a different instruction`() {
        val prompt = finalFollowUpPrompt("BASE", emptyList())

        assertEquals(
            "BASE\n\nUse the authoritative tool results below and answer now. " +
                "Do not request another tool.\n\nThe scripture tool is unavailable. " +
                "Answer only from the authoritative chapter reference already supplied.",
            prompt,
        )
    }

    @Test
    fun `the loop is bounded at three requests`() {
        assertEquals(3, MAX_TOOL_ROUNDS)
    }

    private companion object {
        val JOHN = BibleBook(
            id = "JHN",
            ordinal = 43,
            nameZh = "約翰福音",
            nameEn = "John",
            chapters = 21,
            testament = Testament.NEW,
        )
    }
}
