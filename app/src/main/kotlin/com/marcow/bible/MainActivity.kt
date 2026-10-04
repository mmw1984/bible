package com.marcow.bible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.marcow.bible.core.network.openrouter.OpenRouterAuthManager
import com.marcow.bible.core.network.openrouter.OpenRouterCallbackForwarder
import com.marcow.bible.startup.LegacyImportStartup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Single activity host. The content is [BibleApp]: the tab shell (reader / AI chat / devotion),
 * the reader's top bar, the library destination, the search dialog and settings, mirroring
 * `BibleHome` in `legacy/flutter/lib/main.dart:199`.
 *
 * **The two legs of the OpenRouter sign-in live here, and this is the only place they can.** Dart ran
 * both from `initialize()` — `AppLinks.getInitialLink()` answered the launch link and
 * `uriLinkStream` answered the one that arrived while the app was up — because the sign-in and the
 * link listener were one object and the activity was not in the path. Android splits them: the
 * activity owns the intent and `core/network` owns the exchange, so the two halves are called from the
 * two moments Dart answered, and neither is interchangeable with the other.
 *
 * Both halves were dead until now, in a way the search sheet would have shown:
 *
 *  - [OpenRouterAuthManager.signedIn] is only ever *published* by `initialize()`, so with nothing
 *    calling it every launch reported signed out. `SearchSheetState.requiresLogin` reads that value,
 *    so the sheet's AI mode drew the `login_to_search` panel forever and the AI search behind it
 *    could not be reached — with a perfectly good key sitting in the migrated store.
 *  - The `bible://openrouter/callback` intent-filter in `AndroidManifest.xml` had no consumer, so the
 *    Custom Tab handed its code to nothing.
 *
 * The order inside [openRouterSession] is Dart's: the stored session is published, then the link is
 * answered, then any exchange a previous run left parked is finished. Both calls retry, because
 * `initialize` answers the web target that Android does not have and `forwardFrom` answers the link
 * that `initialize` cannot see — so the retry runs twice and the second one finds nothing left to do,
 * behind the same mutex, for two encrypted-store reads. Each half swallows its own failure, which is
 * what keeps a refused callback from taking the launch down with it: the reason is in `lastError` and
 * the app still starts.
 *
 * Nothing here needs a test of its own, and there is none to write: [OpenRouterAuthManager] and
 * [OpenRouterCallbackForwarder] are both exercised without Android in `core/network`'s own suite, and
 * what this class adds over them is *which intent goes to which call* — `onCreate` against both, and
 * `onNewIntent` against the link alone. That needs a `Robolectric` activity, and no module in this
 * project has one (`07f81db`, `d92abfc`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var keepSplash = true

    /** `OpenRouterAuth.initialize()`: publish what is already stored, then finish a parked exchange. */
    @Inject
    lateinit var auth: OpenRouterAuthManager

    @Inject
    lateinit var legacyImportStartup: LegacyImportStartup

    /** `OpenRouterCallbackForwarder`: the seam between this activity's intents and the sign-in. */
    @Inject
    lateinit var callbacks: OpenRouterCallbackForwarder

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplash }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openRouterSession(intent)
        lifecycleScope.launch {
            withTimeoutOrNull(STARTUP_IMPORT_TIMEOUT_MS) {
                legacyImportStartup.awaitReady()
            }
            setContent { BibleApp() }
            keepSplash = false
        }
    }

    /**
     * `onNewIntent`: the Custom Tab handing the code back to an app already in the foreground.
     *
     * `setIntent` first, because `launchMode="singleTop"` leaves `getIntent()` answering with the
     * intent the activity was *started* with — so without it every link after the first would be
     * answered by the launch intent instead of the one that just arrived.
     *
     * The stored session is deliberately not read again here. It was published on the way in, and the
     * exchange this may run publishes it again on the way out (`publishSignedIn`), so the only thing
     * that changes between the two entry points is the link.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { callbacks.forwardFrom(intent) }
    }

    /** Dart's `initialize()` for Android: the launch intent's link, then the parked exchange. */
    private fun openRouterSession(launchIntent: Intent) {
        lifecycleScope.launch {
            auth.initialize()
            callbacks.forwardFrom(launchIntent)
        }
    }

    private companion object {
        const val STARTUP_IMPORT_TIMEOUT_MS = 5_000L
    }
}
