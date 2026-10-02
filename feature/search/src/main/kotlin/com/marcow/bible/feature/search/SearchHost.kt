package com.marcow.bible.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.feature.search.domain.SearchSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The sheet as a host opens it, with the sign-in already wired: [SearchRoute] plus the two calls its
 * own KDoc leaves to the host out of the three it lists. (The third is
 * `OpenRouterCallbackForwarder.forwardFrom`, from the activity's `onCreate` and `onNewIntent`, and
 * that one is not this sheet's to hand over.)
 *
 * A host that composes [SearchRoute] directly has to supply two things that are not its own, and both
 * are easy to get subtly wrong:
 *
 *  - `authError` is a `StateFlow` on the sign-in, so somebody has to collect it and hand the sheet the
 *    current value. Reading the flow as a plain value gives a panel that never updates.
 *  - `onSignIn` launches a **suspend** call, and that call throws when no browser can be opened. Dart
 *    could let it escape into the zone; Android cannot, and an unhandled exception in the coroutine a
 *    button launches takes the app down.
 *
 * [SearchSignIn] makes both one parameter. Everything else is [SearchRoute]'s, unchanged — including
 * the two parameters that are the reader's rather than the sheet's, and the [viewModel] whose lifetime
 * the host still decides.
 *
 * **Draw it while the sheet is open, and give each open its own [viewModel].** The sheet is a Compose
 * `Dialog` window, so there is no destination to navigate to and nothing to keep it alive but the host's
 * own state — the same arrangement `LibraryRoute` documents for itself. The view model is the half
 * that a host gets wrong by accident: `hiltViewModel()` resolves against the host's
 * `ViewModelStoreOwner`, which is the activity or the destination, and both outlive the sheet, so the
 * second open of a session would start on the first one's query, its results and its mode. `_openSearch`
 * pushed a new `_SearchDialog` every time, and every push was a new `_SearchDialogState` — an empty
 * box, no results, no mode — so pass a view model scoped to this open.
 *
 * @see SearchRoute for the rest of the contract, which this adds nothing to.
 */
@Composable
fun SearchHost(
    readingMode: ReadingMode,
    locale: AppLocale,
    onOpenVerse: (ScriptureHit) -> Unit,
    onDismiss: () -> Unit,
    signIn: SearchSignIn,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val authError by signIn.lastError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SearchRoute(
        readingMode = readingMode,
        locale = locale,
        onOpenVerse = onOpenVerse,
        onDismiss = onDismiss,
        // Arming is not repeated here. `SearchRoute` puts `beginSignIn` ahead of whatever it is given, so
        // arming again in this function would set the same flag twice — for a promise `armQueryThenSignIn`
        // already keeps in one place.
        onSignIn = beginSignInOnTap(signIn, scope),
        modifier = modifier,
        authError = authError,
        viewModel = viewModel,
    )
}

/**
 * The host's half of `_beginOpenRouterLogin`, as a callback: open the authorize page, and let the app
 * survive it not opening.
 *
 * The arming is [SearchRoute]'s and not this function's: it calls [SearchViewModel.beginSignIn] and
 * only then whatever it is handed, which is this. That order is the one that matters, because the
 * sheet's button promises the typed query runs once a key lands, and the promise is a flag the
 * signed-in watcher clears — so the browser is opened after the flag is set and never before it, and
 * the `launch` sits inside this function rather than around it for exactly that reason. A sign-in that
 * completes synchronously, from a key already in the store or a restored session, is the case that
 * would lose the query otherwise.
 *
 * The catch is the whole reason this is a function rather than three lines in the composable. Dart's
 * `beginSignIn` threw `StateError('Could not open OpenRouter sign in.')` when `launchUrl` came back
 * false, and `_beginOpenRouterLogin` let it reach the zone: a logged error, and in a release build
 * nothing at all on screen. The message never went to `lastError`, because the manager does not publish
 * a launch failure — and it is not published here either, so a reader whose phone has no browser still
 * sees the button do nothing, exactly as the Flutter build had it. What does change is that the throw
 * stops here rather than in the coroutine's uncaught handler.
 *
 * It is broad for the same reason [SearchViewModel]'s are: [SearchSignIn] is a port, the set of things
 * that can fail behind it is not this file's to enumerate, and [CancellationException] is rethrown
 * first because a cancelled scope is not a failure of the sign-in.
 */
@Suppress("TooGenericExceptionCaught")
internal fun beginSignInOnTap(signIn: SearchSignIn, scope: CoroutineScope): () -> Unit = {
    scope.launch {
        try {
            signIn.beginSignIn()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Dart reached the zone with this; here it stops at the tap. The message is on the
            // exception for a panel that chooses to show it.
        }
    }
}
