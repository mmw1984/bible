package com.marcow.bible.core.network.devotion

import java.time.LocalDateTime

/**
 * One devotional article as the WordPress blog publishes it, before any of it is interpreted.
 *
 * The fields are kept verbatim for the same reason `DevotionPost.contentHtml` was: the cache stores
 * raw post data, not rendered blocks, so a parser improvement re-parses old entries instead of
 * serving what an older parser made of them (see `NATIVE_PLAN.md` §3.6).
 *
 * There is deliberately no `blocks` here. `parseDevotionBlocks` lives with the feature that renders
 * the result (`feature/devotion/domain/HtmlToBlocks.kt`), and `core:network` cannot depend on a
 * feature — so the transport returns what the site said and the reader turns it into blocks.
 *
 * @property id the WordPress post id, or `0` for the sources that carry none (RSS, page scraping).
 * @property publishedAt the publish time the API reported, in the site's own local time.
 * @property titleHtml `title.rendered` exactly as it arrived, entities and all. The reader is what
 *   strips tags and decodes entities, because that is HTML handling.
 * @property link the permalink, falling back to [DEVOTION_ORIGIN] when the source has none.
 * @property contentHtml the post body, verbatim.
 */
data class DevotionPost(
    val id: Long,
    val publishedAt: LocalDateTime,
    val titleHtml: String,
    val link: String,
    val contentHtml: String,
)