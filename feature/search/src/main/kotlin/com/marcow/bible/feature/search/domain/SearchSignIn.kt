package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.network.openrouter.OpenRouterAuthManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The half of the sign-in that belongs to whoever opens the sheet, as a port over
 * `OpenRouterAuthManager`.
 *
 * The sheet does two things with a key and splits them deliberately. Whether one is *held* is
 * `OpenRouterSession` and lives in `SearchViewModel`, because it is a reaction — it decides whether AI
 * mode can run at all, and a sign-in landing mid-search re-runs the query that was waiting. Getting one
 * *is* not the sheet's: Dart's `_beginOpenRouterLogin` armed `pendingCloudSearch` and then handed the
 * rest to `BibleAiController`, and the rest opens a browser. That is this port, and it is two members
 * because those two are all of it.
 *
 * It is a port rather than the manager for the reason `AiChatSignIn` is one: the manager cannot be
 * built in a JVM test — it wants an encrypted store, a Custom Tabs launcher and an HTTP exchange — and
 * the decision worth pinning on this side is the *order* of the arming and the browser, which is
 * reachable only through something that can be stood up without any of the three. `SearchModule` binds
 * it to the manager; a test binds it to the stub that answers.
 *
 * Deliberately not a general "any AI provider" port, and for `AiChatSignIn`'s reason: `NATIVE_PLAN.md`
 * §4.1 makes OpenRouter the default and the only provider that can answer a web search, and the
 * on-device Gemini Nano provider Phase 4 adds needs no sign-in at all — so the second provider arrives
 * by not needing this rather than by answering it.
 */
interface SearchSignIn {
    /**
     * `BibleAiController.openRouterAuthError`, which the sheet's `login_to_search` panel draws beneath
     * its button as the one line a user may need to copy into a bug report.
     *
     * A flow rather than a value, because the panel is up and the text changes under it: the sign-in
     * this opens reports a refused callback, a missing code, a missing verifier or a failed exchange,
     * and Flutter's `onChanged?.call()` made each of those a rebuild. `null` is nothing wrong.
     */
    val lastError: StateFlow<String?>

    /**
     * `beginSignIn()`: a fresh PKCE verifier parked, and the authorize page in a Custom Tab.
     *
     * Throws when no browser can be opened, which is a `StateError('Could not open OpenRouter sign
     * in.')` in Dart and an `IllegalStateException` carrying the same text here. The port does not
     * soften that, because the place that can absorb it is the place a tap reaches it from —
     * `openRouterSignIn` in `SearchHost`.
     */
    suspend fun beginSignIn()
}

/**
 * The port over [OpenRouterAuthManager], an adapter rather than a `@Binds` of the manager itself.
 *
 * The dependency direction decides it: the port is the sheet's, so it is declared here in
 * `feature/search`, and `OpenRouterAuthManager` is `core/network`'s. Core never depends on a feature,
 * so the manager cannot declare itself a [SearchSignIn] — a `@Binds` of the two would produce a
 * binding whose generated factory upcasts a class that was never a subtype, which the compiler would
 * only notice if a member's JVM name ever diverged from the port's. This says the two members are the
 * manager's, so the relationship is checked.
 *
 * It takes the manager concretely rather than through `OpenRouterSession` because neither member is on
 * that port: opening a Custom Tab and publishing the last failure are sign-in, not key access, and
 * `OpenRouterSession` is what `OpenRouterChatClient` is given so it can turn "no key" into a
 * login-required failure.
 *
 * Public rather than internal because `SearchModule` names it in a `@Binds` signature, which a public
 * module cannot do with an internal type.
 */
class OpenRouterSearchSignIn @Inject constructor(private val auth: OpenRouterAuthManager) : SearchSignIn {
    override val lastError: StateFlow<String?> = auth.lastError

    override suspend fun beginSignIn() = auth.beginSignIn()
}
