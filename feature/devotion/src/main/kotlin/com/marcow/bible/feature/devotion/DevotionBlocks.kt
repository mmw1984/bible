package com.marcow.bible.feature.devotion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.theme.AppFontWeights
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.core.designsystem.theme.scriptureStyle
import com.marcow.bible.feature.devotion.domain.DevotionBlock
import com.marcow.bible.feature.devotion.domain.DevotionEmbed
import com.marcow.bible.feature.devotion.domain.DevotionHeading
import com.marcow.bible.feature.devotion.domain.DevotionImage
import com.marcow.bible.feature.devotion.domain.DevotionParagraph
import com.marcow.bible.feature.devotion.domain.DevotionQuote
import com.marcow.bible.feature.devotion.domain.DevotionSection
import com.marcow.bible.feature.devotion.domain.DevotionVideo
import java.net.URI

/**
 * The article body: one branch per case of `_buildBlock` in
 * `legacy/flutter/lib/devotion_page.dart:563`.
 *
 * [indent] is Flutter's `indent`, the 46 dp a wide window puts on the left and right of every block
 * and zero on a phone. It is passed rather than derived because the page already resolved it — the
 * same window that widens the masthead is the one that indents the blocks — and because the article
 * column's own 20 dp has to stay *underneath* it, which is only visible if the two are separate.
 */
@Composable
fun DevotionBlocks(
    blocks: List<DevotionBlock>,
    indent: Dp,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        blocks.forEach { block ->
            DevotionBlockRow(block = block, indent = indent, onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun DevotionBlockRow(block: DevotionBlock, indent: Dp, onOpenUrl: (String) -> Unit) {
    val colors = appColors
    val inset = Modifier.padding(horizontal = indent)
    when (block) {
        is DevotionParagraph -> Text(
            text = block.text,
            style = scriptureStyle(
                size = DevotionChrome.PARAGRAPH_SIZE,
                lineHeight = DevotionChrome.PARAGRAPH_SIZE * DevotionChrome.PARAGRAPH_LINE_HEIGHT,
                color = colors.ink,
            ),
            modifier = inset.padding(bottom = DevotionChrome.PARAGRAPH_BELOW),
        )

        is DevotionHeading -> Text(
            text = block.text,
            style = scriptureStyle(
                size = DevotionChrome.HEADING_SIZE,
                lineHeight = DevotionChrome.HEADING_SIZE * DevotionChrome.HEADING_LINE_HEIGHT,
                weight = AppFontWeights.SERIF_SEMIBOLD,
                color = colors.ink,
            ),
            modifier = inset.padding(
                top = DevotionChrome.HEADING_ABOVE,
                bottom = DevotionChrome.HEADING_BELOW,
            ),
        )

        is DevotionQuote -> DevotionQuotePanel(
            text = block.text,
            modifier = inset.padding(
                top = DevotionChrome.QUOTE_ABOVE,
                bottom = DevotionChrome.QUOTE_BELOW,
            ),
        )

        is DevotionImage -> DevotionImageFrame(
            url = block.url,
            modifier = inset.padding(
                top = DevotionChrome.IMAGE_ABOVE,
                bottom = DevotionChrome.IMAGE_BELOW,
            ),
        )

        is DevotionSection -> Column(
            modifier = inset.padding(
                top = DevotionChrome.SECTION_ABOVE,
                bottom = DevotionChrome.SECTION_BELOW,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(DevotionChrome.SECTION_BAR_WIDTH)
                        .height(DevotionChrome.SECTION_BAR_HEIGHT)
                        .clip(RoundedCornerShape(DevotionChrome.SECTION_BAR_RADIUS))
                        .background(colors.ink),
                )
                Spacer(Modifier.width(DevotionChrome.SECTION_TITLE_GAP))
                Text(text = block.title, style = sectionTitleStyle(colors.ink))
            }
            Spacer(Modifier.height(DevotionChrome.SECTION_TITLE_BELOW))
            // The children carry the indent the section has, not one more: Flutter passed the same
            // `indent` back down through `_buildBlock`, so a nested list sits level with its heading.
            block.blocks.forEach { child ->
                DevotionBlockRow(block = child, indent = indent, onOpenUrl = onOpenUrl)
            }
        }

        is DevotionVideo -> DevotionVideoPlayer(
            videoId = block.videoId,
            watchUrl = block.watchUrl,
            thumbnailUrl = block.thumbnailUrl,
            onOpenUrl = onOpenUrl,
            modifier = inset.padding(
                top = DevotionChrome.MEDIA_ABOVE,
                bottom = DevotionChrome.MEDIA_BELOW,
            ),
        )

        is DevotionEmbed -> DevotionEmbedPlayer(
            url = block.url,
            onOpenUrl = onOpenUrl,
            modifier = inset.padding(
                top = DevotionChrome.MEDIA_ABOVE,
                bottom = DevotionChrome.MEDIA_BELOW,
            ),
        )
    }
}

/**
 * A `<blockquote>`: the 詩歌 and 經文 quotes, the only indented thing in an article.
 *
 * Flutter drew this as one `Container` with a decoration *and* a padding, and part of that decoration
 * was a `Border(left: …)` — a bar down the very edge, square at the corners, not a four-sided frame
 * and not a rounded one. So the fill, the bar and the text are three siblings: inside the clipped
 * shape the bar would be tapered away at its ends, and in the flow it would push the text along.
 *
 * Named for the panel rather than for the block: [DevotionQuote] is the block it draws, and a
 * composable cannot share that name.
 */
@Composable
private fun DevotionQuotePanel(text: String, modifier: Modifier = Modifier) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.compact)
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(colors.surfaceRaised.copy(alpha = DevotionChrome.RAISED_FILL_ALPHA)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(DevotionChrome.QUOTE_BAR_WIDTH)
                .fillMaxHeight()
                .background(colors.ink.copy(alpha = DevotionChrome.QUOTE_BAR_ALPHA)),
        )
        Text(
            text = text,
            style = scriptureStyle(
                size = DevotionChrome.QUOTE_SIZE,
                lineHeight = DevotionChrome.QUOTE_SIZE * DevotionChrome.QUOTE_LINE_HEIGHT,
                color = colors.ink,
            ).copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(
                // A `Container` insets its content past its border as well as by its padding.
                start = DevotionChrome.QUOTE_BAR_WIDTH + DevotionChrome.QUOTE_PADDING,
                end = DevotionChrome.QUOTE_PADDING,
                top = DevotionChrome.QUOTE_PADDING_VERTICAL,
                bottom = DevotionChrome.QUOTE_PADDING_VERTICAL,
            ),
        )
    }
}

/**
 * A section's title, the 3 dp ink tab's other half.
 *
 * The one thing in an article Flutter set no line height on, so its line box came from the font
 * rather than from a ratio: [TextUnit.Unspecified] is how that is said to Compose, and
 * [scriptureStyle] insists on a line height before one can be cleared, hence the size passed twice.
 */
private fun sectionTitleStyle(ink: Color) = scriptureStyle(
    size = DevotionChrome.SECTION_TITLE_SIZE,
    lineHeight = DevotionChrome.SECTION_TITLE_SIZE,
    // `FontWeight.w700`, past the two weights `AppFontWeights` names. The bundled font is a variable
    // one with a live `wght` axis, so this reaches 700 the way `scriptureStyle` reaches 600.
    weight = 700,
    color = ink,
).copy(
    lineHeight = TextUnit.Unspecified,
    letterSpacing = DevotionChrome.SECTION_TITLE_SPACING.sp,
)

/**
 * An `<img>`, through `buildDevotionImage`.
 *
 * Flutter asked for a browser's `User-Agent` here. The blog does not need one — its own
 * `/wp-content/uploads` answers `okhttp/4.12.0`, and answers a request with no `User-Agent` at all,
 * with 200 either way — so the image goes out on Coil's client as it is rather than through a loader
 * configured for a header nothing checks.
 *
 * Named for the frame rather than for the block: [DevotionImage] is the block it draws, and a
 * composable cannot share that name.
 */
@Composable
private fun DevotionImageFrame(url: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        imageLoader = devotionImageLoader(),
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Flutter's `ClipRRect(AspectRatio(3 / 2, ColoredBox(…)))`, and the nesting is the whole
        // point: `loadingBuilder`'s return value *replaces* the `Image` in the tree, so the ratio it
        // builds stays the parent of the decoded child rather than being swapped out for it. Applied
        // here, on the frame, every state of the image is 3:2 — spinner, painting or the failure glyph
        // — which is what keeps a gallery of portrait canvases as one uniform strip.
        modifier = modifier
            .clip(RoundedCornerShape(appRadii.surface))
            .aspectRatio(DevotionChrome.IMAGE_ASPECT_RATIO)
            // The `ColoredBox` is inside the ratio rather than only under the placeholder, because in
            // Flutter it wraps the loaded child too. Under an opaque painting it never shows.
            .background(appColors.surfaceRaised.copy(alpha = DevotionChrome.IMAGE_PANEL_ALPHA)),
        loading = { DevotionImagePlaceholder(loading = true) },
        error = { DevotionImagePlaceholder(loading = false) },
    )
}

/**
 * The 3:2 panel `buildDevotionImage` stood in while the bytes arrived, and again when they never did.
 *
 * It fills the frame [DevotionImageFrame] already reserved rather than asking for one of its own: Flutter's
 * two panels were `AspectRatio`s in their own right only because neither had a ratio above them, and
 * they sat inside the loading builder's and the error builder's boxes — which is 3:2 either way.
 */
@Composable
private fun DevotionImagePlaceholder(loading: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            DevotionSpinner(color = appColors.muted)
        } else {
            DevotionGlyphView(
                glyph = DevotionGlyph.IMAGE_OFF,
                size = DevotionChrome.IMAGE_GLYPH_SIZE,
                color = appColors.faint,
            )
        }
    }
}

/**
 * A YouTube embed Flutter could not play in place, as `_ThumbnailFallback` drew it.
 *
 * This is a fallback, not the rendering: [DevotionVideoPlayer] shows a real player, and stands this
 * card in only once its frame reports a main-frame error — which is the same branch Flutter took on the
 * web and whenever building its player failed. The open-in-browser row under the thumbnail is the
 * shared [DevotionOpenInBrowserRow], because Flutter drew that row under both renderings.
 */
@Composable
internal fun DevotionVideoCard(
    watchUrl: String,
    thumbnailUrl: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.devotion_watch_video)
    val picture = Modifier.fillMaxWidth().aspectRatio(DevotionChrome.VIDEO_ASPECT_RATIO)
    Column(modifier = modifier) {
        AppTap(
            onClick = { onOpenUrl(watchUrl) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(appRadii.surface))
                // The picture is the whole target and says nothing, so it is named for a screen
                // reader here rather than left as an unlabelled tap.
                .semantics { contentDescription = label },
        ) {
            Box(modifier = picture, contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    imageLoader = devotionImageLoader(),
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = picture,
                    loading = { DevotionVideoBackdrop() },
                    error = { DevotionVideoBackdrop() },
                )
                // The scrim Flutter stacked under the mark, so the play button reads over any painting.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(DevotionChrome.VIDEO_ASPECT_RATIO)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = DevotionChrome.VIDEO_SCRIM_ALPHA),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(DevotionChrome.VIDEO_PLAY_SIZE)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = DevotionChrome.VIDEO_PLAY_ALPHA))
                        .border(DevotionChrome.VIDEO_PLAY_RING, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    DevotionGlyphView(
                        glyph = DevotionGlyph.PLAY,
                        size = DevotionChrome.VIDEO_PLAY_GLYPH,
                        color = Color.White,
                    )
                }
            }
        }
        // The open-in-browser row sits under the picture rather than as a chip over its corner: there
        // it covered the painting, ate touches and read as a fourth control on a card with three.
        DevotionOpenInBrowserRow(url = watchUrl, onOpenUrl = onOpenUrl)
    }
}

/**
 * The "open in browser" row under a video, from `DevotionYoutubePlayer.build`.
 *
 * One implementation for both renderings of a video block, because Flutter had one: whether the frame
 * played or the thumbnail card was standing in, the row under it was the same control with the same
 * measurements. It is a `ColumnScope` member because both of its homes align it to the end edge of
 * the column it sits in, and both of them are that column.
 */
@Composable
internal fun ColumnScope.DevotionOpenInBrowserRow(url: String, onOpenUrl: (String) -> Unit) {
    AppTap(
        onClick = { onOpenUrl(url) },
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = DevotionChrome.VIDEO_OPEN_ABOVE),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DevotionChrome.VIDEO_OPEN_HORIZONTAL,
                vertical = DevotionChrome.VIDEO_OPEN_VERTICAL,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DevotionGlyphView(
                glyph = DevotionGlyph.EXTERNAL_LINK,
                size = DevotionChrome.VIDEO_OPEN_GLYPH,
                color = appColors.faint,
            )
            Spacer(Modifier.width(DevotionChrome.VIDEO_OPEN_GAP))
            Text(
                text = stringResource(R.string.devotion_open_in_browser),
                style = openRundeStyle(
                    size = DevotionChrome.VIDEO_OPEN_SIZE,
                    weight = FontWeight.Normal,
                    color = appColors.faint,
                ),
            )
        }
    }
}

/**
 * The raised panel Flutter put behind the play mark while `hqdefault.jpg` was in flight, and when it
 * failed outright — `_ThumbnailFallback`'s `errorBuilder` had no glyph in it, only the surface.
 */
@Composable
private fun DevotionVideoBackdrop() {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(DevotionChrome.VIDEO_ASPECT_RATIO)
            .background(appColors.surfaceRaised.copy(alpha = DevotionChrome.RAISED_FILL_ALPHA)),
    )
}

/**
 * An embed whose frame failed, as `_ExternalFallback` drew it.
 *
 * A fallback for [DevotionEmbedPlayer], shown once that frame reports a main-frame error — which is
 * the branch Flutter itself took when its WebView errored, down to the host printed under the label.
 * The button repeats the block's own action rather than opening anything else, so there is one
 * destination per block.
 */
@Composable
internal fun DevotionEmbedCard(url: String, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val radii = appRadii
    val shape = RoundedCornerShape(radii.surface)
    val label = stringResource(R.string.devotion_open_embed)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = DevotionChrome.RAISED_FILL_ALPHA))
            .border(DevotionChrome.EMBED_BORDER, colors.line, shape)
            .padding(
                horizontal = DevotionChrome.EMBED_PADDING,
                vertical = DevotionChrome.EMBED_PADDING_VERTICAL,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DevotionGlyphView(
            glyph = DevotionGlyph.AUDIO_LINES,
            size = DevotionChrome.EMBED_GLYPH,
            color = colors.ink,
        )
        Spacer(Modifier.width(DevotionChrome.EMBED_GLYPH_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = openRundeStyle(
                    size = DevotionChrome.EMBED_LABEL_SIZE,
                    weight = FontWeight.SemiBold,
                    color = colors.ink,
                ),
            )
            Spacer(Modifier.height(DevotionChrome.EMBED_HOST_ABOVE))
            Text(
                text = url.host(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = openRundeStyle(
                    size = DevotionChrome.EMBED_HOST_SIZE,
                    weight = FontWeight.Normal,
                    color = colors.faint,
                ),
            )
        }
        Spacer(Modifier.width(DevotionChrome.EMBED_BUTTON_GAP))
        AppTap(
            onClick = { onOpenUrl(url) },
            modifier = Modifier
                .clip(RoundedCornerShape(radii.compact))
                .background(colors.surface.copy(alpha = EmbedButtonFillAlpha))
                .border(DevotionChrome.EMBED_BORDER, colors.line, RoundedCornerShape(radii.compact))
                .padding(
                    horizontal = DevotionChrome.EMBED_BUTTON_HORIZONTAL,
                    vertical = DevotionChrome.EMBED_BUTTON_VERTICAL,
                ),
        ) {
            DevotionGlyphView(
                glyph = DevotionGlyph.EXTERNAL_LINK,
                size = DevotionChrome.EMBED_BUTTON_GLYPH,
                color = colors.ink,
            )
        }
    }
}

/**
 * A URL's host, or the whole URL when it has none — Flutter's `Uri.tryParse(url)?.host ?? url`.
 *
 * `internal` rather than private because the web reader's title bar wants the same fallback for the
 * same reason, and two copies of this expression in one module is one copy too many: the SoundCloud
 * card's label names the service rather than the `w.soundcloud.com/player/?url=…` query that follows
 * it, and the reader's title bar would otherwise put a whole permalink into a one-line title.
 */
internal fun String.host(): String = runCatching { URI(this).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: this

/**
 * A UI label in the app's own typeface.
 *
 * These are the parts of a block Flutter styled as bare `TextStyle`s with no `fontFamily`, which
 * under its theme is `OpenRunde` — not the family [scriptureStyle] sets, because a control around the
 * article is not part of the article. `OpenRunde` ships 400 and 500, so the label Flutter set at 600
 * is drawn at the nearest face that exists.
 */
private fun openRundeStyle(size: TextUnit, weight: FontWeight, color: Color) = TextStyle(
    fontFamily = AppFonts.OpenRunde,
    fontSize = size,
    fontWeight = weight,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** `colors.surface.withValues(alpha: .9)`, the fill behind the embed card's button. */
private const val EmbedButtonFillAlpha = 0.9f
