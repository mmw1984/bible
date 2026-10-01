package com.marcow.bible.core.network.devotion

import okhttp3.HttpUrl.Companion.toHttpUrl

/** `devotionOrigin` in `legacy/flutter/lib/devotion_content.dart`. */
const val DEVOTION_ORIGIN = "https://devotion.wkphc.org"

/**
 * `_kDevotionHeaders`: present as a browser because some WordPress security plugins reject unknown
 * user agents, which is what would explain the REST endpoint failing on devices.
 *
 * Kept as one constant so every tier of the fetch sends the same thing — the RSS feed and the
 * scraped pages go through the same door as the API, and are as welcome as it is.
 */
internal val DEVOTION_HEADERS: Map<String, String> = mapOf(
    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
)

/**
 * The two REST routes `_fetchFromRestApi` tried, in order.
 *
 * The first is the pretty-permalink route; the second is `index.php?rest_route=…`, which survives
 * the security-plugin and permalink setups that block `/wp-json`. Both ask for the same six fields
 * and the same page size, so which one answered cannot change what comes back.
 */
internal val DEVOTION_REST_URIS: List<String> = listOf(
    "$DEVOTION_ORIGIN$DEVOTION_REST_PATH",
    "$DEVOTION_ORIGIN$DEVOTION_INDEX_PATH",
).mapIndexed { index, base ->
    base.toHttpUrl().newBuilder()
        .addQueryParameter("per_page", DEVOTION_PAGE_SIZE.toString())
        .addQueryParameter("_fields", DEVOTION_FIELDS)
        .apply { if (index == 1) addQueryParameter("rest_route", DEVOTION_REST_ROUTE) }
        .build()
        .toString()
}

/** `_kDevotionApi`: the REST endpoint itself. */
internal const val DEVOTION_REST_PATH = "/wp-json/wp/v2/posts"

/** The front controller that answers the same API when `/wp-json` is blocked. */
internal const val DEVOTION_INDEX_PATH = "/index.php"

/** The `rest_route` value that makes [DEVOTION_INDEX_PATH] serve [DEVOTION_REST_PATH]. */
internal const val DEVOTION_REST_ROUTE = "/wp/v2/posts"

/** `_kDevotionRss`: the classic feed, which stays open when the REST API does not. */
internal const val DEVOTION_FEED_PATH = "/feed"

/** `per_page: '20'` of `_fetchFromRestApi`. */
internal const val DEVOTION_PAGE_SIZE = 20

/** `id,date_gmt,date,title,content,link`, the `_fields` of `_fetchFromRestApi`. */
internal const val DEVOTION_FIELDS = "id,date_gmt,date,title,content,link"