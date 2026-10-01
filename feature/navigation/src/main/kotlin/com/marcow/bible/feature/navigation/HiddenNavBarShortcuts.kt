package com.marcow.bible.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.icons.AppGlyph

/**
 * The Ask / Devotions shortcuts that stand in for the hidden bar, mirroring the `!showNavbar`
 * branch of the reader's top bar in `legacy/flutter/lib/main.dart:654` and `_IconControlButton`
 * at `legacy/flutter/lib/main.dart:995`.
 *
 * They share the settings gear's styling, which is exactly what [AppGlyphButton] draws: a 40 dp
 * frosted surface with a 19 dp glyph. Devotions stays hidden when the user turned that tab off, so
 * Ask is left on its own rather than centred in the gap the pair would have occupied.
 *
 * The bar itself is not concerned with what these do — it hands over two callbacks — because the
 * Ask and Devotions destinations belong to their own features and this one may not reach them.
 */
@Composable
fun HiddenNavBarShortcuts(
    showDevotion: Boolean,
    onAsk: () -> Unit,
    onDevotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ShortcutGap),
    ) {
        AppGlyphButton(
            glyph = AppGlyph.CHAT,
            label = stringResource(R.string.tab_ask),
            onClick = onAsk,
            size = ShortcutSize,
            glyphSize = ShortcutGlyphSize,
        )
        if (showDevotion) {
            AppGlyphButton(
                glyph = AppGlyph.SUN,
                label = stringResource(R.string.tab_devotion),
                onClick = onDevotion,
                size = ShortcutSize,
                glyphSize = ShortcutGlyphSize,
            )
        }
    }
}

/** The 40 x 40 control Flutter measured `_IconControlButton` at. */
private val ShortcutSize = 40.dp

/** The 19 px icon inside it. */
private val ShortcutGlyphSize = 19.dp

/** The 9 px gap between the two shortcuts and between them and the search button. */
private val ShortcutGap = 9.dp
