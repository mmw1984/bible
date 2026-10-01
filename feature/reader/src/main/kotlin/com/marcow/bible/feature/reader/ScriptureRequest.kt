package com.marcow.bible.feature.reader

import com.marcow.bible.core.model.VersePair

/**
 * The two things a long-pressed verse can be sent to, mirroring the two `_openAiChat` calls
 * `_openVerseActions` made in `legacy/flutter/lib/main.dart:923`.
 */
enum class VerseAction {
    ASK_AI,
    EXPLAIN,
    ;

    /** Whether this action opens with a question already asked, which only Explain did. */
    val carriesQuestion: Boolean
        get() = this == EXPLAIN
}

/**
 * What a long press on a verse hands to the Ask tab, mirroring the four arguments Flutter's
 * `_openAiChat` took at `legacy/flutter/lib/main.dart:969`.
 *
 * Built here rather than in the screen because all four are text: three are composed out of the book,
 * the chapter and the verse, and the fourth is a localised string. Keeping them out of the
 * composable is what makes them checkable.
 */
data class ScriptureRequest(
    /** `"Genesis 1:1"` — the name the reading mode and the interface language chose. */
    val reference: String,
    /** The verse on its own, which is what the clipboard gets and what the model is shown. */
    val text: String,
    /** The whole chapter, the context the model reads alongside the question. */
    val chapterContext: String,
    /** The opening question, which only Explain had; null when the reader asked nothing in advance. */
    val question: String? = null,
)

/** `'$bookName $chapter:$verse'`, the reference at the top of the copied text in Flutter. */
fun scriptureReference(bookName: String, chapter: Int, verseNumber: Int): String = "$bookName $chapter:$verseNumber"

/**
 * The text the clipboard and the AI attachment are both given, mirroring
 * `'$reference\n${verse.zh}\n${verse.en}'`.
 *
 * Both translations whatever the reading mode is, because this is not what is on screen: a Chinese
 * reader copying an English verse still wanted the pairing, which is what Flutter sent.
 */
fun verseSelectionText(reference: String, verse: VersePair): String = "$reference\n${verse.zh}\n${verse.en}"

/**
 * The chapter as the model is given it, one blank line between verses.
 *
 * The Chinese book name and the `中文：` / `English:` labels are Flutter's, and they stay in Chinese
 * for an English reading on purpose: this is context for a model, not an interface, and Flutter
 * reached for `book.zh` here even when the reader was reading English.
 */
fun chapterContextText(bookNameZh: String, chapter: Int, verses: List<VersePair>): String =
    verses.joinToString(VERSE_SEPARATOR) { verse ->
        "$bookNameZh $chapter:${verse.number}\n$ZH_LABEL${verse.zh}\n$EN_LABEL${verse.en}"
    }

/**
 * The request for [action], which is the same three pieces of text either way.
 *
 * Only [explainQuestion] separates Ask AI from Explain, and it is only carried when there is one:
 * Flutter's Explain filled in `initialQuestion` and sent it on, while Ask left it null and showed the
 * reader an empty composer.
 */
fun scriptureRequest(
    action: VerseAction,
    reference: String,
    verse: VersePair,
    chapterContext: String,
    explainQuestion: String,
): ScriptureRequest = ScriptureRequest(
    reference = reference,
    text = verseSelectionText(reference, verse),
    chapterContext = chapterContext,
    question = explainQuestion.takeIf { action.carriesQuestion },
)

/** The blank line between two verses in the chapter context, `join('\n\n')` in Flutter. */
private const val VERSE_SEPARATOR = "\n\n"

private const val ZH_LABEL = "中文："
private const val EN_LABEL = "English: "
