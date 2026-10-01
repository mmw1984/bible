package com.marcow.bible.feature.aichat.domain

import com.marcow.bible.core.network.openrouter.OpenRouterAuthManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The sign-in the chat needs, as a port over `OpenRouterAuthManager`.
 *
 * The chat does four things with a key: report whether one is held, initialise (which also finishes an
 * exchange the last run left parked), open the authorize page, and say what went wrong. All four were
 * on the concrete manager, which cannot be built in a unit test — it wants an encrypted store, a
 * Custom Tabs launcher and an HTTP exchange — so the chat is written against this and
 * `AiChatModule` binds it to the manager.
 *
 * It is deliberately *not* a general "any AI provider" port. `NATIVE_PLAN.md` §4.1 makes OpenRouter the
 * default and the only provider that can answer a web search, and a Gemini Nano answer needs no
 * sign-in at all — so the second provider arrives by not needing this rather than by answering it.
 */
interface AiChatSignIn {
    /** `bool openRouterSignedIn`, as a change rather than a value, because the sign-in panel reacts. */
    val signedIn: StateFlow<Boolean>

    /** `String? openRouterAuthError`: the last sign-in failure, or null when there is nothing wrong. */
    val lastError: StateFlow<String?>

    /** `initialize()`: read the stored key, then finish any exchange left parked by the last run. */
    suspend fun initialize()

    /** `beginOpenRouterLogin()`: a fresh verifier and the authorize page in a Custom Tab. */
    suspend fun beginSignIn()
}

/**
 * The port over `OpenRouterAuthManager`, an adapter rather than a `@Binds` of the manager itself.
 *
 * The dependency direction decides it: the port is the chat's, so it is declared here in
 * `feature/aichat`, and `OpenRouterAuthManager` is `core/network`'s. Core never depends on a feature,
 * so the manager cannot declare itself an [AiChatSignIn] — a `@Binds` of the two would produce a
 * binding whose generated factory upcasts a class that was never a subtype, which the compiler would
 * only notice if a member's JVM name ever diverged from the port's. This says the four members are the
 * manager's, so the relationship is checked.
 *
 * It takes the manager concretely rather than the `OpenRouterSession` port because two of the four
 * members are not on that port: opening a Custom Tab and finishing a parked exchange are sign-in,
 * not key access, and `OpenRouterSession` is what `OpenRouterChatClient` is given so it can turn "no
 * key" into a login-required failure.
 *
 * Public rather than internal because `AiChatModule` names it in an `@Binds` signature, which a public
 * module cannot do with an internal type.
 */
class OpenRouterChatSignIn @Inject constructor(private val auth: OpenRouterAuthManager) : AiChatSignIn {
    override val signedIn: StateFlow<Boolean> = auth.signedIn

    override val lastError: StateFlow<String?> = auth.lastError

    override suspend fun initialize() = auth.initialize()

    override suspend fun beginSignIn() = auth.beginSignIn()
}
