package com.marcow.bible.feature.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * `Curves.easeOutCubic`, the curve Flutter ran every one of the reader's switches on.
 *
 * The chapter title's `AnimatedSwitcher` and `_VerseRow`'s own `AnimatedSize`/`AnimatedSwitcher` both
 * named the same curve, and Compose has to be handed the control points rather than the name, so it is
 * spelled out once here instead of twice over. The verse action sheet's slide-up named it as well, at
 * `legacy/flutter/lib/main.dart:962`, which is the third Flutter transition in the reader to land on
 * one curve.
 *
 * The chapter picker is the exception and is not on this curve: it ran on `springCurve`, which
 * `core/design-system` already publishes because the app's theme crossfade uses it too.
 */
internal val EaseOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** `Curves.easeOut`, the curve inside the chapter picker fade's `Interval`. */
internal val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * How opaque the chapter picker is [t] of the way through its 320 ms, which is the
 * `Interval(0, .65, curve: Curves.easeOut)` in the `_showChapterPicker` `transitionBuilder`
 * (`legacy/flutter/lib/main.dart:1368`).
 *
 * Flutter faded the bubble in over the first 65% of the transition and then left it fully opaque
 * while the spring carried the scale the remaining 35%. The two are deliberately not the same curve
 * and deliberately not the same length, which is why this is a function of the raw progress rather
 * than a second progress value: the bubble is fully visible well before it has finished growing, and
 * tying its opacity to the growth instead makes the fade arrive *after* the movement it belongs to.
 */
internal fun chapterPickerFade(t: Float): Float {
    val fraction = t.coerceIn(0f, 1f)
    if (fraction >= ChapterPickerFadeEnd) return 1f
    return EaseOut.transform(fraction / ChapterPickerFadeEnd)
}

/** `Interval(0, .65)`, where Flutter stopped fading the chapter picker in. */
private const val ChapterPickerFadeEnd = 0.65f
