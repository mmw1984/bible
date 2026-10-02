package com.marcow.bible.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The books arriving rather than appearing, ported a second time.
 *
 * This is `_ScrollAwareEntrance` at `legacy/flutter/lib/main.dart:1624` again: Flutter's
 * `_LibraryPanel` wrapped every row in the same widget it wrapped every verse in, and
 * `feature/reader` already has a copy. Features do not depend on each other (`NATIVE_PLAN.md` §2.2),
 * so the panel carries its own rather than reaching across — the shared home for this is the design
 * system, which is not where Phase 2 said to put it.
 *
 * The gate is the same question as the reader's, asked the same way round: a `LazyColumn` composes a
 * row when it scrolls into view, so the entrance covers the rows laid out when the panel opens and
 * everything the reader scrolls to afterwards is simply there.
 */

/** How long a book takes to arrive, `260 + staggerIndex.clamp(0, 12) * 18`. */
fun rowEntranceDurationMillis(staggerIndex: Int): Int =
    RowBaseMillis + staggerIndex.coerceIn(0, RowMaxDurationStagger) * RowStaggerMillis

/** When this book starts moving, `staggerIndex.clamp(0, 10) * .035` of its own duration. */
fun rowEntranceDelayMillis(staggerIndex: Int): Int {
    val staggered = staggerIndex.coerceIn(0, RowMaxDelayStagger)
    return (staggered * RowDelayFraction * rowEntranceDurationMillis(staggered)).toInt()
}

/** Whether the rows being composed right now belong to the panel's first pass. */
class RowGate(
    /**
     * Whether the gate still admits a stagger. Closed once the panel's first frame has been laid
     * out, or from the start when the panel is being previewed rather than played — see
     * `LibraryPanel`. Written from a coroutine and read during composition, so it is deliberately
     * not snapshot state.
     *
     * A constructor parameter rather than a second field, so the panel's one decision stays one
     * line and the reader's `EntranceGate` stays the same shape.
     */
    @Volatile var open: Boolean = true,
)

/** Fades and raises [content] into place, [staggerIndex] rows after the first one. */
@Composable
fun RowEntrance(staggerIndex: Int, gate: RowGate, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (gate.open) 0f else 1f) }
    LaunchedEffect(progress) {
        if (!gate.open) return@LaunchedEffect
        val wait = rowEntranceDelayMillis(staggerIndex)
        if (wait > 0) delay(wait.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (rowEntranceDurationMillis(staggerIndex) - wait).coerceAtLeast(1),
                easing = EaseOutCubic,
            ),
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = RowRiseDp.dp.toPx() * (1f - progress.value)
        },
    ) {
        content()
    }
}

private const val RowBaseMillis = 260
private const val RowMaxDurationStagger = 12
private const val RowMaxDelayStagger = 10
private const val RowStaggerMillis = 18
private const val RowDelayFraction = 0.035
private const val RowRiseDp = 8f
