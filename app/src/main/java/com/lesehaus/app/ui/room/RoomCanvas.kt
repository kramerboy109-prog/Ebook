package com.lesehaus.app.ui.room

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import com.lesehaus.app.data.FloorType
import com.lesehaus.app.data.WallPattern
import com.lesehaus.app.data.WindowView
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.shelf.BookcaseStyle
import com.lesehaus.app.ui.shelf.Piece
import com.lesehaus.app.ui.shelf.bookcaseRect
import com.lesehaus.app.ui.shelf.drawBookcase
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class BookcaseData(val shelf: Shelf, val pieces: List<Piece>)

sealed interface RoomTarget {
    data class Item(val item: RoomItem) : RoomTarget
    data class Case(val shelf: Shelf) : RoomTarget
}

/** Kleiner Effekt, z. B. Herzchen über der Katze. */
data class RoomEffect(val pos: Offset, val startNanos: Long, val kind: Int = 0)

private sealed interface Entry {
    val sortKey: Float
    data class It(val item: RoomItem, val type: ItemType, override val sortKey: Float) : Entry
    data class Bc(val data: BookcaseData, override val sortKey: Float) : Entry
}

private class Ordered(val back: List<Entry>, val front: List<Entry>)

private fun order(items: List<RoomItem>, cases: List<BookcaseData>): Ordered {
    val back = ArrayList<Entry>()
    val decal = ArrayList<Entry>()
    val floor = ArrayList<Entry>()
    val ceiling = ArrayList<Entry>()
    for (i in items) {
        val t = itemTypeOf(i.type) ?: continue
        when (t.layer) {
            ItemLayer.WALL -> back += Entry.It(i, t, i.z.toFloat())
            ItemLayer.DECAL -> decal += Entry.It(i, t, i.z.toFloat())
            ItemLayer.FLOOR -> floor += Entry.It(i, t, i.y * 1000f + i.z * 0.01f)
            ItemLayer.CEILING -> ceiling += Entry.It(i, t, i.z.toFloat())
        }
    }
    for (c in cases) {
        if (c.shelf.posX < 0f) continue
        val st = enumOr(c.shelf.style, BookcaseStyle.TALL)
        if (st.onWall) back += Entry.Bc(c, 500f) else floor += Entry.Bc(c, c.shelf.posY * 1000f - 0.5f)
    }
    return Ordered(back.sortedBy { it.sortKey } + decal.sortedBy { it.sortKey }, floor.sortedBy { it.sortKey } + ceiling.sortedBy { it.sortKey })
}

fun entryRect(item: RoomItem, w: Float, h: Float) = itemRect(item, w, h)

/** Was liegt an dieser Stelle (vorderstes Objekt zuerst)? */
fun hitTest(pos: Offset, items: List<RoomItem>, cases: List<BookcaseData>, w: Float, h: Float, includeDecals: Boolean = true): RoomTarget? {
    val o = order(items, cases)
    for (e in (o.back + o.front).asReversed()) {
        when (e) {
            is Entry.It -> {
                if (!includeDecals && e.type.layer == ItemLayer.DECAL) continue
                val r = itemRect(e.type, e.item.x, e.item.y, e.item.scale, w, h)
                val pad = 12f
                val hit = if (e.type == ItemType.STRING_LIGHTS) Rect(r.left, r.top - pad, r.right, r.bottom + pad * 2) else r.inflate(pad)
                if (hit.contains(pos)) return RoomTarget.Item(e.item)
            }
            is Entry.Bc -> if (bookcaseRect(e.data.shelf, w, h).inflate(8f).contains(pos)) return RoomTarget.Case(e.data.shelf)
        }
    }
    return null
}

@Composable
fun rememberParticles(seed: Long): List<Triple<Float, Float, Float>> = remember(seed) {
    val rnd = Random(seed.toInt() + 7)
    List(70) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
}

@Composable
fun RoomCanvas(
    room: ReadingRoom,
    items: List<RoomItem>,
    bookcases: List<BookcaseData>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    images: Map<String, ImageBitmap> = emptyMap(),
    covers: Map<String, ImageBitmap> = emptyMap(),
    selected: RoomTarget? = null,
    effects: List<RoomEffect> = emptyList(),
    vignette: Boolean = true,
    /** Bezugshöhe für Möbelgrößen (px); null = ganze Höhe. Darüber/darunter werden Wand und Boden fortgesetzt. */
    refHeight: Float? = null,
) {
    val time by produceState(0f, animate) {
        if (animate) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { value = (it - start) / 1_000_000_000f }
        }
    }
    val particles = rememberParticles(room.id)
    val ordered = remember(items, bookcases) { order(items, bookcases) }
    Box(modifier.clipToBounds()) {
        // Ebene 1: Wand, Boden, Wanddeko (statisch)
        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawBehind {
                val f = roomFrame(size, refHeight)
                val ctx = DrawCtx(room, f.height, images, particles)
                drawRoomBackground(room, f.top + FLOOR_Y * f.height, f.height)
                translate(0f, f.top) { ordered.back.forEach { drawEntryStatic(it, ctx, covers, f.height) } }
            }
        )
        // Ebene 2: bewegte Wanddeko (Wetter, Uhrzeiger, Lichterkette)
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val f = roomFrame(size, refHeight)
                val ctx = DrawCtx(room, f.height, images, particles)
                translate(0f, f.top) { ordered.back.forEach { drawEntryAnimated(it, ctx, t, f.height) } }
            }
        )
        // Ebene 3: Möbel, Regale, Deckenleuchten (statisch)
        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawBehind {
                val f = roomFrame(size, refHeight)
                val ctx = DrawCtx(room, f.height, images, particles)
                translate(0f, f.top) { ordered.front.forEach { drawEntryStatic(it, ctx, covers, f.height) } }
            }
        )
        // Ebene 4: Feuer, Tiere, Licht, Auswahl
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val f = roomFrame(size, refHeight)
                val ctx = DrawCtx(room, f.height, images, particles)
                translate(0f, f.top) {
                    ordered.front.forEach { drawEntryAnimated(it, ctx, t, f.height) }
                    drawLighting(room, items, t, false, f.height)
                    drawEffects(effects, f.height)
                    selected?.let { drawSelection(it, items, bookcases, f.height) }
                }
                if (vignette) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), Offset(size.width / 2, f.top + f.height * 0.45f), max(size.width, size.height) * 0.8f))
            }
        )
    }
}

/** Bereich, in dem das Zimmer „steht“ (oberer Rand und Bezugshöhe). */
fun roomFrame(size: Size, refHeight: Float?): Rect {
    val h = (refHeight ?: size.height).coerceAtMost(size.height)
    val top = (size.height - h) * 0.55f
    return Rect(0f, top, size.width, top + h)
}

private fun DrawScope.drawEntryStatic(e: Entry, ctx: DrawCtx, covers: Map<String, ImageBitmap>, h: Float) {
    when (e) {
        is Entry.It -> {
            val r = itemRect(e.type, e.item.x, e.item.y, e.item.scale, size.width, h)
            withFlip(e.item.flip, r) { drawItemStatic(e.item, e.type, r, ctx) }
        }
        is Entry.Bc -> drawBookcase(e.data.shelf, bookcaseRect(e.data.shelf, size.width, h), e.data.pieces, covers)
    }
}

private fun DrawScope.drawEntryAnimated(e: Entry, ctx: DrawCtx, t: Float, h: Float) {
    if (e !is Entry.It || !e.type.animated) return
    val r = itemRect(e.type, e.item.x, e.item.y, e.item.scale, size.width, h)
    withFlip(e.item.flip, r) { drawItemAnimated(e.item, e.type, r, t, ctx) }
}

private inline fun DrawScope.withFlip(flip: Boolean, r: Rect, block: DrawScope.() -> Unit) {
    if (!flip) block() else withTransform({ scale(-1f, 1f, r.center) }) { block() }
}

fun DrawScope.drawRoomBackground(room: ReadingRoom, floorY: Float = FLOOR_Y * size.height, refH: Float = size.height) {
    val wall = Color(room.wallColor)
    val w = size.width
    val h = refH
    drawRect(Brush.verticalGradient(listOf(wall.darken(0.22f), wall, wall.darken(0.08f)), 0f, floorY), size = Size(w, floorY))
    drawWallPattern(enumOr(room.wallPattern, WallPattern.PLAIN), wall, floorY)
    // Stuckleiste unter der Decke
    drawRect(wall.darken(0.35f), Offset(0f, 0f), Size(w, h * 0.02f))
    drawRect(wall.lighten(0.12f), Offset(0f, h * 0.02f), Size(w, h * 0.008f))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), h * 0.028f, h * 0.07f), Offset(0f, h * 0.028f), Size(w, h * 0.042f))
    drawFloor(enumOr(room.floorType, FloorType.PARQUET), floorY)
    drawRect(wall.darken(0.45f), Offset(0f, floorY - h * 0.018f), Size(w, h * 0.022f))
    drawRect(Color.White.copy(alpha = 0.08f), Offset(0f, floorY - h * 0.018f), Size(w, h * 0.003f))
}

private val GlowColor = Color(0xFFFFB25C)

private fun DrawScope.drawLighting(room: ReadingRoom, items: List<RoomItem>, t: Float, vignette: Boolean, h: Float) {
    val w = size.width
    val windows = items.filter { it.type == ItemType.WINDOW.name }
    val tod = timeOfDay(enumOr(room.windowView, WindowView.AUTO))
    val lightsOn = items.filter { (itemTypeOf(it.type)?.light ?: 0f) > 0f && it.isOn }
    val darkness = when (tod) {
        TimeOfDay.DAY -> if (windows.isEmpty()) 0.15f else 0.04f
        TimeOfDay.SUNSET -> 0.18f
        TimeOfDay.NIGHT -> 0.34f
    } + (1f - room.lightLevel) * 0.45f - lightsOn.size * 0.02f
    // Abdunklung über die ganze Fläche (auch fortgesetzte Wand/Boden)
    drawRect(Color(0xFF0A0604).copy(alpha = darkness.coerceIn(0f, 0.8f)), Offset(0f, -h * 2), Size(w, h * 5))
    drawRect(Color(0xFFFF9A3C).copy(alpha = 0.14f * room.lightWarmth), Offset(0f, -h * 2), Size(w, h * 5))
    lightsOn.forEachIndexed { i, it ->
        val type = itemTypeOf(it.type)!!
        val r = itemRect(type, it.x, it.y, it.scale, w, h)
        val c = lightCenter(type, r).let { p -> if (it.flip) Offset(r.center.x * 2 - p.x, p.y) else p }
        val rad = type.light * h * it.scale
        val flick = when (type) {
            ItemType.FIREPLACE, ItemType.CANDLES -> 0.85f + 0.15f * sin(t * 9f + i) * sin(t * 5.3f)
            else -> 0.96f + 0.04f * sin(t * 2f + i)
        }
        drawCircle(
            Brush.radialGradient(listOf(GlowColor.copy(alpha = 0.45f * flick), GlowColor.copy(alpha = 0.13f * flick), Color.Transparent), c, rad),
            rad, c, blendMode = BlendMode.Screen,
        )
    }
    if (tod == TimeOfDay.DAY) windows.forEach { wi ->
        val r = windowGlass(itemRect(ItemType.WINDOW, wi.x, wi.y, wi.scale, w, h))
        val beam = Path().apply {
            moveTo(r.left, r.top); lineTo(r.right, r.top)
            lineTo(r.right + r.width * 0.7f, h * 1.2f); lineTo(r.left + r.width * 0.3f, h * 1.2f); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFFF5D6).copy(alpha = 0.16f), Color.Transparent), r.top, h), blendMode = BlendMode.Screen)
    }
    if (vignette) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), Offset(w / 2, h * 0.45f), max(w, h) * 0.8f))
}

private fun DrawScope.drawEffects(effects: List<RoomEffect>, h: Float) {
    val now = System.nanoTime()
    effects.forEach { e ->
        val age = (now - e.startNanos) / 1e9f
        if (age > 1.8f) return@forEach
        val p = Offset(e.pos.x * size.width, e.pos.y * h)
        for (k in 0 until 3) {
            val a = (age - k * 0.2f).coerceAtLeast(0f)
            val alpha = (1f - a / 1.6f).coerceIn(0f, 1f)
            val c = p + Offset((k - 1) * h * 0.03f + sin(a * 5f + k) * 8f, -a * h * 0.12f)
            drawHeart(c, h * 0.018f, Color(0xFFE85A7A).copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawHeart(c: Offset, s: Float, col: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s)
        cubicTo(c.x - s * 2f, c.y - s * 0.4f, c.x - s * 0.8f, c.y - s * 1.8f, c.x, c.y - s * 0.6f)
        cubicTo(c.x + s * 0.8f, c.y - s * 1.8f, c.x + s * 2f, c.y - s * 0.4f, c.x, c.y + s)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.drawSelection(sel: RoomTarget, items: List<RoomItem>, cases: List<BookcaseData>, h: Float) {
    val r = when (sel) {
        is RoomTarget.Item -> items.firstOrNull { it.id == sel.item.id }?.let { itemRect(it, size.width, h) }
        is RoomTarget.Case -> cases.firstOrNull { it.shelf.id == sel.shelf.id }?.let { bookcaseRect(it.shelf, size.width, h) }
    } ?: return
    val rr = r.inflate(8f)
    drawRect(Color.White.copy(alpha = 0.12f), rr.topLeft, rr.size)
    drawRect(Color(0xFFFFE08A), rr.topLeft, rr.size, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
    listOf(rr.topLeft, rr.topRight, rr.bottomLeft, rr.bottomRight).forEach { drawCircle(Color(0xFFFFE08A), 9f, it) }
}

/** Vorschau eines Zimmers (Flur, Dialoge, Editor) – räumlich, aus der Standardperspektive. */
@Composable
fun RoomScene(room: ReadingRoom, modifier: Modifier = Modifier, animate: Boolean = true, items: List<RoomItem>? = null, bookcases: List<BookcaseData> = emptyList()) {
    val setup = remember(room, items == null) { if (items == null) defaultSetup(room, emptyList()) else null }
    val r = if (setup != null) room.copy(roomWidth = setup.roomWidth) else room
    Room3D(r, items ?: setup!!.items, bookcases, cam = { defaultCam(r) }, modifier = modifier, animate = animate)
}
