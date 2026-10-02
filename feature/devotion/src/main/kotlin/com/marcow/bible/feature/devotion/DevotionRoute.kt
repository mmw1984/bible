package com.marcow.bible.feature.devotion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.feature.devotion.domain.devotionPostToPlainText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The devotion page as the host draws it, replacing the `_DevotionPageState` that owned the page's
 * widget in `legacy/flutter/lib/devotion_page.dart`.
 *
 * This is the only place in the feature that knows about [DevotionViewModel], the clipboard, the
 * browser or a clock. [DevotionScreen] takes a state and six callbacks so that it can be drawn from a
 * fixed [DevotionUiState] without any of them — which is what lets a preview or a golden draw it.
 *
 * Five things Flutter did here and this does too, and none of them are visible in the screen:
 *
 *  - **The copy writes the article as text and then says so.** `Clipboard.setData` and the snackbar
 *    that followed it were `_copyArticle`'s whole body. The two seconds are
 *    [DevotionChrome.COPY_FEEDBACK_MILLIS] and not Material's own four because Flutter's `SnackBar`
 *    was given `Duration(seconds: 2)`; [Snackbar] has no duration of its own here — this route owns
 *    the timer — so Material's `SnackbarDuration` never gets a say.
 *  - **A second copy restarts those two seconds**, which is what Flutter's `hideCurrentSnackBar()`
 *    ahead of `showSnackBar()` did: the confirmation on screen is always two seconds of the *latest*
 *    copy, never the remainder of an earlier one.
 *  - **The page refreshes itself while it is on screen and again when it comes back.** Flutter polled
 *    on a half-hour timer and re-checked on resume, both times going through `_shouldRefresh`, which
 *    is [DevotionViewModel.refreshIfStale] — silent, because a spinner over an article somebody is
 *    reading is worse than a stale day.
 *  - **A link goes to the browser and the web reader is a screen of its own.** Both are
 *    `showDevotionWebReader`'s business on the Dart side and both are named here, because which URL is
 *    decided by the content — `post?.link ?? devotionOrigin` — while where that URL is *shown* is the
 *    host's arrangement. See [onOpenWebReader] for the two arrangements.
 *  - **The reader leaves on the back press.** Flutter's push was on the root navigator, so back popped
 *    it and left the article exactly where it was; the reader here takes that press itself while it is
 *    the one showing, and the page behind it never moves.
 *
 * The poll is a [LaunchedEffect] over the half hour rather than a timer Flutter started and stopped by
 * hand: the effect is torn down with the page, which covers everything Flutter cancelled its timer
 * for except a tab that is composed but not on screen — and a refresh that happens then is silent, so
 * nothing moves under a reader who is somewhere else.
 *
 * [bottomClearance] is the host's navigation bar. The article leaves room for it, and so does the
 * confirmation, which floats above it exactly as Flutter's `ScaffoldMessenger` put its `SnackBar`
 * above the bottom navigation bar.
 *
 * @param onOpenWebReader where the fallback reader is shown. Left out — which is what the Dart page did
 *   and what a host gets by default — this route draws [DevotionWebReader] over the page and takes the
 *   back press for it. A host with a navigation graph of its own passes a callback instead and is handed
 *   the [DevotionWebReaderTarget] to turn into a destination; nothing else changes, and the reader's own
 *   screen, toolbar and frame are the same composable either way.
 */
@Composable
fun DevotionRoute(
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    viewModel: DevotionViewModel = hiltViewModel(),
    onOpenWebReader: ((DevotionWebReaderTarget) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val copied = stringResource(R.string.devotion_copied)
    // The count is what restarts the timer; the flag is what the page draws. A single boolean would
    // keep the first copy's deadline when the second tap lands on the same true.
    var copies by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    // Which reader this route is showing, or null while it is showing none. It stays null for a host
    // that passed a callback, so the reader below is never composed when the host owns one.
    var reader by remember { mutableStateOf<DevotionWebReaderTarget?>(null) }
    val closeReader: () -> Unit = { reader = null }
    val openWebReader: (DevotionWebReaderTarget) -> Unit = onOpenWebReader ?: { target -> reader = target }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
    // The reader's back press, and only while this route is the one showing the reader: a host that
    // pushes its own destination gets the `NavHost`'s press for that, exactly as the Dart page's push
    // was popped by its navigator.
    BackHandler(enabled = onOpenWebReader == null && reader != null, onBack = closeReader)
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(DevotionViewModel.STALE_AFTER.toMillis())
            viewModel.refreshIfStale()
        }
    }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        confirming = true
        delay(DevotionChrome.COPY_FEEDBACK_MILLIS.toLong())
        confirming = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        DevotionScreen(
            state = state,
            bottomClearance = bottomClearance,
            onRefresh = viewModel::refresh,
            onSelectDate = viewModel::select,
            onCopyArticle = {
                // The button is disabled without a post, so the null case here is the language changing
                // between the tap and the copy rather than a copy of nothing.
                state.post?.let { post ->
                    clipboard.setText(AnnotatedString(devotionPostToPlainText(post, state.devotionLocale())))
                    copies++
                }
            },
            onOpenWebReader = { openWebReader(devotionWebReaderTarget(state.post)) },
            onOpenUrl = uriHandler::openUri,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = confirming,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance),
        ) {
            // Flutter's `SnackBar` took its two colours from the seeded scheme's inverse roles, which
            // this palette has no token for; the box is the app's own raised surface instead, with the
            // ink on it, and Material's own shape and padding around both.
            Snackbar(
                containerColor = appColors.surfaceRaised,
                contentColor = appColors.ink,
                content = { Text(copied) },
            )
        }
        // `Navigator.of(context, rootNavigator: true).push(MaterialPageRoute(...))`: a screen of its own
        // over the page, covering the article and the confirmation the way the pushed route covered
        // everything under it. It fills the area this route was given rather than the window, so whether
        // the host draws its navigation bar over the reader is the host's arrangement, and the reader's
        // own toolbar button is what a finger presses to leave.
        AnimatedContent(
            targetState = reader,
            transitionSpec = { webReaderArrive() togetherWith webReaderLeave() },
            label = WEB_READER_LABEL,
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            // `AnimatedContent` keeps the reader composed through its exit and hands the content the
            // value that was open, which is why the reader is drawn for the target rather than for
            // whatever `reader` is at this instant: a null target is the empty screen behind it.
            if (target != null) {
                DevotionWebReader(
                    url = target.url,
                    title = target.title,
                    onBack = closeReader,
                    onOpenUrl = uriHandler::openUri,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The reader arriving: Flutter's default page transition on Android, a zoom out of 0.85 with a fade.
 *
 * `Curves.fastOutSlowIn` in Dart and [FastOutSlowInEasing] here are the same Material curve, and
 * 300 ms was that builder's own `transitionDuration` rather than the 420 ms this build gives the
 * library sheet — a route that opens a browser tab is not a panel arriving over an article.
 */
private fun webReaderArrive(): EnterTransition = fadeIn(tween(DevotionChrome.WEB_READER_ARRIVE_MILLIS)) +
    scaleIn(
        tween(DevotionChrome.WEB_READER_ARRIVE_MILLIS, easing = FastOutSlowInEasing),
        initialScale = DevotionChrome.WEB_READER_SCALE_FROM,
    )

/** The way out: the same zoom and fade in reverse, over Material's 300 ms `reverseTransitionDuration`. */
private fun webReaderLeave(): ExitTransition = fadeOut(tween(DevotionChrome.WEB_READER_DISMISS_MILLIS)) +
    scaleOut(
        tween(DevotionChrome.WEB_READER_DISMISS_MILLIS, easing = FastOutSlowInEasing),
        targetScale = DevotionChrome.WEB_READER_SCALE_FROM,
    )

/** The reader's arrival, named for what Compose reports it as in a trace or a layout inspector. */
private const val WEB_READER_LABEL = "devotionWebReader"
