package com.lesehaus.app.ui.shelf

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.book.bookColor
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.lighten
import kotlin.math.abs
import kotlin.math.ln

/** Bauformen für Bücherregale (Maße in Zimmerhöhen). */
enum class BookcaseStyle(val label: String, val w: Float, val h: Float, val onWall: Boolean = false) {
    TALL("Bücherschrank", 0.42f, 0.62f),
    WIDE("Breites Regal", 0.66f, 0.46f),
    LOW("Sideboard", 0.52f, 0.24f),
    WALL("Wandbretter", 0.5f, 0.34f, onWall = true),
    LADDER("Leiterregal", 0.32f, 0.58f),
    GLASS("Vitrine", 0.38f, 0.6f),
    ARCH("Bogenregal", 0.46f, 0.66f),
    CUBE("Würfelregal", 0.44f, 0.44f),
}

enum class BookArrangement(val label: String, val description: String) {
    ALPHA("Ordentlich", "Aufrecht in der gewählten Sortierung"),
    RAINBOW("Regenbogen", "Nach Farben sortiert"),
    MIXED("Gemütlich gemischt", "Mit Stapeln, schrägen Büchern und Deko"),
    COVERS("Cover nach vorn", "Wie in einer Buchhandlung"),
    STACKS("Stapel", "Bücher liegen in Stapeln"),
}

enum class DecorKind { PLANT, CANDLE, OWL, GLOBE, HOURGLASS, VASE }

sealed interface Piece {
    /** Breite in „Einheiten“ (1 Einheit = Fachhöhe / 10). */
    val units: Float
    data class Standing(val book: Book, val height: Float, override val units: Float) : Piece
    data class Leaning(val book: Book, val height: Float, override val units: Float) : Piece
    data class Stack(val books: List<Book>, override val units: Float = 5.6f) : Piece
    data class Cover(val book: Book, override val units: Float = 6.4f) : Piece
    data class Decor(val kind: DecorKind, override val units: Float = 3.2f) : Piece
    data object Bookend : Piece { override val units: Float = 1.4f }
}

fun spineUnits(book: Book): Float {
    val kb = (book.fileSize / 1024f).coerceAtLeast(10f)
    return (0.75f + ln(kb) * 0.14f).coerceIn(0.9f, 2.1f)
}

fun spineHeightFactor(book: Book): Float = 0.78f + (abs(book.title.hashCode()) % 17) / 100f

private fun hue(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return if (hsv[1] < 0.15f) 400f + hsv[2] else hsv[0]
}

/** Ordnet Bücher zu Regal-„Stücken“ an – abhängig von der gewählten Anordnung. */
fun arrangeBooks(books: List<Book>, arrangement: BookArrangement, decor: Boolean, seed: Long): List<Piece> {
    val out = ArrayList<Piece>()
    val ordered = if (arrangement == BookArrangement.RAINBOW) books.sortedBy { hue(bookColor(it)) } else books
    val decorKinds = DecorKind.entries
    var decorIndex = (seed % decorKinds.size).toInt()
    fun maybeDecor(i: Int) {
        if (decor && i > 0 && i % 11 == 0) out += Piece.Decor(decorKinds[decorIndex++ % decorKinds.size])
    }
    when (arrangement) {
        BookArrangement.COVERS -> ordered.forEachIndexed { i, b -> maybeDecor(i); out += Piece.Cover(b) }
        BookArrangement.STACKS -> ordered.chunked(4).forEachIndexed { i, c -> maybeDecor(i * 3); out += Piece.Stack(c) }
        BookArrangement.MIXED -> {
            var i = 0
            var n = 0
            while (i < ordered.size) {
                maybeDecor(n)
                val r = abs((ordered[i].id * 31 + seed).hashCode()) % 9
                when {
                    r == 0 && i + 3 <= ordered.size -> { out += Piece.Stack(ordered.subList(i, i + 3)); i += 3 }
                    r == 1 -> { out += Piece.Leaning(ordered[i], spineHeightFactor(ordered[i]), spineUnits(ordered[i]) + 2.2f); i++ }
                    else -> { out += Piece.Standing(ordered[i], spineHeightFactor(ordered[i]), spineUnits(ordered[i])); i++ }
                }
                n++
            }
            if (decor) out += Piece.Bookend
        }
        else -> {
            ordered.forEachIndexed { i, b -> maybeDecor(i); out += Piece.Standing(b, spineHeightFactor(b), spineUnits(b)) }
            if (ordered.isNotEmpty()) out += Piece.Bookend
        }
    }
    return out
}

/** Verteilt Stücke auf Fächer (Kapazität in Einheiten). Gibt pro Fach die Stücke zurück. */
fun fillCompartments(pieces: List<Piece>, capacities: List<Float>, spread: Boolean = false): List<List<Piece>> {
    val result = capacities.map { ArrayList<Piece>() }
    val total = pieces.sumOf { it.units.toDouble() }.toFloat()
    // Bei wenigen Büchern gleichmäßig auf die Fächer verteilen statt nur das oberste zu füllen
    val target = if (spread && total < capacities.sum()) capacities.map { maxOf(it * total / capacities.sum() * 1.05f, 4f).coerceAtMost(it) } else capacities
    var ci = 0
    var used = 0f
    for (p in pieces) {
        while (ci < capacities.size && used + p.units > target[ci] && result[ci].isNotEmpty()) { ci++; used = 0f }
        if (ci >= capacities.size) break
        if (p.units > capacities[ci]) { ci++; used = 0f; if (ci >= capacities.size) break }
        result[ci] += p
        used += p.units
    }
    return result
}

/** Innenräume (Fächer) einer Bauform innerhalb des Regal-Rechtecks. */
fun compartments(style: BookcaseStyle, r: Rect): List<Rect> {
    val w = r.width
    val h = r.height
    fun rows(n: Int, top: Float, bottom: Float, left: Float, right: Float, cols: Int = 1): List<Rect> {
        val board = h * 0.022f
        val rh = (bottom - top) / n
        val out = ArrayList<Rect>()
        for (i in 0 until n) for (c in 0 until cols) {
            val cw = (right - left) / cols
            out += Rect(left + c * cw + (if (c > 0) board / 2 else 0f), top + i * rh + board, left + (c + 1) * cw - (if (c < cols - 1) board / 2 else 0f), top + (i + 1) * rh)
        }
        return out
    }
    return when (style) {
        BookcaseStyle.TALL -> rows(5, r.top + h * 0.07f, r.bottom - h * 0.07f, r.left + w * 0.06f, r.right - w * 0.06f)
        BookcaseStyle.GLASS -> rows(4, r.top + h * 0.09f, r.bottom - h * 0.12f, r.left + w * 0.08f, r.right - w * 0.08f)
        BookcaseStyle.ARCH -> rows(5, r.top + h * 0.16f, r.bottom - h * 0.06f, r.left + w * 0.07f, r.right - w * 0.07f)
        BookcaseStyle.WIDE -> rows(4, r.top + h * 0.05f, r.bottom - h * 0.06f, r.left + w * 0.04f, r.right - w * 0.04f, cols = 2)
        BookcaseStyle.LOW -> rows(2, r.top + h * 0.1f, r.bottom - h * 0.16f, r.left + w * 0.04f, r.right - w * 0.04f, cols = 3)
        BookcaseStyle.CUBE -> rows(3, r.top + h * 0.04f, r.bottom - h * 0.04f, r.left + w * 0.04f, r.right - w * 0.04f, cols = 3)
        BookcaseStyle.WALL -> (0 until 3).map { i ->
            val boardY = r.top + h * (i + 1) / 3f - h * 0.03f
            val inset = w * (0.04f + (i % 2) * 0.06f)
            Rect(r.left + inset, boardY - h * 0.28f, r.right - inset, boardY)
        }
        BookcaseStyle.LADDER -> (0 until 4).map { i ->
            val top = r.top + h * (0.04f + i * 0.235f)
            val bottom = top + h * 0.2f
            val inset = w * (0.24f - i * 0.06f)
            Rect(r.left + inset, top, r.right - w * 0.06f, bottom)
        }
    }
}

fun capacities(style: BookcaseStyle, r: Rect): List<Float> = compartments(style, r).map { it.width / (it.height / 10f) }

// ------------------------------------------------------------------------------------------------
// Zeichnen im Zimmer
// ------------------------------------------------------------------------------------------------
private val titlePaint = Paint().apply { isAntiAlias = true; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }

fun DrawScope.drawBookcase(shelf: Shelf, r: Rect, pieces: List<Piece>, covers: Map<String, ImageBitmap>, alignRight: Boolean = false) {
    val style = enumOr(shelf.style, BookcaseStyle.TALL)
    val wood = enumOr(shelf.wood, Wood.WALNUT)
    val base = Color(wood.base)
    val dark = Color(wood.dark)
    val light = Color(wood.light)
    val comps = compartments(style, r)
    val filled = fillCompartments(pieces, comps.map { it.width / (it.height / 10f) }, spread = true)

    // Schatten
    if (!style.onWall) drawOval(Color.Black.copy(alpha = 0.3f), Offset(r.left - r.width * 0.03f, r.bottom - r.height * 0.03f), Size(r.width * 1.06f, r.height * 0.06f))
    else drawRect(Color.Black.copy(alpha = 0.18f), Offset(r.left + 6f, r.top + 10f), r.size)

    // Korpus
    when (style) {
        BookcaseStyle.WALL -> comps.forEach { c ->
            // Konsolen
            drawRect(dark, Offset(c.left + c.width * 0.1f, c.bottom), Size(c.width * 0.03f, c.height * 0.25f))
            drawRect(dark, Offset(c.right - c.width * 0.13f, c.bottom), Size(c.width * 0.03f, c.height * 0.25f))
        }
        BookcaseStyle.LADDER -> {
            drawLine(dark, Offset(r.left + r.width * 0.12f, r.bottom), Offset(r.left + r.width * 0.32f, r.top), r.width * 0.04f)
            drawLine(base, Offset(r.right - r.width * 0.04f, r.bottom), Offset(r.right - r.width * 0.04f, r.top), r.width * 0.05f)
        }
        BookcaseStyle.ARCH -> {
            val p = Path().apply {
                moveTo(r.left, r.bottom); lineTo(r.left, r.top + r.width * 0.5f)
                arcTo(Rect(r.left, r.top, r.right, r.top + r.width), 180f, 180f, false)
                lineTo(r.right, r.bottom); close()
            }
            drawPath(p, Brush.horizontalGradient(listOf(light, base, dark), r.left, r.right))
            val inner = Rect(r.left + r.width * 0.07f, r.top + r.height * 0.05f, r.right - r.width * 0.07f, r.bottom - r.height * 0.06f)
            val ip = Path().apply {
                moveTo(inner.left, inner.bottom); lineTo(inner.left, inner.top + inner.width * 0.5f)
                arcTo(Rect(inner.left, inner.top, inner.right, inner.top + inner.width), 180f, 180f, false)
                lineTo(inner.right, inner.bottom); close()
            }
            drawPath(ip, Brush.verticalGradient(listOf(dark.darken(0.45f), dark.darken(0.25f)), inner.top, inner.bottom))
        }
        else -> {
            drawRect(Brush.horizontalGradient(listOf(light, base, dark), r.left, r.right), r.topLeft, r.size)
            val inner = Rect(comps.minOf { it.left }, comps.minOf { it.top } - r.height * 0.022f, comps.maxOf { it.right }, comps.maxOf { it.bottom })
            drawRect(Brush.verticalGradient(listOf(dark.darken(0.45f), dark.darken(0.25f)), inner.top, inner.bottom), inner.topLeft, inner.size)
            // Krone & Sockel
            if (style == BookcaseStyle.TALL || style == BookcaseStyle.GLASS) {
                drawRect(light.lighten(0.05f), Offset(r.left - r.width * 0.03f, r.top), Size(r.width * 1.06f, r.height * 0.03f))
                drawRect(dark, Offset(r.left - r.width * 0.02f, r.bottom - r.height * 0.05f), Size(r.width * 1.04f, r.height * 0.05f))
            }
            if (style == BookcaseStyle.LOW) {
                drawRect(dark, Offset(r.left + r.width * 0.05f, r.bottom - r.height * 0.16f), Size(r.width * 0.05f, r.height * 0.16f))
                drawRect(dark, Offset(r.right - r.width * 0.1f, r.bottom - r.height * 0.16f), Size(r.width * 0.05f, r.height * 0.16f))
            }
        }
    }

    // Bretter & Bücher
    comps.forEachIndexed { i, c ->
        val boardH = r.height * 0.022f
        // Schatten des oberen Bretts
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), c.top, c.top + c.height * 0.2f), c.topLeft, Size(c.width, c.height * 0.2f))
        clipRect(c.left, c.top - c.height, c.right, c.bottom) {
            drawPieces(filled.getOrNull(i).orEmpty(), c, covers, alignRight)
        }
        // Brett unter dem Fach
        drawRect(Brush.verticalGradient(listOf(light, base, dark), c.bottom, c.bottom + boardH), Offset(c.left - (if (style == BookcaseStyle.WALL) c.width * 0.02f else 0f), c.bottom), Size(c.width + (if (style == BookcaseStyle.WALL) c.width * 0.04f else 0f), boardH))
        drawRect(Color.White.copy(alpha = 0.15f), Offset(c.left, c.bottom), Size(c.width, boardH * 0.2f))
    }

    if (style == BookcaseStyle.GLASS) {
        // Glastüren mit Spiegelung
        val inner = Rect(comps.minOf { it.left }, comps.minOf { it.top }, comps.maxOf { it.right }, comps.maxOf { it.bottom })
        val half = inner.width / 2
        for (k in 0..1) {
            val d = Rect(inner.left + k * half, inner.top - r.height * 0.02f, inner.left + (k + 1) * half, inner.bottom + r.height * 0.02f)
            drawRect(Color(0x22C9E2EE), d.topLeft, d.size)
            drawRect(base, d.topLeft, d.size, style = Stroke(r.width * 0.025f))
            val gp = Path().apply { moveTo(d.left + d.width * 0.15f, d.top); lineTo(d.left + d.width * 0.45f, d.top); lineTo(d.left + d.width * 0.05f, d.top + d.height * 0.5f); lineTo(d.left, d.top + d.height * 0.42f); close() }
            drawPath(gp, Color.White.copy(alpha = 0.12f))
        }
        drawCircle(Color(0xFFC9A45A), r.width * 0.018f, Offset(inner.center.x - r.width * 0.03f, inner.center.y))
        drawCircle(Color(0xFFC9A45A), r.width * 0.018f, Offset(inner.center.x + r.width * 0.03f, inner.center.y))
    }
}

private fun DrawScope.drawPieces(pieces: List<Piece>, c: Rect, covers: Map<String, ImageBitmap>, alignRight: Boolean = false) {
    val u = c.height / 10f
    var x = if (alignRight) c.right - u * 0.3f - pieces.sumOf { it.units.toDouble() }.toFloat() * u else c.left + u * 0.3f
    for (p in pieces) {
        val pw = p.units * u
        when (p) {
            is Piece.Standing -> drawSpine(p.book, Rect(x, c.bottom - p.height * c.height, x + pw, c.bottom))
            is Piece.Leaning -> {
                val sw = spineUnits(p.book) * u
                val bh = p.height * c.height
                rotate(16f, Offset(x + sw, c.bottom)) { drawSpine(p.book, Rect(x + sw * 0.2f, c.bottom - bh, x + sw * 1.2f, c.bottom)) }
            }
            is Piece.Stack -> {
                var y = c.bottom
                p.books.forEachIndexed { i, b ->
                    val th = spineUnits(b) * u * 1.5f
                    val bw = pw * (0.82f + (abs(b.title.hashCode()) % 15) / 100f)
                    val off = (pw - bw) / 2 + (if (i % 2 == 0) 1 else -1) * u * 0.15f
                    drawHorizontalSpine(b, Rect(x + off, y - th, x + off + bw, y))
                    y -= th
                }
            }
            is Piece.Cover -> {
                val ch = c.height * 0.88f
                val cw = ch * 0.66f
                val rr = Rect(x + (pw - cw) / 2, c.bottom - ch, x + (pw + cw) / 2, c.bottom)
                val img = p.book.coverPath?.let { covers[it] }
                drawRect(Color.Black.copy(alpha = 0.3f), rr.topLeft + Offset(3f, 3f), rr.size)
                if (img != null) drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(rr.left.toInt(), rr.top.toInt()), IntSize(rr.width.toInt(), rr.height.toInt()))
                else {
                    val col = bookColor(p.book)
                    drawRect(Brush.verticalGradient(listOf(col.lighten(0.1f), col.darken(0.2f)), rr.top, rr.bottom), rr.topLeft, rr.size)
                    drawRect(Color(0xFFE2C27E).copy(alpha = 0.8f), rr.topLeft + Offset(rr.width * 0.08f, rr.width * 0.08f), Size(rr.width * 0.84f, rr.height - rr.width * 0.16f), style = Stroke(1.2f))
                    drawCoverTitle(p.book.title, rr)
                }
            }
            is Piece.Decor -> drawDecor(p.kind, Rect(x, c.bottom - c.height * 0.6f, x + pw, c.bottom))
            Piece.Bookend -> {
                val p2 = Path().apply { moveTo(x, c.bottom); lineTo(x, c.bottom - c.height * 0.45f); lineTo(x + pw * 0.4f, c.bottom - c.height * 0.45f); lineTo(x + pw, c.bottom); close() }
                drawPath(p2, Color(0xFFB08D57))
                drawPath(p2, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), x, x + pw))
            }
        }
        x += pw
    }
}

fun DrawScope.drawSpine(book: Book, r: Rect) {
    val col = bookColor(book)
    drawRect(Brush.horizontalGradient(listOf(col.darken(0.35f), col.lighten(0.12f), col, col.darken(0.3f)), r.left, r.right), r.topLeft, r.size)
    val gold = Color(0xFFE2C27E)
    val bands = abs(book.author.hashCode()) % 3
    val ys = when (bands) { 0 -> listOf(0.08f, 0.11f, 0.89f, 0.92f); 1 -> listOf(0.06f, 0.94f); else -> listOf(0.1f, 0.14f, 0.86f) }
    ys.forEach { y -> drawLine(gold.copy(alpha = 0.85f), Offset(r.left, r.top + r.height * y), Offset(r.right, r.top + r.height * y), (r.width * 0.06f).coerceIn(0.6f, 2.5f)) }
    if (book.status == "READING") drawRect(Color(0xFF9B2F3F), Offset(r.left + r.width * 0.3f, r.top), Size(r.width * 0.4f, r.height * 0.05f))
    // Titel (vertikal), nur wenn lesbar groß
    if (r.width >= 9f && r.height > 60f) {
        val ts = (r.width * 0.55f).coerceAtMost(34f)
        titlePaint.textSize = ts
        titlePaint.color = if (col.luminanceApprox() > 0.6f) 0xFF2B1A0C.toInt() else 0xFFF6E7C4.toInt()
        val maxLen = r.height * 0.7f
        var t = book.title
        while (t.length > 1 && titlePaint.measureText(t) > maxLen) t = t.dropLast(2) + "…"
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(r.center.x + ts * 0.35f, r.center.y)
            rotate(90f)
            translate(-titlePaint.measureText(t) / 2, 0f)
            drawText(t, 0f, 0f, titlePaint)
            restore()
        }
    }
}

private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue

fun DrawScope.drawHorizontalSpine(book: Book, r: Rect) {
    val col = bookColor(book)
    drawRect(Brush.verticalGradient(listOf(col.lighten(0.12f), col, col.darken(0.3f)), r.top, r.bottom), r.topLeft, r.size)
    drawRect(Color(0xFFF2E8D5), Offset(r.right - r.width * 0.04f, r.top + r.height * 0.15f), Size(r.width * 0.03f, r.height * 0.7f))
    if (r.height >= 9f && r.width > 60f) {
        val ts = (r.height * 0.55f).coerceAtMost(34f)
        titlePaint.textSize = ts
        titlePaint.color = 0xFFF6E7C4.toInt()
        var t = book.title
        while (t.length > 1 && titlePaint.measureText(t) > r.width * 0.8f) t = t.dropLast(2) + "…"
        drawContext.canvas.nativeCanvas.drawText(t, r.center.x - titlePaint.measureText(t) / 2, r.center.y + ts * 0.35f, titlePaint)
    }
}

private fun DrawScope.drawCoverTitle(title: String, r: Rect) {
    if (r.width < 30f) return
    titlePaint.textSize = r.width * 0.13f
    titlePaint.color = 0xFFF6E7C4.toInt()
    val words = title.split(' ')
    val lines = ArrayList<String>()
    var cur = ""
    for (w in words) {
        val cand = if (cur.isEmpty()) w else "$cur $w"
        if (titlePaint.measureText(cand) > r.width * 0.78f && cur.isNotEmpty()) { lines += cur; cur = w } else cur = cand
    }
    if (cur.isNotEmpty()) lines += cur
    val lh = titlePaint.textSize * 1.2f
    var y = r.center.y - (lines.size - 1) * lh / 2
    lines.take(5).forEach { l ->
        drawContext.canvas.nativeCanvas.drawText(l, r.center.x - titlePaint.measureText(l) / 2, y, titlePaint)
        y += lh
    }
}

fun DrawScope.drawDecor(kind: DecorKind, r: Rect) {
    val cx = r.center.x
    when (kind) {
        DecorKind.PLANT -> {
            val potTop = r.bottom - r.height * 0.35f
            for (k in 0 until 5) {
                val dx = (k - 2) * r.width * 0.16f
                drawOval(if (k % 2 == 0) Color(0xFF4E8A55) else Color(0xFF6FA36A), Offset(cx + dx - r.width * 0.14f, potTop - r.height * (0.4f + (k % 2) * 0.15f)), Size(r.width * 0.28f, r.height * 0.4f))
            }
            drawRoundRect(Color(0xFFC96A3E), Offset(cx - r.width * 0.3f, potTop), Size(r.width * 0.6f, r.height * 0.35f), CornerRadius(4f))
        }
        DecorKind.CANDLE -> {
            drawRect(Color(0xFFF7EEDB), Offset(cx - r.width * 0.14f, r.bottom - r.height * 0.6f), Size(r.width * 0.28f, r.height * 0.55f))
            drawOval(Color(0xFFB08D57), Offset(cx - r.width * 0.35f, r.bottom - r.height * 0.08f), Size(r.width * 0.7f, r.height * 0.1f))
            drawOval(Color(0xFFFFD36B), Offset(cx - r.width * 0.08f, r.bottom - r.height * 0.78f), Size(r.width * 0.16f, r.height * 0.18f))
        }
        DecorKind.OWL -> {
            val b = Color(0xFF8C6D3C)
            drawOval(b, Offset(cx - r.width * 0.32f, r.bottom - r.height * 0.75f), Size(r.width * 0.64f, r.height * 0.75f))
            drawCircle(Color(0xFFF2E3C6), r.width * 0.12f, Offset(cx - r.width * 0.13f, r.bottom - r.height * 0.55f))
            drawCircle(Color(0xFFF2E3C6), r.width * 0.12f, Offset(cx + r.width * 0.13f, r.bottom - r.height * 0.55f))
            drawCircle(Color(0xFF1E1410), r.width * 0.05f, Offset(cx - r.width * 0.13f, r.bottom - r.height * 0.55f))
            drawCircle(Color(0xFF1E1410), r.width * 0.05f, Offset(cx + r.width * 0.13f, r.bottom - r.height * 0.55f))
            val beak = Path().apply { moveTo(cx - r.width * 0.05f, r.bottom - r.height * 0.46f); lineTo(cx + r.width * 0.05f, r.bottom - r.height * 0.46f); lineTo(cx, r.bottom - r.height * 0.38f); close() }
            drawPath(beak, Color(0xFFE0A93B))
        }
        DecorKind.GLOBE -> {
            drawLine(Color(0xFF3F271A), Offset(cx, r.bottom - r.height * 0.3f), Offset(cx, r.bottom), r.width * 0.06f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8FB3C9), Color(0xFF3C6680)), Offset(cx - r.width * 0.1f, r.bottom - r.height * 0.6f), r.width * 0.5f), r.width * 0.3f, Offset(cx, r.bottom - r.height * 0.55f))
            drawOval(Color(0xFFC9B27A), Offset(cx - r.width * 0.18f, r.bottom - r.height * 0.68f), Size(r.width * 0.2f, r.height * 0.12f))
        }
        DecorKind.HOURGLASS -> {
            val wood = Color(0xFF5B3A26)
            drawRect(wood, Offset(cx - r.width * 0.3f, r.bottom - r.height * 0.08f), Size(r.width * 0.6f, r.height * 0.08f))
            drawRect(wood, Offset(cx - r.width * 0.3f, r.bottom - r.height * 0.75f), Size(r.width * 0.6f, r.height * 0.08f))
            val g = Path().apply {
                moveTo(cx - r.width * 0.22f, r.bottom - r.height * 0.67f); lineTo(cx + r.width * 0.22f, r.bottom - r.height * 0.67f)
                lineTo(cx, r.bottom - r.height * 0.38f); lineTo(cx + r.width * 0.22f, r.bottom - r.height * 0.08f)
                lineTo(cx - r.width * 0.22f, r.bottom - r.height * 0.08f); lineTo(cx, r.bottom - r.height * 0.38f); close()
            }
            drawPath(g, Color(0x55DDEEF5))
            val sand = Path().apply { moveTo(cx - r.width * 0.18f, r.bottom - r.height * 0.08f); lineTo(cx + r.width * 0.18f, r.bottom - r.height * 0.08f); lineTo(cx, r.bottom - r.height * 0.24f); close() }
            drawPath(sand, Color(0xFFE6C98F))
        }
        DecorKind.VASE -> {
            val v = Path().apply {
                moveTo(cx - r.width * 0.12f, r.bottom - r.height * 0.7f); lineTo(cx + r.width * 0.12f, r.bottom - r.height * 0.7f)
                cubicTo(cx + r.width * 0.4f, r.bottom - r.height * 0.4f, cx + r.width * 0.3f, r.bottom, cx, r.bottom)
                cubicTo(cx - r.width * 0.3f, r.bottom, cx - r.width * 0.4f, r.bottom - r.height * 0.4f, cx - r.width * 0.12f, r.bottom - r.height * 0.7f); close()
            }
            drawPath(v, Brush.horizontalGradient(listOf(Color(0xFF2F5A6E), Color(0xFF6F9CB0), Color(0xFF2F5A6E)), cx - r.width * 0.35f, cx + r.width * 0.35f))
        }
    }
}

fun bookcaseRect(shelf: Shelf, w: Float, h: Float): Rect {
    val st = enumOr(shelf.style, BookcaseStyle.TALL)
    val bw = st.w * h * shelf.scale
    val bh = st.h * h * shelf.scale
    val cx = shelf.posX * w
    val bottom = shelf.posY * h
    return Rect(cx - bw / 2, bottom - bh, cx + bw / 2, bottom)
}
