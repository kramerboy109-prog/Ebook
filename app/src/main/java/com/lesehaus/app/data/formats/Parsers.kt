package com.lesehaus.app.data.formats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipFile

object BookParsers {

    fun formatOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    /** Kopiert die Datei (falls nötig) in den Cache, damit z. B. ZIP-Zugriffe wahlfrei möglich sind. */
    fun localCopy(context: Context, uri: Uri, ext: String): File {
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val f = File(dir, hash(uri.toString()) + "." + ext)
        val size = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        }.getOrDefault(-1L)
        if (f.exists() && size > 0 && f.length() == size) return f
        context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { input.copyTo(it) }
        } ?: error("Datei kann nicht geöffnet werden")
        return f
    }

    fun hash(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei kann nicht geöffnet werden")

    fun readMeta(context: Context, uri: Uri, fileName: String): BookMeta = runCatching {
        when (formatOf(fileName)) {
            "epub" -> Epub.meta(localCopy(context, uri, "epub"))
            "fb2" -> Fb2.meta(readBytes(context, uri))
            "pdf" -> Pdf.meta(context, uri)
            "docx" -> Office.docxMeta(localCopy(context, uri, "docx"))
            "odt" -> Office.odtMeta(localCopy(context, uri, "odt"))
            "html", "htm", "xhtml" -> {
                val doc = Jsoup.parse(TextDecode.decode(readBytes(context, uri)))
                BookMeta(
                    title = doc.title().takeIf { it.isNotBlank() },
                    author = doc.selectFirst("meta[name=author]")?.attr("content")?.takeIf { it.isNotBlank() },
                )
            }
            else -> BookMeta()
        }
    }.getOrDefault(BookMeta())

    fun parse(context: Context, uri: Uri, fileName: String, fallbackTitle: String, fallbackAuthor: String): BookContent {
        val fmt = formatOf(fileName)
        val imageDir = File(context.cacheDir, "bookimg/" + hash(uri.toString())).apply { mkdirs() }
        val content = when (fmt) {
            "epub" -> Epub.parse(localCopy(context, uri, "epub"), imageDir)
            "fb2" -> Fb2.parse(readBytes(context, uri), imageDir)
            "txt" -> PlainText.parse(TextDecode.decode(readBytes(context, uri)))
            "md" -> Markdown.parse(TextDecode.decode(readBytes(context, uri)))
            "rtf" -> PlainText.parse(Rtf.toText(readBytes(context, uri)))
            "html", "htm", "xhtml" -> HtmlBook.parse(TextDecode.decode(readBytes(context, uri)))
            "docx" -> Office.docx(localCopy(context, uri, "docx"))
            "odt" -> Office.odt(localCopy(context, uri, "odt"))
            "pdf" -> Pdf.text(context, uri)
            else -> PlainText.parse(TextDecode.decode(readBytes(context, uri)))
        }
        val chapters = splitLongChapters(content.chapters.filter { ch -> ch.blocks.isNotEmpty() })
            .ifEmpty { listOf(Chapter("Leer", listOf(Block(BlockKind.CENTER, "Dieses Buch enthält keinen lesbaren Text.")))) }
        return BookContent(
            title = content.title.ifBlank { fallbackTitle },
            author = content.author.ifBlank { fallbackAuthor },
            chapters = chapters,
        )
    }
}

/** Robuste Zeichensatz-Erkennung: BOM, UTF-8, sonst Windows-1252. */
object TextDecode {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}

private fun cleanTitle(s: String?): String? = s?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

// ----------------------------------------------------------------------------------------------
// EPUB
// ----------------------------------------------------------------------------------------------
object Epub {
    private class Pkg(
        val opfPath: String,
        val opf: Document,
        val manifest: Map<String, Pair<String, String>>, // id -> (href, mediaType)
        val spine: List<String>,
    )

    private fun dirOf(path: String) = if (path.contains('/')) path.substringBeforeLast('/') + "/" else ""

    fun resolve(base: String, href: String): String {
        val clean = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
        val parts = ArrayList<String>()
        (dirOf(base) + clean).split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(it)
            }
        }
        return parts.joinToString("/")
    }

    private fun entryText(zip: ZipFile, path: String): String? {
        val e = zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, true) } ?: return null
        return zip.getInputStream(e).use { TextDecode.decode(it.readBytes()) }
    }

    private fun entryBytes(zip: ZipFile, path: String): ByteArray? {
        val e = zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, true) } ?: return null
        return zip.getInputStream(e).use { it.readBytes() }
    }

    private fun pkg(zip: ZipFile): Pkg {
        val container = entryText(zip, "META-INF/container.xml")
        val opfPath = container?.let {
            Jsoup.parse(it, "", Parser.xmlParser()).selectFirst("rootfile")?.attr("full-path")
        } ?: zip.entries().asSequence().first { it.name.endsWith(".opf", true) }.name
        val opf = Jsoup.parse(entryText(zip, opfPath) ?: "", "", Parser.xmlParser())
        val manifest = LinkedHashMap<String, Pair<String, String>>()
        opf.select("manifest > item, item").forEach {
            manifest[it.attr("id")] = resolve(opfPath, it.attr("href")) to it.attr("media-type")
        }
        val spine = opf.select("spine > itemref, itemref").map { it.attr("idref") }
        return Pkg(opfPath, opf, manifest, spine)
    }

    private fun Document.meta(name: String): String? =
        cleanTitle(selectFirst("dc|$name")?.text() ?: select("*").firstOrNull { it.tagName().equals("dc:$name", true) }?.text())

    fun meta(file: File): BookMeta = ZipFile(file).use { zip ->
        val p = pkg(zip)
        val desc = p.opf.meta("description")?.let { Jsoup.parse(it).text() }
        BookMeta(
            title = p.opf.meta("title"),
            author = p.opf.select("*").filter { it.tagName().equals("dc:creator", true) }.joinToString(", ") { it.text().trim() }.takeIf { it.isNotBlank() },
            description = desc,
            language = p.opf.meta("language"),
            publisher = p.opf.meta("publisher"),
            coverBytes = coverHref(p, zip)?.let { entryBytes(zip, it) },
        )
    }

    private fun coverHref(p: Pkg, zip: ZipFile): String? {
        p.opf.select("item[properties~=cover-image]").firstOrNull()?.let { return resolve(p.opfPath, it.attr("href")) }
        val coverId = p.opf.select("meta[name=cover]").firstOrNull()?.attr("content")
        if (!coverId.isNullOrEmpty()) {
            p.manifest[coverId]?.let { if (it.second.startsWith("image")) return it.first }
            // manche Bücher referenzieren direkt den Dateinamen
            p.manifest.values.firstOrNull { it.first.endsWith(coverId) && it.second.startsWith("image") }?.let { return it.first }
        }
        p.manifest.entries.firstOrNull { it.key.contains("cover", true) && it.value.second.startsWith("image") }?.let { return it.value.first }
        p.manifest.values.firstOrNull { it.first.contains("cover", true) && it.second.startsWith("image") }?.let { return it.first }
        // erstes Bild der ersten Seite
        val first = p.spine.firstOrNull()?.let { p.manifest[it]?.first } ?: return null
        val html = entryText(zip, first) ?: return null
        val doc = Jsoup.parse(html)
        val src = doc.selectFirst("img")?.attr("src") ?: doc.select("image").firstOrNull()?.let { it.attr("xlink:href").ifEmpty { it.attr("href") } }
        return src?.takeIf { it.isNotEmpty() }?.let { resolve(first, it) }
    }

    private fun tocTitles(p: Pkg, zip: ZipFile): Map<String, String> {
        val map = HashMap<String, String>()
        // EPUB 3 nav
        p.opf.select("item[properties~=nav]").firstOrNull()?.let { navItem ->
            val navPath = resolve(p.opfPath, navItem.attr("href"))
            entryText(zip, navPath)?.let { html ->
                Jsoup.parse(html).select("nav a[href]").forEach { a ->
                    val target = resolve(navPath, a.attr("href"))
                    if (!map.containsKey(target)) cleanTitle(a.text())?.let { map[target] = it }
                }
            }
        }
        // EPUB 2 NCX
        if (map.isEmpty()) {
            val ncx = p.manifest.values.firstOrNull { it.second.contains("ncx") }?.first
            if (ncx != null) entryText(zip, ncx)?.let { xml ->
                Jsoup.parse(xml, "", Parser.xmlParser()).select("navPoint").forEach { np ->
                    val src = np.selectFirst("content")?.attr("src") ?: return@forEach
                    val target = resolve(ncx, src)
                    if (!map.containsKey(target)) cleanTitle(np.selectFirst("navLabel")?.text())?.let { map[target] = it }
                }
            }
        }
        return map
    }

    fun parse(file: File, imageDir: File): BookContent = ZipFile(file).use { zip ->
        val p = pkg(zip)
        val toc = tocTitles(p, zip)
        val chapters = ArrayList<Chapter>()
        p.spine.forEachIndexed { idx, idref ->
            val (href, media) = p.manifest[idref] ?: return@forEachIndexed
            if (!(media.contains("html") || media.contains("xml") || href.endsWith("htm", true) || href.endsWith("html", true))) return@forEachIndexed
            val html = entryText(zip, href) ?: return@forEachIndexed
            val doc = Jsoup.parse(html)
            val converter = HtmlConverter { src ->
                val path = resolve(href, src)
                val out = File(imageDir, BookParsers.hash(path) + "." + path.substringAfterLast('.', "img"))
                if (!out.exists()) {
                    val bytes = entryBytes(zip, path) ?: return@HtmlConverter null
                    out.writeBytes(bytes)
                }
                out.absolutePath
            }
            val blocks = converter.convert(doc.body())
            if (blocks.isEmpty()) return@forEachIndexed
            val title = toc[href]
                ?: blocks.firstOrNull { it.kind == BlockKind.H1 || it.kind == BlockKind.H2 || it.kind == BlockKind.H3 }?.text?.take(80)
                ?: cleanTitle(doc.title())
                ?: "Abschnitt ${idx + 1}"
            chapters.add(Chapter(title, blocks))
        }
        BookContent(p.opf.meta("title") ?: "", p.opf.select("*").filter { it.tagName().equals("dc:creator", true) }.joinToString(", ") { it.text().trim() }, chapters)
    }
}

// ----------------------------------------------------------------------------------------------
// FB2
// ----------------------------------------------------------------------------------------------
object Fb2 {
    private fun doc(bytes: ByteArray): Document {
        // FB2 deklariert die Kodierung im XML-Header
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val enc = Regex("encoding=\"([^\"]+)\"").find(head)?.groupValues?.get(1)
        val text = runCatching { if (enc != null) String(bytes, Charset.forName(enc)) else TextDecode.decode(bytes) }
            .getOrElse { TextDecode.decode(bytes) }
        return Jsoup.parse(text, "", Parser.xmlParser())
    }

    private fun author(d: Document): String? = d.select("title-info > author").joinToString(", ") { a ->
        listOf("first-name", "middle-name", "last-name").mapNotNull { a.selectFirst(it)?.text() }.joinToString(" ").ifBlank { a.selectFirst("nickname")?.text() ?: "" }
    }.takeIf { it.isNotBlank() }

    private fun binary(d: Document, href: String): ByteArray? {
        val id = href.removePrefix("#")
        val bin = d.select("binary").firstOrNull { it.attr("id") == id } ?: return null
        return runCatching { Base64.decode(bin.text(), Base64.DEFAULT) }.getOrNull()
    }

    private fun hrefOf(el: Element): String =
        el.attributes().firstOrNull { it.key.endsWith("href") }?.value ?: ""

    fun meta(bytes: ByteArray): BookMeta {
        val d = doc(bytes)
        val coverHref = d.selectFirst("title-info > coverpage > image")?.let { hrefOf(it) }
        return BookMeta(
            title = cleanTitle(d.selectFirst("title-info > book-title")?.text()),
            author = author(d),
            description = cleanTitle(d.selectFirst("title-info > annotation")?.text()),
            language = cleanTitle(d.selectFirst("title-info > lang")?.text()),
            coverBytes = coverHref?.let { binary(d, it) },
        )
    }

    fun parse(bytes: ByteArray, imageDir: File): BookContent {
        val d = doc(bytes)
        val resolver: (String) -> String? = { href ->
            val out = File(imageDir, BookParsers.hash(href) + ".img")
            if (out.exists()) out.absolutePath else binary(d, href)?.let { out.writeBytes(it); out.absolutePath }
        }
        // Bilder in FB2 nutzen l:href → in src umschreiben, damit der Konverter sie findet
        d.select("image").forEach { it.attr("src", hrefOf(it)) }
        val chapters = ArrayList<Chapter>()
        val bodies = d.select("FictionBook > body, body").filter { it.attr("name") != "notes" }
        for (body in bodies) {
            val sections = body.children().filter { it.normalName() == "section" }
            if (sections.isEmpty()) {
                val blocks = HtmlConverter(resolver).convert(body)
                if (blocks.isNotEmpty()) chapters.add(Chapter("Text", blocks))
                continue
            }
            // Einleitende Elemente vor den Sektionen (Titel, Epigraph)
            val intro = body.children().filter { it.normalName() != "section" }
            if (intro.isNotEmpty()) {
                val wrapper = Element("div")
                intro.forEach { wrapper.appendChild(it.clone()) }
                val blocks = HtmlConverter(resolver).convert(wrapper)
                if (blocks.isNotEmpty()) chapters.add(Chapter(blocks.first().text.take(60), blocks))
            }
            sections.forEachIndexed { i, s ->
                val blocks = HtmlConverter(resolver).convert(s)
                if (blocks.isNotEmpty()) {
                    val title = cleanTitle(s.selectFirst("title")?.text()) ?: "Kapitel ${i + 1}"
                    chapters.add(Chapter(title.take(80), blocks))
                }
            }
        }
        return BookContent(cleanTitle(d.selectFirst("title-info > book-title")?.text()) ?: "", author(d) ?: "", chapters)
    }
}

// ----------------------------------------------------------------------------------------------
// Einfacher Text / Markdown / HTML / RTF
// ----------------------------------------------------------------------------------------------
object PlainText {
    private val chapterRegex = Regex(
        "^\\s*((kapitel|chapter|teil|buch|book|part|prolog|prologue|epilog|epilogue|vorwort|nachwort|einleitung|anhang)\\b.{0,60}|[IVXLC]{1,7}\\.?|\\d{1,3}\\.?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): BookContent {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val blankLines = lines.count { it.isBlank() }
        val lineMode = blankLines < lines.size / 12 // jede Zeile ist ein Absatz
        val paragraphs = ArrayList<String>()
        if (lineMode) {
            lines.forEach { if (it.isNotBlank()) paragraphs.add(it.trim()) }
        } else {
            val cur = StringBuilder()
            for (l in lines) {
                if (l.isBlank()) {
                    if (cur.isNotEmpty()) { paragraphs.add(cur.toString().trim()); cur.setLength(0) }
                } else {
                    if (cur.isNotEmpty()) cur.append(' ')
                    cur.append(l.trim())
                }
            }
            if (cur.isNotEmpty()) paragraphs.add(cur.toString().trim())
        }
        val chapters = ArrayList<Chapter>()
        var title = "Anfang"
        var blocks = ArrayList<Block>()
        var headings = 0
        for (p in paragraphs) {
            if (p.length < 70 && chapterRegex.matches(p)) {
                headings++
                if (blocks.isNotEmpty()) chapters.add(Chapter(title, blocks))
                title = p
                blocks = arrayListOf(Block(BlockKind.H2, p))
            } else {
                blocks.add(Block(BlockKind.PARAGRAPH, p))
            }
        }
        if (blocks.isNotEmpty()) chapters.add(Chapter(title, blocks))
        if (headings == 0) {
            // ohne Kapitelstruktur in handliche Abschnitte teilen
            val all = chapters.flatMap { it.blocks }
            val out = ArrayList<Chapter>()
            var part = ArrayList<Block>()
            var size = 0
            for (b in all) {
                part.add(b); size += b.text.length
                if (size > 18_000) { out.add(Chapter("Abschnitt ${out.size + 1}", part)); part = ArrayList(); size = 0 }
            }
            if (part.isNotEmpty()) out.add(Chapter("Abschnitt ${out.size + 1}", part))
            return BookContent("", "", out)
        }
        return BookContent("", "", chapters)
    }
}

object Markdown {
    private val bold = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val italic = Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])|(?<![_\\w])_(?!\\s)(.+?)(?<!\\s)_(?![_\\w])")
    private val link = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val code = Regex("`([^`]+)`")

    private fun inline(src: String, kind: BlockKind): Block {
        var s = link.replace(src) { it.groupValues[1] }
        val spans = ArrayList<InlineSpan>()
        fun apply(rx: Regex, style: InlineStyle) {
            while (true) {
                val m = rx.find(s) ?: break
                val inner = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: break
                val start = m.range.first
                s = s.substring(0, start) + inner + s.substring(m.range.last + 1)
                // vorhandene Spans verschieben
                val removed = (m.range.last + 1 - m.range.first) - inner.length
                for (i in spans.indices) {
                    val sp = spans[i]
                    if (sp.start > start) spans[i] = sp.copy(start = sp.start - removed, end = sp.end - removed)
                }
                spans.add(InlineSpan(start, start + inner.length, style))
            }
        }
        apply(bold, InlineStyle.BOLD)
        apply(italic, InlineStyle.ITALIC)
        apply(code, InlineStyle.CODE)
        return Block(kind, s, spans.filter { it.end <= s.length && it.start < it.end })
    }

    fun parse(raw: String): BookContent {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val chapters = ArrayList<Chapter>()
        var title = "Anfang"
        var blocks = ArrayList<Block>()
        val para = StringBuilder()
        var inCode = false
        val codeBuf = StringBuilder()
        fun flushPara() {
            if (para.isNotBlank()) blocks.add(inline(para.toString().trim(), BlockKind.PARAGRAPH))
            para.setLength(0)
        }
        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                if (inCode) { blocks.add(Block(BlockKind.PRE, codeBuf.toString().trimEnd())); codeBuf.setLength(0) } else flushPara()
                inCode = !inCode
                continue
            }
            if (inCode) { codeBuf.append(line).append('\n'); continue }
            val t = line.trim()
            when {
                t.isEmpty() -> flushPara()
                t.startsWith("#") -> {
                    flushPara()
                    val level = t.takeWhile { it == '#' }.length
                    val h = t.drop(level).trim()
                    if (level <= 2) {
                        if (blocks.isNotEmpty()) chapters.add(Chapter(title, blocks))
                        title = h
                        blocks = ArrayList()
                    }
                    blocks.add(inline(h, if (level == 1) BlockKind.H1 else if (level == 2) BlockKind.H2 else BlockKind.H3))
                }
                t.startsWith(">") -> { flushPara(); blocks.add(inline(t.trimStart('>').trim(), BlockKind.QUOTE)) }
                t.matches(Regex("^([-*+]|\\d+\\.)\\s+.*")) -> {
                    flushPara()
                    val content = t.replaceFirst(Regex("^([-*+]|\\d+\\.)\\s+"), "")
                    blocks.add(inline("•  $content", BlockKind.PARAGRAPH))
                }
                t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flushPara(); blocks.add(Block(BlockKind.RULE, "⁂")) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
            }
        }
        flushPara()
        if (blocks.isNotEmpty()) chapters.add(Chapter(title, blocks))
        return BookContent("", "", chapters)
    }
}

object HtmlBook {
    fun parse(html: String): BookContent {
        val doc = Jsoup.parse(html)
        val blocks = HtmlConverter().convert(doc.body())
        val chapters = ArrayList<Chapter>()
        var title = cleanTitle(doc.title()) ?: "Anfang"
        var cur = ArrayList<Block>()
        for (b in blocks) {
            if ((b.kind == BlockKind.H1 || b.kind == BlockKind.H2) && cur.any { it.kind == BlockKind.PARAGRAPH }) {
                chapters.add(Chapter(title, cur))
                cur = ArrayList()
                title = b.text.take(80)
            } else if ((b.kind == BlockKind.H1 || b.kind == BlockKind.H2) && cur.isEmpty()) {
                title = b.text.take(80)
            }
            cur.add(b)
        }
        if (cur.isNotEmpty()) chapters.add(Chapter(title, cur))
        return BookContent(cleanTitle(doc.title()) ?: "", doc.selectFirst("meta[name=author]")?.attr("content") ?: "", chapters)
    }
}

object Rtf {
    fun toText(bytes: ByteArray): String {
        val s = String(bytes, Charsets.ISO_8859_1)
        val out = StringBuilder()
        var i = 0
        var depth = 0
        val skipDepth = ArrayList<Int>()
        var ucSkip = 1
        fun skipping() = skipDepth.isNotEmpty()
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '{' -> { depth++; i++ }
                '}' -> { if (skipDepth.lastOrNull() == depth) skipDepth.removeAt(skipDepth.lastIndex); depth--; i++ }
                '\\' -> {
                    i++
                    if (i >= s.length) break
                    val n = s[i]
                    when {
                        n == '\'' -> {
                            val hex = s.substring(i + 1, minOf(i + 3, s.length))
                            if (!skipping()) runCatching { out.append(String(byteArrayOf(hex.toInt(16).toByte()), Charset.forName("windows-1252"))) }
                            i += 3
                        }
                        n == '*' -> { skipDepth.add(depth); i++ }
                        n == '\\' || n == '{' || n == '}' -> { if (!skipping()) out.append(n); i++ }
                        n == '~' -> { if (!skipping()) out.append(' '); i++ }
                        n == '\n' || n == '\r' -> { if (!skipping()) out.append('\n'); i++ }
                        n.isLetter() -> {
                            val start = i
                            while (i < s.length && s[i].isLetter()) i++
                            val word = s.substring(start, i)
                            val numStart = i
                            if (i < s.length && (s[i] == '-' || s[i].isDigit())) { i++; while (i < s.length && s[i].isDigit()) i++ }
                            val num = s.substring(numStart, i).toIntOrNull()
                            if (i < s.length && s[i] == ' ') i++
                            when (word) {
                                "par", "line" -> if (!skipping()) out.append('\n')
                                "tab" -> if (!skipping()) out.append('\t')
                                "uc" -> ucSkip = num ?: 1
                                "u" -> if (num != null) {
                                    if (!skipping()) out.append((if (num < 0) num + 65536 else num).toChar())
                                    i += ucSkip.coerceAtLeast(0)
                                }
                                "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "rsidtbl", "generator", "xmlnstbl" ->
                                    skipDepth.add(depth)
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> { if (!skipping()) out.append(c); i++ }
            }
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n")
    }
}

// ----------------------------------------------------------------------------------------------
// DOCX / ODT
// ----------------------------------------------------------------------------------------------
object Office {
    private fun zipText(file: File, name: String): String? = ZipFile(file).use { z ->
        z.getEntry(name)?.let { e -> z.getInputStream(e).use { TextDecode.decode(it.readBytes()) } }
    }

    fun docxMeta(file: File): BookMeta {
        val core = zipText(file, "docProps/core.xml") ?: return BookMeta()
        val d = Jsoup.parse(core, "", Parser.xmlParser())
        return BookMeta(
            title = cleanTitle(d.select("*").firstOrNull { it.tagName() == "dc:title" }?.text()),
            author = cleanTitle(d.select("*").firstOrNull { it.tagName() == "dc:creator" }?.text()),
        )
    }

    fun odtMeta(file: File): BookMeta {
        val meta = zipText(file, "meta.xml") ?: return BookMeta()
        val d = Jsoup.parse(meta, "", Parser.xmlParser())
        return BookMeta(
            title = cleanTitle(d.select("*").firstOrNull { it.tagName() == "dc:title" }?.text()),
            author = cleanTitle(d.select("*").firstOrNull { it.tagName() == "meta:initial-creator" || it.tagName() == "dc:creator" }?.text()),
        )
    }

    fun docx(file: File): BookContent {
        val xml = zipText(file, "word/document.xml") ?: return BookContent("", "", emptyList())
        val d = Jsoup.parse(xml, "", Parser.xmlParser())
        val blocks = ArrayList<Block>()
        d.select("w|p").forEach { p ->
            val style = p.selectFirst("w|pPr > w|pStyle")?.attr("w:val")?.lowercase() ?: ""
            val kind = when {
                style.contains("heading1") || style == "title" || style.contains("berschrift1") -> BlockKind.H1
                style.contains("heading2") || style.contains("berschrift2") -> BlockKind.H2
                style.contains("heading") || style.contains("berschrift") -> BlockKind.H3
                style.contains("quote") || style.contains("zitat") -> BlockKind.QUOTE
                p.selectFirst("w|pPr > w|jc")?.attr("w:val") == "center" -> BlockKind.CENTER
                else -> BlockKind.PARAGRAPH
            }
            val pb = ParagraphBuilder()
            p.select("w|r").forEach { r ->
                val rp = r.selectFirst("w|rPr")
                val styles = buildList {
                    if (rp?.selectFirst("w|b")?.attr("w:val")?.let { it != "0" && it != "false" } == true) add(InlineStyle.BOLD)
                    if (rp?.selectFirst("w|i")?.attr("w:val")?.let { it != "0" && it != "false" } == true) add(InlineStyle.ITALIC)
                    if (rp?.selectFirst("w|u") != null) add(InlineStyle.UNDERLINE)
                }
                styles.forEach { pb.push(it) }
                r.children().forEach { c ->
                    when (c.tagName()) {
                        "w:t" -> pb.appendText(c.wholeText().ifEmpty { c.text() }, preserveWhitespace = true)
                        "w:tab" -> pb.appendText(" ")
                        "w:br", "w:cr" -> pb.lineBreak()
                    }
                }
                styles.reversed().forEach { pb.pop(it) }
            }
            pb.build(kind)?.let { blocks.add(it) }
        }
        return chaptersFromBlocks(blocks)
    }

    fun odt(file: File): BookContent {
        val xml = zipText(file, "content.xml") ?: return BookContent("", "", emptyList())
        val d = Jsoup.parse(xml, "", Parser.xmlParser())
        val blocks = ArrayList<Block>()
        d.select("text|h, text|p").forEach { el ->
            val txt = buildString {
                el.childNodes().forEach { n ->
                    when (n) {
                        is org.jsoup.nodes.TextNode -> append(n.wholeText)
                        is Element -> when (n.tagName()) {
                            "text:s" -> append(" ".repeat(n.attr("text:c").toIntOrNull() ?: 1))
                            "text:tab" -> append(' ')
                            "text:line-break" -> append('\n')
                            else -> append(n.text())
                        }
                    }
                }
            }.trim()
            if (txt.isEmpty()) return@forEach
            val kind = if (el.tagName() == "text:h") {
                when (el.attr("text:outline-level")) { "1" -> BlockKind.H1; "2" -> BlockKind.H2; else -> BlockKind.H3 }
            } else BlockKind.PARAGRAPH
            blocks.add(Block(kind, txt))
        }
        return chaptersFromBlocks(blocks)
    }

    private fun chaptersFromBlocks(blocks: List<Block>): BookContent {
        val chapters = ArrayList<Chapter>()
        var title = "Anfang"
        var cur = ArrayList<Block>()
        for (b in blocks) {
            if ((b.kind == BlockKind.H1 || b.kind == BlockKind.H2) && cur.isNotEmpty()) {
                chapters.add(Chapter(title, cur)); cur = ArrayList()
            }
            if (b.kind == BlockKind.H1 || b.kind == BlockKind.H2) title = b.text.take(80)
            cur.add(b)
        }
        if (cur.isNotEmpty()) chapters.add(Chapter(title, cur))
        return BookContent("", "", chapters)
    }
}

// ----------------------------------------------------------------------------------------------
// PDF
// ----------------------------------------------------------------------------------------------
object Pdf {
    @Volatile private var pdfBoxReady = false

    private fun ensurePdfBox(context: Context) {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
    }

    fun renderPage(context: Context, uri: Uri, page: Int, width: Int): Bitmap? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> renderPage(pfd, page, width) }
    }.getOrNull()

    fun renderPage(pfd: ParcelFileDescriptor, page: Int, width: Int): Bitmap? {
        PdfRenderer(pfd).use { r ->
            if (r.pageCount == 0) return null
            r.openPage(page.coerceIn(0, r.pageCount - 1)).use { p ->
                val h = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        }
    }

    fun pageCount(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { PdfRenderer(it).use { r -> r.pageCount } } ?: 0
    }.getOrDefault(0)

    fun meta(context: Context, uri: Uri): BookMeta {
        val cover = renderPage(context, uri, 0, 600)?.let { bmp ->
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, bos)
            bmp.recycle()
            bos.toByteArray()
        }
        var title: String? = null
        var author: String? = null
        runCatching {
            ensurePdfBox(context)
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0
            if (size in 1..80_000_000) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        title = cleanTitle(doc.documentInformation?.title)
                        author = cleanTitle(doc.documentInformation?.author)
                    }
                }
            }
        }
        return BookMeta(title = title?.takeIf { it.length > 2 && !it.contains("untitled", true) && !it.endsWith(".doc") }, author = author, coverBytes = cover)
    }

    /** Extrahiert den Text einer PDF für den Textmodus (jede Seite ein Abschnitt). */
    fun text(context: Context, uri: Uri): BookContent {
        ensurePdfBox(context)
        val chapters = ArrayList<Chapter>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                for (i in 1..doc.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val pageText = stripper.getText(doc)
                    val blocks = pdfParagraphs(pageText)
                    chapters.add(Chapter("Seite $i", blocks.ifEmpty { listOf(Block(BlockKind.CENTER, "· · ·")) }))
                }
            }
        }
        return BookContent("", "", chapters)
    }

    private fun pdfParagraphs(text: String): List<Block> {
        val lines = text.replace("\r", "").split('\n').map { it.trimEnd() }
        val out = ArrayList<Block>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotBlank()) out.add(Block(BlockKind.PARAGRAPH, cur.toString().trim())); cur.setLength(0) }
        val avg = lines.filter { it.isNotBlank() }.map { it.length }.average().takeIf { !it.isNaN() } ?: 60.0
        for (l in lines) {
            if (l.isBlank()) { flush(); continue }
            if (cur.isNotEmpty()) {
                if (cur.endsWith("-") && l.firstOrNull()?.isLowerCase() == true) cur.setLength(cur.length - 1) else cur.append(' ')
            }
            cur.append(l.trim())
            // kurze Zeile mit Satzende → Absatzende
            if (l.length < avg * 0.7 && l.trimEnd().lastOrNull() in setOf('.', '!', '?', ':', '“', '"', '«', '»')) flush()
        }
        flush()
        return out
    }
}
