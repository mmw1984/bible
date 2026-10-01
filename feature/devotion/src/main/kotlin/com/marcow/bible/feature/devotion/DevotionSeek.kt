package com.marcow.bible.feature.devotion

/**
 * YouTube-app-style stacked double-tap seek, replacing `clampSeekTarget` in
 * `legacy/flutter/lib/devotion_youtube_player.dart:18`.
 *
 * Each double-tap seeks relative to the position at tap time, so stacking (−10 → −20) falls out of
 * asking for the position again rather than of tracking an offset here; this only clamps the answer
 * into `[0, total]`.
 *
 * It is a plain function rather than part of the player because the player that uses it needs a real
 * WebView and this does not — which is why the Dart build extracted it for the same reason and what
 * `legacy/flutter/test/youtube_seek_test.dart` tests.
 *
 * The two guards it carries are the ones a reader notices:
 *
 *  - **A total of zero seeks to the start rather than to `NaN`.** A duration is only known once the
 *    video has loaded, and a live video reports `0` for it; without the guard, `(current + delta)
 *    .coerceIn(0, 0)` is a seek past the end of a video that has not reported a length yet.
 *  - **The clamp is on the sum, not on each end.** Stacking ten taps at 295 s lands on 300 s and
 *    stays there, rather than every tap past the end being measured from a position the reader never
 *    reached.
 */
internal fun clampSeekTarget(current: Double, total: Double, delta: Double): Double =
    if (total <= 0) 0.0 else (current + delta).coerceIn(0.0, total)