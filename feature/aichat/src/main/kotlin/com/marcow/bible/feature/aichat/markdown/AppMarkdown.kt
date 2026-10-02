package com.marcow.bible.feature.aichat.markdown

/**
 * The blocks `AppMarkdown` from `legacy/flutter/lib/app_markdown.dart` drew, as data rather than as
 * widgets — [parseAppMarkdown] is its `_blocks` and [inlineSpans] its `_inline`, and the two sealed
 * hierarchies below are what they built.
 *
 * ## Why the scanner is ported and not replaced by commonmark
 *
 * `NATIVE_PLAN.md` §5 Phase 4 item 4 asks for "commonmark + 自製 Compose renderer". Taking that
 * literally would not be 100% parity, because Flutter's `AppMarkdown` never used commonmark: it is a
 * hand-written line scanner, and its behaviour is not the Markdown spec's. A commonmark parse
 * disagrees with it on each of these, so each is a case where the spec is the wrong answer here:
 *
 *  - `- [x] done` is a task-list item in commonmark and renders as a literal `[x]` here. The checkbox
 *    is [MarkdownBlock.ListItem.checked] and there is no Markdown for it at all.
 *  - A table is recognised only when the line *after* the header is a divider of `---`-shaped cells.
 *    A commonmark table also wants the delimiter row's column count to line up and tolerates a missing
 *    leading `|`; this one reads a row of cells and stops at the first row with fewer than two of them.
 *  - The inline pass accepts an *unclosed* marker, because an answer is re-read on every streamed
 *    chunk: `**half an ans` is a strong run holding the text so far, not two stray asterisks left on
 *    screen until the closing one arrives.
 *  - A fence that is never closed runs to the end of the answer, and the closing fence's own line is
 *    dropped rather than shown.
 *
 * So the scanner is ported, regex for regex, and so is its precedence: blank line, fence, heading,
 * rule, table, quote, list, paragraph. What this file leaves out is the drawing, because the drawing
 * is the half a test cannot reach: `legacy/flutter/test/app_markdown_test.dart` asserts on `RichText`
 * and `Table` widgets and needs a device, while the blocks it was asserting about are plain data.
 *
 * Nothing visual is modelled. Every colour, size, radius and padding in the Dart lives in the widget
 * tree, and [MarkdownBlock.Table] keeps its rows ragged exactly as `_table` received them — padding a
 * short row out to the column count happened in `_table`'s `List.generate`, so it belongs to the
 * renderer too.
 *
 * Dart's `_blocks` ended with `blocks.isEmpty ? const [SizedBox.shrink()] : blocks`. A document with no
 * blocks is an empty list here, which reserves no height — the same pixels, without a block that exists
 * only to be nothing.
 */
sealed interface MarkdownBlock {
    /**
     * `_paragraph`: consecutive lines joined with a newline and read as inline Markdown.
     *
     * Joined rather than read line by line, so a paragraph that wraps across a soft line break is one
     * run of text — which is what lets a `**bold**` span written over two lines still close.
     */
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock

    /**
     * `_heading`: `#{1,3}` and a space, with the emphasis already taken off.
     *
     * [text] is `_plain`'s output and not the inline list, because Dart drew the heading with a plain
     * `Text`: a heading's `*emphasis*` is reduced to the words rather than drawn. [level] is the count
     * of `#`, which is all `_heading` read of it — the three sizes were a switch at draw time.
     */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** The `Container(height: 1, color: border)` a `---` / `***` / `___` line became. */
    data object Rule : MarkdownBlock

    /** `_codeBlock`: the lines between the fences, verbatim and unparsed. */
    data class CodeBlock(val code: String) : MarkdownBlock

    /** `_quote`: the `>` marker taken off the front of every line it collected. */
    data class Quote(val inlines: List<MarkdownInline>) : MarkdownBlock

    /**
     * `_listItem`, and the one item the Markdown spec does not have.
     *
     * [marker] is `—` for a bullet and the typed `1.` / `2.` for an ordered item, so the marker column
     * reads the way it did rather than being derived from a nesting level the block does not carry.
     * [checked] is null when the item was not a checkbox at all, and false or true for `- [ ]` and
     * `- [x]`: the box was 22dp wide as a marker and 24dp with the glyph in it, and it was drawn
     * instead of the marker rather than beside it.
     */
    data class ListItem(val inlines: List<MarkdownInline>, val marker: String, val checked: Boolean?) : MarkdownBlock

    /**
     * `_table`: the header row plus every body row, each a list of already-read cells.
     *
     * The nesting is rows → cells → inlines because that is the shape the question is about: a row is
     * a list of cells, and a cell is what `_inline` was handed, so `**愛**` inside one is bold. Rows
     * are ragged because `_table` received them ragged and padded them itself, so a body row shorter
     * than the header is not an error here and is not padded here either.
     */
    data class Table(val rows: List<List<List<MarkdownInline>>>) : MarkdownBlock
}

/**
 * One run of a block's text, as `_inline` split it.
 *
 * A link is [Link] rather than a styled text run because Dart drew it as a `WidgetSpan` wrapping a
 * tappable `AppTap`, not as an underlined `TextSpan`: the whole point of the case is that the reader can
 * follow it, and a renderer that could only underline it would have no way to say so.
 */
sealed interface MarkdownInline {
    /** Text with no marker in it, which is also the label a link is drawn from. */
    data class Text(val text: String) : MarkdownInline

    /** `` `code` ``, backticks removed and the marker kept on the run as its own styling. */
    data class Code(val text: String) : MarkdownInline

    /** `**strong**`, or a marker that has not closed yet and is strong all the same. */
    data class Strong(val text: String) : MarkdownInline

    /** `*emphasis*`, opened or not. */
    data class Emphasis(val text: String) : MarkdownInline

    /**
     * `[label](target)`.
     *
     * The target is the text inside the parentheses rather than a parsed `Uri`, because whether it was
     * tappable was a draw-time question: Dart passed `onTap` only once both `Uri.tryParse` had succeeded
     * and `onLink` was supplied, so a renderer with no link handler still has to be able to ask. Nothing
     * here is validated — `_inline` never looked at the target.
     */
    data class Link(val label: String, val target: String) : MarkdownInline
}

/**
 * `AppMarkdown._blocks` (`legacy/flutter/lib/app_markdown.dart:35`) — the answer's text split into the
 * blocks the chat draws, in the order `_blocks` recognised them.
 *
 * The whole of Dart's `for` loop is here, including the two places where its index arithmetic looks
 * like a slip and is not:
 *
 *  - A fenced block advances past the closing fence, so prose after it is prose and not code. An
 *    unterminated fence stops at the end of the answer and drops nothing, there being no closing fence
 *    to step over.
 *  - A table leaves the index on the line that ended it rather than stepping past it. Dart spelled that
 *    as `index--` immediately before a `continue` whose `for` update did `index++`, and the two cancel;
 *    here the row loop simply stops where it stopped, which is the line the next pass reads. Getting
 *    this wrong swallows the first line after every table.
 *
 * [data] is split on `\n` once `\r\n` has been folded, which is Dart's `split('\n')` and keeps the
 * trailing empty element — so a document ending in a newline ends on a blank line rather than ending on
 * its last line of text, exactly as it did there.
 */
fun parseAppMarkdown(data: String): List<MarkdownBlock> {
    val lines = data.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += MarkdownBlock.Paragraph(inlineSpans(paragraph.joinToString("\n")))
        paragraph.clear()
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        // Dart matched on these inside its own `&&` and `??`, so the branch that matched ran the
        // pattern a second time for the groups it went on to read. They are read once per line here
        // instead, which keeps the `when` reading like the chain of `if`s it replaces and costs four
        // pattern matches on a line that may match none.
        val heading = HEADING.find(line)
        val bullet = BULLET.find(line)
        val ordered = ORDERED.find(line)
        val header = markdownTableCells(line)
        when {
            // `line.trim().isEmpty()`: Kotlin's `isBlank` is Dart's `trim` over the same Unicode
            // White_Space set, so a line of fullwidth spaces separates paragraphs here too — and a
            // Chinese answer is full of them.
            line.isBlank() -> {
                flushParagraph()
                index++
            }

            line.trimStart().startsWith(FENCE) -> {
                flushParagraph()
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(FENCE)) {
                    code += lines[index]
                    index++
                }
                blocks += MarkdownBlock.CodeBlock(code.joinToString("\n"))
                index++
            }

            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = heading.groupValues[1].length,
                    text = markdownPlainText(heading.groupValues[2]),
                )
                index++
            }

            RULE.containsMatchIn(line) -> {
                flushParagraph()
                blocks += MarkdownBlock.Rule
                index++
            }

            header.size > 1 && index + 1 < lines.size && isTableDivider(lines[index + 1]) -> {
                flushParagraph()
                val rows = mutableListOf(listOf(inlineCells(header)))
                index += 2
                while (index < lines.size && lines[index].isNotBlank()) {
                    val cells = markdownTableCells(lines[index])
                    if (cells.size <= 1) break
                    rows += listOf(inlineCells(cells))
                    index++
                }
                blocks += MarkdownBlock.Table(rows)
            }

            line.startsWith(">") -> {
                flushParagraph()
                val quote = mutableListOf(QUOTE_PREFIX.replace(line, ""))
                while (index + 1 < lines.size && lines[index + 1].startsWith(">")) {
                    index++
                    quote += QUOTE_PREFIX.replace(lines[index], "")
                }
                blocks += MarkdownBlock.Quote(inlineSpans(quote.joinToString("\n")))
                index++
            }

            bullet != null || ordered != null -> {
                flushParagraph()
                val value = if (bullet != null) bullet.groupValues[1] else ordered!!.groupValues[2]
                val checkbox = CHECKBOX.find(value)
                val body = if (checkbox == null) value else value.substring(checkbox.range.last + 1)
                blocks += MarkdownBlock.ListItem(
                    inlines = inlineSpans(body),
                    marker = if (ordered == null) BULLET_MARKER else "${ordered.groupValues[1]}.",
                    checked = checkbox?.let { it.groupValues[1].lowercase() == "x" },
                )
                index++
            }

            else -> {
                paragraph += line
                index++
            }
        }
    }
    flushParagraph()
    return blocks
}

/**
 * `AppMarkdown._inline` (`legacy/flutter/lib/app_markdown.dart:336`) — one block's text split into the
 * runs it draws, markers taken off and the styling they asked for kept.
 *
 * The pattern is Dart's, its four alternatives and their laziness intact, and the laziness is
 * load-bearing: the second is `\*\*[\s\S]*?(?:\*\*|$)`, whose `|$` is what makes `**half an answer` a
 * strong run rather than two stray asterisks. The same `|$` is on the backtick and emphasis
 * alternatives.
 *
 * Every matched run is wrapped in [SENTINEL] before the split, which is how a match sitting flush
 * against neighbouring text cannot merge with it. Dart's `splitMapJoin` with an `onNonMatch` returning
 * its text unchanged is the same operation as Kotlin's `Regex.replace`, so both halves of that trick
 * are the one call here.
 *
 * The `$` anchors are Dart's and mean the end of the string. Java's `$` also matches on the line
 * before a final newline, and that difference is not papered over here; it cannot bite because the
 * only text reaching this function is a paragraph, a quote or a list item, and none of them can end in a
 * newline — the blocks that join lines join them from lines the blank-line check has already refused.
 */
fun inlineSpans(source: String): List<MarkdownInline> = INLINE.replace(source) {
    SENTINEL + it.value + SENTINEL
}.split(SENTINEL)
    .filter { it.isNotEmpty() }
    .map(::inlineSpanOf)

/** `_plain` (`legacy/flutter/lib/app_markdown.dart:400`): emphasis markers dropped, link text kept. */
fun markdownPlainText(value: String): String = LINK_LABEL.replace(value.replace(MARKDOWN_RUNS, "")) {
    it.groupValues[1]
}

/**
 * `_tableCells` (`legacy/flutter/lib/app_markdown.dart:413`) — one pipe-separated row, split on the
 * pipes that are not inside a code span and not escaped.
 *
 * The code-span toggle is why a cell can hold a pipe at all: `` `a|b` `` is one cell, and the backticks
 * stay in it because the cell is read with [inlineSpans] afterwards and they are what make the span
 * render. A trailing lone backslash is kept too, as Dart kept it — it escapes the character after it,
 * and there is no character after it.
 */
private fun markdownTableCells(source: String): List<String> {
    val trimmed = source.trim()
    if (!trimmed.contains("|")) return emptyList()
    val value = trimmed.replaceFirst(LEADING_PIPE, "").replaceFirst(TRAILING_PIPE, "")
    val cells = mutableListOf<String>()
    val buffer = StringBuilder()
    var escaped = false
    var inCode = false
    for (character in value) {
        when {
            escaped -> {
                buffer.append(character)
                escaped = false
            }

            character == '\\' -> escaped = true
            character == '`' -> {
                inCode = !inCode
                buffer.append(character)
            }

            character == '|' && !inCode -> {
                cells += buffer.toString().trim()
                buffer.clear()
            }

            else -> buffer.append(character)
        }
    }
    if (escaped) buffer.append('\\')
    cells += buffer.toString().trim()
    return cells
}

/** `_isTableDivider` (`legacy/flutter/lib/app_markdown.dart:407`): a row of `---`-shaped cells. */
private fun isTableDivider(source: String): Boolean {
    val cells = markdownTableCells(source)
    return cells.size > 1 && cells.all { DIVIDER_CELL.matches(it.trim()) }
}

/** One row of a table, each cell read the way `_table` read it. */
private fun inlineCells(cells: List<String>): List<List<MarkdownInline>> = cells.map(::inlineSpans)

/**
 * The one part of [inlineSpans] that branches: which run a sentinel-wrapped part became.
 *
 * The markers are tested in Dart's order — link, code, strong, emphasis — by `startsWith` rather than
 * by a match against the whole part, so a part the pattern produced through one alternative is
 * classified by that alternative's marker and is never re-read as another.
 */
private fun inlineSpanOf(part: String): MarkdownInline {
    val link = LINK.matchEntire(part)
    if (link != null) return MarkdownInline.Link(label = link.groupValues[1], target = link.groupValues[2])
    val code = part.startsWith(CODE_MARKER)
    val strong = part.startsWith(STRONG_MARKER)
    return when {
        code -> MarkdownInline.Code(unmarked(part, CODE_MARKER))
        strong -> MarkdownInline.Strong(unmarked(part, STRONG_MARKER))
        part.startsWith(EMPHASIS_MARKER) -> MarkdownInline.Emphasis(unmarked(part, EMPHASIS_MARKER))
        else -> MarkdownInline.Text(part)
    }
}

/**
 * The text between a run's two markers, or between the one marker and the end when it never closed.
 *
 * A run counts as closed only if it is long enough to hold something between the markers, which is
 * Dart's `part.length > 1` and `> 3` written once. A `***` is three characters and one unclosed strong
 * marker, so reading it as a closed `**…**` would ask for `substring(2, 1)`.
 */
private fun unmarked(part: String, marker: String): String {
    val closed = part.length > 2 * marker.length - 1 && part.endsWith(marker)
    return part.substring(marker.length, if (closed) part.length - marker.length else part.length)
}

/** ``` — Dart's `trimLeft().startsWith('```')`, which is `trimStart()` and the same fence. */
private const val FENCE = "```"

/** `—`, the marker a bullet was drawn with, so an ordered item's `1.` is the only numbered one. */
private const val BULLET_MARKER = "—"

/** What `splitMapJoin` fenced each match with, so a match cannot merge into the text beside it. */
private const val SENTINEL = "\u0000"

private const val CODE_MARKER = "`"
private const val STRONG_MARKER = "**"
private const val EMPHASIS_MARKER = "*"

/** `^(#{1,3})\s+(.+)$`, and the level is its group 1's length. */
private val HEADING = Regex("""^(#{1,3})\s+(.+)$""")

/** `^\s{0,3}([-*_])\1\1+\s*$` — three or more of one of the three rule characters. */
private val RULE = Regex("""^\s{0,3}([-*_])\1\1+\s*$""")

/** `^\s*[-*+]\s+(.+)$`; the three bullet markers were drawn identically, so they stay one pattern. */
private val BULLET = Regex("""^\s*[-*+]\s+(.+)$""")

/** `^\s*(\d+)\.\s+(.+)$`, whose group 1 is the number the marker repeated. */
private val ORDERED = Regex("""^\s*(\d+)\.\s+(.+)$""")

/** `^>\s?` — the quote marker, and at most one space behind it. */
private val QUOTE_PREFIX = Regex("""^>\s?""")

/** `^\[([ xX])\]\s+`, the task-list marker Flutter invented and commonmark also has. */
private val CHECKBOX = Regex("""^\[([ xX])\]\s+""")

/**
 * The four alternatives `_inline` matched, in Dart's order and with its laziness.
 *
 * A wrapped match is [SENTINEL] + the match + [SENTINEL] rather than a bare marker, which is what lets
 * `split(SENTINEL)` separate a match from its neighbours without the match having to be known by length.
 */
private val INLINE = Regex("""(`[^`\n]*(?:`|$)|\*\*[\s\S]*?(?:\*\*|$)|\*[^*\n]+(?:\*|$)|\[[^\]]+\]\([^\s)]+\))""")

/** `^\[([^\]]+)\]\(([^\s)]+)\)$` — anchored, so a link is the whole part or it is not a link. */
private val LINK = Regex("""^\[([^\]]+)\]\(([^\s)]+)\)$""")

/** `` [`*_]+ ``, what `_plain` deleted before keeping a heading's words. */
private val MARKDOWN_RUNS = Regex("""[`*_]+""")

/** `\[([^\]]+)\]\([^)]*\)`, the link form `_plain` reduced to its label. */
private val LINK_LABEL = Regex("""\[([^\]]+)\]\([^)]*\)""")

/** `^\|`, the leading pipe a table row is allowed to carry. */
private val LEADING_PIPE = Regex("""^\|""")

/** `\|$`, the trailing one. */
private val TRAILING_PIPE = Regex("""\|$""")

/** `^:?-{3,}:?$` — a divider cell, with the alignment colons the tables are read with. */
private val DIVIDER_CELL = Regex("""^:?-{3,}:?$""")
