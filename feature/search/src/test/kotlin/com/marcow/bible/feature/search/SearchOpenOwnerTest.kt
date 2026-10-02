package com.marcow.bible.feature.search

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The store one open of the sheet gets, which is the only part of a host's opening of the sheet that
 * the composables above it cannot reach on their own.
 *
 * [SearchHost] and [SearchRoute] each say in their KDoc that an open has its own view model, and before
 * `SearchOpenOwner` there was no way for a host to give them one — the store had to be `hiltViewModel()`'s
 * default, which resolves against the activity. `rememberSearchViewModelForOpen` is the composable that
 * builds one and clears it again, which is three lines of `remember` and `DisposableEffect` around the
 * class below; an owner is two properties and no composition, so these are plain JVM tests and the
 * composable is not.
 *
 * The view model that goes into the stores is [EmptyViewModel] rather than [SearchViewModel]: what is
 * being pinned is whether an open finds anything at all, and a real one would ask for five ports and a
 * dispatcher to say nothing more.
 */
class SearchOpenOwnerTest {
    /**
     * Flutter's `_openSearch`, which pushed a new `_SearchDialog` every time and so handed a reopened
     * sheet a new `_SearchDialogState` — an empty box, no results, no mode.
     *
     * One host, two opens, and the view model the first open put away is not the one the second gets.
     * That is the whole parity claim, and it is the failure a plain `hiltViewModel()` produces, since
     * the activity's store is still holding the first open's `SearchViewModel` when the sheet comes
     * back up.
     *
     * Read through [ViewModelProvider] rather than by putting things into the store by hand, because
     * that is the path `hiltViewModel` takes and the one whose key convention is not this file's to
     * reproduce.
     */
    @Test
    fun `a second open starts on an empty box`() {
        val host = FakeHost()
        val first = SearchOpenOwner(host)
        val beforeReopen = ViewModelProvider(first, NewFactory)[EmptyViewModel::class.java]

        val second = SearchOpenOwner(host)

        assertNotSame(beforeReopen, ViewModelProvider(second, NewFactory)[EmptyViewModel::class.java])
    }

    /**
     * The factory is handed straight through, because it is the host's Hilt factory that
     * `hiltViewModel` wraps to build a `@HiltViewModel`, and a second wrapper would only move the point
     * at which a misconfigured host finds out.
     *
     * Asserted as identity rather than as "both work", because a substituted factory is the change this
     * guards against and the only way to see one is to look at the object. [NewFactory] would not do:
     * `NewInstanceFactory` is a singleton, so a swapped-in one is indistinguishable from the original.
     */
    @Test
    fun `the open fills itself from the host's own factory`() {
        val factory = NamedFactory()
        val host = FakeHost(factory)

        val owner = SearchOpenOwner(host)

        assertSame(factory, owner.defaultViewModelProviderFactory)
    }

    /**
     * An owner that publishes no factory is turned away where it is asked for, rather than left to fail
     * downstream as "Cannot create an instance of class SearchViewModel" — which blames the view model
     * for something the composition around it is missing, and names neither.
     *
     * The message is asserted because that is the half of this that a reader or the next developer ever
     * sees: the refusal is all this failure has to offer, so a wording change is a behaviour change.
     */
    @Test
    fun `a host that publishes no factory is refused by name`() {
        val failure = assertThrows(IllegalStateException::class.java) { SearchOpenOwner(PlainHost()) }

        assertTrue(failure.message.orEmpty().contains("ViewModelProvider.Factory"), failure.message.orEmpty())
    }

    /**
     * Leaving the sheet clears the view model the open was holding, which is the only teardown the open
     * gets — the store belongs to no host, so nothing else empties it.
     *
     * `onCleared` is the assertion rather than an empty store, because it is what the clear is *for*:
     * [SearchViewModel]'s constructor starts a watcher on `session.signedIn` in `viewModelScope`, and
     * that scope is cancelled by this call. Left alone, the watcher would keep collecting for the rest
     * of the process, once for every open of the sheet.
     *
     * The store is not asked anything afterwards. A cleared `ViewModelStore` refuses to hold anything
     * else — which is the right answer for an owner nothing reuses, and would make a second
     * [ViewModelProvider] here fail for a reason that has nothing to do with the behaviour under test.
     */
    @Test
    fun `leaving the open clears the view model it was holding`() {
        val owner = SearchOpenOwner(FakeHost())
        val viewModel = ViewModelProvider(owner, NewFactory)[ClearedViewModel::class.java]

        owner.clear()

        assertTrue(viewModel.cleared)
    }
}

/**
 * The factory `androidx.lifecycle.viewmodel.compose.viewModel` falls back to when the owner it is given
 * publishes none, which is the same shape the activity that hosts the sheet has.
 */
private val NewFactory = ViewModelProvider.NewInstanceFactory.getInstance()

/**
 * Stands in for [SearchViewModel], whose five injected ports would say nothing more here.
 *
 * `internal` rather than `private` because [NewFactory] builds it reflectively, and a class the JVM
 * cannot reach from `androidx.lifecycle` would fail the access check rather than the assertion.
 */
internal class EmptyViewModel : ViewModel()

/**
 * [EmptyViewModel] with a note of whether it was cleared, standing in for the [SearchViewModel] whose
 * `viewModelScope` teardown is what the clear is meant to buy.
 *
 * `internal` for the same reason as [EmptyViewModel]: [NewFactory] builds it reflectively.
 */
internal class ClearedViewModel : ViewModel() {
    /** Set by [onCleared], so a test can see the teardown rather than infer it from a store. */
    var cleared: Boolean = false

    override fun onCleared() {
        cleared = true
    }
}

/** A host that publishes a factory, which is what `ComponentActivity` does and the shape accepted. */
private class FakeHost(
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = NewFactory,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/** A host that publishes nothing, which is what the refusal above is for. */
private class PlainHost : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * A factory of this file's own, so that the identity assertion above has something recognisable to
 * look for.
 *
 * It builds nothing: the one test that holds it only compares it against the host's, and a factory
 * that quietly built a view model would let a substitution pass unnoticed anyway.
 */
private class NamedFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        throw UnsupportedOperationException("No test builds a view model through this factory.")
}
