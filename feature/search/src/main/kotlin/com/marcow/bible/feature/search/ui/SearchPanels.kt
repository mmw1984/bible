package com.marcow.bible.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.feature.search.domain.ReferenceFailure

/**
 * The four failure panels the sheet can draw, mirroring the `Text` children of `_SearchSection` in
 * `legacy/flutter/lib/main.dart`.
 *
 * Each is a section title and a line of copy and nothing else: no icon, no retry button, because
 * nothing in Flutter had one. The user tries again by re-submitting the same query from the box
 * above, which is the only retry either build offers.
 *
 * Four composables rather than one taking a string, because three of the four are fixed copy and one
 * ([SignInPanel]) is not — resolving the strings here rather than in the sheet is what keeps the
 * sheet from deciding which panel it is drawing.
 */
@Composable
internal fun SearchStatusPanel(modifier: Modifier = Modifier) {
    SearchSection(title = stringResource(R.string.search_status), modifier = modifier) {
        Text(
            text = stringResource(R.string.search_failed),
            color = appColors.muted,
        )
    }
}

/** The `overview_failed` panel, under the `ai_overview` section title. */
@Composable
internal fun OverviewFailedPanel(modifier: Modifier = Modifier) {
    SearchSection(title = stringResource(R.string.ai_overview), modifier = modifier) {
        Text(
            text = stringResource(R.string.overview_failed),
            color = appColors.muted,
        )
    }
}

/**
 * The references failure panel, under the `ai_scripture_results` section title.
 *
 * Two messages for [failure], because the two failures point at two different things.
 * [ReferenceFailure.VERSES] is this device's own Bible database failing to answer a reference the
 * model got right, so telling the user the *AI* search could not be completed would send them to
 * check a network that was never involved.
 */
@Composable
internal fun ReferencesFailedPanel(failure: ReferenceFailure, modifier: Modifier = Modifier) {
    SearchSection(title = stringResource(R.string.ai_scripture_results), modifier = modifier) {
        Text(
            text = referencesFailureMessage(failure),
            color = appColors.muted,
        )
    }
}

/** Which of the two `ai_scripture_results` failure messages [failure] is. */
@Composable
internal fun referencesFailureMessage(failure: ReferenceFailure): String = stringResource(
    when (failure) {
        ReferenceFailure.VERSES -> R.string.verse_results_failed
        ReferenceFailure.REQUEST -> R.string.references_failed
    },
)

/**
 * The `login_to_search` panel: why there are no results, and the button that fixes it.
 *
 * Flutter promised in this panel that the query would be run once the sign-in landed
 * (`pendingCloudSearch`), so the button has to reach the view model's own sign-in arming as well as
 * the host's sign-in flow. Arming that promise is all this sheet owns: the sign-in itself belongs to
 * Phase 4's `OpenRouterAuthManager` and to whoever opens the sheet, so [onSignIn] is the host's
 * callback and the sheet's half of the promise is [SearchViewModel.beginSignIn].
 *
 * [authError] is `BibleAiController.openRouterAuthError`, which was never part of the sheet's own
 * state. The manager that owns it arrives with Phase 4, so it is passed in rather than held here.
 */
@Composable
internal fun SignInPanel(onSignIn: () -> Unit, modifier: Modifier = Modifier, authError: String? = null) {
    val colors = appColors
    SearchSection(title = stringResource(R.string.ai_overview), modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_to_search),
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    lineHeight = PANEL_LINE_HEIGHT,
                )
                SignInAction(onSignIn = onSignIn)
            }
            if (!authError.isNullOrEmpty()) {
                Spacer(Modifier.height(AUTH_ERROR_GAP))
                // Flutter drew this one as a `SelectableText` and the other four as plain `Text`: it
                // is the only line a user may need to copy into a bug report.
                SelectionContainer {
                    Text(
                        text = authError,
                        color = colors.danger,
                        fontSize = 11.sp,
                        lineHeight = SMALL_LINE_HEIGHT,
                    )
                }
            }
        }
    }
}

/**
 * `_InlineAction` with the `login` glyph: an ink pill carrying a canvas-coloured label.
 *
 * An ink fill rather than a bordered surface, which is what makes it the only control in the sheet
 * that reads as a button rather than as a field.
 */
@Composable
private fun SignInAction(onSignIn: () -> Unit) {
    val colors = appColors
    AppTap(
        onClick = onSignIn,
        content = {
            Row(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .background(colors.ink, RoundedCornerShape(appRadii.control))
                    .padding(horizontal = ACTION_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGlyphView(glyph = AppGlyph.LOGIN, color = colors.canvas, size = ACTION_GLYPH_SIZE)
                Spacer(Modifier.width(ACTION_GLYPH_GAP))
                Text(
                    text = stringResource(R.string.login),
                    color = colors.canvas,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                )
            }
        },
    )
}

/**
 * The `search_hint_body` paragraph: what the sheet shows until a query is submitted.
 *
 * Centred and faint, with the whole results area behind it — Flutter's `Center` inside the expanded
 * child, which is why this is given the space rather than sitting under the header.
 */
@Composable
internal fun SearchHint(modifier: Modifier = Modifier) {
    CenteredMessage(
        text = stringResource(R.string.search_hint_body),
        verticalPadding = HINT_PADDING,
        modifier = modifier,
    )
}

/**
 * The `no_results` paragraph: a search that finished and found nothing.
 *
 * [SearchSheetState.showsNoResults] decides whether this is drawn at all — in particular it is *not*
 * drawn for a signed-out AI search, because the sign-in panel above it already explains the absence.
 */
@Composable
internal fun NoResultsMessage(modifier: Modifier = Modifier) {
    CenteredMessage(
        text = stringResource(R.string.no_results),
        verticalPadding = NO_RESULTS_PADDING,
        modifier = modifier,
    )
}

/** The two messages above, which Flutter drew the same way apart from the room they leave. */
@Composable
private fun CenteredMessage(text: String, verticalPadding: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = appColors.faint,
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
            lineHeight = MESSAGE_LINE_HEIGHT,
        )
    }
}

/** `EdgeInsets.all(24)` around the hint, which Flutter centred in the whole results area. */
private val HINT_PADDING = 24.dp

/** `EdgeInsets.symmetric(vertical: 54)` reserved around `no_results`. */
private val NO_RESULTS_PADDING = 54.dp

/** `height: 1.5` at `fontSize: 14`, on both messages and on the sign-in panel's body copy. */
private val MESSAGE_LINE_HEIGHT = 21.sp
private val PANEL_LINE_HEIGHT = 21.sp

/** `height: 1.45` at `fontSize: 11`, and the 10 px Flutter left above the auth error. */
private val SMALL_LINE_HEIGHT = 16.sp
private val AUTH_ERROR_GAP = 10.dp

/** `_InlineAction`'s own metrics: a 38 dp tall pill, a 16 dp glyph and a 7 px gap. */
private val ACTION_PADDING = 12.dp
private val ACTION_GLYPH_SIZE = 16.dp
private val ACTION_GLYPH_GAP = 7.dp
