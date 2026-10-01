package com.marcow.bible.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app database. Scripture text ships as a pre-packaged asset
 * (`app/src/main/assets/databases/bible.db`, built by `tool/build_bible_db.mjs`) and is opened
 * with `createFromAsset`, so [VERSION] must stay in step with the `PRAGMA user_version` written by
 * that script. `fallbackToDestructiveMigration` is deliberately not used: losing user reading
 * positions on a schema change would be a silent data loss.
 */
@Database(
    entities = [
        BookEntity::class,
        VerseEntity::class,
        ReadingProgressEntity::class,
        DevotionCacheEntity::class,
    ],
    version = BibleDb.VERSION,
    exportSchema = true,
)
abstract class BibleDb : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun devotionCacheDao(): DevotionCacheDao

    companion object {
        const val VERSION = 1

        /** Asset path passed to `Room.databaseBuilder(...).createFromAsset(...)`. */
        const val ASSET_PATH = "databases/bible.db"

        const val DATABASE_NAME = "bible.db"
    }
}
