package com.marcow.bible.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit

/**
 * The search sheet as a host opens it, replacing the `_openSearch()` half of
 * `legacy/flutter/lib/main.dart:832` — the `showGeneralDialog` that put `_SearchDialog` over the
 * reader when the top bar's `searchWholeBible` glyph was pressed.
 *
 * This is the only place in the feature that knows about [SearchViewModel]. [SearchDialog] draws a
 * [SearchSheetState] and reports a tap; [SearchSheet] lays it out; every decision about what a search
 * does lives in the view model. So what is left for a host is this: hand the sheet its reading mode
 * and language, and take back the three things only the host can do.
 *
 * **There is no navigation destination here, and there is not meant to be one.** Flutter pushed the
 * sheet as a route, so it had a back stack entry for free; [SearchDialog] draws a Compose `Dialog`
 * window instead, which brings its own dismissal and its own share of the back press. Registering it
 * in a `NavHost` as well would give the sheet a second window and a second back stack entry for the
 * same tap, which is the bug `jumpToVerse` in `SearchDialog.kt` exists to keep out. A host composes
 * this while the sheet is open and removes it when it is not — the same shape `LibraryRoute` draws
 * its sheet in, and the reason both are `@Composable` rather than `NavGraphBuilder` functions.
 *
 * Three of the parameters are the host's, and each for a reason Flutter gives:
 *
 *  - [readingMode] is the *reader's*, not the sheet's. `_openSearch` read `mode` off its own state and
 *    passed it in, so a reader switched to English opens the sheet with English book names even in a
 *    Chinese interface — which is `searchReferenceLabel`'s rule, reading the reading mode before the
 *    UI language.
 *  - [locale] is the app's, and it decides the other half: `_usesEnglishUi` at
 *    `legacy/flutter/lib/main.dart:35` chose the verse text. Flutter read it off the `BuildContext`
 *    that `showGeneralDialog` handed the page; Compose reads its own strings off the composition
 *    instead, so the one thing that is not a string resource — `bookNameFor` and `verseTextFor` — is
 *    the one thing left for the host to say.
 *  - [onOpenVerse] is the reader's navigation, and is passed straight through: [SearchDialog] already
 *    closes its window before it hands the verse over, which is the `Navigator.pop(dialogContext)`
 *    that came before the jump at `legacy/flutter/lib/main.dart:847`.
 *
 * [onSignIn] and [authError] are the host's because the sign-in is. Flutter's button was
 * `_beginOpenRouterLogin`, which armed `pendingCloudSearch` and then asked `BibleAiController` to
 * start a PKCE exchange; the sheet owns the first half of that and the host owns the second, and
 * [armQueryThenSignIn] keeps them in Dart's order rather than leaving it to whichever callback a host
 * happens to write first. The host's half is three calls to things `core/network` already publishes:
 * `OpenRouterAuthManager.beginSignIn()` to open the Custom Tab and
 * `OpenRouterAuthManager.lastError` for [authError], with the other leg of the exchange —
 * `OpenRouterCallbackForwarder.forwardFrom`, from the activity's `onCreate` and `onNewIntent` — the
 * one leg that genuinely needs the activity this sheet is drawn over.
 *
 * [viewModel] is a parameter so that its lifetime is the host's decision, and it defaults to
 * [rememberSearchViewModelForOpen] so that the decision a host is given is already the right one.
 * `_openSearch` pushed a new `_SearchDialog` every time, and every push was a new `_SearchDialogState`
 * — an empty box, no results, no mode — so a reopen starts over. A view model scoped to the activity
 * outlives the sheet instead, and the second open of a session would start on the first one's query
 * and its results, which is the Flutter behaviour this whole feature is written against. Each open
 * gets a view model of its own; pass one of your own only when you have made one per open.
 *
 * @see SearchDialog for the parameters, which are the same ones this hands it.
 */
@Suppress("LongParameterList")
@Composable
fun SearchRoute(
    readingMode: ReadingMode,
    locale: AppLocale,
    onOpenVerse: (ScriptureHit) -> Unit,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    authError: String? = null,
    viewModel: SearchViewModel = rememberSearchViewModelForOpen(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchDialog(
        state = state,
        readingMode = readingMode,
        locale = locale,
        onQueryChanged = viewModel::onQueryChanged,
        onSearch = viewModel::search,
        onModeChange = viewModel::changeMode,
        onSignIn = armQueryThenSignIn(viewModel::beginSignIn, onSignIn),
        onDismiss = onDismiss,
        onOpenVerse = onOpenVerse,
        modifier = modifier,
        authError = authError,
    )
}

/**
 * Flutter's `_beginOpenRouterLogin` at `legacy/flutter/lib/main.dart:2316`, in Dart's order:
 * `pendingCloudSearch = input.text.trim().isNotEmpty`, and only then `await
 * widget.ai.beginOpenRouterLogin()`.
 *
 * The order is the whole of it, and the other order is the one a host reaches for by accident. The
 * sheet's sign-in button is a promise that the typed query runs once a key lands, and the promise is
 * held as a flag [SearchViewModel.beginSignIn] sets while the signed-in watcher in
 * [SearchViewModel]'s constructor is what clears it. A host that opened the browser first and armed
 * the flag after would lose the query to any sign-in that completed inside that window — which is not
 * only a Custom Tab round trip, but a key already in the store, a restored session, and every
 * `remember`ed callback replaying.
 *
 * It is a function rather than two statements inside the composable for the reason
 * `SearchDialog.jumpToVerse` is one: the ordering is the only thing about a tap here that a plain JVM
 * test can see, and the sheet is drawn from a state a host supplies, so nothing else is testable
 * without a composition.
 */
internal fun armQueryThenSignIn(arm: () -> Unit, signIn: () -> Unit): () -> Unit = {
    arm()
    signIn()
}
