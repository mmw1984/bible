package com.marcow.bible.feature.aichat

import com.marcow.bible.feature.aichat.domain.AiMemoryStore
import com.marcow.bible.feature.aichat.domain.BlankAiMemoryStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The chat's own bindings, the counterpart of `SearchModule` in `feature/search`.
 *
 * One binding, and it is a seam rather than an implementation: the memory the prompt carries is
 * [AiMemoryStore], and `NATIVE_PLAN.md` §4.6 replaces the `memory.md` / `transcript.jsonl` files with
 * Room tables. Until that lands the chat is bound to a store that has neither, which is exactly the
 * state the Flutter build was in on a fresh install — no history and no memory to read.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiChatModule {
    @Binds
    @Singleton
    abstract fun bindAiMemoryStore(store: BlankAiMemoryStore): AiMemoryStore
}
