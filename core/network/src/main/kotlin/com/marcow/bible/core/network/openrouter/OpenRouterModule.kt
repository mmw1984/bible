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
 * The session is [OpenRouterAuthManager] — the PKCE sign-in of `NATIVE_PLAN.md` §4.7 — so the key the
 * chat and the search send with comes from the store the sign-in wrote, and neither of them moved when
 * it arrived.
 */
@Module
@InstallIn(SingletonComponent::class)
object OpenRouterModule {
    @Provides
    @Singleton
    fun provideOpenRouterSession(session: OpenRouterAuthManager): OpenRouterSession = session

    @Provides
    @Singleton
    fun provideOpenRouterModelId(modelId: StoredOpenRouterModelId): OpenRouterModelId = modelId

    @Provides
    @Singleton
    fun provideOpenRouterChatClient(client: HttpOpenRouterChatClient): OpenRouterChatClient = client

    /**
     * The forced web search, provided behind its own port because `research()` is a capability rather
     * than a completion: a server-side search with a budget and a citation list, which an on-device
     * provider cannot answer and so cannot be asked for through `AiProvider`.
     */
    @Provides
    @Singleton
    fun provideOpenRouterResearch(research: HttpOpenRouterResearch): OpenRouterResearch = research

    @Provides
    @Singleton
    fun provideOpenRouterTokenExchange(exchange: HttpOpenRouterTokenExchange): OpenRouterTokenExchange = exchange

    /**
     * [MigratingOpenRouterSecureStore] wrapping the encrypted store, so the API key the Flutter build
     * left in `FlutterSecureStorage.xml` is carried over on the first read. R1 accepts the attempt
     * failing, and the fallback is the sign-in prompt the app already shows.
     *
     * The decorator is built here rather than injected, since asking for it as a dependency would mean
     * asking for the [OpenRouterSecureStore] it is being bound as.
     */
    @Provides
    @Singleton
    fun provideOpenRouterSecureStore(
        store: EncryptedOpenRouterSecureStore,
        legacy: FlutterSecureStorageReader,
    ): OpenRouterSecureStore = MigratingOpenRouterSecureStore(store, legacy)

    @Provides
    @Singleton
    fun provideFlutterSecureStorageReader(reader: EncryptedFlutterSecureStorageReader): FlutterSecureStorageReader =
        reader

    @Provides
    @Singleton
    fun provideOpenRouterAuthorizeLauncher(
        launcher: CustomTabsOpenRouterAuthorizeLauncher,
    ): OpenRouterAuthorizeLauncher = launcher

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
