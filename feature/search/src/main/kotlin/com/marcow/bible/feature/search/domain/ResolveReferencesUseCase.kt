package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.database.BibleRepository
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the model's references into the verses the tile list renders, mirroring `_resolveReferences`
 * in `legacy/flutter/lib/main.dart`.
 *
 * The lookup is what makes an AI reference trustworthy: the model supplies `JHN 3:16-17` and the text
 * the user reads comes out of the app's own database, which is why the references prompt forbids the
 * model from quoting verse text at all.
 *
 * The order of the three checks below is the Flutter order and each one is a different kind of
 * rejection. An unknown book is impossible after [parseSearchReferences] — the parser already dropped
 * it — but the check stays because this is also the resolution path a future caller would use, and
 * silently returning nothing for a book that is not there is worse than an explicit skip. An
 * out-of-range chapter cannot happen for the same reason and is kept for the same reason. What *does*
 * happen is a range that runs past the end of a chapter (`JHN 3:16-400`): the reference is valid, the
 * verses are not, and the answer is the verses that exist inside the range, because that is what the
 * Flutter build showed.
 */
@Singleton
internal class ResolveReferencesUseCase @Inject constructor(
    private val bibleRepository: BibleRepository,
) {
    /**
     * One [AiSearchHit] per verse each reference covers, references in the order the model gave them.
     *
     * Every failure here is a failure of the *verses* half, not of the request: [ReferenceFailure.VERSES]
     * is what the sheet shows for it, because a chapter that will not load is the device's problem
     * rather than the provider's.
     */
    suspend operator fun invoke(references: List<AiScriptureReference>): List<AiSearchHit> {
        val canon: Map<String, BibleBook> = bibleRepository.books().associateBy { it.id }
        val hits = mutableListOf<AiSearchHit>()
        for (reference in references) {
            val book = canon[reference.bookId] ?: continue
            if (reference.chapter < 1 || reference.chapter > book.chapters) continue
            val verses = bibleRepository.chapter(book.id, reference.chapter)
            verses
                .filter { it.number >= reference.verseStart && it.number <= reference.verseEnd }
                .forEach { verse ->
                    hits += AiSearchHit(
                        hit = ScriptureHit(book = book, chapter = reference.chapter, verse = verse),
                        reason = reference.reason,
                    )
                }
        }
        return hits
    }
}