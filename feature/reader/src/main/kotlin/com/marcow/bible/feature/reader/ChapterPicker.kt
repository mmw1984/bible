package com.marcow.bible.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.SpringCurve
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii

/*
 * Where the chapter picker sits, mirroring the anchor arithmetic in `_showChapterPicker`
 * (`legacy/flutter/lib/main.dart:1324`).
 *
 * Flutter positioned the bubble by hand rather than from an overlay anchor, so these four numbers
 * are the whole of it: the bubble hangs just under the chapter button, is as wide as it can be
 * without touching either edge, keeps a gap of 16 to 28 px to the window's right edge, and never
 * reaches past a third of the way down the screen. They are pure functions of the window and the
 * anchor because that is all they ever were, and because the Flutter tests could only reach them
 * through a rendered dialog, so none of them were covered.
 */

/** `math.min(330.0, size.width - 32)` — five columns of chapters wide, and never off-screen. */
fun chapterPickerWidth(windowWidth: Dp): Dp = minOf(PickerWidthCap, windowWidth - PickerWidthMargin)

/** `(size.width - anchorRect.right).clamp(16.0, 28.0)` — the gap to the window's right edge. */
fun chapterPickerRightMargin(anchorRight: Dp, windowWidth: Dp): Dp =
    (windowWidth - anchorRight).coerceIn(PickerMinRightMargin, PickerMaxRightMargin)

/** `math.min(anchorRect.bottom + 10, size.height * .36)` — under the button, never mid-screen. */
fun chapterPickerTop(anchorBottom: Dp, windowHeight: Dp): Dp =
    minOf(anchorBottom + PickerAnchorGap, windowHeight * PickerTopFraction)

/** `math.min(400.0, size.height - top - padding.bottom - 16)` — Psalms scrolls inside this. */
fun chapterPickerMaxHeight(top: Dp, windowHeight: Dp, bottomInset: Dp): Dp =
    minOf(PickerHeightCap, windowHeight - top - bottomInset - PickerHeightMargin)

/**
 * The chapter number the button draws: `chapter.toString().padLeft(2, '0')`, so it reads `02`.
 *
 * Flutter's `chapter-anchor` and its chapter grid pad differently, and the reader can see it. The
 * anchor is a single number in 23 px italic, so it is padded to keep the button from resizing as the
 * reader steps through a chapter — the test pinned it, asserting `02` inside the anchor after
 * choosing chapter two. The grid cells are `'$value'` with no padding, because fifty `02`-wide cells
 * in five columns would not fit the bubble's 330 px.
 */
fun chapterControlLabel(chapter: Int): String = chapter.toString().padStart(2, '0')

/** `'$value'`, unpadded — the picker cell at `legacy/flutter/lib/main.dart:1492`. */
fun chapterCellLabel(chapter: Int): String = chapter.toString()

/**
 * Whether the grid needs to scroll, from Flutter's `shrinkWrap` inside a `Flexible`.
 *
 * Flutter's grid took whatever height the header left it and scrolled only on overflow, which the
 * native grid has to be told instead: `userScrollEnabled = false` on an overfull grid swallows the
 * touch gestures of the verses behind it. Six rows of five is what fills [PickerHeightCap], so the
 * thirty chapters that fit scroll not at all and the longer books — most of the Pentateuch, the
 * Prophets, Psalms at 150 — do.
 */
fun chapterPickerScrolls(chapterCount: Int): Boolean =
    chapterCount > ChapterRowsInView * ChapterColumns

/**
 * The chapter picker, replacing `_ChapterPickerBubble` and the `showGeneralDialog` that opened it
 * (`legacy/flutter/lib/main.dart:1385`).
 *
 * [anchorBounds] is the chapter button's bounds **in window coordinates**, which is what Flutter
 * measured with `anchorBox.localToGlobal(Offset.zero)`. A null anchor — the button has not been
 * laid out yet, or has left the composition — draws nothing, which is the `anchorBox == null ||
 * !anchorBox.hasSize` guard at `main.dart:1327`.
 *
 * A [Popup] rather than a route, because the bubble is anchored to a control inside the reader and
 * a route would have to be pushed by a host that knows nothing about the reader's own layout. The
 * scrim, the dismissal and the entrance therefore belong to the popup, and the four functions above
 * place the bubble inside it.
 *
 * The window is taken from [LocalConfiguration] rather than from the reader's own bounds: on a
 * window wide enough for the sidebar the reader is 270 px narrower than the window, and the anchor
 * is measured in window coordinates, so the bubble has to be placed against the same frame.
 */
@Composable
fun ChapterPickerBubble(
    chapter: Int,
    chapterCount: Int,
    anchorBounds: IntRect?,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = anchorBounds ?: return
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidth = configuration.screenWidthDp.dp
    val windowHeight = configuration.screenHeightDp.dp
    val bottomInset = density.toDp(WindowInsets.navigationBars.getBottom(density))
    val width = chapterPickerWidth(windowWidth)
    val rightMargin = chapterPickerRightMargin(
        anchorRight = density.toDp(bounds.right),
        windowWidth = windowWidth,
    )
    val top = chapterPickerTop(
        anchorBottom = density.toDp(bounds.bottom),
        windowHeight = windowHeight,
    )
    val maxHeight = chapterPickerMaxHeight(top = top, windowHeight = windowHeight, bottomInset = bottomInset)
    val offset = with(density) {
        IntOffset(
            x = (windowWidth - rightMargin - width).roundToPx(),
            y = top.roundToPx(),
        )
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            // The scrim fills the window and consumes the tap that dismisses, so the platform's own
            // outside-click handling would never see one.
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScrimColor)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            ChapterPickerContent(
                chapter = chapter,
                chapterCount = chapterCount,
                maxHeight = maxHeight,
                onChapterSelected = onChapterSelected,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { offset }
                    .width(width)
                    .heightIn(max = maxHeight),
            )
        }
    }
}

/**
 * The bubble itself: a header, the chapter grid, and the entrance Flutter's transition built.
 *
 * `FadeTransition(Interval(0, .65, easeOut))` over a `ScaleTransition` from `.82` at the top right
 * is reproduced with an [Animatable] rather than with `AnimatedVisibility`, because a popup's
 * content is composed once and has no enter transition to hook into.
 *
 * The `BackdropFilter(blur 24)` behind the surface is the one thing not carried over: Compose has no
 * backdrop equivalent, and blurring the bubble would blur the chapters inside it. The `.82` surface
 * is left to do the work, the same way `AppControlSurface` leaves its tint to carry the frosted look
 * below API 31.
 */
@Composable
private fun ChapterPickerContent(
    chapter: Int,
    chapterCount: Int,
    maxHeight: Dp,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val shape = bubbleShape(appRadii.screen)
    // A preview and a golden render one frame, and `Animatable` anchors to the first frame it sees,
    // so a bubble that started at 0 would be captured at .82 scale and no alpha.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(progress, inspection) {
        if (inspection) return@LaunchedEffect
        progress.animateTo(1f, tween(durationMillis = EnterAnimationMillis, easing = SpringCurve))
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                // `ScaleTransition(alignment: Alignment.topRight, scale: .82 -> 1)`.
                val scale = EnterScale + (1f - EnterScale) * progress.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0f)
                alpha = progress.value
            }
            .clip(shape)
            .background(colors.surface.copy(alpha = BubbleSurfaceAlpha))
            .border(BorderStroke(BorderWidth, colors.line.copy(alpha = BubbleBorderAlpha)), shape)
            .padding(start = BubblePadding, top = BubblePadding, end = BubblePadding, bottom = BubbleBottomPadding),
    ) {
        ChapterPickerHeader(onDismiss = onDismiss)
        LazyVerticalGrid(
            columns = GridCells.Fixed(ChapterColumns),
            // `Flexible` + `shrinkWrap` in Flutter: the grid takes the height the header left it and
            // scrolls only when the book's chapter count does not fit, which is Psalms.
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(ChapterCellGap),
            verticalArrangement = Arrangement.spacedBy(ChapterCellGap),
            userScrollEnabled = chapterPickerScrolls(chapterCount),
        ) {
            items(count = chapterCount.coerceAtLeast(0), key = { index -> index }) { index ->
                ChapterPickerCell(
                    chapter = index + 1,
                    active = index + 1 == chapter,
                    onClick = { onChapterSelected(index + 1) },
                )
            }
        }
    }
}

/** `Padding(fromLTRB(6, 2, 3, 12))`, the 15 px semibold title and the 32 px close control. */
@Composable
private fun ChapterPickerHeader(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    Row(
        modifier = modifier.padding(start = 6.dp, top = 2.dp, end = 3.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.select_chapter),
            color = colors.ink,
            fontSize = HeaderSize,
            fontWeight = FontWeight.W600,
            modifier = Modifier.weight(1f),
        )
        // A `Container` on a `CircleBorder` in Flutter rather than an `AppControlSurface`: the
        // control is part of the bubble, not one of the frosted controls the app draws over content.
        AppTap(onClick = onDismiss) {
            Box(
                modifier = Modifier
                    .size(CloseSize)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised.copy(alpha = CloseFillAlpha))
                    .border(BorderStroke(BorderWidth, colors.line), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphView(
                    glyph = AppGlyph.CLOSE,
                    color = colors.ink,
                    size = CloseGlyphSize,
                    contentDescription = stringResource(R.string.close_chapter_picker),
                )
            }
        }
    }
}

/** One chapter: inverted when it is the one being read, with the Flutter colours exactly. */
@Composable
private fun ChapterPickerCell(chapter: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val shape = RoundedCornerShape(ChapterCellRadius)
    val label = stringResource(R.string.chapter_number, chapter)
    val number = chapterCellLabel(chapter)
    AppTap(
        onClick = onClick,
        selected = active,
        inMutuallyExclusiveGroup = true,
        modifier = modifier
            .height(ChapterCellHeight)
            .clip(shape)
            .background(if (active) colors.ink else colors.surfaceRaised.copy(alpha = ChapterFillAlpha))
            .border(BorderStroke(BorderWidth, if (active) colors.ink else colors.line), shape)
            // `AppTap(label: context.l10n.chapterNumber(value))`: the number is what is drawn, the
            // chapter is what is read out, because a bare "5" tells a screen reader nothing.
            .semantics { contentDescription = label },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = number,
                textAlign = TextAlign.Center,
                color = if (active) colors.canvas else colors.ink,
                fontSize = ChapterLabelSize,
                // `height: 1` — the number sits on one line, however wide the cell turns out.
                lineHeight = ChapterLabelSize,
                fontWeight = if (active) FontWeight.W600 else FontWeight.W400,
            )
        }
    }
}

/**
 * The chapter button the picker hangs from, replacing the `chapter-anchor` control in the reader's
 * header (`legacy/flutter/lib/main.dart:1187`).
 *
 * It measures itself and nothing else: the bounds are reported through [onAnchorChanged] so the
 * screen can hand them to [ChapterPickerBubble], which is the piece that turns them into a position.
 * The `AnimatedSwitcher` Flutter put around the number is not reproduced — a chapter number changes
 * only when the reader has already navigated, and the header's own fade covers it.
 */
@Composable
fun ChapterControl(
    chapter: Int,
    onClick: () -> Unit,
    onAnchorChanged: (IntRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val label = chapterLabel(chapter)
    AppControlSurface(
        modifier = modifier.onGloballyPositioned { onAnchorChanged(it.boundsInWindow()) },
        color = colors.surfaceRaised.copy(alpha = ChapterControlFillAlpha),
    ) {
        AppTap(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(
                text = chapterControlLabel(chapter),
                textAlign = TextAlign.Center,
                color = colors.faint,
                fontFamily = AppFonts.Exposure,
                fontSize = ReaderChrome.chapterNumberSize,
                lineHeight = ReaderChrome.chapterNumberSize * ReaderChrome.chapterNumberLineHeight,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(
                    horizontal = ReaderChrome.chapterControlHorizontal,
                    vertical = ReaderChrome.chapterControlVertical,
                ),
            )
        }
    }
}

/** `context.l10n.selectChapterCurrent(chapter)`, the button's accessibility label. */
@Composable
private fun chapterLabel(chapter: Int): String = stringResource(R.string.select_chapter_current, chapter)

/** 320 ms on `springCurve`, the `_showChapterPicker` transition duration. */
private const val EnterAnimationMillis = 320

/** `.82`, the scale the bubble enters at. */
private const val EnterScale = 0.82f

/** `Colors.black.withValues(alpha: .16)`, the chapter picker's barrier. */
private val ScrimColor = Color(0x29000000)

/** Every border Flutter drew was 1 px. */
private val BorderWidth = 1.dp

private val PickerWidthCap = 330.dp
private val PickerWidthMargin = 32.dp
private val PickerMinRightMargin = 16.dp
private val PickerMaxRightMargin = 28.dp
private val PickerAnchorGap = 10.dp
private val PickerTopFraction = 0.36f
private val PickerHeightCap = 400.dp
private val PickerHeightMargin = 16.dp

/** `colors.surface.withValues(alpha: .82)` and `colors.line.withValues(alpha: .84)`. */
private const val BubbleSurfaceAlpha = 0.82f
private const val BubbleBorderAlpha = 0.84f

private val BubblePadding = 14.dp
private val BubbleBottomPadding = 16.dp

/** `Icons.close` at 16 px inside a 32 px circle. */
private val CloseSize = 32.dp
private val CloseGlyphSize = 16.dp
private const val CloseFillAlpha = 0.55f

private const val ChapterColumns = 5

/**
 * How many chapters the bubble shows before it scrolls.
 *
 * Flutter did not have this number: its grid was `shrinkWrap`ed inside a `Flexible`, so it took
 * whatever height was left and scrolled only when the chapter count overflowed that. The native
 * grid needs to be told, because `userScrollEnabled = false` on an overfull grid swallows the touch
 * gestures of the verses behind it — and six rows is the count that fills the 400 px cap.
 */
private const val ChapterRowsInView = 6

private val ChapterCellHeight = 46.dp
private val ChapterCellRadius = 12.dp
private val ChapterCellGap = 7.dp
private val ChapterLabelSize = 13.sp
private const val ChapterFillAlpha = 0.55f
private const val ChapterControlFillAlpha = 0.5f

private val HeaderSize = 15.sp

/** The bubble's shape, spelled out once so the clip and the border agree on it. */
private fun bubbleShape(radius: Dp): RoundedCornerShape = RoundedCornerShape(radius)
