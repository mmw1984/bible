package com.marcow.bible.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.theme.AppColors
import com.marcow.bible.core.designsystem.theme.appBlurEnabled
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.designsystem.theme.tapAnimationSpec

/**
 * The frosted surface every control in the app is built on, mirroring `AppControlSurface` in
 * `legacy/flutter/lib/app_ui.dart`.
 *
 * Two modes, chosen by the stored navbar style exactly as the Flutter build chose them:
 *
 *  - blur: a translucent tint so the content behind shows through, with a softened border.
 *    The tint alone carries the frosted look: Flutter sampled the backdrop with a `BackdropFilter`,
 *    which blurs what is *behind* the control, while Compose's `Modifier.blur` blurs the layer it
 *    decorates — the control itself, including its glyph and label. Applying it here blurred every
 *    button's own content, so no blur is applied and the surface stays crisp.
 *  - solid: an opaque fill.
 *
 * A selected control is always solid: the Flutter build skipped the blur for `selected` because a
 * selection has to read as a distinct chip against the track it sits in.
 */
@Suppress("LongParameterList")
@Composable
fun AppControlSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    color: Color? = null,
    borderColor: Color? = null,
    padding: Dp? = null,
    horizontalPadding: Dp? = null,
    verticalPadding: Dp? = null,
    selected: Boolean = false,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = appColors
    val radii = appRadii
    val resolvedShape = shape ?: RoundedCornerShape(radii.control)
    val blur = appBlurEnabled && !selected

    val tint = if (blur) blurTint(colors, color, emphasized) else solidTint(colors, color, selected, emphasized)
    val stroke = if (blur) {
        (borderColor ?: colors.line).copy(alpha = BLUR_BORDER_ALPHA)
    } else {
        borderColor ?: colors.line
    }

    Box(
        modifier = modifier
            .clip(resolvedShape)
            .background(tint, resolvedShape)
            .border(BorderStroke(1.dp, stroke), resolvedShape)
            .then(
                when {
                    padding != null -> Modifier.padding(padding)
                    horizontalPadding != null || verticalPadding != null ->
                        Modifier.padding(
                            horizontal = horizontalPadding ?: 0.dp,
                            vertical = verticalPadding ?: 0.dp,
                        )

                    else -> Modifier
                },
            ),
    ) {
        content()
    }
}

/** `_solidTint` in `app_ui.dart`. */
private fun solidTint(colors: AppColors, requested: Color?, selected: Boolean, emphasized: Boolean): Color = when {
    requested != null && requested != Color.Transparent -> requested
    selected -> colors.ink
    emphasized -> colors.surfaceRaised
    else -> colors.surface
}

/** The three translucent cases `AppControlSurface` picks between in blur mode. */
private fun blurTint(colors: AppColors, requested: Color?, emphasized: Boolean): Color = when {
    requested != null && requested != Color.Transparent -> requested.copy(alpha = BLUR_TINT_ALPHA)
    emphasized -> colors.surfaceRaised.copy(alpha = BLUR_EMPHASIZED_ALPHA)
    else -> colors.surface.copy(alpha = BLUR_TINT_ALPHA)
}

/**
 * Press feedback for a control, mirroring `AppTap`.
 *
 * The Flutter version scaled to 0.975 and dropped to 0.72 opacity over 120 ms, and it dimmed a
 * disabled control to 0.42 outright.
 */
@Composable
fun Modifier.appTapFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    scaleOnPress: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = tapAnimationSpec(),
        label = "appTapScale",
    )
    val opacity by animateFloatAsState(
        targetValue = if (!enabled) {
            DISABLED_ALPHA
        } else if (pressed) {
            PRESSED_ALPHA
        } else {
            1f
        },
        animationSpec = tapAnimationSpec(),
        label = "appTapAlpha",
    )
    return this
        .then(if (scaleOnPress) Modifier.scale(scale) else Modifier)
        .alpha(opacity)
}

/** A tappable control, mirroring `AppTap`. */
@Composable
fun AppTap(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    inMutuallyExclusiveGroup: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = enabled && onClick != null
    Box(
        modifier = modifier
            .appTapFeedback(interactionSource, clickable)
            .then(
                if (clickable) {
                    // The indication comes from `LocalIndication`, which Material 3 sets to a ripple,
                    // so there is no indication to spell out here.
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (selected == null && !inMutuallyExclusiveGroup) {
                    Modifier
                } else {
                    Modifier.semantics { this.selected = selected ?: false }
                },
            ),
    ) {
        content()
    }
}

/** A tappable control on a frosted surface, mirroring `AppButton`. */
@Suppress("LongParameterList")
@Composable
fun AppButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    emphasized: Boolean = false,
    shape: Shape? = null,
    color: Color? = null,
    borderColor: Color? = null,
    padding: Dp? = null,
    horizontalPadding: Dp? = null,
    verticalPadding: Dp? = null,
    content: @Composable () -> Unit,
) {
    AppControlSurface(
        modifier = modifier,
        shape = shape,
        color = color,
        borderColor = borderColor,
        padding = padding,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        selected = selected,
        emphasized = emphasized,
    ) {
        AppTap(
            onClick = onClick,
            enabled = enabled,
            selected = selected,
            content = content,
        )
    }
}

private const val BLUR_TINT_ALPHA = 0.42f
private const val BLUR_EMPHASIZED_ALPHA = 0.55f
private const val BLUR_BORDER_ALPHA = 0.72f
private const val PRESSED_SCALE = 0.975f
private const val PRESSED_ALPHA = 0.72f
private const val DISABLED_ALPHA = 0.42f
