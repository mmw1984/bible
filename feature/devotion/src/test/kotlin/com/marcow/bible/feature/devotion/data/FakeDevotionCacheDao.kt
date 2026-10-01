package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.database.DevotionCacheDao
import com.marcow.bible.core.database.DevotionCacheEntity

/**
 * The one row the cache writes, which is the only query the tests need to observe.
 *
 * `writes` counts upserts so a caller can tell "the feed was cached" from "a cache that was already
 * there was read", which two of the repository tests need to keep apart.
 */
internal class FakeDevotionCacheDao : DevotionCacheDao {
    val rows = mutableMapOf<String, DevotionCacheEntity>()
    var writes = 0
        private set

    override suspend fun entry(id: String): DevotionCacheEntity? = rows[id]

    override suspend fun upsert(entry: DevotionCacheEntity) {
        writes++
        rows[entry.id] = entry
    }

    override suspend fun clear() {
        rows.clear()
    }
}

/** A database that refuses everything, which is what a full disk looks like to the cache. */
internal class FailingDevotionCacheDao : DevotionCacheDao {
    override suspend fun entry(id: String): DevotionCacheEntity? = throw IllegalStateException("disk full")

    override suspend fun upsert(entry: DevotionCacheEntity): Unit = throw IllegalStateException("disk full")

    override suspend fun clear(): Unit = throw IllegalStateException("disk full")
}
