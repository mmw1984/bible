package com.marcow.bible.core.network.openrouter

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The way `bible://openrouter/callback` reaches [OpenRouterAuthManager], replacing the two answers
 * `AppLinks` gave inside `initialize` at `legacy/flutter/lib/openrouter_service.dart:48`–`86`.
 *
 * Dart had no forwarding step to port: `AppLinks` handed the link straight to `_exchange`, so the link
 * listener and the sign-in were one object and the activity was not in the path. Android splits them —
 * the activity owns the intent, `core/network` owns the sign-in — so this is the seam between the two,
 * and it is the part of `NATIVE_PLAN.md` §4.7's "Custom Tabs + deep link" that can be tested without a
 * browser, a socket or a device.
 *
 * The host calls [forward] from both `MainActivity.onCreate` and `MainActivity.onNewIntent`, because
 * those are the two moments Dart's `initialize` answered and they are not interchangeable:
 *
 *  - `onCreate` is where `getInitialLink()` was read — the link the activity was *started* with,
 *    including the case of an app that was killed while the browser was in front and is resumed by the
 *    redirect rather than by the reader.
 *  - `onNewIntent` is where `uriLinkStream` delivered — a link arriving at an app already in the
 *    foreground, which is the ordinary case of the Custom Tab handing back the code.
 *
 * Either may arrive more than once, and [OpenRouterAuthManager] is what de-duplicates them: Android
 * delivers the launch intent again as a new intent on some rotations, and `handledCodes` drops the
 * second copy of a code. This class therefore does not track what it has seen — a second pass is one
 * extra store read, not a second exchange.
 *
 * The retry that follows every entry point is Dart's `await retryPendingExchange()` at the end of
 * `initialize`: the code a killed run parked on disk is finished on the next entry rather than being
 * left for the reader to notice. It runs whether or not the intent carried a callback, which is why
 * this is not inside the branch.
 */
@Singleton
class OpenRouterCallbackForwarder @Inject constructor(private val auth: OpenRouterAuthManager) {
    /**
     * The link the activity was handed, answering whether it was an OpenRouter callback at all.
     *
     * `false` for a launch with no data — the launcher icon, a share, any intent that is not this
     * app's redirect — and the retry still happens, because that is the ordinary entry point.
     *
     * @param uri the intent's data as text, from [forwardFrom], or null when there was none.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun forward(uri: String?): Boolean {
        val callback = uri?.takeIf { isOpenRouterCallback(it) }
        // The two catches are separate on purpose. Dart had two try blocks in `initialize` — the
        // initial link, then the pending exchange — so a link whose exchange failed was still retried
        // immediately afterwards rather than skipping the retry. One block would have changed that.
        try {
            if (callback != null) auth.handleCallback(callback)
        } catch (_: Exception) {
            // The manager published the failure in `lastError` before rethrowing, which is what the
            // chat's error panel shows. Dart wrapped it as 'OpenRouter callback failed: $error'; the
            // wrapper is dropped because a panel showing the failure inside a copy of the failure is
            // no more readable than the failure itself.
        }
        try {
            auth.retryPendingExchange()
        } catch (_: Exception) {
            // "A failed exchange remains pending and can be retried without blocking AI" — the code
            // and the verifier both stay on disk, so this is a retry and not a restart.
        }
        return callback != null
    }

    /**
     * The same call for a host holding an [Intent], which is what `MainActivity` has.
     *
     * `intent.data` rather than a lookup of the app's own link store: the `bible://` filter in
     * `AndroidManifest.xml` is what delivers the callback here in the first place, and
     * [isOpenRouterCallback] still decides whether the URI is one. `null` in, `false` out — the
     * launcher icon is an intent with no data and is not a sign-in that failed.
     */
    suspend fun forwardFrom(intent: Intent?): Boolean = forward(intent?.data?.toString())
}
