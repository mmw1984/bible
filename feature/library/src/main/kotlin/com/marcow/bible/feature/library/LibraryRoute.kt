package com.marcow.bible.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.theme.SpringCurve
import com.marcow.bible.core.model.ReadingMode
import kotlinx.coroutines.launch

/**
 * The library over the reader, replacing the `_LibraryRoute` pushed at `legacy/flutter/lib/main.dart:1882`.
 *
 * **It is drawn rather than navigated to.** Flutter pushed a `PageRoute`, which brought a route's
 * lifecycle, a back gesture and a place in the history for free. There is no host to push one onto
 * yet — `app`'s activity is still the Phase 0 placeholder — and features cannot depend on each other
 * to host each other's destinations (`NATIVE_PLAN.md` §2.2), so the sheet draws itself: it covers the
 * reader, takes the back press, and asks to be removed. When a host does exist this composable is
 * what a `NavGraphBuilder` extension would wrap, and the entrance below is the transition such an
 * extension would not have to write.
 *
 * **It arrives from the left and leaves the way it came.** 420 ms on the spring curve going in, 300 ms
 * on `easeInOutCubic` coming out, sliding a twelfth of its own width — Flutter's `Offset(-.12, 0)`, a
 * fraction of the sheet rather than of the window. Going out, the sheet is not removed until the
 * animation has run: Flutter's `reverseTransitionDuration` was 300 ms of the reader being uncovered
 * rather than of nothing at all, and dropping the sheet on the first frame would shorten it to a
 * flash.
 *
 * **The barrier is a barrier.** `barrierDismissible` and `barrierLabel` were two properties of the
 * route; here the 62% black scrim and the "Close book library" label are the scrim itself, and the
 * tap, the back press and the close button all run the same dismissal.
 */
@Composable
fun LibraryRoute(
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val progress = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val closeLabel = stringResource(R.string.close_library)
    val dismiss: () -> Unit = {
        // Guarded on intent rather than on whether an animation happens to be running, so a tap
        // during the 420 ms entrance is not swallowed: the reverse picks up from wherever the sheet
        // had got to, which is what popping a half-arrived route does.
        if (!dismissing) {
            dismissing = true
            scope.launch { animateOut(progress, onDismiss) }
        }
    }

    LaunchedEffect(progress) { progress.animateTo(1f, tween(EnterMillis, easing = SpringCurve)) }
    BackHandler(onBack = dismiss)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                // No indication: Flutter's barrier was a plain tappable sheet with nothing to press,
                // and a ripple would be the only thing moving under a finger that is on its way out.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                )
                .semantics { contentDescription = closeLabel },
        )
        LibraryPanel(
            state = state,
            readingMode = readingMode,
            selectedBookId = selectedBookId,
            onBookSelected = { book ->
                // Flutter popped first and selected second, so the reader was already moving as the
                // sheet left. Dismissal is asynchronous here, which makes that ordering the default
                // rather than something to arrange.
                dismiss()
                onBookSelected(book.id)
            },
            onDismiss = dismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(libraryPanelWidth(windowWidth))
                .graphicsLayer {
                    // A fraction of the sheet's own width, the way `SlideTransition` read
                    // `Offset(-.12, 0)`. The measured width is used rather than the window's, because
                    // the two differ on a tablet — the panel is capped at 440.
                    translationX = -PanelSlideFraction * size.width * (1f - progress.value)
                    alpha = progress.value
                },
        )
    }
}

/** The way out, run before the host is asked to remove the sheet. */
private suspend fun animateOut(progress: Animatable<Float, *>, onDismiss: () -> Unit) {
    progress.animateTo(0f, tween(ExitMillis, easing = ExitCurve))
    onDismiss()
}

/** `transitionDuration` — 420 ms in, on the same curve as the theme crossfade. */
private const val EnterMillis = 420

/** `reverseTransitionDuration`. */
private const val ExitMillis = 300

/** `Offset(-.12, 0)` — how far to the left of where it ends up the sheet starts. */
private const val PanelSlideFraction = 0.12f

/** `Colors.black.withValues(alpha: .62)`. */
private val ScrimColor = Color(0f, 0f, 0f, 0.62f)

/** `Curves.easeInOutCubic`, the sheet's reverse curve. */
private val ExitCurve = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)
