package com.marcow.bible.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.theme.SpringCurve
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ReadingMode
import kotlinx.coroutines.launch

/** The route the library sheet is registered under, for a host that navigates to it by name. */
const val LibraryDestination = "library"

/**
 * The library sheet as a destination, replacing the `_LibraryRoute` pushed at
 * `legacy/flutter/lib/main.dart:1882` — the "`LibraryRoute` (`navigation-compose`)" of
 * `NATIVE_PLAN.md:540`, which is the whole of what Phase 2 asks of `feature/library`.
 *
 * The route in Flutter brought a lifecycle, a place in the history and a back gesture for free, and
 * [androidx.navigation.compose.composable] is the same three things. So the entrance and the exit are
 * declared here as the destination's transitions rather than drawn by the sheet: 420 ms in on the
 * spring curve, 300 ms out on `easeInOutCubic`, the whole sheet fading and sliding in from
 * `Offset(-.12, 0)` — a twelfth of the *page*, which on a `NavHost` is the window.
 *
 * Two things the caller has to know, because they are the route's other half and neither can be
 * inferred here:
 *
 * **The reader underneath must not move.** Flutter pushed the sheet over a reader that stayed exactly
 * where it was; a `NavHost` default would run the reader's own enter transition as the sheet leaves.
 * So the destination below this one wants `popEnterTransition` and `popExitTransition` of
 * `EnterTransition.None` / `ExitTransition.None`.
 *
 * **The scrim fades with the sheet rather than on its own curve.** Flutter's barrier was
 * `AnimatedModalBarrier` on `Curves.ease` while the page ran on the spring curve. One transition
 * cannot run two curves over a scrim it does not own, so both run on the spring curve here.
 */
fun NavGraphBuilder.libraryRoute(
    navController: NavHostController,
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel? = null,
) {
    composable(
        route = LibraryDestination,
        enterTransition = { libraryEnter() },
        exitTransition = { libraryExit() },
    ) {
        LibraryDestination(
            navController = navController,
            readingMode = readingMode,
            selectedBookId = selectedBookId,
            onBookSelected = onBookSelected,
            modifier = modifier,
            viewModel = viewModel ?: hiltViewModel(),
        )
    }
}

/**
 * The destination's content, split out so that [hiltViewModel] is only ever called from a
 * composable scope.
 *
 * It takes its view model as a parameter rather than resolving one itself so that the destination can
 * be rendered with a stub, and so the scope it is resolved in is the destination's own back stack
 * entry — the panel outlives nothing, so a view model held by the host `NavHost` would outlive it.
 */
@Composable
private fun LibraryDestination(
    navController: NavHostController,
    readingMode: ReadingMode,
    selectedBookId: String?,
    onBookSelected: (String) -> Unit,
    modifier: Modifier,
    viewModel: LibraryViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LibrarySheet(
        state = state,
        readingMode = readingMode,
        selectedBookId = selectedBookId,
        // The transition above is the animation; the sheet itself is drawn at rest, which is what
        // keeps the two from fighting over the same 420 ms.
        progress = 1f,
        onBookSelected = { book ->
            // Flutter popped first and selected second, so the reader was already moving as the
            // sheet left.
            navController.popBackStack()
            onBookSelected(book.id)
        },
        // The back press is the `NavHost`'s own: it pops this destination, which runs the exit
        // transition above. `LibraryRoute` needs a `BackHandler` for exactly this reason and
        // `LibrarySheet` needs none, or the press would be handled twice.
        onDismiss = { navController.popBackStack() },
        modifier = modifier,
    )
}

/**
 * The library over the reader, replacing the `_LibraryRoute` pushed at `legacy/flutter/lib/main.dart:1882`.
 *
 * **It is drawn rather than navigated to.** Flutter pushed a `PageRoute`, which brought a route's
 * lifecycle, a back gesture and a place in the history for free. There is no host to push one onto
 * yet — `app`'s activity is still the Phase 0 placeholder — so this composable draws itself: it
 * covers the reader, takes the back press, and asks to be removed. It is the same sheet
 * [NavGraphBuilder.libraryRoute] registers, with the entrance that a destination's transition would
 * otherwise have to write, and nothing about it is required once a host does exist.
 *
 * **It arrives from the left and leaves the way it came.** 420 ms on the spring curve going in, 300 ms
 * on `easeInOutCubic` coming out, sliding a twelfth of its own width — Flutter's `Offset(-.12, 0)`, a
 * fraction of the sheet rather than of the window. Going out, the sheet is not removed until the
 * animation has run: Flutter's `reverseTransitionDuration` was 300 ms of the reader being uncovered
 * rather than of nothing at all, and dropping the sheet on the first frame would shorten it to a
 * flash.
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
    val progress = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        // Guarded on intent rather than on whether an animation happens to be running, so a tap
        // during the 420 ms entrance is not swallowed: the reverse picks up from wherever the sheet
        // had got to, which is what popping a half-arrived route does.
        if (!dismissing) {
            dismissing = true
            scope.launch { animateOut(progress, onDismiss) }
        }
    }

    LaunchedEffect(progress) { progress.animateTo(1f, tween(LIBRARY_ARRIVE_MILLIS, easing = SpringCurve)) }
    BackHandler(onBack = dismiss)

    LibrarySheet(
        state = state,
        readingMode = readingMode,
        selectedBookId = selectedBookId,
        progress = progress.value,
        onBookSelected = { book ->
            // Flutter popped first and selected second, so the reader was already moving as the sheet
            // left. Dismissal is asynchronous here, which makes that ordering the default rather than
            // something to arrange.
            dismiss()
            onBookSelected(book.id)
        },
        onDismiss = dismiss,
        modifier = modifier,
    )
}

/**
 * The sheet itself — the scrim, and the panel arriving at [progress] — with every input it needs.
 *
 * [progress] is the route's animation, not the sheet's own: 0 is off the left edge and transparent, 1
 * is in place, and the host owns it because the host is what knows whether the sheet is arriving,
 * leaving or sitting still. A `NavHost` supplies it as a transition and a self-drawn sheet as an
 * `Animatable`, which is why [LibraryRoute] can wrap this rather than be wrapped by it.
 *
 * Focus is dropped as the sheet appears, on Flutter's reasoning: `_openLibrary` unfocused before
 * pushing so that closing the sheet could not hand focus back to a text field and pop the keyboard.
 * It lives here rather than in either host so that neither can forget it.
 *
 * **The barrier is a barrier.** `barrierDismissible` and `barrierLabel` were two properties of the
 * route; here the 62% black scrim and the "Close book library" label are the scrim itself, and the
 * tap, the back press and the close button all run the same dismissal. The scrim holds its full
 * strength for the whole of the panel's arrival: Flutter's barrier was an `AnimatedModalBarrier` on
 * `Curves.ease` and its page was on the spring curve, and one progress cannot run two curves — so
 * this is the one place the two curves are collapsed into the one that matters.
 */
@Composable
fun LibrarySheet(
    state: LibraryUiState,
    readingMode: ReadingMode,
    selectedBookId: String?,
    progress: Float,
    onBookSelected: (BibleBook) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val focusManager = LocalFocusManager.current
    val closeLabel = stringResource(R.string.close_library)
    LaunchedEffect(focusManager) { focusManager.clearFocus() }

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
                    onClick = onDismiss,
                )
                .semantics { contentDescription = closeLabel },
        )
        LibraryPanel(
            state = state,
            readingMode = readingMode,
            selectedBookId = selectedBookId,
            onBookSelected = onBookSelected,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(libraryPanelWidth(windowWidth))
                .graphicsLayer {
                    // A fraction of the sheet's own width, the way `SlideTransition` read
                    // `Offset(-.12, 0)`. The measured width is used rather than the window's, because
                    // the two differ on a tablet — the panel is capped at 440.
                    translationX = libraryPanelSlideOffset(size.width, progress)
                    alpha = progress
                },
        )
    }
}

/** The way in, for the destination: [fadeIn] and [slideIn] on the same spring curve Flutter's page had. */
private fun libraryEnter(): EnterTransition = fadeIn(tween(LIBRARY_ARRIVE_MILLIS, easing = SpringCurve)) +
    slideIn(tween(LIBRARY_ARRIVE_MILLIS, easing = SpringCurve), initialOffset = offPanelLeft)

/** The way out. `easeInOutCubic` in Flutter, as `reverseAnimation`, and the same slide. */
private fun libraryExit(): ExitTransition = fadeOut(tween(LIBRARY_DISMISS_MILLIS, easing = LibraryDismissCurve)) +
    slideOut(tween(LIBRARY_DISMISS_MILLIS, easing = LibraryDismissCurve), targetOffset = offPanelLeft)

/**
 * `Offset(-.12, 0)`, resolved against the page.
 *
 * A fraction of the *page* here and a fraction of the *panel* in [LibraryRoute]: Flutter slid the
 * whole pushed route, which filled the window, while a sheet that draws itself has only its own
 * width to slide within, and the panel is capped at 440 on a tablet. Both read
 * [libraryPanelSlideOffset], so the two only ever differ in what they measure.
 */
internal val offPanelLeft: (IntSize) -> IntOffset = { page ->
    IntOffset(libraryPageSlideOffset(page.width), 0)
}

/** The way out, run before the host is asked to remove the sheet. */
private suspend fun animateOut(progress: Animatable<Float, *>, onDismiss: () -> Unit) {
    progress.animateTo(0f, tween(LIBRARY_DISMISS_MILLIS, easing = LibraryDismissCurve))
    onDismiss()
}

/** `Colors.black.withValues(alpha: .62)`. */
private val ScrimColor = Color(0f, 0f, 0f, LIBRARY_SCRIM_ALPHA)
