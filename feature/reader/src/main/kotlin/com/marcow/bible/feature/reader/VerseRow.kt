package com.marcow.bible.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextUnit
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.VersePair

/**
 * One verse, mirroring `_VerseRow` in `legacy/flutter/lib/main.dart:1522`.
 *
 * The verse number sits above the text rather than in a gutter beside it, on Flutter's own comment:
 * the number and the copy share a left edge, because the old desktop gutter made every verse look
 * indented against the chapter title.
 *
 * The text is selectable — Phase 2 asks for `SelectableText`, which in Compose is the
 * `SelectionContainer` the list puts around its items — and a long press opens the action sheet. The
 * two are kept apart deliberately: the long press is claimed by the row's own gesture so it never
 * reaches the selection, which would otherwise start a drag-select instead of the sheet.
 *
 * [verseSize] is the one measurement that is not a constant: Flutter resolved the Chinese size from
 * the *window* width (17 below 920, 20 above) rather than from the reader's own constraints, because
 * a window wide enough for the sidebar is also a window that gets the larger type.
 */
@Composable
fun VerseRow(
    verse: VersePair,
    mode: ReadingMode,
    verseSize: TextUnit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(verse.number) { detectTapGestures(onLongPress = { onLongPress() }) }
            // Flutter's 18 dp of padding inside the row and its 4 dp of margin below it, which is
            // where the gap between two verses came from.
            .padding(
                top = ReaderChrome.verseVerticalPadding,
                bottom = ReaderChrome.verseVerticalPadding + ReaderChrome.verseGap,
            ),
    ) {
        Text(
            text = verse.number.toString().padStart(NUMBER_PAD, '0'),
            color = colors.faint,
            fontSize = ReaderChrome.verseNumberSize,
        )
        Spacer(Modifier.height(ReaderChrome.verseNumberGap))
        if (mode.showsChinese) {
            Text(
                text = verse.zh,
                color = colors.ink,
                fontFamily = AppFonts.NotoSerifTC,
                fontSize = verseSize,
                lineHeight = verseSize * ReaderChrome.verseLineHeight,
            )
        }
        if (mode == ReadingMode.BILINGUAL) {
            Spacer(Modifier.height(ReaderChrome.verseEnglishGap))
        }
        if (mode.showsEnglish) {
            // The English line is the smaller half of a bilingual pair and the whole of an English
            // reading, which is why it has its own size and its own line height.
            val onlyEnglish = mode == ReadingMode.ENGLISH
            Text(
                text = verse.en,
                color = if (onlyEnglish) colors.ink else colors.muted,
                fontFamily = AppFonts.OpenRunde,
                fontSize = if (onlyEnglish) {
                    ReaderChrome.verseOnlyEnglishSize
                } else {
                    ReaderChrome.verseEnglishSize
                },
                lineHeight = if (onlyEnglish) {
                    ReaderChrome.verseOnlyEnglishSize * ReaderChrome.verseOnlyEnglishLineHeight
                } else {
                    ReaderChrome.verseEnglishSize * ReaderChrome.verseEnglishLineHeight
                },
            )
        }
    }
}

/** `padLeft(2, '0')`, so a chapter's verses read 01, 02 … the way Flutter numbered them. */
private const val NUMBER_PAD = 2
