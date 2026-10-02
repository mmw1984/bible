package com.marcow.bible.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp
import com.marcow.bible.core.database.SEARCH_RESULT_LIMIT
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppChoice
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppSegmented
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.feature.search.ui.AiSearchLoading
import com.marcow.bible.feature.search.ui.NoResultsMessage
import com.marcow.bible.feature.search.ui.OverviewFailedPanel
import com.marcow.bible.feature.search.ui.ReferencesFailedPanel
import com.marcow.bible.feature.search.ui.SearchHint
import com.marcow.bible.feature.search.ui.SearchHitTile
import com.marcow.bible.feature.search.ui.SearchPanel
import com.marcow.bible.feature.search.ui.SearchProgressLine
import com.marcow.bible.feature.search.ui.SearchQueryField
import com.marcow.bible.feature.search.ui.SearchRow
import com.marcow.bible.feature.search.ui.SearchSection
import com.marcow.bible.feature.search.ui.SearchSectionLabel
import com.marcow.bible.feature.search.ui.SearchStatusPanel
import com.marcow.bible.feature.search.ui.SignInPanel
import com.marcow.bible.feature.search.ui.searchRows

/**
 * The search sheet, ported from `_SearchDialog` in `legacy/flutter/lib/main.dart:2321`.
 *
 * Stateless by design: [SearchViewModel] owns [SearchSheetState] and every write to it, and this
 * composable only draws that state and reports what the user did. It is the same split the Flutter
 * build had — a `StatefulWidget` whose thirteen fields were written from four different callbacks —
 * except that here the fields are one value with one writer, so what the sheet shows is always one
 * consistent thing.
 *
 * The layout is Flutter's, in Flutter's order and with Flutter's gaps: the query box with the forward
 * and close buttons, the mode control below it, the progress line under that, then the results,
 * which are either [SearchSheetState.showsHint] or the rows [searchRows] asks for.
 *
 * Two decisions are worth stating because they are where the two builds could most easily diverge.
 * The sheet decides *nothing* about which panel is up — [searchRows] does, in one testable list — and
 * it runs nothing: a callback per control, so each says what it does and nothing else.
 *
 * @param state what to draw, as [SearchViewModel] last left it.
 * @param readingMode which translation the reader is in, which decides a tile's book name.
 * @param locale the app's language, which decides the same heading and the verse text.
 * @param onQueryChanged what is in the box, which is not yet a search.
 * @param onSearch submit the box, from the forward button or the keyboard's search key.
 * @param onModeChange pick the other search; the view model refuses while one is running.
 * @param onSignIn arm the typed query so it runs once a sign-in lands, then start the sign-in.
 * @param onDismiss close the sheet.
 * @param onOpenVerse take a hit to the reader.
 * @param authError `BibleAiController.openRouterAuthError`, shown under the sign-in panel.
 */
@Suppress("LongParameterList")
@Composable
fun SearchSheet(
    state: SearchSheetState,
    readingMode: ReadingMode,
    locale: AppLocale,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVerse: (ScriptureHit) -> Unit,
    modifier: Modifier = Modifier,
    authError: String? = null,
) {
    val colors = appColors
    val surfaceShape = RoundedCornerShape(appRadii.surface)
    // Flutter's `AnimatedPadding(bottom: keyboard)` in 220 ms: the one animation here that is about
    // the window rather than about the search, and the reason the sheet sits where it does.
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val lifted by animateDpAsState(
        targetValue = keyboard,
        animationSpec = tween(KEYBOARD_MILLIS, easing = EaseOutCubic),
        label = "searchKeyboard",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(SHEET_INSET),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = SHEET_MAX_WIDTH)
                .fillMaxHeight()
                .padding(bottom = lifted)
                .background(colors.canvas, surfaceShape)
                .border(SHEET_BORDER_WIDTH, colors.line, surfaceShape)
                // Flutter's dialog widget blocked the barrier from everything inside its own bounds,
                // so a tap on the surface's empty space — a gap between two tiles, the margin above
                // the results — did nothing at all rather than closing the sheet. `SearchDialog`'s
                // barrier is dismissible, so the surface has to say that it took the tap. No
                // indication: Flutter drew no ripple on the dialog itself.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            SearchHeader(
                state = state,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onDismiss = onDismiss,
            )
            // Flutter gated this row on `widget.ai.isSupported`, which with no on-device provider is
            // always true — OpenRouter is reachable everywhere the app ships.
            AppSegmented(
                choices = searchModeChoices(),
                selected = state.mode,
                onChanged = onModeChange,
                modifier = Modifier.padding(MODE_CONTROL_PADDING),
                height = MODE_CONTROL_HEIGHT,
            )
            SearchProgressVisibility(searching = state.searching)
            SearchResults(
                state = state,
                readingMode = readingMode,
                locale = locale,
                onSignIn = onSignIn,
                onOpenVerse = onOpenVerse,
                authError = authError,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * The query box with the forward and close buttons beside it, mirroring the header `Row` in
 * `legacy/flutter/lib/main.dart:2352`.
 */
@Composable
private fun SearchHeader(
    state: SearchSheetState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(HEADER_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchQueryField(
            value = state.input,
            onValueChange = onQueryChanged,
            onSearch = onSearch,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SEARCH_BUTTON_GAP))
        AppGlyphButton(
            glyph = AppGlyph.FORWARD,
            label = stringResource(R.string.search),
            onClick = onSearch,
        )
        Spacer(Modifier.width(CLOSE_BUTTON_GAP))
        AppGlyphButton(
            glyph = AppGlyph.CLOSE,
            label = stringResource(R.string.close),
            onClick = onDismiss,
        )
    }
}

/**
 * The hairline under the header, shown only while something is running, mirroring Flutter's
 * `ClipRect(AnimatedSize(...))` around `AppProgressLine`.
 *
 * When nothing is running Flutter drew `SizedBox(width: double.infinity)`, which takes no height — so
 * the row below did not move when a search started, and the expansion here is what keeps it that way.
 */
@Composable
private fun SearchProgressVisibility(searching: Boolean) {
    AnimatedVisibility(
        visible = searching,
        enter = expandVertically(animationSpec = tween(PROGRESS_MILLIS, easing = EaseOutCubic)) +
            fadeIn(animationSpec = tween(PROGRESS_MILLIS)),
        exit = shrinkVertically(animationSpec = tween(PROGRESS_MILLIS, easing = EaseOutCubic)) +
            fadeOut(animationSpec = tween(PROGRESS_MILLIS)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(PROGRESS_PADDING)) {
            SearchProgressLine(color = appColors.ink)
        }
    }
}

/**
 * The hint, or the rows, mirroring `_results` in `legacy/flutter/lib/main.dart:2420`.
 *
 * The crossfade is Flutter's `AnimatedSwitcher(duration: 220)` over [SearchSheetState.resultsIdentity]
 * — the same thing the `ValueKey` said, as an `AnimatedSwitcher` compares children by key. So it
 * fires on both of the transitions Flutter animated: crossing between "nothing submitted" and "has
 * results", and crossing to a different query or mode, which is a new key and so a new child. Before,
 * targeting only [SearchSheetState.showsHint] dropped the second case.
 */
@Composable
private fun SearchResults(
    state: SearchSheetState,
    readingMode: ReadingMode,
    locale: AppLocale,
    onSignIn: () -> Unit,
    onOpenVerse: (ScriptureHit) -> Unit,
    authError: String?,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = state.resultsIdentity,
        modifier = modifier,
        animationSpec = tween(RESULTS_MILLIS, easing = EaseOutCubic),
        label = "searchResults",
    ) { identity ->
        if (state.showsHint) {
            SearchHint()
        } else {
            SearchResultList(
                identity = identity,
                state = state,
                readingMode = readingMode,
                locale = locale,
                onSignIn = onSignIn,
                onOpenVerse = onOpenVerse,
                authError = authError,
            )
        }
    }
}

/**
 * The rows [searchRows] asks for.
 *
 * A `LazyColumn` where Flutter had an eager `ListView(children: …)`: the AI half can resolve to more
 * verses than the sheet draws, and each of those is a border, up to three text blocks and a semantics
 * node. The rows are keyed by position rather than by content because Flutter gave its tiles no keys
 * at all and a model can legitimately propose the same verse twice — two overlapping ranges both
 * resolving to `JHN 3:16` — so a reference is not a unique key.
 *
 * [identity] is [SearchSheetState.resultsIdentity], the `ValueKey` Flutter gave the `ListView`, and it
 * is what the scroll position is remembered against: a new query or a new mode means a new list that
 * opens at the top, which is what the key did when it threw the old `ScrollController` away.
 */
@Composable
private fun SearchResultList(
    identity: String,
    state: SearchSheetState,
    readingMode: ReadingMode,
    locale: AppLocale,
    onSignIn: () -> Unit,
    onOpenVerse: (ScriptureHit) -> Unit,
    authError: String?,
) {
    val rows = searchRows(state)
    val listState = remember(identity) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = RESULTS_PADDING,
    ) {
        itemsIndexed(rows) { _, row ->
            SearchRowContent(
                row = row,
                state = state,
                readingMode = readingMode,
                locale = locale,
                onSignIn = onSignIn,
                onOpenVerse = onOpenVerse,
                authError = authError,
            )
        }
    }
}

/** One row of [SearchResultList]. */
@Composable
private fun SearchRowContent(
    row: SearchRow,
    state: SearchSheetState,
    readingMode: ReadingMode,
    locale: AppLocale,
    onSignIn: () -> Unit,
    onOpenVerse: (ScriptureHit) -> Unit,
    authError: String?,
) {
    when (row) {
        is SearchRow.Panel -> SearchPanelContent(
            panel = row.panel,
            state = state,
            onSignIn = onSignIn,
            authError = authError,
        )

        is SearchRow.TraditionalCount -> SearchSectionLabel(
            stringResource(R.string.traditional_result_count, traditionalResultCount(state.traditionalHits.size)),
        )

        is SearchRow.AiCount -> SearchSectionLabel(
            stringResource(R.string.ai_result_count, state.aiHits.size),
        )

        is SearchRow.Tile -> SearchHitTile(
            hit = row.hit,
            readingMode = readingMode,
            locale = locale,
            reason = row.reason,
            onClick = { onOpenVerse(row.hit) },
        )

        is SearchRow.NoResults -> NoResultsMessage()
    }
}

/** One bordered box. */
@Composable
private fun SearchPanelContent(panel: SearchPanel, state: SearchSheetState, onSignIn: () -> Unit, authError: String?) {
    when (panel) {
        SearchPanel.SearchStatus -> SearchStatusPanel()
        SearchPanel.SignIn -> SignInPanel(onSignIn = onSignIn, authError = authError)
        // The overview prose. Flutter drew it through `AppMarkdown`, which arrives with Phase 4's
        // commonmark renderer; until then it is the same prose as plain text, which is what the prompt
        // asks the model for anyway ("Return only the short overview prose").
        SearchPanel.Overview -> SearchSection(stringResource(R.string.ai_overview)) {
            Text(text = state.overview.orEmpty(), color = appColors.ink)
        }

        is SearchPanel.OverviewLoading -> SearchSection(stringResource(panel.title)) {
            AiSearchLoading(stringResource(panel.label))
        }

        SearchPanel.OverviewFailed -> OverviewFailedPanel()
        is SearchPanel.ScriptureLoading -> SearchSection(stringResource(panel.title)) {
            AiSearchLoading(stringResource(panel.label))
        }

        is SearchPanel.ScriptureFailed -> ReferencesFailedPanel(failure = panel.failure)
    }
}

/**
 * The count inside `traditional_result_count`, which is Flutter's
 * `'${length}${length == 80 ? '+' : ''}'`.
 *
 * The `+` says the list was cut at the database's limit rather than that it happens to be this long,
 * and it is the only place the sheet admits a search may have had more to give.
 */
internal fun traditionalResultCount(count: Int): String =
    if (count == SEARCH_RESULT_LIMIT) "$count$TRUNCATED_COUNT_SUFFIX" else "$count"

/** The two segments of the mode control, in the order `SearchMode` declares them. */
@Composable
private fun searchModeChoices(): List<AppChoice<SearchMode>> = listOf(
    AppChoice(SearchMode.TRADITIONAL, stringResource(R.string.traditional_search)),
    AppChoice(SearchMode.AI, stringResource(R.string.ai_search)),
)

/** `SafeArea(minimum: EdgeInsets.all(10))`, and `constraints: BoxConstraints(maxWidth: 760)`. */
private val SHEET_INSET = 10.dp
private val SHEET_MAX_WIDTH = 760.dp

/** The surface's own `Border.all(color: colors.line)`. */
private val SHEET_BORDER_WIDTH = 1.dp

/** `EdgeInsets.fromLTRB(10, 10, 8, 10)` around the header row. */
private val HEADER_PADDING = PaddingValues(start = 10.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)

/** `SizedBox(width: 7)` before the forward button, `SizedBox(width: 3)` before the close button. */
private val SEARCH_BUTTON_GAP = 7.dp
private val CLOSE_BUTTON_GAP = 3.dp

/** `EdgeInsets.fromLTRB(10, 0, 10, 8)` around the mode control, and its `height: 40`. */
private val MODE_CONTROL_PADDING = PaddingValues(start = 10.dp, end = 10.dp, bottom = 8.dp)
private val MODE_CONTROL_HEIGHT = 40.dp

/** `EdgeInsets.fromLTRB(10, 0, 10, 7)` around the progress line. */
private val PROGRESS_PADDING = PaddingValues(start = 10.dp, end = 10.dp, bottom = 7.dp)

/** `padding: EdgeInsets.fromLTRB(10, 8, 10, 20)` on the results list. */
private val RESULTS_PADDING = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 20.dp)

/** Flutter's 220 ms keyboard pad, 240 ms progress line and 220 ms results crossfade. */
private const val KEYBOARD_MILLIS = 220
private const val PROGRESS_MILLIS = 240
private const val RESULTS_MILLIS = 220

/** The `+` in `Text results · 80+`. */
private const val TRUNCATED_COUNT_SUFFIX = "+"

/** `Curves.easeOutCubic`, the curve all three of Flutter's animations used. */
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
