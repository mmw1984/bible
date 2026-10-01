package com.marcow.bible.feature.aichat

import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.openrouter.OpenRouterAiProvider
import com.marcow.bible.feature.aichat.domain.AiChatSignIn
import com.marcow.bible.feature.aichat.domain.AiMemoryStore
import com.marcow.bible.feature.aichat.domain.AskAiQuestion
import com.marcow.bible.feature.aichat.domain.AskQuestionUseCase
import com.marcow.bible.feature.aichat.domain.BlankAiMemoryStore
import com.marcow.bible.feature.aichat.domain.OpenRouterChatSignIn
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The chat's own bindings, the counterpart of `SearchModule` in `feature/search`.
 *
 * Three bindings, and every one is a seam rather than an implementation.
 *
 * [AiMemoryStore] is where the chat's memory lives, and `NATIVE_PLAN.md` §4.6 replaces the
 * `memory.md` / `transcript.jsonl` files with Room tables; until that lands the chat is bound to a store
 * that has neither, which is exactly the state the Flutter build was in on a fresh install — no history
 * and no memory to read.
 *
 * [AskAiQuestion] is the answering loop, bound so the view model takes the port. The view model is
 * where the reader's half of a turn lives and this is the model's half, and a stop landing between two
 * rounds is a thing that can be written down against a stub and not against a live model.
 *
 * [AiChatSignIn] is the sign-in, whose OpenRouter half is `OpenRouterChatSignIn` — an adapter rather
 * than the manager itself, because the port is the chat's and core cannot depend on a feature.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiChatModule {
    @Binds
    @Singleton
    abstract fun bindAiMemoryStore(store: BlankAiMemoryStore): AiMemoryStore

    @Binds
    @Singleton
    abstract fun bindAskAiQuestion(askQuestion: AskQuestionUseCase): AskAiQuestion

    @Binds
    @Singleton
    abstract fun bindAiChatSignIn(signIn: OpenRouterChatSignIn): AiChatSignIn
}

/**
 * The provider the chat answers through, provided rather than bound because [AiProvider] has two
 * implementations in the plan and one of them today.
 *
 * `NATIVE_PLAN.md` §4.3 makes OpenRouter the default precisely so that this binding is a temporary
 * answer rather than a decision: `AiProviderId.fromStorage` reads the `ai_provider` setting, that
 * setting is not in the proto yet, and when it arrives this becomes a lookup against it. Binding
 * `OpenRouterAiProvider` directly to the port now is what lets the chat, the search and any future
 * screen take [AiProvider] without each of them naming a transport.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiChatProviderModule {
    @Provides
    @Singleton
    fun provideAiProvider(provider: OpenRouterAiProvider): AiProvider = provider
}
