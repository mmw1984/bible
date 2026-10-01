package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.core.network.devotion.DevotionFetchException
import com.marcow.bible.feature.devotion.domain.DevotionParagraph
import com.marcow.bible.feature.devotion.domain.DevotionPost
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The tier fallback and the cache handoff, which together are what `fetchDevotionPosts` was.
 *
 * The cases are the paths the Dart fallback had: the API answers, the API is down so the feed answers,
 * everything is down so the pages answer, and — the one that is easy to forget when splitting a
 * nested `try` into three types — a tier that answers *with nothing* is a failure and the next tier
 * still gets its turn.
 */
class DevotionRepositoryTest {
    private val dao = FakeDevotionCacheDao()
    private val cache = DevotionCache(dao)

    @Test
    fun `the first tier that answers wins and no later tier is asked`() = runTest {
        val rest = RecordingTier("rest", listOf(post(id = 1, day = DAY_TWO)))
        val rss = RecordingTier("rss", listOf(post(id = 2, day = DAY_ONE)))
        val site = RecordingTier("site", listOf(post(id = 3, day = DAY_ONE)))

        val posts = repository(rest, rss, site).fetchPosts()

        assertEquals(listOf(1L), posts.map { it.id })
        assertEquals(0, rss.calls, "the RSS tier must not be asked once the API answered")
        assertEquals(0, site.calls)
    }

    @Test
    fun `a failed tier falls through to the next`() = runTest {
        val rest = FailingTier("rest", "api closed")
        val rss = RecordingTier("rss", listOf(post(id = 2, day = DAY_TWO)))

        val posts = repository(rest, rss).fetchPosts()

        assertEquals(listOf(2L), posts.map { it.id })
        assertEquals(1, rss.calls)
    }

    @Test
    fun `a tier that answers with nothing is a failure`() = runTest {
        // `if (posts.isEmpty) throw` in Dart: an empty feed must not end the load on an empty page.
        val rest = RecordingTier("rest", emptyList())
        val rss = RecordingTier("rss", listOf(post(id = 2, day = DAY_TWO)))

        val posts = repository(rest, rss).fetchPosts()

        assertEquals(listOf(2L), posts.map { it.id })
        assertEquals(1, rss.calls)
    }

    @Test
    fun `a tier that fails in an unexpected way still degrades`() = runTest {
        // The Dart fetch wrapped each tier in `catch (_)`, which caught everything, so a parser or
        // transport bug must not be the one thing that ends the load.
        val rest = ExplodingTier("rest", IllegalStateException("decoder blew up"))
        val site = RecordingTier("site", listOf(post(id = 3, day = DAY_ONE)))

        val posts = repository(rest, site).fetchPosts()

        assertEquals(listOf(3L), posts.map { it.id })
    }

    @Test
    fun `every tier failing reports the tier that got furthest`() = runTest {
        val rest = FailingTier("rest", "api closed")
        val rss = FailingTier("rss", "feed blocked")
        val site = FailingTier("site", "homepage unreachable")

        val thrown = assertThrows<DevotionFetchException> {
            repository(rest, rss, site).fetchPosts()
        }

        // `lastError` in Dart: the last tier tried is the furthest anyone got.
        assertEquals("homepage unreachable", thrown.message)
        assertEquals(listOf(1, 1, 1), listOf(rest.calls, rss.calls, site.calls))
    }

    @Test
    fun `an unexpected failure names the tier it happened on`() = runTest {
        val rest = ExplodingTier("rest", IllegalStateException("decoder blew up"))

        val thrown = assertThrows<DevotionFetchException> { repository(rest).fetchPosts() }

        assertTrue(thrown.message!!.startsWith("rest tier failed:"), thrown.message)
        assertTrue(thrown.message!!.contains("decoder blew up"), thrown.message)
    }

    @Test
    fun `the fetched posts are cached on the way through`() = runTest {
        val rest = RecordingTier("rest", listOf(post(id = 1, day = DAY_TWO)))

        repository(rest).fetchPosts()

        assertEquals(1, dao.writes)
        assertEquals(listOf(1L), cache.read().map { it.id })
    }

    @Test
    fun `a failed fetch leaves the last good feed in the cache`() = runTest {
        // `unawaited(writeDevotionCache(fetched))` only ran on success; an outage must leave the last
        // good feed in place rather than replacing it with nothing.
        cache.write(listOf(post(id = 7, day = DAY_ONE)))
        val repository = repository(FailingTier("rest", "api closed"), FailingTier("rss", "feed blocked"))

        assertThrows<DevotionFetchException> { repository.fetchPosts() }

        assertEquals(1, dao.writes, "a failed fetch must not write over the cache")
        assertEquals(listOf(7L), cache.read().map { it.id })
    }

    @Test
    fun `the cache is read without the network`() = runTest {
        dao.rows["feed"] = DevotionCacheEntity(
            id = "feed",
            rawPost = encodeDevotionPostCache(listOf(post(id = 7, day = DAY_ONE))),
            fetchedAt = 0,
        )

        assertEquals(listOf(7L), repository().cachedPosts().map { it.id })
    }

    private fun repository(vararg tiers: DevotionTier) = DevotionRepository(tiers.toList(), cache)

    private fun post(id: Long, day: LocalDate) = DevotionPost(
        id = id,
        publishedAt = LocalDateTime.of(day.atTime(9, 0)),
        devotionDate = day,
        title = "${day} 默想",
        link = "https://devotion.wkphc.org/$id",
        contentHtml = "<p>內文</p>",
        blocks = listOf(DevotionParagraph("內文")),
    )

    private companion object {
        val DAY_TWO = LocalDate.of(2026, 8, 21)
        val DAY_ONE = LocalDate.of(2026, 8, 20)
    }
}

private class RecordingTier(private val tierName: String, private val posts: List<DevotionPost>) : DevotionTier {
    var calls = 0
        private set

    override val name: String = tierName

    override suspend fun posts(): List<DevotionPost> {
        calls++
        return posts
    }
}

private class FailingTier(private val tierName: String, private val reason: String) : DevotionTier {
    var calls = 0
        private set

    override val name: String = tierName

    override suspend fun posts(): List<DevotionPost> {
        calls++
        throw DevotionFetchException(reason)
    }
}

private class ExplodingTier(
    private val tierName: String,
    private val error: Exception,
) : DevotionTier {
    override val name: String = tierName

    override suspend fun posts(): List<DevotionPost> = throw error
}