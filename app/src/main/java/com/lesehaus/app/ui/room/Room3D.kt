package com.lesehaus.app.ui.room

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.lesehaus.app.data.FloorType
import com.lesehaus.app.data.WallPattern
import com.lesehaus.app.data.WindowView
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.shelf.BookcaseStyle
import com.lesehaus.app.ui.shelf.drawBookcase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Weltmaßstab: Vorlagengrößen (in alten Bildhöhen) → Wandhöhen. */
const val WORLD_K = 1f / FLOOR_Y
const val EYE = 0.48f
const val NEAR = 0.3f
private const val TEX = 256f
private const val LOCAL = 420f

enum class Wall(val label: String) { BACK("Rückwand"), LEFT("Linke Wand"), RIGHT("Rechte Wand"), FLOOR("Freistehend"), CEILING("Decke") }

enum class Ceiling(val label: String) { PLAIN("Glatt"), BEAMS("Holzbalken"), COFFERED("Kassetten") }

/** Kamera: Position (x, Tiefe d) und Blickrichtung [yaw] (Bogenmaß, positiv = nach rechts gedreht). */
data class Cam(val x: Float, val d: Float, val yaw: Float = 0f)

/** Perspektivische Projektion mit drehbarer Blickrichtung für ein Zimmer der Größe W×1×D. */
class Proj(val sw: Float, val sh: Float, val w: Float, val depth: Float, val cam: Cam, fDepth: Float = depth) {
    /** Brennweite: aus der Startposition nimmt die Rückwand gut die Hälfte der Breite ein, Seitenwände sind sichtbar. */
    val f = 0.56f * min(sw, sh * 1.1f) * (fDepth + 0.3f) / w.coerceAtLeast(1.2f)
    val horizon = sh * 0.45f
    val cx = sw / 2f
    private val cy = kotlin.math.cos(cam.yaw)
    private val sy = kotlin.math.sin(cam.yaw)

    /** Abstand vor der Kamera (Blickrichtung). */
    fun z(x: Float, d: Float) = (x - cam.x) * sy + (cam.d - d) * cy
    private fun lat(x: Float, d: Float) = (x - cam.x) * cy + (d - cam.d) * sy
    fun visible(x: Float, d: Float) = z(x, d) > NEAR

    fun dz(d: Float) = max(cam.d - d, NEAR)
    fun p(x: Float, y: Float, d: Float): Offset {
        val zz = max(z(x, d), NEAR * 0.5f)
        return Offset(cx + lat(x, d) * f / zz, horizon + (EYE - y) * f / zz)
    }
    fun s(d: Float) = f / dz(d)
    fun sAt(x: Float, d: Float) = f / max(z(x, d), NEAR)

    /** Sehstrahl durch einen Bildschirmpunkt: Richtung (dx, dy, dd) in Weltkoordinaten. */
    private fun ray(sx: Float, sy2: Float): Triple<Float, Float, Float> {
        val xc = (sx - cx) / f
        val yc = (horizon - sy2) / f
        return Triple(xc * cy + sy, yc, xc * sy - cy)
    }
    fun floorAt(sx: Float, sy2: Float, elev: Float = 0f): Pair<Float, Float>? {
        val (dx, dy, dd) = ray(sx, sy2)
        if (dy >= -1e-4f) return null
        val t = (elev - EYE) / dy
        return (cam.x + t * dx) to (cam.d + t * dd)
    }
    fun ceilingAt(sx: Float, sy2: Float): Pair<Float, Float>? {
        val (dx, dy, dd) = ray(sx, sy2)
        if (dy <= 1e-4f) return null
        val t = (1f - EYE) / dy
        return (cam.x + t * dx) to (cam.d + t * dd)
    }
    /** Rückwand (d = 0): liefert (x, y). */
    fun backAt(sx: Float, sy2: Float): Pair<Float, Float>? {
        val (dx, dy, dd) = ray(sx, sy2)
        if (dd >= -1e-4f) return null
        val t = -cam.d / dd
        return (cam.x + t * dx) to (EYE + t * dy)
    }
    /** Linke (x=0) bzw. rechte (x=W) Wand: liefert (Tiefe d, Höhe y). */
    fun sideAt(right: Boolean, sx: Float, sy2: Float): Pair<Float, Float>? {
        val (dx, dy, dd) = ray(sx, sy2)
        val wx = if (right) w else 0f
        if (abs(dx) < 1e-4f) return null
        val t = (wx - cam.x) / dx
        if (t <= 0.02f) return null
        return (cam.d + t * dd) to (EYE + t * dy)
    }
    /** Bewegung eines Aufstellers auf dem Boden aus einer Fingerbewegung (Weltdelta x, d). */
    fun billboardDelta(x: Float, d: Float, elev: Float, dxs: Float, dys: Float): Pair<Float, Float> {
        val zz = max(z(x, d), NEAR)
        val latW = dxs * zz / f
        val fwd = -dys * zz * zz / ((EYE - elev).coerceAtLeast(0.05f) * f)
        return (latW * cy + fwd * sy) to (latW * sy - fwd * cy)
    }
}

// ------------------------------------------------------------------------------------------------
// Normalisierung alter (flacher) Koordinaten in Raumkoordinaten
// ------------------------------------------------------------------------------------------------
fun RoomItem.wallOf(): Wall = runCatching { Wall.valueOf(wall) }.getOrDefault(Wall.BACK)

/** Wandelt Einträge mit alten Flachansicht-Koordinaten in Raumkoordinaten um. */
fun normalizeItems(items: List<RoomItem>): List<RoomItem> {
    val tables = items.filter { it.wall.isEmpty() && it.type == ItemType.SIDE_TABLE.name }
    return items.map { it ->
        if (it.wall.isNotEmpty()) return@map it
        val t = itemTypeOf(it.type) ?: return@map it.copy(wall = Wall.FLOOR.name)
        when (t.layer) {
            ItemLayer.WALL -> it.copy(wall = Wall.BACK.name, y = ((FLOOR_Y - it.y) / FLOOR_Y).coerceAtLeast(0f))
            ItemLayer.DECAL -> it.copy(wall = Wall.FLOOR.name, y = 0.55f)
            ItemLayer.CEILING -> it.copy(wall = Wall.CEILING.name, y = 0.35f)
            ItemLayer.FLOOR -> {
                val table = tables.firstOrNull { tb -> abs(tb.x - it.x) < 0.06f && it.y < tb.y }
                when {
                    table != null -> it.copy(wall = Wall.FLOOR.name, y = legacyDepth(table.y), elev = ItemType.SIDE_TABLE.h * WORLD_K * table.scale * 0.94f)
                    it.y < 0.78f -> it.copy(wall = Wall.BACK.name, y = ((FLOOR_Y + 0.04f - it.y) / FLOOR_Y).coerceAtLeast(0f))
                    else -> it.copy(wall = Wall.FLOOR.name, y = legacyDepth(it.y))
                }
            }
        }
    }
}

private fun legacyDepth(y: Float) = (((y - 0.74f) / 0.26f).coerceIn(0f, 1f) * 0.6f + 0.15f)

fun Shelf.wallOf(): Wall = if (wall.isEmpty()) Wall.BACK else runCatching { Wall.valueOf(wall) }.getOrDefault(Wall.BACK)

fun normalizeShelf(s: Shelf): Shelf {
    if (s.wall.isNotEmpty()) return s
    val st = enumOr(s.style, BookcaseStyle.TALL)
    return s.copy(wall = Wall.BACK.name, posY = if (st.onWall) ((FLOOR_Y - s.posY) / FLOOR_Y).coerceAtLeast(0.2f) else 0f)
}

// ------------------------------------------------------------------------------------------------
// Szene
// ------------------------------------------------------------------------------------------------
sealed interface Ent {
    val dzKey: (Proj) -> Float
}

/** Alles, was im Raum gezeichnet wird – mit Weltgeometrie. */
class SceneEnt(
    val item: RoomItem?,
    val type: ItemType?,
    val case: BookcaseData?,
    val wall: Wall,
    /** Breite/Höhe in Wandhöhen. */
    val ww: Float,
    val wh: Float,
    /** Position: BACK → (x Welt, y Unterkante), LEFT/RIGHT → (d Welt, y), FLOOR/CEILING → (x, d). */
    val a: Float,
    val b: Float,
    val elev: Float,
    val flip: Boolean,
    val decal: Boolean,
)

fun buildScene(room: ReadingRoom, items: List<RoomItem>, cases: List<BookcaseData>): List<SceneEnt> {
    val w = room.roomWidth
    val dpt = room.roomDepth
    val out = ArrayList<SceneEnt>()
    for (it in normalizeItems(items)) {
        val t = itemTypeOf(it.type) ?: continue
        var ww = t.w * WORLD_K * it.scale
        val wh = t.h * WORLD_K * it.scale
        val wall = it.wallOf()
        if (t == ItemType.STRING_LIGHTS) ww = t.w * WORLD_K * it.scale
        val (a, b) = when (wall) {
            Wall.BACK -> it.x * w to it.y
            Wall.LEFT, Wall.RIGHT -> it.x * dpt to it.y
            Wall.FLOOR, Wall.CEILING -> it.x * w to it.y * dpt
        }
        out += SceneEnt(it, t, null, wall, ww, wh, a, b, it.elev, it.flip, t.layer == ItemLayer.DECAL)
    }
    for (c in cases) {
        val s = normalizeShelf(c.shelf)
        if (s.posX < 0f) continue
        val st = enumOr(s.style, BookcaseStyle.TALL)
        val wall = s.wallOf()
        val (a, b) = when (wall) {
            Wall.BACK -> s.posX * w to s.posY
            Wall.LEFT, Wall.RIGHT -> s.posX * dpt to s.posY
            else -> s.posX * w to s.posY * dpt
        }
        out += SceneEnt(null, null, c.copy(shelf = s), wall, st.w * WORLD_K * s.scale, st.h * WORLD_K * s.scale, a, b, 0f, false, false)
    }
    return out
}

/** Vier Weltpunkte (oben links, oben rechts, unten rechts, unten links) einer Wandfläche. */
private fun worldQuad(e: SceneEnt, w: Float): List<Triple<Float, Float, Float>>? {
    val off = 0.012f
    val y0 = e.b
    val y1 = e.b + e.wh
    return when (e.wall) {
        Wall.BACK -> {
            val x0 = e.a - e.ww / 2; val x1 = e.a + e.ww / 2
            listOf(Triple(x0, y1, off), Triple(x1, y1, off), Triple(x1, y0, off), Triple(x0, y0, off))
        }
        Wall.LEFT -> {
            val d0 = e.a - e.ww / 2; val d1 = e.a + e.ww / 2
            listOf(Triple(off, y1, d1), Triple(off, y1, d0), Triple(off, y0, d0), Triple(off, y0, d1))
        }
        Wall.RIGHT -> {
            val d0 = e.a - e.ww / 2; val d1 = e.a + e.ww / 2
            listOf(Triple(w - off, y1, d0), Triple(w - off, y1, d1), Triple(w - off, y0, d1), Triple(w - off, y0, d0))
        }
        Wall.FLOOR -> if (e.decal) {
            val x0 = e.a - e.ww / 2; val x1 = e.a + e.ww / 2
            val dd = e.ww * 0.32f
            listOf(Triple(x0, 0.002f, e.b - dd), Triple(x1, 0.002f, e.b - dd), Triple(x1, 0.002f, e.b + dd), Triple(x0, 0.002f, e.b + dd))
        } else null
        Wall.CEILING -> null
    }
}

/** Tiefe (für die Zeichenreihenfolge): größer = weiter weg. */
fun sceneDepth(e: SceneEnt, proj: Proj): Float = when (e.wall) {
    Wall.BACK -> proj.z(e.a, 0f) + 0.05f
    Wall.LEFT -> proj.z(0f, e.a) + 0.05f
    Wall.RIGHT -> proj.z(proj.w, e.a) + 0.05f
    Wall.FLOOR -> if (e.decal) 1000f else proj.z(e.a, e.b)
    Wall.CEILING -> proj.z(e.a, e.b)
}

class Placed(val quad: Array<Offset>, val local: Rect, val matrix: Matrix?, val visible: Boolean)

/** Bildschirmlage eines Objekts. Wandobjekte bekommen eine Perspektiv-Matrix, freistehende sind „Aufsteller“. */
fun place(e: SceneEnt, proj: Proj): Placed {
    val wq = worldQuad(e, proj.w)
    if (wq != null) {
        val dzs = wq.map { proj.z(it.first, it.third) }
        if (dzs.all { it < NEAR + 0.02f }) return Placed(emptyArray(), Rect.Zero, null, false)
        val pts = wq.map { (x, y, d) -> proj.p(x, y, d) }.toTypedArray()
        val local = Rect(0f, 0f, e.ww * LOCAL, e.wh * LOCAL)
        val m = Matrix()
        m.setPolyToPoly(
            floatArrayOf(0f, 0f, local.right, 0f, local.right, local.bottom, 0f, local.bottom), 0,
            floatArrayOf(pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y), 0, 4,
        )
        val visible = dzs.all { it >= NEAR + 0.02f }
        return Placed(pts, local, m, visible)
    }
    // Aufsteller (frei im Raum oder hängend)
    if (proj.z(e.a, e.b) < NEAR + 0.05f) return Placed(emptyArray(), Rect.Zero, null, false)
    val s = proj.sAt(e.a, e.b)
    val r = if (e.wall == Wall.CEILING) {
        val top = proj.p(e.a, 0.97f, e.b)
        Rect(top.x - e.ww * s / 2, top.y, top.x + e.ww * s / 2, top.y + e.wh * s)
    } else {
        val base = proj.p(e.a, e.elev, e.b)
        Rect(base.x - e.ww * s / 2, base.y - e.wh * s, base.x + e.ww * s / 2, base.y)
    }
    return Placed(arrayOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft), r, null, true)
}

private fun inQuad(p: Offset, q: Array<Offset>): Boolean {
    if (q.size != 4) return false
    var sign = 0
    for (i in 0 until 4) {
        val a = q[i]; val b = q[(i + 1) % 4]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val sgn = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (sgn != 0) { if (sign == 0) sign = sgn else if (sgn != sign) return false }
    }
    return true
}

fun hitTest3D(pos: Offset, scene: List<SceneEnt>, proj: Proj, includeDecals: Boolean): SceneEnt? {
    val sorted = scene.sortedBy { sceneDepth(it, proj) } // nächstes zuerst
    for (e in sorted) {
        if (e.decal && !includeDecals) continue
        val p = place(e, proj)
        if (!p.visible) continue
        val q = if (p.matrix == null) {
            val r = p.local.inflate(14f)
            arrayOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
        } else p.quad
        if (inQuad(pos, q)) return e
    }
    return null
}

// ------------------------------------------------------------------------------------------------
// Texturen
// ------------------------------------------------------------------------------------------------
class RoomTextures(val wall: Bitmap, val floor: Bitmap, val ceiling: Bitmap) {
    /** Mittlere Farben – füllen Lücken direkt vor der Kamera. */
    val floorAvg = avgColor(floor)
    val ceilingAvg = avgColor(ceiling)
}

private fun avgColor(b: Bitmap): Color = runCatching { Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)) }.getOrDefault(Color(0xFF3A2618))

private fun render(w: Int, h: Int, block: DrawScope.() -> Unit): Bitmap {
    val img = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) { block() }
    return img.asAndroidBitmap()
}

fun makeTextures(room: ReadingRoom): RoomTextures {
    val wallColor = Color(room.wallColor)
    val tw = (1.5f * TEX).toInt()
    val th = TEX.toInt()
    val wall = render(tw, th) {
        drawRect(Brush.verticalGradient(listOf(wallColor.darken(0.12f), wallColor, wallColor.darken(0.06f))))
        drawWallPattern(enumOr(room.wallPattern, WallPattern.PLAIN), wallColor, size.height)
        if (room.wainscot) {
            val top = size.height * 0.62f
            drawRect(Color(0xFF5B3A26), Offset(0f, top), Size(size.width, size.height - top))
            drawRect(Color(0xFF7A5236), Offset(0f, top - 5f), Size(size.width, 9f))
            val pw = size.width / 3f
            for (i in 0 until 3) {
                val r = Rect(i * pw + pw * 0.1f, top + (size.height - top) * 0.15f, (i + 1) * pw - pw * 0.1f, size.height - (size.height - top) * 0.2f)
                drawRect(Color(0xFF3F271A), r.topLeft, r.size, style = Stroke(3f))
                drawRect(Color.White.copy(alpha = 0.06f), r.topLeft + Offset(2f, 2f), r.size, style = Stroke(1.5f))
            }
        }
        // Stuckleiste & Fußleiste
        drawRect(wallColor.lighten(0.25f), Offset(0f, 0f), Size(size.width, size.height * 0.03f))
        drawRect(wallColor.darken(0.3f), Offset(0f, size.height * 0.03f), Size(size.width, size.height * 0.008f))
        drawRect(wallColor.darken(0.5f), Offset(0f, size.height * 0.955f), Size(size.width, size.height * 0.045f))
        drawRect(Color.White.copy(alpha = 0.1f), Offset(0f, size.height * 0.955f), Size(size.width, size.height * 0.004f))
    }
    val floorType = enumOr(room.floorType, FloorType.PARQUET)
    val floor = render(tw, tw) { drawFloorTile(floorType, if (room.floorTint != 0) Color(room.floorTint) else null) }
    val ceilingType = runCatching { Ceiling.valueOf(room.ceiling) }.getOrDefault(Ceiling.PLAIN)
    val ceiling = render(tw, tw) {
        val plaster = wallColor.lighten(0.55f)
        drawRect(Brush.verticalGradient(listOf(plaster.darken(0.12f), plaster)))
        when (ceilingType) {
            Ceiling.BEAMS -> for (k in 0 until 3) {
                val y = size.height * (k / 3f)
                drawRect(Color(0xFF5B3A26), Offset(0f, y), Size(size.width, size.height * 0.08f))
                drawRect(Color(0xFF7A5236), Offset(0f, y), Size(size.width, size.height * 0.015f))
            }
            Ceiling.COFFERED -> for (i in 0 until 3) for (j in 0 until 3) {
                val c = Rect(size.width * (i / 3f) + 10f, size.height * (j / 3f) + 10f, size.width * ((i + 1) / 3f) - 10f, size.height * ((j + 1) / 3f) - 10f)
                drawRect(plaster.darken(0.12f), c.topLeft, c.size)
                drawRect(plaster.lighten(0.1f), c.topLeft + Offset(8f, 8f), Size(c.width - 16f, c.height - 16f))
            }
            Ceiling.PLAIN -> Unit
        }
    }
    return RoomTextures(wall, floor, ceiling)
}

/** Boden von oben gesehen (kachelbar). */
private fun DrawScope.drawFloorTile(type: FloorType, tint: Color?) {
    val w = size.width
    val h = size.height
    val rnd = Random(7)
    when (type) {
        FloorType.PARQUET, FloorType.PLANKS -> {
            val base = tint ?: if (type == FloorType.PARQUET) Color(0xFF8A5A35) else Color(0xFFA67B52)
            drawRect(base)
            val n = if (type == FloorType.PARQUET) 12 else 6
            val pw = w / n
            for (i in 0 until n) {
                var y = -rnd.nextFloat() * h * 0.5f
                while (y < h) {
                    val len = h * (0.35f + rnd.nextFloat() * 0.4f)
                    drawRect(base.darken(rnd.nextFloat() * 0.18f).lighten(rnd.nextFloat() * 0.06f), Offset(i * pw, y), Size(pw, len))
                    drawLine(base.darken(0.45f), Offset(i * pw, y), Offset((i + 1) * pw, y), 2f)
                    repeat(3) { g -> drawLine(base.darken(0.25f).copy(alpha = 0.35f), Offset(i * pw + pw * (0.25f + g * 0.25f), y + 4f), Offset(i * pw + pw * (0.25f + g * 0.25f), y + len - 4f), 1f) }
                    y += len
                }
                drawLine(base.darken(0.5f), Offset(i * pw, 0f), Offset(i * pw, h), 2f)
            }
        }
        FloorType.CARPET -> {
            val base = tint ?: Color(0xFF7D6A5A)
            drawRect(base)
            repeat(1400) { drawCircle(base.lighten(rnd.nextFloat() * 0.15f).copy(alpha = 0.4f), 1.3f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)) }
        }
        FloorType.TILES -> {
            val a = Color(0xFFD9CFC0)
            val b = tint ?: Color(0xFF6E4E3A)
            val n = 6
            for (i in 0 until n) for (j in 0 until n) drawRect(if ((i + j) % 2 == 0) a else b, Offset(i * w / n, j * h / n), Size(w / n, h / n))
            for (i in 0..n) { drawLine(Color.Black.copy(alpha = 0.2f), Offset(i * w / n, 0f), Offset(i * w / n, h), 1.5f); drawLine(Color.Black.copy(alpha = 0.2f), Offset(0f, i * h / n), Offset(w, i * h / n), 1.5f) }
        }
        FloorType.STONE -> {
            val base = tint ?: Color(0xFF8C8478)
            drawRect(base.darken(0.3f))
            var y = 0f
            while (y < h) {
                val rh = h * (0.12f + rnd.nextFloat() * 0.1f)
                var x = -rnd.nextFloat() * w * 0.1f
                while (x < w) {
                    val sw = w * (0.12f + rnd.nextFloat() * 0.14f)
                    drawRoundRect(base.darken(rnd.nextFloat() * 0.2f), Offset(x + 3, y + 3), Size(sw - 6, rh - 6), CornerRadius(6f))
                    x += sw
                }
                y += rh
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Zeichnen
// ------------------------------------------------------------------------------------------------
private val texPaints = HashMap<Bitmap, Paint>()

private fun texPaint(tex: Bitmap) = texPaints.getOrPut(tex) {
    if (texPaints.size > 24) texPaints.clear()
    Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
}

/**
 * Zeichnet eine Fläche mit Textur. Liegt sie teilweise hinter der Kamera (beim Umdrehen),
 * wird sie rekursiv unterteilt und nur der sichtbare Teil gezeichnet.
 */
private fun DrawScope.texQuad(tex: Bitmap, world: List<Triple<Float, Float, Float>>, texRect: Rect, proj: Proj, shade: Float, level: Int = 0) {
    val zs = world.map { proj.z(it.first, it.third) }
    if (zs.all { it < NEAR }) return
    if (zs.any { it < NEAR }) {
        if (level >= 5) return
        fun mid(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>) = Triple((a.first + b.first) / 2, (a.second + b.second) / 2, (a.third + b.third) / 2)
        val (a, b, c, d) = world
        val ab = mid(a, b); val bc = mid(b, c); val cd = mid(c, d); val da = mid(d, a); val m = mid(ab, cd)
        val mx = (texRect.left + texRect.right) / 2; val my = (texRect.top + texRect.bottom) / 2
        texQuad(tex, listOf(a, ab, m, da), Rect(texRect.left, texRect.top, mx, my), proj, shade, level + 1)
        texQuad(tex, listOf(ab, b, bc, m), Rect(mx, texRect.top, texRect.right, my), proj, shade, level + 1)
        texQuad(tex, listOf(m, bc, c, cd), Rect(mx, my, texRect.right, texRect.bottom), proj, shade, level + 1)
        texQuad(tex, listOf(da, m, cd, d), Rect(texRect.left, my, mx, texRect.bottom), proj, shade, level + 1)
        return
    }
    val pts = world.map { (x, y, d) -> proj.p(x, y, d) }
    val m = Matrix()
    val ok = m.setPolyToPoly(
        floatArrayOf(texRect.left, texRect.top, texRect.right, texRect.top, texRect.right, texRect.bottom, texRect.left, texRect.bottom), 0,
        floatArrayOf(pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y), 0, 4,
    )
    if (!ok) return
    val c = drawContext.canvas.nativeCanvas
    c.save()
    c.concat(m)
    c.drawRect(texRect.left - 0.5f, texRect.top - 0.5f, texRect.right + 0.5f, texRect.bottom + 0.5f, texPaint(tex))
    if (shade > 0f) c.drawRect(texRect.left - 0.5f, texRect.top - 0.5f, texRect.right + 0.5f, texRect.bottom + 0.5f, Paint().apply { color = android.graphics.Color.argb((shade * 255).toInt(), 10, 6, 4) })
    c.restore()
}

/** Wände, Boden, Decke mit Perspektive. */
fun DrawScope.drawShell(room: ReadingRoom, tex: RoomTextures, proj: Proj) {
    val w = proj.w
    // Mit Drehung kann man auch in Richtung der offenen Vorderseite schauen – dort endet das Zimmer
    val dn = proj.depth
    drawRect(Color(0xFF120C09))
    // Hintergrund oben/unten in Decken- und Bodenfarbe (der Bereich direkt vor der Kamera wird nicht unterteilt gezeichnet)
    drawRect(tex.ceilingAvg.darken(0.12f), Offset.Zero, Size(size.width, proj.horizon))
    drawRect(tex.floorAvg.darken(0.08f), Offset(0f, proj.horizon), Size(size.width, size.height - proj.horizon))
    // Decke
    texQuad(tex.ceiling, listOf(Triple(0f, 1f, 0f), Triple(w, 1f, 0f), Triple(w, 1f, dn), Triple(0f, 1f, dn)), Rect(0f, 0f, w * TEX, dn * TEX), proj, 0.12f)
    // Boden
    texQuad(tex.floor, listOf(Triple(0f, 0f, 0f), Triple(w, 0f, 0f), Triple(w, 0f, dn), Triple(0f, 0f, dn)), Rect(0f, 0f, w * TEX, dn * TEX), proj, 0f)
    // Seitenwände (etwas dunkler für Tiefe)
    texQuad(tex.wall, listOf(Triple(0f, 1f, 0f), Triple(0f, 1f, dn), Triple(0f, 0f, dn), Triple(0f, 0f, 0f)), Rect(0f, 0f, dn * TEX, TEX), proj, 0.16f)
    texQuad(tex.wall, listOf(Triple(w, 1f, dn), Triple(w, 1f, 0f), Triple(w, 0f, 0f), Triple(w, 0f, dn)), Rect(0f, 0f, dn * TEX, TEX), proj, 0.16f)
    // Rückwand
    texQuad(tex.wall, listOf(Triple(0f, 1f, 0f), Triple(w, 1f, 0f), Triple(w, 0f, 0f), Triple(0f, 0f, 0f)), Rect(0f, 0f, w * TEX, TEX), proj, 0f)
    // Ecken-Schatten (Ambient Occlusion)
    fun edge(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, alpha: Float, width: Float) {
        if (proj.visible(a.first, a.third) && proj.visible(b.first, b.third)) drawLine(Color.Black.copy(alpha = alpha), proj.p(a.first, a.second, a.third), proj.p(b.first, b.second, b.third), width)
    }
    listOf(0f, w).forEach { x -> edge(Triple(x, 1f, 0f), Triple(x, 0f, 0f), 0.25f, 6f) }
    edge(Triple(0f, 0f, 0f), Triple(w, 0f, 0f), 0.3f, 5f)
}

private inline fun DrawScope.withPlaced(p: Placed, flip: Boolean, block: DrawScope.(Rect) -> Unit) {
    if (p.matrix != null) {
        val c = drawContext.canvas.nativeCanvas
        c.save()
        c.concat(p.matrix)
        if (flip) withTransform({ scale(-1f, 1f, p.local.center) }) { block(p.local) } else block(p.local)
        c.restore()
    } else {
        if (flip) withTransform({ scale(-1f, 1f, p.local.center) }) { block(p.local) } else block(p.local)
    }
}

fun DrawScope.drawEntStatic(e: SceneEnt, proj: Proj, ctx: DrawCtx, covers: Map<String, ImageBitmap>) {
    val p = place(e, proj)
    if (!p.visible) return
    // Schatten auf dem Boden für Aufsteller
    if (p.matrix == null && e.wall == Wall.FLOOR) {
        val s = proj.sAt(e.a, e.b)
        val g = proj.p(e.a, 0f, e.b)
        drawOval(Color.Black.copy(alpha = 0.28f), Offset(g.x - e.ww * s * 0.52f, g.y - e.ww * s * 0.06f), Size(e.ww * s * 1.04f, e.ww * s * 0.14f))
    }
    if (e.wall == Wall.CEILING) {
        val top = proj.p(e.a, 1f, e.b)
        drawLine(Color(0xFF3A2A1E), top, Offset(p.local.center.x, p.local.top), 2f)
    }
    withPlaced(p, e.flip) { r ->
        when {
            e.case != null -> drawBookcase(e.case.shelf, r, e.case.pieces, covers, alignRight = e.wall == Wall.LEFT)
            e.item != null && e.type != null -> drawItemStatic(e.item, e.type, r, ctx)
        }
    }
}

fun DrawScope.drawEntAnimated(e: SceneEnt, proj: Proj, ctx: DrawCtx, t: Float) {
    if (e.type == null || e.item == null || !e.type.animated) return
    val p = place(e, proj)
    if (!p.visible) return
    withPlaced(p, e.flip) { r -> drawItemAnimated(e.item, e.type, r, t, ctx) }
}

/** Licht & Stimmung im Raum. */
fun DrawScope.drawLighting3D(room: ReadingRoom, scene: List<SceneEnt>, proj: Proj, t: Float) {
    val tod = timeOfDay(enumOr(room.windowView, WindowView.AUTO))
    val lights = scene.filter { (it.type?.light ?: 0f) > 0f && it.item?.isOn == true }
    val hasWindow = scene.any { it.type == ItemType.WINDOW }
    val darkness = when (tod) {
        TimeOfDay.DAY -> if (hasWindow) 0.05f else 0.16f
        TimeOfDay.SUNSET -> 0.18f
        TimeOfDay.NIGHT -> 0.34f
    } + (1f - room.lightLevel) * 0.45f - lights.size * 0.02f
    drawRect(Color(0xFF0A0604).copy(alpha = darkness.coerceIn(0f, 0.8f)))
    drawRect(Color(0xFFFF9A3C).copy(alpha = 0.13f * room.lightWarmth))
    lights.forEachIndexed { i, e ->
        val p = place(e, proj)
        if (!p.visible) return@forEachIndexed
        val local = lightCenter(e.type!!, p.local).let { if (e.flip) Offset(p.local.center.x * 2 - it.x, it.y) else it }
        val c = if (p.matrix != null) { val pts = floatArrayOf(local.x, local.y); p.matrix.mapPoints(pts); Offset(pts[0], pts[1]) } else local
        val (lx, ld) = when (e.wall) { Wall.BACK -> e.a to 0f; Wall.LEFT -> 0f to e.a; Wall.RIGHT -> proj.w to e.a; else -> e.a to e.b }
        val rad = e.type.light * WORLD_K * (e.item?.scale ?: 1f) * proj.sAt(lx, ld) * 1.15f
        val flick = when (e.type) {
            ItemType.FIREPLACE, ItemType.CANDLES, ItemType.CHANDELIER -> 0.85f + 0.15f * kotlin.math.sin(t * 9f + i) * kotlin.math.sin(t * 5.3f)
            else -> 0.96f + 0.04f * kotlin.math.sin(t * 2f + i)
        }
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFB25C).copy(alpha = 0.42f * flick), Color(0xFFFFB25C).copy(alpha = 0.12f * flick), Color.Transparent), c, rad),
            rad, c, blendMode = BlendMode.Screen,
        )
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)), Offset(size.width / 2, proj.horizon), max(size.width, size.height) * 0.8f))
}

data class Effect3D(val x: Float, val y: Float, val d: Float, val startNanos: Long)

private fun DrawScope.drawHearts3D(effects: List<Effect3D>, proj: Proj) {
    val now = System.nanoTime()
    effects.forEach { e ->
        val age = (now - e.startNanos) / 1e9f
        if (age > 1.8f) return@forEach
        if (!proj.visible(e.x, e.d)) return@forEach
        val p = proj.p(e.x, e.y, e.d)
        val s = proj.sAt(e.x, e.d)
        for (k in 0 until 3) {
            val a = (age - k * 0.2f).coerceAtLeast(0f)
            val alpha = (1f - a / 1.6f).coerceIn(0f, 1f)
            val c = p + Offset((k - 1) * s * 0.06f + kotlin.math.sin(a * 5f + k) * 8f, -a * s * 0.2f)
            val hs = s * 0.03f
            val path = Path().apply {
                moveTo(c.x, c.y + hs)
                cubicTo(c.x - hs * 2f, c.y - hs * 0.4f, c.x - hs * 0.8f, c.y - hs * 1.8f, c.x, c.y - hs * 0.6f)
                cubicTo(c.x + hs * 0.8f, c.y - hs * 1.8f, c.x + hs * 2f, c.y - hs * 0.4f, c.x, c.y + hs)
                close()
            }
            drawPath(path, Color(0xFFE85A7A).copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawSelection3D(sel: SceneEnt?, proj: Proj) {
    sel ?: return
    val p = place(sel, proj)
    if (!p.visible) return
    val q = if (p.matrix == null) p.local.inflate(8f).let { arrayOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) } else p.quad
    val path = Path().apply { moveTo(q[0].x, q[0].y); for (i in 1..3) lineTo(q[i].x, q[i].y); close() }
    drawPath(path, Color.White.copy(alpha = 0.12f))
    drawPath(path, Color(0xFFFFE08A), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
    q.forEach { drawCircle(Color(0xFFFFE08A), 9f, it) }
}

/** Das begehbare Zimmer. [cam] wird beim Zeichnen gelesen (flüssige Kamerafahrten ohne Neuaufbau). */
@Composable
fun Room3D(
    room: ReadingRoom,
    items: List<RoomItem>,
    bookcases: List<BookcaseData>,
    cam: () -> Cam,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    images: Map<String, ImageBitmap> = emptyMap(),
    covers: Map<String, ImageBitmap> = emptyMap(),
    selectedKey: String? = null,
    effects: List<Effect3D> = emptyList(),
    /** Zusätzliche feste Ausstattung (z. B. Türen im Flur), gezeichnet nach den Wänden. */
    extra: (DrawScope.(Proj) -> Unit)? = null,
) {
    val time by produceState(0f, animate) {
        if (animate) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { value = (it - start) / 1_000_000_000f }
        }
    }
    val particles = rememberParticles(room.id)
    val texKey = listOf(room.wallColor, room.wallPattern, room.floorType, room.ceiling, room.wainscot, room.floorTint)
    val tex = remember(texKey) { makeTextures(room) }
    val scene = remember(room, items, bookcases) { buildScene(room, items, bookcases) }
    Box(modifier.clipToBounds()) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val proj = Proj(size.width, size.height, room.roomWidth, room.roomDepth, cam())
                val ctx = DrawCtx(room, 1f, images, particles)
                drawShell(room, tex, proj)
                extra?.invoke(this, proj)
                scene.sortedByDescending { sceneDepth(it, proj) }.forEach { drawEntStatic(it, proj, ctx, covers) }
            }
        )
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val proj = Proj(size.width, size.height, room.roomWidth, room.roomDepth, cam())
                val ctx = DrawCtx(room, 1f, images, particles)
                scene.sortedByDescending { sceneDepth(it, proj) }.forEach { drawEntAnimated(it, proj, ctx, t) }
                drawLighting3D(room, scene, proj, t)
                drawHearts3D(effects, proj)
                drawSelection3D(scene.firstOrNull { keyOf(it) == selectedKey }, proj)
            }
        )
    }
}

fun keyOf(e: SceneEnt): String = e.item?.let { "i${it.id}" } ?: e.case?.let { "s${it.shelf.id}" } ?: ""

fun defaultCam(room: ReadingRoom) = Cam(room.roomWidth / 2f, room.roomDepth + 0.3f, 0f)
fun camLimits(room: ReadingRoom) = Triple(0.38f..(room.roomWidth - 0.38f).coerceAtLeast(0.39f), 0.62f, room.roomDepth + 1.0f)
const val MAX_YAW = 1.25f

/** Zeichnet in ein lokales Rechteck, das perspektivisch auf vier Weltpunkte abgebildet wird. */
fun DrawScope.drawOnQuad(proj: Proj, world: List<Triple<Float, Float, Float>>, localW: Float, localH: Float, block: DrawScope.(Rect) -> Unit) {
    if (world.any { proj.z(it.first, it.third) < NEAR }) return
    val pts = world.map { (x, y, d) -> proj.p(x, y, d) }
    val m = Matrix()
    m.setPolyToPoly(
        floatArrayOf(0f, 0f, localW, 0f, localW, localH, 0f, localH), 0,
        floatArrayOf(pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y), 0, 4,
    )
    val c = drawContext.canvas.nativeCanvas
    c.save()
    c.concat(m)
    block(Rect(0f, 0f, localW, localH))
    c.restore()
}

fun DrawScope.drawShellTextured(tex: RoomTextures, proj: Proj) = drawShell(dummyShellRoom, tex, proj)
private val dummyShellRoom = ReadingRoom(name = "", type = "", wallColor = 0, wallPattern = "", floorType = "", windowView = "", weather = "", lightLevel = 1f, lightWarmth = 0f, furniture = "", ambientSound = "", curtainColor = 0)
