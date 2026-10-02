package com.marcow.bible.feature.aichat.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The blocks `_blocks` made of an answer, pinned against `legacy/flutter/test/app_markdown_test.dart`
 * and the scanner it was asserting through.
 *
 * The Dart test drove widgets — it looked for `RichText` spans carrying `FontWeight.w700` and for a
 * `Table` with three children — so what it pinned was the *styling* of a run and the shape of a table
 * rather than the blocks underneath. Those two are the only two claims it made, and both are restated
 * here against the model: the five bold inputs are the same five strings, and the table is the same
 * fixture with the same three rows and four columns. The rest of the file is the scanner the styling
 * came out of, which no Dart test reached because reaching it meant driving a widget.
 *
 * `AppMarkdown` is the one piece of the Flutter build that is a renderer rather than a service, so
 * parity here is a matter of reproducing a regex rather than a behaviour: every expectation below is a
 * case where the spec and `_blocks` disagree, and the spec is not what ships.
 */
class AppMarkdownTest {
    @Test
    fun `completed and streaming strong spans are the same run`() {
        // The five inputs of the Dart test's first `testWidgets`. The fourth and fifth are the
        // interesting ones: `**` followed by a space is not emphasis in Markdown, and the lazy
        // alternative `\*\*[\s\S]*?(?:\*\*|$)` closes at the first `**` it can rather than at the last.
        assertEquals(listOf(MarkdownInline.Strong("completed")), inlineSpans("**completed**"))
        assertEquals(listOf(MarkdownInline.Strong("still streaming")), inlineSpans("**still streaming"))
        assertEquals(listOf(MarkdownInline.Strong("trailing space ")), inlineSpans("**trailing space **"))
        assertEquals(listOf(MarkdownInline.Strong(" 你好")), inlineSpans("** 你好"))
        assertEquals(
            listOf(
                MarkdownInline.Strong(" text"),
                MarkdownInline.Text(" / "),
                MarkdownInline.Strong("text "),
                MarkdownInline.Text(" / "),
                MarkdownInline.Strong(" text "),
            ),
            inlineSpans("** text** / **text ** / ** text **"),
        )
    }

    @Test
    fun `a table is a header row and every body row, with each cell read`() {
        // The Dart test's fixture. `**愛**` in a cell is bold because `_table` read cells with `_inline`,
        // and the row count is three because the divider line is consumed rather than drawn.
        val table = parseAppMarkdown(TABLE).single() as MarkdownBlock.Table

        assertEquals(3, table.rows.size)
        assertEquals(4, table.rows.first().size)
        assertEquals(
            listOf(
                MarkdownInline.Text("書卷"),
                MarkdownInline.Text("章節"),
                MarkdownInline.Text("主題"),
                MarkdownInline.Text("說明"),
            ),
            table.rows[0],
        )
        assertEquals(
            listOf(
                MarkdownInline.Text("約翰福音"),
                MarkdownInline.Text("3:16"),
                MarkdownInline.Strong("愛"),
                MarkdownInline.Text("神愛世人"),
            ),
            table.rows[1],
        )
        assertEquals(
            listOf(
                MarkdownInline.Text("詩篇"),
                MarkdownInline.Text("23:1"),
                MarkdownInline.Text("牧者"),
                MarkdownInline.Text("我必不致缺乏"),
            ),
            table.rows[2],
        )
    }

    @Test
    fun `the line after a table is a paragraph and not another row`() {
        // Dart's `index--` immediately before a `continue` whose `for` update did `index++`: the row
        // loop stops on the line that ended it and the next pass reads that line again. Stepping past
        // it instead would swallow the first line of every prose answer that followed a table.
        assertEquals(
            listOf(
                MarkdownBlock.Table(listOf(listOf(listOf(MarkdownInline.Text("a")), listOf(MarkdownInline.Text("b"))))),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("after"))),
            ),
            parseAppMarkdown("| a | b |\n| --- | --- |\nafter"),
        )
    }

    @Test
    fun `a row that is not a row ends the table where it is`() {
        // The loop's `cells.length <= 1` break: a line with no pipes at all is prose, and prose after a
        // table is a paragraph rather than a row of empty cells.
        assertEquals(
            listOf(
                MarkdownBlock.Table(
                    listOf(
                        listOf(listOf(MarkdownInline.Text("a")), listOf(MarkdownInline.Text("b"))),
                        listOf(listOf(MarkdownInline.Text("c")), listOf(MarkdownInline.Text("d"))),
                    ),
                ),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("not a row"))),
            ),
            parseAppMarkdown("| a | b |\n| --- | --- |\n| c | d |\nnot a row"),
        )
    }

    @Test
    fun `a one-cell header is prose, because only a row of cells can be a table`() {
        // `_tableCells(line).length > 1` ran before the divider was read, so a single-column pipe line
        // never reached the divider check and stayed a paragraph.
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("| a |\n| --- |")))),
            parseAppMarkdown("| a |\n| --- |"),
        )
    }

    @Test
    fun `a pipe inside a code span is one cell, and a backslash escapes the next character`() {
        val blocks = parseAppMarkdown("| `a|b` | c\\|d |\n| --- | --- |\n| x | y |")
        val table = blocks.single() as MarkdownBlock.Table

        // The backticks stay in the cell text, because the cell is read as inline Markdown afterwards
        // and they are what make the span render rather than print.
        assertEquals(
            listOf(
                listOf(listOf(MarkdownInline.Code("a|b")), listOf(MarkdownInline.Text("c|d"))),
                listOf(listOf(MarkdownInline.Text("x")), listOf(MarkdownInline.Text("y"))),
            ),
            table.rows,
        )
    }

    @Test
    fun `a fenced block ends at its fence, and an unterminated one runs to the end`() {
        assertEquals(
            listOf(
                MarkdownBlock.CodeBlock("val x = 1"),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("after"))),
            ),
            parseAppMarkdown("```\nval x = 1\n```\nafter"),
        )
        assertEquals(
            listOf(MarkdownBlock.CodeBlock("val x = 1\nafter")),
            parseAppMarkdown("```\nval x = 1\nafter"),
        )
    }

    @Test
    fun `three or more of one rule character is a rule`() {
        assertEquals(listOf(MarkdownBlock.Rule), parseAppMarkdown("---"))
        assertEquals(listOf(MarkdownBlock.Rule), parseAppMarkdown("***"))
        assertEquals(listOf(MarkdownBlock.Rule), parseAppMarkdown("___"))
        // Two are not. `---{3,}` needs three, and a bullet needs a space and some text behind it, so
        // `--` is prose — which is the whole of the difference between a rule and a stray marker.
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("--")))),
            parseAppMarkdown("--"),
        )
    }

    @Test
    fun `a heading keeps its level and loses its emphasis`() {
        // `_plain` rather than `_inline`: Dart drew the heading with a plain `Text`, so the asterisks
        // were deleted instead of styled. The level is the count of `#` and nothing else — the three
        // sizes were a switch at draw time.
        assertEquals(listOf(MarkdownBlock.Heading(1, "重點")), parseAppMarkdown("# 重點"))
        assertEquals(listOf(MarkdownBlock.Heading(2, "重點")), parseAppMarkdown("## **重點**"))
        assertEquals(listOf(MarkdownBlock.Heading(3, "解釋")), parseAppMarkdown("### [解釋](https://a.test)"))
        // Four hashes is not a heading. `#{1,3}` cannot reach the space, so the line is prose — which is
        // what a model writing `#### ` meant it not to be.
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("#### 四")))),
            parseAppMarkdown("#### 四"),
        )
    }

    @Test
    fun `a quote takes the marker off every line it collected`() {
        assertEquals(
            listOf(MarkdownBlock.Quote(listOf(MarkdownInline.Text("一句\n兩句")))),
            parseAppMarkdown("> 一句\n> 兩句"),
        )
        // `^>\s?` takes at most one space, so an indented quote keeps the rest of its indent.
        assertEquals(
            listOf(MarkdownBlock.Quote(listOf(MarkdownInline.Text("一句\n  兩句")))),
            parseAppMarkdown("> 一句\n>   兩句"),
        )
    }

    @Test
    fun `a bullet and an ordered item keep the markers they were drawn with`() {
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("第一")), "—", null)),
            parseAppMarkdown("- 第一"),
        )
        // The three bullet markers were one pattern, so they were all drawn as the dash.
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("星")), "—", null)),
            parseAppMarkdown("* 星"),
        )
        // An ordered item keeps the number the model typed rather than a list level.
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("第三")), "3.", null)),
            parseAppMarkdown("3. 第三"),
        )
    }

    @Test
    fun `a checkbox is drawn instead of the marker, and says which state it is in`() {
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("完成")), "—", true)),
            parseAppMarkdown("- [x] 完成"),
        )
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("未完成")), "—", false)),
            parseAppMarkdown("- [ ] 未完成"),
        )
        assertEquals(
            listOf(MarkdownBlock.ListItem(listOf(MarkdownInline.Text("大寫")), "—", true)),
            parseAppMarkdown("- [X] 大寫"),
        )
    }

    @Test
    fun `inline code and emphasis are their own runs, and an unclosed one is too`() {
        assertEquals(
            listOf(
                MarkdownInline.Text("用 "),
                MarkdownInline.Code("code"),
                MarkdownInline.Text(" 和 "),
                MarkdownInline.Emphasis("斜體"),
            ),
            inlineSpans("用 `code` 和 *斜體*"),
        )
        // `\*[^*\n]+(?:\*|$)` is what makes a lone `*` italic while the answer streams.
        assertEquals(listOf(MarkdownInline.Emphasis("half")), inlineSpans("*half"))
        // A `***` is three characters and one unclosed strong marker. Reading it as a closed `**…**`
        // would ask for `substring(2, 1)`, which is the guard `unmarked` exists for.
        assertEquals(listOf(MarkdownInline.Strong("*")), inlineSpans("***"))
    }

    @Test
    fun `a link keeps its label apart from its target`() {
        // `onLink` was a draw-time question, so the target stays text here: a renderer with no link
        // handler still has to be able to decide the run is inert.
        assertEquals(
            listOf(
                MarkdownInline.Text("看 "),
                MarkdownInline.Link("這裡", "https://a.test/x"),
                MarkdownInline.Text(" 吧"),
            ),
            inlineSpans("看 [這裡](https://a.test/x) 吧"),
        )
        // A space or a closing bracket inside the target ends the match, so this is not a link at all.
        assertEquals(
            listOf(MarkdownInline.Text("[x](a b)")),
            inlineSpans("[x](a b)"),
        )
    }

    @Test
    fun `a paragraph is joined across a soft line break, so a span can close on the next line`() {
        // The reason the lines are joined rather than read one at a time: `**` opened on the first line
        // still closes on the second, which only works if the two are one string by the time `_inline`
        // sees them.
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(
                    listOf(MarkdownInline.Text("這是\n"), MarkdownInline.Strong("重點")),
                ),
            ),
            parseAppMarkdown("這是\n**重點**"),
        )
    }

    @Test
    fun `a blank line ends a paragraph, and a carriage return is not one`() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("一"))),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("二"))),
            ),
            parseAppMarkdown("一\n\n二"),
        )
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("一\n二")))),
            parseAppMarkdown("一\r\n二"),
        )
    }

    @Test
    fun `an answer with nothing in it has no blocks`() {
        // Dart returned a single `SizedBox.shrink()` here, which reserved no height. An empty list is
        // the same drawing without a block that exists only to be nothing.
        assertEquals(emptyList<MarkdownBlock>(), parseAppMarkdown(""))
        assertEquals(emptyList<MarkdownBlock>(), parseAppMarkdown("   \n\n"))
    }

    @Test
    fun `a whole answer keeps the blocks in the order the scanner found them`() {
        val blocks = parseAppMarkdown(
            """
            | 書卷 | 章節 |
            | --- | --- |
            | JHN | 3:16 |

            ## 解釋

            第一段。

            - 一項
            - [x] 已完成

            > 註

            ---

            ```
            JHN 3:16
            ```
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "Table",
                "Heading",
                "Paragraph",
                "ListItem",
                "ListItem",
                "Quote",
                "Rule",
                "CodeBlock",
            ),
            blocks.map { it::class.simpleName },
        )
    }

    private companion object {
        /** The Dart test's table fixture, blank lines and all. */
        val TABLE = """
            | 書卷 | 章節 | 主題 | 說明 |
            | --- | :---: | --- | ---: |
            | 約翰福音 | 3:16 | **愛** | 神愛世人 |
            | 詩篇 | 23:1 | 牧者 | 我必不致缺乏 |
        """.trimIndent()
    }
}
