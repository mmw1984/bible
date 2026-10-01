package com.marcow.bible.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.icons.drawAppGlyph
import com.marcow.bible.core.designsystem.theme.appColors

/** A glyph at its natural size, mirroring `AppGlyphView`. */
@Composable
fun AppGlyphView(
    glyph: AppGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .drawBehind { drawAppGlyph(glyph, color) },
    )
}

/**
 * A glyph on a frosted surface, mirroring `AppGlyphButton`.
 *
 * The glyph is tinted from [appColors] exactly as in Flutter: muted when there is nothing to tap,
 * canvas when the control is selected (it sits on an ink fill), ink otherwise.
 */
@Composable
fun AppGlyphButton(
    glyph: AppGlyph,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glyphSize: Dp = 19.dp,
    fill: Color? = null,
    border: Color? = null,
    selected: Boolean = false,
    emphasized: Boolean = false,
) {
    val colors = appColors
    AppButton(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        emphasized = emphasized,
        color = fill,
        borderColor = border,
        content = {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center,
            ) {
                AppGlyphView(
                    glyph = glyph,
                    color = when {
                        onClick == null -> colors.muted
                        selected -> colors.canvas
                        else -> colors.ink
                    },
                    size = glyphSize,
                    contentDescription = label,
                )
            }
        },
    )
}
