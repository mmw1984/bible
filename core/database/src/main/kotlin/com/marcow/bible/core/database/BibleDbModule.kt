package com.marcow.bible.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BibleDbModule {
    @Provides
    @Singleton
    fun provideBibleDb(@ApplicationContext context: Context): BibleDb =
        Room.databaseBuilder(context, BibleDb::class.java, BibleDb.DATABASE_NAME)
            .createFromAsset(BibleDb.ASSET_PATH)
            // No fallbackToDestructiveMigration: `reading_progress` holds user data, and the
            // scripture tables are static. A new version needs an explicit migration.
            .build()
}
