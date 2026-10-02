package com.marcow.bible.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode

/**
 * The column beside the reader on a wide window, replacing `_Sidebar` at
 * `legacy/flutter/lib/main.dart:1729`.
 *
 * Flutter's sidebar was a `SizedBox(width: 270)` inside a `Row` in the shell's `LayoutBuilder`, shown
 * when the window was at least `920` wide — a decision about the *window* that belongs to whatever
 * lays the two out, not to the column itself. So this fills the width it is given and knows nothing
 * about the threshold. [SidebarWidth] is the width the host should give it.
 *
 * The two name inputs, [readingMode] and [usesEnglishUi], are the reader's own — they are what
 * [BibleBook.displayName] is called with, and the book row here has to agree with the title in the
 * reader beside it or the two halves of one screen spell the same book two ways.
 */
@Composable
fun LibrarySidebar(
    book: BibleBook,
    chapter: Int,
    readingMode: ReadingMode,
    usesEnglishUi: Boolean,
    onLibraryClick: () -> Unit,
    onChapterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val name = book.displayName(readingMode, usesEnglishUi)
    val openLibraryLabel = stringResource(R.string.select_book)
    // `Dp.toSp()` is the conversion that keeps the token absolute: the sp it hands back is scaled
    // up by the same font scale before the text engine sees it, so the tracking stays 2 dp.
    val labelTracking = with(LocalDensity.current) { SidebarChrome.readingLabelSpacing.toSp() }
    Column(
        modifier = modifier
            .fillMaxSize()
            // The status bar plus 102, which is where the reader's own top chrome begins. The reader
            // derives its top from the same 102 (as 72 when it is wide), so the two line up.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(
                start = SidebarChrome.horizontalStart,
                top = SidebarChrome.top,
                end = SidebarChrome.horizontalEnd,
                bottom = SidebarChrome.bottom,
            ),
    ) {
        Text(
            text = stringResource(R.string.currently_reading),
            color = colors.faint,
            fontSize = SidebarChrome.readingLabelSize,
            letterSpacing = labelTracking,
        )
        Spacer(Modifier.height(SidebarChrome.belowReadingLabel))
        AppTap(
            onClick = onLibraryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SidebarChrome.bookRowStart,
                    top = SidebarChrome.bookRowTop,
                    end = SidebarChrome.bookRowEnd,
                    bottom = SidebarChrome.bookRowBottom,
                )
                // What the row is named, not what it is showing. Flutter's `AppTap` at
                // `legacy/flutter/lib/main.dart:1765` was `AppTap(label: context.l10n.selectBook)` —
                // the same label the menu button over the reader carries — so this row is announced as
                // the control that opens the library rather than as the book currently open beside it.
                // Naming the book instead was a plausible reading and the wrong one: a reader who taps
                // this row gets the sheet, and the name is already the column's own heading above it.
                //
                // Flutter also excluded the row's inner text, so its node was the label alone. Here the
                // name is still read after it, which is the one difference left and the useful one: the
                // control says what it does and then which book it would change.
                .semantics { contentDescription = openLibraryLabel },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    color = colors.ink,
                    fontSize = SidebarChrome.bookNameSize,
                    modifier = Modifier.weight(1f),
                )
                AppGlyphView(
                    glyph = AppGlyph.CHEVRON_DOWN,
                    color = colors.muted,
                    size = SidebarChrome.bookChevronSize,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SidebarChrome.ruleThickness)
                .background(colors.line),
        )
        Spacer(Modifier.height(SidebarChrome.belowRule))
        ChapterGrid(
            chapterCount = book.chapters,
            chapter = chapter,
            onChapterSelected = onChapterSelected,
        )
    }
}

/**
 * The book's chapters, five to a row.
 *
 * The chapter being read is filled with ink and its number turns to canvas, which is how Flutter
 * marked it. Only the fill is animated, because that is all `AnimatedContainer` animated: the number's
 * colour changed with the recomposition, not on a tween, and the fill under it is what carries the
 * 220 ms.
 */
@Composable
private fun ChapterGrid(chapterCount: Int, chapter: Int, onChapterSelected: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(SidebarChrome.chapterColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(SidebarChrome.chapterSpacing),
        verticalArrangement = Arrangement.spacedBy(SidebarChrome.chapterSpacing),
    ) {
        items(count = chapterCount, key = { index -> index + 1 }) { index ->
            val number = index + 1
            ChapterCell(
                number = number,
                active = number == chapter,
                onClick = { onChapterSelected(number) },
            )
        }
    }
}

@Composable
private fun ChapterCell(number: Int, active: Boolean, onClick: () -> Unit) {
    val colors = appColors
    // Read here rather than inside the `semantics` block below, which is a plain lambda: a
    // `stringResource` is composable and only the enclosing function is.
    val chapterLabel = stringResource(R.string.chapter_number, number)
    val fill by animateColorAsState(
        targetValue = if (active) colors.ink else Color.Transparent,
        animationSpec = tween(SidebarChrome.chapterFillMillis),
        label = "chapterFill",
    )
    AppTap(
        onClick = onClick,
        selected = active,
        // Square, as `SliverGridDelegateWithFixedCrossAxisCount` was: its child aspect ratio
        // defaults to one, so a cell is as tall as its row is wide whatever the width works out to.
        modifier = Modifier
            .aspectRatio(1f)
            .semantics { contentDescription = chapterLabel },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(appRadii.compact))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                color = if (active) colors.canvas else colors.faint,
                fontSize = SidebarChrome.chapterNumberSize,
            )
        }
    }
}
