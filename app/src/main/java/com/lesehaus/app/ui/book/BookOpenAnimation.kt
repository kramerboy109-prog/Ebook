package com.lesehaus.app.ui.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lesehaus.app.data.db.Book
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Zustand für die Öffnen-Animation: welches Buch, von wo aus (Bildschirmkoordinaten). */
data class OpeningBook(val book: Book, val from: Rect)

/**
 * Das Buch wird aus dem Regal gezogen, in die Mitte geholt, der Einband klappt auf
 * und die erste Seite füllt den Bildschirm – dann startet der Reader.
 */
@Composable
fun BookOpeningOverlay(
    opening: OpeningBook,
    paperColor: Color,
    onOpened: () -> Unit,
) {
    val move = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    val zoom = remember { Animatable(0f) }
    LaunchedEffect(opening) {
        move.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        launch { flip.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }.join()
        zoom.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        onOpened()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sw = with(density) { maxWidth.toPx() }
        val sh = with(density) { maxHeight.toPx() }
        // Zielgröße in der Mitte
        val tw = sw * 0.56f
        val th = tw * 1.5f
        val target = Rect(Offset((sw - tw) / 2f + tw * 0.25f * flip.value, (sh - th) / 2f), androidx.compose.ui.geometry.Size(tw, th))
        val from = opening.from
        val m = move.value
        val cur = Rect(
            left = from.left + (target.left - from.left) * m,
            top = from.top + (target.top - from.top) * m,
            right = from.right + (target.right - from.right) * m,
            bottom = from.bottom + (target.bottom - from.bottom) * m,
        )
        // Hintergrund abdunkeln
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * m + 0.45f * zoom.value)))
        // Seitenblock unter dem Einband
        val z = zoom.value
        val pageRect = Rect(
            left = cur.left + (0f - cur.left) * z,
            top = cur.top + (0f - cur.top) * z,
            right = cur.right + (sw - cur.right) * z,
            bottom = cur.bottom + (sh - cur.bottom) * z,
        )
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()) }
                    .size(pageRect.width.toDp(), pageRect.height.toDp())
                    .graphicsLayer { alpha = (flip.value * 3f).coerceAtMost(1f) }
                    .background(paperColor)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // angedeutete Zeilen der ersten Seite
                    val lines = 14
                    val mx = size.width * 0.14f
                    for (i in 0 until lines) {
                        val y = size.height * (0.2f + i * 0.045f)
                        val end = if (i % 5 == 4) size.width * 0.55f else size.width - mx
                        drawLine(Color(0x22000000), Offset(mx, y), Offset(end, y), 2.dp.toPx() * (1f - z))
                    }
                    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f * (1 - z)), Color.Transparent), 0f, size.width * 0.12f))
                }
            }
            // Einband, der aufklappt (Drehung um die linke Kante)
            if (flip.value < 0.999f) {
                Box(
                    Modifier
                        .offset { IntOffset(cur.left.roundToInt(), cur.top.roundToInt()) }
                        .size(cur.width.toDp(), cur.height.toDp())
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = -flip.value * 180f
                            cameraDistance = 14f * density.density
                            shadowElevation = 24f * (1 - flip.value)
                            alpha = if (flip.value > 0.5f) 0f else 1f
                        }
                ) {
                    BookCover(opening.book, Modifier.fillMaxSize(), titleSize = 18.dp)
                }
            }
        }
    }
}
