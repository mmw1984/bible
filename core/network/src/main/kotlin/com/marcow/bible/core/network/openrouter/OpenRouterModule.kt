package com.marcow.bible.core.network.openrouter

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The OpenRouter transport graph.
 *
 * Phase 4 adds the PKCE sign-in here: `OpenRouterSession` is bound to the implementation that holds
 * the key, and nothing else in the app changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpenRouterModule {
    @Provides
    @Singleton
    fun provideOpenRouterSession(session: SignedOutOpenRouterSession): OpenRouterSession = session

    /**
     * One shared client for the whole app.
     *
     * OkHttp's defaults — a 10 s read timeout — are shorter than a model takes to answer a search,
     * so both the connect and the call timeout are raised. `callTimeout` is the backstop: it bounds
     * the whole exchange including retries, which a read timeout alone does not, because a stalled
     * connection can keep trickling bytes past it.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 180L
}
