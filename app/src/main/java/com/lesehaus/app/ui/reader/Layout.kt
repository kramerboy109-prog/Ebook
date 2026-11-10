package com.lesehaus.app.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lesehaus.app.data.ReaderSettings
import com.lesehaus.app.data.TextAlignMode
import com.lesehaus.app.data.formats.BlockKind
import com.lesehaus.app.data.formats.Chapter
import com.lesehaus.app.data.formats.InlineSpan
import com.lesehaus.app.data.formats.InlineStyle
import com.lesehaus.app.ui.theme.Fonts

const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
private const val SPACER = '​'

data class PBlock(
    val start: Int,
    val end: Int,
    val kind: BlockKind,
    val imagePath: String? = null,
    val spacerEm: Float = 0f,
    val firstAfterHeading: Boolean = false,
    val imgW: Int = 0,
    val imgH: Int = 0,
)

/** Kapitel als durchgehender Text mit Blockgrenzen und Inline-Stilen (absolute Offsets). */
class PreparedChapter(
    val index: Int,
    val title: String,
    val text: String,
    val blocks: List<PBlock>,
    val spans: List<InlineSpan>,
) {
    /** Lesbarer Text eines Bereichs (ohne Steuerzeichen). */
    fun plain(start: Int, end: Int): String =
        text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
            .replace(SPACER.toString(), "\n").replace("￼", "").replace(Regex("\n{2,}"), "\n").trim()

    fun blockAt(offset: Int): PBlock? = blocks.firstOrNull { offset >= it.start && offset < it.end }
}

/**
 * Eine Seite: [start]..[end] ist der sichtbare Bereich. Gerendert wird bis [renderEnd]
 * (Ende des Absatzes) und auf [heightPx] beschnitten – so bleiben Zeilenumbrüche und
 * Blocksatz exakt wie beim Messen.
 */
data class PageRef(val chapter: Int, val start: Int, val end: Int, val renderEnd: Int = end, val heightPx: Int = 0)

fun prepareChapter(index: Int, ch: Chapter, s: ReaderSettings): PreparedChapter {
    val sb = StringBuilder()
    val blocks = ArrayList<PBlock>()
    val spans = ArrayList<InlineSpan>()
    var prevKind: BlockKind? = null
    for (b in ch.blocks) {
        if (b.kind == BlockKind.IMAGE && !s.showImages) continue
        val isHeading = b.kind == BlockKind.H1 || b.kind == BlockKind.H2 || b.kind == BlockKind.H3
        val prevHeading = prevKind == BlockKind.H1 || prevKind == BlockKind.H2 || prevKind == BlockKind.H3
        // Abstand vor dem Block
        val spacing = when {
            prevKind == null -> 0f
            isHeading -> if (b.kind == BlockKind.H3) 0.9f else 1.4f
            prevHeading -> 0.7f
            b.kind == BlockKind.IMAGE || prevKind == BlockKind.IMAGE -> 0.6f
            b.kind == BlockKind.QUOTE || prevKind == BlockKind.QUOTE -> maxOf(0.5f, s.paragraphSpacing)
            b.kind == BlockKind.RULE || prevKind == BlockKind.RULE -> 0.8f
            else -> s.paragraphSpacing
        }
        if (spacing > 0.01f) {
            blocks.add(PBlock(sb.length, sb.length + 1, BlockKind.PARAGRAPH, spacerEm = spacing))
            sb.append(SPACER)
        }
        val start = sb.length
        if (b.kind == BlockKind.IMAGE) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(b.imagePath, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) continue
            sb.append('￼')
            blocks.add(PBlock(start, sb.length, BlockKind.IMAGE, imagePath = b.imagePath, imgW = opts.outWidth, imgH = opts.outHeight))
        } else {
            sb.append(b.text)
            b.spans.forEach { spans.add(InlineSpan(it.start + start, it.end + start, it.style)) }
            blocks.add(PBlock(start, sb.length, b.kind, firstAfterHeading = prevKind == null || prevHeading || prevKind == BlockKind.IMAGE || prevKind == BlockKind.RULE))
        }
        prevKind = b.kind
    }
    if (sb.isEmpty()) {
        sb.append("·")
        blocks.add(PBlock(0, 1, BlockKind.CENTER))
    }
    return PreparedChapter(index, ch.title, sb.toString(), blocks, spans)
}

/** Alles, was zum Setzen einer Seite nötig ist. */
class TypeSetter(
    val settings: ReaderSettings,
    val fontFamily: FontFamily,
    val textColor: Color,
    val accent: Color,
    val density: Density,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
) {
    val baseStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = settings.fontSize.sp,
        fontWeight = FontWeight(settings.fontWeight),
        lineHeight = settings.lineHeight.em,
        letterSpacing = (settings.letterSpacing).em,
        color = textColor,
        textAlign = if (settings.align == TextAlignMode.JUSTIFY) TextAlign.Justify else TextAlign.Start,
        hyphens = if (settings.hyphenation) Hyphens.Auto else Hyphens.None,
        lineBreak = LineBreak.Simple,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontSynthesis = FontSynthesis.All,
    )

    private fun pxToSp(px: Float): TextUnit = with(density) { px.toSp() }

    fun imageSizePx(b: PBlock): Pair<Float, Float> {
        val maxW = pageWidthPx.toFloat()
        val maxH = pageHeightPx * 0.86f
        var w = minOf(maxW, b.imgW * density.density * 1.6f)
        var h = w * b.imgH / b.imgW
        if (h > maxH) { h = maxH; w = h * b.imgW / b.imgH }
        return w to h
    }

    fun placeholder(b: PBlock): Placeholder {
        val (w, h) = imageSizePx(b)
        return Placeholder(pxToSp(w), pxToSp(h), PlaceholderVerticalAlign.TextCenter)
    }

    private fun paragraphStyle(b: PBlock, continuation: Boolean): ParagraphStyle {
        if (b.spacerEm > 0f) return ParagraphStyle(
            lineHeight = b.spacerEm.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
        return when (b.kind) {
            BlockKind.H1 -> ParagraphStyle(textAlign = TextAlign.Center, lineHeight = 1.25.em, hyphens = Hyphens.None)
            BlockKind.H2 -> ParagraphStyle(textAlign = TextAlign.Center, lineHeight = 1.3.em, hyphens = Hyphens.None)
            BlockKind.H3 -> ParagraphStyle(textAlign = TextAlign.Start, lineHeight = 1.3.em, hyphens = Hyphens.None)
            BlockKind.QUOTE -> ParagraphStyle(textIndent = TextIndent(1.4.em, 1.4.em))
            BlockKind.CENTER, BlockKind.RULE, BlockKind.IMAGE -> ParagraphStyle(textAlign = TextAlign.Center)
            BlockKind.PRE -> ParagraphStyle(textAlign = TextAlign.Start, hyphens = Hyphens.None, lineHeight = 1.35.em)
            BlockKind.PARAGRAPH -> {
                val indent = settings.indent && !continuation && !b.firstAfterHeading
                ParagraphStyle(textIndent = if (indent) TextIndent(firstLine = 1.5.em) else TextIndent.None)
            }
        }
    }

    private fun blockSpanStyle(b: PBlock): SpanStyle? = when (b.kind) {
        BlockKind.H1 -> SpanStyle(fontSize = (settings.fontSize * 1.6f).sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.Playfair)
        BlockKind.H2 -> SpanStyle(fontSize = (settings.fontSize * 1.35f).sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.Playfair)
        BlockKind.H3 -> SpanStyle(fontSize = (settings.fontSize * 1.12f).sp, fontWeight = FontWeight.Bold)
        BlockKind.QUOTE -> SpanStyle(fontStyle = FontStyle.Italic)
        BlockKind.PRE -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (settings.fontSize * 0.82f).sp)
        BlockKind.RULE -> SpanStyle(color = accent, letterSpacing = 0.5.em)
        else -> null
    }

    private fun inlineStyle(st: InlineStyle): SpanStyle = when (st) {
        InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        InlineStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        InlineStyle.SUP -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
        InlineStyle.SUB -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
        InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.88.em)
        InlineStyle.SMALLCAPS -> SpanStyle(fontFeatureSettings = "smcp", letterSpacing = 0.04.em)
    }

    /**
     * Baut den formatierten Text für einen Bereich eines Kapitels.
     * [decorations] sind zusätzliche Hervorhebungen (Markierungen, Auswahl, Suche, Vorlesen).
     */
    fun build(ch: PreparedChapter, start: Int, end: Int, decorations: List<Decoration> = emptyList()): AnnotatedString {
        val s = start.coerceIn(0, ch.text.length)
        val e = end.coerceIn(s, ch.text.length)
        val b = AnnotatedString.Builder(ch.text.substring(s, e))
        for (blk in ch.blocks) {
            if (blk.end <= s || blk.start >= e) continue
            val bs = maxOf(blk.start, s) - s
            val be = minOf(blk.end, e) - s
            b.addStyle(paragraphStyle(blk, continuation = blk.start < s), bs, be)
            blockSpanStyle(blk)?.let { b.addStyle(it, bs, be) }
            if (blk.kind == BlockKind.IMAGE) b.addStringAnnotation(INLINE_CONTENT_TAG, "img:${blk.start}", bs, be)
        }
        for (sp in ch.spans) {
            if (sp.end <= s || sp.start >= e) continue
            b.addStyle(inlineStyle(sp.style), maxOf(sp.start, s) - s, minOf(sp.end, e) - s)
        }
        for (d in decorations) {
            if (d.end <= s || d.start >= e) continue
            b.addStyle(d.style, maxOf(d.start, s) - s, minOf(d.end, e) - s)
        }
        return b.toAnnotatedString()
    }

    fun placeholders(ch: PreparedChapter, start: Int, end: Int): List<AnnotatedString.Range<Placeholder>> =
        ch.blocks.filter { it.kind == BlockKind.IMAGE && it.start >= start && it.end <= end }
            .map { AnnotatedString.Range(placeholder(it), it.start - start, it.end - start) }

    /** Teilt ein Kapitel in Seiten. Liefert Seiten-Startoffsets. */
    fun paginate(measurer: TextMeasurer, ch: PreparedChapter): List<PageRef> {
        val text = build(ch, 0, ch.text.length)
        val layout = measurer.measure(
            text,
            style = baseStyle,
            constraints = Constraints(maxWidth = pageWidthPx),
            placeholders = placeholders(ch, 0, ch.text.length),
            density = density,
            skipCache = true,
        )
        val pages = ArrayList<PageRef>()
        val h = pageHeightPx - 2
        var pageStartLine = 0
        var pageTop = layout.getLineTop(0)
        fun isSpacerLine(line: Int): Boolean {
            val ls = layout.getLineStart(line)
            return ch.text.getOrNull(ls) == SPACER && layout.getLineEnd(line) - ls <= 1
        }
        fun renderEndFor(end: Int): Int {
            if (end >= ch.text.length) return ch.text.length
            val blk = ch.blocks.firstOrNull { end - 1 >= it.start && end - 1 < it.end } ?: return end
            return maxOf(end, blk.end)
        }
        var line = 0
        while (line < layout.lineCount) {
            val bottom = layout.getLineBottom(line)
            if (bottom - pageTop > h && line > pageStartLine) {
                val end = layout.getLineStart(line)
                pages.add(PageRef(ch.index, layout.getLineStart(pageStartLine), end, renderEndFor(end), kotlin.math.ceil(layout.getLineBottom(line - 1) - pageTop).toInt()))
                var next = line
                while (next < layout.lineCount - 1 && isSpacerLine(next)) next++
                pageStartLine = next
                pageTop = layout.getLineTop(next)
                line = next
                continue
            }
            line++
        }
        pages.add(PageRef(ch.index, layout.getLineStart(pageStartLine), ch.text.length, ch.text.length, kotlin.math.ceil(layout.getLineBottom(layout.lineCount - 1) - pageTop).toInt()))
        return pages
    }
}

data class Decoration(val start: Int, val end: Int, val style: SpanStyle)
