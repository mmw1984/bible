package com.marcow.bible.core.network.devotion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * The REST tier's decoder and its two routes, checked against `_decodeRestPosts` /
 * `_fetchFromRestApi` in `legacy/flutter/lib/devotion_content.dart`.
 *
 * The request itself is not exercised over a socket: `mockwebserver` is not on the version catalog
 * and there is no network in CI, so what is asserted here is the half that decides what the API's
 * answer means — the two URIs, and the projection of one payload.
 */
class DevotionRestClientTest {
    @Test
    fun `the pretty route asks for the six fields and twenty posts`() {
        val uri = DEVOTION_REST_URIS.first()
        assertTrue(uri.startsWith("$DEVOTION_ORIGIN$DEVOTION_REST_PATH?"), uri)
        assertTrue(uri.contains("per_page=20"), uri)
        assertTrue(uri.contains("_fields=id%2Cdate_gmt%2Cdate%2Ctitle%2Ccontent%2Clink"), uri)
    }

    @Test
    fun `the fallback route reaches the same api through the front controller`() {
        val uri = DEVOTION_REST_URIS.last()
        assertTrue(uri.startsWith("$DEVOTION_ORIGIN$DEVOTION_INDEX_PATH?"), uri)
        assertTrue(uri.contains("rest_route=%2Fwp%2Fv2%2Fposts"), uri)
        assertEquals(2, DEVOTION_REST_URIS.size)
    }

    @Test
    fun `a post carries its id, gmt publish time, rendered title and body`() {
        val posts = decodeDevotionPosts(
            """
            [
              {
                "id": 25436,
                "date_gmt": "2026-08-20T17:42:24",
                "date": "2026-08-21T01:42:24",
                "title": {"rendered": "[觀畫靈修] 亞伯蘭與撒萊在埃及 &amp;ndash;2026年8月21日"},
                "content": {"rendered": "<p>觀畫內文</p>"},
                "link": "https://devotion.wkphc.org/25436"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, posts.size)
        val post = posts.single()
        assertEquals(25436L, post.id)
        assertEquals(LocalDateTime.of(2026, 8, 20, 17, 42, 24), post.publishedAt)
        // The title keeps its markup: decoding it is HTML work, and `feature/devotion/domain` does it.
        assertEquals("[觀畫靈修] 亞伯蘭與撒萊在埃及 &amp;ndash;2026年8月21日", post.titleHtml)
        assertEquals("<p>觀畫內文</p>", post.contentHtml)
        assertEquals("https://devotion.wkphc.org/25436", post.link)
    }

    @Test
    fun `date_gmt falls back to date, then to now`() {
        val posts = decodeDevotionPosts(
            """[{"id":1,"date":"2026-08-21T01:42:24"},{"id":2,"date_gmt":"nonsense"}]""",
        )

        assertEquals(LocalDateTime.of(2026, 8, 21, 1, 42, 24), posts[0].publishedAt)
        assertEquals(posts[1].publishedAt.year, LocalDateTime.now().year)
    }

    @Test
    fun `a missing link becomes the site origin and missing fields become empty`() {
        val post = decodeDevotionPosts("""[{"id":7}]""").single()

        assertEquals(DEVOTION_ORIGIN, post.link)
        assertEquals("", post.titleHtml)
        assertEquals("", post.contentHtml)
        assertEquals(7L, post.id)
    }

    @Test
    fun `entries that are not objects are skipped rather than failing the tier`() {
        val posts = decodeDevotionPosts("""["nonsense", {"id":9,"link":"https://devotion.wkphc.org/9"}]""")

        assertEquals(listOf(9L), posts.map { it.id })
    }

    @Test
    fun `a body that is not an array fails the tier`() {
        val thrown = assertThrows<DevotionFetchException> { decodeDevotionPosts("""{"code":"rest_no_route"}""") }

        assertTrue(thrown.message!!.contains("rest_no_route"), thrown.message)
    }

    @Test
    fun `an unreadable body fails the tier`() {
        assertThrows<DevotionFetchException> { decodeDevotionPosts("<html>blocked</html>") }
    }

    @Test
    fun `an empty list is a failure, not an empty day`() {
        // `_decodeRestPosts` threw when the API answered with nothing; the client turns that into the
        // same throw by refusing to hand an empty list on.
        assertTrue(decodeDevotionPosts("[]").isEmpty())
    }
}
