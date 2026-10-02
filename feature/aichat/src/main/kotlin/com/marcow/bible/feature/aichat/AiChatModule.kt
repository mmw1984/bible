package com.marcow.bible.feature.aichat

import com.marcow.bible.core.network.ai.AiProvider
import com.marcow.bible.core.network.aicore.GeminiNanoAiProvider
import com.marcow.bible.core.network.aicore.NanoModelFactory
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
import javax.inject.Named
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
 *
 * [NanoModelFactory] is the on-device model behind the second provider; see below.
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

    /**
     * The on-device model behind [GeminiNanoAiProvider], bound to the placeholder until the Play
     * services for AI Edge client lands. The swap is this one line: the provider and the chat both
     * read the port and never name the implementation.
     */
    @Binds
    @Singleton
    abstract fun bindNanoModelFactory(factory: UnavailableNanoModelFactory): NanoModelFactory
}

/**
 * The two providers the chat answers through, named rather than bound because [AiProvider] is one
 * port with two implementations and the `ai_provider` setting is what chooses between them.
 *
 * OpenRouter is the default (`AiProviderId.fromStorage` resolves a fresh install and every unknown
 * tag to it), and `AiChatViewModel` is the one that reads the setting — a use case takes its
 * provider as a parameter precisely so the choice stays in one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiChatProviderModule {
    @Provides
    @Singleton
    @Named(OPEN_ROUTER_PROVIDER)
    fun provideOpenRouterAiProvider(provider: OpenRouterAiProvider): AiProvider = provider

    @Provides
    @Singleton
    @Named(GEMINI_NANO_PROVIDER)
    fun provideGeminiNanoAiProvider(provider: GeminiNanoAiProvider): AiProvider = provider
}

/** Qualifier for the OpenRouter [AiProvider]: the cloud half of the pair. */
const val OPEN_ROUTER_PROVIDER = "openRouter"

/** Qualifier for the Gemini Nano [AiProvider]: the on-device half of the pair. */
const val GEMINI_NANO_PROVIDER = "geminiNano"
