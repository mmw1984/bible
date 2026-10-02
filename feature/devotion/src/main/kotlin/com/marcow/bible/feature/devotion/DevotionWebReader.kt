package com.marcow.bible.feature.devotion

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isDark
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.network.devotion.DEVOTION_ORIGIN
import com.marcow.bible.feature.devotion.domain.DevotionPost
import java.net.URI
import android.graphics.Color as AndroidColor

/**
 * What the page asks the reader to open, as one value.
 *
 * These are the two named arguments of `showDevotionWebReader(context, url:, title:)` in
 * `legacy/flutter/lib/devotion_web_reader_io.dart:17`, kept together because they are one decision:
 * which page the fallback reader is pointed at, and what its toolbar calls it.
 *
 * The title is nullable because Flutter's own two call sites disagreed about it — the masthead's button
 * passed `post.title` and the failure screen's button passed nothing, which left the toolbar naming the
 * host there. One type can carry either, so a host that pushes its own destination can put both in its
 * arguments without having to know which control was pressed.
 */
data class DevotionWebReaderTarget(
    /** The page to load: the post's permalink, or [DEVOTION_ORIGIN] when there is no post. */
    val url: String,
    /** What the toolbar's title says, falling back to the host when this is null. */
    val title: String?,
)

/**
 * The page's one rule for what the reader opens: `post?.link ?? DEVOTION_ORIGIN`.
 *
 * Flutter wrote that fallback twice — `showDevotionWebReader(context, url: post?.link ?? devotionOrigin)`
 * at the failure screen's button — and passed `url: post.link` alone at the masthead's, where the button
 * is disabled without a post so the two could not disagree. It is one rule here because there is one
 * rule there, and the title rides along with the post it came from.
 *
 * Naming the origin rather than leaving it implicit matters: it is the one URL the reader is given that
 * is not an article, and it is what a device with no post to show opens.
 */
internal fun devotionWebReaderTarget(post: DevotionPost?): DevotionWebReaderTarget = DevotionWebReaderTarget(
    url = post?.link ?: DEVOTION_ORIGIN,
    title = post?.title,
)

/**
 * The fallback reader, replacing `DevotionWebReaderPage` in
 * `legacy/flutter/lib/devotion_web_reader_io.dart`.
 *
 * This is the way out when everything else has failed. It renders the article's *own* page in an
 * embedded browser, so it bypasses the REST API, the RSS feed and this feature's parser altogether —
 * which is the point: a device that cannot reach a JSON endpoint can very often still reach the site,
 * and a reader whose day has gone dark would otherwise have nothing at all. The two buttons on the
 * failure screen are both ways back out to a browser the system owns.
 *
 * Flutter pushed this as a route from `showDevotionWebReader`. Here it is a composable and the showing is
 * the host's, because the host is what owns navigation: `DevotionRoute` draws it over the page when no
 * `onOpenWebReader` is given, which is what the Dart page got for free from a root navigator, and hands
 * a host that would rather push a destination a [DevotionWebReaderTarget] to put in its arguments. What
 * this owns is the reader itself — the frame, its toolbar, its progress line and the screen it falls
 * back to.
 *
 * **The frame is the site's own page, so it is opened as narrowly as it can be.** `allowFileAccess` and
 * `allowContentAccess` stay off even here, where the temptation to leave them on is strongest because
 * this really is a browser: the pages being loaded are the blog's and its media, a reader has no reason
 * to reach the device's filesystem from one, and leaving it on would widen what a compromised script on
 * the page could get at. Nothing is lost by it — the blog serves no `file://` content.
 *
 * The frame is released with `destroy()` in `onRelease`, for the reason the video player's is: an
 * `AndroidView` whose key changes is disposed without any effect keyed on the old instance being able
 * to clean up after it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevotionWebReader(
    url: String,
    title: String?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    // `Theme.of(context).brightness == Brightness.dark`, which is the app's own setting rather than
    // the system's: a reader in dark mode with the system in light mode still gets a dark page. The
    // same value is pushed imperatively below, because a WebView's background is not the one painted
    // behind it.
    val pageBackground = if (colors.canvas.isDark()) Color.Black else Color.White
    var frame by remember(url) { mutableStateOf<WebView?>(null) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var failed by remember(url) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // `didChangeDependencies` / `_appliedBrightness`: the colour is imperative, so it is pushed on the
    // change rather than on every recomposition. Keyed on the colour itself, which is what makes a
    // theme switch land while the reader is open.
    LaunchedEffect(frame, pageBackground) {
        frame?.setBackgroundColor(pageBackground.toArgb())
    }

    // `_retry`, shared by the toolbar's reload and the failure screen's Retry. The progress reset is
    // the other half of it: a retry that loads a fresh page has to show the line again, and a reload
    // reports progress from the top rather than from wherever the failed attempt stopped.
    val retry: () -> Unit = {
        failed = false
        progress = 0f
        frame?.reload()
    }

    // Flutter's `SafeArea(bottom: false)`, and the same reading of the status bar as the page takes:
    // the toolbar moves down under it. The bottom is left out because the reader has no bottom bar of
    // its own and the frame should run to the edge, which is what a browser's content view does.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        WebReaderToolbar(
            title = title ?: url.host(),
            onBack = onBack,
            onReload = retry,
            onOpenExternally = { onOpenUrl(url) },
        )
        // `if (!_failed && _progress < 1)`. The bar is left out rather than drawn at zero once the page
        // is up, so the reader's content is not laid out against a line that is not there.
        if (!failed && progress < 1f) {
            LinearProgressIndicator(
                // Flutter's `value: _progress <= 0 ? null : _progress` is Material's *indeterminate*
                // bar — one that animates because the total is unknown — and `progress` cannot be null
                // in Compose, so the fraction is what is drawn.
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(DevotionChrome.WEB_PROGRESS_THICKNESS),
                color = colors.ink,
                trackColor = colors.line.copy(alpha = DevotionChrome.WEB_PROGRESS_TRACK_ALPHA),
                // Material 3 leaves a gap around the indicator and draws a stop dot at the end of the
                // track; Flutter's was a plain 2 dp line with neither, so both are removed rather than
                // left as the one place this screen departs from the Dart build.
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // The `ColoredBox(dark ? black : white)` Flutter wrapped the frame in: what shows while the
            // page is still painting, which is nearly always, and is most of what a reader sees for
            // the first second or two.
            Box(modifier = Modifier.fillMaxSize().background(pageBackground)) {
                AndroidView(
                    key = url,
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            webViewClient = object : WebViewClient() {
                                // `onProgress`: `value / 100` in Dart, so the bar is a fraction here.
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / PERCENT
                                }

                                // `onPageFinished` pinned the bar to full rather than trusting the
                                // last `onProgress`, because a page whose final asset is still
                                // rendering reports 100 and then draws for another second.
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    progress = 1f
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // `error.isForMainFrame`: a failed image or a tracker that
                                    // timed out is not a failed page, and replacing a readable
                                    // article over one would be worse than the silence it fixes.
                                    if (request?.isForMainFrame == true) failed = true
                                }

                                // `onNavigationRequest`, which is the one rule of this delegate
                                // worth naming: the reader stays inside itself for the open web
                                // and hands everything else to the operating system.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url?.toString() ?: return false
                                    if (webReaderMayNavigateInPlace(target)) return false
                                    if (webReaderLinkIsExternal(target)) uriHandler.openUri(target)
                                    // Nothing else is followed. Dart launched whatever it could
                                    // not parse and let the frame decline; see
                                    // [webReaderLinkIsExternal] for why that is narrowed here.
                                    return true
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        if (frame === webView) frame = null
                        webView.destroy()
                    },
                    update = { webView -> frame = webView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Drawn *over* the frame rather than swapped for it, which is the one place this departs
            // from Flutter's `_failed ? _ErrorView : ColoredBox(WebViewWidget)` and is deliberate: the
            // Dart frame kept its `WebViewController` in `State`, so `_retry` reloaded a live frame
            // after the widget tree had already dropped it. Unmounting the frame here would dispose it
            // — `destroy()` runs in `onRelease` — and leave Retry reloading a destroyed WebView.
            // Keeping it mounted is also what Flutter actually got for free: a frame that has failed
            // is idle, and an idle frame costs nothing to keep.
            if (failed) {
                WebReaderFailure(onRetry = retry, onOpenExternally = { onOpenUrl(url) })
            }
        }
    }
}

/**
 * The toolbar: back, the title, reload and open-in-browser.
 *
 * One row of four, as Flutter had it, and the same 40 dp control on a 60%-alpha `surfaceRaised` with
 * a `line` border that the masthead's refresh button is. Back is the host's [onBack] rather than a
 * `BackHandler` of this screen's own, because whether this is a navigation destination or an overlay is
 * the host's arrangement and the two disagree about what back means.
 */
@Composable
private fun WebReaderToolbar(
    title: String,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenExternally: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val reloadLabel = stringResource(R.string.devotion_refresh)
    val openLabel = stringResource(R.string.devotion_open_in_browser)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = DevotionChrome.WEB_TOOLBAR_HORIZONTAL,
                vertical = DevotionChrome.WEB_TOOLBAR_VERTICAL,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphButton(
            glyph = AppGlyph.BACK,
            label = stringResource(R.string.back),
            onClick = onBack,
            fill = colors.surfaceRaised.copy(alpha = DevotionChrome.CONTROL_FILL_ALPHA),
            border = colors.line,
        )
        Spacer(Modifier.width(DevotionChrome.WEB_TITLE_GAP))
        Text(
            text = title,
            color = colors.ink,
            fontSize = DevotionChrome.WEB_TITLE_SIZE,
            fontWeight = FontWeight.W600,
            // Flutter's `maxLines: 1` with an ellipsis: the title is a whole article's headline and the
            // two controls beside it are not going to grow to make room for it. The `weight` is
            // Flutter's `Expanded`, which keeps those controls at the far end of the row however long
            // the headline is.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(DevotionChrome.WEB_TITLE_GAP))
        WebReaderControl(label = reloadLabel, onClick = onReload) {
            // `LucideIcons.rotateCw` rather than the masthead's `refreshCw`: this is a plain reload,
            // and Lucide draws the two differently. See `DevotionGlyph`.
            DevotionGlyphView(
                glyph = DevotionGlyph.ROTATE_CW,
                size = DevotionChrome.CONTROL_GLYPH_SIZE,
                color = colors.ink,
            )
        }
        Spacer(Modifier.width(DevotionChrome.WEB_CONTROL_GAP))
        WebReaderControl(label = openLabel, onClick = onOpenExternally) {
            DevotionGlyphView(
                glyph = DevotionGlyph.EXTERNAL_LINK,
                size = DevotionChrome.CONTROL_GLYPH_SIZE,
                color = colors.ink,
            )
        }
    }
}

/**
 * One of the toolbar's two Lucide controls, on the same surface the masthead's refresh sits on.
 *
 * A separate composable because [AppGlyphButton] draws out of the shared `AppGlyph` language and these
 * two are Lucide — the reader's reload and its external link — so the surface and the tap have to be
 * built here around a [DevotionGlyphView] instead. [label] is Flutter's `tooltip`, which the
 * `IconButton` used to announce the button to a screen reader.
 */
@Composable
private fun WebReaderControl(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    AppControlSurface(
        color = appColors.surfaceRaised.copy(alpha = DevotionChrome.CONTROL_FILL_ALPHA),
        borderColor = appColors.line,
    ) {
        AppTap(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
            Box(modifier = Modifier.size(DevotionChrome.CONTROL_SIZE), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

/**
 * The screen a failed page falls back to, from `_ErrorView`.
 *
 * The page's own failure screen without the exception text underneath: the glyph, one localised line
 * and the row of two buttons, through the same [FailureButton] that screen uses. The detail is left
 * out because there is none to leave out — a `WebView` failure is not a string anybody wrote, so what
 * Flutter could have printed there was the platform's error code, which says less than the line above
 * it. The second button is also not the same one: the page's failure screen offers the reader, where
 * this one is already *in* the reader, so what is left to offer is a browser.
 *
 * It paints the canvas colour over the frame underneath, which Flutter got from the `Scaffold` the
 * error view replaced inside; here it is this box's own background, because the frame is still there.
 */
@Composable
private fun WebReaderFailure(onRetry: () -> Unit, onOpenExternally: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    Box(
        modifier = modifier.fillMaxSize().background(colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppGlyphView(glyph = AppGlyph.CLOUD_OFF, size = DevotionChrome.FAILURE_GLYPH_SIZE, color = colors.muted)
            Spacer(Modifier.height(DevotionChrome.FAILURE_GLYPH_GAP))
            Text(
                text = stringResource(R.string.devotion_web_load_failed),
                color = colors.muted,
                fontSize = DevotionChrome.FAILURE_SIZE,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DevotionChrome.FAILURE_BUTTONS_ABOVE))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FailureButton(label = stringResource(R.string.devotion_retry), onClick = onRetry, emphasized = true)
                Spacer(Modifier.width(DevotionChrome.FAILURE_BUTTON_GAP))
                FailureButton(label = stringResource(R.string.devotion_open_in_browser), onClick = onOpenExternally)
            }
        }
    }
}

/**
 * Whether a URL the reader was asked to navigate to may be followed inside the reader.
 *
 * The Dart delegate's own condition — `scheme == 'http' || scheme == 'https'` over a lower-cased
 * scheme. A blog page, an image, a link to another article: all of those belong in the frame, because
 * the reader *is* a browser for as long as it is open, and bouncing an internal link out to the system
 * browser would drop the reader out of the reader.
 *
 * The scheme is lower-cased before it is compared, as Dart's was. `URI` preserves the case a URL was
 * written in and `HTTP://` is a scheme any browser accepts, so a case-sensitive test here would send a
 * page out to the system browser over nothing but its capitalisation.
 */
internal fun webReaderMayNavigateInPlace(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
    return scheme == "http" || scheme == "https"
}

/**
 * Whether a URL the reader may not follow is one the operating system should be handed.
 *
 * `mailto:`, `tel:`, `intent:` and `market:` — the things a blog page can link to that no frame can
 * follow. A `null` scheme, which is what a string with no scheme at all parses to, is *not* external:
 * there is no handler to hand it to and no frame that will follow it either, so it is declined and the
 * reader stays where it is.
 *
 * This is narrower than the SoundCloud frame's equivalent on purpose. That frame's third branch let an
 * unfollowable URL through to the widget, on the reasoning that following is at least recoverable. Here
 * it is not: this frame's job is to *be* the browser, so a frame that walks itself into `about:blank`
 * takes the reader's whole page with it and leaves nothing to come back to.
 */
internal fun webReaderLinkIsExternal(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase() ?: return false
    return scheme.isNotEmpty() && scheme != "http" && scheme != "https"
}

/** `value / 100`, from `onProgress`. */
private const val PERCENT = 100f
