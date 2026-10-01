package com.marcow.bible.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The reader destination as the navigation host sees it, replacing the `_BibleHomeState` that owned
 * the reader's widget (`legacy/flutter/lib/main.dart:213`).
 *
 * This is the only place in the feature that knows about [ReaderViewModel]: the screen below takes a
 * state and the two contracts, so it can be drawn from a fixed [ReaderUiState] without a database,
 * which is what lets a preview and a golden do it.
 *
 * [bottomClearance] is the host's navigation bar, which the reader leaves room for without knowing
 * what draws it — features do not depend on each other (`NATIVE_PLAN.md` §2.2). [onVerseAction] is
 * null until the Ask tab exists, and the action sheet drops its Ask and Explain rows rather than
 * offering rows that go nowhere.
 *
 * The position is written on `ON_PAUSE`, which is where Flutter's `AppLifecycleListener` wrote it. It
 * is deliberately not written from `onCleared`: by then the view model's scope is already cancelled,
 * so a queued write would never reach the database.
 */
@Composable
fun ReaderRoute(
    bottomClearance: Dp,
    onVerseAction: ((VerseAction, ScriptureRequest) -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.savePosition() }

    ReaderScreen(
        state = state,
        navigator = viewModel,
        scroll = viewModel,
        bottomClearance = bottomClearance,
        onVerseAction = onVerseAction,
        modifier = modifier,
    )
}
