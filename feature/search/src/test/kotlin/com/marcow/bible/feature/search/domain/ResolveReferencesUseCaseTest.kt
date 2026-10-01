package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleDao
import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.database.BookEntity
import com.marcow.bible.core.database.ReadingProgressDao
import com.marcow.bible.core.database.ReadingProgressEntity
import com.marcow.bible.core.database.ScriptureSearchRow
import com.marcow.bible.core.database.VerseEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `_resolveReferences` in `legacy/flutter/lib/main.dart:2199`: the model's references becoming the
 * tiles the user can tap.
 *
 * This is the step that makes an AI reference trustworthy and nothing else does. The model supplies a
 * pointer — `JHN 3:16-17` — and the text on screen comes out of this device's own Bible database,
 * which is why the references prompt forbids the model from quoting verse text at all. A test of the
 * parse alone therefore never proves the verse shown is the verse the app holds, which is what these
 * pin down.
 *
 * The three rejections are each a different kind and are kept in that order, so they are asserted
 * separately: an unknown book, a chapter past the end of the book, and a range whose verses do not
 * exist. The last is the only one the model can legitimately get wrong and still show something —
 * `JHN 3:16-400` is a real reference to a real chapter, so the answer is the verses inside it.
 */
class ResolveReferencesUseCaseTest {
    private val repository = BibleRepository(FakeBibleDao(), FakeReadingProgressDao())

    @Test
    fun `a range becomes one tile per verse, in canon order`() {
        val hits = resolve(reference("JHN", 3, 16, 17))

        assertEquals(listOf(16, 17), hits.map { it.hit.verse.number })
        assertEquals(listOf(3, 3), hits.map { it.hit.chapter })
        assertEquals(listOf(JOHN.id), hits.map { it.hit.book.id })
    }

    @Test
    fun `the text on the tile is this device's verse, not the model's`() {
        // The reference carries no text at all — only a reason — so every word the user reads came out
        // of the lookup, and `reason` is the only thing the model was allowed to contribute.
        val hits = resolve(reference("JHN", 3, 16, 16, reason = "耶穌降生"))

        assertEquals(VERSE_16_ZH, hits.single().hit.verse.zh)
        assertEquals(VERSE_16_EN, hits.single().hit.verse.en)
        assertEquals("耶穌降生", hits.single().reason)
    }

    @Test
    fun `the model's reason rides on every verse of a range`() {
        // `JHN 3:16-17` draws two tiles, and Flutter gave both the one reason from the reference
        // rather than the first tile the reason and the rest none.
        val hits = resolve(reference("JHN", 3, 16, 17, reason = "神的愛"))

        assertEquals(listOf("神的愛", "神的愛"), hits.map { it.reason })
    }

    @Test
    fun `a range running past the end of the chapter shows the verses that exist`() {
        // The reference is valid and the verses are not: `JHN 3` has 36 verses. Dropping the whole
        // reference would cost the user a hit the Flutter build showed, so it is clamped instead.
        val hits = resolve(reference("JHN", 3, 35, 400))

        assertEquals(listOf(35, 36), hits.map { it.hit.verse.number })
    }

    @Test
    fun `a range past the end of the chapter is not padded with empty verses`() {
        val hits = resolve(reference("JHN", 3, 30, 400))

        assertEquals(listOf(30, 31, 32, 33, 34, 35, 36), hits.map { it.hit.verse.number })
    }

    @Test
    fun `references keep the order the model gave them`() {
        val hits = resolve(
            reference("JHN", 3, 16, 16),
            reference("MAT", 4, 1, 1),
            reference("JHN", 4, 24, 24),
        )

        assertEquals(
            listOf("JHN 3:16", "MAT 4:1", "JHN 4:24"),
            hits.map { "${it.hit.book.id} ${it.hit.chapter}:${it.hit.verse.number}" },
        )
    }

    @Test
    fun `an unknown book loses its row and not the others`() {
        // Unreachable through the search path — [parseSearchReferences] already drops it against the
        // same canon — but it is the one failure that is silently wrong rather than loud, so the skip
        // is pinned rather than left to be discovered.
        val hits = resolve(
            reference("PTT", 5, 99, 99),
            reference("JHN", 3, 16, 16),
        )

        assertEquals(listOf("JHN 3:16"), hits.map { "${it.hit.book.id} ${it.hit.chapter}:${it.hit.verse.number}" })
    }

    @Test
    fun `a chapter past the end of the book is skipped`() {
        val hits = resolve(
            reference("JHN", 22, 1, 1),
            reference("JHN", 21, 1, 1),
        )

        assertEquals(listOf(21), hits.map { it.hit.chapter })
    }

    @Test
    fun `a chapter below one is skipped rather than read`() {
        val hits = resolve(reference("JHN", 0, 1, 1))

        assertEquals(emptyList<AiSearchHit>(), hits)
    }

    @Test
    fun `a chapter with no verses of its own contributes nothing rather than failing`() {
        // `chapter` returns an empty list for a chapter the table has no rows for, where the Flutter
        // reader threw. A model asking for one should cost the search nothing.
        val hits = resolve(reference("JHN", 7, 1, 5))

        assertEquals(emptyList<AiSearchHit>(), hits)
    }

    @Test
    fun `a chapter that will not load fails the verses half and not the request`() {
        // `ReferenceFailure.VERSES` is exactly this: a device problem, so the sheet says the verses
        // could not be loaded rather than sending the user to check a network that was fine.
        val failing = BibleRepository(FakeBibleDao(CHAPTER_FAILURE), FakeReadingProgressDao())

        val failure = assertThrows<IllegalStateException> {
            ResolveReferencesUseCase(failing).invoke(listOf(reference("JHN", 3, 16, 16)))
        }

        assertEquals(CHAPTER_FAILURE.message, failure.message)
    }

    @Test
    fun `nothing the model asked for resolves to nothing rather than to a failure`() {
        assertEquals(emptyList<AiSearchHit>(), resolve())
    }

    private suspend fun resolve(vararg references: AiScriptureReference): List<AiSearchHit> =
        ResolveReferencesUseCase(repository).invoke(references.toList())

    private fun reference(bookId: String, chapter: Int, verseStart: Int, verseEnd: Int, reason: String = "") =
        AiScriptureReference(
            bookId = bookId,
            chapter = chapter,
            verseStart = verseStart,
            verseEnd = verseEnd,
            reason = reason,
        )
}

private val GENESIS = BookEntity("GEN", 1, "創世記", "Genesis", 50, 0)

private val JOHN = BookEntity("JHN", 43, "約翰福音", "John", 21, 1)

private val MATTHEW = BookEntity("MAT", 40, "馬太福音", "Matthew", 28, 1)

/** John 3 has 36 verses; 3:16 and 3:17 are the ones the prompts use as their example. */
private const val VERSE_16_ZH = "神愛世人，甚至將他的獨生子賜給他們。"

private const val VERSE_16_EN = "For God so loved the world that he gave his only Son."

/** A chapter lookup that throws, which is what makes the sheet report `ReferenceFailure.VERSES`. */
private val CHAPTER_FAILURE = IllegalStateException("no such column: text_cuv")

private fun verse(bookId: String, chapter: Int, number: Int, zh: String = "", en: String = "") =
    VerseEntity(bookId = bookId, chapter = chapter, verse = number, textCuv = zh, textWeb = en)

/** John 3 has 36 verses, and 3:35-36 is how the clamping is tested: 400 is past the last of them. */
private fun johnThree() = listOf(
    verse("JHN", 3, 15, zh = "神愛世人"),
    verse("JHN", 3, 16, zh = VERSE_16_ZH, en = VERSE_16_EN),
    verse("JHN", 3, 17, zh = "神差他的兒來", en = "For God did not send his Son"),
    verse("JHN", 3, 35, zh = "父愛子", en = "The Father loves the Son"),
    verse("JHN", 3, 36, zh = "信從子的必有永生", en = "Whoever believes the Son has eternal life"),
    verse("JHN", 4, 24, zh = "敬拜父", en = "Worship the Father in spirit and truth"),
)

/** The books and verses the resolution reads; the search SQL itself is `BibleSearchTest`'s. */
private class FakeBibleDao(private val chapterFailure: Throwable? = null) : BibleDao {
    private val canon = listOf(GENESIS, MATTHEW, JOHN)

    private val rows = johnThree() +
        verse("MAT", 4, 1, zh = "耶穌被聖靈引到曠野", en = "Then Jesus was led by the Spirit")

    override suspend fun books(): List<BookEntity> = canon

    override suspend fun book(book: String): BookEntity? = canon.firstOrNull { it.id == book }

    override suspend fun chapter(book: String, chapter: Int): List<VerseEntity> {
        chapterFailure?.let { throw it }
        return rows.filter { it.bookId == book && it.chapter == chapter }.sortedBy { it.verse }
    }

    override suspend fun versesByTestament(testament: Int): List<VerseEntity> =
        rows.filter { verse -> canon.any { it.id == verse.bookId && it.testament == testament } }

    override suspend fun booksByTestament(testament: Int): List<BookEntity> = canon.filter { it.testament == testament }

    override suspend fun searchContains(pattern: String, limit: Int): List<ScriptureSearchRow> = emptyList()
}

private class FakeReadingProgressDao : ReadingProgressDao {
    override suspend fun progress(book: String): ReadingProgressEntity? = null

    override suspend fun allProgress(): List<ReadingProgressEntity> = emptyList()

    override suspend fun upsert(progress: ReadingProgressEntity) = Unit

    override suspend fun delete(book: String) = Unit
}
