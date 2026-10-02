package com.marcow.bible.feature.devotion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two navigation rules the SoundCloud frame's delegate was made of, from `onNavigationRequest`
 * in `legacy/flutter/lib/devotion_soundcloud_player.dart`.
 *
 * They are pulled out of the `WebViewClient` and named because both of them are decisions about
 * whether a reader keeps their place, and neither is checkable by looking at a running frame: one
 * sends a link to another app, the other declines it. The rule that matters most is the one about
 * `sndcdn.com`, because that is the host the player redirects through constantly and a guard without
 * it bounces the widget out to a browser on the first seek.
 */
class DevotionEmbedPlayerTest {
    @Test
    fun `soundcloud's own hosts stay inside the frame`() {
        assertTrue(embedMayNavigateInPlace("https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/t"))
        assertTrue(embedMayNavigateInPlace("https://api.soundcloud.com/tracks/1"))
        assertTrue(embedMayNavigateInPlace("https://sndcdn.com/assets/abc.jpg"))
    }

    @Test
    fun `a host merely containing the name is matched, as Dart's contains did`() {
        // `host.contains(...)`, not equality: the Dart build used contains, and a subdomain of
        // SoundCloud's is still SoundCloud's. Narrowing this to equality would send a real player host
        // to the browser mid-playback.
        assertTrue(embedMayNavigateInPlace("https://m.soundcloud.com/tracks/1"))
        assertTrue(embedMayNavigateInPlace("https://a.sndcdn.com/x"))
    }

    @Test
    fun `anything else is handed to the browser instead`() {
        assertFalse(embedMayNavigateInPlace("https://devotion.wkphc.org/25436"))
        assertFalse(embedMayNavigateInPlace("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `a url that does not parse is left to the frame`() {
        // `Uri.tryParse(url) == null` returned `NavigationDecision.navigate` before the host was ever
        // compared, so a malformed URL was followed rather than escalated. Sending something no
        // browser could open to the browser would drop the tap on the floor instead.
        assertTrue(embedMayNavigateInPlace("::::not a url"))
        assertTrue(embedMayNavigateInPlace(""))
    }

    @Test
    fun `only http and https are escalated to the browser`() {
        assertTrue(embedLinkIsExternal("https://devotion.wkphc.org/25436"))
        assertTrue(embedLinkIsExternal("http://example.test/a"))

        // Everything else was `NavigationDecision.navigate` in Dart — followed by the frame. An
        // `intent:` or `mailto:` handed to the browser either opens another app over the reader's or
        // is dropped silently, and neither is what the widget did.
        assertFalse(embedLinkIsExternal("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(embedLinkIsExternal("mailto:someone@example.test"))
        assertFalse(embedLinkIsExternal("about:blank"))
        assertFalse(embedLinkIsExternal("::::not a url"))
    }

    @Test
    fun `an unparseable url is never escalated`() {
        assertFalse(embedLinkIsExternal("::::not a url"))
    }

    @Test
    fun `the wrapper asks the iframe api for the parameters the player needs`() {
        val html = youtubePlayerHtml("dQw4w9WgXcQ")

        // `playsinline=1` is what stops the video escalating itself to fullscreen on its own, and
        // loading `iframe_api` is the whole reason the page exists: the bare embed URL publishes no
        // handle on its player, so without this the double-tap seek has nothing to call.
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("onYouTubeIframeAPIReady"))
        assertTrue(html.contains("window.player = new YT.Player"))
        assertTrue(html.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("playsinline: 1"))
    }

    @Test
    fun `the frame asks youtube for the same two player decisions the dart params made`() {
        val html = youtubePlayerHtml("dQw4w9WgXcQ")

        // `showVideoAnnotations: false` at `legacy/flutter/lib/devotion_youtube_player.dart:71`, which
        // is YouTube's `iv_load_policy: 3`. Left at the default the player draws its annotations and an
        // endscreen over the video — the uncontrollable second UI layer the Dart build's comment on
        // those params says it was switching them off to avoid.
        assertTrue(html.contains("iv_load_policy: 3"))

        // `strictRelatedVideos: true`, the same params' other half: `strict: 1` limits the related list
        // to the video's own channel. `rel: 0` beside it already removes that list outright, so this is
        // the stricter of two settings rather than the only one — it is named so the Dart params are
        // not silently dropped.
        assertTrue(html.contains("strict: 1"))
        assertTrue(html.contains("rel: 0"))
    }

    @Test
    fun `the frame is given an origin for the api to check itself against`() {
        // `loadDataWithBaseURL` without a base would present the page as `about:blank`, and the IFrame
        // API refuses to run from there.
        assertEquals("https://www.youtube.com", YouTubeBaseUrl)
    }

    @Test
    fun `a video id is reduced to the shape youtube accepts`() {
        assertEquals("dQw4w9WgXcQ", sanitizedVideoId("dQw4w9WgXcQ"))
        assertEquals("ab-c_9", sanitizedVideoId("ab-c_9"))

        // The id goes into a JavaScript string literal, so a quote or a newline could otherwise close
        // it. YouTube ids are `[A-Za-z0-9_-]`, so dropping the rest costs no real id.
        assertEquals("abc", sanitizedVideoId("abc';alert(1);'"))
        assertEquals("abc", sanitizedVideoId("abc\nalert(1)"))
        assertEquals("", sanitizedVideoId("';alert(1);//"))
    }

    @Test
    fun `an injected id cannot leave the literal it was interpolated into`() {
        // The end-to-end version of the test above: whatever the id was, the page must still hold one
        // string literal for the video id and no statement of the attacker's.
        val html = youtubePlayerHtml(sanitizedVideoId("x',evil:'"))

        assertFalse(html.contains("evil"))
        assertEquals(1, Regex("videoId: '").findAll(html).count())
    }
}
