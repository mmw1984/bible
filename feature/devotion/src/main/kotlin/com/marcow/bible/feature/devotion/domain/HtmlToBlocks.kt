package com.marcow.bible.feature.devotion.domain

import org.jsoup.parser.Parser
import org.jsoup.parser.Token

/**
 * WordPress post HTML → [DevotionBlock]s, ported from `parseDevotionBlocks` and the tolerant tree
 * it walks in `legacy/flutter/lib/devotion_content.dart`.
 *
 * The tree is built from Jsoup's tokenizer rather than Jsoup's own tree builder, which is the whole
 * point of the file. `Jsoup.parse` would apply HTML5's implied end tags — a `<div>` closes an open
 * `<p>`, `<li>` closes `<li>` — and the Dart build never did, because it nested tags by position and
 * ignored close tags that matched nothing. Posts are hand-written markup wrapped in five layers of
 * `<div>`; the difference between those two readings is the difference between one paragraph and two,
 * and between a 觀畫 section being rendered at all or being swallowed into a paragraph. Tokenizing
 * with Jsoup and *building* the tree with the Dart stack keeps the malformed-markup tolerance of the
 * original while taking Jsoup's quote-aware attribute scanning off our hands.
 */
private const val DOCUMENT_TAG = "#document"

/** `_voidElements`: never pushed on the stack, so `<img>` cannot swallow its siblings. */
private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
    "meta", "param", "source", "track", "wbr",
)

/** Raw-text elements, whose body Jsoup hands over as one character run and the parser drops. */
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

internal class HtmlElement(
    val tag: String,
    val attributes: Map<String, String> = emptyMap(),
) : HtmlNode {
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
    val tokens = Parser.tokenize(html)
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        index++
        if (token.isCharacter) {
            stack.last().children.add(HtmlText(token.data()))
        } else if (token.isEndTag) {
            closeTag(stack, token.normalName())
        } else if (token.isStartTag) {
            val element = openTag(stack, token)
            if (element.tag in RAW_TEXT_ELEMENTS) index = skipRawText(tokens, index, element.tag)
        }
    }
    return document
}

/**
 * Adds an element under the current node, and leaves it open unless it cannot hold anything.
 *
 * Raw-text elements are not left on the stack either: the Dart scanner jumped past `</script` without
 * unwinding, so what followed a script stayed a sibling of it rather than a child.
 */
private fun openTag(stack: ArrayDeque<HtmlElement>, token: Token): HtmlElement {
    val element = HtmlElement(token.normalName(), readAttributes(token))
    stack.last().children.add(element)
    val staysOpen = !token.isSelfClosing && element.tag !in VOID_ELEMENTS && element.tag !in RAW_TEXT_ELEMENTS
    if (staysOpen) stack.addLast(element)
    return element
}

/** Steps over a `<script>`/`<style>` body, which the tokeniser hands over as one character run. */
private fun skipRawText(tokens: List<Token>, from: Int, tag: String): Int {
    var index = from
    while (index < tokens.size) {
        val token = tokens[index]
        index++
        if (token.isEndTag && token.normalName() == tag) return index
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

/** Attribute names lowercased, first occurrence wins — Word-pasted markup repeats attributes. */
private fun readAttributes(token: Token): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    // Jsoup's `Attributes` is a `Map<String, String>` whose `iterator()` it re-declares for its own
    // `Attribute` type, so it is read through the map view to get plain name/value pairs.
    val tokenAttributes: Map<String, String> = token.attributes()
    for ((rawName, rawValue) in tokenAttributes) {
        val name = rawName.lowercase()
        if (name !in attributes) attributes[name] = rawValue
    }
    return attributes
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
 */
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
    return trimmed == "安靜" || trimmed == "安静" ||
        trimmed.startsWith("經文") || trimmed.startsWith("经文")
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
