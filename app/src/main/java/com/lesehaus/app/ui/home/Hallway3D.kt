package com.lesehaus.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.lesehaus.app.data.AppSettings
import com.lesehaus.app.data.DoorStyle
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.room.Cam
import com.lesehaus.app.ui.room.NEAR
import com.lesehaus.app.ui.room.Proj
import com.lesehaus.app.ui.room.TimeOfDay
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.drawOnQuad
import com.lesehaus.app.ui.room.drawShell
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.room.makeTextures
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val HALL_W = 1.45f
private const val DOOR_W = 0.5f
private const val DOOR_H = 0.78f
private const val SPACING = 1.25f

/** Eine Tür im Flur (Zimmer oder „Neues Zimmer“). */
data class HallDoor(val room: ReadingRoom?, val left: Boolean, val d: Float)

fun hallDoors(rooms: List<ReadingRoom>): Pair<List<HallDoor>, Float> {
    val n = rooms.size + 1
    val depth = ((n + 1) / 2) * SPACING + 1.6f
    val doors = (0 until n).map { i ->
        val d = depth - 1.25f - (i / 2) * SPACING - (if (i % 2 == 1) 0.35f else 0f)
        HallDoor(rooms.getOrNull(i), i % 2 == 0, d)
    }
    return doors to depth
}

private fun doorQuad(door: HallDoor, w: Float, hingeAngle: Float = 0f, inset: Float = 0f): List<Triple<Float, Float, Float>> {
    val d0 = door.d - DOOR_W / 2 + inset
    val d1 = door.d + DOOR_W / 2 - inset
    val y1 = DOOR_H - inset
    val y0 = 0f
    // Scharnier an der vorderen Kante; die Tür schwingt in das Zimmer hinein (weg vom Flur)
    val dw = d1 - d0
    return if (door.left) {
        val fx = -sin(hingeAngle) * dw
        val fd = d1 - cos(hingeAngle) * dw
        listOf(Triple(0.005f, y1, d1), Triple(0.005f + fx, y1, fd), Triple(0.005f + fx, y0, fd), Triple(0.005f, y0, d1))
    } else {
        val fx = sin(hingeAngle) * dw
        val fd = d1 - cos(hingeAngle) * dw
        listOf(Triple(w - 0.005f + fx, y1, fd), Triple(w - 0.005f, y1, d1), Triple(w - 0.005f, y0, d1), Triple(w - 0.005f + fx, y0, fd))
    }
}

private fun wallQuad(left: Boolean, w: Float, d0: Float, d1: Float, y0: Float, y1: Float, off: Float = 0.006f): List<Triple<Float, Float, Float>> =
    if (left) listOf(Triple(off, y1, d1), Triple(off, y1, d0), Triple(off, y0, d0), Triple(off, y0, d1))
    else listOf(Triple(w - off, y1, d0), Triple(w - off, y1, d1), Triple(w - off, y0, d1), Triple(w - off, y0, d0))

private fun inPoly(p: Offset, q: List<Offset>): Boolean {
    var sign = 0
    for (i in q.indices) {
        val a = q[i]; val b = q[(i + 1) % q.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
    }
    return true
}

/**
 * Der begehbare Flur: Türen links und rechts, man läuft mit Wischen den Gang entlang.
 * [onNearest] meldet die Tür, vor der man gerade steht.
 */
@Composable
fun Hallway3D(
    rooms: List<ReadingRoom>,
    settings: AppSettings,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onNearest: (ReadingRoom?) -> Unit,
    onEnter: (ReadingRoom) -> Unit,
    onEdit: (ReadingRoom) -> Unit,
    onAddRoom: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val (doors, depth) = remember(rooms) { hallDoors(rooms) }
    val time by produceState(0f, animate) {
        if (animate) {
            val s = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { value = (it - s) / 1e9f }
        }
    }
    val tod = remember { hallTime() }
    val hallRoom = remember(settings.hallWall, settings.hallPattern, settings.hallFloor, settings.hallWainscot, settings.hallCeiling) {
        ReadingRoom(
            name = "Flur", type = "HALL", wallColor = settings.hallWall, wallPattern = settings.hallPattern, floorType = settings.hallFloor,
            windowView = "AUTO", weather = "CLEAR", lightLevel = 0.7f, lightWarmth = 0.6f, furniture = "", ambientSound = "NONE", curtainColor = settings.hallRunner,
            roomWidth = HALL_W, roomDepth = depth, wainscot = settings.hallWainscot, ceiling = settings.hallCeiling,
        )
    }
    val tex = remember(hallRoom) { makeTextures(hallRoom) }
    var cam by remember(depth) { mutableStateOf(Cam(HALL_W / 2, depth + 0.2f)) }
    var camJob by remember { mutableStateOf<Job?>(null) }
    var opening by remember { mutableStateOf<HallDoor?>(null) }
    val openAnim = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val currentDoors by rememberUpdatedState(doors)
    fun clamp(c: Cam) = Cam(c.x.coerceIn(0.35f, HALL_W - 0.35f), c.d.coerceIn(0.9f, depth + 0.4f))
    fun nearest(c: Cam): HallDoor? = currentDoors.minByOrNull { abs(it.d - (c.d - 1.1f)) }
    LaunchedEffect(cam, doors) { onNearest(nearest(cam)?.room) }

    Box(
        modifier.pointerInput(depth) {
            awaitEachGesture {
                if (opening != null) return@awaitEachGesture
                val down = awaitFirstDown(requireUnconsumed = false)
                camJob?.cancel()
                val start = cam
                val proj0 = Proj(size.width.toFloat(), size.height.toFloat(), HALL_W, depth, start, fDepth = 2.2f)
                val vt = VelocityTracker()
                vt.addPosition(down.uptimeMillis, down.position)
                var dragging = false
                var longPressed = false
                val downTime = down.uptimeMillis
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    if (!ch.pressed) break
                    vt.addPosition(ch.uptimeMillis, ch.position)
                    if (!dragging && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        val delta = ch.position - ch.previousPosition
                        val k = cam.d.coerceAtMost(3f) / proj0.f
                        cam = clamp(Cam(cam.x - delta.x * k * 0.6f, cam.d - delta.y * k * 2.2f))
                        ch.consume()
                    } else if (!longPressed && ch.uptimeMillis - downTime > viewConfiguration.longPressTimeoutMillis) {
                        longPressed = true
                    }
                }
                if (dragging) {
                    val v = vt.calculateVelocity()
                    val k = cam.d.coerceAtMost(3f) / proj0.f
                    val c = cam
                    camJob = scope.launch {
                        animateDecay(c.d, -v.y * k * 2.2f, FloatExponentialDecaySpec(2.4f)) { value, _ -> cam = clamp(Cam(cam.x, value)) }
                    }
                    return@awaitEachGesture
                }
                // Tippen auf eine Tür
                val proj = Proj(size.width.toFloat(), size.height.toFloat(), HALL_W, depth, cam, fDepth = 2.2f)
                val hit = currentDoors.sortedByDescending { it.d }.firstOrNull { door ->
                    val q = doorQuad(door, HALL_W)
                    q.all { proj.cam.d - it.third > NEAR } && inPoly(down.position, q.map { (x, y, d) -> proj.p(x, y, d) })
                } ?: return@awaitEachGesture
                val room = hit.room
                if (room == null) { onAddRoom(); return@awaitEachGesture }
                if (longPressed) { onEdit(room); return@awaitEachGesture }
                opening = hit
                scope.launch {
                    // vor die Tür gehen, Tür öffnen, eintreten
                    val from = cam
                    val target = Cam(if (hit.left) 0.45f else HALL_W - 0.45f, hit.d + 1.15f)
                    androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) { v, _ ->
                        cam = clamp(Cam(from.x + (target.x - from.x) * v, from.d + (target.d - from.d) * v))
                    }
                    openAnim.snapTo(0f)
                    openAnim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                    launch { fade.animateTo(1f, tween(380)) }
                    val f2 = cam
                    androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(400)) { v, _ ->
                        cam = clamp(Cam(f2.x + ((if (hit.left) 0.36f else HALL_W - 0.36f) - f2.x) * v, f2.d + (hit.d + 0.92f - f2.d) * v))
                    }
                    onEnter(room)
                    kotlinx.coroutines.delay(500)
                    opening = null
                    openAnim.snapTo(0f); fade.snapTo(0f)
                }
            }
        }
    ) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val proj = Proj(size.width, size.height, HALL_W, depth, cam, fDepth = 2.2f)
                drawShell(hallRoom, tex, proj)
                drawHallDecor(proj, depth, doors, settings, tod, t)
                // Türen von hinten nach vorn
                doors.sortedBy { it.d }.forEach { door ->
                    val open = if (opening == door) openAnim.value * 1.35f else 0f
                    drawHallDoor(proj, door, open, t)
                }
                // Licht & Stimmung
                drawRect(Color(0xFF0A0604).copy(alpha = when (tod) { TimeOfDay.DAY -> 0.06f; TimeOfDay.SUNSET -> 0.16f; TimeOfDay.NIGHT -> 0.3f }))
                drawHallLights(proj, depth, tod, t)
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(size.width / 2, proj.horizon), max(size.width, size.height) * 0.75f))
                if (fade.value > 0f) drawRect(Color(0xFF1A110C).copy(alpha = fade.value))
            }
        )
    }
}

/** Läufer, Wandleuchten, Bilder, Fenster am Ende des Gangs. */
private fun DrawScope.drawHallDecor(proj: Proj, depth: Float, doors: List<HallDoor>, s: AppSettings, tod: TimeOfDay, t: Float) {
    val w = HALL_W
    val dn = minOf(depth, proj.cam.d - NEAR - 0.02f)
    // Fenster am Ende des Flurs
    drawOnQuad(proj, listOf(Triple(w / 2 - 0.28f, 0.82f, 0.01f), Triple(w / 2 + 0.28f, 0.82f, 0.01f), Triple(w / 2 + 0.28f, 0.22f, 0.01f), Triple(w / 2 - 0.28f, 0.22f, 0.01f)), 280f, 300f) { r ->
        val sky = when (tod) { TimeOfDay.DAY -> listOf(Color(0xFF6FA8DC), Color(0xFFB9D9F0)); TimeOfDay.SUNSET -> listOf(Color(0xFF4B3A73), Color(0xFFE07B52)); TimeOfDay.NIGHT -> listOf(Color(0xFF0B1026), Color(0xFF1B2547)) }
        val glass = Rect(r.left + 30f, r.top + 20f, r.right - 30f, r.bottom - 20f)
        drawRect(Brush.verticalGradient(sky, glass.top, glass.bottom), glass.topLeft, glass.size)
        if (tod == TimeOfDay.NIGHT) drawCircle(Color(0xFFFFF4D6), 14f, Offset(glass.right - 40f, glass.top + 40f))
        drawRect(Color(0xFFE2D8C6), glass.topLeft, glass.size, style = Stroke(10f))
        drawLine(Color(0xFFE2D8C6), Offset(glass.center.x, glass.top), Offset(glass.center.x, glass.bottom), 8f)
        drawLine(Color(0xFFE2D8C6), Offset(glass.left, glass.top + glass.height * 0.4f), Offset(glass.right, glass.top + glass.height * 0.4f), 8f)
        val cc = Color(s.hallRunner)
        drawRect(Brush.horizontalGradient(listOf(cc.darken(0.3f), cc, cc.darken(0.2f)), r.left, r.left + 40f), Offset(r.left, r.top), Size(40f, r.height))
        drawRect(Brush.horizontalGradient(listOf(cc.darken(0.2f), cc, cc.darken(0.3f)), r.right - 40f, r.right), Offset(r.right - 40f, r.top), Size(40f, r.height))
    }
    // Konsole mit Pflanze unter dem Fenster
    drawOnQuad(proj, listOf(Triple(w / 2 - 0.3f, 0.3f, 0.06f), Triple(w / 2 + 0.3f, 0.3f, 0.06f), Triple(w / 2 + 0.3f, 0f, 0.06f), Triple(w / 2 - 0.3f, 0f, 0.06f)), 300f, 150f) { r ->
        drawRect(Color(0xFF5B3A26), Offset(r.left, r.top + 20f), Size(r.width, 18f))
        drawRect(Color(0xFF3F271A), Offset(r.left + 20f, r.top + 38f), Size(14f, r.height - 38f))
        drawRect(Color(0xFF3F271A), Offset(r.right - 34f, r.top + 38f), Size(14f, r.height - 38f))
        drawRoundRect(Color(0xFFB0643E), Offset(r.center.x - 22f, r.top - 20f), Size(44f, 40f), CornerRadius(6f))
        for (k in 0 until 6) drawOval(Color(0xFF4E8A55).lighten(k * 0.03f), Offset(r.center.x - 40f + k * 14f, r.top - 80f - (k % 2) * 16f), Size(26f, 70f))
    }
    // Läufer
    drawOnQuad(proj, listOf(Triple(w / 2 - 0.3f, 0.002f, 0.25f), Triple(w / 2 + 0.3f, 0.002f, 0.25f), Triple(w / 2 + 0.3f, 0.002f, dn), Triple(w / 2 - 0.3f, 0.002f, dn)), 300f, 300f * dn) { r ->
        val c = Color(s.hallRunner)
        drawRect(c, r.topLeft, r.size)
        drawRect(Color(0xFFE2C27E), Offset(r.left + 18f, r.top), Size(6f, r.height))
        drawRect(Color(0xFFE2C27E), Offset(r.right - 24f, r.top), Size(6f, r.height))
        var y = 40f
        while (y < r.height) {
            drawCircle(c.lighten(0.15f), 26f, Offset(r.center.x, y), style = Stroke(5f))
            drawCircle(Color(0xFFE2C27E).copy(alpha = 0.6f), 8f, Offset(r.center.x, y))
            y += 150f
        }
    }
    // Wandleuchten und Bilder zwischen den Türen
    var d = depth - 0.6f
    var i = 0
    while (d > 0.4f) {
        for (left in listOf(true, false)) {
            val tooClose = doors.any { it.left == left && abs(it.d - d) < DOOR_W / 2 + 0.12f }
            if (tooClose) continue
            if ((i + if (left) 0 else 1) % 2 == 0) {
                // Bild
                drawOnQuad(proj, wallQuad(left, w, d - 0.16f, d + 0.16f, 0.45f, 0.68f), 200f, 150f) { r ->
                    drawRect(Color(0xFFC09A55), r.topLeft, r.size)
                    val inner = Rect(r.left + 14f, r.top + 14f, r.right - 14f, r.bottom - 14f)
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFE9C893), Color(0xFF7FA07A)), inner.top, inner.bottom), inner.topLeft, inner.size)
                    drawCircle(Color(0xFF5D6E5A), inner.width * 0.4f, Offset(inner.center.x - inner.width * 0.2f, inner.bottom + inner.width * 0.15f))
                }
            } else {
                // Wandleuchte
                drawOnQuad(proj, wallQuad(left, w, d - 0.05f, d + 0.05f, 0.55f, 0.68f), 60f, 80f) { r ->
                    drawRect(Color(0xFFB08D57), Offset(r.center.x - 4f, r.top + 30f), Size(8f, 50f))
                    drawRoundRect(Color(0xFFF5DDB0), Offset(r.left, r.top), Size(r.width, 32f), CornerRadius(6f))
                }
            }
        }
        d -= SPACING / 2
        i++
    }
}

private fun DrawScope.drawHallLights(proj: Proj, depth: Float, tod: TimeOfDay, t: Float) {
    val strength = when (tod) { TimeOfDay.DAY -> 0.25f; TimeOfDay.SUNSET -> 0.4f; TimeOfDay.NIGHT -> 0.6f }
    var d = depth - 0.6f
    var k = 0
    while (d > 0.4f) {
        for (left in listOf(true, false)) {
            val c = proj.p(if (left) 0.05f else HALL_W - 0.05f, 0.64f, d)
            if (proj.cam.d - d < NEAR) continue
            val rad = proj.s(d) * 0.45f
            val f = 0.95f + 0.05f * sin(t * 2f + k)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = strength * f), Color.Transparent), c, rad), rad, c, blendMode = BlendMode.Screen)
        }
        d -= SPACING
        k++
    }
    // Deckenlampen
    var dl = depth - 0.9f
    while (dl > 0.3f) {
        if (proj.cam.d - dl > NEAR + 0.1f) {
            val top = proj.p(HALL_W / 2, 1f, dl)
            val lamp = proj.p(HALL_W / 2, 0.86f, dl)
            val s = proj.s(dl)
            drawLine(Color(0xFF2B2018), top, lamp, 2f)
            drawArc(Color(0xFFB08D57), 180f, 180f, true, Offset(lamp.x - s * 0.08f, lamp.y - s * 0.03f), Size(s * 0.16f, s * 0.08f))
            drawCircle(Color(0xFFFFE9B8), s * 0.022f, Offset(lamp.x, lamp.y + s * 0.012f))
            val rad = s * 0.6f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = strength * 0.7f), Color.Transparent), lamp, rad), rad, lamp, blendMode = BlendMode.Screen)
        }
        dl -= SPACING * 1.5f
    }
}

/** Eine Tür mit Rahmen, Schild, Lichtschein und Fußmatte – perspektivisch an der Seitenwand. */
private fun DrawScope.drawHallDoor(proj: Proj, door: HallDoor, open: Float, t: Float) {
    if (proj.cam.d - (door.d + DOOR_W / 2) < NEAR + 0.02f) return
    val w = HALL_W
    val room = door.room
    val frameQuad = wallQuad(door.left, w, door.d - DOOR_W / 2 - 0.05f, door.d + DOOR_W / 2 + 0.05f, 0f, DOOR_H + 0.05f, 0.004f)
    val style = room?.let { enumOr(it.doorStyle, DoorStyle.PANEL) } ?: DoorStyle.PANEL
    // Lichtschein auf dem Boden vor der Tür
    if (room != null) {
        val glow = (0.35f + room.lightLevel * 0.45f) * (0.9f + 0.1f * sin(t * 3f + door.d))
        val x0 = if (door.left) 0f else w - 0.22f
        drawOnQuad(proj, listOf(Triple(x0, 0.003f, door.d - DOOR_W / 2), Triple(x0 + 0.22f, 0.003f, door.d - DOOR_W / 2), Triple(x0 + 0.22f, 0.003f, door.d + DOOR_W / 2), Triple(x0, 0.003f, door.d + DOOR_W / 2)), 100f, 100f) { r ->
            val brush = if (door.left) Brush.horizontalGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.5f * glow), Color.Transparent), r.left, r.right)
            else Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFC27A).copy(alpha = 0.5f * glow)), r.left, r.right)
            drawRect(brush, r.topLeft, r.size, blendMode = BlendMode.Screen)
        }
    }
    // Türrahmen
    drawOnQuad(proj, frameQuad, 300f, 440f) { r ->
        val frame = Color(0xFFEDE4D3).darken(0.12f)
        if (room == null) {
            drawRoundRect(Color.White.copy(alpha = 0.06f), r.topLeft, r.size, CornerRadius(12f))
            drawRoundRect(Color(0xFFF6E7C4).copy(alpha = 0.7f), r.topLeft + Offset(20f, 20f), Size(r.width - 40f, r.height - 20f), CornerRadius(12f), style = Stroke(6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f))))
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFFF6E7C4.toInt(); textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.SERIF }
                p.textSize = 120f; drawText("+", r.center.x, r.center.y, p)
                p.textSize = 30f; drawText("Neues Zimmer", r.center.x, r.center.y + 60f, p)
            }
            return@drawOnQuad
        }
        drawRect(frame, r.topLeft, r.size)
        drawRect(frame.lighten(0.15f), r.topLeft, Size(r.width, 22f))
        drawRect(Color.Black.copy(alpha = 0.2f), Offset(r.left, r.top + 22f), Size(r.width, 4f))
        // dunkle Türöffnung mit warmem Raumlicht
        val opening = Rect(r.left + 26f, r.top + 30f, r.right - 26f, r.bottom)
        drawRect(Brush.verticalGradient(listOf(Color(room.wallColor).darken(0.4f), Color(0xFFFFB25C).copy(alpha = 0.9f), Color(room.wallColor).darken(0.5f)), opening.top, opening.bottom), opening.topLeft, opening.size)
    }
    if (room == null) return
    // Türblatt (schwingt auf)
    val leaf = doorQuad(door, w, hingeAngle = open)
    drawOnQuad(proj, leaf, 260f, 410f) { r -> drawDoor(style, doorColor(room), r) }
    // Schild über der Tür
    drawOnQuad(proj, wallQuad(door.left, w, door.d - 0.17f, door.d + 0.17f, DOOR_H + 0.07f, DOOR_H + 0.15f), 340f, 80f) { r ->
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFE8CC8C), Color(0xFFB08D57), Color(0xFFD9B97F)), r.topLeft, r.bottomRight), r.topLeft, r.size, CornerRadius(40f))
        drawRoundRect(Color(0xFF7A5A2E), r.topLeft + Offset(6f, 6f), Size(r.width - 12f, r.height - 12f), CornerRadius(34f), style = Stroke(2f))
        val type = runCatching { RoomType.valueOf(room.type) }.getOrNull()
        drawContext.canvas.nativeCanvas.apply {
            val p = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF3B2614.toInt(); textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD) }
            var size = 38f
            val label = (type?.emoji?.let { "$it " } ?: "") + room.name
            p.textSize = size
            while (p.measureText(label) > r.width - 40f && size > 16f) { size -= 2f; p.textSize = size }
            drawText(label, r.center.x, r.center.y + size * 0.35f, p)
        }
    }
    // Fußmatte
    val mx0 = if (door.left) 0.03f else w - 0.25f
    drawOnQuad(proj, listOf(Triple(mx0, 0.004f, door.d - 0.22f), Triple(mx0 + 0.22f, 0.004f, door.d - 0.22f), Triple(mx0 + 0.22f, 0.004f, door.d + 0.22f), Triple(mx0, 0.004f, door.d + 0.22f)), 120f, 220f) { r ->
        drawRoundRect(Color(room.curtainColor).darken(0.35f), r.topLeft, r.size, CornerRadius(8f))
        drawRoundRect(Color(0xFFE2C27E).copy(alpha = 0.55f), r.topLeft + Offset(8f, 8f), Size(r.width - 16f, r.height - 16f), CornerRadius(6f), style = Stroke(3f))
    }
}
