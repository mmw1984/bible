package com.marcow.bible.feature.devotion

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import java.net.URI
import android.graphics.Color as AndroidColor

/**
 * The inline SoundCloud player, replacing `DevotionSoundCloudPlayer` in
 * `legacy/flutter/lib/devotion_soundcloud_player.dart`.
 *
 * The Dart build explains at length why this is a `WebView` and not a Dart audio package —
 * `minikin/soundcloud_audio_player` is a demo app, not a library — and embeds the same
 * `w.soundcloud.com/player/?url=…` widget the site already ships. That decision survives intact: the
 * iframe is a web page, so a `WebView` is the only thing that can play it inline.
 *
 * **The host guard** is the substance of the widget, and it is [embedMayNavigateInPlace] so that it
 * can be tested without a device. SoundCloud's own player navigates inside its frame — to a track
 * page, to a share sheet, to a playlist — and every one of those has to stay in the frame or the
 * widget disappears and leaves an audio element nobody can reach. Everything *else* on the web is
 * somebody's article, and letting the frame follow a link would take the reader off a devotion and
 * leave no way back. So the rule is: the SoundCloud and CDN hosts stay, other `http`/`https` URLs go
 * to the browser, and the frame never follows.
 *
 * **The fallback card** is [DevotionEmbedCard], drawn when the frame reports a main-frame error — the
 * same branch Flutter took, down to printing the host under the label.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DevotionEmbedPlayer(url: String, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

    if (failed) {
        // Returning before the `AndroidView` below is what tears the frame down: a frame that has
        // reported a main-frame error has nothing left to show, and leaving it mounted under a card
        // would keep an audio element playing behind a surface the reader can no longer reach.
        DevotionEmbedCard(url = url, onOpenUrl = onOpenUrl, modifier = modifier)
        return
    }

    val shape = RoundedCornerShape(appRadii.surface)
    Box(
        modifier = modifier
            .clip(shape)
            .background(appColors.surfaceRaised.copy(alpha = DevotionChrome.RAISED_FILL_ALPHA)),
    ) {
        // Keyed on the URL for the same reason the video's frame is keyed on its id: `AndroidView`
        // builds its `WebView` once and keeps it, so a reader who moves to the next article's embed
        // would otherwise still be looking at — and hearing — the previous one. `AndroidView` takes no
        // key of its own, so the frame is keyed by the composition around it, which is what also
        // makes `onRelease` run for the widget being left behind.
        key(url) {
            AndroidView(
                factory = { context: Context ->
                    WebView(context).apply {
                        // `setJavaScriptMode(JavaScriptMode.unrestricted)` in the Dart build: the widget
                        // is a page, and it does not run without script.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // The page is SoundCloud's, but a frame is still a frame: local file and content
                        // access have no place in a widget that only ever loads an `https` URL, and
                        // leaving them on would widen what a compromised script could reach.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // `setBackgroundColor(Colors.transparent)`: the frame sits on the card's own fill.
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        // `onNavigationRequest`'s three branches are in [shouldOverrideUrlLoading] below,
                        // one for one with the Dart delegate's.
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                // `if (mounted) setState(() => _isLoading = false)`. SoundCloud's widget
                                // finishes this page well before its own artwork is up, so the spinner can
                                // clear a beat early — the Dart build accepted that, and matching its
                                // timing is the point.
                                loading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // `error.isForMainFrame`: a failed subresource is not a failed player, and
                                // replacing a working widget over one would be worse than the silence it
                                // fixes.
                                if (request?.isForMainFrame == true) failed = true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                // `false` means the frame follows the URL; `true` means it declines.
                                //
                                // 1. SoundCloud's own hosts and CDNs stay — this is the whole point of
                                //    the widget, and the guard that keeps a track tap from replacing the
                                //    player with a track page. See [embedMayNavigateInPlace].
                                if (embedMayNavigateInPlace(target)) return false
                                // 2. Other `http`/`https` go to the browser, and the frame declines.
                                //    This is the branch that matters: it is what stops a reader tapping
                                //    a link inside the widget from replacing the widget they came to
                                //    play with somebody's article. See [embedLinkIsExternal].
                                if (embedLinkIsExternal(target)) {
                                    uriHandler.openUri(target)
                                    return true
                                }
                                // 3. Everything else is followed, which is Dart's
                                //    `return NavigationDecision.navigate`. `about:blank` and `intent:`
                                //    have no browser to hand them to, so escalating would drop the tap
                                //    silently, and following is at least recoverable.
                                return false
                            }
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { webView: WebView -> webView.destroy() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DevotionChrome.MEDIA_HEIGHT),
            )
        }
        if (loading) {
            // The spinner Flutter drew over the frame until its first page finished.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DevotionChrome.MEDIA_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                DevotionSpinner(color = appColors.muted)
            }
        }
        // `_ExternalChip`: a black pill in the top-right corner, above the frame rather than inside it.
        AppTap(
            onClick = { onOpenUrl(url) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(EMBED_CHIP_INSET),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(EMBED_CHIP_RADIUS))
                    .background(Color.Black.copy(alpha = EMBED_CHIP_ALPHA))
                    .padding(
                        horizontal = DevotionChrome.EMBED_CHIP_HORIZONTAL,
                        vertical = DevotionChrome.EMBED_CHIP_VERTICAL,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.devotion_open_in_browser),
                    color = Color.White,
                    fontSize = DevotionChrome.EMBED_CHIP_SIZE,
                )
            }
        }
    }
}

/**
 * Whether a URL the SoundCloud frame asked to navigate to may be followed inside the frame.
 *
 * The Dart delegate's own condition: `uri.host.contains('soundcloud.com') ||
 * uri.host.contains('sndcdn.com')`, with a `Uri.tryParse` that yields `null` for a malformed URL — and
 * a `null` there meant "navigate", because the delegate returned before it could judge.
 *
 * `sndcdn.com` is not optional decoration: it is the host that serves the track audio and the artwork,
 * and the player redirects through it constantly. Treating it as external would bounce the widget out
 * to the browser on its first seek.
 */
internal fun embedMayNavigateInPlace(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull() ?: return true
    return host.contains("soundcloud.com") || host.contains("sndcdn.com")
}

/**
 * Whether a URL the frame may not follow is one the browser should be handed.
 *
 * `uri.scheme == 'http' || uri.scheme == 'https'`, from the Dart delegate's second branch. The third
 * branch — everything else, `return NavigationDecision.navigate` — let the frame walk itself into
 * `about:blank` or an `intent:` URL rather than escalating to a browser that has no handler for it,
 * which is a blank widget the reader cannot recover from.
 */
internal fun embedLinkIsExternal(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()
    return scheme == "http" || scheme == "https"
}

/** `Positioned(right: 6, top: 6)` — the external chip's inset from the frame's corner. */
private val EMBED_CHIP_INSET = 6.dp

/** `BorderRadius.circular(16)` on the chip. */
private val EMBED_CHIP_RADIUS = 16.dp

/** `Material(color: Colors.black54)` — the chip's own fill. */
private const val EMBED_CHIP_ALPHA = 0.54f
