package com.lesehaus.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lesehaus.app.data.formats.BlockKind
import com.lesehaus.app.data.formats.Epub
import com.lesehaus.app.data.formats.InlineStyle
import com.lesehaus.app.data.formats.Markdown
import com.lesehaus.app.data.formats.PlainText
import com.lesehaus.app.data.formats.Rtf
import com.lesehaus.app.data.formats.TextDecode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ParserTest {

    private fun makeEpub(): File {
        val f = Files.createTempFile("test", ".epub").toFile()
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Die Verwandlung</dc:title><dc:creator>Franz Kafka</dc:creator><dc:language>de</dc:language></metadata>
                <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            put("OEBPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><nav><ol><li><a href="text/c1.xhtml">Erstes Kapitel</a></li><li><a href="text/c2.xhtml#x">Zweites Kapitel</a></li></ol></nav></body></html>""")
            put("OEBPS/text/c1.xhtml", """<html><body><h1>I</h1><p>Als Gregor Samsa eines Morgens aus <em>unruhigen</em> Träumen erwachte,</p><p>fand er sich in seinem Bett.</p></body></html>""")
            put("OEBPS/text/c2.xhtml", """<html><body><h2>II</h2><p>Erst in der Abenddämmerung erwachte Gregor.</p></body></html>""")
        }
        return f
    }

    @Test
    fun epubMetaAndChapters() {
        val f = makeEpub()
        val meta = Epub.meta(f)
        assertEquals("Die Verwandlung", meta.title)
        assertEquals("Franz Kafka", meta.author)
        val content = Epub.parse(f, Files.createTempDirectory("img").toFile())
        assertEquals(2, content.chapters.size)
        assertEquals("Erstes Kapitel", content.chapters[0].title)
        assertEquals("Zweites Kapitel", content.chapters[1].title)
        val p = content.chapters[0].blocks.first { it.kind == BlockKind.PARAGRAPH }
        assertEquals("Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte,", p.text)
        val italic = p.spans.single { it.style == InlineStyle.ITALIC }
        assertEquals("unruhigen", p.text.substring(italic.start, italic.end))
    }

    @Test
    fun plainTextChapters() {
        val text = "Kapitel 1\n\nEs war einmal.\nEin König.\n\nKapitel 2\n\nUnd dann.\n"
        val c = PlainText.parse(text)
        assertEquals(2, c.chapters.size)
        assertEquals("Kapitel 1", c.chapters[0].title)
        assertTrue(c.chapters[0].blocks.any { it.text == "Es war einmal. Ein König." })
    }

    @Test
    fun markdownInline() {
        val c = Markdown.parse("# Titel\n\nDas ist **fett** und *kursiv*.\n\n## Teil 2\n\n> Zitat")
        assertEquals(2, c.chapters.size)
        val p = c.chapters[0].blocks.first { it.kind == BlockKind.PARAGRAPH }
        assertEquals("Das ist fett und kursiv.", p.text)
        assertEquals(setOf(InlineStyle.BOLD, InlineStyle.ITALIC), p.spans.map { it.style }.toSet())
    }

    @Test
    fun rtfAndCharsets() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl{\\f0 Times;}}\\f0 Gr\\'fc\\'dfe aus K\\'f6ln\\par Zweite Zeile}"
        assertEquals("Grüße aus Köln\nZweite Zeile", Rtf.toText(rtf.toByteArray()).trim())
        assertEquals("Grüße", TextDecode.decode("Grüße".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("Grüße", TextDecode.decode("Grüße".toByteArray(Charsets.UTF_8)))
    }
}
