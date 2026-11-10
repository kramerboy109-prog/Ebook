package com.lesehaus.app.ui.room

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lesehaus.app.data.Furniture
import com.lesehaus.app.data.Weather
import com.lesehaus.app.data.WindowView
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Höhe der Wand-/Boden-Kante als Anteil der Zimmerhöhe. */
const val FLOOR_Y = 0.70f

enum class ItemLayer { DECAL, WALL, CEILING, FLOOR }

enum class ItemCategory(val label: String) {
    FURNITURE("Möbel"), LIGHT("Licht"), WALL("Wand & Bilder"), PLANTS("Pflanzen"), DECOR("Deko"), ANIMALS("Tiere"),
}

/**
 * Alle Einrichtungsgegenstände. Breite/Höhe sind Anteile der Zimmerhöhe,
 * [light] ist der Radius des Lichtscheins (ebenfalls in Zimmerhöhen).
 */
enum class ItemType(
    val label: String,
    val category: ItemCategory,
    val layer: ItemLayer,
    val w: Float,
    val h: Float,
    val light: Float = 0f,
    val variants: List<String> = emptyList(),
    val tintable: Boolean = false,
    val animated: Boolean = false,
) {
    FIREPLACE("Kamin", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.52f, 0.40f, light = 0.75f, animated = true),
    PIANO("Klavier", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.52f, 0.31f),
    SOFA("Sofa", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.56f, 0.20f, tintable = true),
    ARMCHAIR("Ohrensessel", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.27f, 0.27f, tintable = true),
    BED("Bett", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.66f, 0.24f, tintable = true),
    DESK("Schreibtisch", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.44f, 0.24f, light = 0.32f),
    SIDE_TABLE("Beistelltisch", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.12f, 0.12f),
    GRANDFATHER_CLOCK("Standuhr", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.15f, 0.52f, animated = true),
    RECORD_PLAYER("Plattenspieler", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.24f, 0.25f, animated = true),
    LADDER("Bibliotheksleiter", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.2f, 0.58f),
    TELESCOPE("Teleskop", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.2f, 0.36f),
    GLOBE("Globus", ItemCategory.FURNITURE, ItemLayer.FLOOR, 0.12f, 0.19f),
    LAMP("Stehlampe", ItemCategory.LIGHT, ItemLayer.FLOOR, 0.11f, 0.5f, light = 0.55f),
    CHANDELIER("Kronleuchter", ItemCategory.LIGHT, ItemLayer.CEILING, 0.3f, 0.2f, light = 0.8f, animated = true),
    WALL_SCONCE("Wandleuchte", ItemCategory.LIGHT, ItemLayer.WALL, 0.07f, 0.13f, light = 0.32f),
    CANDLES("Kerzen", ItemCategory.LIGHT, ItemLayer.FLOOR, 0.1f, 0.09f, light = 0.22f, animated = true),
    STRING_LIGHTS("Lichterkette", ItemCategory.LIGHT, ItemLayer.WALL, 1.1f, 0.08f, animated = true),
    WINDOW("Fenster", ItemCategory.WALL, ItemLayer.WALL, 0.42f, 0.46f, variants = listOf("Sprossenfenster", "Rundbogen"), tintable = true, animated = true),
    PAINTING("Gemälde", ItemCategory.WALL, ItemLayer.WALL, 0.28f, 0.21f, variants = listOf("Landschaft", "Meer", "Porträt", "Abstrakt", "Sternennacht")),
    PHOTO("Eigenes Bild", ItemCategory.WALL, ItemLayer.WALL, 0.22f, 0.27f, variants = listOf("Goldrahmen", "Holzrahmen", "Schwarz")),
    MIRROR("Spiegel", ItemCategory.WALL, ItemLayer.WALL, 0.2f, 0.3f),
    CLOCK("Wanduhr", ItemCategory.WALL, ItemLayer.WALL, 0.14f, 0.14f, animated = true),
    PLANT("Zimmerpflanze", ItemCategory.PLANTS, ItemLayer.FLOOR, 0.15f, 0.26f, variants = listOf("Monstera", "Farn", "Kaktus")),
    HANGING_PLANT("Hängepflanze", ItemCategory.PLANTS, ItemLayer.CEILING, 0.15f, 0.3f),
    FLOWERS("Blumenvase", ItemCategory.PLANTS, ItemLayer.FLOOR, 0.09f, 0.15f, tintable = true),
    RUG("Teppich", ItemCategory.DECOR, ItemLayer.DECAL, 0.9f, 0.15f, variants = listOf("Oval", "Rechteckig", "Rund"), tintable = true),
    BOOKSTACK("Bücherstapel", ItemCategory.DECOR, ItemLayer.FLOOR, 0.16f, 0.12f),
    TEA("Teetasse", ItemCategory.DECOR, ItemLayer.FLOOR, 0.07f, 0.06f, animated = true),
    CAT("Katze", ItemCategory.ANIMALS, ItemLayer.FLOOR, 0.19f, 0.11f, variants = listOf("Rot getigert", "Grau", "Schwarz"), animated = true),
    DOG("Hund", ItemCategory.ANIMALS, ItemLayer.FLOOR, 0.28f, 0.15f, variants = listOf("Golden", "Dackel"), animated = true),
}

fun itemTypeOf(name: String): ItemType? = runCatching { ItemType.valueOf(name) }.getOrNull()

val RoomItem.isOn: Boolean get() = label != "off"

/** Rechteck eines Gegenstands in Pixeln. */
fun itemRect(type: ItemType, x: Float, y: Float, scale: Float, w: Float, h: Float): Rect {
    val iw = type.w * h * scale
    val ih = type.h * h * scale
    val cx = x * w
    val bottom = y * h
    return Rect(cx - iw / 2, bottom - ih, cx + iw / 2, bottom)
}

fun itemRect(item: RoomItem, w: Float, h: Float): Rect? = itemTypeOf(item.type)?.let { itemRect(it, item.x, item.y, item.scale, w, h) }

// ------------------------------------------------------------------------------------------------
// Startaufstellung aus den Zimmer-Vorlagen
// ------------------------------------------------------------------------------------------------
private fun Furniture.toItem(): ItemType? = when (this) {
    Furniture.FIREPLACE -> ItemType.FIREPLACE
    Furniture.PIANO -> ItemType.PIANO
    Furniture.SOFA -> ItemType.SOFA
    Furniture.ARMCHAIR -> ItemType.ARMCHAIR
    Furniture.BED -> ItemType.BED
    Furniture.DESK -> ItemType.DESK
    Furniture.LAMP -> ItemType.LAMP
    Furniture.PLANT -> ItemType.PLANT
    Furniture.RUG -> ItemType.RUG
    Furniture.PAINTING -> ItemType.PAINTING
    Furniture.CLOCK -> ItemType.CLOCK
    Furniture.CANDLES -> ItemType.CANDLES
    Furniture.CAT -> ItemType.CAT
    Furniture.GLOBE -> ItemType.GLOBE
    Furniture.STRING_LIGHTS -> ItemType.STRING_LIGHTS
    Furniture.BOOKSTACK -> ItemType.BOOKSTACK
    Furniture.TEA -> ItemType.TEA
}

data class DefaultSetup(val items: List<RoomItem>, val shelves: List<Shelf>, val roomWidth: Float)

/** Erzeugt eine stimmige Startaufstellung für ein Zimmer (inkl. Platzierung der Regale). */
fun defaultSetup(room: ReadingRoom, shelves: List<Shelf>): DefaultSetup {
    val furniture = room.furnitureList.mapNotNull { runCatching { Furniture.valueOf(it) }.getOrNull()?.toItem() }.toSet()
    val backOrder = listOf(ItemType.PLANT, ItemType.FIREPLACE, ItemType.PIANO, ItemType.BED, ItemType.DESK, ItemType.SOFA, ItemType.ARMCHAIR, ItemType.GLOBE, ItemType.LAMP)
    val back = backOrder.filter { it in furniture }
    // Reihenfolge an der Rückwand: Pflanze, Regale, große Möbel, Sitzmöbel, Lampe
    data class Slot(val type: ItemType?, val shelf: Shelf?, val width: Float)
    val slots = ArrayList<Slot>()
    back.filter { it == ItemType.PLANT }.forEach { slots += Slot(it, null, it.w) }
    shelves.filter { it.posX < 0f }.forEachIndexed { i, s ->
        val st = enumOr(s.style, com.lesehaus.app.ui.shelf.BookcaseStyle.TALL)
        slots += Slot(null, s, st.w * s.scale)
        if (i == 0) back.filter { it == ItemType.FIREPLACE || it == ItemType.PIANO || it == ItemType.BED || it == ItemType.DESK }.forEach { slots += Slot(it, null, it.w) }
    }
    if (shelves.none { it.posX < 0f }) back.filter { it == ItemType.FIREPLACE || it == ItemType.PIANO || it == ItemType.BED || it == ItemType.DESK }.forEach { slots += Slot(it, null, it.w) }
    back.filter { it == ItemType.SOFA || it == ItemType.ARMCHAIR || it == ItemType.GLOBE || it == ItemType.LAMP }.forEach { slots += Slot(it, null, it.w) }
    val gap = 0.07f
    val needed = slots.sumOf { it.width.toDouble() }.toFloat() + gap * (slots.size + 1) + 0.25f
    val roomWidth = max(room.roomWidth, needed).coerceAtMost(4f)
    val spare = (roomWidth - (needed - 0.25f)).coerceAtLeast(0f)
    var x = gap + spare / 2f
    val items = ArrayList<RoomItem>()
    val placedShelves = ArrayList<Shelf>()
    val y = FLOOR_Y + 0.04f
    var z = 0
    val tall = ArrayList<Pair<Float, Float>>() // belegte Wandbereiche (in Zimmerhöhen)
    for (s in slots) {
        val cx = x + s.width / 2f
        if (s.type != null) {
            items += RoomItem(roomId = room.id, type = s.type.name, x = cx / roomWidth, y = y, z = z++)
            if (s.type.h > 0.3f) tall += (x to x + s.width)
        } else if (s.shelf != null) {
            placedShelves += s.shelf.copy(posX = cx / roomWidth, posY = y)
            tall += (x to x + s.width)
        }
        x += s.width + gap
    }
    fun freeX(width: Float, avoid: List<Pair<Float, Float>>, prefer: Float): Float {
        val candidates = (0..40).map { it / 40f * (roomWidth - width) + width / 2 }.sortedBy { abs(it - prefer) }
        return candidates.firstOrNull { c -> avoid.none { (a, b) -> c + width / 2 > a && c - width / 2 < b } } ?: prefer
    }
    // Fenster über Sitzmöbeln oder an freier Wand
    val wallUsed = ArrayList(tall)
    if (enumOr(room.windowView, WindowView.AUTO) != WindowView.NONE) {
        val seat = items.firstOrNull { it.type == ItemType.SOFA.name || it.type == ItemType.ARMCHAIR.name || it.type == ItemType.BED.name }
        val wx = freeX(ItemType.WINDOW.w + 0.15f, wallUsed, seat?.x?.times(roomWidth) ?: (roomWidth * 0.6f))
        items += RoomItem(roomId = room.id, type = ItemType.WINDOW.name, x = wx / roomWidth, y = 0.56f, z = z++)
        wallUsed += (wx - 0.3f to wx + 0.3f)
    }
    if (ItemType.PAINTING in furniture) {
        val anchor = items.firstOrNull { it.type in setOf("FIREPLACE", "SOFA", "PIANO", "BED", "DESK") }
        val windowX = items.firstOrNull { it.type == ItemType.WINDOW.name }?.x?.times(roomWidth)
        var px = anchor?.x?.times(roomWidth) ?: freeX(0.3f, wallUsed, roomWidth * 0.3f)
        if (windowX != null && abs(px - windowX) < 0.42f) px = freeX(0.3f, wallUsed, roomWidth * 0.3f)
        val top = if (anchor?.type == "FIREPLACE") 0.30f else 0.36f
        items += RoomItem(roomId = room.id, type = ItemType.PAINTING.name, x = px / roomWidth, y = top, z = z++, variant = abs(room.name.hashCode()) % 5)
        wallUsed += (px - 0.15f to px + 0.15f)
    }
    if (ItemType.CLOCK in furniture) {
        val cx = freeX(0.16f, wallUsed, roomWidth * 0.12f)
        items += RoomItem(roomId = room.id, type = ItemType.CLOCK.name, x = cx / roomWidth, y = 0.3f, z = z++)
        wallUsed += (cx - 0.1f to cx + 0.1f)
    }
    if (ItemType.STRING_LIGHTS in furniture) {
        items += RoomItem(roomId = room.id, type = ItemType.STRING_LIGHTS.name, x = 0.5f, y = 0.12f, scale = roomWidth / ItemType.STRING_LIGHTS.w, z = z++)
    }
    // eine Wandleuchte neben dem ersten Regal
    placedShelves.firstOrNull()?.let { s ->
        val st = enumOr(s.style, com.lesehaus.app.ui.shelf.BookcaseStyle.TALL)
        val sx = s.posX * roomWidth + st.w * s.scale / 2 + 0.06f
        if (sx < roomWidth - 0.05f) items += RoomItem(roomId = room.id, type = ItemType.WALL_SCONCE.name, x = sx / roomWidth, y = 0.3f, z = z++)
    }
    // Teppich, Tiere, Kleinkram vorne
    val rugCx = items.firstOrNull { it.type == "FIREPLACE" || it.type == "SOFA" || it.type == "ARMCHAIR" }?.x ?: 0.5f
    if (ItemType.RUG in furniture) items += RoomItem(roomId = room.id, type = ItemType.RUG.name, x = rugCx, y = 0.95f, z = -10)
    if (ItemType.CAT in furniture) items += RoomItem(roomId = room.id, type = ItemType.CAT.name, x = (rugCx * roomWidth - 0.18f).coerceAtLeast(0.15f) / roomWidth, y = 0.9f, z = z++)
    val fireplace = items.firstOrNull { it.type == "FIREPLACE" }
    val seat = items.firstOrNull { it.type == "ARMCHAIR" } ?: items.firstOrNull { it.type == "SOFA" }
    val needsTable = (ItemType.CANDLES in furniture && fireplace == null) || ItemType.TEA in furniture
    var tableX = 0f
    if (needsTable) {
        val sw = seat?.let { itemTypeOf(it.type)!!.w } ?: 0f
        tableX = seat?.let { it.x * roomWidth + sw / 2 + 0.05f } ?: (roomWidth * 0.75f)
        tableX = tableX.coerceAtMost(roomWidth - 0.08f)
        items += RoomItem(roomId = room.id, type = ItemType.SIDE_TABLE.name, x = tableX / roomWidth, y = 0.82f, z = z++)
    }
    val tableTop = 0.82f - ItemType.SIDE_TABLE.h * 0.96f
    if (ItemType.CANDLES in furniture) {
        if (fireplace != null) {
            val fw = ItemType.FIREPLACE.w
            val mantle = y - ItemType.FIREPLACE.h + 0.004f
            items += RoomItem(roomId = room.id, type = ItemType.CANDLES.name, x = (fireplace.x * roomWidth - fw * 0.34f) / roomWidth, y = mantle, scale = 0.8f, z = z++)
            items += RoomItem(roomId = room.id, type = ItemType.CANDLES.name, x = (fireplace.x * roomWidth + fw * 0.36f) / roomWidth, y = mantle, scale = 0.7f, flip = true, z = z++)
        } else items += RoomItem(roomId = room.id, type = ItemType.CANDLES.name, x = (tableX - 0.02f) / roomWidth, y = tableTop, scale = 0.7f, z = z++)
    }
    if (ItemType.TEA in furniture) items += RoomItem(roomId = room.id, type = ItemType.TEA.name, x = (tableX + 0.025f) / roomWidth, y = tableTop, z = z++)
    if (ItemType.BOOKSTACK in furniture) items += RoomItem(roomId = room.id, type = ItemType.BOOKSTACK.name, x = (roomWidth - 0.2f) / roomWidth, y = 0.97f, z = z++)
    return DefaultSetup(items, placedShelves, roomWidth)
}


/** Freie Stelle für ein neues Objekt suchen (in Anteilen der Zimmerbreite). */
fun freeSpot(widthH: Float, roomWidth: Float, occupied: List<Pair<Float, Float>>): Float {
    for (i in 0..60) {
        val c = (i / 60f) * (roomWidth - widthH) + widthH / 2
        if (occupied.none { (a, b) -> c + widthH / 2 > a && c - widthH / 2 < b }) return c / roomWidth
    }
    return 0.5f
}

// ------------------------------------------------------------------------------------------------
// Zeichnen
// ------------------------------------------------------------------------------------------------
class DrawCtx(
    val room: ReadingRoom,
    val h: Float,
    val images: Map<String, ImageBitmap> = emptyMap(),
    val particles: List<Triple<Float, Float, Float>> = emptyList(),
)

private fun fabricOf(item: RoomItem, room: ReadingRoom) = Color(if (item.color != 0) item.color else room.curtainColor)

/** Fensterscheibe innerhalb des Fenster-Rechtecks (außen sind Vorhänge). */
fun windowGlass(r: Rect) = Rect(r.left + r.width * 0.2f, r.top + r.height * 0.12f, r.right - r.width * 0.2f, r.bottom - r.height * 0.06f)

/** Statischer Teil eines Gegenstands. */
fun DrawScope.drawItemStatic(item: RoomItem, type: ItemType, r: Rect, ctx: DrawCtx) {
    val room = ctx.room
    when (type) {
        ItemType.FIREPLACE -> drawFireplace(r)
        ItemType.PIANO -> drawPiano(r)
        ItemType.SOFA -> drawSofa(r, fabricOf(item, room))
        ItemType.ARMCHAIR -> drawArmchair(r, fabricOf(item, room))
        ItemType.BED -> drawBed(r, fabricOf(item, room))
        ItemType.DESK -> drawDesk(r)
        ItemType.SIDE_TABLE -> drawSideTable(r)
        ItemType.LAMP -> drawLamp(r)
        ItemType.GLOBE -> drawGlobe(r)
        ItemType.BOOKSTACK -> drawBookstack(r)
        ItemType.PLANT -> when (item.variant) { 1 -> drawFern(r); 2 -> drawCactus(r); else -> drawPlant(r) }
        ItemType.PAINTING -> drawPaintingVariant(r, item.variant)
        ItemType.CLOCK -> drawClockFace(r.center, min(r.width, r.height) / 2.3f)
        ItemType.RUG -> drawRugItem(r, fabricOf(item, room), item.variant)
        ItemType.WINDOW -> drawWindowItem(r, room, item)
        ItemType.PHOTO -> drawPhoto(r, item, ctx.images[item.imagePath])
        ItemType.MIRROR -> drawMirror(r)
        ItemType.CHANDELIER -> drawChandelier(r, item.isOn)
        ItemType.WALL_SCONCE -> drawSconce(r, item.isOn)
        ItemType.GRANDFATHER_CLOCK -> drawGrandfatherClock(r)
        ItemType.RECORD_PLAYER -> drawRecordPlayer(r)
        ItemType.LADDER -> drawLadder(r)
        ItemType.TELESCOPE -> drawTelescope(r)
        ItemType.HANGING_PLANT -> drawHangingPlant(r)
        ItemType.FLOWERS -> drawFlowers(r, fabricOf(item, room))
        ItemType.CANDLES -> drawCandles(r)
        ItemType.TEA -> drawTeaCup(r)
        ItemType.STRING_LIGHTS, ItemType.CAT, ItemType.DOG -> Unit
    }
}

/** Bewegter Teil (Feuer, Flammen, Zeiger, Wetter, Tiere …). */
fun DrawScope.drawItemAnimated(item: RoomItem, type: ItemType, r: Rect, t: Float, ctx: DrawCtx) {
    when (type) {
        ItemType.FIREPLACE -> {
            val o = fireOpening(r)
            if (item.isOn) drawFire(Rect(o.left + o.width * 0.15f, o.top + o.height * 0.25f, o.right - o.width * 0.15f, o.bottom - o.height * 0.1f), t)
        }
        ItemType.CANDLES -> if (item.isOn) candleTips(r).forEachIndexed { i, p -> drawFlame(p, r.width * 0.09f, t + i * 1.7f + item.id) }
        ItemType.CHANDELIER -> if (item.isOn) chandelierTips(r).forEachIndexed { i, p -> drawFlame(p, r.width * 0.035f, t + i * 1.3f) }
        ItemType.CLOCK -> drawClockHands(r.center, min(r.width, r.height) / 2.3f)
        ItemType.GRANDFATHER_CLOCK -> {
            val face = Rect(r.left + r.width * 0.18f, r.top + r.height * 0.09f, r.right - r.width * 0.18f, r.top + r.height * 0.09f + r.width * 0.64f)
            drawClockHands(face.center, face.width / 2.3f)
            // Pendel
            val pivot = Offset(r.center.x, r.top + r.height * 0.42f)
            val angle = sin(t * PI.toFloat()) * 12f
            rotate(angle, pivot) {
                drawLine(Color(0xFFC9A45A), pivot, pivot + Offset(0f, r.height * 0.34f), r.width * 0.03f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF2D99A), Color(0xFFB08D57)), pivot + Offset(0f, r.height * 0.34f), r.width * 0.12f), r.width * 0.11f, pivot + Offset(0f, r.height * 0.34f))
            }
        }
        ItemType.RECORD_PLAYER -> {
            val c = Offset(r.left + r.width * 0.45f, r.top + r.height * 0.18f)
            val rad = r.width * 0.32f
            val spin = if (item.label == "on") t * 200f else 0f
            withTransform({ scale(1f, 0.32f, c) }) {
                drawCircle(Color(0xFF151515), rad, c)
                for (k in 1..5) drawCircle(Color(0xFF2A2A2A), rad * (0.35f + k * 0.12f), c, style = Stroke(1f))
                rotate(spin, c) {
                    drawCircle(Color(0xFF9B2F3F), rad * 0.28f, c)
                    drawLine(Color.White.copy(alpha = 0.25f), c, c + Offset(rad * 0.9f, 0f), 2f)
                }
            }
            if (item.label == "on") {
                for (k in 0 until 3) {
                    val ph = (t * 0.4f + k / 3f) % 1f
                    drawNote(Offset(r.left + r.width * (0.5f + 0.3f * sin(ph * 6f + k)), r.top - ph * r.height * 0.9f), r.width * 0.06f, Color(0xFFF6E7C4).copy(alpha = 1f - ph))
                }
            }
        }
        ItemType.WINDOW -> drawWindowWeather(windowGlass(r), ctx.room, t, ctx.particles)
        ItemType.STRING_LIGHTS -> drawStringLights(r, t, item.isOn)
        ItemType.CAT -> drawCatVariant(r, t, item.variant)
        ItemType.DOG -> drawDog(r, t, item.variant)
        ItemType.TEA -> {
            val s = r.width * 0.3f
            for (k in 0 until 3) {
                val ph = ((t * 0.35f + k / 3f) % 1f)
                val y = r.top - ph * s * 4f
                val x = r.center.x + sin(ph * 6f + k) * s * 0.4f
                drawCircle(Color.White.copy(alpha = 0.25f * (1 - ph)), s * (0.25f + ph * 0.5f), Offset(x, y))
            }
        }
        else -> Unit
    }
}

/** Mittelpunkt des Lichtscheins. */
fun lightCenter(type: ItemType, r: Rect): Offset = when (type) {
    ItemType.FIREPLACE -> fireOpening(r).center
    ItemType.LAMP -> Offset(r.center.x, r.top + r.height * 0.12f)
    ItemType.DESK -> Offset(r.left + r.width * 0.2f, r.top - r.height * 0.2f)
    ItemType.CHANDELIER -> Offset(r.center.x, r.top + r.height * 0.5f)
    ItemType.WALL_SCONCE -> Offset(r.center.x, r.top + r.height * 0.25f)
    ItemType.CANDLES -> Offset(r.center.x, r.top)
    else -> r.center
}

// ---------- einzelne neue Zeichnungen ----------
private fun DrawScope.drawClockHands(c: Offset, r: Float) {
    val cal = Calendar.getInstance()
    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
    val mn = cal.get(Calendar.MINUTE) + sec / 60f
    val hr = (cal.get(Calendar.HOUR) + mn / 60f)
    fun hand(angle: Float, len: Float, width: Float, color: Color) {
        val a = angle * 2 * PI
        drawLine(color, c, c + Offset((sin(a) * len).toFloat(), (-cos(a) * len).toFloat()), width, StrokeCap.Round)
    }
    val k = (r / 40f).coerceIn(0.6f, 3f)
    hand(hr / 12f, r * 0.5f, 3.5f * k, Color(0xFF2B2018))
    hand(mn / 60f, r * 0.75f, 2.5f * k, Color(0xFF2B2018))
    hand(sec / 60f, r * 0.8f, 1.2f * k, Color(0xFF9B2F3F))
    drawCircle(Color(0xFF2B2018), 3f * k, c)
}

private fun DrawScope.drawNote(p: Offset, s: Float, c: Color) {
    drawCircle(c, s * 0.5f, p)
    drawLine(c, p + Offset(s * 0.45f, 0f), p + Offset(s * 0.45f, -s * 1.8f), s * 0.18f)
    drawLine(c, p + Offset(s * 0.45f, -s * 1.8f), p + Offset(s * 1.1f, -s * 1.3f), s * 0.18f)
}

fun DrawScope.drawWindowWeather(r: Rect, room: ReadingRoom, t: Float, particles: List<Triple<Float, Float, Float>>) {
    val weather = enumOr(room.weather, Weather.CLEAR)
    val tod = timeOfDay(enumOr(room.windowView, WindowView.AUTO))
    clipRect(r.left, r.top, r.right, r.bottom) {
        when (weather) {
            Weather.RAIN, Weather.STORM -> {
                particles.forEach { (a, b, c) ->
                    val speed = 0.9f + c * 0.6f
                    val y = r.top + ((b + t * speed) % 1f) * r.height
                    val x = r.left + a * r.width + (y - r.top) * 0.08f
                    drawLine(Color(0xFFCFE0F0).copy(alpha = 0.55f), Offset(x, y), Offset(x + r.width * 0.01f, y + r.height * 0.07f), 1.6f)
                }
                if (weather == Weather.STORM) {
                    val phase = (t % 7f)
                    if (phase in 0f..0.12f || phase in 0.2f..0.26f) drawRect(Color.White.copy(alpha = 0.55f), r.topLeft, r.size)
                }
                particles.take(14).forEach { (a, b, c) ->
                    val y = r.top + ((b + t * 0.04f * (0.5f + c)) % 1f) * r.height
                    drawCircle(Color.White.copy(alpha = 0.25f), 2f + c * 2f, Offset(r.left + a * r.width, y))
                }
            }
            Weather.SNOW -> particles.forEach { (a, b, c) ->
                val y = r.top + ((b + t * (0.05f + c * 0.06f)) % 1f) * r.height
                val x = r.left + a * r.width + sin(t * 1.3f + c * 10) * r.width * 0.03f
                drawCircle(Color.White.copy(alpha = 0.85f), 1.5f + c * 3f, Offset(x, y))
            }
            Weather.CLEAR -> if (tod == TimeOfDay.NIGHT) particles.take(12).forEach { (a, b, c) ->
                val alpha = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (1f + c * 2f) + a * 20))
                drawCircle(Color.White.copy(alpha = alpha), 1.8f, Offset(r.left + a * r.width, r.top + b * r.height * 0.6f))
            }
        }
    }
}

private fun DrawScope.drawWindowItem(r: Rect, room: ReadingRoom, item: RoomItem) {
    val glass = windowGlass(r)
    val tinted = if (item.color != 0) room.copy(curtainColor = item.color) else room
    if (item.variant == 1) {
        // Rundbogenfenster: oben abgerundet
        val arch = Path().apply {
            moveTo(glass.left, glass.bottom)
            lineTo(glass.left, glass.top + glass.width / 2)
            arcTo(Rect(glass.left, glass.top, glass.right, glass.top + glass.width), 180f, 180f, false)
            lineTo(glass.right, glass.bottom)
            close()
        }
        clipPath(arch) { drawWindowStatic(tinted, glass) }
        drawPath(arch, Color(0xFFD6CBB8), style = Stroke(glass.width * 0.045f))
        drawRect(Color(0xFFE2D8C6), Offset(glass.left - glass.width * 0.08f, glass.bottom), Size(glass.width * 1.16f, glass.height * 0.045f))
    } else drawWindowStatic(tinted, glass)
}

private fun DrawScope.drawPaintingVariant(r: Rect, v: Int) {
    if (v == 0) { drawPainting(r, dummyRoom); return }
    val gold = Color(0xFFC09A55)
    drawRect(Color.Black.copy(alpha = 0.3f), r.topLeft + Offset(4f, 6f), r.size)
    drawRect(Brush.linearGradient(listOf(gold.lighten(0.2f), gold.darken(0.3f), gold), r.topLeft, r.bottomRight), r.topLeft, r.size)
    val i = Rect(r.left + r.width * 0.09f, r.top + r.height * 0.1f, r.right - r.width * 0.09f, r.bottom - r.height * 0.1f)
    clipRect(i.left, i.top, i.right, i.bottom) {
        when (v) {
            1 -> { // Meer mit Segelboot
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF2D3A0), Color(0xFF9CC2CF)), i.top, i.bottom), i.topLeft, i.size)
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3D6E8C), Color(0xFF1F3F57)), i.top + i.height * 0.55f, i.bottom), Offset(i.left, i.top + i.height * 0.55f), Size(i.width, i.height * 0.45f))
                drawCircle(Color(0xFFFFE3A8), i.width * 0.08f, Offset(i.left + i.width * 0.7f, i.top + i.height * 0.48f))
                val boat = Path().apply { moveTo(i.left + i.width * 0.3f, i.top + i.height * 0.6f); lineTo(i.left + i.width * 0.45f, i.top + i.height * 0.6f); lineTo(i.left + i.width * 0.42f, i.top + i.height * 0.66f); lineTo(i.left + i.width * 0.33f, i.top + i.height * 0.66f); close() }
                drawPath(boat, Color(0xFF3B2614))
                val sail = Path().apply { moveTo(i.left + i.width * 0.38f, i.top + i.height * 0.3f); lineTo(i.left + i.width * 0.38f, i.top + i.height * 0.58f); lineTo(i.left + i.width * 0.47f, i.top + i.height * 0.58f); close() }
                drawPath(sail, Color(0xFFF7F0E2))
            }
            2 -> { // Porträt-Silhouette
                drawRect(Brush.radialGradient(listOf(Color(0xFF6E5A4A), Color(0xFF2E241D)), i.center, i.width), i.topLeft, i.size)
                drawCircle(Color(0xFF1A1310), i.width * 0.2f, Offset(i.center.x, i.top + i.height * 0.38f))
                drawOval(Color(0xFF1A1310), Offset(i.center.x - i.width * 0.36f, i.top + i.height * 0.6f), Size(i.width * 0.72f, i.height * 0.6f))
            }
            3 -> { // Abstrakt
                drawRect(Color(0xFFEDE3CF), i.topLeft, i.size)
                drawCircle(Color(0xFFC0392B), i.width * 0.22f, Offset(i.left + i.width * 0.35f, i.top + i.height * 0.4f))
                drawRect(Color(0xFF2C3E66), Offset(i.left + i.width * 0.5f, i.top + i.height * 0.2f), Size(i.width * 0.35f, i.height * 0.5f))
                drawRect(Color(0xFFE0A93B), Offset(i.left + i.width * 0.15f, i.top + i.height * 0.7f), Size(i.width * 0.6f, i.height * 0.12f))
            }
            else -> { // Sternennacht
                drawRect(Brush.verticalGradient(listOf(Color(0xFF14204A), Color(0xFF3A5A9C)), i.top, i.bottom), i.topLeft, i.size)
                for (k in 0 until 6) drawArc(Color(0xFF9CC2E8).copy(alpha = 0.6f), 180f + k * 20, 200f, false, Offset(i.left + i.width * (0.1f + k * 0.12f), i.top + i.height * 0.15f), Size(i.width * 0.3f, i.height * 0.25f), style = Stroke(2f))
                drawCircle(Color(0xFFFFE27A), i.width * 0.08f, Offset(i.left + i.width * 0.8f, i.top + i.height * 0.2f))
                repeat(7) { k -> drawCircle(Color(0xFFFFF2B0), i.width * 0.025f, Offset(i.left + i.width * (0.1f + k * 0.12f), i.top + i.height * (0.15f + (k % 3) * 0.1f))) }
                val cyp = Path().apply { moveTo(i.left + i.width * 0.2f, i.bottom); quadraticTo(i.left + i.width * 0.12f, i.top + i.height * 0.4f, i.left + i.width * 0.25f, i.top + i.height * 0.25f); quadraticTo(i.left + i.width * 0.3f, i.top + i.height * 0.6f, i.left + i.width * 0.32f, i.bottom); close() }
                drawPath(cyp, Color(0xFF13261A))
            }
        }
    }
    drawRect(gold.darken(0.5f), i.topLeft, i.size, style = Stroke(2f))
}

private val dummyRoom = ReadingRoom(name = "", type = "", wallColor = 0, wallPattern = "", floorType = "", windowView = "", weather = "", lightLevel = 1f, lightWarmth = 0f, furniture = "", ambientSound = "", curtainColor = 0)

private fun DrawScope.drawPhoto(r: Rect, item: RoomItem, img: ImageBitmap?) {
    val frame = when (item.variant) { 1 -> Color(0xFF6B4630); 2 -> Color(0xFF1C1C1C); else -> Color(0xFFC09A55) }
    drawRect(Color.Black.copy(alpha = 0.3f), r.topLeft + Offset(4f, 6f), r.size)
    drawRect(Brush.linearGradient(listOf(frame.lighten(0.2f), frame.darken(0.25f), frame), r.topLeft, r.bottomRight), r.topLeft, r.size)
    val mat = Rect(r.left + r.width * 0.07f, r.top + r.width * 0.07f, r.right - r.width * 0.07f, r.bottom - r.width * 0.07f)
    drawRect(Color(0xFFF6F1E6), mat.topLeft, mat.size)
    val i = Rect(mat.left + mat.width * 0.08f, mat.top + mat.width * 0.08f, mat.right - mat.width * 0.08f, mat.bottom - mat.width * 0.08f)
    if (img != null) {
        // „Center crop“
        val ia = img.width.toFloat() / img.height
        val ra = i.width / i.height
        val (sw, sh) = if (ia > ra) (img.height * ra).toInt() to img.height else img.width to (img.width / ra).toInt()
        drawImage(img, IntOffset((img.width - sw) / 2, (img.height - sh) / 2), IntSize(sw, sh), IntOffset(i.left.toInt(), i.top.toInt()), IntSize(i.width.toInt(), i.height.toInt()))
    } else {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFD9C9A8), Color(0xFFB59E7A)), i.top, i.bottom), i.topLeft, i.size)
        drawCircle(Color(0xFFF2E3C6), i.width * 0.12f, Offset(i.center.x, i.top + i.height * 0.38f))
        drawOval(Color(0xFFF2E3C6), Offset(i.center.x - i.width * 0.25f, i.top + i.height * 0.55f), Size(i.width * 0.5f, i.height * 0.5f))
    }
    drawRect(Color.Black.copy(alpha = 0.15f), i.topLeft, i.size, style = Stroke(1.5f))
}

private fun DrawScope.drawMirror(r: Rect) {
    val gold = Color(0xFFC09A55)
    drawOval(Color.Black.copy(alpha = 0.3f), r.topLeft + Offset(4f, 6f), r.size)
    drawOval(Brush.linearGradient(listOf(gold.lighten(0.25f), gold.darken(0.3f), gold), r.topLeft, r.bottomRight), r.topLeft, r.size)
    val i = Rect(r.left + r.width * 0.1f, r.top + r.width * 0.1f, r.right - r.width * 0.1f, r.bottom - r.width * 0.1f)
    drawOval(Brush.linearGradient(listOf(Color(0xFFD7E4E8), Color(0xFF8FA6AD), Color(0xFFC9D8DC)), i.topLeft, i.bottomRight), i.topLeft, i.size)
    drawLine(Color.White.copy(alpha = 0.5f), Offset(i.left + i.width * 0.25f, i.top + i.height * 0.2f), Offset(i.left + i.width * 0.45f, i.top + i.height * 0.08f), 4f)
    drawLine(Color.White.copy(alpha = 0.35f), Offset(i.left + i.width * 0.25f, i.top + i.height * 0.32f), Offset(i.left + i.width * 0.6f, i.top + i.height * 0.12f), 3f)
    drawCircle(gold, r.width * 0.07f, Offset(r.center.x, r.top + r.width * 0.02f))
}

private fun DrawScope.drawChandelier(r: Rect, on: Boolean) {
    val brass = Color(0xFFC9A45A)
    drawLine(brass.darken(0.3f), Offset(r.center.x, 0f), Offset(r.center.x, r.top + r.height * 0.3f), r.width * 0.02f)
    drawCircle(brass, r.width * 0.06f, Offset(r.center.x, r.top + r.height * 0.35f))
    drawArc(brass, 0f, 180f, false, Offset(r.left + r.width * 0.05f, r.top + r.height * 0.2f), Size(r.width * 0.9f, r.height * 0.6f), style = Stroke(r.width * 0.025f))
    drawArc(brass.darken(0.15f), 0f, 180f, false, Offset(r.left + r.width * 0.22f, r.top + r.height * 0.3f), Size(r.width * 0.56f, r.height * 0.45f), style = Stroke(r.width * 0.02f))
    chandelierTips(r).forEach { p ->
        drawRect(Color(0xFFF7EEDB), Offset(p.x - r.width * 0.012f, p.y + r.width * 0.02f), Size(r.width * 0.024f, r.height * 0.15f))
        drawOval(brass, Offset(p.x - r.width * 0.035f, p.y + r.width * 0.02f + r.height * 0.15f), Size(r.width * 0.07f, r.height * 0.05f))
    }
    for (k in 0 until 5) {
        val x = r.left + r.width * (0.2f + k * 0.15f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top + r.height * 0.7f), Offset(x, r.top + r.height * 0.88f), 1.5f)
        drawCircle(Color(0xFFE8F4FF).copy(alpha = if (on) 0.95f else 0.6f), r.width * 0.018f, Offset(x, r.top + r.height * 0.9f))
    }
}

private fun chandelierTips(r: Rect): List<Offset> = listOf(0.08f, 0.3f, 0.5f, 0.7f, 0.92f).map { fx ->
    val dy = if (fx == 0.5f) 0.12f else if (fx == 0.3f || fx == 0.7f) 0.35f else 0.42f
    Offset(r.left + r.width * fx, r.top + r.height * dy)
}

private fun DrawScope.drawSconce(r: Rect, on: Boolean) {
    val brass = Color(0xFFB08D57)
    drawRoundRect(brass.darken(0.2f), Offset(r.center.x - r.width * 0.18f, r.top + r.height * 0.45f), Size(r.width * 0.36f, r.height * 0.5f), CornerRadius(6f))
    drawLine(brass, Offset(r.center.x, r.top + r.height * 0.65f), Offset(r.center.x, r.top + r.height * 0.35f), r.width * 0.1f)
    val shade = Path().apply {
        moveTo(r.left + r.width * 0.2f, r.top); lineTo(r.right - r.width * 0.2f, r.top)
        lineTo(r.right, r.top + r.height * 0.35f); lineTo(r.left, r.top + r.height * 0.35f); close()
    }
    drawPath(shade, Brush.verticalGradient(listOf(if (on) Color(0xFFFFE9B8) else Color(0xFFE4D3B0), if (on) Color(0xFFF0B964) else Color(0xFFB9A47E)), r.top, r.top + r.height * 0.35f))
}

private fun DrawScope.drawGrandfatherClock(r: Rect) {
    val wood = Color(0xFF5B3422)
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(r.left + 6f, r.top + 8f), r.size)
    // Krone
    val crown = Path().apply { moveTo(r.left, r.top + r.height * 0.06f); quadraticTo(r.center.x, r.top - r.height * 0.04f, r.right, r.top + r.height * 0.06f); lineTo(r.right, r.top + r.height * 0.08f); lineTo(r.left, r.top + r.height * 0.08f); close() }
    drawPath(crown, wood.darken(0.2f))
    drawRect(Brush.horizontalGradient(listOf(wood.darken(0.3f), wood.lighten(0.08f), wood.darken(0.3f)), r.left, r.right), Offset(r.left, r.top + r.height * 0.07f), Size(r.width, r.height * 0.3f))
    val face = Rect(r.left + r.width * 0.18f, r.top + r.height * 0.09f, r.right - r.width * 0.18f, r.top + r.height * 0.09f + r.width * 0.64f)
    drawCircle(Color(0xFFC9A45A), face.width / 2 * 1.08f, face.center)
    drawClockFace(face.center, face.width / 2.3f)
    // Schaft mit Glasfenster für das Pendel
    drawRect(Brush.horizontalGradient(listOf(wood.darken(0.3f), wood, wood.darken(0.3f)), r.left + r.width * 0.1f, r.right - r.width * 0.1f), Offset(r.left + r.width * 0.1f, r.top + r.height * 0.37f), Size(r.width * 0.8f, r.height * 0.5f))
    drawRect(Color(0xFF1E140E), Offset(r.left + r.width * 0.22f, r.top + r.height * 0.4f), Size(r.width * 0.56f, r.height * 0.42f))
    drawRect(Color.White.copy(alpha = 0.06f), Offset(r.left + r.width * 0.22f, r.top + r.height * 0.4f), Size(r.width * 0.18f, r.height * 0.42f))
    // Sockel
    drawRect(Brush.horizontalGradient(listOf(wood.darken(0.35f), wood.lighten(0.05f), wood.darken(0.35f)), r.left, r.right), Offset(r.left, r.top + r.height * 0.87f), Size(r.width, r.height * 0.13f))
}

private fun DrawScope.drawRecordPlayer(r: Rect) {
    val wood = Color(0xFF7A4B2A)
    // Kommode
    drawRect(Brush.verticalGradient(listOf(wood.lighten(0.08f), wood.darken(0.2f)), r.top + r.height * 0.3f, r.bottom), Offset(r.left, r.top + r.height * 0.3f), Size(r.width, r.height * 0.6f))
    for (k in 0 until 2) {
        val x = r.left + r.width * (0.06f + k * 0.47f)
        drawRect(wood.darken(0.25f), Offset(x, r.top + r.height * 0.38f), Size(r.width * 0.41f, r.height * 0.44f), style = Stroke(2f))
        drawCircle(Color(0xFFC9A45A), r.width * 0.02f, Offset(x + r.width * 0.2f, r.top + r.height * 0.6f))
    }
    drawRect(Color(0xFF3F271A), Offset(r.left + r.width * 0.06f, r.top + r.height * 0.9f), Size(r.width * 0.06f, r.height * 0.1f))
    drawRect(Color(0xFF3F271A), Offset(r.right - r.width * 0.12f, r.top + r.height * 0.9f), Size(r.width * 0.06f, r.height * 0.1f))
    // Plattenspieler-Kasten
    drawRect(Color(0xFF4A2E1F), Offset(r.left + r.width * 0.05f, r.top + r.height * 0.12f), Size(r.width * 0.9f, r.height * 0.18f))
    drawRect(Color(0xFF2A1A12), Offset(r.left + r.width * 0.05f, r.top + r.height * 0.1f), Size(r.width * 0.9f, r.height * 0.04f))
    // Tonarm
    drawLine(Color(0xFFD8D8D8), Offset(r.left + r.width * 0.86f, r.top + r.height * 0.14f), Offset(r.left + r.width * 0.62f, r.top + r.height * 0.2f), 3f)
    // Plattenstapel daneben
    drawRect(Color(0xFF1F2F4A), Offset(r.right - r.width * 0.02f, r.top + r.height * 0.42f), Size(r.width * 0.05f, r.height * 0.48f))
}

private fun DrawScope.drawLadder(r: Rect) {
    val wood = Color(0xFF8C5A34)
    val l1 = Offset(r.left + r.width * 0.15f, r.bottom)
    val l2 = Offset(r.left + r.width * 0.55f, r.top)
    val r1 = Offset(r.left + r.width * 0.55f, r.bottom)
    val r2 = Offset(r.right - r.width * 0.05f, r.top)
    drawLine(wood.darken(0.2f), l1, l2, r.width * 0.07f, StrokeCap.Round)
    drawLine(wood, r1, r2, r.width * 0.07f, StrokeCap.Round)
    for (k in 1..7) {
        val f = k / 8f
        val a = l1 + (l2 - l1) * f
        val b = r1 + (r2 - r1) * f
        drawLine(wood.lighten(0.05f), a, b, r.width * 0.045f, StrokeCap.Round)
    }
    drawCircle(Color(0xFFC9A45A), r.width * 0.05f, r2)
    drawCircle(Color(0xFFC9A45A), r.width * 0.05f, l2)
}

private fun DrawScope.drawTelescope(r: Rect) {
    val brass = Color(0xFFC9A45A)
    val c = Offset(r.center.x, r.top + r.height * 0.45f)
    // Stativ
    drawLine(Color(0xFF3F271A), c, Offset(r.left + r.width * 0.1f, r.bottom), r.width * 0.05f)
    drawLine(Color(0xFF3F271A), c, Offset(r.right - r.width * 0.1f, r.bottom), r.width * 0.05f)
    drawLine(Color(0xFF3F271A), c, Offset(r.center.x, r.bottom), r.width * 0.05f)
    rotate(-28f, c) {
        drawRoundRect(Brush.verticalGradient(listOf(brass.lighten(0.3f), brass, brass.darken(0.3f)), c.y - r.height * 0.07f, c.y + r.height * 0.07f), Offset(c.x - r.width * 0.55f, c.y - r.height * 0.06f), Size(r.width * 1.05f, r.height * 0.12f), CornerRadius(6f))
        drawRect(Color(0xFF2A2A2A), Offset(c.x + r.width * 0.45f, c.y - r.height * 0.075f), Size(r.width * 0.08f, r.height * 0.15f))
    }
    drawCircle(brass.darken(0.2f), r.width * 0.06f, c)
}

private fun DrawScope.drawHangingPlant(r: Rect) {
    val pot = Color(0xFFE6D7BC)
    val potTop = r.top + r.height * 0.3f
    drawLine(Color(0xFFB9A88C), Offset(r.center.x, 0f), Offset(r.left + r.width * 0.2f, potTop), 2f)
    drawLine(Color(0xFFB9A88C), Offset(r.center.x, 0f), Offset(r.right - r.width * 0.2f, potTop), 2f)
    val leaf = Color(0xFF4E8A55)
    val rnd = Random(4)
    for (k in 0 until 7) {
        val x0 = r.left + r.width * (0.2f + k * 0.1f)
        val len = r.height * (0.4f + rnd.nextFloat() * 0.35f)
        val p = Path().apply { moveTo(x0, potTop + r.height * 0.1f); quadraticTo(x0 + (k - 3) * r.width * 0.06f, potTop + len * 0.6f, x0 + (k - 3) * r.width * 0.04f, potTop + len) }
        drawPath(p, leaf.darken(0.2f), style = Stroke(2f))
        for (j in 1..5) {
            val f = j / 6f
            drawOval(if (j % 2 == 0) leaf else leaf.lighten(0.15f), Offset(x0 + (k - 3) * r.width * 0.05f * f - r.width * 0.05f, potTop + len * f), Size(r.width * 0.1f, r.width * 0.07f))
        }
    }
    drawRoundRect(Brush.horizontalGradient(listOf(pot.darken(0.15f), pot, pot.darken(0.2f)), r.left, r.right), Offset(r.left + r.width * 0.18f, potTop), Size(r.width * 0.64f, r.height * 0.18f), CornerRadius(r.width * 0.15f))
}

private fun DrawScope.drawFlowers(r: Rect, c: Color) {
    val vase = Color(0xFF2F5A6E)
    val vTop = r.top + r.height * 0.55f
    for (k in 0 until 6) {
        val a = -PI / 2 + (k - 2.5) * 0.3
        val base = Offset(r.center.x, vTop)
        val tip = base + Offset((cos(a) * r.height * 0.5f).toFloat(), (sin(a) * r.height * 0.5f).toFloat())
        drawLine(Color(0xFF4E7A45), base, tip, 2f)
        val fc = if (k % 2 == 0) c.lighten(0.25f) else Color(0xFFF6E7C4)
        for (p in 0 until 5) {
            val pa = p * 2 * PI / 5
            drawCircle(fc, r.width * 0.07f, tip + Offset((cos(pa) * r.width * 0.07f).toFloat(), (sin(pa) * r.width * 0.07f).toFloat()))
        }
        drawCircle(Color(0xFFE0A93B), r.width * 0.05f, tip)
    }
    val vp = Path().apply {
        moveTo(r.left + r.width * 0.3f, vTop); lineTo(r.right - r.width * 0.3f, vTop)
        cubicTo(r.right, r.top + r.height * 0.75f, r.right - r.width * 0.15f, r.bottom, r.center.x, r.bottom)
        cubicTo(r.left + r.width * 0.15f, r.bottom, r.left, r.top + r.height * 0.75f, r.left + r.width * 0.3f, vTop)
        close()
    }
    drawPath(vp, Brush.horizontalGradient(listOf(vase.darken(0.3f), vase.lighten(0.2f), vase.darken(0.3f)), r.left, r.right))
}

private fun DrawScope.drawFern(r: Rect) {
    val pot = Color(0xFF8C8478)
    val potTop = r.top + r.height * 0.68f
    val leaf = Color(0xFF4E7A45)
    for (k in 0 until 11) {
        val a = -PI / 2 + (k - 5) * 0.22
        val base = Offset(r.center.x, potTop)
        val len = r.height * (0.55f + (k % 3) * 0.08f)
        val tip = base + Offset((cos(a) * len * 0.8f).toFloat(), (sin(a) * len).toFloat())
        val p = Path().apply { moveTo(base.x, base.y); quadraticTo((base.x + tip.x) / 2 + (k - 5) * 3f, (base.y + tip.y) / 2 - 10f, tip.x, tip.y) }
        drawPath(p, leaf.darken(0.1f), style = Stroke(2.5f))
        for (j in 1..8) {
            val f = j / 9f
            val q = Offset(base.x + (tip.x - base.x) * f, base.y + (tip.y - base.y) * f)
            drawLine(if (j % 2 == 0) leaf else leaf.lighten(0.15f), q, q + Offset(r.width * 0.08f * (1 - f), -r.width * 0.03f), 2.5f)
            drawLine(if (j % 2 == 0) leaf else leaf.lighten(0.15f), q, q + Offset(-r.width * 0.08f * (1 - f), -r.width * 0.03f), 2.5f)
        }
    }
    drawRoundRect(Brush.horizontalGradient(listOf(pot.darken(0.2f), pot.lighten(0.1f), pot.darken(0.25f)), r.left, r.right), Offset(r.left + r.width * 0.22f, potTop), Size(r.width * 0.56f, r.bottom - potTop), CornerRadius(8f))
}

private fun DrawScope.drawCactus(r: Rect) {
    val pot = Color(0xFFC96A3E)
    val potTop = r.top + r.height * 0.7f
    val g = Color(0xFF5E8A4E)
    drawRoundRect(Brush.horizontalGradient(listOf(g.darken(0.2f), g.lighten(0.1f), g.darken(0.2f)), r.center.x - r.width * 0.16f, r.center.x + r.width * 0.16f), Offset(r.center.x - r.width * 0.16f, r.top + r.height * 0.1f), Size(r.width * 0.32f, potTop - r.top - r.height * 0.1f), CornerRadius(r.width * 0.16f))
    drawRoundRect(g, Offset(r.center.x - r.width * 0.42f, r.top + r.height * 0.35f), Size(r.width * 0.16f, r.height * 0.22f), CornerRadius(r.width * 0.08f))
    drawRect(g, Offset(r.center.x - r.width * 0.34f, r.top + r.height * 0.5f), Size(r.width * 0.2f, r.width * 0.12f))
    drawRoundRect(g, Offset(r.center.x + r.width * 0.26f, r.top + r.height * 0.25f), Size(r.width * 0.16f, r.height * 0.25f), CornerRadius(r.width * 0.08f))
    drawRect(g, Offset(r.center.x + r.width * 0.12f, r.top + r.height * 0.42f), Size(r.width * 0.2f, r.width * 0.12f))
    drawCircle(Color(0xFFE88BA6), r.width * 0.08f, Offset(r.center.x, r.top + r.height * 0.1f))
    drawRect(Brush.horizontalGradient(listOf(pot.darken(0.2f), pot.lighten(0.1f), pot.darken(0.25f)), r.left, r.right), Offset(r.left + r.width * 0.2f, potTop), Size(r.width * 0.6f, r.bottom - potTop))
}

private fun DrawScope.drawRugItem(r: Rect, c: Color, v: Int) {
    when (v) {
        1 -> {
            drawRect(Color.Black.copy(alpha = 0.25f), r.topLeft + Offset(0f, 6f), r.size)
            drawRect(c.darken(0.25f), r.topLeft, r.size)
            val ins = r.height * 0.12f
            drawRect(c.lighten(0.12f), Offset(r.left + ins * 1.6f, r.top + ins), Size(r.width - ins * 3.2f, r.height - ins * 2))
            drawRect(Color(0xFFE6C98F).copy(alpha = 0.7f), Offset(r.left + ins * 2.4f, r.top + ins * 1.5f), Size(r.width - ins * 4.8f, r.height - ins * 3), style = Stroke(2.5f))
            for (k in 0 until 14) {
                val x = r.left + r.width * (k + 0.5f) / 14f
                drawLine(c.darken(0.3f), Offset(x, r.top), Offset(x, r.top - r.height * 0.06f), 1.5f)
                drawLine(c.darken(0.3f), Offset(x, r.bottom), Offset(x, r.bottom + r.height * 0.06f), 1.5f)
            }
        }
        2 -> {
            val rr = Rect(r.center.x - r.width * 0.3f, r.top, r.center.x + r.width * 0.3f, r.bottom)
            drawOval(Color.Black.copy(alpha = 0.25f), rr.topLeft + Offset(0f, 6f), rr.size)
            for (k in 0 until 5) {
                val f = k * 0.1f
                drawOval(if (k % 2 == 0) c.darken(0.15f) else c.lighten(0.15f), Offset(rr.left + rr.width * f, rr.top + rr.height * f), Size(rr.width * (1 - 2 * f), rr.height * (1 - 2 * f)))
            }
        }
        else -> drawRug(r, dummyRoom.copy(curtainColor = colorToInt(c)))
    }
}

private fun colorToInt(c: Color): Int = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

private fun candleTips(r: Rect): List<Offset> = listOf(
    Offset(r.left + r.width * 0.22f, r.top + r.height * 0.18f),
    Offset(r.left + r.width * 0.5f, r.top),
    Offset(r.left + r.width * 0.78f, r.top + r.height * 0.32f),
)

private fun DrawScope.drawCandles(r: Rect) {
    val cw = r.width * 0.17f
    val tips = candleTips(r)
    drawOval(Color(0xFFB08D57), Offset(r.left, r.bottom - r.height * 0.1f), Size(r.width, r.height * 0.14f))
    tips.forEach { p ->
        drawRect(Color(0xFFF7EEDB), Offset(p.x - cw / 2, p.y + r.width * 0.08f), Size(cw, r.bottom - r.height * 0.05f - p.y - r.width * 0.08f))
        drawRect(Color.Black.copy(alpha = 0.08f), Offset(p.x, p.y + r.width * 0.08f), Size(cw / 2, r.bottom - r.height * 0.05f - p.y - r.width * 0.08f))
        drawLine(Color(0xFF2A2A2A), p + Offset(0f, r.width * 0.08f), p + Offset(0f, r.width * 0.03f), 1.5f)
    }
}

private fun DrawScope.drawTeaCup(r: Rect) {
    val s = r.width * 0.3f
    val p = Offset(r.center.x, r.bottom)
    drawOval(Color(0xFFF4EFE6), Offset(p.x - s * 1.4f, p.y - s * 0.25f), Size(s * 2.8f, s * 0.5f))
    drawRoundRect(Color(0xFFF7F3EC), Offset(p.x - s, p.y - s * 1.3f), Size(s * 2f, s * 1.3f), CornerRadius(s * 0.4f))
    drawArc(Color(0xFFF7F3EC), -90f, 180f, false, Offset(p.x + s * 0.7f, p.y - s * 1.1f), Size(s * 0.8f, s * 0.8f), style = Stroke(s * 0.22f))
    drawRect(Color(0xFF9C2F2F), Offset(p.x - s, p.y - s * 0.9f), Size(s * 2f, s * 0.2f))
}

private fun DrawScope.drawStringLights(r: Rect, t: Float, on: Boolean) {
    val n = (r.width / (r.height * 1.2f)).toInt().coerceIn(6, 60)
    val pts = (0..n).map { i ->
        val f = i / n.toFloat()
        val sag = abs(sin(f * PI.toFloat() * (n / 5f).coerceAtLeast(2f))) * r.height * 0.6f
        Offset(r.left + f * r.width, r.top + sag)
    }
    val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(path, Color(0xFF3A3A2A), style = Stroke(1.8f))
    pts.forEachIndexed { i, p ->
        val tw = if (on) 0.6f + 0.4f * sin(t * 2.2f + i * 1.3f) else 0f
        val bulb = p + Offset(0f, r.height * 0.25f)
        if (on) drawCircle(Color(0xFFFFB25C).copy(alpha = 0.22f * tw), r.height * 0.7f, bulb)
        drawCircle(if (on) Color(0xFFFFE0A0).copy(alpha = 0.7f + 0.3f * tw) else Color(0xFF8C8270), r.height * 0.2f, bulb)
    }
}

private fun DrawScope.drawCatVariant(r: Rect, t: Float, v: Int) {
    when (v) {
        1 -> withTint(Color(0xFF8E9094)) { drawCat(r, t) }
        2 -> withTint(Color(0xFF2A2626)) { drawCat(r, t) }
        else -> drawCat(r, t)
    }
}

/** Färbt die Katze über eine Farbmatrix um (einfach, aber charmant). */
private inline fun DrawScope.withTint(c: Color, block: DrawScope.() -> Unit) {
    val paint = androidx.compose.ui.graphics.Paint().apply {
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c, androidx.compose.ui.graphics.BlendMode.Modulate)
    }
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
    block()
    drawContext.canvas.restore()
}

private fun DrawScope.drawDog(r: Rect, t: Float, v: Int) {
    val fur = if (v == 1) Color(0xFF6B3A1E) else Color(0xFFD9A45E)
    val breathe = 1f + 0.035f * sin(t * 1.4f)
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(r.left, r.bottom - r.height * 0.15f), Size(r.width, r.height * 0.25f))
    // Körer liegend
    val bodyW = if (v == 1) 0.8f else 0.66f
    withTransform({ scale(1f, breathe, Offset(r.center.x, r.bottom)) }) {
        drawOval(Brush.verticalGradient(listOf(fur.lighten(0.1f), fur.darken(0.12f)), r.top, r.bottom), Offset(r.left + r.width * 0.12f, r.top + r.height * 0.3f), Size(r.width * bodyW, r.height * 0.62f))
    }
    // Pfoten
    drawRoundRect(fur.lighten(0.05f), Offset(r.right - r.width * 0.28f, r.bottom - r.height * 0.16f), Size(r.width * 0.26f, r.height * 0.14f), CornerRadius(r.height * 0.07f))
    // Kopf auf den Pfoten
    val head = Offset(r.right - r.width * 0.2f, r.top + r.height * 0.5f)
    val hr = r.height * 0.3f
    drawOval(fur, Offset(head.x - hr, head.y - hr * 0.85f), Size(hr * 2f, hr * 1.7f))
    drawOval(fur.darken(0.05f), Offset(head.x + hr * 0.3f, head.y - hr * 0.1f), Size(hr * 1.1f, hr * 0.8f))
    drawCircle(Color(0xFF1E1410), hr * 0.16f, Offset(head.x + hr * 1.3f, head.y + hr * 0.2f))
    // Schlappohr
    drawOval(fur.darken(0.3f), Offset(head.x - hr * 0.9f, head.y - hr * 0.6f), Size(hr * 0.75f, hr * 1.5f))
    drawArc(Color(0xFF2B1A10), 20f, 140f, false, Offset(head.x - hr * 0.05f, head.y - hr * 0.3f), Size(hr * 0.45f, hr * 0.3f), style = Stroke(2f))
    // Schwanz wedelt ab und zu
    val wag = sin(t * 9f) * if ((t % 6f) < 1.2f) 18f else 2f
    rotate(wag, Offset(r.left + r.width * 0.14f, r.top + r.height * 0.6f)) {
        drawLine(fur.darken(0.1f), Offset(r.left + r.width * 0.14f, r.top + r.height * 0.6f), Offset(r.left, r.top + r.height * 0.35f), r.height * 0.12f, StrokeCap.Round)
    }
}
