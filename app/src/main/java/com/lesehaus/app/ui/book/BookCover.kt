package com.lesehaus.app.ui.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.theme.Fonts
import java.io.File
import kotlin.math.abs

/** Stoffeinband-Farben für generierte Cover und Buchrücken. */
val ClothColors = listOf(
    Color(0xFF7B2D3B), Color(0xFF2F4A3F), Color(0xFF34465E), Color(0xFF8C5A2B), Color(0xFF5E3A6E),
    Color(0xFF2E5A5A), Color(0xFF6B4630), Color(0xFF8E3D31), Color(0xFF3B4B2A), Color(0xFF1F2F4A),
    Color(0xFF9C6B30), Color(0xFF4A2A3A),
)

fun bookColor(book: Book): Color =
    if (book.coverColor != 0) Color(book.coverColor) else ClothColors[abs((book.title + book.author).hashCode()) % ClothColors.size]

private fun ornamentStyle(book: Book) = abs(book.title.hashCode()) % 3

/** Zeigt das Cover eines Buchs – Bild oder ein stilvoll generierter Einband. */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    showSpineShade: Boolean = true,
    titleSize: Dp = 13.dp,
) {
    Box(modifier.clip(RectangleShape)) {
        val path = book.coverPath
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(book.title, book.author, bookColor(book), ornamentStyle(book), titleSize)
        }
        if (showSpineShade) {
            // Falz & Glanz für einen echten Buch-Look
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.035f to Color.White.copy(alpha = 0.18f),
                        0.07f to Color.Black.copy(alpha = 0.12f),
                        0.11f to Color.Transparent,
                        0.85f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f),
                    )
                )
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset.Zero, Offset(size.width, size.height * 0.6f)))
            }
        }
    }
}

@Composable
fun GeneratedCover(title: String, author: String, color: Color, style: Int, titleSize: Dp) {
    val gold = Color(0xFFE2C27E)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color.lighten(0.08f), color, color.darken(0.25f))))
            .drawWithContent {
                drawContent()
                val inset = size.minDimension * 0.07f
                val c = gold.copy(alpha = 0.85f)
                when (style) {
                    0 -> {
                        drawRect(c, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1.5.dp.toPx()))
                        drawRect(c, Offset(inset * 1.5f, inset * 1.5f), Size(size.width - inset * 3, size.height - inset * 3), style = Stroke(0.7.dp.toPx()))
                    }
                    1 -> {
                        drawLine(c, Offset(inset, size.height * 0.08f), Offset(size.width - inset, size.height * 0.08f), 1.2.dp.toPx())
                        drawLine(c, Offset(inset, size.height * 0.92f), Offset(size.width - inset, size.height * 0.92f), 1.2.dp.toPx())
                        drawCircle(c, size.minDimension * 0.03f, Offset(size.width / 2, size.height * 0.08f))
                        drawCircle(c, size.minDimension * 0.03f, Offset(size.width / 2, size.height * 0.92f))
                    }
                    else -> {
                        val r = size.minDimension * 0.05f
                        drawCircle(c, r, Offset(size.width / 2, size.height - inset * 2.2f), style = Stroke(1.dp.toPx()))
                        drawCircle(c, r * 0.5f, Offset(size.width / 2, size.height - inset * 2.2f))
                        drawRect(c, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1.dp.toPx()))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
        // Schriftgröße so wählen, dass das längste Wort in eine Zeile passt (keine Trennung mitten im Wort)
        val longest = title.split(' ', '-').maxOfOrNull { it.length } ?: 1
        val fitted = minOf(titleSize.value, maxWidth.value / (longest * 0.68f).coerceAtLeast(1f)).coerceAtLeast(7f)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = TextStyle(
                    fontFamily = Fonts.Playfair,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fitted.sp,
                    lineHeight = (fitted * 1.2f).sp,
                    hyphens = androidx.compose.ui.text.style.Hyphens.None,
                    color = Color(0xFFF6E7C4),
                    textAlign = TextAlign.Center,
                ),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (author.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(24.dp).height(1.dp).background(gold.copy(alpha = 0.7f)))
                Spacer(Modifier.height(6.dp))
                Text(
                    author,
                    style = TextStyle(
                        fontFamily = Fonts.Lora,
                        fontSize = (titleSize.value * 0.72f).sp,
                        color = Color(0xFFEBD9B4),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        }
    }
}

fun Color.argb() = toArgb()
