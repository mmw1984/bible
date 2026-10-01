package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.database.DevotionCacheDao
import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.feature.devotion.domain.DevotionHeading
import com.marcow.bible.feature.devotion.domain.DevotionParagraph
import com.marcow.bible.feature.devotion.domain.DevotionPost
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The offline cache, over a fake DAO.
 *
 * What is asserted is the two properties the Flutter cache was designed around and that are easy to
 * lose in a rewrite: a write stores the *raw* post so a later read re-parses it, and the posts come
 * back in devotion-day order rather than in the order the blog published them.
 */
class DevotionCacheTest {
    private val dao = FakeDevotionCacheDao()

    @Test
    fun `a written feed reads back with its title, day and blocks`() = runTest {
        val cache = DevotionCache(dao)

        cache.write(listOf(post(id = 25436, title = "2026年8月21日 安靜", html = "<p>靈修的早晨</p>")))
        val posts = cache.read()

        assertEquals(1, posts.size)
        assertEquals("2026年8月21日 安靜", posts[0].title)
        assertEquals(LocalDate.of(2026, 8, 21), posts[0].devotionDate)
        assertEquals("https://devotion.wkphc.org/25436", posts[0].link)
        assertEquals(25436L, posts[0].id)
        assertEquals(listOf(DevotionParagraph("靈修的早晨")), posts[0].blocks)
    }

    @Test
    fun `the row stores raw post HTML rather than rendered blocks`() = runTest {
        val cache = DevotionCache(dao)
        val html = "<div class=\"dove\"><div class=\"title\">詩歌</div><p>蝶舞於花間</p></div>"

        cache.write(listOf(post(id = 1, title = "2026年8月20日 詩歌", html = html)))

        val raw = dao.entry("feed")?.rawPost.orEmpty()
        assertTrue(raw.contains("蝶舞於花間"), raw)
        // A parsed day is deliberately absent: it is read back out of the title on every load.
        assertFalse(raw.contains("devotionDate"), raw)
    }

    @Test
    fun `a stored post re-parses into blocks on every read`() = runTest {
        val cache = DevotionCache(dao)
        cache.write(listOf(post(id = 1, title = "2026年8月20日 詩歌", html = "<p>甲</p>")))
        val before = cache.read().single().blocks

        // Rewriting the body is how a parser improvement reaches posts that are already cached: the
        // row holds the post, never the blocks a previous run of the parser made of it.
        val stored = dao.entry("feed")!!.copy(rawPost = dao.entry("feed")!!.rawPost.replace("甲", "甲</p><h2>乙"))
        dao.rows["feed"] = stored
        val after = cache.read().single().blocks

        assertEquals(listOf(DevotionParagraph("甲")), before)
        assertEquals(listOf(DevotionParagraph("甲"), DevotionHeading("乙")), after)
    }

    @Test
    fun `the cached feed is ordered by devotion day, not publish order`() = runTest {
        val cache = DevotionCache(dao)

        cache.write(
            listOf(
                // Published first, but the devotion is for the later day.
                post(id = 1, title = "2026年7月22日 舊約", publishedAt = LocalDateTime.of(2026, 8, 21, 9, 0)),
                post(id = 2, title = "2026年8月21日 新約", publishedAt = LocalDateTime.of(2026, 8, 21, 10, 0)),
            ),
        )

        assertEquals(
            listOf("2026年8月21日 新約", "2026年7月22日 舊約"),
            cache.read().map { it.title },
        )
    }

    @Test
    fun `an empty or refused cache is no cache rather than a failure`() = runTest {
        assertTrue(DevotionCache(dao).read().isEmpty())
        assertTrue(DevotionCache(FailingDevotionCacheDao()).read().isEmpty())
    }

    @Test
    fun `a row that cannot be decoded is no cache`() = runTest {
        dao.rows["feed"] = DevotionCacheEntity(id = "feed", rawPost = "{not json", fetchedAt = 0)

        assertTrue(DevotionCache(dao).read().isEmpty())
    }

    @Test
    fun `a cached entry with no body is skipped instead of shown as an empty article`() = runTest {
        dao.rows["feed"] = DevotionCacheEntity(
            id = "feed",
            rawPost = """[{"id":1,"date":"2026-08-21T09:00","title":"空","link":"","content":""},
                {"id":2,"date":"2026-08-21T09:00","title":"有","link":"","content":"<p>內文</p>"}]""",
            fetchedAt = 0,
        )

        val posts = DevotionCache(dao).read()

        assertEquals(1, posts.size)
        assertEquals("有", posts[0].title)
        // `item['link'] ?? devotionOrigin`: an entry with no permalink still opens the site.
        assertEquals("https://devotion.wkphc.org", posts[0].link)
    }

    @Test
    fun `a refused write leaves the previous cache alone`() = runTest {
        val cache = DevotionCache(FailingDevotionCacheDao())

        cache.write(listOf(post(id = 1, title = "2026年8月20日 一")))

        assertTrue(cache.read().isEmpty())
    }

    @Test
    fun `a cached entry with an unreadable date falls back to the day it was read`() {
        val posts = decodeDevotionPostCache(
            """[{"id":1,"date":"not a date","title":"沒有日期","link":"/1","content":"<p>甲</p>"}]""",
        )

        val post = posts.single()
        // Neither the date nor the title carries a day, so the post files under the day it was read.
        assertEquals(post.publishedAt.toLocalDate(), post.devotionDate)
    }

    private fun post(
        id: Long,
        title: String,
        html: String = "<p>內文</p>",
        publishedAt: LocalDateTime = LocalDateTime.of(2026, 8, 21, 9, 0),
    ) = DevotionPost(
        id = id,
        publishedAt = publishedAt,
        devotionDate = LocalDate.of(2026, 8, 21),
        title = title,
        link = "https://devotion.wkphc.org/$id",
        contentHtml = html,
        blocks = emptyList(),
    )
}

/** The one row the cache writes, which is the only query the tests need to observe. */
private class FakeDevotionCacheDao : DevotionCacheDao {
    val rows = mutableMapOf<String, DevotionCacheEntity>()

    override suspend fun entry(id: String): DevotionCacheEntity? = rows[id]

    override suspend fun upsert(entry: DevotionCacheEntity) {
        rows[entry.id] = entry
    }

    override suspend fun clear() {
        rows.clear()
    }
}

/** A database that refuses everything, which is what a full disk looks like to the cache. */
private class FailingDevotionCacheDao : DevotionCacheDao {
    override suspend fun entry(id: String): DevotionCacheEntity? = throw IllegalStateException("disk full")

    override suspend fun upsert(entry: DevotionCacheEntity): Unit = throw IllegalStateException("disk full")

    override suspend fun clear(): Unit = throw IllegalStateException("disk full")
}