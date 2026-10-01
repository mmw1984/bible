package com.marcow.bible.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextOverflow
import androidx.compose.ui.text.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppChoice
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppSegmented
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.VersePair

/**
 * The reader, replacing `_Reader` in `legacy/flutter/lib/main.dart:1063`.
 *
 * The three slivers Flutter built become one [LazyColumn] with three kinds of item — the header, one
 * per verse, and the chapter links — because a `LazyColumn` item is the same unit `scrollRatioFor`
 * counts. The header is item 0, so a ratio of 0 is the top of the chapter, which is the top of the
 * header.
 *
 * The list is keyed on the book and the chapter, which is Flutter's `ValueKey('${book.id}-$chapter')`
 * on its `CustomScrollView`: navigating starts a new chapter at its top, while changing the reading
 * mode or the interface language keeps the reader where they were. The key wraps the list *and* the
 * restore effect, so a fresh list and the instruction to scroll it are set up together.
 *
 * The window width, not the reader's own width, is what [readerLayout] is given: a window wide
 * enough for the sidebar is a window that also gets the larger type, and on such a window the reader
 * is 270 dp narrower than the frame the chapter picker anchors to.
 *
 * The top bar, the sidebar and the Ask and Devotion buttons are not here. They are the Flutter
 * shell's own `Stack` over the reader (`main.dart:638`), they know nothing about a chapter, and
 * drawing them from here would tie the reader to a host it is not part of. [bottomClearance] is how
 * the host tells the reader how much room its navigation bar is taking, for the same reason.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    navigator: ReaderNavigator,
    scroll: ReaderScrollSink,
    bottomClearance: Dp,
    onVerseAction: ((VerseAction, ScriptureRequest) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val book = state.book
    val verses = state.verses
    val bookName = book?.let { it.displayName(state.mode, state.usesEnglishUi) }.orEmpty()
    val hasPrevious = state.chapter > 1
    val hasNext = state.chapter < state.chapterCount
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val layout = readerLayout(screenWidth, topInset, bottomClearance)
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var actionVerse by remember { mutableStateOf<VersePair?>(null) }
    // A fresh gate per chapter, so its verses arrive one after another and the ones scrolled to later
    // do not: see `ScrollAwareEntrance` for why the question is asked this way round.
    //
    // A gate that starts closed is what a preview and a golden want. Nothing is playing a stagger
    // there — an `@Preview` and a Paparazzi snapshot each render one frame — and `Animatable`
    // anchors to the first frame it sees, so an open gate would leave every verse of the chapter at
    // `alpha = 0` and the preview would show a title above an empty page.
    val inspection = LocalInspectionMode.current
    val entrance = remember(verses) { EntranceGate(open = !inspection) }
    CloseEntrance(entrance)

    Box(modifier = modifier.background(colors.canvas)) {
        key(book?.id, state.chapter) {
            val listState = rememberLazyListState()
            val itemCount = verses.size + ListOverheadItems
            ReportScroll(listState = listState, itemCount = itemCount, scroll = scroll)
            RestoreScroll(listState = listState, itemCount = itemCount, target = state.scrollToRatio, scroll = scroll)

            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = HEADER_ITEM_KEY) {
                        ReaderHeader(
                            state = state,
                            title = bookName,
                            titleFamily = titleFamily(state),
                            layout = layout,
                            onModeSelected = navigator::selectMode,
                            onChapterClick = { pickerOpen = true },
                            onAnchorChanged = { anchorBounds = it },
                        )
                    }
                    items(count = verses.size, key = { index -> verses[index].number }) { index ->
                        ScrollAwareEntrance(staggerIndex = index, gate = entrance) {
                            VerseRow(
                                verse = verses[index],
                                mode = state.mode,
                                verseSize = layout.verseSize,
                                onLongPress = { actionVerse = verses[index] },
                            )
                        }
                    }
                    item(key = LINKS_ITEM_KEY) {
                        ChapterLinks(
                            previous = chapterLinkLabel(bookName, state.chapter - 1, hasPrevious),
                            next = chapterLinkLabel(bookName, state.chapter + 1, hasNext),
                            hasPrevious = hasPrevious,
                            hasNext = hasNext,
                            layout = layout,
                            onPrevious = navigator::selectPreviousChapter,
                            onNext = navigator::selectNextChapter,
                        )
                    }
                }
            }
        }

        if (pickerOpen) {
            ChapterPickerBubble(
                chapter = state.chapter,
                chapterCount = state.chapterCount,
                anchorBounds = anchorBounds,
                onChapterSelected = { chapter ->
                    pickerOpen = false
                    navigator.selectChapter(chapter)
                },
                onDismiss = { pickerOpen = false },
            )
        }
    }

    actionVerse?.let { verse ->
        VerseActionSheet(
            reference = scriptureReference(bookName, state.chapter, verse.number),
            verse = verse,
            chapterContext = chapterContextText(book?.nameZh.orEmpty(), state.chapter, verses),
            onAction = onVerseAction,
            onDismiss = { actionVerse = null },
        )
    }
}

/**
 * The header, the rule below it, and whichever of the skeleton and the failure message is showing.
 *
 * Flutter kept these in the same `SliverList` as the title rather than in their own sliver, so a
 * loading chapter is a tall header with a pulsing placeholder under the rule rather than a bare
 * spinner.
 */
@Composable
private fun ReaderHeader(
    state: ReaderUiState,
    title: String,
    titleFamily: FontFamily,
    layout: ReaderLayout,
    onModeSelected: (ReadingMode) -> Unit,
    onChapterClick: () -> Unit,
    onAnchorChanged: (IntRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = layout.top)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.headerHeight)
                .padding(horizontal = layout.horizontal),
            verticalAlignment = Alignment.Bottom,
        ) {
            BookTitle(
                title = title,
                family = titleFamily,
                titleSize = layout.titleSize,
                modifier = Modifier.weight(1f),
            )
            ChapterControl(
                chapter = state.chapter,
                onClick = onChapterClick,
                onAnchorChanged = onAnchorChanged,
            )
        }
        Spacer(Modifier.height(ReaderChrome.belowTitle))
        AppSegmented(
            choices = modeChoices(),
            selected = state.mode,
            onChanged = onModeSelected,
            // Start-aligned and 180 wide, which is Flutter's `Align(centerLeft, SizedBox(width: 180))`.
            // Only the start is padded: padding on both sides would make the control 180 wide *plus*
            // the inset, and `AppSegmented` fills whatever width it is given.
            modifier = Modifier
                .padding(start = layout.horizontal)
                .width(ReaderChrome.modeControlWidth),
            height = ReaderChrome.modeControlHeight,
            accessibilityLabel = stringResource(R.string.reading_language),
        )
        Spacer(Modifier.height(ReaderChrome.belowModeControl))
        Box(
            modifier = Modifier
                .padding(horizontal = layout.horizontal)
                .fillMaxWidth()
                .height(ReaderChrome.ruleThickness)
                .background(appColors.line),
        )
        Spacer(Modifier.height(ReaderChrome.belowRule))
        ChapterPlaceholder(
            loading = state.loading,
            failed = state.failed,
            modifier = Modifier.padding(horizontal = layout.horizontal),
        )
    }
}

/**
 * The book name, with Flutter's crossfade and slide.
 *
 * The `AnimatedSwitcher` is keyed on the text itself rather than on `'${book.id}-${mode.name}'`, so
 * changing the interface language also animates the title. Flutter did not animate there; a language
 * is changed deliberately, from another screen, and a fade is not a surprise.
 *
 * The switcher is skipped where [LocalInspectionMode] is true, and this is the one place in the
 * reader that has to skip rather than start settled. `Animatable` can be handed its finished value
 * up front, but `AnimatedContent` holds the entering title at `alpha = 0` until its first frame
 * advances, and a preview or a golden is exactly one frame that never advances — so the title would
 * be the one thing on the page a reader never sees, and the golden would pin a page with no heading.
 */
@Composable
private fun BookTitle(title: String, family: FontFamily, titleSize: TextUnit, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        BookTitleText(title = title, family = family, titleSize = titleSize, modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = title,
        modifier = modifier,
        contentAlignment = Alignment.BottomStart,
        transitionSpec = {
            val enter = fadeIn(tween(TitleEnterMillis, easing = EaseOutCubic)) +
                slideIn(tween(TitleEnterMillis, easing = EaseOutCubic)) {
                    IntOffset(it.width / TitleSlideDivisor, 0)
                }
            enter togetherWith fadeOut(tween(TitleExitMillis, easing = EaseOutCubic))
        },
        label = "readerTitle",
    ) { text ->
        BookTitleText(title = text, family = family, titleSize = titleSize)
    }
}

/** The title as it is set, whichever way [BookTitle] decided to arrive at it. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun BookTitleText(title: String, family: FontFamily, titleSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = appColors.ink,
        fontFamily = family,
        fontSize = titleSize,
        lineHeight = titleSize * ReaderChrome.titleLineHeight,
        fontWeight = FontWeight.Medium,
        maxLines = TitleMaxLines,
        // Flutter's `overflow: TextOverflow.fade`, so a two-line title softens rather than growing an
        // ellipsis.
        overflow = TextOverflow.Fade,
        modifier = modifier,
    )
}

/** The three reading modes, in the order Flutter listed them. */
@Composable
private fun modeChoices(): List<AppChoice<ReadingMode>> = listOf(
    AppChoice(ReadingMode.CHINESE, stringResource(R.string.chinese)),
    AppChoice(ReadingMode.ENGLISH, stringResource(R.string.english)),
    AppChoice(ReadingMode.BILINGUAL, stringResource(R.string.bilingual)),
)

/**
 * The family the title is set in, replacing `fontFamilyFallback: ['NotoSerifTC']`.
 *
 * Compose has no fallback list on a `TextStyle`, so the same choice is made up front: a name the
 * reading mode and the language chose to be Chinese is set in the bundled serif, and an English one
 * in `Exposure`. The rendered result is what Flutter produced, since a glyph Flutter reached
 * `NotoSerifTC` for is the one Compose would have fallen back to the platform for — and the platform
 * would not necessarily have given a serif.
 */
@Composable
private fun titleFamily(state: ReaderUiState): FontFamily =
    if (state.mode == ReadingMode.CHINESE && !state.usesEnglishUi) {
        AppFonts.NotoSerifTC
    } else {
        AppFonts.Exposure
    }

/** `_VerseSkeleton` or the failure message: what a chapter that is not here yet is drawn as. */
@Composable
private fun ChapterPlaceholder(loading: Boolean, failed: Boolean, modifier: Modifier = Modifier) {
    when {
        loading -> VerseSkeleton(modifier = modifier)
        failed -> Text(
            text = stringResource(R.string.scripture_load_failed),
            color = appColors.muted,
            modifier = modifier.padding(vertical = ReaderChrome.failureVerticalPadding),
        )
    }
}

/** Six bars pulsing between two alphas, from `_VerseSkeleton` at `main.dart:1681`. */
@Composable
private fun VerseSkeleton(modifier: Modifier = Modifier) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.control)
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val value by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ReaderChrome.skeletonPulseMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonPulse",
    )

    Column(modifier = modifier.padding(bottom = ReaderChrome.skeletonBottomPadding)) {
        repeat(ReaderChrome.skeletonBars) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReaderChrome.skeletonBarHeight)
                    .clip(shape)
                    .background(colors.surfaceRaised.copy(alpha = SKELETON_MIN_ALPHA + value * SKELETON_ALPHA_RANGE)),
            )
            Spacer(Modifier.height(ReaderChrome.skeletonBarGap))
        }
    }
}

/** The previous and next chapter links, the third sliver in `legacy/flutter/lib/main.dart:1288`. */
@Composable
private fun ChapterLinks(
    previous: String,
    next: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    layout: ReaderLayout,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = layout.horizontal, end = layout.horizontal, top = ReaderChrome.aboveChapterLinks)
            .padding(bottom = layout.bottom),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChapterLink(
            next = false,
            label = previous,
            enabled = hasPrevious,
            onClick = onPrevious,
        )
        ChapterLink(
            next = true,
            label = next,
            enabled = hasNext,
            onClick = onNext,
        )
    }
}

/**
 * One chapter link, replacing `_ChapterLink` at `legacy/flutter/lib/main.dart:1830`.
 *
 * A link with no chapter to go to is a dash rather than a missing row: Flutter kept the glyph, the
 * caption and the padding and swapped only the label, so the row does not shift as the reader reaches
 * the first chapter of Genesis or the last of Revelation. It is disabled rather than left live, which
 * is what the outcome already was — the view model has no next position and does nothing.
 */
@Composable
private fun ChapterLink(
    next: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val caption = stringResource(if (next) R.string.next_chapter else R.string.previous_chapter)
    val glyph = if (next) AppGlyph.FORWARD else AppGlyph.BACK

    AppTap(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = caption },
    ) {
        Row(modifier = Modifier.padding(LINK_PADDING)) {
            if (!next) {
                AppGlyphView(glyph = glyph, color = colors.muted, size = LINK_GLYPH_SIZE)
            }
            Column(
                modifier = Modifier.padding(horizontal = LINK_LABEL_INSET),
                horizontalAlignment = if (next) Alignment.End else Alignment.Start,
            ) {
                Text(text = caption, color = colors.faint, fontSize = LINK_CAPTION_SIZE)
                Spacer(Modifier.height(LINK_CAPTION_GAP))
                Text(text = label, color = colors.ink, fontSize = LINK_LABEL_SIZE)
            }
            if (next) {
                AppGlyphView(glyph = glyph, color = colors.muted, size = LINK_GLYPH_SIZE)
            }
        }
    }
}

/**
 * `'${bookName} ${chapter}'`, or the dash Flutter showed at the ends of a book.
 *
 * The dash is Flutter's, and it is why the label is not simply computed from the direction: past the
 * last chapter of a book there is no name to put in it, because the link crosses into the next book
 * and Flutter only ever named the chapter. So the caller decides whether the link exists and this
 * only says what it reads.
 */
private fun chapterLinkLabel(bookName: String, chapter: Int, exists: Boolean): String =
    if (exists) "$bookName $chapter" else NO_CHAPTER_LABEL

/**
 * Closes the chapter's entrance once the frame its verses were laid out in has passed.
 *
 * One frame, and not one composition: a `LazyColumn` builds its items while it is measured rather
 * than while this composable runs, so the items of the first frame exist before any effect here
 * could have closed the gate. Closing on the following frame is what separates "the chapter arrived"
 * from "the reader scrolled to another verse".
 */
@Composable
private fun CloseEntrance(gate: EntranceGate) {
    LaunchedEffect(gate) {
        withFrameNanos { }
        gate.open = false
    }
}

/**
 * Reports where the list is, on every frame, which is what `ScrollEndNotification` did in Flutter.
 *
 * The view model replaces its 180 ms timer on each call, so reporting every frame only means the
 * write happens once the reader has stopped — the same thing Flutter's end-of-scroll notification
 * plus its `Timer(180)` did, without a listener for an event Compose does not raise.
 */
@Composable
private fun ReportScroll(listState: LazyListState, itemCount: Int, scroll: ReaderScrollSink) {
    LaunchedEffect(listState, itemCount) {
        snapshotFlow {
            ScrollSample(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset.toFloat(),
                viewport = listState.viewportHeightPx(),
                extent = listState.estimatedMaxScrollPx(),
            )
        }.collect { sample ->
            scroll.onScrolled(scrollRatioFor(sample.index, sample.offset, sample.viewport, itemCount))
            scroll.onChapterMeasured(sample.extent)
        }
    }
}

/**
 * Jumps to the one-shot ratio, then says it has been consumed.
 *
 * Without the second call the instruction would stay in the state and the next recomposition would
 * scroll the reader back where they had been, so a rotation would land twice on the same offset and
 * a fling that had moved on would be undone.
 */
@Composable
private fun RestoreScroll(listState: LazyListState, itemCount: Int, target: Float?, scroll: ReaderScrollSink) {
    LaunchedEffect(target) {
        val ratio = target ?: return@LaunchedEffect
        listState.scrollToItem(scrollItemForRatio(ratio, itemCount))
        scroll.onScrollRestored()
    }
}

/** What one pass over the list's layout yields, so the flow below emits once per layout. */
private data class ScrollSample(val index: Int, val offset: Float, val viewport: Float, val extent: Float)

/** The height of the visible area, which is the viewport a ratio is measured against. */
private fun LazyListState.viewportHeightPx(): Float =
    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()

/**
 * The chapter's scrollable extent, estimated from the items that have been laid out.
 *
 * A `LazyListState` has no maximum scroll extent — it only knows the items in view — and one is only
 * needed for one thing: dividing the pixel offset an upgraded Flutter install left behind. That is
 * already an approximation by its own admission, and it is applied once, so the average height of
 * what is on screen is a good enough stand-in for a chapter's full extent, and it tightens as the
 * reader scrolls. A chapter that fits on the screen has no extent to speak of and yields 0, which
 * the view model ignores.
 */
private fun LazyListState.estimatedMaxScrollPx(): Float {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || info.totalItemsCount == 0) return 0f
    val average = visible.sumOf { it.size.toDouble() } / visible.size
    return (average * info.totalItemsCount - viewportHeightPx()).coerceAtLeast(0.0).toFloat()
}

/** The header and the chapter links, which are items of their own between the verses. */
private const val ListOverheadItems = 2

private const val HEADER_ITEM_KEY = "readerHeader"
private const val LINKS_ITEM_KEY = "readerChapterLinks"

/** 240 ms in, 140 ms out, both on `Curves.easeOutCubic`, from the title's `AnimatedSwitcher`. */
private const val TitleEnterMillis = 240
private const val TitleExitMillis = 140

/** `Offset(.025, 0)`, the slide as a fraction of the title's own width. */
private const val TitleSlideDivisor = 40
private const val TitleMaxLines = 2

/** `.38 + controller.value * .22`, the skeleton bars' two ends. */
private const val SKELETON_MIN_ALPHA = 0.38f
private const val SKELETON_ALPHA_RANGE = 0.22f

/** `EdgeInsets.all(10)`, the link's padding, and the caption's 9 px and 3 px gap. */
private val LINK_PADDING = 10.dp
private val LINK_LABEL_INSET = 10.dp
private val LINK_CAPTION_GAP = 3.dp
private val LINK_GLYPH_SIZE = 18.dp
private val LINK_CAPTION_SIZE = 9.sp
private val LINK_LABEL_SIZE = 13.sp

/** The `'—'` Flutter showed where there was no chapter to go to. */
private const val NO_CHAPTER_LABEL = "—"
