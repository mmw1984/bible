package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The parse half of `_searchReferences`, which is what decides whether the model's answer becomes
 * tiles or becomes a failure panel.
 *
 * Two different verdicts live here and the difference is the point: a reference the canon does not
 * have loses its own row and leaves the other fifteen alone, because the prompt asks for up to
 * sixteen and one invented `PTT 5:99` should not cost the search; a payload that is not the shape
 * the schema promised fails the whole request, because then nothing in it can be trusted.
 */
class SearchReferencesTest {
    @Test
    fun `a reference the model got right survives, uppercased`() {
        val references = parse(
            """
            {"scriptures":[
              {"bookId":"jhn","chapter":3,"verseStart":16,"verseEnd":17,"reason":"耶穌降生"}],
             "suggestedQuestions":["神的愛從哪裡來？"]}
            """,
        )

        val reference = references.scriptures.single()
        assertEquals("JHN", reference.bookId)
        assertEquals(3, reference.chapter)
        assertEquals(16, reference.verseStart)
        assertEquals(17, reference.verseEnd)
        assertEquals("耶穌降生", reference.reason)
        assertEquals(listOf("神的愛從哪裡來？"), references.suggestedQuestions)
    }

    @Test
    fun `an invented book loses its row and not the others`() {
        val references = parse(
            """
            {"scriptures":[
              {"bookId":"PTT","chapter":5,"verseStart":99,"verseEnd":99,"reason":""},
              {"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":16,"reason":"耶穌降生"}],
             "suggestedQuestions":[]}
            """,
        )

        assertEquals(listOf("JHN"), references.scriptures.map { it.bookId })
    }

    @Test
    fun `a chapter past the end of the book is dropped, and the last chapter is not`() {
        val beyond = parse(reference("JHN", 22, 1, 1))
        val last = parse(reference("JHN", 21, 1, 1))

        assertEquals(0, beyond.scriptures.size)
        assertEquals(1, last.scriptures.size)
    }

    @Test
    fun `a reference that runs backwards or below verse one is dropped`() {
        assertEquals(0, parse(reference("JHN", 3, 17, 16)).scriptures.size)
        assertEquals(0, parse(reference("JHN", 3, 0, 16)).scriptures.size)
        assertEquals(0, parse(reference("JHN", 0, 1, 1)).scriptures.size)
    }

    @Test
    fun `an entry that is not an object is skipped rather than fatal`() {
        // `whereType<Map>()` in Dart: the strict schema makes this unlikely, and the schema also caps
        // the list, so skipping the stray entry costs nothing and keeps the real references.
        val references = parse(
            """
            {"scriptures":[
              "JHN 3:16",
              {"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":16,"reason":"耶穌降生"}],
             "suggestedQuestions":[]}
            """,
        )

        assertEquals(1, references.scriptures.size)
    }

    @Test
    fun `an answer with a sentence around the JSON is still read`() {
        val references = parse("Sure, here you go: ${reference("JHN", 3, 16, 16)} Hope that helps!")

        assertEquals(1, references.scriptures.size)
    }

    @Test
    fun `a payload the schema does not describe fails the whole request`() {
        // All of these threw `FormatException('AI scripture search returned invalid JSON.')` in Dart,
        // and that message is what the sheet's `references_failed` panel stands in for.
        val payloads = listOf(
            "not JSON at all",
            "{}",
            "[]",
            """{"scriptures":[]}""",
            """{"suggestedQuestions":[]}""",
            """{"scriptures":{},"suggestedQuestions":[]}""",
        )

        payloads.forEach { payload ->
            val failure = assertThrows<AiSearchFormatException> { parse(payload) }
            assertEquals(INVALID_REFERENCES_JSON_MESSAGE, failure.message)
        }
    }

    @Test
    fun `a fractional number truncates and a missing one falls back to one`() {
        val truncated = parse(
            """
            {"scriptures":[
              {"bookId":"JHN","chapter":3.7,"verseStart":16,"verseEnd":16,"reason":""}],
             "suggestedQuestions":[]}
            """,
        )

        assertEquals(3, truncated.scriptures.single().chapter)
        assertEquals(1, parse("""{"scriptures":[{"bookId":"JHN"}],"suggestedQuestions":[]}""").scriptures.size)
    }

    @Test
    fun `verseEnd falls back to verseStart, as fromJson's local default did`() {
        val reference = parse(
            """
            {"scriptures":[
              {"bookId":"JHN","chapter":3,"verseStart":16,"reason":"耶穌降生"}],
             "suggestedQuestions":[]}
            """,
        ).scriptures.single()

        assertEquals(16, reference.verseStart)
        assertEquals(16, reference.verseEnd)
    }

    @Test
    fun `a number written as a string fails rather than being read`() {
        // Dart threw a `TypeError` on a value that was neither a string nor null, and it propagated out
        // of `_searchReferences`; reading `"3"` as a chapter would let a malformed answer resolve to a
        // real verse and show it as if the app had asked for it.
        val payloads = listOf(
            """{"scriptures":[{"bookId":"JHN","chapter":"3"}],"suggestedQuestions":[]}""",
            """{"scriptures":[{"bookId":3,"chapter":3}],"suggestedQuestions":[]}""",
        )

        payloads.forEach { payload ->
            val failure = assertThrows<AiSearchFormatException> { parse(payload) }
            assertEquals(INVALID_REFERENCES_JSON_MESSAGE, failure.message)
        }
    }

    @Test
    fun `suggested questions stop at three and skip anything that is not a string`() {
        val references = parse(
            """
            {"scriptures":[],
             "suggestedQuestions":["一","二","三","四",7,"五"]}
            """,
        )

        assertEquals(listOf("一", "二", "三"), references.suggestedQuestions)
    }
}

private val CANON = listOf(
    BibleBook(
        id = "GEN",
        ordinal = 1,
        nameZh = "創世記",
        nameEn = "Genesis",
        chapters = 50,
        testament = Testament.OLD,
    ),
    BibleBook(
        id = "JHN",
        ordinal = 43,
        nameZh = "約翰福音",
        nameEn = "John",
        chapters = 21,
        testament = Testament.NEW,
    ),
)

private fun parse(raw: String): AiSearchReferences = parseSearchReferences(raw, CANON)

/** One reference on its own, as a payload with an empty question list around it. */
private fun reference(bookId: String, chapter: Int, verseStart: Int, verseEnd: Int): String =
    """{"scriptures":[${referenceEntry(bookId, chapter, verseStart, verseEnd)}],"suggestedQuestions":[]}"""

/** The one `scriptures` entry on its own, so the wrapper above reads as the payload it is. */
private fun referenceEntry(bookId: String, chapter: Int, verseStart: Int, verseEnd: Int): String =
    """{"bookId":"$bookId","chapter":$chapter,"verseStart":$verseStart,"verseEnd":$verseEnd,"reason":""}"""
