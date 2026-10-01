package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.network.devotion.DevotionPageClient
import com.marcow.bible.core.network.devotion.DevotionRestClient
import com.marcow.bible.core.network.devotion.DevotionRssClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the three tiers in the order the Flutter build tried them.
 *
 * The order is the substance of the fallback rather than an implementation detail: the API is the only
 * tier with history, the feed is the one that survives an API outage, and the pages are what is left
 * when both are unreachable.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevotionModule {
    @Provides
    @Singleton
    fun provideDevotionRepository(
        restClient: DevotionRestClient,
        rssClient: DevotionRssClient,
        pageClient: DevotionPageClient,
        cache: DevotionCache,
    ): DevotionRepository = DevotionRepository(
        tiers = listOf(RestDevotionTier(restClient), RssDevotionTier(rssClient), SitePageDevotionTier(pageClient)),
        cache = cache,
    )
}
