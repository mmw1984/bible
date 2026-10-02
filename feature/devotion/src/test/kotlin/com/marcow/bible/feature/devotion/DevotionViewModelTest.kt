package com.marcow.bible.feature.devotion

import com.marcow.bible.core.database.DevotionCacheEntity
import com.marcow.bible.core.datastore.InMemorySettingsDataStore
import com.marcow.bible.core.datastore.SettingsRepository
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.core.network.devotion.DevotionFetchException
import com.marcow.bible.feature.devotion.data.DevotionCache
import com.marcow.bible.feature.devotion.data.DevotionRepository
import com.marcow.bible.feature.devotion.data.DevotionTier
import com.marcow.bible.feature.devotion.data.FakeDevotionCacheDao
import com.marcow.bible.feature.devotion.data.encodeDevotionPostCache
import com.marcow.bible.feature.devotion.domain.DevotionParagraph
import com.marcow.bible.feature.devotion.domain.DevotionPost
import com.marcow.bible.feature.devotion.domain.DevotionQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The load rules `_DevotionPageState._load` had, over a scripted feed and the real cache.
 *
 * What is worth pinning is the behaviour a "just call the repository" view model would quietly lose:
 * which loads are allowed to disturb a reader who is already reading, and what happens to the article
 * on screen when a background refresh lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevotionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDevotionCacheDao()
    private val feed = ScriptedFeed()
    private val settings = SettingsRepository(InMemorySettingsDataStore())
    private lateinit var viewModel: DevotionViewModel

    @BeforeEach
    fun setUp() {
        // `viewModelScope` runs on the main dispatcher, so the tests replace it with the test one and
        // `advanceUntilIdle()` becomes the only thing that moves the state forward.
        Dispatchers.setMain(dispatcher)
        viewModel = DevotionViewModel(DevotionRepository(listOf(feed), DevotionCache(dao)), settings)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a cold start paints the cache and then asks the network`() = runTest(dispatcher) {
        dao.rows["feed"] = cacheRow(post(1, TODAY))
        feed.seed(listOf(post(2, TODAY)))

        advanceUntilIdle()

        // The cache was readable before the fetch, so the page is never blank while the network is out.
        assertEquals(listOf(2L), viewModel.state.value.posts.map { it.id })
        assertEquals(1, feed.calls)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `a successful fetch loads the feed`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))

        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.state.value.posts.map { it.id })
        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.failed)
        assertNull(viewModel.state.value.failureDetail)
        assertTrue(viewModel.state.value.loadedOnce)
    }

    @Test
    fun `a failed fetch with nothing cached is the failure screen`() = runTest(dispatcher) {
        feed.failWith("devotion site unreachable")

        advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.loading)
        assertEquals("devotion site unreachable", viewModel.state.value.failureDetail)
        assertFalse(viewModel.state.value.loadedOnce)
    }

    @Test
    fun `a failed fetch with a cached feed shows the cache, not an error`() = runTest(dispatcher) {
        dao.rows["feed"] = cacheRow(post(1, TODAY))
        feed.failWith("offline")

        advanceUntilIdle()

        assertFalse(viewModel.state.value.failed)
        assertEquals(listOf(1L), viewModel.state.value.posts.map { it.id })
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `a silent refresh keeps the reader on the post they were reading`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, YESTERDAY), post(2, TODAY), post(3, TWO_DAYS_AGO)))
        advanceUntilIdle()
        viewModel.select(0)
        assertEquals(1L, viewModel.state.value.post?.id)

        // Tomorrow's devotion goes out while the reader is on yesterday's, and the feed grows: the
        // reader must not be moved onto today's.
        feed.seed(listOf(post(4, TOMORROW), post(2, TODAY), post(1, YESTERDAY), post(3, TWO_DAYS_AGO)))
        viewModel.refreshIfStale(now = LocalDateTime.now().plusMinutes(31))
        advanceUntilIdle()

        assertEquals(listOf(4L, 2L, 1L, 3L), viewModel.state.value.posts.map { it.id })
        assertEquals(1L, viewModel.state.value.post?.id, "the silent refresh moved the reader")
    }

    @Test
    fun `a silent refresh puts no spinner over an article`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))
        advanceUntilIdle()

        viewModel.refreshIfStale(now = LocalDateTime.now().plusMinutes(31))

        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `a silent failure is not reported to a reader who has content`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))
        advanceUntilIdle()

        feed.failWith("gone offline")
        viewModel.refreshIfStale(now = LocalDateTime.now().plusMinutes(31))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.failed)
        assertNull(viewModel.state.value.failureDetail)
        assertEquals(listOf(1L), viewModel.state.value.posts.map { it.id })
    }

    @Test
    fun `a reader asking to refresh sees the cause under the article they have`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))
        advanceUntilIdle()

        feed.failWith("gone offline")
        viewModel.refresh()
        advanceUntilIdle()

        // Not the failure screen: the article is still readable.
        assertFalse(viewModel.state.value.failed)
        assertEquals("gone offline", viewModel.state.value.failureDetail)
        assertEquals(listOf(1L), viewModel.state.value.posts.map { it.id })
    }

    @Test
    fun `a feed that has never been fetched is always stale`() = runTest(dispatcher) {
        assertTrue(viewModel.shouldRefresh(LocalDateTime.now()))
    }

    @Test
    fun `a feed fetched moments ago is not stale, and is not refetched`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))
        advanceUntilIdle()

        assertFalse(viewModel.shouldRefresh(LocalDateTime.now()))

        val calls = feed.calls
        viewModel.refreshIfStale()
        advanceUntilIdle()
        assertEquals(calls, feed.calls)
    }

    @Test
    fun `a feed goes stale after half an hour, or at midnight`() = runTest(dispatcher) {
        feed.seed(listOf(post(1, TODAY)))
        advanceUntilIdle()
        val now = LocalDateTime.now()

        assertFalse(viewModel.shouldRefresh(now.plusMinutes(29)))
        assertTrue(viewModel.shouldRefresh(now.plusMinutes(31)))
        // The blog posts one devotion a day, so a new calendar day is stale however recent the fetch.
        assertTrue(viewModel.shouldRefresh(now.plusDays(1).plusMinutes(1)))
    }

    @Test
    fun `the interface language is read out of settings`() = runTest(dispatcher) {
        settings.setLocale(AppLocale.EN)
        feed.seed(listOf(post(1, TODAY)))

        advanceUntilIdle()

        assertTrue(viewModel.state.value.usesEnglishUi)
        assertEquals(AppLocale.EN, settings.settings.first().locale)
    }

    @Test
    fun `today's devotion opens by default rather than a scheduled one`() {
        val posts = listOf(
            post(3, TOMORROW.plusDays(1)),
            post(2, TOMORROW),
            post(1, TODAY),
            post(0, YESTERDAY),
        )

        assertEquals(2, viewModel.todayIndex(posts, TODAY))
    }

    @Test
    fun `with no devotion for today the newest past one opens`() {
        val posts = listOf(post(2, TOMORROW), post(1, TWO_DAYS_AGO))

        assertEquals(1, viewModel.todayIndex(posts, TODAY))
    }

    @Test
    fun `a feed of only future devotions opens the earliest of them`() {
        val posts = listOf(post(3, TOMORROW.plusDays(2)), post(2, TOMORROW))

        assertEquals(1, viewModel.todayIndex(posts, TODAY))
    }

    @Test
    fun `two devotions for the same day open the first`() {
        assertEquals(0, viewModel.todayIndex(listOf(post(2, TODAY), post(1, TODAY)), TODAY))
    }

    @Test
    fun `an empty feed opens nothing, and selects nothing`() = runTest(dispatcher) {
        feed.seed(emptyList())
        advanceUntilIdle()
        viewModel.select(4)

        assertNull(viewModel.state.value.post)
        assertEquals(0, viewModel.state.value.selected)
    }

    private fun post(id: Long, day: LocalDate) = DevotionPost(
        id = id,
        publishedAt = day.atTime(9, 0),
        devotionDate = day,
        title = "$day 默想",
        link = "https://devotion.wkphc.org/$id",
        contentHtml = "<p>內文</p>",
        blocks = listOf(DevotionParagraph("內文"), DevotionQuote("經文")),
    )

    private fun cacheRow(post: DevotionPost) = DevotionCacheEntity(
        id = "feed",
        rawPost = encodeDevotionPostCache(listOf(post)),
        fetchedAt = 0,
    )

    private companion object {
        /** Today, read once: a test that moved midnight mid-run would be a test about midnight. */
        val TODAY: LocalDate = LocalDate.now()
        val YESTERDAY: LocalDate = TODAY.minusDays(1)
        val TWO_DAYS_AGO: LocalDate = TODAY.minusDays(2)
        val TOMORROW: LocalDate = TODAY.plusDays(1)
    }
}

/**
 * One tier, scripted: [seed] is what the next fetch answers with, [failWith] what the next one throws.
 *
 * The view model is given a repository over this rather than the real clients because what is under test
 * is what the page does with a fetch that worked and with one that did not; how the tiers fall over each
 * other is `DevotionRepository`'s own test.
 */
private class ScriptedFeed : DevotionTier {
    var calls = 0
        private set
    private var answer: List<DevotionPost> = emptyList()
    private var failure: String? = null

    override val name: String = "scripted"

    fun seed(posts: List<DevotionPost>) {
        answer = posts
        failure = null
    }

    fun failWith(reason: String) {
        failure = reason
    }

    override suspend fun posts(): List<DevotionPost> {
        calls++
        failure?.let { throw DevotionFetchException(it) }
        return answer
    }
}
