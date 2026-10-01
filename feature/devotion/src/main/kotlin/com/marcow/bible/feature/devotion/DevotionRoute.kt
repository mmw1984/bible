package com.marcow.bible.feature.devotion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.marcow.bible.core.network.devotion.DEVOTION_ORIGIN
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
 * Four things Flutter did here and this does too, and none of them are visible in the screen:
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
 *  - **A link goes to the browser and the web reader is the host's.** `onOpenWebReader` is the screen's
 *    `showDevotionWebReader(context, post?.link ?? devotionOrigin)`: the URL is decided here, because
 *    the fallback — the site's front page, when there is no post to open — is a rule about the
 *    content, and the WebView that shows it belongs to whoever owns navigation.
 *
 * The poll is a [LaunchedEffect] over the half hour rather than a timer Flutter started and stopped by
 * hand: the effect is torn down with the page, which covers everything Flutter cancelled its timer
 * for except a tab that is composed but not on screen — and a refresh that happens then is silent, so
 * nothing moves under a reader who is somewhere else.
 *
 * [bottomClearance] is the host's navigation bar. The article leaves room for it, and so does the
 * confirmation, which floats above it exactly as Flutter's `ScaffoldMessenger` put its `SnackBar`
 * above the bottom navigation bar.
 */
@Composable
fun DevotionRoute(
    bottomClearance: Dp,
    onOpenWebReader: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevotionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val copied = stringResource(R.string.devotion_copied)
    // The count is what restarts the timer; the flag is what the page draws. A single boolean would
    // keep the first copy's deadline when the second tap lands on the same true.
    var copies by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
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
            onOpenWebReader = { onOpenWebReader(state.post?.link ?: DEVOTION_ORIGIN) },
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
    }
}
