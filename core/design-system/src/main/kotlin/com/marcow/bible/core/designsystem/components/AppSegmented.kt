package com.marcow.bible.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.designsystem.theme.segmentAnimationSpec

/** One option in an [AppSegmented] control, mirroring `AppChoice`. */
data class AppChoice<T>(val value: T, val label: String, val glyph: AppGlyph? = null)

/**
 * The single-select control the settings screen is built from, mirroring `AppSegmented` in
 * `legacy/flutter/lib/app_ui.dart`.
 *
 * The Flutter version stacked three layers: a sliding pill, a row of labels, and a row of invisible
 * hit targets. That is reproduced literally, because the labels are not inside the moving element
 * (so a label never moves under the finger) and the hit targets span the full height (so the 3 px
 * inset is still tappable).
 */
@Composable
fun <T> AppSegmented(
    choices: List<AppChoice<T>>,
    selected: T,
    onChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    accessibilityLabel: String? = null,
) {
    if (choices.isEmpty()) return
    val colors = appColors
    val radii = appRadii
    val selectedIndex = choices.indexOfFirst { it.value == selected }
    val inset = 3.dp
    val pillShape = RoundedCornerShape((radii.control - inset).coerceAtLeast(0.dp))
    val trackShape = RoundedCornerShape(radii.control)

    AppControlSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics { contentDescription = accessibilityLabel }
                } else {
                    Modifier
                },
            ),
        shape = trackShape,
    ) {
        BoxWithConstraints(modifier = Modifier.height(height)) {
            val innerWidth = (maxWidth - inset * 2) / choices.size
            val hitWidth = maxWidth / choices.size

            val pillLeft by animateDpAsState(
                targetValue = inset + innerWidth * selectedIndex.coerceAtLeast(0),
                animationSpec = segmentAnimationSpec(),
                label = "segmentPillLeft",
            )
            if (selectedIndex >= 0) {
                AppControlSurface(
                    modifier = Modifier
                        .padding(start = pillLeft, top = inset, bottom = inset)
                        .width(innerWidth)
                        .fillMaxHeight(),
                    shape = pillShape,
                    color = colors.surfaceRaised,
                    borderColor = colors.line,
                    selected = true,
                    content = {},
                )
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = inset)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                choices.forEach { choice ->
                    SegmentLabel(
                        choice = choice,
                        active = choice.value == selected,
                        modifier = Modifier.width(innerWidth),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                choices.forEachIndexed { index, choice ->
                    AppTap(
                        onClick = { onChanged(choice.value) },
                        modifier = Modifier
                            .padding(start = hitWidth * index)
                            .width(hitWidth)
                            .fillMaxHeight(),
                        selected = choice.value == selected,
                        inMutuallyExclusiveGroup = true,
                        content = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentLabel(choice: AppChoice<*>, active: Boolean, modifier: Modifier = Modifier) {
    val colors = appColors
    Box(
        modifier = modifier.padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (choice.glyph != null) {
                AppGlyphView(
                    glyph = choice.glyph,
                    color = if (active) colors.ink else colors.muted,
                    size = 15.dp,
                )
            }
            Text(
                text = choice.label,
                // Flutter wrapped this in a `FittedBox(scaleDown)`, so a long label shrinks instead
                // of wrapping or truncating.
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    color = if (active) colors.ink else colors.muted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.W600 else FontWeight.W500,
                ),
            )
            // The trailing gap in Flutter's label row, which is what keeps a glyph-only choice
            // optically centred in its segment.
            if (choice.glyph != null) Spacer(Modifier.size(21.dp))
        }
    }
}
