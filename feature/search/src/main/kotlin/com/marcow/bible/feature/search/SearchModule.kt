package com.marcow.bible.feature.search

import com.marcow.bible.feature.search.domain.AiSearch
import com.marcow.bible.feature.search.domain.AiSearchMemory
import com.marcow.bible.feature.search.domain.AiSearchUseCase
import com.marcow.bible.feature.search.domain.BlankAiSearchMemory
import com.marcow.bible.feature.search.domain.OpenRouterSearchSignIn
import com.marcow.bible.feature.search.domain.SearchSignIn
import com.marcow.bible.feature.search.domain.TraditionalSearch
import com.marcow.bible.feature.search.domain.TraditionalSearchUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The search domain layer as the sheet depends on it.
 *
 * Four bindings, all of them seams rather than implementations: the two searches, the memory block they
 * read, and the half of the sign-in that belongs to whoever opens the sheet. Phase 4 replaces the
 * memory block with the store behind `memory.md`, and adds the on-device provider behind [AiSearch] —
 * which is why the sheet takes the ports rather than the use cases, so nothing above this file has to
 * move when it does.
 *
 * [SearchSignIn] is the fourth for the same reason `AiChatModule` binds `AiChatSignIn`: the sign-in
 * lives in `core/network` and cannot be built in a JVM test, while the order a host has to get right —
 * the query is armed before the browser is asked for — is only reachable through something a test can
 * stand up. `OpenRouterSearchSignIn` is the adapter over `OpenRouterAuthManager`, and a test binds a
 * stub instead.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindTraditionalSearch(search: TraditionalSearchUseCase): TraditionalSearch

    @Binds
    @Singleton
    abstract fun bindAiSearch(search: AiSearchUseCase): AiSearch

    @Binds
    @Singleton
    abstract fun bindAiSearchMemory(memory: BlankAiSearchMemory): AiSearchMemory

    @Binds
    @Singleton
    abstract fun bindSearchSignIn(signIn: OpenRouterSearchSignIn): SearchSignIn
}
