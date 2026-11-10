package com.lesehaus.app.ui.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.lesehaus.app.data.FloorType
import com.lesehaus.app.data.Furniture
import com.lesehaus.app.data.WallPattern
import com.lesehaus.app.data.Weather
import com.lesehaus.app.data.WindowView
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.enumOr
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------
// Farbhelfer
// ------------------------------------------------------------------------------------------------
fun Color.darken(f: Float) = lerp(this, Color.Black, f.coerceIn(0f, 1f))
fun Color.lighten(f: Float) = lerp(this, Color.White, f.coerceIn(0f, 1f))

private val FireOrange = Color(0xFFFF8A2B)
private val FireYellow = Color(0xFFFFD36B)
private val Glow = Color(0xFFFFB25C)

enum class TimeOfDay { DAY, SUNSET, NIGHT }

fun timeOfDay(view: WindowView): TimeOfDay = when (view) {
    WindowView.DAY -> TimeOfDay.DAY
    WindowView.SUNSET -> TimeOfDay.SUNSET
    WindowView.NIGHT -> TimeOfDay.NIGHT
    else -> {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (h) {
            in 7..16 -> TimeOfDay.DAY
            in 17..19, 6 -> TimeOfDay.SUNSET
            else -> TimeOfDay.NIGHT
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Layout
// ------------------------------------------------------------------------------------------------
internal fun DrawScope.drawWallPattern(p: WallPattern, wall: Color, floorY: Float) {
    val w = size.width
    val fy = floorY
    when (p) {
        WallPattern.PLAIN -> Unit
        WallPattern.STRIPES -> {
            val sw = w / 18f
            var x = 0f
            var i = 0
            while (x < w) {
                if (i % 2 == 0) drawRect(wall.lighten(0.07f), Offset(x, 0f), Size(sw * 0.55f, fy))
                drawLine(wall.darken(0.12f), Offset(x, 0f), Offset(x, fy), strokeWidth = 1f)
                x += sw; i++
            }
        }
        WallPattern.DAMASK -> {
            val step = w / 9f
            val c = wall.lighten(0.1f).copy(alpha = 0.55f)
            var row = 0
            var y = step * 0.5f
            while (y < fy) {
                var x = if (row % 2 == 0) step * 0.5f else step
                while (x < w + step) {
                    val s = step * 0.22f
                    val path = Path().apply {
                        moveTo(x, y - s * 1.6f)
                        cubicTo(x + s, y - s, x + s * 1.2f, y + s * 0.2f, x, y + s * 1.6f)
                        cubicTo(x - s * 1.2f, y + s * 0.2f, x - s, y - s, x, y - s * 1.6f)
                        close()
                    }
                    drawPath(path, c)
                    drawCircle(c, s * 0.35f, Offset(x, y - s * 2.2f))
                    drawCircle(c, s * 0.25f, Offset(x - s * 1.4f, y))
                    drawCircle(c, s * 0.25f, Offset(x + s * 1.4f, y))
                    x += step
                }
                y += step * 0.75f; row++
            }
        }
        WallPattern.PANELING -> {
            val top = fy * 0.55f
            drawRect(wall.darken(0.28f), Offset(0f, top), Size(w, fy - top))
            drawRect(wall.darken(0.45f), Offset(0f, top - 4f), Size(w, 8f))
            drawRect(Color.White.copy(alpha = 0.1f), Offset(0f, top - 4f), Size(w, 2f))
            val pw = w / 6f
            for (i in 0 until 6) {
                val r = Rect(i * pw + pw * 0.1f, top + (fy - top) * 0.14f, (i + 1) * pw - pw * 0.1f, fy - (fy - top) * 0.14f)
                drawRect(wall.darken(0.4f), r.topLeft, r.size, style = Stroke(3f))
                drawRect(Color.White.copy(alpha = 0.07f), r.topLeft + Offset(2f, 2f), r.size, style = Stroke(1.5f))
            }
        }
        WallPattern.BRICK -> {
            val bh = fy / 22f
            val bw = bh * 2.6f
            var row = 0
            var y = 0f
            val rnd = Random(3)
            while (y < fy) {
                var x = if (row % 2 == 0) 0f else -bw / 2
                while (x < w) {
                    val tint = rnd.nextFloat() * 0.12f
                    drawRect(wall.darken(tint), Offset(x + 1.5f, y + 1.5f), Size(bw - 3f, bh - 3f))
                    x += bw
                }
                y += bh; row++
            }
        }
        WallPattern.FLORAL -> {
            val step = w / 11f
            val rnd = Random(11)
            var y = step * 0.4f
            var row = 0
            while (y < fy) {
                var x = if (row % 2 == 0) step * 0.3f else step * 0.8f
                while (x < w) {
                    val c1 = wall.lighten(0.22f).copy(alpha = 0.6f)
                    val r = step * 0.09f
                    for (k in 0 until 5) {
                        val a = k * 2 * PI / 5 + rnd.nextFloat() * 0.2
                        drawCircle(c1, r, Offset(x + cos(a).toFloat() * r * 1.4f, y + sin(a).toFloat() * r * 1.4f))
                    }
                    drawCircle(Color(0xFFE6C068).copy(alpha = 0.6f), r * 0.8f, Offset(x, y))
                    x += step
                }
                y += step * 0.8f; row++
            }
        }
        WallPattern.WOOD -> {
            val pw = w / 14f
            val rnd = Random(5)
            var x = 0f
            while (x < w) {
                drawRect(wall.darken(rnd.nextFloat() * 0.15f), Offset(x, 0f), Size(pw, fy))
                drawLine(wall.darken(0.4f), Offset(x, 0f), Offset(x, fy), 2f)
                // Maserung
                for (g in 0 until 4) {
                    val gx = x + pw * (0.2f + g * 0.2f)
                    drawLine(wall.darken(0.22f).copy(alpha = 0.4f), Offset(gx, 0f), Offset(gx + pw * 0.05f * sin(g.toFloat()), fy), 1f)
                }
                x += pw
            }
        }
    }
}

internal fun DrawScope.skyColors(tod: TimeOfDay): List<Color> = when (tod) {
    TimeOfDay.DAY -> listOf(Color(0xFF6FA8DC), Color(0xFFB9D9F0))
    TimeOfDay.SUNSET -> listOf(Color(0xFF4B3A73), Color(0xFFE07B52), Color(0xFFF6C177))
    TimeOfDay.NIGHT -> listOf(Color(0xFF0B1026), Color(0xFF1B2547))
}

internal fun DrawScope.drawWindowStatic(room: ReadingRoom, r: Rect) {
    val tod = timeOfDay(enumOr(room.windowView, WindowView.AUTO))
    val weather = enumOr(room.weather, Weather.CLEAR)
    val frame = Color(0xFFEDE4D3).darken(0.15f)
    // Laibung
    drawRect(Color.Black.copy(alpha = 0.25f), r.topLeft - Offset(r.width * 0.06f, r.height * 0.04f), Size(r.width * 1.12f, r.height * 1.08f))
    clipRect(r.left, r.top, r.right, r.bottom) {
        val sky = skyColors(tod).let { if (weather != Weather.CLEAR) it.map { c -> lerp(c, Color(0xFF6C7380), 0.45f).darken(if (tod == TimeOfDay.NIGHT) 0.5f else 0.1f) } else it }
        drawRect(Brush.verticalGradient(sky, r.top, r.bottom), r.topLeft, r.size)
        val rnd = Random(42)
        when (tod) {
            TimeOfDay.NIGHT -> {
                if (weather == Weather.CLEAR) {
                    repeat(40) {
                        drawCircle(Color.White.copy(alpha = 0.4f + rnd.nextFloat() * 0.6f), 0.6f + rnd.nextFloat() * 1.4f, Offset(r.left + rnd.nextFloat() * r.width, r.top + rnd.nextFloat() * r.height * 0.7f))
                    }
                    val mc = Offset(r.left + r.width * 0.7f, r.top + r.height * 0.22f)
                    drawCircle(Color(0xFFFFF4D6).copy(alpha = 0.15f), r.width * 0.16f, mc)
                    drawCircle(Color(0xFFFFF4D6), r.width * 0.08f, mc)
                    drawCircle(sky.first(), r.width * 0.07f, mc + Offset(r.width * 0.035f, -r.width * 0.02f))
                }
            }
            TimeOfDay.SUNSET -> if (weather == Weather.CLEAR) drawCircle(Color(0xFFFFD27F), r.width * 0.12f, Offset(r.left + r.width * 0.35f, r.bottom - r.height * 0.22f))
            TimeOfDay.DAY -> {
                if (weather == Weather.CLEAR) drawCircle(Color(0xFFFFF3B0), r.width * 0.07f, Offset(r.left + r.width * 0.75f, r.top + r.height * 0.2f))
                // Wolken
                repeat(3) { i ->
                    val c = Offset(r.left + r.width * (0.2f + i * 0.3f), r.top + r.height * (0.25f + (i % 2) * 0.12f))
                    val col = Color.White.copy(alpha = if (weather == Weather.CLEAR) 0.8f else 0.5f)
                    drawCircle(col, r.width * 0.07f, c)
                    drawCircle(col, r.width * 0.09f, c + Offset(r.width * 0.07f, -r.width * 0.02f))
                    drawCircle(col, r.width * 0.06f, c + Offset(r.width * 0.15f, 0f))
                }
            }
        }
        // Hügel / Bäume am Horizont
        val hill = when (tod) {
            TimeOfDay.DAY -> Color(0xFF5E8A5A)
            TimeOfDay.SUNSET -> Color(0xFF3B2F4A)
            TimeOfDay.NIGHT -> Color(0xFF0A0F1C)
        }.let { if (weather == Weather.SNOW) lerp(it, Color.White, 0.65f) else it }
        val p = Path().apply {
            moveTo(r.left, r.bottom)
            lineTo(r.left, r.bottom - r.height * 0.18f)
            cubicTo(r.left + r.width * 0.3f, r.bottom - r.height * 0.32f, r.left + r.width * 0.55f, r.bottom - r.height * 0.12f, r.right, r.bottom - r.height * 0.24f)
            lineTo(r.right, r.bottom)
            close()
        }
        drawPath(p, hill)
        for (i in 0 until 5) {
            val tx = r.left + r.width * (0.1f + i * 0.2f)
            val ty = r.bottom - r.height * (0.18f + (i % 2) * 0.05f)
            val tp = Path().apply { moveTo(tx, ty - r.height * 0.14f); lineTo(tx - r.width * 0.04f, ty); lineTo(tx + r.width * 0.04f, ty); close() }
            drawPath(tp, hill.darken(0.25f))
        }
    }
    // Rahmen und Sprossen
    val fw = r.width * 0.045f
    drawRect(frame, r.topLeft, r.size, style = Stroke(fw))
    drawLine(frame, Offset(r.center.x, r.top), Offset(r.center.x, r.bottom), fw * 0.7f)
    drawLine(frame, Offset(r.left, r.top + r.height * 0.42f), Offset(r.right, r.top + r.height * 0.42f), fw * 0.7f)
    // Fensterbank
    drawRect(frame.lighten(0.1f), Offset(r.left - r.width * 0.08f, r.bottom + fw * 0.3f), Size(r.width * 1.16f, r.height * 0.045f))
    drawRect(Color.Black.copy(alpha = 0.2f), Offset(r.left - r.width * 0.08f, r.bottom + fw * 0.3f + r.height * 0.045f), Size(r.width * 1.16f, r.height * 0.015f))
    // Vorhänge
    val cc = Color(room.curtainColor)
    val ch = r.height * 1.25f
    for (side in listOf(-1, 1)) {
        val x0 = if (side < 0) r.left - r.width * 0.16f else r.right + r.width * 0.16f
        val x1 = if (side < 0) r.left + r.width * 0.08f else r.right - r.width * 0.08f
        val path = Path().apply {
            moveTo(x0, r.top - r.height * 0.1f)
            lineTo(x1, r.top - r.height * 0.1f)
            quadraticTo(x1 - side * r.width * 0.02f, r.top + ch * 0.4f, x1 - side * r.width * 0.1f, r.top + ch * 0.55f)
            quadraticTo(x0 + side * r.width * 0.02f, r.top + ch * 0.8f, x0 + side * r.width * 0.01f, r.top + ch)
            lineTo(x0, r.top + ch)
            close()
        }
        drawPath(path, Brush.horizontalGradient(listOf(cc.darken(0.35f), cc, cc.darken(0.2f), cc.lighten(0.08f), cc.darken(0.3f)), min(x0, x1), max(x0, x1)))
    }
    // Vorhangstange
    drawLine(Color(0xFFB08D57), Offset(r.left - r.width * 0.22f, r.top - r.height * 0.1f), Offset(r.right + r.width * 0.22f, r.top - r.height * 0.1f), r.height * 0.025f, StrokeCap.Round)
    drawCircle(Color(0xFFD9B97F), r.height * 0.03f, Offset(r.left - r.width * 0.22f, r.top - r.height * 0.1f))
    drawCircle(Color(0xFFD9B97F), r.height * 0.03f, Offset(r.right + r.width * 0.22f, r.top - r.height * 0.1f))
}

internal fun DrawScope.drawPainting(r: Rect, room: ReadingRoom) {
    val gold = Color(0xFFC09A55)
    drawRect(Color.Black.copy(alpha = 0.3f), r.topLeft + Offset(4f, 6f), r.size)
    drawRect(Brush.linearGradient(listOf(gold.lighten(0.2f), gold.darken(0.3f), gold), r.topLeft, r.bottomRight), r.topLeft, r.size)
    val inner = Rect(r.left + r.width * 0.09f, r.top + r.height * 0.1f, r.right - r.width * 0.09f, r.bottom - r.height * 0.1f)
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE9C893), Color(0xFFA9B9A0)), inner.top, inner.bottom), inner.topLeft, inner.size)
        val p = Path().apply {
            moveTo(inner.left, inner.bottom)
            lineTo(inner.left, inner.top + inner.height * 0.6f)
            lineTo(inner.left + inner.width * 0.35f, inner.top + inner.height * 0.3f)
            lineTo(inner.left + inner.width * 0.6f, inner.top + inner.height * 0.55f)
            lineTo(inner.left + inner.width * 0.8f, inner.top + inner.height * 0.4f)
            lineTo(inner.right, inner.top + inner.height * 0.62f)
            lineTo(inner.right, inner.bottom)
            close()
        }
        drawPath(p, Color(0xFF5D6E5A))
        drawRect(Color(0xFF6F8B60), Offset(inner.left, inner.bottom - inner.height * 0.22f), Size(inner.width, inner.height * 0.22f))
        drawCircle(Color(0xFFF6E2A8), inner.width * 0.07f, Offset(inner.left + inner.width * 0.75f, inner.top + inner.height * 0.22f))
    }
    drawRect(gold.darken(0.5f), inner.topLeft, inner.size, style = Stroke(2f))
    // kleine Bilderleuchte
    drawRect(Color(0xFFB08D57), Offset(r.center.x - r.width * 0.15f, r.top - r.height * 0.08f), Size(r.width * 0.3f, r.height * 0.05f))
}

internal fun DrawScope.drawClockFace(c: Offset, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.3f), r * 1.12f, c + Offset(3f, 5f))
    drawCircle(Brush.linearGradient(listOf(Color(0xFF8C5A34), Color(0xFF4A2C18)), c - Offset(r, r), c + Offset(r, r)), r * 1.15f, c)
    drawCircle(Color(0xFFF7EEDC), r, c)
    for (i in 0 until 12) {
        val a = i * PI / 6
        val p1 = c + Offset((sin(a) * r * 0.82f).toFloat(), (-cos(a) * r * 0.82f).toFloat())
        val p2 = c + Offset((sin(a) * r * 0.95f).toFloat(), (-cos(a) * r * 0.95f).toFloat())
        drawLine(Color(0xFF3A2A1E), p1, p2, if (i % 3 == 0) 3f else 1.5f)
    }
}

internal fun DrawScope.drawFloor(type: FloorType, floorY: Float) {
    val w = size.width
    val h = size.height
    val top = floorY
    val fh = h - top
    val vp = Offset(w / 2f, top - h * 0.9f)
    when (type) {
        FloorType.PARQUET, FloorType.PLANKS -> {
            val base = if (type == FloorType.PARQUET) Color(0xFF8A5A35) else Color(0xFFA67B52)
            drawRect(Brush.verticalGradient(listOf(base.darken(0.35f), base, base.lighten(0.05f)), top, h), Offset(0f, top), Size(w, fh))
            val n = if (type == FloorType.PARQUET) 22 else 12
            val rnd = Random(9)
            for (i in -n..n * 2) {
                val xb = i * (w / n)
                val t = (h - vp.y) / (top - vp.y)
                val xt = vp.x + (xb - vp.x) / t
                drawLine(base.darken(0.35f), Offset(xt, top), Offset(xb, h), 1.5f)
            }
            // Querfugen
            var y = top + fh * 0.08f
            var k = 1f
            while (y < h) {
                drawLine(base.darken(0.25f).copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), 1f)
                k *= 1.35f
                y += fh * 0.06f * k
            }
            repeat(18) {
                val x = rnd.nextFloat() * w
                val yy = top + rnd.nextFloat() * fh
                drawLine(base.lighten(0.12f).copy(alpha = 0.25f), Offset(x, yy), Offset(x + w * 0.05f, yy), 1f)
            }
        }
        FloorType.CARPET -> {
            val base = Color(0xFF7D6A5A)
            drawRect(Brush.verticalGradient(listOf(base.darken(0.35f), base), top, h), Offset(0f, top), Size(w, fh))
            val rnd = Random(4)
            repeat(400) {
                drawCircle(base.lighten(rnd.nextFloat() * 0.15f).copy(alpha = 0.35f), 1.2f, Offset(rnd.nextFloat() * w, top + rnd.nextFloat() * fh))
            }
        }
        FloorType.TILES -> {
            val a = Color(0xFFD9CFC0)
            val b = Color(0xFF6E4E3A)
            drawRect(a, Offset(0f, top), Size(w, fh))
            val rows = 7
            var y0 = top
            for (row in 0 until rows) {
                val t0 = row / rows.toFloat()
                val t1 = (row + 1) / rows.toFloat()
                val y1 = top + fh * (t1 * t1 * 0.6f + t1 * 0.4f)
                val yA = top + fh * (t0 * t0 * 0.6f + t0 * 0.4f)
                val cols = 14
                for (c in -cols..cols * 2) {
                    if ((c + row) % 2 != 0) continue
                    fun xAt(xb: Float, yy: Float): Float {
                        val t = (h - vp.y) / (yy - vp.y)
                        return vp.x + (xb - vp.x) / t
                    }
                    val xb0 = c * (w / cols)
                    val xb1 = (c + 1) * (w / cols)
                    val p = Path().apply {
                        moveTo(xAt(xb0, yA), yA); lineTo(xAt(xb1, yA), yA); lineTo(xAt(xb1, y1), y1); lineTo(xAt(xb0, y1), y1); close()
                    }
                    drawPath(p, b)
                }
                y0 = y1
            }
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), top, top + fh * 0.5f), Offset(0f, top), Size(w, fh * 0.5f))
        }
        FloorType.STONE -> {
            val base = Color(0xFF8C8478)
            drawRect(Brush.verticalGradient(listOf(base.darken(0.4f), base), top, h), Offset(0f, top), Size(w, fh))
            val rnd = Random(6)
            var y = top
            var rh = fh * 0.12f
            while (y < h) {
                var x = -rnd.nextFloat() * w * 0.1f
                while (x < w) {
                    val sw = w * (0.08f + rnd.nextFloat() * 0.1f) * (rh / (fh * 0.12f))
                    drawRoundRect(base.darken(rnd.nextFloat() * 0.2f), Offset(x + 2, y + 2), Size(sw - 4, rh - 4), CornerRadius(4f))
                    x += sw
                }
                y += rh
                rh *= 1.3f
            }
        }
    }
}

internal fun DrawScope.drawRug(r: Rect, room: ReadingRoom) {
    val c = Color(room.curtainColor)
    drawOval(Color.Black.copy(alpha = 0.25f), r.topLeft + Offset(0f, 6f), r.size)
    drawOval(c.darken(0.25f), r.topLeft, r.size)
    val inset = r.height * 0.12f
    drawOval(c.lighten(0.15f), Offset(r.left + inset * 1.6f, r.top + inset), Size(r.width - inset * 3.2f, r.height - inset * 2))
    drawOval(Color(0xFFE6C98F).copy(alpha = 0.7f), Offset(r.left + inset * 2.4f, r.top + inset * 1.5f), Size(r.width - inset * 4.8f, r.height - inset * 3), style = Stroke(2.5f))
    drawOval(c.darken(0.1f), Offset(r.left + r.width * 0.38f, r.top + r.height * 0.38f), Size(r.width * 0.24f, r.height * 0.24f))
}

internal fun DrawScope.drawFireplace(r: Rect) {
    val stone = Color(0xFFB8A68E)
    val mantleH = r.height * 0.09f
    // Korpus
    drawRect(Brush.verticalGradient(listOf(stone.lighten(0.1f), stone.darken(0.15f)), r.top, r.bottom), Offset(r.left + r.width * 0.04f, r.top + mantleH), Size(r.width * 0.92f, r.height - mantleH))
    // Steinfugen
    val rows = 7
    for (i in 1 until rows) {
        val y = r.top + mantleH + (r.height - mantleH) * i / rows
        drawLine(stone.darken(0.3f), Offset(r.left + r.width * 0.04f, y), Offset(r.right - r.width * 0.04f, y), 1.5f)
    }
    // Sims
    drawRect(Color(0xFF5B3A26), Offset(r.left, r.top), Size(r.width, mantleH))
    drawRect(Color(0xFF7A5236), Offset(r.left, r.top), Size(r.width, mantleH * 0.35f))
    // Öffnung
    val o = fireOpening(r)
    val arch = Path().apply {
        moveTo(o.left, o.bottom)
        lineTo(o.left, o.top + o.height * 0.3f)
        quadraticTo(o.center.x, o.top - o.height * 0.15f, o.right, o.top + o.height * 0.3f)
        lineTo(o.right, o.bottom)
        close()
    }
    drawPath(arch, Color(0xFF6E5C4A))
    val inner = Path().apply {
        moveTo(o.left + o.width * 0.05f, o.bottom)
        lineTo(o.left + o.width * 0.05f, o.top + o.height * 0.33f)
        quadraticTo(o.center.x, o.top - o.height * 0.05f, o.right - o.width * 0.05f, o.top + o.height * 0.33f)
        lineTo(o.right - o.width * 0.05f, o.bottom)
        close()
    }
    drawPath(inner, Brush.verticalGradient(listOf(Color(0xFF120A06), Color(0xFF2A160C)), o.top, o.bottom))
    // Holzscheite
    val logY = o.bottom - o.height * 0.12f
    drawRoundRect(Color(0xFF4A2E1A), Offset(o.left + o.width * 0.2f, logY), Size(o.width * 0.6f, o.height * 0.1f), CornerRadius(8f))
    rotate(-12f, Offset(o.center.x, logY)) {
        drawRoundRect(Color(0xFF5E3B22), Offset(o.left + o.width * 0.25f, logY - o.height * 0.06f), Size(o.width * 0.5f, o.height * 0.09f), CornerRadius(8f))
    }
    // Feuerrost
    drawRect(Color(0xFF1C1C1C), Offset(o.left + o.width * 0.12f, o.bottom - o.height * 0.03f), Size(o.width * 0.76f, o.height * 0.03f))
}

fun fireOpening(r: Rect): Rect {
    val mantleH = r.height * 0.09f
    return Rect(r.left + r.width * 0.22f, r.top + mantleH + r.height * 0.22f, r.right - r.width * 0.22f, r.bottom - r.height * 0.04f)
}

internal fun DrawScope.drawPiano(r: Rect) {
    val body = Color(0xFF1E1714)
    val hi = Color(0xFF3B302A)
    // Korpus
    drawRoundRect(Brush.horizontalGradient(listOf(body, hi, body), r.left, r.right), Offset(r.left, r.top), Size(r.width, r.height * 0.82f), CornerRadius(6f))
    // Notenpult
    drawRect(Color(0xFF2A211C), Offset(r.left + r.width * 0.25f, r.top + r.height * 0.18f), Size(r.width * 0.5f, r.height * 0.16f))
    drawRect(Color(0xFFF2E8D5), Offset(r.left + r.width * 0.32f, r.top + r.height * 0.15f), Size(r.width * 0.15f, r.height * 0.17f))
    drawRect(Color(0xFFEDE1CA), Offset(r.left + r.width * 0.48f, r.top + r.height * 0.15f), Size(r.width * 0.15f, r.height * 0.17f))
    for (i in 0 until 5) {
        val y = r.top + r.height * (0.19f + i * 0.025f)
        drawLine(Color(0xFF6B5B4A), Offset(r.left + r.width * 0.34f, y), Offset(r.left + r.width * 0.45f, y), 1f)
        drawLine(Color(0xFF6B5B4A), Offset(r.left + r.width * 0.5f, y), Offset(r.left + r.width * 0.61f, y), 1f)
    }
    // Tastatur
    val ky = r.top + r.height * 0.46f
    val kh = r.height * 0.11f
    drawRect(Color(0xFFF7F2E8), Offset(r.left + r.width * 0.04f, ky), Size(r.width * 0.92f, kh))
    val keys = 28
    val kw = r.width * 0.92f / keys
    for (i in 0..keys) drawLine(Color(0xFF9C9488), Offset(r.left + r.width * 0.04f + i * kw, ky), Offset(r.left + r.width * 0.04f + i * kw, ky + kh), 1f)
    for (i in 0 until keys) {
        if (i % 7 in setOf(0, 1, 3, 4, 5)) drawRect(Color(0xFF111111), Offset(r.left + r.width * 0.04f + i * kw + kw * 0.65f, ky), Size(kw * 0.7f, kh * 0.6f))
    }
    drawRect(body, Offset(r.left, ky + kh), Size(r.width, r.height * 0.05f))
    // Beine & Pedale
    drawRect(body, Offset(r.left + r.width * 0.03f, r.top + r.height * 0.82f), Size(r.width * 0.05f, r.height * 0.18f))
    drawRect(body, Offset(r.right - r.width * 0.08f, r.top + r.height * 0.82f), Size(r.width * 0.05f, r.height * 0.18f))
    drawRect(Color(0xFFC9A45A), Offset(r.center.x - r.width * 0.06f, r.bottom - r.height * 0.03f), Size(r.width * 0.12f, r.height * 0.02f))
    // Kerzenhalter-Glanz
    drawLine(Color.White.copy(alpha = 0.12f), Offset(r.left + r.width * 0.05f, r.top + 4f), Offset(r.right - r.width * 0.05f, r.top + 4f), 2f)
    // Klavierbank
    val bw = r.width * 0.42f
    val bx = r.center.x - bw / 2
    val by = r.bottom + r.height * 0.06f
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(bx, by + r.height * 0.02f), Size(bw, r.height * 0.03f))
    drawRoundRect(Color(0xFF4A2D20), Offset(bx, by - r.height * 0.14f), Size(bw, r.height * 0.06f), CornerRadius(5f))
    drawRect(Color(0xFF2A1A12), Offset(bx + bw * 0.05f, by - r.height * 0.08f), Size(bw * 0.05f, r.height * 0.1f))
    drawRect(Color(0xFF2A1A12), Offset(bx + bw * 0.9f, by - r.height * 0.08f), Size(bw * 0.05f, r.height * 0.1f))
}

internal fun DrawScope.drawSofa(r: Rect, c: Color) {
    val base = c.darken(0.1f)
    // Lehne
    drawRoundRect(Brush.verticalGradient(listOf(base.lighten(0.08f), base.darken(0.15f)), r.top, r.bottom), Offset(r.left + r.width * 0.06f, r.top), Size(r.width * 0.88f, r.height * 0.6f), CornerRadius(r.height * 0.2f))
    // Sitz
    drawRoundRect(base.darken(0.05f), Offset(r.left + r.width * 0.05f, r.top + r.height * 0.5f), Size(r.width * 0.9f, r.height * 0.3f), CornerRadius(r.height * 0.1f))
    drawLine(base.darken(0.3f), Offset(r.center.x, r.top + r.height * 0.52f), Offset(r.center.x, r.top + r.height * 0.78f), 2f)
    // Armlehnen
    drawRoundRect(base.lighten(0.04f), Offset(r.left, r.top + r.height * 0.3f), Size(r.width * 0.11f, r.height * 0.55f), CornerRadius(r.height * 0.12f))
    drawRoundRect(base.lighten(0.04f), Offset(r.right - r.width * 0.11f, r.top + r.height * 0.3f), Size(r.width * 0.11f, r.height * 0.55f), CornerRadius(r.height * 0.12f))
    // Kissen
    drawRoundRect(Color(0xFFE8C77E), Offset(r.left + r.width * 0.14f, r.top + r.height * 0.22f), Size(r.width * 0.16f, r.height * 0.3f), CornerRadius(12f))
    drawRoundRect(Color(0xFFF2E4C9), Offset(r.right - r.width * 0.3f, r.top + r.height * 0.24f), Size(r.width * 0.15f, r.height * 0.28f), CornerRadius(12f))
    // Füße
    drawRect(Color(0xFF3A2416), Offset(r.left + r.width * 0.07f, r.top + r.height * 0.8f), Size(r.width * 0.03f, r.height * 0.2f))
    drawRect(Color(0xFF3A2416), Offset(r.right - r.width * 0.1f, r.top + r.height * 0.8f), Size(r.width * 0.03f, r.height * 0.2f))
    // Decke über der Lehne
    val throwPath = Path().apply {
        moveTo(r.right - r.width * 0.35f, r.top + r.height * 0.05f)
        lineTo(r.right - r.width * 0.18f, r.top + r.height * 0.03f)
        lineTo(r.right - r.width * 0.16f, r.top + r.height * 0.75f)
        lineTo(r.right - r.width * 0.32f, r.top + r.height * 0.7f)
        close()
    }
    drawPath(throwPath, Color(0xFFD9CBB2).copy(alpha = 0.9f))
    for (i in 1..3) drawLine(Color(0xFF9C2F2F).copy(alpha = 0.5f), Offset(r.right - r.width * (0.35f - i * 0.04f), r.top + r.height * 0.05f), Offset(r.right - r.width * (0.32f - i * 0.04f), r.top + r.height * 0.7f), 2f)
}

internal fun DrawScope.drawArmchair(r: Rect, c: Color) {
    val base = c
    // Ohrensessel: hohe Lehne mit „Ohren“
    val back = Path().apply {
        moveTo(r.left + r.width * 0.15f, r.top + r.height * 0.6f)
        lineTo(r.left + r.width * 0.12f, r.top + r.height * 0.15f)
        quadraticTo(r.left + r.width * 0.12f, r.top, r.left + r.width * 0.3f, r.top)
        lineTo(r.right - r.width * 0.3f, r.top)
        quadraticTo(r.right - r.width * 0.12f, r.top, r.right - r.width * 0.12f, r.top + r.height * 0.15f)
        lineTo(r.right - r.width * 0.15f, r.top + r.height * 0.6f)
        close()
    }
    drawPath(back, Brush.verticalGradient(listOf(base.lighten(0.08f), base.darken(0.2f)), r.top, r.bottom))
    // Knopfheftung
    for (i in 0 until 3) for (j in 0 until 2) drawCircle(base.darken(0.35f), 2.5f, Offset(r.left + r.width * (0.35f + i * 0.15f), r.top + r.height * (0.15f + j * 0.15f)))
    // Sitz
    drawRoundRect(base.darken(0.08f), Offset(r.left + r.width * 0.12f, r.top + r.height * 0.52f), Size(r.width * 0.76f, r.height * 0.2f), CornerRadius(10f))
    // Armlehnen
    drawRoundRect(base.lighten(0.04f), Offset(r.left, r.top + r.height * 0.35f), Size(r.width * 0.2f, r.height * 0.45f), CornerRadius(r.width * 0.08f))
    drawRoundRect(base.lighten(0.04f), Offset(r.right - r.width * 0.2f, r.top + r.height * 0.35f), Size(r.width * 0.2f, r.height * 0.45f), CornerRadius(r.width * 0.08f))
    drawLine(base.darken(0.3f), Offset(r.left + r.width * 0.2f, r.top + r.height * 0.4f), Offset(r.left + r.width * 0.2f, r.top + r.height * 0.78f), 2f)
    drawLine(base.darken(0.3f), Offset(r.right - r.width * 0.2f, r.top + r.height * 0.4f), Offset(r.right - r.width * 0.2f, r.top + r.height * 0.78f), 2f)
    // Füße
    drawRect(Color(0xFF3A2416), Offset(r.left + r.width * 0.08f, r.top + r.height * 0.8f), Size(r.width * 0.06f, r.height * 0.2f))
    drawRect(Color(0xFF3A2416), Offset(r.right - r.width * 0.14f, r.top + r.height * 0.8f), Size(r.width * 0.06f, r.height * 0.2f))
    // Buch auf der Armlehne
    drawRect(Color(0xFF7B2D3B), Offset(r.right - r.width * 0.2f, r.top + r.height * 0.31f), Size(r.width * 0.18f, r.height * 0.05f))
    drawRect(Color(0xFFF2E8D5), Offset(r.right - r.width * 0.19f, r.top + r.height * 0.32f), Size(r.width * 0.16f, r.height * 0.02f))
}

internal fun DrawScope.drawBed(r: Rect, c: Color) {
    val wood = Color(0xFF6B4630)
    // Kopfteil
    drawRoundRect(wood, Offset(r.left, r.top - r.height * 0.2f), Size(r.width * 0.12f, r.height * 1.2f), CornerRadius(8f))
    drawRoundRect(wood.lighten(0.1f), Offset(r.left + r.width * 0.02f, r.top - r.height * 0.15f), Size(r.width * 0.08f, r.height * 0.5f), CornerRadius(6f))
    // Matratze
    drawRoundRect(Color(0xFFF2EBDD), Offset(r.left + r.width * 0.08f, r.top + r.height * 0.3f), Size(r.width * 0.9f, r.height * 0.32f), CornerRadius(12f))
    // Kissen
    drawRoundRect(Color.White, Offset(r.left + r.width * 0.11f, r.top + r.height * 0.12f), Size(r.width * 0.2f, r.height * 0.25f), CornerRadius(20f))
    drawRoundRect(Color(0xFFE9DFCC), Offset(r.left + r.width * 0.13f, r.top + r.height * 0.17f), Size(r.width * 0.18f, r.height * 0.22f), CornerRadius(20f))
    // Decke
    val quilt = Path().apply {
        moveTo(r.left + r.width * 0.3f, r.top + r.height * 0.25f)
        quadraticTo(r.left + r.width * 0.6f, r.top + r.height * 0.15f, r.right, r.top + r.height * 0.28f)
        lineTo(r.right, r.top + r.height * 0.72f)
        lineTo(r.left + r.width * 0.3f, r.top + r.height * 0.72f)
        close()
    }
    drawPath(quilt, Brush.verticalGradient(listOf(c.lighten(0.1f), c.darken(0.15f)), r.top, r.bottom))
    for (i in 1..4) {
        val x = r.left + r.width * (0.3f + i * 0.14f)
        drawLine(c.darken(0.3f).copy(alpha = 0.6f), Offset(x, r.top + r.height * 0.24f), Offset(x, r.top + r.height * 0.72f), 1.5f)
    }
    // Bettgestell
    drawRect(wood.darken(0.1f), Offset(r.left + r.width * 0.08f, r.top + r.height * 0.62f), Size(r.width * 0.92f, r.height * 0.18f))
    drawRect(wood.darken(0.3f), Offset(r.right - r.width * 0.04f, r.top + r.height * 0.4f), Size(r.width * 0.04f, r.height * 0.6f))
    drawRect(wood.darken(0.3f), Offset(r.left + r.width * 0.09f, r.top + r.height * 0.8f), Size(r.width * 0.03f, r.height * 0.2f))
}

internal fun DrawScope.drawDesk(r: Rect) {
    val wood = Color(0xFF5B3A26)
    drawRect(wood.lighten(0.08f), Offset(r.left, r.top + r.height * 0.25f), Size(r.width, r.height * 0.08f))
    drawRect(wood, Offset(r.left + r.width * 0.02f, r.top + r.height * 0.33f), Size(r.width * 0.3f, r.height * 0.67f))
    for (i in 0 until 3) {
        val y = r.top + r.height * (0.38f + i * 0.2f)
        drawRect(wood.darken(0.2f), Offset(r.left + r.width * 0.04f, y), Size(r.width * 0.26f, r.height * 0.17f), style = Stroke(2f))
        drawCircle(Color(0xFFC9A45A), 3f, Offset(r.left + r.width * 0.17f, y + r.height * 0.085f))
    }
    drawRect(wood, Offset(r.right - r.width * 0.06f, r.top + r.height * 0.33f), Size(r.width * 0.04f, r.height * 0.67f))
    // Papiere, Tintenfass, Feder
    drawRect(Color(0xFFF4ECDC), Offset(r.left + r.width * 0.42f, r.top + r.height * 0.2f), Size(r.width * 0.2f, r.height * 0.06f))
    drawCircle(Color(0xFF1B2440), r.width * 0.025f, Offset(r.left + r.width * 0.72f, r.top + r.height * 0.21f))
    drawLine(Color(0xFFEDE6DA), Offset(r.left + r.width * 0.73f, r.top + r.height * 0.2f), Offset(r.left + r.width * 0.8f, r.top - r.height * 0.1f), 3f, StrokeCap.Round)
    // Schreibtischlampe
    val lx = r.left + r.width * 0.2f
    drawLine(Color(0xFF2E4A3A), Offset(lx, r.top + r.height * 0.25f), Offset(lx, r.top - r.height * 0.15f), 3f)
    val shade = Path().apply {
        moveTo(lx - r.width * 0.09f, r.top - r.height * 0.15f)
        lineTo(lx + r.width * 0.09f, r.top - r.height * 0.15f)
        lineTo(lx + r.width * 0.05f, r.top - r.height * 0.3f)
        lineTo(lx - r.width * 0.05f, r.top - r.height * 0.3f)
        close()
    }
    drawPath(shade, Color(0xFF2E5A44))
    // Stuhl
    drawRect(Color(0xFF3F271A), Offset(r.left + r.width * 0.5f, r.top + r.height * 0.4f), Size(r.width * 0.2f, r.height * 0.06f))
    drawRect(Color(0xFF3F271A), Offset(r.left + r.width * 0.68f, r.top - r.height * 0.05f), Size(r.width * 0.03f, r.height * 1.05f))
    drawRect(Color(0xFF3F271A), Offset(r.left + r.width * 0.51f, r.top + r.height * 0.46f), Size(r.width * 0.025f, r.height * 0.54f))
}

internal fun DrawScope.drawLamp(r: Rect) {
    val brass = Color(0xFFB08D57)
    val cx = r.center.x
    drawLine(brass.darken(0.2f), Offset(cx, r.top + r.height * 0.18f), Offset(cx, r.bottom - r.height * 0.02f), r.width * 0.07f)
    drawOval(brass.darken(0.3f), Offset(cx - r.width * 0.35f, r.bottom - r.height * 0.03f), Size(r.width * 0.7f, r.height * 0.03f))
    val shade = Path().apply {
        moveTo(cx - r.width * 0.28f, r.top)
        lineTo(cx + r.width * 0.28f, r.top)
        lineTo(cx + r.width * 0.5f, r.top + r.height * 0.2f)
        lineTo(cx - r.width * 0.5f, r.top + r.height * 0.2f)
        close()
    }
    drawPath(shade, Brush.verticalGradient(listOf(Color(0xFFF5DDB0), Color(0xFFE6B970)), r.top, r.top + r.height * 0.2f))
    drawLine(Color(0xFFB0874B), Offset(cx - r.width * 0.5f, r.top + r.height * 0.2f), Offset(cx + r.width * 0.5f, r.top + r.height * 0.2f), 2f)
}

internal fun DrawScope.drawPlant(r: Rect) {
    val pot = Color(0xFFB0643E)
    val potTop = r.top + r.height * 0.62f
    val potPath = Path().apply {
        moveTo(r.left + r.width * 0.15f, potTop)
        lineTo(r.right - r.width * 0.15f, potTop)
        lineTo(r.right - r.width * 0.25f, r.bottom)
        lineTo(r.left + r.width * 0.25f, r.bottom)
        close()
    }
    val leaf = Color(0xFF3F7A4A)
    val rnd = Random(2)
    for (i in 0 until 9) {
        val a = -PI / 2 + (i - 4) * 0.32 + rnd.nextFloat() * 0.1
        val len = r.height * (0.45f + rnd.nextFloat() * 0.2f)
        val base = Offset(r.center.x, potTop)
        val tip = base + Offset((cos(a) * len).toFloat(), (sin(a) * len).toFloat())
        drawLine(leaf.darken(0.3f), base, tip, 2f)
        withTransform({
            rotate((a * 180 / PI).toFloat() + 90f, tip)
        }) {
            drawOval(if (i % 2 == 0) leaf else leaf.lighten(0.12f), Offset(tip.x - r.width * 0.14f, tip.y - r.height * 0.02f), Size(r.width * 0.28f, r.height * 0.18f))
        }
    }
    drawPath(potPath, Brush.horizontalGradient(listOf(pot.darken(0.2f), pot.lighten(0.1f), pot.darken(0.25f)), r.left, r.right))
    drawRect(pot.darken(0.1f), Offset(r.left + r.width * 0.12f, potTop - r.height * 0.03f), Size(r.width * 0.76f, r.height * 0.05f))
}

internal fun DrawScope.drawGlobe(r: Rect) {
    val cx = r.center.x
    val rad = r.width * 0.45f
    val cy = r.top + rad
    drawLine(Color(0xFF3F271A), Offset(cx, cy + rad), Offset(cx, r.bottom - r.height * 0.05f), 4f)
    drawLine(Color(0xFF3F271A), Offset(cx - r.width * 0.3f, r.bottom), Offset(cx + r.width * 0.3f, r.bottom), 5f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF8FB3C9), Color(0xFF3C6680)), Offset(cx - rad * 0.3f, cy - rad * 0.3f), rad * 1.4f), rad, Offset(cx, cy))
    drawOval(Color(0xFFC9B27A), Offset(cx - rad * 0.6f, cy - rad * 0.5f), Size(rad * 0.7f, rad * 0.5f))
    drawOval(Color(0xFFC9B27A), Offset(cx + rad * 0.05f, cy + rad * 0.05f), Size(rad * 0.5f, rad * 0.6f))
    drawArc(Color(0xFFB08D57), -110f, 220f, false, Offset(cx - rad * 1.15f, cy - rad * 1.15f), Size(rad * 2.3f, rad * 2.3f), style = Stroke(3f))
}

internal fun DrawScope.drawSideTable(r: Rect) {
    val wood = Color(0xFF5B3A26)
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(r.left - 4f, r.bottom - 6f), Size(r.width + 8f, 12f))
    drawOval(wood.lighten(0.12f), Offset(r.left - r.width * 0.1f, r.top - r.height * 0.06f), Size(r.width * 1.2f, r.height * 0.14f))
    drawRect(wood, Offset(r.center.x - r.width * 0.06f, r.top + r.height * 0.06f), Size(r.width * 0.12f, r.height * 0.9f))
    drawOval(wood.darken(0.1f), Offset(r.left + r.width * 0.1f, r.bottom - r.height * 0.06f), Size(r.width * 0.8f, r.height * 0.08f))
}

internal fun DrawScope.drawTea(p: Offset, w: Float) {
    val s = w * 0.022f
    drawOval(Color(0xFFF4EFE6), Offset(p.x - s * 1.4f, p.y - s * 0.25f), Size(s * 2.8f, s * 0.5f))
    drawRoundRect(Color(0xFFF7F3EC), Offset(p.x - s, p.y - s * 1.3f), Size(s * 2f, s * 1.3f), CornerRadius(s * 0.4f))
    drawArc(Color(0xFFF7F3EC), -90f, 180f, false, Offset(p.x + s * 0.7f, p.y - s * 1.1f), Size(s * 0.8f, s * 0.8f), style = Stroke(s * 0.22f))
    drawRect(Color(0xFF9C2F2F), Offset(p.x - s, p.y - s * 0.9f), Size(s * 2f, s * 0.2f))
}

internal fun DrawScope.drawCandleBody(p: Offset, w: Float) {
    val cw = w * 0.012f
    val ch = w * 0.04f
    drawRect(Color(0xFFF7EEDB), Offset(p.x - cw / 2, p.y - ch), Size(cw, ch))
    drawRect(Color.Black.copy(alpha = 0.08f), Offset(p.x, p.y - ch), Size(cw / 2, ch))
    drawOval(Color(0xFFB08D57), Offset(p.x - cw * 1.2f, p.y - cw * 0.3f), Size(cw * 2.4f, cw * 0.6f))
}

internal fun DrawScope.drawBookstack(r: Rect) {
    val colors = listOf(Color(0xFF7B2D3B), Color(0xFF2F4A3F), Color(0xFF34465E), Color(0xFFB08D57), Color(0xFF5C2E2E))
    val n = 5
    val bh = r.height / n
    for (i in 0 until n) {
        val off = (if (i % 2 == 0) 1 else -1) * r.width * 0.05f * (i % 3)
        val y = r.bottom - (i + 1) * bh
        drawRect(colors[i % colors.size], Offset(r.left + off, y), Size(r.width * (0.9f - i * 0.04f), bh * 0.92f))
        drawRect(Color(0xFFF2E8D5), Offset(r.left + off + r.width * 0.86f - i * r.width * 0.04f, y + bh * 0.12f), Size(r.width * 0.035f, bh * 0.68f))
        drawLine(Color(0xFFD9B97F), Offset(r.left + off + r.width * 0.1f, y + bh * 0.45f), Offset(r.left + off + r.width * 0.3f, y + bh * 0.45f), 1.5f)
    }
}

// ------------------------------------------------------------------------------------------------
// Animierte Ebene
// ------------------------------------------------------------------------------------------------
internal fun DrawScope.drawFire(r: Rect, t: Float) {
    // Glut
    drawOval(Brush.radialGradient(listOf(FireOrange.copy(alpha = 0.9f), Color(0xFF8A2A0A).copy(alpha = 0.6f), Color.Transparent), Offset(r.center.x, r.bottom), r.width * 0.6f), Offset(r.left, r.bottom - r.height * 0.2f), Size(r.width, r.height * 0.3f))
    val flames = 7
    for (layer in 0 until 3) {
        val col = when (layer) { 0 -> Color(0xFFD9481E); 1 -> FireOrange; else -> FireYellow }
        val scale = 1f - layer * 0.28f
        for (i in 0 until flames) {
            val fx = r.left + r.width * (0.12f + i * 0.76f / (flames - 1))
            val phase = t * (5f + i * 0.7f) + i * 1.9f + layer
            val fh = r.height * scale * (0.55f + 0.3f * sin(phase) * sin(phase * 0.53f + 1f) + 0.15f * (if (i in 2..4) 1f else 0f))
            val fw = r.width * 0.14f * scale
            val sway = sin(phase * 0.8f) * fw * 0.5f
            val p = Path().apply {
                moveTo(fx - fw, r.bottom)
                quadraticTo(fx - fw * 1.1f, r.bottom - fh * 0.5f, fx + sway, r.bottom - fh)
                quadraticTo(fx + fw * 1.1f, r.bottom - fh * 0.5f, fx + fw, r.bottom)
                close()
            }
            drawPath(p, col.copy(alpha = 0.85f))
        }
    }
    // Funken
    for (i in 0 until 6) {
        val ph = ((t * 0.6f + i * 0.17f) % 1f)
        val x = r.center.x + sin(i * 12.3f + t) * r.width * 0.3f
        val y = r.bottom - ph * r.height * 1.3f
        drawCircle(FireYellow.copy(alpha = (1 - ph) * 0.9f), 1.6f, Offset(x, y))
    }
}

internal fun DrawScope.drawFlame(p: Offset, s: Float, t: Float) {
    val flick = 0.85f + 0.15f * sin(t * 11f) * sin(t * 7.3f)
    drawCircle(Glow.copy(alpha = 0.25f), s * 3.2f * flick, p)
    val sway = sin(t * 4f) * s * 0.25f
    val path = Path().apply {
        moveTo(p.x - s * 0.5f, p.y + s * 0.6f)
        quadraticTo(p.x - s * 0.7f, p.y - s * 0.4f, p.x + sway, p.y - s * 1.6f * flick)
        quadraticTo(p.x + s * 0.7f, p.y - s * 0.4f, p.x + s * 0.5f, p.y + s * 0.6f)
        close()
    }
    drawPath(path, FireYellow)
    drawCircle(Color(0xFFFFF6D8), s * 0.28f, Offset(p.x, p.y + s * 0.15f))
}

internal fun DrawScope.drawCat(r: Rect, t: Float) {
    val breathe = 1f + 0.03f * sin(t * 1.6f)
    val fur = Color(0xFFB8743E)
    val dark = fur.darken(0.3f)
    // Schatten
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(r.left, r.bottom - r.height * 0.15f), Size(r.width, r.height * 0.3f))
    // Körper (zusammengerollt)
    withTransform({ scale(1f, breathe, Offset(r.center.x, r.bottom)) }) {
        drawOval(Brush.verticalGradient(listOf(fur.lighten(0.1f), fur.darken(0.1f)), r.top, r.bottom), Offset(r.left + r.width * 0.05f, r.top + r.height * 0.2f), Size(r.width * 0.85f, r.height * 0.85f))
        // Streifen
        for (i in 0 until 4) {
            val x = r.left + r.width * (0.25f + i * 0.13f)
            drawArc(dark.copy(alpha = 0.6f), 200f, 60f, false, Offset(x - r.width * 0.08f, r.top + r.height * 0.25f), Size(r.width * 0.16f, r.height * 0.4f), style = Stroke(3f))
        }
    }
    // Schwanz
    drawArc(fur.darken(0.05f), 20f, 160f, false, Offset(r.left + r.width * 0.05f, r.top + r.height * 0.55f), Size(r.width * 0.8f, r.height * 0.5f), style = Stroke(r.height * 0.14f, cap = StrokeCap.Round))
    // Kopf
    val head = Offset(r.right - r.width * 0.12f, r.top + r.height * 0.62f)
    val hr = r.height * 0.3f
    drawCircle(fur, hr, head)
    val ear1 = Path().apply { moveTo(head.x - hr * 0.8f, head.y - hr * 0.4f); lineTo(head.x - hr * 0.5f, head.y - hr * 1.3f); lineTo(head.x - hr * 0.05f, head.y - hr * 0.8f); close() }
    val ear2 = Path().apply { moveTo(head.x + hr * 0.1f, head.y - hr * 0.85f); lineTo(head.x + hr * 0.6f, head.y - hr * 1.25f); lineTo(head.x + hr * 0.85f, head.y - hr * 0.3f); close() }
    drawPath(ear1, fur); drawPath(ear2, fur)
    // geschlossene Augen
    drawArc(Color(0xFF2B1A10), 20f, 140f, false, Offset(head.x - hr * 0.6f, head.y - hr * 0.2f), Size(hr * 0.45f, hr * 0.3f), style = Stroke(2f))
    drawArc(Color(0xFF2B1A10), 20f, 140f, false, Offset(head.x + hr * 0.1f, head.y - hr * 0.2f), Size(hr * 0.45f, hr * 0.3f), style = Stroke(2f))
    drawCircle(Color(0xFFD98C8C), hr * 0.09f, Offset(head.x - hr * 0.05f, head.y + hr * 0.25f))
    // Zzz
    val ph = (t * 0.25f) % 1f
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb((200 * (1 - ph)).toInt(), 255, 245, 220)
            textSize = hr * (0.7f + ph * 0.6f)
            isAntiAlias = true
            typeface = android.graphics.Typeface.SERIF
        }
        drawText("z", head.x + hr * (0.6f + ph), head.y - hr * (1.4f + ph * 2.2f), paint)
    }
}
