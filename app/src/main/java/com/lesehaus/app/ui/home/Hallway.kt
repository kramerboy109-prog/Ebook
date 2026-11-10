package com.lesehaus.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lesehaus.app.data.DoorStyle
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.room.TimeOfDay
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.room.RoomScene
import com.lesehaus.app.ui.theme.Fonts
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

/** Farben des Flurs (aus den Einstellungen). */
data class HallStyle(
    val wall: Color = Color(0xFFD9C7A6),
    val runner: Color = Color(0xFF7B2D3B),
    val floor: Color = Color(0xFF8A5A35),
    val wainscot: Boolean = true,
)

fun hallStyleOf(s: com.lesehaus.app.data.AppSettings) = HallStyle(
    wall = Color(s.hallWall),
    runner = Color(s.hallRunner),
    floor = when (s.hallFloor) { "STONE" -> Color(0xFF8E8A84); "TILES" -> Color(0xFF9C8F7E); "CARPET" -> Color(s.hallRunner).darken(0.2f); "OAK", "LIGHT" -> Color(0xFFB08A5E); else -> Color(0xFF8A5A35) },
    wainscot = s.hallWainscot,
)

fun hallTime(): TimeOfDay {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) { in 7..16 -> TimeOfDay.DAY; in 17..19, 6 -> TimeOfDay.SUNSET; else -> TimeOfDay.NIGHT }
}

fun doorColor(room: ReadingRoom): Color =
    if (room.doorColor != 0) Color(room.doorColor) else Color(room.wallColor).darken(0.45f).let { c -> if (c.luminance() < 0.05f) c.lighten(0.15f) else c }

private fun Color.luminance() = 0.299f * red + 0.587f * green + 0.114f * blue

internal fun DrawScope.drawDoorFrame(r: Rect, style: DoorStyle) {
    val frame = Color(0xFFEDE4D3).darken(0.12f)
    val fw = r.width * 0.07f
    if (style == DoorStyle.ARCH) {
        val p = Path().apply {
            moveTo(r.left - fw, r.bottom); lineTo(r.left - fw, r.top + r.width / 2)
            arcTo(Rect(r.left - fw, r.top - fw, r.right + fw, r.top + r.width + fw), 180f, 180f, false)
            lineTo(r.right + fw, r.bottom)
        }
        drawPath(p, frame, style = Stroke(fw * 1.2f))
    } else {
        drawRect(frame, Offset(r.left - fw, r.top - fw), Size(fw, r.height + fw))
        drawRect(frame, Offset(r.right, r.top - fw), Size(fw, r.height + fw))
        drawRect(frame.lighten(0.1f), Offset(r.left - fw * 1.4f, r.top - fw * 1.6f), Size(r.width + fw * 2.8f, fw * 0.8f))
        drawRect(Color.Black.copy(alpha = 0.15f), Offset(r.left - fw * 1.4f, r.top - fw * 0.8f), Size(r.width + fw * 2.8f, fw * 0.2f))
    }
}

internal fun DrawScope.drawDoormat(r: Rect, c: Color) {
    drawRoundRect(c.darken(0.3f), r.topLeft, r.size, CornerRadius(6f))
    drawRoundRect(Color(0xFFE2C27E).copy(alpha = 0.6f), r.topLeft + Offset(6f, 6f), Size(r.width - 12f, r.height - 12f), CornerRadius(4f), style = Stroke(2f))
    drawContext.canvas.nativeCanvas.apply {
        val p = android.graphics.Paint().apply { isAntiAlias = true; color = 0xCCF6E7C4.toInt(); textSize = r.height * 0.42f; typeface = android.graphics.Typeface.SERIF }
        val t = "Willkommen"
        drawText(t, r.center.x - p.measureText(t) / 2, r.center.y + p.textSize * 0.35f, p)
    }
}

/** Türblatt in verschiedenen Stilen, gezeichnet in das Rechteck [r]. */
internal fun DrawScope.drawDoor(style: DoorStyle, c: Color, r: Rect) = translate(r.left, r.top) {
    clipRect(0f, 0f, r.width, r.height) { drawDoorBody(style, c, r.width, r.height) }
}

private fun DrawScope.drawDoorBody(style: DoorStyle, c: Color, w: Float, h: Float) {
    val brass = Color(0xFFD9B97F)
    fun knob(x: Float) {
        drawCircle(Brush.radialGradient(listOf(brass.lighten(0.4f), brass.darken(0.3f)), Offset(x - 3f, h * 0.52f - 3f), w * 0.05f), w * 0.04f, Offset(x, h * 0.52f))
        drawRoundRect(brass.darken(0.2f), Offset(x - w * 0.015f, h * 0.55f), Size(w * 0.03f, h * 0.05f), CornerRadius(3f))
    }
    when (style) {
        DoorStyle.PANEL -> {
            drawRect(Brush.horizontalGradient(listOf(c.darken(0.15f), c.lighten(0.06f), c.darken(0.1f)), 0f, w), size = Size(w, h))
            for (row in 0 until 3) for (col in 0 until 2) {
                val pr = Rect(w * (0.12f + col * 0.42f), h * (0.06f + row * 0.31f), w * (0.46f + col * 0.42f), h * (0.32f + row * 0.31f))
                drawRect(c.darken(0.22f), pr.topLeft, pr.size)
                drawRect(c.lighten(0.08f), pr.topLeft + Offset(pr.width * 0.12f, pr.height * 0.1f), Size(pr.width * 0.76f, pr.height * 0.8f))
                drawLine(Color.White.copy(alpha = 0.12f), pr.topLeft, Offset(pr.right, pr.top), 2f)
            }
            knob(w * 0.86f)
        }
        DoorStyle.ARCH -> {
            drawRect(c, size = Size(w, h))
            val planks = 6
            for (k in 1 until planks) drawLine(c.darken(0.3f), Offset(w * k / planks, 0f), Offset(w * k / planks, h), 2f)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent), 0f, w), size = Size(w, h))
            for (y in listOf(0.25f, 0.75f)) {
                drawRoundRect(Color(0xFF2B2B2B), Offset(0f, h * y), Size(w * 0.55f, h * 0.035f), CornerRadius(6f))
                for (k in 0 until 4) drawCircle(Color(0xFF4A4A4A), w * 0.012f, Offset(w * (0.06f + k * 0.13f), h * y + h * 0.017f))
            }
            drawCircle(Color(0xFF2B2B2B), w * 0.06f, Offset(w * 0.85f, h * 0.55f), style = Stroke(w * 0.015f))
        }
        DoorStyle.GLASS -> {
            // Rahmen mit großen Glasflächen – der Blick ins Zimmer bleibt sichtbar
            val frame = Path().apply {
                addRect(Rect(0f, 0f, w, h))
                addRect(Rect(w * 0.14f, h * 0.06f, w * 0.86f, h * 0.62f))
            }
            frame.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            drawPath(frame, c)
            val gl = Rect(w * 0.14f, h * 0.06f, w * 0.86f, h * 0.62f)
            drawRect(Color(0x33DDEEF5), gl.topLeft, gl.size)
            drawLine(c, Offset(gl.center.x, gl.top), Offset(gl.center.x, gl.bottom), w * 0.035f)
            drawLine(c, Offset(gl.left, gl.top + gl.height * 0.33f), Offset(gl.right, gl.top + gl.height * 0.33f), w * 0.035f)
            drawLine(c, Offset(gl.left, gl.top + gl.height * 0.66f), Offset(gl.right, gl.top + gl.height * 0.66f), w * 0.035f)
            val refl = Path().apply { moveTo(gl.left + gl.width * 0.1f, gl.top); lineTo(gl.left + gl.width * 0.35f, gl.top); lineTo(gl.left, gl.top + gl.height * 0.4f); lineTo(gl.left, gl.top + gl.height * 0.2f); close() }
            drawPath(refl, Color.White.copy(alpha = 0.18f))
            val pr = Rect(w * 0.14f, h * 0.68f, w * 0.86f, h * 0.94f)
            drawRect(c.darken(0.2f), pr.topLeft, pr.size)
            drawRect(c.lighten(0.08f), pr.topLeft + Offset(pr.width * 0.08f, pr.height * 0.12f), Size(pr.width * 0.84f, pr.height * 0.76f))
            knob(w * 0.9f)
        }
        DoorStyle.DOUBLE -> {
            drawRect(Brush.horizontalGradient(listOf(c.darken(0.15f), c.lighten(0.05f), c.darken(0.15f), c.lighten(0.05f), c.darken(0.15f)), 0f, w), size = Size(w, h))
            drawLine(c.darken(0.45f), Offset(w / 2, 0f), Offset(w / 2, h), 3f)
            for (side in 0..1) {
                val ox = side * w / 2
                val p1 = Rect(ox + w * 0.07f, h * 0.06f, ox + w * 0.43f, h * 0.5f)
                val p2 = Rect(ox + w * 0.07f, h * 0.56f, ox + w * 0.43f, h * 0.94f)
                listOf(p1, p2).forEach { pr ->
                    drawRect(c.darken(0.22f), pr.topLeft, pr.size)
                    drawRect(c.lighten(0.07f), pr.topLeft + Offset(pr.width * 0.12f, pr.height * 0.06f), Size(pr.width * 0.76f, pr.height * 0.88f))
                }
            }
            drawRoundRect(brass, Offset(w * 0.44f, h * 0.45f), Size(w * 0.025f, h * 0.12f), CornerRadius(6f))
            drawRoundRect(brass, Offset(w * 0.535f, h * 0.45f), Size(w * 0.025f, h * 0.12f), CornerRadius(6f))
        }
        DoorStyle.COTTAGE -> {
            drawRect(Brush.horizontalGradient(listOf(c.darken(0.1f), c.lighten(0.08f), c.darken(0.1f)), 0f, w), size = Size(w, h))
            val pc = Offset(w / 2, h * 0.24f)
            drawCircle(Color(0xFFE2D8C6), w * 0.22f, pc)
            drawCircle(Color(0x55BBD6E2), w * 0.19f, pc)
            drawLine(Color(0xFFE2D8C6), Offset(pc.x - w * 0.19f, pc.y), Offset(pc.x + w * 0.19f, pc.y), 4f)
            drawLine(Color(0xFFE2D8C6), Offset(pc.x, pc.y - w * 0.19f), Offset(pc.x, pc.y + w * 0.19f), 4f)
            val pr = Rect(w * 0.15f, h * 0.48f, w * 0.85f, h * 0.92f)
            drawRect(c.darken(0.2f), pr.topLeft, pr.size, style = Stroke(5f))
            drawLine(c.darken(0.2f), Offset(pr.left, pr.top), Offset(pr.right, pr.bottom), 5f)
            // Türkranz
            drawCircle(Color(0xFF4E7A45), w * 0.13f, Offset(w / 2, h * 0.24f + w * 0.36f), style = Stroke(w * 0.05f))
            drawCircle(Color(0xFFC0392B), w * 0.025f, Offset(w / 2, h * 0.24f + w * 0.49f))
            knob(w * 0.86f)
        }
        DoorStyle.BARN -> {
            drawRect(c, size = Size(w, h))
            for (k in 1 until 7) drawLine(c.darken(0.35f), Offset(w * k / 7f, 0f), Offset(w * k / 7f, h), 3f)
            val brace = c.lighten(0.08f)
            drawRect(brace, Offset(0f, h * 0.08f), Size(w, h * 0.07f))
            drawRect(brace, Offset(0f, h * 0.85f), Size(w, h * 0.07f))
            val z = Path().apply { moveTo(0f, h * 0.85f); lineTo(w * 0.12f, h * 0.85f); lineTo(w, h * 0.15f); lineTo(w * 0.88f, h * 0.15f); close() }
            drawPath(z, brace)
            drawRect(Color(0xFF2B2B2B), Offset(-w * 0.1f, -h * 0.03f), Size(w * 1.2f, h * 0.02f))
            drawRoundRect(Color(0xFF2B2B2B), Offset(w * 0.08f, h * 0.45f), Size(w * 0.03f, h * 0.15f), CornerRadius(4f))
        }
    }
    // dezenter Schatten links (Tiefe)
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), 0f, w * 0.08f), size = Size(w * 0.08f, h))
}


/**
 * Der Flur: Man wischt von Tür zu Tür. Antippen öffnet die Tür (mit Blick ins Zimmer),
 * langes Drücken öffnet die Zimmer-Einstellungen.
 */
@Composable
fun Hallway(
    rooms: List<ReadingRoom>,
    itemsByRoom: Map<Long, List<RoomItem>>,
    pager: PagerState,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onEnter: (ReadingRoom) -> Unit,
    onEdit: (ReadingRoom) -> Unit,
    onAddRoom: () -> Unit,
    style: HallStyle = HallStyle(),
) {
    val scope = rememberCoroutineScope()
    val time by produceState(0f, animate) {
        if (animate) {
            val s = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { value = (it - s) / 1e9f }
        }
    }
    val tod = remember { hallTime() }
    val openingIndex = remember { mutableIntState(-1) }
    val doorAnim = remember { Animatable(0f) }
    val zoomAnim = remember { Animatable(0f) }
    BoxWithConstraints(modifier.background(style.wall.darken(0.6f))) {
        // durchgehende Flurwand hinter den Türen
        Canvas(Modifier.fillMaxSize()) { drawHallSegment(-1, tod, time, style) }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = maxWidth * 0.17f),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = openingIndex.intValue < 0,
        ) { page ->
            val room = rooms.getOrNull(page)
            val opening = openingIndex.intValue == page
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (opening) {
                            val z = zoomAnim.value
                            scaleX = 1f + z * 2.2f; scaleY = scaleX
                            transformOrigin = TransformOrigin(0.5f, 0.6f)
                        }
                    }
            ) {
                // Wandabschnitt des Flurs
                Canvas(Modifier.fillMaxSize()) { drawHallSegment(page, tod, time, style) }
                val doorW = 0.6f
                val doorH = 0.56f
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val dw = maxWidth * doorW
                    val dh = maxHeight * doorH
                    val left = (maxWidth - dw) / 2
                    val top = maxHeight * 0.83f - dh
                    if (room != null) {
                        // Zimmer hinter der Tür (sichtbar beim Öffnen und durch Glastüren)
                        Box(
                            Modifier
                                .offset(left, top)
                                .size(dw, dh)
                                .clip(doorShape(enumOr(room.doorStyle, DoorStyle.PANEL)))
                        ) {
                            RoomScene(room, Modifier.fillMaxSize(), animate = opening || enumOr(room.doorStyle, DoorStyle.PANEL) == DoorStyle.GLASS && animate, items = itemsByRoom[room.id])
                        }
                        // Lichtschein unter der Tür
                        Canvas(Modifier.offset(left, top).size(dw, dh)) {
                            val glow = (0.35f + room.lightLevel * 0.5f) * (0.9f + 0.1f * sin(time * 3f + page))
                            drawRect(
                                Brush.verticalGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.55f * glow), Color.Transparent), size.height, size.height + size.height * 0.12f),
                                Offset(-size.width * 0.15f, size.height), Size(size.width * 1.3f, size.height * 0.12f), blendMode = BlendMode.Screen,
                            )
                        }
                        // Türblatt (schwingt beim Öffnen auf)
                        Box(
                            Modifier
                                .offset(left, top)
                                .size(dw, dh)
                                .graphicsLayer {
                                    if (opening) {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        rotationY = -doorAnim.value * 105f
                                        cameraDistance = 10f * density
                                    }
                                }
                                .combinedClickable(
                                    onClick = {
                                        if (openingIndex.intValue >= 0) return@combinedClickable
                                        if (pager.currentPage != page) { scope.launch { pager.animateScrollToPage(page) }; return@combinedClickable }
                                        scope.launch {
                                            openingIndex.intValue = page
                                            doorAnim.snapTo(0f); zoomAnim.snapTo(0f)
                                            doorAnim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                                            zoomAnim.animateTo(1f, tween(380))
                                            onEnter(room)
                                            kotlinx.coroutines.delay(600)
                                            openingIndex.intValue = -1
                                            doorAnim.snapTo(0f); zoomAnim.snapTo(0f)
                                        }
                                    },
                                    onLongClick = { onEdit(room) },
                                )
                        ) {
                            Canvas(Modifier.fillMaxSize()) { drawDoor(enumOr(room.doorStyle, DoorStyle.PANEL), doorColor(room), Rect(Offset.Zero, size)) }
                        }
                        // Türrahmen, Schild, Fußmatte
                        Canvas(Modifier.fillMaxSize()) {
                            val dr = Rect(left.toPx(), top.toPx(), (left + dw).toPx(), (top + dh).toPx())
                            drawDoorFrame(dr, enumOr(room.doorStyle, DoorStyle.PANEL))
                            drawDoormat(Rect(dr.left - dr.width * 0.05f, dr.bottom + size.height * 0.035f, dr.right + dr.width * 0.05f, dr.bottom + size.height * 0.09f), Color(room.curtainColor))
                        }
                        // Namensschild
                        Column(
                            Modifier.offset(y = top - 64.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    .background(Brush.linearGradient(listOf(Color(0xFFE8CC8C), Color(0xFFB08D57), Color(0xFFD9B97F))))
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                            ) {
                                val type = runCatching { RoomType.valueOf(room.type) }.getOrNull()
                                Text(
                                    (type?.emoji?.let { "$it " } ?: "") + room.name,
                                    style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF3B2614)),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        // „Neues Zimmer“: Tür-Umriss zum Einbauen
                        Box(
                            Modifier.offset(left, top).size(dw, dh).combinedClickable(onClick = onAddRoom),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(12f))
                                drawRoundRect(Color(0xFFF6E7C4).copy(alpha = 0.6f), style = Stroke(4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(22f, 14f))), cornerRadius = CornerRadius(12f))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+", style = TextStyle(fontFamily = Fonts.Playfair, fontSize = 54.sp, color = Color(0xFFF6E7C4)))
                                Text("Neues Zimmer\neinrichten", style = TextStyle(fontFamily = Fonts.Lora, fontSize = 15.sp, color = Color(0xFFF6E7C4), textAlign = TextAlign.Center, shadow = Shadow(Color.Black, blurRadius = 6f)))
                            }
                        }
                    }
                }
            }
        }
        // Seitenpunkte
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(rooms.size + 1) { i ->
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF6E7C4).copy(alpha = if (i == pager.currentPage) 0.95f else 0.4f))
                )
            }
        }
    }
}


private fun mutableIntState(v: Int) = androidx.compose.runtime.mutableIntStateOf(v)

private fun doorShape(style: DoorStyle) = when (style) {
    DoorStyle.ARCH -> androidx.compose.foundation.shape.RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)
    else -> androidx.compose.ui.graphics.RectangleShape
}

/** Ein Stück Flurwand mit Wandvertäfelung, Läufer, Wandlampe und kleiner Deko. */
private fun DrawScope.drawHallSegment(index: Int, tod: TimeOfDay, t: Float, st: HallStyle) {
    val HallWall = st.wall
    val HallPanel = if (st.wainscot) Color(0xFF5B3A26) else st.wall.darken(0.18f)
    val w = size.width
    val h = size.height
    val floorY = h * 0.83f
    // Tapete mit Streifen
    drawRect(Brush.verticalGradient(listOf(HallWall.darken(0.25f), HallWall.darken(0.05f)), 0f, floorY), size = Size(w, floorY))
    var x = 0f
    while (x < w) { drawRect(HallWall.lighten(0.06f), Offset(x, 0f), Size(w / 24f, floorY)); x += w / 12f }
    // Stuck
    drawRect(HallWall.darken(0.4f), Offset(0f, 0f), Size(w, h * 0.025f))
    drawRect(HallWall.lighten(0.2f), Offset(0f, h * 0.025f), Size(w, h * 0.008f))
    // Vertäfelung unten
    val panelTop = h * 0.6f
    drawRect(HallPanel, Offset(0f, panelTop), Size(w, floorY - panelTop))
    drawRect(HallPanel.lighten(0.15f), Offset(0f, panelTop - 4f), Size(w, 8f))
    for (k in 0 until 2) {
        val pr = Rect(w * (0.04f + k * 0.5f), panelTop + (floorY - panelTop) * 0.15f, w * (0.46f + k * 0.5f), floorY - (floorY - panelTop) * 0.15f)
        drawRect(HallPanel.darken(0.25f), pr.topLeft, pr.size, style = Stroke(3f))
    }
    // Boden (Dielen) und Läufer in Perspektive
    drawRect(Brush.verticalGradient(listOf(st.floor.darken(0.25f), st.floor), floorY, h), Offset(0f, floorY), Size(w, h - floorY))
    for (k in 0..8) drawLine(Color(0xFF3F271A).copy(alpha = 0.5f), Offset(w * k / 8f, floorY), Offset(w * k / 8f, h), 1.5f)
    val runner = Path().apply { moveTo(0f, floorY + (h - floorY) * 0.35f); lineTo(w, floorY + (h - floorY) * 0.35f); lineTo(w, h); lineTo(0f, h); close() }
    drawPath(runner, st.runner)
    drawLine(Color(0xFFE2C27E), Offset(0f, floorY + (h - floorY) * 0.45f), Offset(w, floorY + (h - floorY) * 0.45f), 3f)
    drawLine(Color(0xFFE2C27E), Offset(0f, h * 0.985f), Offset(w, h * 0.985f), 3f)
    drawRect(Color(0xFF3F271A), Offset(0f, floorY - h * 0.012f), Size(w, h * 0.016f))
    // Kleine Deko zwischen den Türen (am Seitenrand jedes Abschnitts)
    when (abs(index * 7 + 3) % 3) {
        0 -> { // Pflanze
            val px = w * 0.04f
            drawRoundRect(Color(0xFFB0643E), Offset(px - w * 0.03f, floorY - h * 0.08f), Size(w * 0.06f, h * 0.08f), CornerRadius(4f))
            for (k in 0 until 5) drawOval(Color(0xFF4E8A55).lighten(k * 0.04f), Offset(px - w * 0.05f + k * w * 0.012f, floorY - h * (0.2f + (k % 2) * 0.05f)), Size(w * 0.05f, h * 0.12f))
        }
        1 -> { // Schirmständer
            val px = w * 0.035f
            drawRect(Color(0xFF34465E), Offset(px - w * 0.025f, floorY - h * 0.1f), Size(w * 0.05f, h * 0.1f))
            drawLine(Color(0xFF2B2B2B), Offset(px, floorY - h * 0.1f), Offset(px - w * 0.01f, floorY - h * 0.2f), 4f)
            drawArc(Color(0xFF2B2B2B), 180f, 180f, false, Offset(px - w * 0.03f, floorY - h * 0.22f), Size(w * 0.03f, h * 0.03f), style = Stroke(4f))
        }
        else -> { // kleines Bild
            val fr = Rect(w * 0.0f, h * 0.3f, w * 0.06f, h * 0.38f)
            drawRect(Color(0xFFC09A55), fr.topLeft, fr.size)
            drawRect(Color(0xFF6F8B60), fr.topLeft + Offset(4f, 4f), Size(fr.width - 8f, fr.height - 8f))
        }
    }
    // Wandleuchte rechts neben der Tür
    val lx = w * 0.88f
    val ly = h * 0.33f
    drawRect(Color(0xFFB08D57), Offset(lx - 4f, ly), Size(8f, h * 0.05f))
    val shade = Path().apply { moveTo(lx - w * 0.025f, ly - h * 0.01f); lineTo(lx + w * 0.025f, ly - h * 0.01f); lineTo(lx + w * 0.04f, ly - h * 0.06f); lineTo(lx - w * 0.04f, ly - h * 0.06f); close() }
    drawPath(shade, Color(0xFFF5DDB0))
    val glow = when (tod) { TimeOfDay.DAY -> 0.18f; TimeOfDay.SUNSET -> 0.35f; TimeOfDay.NIGHT -> 0.55f } * (0.95f + 0.05f * sin(t * 2f + index))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = glow), Color.Transparent), Offset(lx, ly - h * 0.03f), w * 0.28f), w * 0.28f, Offset(lx, ly - h * 0.03f), blendMode = BlendMode.Screen)
    // Tageszeit: nachts dunkler
    if (tod != TimeOfDay.DAY) drawRect(Color(0xFF0A0604).copy(alpha = if (tod == TimeOfDay.NIGHT) 0.28f else 0.12f))
}
