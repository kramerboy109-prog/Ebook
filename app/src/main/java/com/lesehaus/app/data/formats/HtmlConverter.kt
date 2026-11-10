package com.lesehaus.app.data.formats

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Wandelt HTML/XHTML (EPUB-Kapitel, HTML-Dateien, FB2) in Blöcke um.
 * [resolveImage] bekommt das src-Attribut und liefert einen lokalen Pfad (oder null).
 */
class HtmlConverter(
    private val resolveImage: (String) -> String? = { null },
) {
    private val blocks = ArrayList<Block>()
    private val pb = ParagraphBuilder()
    private val kindStack = ArrayList<BlockKind>()
    private var preDepth = 0

    private val blockTags = setOf(
        "p", "div", "section", "article", "aside", "header", "footer", "main", "nav", "figure", "figcaption",
        "blockquote", "li", "ul", "ol", "dl", "dt", "dd", "table", "tr", "td", "th", "caption", "pre",
        "h1", "h2", "h3", "h4", "h5", "h6", "body", "center", "address", "hgroup", "details", "summary",
        // FB2
        "section", "title", "subtitle", "epigraph", "poem", "stanza", "v", "cite", "text-author", "annotation", "empty-line",
    )
    private val skipTags = setOf("head", "script", "style", "noscript", "svg:title", "binary", "description", "nav")

    fun convert(root: Element): List<Block> {
        walk(root)
        flush()
        return blocks.toList()
    }

    private fun currentKind(): BlockKind = kindStack.lastOrNull() ?: BlockKind.PARAGRAPH

    private fun flush() {
        pb.build(currentKind())?.let { blocks.add(it) }
    }

    private fun kindFor(el: Element): BlockKind? {
        val tag = el.normalName()
        val cls = el.className().lowercase()
        return when {
            tag == "h1" -> BlockKind.H1
            tag == "h2" -> BlockKind.H2
            tag in setOf("h3", "h4", "h5", "h6", "subtitle") -> BlockKind.H3
            tag == "title" -> BlockKind.H2
            tag == "blockquote" || tag == "epigraph" || tag == "cite" || tag == "poem" -> BlockKind.QUOTE
            tag == "pre" -> BlockKind.PRE
            tag == "center" || tag == "text-author" || cls.contains("center") || el.attr("style").contains("text-align: center") || el.attr("style").contains("text-align:center") -> BlockKind.CENTER
            else -> null
        }
    }

    private fun walk(node: Node) {
        when (node) {
            is TextNode -> pb.appendText(node.wholeText, preserveWhitespace = preDepth > 0)
            is Element -> {
                val tag = node.normalName()
                if (tag in skipTags && node.parent() != null) return
                when (tag) {
                    "br" -> { pb.lineBreak(); return }
                    "hr" -> {
                        flush()
                        blocks.add(Block(BlockKind.RULE, "⁂"))
                        return
                    }
                    "empty-line" -> { flush(); return }
                    "img", "image" -> {
                        val src = node.attr("src").ifEmpty { node.attr("xlink:href") }.ifEmpty { node.attr("l:href") }.ifEmpty { node.attr("href") }
                        val path = if (src.isNotEmpty()) resolveImage(src) else null
                        if (path != null) {
                            flush()
                            blocks.add(Block(BlockKind.IMAGE, "￼", imagePath = path))
                        } else {
                            val alt = node.attr("alt")
                            if (alt.isNotBlank()) pb.appendText(alt)
                        }
                        return
                    }
                    "sup" -> return styled(node, InlineStyle.SUP)
                    "sub" -> return styled(node, InlineStyle.SUB)
                    "b", "strong" -> return styled(node, InlineStyle.BOLD)
                    "i", "em", "emphasis", "var", "dfn" -> return styled(node, InlineStyle.ITALIC)
                    "u", "ins" -> return styled(node, InlineStyle.UNDERLINE)
                    "s", "strike", "del", "strikethrough" -> return styled(node, InlineStyle.STRIKE)
                    "code", "kbd", "samp", "tt" -> return styled(node, InlineStyle.CODE)
                }
                if (tag == "span" || tag == "a" || tag == "font" || tag == "small" || tag == "big" || tag == "abbr" || tag == "q") {
                    val style = node.attr("style").lowercase()
                    val cls = node.className().lowercase()
                    var pushed: InlineStyle? = null
                    if (style.contains("italic") || cls.contains("italic") || cls == "i" || cls.contains("emph")) pushed = InlineStyle.ITALIC
                    else if (style.contains("bold") || cls.contains("bold")) pushed = InlineStyle.BOLD
                    else if (style.contains("small-caps") || cls.contains("smallcap") || cls.contains("small-caps")) pushed = InlineStyle.SMALLCAPS
                    if (tag == "q") pb.appendText("„")
                    if (pushed != null) pb.push(pushed)
                    node.childNodes().forEach { walk(it) }
                    if (pushed != null) pb.pop(pushed)
                    if (tag == "q") pb.appendText("“")
                    return
                }
                if (tag in blockTags) {
                    flush()
                    val k = kindFor(node)
                    if (k != null) kindStack.add(k)
                    if (tag == "pre") preDepth++
                    if (tag == "li") pb.appendText("•  ")
                    node.childNodes().forEach { walk(it) }
                    flush()
                    if (tag == "pre") preDepth--
                    if (k != null) kindStack.removeAt(kindStack.lastIndex)
                    return
                }
                node.childNodes().forEach { walk(it) }
            }
            else -> Unit
        }
    }

    private fun styled(el: Element, style: InlineStyle) {
        pb.push(style)
        el.childNodes().forEach { walk(it) }
        pb.pop(style)
    }
}
