package com.marcow.bible.feature.devotion.data

import com.marcow.bible.core.network.devotion.DevotionFetchException
import com.marcow.bible.feature.devotion.domain.DevotionPost
import kotlinx.coroutines.CancellationException

/**
 * Where the devotion feed comes from, replacing `fetchDevotionPosts` in
 * `legacy/flutter/lib/devotion_content.dart`.
 *
 * The tiers are tried in the order they are handed in and the first one that answers wins, which is
 * the whole of the Dart function: `try { return await _fetchFromRestApi(...) } catch (_) { return await
 * _fetchFromRssFeed(...) } catch (_) { return _fetchFromSitePages(...) }`. What this adds is that a
 * tier which *answers with nothing* is also a failure — `http.ClientException('devotion api returned
 * no posts')` and `if (posts.isEmpty) throw` did that in Dart — so an empty feed degrades instead of
 * showing the reader an empty page.
 *
 * The winning posts are cached on the way through, which is where Flutter's `unawaited(
 * writeDevotionCache(fetched))` sat: it fired and was not waited for, and here the cache write is the
 * same best-effort call the cache itself makes.
 *
 * Constructed by [DevotionModule] rather than injected directly, because the tier list *is* the
 * fallback order, and a `List<DevotionTier>` is not a constructor parameter a module can resolve.
 */
class DevotionRepository(private val tiers: List<DevotionTier>, private val cache: DevotionCache) {
    /**
     * The last fetch that worked, re-parsed with the current parser. Never fails.
     *
     * Read on a cold start so the reader has something before — or without — a network round-trip,
     * exactly as `_load` painted `readDevotionCache()` before calling `fetchDevotionPosts`.
     */
    suspend fun cachedPosts(): List<DevotionPost> = cache.read()

    /**
     * The posts from the first tier that answers, written to the cache as they arrive.
     *
     * @throws DevotionFetchException when no tier produced anything. The message names the tier that
     *   got furthest, because that is the one worth diagnosing from; the reader shows a generic
     *   message over it.
     */
    suspend fun fetchPosts(): List<DevotionPost> {
        var failure: DevotionFetchException? = null
        for (tier in tiers) {
            val posts = try {
                tier.posts()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (tierFailure: DevotionFetchException) {
                // `lastError = error` in Dart: the next tier may still work, and the one that got
                // furthest is the one worth reporting if none of them do.
                failure = tierFailure
                continue
            } catch (unexpected: Exception) {
                // The Dart fetch caught `Object?` around each tier, so a tier that failed in a way
                // the transport did not anticipate still degrades instead of ending the load.
                failure = DevotionFetchException("${tier.name} tier failed: ${unexpected.message}", unexpected)
                continue
            }
            if (posts.isEmpty()) {
                failure = DevotionFetchException("${tier.name} tier returned no posts")
                continue
            }
            cache.write(posts)
            return posts
        }
        throw failure ?: DevotionFetchException("devotion fetch produced no posts")
    }
}
