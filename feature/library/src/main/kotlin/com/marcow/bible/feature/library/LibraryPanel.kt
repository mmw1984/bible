package com.marcow.bible.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppChoice
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppSegmented
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.SpringCurve
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.Testament

/**
 * The book sheet, replacing `_LibraryPanel` at `legacy/flutter/lib/main.dart:1944`.
 *
 * Everything it needs is a parameter, so it can be drawn from a fixed [LibraryUiState] with no
 * database behind it — which is what lets a preview, and a golden, open the sheet.
 *
 * Three Flutter details are load-bearing and are kept:
 *
 * **It opens on the testament you are reading.** `late bool old = bibleBooks[widget.selected].old`
 * meant the sheet appeared already showing the half of the canon the current book is in, and the
 * segmented control left it there until the reader chose otherwise. Keying the remembered testament
 * on the selected book reproduces both halves of that.
 *
 * **The outgoing list was never drawn.** `AnimatedSwitcher`'s `layoutBuilder` returned only the
 * incoming child, so the 240 ms `switchOutCurve` had nothing to fade and the swap was instant; the
 * new list then arrived over 430 ms on the spring curve. `AnimatedContent` keeps both by default, so
 * the exit is switched off here rather than the switch being approximated with a crossfade.
 *
 * **A book is named by the reading mode, not the interface language.** The number down the side is
 * its canon position and the trailing detail is a chapter count, and both stay as they are whatever
 * the book is called; only the name follows [BibleBook.displayName].
 */
@Composable
fun LibraryPanel(
    state: LibraryUiState,
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (BibleBook) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val selected = state.books.firstOrNull { it.id == selectedBookId }
    // Keyed on the book rather than the testament, so choosing a book in the other half and dismissing
    // the sheet leaves the reader where they asked to be — which is the same rule Flutter's `late bool
    // old = bibleBooks[widget.selected].old` ran on every time the panel was built.
    var testament by remember(selected?.id) { mutableStateOf(openingTestament(state.books, selectedBookId)) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape = RoundedCornerShape(topEnd = appRadii.screen, bottomEnd = appRadii.screen))
            .background(colors.canvas)
            // The status bar and the sides, but not the navigation bar: Flutter's
            // `SafeArea(bottom: false)` let the sheet's list run under it, which is where its last
            // book ends up. The cutout is Flutter's `MediaQuery.padding` and so is `safeDrawing`,
            // which matters on a landscape phone where a notch cuts into the side the panel is on.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PanelChrome.horizontal, PanelChrome.top, PanelChrome.horizontal, 0.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(libraryPanelLabel(LibraryPanelLabel.TITLE)),
                    color = colors.ink,
                    fontFamily = AppFonts.Exposure,
                    fontSize = PanelChrome.titleSize,
                    modifier = Modifier.weight(1f),
                )
                AppGlyphButton(
                    glyph = AppGlyph.CLOSE,
                    label = stringResource(libraryPanelLabel(LibraryPanelLabel.CLOSE)),
                    onClick = onDismiss,
                    size = PanelChrome.closeSize,
                    glyphSize = PanelChrome.closeGlyphSize,
                )
            }
            Spacer(Modifier.height(PanelChrome.belowTitle))
            AppSegmented(
                choices = testamentChoices(),
                selected = testament,
                onChanged = { chosen -> testament = chosen },
                accessibilityLabel = stringResource(libraryPanelLabel(LibraryPanelLabel.TESTAMENT_SWITCHER)),
            )
            Spacer(Modifier.height(PanelChrome.belowSegmented))
            TestamentList(
                testament = testament,
                state = state,
                readingMode = readingMode,
                selectedBookId = selectedBookId,
                onBookSelected = onBookSelected,
            )
        }
    }
}

/**
 * The half of the canon the segmented control has chosen, swapping as it is chosen.
 *
 * The switcher is skipped where [LocalInspectionMode] is true, for the reason the reader's title
 * skips its own: `AnimatedContent` holds the incoming list at `alpha = 0` until its first frame
 * advances, so the panel would be captured as its title and its segmented control over nothing. The
 * gate below it has the same problem and the same answer, and the two have to agree — a panel whose
 * switcher is settled but whose gate is not would show a list at `alpha = 0` just as blankly.
 */
@Composable
private fun TestamentList(
    testament: Testament,
    state: LibraryUiState,
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (BibleBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        BookList(
            books = state.booksIn(testament),
            state = state,
            readingMode = readingMode,
            selectedBookId = selectedBookId,
            onBookSelected = onBookSelected,
            modifier = modifier,
        )
        return
    }
    AnimatedContent(
        targetState = testament,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(PanelChrome.listEnterMillis, easing = SpringCurve)) +
                slideIn(tween(PanelChrome.listEnterMillis, easing = SpringCurve)) { height ->
                    IntOffset(0, (height * PanelChrome.ListSlideFraction).toInt())
                } togetherWith ExitTransition.None
        },
        label = "libraryTestament",
    ) { shown ->
        BookList(
            books = state.booksIn(shown),
            state = state,
            readingMode = readingMode,
            selectedBookId = selectedBookId,
            onBookSelected = onBookSelected,
        )
    }
}

/**
 * The list of books, with the 1 px rule Flutter's `ListView.separated` drew between rows.
 *
 * The rule is the top of every row but the last rather than an item of its own, so it cannot scroll
 * independently of the row above it.
 */
@Composable
private fun BookList(
    books: List<BibleBook>,
    state: LibraryUiState,
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (BibleBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // A gate that starts closed is what a preview and a golden want: nothing is playing a stagger
    // there, and `Animatable` anchors to the first frame it is given, so an open gate would draw
    // every row of the panel at `alpha = 0` — the title, the segmented control and an empty list.
    val inspection = LocalInspectionMode.current
    val gate = remember { RowGate(open = !inspection) }
    CloseRowGate(gate)

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(count = books.size, key = { index -> books[index].id }) { index ->
            val book = books[index]
            RowEntrance(staggerIndex = index, gate = gate) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BookRow(
                        ordinal = book.ordinal,
                        name = state.displayName(book, readingMode),
                        selected = book.id == selectedBookId,
                        chapterCount = book.chapters,
                        onClick = { onBookSelected(book) },
                    )
                    if (index < books.lastIndex) BookRule()
                }
            }
        }
    }
}

/**
 * Closes the panel's entrance once the frame its rows were laid out in has passed.
 *
 * One frame, for the reason the reader's is: a `LazyColumn` builds its rows while it is measured, so
 * the rows of the first frame exist before any effect here could have closed the gate.
 */
@Composable
private fun CloseRowGate(gate: RowGate) {
    LaunchedEffect(gate) {
        withFrameNanos { }
        gate.open = false
    }
}

/** One book: its canon number, its name, and how many chapters it has. */
@Composable
private fun BookRow(ordinal: Int, name: String, selected: Boolean, chapterCount: Int, onClick: () -> Unit) {
    val colors = appColors
    AppTap(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PanelChrome.rowVertical, horizontal = PanelChrome.rowHorizontal)
            .semantics { contentDescription = name },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = libraryRowOrdinal(ordinal),
                color = colors.faint,
                fontSize = PanelChrome.rowDetailSize,
                modifier = Modifier.width(PanelChrome.rowOrdinalWidth),
            )
            Text(
                text = name,
                color = colors.ink,
                fontSize = PanelChrome.rowNameSize,
                // Flutter semibolded only the book being read, which is the one that stays where it
                // is when the sheet is dismissed.
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.chapter_count, chapterCount),
                color = colors.faint,
                fontSize = PanelChrome.rowDetailSize,
            )
        }
    }
}

@Composable
private fun BookRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelChrome.ruleThickness)
            .background(appColors.line),
    )
}

/** `Old Testament · 39` and `New Testament · 27`, with the counts baked into the labels. */
@Composable
private fun testamentChoices(): List<AppChoice<Testament>> =
    testamentLabels().map { (testament, label) -> AppChoice(testament, stringResource(label)) }

/**
 * The two halves of the canon and the string each is named by, with the resources left in them.
 *
 * Old before New, which is `[true, false]` in Flutter's `_TestamentSegmented`
 * (`legacy/flutter/lib/main.dart:2100`) and canon order here. The order is not free: the switcher
 * opens on whichever half the reader's book is in — [openingTestament] — so the two are told apart by
 * position as often as by name, and a New Testament reader arriving at 舊約 has to read the other one
 * to know where they are.
 *
 * The counts are *inside* the translations rather than computed, and that is Flutter's arrangement
 * too: `舊約 · 39` and `新約 · 27` are two whole strings in `app_zh.arb:96` and `:97`. 39 and 27 are
 * the size of the canon and not a property of a build, so a build shipping a different bible would
 * carry them in its own translations rather than have them formatted in here from a list that might
 * not be the same list.
 */
fun testamentLabels(): List<Pair<Testament, Int>> = listOf(
    Testament.OLD to R.string.old_testament_count,
    Testament.NEW to R.string.new_testament_count,
)

/** The three strings the panel's own chrome is written in. */
enum class LibraryPanelLabel {
    /** The 34 dp heading at the top of the sheet. */
    TITLE,

    /**
     * What the testament switcher announces itself as.
     *
     * Flutter's `_TestamentSegmented` said nothing here — its `AppSegmented` has no label of its own
     * and there is no `Semantics` around it, unlike the reader's `_Segmented`, which wraps itself in
     * `Semantics(label: readingLanguage)` at `:1659`. Two cells reading 舊約 · 39 and 新約 · 27 are
     * enough for a reader who can see them and nothing at all for one who cannot, so the name is
     * added here — and it is the sheet's own name rather than a fourth thing to learn, because the
     * heading two rows above is already saying it.
     */
    TESTAMENT_SWITCHER,

    /** The button that dismisses the sheet. */
    CLOSE,
}

/**
 * The string each piece of the panel's chrome is named by.
 *
 * The title and the switcher are one string on purpose: both are the sheet's own name, one where a
 * reader reads it and one where a screen reader announces it, and two different words for the two
 * would be one sheet with two names. `select_book` is Flutter's `context.l10n.selectBook` at
 * `legacy/flutter/lib/main.dart:1985`, and `close` its `context.l10n.close` at `:1995`.
 */
fun libraryPanelLabel(label: LibraryPanelLabel): Int = when (label) {
    LibraryPanelLabel.TITLE -> R.string.select_book
    LibraryPanelLabel.TESTAMENT_SWITCHER -> R.string.select_book
    LibraryPanelLabel.CLOSE -> R.string.close
}
