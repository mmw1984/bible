package com.marcow.bible.feature.search

import com.marcow.bible.feature.search.domain.SearchSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The host's half of the sheet's sign-in button, which is the whole of what [SearchHost] does that
 * `SearchRoute` and the layout below it do not already cover.
 *
 * `SearchHost` is a `@Composable` over a state it does not own — the panel is drawn by `SearchSheet`,
 * which `SearchSheetStateTest` and `SearchDialogTest` already stand up. What it adds is
 * `beginSignInOnTap`: a callback that launches a suspend call which throws, and absorbs the throw. That
 * function is internal and takes a `CoroutineScope`, so its four cases below are plain JVM tests.
 *
 * The port is what makes them possible. `OpenRouterAuthManager` wants an encrypted store, a Custom Tabs
 * launcher and an HTTP exchange, so the fake below stands in for it and the manager's own behaviour is
 * pinned in `core/network`'s `OpenRouterAuthManagerTest` instead.
 *
 * The `CancellationException` clause is deliberately untested. `beginSignInOnTap` returns `() -> Unit`
 * rather than a `Job`, so a launched coroutine that ended cancelled and one that ended normally are
 * indistinguishable from outside it, and the sheet draws the same thing either way. It is there because
 * `catch (_: Exception)` catches `CancellationException` — it is an `IllegalStateException` — so without
 * the clause a cancelled sheet would report a sign-in that finished. The cancelled *scope* below is the
 * case a reader can reach, and that one is tested.
 *
 * `advanceUntilIdle` would leave every assertion below reading zero. It stops the virtual clock "once only
 * the coroutines in this scope are left unprocessed", and the coroutines in `backgroundScope` are exactly
 * that — so the launched sign-in never starts and `beginSignInCalls` stays at 0 however long the clock is
 * advanced. `runCurrent` runs them, because it runs pending tasks at the current moment rather than
 * draining the queue, and nothing here waits on the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchHostTest {
    /** One dispatcher for the test body and for the scopes below, so nothing runs off-scheduler. */
    private val dispatcher = StandardTestDispatcher()

    /**
     * A tap that opens the browser, which the next two are variations of.
     *
     * `backgroundScope` rather than a scope of the test's own, so that an uncaught exception in the
     * launched coroutine fails this test instead of reaching a global handler: that scope is cancelled
     * with the test and `runTest` rethrows whatever it collected on the way out. That is what lets the
     * next test be a test of the catch.
     */
    @Test
    fun `a tap opens the authorize page`() = runTest(dispatcher) {
        val signIn = RecordingSearchSignIn()

        beginSignInOnTap(signIn, backgroundScope)()
        runCurrent()

        assertEquals(1, signIn.beginSignInCalls)
    }

    /**
     * The crash this function exists to prevent: `beginSignIn` throws
     * `IllegalStateException("Could not open OpenRouter sign in.")` when nothing can take the URI, and an
     * unhandled exception in the coroutine a button launches takes the app down.
     *
     * So the failure has to be contained, and nothing written down about the outcome. Dart threw
     * `StateError` from `beginSignIn` and let `_beginOpenRouterLogin` carry it to the zone, which
     * published no error and drew no panel — a reader whose phone has no browser saw the button do
     * nothing, and that is what a reader sees now. The [assertNull] is half the assertion: a change that
     * published the message on this path would draw a panel the Flutter build never drew.
     */
    @Test
    fun `a phone with no browser leaves the reader where they were`() = runTest(dispatcher) {
        val signIn = RecordingSearchSignIn(onBegin = { throw IllegalStateException(LAUNCH_FAILED_MESSAGE) })

        beginSignInOnTap(signIn, backgroundScope)()
        runCurrent()

        assertEquals(1, signIn.beginSignInCalls)
        assertNull(signIn.lastError.value)
    }

    /**
     * The sheet closed before the tap's coroutine ran.
     *
     * `rememberCoroutineScope` is cancelled when the composable leaves, so this is not a corner case. It
     * is a reader who taps and immediately backs out, and the browser must not open over whatever they
     * backed out to. The scope is one of the test's own here, cancelled before the tap rather than
     * through `backgroundScope`, which `runTest` cancels only after the body has finished.
     */
    @Test
    fun `a tap after the sheet leaves opens nothing`() = runTest(dispatcher) {
        val signIn = RecordingSearchSignIn()
        val scopeJob = Job()
        val scope = CoroutineScope(scopeJob + dispatcher)

        scopeJob.cancel()
        beginSignInOnTap(signIn, scope)()
        runCurrent()

        assertEquals(0, signIn.beginSignInCalls)
    }

    /**
     * The order the button runs in, composed the way `SearchHost` composes it.
     *
     * `SearchRouteTest` already pins that [armQueryThenSignIn] arms before whatever it is handed, and
     * nothing here disputes that — what this adds is the other half of the same composition: the
     * callback actually handed to it is [beginSignInOnTap], which launches and swallows. Read together
     * they say the sheet's promise holds across the whole wiring, and either one alone would still pass
     * if `SearchHost` passed something that never reached the port.
     *
     * The list is arm-then-port rather than two booleans, because two booleans have no order, and the
     * arming has to be visible to the launch: a host that armed inside the coroutine would be racing a
     * flag against a sign-in that lands immediately, which is the failure `SearchRouteTest`'s race case
     * runs in full.
     */
    @Test
    fun `the query is armed before the port is asked for a browser`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val signIn = RecordingSearchSignIn(onBegin = { calls += "beginSignIn" })

        armQueryThenSignIn(
            arm = { calls += "arm" },
            signIn = beginSignInOnTap(signIn, backgroundScope),
        )()
        runCurrent()

        assertEquals(listOf("arm", "beginSignIn"), calls)
    }

    private companion object {
        /** `StateError('Could not open OpenRouter sign in.')`, which the manager throws as this text. */
        const val LAUNCH_FAILED_MESSAGE = "Could not open OpenRouter sign in."
    }
}

/**
 * A port that answers, and remembers how often it was asked.
 *
 * [onBegin] is where a test puts what a manager would do, so one fake covers the happy path and the
 * refused browser.
 */
private class RecordingSearchSignIn(private val onBegin: suspend () -> Unit = {}) : SearchSignIn {
    var beginSignInCalls = 0
        private set

    override val lastError: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun beginSignIn() {
        beginSignInCalls++
        onBegin()
    }
}
