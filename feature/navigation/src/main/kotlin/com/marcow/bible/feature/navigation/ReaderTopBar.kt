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
    if (showNavBar) return listOf(
        ReaderTopBarSlot(ReaderTopBarControl.SEARCH, gapBefore = 0.dp),
        ReaderTopBarSlot(ReaderTopBarControl.SETTINGS, gapBefore = ReaderTopBarControlGap),
    )
    val slots = mutableListOf(ReaderTopBarSlot(ReaderTopBarControl.ASK, gapBefore = 0.dp))
    if (showDevotion) slots += ReaderTopBarSlot(ReaderTopBarControl.DEVOTION, gapBefore = ReaderTopBarControlGap)
    slots += ReaderTopBarSlot(ReaderTopBarControl.SEARCH, gapBefore = ReaderTopBarControlGap)
    slots += ReaderTopBarSlot(ReaderTopBarControl.SETTINGS, gapBefore = ReaderTopBarControlGap)
    return slots
}

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
                label = stringResource(R.string.select_book),
                onClick = onOpenLibrary,
            )
        }
        // The `Spacer` between the book's button and everything else, which is what pushes the two
        // right-hand groups to their edges on a window of any width.
        Spacer(Modifier.weight(1f))
        readerTopBarSlots(visibility.navBar, visibility.devotion).forEach { slot ->
            Spacer(Modifier.width(slot.gapBefore))
            when (slot.control) {
                ReaderTopBarControl.ASK -> AppGlyphButton(
                    glyph = AppGlyph.CHAT,
                    label = stringResource(R.string.tab_ask),
                    onClick = onAsk,
                )

                ReaderTopBarControl.DEVOTION -> AppGlyphButton(
                    glyph = AppGlyph.SUN,
                    label = stringResource(R.string.tab_devotion),
                    onClick = onDevotion,
                )

                ReaderTopBarControl.SEARCH -> AppGlyphButton(
                    glyph = AppGlyph.SEARCH,
                    label = stringResource(R.string.search_whole_bible),
                    onClick = onSearch,
                )

                ReaderTopBarControl.SETTINGS -> AppGlyphButton(
                    glyph = AppGlyph.SETTINGS,
                    label = stringResource(R.string.settings),
                    onClick = onSettings,
                )
            }
        }
    }
}

/** `left: 20, right: 20` on Flutter's `Positioned`, which is where the bar sat on the window. */
private val ReaderTopBarHorizontalInset: Dp = 20.dp

/** The 4 px between the status bar and the top of the controls. */
private val ReaderTopBarTopGap: Dp = 4.dp

/** The `SizedBox(width: 9)` Flutter put between any two of the trailing controls. */
private val ReaderTopBarControlGap: Dp = 9.dp