package com.marcow.bible.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Row of the `devotion_cache` table. Holds the raw WordPress post JSON so a parser upgrade
 * re-parses naturally instead of serving stale blocks, which is the same design the Flutter app
 * uses today (see `NATIVE_PLAN.md` §3.6).
 */
@Entity(tableName = "devotion_cache")
data class DevotionCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "raw_post")
    val rawPost: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
