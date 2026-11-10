package com.lesehaus.app.data.formats

/** Formatunabhängiges Inhaltsmodell eines Buchs. */
data class BookContent(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
)

data class Chapter(
    val title: String,
    val blocks: List<Block>,
)

enum class BlockKind { PARAGRAPH, H1, H2, H3, QUOTE, CENTER, PRE, IMAGE, RULE }

enum class InlineStyle { BOLD, ITALIC, UNDERLINE, STRIKE, SUP, SUB, CODE, SMALLCAPS }

data class InlineSpan(val start: Int, val end: Int, val style: InlineStyle)

data class Block(
    val kind: BlockKind,
    val text: String,
    val spans: List<InlineSpan> = emptyList(),
    /** Lokaler Dateipfad eines Bildes (nur bei [BlockKind.IMAGE]). */
    val imagePath: String? = null,
)

/** Metadaten, die beim Scannen aus einer Datei gelesen werden. */
data class BookMeta(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val coverBytes: ByteArray? = null,
)

/**
 * Baut Absätze mit Inline-Formatierung zusammen. Leerraum wird wie in HTML zusammengefasst.
 */
class ParagraphBuilder {
    private val sb = StringBuilder()
    private val spans = ArrayList<InlineSpan>()
    private val open = ArrayList<Pair<InlineStyle, Int>>()
    private var lastWasSpace = true

    val isBlank: Boolean get() = sb.isBlank()

    fun appendText(raw: String, preserveWhitespace: Boolean = false) {
        if (preserveWhitespace) {
            sb.append(raw)
            lastWasSpace = raw.lastOrNull()?.isWhitespace() ?: lastWasSpace
            return
        }
        for (c in raw) {
            if (c != '\u00A0' && c.isWhitespace()) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(c)
                lastWasSpace = false
            }
        }
    }

    fun lineBreak() {
        // Zeilenumbruch innerhalb eines Absatzes (z. B. <br/> in Gedichten)
        while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        if (sb.isNotEmpty()) sb.append('\n')
        lastWasSpace = true
    }

    fun push(style: InlineStyle) {
        open.add(style to sb.length)
    }

    fun pop(style: InlineStyle) {
        val idx = open.indexOfLast { it.first == style }
        if (idx < 0) return
        val (_, start) = open.removeAt(idx)
        if (sb.length > start) spans.add(InlineSpan(start, sb.length, style))
    }

    /** Liefert den fertigen Block und setzt den Builder zurück (offene Stile bleiben offen). */
    fun build(kind: BlockKind): Block? {
        // Trim mit Span-Korrektur
        var startTrim = 0
        while (startTrim < sb.length && sb[startTrim].isWhitespace()) startTrim++
        var end = sb.length
        while (end > startTrim && sb[end - 1].isWhitespace()) end--
        val text = sb.substring(startTrim, end)
        // Stile, die noch offen sind, bis zum Ende abschließen und für den nächsten Absatz neu öffnen
        val stillOpen = open.map { it.first }
        stillOpen.forEach { pop(it) }
        val result = if (text.isEmpty()) null else Block(
            kind = kind,
            text = text,
            spans = spans.mapNotNull {
                val s = (it.start - startTrim).coerceIn(0, text.length)
                val e = (it.end - startTrim).coerceIn(0, text.length)
                if (e > s) InlineSpan(s, e, it.style) else null
            },
        )
        sb.setLength(0)
        spans.clear()
        open.clear()
        lastWasSpace = true
        stillOpen.forEach { push(it) }
        return result
    }
}

/** Teilt sehr lange Kapitel an Absatzgrenzen, damit das Seitenlayout flüssig bleibt. */
fun splitLongChapters(chapters: List<Chapter>, maxChars: Int = 60_000): List<Chapter> {
    val out = ArrayList<Chapter>()
    for (ch in chapters) {
        val total = ch.blocks.sumOf { it.text.length }
        if (total <= maxChars) {
            out += ch
            continue
        }
        var part = ArrayList<Block>()
        var size = 0
        var n = 1
        for (b in ch.blocks) {
            if (size > 0 && size + b.text.length > maxChars) {
                out += Chapter(if (n == 1) ch.title else "${ch.title} (${n})", part)
                part = ArrayList()
                size = 0
                n++
            }
            // einzelne Riesenabsätze (z. B. Text ohne Umbrüche) zerteilen
            if (b.text.length > maxChars && b.kind == BlockKind.PARAGRAPH) {
                var i = 0
                while (i < b.text.length) {
                    var e = (i + maxChars / 4).coerceAtMost(b.text.length)
                    if (e < b.text.length) {
                        val dot = b.text.lastIndexOf(". ", e)
                        if (dot > i + 100) e = dot + 1
                    }
                    part.add(Block(BlockKind.PARAGRAPH, b.text.substring(i, e).trim()))
                    size += e - i
                    i = e
                }
            } else {
                part.add(b)
                size += b.text.length
            }
        }
        if (part.isNotEmpty()) out += Chapter(if (n == 1) ch.title else "${ch.title} (${n})", part)
    }
    return out
}
