package com.marcow.bible.feature.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.core.model.ScriptureHit

/**
 * The sheet in a modal window with Flutter's barrier, which is the `showGeneralDialog` at
 * `legacy/flutter/lib/main.dart:836`.
 *
 * A dialog rather than an inline overlay for one reason: Flutter's version was a route, so the
 * system back gesture closed it and it sat above the reader's own navigation. Whoever hosts the
 * sheet therefore only has to open this and hand it the state — the barrier, the dismissal and the
 * transition are here rather than repeated by every host.
 *
 * [onOpenVerse] is a callback rather than a route because the reader is another feature and
 * `core:navigation` has no route for a verse yet. The window closes itself and *then* hands the verse
 * over, which is the order Flutter's `onVerse` used at `legacy/flutter/lib/main.dart:847`; the
 * navigation itself is the host's, because the reader's location is the host's to keep.
 *
 * @see SearchSheet for the parameters, which are the same ones.
 */
@Suppress("LongParameterList")
@Composable
fun SearchDialog(
    state: SearchSheetState,
    readingMode: ReadingMode,
    locale: AppLocale,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVerse: (ScriptureHit) -> Unit,
    modifier: Modifier = Modifier,
    authError: String? = null,
) {
    Dialog(
        // `showGeneralDialog`'s `pageBuilder` returned a full-screen widget, so the window must not
        // take Material's default width or the sheet would be laid out inside a card.
        properties = DialogProperties(
            dismissOnBackPress = true,
            // Handled by the barrier below rather than by the window: this content fills the window,
            // so there is no "outside" for the platform's own outside-tap dismissal to find.
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
        onDismissRequest = onDismiss,
    ) {
        // Flutter faded the whole dialog in over 180 ms. The fade out is the platform's: Compose
        // removes the window as soon as it leaves composition, with no exit frame to animate, so the
        // `fadeOut` half of `transitionBuilder` has nowhere to run.
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f, tween(BARRIER_MILLIS, easing = LinearOutSlowInEasing))
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.Black.copy(alpha = BARRIER_ALPHA)),
        ) {
            // `barrierDismissible: true`: a tap beside the sheet closes it. The barrier is a sibling
            // *behind* the sheet rather than a full-screen catcher in front of it, so a tap reaches
            // whichever of the two is on top. Flutter's barrier was only dismissible outside the
            // dialog widget, and the sheet's own surface is what draws the widget's bounds — so the
            // surface swallows taps on its empty space rather than letting them read as a tap on the
            // barrier. No ripple here: the barrier is the whole visual.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            SearchSheet(
                state = state,
                readingMode = readingMode,
                locale = locale,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onModeChange = onModeChange,
                onSignIn = onSignIn,
                onDismiss = onDismiss,
                onOpenVerse = jumpToVerse(onDismiss, onOpenVerse),
                authError = authError,
            )
        }
    }
}

/**
 * Flutter's `onVerse`, and nothing else: the window goes first, then the jump.
 *
 * `legacy/flutter/lib/main.dart:847` popped the dialog and only then looked the book up and moved the
 * reader. The other order is the one a host reaches for by accident — navigate while this window is
 * still up, and the navigation's own back stack entry is what gets popped, leaving the sheet sitting
 * over the verse it was just asked to open.
 *
 * It is a function rather than a lambda in the composable because the order is the whole of it, and
 * that is the one thing about a tap in a window that a plain JVM test can see.
 */
internal fun jumpToVerse(dismiss: () -> Unit, openVerse: (ScriptureHit) -> Unit): (ScriptureHit) -> Unit = { hit ->
    dismiss()
    openVerse(hit)
}

/**
 * `barrierColor: Colors.black.withValues(alpha: .72)`, compounded with the window's own dim.
 *
 * Compose's dialog window dims whatever is behind it by 0.32 on its own, so a barrier drawn at
 * Flutter's literal 0.72 would land at 0.81. This is the alpha that makes the pair come to 0.72:
 * `1 - (1 - 0.32) * (1 - BARRIER_ALPHA) == 0.72`.
 */
private const val BARRIER_ALPHA = 0.588f

/** `transitionDuration: Duration(milliseconds: 180)`. */
private const val BARRIER_MILLIS = 180
