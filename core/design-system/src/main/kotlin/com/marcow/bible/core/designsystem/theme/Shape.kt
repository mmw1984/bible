package com.marcow.bible.core.designsystem.theme

import androidx.compose.animation.core.CubicEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * `springCurve = Cubic(0.16, 1, 0.3, 1)` in `legacy/flutter/lib/app_theme.dart`.
 *
 * The Flutter app used one curve for the theme crossfade (420 ms) and for the segmented control's
 * selection pill (260 ms), so the easing is a single token here too. Compose has no `Cubic` type and
 * this curve has no physical spring to fit, so it stays a bezier easing.
 */
val SpringCurve: Easing = CubicEasing(0.16f, 1f, 0.3f, 1f)

/** The theme crossfade duration, `themeAnimationDuration` in `main.dart`. */
const val THEME_ANIMATION_MILLIS = 420

/** The duration the segmented control's selection pill takes, from `AppSegmented`. */
const val SEGMENT_ANIMATION_MILLIS = 260

/** The press feedback duration, from `AppTap`. */
const val TAP_ANIMATION_MILLIS = 120

fun themeAnimationSpec() = tween<Float>(durationMillis = THEME_ANIMATION_MILLIS, easing = SpringCurve)

fun segmentAnimationSpec() = tween<Float>(durationMillis = SEGMENT_ANIMATION_MILLIS, easing = SpringCurve)

fun tapAnimationSpec() = tween<Float>(durationMillis = TAP_ANIMATION_MILLIS, easing = TAP_EASING)

/** `Curves.easeOutCubic`, the press curve in `AppTap`. */
private val TAP_EASING = CubicEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * The Material shapes for a set of [AppRadii].
 *
 * The Flutter build drew every surface itself with `BorderRadius.circular`, so the Material shapes
 * only matter for components that are not app-drawn (a dialog, a text field's underline). They
 * follow the same radii so the two never disagree.
 */
fun appShapes(radii: AppRadii): Shapes = Shapes(
    extraSmall = RoundedCornerShape(radii.compact),
    small = RoundedCornerShape(radii.compact),
    medium = RoundedCornerShape(radii.control),
    large = RoundedCornerShape(radii.surface),
    extraLarge = RoundedCornerShape(radii.screen),
)

/** Sized in dp the same way the Flutter layout constants were. */
internal val ControlHeight = 42.dp
