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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.lesehaus.app.data.AppSettings
import com.lesehaus.app.data.DoorStyle
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.room.BookcaseData
import com.lesehaus.app.ui.room.Cam
import com.lesehaus.app.ui.room.DrawCtx
import com.lesehaus.app.ui.room.NEAR
import com.lesehaus.app.ui.room.Proj
import com.lesehaus.app.ui.room.SceneEnt
import com.lesehaus.app.ui.room.TimeOfDay
import com.lesehaus.app.ui.room.buildScene
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.drawEntAnimated
import com.lesehaus.app.ui.room.drawEntStatic
import com.lesehaus.app.ui.room.drawLighting3D
import com.lesehaus.app.ui.room.drawOnQuad
import com.lesehaus.app.ui.room.drawShell
import com.lesehaus.app.ui.room.hitTest3D
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.room.makeTextures
import com.lesehaus.app.ui.room.rememberParticles
import com.lesehaus.app.ui.room.sceneDepth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val DOOR_W = 0.5f
private const val DOOR_H = 0.78f
const val HALL_MAX_YAW = 1.3f
private const val F_DEPTH = 2.2f

/** Was am Ende des Flurs steht. */
enum class HallEnd(val label: String) {
    WINDOW("Fenster"), FIREPLACE("Kamin"), CLOCK("Standuhr"), MIRROR("Spiegel & Konsole"), STAIRS("Treppe nach oben"), ARCH("Torbogen")
}

/** Auf welchen Seiten Türen liegen. */
enum class HallSides(val label: String) { BOTH("Beidseitig"), LEFT("Nur links"), RIGHT("Nur rechts") }

/** Eine Tür im Flur (Zimmer oder „Neues Zimmer“). */
data class HallDoor(val room: ReadingRoom?, val left: Boolean, val d: Float)

/** Form des Flurs: Breite, Länge und die Türen. */
data class HallLayout(val doors: List<HallDoor>, val width: Float, val depth: Float, val sides: HallSides, val spacing: Float) {
    val startCam get() = Cam(width / 2, depth + 0.2f, 0f)
}

fun hallLayout(rooms: List<ReadingRoom>, s: AppSettings): HallLayout {
    val n = rooms.size + 1
    val sides = enumOr(s.hallSides, HallSides.BOTH)
    val spacing = s.hallSpacing.coerceIn(0.85f, 2.2f)
    val w = s.hallWidth.coerceIn(1.1f, 2.8f)
    val rows = if (sides == HallSides.BOTH) (n + 1) / 2 else n
    val depth = rows * spacing + 1.6f
    val doors = (0 until n).map { i ->
        when (sides) {
            HallSides.BOTH -> HallDoor(rooms.getOrNull(i), i % 2 == 0, depth - 1.25f - (i / 2) * spacing - (if (i % 2 == 1) spacing * 0.28f else 0f))
            HallSides.LEFT -> HallDoor(rooms.getOrNull(i), true, depth - 1.25f - i * spacing)
            HallSides.RIGHT -> HallDoor(rooms.getOrNull(i), false, depth - 1.25f - i * spacing)
        }
    }
    return HallLayout(doors, w, depth, sides, spacing)
}

/** Der Flur als „Zimmer“ (Wände, Boden, Decke aus den Einstellungen) – für Szene und Texturen. */
fun hallStyleRoom(s: AppSettings, layout: HallLayout, base: ReadingRoom? = null): ReadingRoom {
    val b = base ?: ReadingRoom(
        name = "Flur", type = "HALL", wallColor = 0, wallPattern = "", floorType = "", windowView = "AUTO", weather = "CLEAR",
        lightLevel = 0.75f, lightWarmth = 0.6f, furniture = "", ambientSound = "NONE", curtainColor = 0,
    )
    return b.copy(
        type = "HALL", wallColor = s.hallWall, wallPattern = s.hallPattern, floorType = s.hallFloor, curtainColor = s.hallRunner,
        roomWidth = layout.width, roomDepth = layout.depth, wainscot = s.hallWainscot, ceiling = s.hallCeiling,
    )
}

private typealias P3 = Triple<Float, Float, Float>

private fun doorQuad(door: HallDoor, w: Float, hingeAngle: Float = 0f): List<P3> {
    val d0 = door.d - DOOR_W / 2
    val d1 = door.d + DOOR_W / 2
    val dw = d1 - d0
    // Scharnier an der vorderen Kante; die Tür schwingt in das Zimmer hinein (weg vom Flur)
    return if (door.left) {
        val fx = -sin(hingeAngle) * dw
        val fd = d1 - cos(hingeAngle) * dw
        listOf(P3(0.005f, DOOR_H, d1), P3(0.005f + fx, DOOR_H, fd), P3(0.005f + fx, 0f, fd), P3(0.005f, 0f, d1))
    } else {
        val fx = sin(hingeAngle) * dw
        val fd = d1 - cos(hingeAngle) * dw
        listOf(P3(w - 0.005f + fx, DOOR_H, fd), P3(w - 0.005f, DOOR_H, d1), P3(w - 0.005f, 0f, d1), P3(w - 0.005f + fx, 0f, fd))
    }
}

private fun wallQuad(left: Boolean, w: Float, d0: Float, d1: Float, y0: Float, y1: Float, off: Float = 0.006f): List<P3> =
    if (left) listOf(P3(off, y1, d1), P3(off, y1, d0), P3(off, y0, d0), P3(off, y0, d1))
    else listOf(P3(w - off, y1, d0), P3(w - off, y1, d1), P3(w - off, y0, d1), P3(w - off, y0, d0))

private fun backQuad(x0: Float, x1: Float, y0: Float, y1: Float, d: Float = 0.008f): List<P3> =
    listOf(P3(x0, y1, d), P3(x1, y1, d), P3(x1, y0, d), P3(x0, y0, d))

private fun floorQuad(x0: Float, x1: Float, d0: Float, d1: Float, y: Float = 0.003f): List<P3> =
    listOf(P3(x0, y, d0), P3(x1, y, d0), P3(x1, y, d1), P3(x0, y, d1))

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

private fun doorZ(proj: Proj, door: HallDoor, w: Float) = proj.z(if (door.left) 0f else w, door.d)

/** Feste Ausstattung des Flurendes (z. B. Fenster mit Pflanzen, Kamin) als Szene-Objekte. */
fun hallEndItems(s: AppSettings, layout: HallLayout): List<RoomItem> {
    var id = -1L
    fun item(type: String, x: Float, y: Float, wall: String, scale: Float = 1f, variant: Int = 0, elev: Float = 0f, color: Int = 0) =
        RoomItem(id = id--, roomId = -1, type = type, x = x, y = y, wall = wall, scale = scale, variant = variant, elev = elev, color = color)
    val dpt = layout.depth
    val out = mutableListOf<RoomItem>()
    when (enumOr(s.hallEnd, HallEnd.WINDOW)) {
        HallEnd.WINDOW -> {
            out += item("WINDOW", 0.5f, 0.22f, "BACK", scale = 1.35f, color = s.hallRunner)
            out += item("PLANT", 0.16f, 0.12f / dpt, "FLOOR", variant = 0)
            out += item("PLANT", 0.84f, 0.12f / dpt, "FLOOR", variant = 1)
        }
        HallEnd.FIREPLACE -> {
            out += item("FIREPLACE", 0.5f, 0f, "BACK", scale = 1.1f)
            out += item("PAINTING", 0.5f, 0.6f, "BACK", scale = 1.1f, variant = 1)
            out += item("ARMCHAIR", 0.22f, 0.45f / dpt, "FLOOR", color = s.hallRunner)
        }
        HallEnd.CLOCK -> {
            out += item("GRANDFATHER_CLOCK", 0.5f, 0f, "BACK", scale = 1.15f)
            out += item("PAINTING", 0.18f, 0.42f, "BACK", scale = 0.8f, variant = 3)
            out += item("PAINTING", 0.82f, 0.42f, "BACK", scale = 0.8f, variant = 0)
        }
        HallEnd.MIRROR -> {
            out += item("MIRROR", 0.5f, 0.36f, "BACK", scale = 1.35f)
            out += item("SIDE_TABLE", 0.5f, 0.12f / dpt, "FLOOR", scale = 2.2f)
            out += item("FLOWERS", 0.42f, 0.12f / dpt, "FLOOR", elev = 0.12f * 1.43f * 2.2f * 0.94f, color = s.hallRunner)
            out += item("WALL_SCONCE", 0.2f, 0.5f, "BACK")
            out += item("WALL_SCONCE", 0.8f, 0.5f, "BACK")
        }
        HallEnd.STAIRS -> out += item("HANGING_PLANT", 0.85f, 0.9f / dpt, "CEILING")
        HallEnd.ARCH -> {
            out += item("PLANT", 0.14f, 0.1f / dpt, "FLOOR", variant = 1)
            out += item("PLANT", 0.86f, 0.1f / dpt, "FLOOR", variant = 1)
        }
    }
    // Einseitiger Flur: hohe Fenster auf der türlosen Seite
    if (layout.sides != HallSides.BOTH) {
        val wall = if (layout.sides == HallSides.LEFT) "RIGHT" else "LEFT"
        var d = dpt - 1.0f
        while (d > 0.7f) {
            out += item("WINDOW", d / dpt, 0.18f, wall, scale = 1.3f, color = s.hallRunner)
            d -= layout.spacing * 1.5f
        }
    }
    return out
}

/**
 * Der begehbare Flur.
 * - hoch/runter wischen: den Gang entlanggehen (die Blickrichtung bleibt, wie sie ist)
 * - links/rechts wischen: sich umdrehen, z. B. zu einer Tür hin
 * - Tür antippen: hineingehen; lange drücken: Zimmer bearbeiten
 */
@Composable
fun Hallway3D(
    rooms: List<ReadingRoom>,
    settings: AppSettings,
    animate: Boolean,
    modifier: Modifier = Modifier,
    hall: ReadingRoom? = null,
    hallItems: List<RoomItem> = emptyList(),
    hallCases: List<BookcaseData> = emptyList(),
    images: Map<String, ImageBitmap> = emptyMap(),
    onNearest: (ReadingRoom?) -> Unit,
    onEnter: (ReadingRoom) -> Unit,
    onEdit: (ReadingRoom) -> Unit,
    onAddRoom: () -> Unit,
    onHallObject: (SceneEnt) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val layout = remember(rooms, settings.hallWidth, settings.hallSpacing, settings.hallSides) { hallLayout(rooms, settings) }
    val w = layout.width
    val time by produceState(0f, animate) {
        if (animate) {
            val s = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { value = (it - s) / 1e9f }
        }
    }
    val tod = remember { hallTime() }
    val hallRoom = remember(settings, layout, hall) { hallStyleRoom(settings, layout, hall) }
    val tex = remember(settings.hallWall, settings.hallPattern, settings.hallFloor, settings.hallWainscot, settings.hallCeiling) { makeTextures(hallRoom) }
    val scene = remember(hallRoom, hallItems, hallCases, settings.hallEnd) {
        buildScene(hallRoom, hallEndItems(settings, layout) + hallItems, hallCases)
    }
    val particles = rememberParticles(7L)
    // Position im Flur überlebt das Betreten eines Zimmers
    var savedD by rememberSaveable { mutableFloatStateOf(-1f) }
    var savedYaw by rememberSaveable { mutableFloatStateOf(0f) }
    var cam by remember(layout.depth, layout.width) {
        mutableStateOf(if (savedD > 0f) Cam(w / 2, savedD.coerceIn(0.9f, layout.depth + 0.4f), savedYaw) else layout.startCam)
    }
    var camJob by remember { mutableStateOf<Job?>(null) }
    var opening by remember { mutableStateOf<HallDoor?>(null) }
    val openAnim = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val currentLayout by rememberUpdatedState(layout)
    val currentScene by rememberUpdatedState(scene)
    fun clamp(c: Cam) = Cam(currentLayout.width / 2, c.d.coerceIn(0.9f, currentLayout.depth + 0.4f), c.yaw.coerceIn(-HALL_MAX_YAW, HALL_MAX_YAW))

    /** Die Tür, auf die man gerade schaut (Blickrichtung zählt mehr als Nähe). */
    fun nearest(c: Cam): HallDoor? {
        val l = currentLayout
        return l.doors.minByOrNull { door ->
            val x = if (door.left) 0f else l.width
            val dx = x - c.x
            val dd = door.d - c.d
            val dist = hypot(dx, dd)
            val dir = atan2(dx, -dd) // 0 = den Gang hinunter, negativ = links
            val off = abs(dir - c.yaw)
            if (dd > -0.05f) 99f + dist else off * 1.2f + abs(dist - 1.3f) * 0.45f
        }
    }
    LaunchedEffect(cam, layout) { onNearest(nearest(cam)?.room) }

    Box(
        modifier.clipToBounds().pointerInput(layout) {
            awaitEachGesture {
                // Wichtig: erst auf eine Berührung warten, sonst dreht sich awaitEachGesture im Kreis
                val down = awaitFirstDown(requireUnconsumed = false)
                if (opening != null) return@awaitEachGesture
                camJob?.cancel()
                val l = currentLayout
                val proj0 = Proj(size.width.toFloat(), size.height.toFloat(), l.width, l.depth, cam, fDepth = F_DEPTH)
                val vt = VelocityTracker()
                vt.addPosition(down.uptimeMillis, down.position)
                var mode = 0 // 0 = noch offen, 1 = gehen, 2 = drehen
                var longPressed = false
                val downTime = down.uptimeMillis
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    if (!ch.pressed) break
                    vt.addPosition(ch.uptimeMillis, ch.position)
                    val total = ch.position - down.position
                    if (mode == 0 && total.getDistance() > viewConfiguration.touchSlop) {
                        // Achse festlegen: wer den Gang entlang will, dreht sich nicht nebenbei
                        mode = if (abs(total.y) >= abs(total.x) * 0.8f) 1 else 2
                    }
                    if (mode != 0) {
                        val delta = ch.position - ch.previousPosition
                        cam = if (mode == 1) clamp(cam.copy(d = cam.d + delta.y * 6f / proj0.f))
                        else clamp(cam.copy(yaw = cam.yaw - delta.x * 0.0042f))
                        ch.consume()
                    } else if (!longPressed && ch.uptimeMillis - downTime > viewConfiguration.longPressTimeoutMillis) {
                        longPressed = true
                    }
                }
                if (mode != 0) {
                    val v = vt.calculateVelocity()
                    val c = cam
                    camJob = scope.launch {
                        if (mode == 1) animateDecay(c.d, v.y * 6f / proj0.f, FloatExponentialDecaySpec(2.4f)) { value, _ -> cam = clamp(cam.copy(d = value)) }
                        else animateDecay(c.yaw, -v.x * 0.0042f, FloatExponentialDecaySpec(3.2f)) { value, _ -> cam = clamp(cam.copy(yaw = value)) }
                        savedD = cam.d; savedYaw = cam.yaw
                    }
                    return@awaitEachGesture
                }
                // Tippen: Tür oder Gegenstand – das vordere gewinnt
                val proj = Proj(size.width.toFloat(), size.height.toFloat(), l.width, l.depth, cam, fDepth = F_DEPTH)
                val doorHit = l.doors.filter { door ->
                    val q = if (door.room == null) wallQuad(door.left, l.width, door.d - DOOR_W / 2, door.d + DOOR_W / 2, 0f, DOOR_H) else doorQuad(door, l.width)
                    q.all { proj.z(it.first, it.third) > NEAR } && inPoly(down.position, q.map { (x, y, d) -> proj.p(x, y, d) })
                }.minByOrNull { doorZ(proj, it, l.width) }
                val objHit = hitTest3D(down.position, currentScene.filter { (it.item?.id ?: 1L) > 0L }, proj, includeDecals = false)
                if (objHit != null && (doorHit == null || sceneDepth(objHit, proj) < doorZ(proj, doorHit, l.width))) {
                    onHallObject(objHit)
                    return@awaitEachGesture
                }
                val hit = doorHit ?: return@awaitEachGesture
                val room = hit.room
                if (room == null) { onAddRoom(); return@awaitEachGesture }
                if (longPressed) { onEdit(room); return@awaitEachGesture }
                opening = hit
                scope.launch {
                    // zur Tür drehen, Tür öffnen, eintreten
                    val from = cam
                    val yawT = if (hit.left) -(PI / 2).toFloat() else (PI / 2).toFloat()
                    val xT = if (hit.left) min(l.width * 0.78f, 1.05f) else max(l.width * 0.22f, l.width - 1.05f)
                    androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { v, _ ->
                        cam = Cam(from.x + (xT - from.x) * v, from.d + (hit.d - from.d) * v, from.yaw + (yawT - from.yaw) * v)
                    }
                    openAnim.snapTo(0f)
                    openAnim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                    launch { fade.animateTo(1f, tween(380)) }
                    val f2 = cam
                    val xIn = if (hit.left) 0.34f else l.width - 0.34f
                    androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(400)) { v, _ -> cam = f2.copy(x = f2.x + (xIn - f2.x) * v) }
                    // beim Zurückkommen steht man vor der Tür, halb zugewandt
                    savedD = (hit.d + 0.75f).coerceAtMost(l.depth + 0.4f)
                    savedYaw = if (hit.left) -0.75f else 0.75f
                    onEnter(room)
                    kotlinx.coroutines.delay(500)
                    cam = Cam(l.width / 2, savedD, savedYaw)
                    opening = null
                    openAnim.snapTo(0f); fade.snapTo(0f)
                }
            }
        }
    ) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val proj = Proj(size.width, size.height, w, layout.depth + 2.5f, cam, fDepth = F_DEPTH)
                val ctx = DrawCtx(hallRoom, 1f, images, particles)
                drawShell(hallRoom, tex, proj)
                drawHallScene(proj, layout, settings, hallRoom, scene, ctx, tod, t, opening, openAnim.value * 1.35f)
                drawLighting3D(hallRoom, scene, proj, t)
                drawHallLights(proj, layout, tod, t)
                if (fade.value > 0f) drawRect(Color(0xFF1A110C).copy(alpha = fade.value))
            }
        )
    }
}

/** Flurboden, Türen, Deko und Gegenstände – nach Entfernung sortiert. */
private fun DrawScope.drawHallScene(
    proj: Proj, layout: HallLayout, s: AppSettings, hallRoom: ReadingRoom, scene: List<SceneEnt>, ctx: DrawCtx,
    tod: TimeOfDay, t: Float, opening: HallDoor?, openAmount: Float, animated: Boolean = true,
) {
    val w = layout.width
    val end = enumOr(s.hallEnd, HallEnd.WINDOW)
    // 1. Bodenbelag: Läufer und Lichtschein vor den Türen
    drawRunner(proj, layout, s, if (end == HallEnd.STAIRS) 0.95f else 0.25f)
    layout.doors.forEach { drawDoorGlow(proj, it, w, t) }
    // 2. Alles Senkrechte nach Entfernung
    val list = ArrayList<Pair<Float, DrawScope.() -> Unit>>()
    for (e in scene) {
        val z = sceneDepth(e, proj)
        list += z to {
            drawEntStatic(e, proj, ctx, emptyMap())
            if (animated) drawEntAnimated(e, proj, ctx, t)
        }
    }
    for (door in layout.doors) {
        list += doorZ(proj, door, w) + 0.03f to { drawHallDoor(proj, door, w, if (door == opening) openAmount else 0f) }
    }
    // Bilder und Wandleuchten zwischen den Türen (nicht dort, wo schon etwas hängt)
    hallWallSlots(layout).forEachIndexed { i, (left, d) ->
        val wallName = if (left) "LEFT" else "RIGHT"
        val taken = scene.any { e -> e.wall.name == wallName && abs(e.a - d) < e.ww / 2 + 0.2f && e.b + e.wh > 0.4f }
        if (taken) return@forEachIndexed
        list += proj.z(if (left) 0f else w, d) + 0.04f to {
            if (i % 2 == 0) drawOnQuad(proj, wallQuad(left, w, d - 0.16f, d + 0.16f, 0.45f, 0.68f), 200f, 150f) { r -> drawLandscape(r, i) }
            else drawOnQuad(proj, wallQuad(left, w, d - 0.05f, d + 0.05f, 0.55f, 0.68f), 60f, 80f) { r ->
                drawRect(Color(0xFFB08D57), Offset(r.center.x - 4f, r.top + 30f), Size(8f, 50f))
                drawRoundRect(Color(0xFFF5DDB0), Offset(r.left, r.top), Size(r.width, 32f), CornerRadius(6f))
            }
        }
    }
    when (end) {
        HallEnd.STAIRS -> list += proj.z(w / 2, 0.4f) to { drawStairs(proj, w, s) }
        HallEnd.ARCH -> list += proj.z(w / 2, 0f) + 0.02f to { drawArchway(proj, w, hallRoom, tod) }
        else -> Unit
    }
    list.sortedByDescending { it.first }.forEach { it.second(this) }
}

/** Plätze an den Wänden zwischen den Türen. */
private fun hallWallSlots(layout: HallLayout): List<Pair<Boolean, Float>> {
    val out = ArrayList<Pair<Boolean, Float>>()
    var d = layout.depth - 0.6f
    while (d > 0.5f) {
        for (left in listOf(true, false)) {
            val tooClose = layout.doors.any { it.left == left && abs(it.d - d) < DOOR_W / 2 + 0.2f }
            val windowSide = layout.sides != HallSides.BOTH && (layout.sides == HallSides.LEFT) != left
            if (!tooClose && !windowSide) out += left to d
        }
        d -= layout.spacing / 2
    }
    return out
}

private fun DrawScope.drawLandscape(r: Rect, i: Int) {
    drawRect(Color(0xFFC09A55), r.topLeft, r.size)
    val inner = Rect(r.left + 14f, r.top + 14f, r.right - 14f, r.bottom - 14f)
    val skies = listOf(listOf(Color(0xFFE9C893), Color(0xFF7FA07A)), listOf(Color(0xFF9DB8D6), Color(0xFF4F6E8E)), listOf(Color(0xFFE7B08B), Color(0xFF8C5A6E)))
    drawRect(Brush.verticalGradient(skies[(i / 2) % skies.size], inner.top, inner.bottom), inner.topLeft, inner.size)
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawCircle(Color(0xFF5D6E5A), inner.width * 0.4f, Offset(inner.center.x - inner.width * 0.2f, inner.bottom + inner.width * 0.15f))
        drawCircle(Color(0xFF4A5A48), inner.width * 0.3f, Offset(inner.center.x + inner.width * 0.3f, inner.bottom + inner.width * 0.12f))
    }
}

private fun DrawScope.drawRunner(proj: Proj, layout: HallLayout, s: AppSettings, from: Float) {
    val w = layout.width
    val half = min(0.3f, w * 0.22f)
    val c = Color(s.hallRunner)
    var d0 = from
    val end = layout.depth + 0.8f
    var k = 0
    // kurze Abschnitte, damit der Läufer bis direkt vor die Füße reicht
    while (d0 < end) {
        val d1 = min(d0 + 0.2f, end)
        val withMedallion = k % 3 == 1
        drawOnQuad(proj, floorQuad(w / 2 - half, w / 2 + half, d0, d1, 0.002f), 300f, 300f * (d1 - d0) / 0.5f) { r ->
            drawRect(c, r.topLeft, r.size.copy(height = r.height + 1f))
            drawRect(Color(0xFFE2C27E), Offset(r.left + 18f, r.top), Size(6f, r.height + 1f))
            drawRect(Color(0xFFE2C27E), Offset(r.right - 24f, r.top), Size(6f, r.height + 1f))
            if (withMedallion) {
                drawCircle(c.lighten(0.15f), 26f, r.center, style = Stroke(5f))
                drawCircle(Color(0xFFE2C27E).copy(alpha = 0.6f), 8f, r.center)
            }
        }
        d0 = d1
        k++
    }
}

private fun DrawScope.drawDoorGlow(proj: Proj, door: HallDoor, w: Float, t: Float) {
    val room = door.room ?: return
    val glow = (0.35f + room.lightLevel * 0.45f) * (0.9f + 0.1f * sin(t * 3f + door.d))
    val x0 = if (door.left) 0f else w - 0.22f
    drawOnQuad(proj, floorQuad(x0, x0 + 0.22f, door.d - DOOR_W / 2, door.d + DOOR_W / 2), 100f, 100f) { r ->
        val warm = Color(0xFFFFC27A).copy(alpha = 0.5f * glow)
        val brush = if (door.left) Brush.horizontalGradient(listOf(warm, Color.Transparent), r.left, r.right)
        else Brush.horizontalGradient(listOf(Color.Transparent, warm), r.left, r.right)
        drawRect(brush, r.topLeft, r.size, blendMode = BlendMode.Screen)
    }
    // Fußmatte
    val mx0 = if (door.left) 0.03f else w - 0.25f
    drawOnQuad(proj, floorQuad(mx0, mx0 + 0.22f, door.d - 0.22f, door.d + 0.22f, 0.004f), 120f, 220f) { r ->
        drawRoundRect(Color(room.curtainColor).darken(0.35f), r.topLeft, r.size, CornerRadius(8f))
        drawRoundRect(Color(0xFFE2C27E).copy(alpha = 0.55f), r.topLeft + Offset(8f, 8f), Size(r.width - 16f, r.height - 16f), CornerRadius(6f), style = Stroke(3f))
    }
}

/** Treppe am Flurende, die nach oben ins nächste Stockwerk führt. */
private fun DrawScope.drawStairs(proj: Proj, w: Float, s: AppSettings) {
    val x0 = w * 0.2f
    val x1 = w * 0.8f
    val steps = 8
    val rise = 0.12f
    val run = 0.1f
    val front = 0.85f
    val wood = Color(0xFF6B4329)
    // Treppenloch in der Decke
    drawOnQuad(proj, listOf(P3(x0, 0.995f, 0f), P3(x1, 0.995f, 0f), P3(x1, 0.995f, 0.4f), P3(x0, 0.995f, 0.4f)), 200f, 100f) { r ->
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0E0906), Color(0xFF2A1C14)), r.top, r.bottom), r.topLeft, r.size)
    }
    // von oben (hinten) nach unten (vorn)
    for (i in steps - 1 downTo 0) {
        val dFront = front - i * run
        val dBack = dFront - run
        val yTop = (i + 1) * rise
        val yBot = i * rise
        // Trittfläche
        drawOnQuad(proj, listOf(P3(x0, yTop, dBack), P3(x1, yTop, dBack), P3(x1, yTop, dFront), P3(x0, yTop, dFront)), 200f, 40f) { r ->
            drawRect(wood.lighten(0.08f), r.topLeft, r.size)
            drawRect(Color(s.hallRunner), Offset(r.left + 50f, r.top), Size(r.width - 100f, r.height))
        }
        // Setzstufe
        drawOnQuad(proj, listOf(P3(x0, yTop, dFront), P3(x1, yTop, dFront), P3(x1, yBot, dFront), P3(x0, yBot, dFront)), 200f, 40f) { r ->
            drawRect(Color(0xFFEDE4D3).darken(0.18f + i * 0.03f), r.topLeft, r.size)
            drawRect(Color(s.hallRunner).darken(0.2f), Offset(r.left + 50f, r.top), Size(r.width - 100f, r.height))
            drawRect(wood.darken(0.2f), r.topLeft, Size(r.width, 5f))
        }
    }
    // Wange und Handlauf (rechts offen)
    drawOnQuad(proj, listOf(P3(x1, steps * rise + 0.05f, front - steps * run), P3(x1, rise + 0.05f, front), P3(x1, 0f, front), P3(x1, 0f, front - steps * run)), 200f, 200f) { r ->
        val path = Path().apply { moveTo(r.left, r.top); lineTo(r.right, r.top + r.height * 0.82f); lineTo(r.right, r.bottom); lineTo(r.right - 20f, r.bottom); lineTo(r.left, r.top + 30f); close() }
        drawPath(path, wood.darken(0.15f))
    }
    val rail = (0..steps).map { k -> proj.p(x1, 0.42f + k * rise, front - k * run) }
    for (k in 0 until steps) {
        val base = proj.p(x1, k * rise + rise, front - k * run - run / 2)
        val top = proj.p(x1, 0.42f + k * rise + rise / 2, front - k * run - run / 2)
        drawLine(Color(0xFF3B2416), base, top, max(2f, proj.sAt(x1, front - k * run) * 0.012f))
    }
    for (k in 0 until rail.size - 1) drawLine(wood.darken(0.3f), rail[k], rail[k + 1], max(3f, proj.sAt(x1, front - k * run) * 0.025f))
    drawCircle(Color(0xFFB08D57), max(4f, proj.sAt(x1, front) * 0.03f), rail[0])
}

/** Torbogen am Flurende mit Blick in einen weiteren, warm beleuchteten Raum. */
private fun DrawScope.drawArchway(proj: Proj, w: Float, hallRoom: ReadingRoom, tod: TimeOfDay) {
    val x0 = w / 2 - 0.36f
    val x1 = w / 2 + 0.36f
    drawOnQuad(proj, backQuad(x0, x1, 0f, 0.9f), 360f, 450f) { r ->
        val archR = r.width / 2
        val opening = Path().apply {
            moveTo(r.left + 30f, r.bottom)
            lineTo(r.left + 30f, r.top + archR)
            arcTo(Rect(r.left + 30f, r.top + 30f, r.right - 30f, r.top + r.width - 30f), 180f, 180f, false)
            lineTo(r.right - 30f, r.bottom)
            close()
        }
        val frame = Path().apply {
            moveTo(r.left, r.bottom)
            lineTo(r.left, r.top + archR)
            arcTo(Rect(r.left, r.top, r.right, r.top + r.width), 180f, 180f, false)
            lineTo(r.right, r.bottom)
            close()
        }
        drawPath(frame, Color(hallRoom.wallColor).darken(0.25f))
        drawPath(opening, Brush.verticalGradient(listOf(Color(0xFF2A1A10), Color(0xFF8A5530), Color(0xFF3A2416)), r.top, r.bottom))
        // Blick in den nächsten Raum: Fenster und Lampe
        val win = Rect(r.center.x - 50f, r.top + archR - 10f, r.center.x + 50f, r.top + archR + 110f)
        val sky = when (tod) { TimeOfDay.DAY -> Color(0xFF9EC3E6); TimeOfDay.SUNSET -> Color(0xFFE59A6B); TimeOfDay.NIGHT -> Color(0xFF1B2547) }
        drawRect(sky.copy(alpha = 0.85f), win.topLeft, win.size)
        drawRect(Color(0xFF3B2614), win.topLeft, win.size, style = Stroke(6f))
        drawLine(Color(0xFF3B2614), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), 5f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD08A).copy(alpha = 0.7f), Color.Transparent), Offset(r.center.x + 70f, r.bottom - 160f), 120f), 120f, Offset(r.center.x + 70f, r.bottom - 160f))
        drawPath(frame, Color(0xFFEDE4D3).darken(0.1f), style = Stroke(10f))
    }
}

private fun DrawScope.drawHallLights(proj: Proj, layout: HallLayout, tod: TimeOfDay, t: Float) {
    val w = layout.width
    val strength = when (tod) { TimeOfDay.DAY -> 0.22f; TimeOfDay.SUNSET -> 0.36f; TimeOfDay.NIGHT -> 0.55f }
    hallWallSlots(layout).forEachIndexed { k, (left, d) ->
        if (k % 2 == 0) return@forEachIndexed
        val x = if (left) 0.05f else w - 0.05f
        if (proj.z(x, d) < NEAR + 0.05f) return@forEachIndexed
        val c = proj.p(x, 0.64f, d)
        val rad = proj.sAt(x, d) * 0.45f
        val f = 0.95f + 0.05f * sin(t * 2f + k)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = strength * f), Color.Transparent), c, rad), rad, c, blendMode = BlendMode.Screen)
    }
    // Deckenlampen
    var dl = layout.depth - 0.9f
    while (dl > 0.3f) {
        if (proj.z(w / 2, dl) > NEAR + 0.1f) {
            val top = proj.p(w / 2, 1f, dl)
            val lamp = proj.p(w / 2, 0.86f, dl)
            val s = proj.sAt(w / 2, dl)
            drawLine(Color(0xFF2B2018), top, lamp, 2f)
            drawArc(Color(0xFFB08D57), 180f, 180f, true, Offset(lamp.x - s * 0.08f, lamp.y - s * 0.03f), Size(s * 0.16f, s * 0.08f))
            drawCircle(Color(0xFFFFE9B8), s * 0.022f, Offset(lamp.x, lamp.y + s * 0.012f))
            val rad = s * 0.6f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = strength * 0.7f), Color.Transparent), lamp, rad), rad, lamp, blendMode = BlendMode.Screen)
        }
        dl -= layout.spacing * 1.5f
    }
}

/** Eine Tür mit Rahmen, Schild und Türblatt – perspektivisch an der Seitenwand. */
private fun DrawScope.drawHallDoor(proj: Proj, door: HallDoor, w: Float, open: Float) {
    val room = door.room
    val frameQuad = wallQuad(door.left, w, door.d - DOOR_W / 2 - 0.05f, door.d + DOOR_W / 2 + 0.05f, 0f, DOOR_H + 0.05f, 0.004f)
    val style = room?.let { enumOr(it.doorStyle, DoorStyle.PANEL) } ?: DoorStyle.PANEL
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
        val hole = Rect(r.left + 26f, r.top + 30f, r.right - 26f, r.bottom)
        drawRect(Brush.verticalGradient(listOf(Color(room.wallColor).darken(0.4f), Color(0xFFFFB25C).copy(alpha = 0.9f), Color(room.wallColor).darken(0.5f)), hole.top, hole.bottom), hole.topLeft, hole.size)
    }
    if (room == null) return
    // Türblatt (schwingt auf)
    drawOnQuad(proj, doorQuad(door, w, hingeAngle = open), 260f, 410f) { r -> drawDoor(style, doorColor(room), r) }
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
}

/**
 * Für das Einrichten des Flurs im Zimmer-Editor: Läufer, Türen, Flurende und Wanddeko
 * (ohne die eigenen Gegenstände, die zeichnet der Editor selbst).
 */
fun DrawScope.drawHallFixtures(proj: Proj, rooms: List<ReadingRoom>, s: AppSettings, hallRoom: ReadingRoom) {
    val layout = hallLayout(rooms, s)
    val ctx = DrawCtx(hallRoom, 1f)
    val fixed = buildScene(hallRoom, hallEndItems(s, layout), emptyList())
    drawHallScene(proj, layout, s, hallRoom, fixed, ctx, hallTime(), 0f, null, 0f, animated = true)
}

/** Bildschirmmitte einer Tür aus der Startposition (für Tests und Hilfen). */
fun hallDoorScreenCenter(rooms: List<ReadingRoom>, settings: AppSettings, index: Int, sw: Float, sh: Float): Offset {
    val layout = hallLayout(rooms, settings)
    val door = layout.doors[index]
    val proj = Proj(sw, sh, layout.width, layout.depth, layout.startCam, fDepth = F_DEPTH)
    val q = doorQuad(door, layout.width).map { (x, y, d) -> proj.p(x, y, d) }
    return Offset(q.map { it.x }.average().toFloat(), q.map { it.y }.average().toFloat())
}
