package com.marcow.bible.feature.reader

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
 * A verse arriving rather than appearing, mirroring `_ScrollAwareEntrance` in
 * `legacy/flutter/lib/main.dart:1624`.
 *
 * `NATIVE_PLAN.md` §1.2 names this as part of the reader: a chapter is not painted all at once, it
 * comes in as a short stagger of fades that rise 8 px, so a page of scripture settles instead of
 * landing. Only the verses are wrapped — Flutter's header and chapter links were plain slivers.
 *
 * The stagger is capped, and the cap is the point. Flutter clamped the index twice: the interval's
 * begin at ten items and the duration at twelve, so the twelfth verse onward all take the same 476
 * ms. A chapter of 176 verses is not a queue of 176 fades, and the numbers are kept as they were
 * rather than made proportional.
 *
 * The two clamps stop in different places, and that is not a detail. Flutter clamped the *fraction*,
 * not the wait it produces, so the wait keeps creeping up while the duration it is a share of is
 * still growing — it holds at 166 ms from the twelfth verse on, not at the tenth's 154 ms.
 *
 * Why a gate. Flutter asked `Scrollable.recommendDeferredLoadingForContext` whether to skip the
 * animation, and the answer was "yes" for every item the reader had not scrolled to yet — animating
 * two hundred offscreen verses is work nobody sees. A `LazyColumn` composes an item when it scrolls
 * into view, so the same question has to be asked the other way round: is this item part of the
 * chapter's first pass? A gate is that answer. The screen opens a fresh gate for every chapter — see
 * `ReaderScreen` — and closes it one frame later, so the verses laid out when the chapter appears
 * arrive one after another and everything the reader scrolls to afterwards is simply there.
 *
 * A plain gate rather than one backed by snapshot state on purpose: an item must not be recomposed
 * when the gate closes underneath it, or the entrance it is halfway through would be cancelled and
 * the verse would jump to full opacity mid-fade.
 */

/**
 * How long a verse takes to arrive, `Duration(milliseconds: 260 + staggerIndex.clamp(0, 12) * 18)`.
 */
fun entranceDurationMillis(staggerIndex: Int): Int =
    EntranceBaseMillis + staggerIndex.coerceIn(0, EntranceMaxDurationStagger) * EntranceStaggerMillis

/**
 * When this verse starts moving, `Interval(staggerIndex.clamp(0, 10) * .035, 1)`.
 *
 * Flutter's `Interval` squeezed `easeOutCubic` into the tail of the duration rather than delaying
 * the animation, so the wait is [entranceDurationMillis] multiplied by the fraction and the fade
 * gets what is left — the two functions are only ever read together, in that order.
 *
 * The fraction is clamped, the duration it multiplies is not, and the index is passed through
 * unclamped for exactly that reason: `Interval`'s begin is a share of the whole duration, which
 * Flutter kept growing to the twelfth verse. Clamping the index first would flatten the eleventh
 * and twelfth verses onto the tenth's wait, which is not what Flutter drew.
 */
fun entranceDelayMillis(staggerIndex: Int): Int {
    val staggered = staggerIndex.coerceIn(0, EntranceMaxDelayStagger)
    return (staggered * EntranceDelayFraction * entranceDurationMillis(staggerIndex)).toInt()
}

/**
 * Whether the items being composed right now belong to a chapter's first pass.
 *
 * The library draws the same entrance on its own rows — Flutter's `_LibraryPanel` wrapped every one
 * of them in the same widget — and cannot reach this one, because features do not depend on each
 * other (`NATIVE_PLAN.md` §2.2).
 */
class EntranceGate(
    /**
     * Whether the gate still admits a stagger. Closed once the chapter's first frame has been laid
     * out, or from the start when the reader is being previewed rather than played — see
     * [ReaderScreen]. Written from a coroutine and read during composition, so it is deliberately
     * not snapshot state.
     */
    @Volatile var open: Boolean = true,
)

/**
 * Fades and raises [content] into place, [staggerIndex] verses after the first one.
 *
 * A verse composed after [gate] has closed is drawn at full opacity with no animation, which is what
 * `recommendDeferredLoadingForContext` bought in Flutter.
 */
@Composable
fun ScrollAwareEntrance(
    staggerIndex: Int,
    gate: EntranceGate,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Remembered without keys, so the progress an item has already earned survives the gate closing
    // and the item being recomposed for anything else.
    val progress = remember { Animatable(if (gate.open) 0f else 1f) }
    LaunchedEffect(progress) {
        if (!gate.open) return@LaunchedEffect
        val wait = entranceDelayMillis(staggerIndex)
        if (wait > 0) delay(wait.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (entranceDurationMillis(staggerIndex) - wait).coerceAtLeast(1),
                easing = EaseOutCubic,
            ),
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = EntranceRiseDp.dp.toPx() * (1f - progress.value)
        },
    ) {
        content()
    }
}

/** The 260 ms an unstaggered verse takes, before the per-verse 18 ms. */
private const val EntranceBaseMillis = 260

/** `.clamp(0, 12)` on the duration: from the twelfth verse on, every verse takes the same time. */
private const val EntranceMaxDurationStagger = 12

/** `.clamp(0, 10)` on the interval's begin: the *share* stops growing at the tenth verse. */
private const val EntranceMaxDelayStagger = 10

/** The 18 ms each of the first twelve verses adds. */
private const val EntranceStaggerMillis = 18

/** `.035` — the share of its own duration a verse waits before it starts. */
private const val EntranceDelayFraction = 0.035

/** `Offset(0, 8 * (1 - value))`, the distance a verse rises as it fades in. */
private const val EntranceRiseDp = 8f
