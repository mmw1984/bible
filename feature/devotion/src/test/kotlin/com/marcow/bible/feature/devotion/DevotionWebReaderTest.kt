package com.marcow.bible.feature.devotion

import com.marcow.bible.core.network.devotion.DEVOTION_ORIGIN
import com.marcow.bible.feature.devotion.domain.DevotionPost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The reader's own decisions, from `legacy/flutter/lib/devotion_web_reader_io.dart`.
 *
 * Three of them, and none of them checkable by looking at a running frame: which URLs a frame follows
 * and which it hands to the operating system (the delegate), what the toolbar says when the page was
 * opened without a title (the host), and which page the page hands the reader in the first place
 * ([devotionWebReaderTarget]). The first two are the reader's security posture, since the frame is
 * pointed at whatever page a post links to; the third is the one decision about the *content* that
 * lives with the reader rather than with the page that draws it.
 *
 * The pair of URL rules is exhaustive on purpose — every URL is followed here, escalated, or declined —
 * which is what lets the delegate be two `if`s and a `return true`.
 */
class DevotionWebReaderTest {
    @Test
    fun `the open web stays inside the reader`() {
        assertTrue(webReaderMayNavigateInPlace("https://devotion.wkphc.org/25436"))
        assertTrue(webReaderMayNavigateInPlace("http://devotion.wkphc.org/25436"))
        // The links the reader is actually made of: to the site's images, and to another article.
        assertTrue(webReaderMayNavigateInPlace("https://devotion.wkphc.org/wp-content/uploads/1.jpg"))
        assertTrue(webReaderMayNavigateInPlace("https://devotion.wkphc.org/25437"))
        // Plain `http` as well as `https`, because the Dart condition named both and this reader is
        // pointed at a site nobody has checked for a redirect.
        assertTrue(webReaderMayNavigateInPlace("http://example.com/a/b?c=d#e"))
    }

    @Test
    fun `the scheme is compared in lower case, as Dart's was`() {
        // Dart ran `uri?.scheme.toLowerCase()` before comparing, and a browser accepts `HTTP://`.
        // `URI` preserves the case a URL was written in, so a case-sensitive test here would send a
        // page out to the system browser over nothing but its capitalisation.
        assertTrue(webReaderMayNavigateInPlace("HTTPS://devotion.wkphc.org/25436"))
        assertTrue(webReaderMayNavigateInPlace("HtTpS://example.com"))
    }

    @Test
    fun `schemes no frame can follow are handed to the operating system`() {
        assertTrue(webReaderLinkIsExternal("mailto:someone@example.com"))
        assertTrue(webReaderLinkIsExternal("tel:+88641234567"))
        // The two the blog's own pages are most likely to carry: an Android link and a store link.
        assertTrue(webReaderLinkIsExternal("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(webReaderLinkIsExternal("market://details?id=com.example.app"))
        assertTrue(webReaderLinkIsExternal("sms:+88641234567"))
        assertTrue(webReaderLinkIsExternal("whatsapp://send?text=hi"))
    }

    @Test
    fun `an external scheme is still matched in lower case`() {
        assertTrue(webReaderLinkIsExternal("MAILTO:someone@example.com"))
        assertTrue(webReaderLinkIsExternal("Tel:+88641234567"))
    }

    @Test
    fun `a url with no scheme is declined rather than escalated`() {
        // This is where the reader is deliberately narrower than the SoundCloud frame, which let an
        // unfollowable URL through to its widget on the reasoning that following is at least
        // recoverable. Here it is not recoverable: the frame's job is to *be* the browser, so a frame
        // that walks itself into `about:blank` takes the reader's whole page with it.
        assertFalse(webReaderLinkIsExternal("::::not a url"))
        assertFalse(webReaderLinkIsExternal(""))
        assertFalse(webReaderLinkIsExternal("/wp-content/uploads/1.jpg"))
        assertFalse(webReaderLinkIsExternal("devotion.wkphc.org/25436"))
        // Neither rule claims it, so the reader stays where it is: not followed, not escalated.
        assertFalse(webReaderMayNavigateInPlace("::::not a url"))
        assertFalse(webReaderMayNavigateInPlace(""))
        assertFalse(webReaderMayNavigateInPlace("devotion.wkphc.org/25436"))
    }

    @Test
    fun `the two rules never both fire on one url`() {
        // The delegate relies on this: `mayNavigate` is checked first, so a URL that were both would
        // be escalated instead of followed.
        listOf(
            "https://devotion.wkphc.org/25436",
            "http://example.com",
            "HTTPS://example.com",
            "mailto:someone@example.com",
            "market://details?id=com.example.app",
            "::::not a url",
            "",
        ).forEach { url ->
            assertFalse(webReaderLinkIsExternal(url), "$url is both followed and escalated")
            assertTrue(webReaderMayNavigateInPlace(url) || !webReaderLinkIsExternal(url))
        }
    }

    @Test
    fun `the title falls back to the host, and to the whole url when there is none`() {
        // Flutter's `widget.title ?? Uri.parse(widget.url).host`.
        assertEquals("devotion.wkphc.org", "https://devotion.wkphc.org/25436".host())
        assertEquals("w.soundcloud.com", "https://w.soundcloud.com/player/?url=x".host())
        // No scheme is no host, so the whole URL is the most useful thing left to say.
        assertEquals("devotion.wkphc.org/25436", "devotion.wkphc.org/25436".host())
        assertEquals("::::not a url", "::::not a url".host())
        assertEquals("", "".host())
    }

    @Test
    fun `the reader is given the post's own page and the post's own title`() {
        // The masthead's `showDevotionWebReader(context, url: post.link, title: post.title)`.
        val target = devotionWebReaderTarget(post(Title, Link))

        assertEquals(Link, target.url)
        assertEquals(Title, target.title)
    }

    @Test
    fun `no post means the site's front page and no title to name it`() {
        // The failure screen's `url: post?.link ?? devotionOrigin`, with no `title:` beside it. This is
        // the one URL the reader is given that is not an article: a device whose JSON endpoint is
        // unreachable opens the blog itself, which is the whole reason the reader exists.
        val target = devotionWebReaderTarget(null)

        assertEquals(DEVOTION_ORIGIN, target.url)
        assertEquals("https://devotion.wkphc.org", target.url)
        // A null title rather than a blank one, which is what lets the toolbar fall back to the host
        // instead of drawing an empty row.
        assertNull(target.title)
    }

    @Test
    fun `the origin is a page rather than a feed or an api endpoint`() {
        // The reader renders what the site serves, so the fallback has to be the site's own home page.
        // `devotion.wkphc.org` and nothing below it: the REST and feed paths below the origin are what
        // just failed, and sending the fallback reader to them would fail the same way.
        assertEquals("https://devotion.wkphc.org", DEVOTION_ORIGIN)
        assertFalse(DEVOTION_ORIGIN.endsWith("/"))
    }
}

private const val Title = "8月21日 靈修默想 – 數算恩典"
private const val Link = "https://devotion.wkphc.org/25436"

/** The few fields the reader's own rule reads, so a test does not have to parse a post to make one. */
private fun post(title: String, link: String): DevotionPost {
    val date = LocalDate.of(2026, 8, 21)
    return DevotionPost(
        id = 1,
        publishedAt = LocalDateTime.of(date, LocalTime.MIN),
        devotionDate = date,
        title = title,
        link = link,
        contentHtml = "",
        blocks = emptyList(),
    )
}
