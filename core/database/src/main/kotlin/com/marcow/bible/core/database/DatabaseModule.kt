package com.marcow.bible.core.database

import com.marcow.bible.core.common.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {

    @Binds
    @Singleton
    abstract fun bindsScriptureQueries(impl: SqliteScriptureQueries): ScriptureQueries
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @IoDispatcher
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
