package com.marcow.bible.feature.navigation

import androidx.compose.animation.core.CubicEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isDark
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.theme.AppColors
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.model.NavBarStyle
import kotlin.math.abs

/**
 * The floating bottom pill, mirroring `AppNavBar` in `legacy/flutter/lib/app_navbar.dart:69`.
 *
 * The pill is a frosted surface whose fill follows [style] exactly as Flutter chose it — an opaque
 * `surfaceRaised` for the solid style and a translucent `surface` for the blur one — carrying a
 * white indicator that both a tap and a horizontal drag move, and whose labels and icons darken
 * continuously while a drag is in flight.
 *
 * Every measurement and every gesture-to-index decision is delegated to the pure functions in
 * [AppNavBar.kt]: those are the parts the Flutter tests assert on, and the parts worth testing
 * without a device.
 *
 * @param items the tabs to show, filtered by `navBarItems(showDevotion)`.
 * @param selectedIndex the selected tab. It is coerced into range, so a shrinking [items] list —
 *   which is what hiding Devotions does — can never point outside it.
 * @param onSelected the tab a tap or a released drag settled on. It is never called with the tab
 *   that was already selected, because the Flutter build returned early in both cases.
 */
@Composable
fun AppFloatingNavBar(
    items: List<AppNavBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.MATERIAL_BLUR,
) {
    if (items.isEmpty()) return
    val colors = appColors
    val shape = RoundedCornerShape(AppNavBarHeight / 2)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pillWidth = navBarPillWidth(maxWidth, items.size)
            AppControlSurface(
                modifier = Modifier
                    .width(pillWidth)
                    .height(AppNavBarHeight)
                    // `BoxShadow(black @ .28 dark / .08 light, blurRadius: 14, offset: (0, 4))`.
                    // Compose has no CSS box-shadow and its shadow offset tracks the elevation, so
                    // the elevation carries the 4 px offset and the tint carries the darkness.
                    .shadow(
                        elevation = PillShadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = pillShadowColor(colors),
                        spotColor = pillShadowColor(colors),
                    ),
                shape = shape,
                // Flutter filled the solid pill with `surfaceRaised` and the blurred one with a
                // translucent `surface`, which is exactly the pair AppControlSurface calls
                // "emphasized" and its default frosted tint — and letting the component pick means
                // the pill picks up the API 31+ blur and the pre-31 scrim with it.
                emphasized = style == NavBarStyle.MATERIAL,
                // Flutter stroked the pill as a foreground decoration at a flat `.72` in both styles,
                // because a background border would lose its inner half to the blur clip. Spelling
                // the alpha out keeps the solid style at `.72` too: `AppControlSurface` re-applies
                // it when it blurs.
                borderColor = colors.line.copy(alpha = PillStrokeAlpha),
            ) {
                NavBarContent(
                    items = items,
                    pillWidth = pillWidth,
                    selectedIndex = selectedIndex,
                    onSelected = onSelected,
                )
            }
        }
    }
}

/**
 * The body of the pill: the draggable indicator underneath, the tabs on top of it.
 *
 * The indicator has two sources, because Flutter had the same two: while a drag is in flight it
 * tracks the finger with no animation at all, and the rest of the time it animates to the selected
 * tab. Deriving the animated value from [selectedIndex] rather than from the drag means a release
 * never has to wait for the host to echo the new index back before the pill starts moving.
 */
@Composable
private fun NavBarContent(
    items: List<AppNavBarItem>,
    pillWidth: Dp,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    // Hiding Devotions shortens the list without renaming a tab, so the host's index is coerced.
    val effectiveIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val itemWidth = navBarItemWidth(pillWidth, items.size)
    val itemWidthPx = with(density) { itemWidth.toPx() }

    val settled = animateFloatAsState(
        targetValue = effectiveIndex.toFloat(),
        animationSpec = tween(durationMillis = IndicatorAnimationMillis, easing = IndicatorEasing),
        label = "navBarIndicator",
    )
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(effectiveIndex.toFloat()) }
    val indicatorPosition = if (dragging) dragPosition else settled.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppNavBarHeight)
            // One gesture handler for the whole pill, exactly like Flutter's single `GestureDetector`
            // carrying both `onTapUp` and the `onHorizontalDrag*` callbacks.
            .pointerInput(items.size, effectiveIndex, itemWidthPx) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The drag starts from wherever the indicator is *visually*, which is what
                    // `_dragStartOffset = widget.index` under a running animation produced.
                    val startPosition = settled.value
                    val startX = down.position.x
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            val target = if (moved) {
                                navBarIndexForRelease(dragPosition, items.size)
                            } else {
                                navBarIndexForTap(change.position.x, itemWidthPx, items.size)
                            }
                            dragging = false
                            if (target != effectiveIndex) onSelected(target)
                            break
                        }
                        if (!moved && abs(change.position.x - startX) > slop) {
                            moved = true
                            dragging = true
                            dragPosition = startPosition
                        }
                        if (moved) {
                            dragPosition = navBarOffsetForDrag(
                                startPosition = startPosition,
                                deltaX = change.position.x - startX,
                                itemWidth = itemWidthPx,
                                itemCount = items.size,
                            )
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .padding(start = navBarIndicatorOffset(indicatorPosition, itemWidth.value).dp)
                .width(itemWidth - IndicatorInset.dp * 2)
                .height(AppNavBarHeight - IndicatorInset.dp * 2)
                .shadow(
                    elevation = IndicatorShadowElevation,
                    shape = RoundedCornerShape((AppNavBarHeight - IndicatorInset.dp * 2) / 2),
                    clip = false,
                    ambientColor = IndicatorShadowColor,
                    spotColor = IndicatorShadowColor,
                )
                .background(IndicatorFill),
        )

        Row(
            modifier = Modifier
                .padding(horizontal = PillHorizontalPadding.dp)
                .fillMaxWidth()
                .height(AppNavBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavBarTab(
                    item = item,
                    modifier = Modifier.weight(1f),
                    selected = index == effectiveIndex,
                    effectiveSelectedness = if (dragging) {
                        navBarSelectedness(indicatorPosition, index)
                    } else if (index == effectiveIndex) {
                        1f
                    } else {
                        0f
                    },
                )
            }
        }
    }
}

/**
 * One tab: a glyph over its label, both darkening with [effectiveSelectedness].
 *
 * The tab carries no tap handler of its own — the pill handles the whole gesture — so it only
 * contributes the `Semantics(button: true, selected: …, label: …)` that Flutter put on it.
 */
@Composable
private fun NavBarTab(
    item: AppNavBarItem,
    effectiveSelectedness: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    // `Color.lerp(Colors.black87, colors.ink, t)` verbatim — including the fact that the dark end of
    // the interpolation stays black in both themes, which is what the Flutter build shipped.
    val tint = lerp(UnselectedInk, colors.ink, effectiveSelectedness)
    Column(
        modifier = modifier
            .height(AppNavBarHeight)
            .semantics {
                this.selected = selected
                this.role = Role.Tab
                contentDescription = item.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppGlyphView(glyph = item.glyph, color = tint, size = 19.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (effectiveSelectedness > SelectednessThreshold) FontWeight.W600 else FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The pill's drop shadow, heavier in dark mode because the canvas is nearly black there. */
private fun pillShadowColor(colors: AppColors): Color =
    Color.Black.copy(alpha = if (colors.canvas.isDark()) PillShadowDarkAlpha else PillShadowLightAlpha)

/** `Colors.black87`, the dark end of the icon and label interpolation. */
private val UnselectedInk = Color(0xDD000000)

/** The indicator is a pure white fill in both themes, as it was in Flutter. */
private val IndicatorFill = Color.White

/** 240 ms `easeOutCubic`, the `AnimatedPositioned` duration when a drag is not in flight. */
private const val IndicatorAnimationMillis = 240

/** `effectiveSelectedness > .5` swapped the label weight, as in `_buildItem`. */
private const val SelectednessThreshold = 0.5f

/** The foreground stroke alpha Flutter pinned the pill to in both styles. */
private const val PillStrokeAlpha = 0.72f

private const val PillShadowLightAlpha = 0.08f
private const val PillShadowDarkAlpha = 0.28f

private val PillShadowElevation = 4.dp

/** The indicator's `blurRadius: 8, offset: (0, 2)`, as an elevation of the same offset. */
private val IndicatorShadowElevation = 2.dp
private val IndicatorShadowColor = Color.Black.copy(alpha = 0.08f)

/** `Curves.easeOutCubic`, the indicator's curve. */
private val IndicatorEasing = CubicEasing(0.215f, 0.61f, 0.355f, 1f)
