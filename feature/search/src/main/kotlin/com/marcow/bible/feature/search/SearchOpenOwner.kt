package com.marcow.bible.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The [SearchViewModel] of one open of the sheet, so that a host which opens the sheet twice opens two
 * of them.
 *
 * [SearchRoute] and [SearchHost] each say in their own KDoc that an open has to have a view model of
 * its own, and until this file neither could be told to. `hiltViewModel()` with no owner resolves
 * against [LocalViewModelStoreOwner], which is the activity or the navigation destination, and both
 * outlive the sheet — the sheet is a Compose `Dialog` window, so there is no destination to scope it
 * to. An activity-scoped view model then hands the second open of a session the first one's query, its
 * results and its mode, which is the one thing Flutter could not get wrong: `_openSearch` pushed a new
 * `_SearchDialog` every time, and every push built a new `_SearchDialogState`, so a reopen was always
 * an empty box.
 *
 * A host cannot hand over that view model itself, which is the reason the store is here rather than
 * left to the caller. [SearchViewModel]'s constructor takes five injected ports, and a [ViewModelStore]
 * of the host's own would need a factory the host would have to dig out of Hilt first. So both live
 * next to the two composables that want them, and a host that passes nothing still gets a view model
 * per open.
 *
 * @see SearchOpenOwner for the store itself, which is the part a JVM test can reach.
 */
@Composable
fun rememberSearchViewModelForOpen(): SearchViewModel {
    val host = checkNotNull(LocalViewModelStoreOwner.current) {
        "The search sheet's view model is drawn inside a composition that has no ViewModelStoreOwner."
    }
    val owner = remember(host) { SearchOpenOwner(host) }
    // Cleared here rather than left behind: this store belongs to no host, so nothing else tears it
    // down. An uncleared one keeps its `SearchViewModel` — and the signed-in watcher that constructor
    // starts — collecting for the rest of the process, once for every open of the sheet.
    DisposableEffect(owner) { onDispose { owner.clear() } }
    return hiltViewModel(viewModelStoreOwner = owner)
}

/**
 * A [ViewModelStoreOwner] per open of the sheet: a store of its own, filled by the host's own factory.
 *
 * `hiltViewModel` builds the view model from [defaultViewModelProviderFactory] whenever the owner has
 * one, and has no way to build a `@HiltViewModel` without it, so the host's is handed straight through
 * rather than replaced. The activity's factory is already the Hilt one; a second wrapper around it
 * would only move the point at which a misconfigured host fails.
 *
 * It is a class and not a bare [ViewModelStore] because the two halves have to come from the same
 * place: a host-scoped factory over an open-scoped store is the whole design, and a `remember` of a
 * store on its own would have left the factory to whoever called it.
 */
internal class SearchOpenOwner(host: ViewModelStoreOwner) :
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    /**
     * A store of its own, which is the whole of what makes a reopen a new open.
     *
     * Nothing in it is keyed to the host's store, so two opens of one session over one activity share
     * no view model: the second open's [SearchViewModel] is built by the same constructor out of the
     * same five ports, and arrives empty.
     */
    override val viewModelStore: ViewModelStore = ViewModelStore()

    /**
     * The host's factory, or a refusal.
     *
     * An owner that publishes none is turned away here rather than left to fail downstream as "Cannot
     * create an instance of class SearchViewModel", which blames the view model for something the
     * composition around it is missing, and names neither. So the refusal names what is missing:
     * `ViewModelProvider.Factory`, the one type that can build a `@HiltViewModel`.
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        checkNotNull((host as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory) {
            "A @HiltViewModel needs the ViewModelStoreOwner it is drawn over to publish a " +
                "ViewModelProvider.Factory, because that is the only thing that can build one."
        }

    /** What [rememberSearchViewModelForOpen] runs when the host stops drawing the sheet. */
    fun clear() = viewModelStore.clear()
}
