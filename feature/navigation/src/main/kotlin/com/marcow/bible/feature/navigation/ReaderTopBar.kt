package com.marcow.bible.feature.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.icons.AppGlyph

/**
 * Which controls the bar shows, which is the whole of Flutter's decision about it.
 *
 * All three came out of one place in the Flutter shell — `wide` from the `LayoutBuilder`, and
 * `showNavbar` / `showDevotion` off the settings controller — and they were read in three different
 * places: the menu button's own `if (!wide)`, the search-and-settings pair after the `Spacer`, and
 * the `if (!showNavbar)` spread in between. Grouped here, the three answers stay together and the
 * one place they are consumed is [readerTopBarSlots], which a test can read without a device.
 *
 * [libraryButton] is `!wide`, and `wide` belongs to the reader rather than to the bar: it is the
 * window being at least 920 dp wide, which is the same number that brings in the sidebar beside the
 * reader. The host reads it off `feature:reader`'s `WideThreshold` and passes the answer down; this
 * module takes the decision, it does not make it.
 */
data class ReaderTopBarVisibility(
    /** The menu button that opens the library, which the wide layout drops because the sidebar has it. */
    val libraryButton: Boolean,
    /** Whether the bottom bar is on screen, which is what puts the Ask and Devotions buttons here. */
    val navBar: Boolean,
    /** Whether the Devotions tab itself is switched on. */
    val devotion: Boolean,
)

/** One control on the trailing side of the bar. */
enum class ReaderTopBarControl {
    ASK,
    DEVOTION,
    SEARCH,
    SETTINGS,
}

/**
 * One trailing control and the gap that sits in front of it.
 *
 * Flutter's gaps were `SizedBox(width: 9)` scattered through a `Row`, and where each one went is the
 * part that is easy to get wrong by one: the 9 that separates Devotions from Ask is inside the
 * Devotions branch, while the 9 that separates Search from whatever came before it is outside it and
 * is therefore paid by Ask on its own. Moving one control in or out shifts a gap that belongs to
 * another control, so the gap travels with the control here rather than being counted between them.
 */
data class ReaderTopBarSlot(val control: ReaderTopBarControl, val gapBefore: Dp)

/**
 * The controls to the right of the book's button, in the order Flutter's `Row` listed them.
 *
 * Search and Settings are unconditional: they are the two ways out of the reader that work either way,
 * and Settings has to reach on a window too wide for the search sheet to be the faster route. The
 * Ask and Devotions buttons are not — they stand in for a hidden bottom bar, so with the bar on they
 * would duplicate destinations the bar is already showing.
 */
fun readerTopBarSlots(showNavBar: Boolean, showDevotion: Boolean): List<ReaderTopBarSlot> {
    if (showNavBar) {
        return listOf(
            ReaderTopBarSlot(ReaderTopBarControl.SEARCH, gapBefore = 0.dp),
            ReaderTopBarSlot(ReaderTopBarControl.SETTINGS, gapBefore = ReaderTopBarControlGap),
        )
    }
    val slots = mutableListOf(ReaderTopBarSlot(ReaderTopBarControl.ASK, gapBefore = 0.dp))
    if (showDevotion) slots += ReaderTopBarSlot(ReaderTopBarControl.DEVOTION, gapBefore = ReaderTopBarControlGap)
    slots += ReaderTopBarSlot(ReaderTopBarControl.SEARCH, gapBefore = ReaderTopBarControlGap)
    slots += ReaderTopBarSlot(ReaderTopBarControl.SETTINGS, gapBefore = ReaderTopBarControlGap)
    return slots
}

/**
 * The glyph on each control, in App's own set rather than the Lucide icons Flutter drew.
 *
 * Ask and Devotions are here standing in for two tabs of [AppNavBar], which carry [AppGlyph.CHAT]
 * and [AppGlyph.SUN]; a shortcut that stood in for a tab with a different glyph on it would be the
 * one control on the bar that looked like something else. All four are interchangeable to the eye at
 * 19 dp — a chat bubble and a rising sun are both a shape on a square — so a swap between them is
 * held only by a golden, which is the one place two 19 dp glyphs tell each other apart at all.
 *
 * Search and Settings keep the two glyphs Flutter used unchanged: the app's own book, search and gear
 * rather than a second icon language for four controls.
 */
fun readerTopBarGlyph(control: ReaderTopBarControl): AppGlyph = when (control) {
    ReaderTopBarControl.ASK -> AppGlyph.CHAT
    ReaderTopBarControl.DEVOTION -> AppGlyph.SUN
    ReaderTopBarControl.SEARCH -> AppGlyph.SEARCH
    ReaderTopBarControl.SETTINGS -> AppGlyph.SETTINGS
}

/**
 * The string each control is named by, which is the whole of what a control says.
 *
 * There is no word on any of them — four identical frosted squares with a glyph each — so this is the
 * only place the destination is written down at all, and each is the string Flutter's `Row` over the
 * reader used for the same control: `tabAsk` at `legacy/flutter/lib/main.dart:657`, `tabDevotion` at
 * `:664`, `searchWholeBible` at `:672` and `settings` at `:680`.
 *
 * The last two are worth spelling out. The search button says *Search the whole Bible* and not
 * *Search*, because that is the string the field it opens is then given as its hint (`:2356`) — the
 * button names the thing the reader is about to type into. And Ask and Devotions say exactly what
 * [AppNavBar]'s tabs of the same destination say, since they are the same destinations, reached from
 * the top of the reader because the bar that normally holds them is switched off.
 *
 * Four names for four controls, none of them shared: a label that named two of them at once would
 * leave a reader with two controls that announce themselves identically and do different things.
 */
fun readerTopBarLabel(control: ReaderTopBarControl): Int = when (control) {
    ReaderTopBarControl.ASK -> R.string.tab_ask
    ReaderTopBarControl.DEVOTION -> R.string.tab_devotion
    ReaderTopBarControl.SEARCH -> R.string.search_whole_bible
    ReaderTopBarControl.SETTINGS -> R.string.settings
}

/**
 * The book button's name, which is the library's rather than the control's.
 *
 * It is on the leading side, outside the [Spacer] that pushes the rest to the right edge, so it is not
 * one of the [ReaderTopBarControl]s — the wide layout drops it entirely, and it is the sidebar's book
 * row that carries the name then. `selectBook` is the same string Flutter gave the sidebar's row at
 * `:1765` and this button at `:650`, which is the whole point: two controls, one destination, one
 * name, and on a window too wide for this one the other is there to say it.
 */
val ReaderLibraryButtonLabel: Int = R.string.select_book

/**
 * The row of controls the Flutter shell drew *over* the reader, replacing the `readerControls`
 * `Stack` at `legacy/flutter/lib/main.dart:642`.
 *
 * It is here rather than in `feature:reader` for the same reason the bar itself is: these controls
 * belong to the shell, they know nothing about a chapter, and the reader's own layout already leaves
 * room for them — its `top = statusBar + 108` is the status bar, the 4 px gap below it, the 40 dp
 * control and the 24 px Flutter left under it. So a reader drawn without a bar over it still leaves
 * that space, which is the same space the wide layout's `statusBar + 72` leaves.
 *
 * The four callbacks are separate rather than one "which control was tapped" closure because they
 * go to four different destinations in three different features, and a host that had to switch over
 * them would be the only thing in the app naming all four.
 *
 * Every control is an `AppGlyphButton`, which is a 40 dp frosted surface with a 19 dp glyph on it —
 * exactly what Flutter's `_IconControlButton` spelled out by hand for the Ask and Devotions buttons
 * and its `AppControlSurface` + `AppTap` pair for the gear. Settings keeps the app's own gear glyph
 * rather than a Lucide one, because using a second icon language for one control is the difference
 * Phase 1 was careful not to introduce anywhere else.
 */
@Composable
fun ReaderTopBar(
    visibility: ReaderTopBarVisibility,
    onOpenLibrary: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAsk: () -> Unit,
    onDevotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .padding(top = topInset + ReaderTopBarTopGap)
            .padding(horizontal = ReaderTopBarHorizontalInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (visibility.libraryButton) {
            AppGlyphButton(
                glyph = AppGlyph.MENU,
                label = stringResource(ReaderLibraryButtonLabel),
                onClick = onOpenLibrary,
            )
        }
        // The `Spacer` between the book's button and everything else, which is what pushes the two
        // right-hand groups to their edges on a window of any width.
        Spacer(Modifier.weight(1f))
        readerTopBarSlots(visibility.navBar, visibility.devotion).forEach { slot ->
            Spacer(Modifier.width(slot.gapBefore))
            // Which control this is has already been decided by `readerTopBarSlots`; what is left is
            // where it goes, and the four callbacks are the only part of the bar that is not a table.
            val onClick = when (slot.control) {
                ReaderTopBarControl.ASK -> onAsk
                ReaderTopBarControl.DEVOTION -> onDevotion
                ReaderTopBarControl.SEARCH -> onSearch
                ReaderTopBarControl.SETTINGS -> onSettings
            }
            AppGlyphButton(
                glyph = readerTopBarGlyph(slot.control),
                label = stringResource(readerTopBarLabel(slot.control)),
                onClick = onClick,
            )
        }
    }
}

/** `left: 20, right: 20` on Flutter's `Positioned`, which is where the bar sat on the window. */
private val ReaderTopBarHorizontalInset: Dp = 20.dp

/** The 4 px between the status bar and the top of the controls. */
private val ReaderTopBarTopGap: Dp = 4.dp

/** The `SizedBox(width: 9)` Flutter put between any two of the trailing controls. */
private val ReaderTopBarControlGap: Dp = 9.dp
