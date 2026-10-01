package com.marcow.bible.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextUnit
import androidx.compose.ui.unit.IntOffset
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
 *
 * Changing the reading mode animates rather than cutting, which is [VerseText]'s job and the reason
 * the verse number sits outside it: the number is the same before and after, so animating it would
 * only make it blur.
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
        VerseText(
            verse = verse,
            mode = mode,
            verseSize = verseSize,
        )
    }
}

/**
 * The verse's text in the reading mode it is being read in, and the change from one mode to another.
 *
 * Flutter put an `AnimatedSize` around an `AnimatedSwitcher` around the text — 240 ms to grow, 210 ms
 * to swap in and 140 ms to swap out, the new text fading up and sliding in from `Offset(.018, 0)` — so
 * that changing the mode did not snap every verse of a long chapter from one height to another at
 * once. [AnimatedContent] is the same pair: its `SizeTransform` is the `AnimatedSize`, the transitions
 * are the `AnimatedSwitcher`, and the outgoing text is kept by default just as the `Stack` of Flutter's
 * `layoutBuilder` kept it.
 *
 * The size is not clipped, because Flutter's was not (`clipBehavior: Clip.none`) and clipping it would
 * cut the descenders of the verse arriving. The text is top-aligned, which is the `Alignment.topLeft`
 * the same `layoutBuilder` stacked on.
 */
@Composable
private fun VerseText(verse: VersePair, mode: ReadingMode, verseSize: TextUnit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = mode,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val enter = fadeIn(tween(VerseSwitchEnterMillis, easing = EaseOutCubic)) +
                slideIn(tween(VerseSwitchEnterMillis, easing = EaseOutCubic)) { width ->
                    IntOffset((width * VerseSwitchSlideFraction).toInt(), 0)
                }
            val exit = fadeOut(tween(VerseSwitchExitMillis, easing = EaseOutCubic))
            val size = SizeTransform(clip = false, sizeAnimationSpec = tween(VerseSizeMillis, easing = EaseOutCubic))
            enter togetherWith exit using size
        },
        label = "verseText",
    ) { shown ->
        Column {
            if (shown.showsChinese) {
                Text(
                    text = verse.zh,
                    color = appColors.ink,
                    fontFamily = AppFonts.NotoSerifTC,
                    fontSize = verseSize,
                    lineHeight = verseSize * ReaderChrome.verseLineHeight,
                )
            }
            if (shown == ReadingMode.BILINGUAL) {
                Spacer(Modifier.height(ReaderChrome.verseEnglishGap))
            }
            if (shown.showsEnglish) {
                // The English line is the smaller half of a bilingual pair and the whole of an English
                // reading, which is why it has its own size and its own line height.
                val onlyEnglish = shown == ReadingMode.ENGLISH
                Text(
                    text = verse.en,
                    color = if (onlyEnglish) appColors.ink else appColors.muted,
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
}

/** `padLeft(2, '0')`, so a chapter's verses read 01, 02 … the way Flutter numbered them. */
private const val NUMBER_PAD = 2

/** 210 ms in, 140 ms out, both on `Curves.easeOutCubic`, from the `AnimatedSwitcher` around the text. */
private const val VerseSwitchEnterMillis = 210
private const val VerseSwitchExitMillis = 140

/** The `AnimatedSize` around it, which is what kept a long chapter from snapping to its new height. */
private const val VerseSizeMillis = 240

/** `Offset(.018, 0)`, the slide as a fraction of the text's own width. */
private const val VerseSwitchSlideFraction = 0.018f
