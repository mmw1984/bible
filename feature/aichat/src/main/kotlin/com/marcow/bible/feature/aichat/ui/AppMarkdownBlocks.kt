package com.marcow.bible.feature.aichat.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppColors
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.designsystem.theme.appRadii
import com.marcow.bible.feature.aichat.markdown.MarkdownBlock
import com.marcow.bible.feature.aichat.markdown.MarkdownInline
import com.marcow.bible.feature.aichat.markdown.parseAppMarkdown

/**
 * The drawing half of [parseAppMarkdown], laying out the blocks it returns with the metrics of
 * `AppMarkdown` in `legacy/flutter/lib/app_markdown.dart`.
 *
 * The scanner is already ported and pinned by tests, so nothing here re-reads the text and the two
 * block hierarchies line up one for one. Every number below is out of the Dart, and [compact] picks
 * between the two values each of them has: Flutter's `AppMarkdown` took a `compact` flag and the chat
 * passed it twice — `false` for an answer (`ai_chat_page.dart:757`) and `true` for the thinking block
 * (`ai_chat_page.dart:875`) — and that flag is the only difference between those two renderings.
 *
 * A link is tappable only when [onLink] is given, because whether one could be followed was a draw-time
 * question in Dart too: `Uri.tryParse` had to have succeeded *and* `onLink` had to have been supplied
 * (`app_markdown.dart:356`). The thinking block is built with no handler, so a link inside it is
 * underlined and inert — which is what an `AppTap` with a null `onTap` drew, rather than something
 * quieter than a link looks everywhere else in the answer.
 *
 * There is no test here, for the reason [parseAppMarkdown] gives for not having one either: the repo's
 * tests are pure JVM and the drawing needs a device. `legacy/flutter/test/app_markdown_test.dart`
 * asserted on `RichText` and `Table` widgets, and everything it asserted about — which block a line
 * became, what the inline runs are, which item is a checkbox — is now pinned by `AppMarkdownTest` against
 * the plain data this draws. What the numbers below fix is the half that file could not reach.
 */
@Composable
internal fun AppMarkdownBlocks(
    data: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    foreground: Color = appColors.ink,
    secondary: Color = appColors.muted,
    onLink: ((String) -> Unit)? = null,
) {
    // Parsed per answer rather than per recomposition, because a streaming answer redraws about twenty
    // times a second and this is the only part of the drawing that walks the whole text.
    val blocks = remember(data) { parseAppMarkdown(data) }
    val palette = remember(foreground, secondary, onLink) { MarkdownPalette(foreground, secondary, onLink) }
    // Dart's `_selectableRichText` joined the ambient `SelectionContainer` instead of making one
    // (`app_markdown.dart:332`), which put selection over the whole transcript rather than one answer.
    // Here the container is per call, so a selection stops at an answer's edge — the two agree on every
    // single answer, which is what the tests ever drag across.
    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth()) {
            blocks.forEach { block ->
                MarkdownBlockRow(block = block, palette = palette, compact = compact)
            }
        }
    }
}

/**
 * The three things a whole answer is drawn with: `AppMarkdown`'s `foreground`, `secondary` and `onLink`.
 *
 * Carried as one value because they change together — an answer and its thinking block are the only
 * two callers and they differ in all three — and threading them separately put `MarkdownTableRow` over
 * the eight-parameter limit for no gain.
 *
 * The thinking block passes `muted` for *both* colours (`ai_chat_page.dart:872`), which is why
 * [foreground] is a parameter at all rather than being read from the theme: an answer is ink, and a
 * thought being read is deliberately not.
 */
@Immutable
private class MarkdownPalette(
    val foreground: Color,
    val secondary: Color,
    val onLink: ((String) -> Unit)?,
)

/**
 * One block, under the bottom space it carried.
 *
 * Five of the six blocks took Dart's `_margin` of `compact ? 7 : 11`. A list item did not: it has its
 * own `compact ? 5 : 7` (`app_markdown.dart:284`), which is tighter, and a list is the one place the
 * two would be seen side by side.
 */
@Composable
private fun MarkdownBlockRow(
    block: MarkdownBlock,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    val margin = if (block is MarkdownBlock.ListItem) listMargin(compact) else blockMargin(compact)
    Column(modifier = Modifier.padding(bottom = margin)) {
        when (block) {
            is MarkdownBlock.Paragraph ->
                MarkdownBody(
                    inlines = block.inlines,
                    color = palette.foreground,
                    compact = compact,
                    onLink = palette.onLink,
                )

            is MarkdownBlock.Heading -> MarkdownHeading(level = block.level, text = block.text, compact = compact)

            MarkdownBlock.Rule -> Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RULE_HEIGHT)
                    .background(appColors.line),
            )

            is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(code = block.code, compact = compact)

            is MarkdownBlock.Quote -> MarkdownQuote(
                inlines = block.inlines,
                palette = palette,
                compact = compact,
            )

            is MarkdownBlock.ListItem -> MarkdownListItem(
                item = block,
                palette = palette,
                compact = compact,
            )

            is MarkdownBlock.Table -> MarkdownTable(table = block, palette = palette, compact = compact)
        }
    }
}

/**
 * The run of text a paragraph, a quote, a list item and a table cell all share.
 *
 * [color] is the block's own choice of ink or muted rather than a switch, so that the thinking block —
 * which passes muted for both — needs no flag of its own to be drawn.
 */
@Composable
private fun MarkdownBody(
    inlines: List<MarkdownInline>,
    color: Color,
    compact: Boolean,
    onLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = bodyLineHeight(compact),
) {
    Text(
        text = markdownAnnotatedString(
            inlines = inlines,
            base = TextStyle(
                color = color,
                fontFamily = AppFonts.OpenRunde,
                fontSize = bodyFontSize(compact),
                lineHeight = lineHeight,
            ),
            foreground = color,
            compact = compact,
            onLink = onLink,
        ),
        modifier = modifier,
    )
}

/**
 * `_heading`: the level's own size, `1.35` line height and `w600`, over `_plain`'s output rather than
 * the inline runs — a heading's `*emphasis*` is reduced to the words instead of drawn.
 *
 * Dart named no family here, which put the heading in the app-wide `ThemeData(fontFamily: 'OpenRunde')`
 * (`main.dart:121`); the family is written out because relying on the ambient style would be a
 * difference between the two toolkits for no gain.
 */
@Composable
private fun MarkdownHeading(level: Int, text: String, compact: Boolean) {
    val size = headingFontSize(level, compact)
    Text(
        text = text,
        color = appColors.ink,
        fontFamily = AppFonts.OpenRunde,
        fontSize = size,
        lineHeight = size * HEADING_LINE_HEIGHT,
        fontWeight = FontWeight.W600,
    )
}

/**
 * `_quote`: a 3 dp rule down the left edge over a `.28` wash, and `1.55` line height.
 *
 * The 1.55 is the one size in the file that ignores [compact] — a quote is always 1.55, where the
 * paragraph beside it is 1.5 or 1.58 (`app_markdown.dart:166`). The rule is drawn rather than laid out
 * so the text keeps Dart's `fromLTRB(12, 8, 12, 8)`, twelve from the wash's own edge: the border was
 * painted over the padding box, not carved out of it.
 */
@Composable
private fun MarkdownQuote(
    inlines: List<MarkdownInline>,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    val colors = appColors
    MarkdownBody(
        inlines = inlines,
        color = palette.secondary,
        compact = compact,
        onLink = palette.onLink,
        lineHeight = bodyFontSize(compact) * QUOTE_LINE_HEIGHT,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.line.copy(alpha = QUOTE_WASH))
            .drawBehind { drawRect(colors.line, size = Size(QUOTE_RULE_WIDTH.toPx(), size.height)) }
            .padding(
                start = QUOTE_PADDING_HORIZONTAL,
                top = QUOTE_PADDING_VERTICAL,
                end = QUOTE_PADDING_HORIZONTAL,
                bottom = QUOTE_PADDING_VERTICAL,
            ),
    )
}

/**
 * `_codeBlock`: the lines verbatim in a monospace face, inside a `.22` wash behind a full border.
 *
 * The block fills the width and its lines wrap, because Dart's `SelectableText` did (`app_markdown.dart:182`)
 * and a code line is only readable by its shape — this deliberately does not scroll sideways.
 */
@Composable
private fun MarkdownCodeBlock(code: String, compact: Boolean) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.compact)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.line.copy(alpha = CODE_WASH), shape)
            .border(RULE_HEIGHT, colors.line, shape)
            .padding(CODE_PADDING),
    ) {
        Text(
            text = code,
            color = colors.ink,
            fontFamily = FontFamily.Monospace,
            fontSize = codeFontSize(compact),
            lineHeight = codeFontSize(compact) * CODE_LINE_HEIGHT,
        )
    }
}

/**
 * `_listItem`: the marker column, then the text, both starting at the top of the row.
 *
 * The column is 22 dp for a plain marker and 24 dp for a checkbox, which is the `SizedBox` Dart chose
 * between (`app_markdown.dart:289`); the box is drawn *instead of* the marker rather than beside it, so
 * ticked and unticked items in one list still line their text up.
 */
@Composable
private fun MarkdownListItem(
    item: MarkdownBlock.ListItem,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(if (item.checked == null) MARKER_COLUMN else CHECK_COLUMN)) {
            if (item.checked == null) {
                MarkdownMarker(text = item.marker)
            } else {
                MarkdownCheckbox(checked = item.checked)
            }
        }
        MarkdownBody(
            inlines = item.inlines,
            color = palette.foreground,
            compact = compact,
            onLink = palette.onLink,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The `—`, or the typed `1.`, in the marker column.
 *
 * Its size is the one number in the file that ignored `compact` and named no `fontSize` at all: Dart
 * styled the marker `TextStyle(color: secondary, height: 1.58)` and nothing else
 * (`app_markdown.dart:291`), so it took Material's ambient 14 rather than the body's `12`/`14`. That 14
 * is written down instead of inherited, because the ambient size here is Material's `bodyLarge` and
 * relying on it would be a difference between the two toolkits for nothing.
 */
@Composable
private fun MarkdownMarker(text: String) {
    Text(
        text = text,
        color = appColors.muted,
        fontFamily = AppFonts.OpenRunde,
        fontSize = MARKER_FONT_SIZE,
        lineHeight = MARKER_FONT_SIZE * MARKER_LINE_HEIGHT,
    )
}

/** `_check`: a 13 dp box, filled and glyphed when ticked, outlined and empty otherwise. */
@Composable
private fun MarkdownCheckbox(checked: Boolean) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.compact / 4)
    Box(
        modifier = Modifier
            .padding(top = CHECK_TOP_INSET)
            .size(CHECK_SIZE)
            .background(if (checked) colors.ink else Color.Transparent, shape)
            .border(RULE_HEIGHT, colors.ink, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            // `AppColors.dark.canvas` in Dart, not the themed canvas: the box is always an ink fill, so
            // the tick was always the light colour even in light mode.
            AppGlyphView(glyph = AppGlyph.CHECK, color = AppColors.Dark.canvas, size = CHECK_GLYPH_SIZE)
        }
    }
}

/**
 * `_table`: every row padded out to the widest row's column count, inside a bordered container that
 * scrolls sideways.
 *
 * Flutter sized the columns from a `LayoutBuilder`, clamped each to 132–240 dp and then made the table
 * as wide as those columns needed even when that was wider than the answer (`app_markdown.dart:203`).
 * The clamp is what stops a two-column table from stretching across the bubble and the overflow is what
 * keeps a five-column one legible, so both are kept.
 */
@Composable
private fun MarkdownTable(
    table: MarkdownBlock.Table,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    val colors = appColors
    val shape = RoundedCornerShape(appRadii.compact)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = table.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val columnWidth = (maxWidth / columns).coerceIn(TABLE_MIN_COLUMN, TABLE_MAX_COLUMN)
        val tableWidth = if (maxWidth > columnWidth * columns) maxWidth else columnWidth * columns
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(RULE_HEIGHT, colors.line, shape)
                .horizontalScroll(rememberScrollState()),
        ) {
            table.rows.forEachIndexed { rowIndex, row ->
                MarkdownTableRow(
                    cells = row,
                    rowIndex = rowIndex,
                    columns = columns,
                    width = tableWidth / columns,
                    palette = palette,
                    compact = compact,
                )
            }
        }
    }
}

/**
 * One table row.
 *
 * The header is `w600` on a `.3` wash, an even row a `.1` one, and `Table`'s `inside` border rules off
 * every cell from its neighbour and every row from the one above it. All three come off [rowIndex]
 * rather than being passed, because Dart read them off the index too
 * (`app_markdown.dart:234-238`) and a row that is not its own kind is the only kind there is.
 *
 * Cells are centred in the row rather than sitting at its top, which is what
 * `TableCellVerticalAlignment.middle` asked for: a row of differing cell heights reads as one line of
 * text when they are centred and as a ragged block when they are not.
 */
@Composable
private fun MarkdownTableRow(
    cells: List<List<MarkdownInline>>,
    rowIndex: Int,
    columns: Int,
    width: Dp,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    val colors = appColors
    val header = rowIndex == 0
    Row(
        modifier = Modifier
            .width(width * columns)
            .drawBehind {
                if (rowIndex > 0) {
                    drawLine(colors.line, start = Offset.Zero, end = Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
            }
            .background(
                when {
                    header -> colors.line.copy(alpha = TABLE_HEADER_WASH)
                    rowIndex % 2 == 0 -> colors.line.copy(alpha = TABLE_ROW_WASH)
                    else -> Color.Transparent
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(columns) { column ->
            Box(
                modifier = Modifier
                    .width(width)
                    .drawBehind {
                        if (column > 0) {
                            drawLine(
                                colors = colors.line,
                                start = Offset.Zero,
                                end = Offset(0f, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                    .padding(
                        horizontal = if (compact) TABLE_CELL_PADDING_COMPACT else TABLE_CELL_PADDING,
                        vertical = if (compact) TABLE_CELL_PADDING_ROW_COMPACT else TABLE_CELL_PADDING_ROW,
                    ),
            ) {
                MarkdownCell(
                    inlines = cells.getOrNull(column).orEmpty(),
                    header = header,
                    palette = palette,
                    compact = compact,
                )
            }
        }
    }
}

/** One table cell, at the table's own `11`/`13` and `1.45`. */
@Composable
private fun MarkdownCell(
    inlines: List<MarkdownInline>,
    header: Boolean,
    palette: MarkdownPalette,
    compact: Boolean,
) {
    MarkdownBody(
        inlines = inlines,
        color = if (header) palette.foreground else palette.secondary,
        compact = compact,
        onLink = palette.onLink,
        lineHeight = tableFontSize(compact) * TABLE_LINE_HEIGHT,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * `_inline`: one block's runs as an [AnnotatedString] over [base].
 *
 * The link run pushes a [LinkAnnotation] before its own style so the two nest in that order and the
 * style is still in force when the label is appended. Its colour is
 * `Color.alphaBlend(const Color(0xFF3F78A8), foreground)`, which resolves to the constant because
 * Flutter's alpha compositing returns a fully opaque over colour unchanged whatever it is laid on — so
 * it is written as the literal it resolves to rather than as a blend that can only produce that.
 */
@Composable
private fun markdownAnnotatedString(
    inlines: List<MarkdownInline>,
    base: TextStyle,
    foreground: Color,
    compact: Boolean,
    onLink: ((String) -> Unit)?,
): AnnotatedString {
    val inlineWash = appColors.line.copy(alpha = INLINE_CODE_WASH)
    return buildAnnotatedString {
        appendStyle(base)
        inlines.forEach { inline ->
            when (inline) {
                is MarkdownInline.Text -> append(inline.text)

                is MarkdownInline.Code -> {
                    // Dart named `foreground` on this run rather than leaving it to inherit
                    // (`app_markdown.dart:375`), so inline code keeps the block's colour where its
                    // surrounding text is muted — a quote's code, or a table cell's.
                    pushStyle(
                        SpanStyle(
                            color = foreground,
                            background = inlineWash,
                            fontFamily = FontFamily.Monospace,
                            fontSize = codeFontSize(compact),
                        ),
                    )
                    append(inline.text)
                    pop()
                }

                is MarkdownInline.Strong -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.W700))
                    append(inline.text)
                    pop()
                }

                is MarkdownInline.Emphasis -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(inline.text)
                    pop()
                }

                is MarkdownInline.Link -> {
                    val tappable = onLink != null && inline.target.isNotBlank()
                    if (tappable) pushLink(LinkAnnotation.Url(Uri.parse(inline.target)))
                    pushStyle(
                        SpanStyle(
                            color = LINK_COLOR,
                            textDecoration = TextDecoration.Underline,
                            fontFamily = AppFonts.OpenRunde,
                            fontSize = bodyFontSize(compact),
                        ),
                    )
                    append(inline.label)
                    pop()
                    if (tappable) pop()
                }
            }
        }
    }
}

/** `EdgeInsets.only(bottom: compact ? 7 : 11)`, which five of the six blocks shared. */
private fun blockMargin(compact: Boolean): Dp = if (compact) 7.dp else 11.dp

/** `EdgeInsets.only(bottom: compact ? 5 : 7)`, the tighter one a list item had instead. */
private fun listMargin(compact: Boolean): Dp = if (compact) 5.dp else 7.dp

/** `fontSize: compact ? 12 : 14`, for paragraphs, quotes, list items and link labels. */
private fun bodyFontSize(compact: Boolean) = if (compact) 12.sp else 14.sp

/** `height: compact ? 1.5 : 1.58`, expressed against the size it multiplies. */
private fun bodyLineHeight(compact: Boolean) = if (compact) 1.5f else 1.58f

/** The `switch (level)` of `_heading`. */
private fun headingFontSize(level: Int, compact: Boolean) = when (level) {
    1 -> if (compact) 17.sp else 21.sp
    2 -> if (compact) 15.sp else 18.sp
    else -> if (compact) 14.sp else 16.sp
}

/** `fontSize: compact ? 11 : 12` of `_codeBlock`, and of an inline code run. */
private fun codeFontSize(compact: Boolean) = if (compact) 11.sp else 12.sp

/** `fontSize: compact ? 11 : 13` of a table cell. */
private fun tableFontSize(compact: Boolean) = if (compact) 11.sp else 13.sp

/** `border.withValues(alpha: .28)` behind a quote. */
private const val QUOTE_WASH = 0.28f

/** `border.withValues(alpha: .22)` behind a code block. */
private const val CODE_WASH = 0.22f

/** `border.withValues(alpha: .35)` behind an inline code run. */
private const val INLINE_CODE_WASH = 0.35f

/** `border.withValues(alpha: .3)` behind a table's header row. */
private const val TABLE_HEADER_WASH = 0.3f

/** `border.withValues(alpha: .1)` behind an even table row. */
private const val TABLE_ROW_WASH = 0.1f

/** `BorderSide(width: 3)` on the left of a quote, and `BorderSide`'s default 1 elsewhere. */
private val QUOTE_RULE_WIDTH = 3.dp

/** The 1 dp a `BorderSide` defaults to, which is also the `height: 1` of a `---` rule. */
private val RULE_HEIGHT = 1.dp

/** The 12 either side of a quote's text. */
private val QUOTE_PADDING_HORIZONTAL = 12.dp

/** The 8 above and below a quote's text. */
private val QUOTE_PADDING_VERTICAL = 8.dp

/** `EdgeInsets.all(13)` inside a code block. */
private val CODE_PADDING = 13.dp

/** The `SizedBox(width: 22)` of a bullet or ordered item's marker column. */
private val MARKER_COLUMN = 22.dp

/** The `SizedBox(width: 24)` a checkbox takes in its place. */
private val CHECK_COLUMN = 24.dp

/** The 13 dp box `_check` drew. */
private val CHECK_SIZE = 13.dp

/** `EdgeInsets.only(top: 4)`, which sat the box down inside the marker column. */
private val CHECK_TOP_INSET = 4.dp

/** The 11 dp check glyph inside the box. */
private val CHECK_GLYPH_SIZE = 11.dp

/** `clamp(132.0, 240.0)` on one table column's width. */
private val TABLE_MIN_COLUMN = 132.dp
private val TABLE_MAX_COLUMN = 240.dp

/** `EdgeInsets.symmetric(horizontal: 11)` in a non-compact table cell. */
private val TABLE_CELL_PADDING = 11.dp

/** The 9 above and below it. */
private val TABLE_CELL_PADDING_ROW = 9.dp

/** `EdgeInsets.symmetric(horizontal: 9)` in a compact table cell. */
private val TABLE_CELL_PADDING_COMPACT = 9.dp

/** The 7 above and below it. */
private val TABLE_CELL_PADDING_ROW_COMPACT = 7.dp

/** `height: 1.35` of a heading. */
private const val HEADING_LINE_HEIGHT = 1.35f

/** `height: 1.55` of a quote, which ignored `compact` where the paragraph did not. */
private const val QUOTE_LINE_HEIGHT = 1.55f

/** `height: 1.45` of a code block and of a table cell. */
private const val CODE_LINE_HEIGHT = 1.45f
private const val TABLE_LINE_HEIGHT = 1.45f

/** `height: 1.58` of a list item's marker. */
private const val MARKER_LINE_HEIGHT = 1.58f

/** The ambient 14 the marker `Text` inherited, since Dart gave it no `fontSize` of its own. */
private val MARKER_FONT_SIZE = 14.sp

/** `Color(0xFF3F78A8)`, what `Color.alphaBlend` resolved the link colour to. */
private val LINK_COLOR = Color(0xFF3F78A8)