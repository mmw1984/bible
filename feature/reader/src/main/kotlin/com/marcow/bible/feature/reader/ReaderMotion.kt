package com.marcow.bible.feature.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * `Curves.easeOutCubic`, the curve Flutter ran every one of the reader's switches on.
 *
 * The chapter title's `AnimatedSwitcher` and `_VerseRow`'s own `AnimatedSize`/`AnimatedSwitcher` both
 * named the same curve, and Compose has to be handed the control points rather than the name, so it is
 * spelled out once here instead of twice over. The spring curve Flutter used for the chapter picker is
 * `SpringCurve`, which `core/design-system` already publishes because the app's theme crossfade uses it
 * too.
 */
internal val EaseOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
