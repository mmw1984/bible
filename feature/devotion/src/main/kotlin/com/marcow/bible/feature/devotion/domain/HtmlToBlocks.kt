package com.marcow.bible.feature.devotion.domain

/**
 * WordPress post HTML → [DevotionBlock]s, ported from `parseDevotionBlocks` and the tolerant tree
 * it walks in `legacy/flutter/lib/devotion_content.dart`.
 *
 * The tree is built by this file's own scan rather than by Jsoup's tree builder, which is the whole
 * point of it. `Jsoup.parse` would apply HTML5's implied end tags — a `<div>` closes an open
 * `<p>`, `<li>` closes `<li>` — and the Dart build never did, because it nested tags by position and
 * ignored close tags that matched nothing. Posts are hand-written markup wrapped in five layers of
 * `<div>`; the difference between those two readings is the difference between one paragraph and two,
 * and between a 觀畫 section being rendered at all or being swallowed into a paragraph. Scanning the
 * tags here and *building* the tree with the Dart stack keeps the malformed-markup tolerance of the
 * original, and the scan has to be ours as well as the tree: Jsoup exposes no tokenizer to borrow.
 */
private const val DOCUMENT_TAG = "#document"

/** `_voidElements`: never pushed on the stack, so `<img>` cannot swallow its siblings. */
private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
    "meta", "param", "source", "track", "wbr",
)

/** Raw-text elements, whose body arrives as one character run and the parser drops. */
private val RAW_TEXT_ELEMENTS = setOf("script", "style")

/**
 * ECMAScript's `\s`, not Kotlin's.
 *
 * `decodeHtmlEntities` turns `&nbsp;` into a plain space, but WordPress also emits `&#160;` and
 * copies in U+3000 (ideographic space) — and U+00A0 is whitespace to a browser while it is not to
 * Kotlin's `\s`. A paragraph would then keep an invisible U+00A0 where Dart's reader had a space,
 * and every golden comparison would differ by one character.
 */
private val WHITESPACE_RUN = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

private val SPACE_AROUND_NEWLINE = Regex(" *\\n *")

/** A node of the tolerant tree: text, or an element with its attributes and children. */
internal sealed interface HtmlNode

/** Raw, undecoded text — a reader never sees `&amp;` because decoding happens per block. */
internal data class HtmlText(val text: String) : HtmlNode

internal class HtmlElement(val tag: String, val attributes: Map<String, String> = emptyMap()) : HtmlNode {
    /** Mutable because the tokenizer appends to it while it builds. */
    val children: MutableList<HtmlNode> = mutableListOf()

    fun hasClass(name: String): Boolean = classes().contains(name)

    fun hasAnyClass(names: Set<String>): Boolean = classes().any { it in names }

    /** The first descendant element with this tag, depth-first. */
    fun firstByTag(tag: String): HtmlElement? {
        for (child in children) {
            if (child !is HtmlElement) continue
            if (child.tag == tag) return child
            child.firstByTag(tag)?.let { return it }
        }
        return null
    }

    /** Deep text of this subtree, entities decoded, whitespace collapsed. */
    val text: String
        get() = collapseWhitespace(decodeHtmlEntities(collectText()))

    private fun classes(): List<String> = attributes["class"].orEmpty().split(CLASS_WHITESPACE)

    private fun collectText(): String {
        val buffer = StringBuilder()
        for (child in children) {
            appendText(child, buffer)
        }
        return buffer.toString()
    }

    private fun appendText(node: HtmlNode, into: StringBuilder) {
        when (node) {
            is HtmlText -> into.append(node.text)
            is HtmlElement -> node.children.forEach { appendText(it, into) }
        }
    }

    private companion object {
        val CLASS_WHITESPACE = Regex("\\s+")
    }
}

/**
 * Parses [html] into a tree rooted at a `#document` element.
 *
 * Never throws: a malformed region degrades to text or is skipped. Close tags unwind the stack to the
 * element they name and are otherwise ignored, so `</div>` in the middle of a post cannot corrupt the
 * rest of the scan the way a regexp replace does.
 */
internal fun parseHtmlDocument(html: String): HtmlElement {
    val document = HtmlElement(DOCUMENT_TAG)
    val stack = ArrayDeque<HtmlElement>()
    stack.addLast(document)
    val tokens = tokenize(html)
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        index++
        index = applyToken(stack, tokens, index, token)
    }
    return document
}

/**
 * One tree-building step, over a token already stepped past.
 *
 * A run of text is appended to the element that is open; a start tag opens one and may swallow the raw
 * body behind it, which is the only step that moves the index on further; an end tag closes one. Split
 * out of the loop so the branch is a level shallower than the walk it belongs to, and so the walk reads
 * as the flat sequence of steps it is. [index] is the token after the one applied, as it was in the
 * loop this came out of.
 */
private fun applyToken(
    stack: ArrayDeque<HtmlElement>,
    tokens: List<HtmlToken>,
    index: Int,
    token: HtmlToken,
): Int {
    var next = index
    when (token) {
        is HtmlCharacter -> stack.last().children.add(HtmlText(token.data))

        is HtmlTag -> if (token.isEndTag) {
            closeTag(stack, token.data)
        } else {
            val element = openTag(stack, token)
            next = if (element.tag in RAW_TEXT_ELEMENTS) skipRawText(tokens, index, element.tag) else index
        }
    }
    return next
}

/** One step of the scan: a run of text, or a tag. */
private sealed interface HtmlToken {
    val data: String
}

/**
 * A run of text, raw and undecoded.
 *
 * Named rather than reused from Jsoup because `org.jsoup.parser.Token` is package-private, and
 * `Parser.tokenize` — the method that produced them — is not part of Jsoup's public API in 1.23.2,
 * so there is no token to alias.
 */
private class HtmlCharacter(override val data: String) : HtmlToken

/** A tag, named lowercased the way `Token.normalName()` named the ones this file was written against. */
private class HtmlTag(
    override val data: String,
    val attributes: Map<String, String> = emptyMap(),
    val isEndTag: Boolean = false,
    val isSelfClosing: Boolean = false,
) : HtmlToken

/**
 * Scans [html] into the token stream the tree is built from, and never throws.
 *
 * This is the hand-rolled tokenizer the file was always going to need: Jsoup's `Token` and
 * `Tokeniser` are both package-private and `Parser.tokenize` is not public, so no code outside
 * `org.jsoup.parser` can ask Jsoup for a token stream, and `Jsoup.parse` is the one reading of the
 * markup this file exists to avoid. Only what the tree walk reads is produced — a run of text, a
 * start tag or an end tag — because a doctype, an XML declaration and a comment carry nothing the
 * walk ever looked at, so they are stepped over here rather than carried as tokens nobody reads.
 */
private fun tokenize(html: String): List<HtmlToken> {
    val tokens = mutableListOf<HtmlToken>()
    var index = 0
    while (index < html.length) {
        val bracket = html.indexOf('<', index)
        if (bracket < 0) {
            tokens.add(HtmlCharacter(html.substring(index)))
            break
        }
        if (bracket > index) tokens.add(HtmlCharacter(html.substring(index, bracket)))
        index = readMarkup(html, bracket, tokens)
    }
    return tokens
}

/**
 * A `<` that opens nothing this tree can use: a doctype, an XML declaration, a comment, or the
 * bogus comment a browser makes of a `<` followed by neither a letter nor a `/`.
 */
private fun readIgnoredMarkup(html: String, start: Int): Int {
    if (!html.startsWith(COMMENT_OPEN, start)) return skipToTagEnd(html, start)
    val end = html.indexOf(COMMENT_CLOSE, start + COMMENT_OPEN.length)
    return if (end < 0) html.length else end + COMMENT_CLOSE.length
}

/** Steps over to the `>` that ends a construct, or to the end of the post when it never arrives. */
private fun skipToTagEnd(html: String, start: Int): Int {
    val end = html.indexOf('>', start)
    return if (end < 0) html.length else end + 1
}

/** One `<`, dispatched on what follows it, returning where the next token begins. */
private fun readMarkup(html: String, start: Int, tokens: MutableList<HtmlToken>): Int {
    val afterBracket = html.getOrNull(start + 1)
    if (afterBracket == null || (!afterBracket.isLetter() && afterBracket != '/')) {
        return readIgnoredMarkup(html, start)
    }
    return if (afterBracket == '/') readEndTag(html, start, tokens) else readStartTag(html, start, tokens)
}

/**
 * A close tag: its name, and nothing else — the tree walk reads attributes from open tags only.
 *
 * A `</div` the post ended in the middle of is still a close tag to the stack, because the blog's
 * markup does run out of `</div>` and a name that never arrived must not close anything.
 */
private fun readEndTag(html: String, start: Int, tokens: MutableList<HtmlToken>): Int {
    val nameStart = start + 2
    var index = nameStart
    while (index < html.length && isTagNameChar(html[index])) index++
    if (index > nameStart) tokens.add(HtmlTag(html.substring(nameStart, index).lowercase(), isEndTag = true))
    val end = html.indexOf('>', index)
    return if (end < 0) html.length else end + 1
}

/**
 * An open tag: its lowercased name, its attributes, and whether the post wrote the `/` of `<br />`.
 *
 * The loop reads the attributes rather than handing them to a sub-scan of their own, because a bare
 * attribute (`allowfullscreen`) and an assigned one (`class=title`) have to both leave the cursor
 * somewhere the loop can keep going from.
 */
private fun readStartTag(html: String, start: Int, tokens: MutableList<HtmlToken>): Int {
    val nameStart = start + 1
    var index = nameStart
    while (index < html.length && isTagNameChar(html[index])) index++
    val name = html.substring(nameStart, index).lowercase()
    val attributes = linkedMapOf<String, String>()
    var selfClosing = false
    while (index < html.length && html[index] != '>') {
        val c = html[index]
        if (c == '/') selfClosing = true
        if (c.isWhitespace() || c == '/') {
            index++
            continue
        }
        index = readAttribute(html, index, attributes)
    }
    tokens.add(HtmlTag(name, attributes, isSelfClosing = selfClosing))
    val afterTag = skipToTagEnd(html, index)
    return if (!selfClosing && name in RAW_TEXT_ELEMENTS) readRawText(html, afterTag, name, tokens) else afterTag
}

/**
 * One attribute, added only the first time it appears — Word-pasted markup repeats attributes, and a
 * later copy must not overwrite what the post wrote first.
 *
 * A quoted value is read whole, so a `>`, a `/` or a `&` inside a `style`, a data URI or a SoundCloud
 * query cannot be mistaken for the end of the tag or the end of the value.
 */
private fun readAttribute(html: String, start: Int, into: MutableMap<String, String>): Int {
    var index = start
    while (index < html.length && isAttributeNameChar(html[index])) index++
    // A stray `=` or quote between two attributes is not one: skip it, or the scan would stall on it.
    if (index == start) return start + 1
    val name = html.substring(start, index).lowercase()
    index = skipSpaces(html, index)
    if (index >= html.length || html[index] != '=') {
        if (name !in into) into[name] = ""
        return index
    }
    val value = readAttributeValue(html, skipSpaces(html, index + 1))
    if (name !in into) into[name] = value.value
    return value.end
}

/** An attribute value, quoted or bare, and where it ended. */
private class ScannedValue(val value: String, val end: Int)

private fun readAttributeValue(html: String, start: Int): ScannedValue {
    val quote = html.getOrNull(start)
    if (quote == '"' || quote == '\'') {
        val close = html.indexOf(quote, start + 1)
        if (close < 0) return ScannedValue(html.substring(start + 1), html.length)
        return ScannedValue(html.substring(start + 1, close), close + 1)
    }
    var index = start
    while (index < html.length && !html[index].isWhitespace() && html[index] != '>') index++
    return ScannedValue(html.substring(start, index), index)
}

/**
 * The body of a `<script>`/`<style>`, which runs to its close tag and cannot hold markup.
 *
 * Emitted as one character run so [skipRawText] still steps over it, and the close tag itself is
 * left for the scan to read as an end tag — the same two tokens the tokenizer this file was written
 * against produced for this shape.
 */
private fun readRawText(html: String, start: Int, tag: String, tokens: MutableList<HtmlToken>): Int {
    val close = html.indexOf("</$tag", start, ignoreCase = true)
    if (close < 0) {
        if (start < html.length) tokens.add(HtmlCharacter(html.substring(start)))
        return html.length
    }
    if (close > start) tokens.add(HtmlCharacter(html.substring(start, close)))
    return close
}

private fun skipSpaces(html: String, start: Int): Int {
    var index = start
    while (index < html.length && html[index].isWhitespace()) index++
    return index
}

private fun isTagNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.'

/** Everything that may appear inside an attribute name — `/` and `=` end it, as in HTML5. */
private fun isAttributeNameChar(c: Char): Boolean =
    !c.isWhitespace() && c != '=' && c != '>' && c != '/' && c != '"' && c != '\''

private const val COMMENT_OPEN = "<!--"

private const val COMMENT_CLOSE = "-->"

/**
 * Adds an element under the current node, and leaves it open unless it cannot hold anything.
 *
 * Raw-text elements are not left on the stack either: the Dart scanner jumped past `</script` without
 * unwinding, so what followed a script stayed a sibling of it rather than a child.
 */
private fun openTag(stack: ArrayDeque<HtmlElement>, token: HtmlTag): HtmlElement {
    // Attribute names arrive lowercased and first-occurrence-wins: the scan applies both policies as
    // it reads, so the tree walk can take the map as it stands.
    val element = HtmlElement(token.data, token.attributes)
    stack.last().children.add(element)
    val staysOpen = !token.isSelfClosing && element.tag !in VOID_ELEMENTS && element.tag !in RAW_TEXT_ELEMENTS
    if (staysOpen) stack.addLast(element)
    return element
}

/** Steps over a `<script>`/`<style>` body, which the scan hands over as one character run. */
private fun skipRawText(tokens: List<HtmlToken>, from: Int, tag: String): Int {
    var index = from
    while (index < tokens.size) {
        val token = tokens[index]
        index++
        if (token is HtmlTag && token.isEndTag && token.data == tag) return index
    }
    return index
}

/**
 * Unwinds to the element an end tag names, and does nothing when there is none.
 *
 * The search stops short of the document node and stops unwinding as soon as it matches, so a stray
 * `</div>` in the middle of a post leaves the stack exactly as it found it.
 */
private fun closeTag(stack: ArrayDeque<HtmlElement>, tag: String) {
    for (depth in stack.lastIndex downTo 1) {
        if (stack[depth].tag == tag) {
            while (stack.size > depth) {
                stack.removeLast()
            }
            return
        }
    }
}

/**
 * Collapses the whitespace of a block's text.
 *
 * `<br>` writes a NUL sentinel instead, so the newlines that come from HTML indentation collapse to
 * spaces while an explicit break survives as `\n` inside the one paragraph that holds it — and a
 * paragraph that is only `<br>` markers collapses to nothing, which is what keeps media from being
 * preceded by a blank line.
 */
internal fun collapseWhitespace(input: String): String = input.replace(WHITESPACE_RUN, " ")
    .trim()
    .replace(BREAK_SENTINEL, '\n')
    .replace(SPACE_AROUND_NEWLINE, "\n")
    .trim()

private const val BREAK_SENTINEL = '\u0000'

/**
 * Serializes a parsed subtree back to HTML.
 *
 * Posts scraped from a page are cached this way, so `parseDevotionBlocks` re-parses them offline
 * exactly as it does API and RSS content.
 */
internal fun serializeHtmlElement(element: HtmlElement): String = nodeToHtml(element)

private fun nodeToHtml(node: HtmlNode): String = when (node) {
    is HtmlText -> node.text
    is HtmlElement -> {
        val attributes = node.attributes.entries.joinToString("") { (key, value) ->
            if (value.isEmpty()) " $key" else " $key=\"$value\""
        }
        if (node.tag in VOID_ELEMENTS) {
            "<${node.tag}$attributes>"
        } else {
            "<${node.tag}$attributes>${node.children.joinToString("") { nodeToHtml(it) }}</${node.tag}>"
        }
    }
}

/** Converts WordPress post HTML into renderable blocks. */
internal fun parseDevotionBlocks(html: String): List<DevotionBlock> {
    if (html.trim().isEmpty()) return emptyList()
    return parseDevotionBlocksFromElement(parseHtmlDocument(html))
}

/**
 * The same conversion from an already-parsed subtree, for the site-page fallback, which locates the
 * content container inside a whole article page before converting it.
 */
internal fun parseDevotionBlocksFromElement(root: HtmlElement): List<DevotionBlock> =
    deduplicateImages(flattenSections(convertChildren(root)))

private fun convertChildren(parent: HtmlElement): List<DevotionBlock> {
    val blocks = mutableListOf<DevotionBlock>()
    for (node in parent.children) {
        if (node is HtmlElement) convertElement(node, blocks)
    }
    return blocks
}

private fun convertNodes(nodes: List<HtmlNode>): List<DevotionBlock> {
    val blocks = mutableListOf<DevotionBlock>()
    for (node in nodes) {
        if (node is HtmlElement) convertElement(node, blocks)
    }
    return blocks
}

private fun convertElement(element: HtmlElement, blocks: MutableList<DevotionBlock>) {
    when (element.tag) {
        "div" -> {
            val titleChild = titledChild(element)
            if (titleChild == null) {
                convertContainer(element, blocks)
            } else {
                val body = element.children.filter { it !== titleChild }
                blocks.add(DevotionSection(titleChild.text, convertNodes(body)))
            }
        }

        "p" -> convertRichContent(element, blocks) { DevotionParagraph(it) }
        "h1", "h2", "h3", "h4", "h5", "h6" -> convertRichContent(element, blocks) { DevotionHeading(it) }
        "blockquote" -> convertRichContent(element, blocks) { DevotionQuote(it) }
        "li" -> convertRichContent(element, blocks) { DevotionParagraph("• $it") }
        "ul", "ol" -> blocks.addAll(convertChildren(element))
        "figure" -> extractMedia(element, blocks)
        "img" -> addImage(element, blocks)
        "iframe" -> addVideo(element, blocks)
        "script", "style", "head" -> Unit
        else -> convertContainer(element, blocks)
    }
}

/** The first direct child styled as a section title, if any. */
private fun titledChild(element: HtmlElement): HtmlElement? =
    element.children.filterIsInstance<HtmlElement>().firstOrNull { it.hasClass("title") }

/**
 * Transparent wrapper: promote the children, and keep orphan text as a paragraph so an unlabelled
 * wrapper never swallows content.
 */
private fun convertContainer(element: HtmlElement, blocks: MutableList<DevotionBlock>) {
    val converted = convertChildren(element)
    if (converted.isNotEmpty()) {
        blocks.addAll(converted)
        return
    }
    val text = element.text
    if (text.isNotEmpty()) blocks.add(DevotionParagraph(text))
}

/**
 * Paragraph-like nodes: text accumulates into one block while embedded media is emitted in document
 * order, so an image between two runs of text splits them the way the post reads.
 */
private fun convertRichContent(
    element: HtmlElement,
    blocks: MutableList<DevotionBlock>,
    build: (String) -> DevotionBlock,
) {
    val buffer = StringBuilder()

    fun flush() {
        val text = collapseWhitespace(buffer.toString())
        buffer.setLength(0)
        if (text.isNotEmpty()) blocks.add(build(text))
    }

    fun visit(node: HtmlNode) {
        when (node) {
            is HtmlText -> buffer.append(decodeHtmlEntities(node.text))
            is HtmlElement -> when (node.tag) {
                "br" -> buffer.append(BREAK_SENTINEL)
                "img" -> {
                    flush()
                    addImage(node, blocks)
                }

                "iframe" -> {
                    flush()
                    addVideo(node, blocks)
                }

                "script", "style" -> Unit
                else -> node.children.forEach { visit(it) }
            }
        }
    }

    element.children.forEach { visit(it) }
    flush()
}

/** Figures and the other containers that exist only to hold media. */
private fun extractMedia(element: HtmlElement, blocks: MutableList<DevotionBlock>) {
    fun visit(node: HtmlNode) {
        if (node !is HtmlElement) return
        when (node.tag) {
            "img" -> addImage(node, blocks)
            "iframe" -> addVideo(node, blocks)
            else -> node.children.forEach { visit(it) }
        }
    }

    element.children.forEach { visit(it) }
}

private fun addImage(image: HtmlElement, blocks: MutableList<DevotionBlock>) {
    var src = image.attributes["src"]?.trim().orEmpty()
    // Lazy-loading plugins park a placeholder in `src` (typically a 1×1 SVG) and keep the real image
    // in `data-src`. Only trust `src` when it points somewhere real.
    if (src.isEmpty() || src.startsWith("data:")) {
        src = image.attributes["data-src"]?.trim().orEmpty()
    }
    if (src.isEmpty() || src.startsWith("data:")) return
    blocks.add(DevotionImage(normalizeDevotionImageUrl(decodeHtmlEntities(src))))
}

private fun addVideo(frame: HtmlElement, blocks: MutableList<DevotionBlock>) {
    val src = frame.attributes["src"]?.trim().orEmpty()
    if (src.isEmpty()) return
    val decoded = decodeHtmlEntities(src)
    val id = parseYouTubeId(decoded)
    if (id != null) {
        blocks.add(DevotionVideo(id))
    } else {
        // SoundCloud and the other players: keep the embed instead of dropping it.
        blocks.add(DevotionEmbed(normalizeDevotionImageUrl(decoded)))
    }
}

/**
 * Flattens the deeply nested WordPress wrappers so logical sections come out as siblings: 安靜 →
 * 經文… → 觀畫. Left nested, 觀畫靈修 renders three levels deep and reads as broken.
 *
 * The loop keeps both of its jumps because both mean the block is already settled: a block that is
 * not a section is emitted untouched, and a container that did split is emitted as its prose plus its
 * own children. Only the section that is neither is rebuilt below, so the jump is what stops a block
 * being written twice.
 */
@Suppress("LoopWithTooManyJumpStatements")
private fun flattenSections(blocks: List<DevotionBlock>): List<DevotionBlock> {
    val result = mutableListOf<DevotionBlock>()
    for (block in blocks) {
        if (block !is DevotionSection) {
            result.add(block)
            continue
        }
        val children = flattenSections(block.blocks)
        if (isContainerSection(block.title)) {
            val childSections = children.filterIsInstance<DevotionSection>()
            if (childSections.isNotEmpty()) {
                result.add(DevotionSection(block.title, children.filter { it !is DevotionSection }))
                result.addAll(childSections)
                continue
            }
        }
        result.add(DevotionSection(block.title, children))
    }
    return result
}

/** Wrappers that only group other sections: they keep their own prose, not their children's. */
private fun isContainerSection(title: String): Boolean {
    val trimmed = title.trim()
    return trimmed == "安靜" ||
        trimmed == "安静" ||
        trimmed.startsWith("經文") ||
        trimmed.startsWith("经文")
}

/**
 * Removes duplicate image URLs, keeping the first occurrence — WordPress emits the same painting at
 * several sizes (`…-1024x768.jpg`, `…-300x200.jpg`) and the gallery was showing each of them.
 */
private fun deduplicateImages(blocks: List<DevotionBlock>): List<DevotionBlock> {
    val seen = mutableSetOf<String>()

    fun dedup(input: List<DevotionBlock>): List<DevotionBlock> {
        val out = mutableListOf<DevotionBlock>()
        for (block in input) {
            when (block) {
                is DevotionImage -> if (seen.add(imageKey(block.url))) out.add(block)
                is DevotionSection -> out.add(DevotionSection(block.title, dedup(block.blocks)))
                else -> out.add(block)
            }
        }
        return out
    }

    return dedup(blocks)
}

/** Strips the WordPress size suffix before the extension, so `-1024x768.jpg` and `.jpg` match. */
private fun imageKey(url: String): String {
    val queryIndex = url.indexOf('?')
    val base = if (queryIndex >= 0) url.substring(0, queryIndex) else url
    val query = if (queryIndex >= 0) url.substring(queryIndex) else ""
    return IMAGE_SIZE_SUFFIX.replace(base, "") + query
}

private val IMAGE_SIZE_SUFFIX = Regex("-\\d+x\\d+(?=\\.\\w+$)")
