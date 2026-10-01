package com.marcow.bible.feature.search

import com.marcow.bible.feature.search.domain.AiSearch
import com.marcow.bible.feature.search.domain.AiSearchMemory
import com.marcow.bible.feature.search.domain.AiSearchUseCase
import com.marcow.bible.feature.search.domain.BlankAiSearchMemory
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
 * Three bindings, all of them seams rather than implementations: the two searches and the memory
 * block they read. Phase 4 replaces the third one with the store behind `memory.md`, and adds the
 * on-device provider behind [AiSearch] — which is why the sheet takes the ports rather than the use
 * cases, so nothing above this file has to move when it does.
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
}
