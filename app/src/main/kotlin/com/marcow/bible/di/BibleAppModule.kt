package com.marcow.bible.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks app-level work that outlives any screen, so it can be distinguished from the
 * feature-scoped singletons each feature module binds for itself.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The app-level Hilt module: the single place `:app` wires the graph together.
 *
 * Every repository, database, DataStore and network binding lives in its own core or
 * feature module (`BibleDbModule`, `SettingsDataStoreModule`, `OpenRouterModule`,
 * `AiChatModule`, `SearchModule`, `DevotionModule`, `LegacyMigrationModule`), each
 * `@InstallIn(SingletonComponent::class)`, so each is already a singleton without anything here
 * re-declaring it. Hilt aggregates those modules automatically because `:app` depends on every one
 * of them (`app/build.gradle.kts`); this module only provides what belongs to no feature — the
 * application-wide coroutine scope the shell uses for work (legacy import, sign-in retries) that
 * must survive navigation and must die with the process rather than with a screen.
 */
@Module
@InstallIn(SingletonComponent::class)
object BibleAppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
