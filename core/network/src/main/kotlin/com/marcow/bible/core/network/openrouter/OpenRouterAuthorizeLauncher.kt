package com.marcow.bible.core.network.openrouter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the OpenRouter sign-in page is opened, replacing the `launchUrl(uri, LaunchMode.externalApplication)`
 * of `beginSignIn` in `legacy/flutter/lib/openrouter_service.dart`.
 *
 * The port exists so [OpenRouterAuthManager] can be pinned on the JVM: everything else in a sign-in is
 * plain data (a verifier, four keys in a store, one POST), but opening the browser needs a `Context`.
 * `test/openrouter_service_test.dart` had the same seam in the shape of its `http.Client` and
 * `AppLinks` constructor arguments.
 */
interface OpenRouterAuthorizeLauncher {
    /**
     * Opens [uri] outside the app, answering whether anything could take it.
     *
     * `false` is the case `beginSignIn` turned into
     * `StateError('Could not open OpenRouter sign in.')`, so a device with no browser at all fails
     * here rather than after the user has been sent nowhere.
     */
    fun launch(uri: String): Boolean
}

/**
 * A Custom Tab, which is what `NATIVE_PLAN.md` §4.7 asks for in place of Flutter's
 * `url_launcher`.
 *
 * `LaunchMode.externalApplication` sent the Flutter build out to the browser and left the sign-in
 * looking like the provider's own page; a Custom Tab keeps the tab strip in view while doing the
 * same, and `Intent.FLAG_ACTIVITY_NEW_TASK` is set because the injected context is the application's,
 * not an activity's — without it Android refuses to start the tab from a non-activity context.
 */
@Singleton
class CustomTabsOpenRouterAuthorizeLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OpenRouterAuthorizeLauncher {
    override fun launch(uri: String): Boolean {
        val intent = CustomTabsIntent.Builder().build().intent.apply {
            data = Uri.parse(uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
