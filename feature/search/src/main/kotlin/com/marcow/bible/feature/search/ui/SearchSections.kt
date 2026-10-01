package com.marcow.bible.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii

/**
 * One bordered box in the sheet's results, mirroring `_SearchSection` in `legacy/flutter/lib/main.dart`.
 *
 * Every panel and every progress row in the sheet is one of these, which is what puts the overview,
 * the scripture results and the failure messages on the same axis as each other: Flutter gave them
 * all a title, a fill and a border rather than drawing the AI half to its own rules.
 */
@Composable
internal fun SearchSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.surface)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = SECTION_GAP)
            .background(colors.surface, shape)
            .border(BORDER_WIDTH, colors.line, shape)
            .padding(SECTION_PADDING),
    ) {
        Text(
            text = title,
            color = colors.faint,
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.height(TITLE_GAP))
        content()
    }
}

/**
 * The small heading above a list of hits, mirroring `_SectionLabel` in `legacy/flutter/lib/main.dart`.
 */
@Composable
internal fun SearchSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 9.dp),
        color = appColors.faint,
        fontSize = 10.sp,
        fontWeight = FontWeight.W600,
    )
}

/**
 * One half of an AI search saying it is still out, mirroring `_AiSearchLoading` in
 * `legacy/flutter/lib/main.dart`.
 *
 * One row per request, and that is the whole reason this is a row rather than a single spinner for
 * the search: an AI search runs the overview and the references at once and either can land first, so
 * the sheet draws this under whichever section is still waiting. The two labels — `searching_overview`
 * and `searching_scripture` — are what tell the rows apart; they are passed in rather than resolved
 * here so the two rows cannot drift apart in wording.
 */
@Composable
internal fun AiSearchLoading(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchSpinner(color = appColors.muted, diameter = SPINNER_DIAMETER)
        Spacer(Modifier.width(SPINNER_GAP))
        Text(
            text = label,
            color = appColors.muted,
            fontSize = 12.sp,
        )
    }
}

/** The gap between a section's title and its content: Flutter's `SizedBox(height: 10)`. */
private val TITLE_GAP = 10.dp

/** `_SearchSection`'s `margin: EdgeInsets.only(bottom: 16)` and `padding: EdgeInsets.all(16)`. */
private val SECTION_GAP = 16.dp
private val SECTION_PADDING = PaddingValues(16.dp)
private val BORDER_WIDTH = 1.dp

/** `AppSpinner(color: colors.muted, size: 15)` with a 9 px gap to the label. */
private val SPINNER_DIAMETER = 15.dp
private val SPINNER_GAP = 9.dp
