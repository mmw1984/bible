package com.marcow.bible.feature.devotion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppButton
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphButton
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFontWeights
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.scriptureStyle
import com.marcow.bible.core.model.AppLocale
import com.marcow.bible.feature.devotion.domain.DevotionPost
import com.marcow.bible.feature.devotion.domain.formatDevotionDate
import com.marcow.bible.feature.devotion.domain.formatDevotionDateShort
import java.time.LocalDate

/**
 * The devotion page, replacing `_DevotionPageState.build` at `legacy/flutter/lib/devotion_page.dart:188`.
 *
 * Flutter's `CustomScrollView` had a masthead sliver and then one of three bodies that never coexisted
 * — the article, a spinner, or a failure screen — so this is one [LazyColumn] with a header item and
 * one of three bodies. [LazyItemScope.fillParentMaxHeight] is what puts the spinner and the failure
 * screen in the middle of the space the article would have taken, which is what Flutter's
 * `SliverFillRemaining(hasScrollBody: false)` was for.
 *
 * The whole list sits inside a [PullToRefreshBox], where Flutter had a `RefreshIndicator` wrapped
 * around it at `legacy/flutter/lib/devotion_page.dart:209`.
 *
 * Every input is a callback: the screen holds no view model and no clock. `DevotionRoute` is what
 * turns a tap into a fetch, a post into the clipboard and a link into a browser.
 *
 * [bottomClearance] is the host's navigation bar, which the article leaves room for without knowing
 * what draws it — the same arrangement the reader has, because features do not depend on each other
 * (`NATIVE_PLAN.md` §2.2).
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevotionScreen(
    state: DevotionUiState,
    bottomClearance: Dp,
    onRefresh: () -> Unit,
    onSelectDate: (Int) -> Unit,
    onCopyArticle: () -> Unit,
    onOpenWebReader: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val locale = state.devotionLocale()
    val post = state.post
    val layout = devotionLayout(
        screenWidth = LocalConfiguration.current.screenWidthDp.dp,
        topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottomClearance = bottomClearance,
    )

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                // `color: colors.ink, backgroundColor: colors.surface` on the Flutter indicator. The
                // arc that fills as the page is pulled and the spinner that replaces it once the
                // gesture crosses the threshold are the same two states Flutter drew itself, so only
                // the palette and the offset are named here.
                Indicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = colors.surface,
                    color = colors.ink,
                )
            },
        ) {
            // Flutter's `SelectionArea`, which made every article text selectable: long-press for the
            // system copy menu. Devotion content has no long-press gestures of its own, so nothing on the
            // page competes with the selection.
            SelectionContainer {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = MASTHEAD_KEY) {
                        DevotionMasthead(
                            state = state,
                            layout = layout,
                            locale = locale,
                            onRefresh = onRefresh,
                            onSelectDate = onSelectDate,
                            onCopyArticle = onCopyArticle,
                            onOpenWebReader = onOpenWebReader,
                        )
                    }
                    when {
                        state.loading -> item(key = BODY_KEY) {
                            CentredBody {
                                DevotionSpinner(color = colors.muted)
                                Spacer(Modifier.height(DevotionChrome.LOADING_GAP))
                                Text(
                                    text = stringResource(devotionScreenLabel(DevotionScreenLabel.LOADING)),
                                    color = colors.muted,
                                    fontSize = DevotionChrome.LOADING_SIZE,
                                )
                            }
                        }

                        state.failed || post == null -> item(key = BODY_KEY) {
                            CentredBody {
                                DevotionFailure(
                                    detail = state.failureDetail,
                                    onRetry = onRefresh,
                                    onWebReader = onOpenWebReader,
                                )
                            }
                        }

                        else -> articleItems(post, layout, locale, onOpenUrl)
                    }
                }
            }
        }
    }
}

/**
 * The article: the day's date, the post's title, and one item per block.
 *
 * Flutter's `SliverList.builder` counted `post.blocks.length + 2` and gave its first two indices
 * their own branches, so a `LazyColumn` item here is the same unit one was. The blocks are *not* keyed
 * because Flutter's were not — its one hand-written key was the video player's, and this build draws a
 * card in that block's place rather than a player that would need disposing.
 */
private fun LazyListScope.articleItems(
    post: DevotionPost,
    layout: DevotionLayout,
    locale: AppLocale,
    onOpenUrl: (String) -> Unit,
) {
    val column = Modifier.padding(horizontal = layout.articleHorizontal)
    item(key = POST_DAY_KEY) {
        Text(
            text = formatDevotionDate(post.devotionDate, locale),
            color = appColors.faint,
            fontSize = DevotionChrome.POST_DATE_SIZE,
            // The article's own `top: 4`, which Flutter put on the sliver and therefore above the
            // date rather than on it.
            modifier = column.padding(top = layout.articleTop, bottom = DevotionChrome.POST_DATE_BELOW),
        )
    }
    item(key = POST_TITLE_KEY) {
        Text(
            text = post.title,
            style = scriptureStyle(
                size = DevotionChrome.POST_TITLE_SIZE,
                lineHeight = DevotionChrome.POST_TITLE_SIZE * DevotionChrome.POST_TITLE_LINE_HEIGHT,
                weight = AppFontWeights.SERIF_SEMIBOLD,
                color = appColors.ink,
            ),
            modifier = column.padding(bottom = DevotionChrome.POST_TITLE_BELOW),
        )
    }
    itemsIndexed(count = post.blocks.size) { index ->
        // `SliverPadding`'s bottom went under the last item only, and its left and right went around
        // all three kinds rather than onto the list.
        val last = index == post.blocks.lastIndex
        DevotionBlocks(
            blocks = listOf(post.blocks[index]),
            indent = layout.blockIndent,
            onOpenUrl = onOpenUrl,
            modifier = column.then(if (last) Modifier.padding(bottom = layout.articleBottom) else Modifier),
        )
    }
}

/**
 * What fills the page while there is no article: Flutter's `Center` around a `MainAxisSize.min`
 * column, in the space the article would have taken.
 *
 * [fillParentMaxHeight] is the sliver Flutter wrote as `SliverFillRemaining(hasScrollBody: false)` — a
 * box the height of the *rest* of the viewport, so the spinner sits in the middle of the screen rather
 * than at the top of a column as tall as its own contents.
 */
@Composable
private fun LazyItemScope.CentredBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillParentMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/**
 * The oversized 靈修默想 heading, the controls, the rule under them and the date chips.
 *
 * One column, because Flutter built it as one `SliverToBoxAdapter` holding a `Column` — which is why
 * the chips are not an item of their own: they scroll away with the masthead rather than staying put
 * above the article.
 */
@Suppress("LongParameterList")
@Composable
private fun DevotionMasthead(
    state: DevotionUiState,
    layout: DevotionLayout,
    locale: AppLocale,
    onRefresh: () -> Unit,
    onSelectDate: (Int) -> Unit,
    onCopyArticle: () -> Unit,
    onOpenWebReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = layout.horizontal, top = layout.top, end = layout.horizontal),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(layout.mastheadHeight),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = stringResource(devotionScreenLabel(DevotionScreenLabel.TITLE)),
                color = appColors.ink,
                fontFamily = devotionTitleFamily(state.usesEnglishUi),
                fontSize = layout.titleSize,
                lineHeight = layout.titleSize * DevotionChrome.TITLE_LINE_HEIGHT,
                // Flutter's `FontWeight.w500`, which is the weight this variable face was drawn at.
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                // Flutter's `TextOverflow.fade`, so a title wider than the window softens.
                overflow = TextOverflow.Fade,
            )
        }
        Spacer(Modifier.height(DevotionChrome.BELOW_TITLE))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RefreshControl(loading = state.loading, onRefresh = onRefresh)
            Spacer(Modifier.width(DevotionChrome.CONTROL_GAP))
            AppGlyphButton(
                glyph = AppGlyph.COPY,
                label = stringResource(devotionScreenLabel(DevotionScreenLabel.COPY_ARTICLE)),
                onClick = onCopyArticle.takeIf { state.post != null },
                size = DevotionChrome.CONTROL_SIZE,
            )
            Spacer(Modifier.width(DevotionChrome.CONTROL_GAP))
            AppGlyphButton(
                glyph = AppGlyph.BOOK,
                label = stringResource(devotionScreenLabel(DevotionScreenLabel.WEB_READER)),
                onClick = onOpenWebReader.takeIf { state.post != null },
                size = DevotionChrome.CONTROL_SIZE,
            )
            // The Flutter `Spacer()`, which is what keeps the date in the corner on a wide window.
            Spacer(Modifier.weight(1f))
            // `if (_lastFetchTime != null)`: a feed that has only been read out of the cache has never
            // been fetched, so it shows nothing there.
            if (state.loadedOnce) {
                Text(
                    text = formatDevotionDate(LocalDate.now(), locale),
                    color = appColors.faint,
                    fontSize = DevotionChrome.TODAY_SIZE,
                )
            }
        }
        Spacer(Modifier.height(DevotionChrome.BELOW_CONTROLS))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevotionChrome.RULE_THICKNESS)
                .background(appColors.line),
        )
        Spacer(Modifier.height(DevotionChrome.BELOW_RULE))
        if (state.hasDateChips) {
            DevotionDateChips(
                state = state,
                locale = locale,
                onSelectDate = onSelectDate,
                modifier = Modifier.height(DevotionChrome.CHIP_HEIGHT),
            )
            Spacer(Modifier.height(DevotionChrome.BELOW_CHIPS))
        }
    }
}

/**
 * The refresh button, from `_buildRefreshButton`.
 *
 * Icon-only and the same 40 dp as the two `AppGlyphButton`s beside it, and it is the one control that
 * shows a spinner *in place of* its glyph while loading, which is what Flutter did: a reader who
 * pulled to refresh would otherwise get the masthead's spinner and the body's for one and the same
 * fetch.
 */
@Composable
private fun RefreshControl(loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(devotionScreenLabel(DevotionScreenLabel.REFRESH))
    AppControlSurface(
        color = appColors.surfaceRaised.copy(alpha = DevotionChrome.CONTROL_FILL_ALPHA),
        borderColor = appColors.line,
        modifier = modifier,
    ) {
        AppTap(
            onClick = onRefresh.takeIf { !loading },
            enabled = !loading,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Box(modifier = Modifier.size(DevotionChrome.CONTROL_SIZE), contentAlignment = Alignment.Center) {
                if (loading) {
                    DevotionSpinner(color = appColors.ink, size = REFRESH_SPINNER_SIZE)
                } else {
                    DevotionGlyphView(
                        glyph = DevotionGlyph.REFRESH_CW,
                        size = DevotionChrome.CONTROL_GLYPH_SIZE,
                        color = appColors.ink,
                    )
                }
            }
        }
    }
}

/**
 * The date chips: the list of days, in Flutter's `ListView.separated` with a 7 dp separator.
 *
 * A [LazyRow] inside the page's own scroll, and only with more than one day to choose between: a
 * reader who has one devotion has nothing to pick, which is the `if (posts.length > 1)` Flutter
 * guarded both the row and the gap under it with.
 */
@Composable
private fun DevotionDateChips(
    state: DevotionUiState,
    locale: AppLocale,
    onSelectDate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(DevotionChrome.CHIP_GAP)) {
        itemsIndexed(count = state.posts.size, key = { index -> state.posts[index].id }) { index ->
            DateChip(
                post = state.posts[index],
                active = index == state.selected,
                locale = locale,
                onClick = { onSelectDate(index) },
            )
        }
    }
}

/**
 * One day's chip, from `_buildDateChip`.
 *
 * The chip is 40 dp tall whatever it says, so the short date is centred in a box of that height
 * instead of being padded to it: a three-character day and a nine-character one have to sit on the
 * same line.
 */
@Composable
private fun DateChip(
    post: DevotionPost,
    active: Boolean,
    locale: AppLocale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    AppControlSurface(
        color = if (active) {
            colors.surfaceRaised
        } else {
            colors.surfaceRaised.copy(alpha = DevotionChrome.CONTROL_FILL_ALPHA)
        },
        borderColor = if (active) colors.ink.copy(alpha = ACTIVE_CHIP_BORDER_ALPHA) else colors.line,
        selected = active,
        modifier = modifier.height(DevotionChrome.CHIP_HEIGHT),
    ) {
        AppTap(
            onClick = onClick,
            selected = active,
            modifier = Modifier
                // Flutter's `AppTap(label: formatDevotionDate(...))` named a chip by its long date, so
                // a screen reader reads 2026年8月21日 rather than the 8月21日 on its face.
                .semantics { contentDescription = formatDevotionDate(post.devotionDate, locale) }
                .padding(horizontal = DevotionChrome.CHIP_HORIZONTAL_PADDING),
        ) {
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = formatDevotionDateShort(post.devotionDate, locale),
                    color = if (active) colors.ink else colors.muted,
                    fontSize = DevotionChrome.CHIP_SIZE,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The failure screen: the glyph, the message, the cause under it and the two buttons.
 *
 * Flutter drew this for `error != null || post == null`, and both halves land here. The cause is the
 * unlocalised exception text, kept because it is what makes an on-device network problem diagnosable
 * from a screenshot; the message above it is the localised one.
 */
@Composable
private fun DevotionFailure(
    detail: String?,
    onRetry: () -> Unit,
    onWebReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppGlyphView(glyph = AppGlyph.CLOUD_OFF, size = DevotionChrome.FAILURE_GLYPH_SIZE, color = colors.muted)
        Spacer(Modifier.height(DevotionChrome.FAILURE_GLYPH_GAP))
        Text(
            text = stringResource(devotionScreenLabel(DevotionScreenLabel.LOAD_FAILED)),
            color = colors.muted,
            fontSize = DevotionChrome.FAILURE_SIZE,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(DevotionChrome.FAILURE_DETAIL_ABOVE))
            Text(
                text = detail,
                color = colors.faint,
                fontSize = DevotionChrome.FAILURE_DETAIL_SIZE,
                lineHeight = DevotionChrome.FAILURE_DETAIL_SIZE * DevotionChrome.FAILURE_DETAIL_LINE_HEIGHT,
                // Flutter's `maxLines: 5` with an ellipsis, so a long socket error is truncated rather
                // than allowed to push the buttons off the page.
                maxLines = DevotionChrome.FAILURE_DETAIL_LINES,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(DevotionChrome.FAILURE_BUTTONS_ABOVE))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FailureButton(
                label = stringResource(devotionScreenLabel(DevotionScreenLabel.RETRY)),
                onClick = onRetry,
                emphasized = true,
            )
            Spacer(Modifier.width(DevotionChrome.FAILURE_BUTTON_GAP))
            // There is no post by definition here, so Flutter's `post?.link ?? devotionOrigin` is the
            // site's front page — whatever the host opens this with is its business.
            FailureButton(
                label = stringResource(devotionScreenLabel(DevotionScreenLabel.WEB_READER)),
                onClick = onWebReader,
            )
        }
    }
}

/**
 * Flutter's two `AppButton`s: the retry is emphasised and its label set at 600, the web reader is not.
 *
 * `internal` because the reader's own failure screen is the same row of two buttons and draws them
 * through here rather than repeating the weight distinction a second time.
 */
@Composable
internal fun FailureButton(label: String, onClick: () -> Unit, emphasized: Boolean = false) {
    AppButton(onClick = onClick, emphasized = emphasized) {
        Text(
            text = label,
            color = appColors.ink,
            fontSize = DevotionChrome.FAILURE_BUTTON_SIZE,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(
                horizontal = DevotionChrome.FAILURE_BUTTON_HORIZONTAL,
                vertical = DevotionChrome.FAILURE_BUTTON_VERTICAL,
            ),
        )
    }
}

/**
 * The family the title is set in, replacing `fontFamily: 'Exposure', fontFamilyFallback: ['NotoSerifTC']`.
 *
 * The choice the reader makes for its chapter heading, for the same reason: Compose has no fallback
 * list on a `TextStyle`, so the language decides up front. 靈修默想 has no glyphs in `Exposure` and
 * reached the bundled serif in Flutter; "Devotions" is Latin and stayed in `Exposure`.
 */
private fun devotionTitleFamily(usesEnglishUi: Boolean): FontFamily =
    if (usesEnglishUi) AppFonts.Exposure else AppFonts.NotoSerifTC

/**
 * The seven sentences the page says for itself, in the order it says them.
 *
 * Not the sentences it *displays* — the article's own day, title and blocks are the blog's words and
 * arrive in [DevotionPost], and the exception text under the failure message is deliberately not
 * localised. What is here is the chrome around them: the masthead's heading and its three controls,
 * then the words of the two bodies the page can be instead of an article.
 *
 * They are gathered into a table so that `DevotionScreenTest` can read them. A string resolved inside a
 * composable cannot be asserted without a device, and the same is true of the reason these are worth
 * a table: a page that said 網頁版 beside 複製全文 while the whole of the English UI was in English would
 * compile, read correctly in a diff, and be wrong on a device only.
 */
enum class DevotionScreenLabel {
    /** The oversized heading, `context.l10n.tabDevotion` at `legacy/flutter/lib/devotion_page.dart:246`. */
    TITLE,

    /** The masthead's refresh control, `context.l10n.devotionRefresh` at `:509`. */
    REFRESH,

    /** The control that copies the article, `context.l10n.devotionCopyArticle` at `:276`. */
    COPY_ARTICLE,

    /**
     * The control that opens the article's own page in a frame, `context.l10n.devotionOpenWebReader`
     * at `:284`.
     *
     * It is one entry for the two places the page says it, because both say the same thing: the
     * masthead's third button and the failure screen's second button open the same reader. Flutter
     * reached for `devotionOpenWebReader` in both, at `:284` and `:402`, and the failure screen is
     * reached precisely when there is no article to open — so the two buttons a reader can reach at
     * the worst moment are the ones that must agree about what they are for.
     */
    WEB_READER,

    /** The word under the spinner, `context.l10n.devotionLoading` at `:342`. */
    LOADING,

    /** The failure message, `context.l10n.devotionLoadFailed` at `:359`. */
    LOAD_FAILED,

    /** The failure screen's first button, `context.l10n.devotionRetry` at `:382`. */
    RETRY,
}

/**
 * The string each of the page's own sentences is written in.
 *
 * [DevotionScreenLabel.WEB_READER] is 網頁版 rather than 在瀏覽器開啟 because the two are different
 * destinations and both exist in the translations: `devotion_open_web_reader` names the embedded frame
 * this page opens, and `devotion_open_in_browser` names the system browser that the reader's own top
 * bar hands out to at `DevotionWebReader.kt:246`. Naming this control with the browser's string would
 * have been the one plausible wrong choice here — it is a book glyph beside a copy glyph, and a reader
 * who was told it would open a browser would not be wrong so much as sent somewhere they had not asked
 * to go.
 */
fun devotionScreenLabel(label: DevotionScreenLabel): Int = when (label) {
    DevotionScreenLabel.TITLE -> R.string.tab_devotion
    DevotionScreenLabel.REFRESH -> R.string.devotion_refresh
    DevotionScreenLabel.COPY_ARTICLE -> R.string.devotion_copy_article
    DevotionScreenLabel.WEB_READER -> R.string.devotion_open_web_reader
    DevotionScreenLabel.LOADING -> R.string.devotion_loading
    DevotionScreenLabel.LOAD_FAILED -> R.string.devotion_load_failed
    DevotionScreenLabel.RETRY -> R.string.devotion_retry
}

/** `colors.ink.withValues(alpha: .55)`, the border of the chip in hand. */
private const val ACTIVE_CHIP_BORDER_ALPHA = 0.55f

/** `AppSpinner(size: 16)` inside the 40 dp refresh button. */
private val REFRESH_SPINNER_SIZE = 16.dp

private const val MASTHEAD_KEY = "devotionMasthead"
private const val BODY_KEY = "devotionBody"
private const val POST_DAY_KEY = "devotionPostDay"
private const val POST_TITLE_KEY = "devotionPostTitle"
