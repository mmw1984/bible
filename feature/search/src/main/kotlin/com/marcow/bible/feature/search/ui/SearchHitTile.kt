package com.marcow.bible.feature.search.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.designsystem.theme.scriptureStyle
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit

/**
 * One search result, mirroring `_SearchHitTile` in `legacy/flutter/lib/main.dart`.
 *
 * The reference is the tile's accessible name rather than its first line: Flutter's `AppTap`
 * `label:` sets `excludeSemantics`, so a screen reader announced "John 3:16" and read the verse as
 * part of that name rather than as separate content. The two halves of that live in the two places
 * Flutter had them — the name on the tap, the exclusion on the content — because `AppTap` puts its
 * own `clickable` *after* the modifier it is given, so a `clearAndSetSemantics` on that modifier
 * would be asking to drop the tap action as well as the text. Putting the label on the tap and
 * clearing the content leaves the tile announcing as "John 3:16, button" and still tappable, which
 * is what `Semantics(label: …, excludeSemantics: true, button: true)` announced.
 *
 * [reason] is the AI half's extra line, the model's own words for why it proposed the verse. It is
 * empty for a text search, where Flutter drew no third line at all.
 */
@Composable
internal fun SearchHitTile(
    hit: ScriptureHit,
    readingMode: ReadingMode,
    locale: AppLocale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String? = null,
) {
    val colors = appColors
    val reference = searchReferenceLabel(hit, readingMode, locale)
    val reasonLine = tileReasonLine(reason)

    AppTap(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = TILE_GAP)
            .semantics { contentDescription = reference },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BORDER_WIDTH, colors.line, RoundedCornerShape(appRadii.control))
                    .padding(TILE_PADDING)
                    .clearAndSetSemantics { },
            ) {
                Text(
                    text = reference,
                    color = colors.faint,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(REFERENCE_GAP))
                Text(
                    text = verseTextFor(hit, locale),
                    color = colors.ink,
                    style = scriptureStyle(size = 14.sp, lineHeight = VERSE_LINE_HEIGHT),
                )
                if (reasonLine != null) {
                    Spacer(Modifier.height(REASON_GAP))
                    Text(
                        text = reasonLine,
                        color = colors.muted,
                        fontSize = 11.sp,
                        lineHeight = REASON_LINE_HEIGHT,
                    )
                }
            }
        },
    )
}

/**
 * The `'${bookName} ${chapter}:${verse}'` heading of a tile.
 *
 * `_bookName` reads the reading mode before the UI language: an English or bilingual reader shows
 * English book names even when the app itself is in Chinese, so [readingMode] has to win over
 * [locale] rather than the other way round.
 */
internal fun searchReferenceLabel(hit: ScriptureHit, readingMode: ReadingMode, locale: AppLocale): String =
    "${bookNameFor(hit.book, readingMode, locale)} ${hit.chapter}:${hit.verse.number}"

/** `_bookName` in `legacy/flutter/lib/main.dart`, verbatim. */
internal fun bookNameFor(book: BibleBook, readingMode: ReadingMode, locale: AppLocale): String =
    if (readingMode != ReadingMode.CHINESE || locale == AppLocale.EN) book.nameEn else book.nameZh

/**
 * Which of the pair of translations a hit shows.
 *
 * `_usesEnglishUi(context) ? hit.verse.en : hit.verse.zh` — the *app* language decides, not the
 * reading mode, because the tile only ever showed one of the two and a bilingual reader was already
 * being shown the language of the interface it was opened from.
 */
internal fun verseTextFor(hit: ScriptureHit, locale: AppLocale): String =
    if (locale == AppLocale.EN) hit.verse.en else hit.verse.zh

/**
 * The AI half's third line for a tile, or null when the tile gets two lines instead.
 *
 * `if (reason?.isNotEmpty == true) …` in `legacy/flutter/lib/main.dart:2720`. Both of the halves that
 * reach this are real: a text search has no reason at all and passes null, and an AI hit whose model
 * omitted the field arrives as `''` rather than as nothing — `AiScriptureReference.reason` is a
 * non-null `String` read through `json.stringOrEmpty`, so a model that answers with the reference and
 * no explanation publishes an empty one. Either way Flutter drew no third line, and the 7 dp above it
 * with it, so the guard is on emptiness rather than on presence.
 *
 * Whitespace is not empty. `isNotEmpty` does not trim, so a reason of `" "` still gets its line and
 * still adds the gap — a visibly blank row under the verse. That is what Flutter did, and it is
 * reproduced rather than tidied because the tile is not where the tidying belongs: the prompt asks
 * for a reason on every reference, and a model that sends blank text is saying something.
 */
internal fun tileReasonLine(reason: String?): String? = reason?.takeIf(String::isNotEmpty)

/** `EdgeInsets.only(bottom: 6)` between two tiles. */
private val TILE_GAP = 6.dp

/** `EdgeInsets.all(14)` inside the tile's border. */
private val TILE_PADDING = 14.dp
private val BORDER_WIDTH = 1.dp

/** The gaps Flutter left around the reference and the verse, and around an AI reason. */
private val REFERENCE_GAP = 6.dp
private val REASON_GAP = 7.dp

/** `fontSize: 14, height: 1.65` and `fontSize: 11, height: 1.45` in the Flutter tile. */
private val VERSE_LINE_HEIGHT = 23.1.sp
private val REASON_LINE_HEIGHT = 16.sp
