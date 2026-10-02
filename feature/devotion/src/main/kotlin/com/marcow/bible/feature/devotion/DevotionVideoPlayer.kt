package com.marcow.bible.feature.devotion

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import android.graphics.Color as AndroidColor

/**
 * The inline YouTube player, replacing `DevotionYoutubePlayer` in
 * `legacy/flutter/lib/devotion_youtube_player.dart`.
 *
 * The Dart build put the iframe in a `WebView` through `youtube_player_flutter` and layered two
 * things over it: the player's own controls, and YouTube-app-style double-tap seek. Both survive
 * here. Fullscreen does not, and the reason is worth stating, because Flutter's handling of it was a
 * bug workaround rather than a feature.
 *
 * **Seek.** There is no `youtube_player_flutter` here, so the position and the duration come out of
 * the player over the JavaScript bridge rather than off a controller object, and
 * [clampSeekTarget] — the function the Dart build extracted into `youtube_seek_test.dart` and which
 * had no caller until this composable — is what turns the two into a target.
 *
 * **Asking is asynchronous, and that is parity rather than a compromise.** Flutter's own seek awaited
 * `Future.wait([controller.currentTime, controller.duration])` before it could clamp, so the answer
 * arrived a tick after the tap there too. What must not be lost is *when* the position is read: each
 * tap reads it again, at the moment of that tap, which is what makes three taps read 10 → 20 → 30
 * instead of each measuring from the position before the first.
 *
 * **Double tap only.** [detectTapGestures] is given `onDoubleTap` and nothing else, which is the whole
 * point of `_DoubleTapZone`: a single tap has to lose the gesture arena and reach the player's own
 * play control underneath. A recognizer that also claimed single taps would swallow it and the video
 * would take two taps to start.
 *
 * **Fullscreen.** The iframe escalates to fullscreen through the HTML5 fullscreen API, which a
 * `WebView` honours by leaving the app. Flutter's `canPop: !_isFullScreen` — the fix for a black
 * screen left behind by a popped route — has no equivalent, because there is no fullscreen listener
 * to observe and so nothing for `BackHandler` to consult. What is kept is the reason the workaround
 * existed: the player is destroyed when its block leaves the composition, so a reader switching days
 * cannot leave a detached `WebView` holding its audio. That is `dispose()` in the Dart state.
 *
 * **The card is a fallback, not the rendering.** [DevotionVideoCard] stands in only when the frame
 * has failed, which is the same branch Flutter drew on the web and on any device whose `WebView`
 * refused the embed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DevotionVideoPlayer(
    videoId: String,
    watchUrl: String,
    thumbnailUrl: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerHtml = remember(videoId) { youtubePlayerHtml(sanitizedVideoId(videoId)) }
    var frame by remember(videoId) { mutableStateOf<WebView?>(null) }
    var failed by remember(videoId) { mutableStateOf(false) }
    var shownDelta by remember(videoId) { mutableDoubleStateOf(0.0) }
    val ready = rememberFrameReady(webView = frame, isStillMounted = { mounted -> mounted === frame })

    // `Timer(const Duration(milliseconds: 900))` in `_seekBy`, restarted by every tap: the flash holds
    // 900 ms after the *last* one, which is what makes a stack read 10 → 20 → 30 instead of resetting
    // to 10 whenever the next tap lands. Keyed on `shownDelta` for the same reason — the effect is
    // cancelled and re-launched on each change, which is the restart.
    LaunchedEffect(shownDelta) {
        if (shownDelta == 0.0) return@LaunchedEffect
        delay(SeekFlashHoldMillis)
        shownDelta = 0.0
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(appRadii.surface))
                .background(appColors.surfaceRaised.copy(alpha = DevotionChrome.RAISED_FILL_ALPHA)),
        ) {
            // `failed` replaces the frame rather than covering it, the way Flutter returned
            // `_ThumbnailFallback` in place of the player. Composing both would leave a live player
            // running — and possibly playing audio — underneath a card the reader has to tap through.
            if (failed) {
                DevotionVideoCard(
                    watchUrl = watchUrl,
                    thumbnailUrl = thumbnailUrl,
                    onOpenUrl = onOpenUrl,
                )
            } else {
                // `key(videoId)` rather than only `remember`, because `AndroidView` builds its `WebView`
                // once and then keeps it: a reader who switches days, or scrolls to the next
                // article's video, would otherwise be handed the previous video's player under
                // the new page. `AndroidView` takes no key of its own, so the frame is keyed by the
                // composition around it — which is what makes the `AndroidView` on the far side of a
                // changed key leave the tree, and `onRelease` below run.
                key(videoId) {
                    AndroidView(
                        factory = { context: Context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // The page is ours, but the player inside it is YouTube's — so the
                                // WebView is its sandbox, and any error its subframes raise arrives
                                // here too. Letting them through is what lets the guard below read them
                                // as the player's own.
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                // `LOAD_NO_CACHE` on the outer document, which is ours and is rebuilt
                                // per video anyway; the iframe's own cache is YouTube's to manage and
                                // this does not reach it.
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                // The seek gesture is a reader's second tap on a moving video, which
                                // the player must act on rather than demand a fresh gesture for.
                                settings.mediaPlaybackRequiresUserGesture = false
                                // `setBackgroundColor(Colors.transparent)` on the Dart controller: the
                                // frame sits on the card's own fill, so a white flash must not show
                                // between them.
                                setBackgroundColor(AndroidColor.TRANSPARENT)
                                // No `onPageFinished`: the document being up is not the player being
                                // up — the API script still has to arrive and run before
                                // `onYouTubeIframeAPIReady` exists — so readiness is polled by
                                // `rememberFrameReady`. Flutter hid its spinner on `_ytPlayer`'s
                                // `onReady` for the same reason.
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        // `error.isForMainFrame`: an ad or a caption track failing is
                                        // not the player failing, and Flutter guarded the same way.
                                        if (request?.isForMainFrame == true) failed = true
                                    }
                                }
                                loadDataWithBaseURL(
                                    YouTubeBaseUrl,
                                    playerHtml,
                                    "text/html",
                                    "utf-8",
                                    // `historyUrl` is null: the frame loads a page of our own and has no
                                    // history to seed.
                                    null,
                                )
                            }
                        },
                        // `onRelease` is where the `WebView` actually goes. A `DisposableEffect`
                        // cannot cover it: the call above is keyed, and a key change disposes the
                        // `AndroidView` without re-running an effect keyed on the old instance — so the
                        // frame, its audio and its JavaScript would outlive the video the reader had
                        // scrolled away from.
                        onRelease = { webView: WebView ->
                            // Clearing the reference is what makes a tap arriving while the replacement
                            // is still loading a no-op, rather than a call into a destroyed frame.
                            if (frame === webView) frame = null
                            webView.destroy()
                        },
                        // Assigned from `update` rather than `factory`, because `update` runs on every
                        // composition including the first and `factory` only on the first.
                        update = { webView: WebView -> frame = webView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(DevotionChrome.VIDEO_ASPECT_RATIO),
                    )
                }
                // The spinner Flutter drew over the frame until its player was ready.
                if (!ready) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DevotionSpinner(color = appColors.muted)
                    }
                }
                // Both zones ask the player where it is *now*, which is what makes the taps stack:
                // the second tap's clock already includes the first tap's seek.
                fun tap(delta: Double) = seekPlayer(
                    // Read at callback time, not at tap time: this is what tells a late answer
                    // apart from a frame that is still the one on screen.
                    webView = frame,
                    isStillMounted = { mounted -> mounted === frame },
                    delta = delta,
                ) { applied -> shownDelta += applied }
                SeekZone(alignment = Alignment.CenterStart, onTap = { tap(SeekBackward) })
                SeekZone(alignment = Alignment.CenterEnd, onTap = { tap(SeekForward) })
                if (shownDelta != 0.0) {
                    SeekFlash(rewind = shownDelta < 0, seconds = abs(shownDelta).toInt())
                }
            }
        }
        DevotionOpenInBrowserRow(url = watchUrl, onOpenUrl = onOpenUrl)
    }
}

/**
 * One of the two transparent catchers from `_DoubleTapZone`.
 *
 * The vertical padding is Flutter's own `top: 64, bottom: 56` — room above for the player's title bar
 * and below for its control bar — and the width is its `160`. The asymmetry is kept because it is
 * where the Dart build put it, and a symmetric inset would put the flash against the player's own
 * controls.
 */
@Composable
private fun BoxScope.SeekZone(alignment: Alignment, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .align(alignment)
            .width(SeekZoneWidth)
            .fillMaxHeight()
            .padding(top = SeekZoneTop, bottom = SeekZoneBottom)
            // Keyed on [onTap], which reads the frame and the running total by the time it is invoked.
            // Keyed on nothing, the recognizer would keep the lambda from the composition that created
            // it and every seek after the first would be clamped against a stale clock.
            .pointerInput(onTap) { detectTapGestures(onDoubleTap = { onTap() }) },
    )
}

/** The feedback circle from `_SeekFlash`: a translucent disc, the transport glyph, and the seconds. */
@Composable
private fun BoxScope.SeekFlash(rewind: Boolean, seconds: Int) {
    Box(
        modifier = Modifier
            .align(if (rewind) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = SeekFlashInset)
            .size(DevotionChrome.SEEK_FLASH_SIZE)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = DevotionChrome.VIDEO_PLAY_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DevotionGlyphView(
                glyph = if (rewind) DevotionGlyph.REWIND else DevotionGlyph.FORWARD,
                size = DevotionChrome.SEEK_FLASH_GLYPH,
                color = Color.White,
            )
            Spacer(Modifier.height(DevotionChrome.SEEK_FLASH_GAP))
            Text(
                text = seconds.toString(),
                color = Color.White,
                fontSize = DevotionChrome.SEEK_FLASH_TEXT_SIZE,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The seek a double-tap by [delta] performs, and the flash it draws.
 *
 * The clock is read at the moment of this tap, which is what makes the taps stack: the second tap
 * asks the player where the video is *now*, having already moved once.
 *
 * The flash is drawn *after* the seek is issued and its clock has come back, not on the tap, and that
 * order is the Dart build's: `setState` there sat below `await controller.seekTo`, so a tap that threw
 * showed nothing at all. A reader who taps a player that is still loading sees their 10 appear a beat
 * late rather than watching it flash and then not move.
 */
private fun seekPlayer(
    webView: WebView?,
    isStillMounted: (WebView?) -> Boolean,
    delta: Double,
    onDelta: (Double) -> Unit,
) {
    val view = webView ?: return
    view.evaluateJavascript(PlayerClockJs) { answer ->
        // The clock arrives a tick after the tap, by which time the reader may have scrolled the video
        // away — and `onRelease` destroys the frame the instant they do, and `evaluateJavascript` on a
        // destroyed `WebView` throws. `isStillMounted` is read inside the callback rather than captured,
        // so a late answer from a gone frame is dropped instead of seeking its successor.
        if (!isStillMounted(view)) return@evaluateJavascript
        val clock = answer.toPlayerClock()
        val target = clampSeekTarget(clock?.first ?: 0.0, clock?.second ?: 0.0, delta)
        view.evaluateJavascript(SeekToJs(target), null)
        onDelta(delta)
    }
}

/**
 * The two numbers a seek needs, out of the iframe's API, as the JSON array the bridge returns.
 *
 * `null` is the honest answer before the player has loaded: `getDuration()` reports `0` until it
 * knows a length, and [clampSeekTarget] already treats a zero total as "seek to the start" rather than
 * seeking past the end of a video whose duration was never reported.
 */
private fun String?.toPlayerClock(): Pair<Double, Double>? {
    val numbers = Regex(NUMBER).findAll(this.orEmpty()).map { it.value.toDouble() }.toList()
    return if (numbers.size >= 2) numbers[0] to numbers[1] else null
}

/**
 * The document the frame actually loads.
 *
 * Loading the bare embed URL puts YouTube's own page at the top of the frame, and that page exposes no
 * handle on itself: `enablejsapi=1` permits API *calls* into it but does not publish the player
 * object, and there is no element to reach it through. So the frame gets a page of our own that loads
 * `iframe_api` and constructs a `YT.Player`, which does publish one — on `window.player`, where
 * [PlayerClockJs] and [SeekToJs] can reach it. That is the one structural difference from Flutter, and
 * it is what makes the seek gesture work at all rather than silently do nothing.
 *
 * `loadDataWithBaseURL` needs a base even though both URLs are absolute, or the API's own origin
 * checks refuse the script.
 *
 * **The `playerVars` carry three decisions that are not ours.** `enableCaption: true`,
 * `showVideoAnnotations: false` and `strictRelatedVideos: true` were what the Dart build passed to
 * `YoutubePlayerParams` (`legacy/flutter/lib/devotion_youtube_player.dart:67-72`), and that file
 * carries the reason for the second of them in a comment: annotations and endscreens would render an
 * uncontrollable second UI layer on top of the video, so they were switched off "to leave exactly one
 * controllable UI layer". YouTube's iframe API takes the same three decisions under other names, and
 * without them this frame is exactly the thing the Dart build refused to ship — an annotation the
 * reader can tap through the seek zones, an endscreen that takes over the block the video sits in, and
 * captions nothing in the frame can reach. `iv_load_policy: 3` hides the first two; `strict: 1` keeps
 * the related list to the video's own channel, which `rel: 0` had already made moot by removing it
 * outright and which is named here so the Dart params have a line they map to. The third is the
 * caption policy, and the paragraph below is why it is the one a default cannot stand in for.
 *
 * Captions are the third decision that came from those params, and it is the one a default cannot be
 * left to. `cc_load_policy` is 0 when it is absent, and 0 is not "no preference" — it is "do not load
 * the captions" — while this frame loads no native controls either, `controls` deliberately not being
 * among the parameters below because the seek overlay is the whole UI. So nothing inside the frame
 * could switch them on afterwards: without the parameter the captions are unreachable, not merely off
 * by default. The Dart build could leave it out because `controls: 1` rode along in the same map and
 * put a CC button under the wrapper's overlay; `cc_load_policy: 1` states that decision where the
 * button would have been.
 *
 * The caption *language* is the one parameter still left out. `cc_lang_pref: 'en'` went with it, but
 * that was the Dart package's own default and never a line the app wrote, and these posts' captions
 * are not English. Leaving it off lets the track list fall to the viewer's own preference, which is
 * the reading of the same decision that survives an English-only track not existing.
 */
internal fun youtubePlayerHtml(videoId: String): String = """
    <!doctype html>
    <html>
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
    <style>
    html,body{margin:0;padding:0;height:100%;background:transparent;overflow:hidden}
    #player{position:absolute;top:0;left:0;width:100%;height:100%}
    </style>
    </head>
    <body>
    <div id="player"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
    function onYouTubeIframeAPIReady() {
      window.player = new YT.Player('player', {
        videoId: '$videoId',
        playerVars: {playsinline: 1, rel: 0, modestbranding: 1, enablejsapi: 1, cc_load_policy: 1,
                     iv_load_policy: 3, strict: 1}
      });
    }
    </script>
    </body>
    </html>
""".trimIndent()

/**
 * The id as it goes into the `videoId` line above.
 *
 * The value is interpolated into a JavaScript string literal, so anything that could close that
 * literal has to go — an id carrying a quote or a newline would otherwise be able to run script in the
 * frame. YouTube ids are `[A-Za-z0-9_-]`, so stopping at the first character outside that set changes
 * no real id, and a hand-edited fixture that does not match that shape gets no player rather than an
 * injected one.
 *
 * It stops rather than deleting the offending characters, because a video id is one contiguous token:
 * the part after a quote is not the rest of the id, it is a second, different string that deleting
 * would splice onto the first. `abc';alert(1);'` shortens to `abc`, where removing only the
 * punctuation would leave `abcalert1` — a token that is well formed, and belongs to some other video.
 */
internal fun sanitizedVideoId(videoId: String): String = VIDEO_ID_PREFIX.find(videoId)?.value ?: ""

/**
 * Reads the player's own clock, as the JSON array [toPlayerClock] parses.
 *
 * `window.player` is the object [youtubePlayerHtml] constructs. Every call is guarded rather than
 * assumed, so a tap that lands before the API has loaded reads a zero clock instead of throwing — and
 * [clampSeekTarget] turns a zero clock into a seek to the start, which is the same thing Flutter did
 * when its controller had no player yet.
 */
private const val PlayerClockJs =
    "(function(){var p=window.player;" +
        "return JSON.stringify([p&&p.getCurrentTime?p.getCurrentTime():0," +
        "p&&p.getDuration?p.getDuration():0]);})()"

/** Whether [youtubePlayerHtml] has built its player yet, which is what the spinner waits for. */
private const val PlayerReadyJs =
    "(function(){return !!(window.player&&window.player.seekTo);})()"

/**
 * The seek itself, in the page's own words.
 *
 * `allowSeekAhead` is the `true` Flutter passed to `seekTo`, so a forward seek past a buffered boundary
 * proceeds instead of waiting for the data — which on a slow connection is the difference between the
 * gesture appearing to work and appearing to be ignored.
 *
 * [seconds] is spliced in as a `Double.toString` rather than through a format string, because a locale
 * that writes decimals with a comma would otherwise produce a literal JavaScript syntax error — and the
 * seek would fail only for readers in that locale, which is the worst way for it to fail.
 */
private fun SeekToJs(seconds: Double): String = "window.player&&window.player.seekTo($seconds,true)"

/**
 * Whether the frame's player exists yet, which is what the spinner waits on.
 *
 * Flutter learned this from the player's own `onReady`. There is no callback to attach to from here —
 * the page is ours, but the event is raised inside YouTube's API and `WebView` has no listener for it
 * — so the question is put to the page on a short, bounded poll. It stops at the first `true`, and the
 * attempt count is a ceiling rather than a loop, so a frame whose API never loads leaves the spinner up
 * for four seconds and then shows a player that is there but blank, which is what a reader would have
 * seen from the same page anyway.
 */
@Composable
private fun rememberFrameReady(webView: WebView?, isStillMounted: (WebView?) -> Boolean): Boolean {
    var ready by remember(webView) { mutableStateOf(false) }
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        repeat(ReadyPollAttempts) {
            if (!isStillMounted(view)) return@LaunchedEffect
            if (awaitJs(view, PlayerReadyJs).toJsFlag()) {
                ready = true
                return@LaunchedEffect
            }
            delay(ReadyPollIntervalMillis)
        }
    }
    return ready
}

/** [WebView.evaluateJavascript] as a suspension point; its callback is not one. */
private suspend fun awaitJs(webView: WebView, script: String): String? = suspendCancellableCoroutine { continuation ->
    webView.evaluateJavascript(script) { answer -> continuation.resume(answer) }
}

/**
 * Whether an `evaluateJavascript` answer was the boolean `true`.
 *
 * The answer comes back JSON-encoded, so the page's `true` is the four characters `"true"` and a page
 * that threw comes back as `null`. Neither is `true` unless it is.
 */
private fun String?.toJsFlag(): Boolean = this?.trim() == "true"

/** The numbers in the JSON the bridge answers with. */
private const val NUMBER = """\d+(?:\.\d+)?"""

/**
 * The run of characters at the head of an id that YouTube would have put there.
 *
 * A real id is eleven characters of `[A-Za-z0-9_-]`, so this matches all of one and stops at the first
 * character it would not have written. Used to keep [sanitizedVideoId] honest in one place rather than
 * repeating the character class at each use.
 */
private val VIDEO_ID_PREFIX = Regex("[A-Za-z0-9_-]*")

/** `Timer(const Duration(milliseconds: 900))` in `_DevotionYoutubePlayerState._seekBy`. */
private const val SeekFlashHoldMillis = 900L

/**
 * The origin [youtubePlayerHtml] is presented under.
 *
 * `loadDataWithBaseURL` hands the page an origin, and the IFrame API checks itself against it, so it
 * cannot be the `about:blank` that an omitted base would produce.
 */
internal const val YouTubeBaseUrl = "https://www.youtube.com"

/** How long the readiness poll waits between attempts. */
private const val ReadyPollIntervalMillis = 100L

/** The poll's ceiling: past this the player is treated as never coming, and the spinner gives up. */
private const val ReadyPollAttempts = 40

/** `Positioned(width: 160)`. */
private val SeekZoneWidth = 160.dp

/** `Positioned(top: 64, bottom: 56)` — the player's own bars, which the zones leave alone. */
private val SeekZoneTop = 64.dp
private val SeekZoneBottom = 56.dp

/** `Positioned(left: 24)` / `(right: 24)`. */
private val SeekFlashInset = 24.dp

/** `_seekBy(-10)` and `_seekBy(10)`, seconds per double-tap. */
internal const val SeekBackward = -10.0
internal const val SeekForward = 10.0
