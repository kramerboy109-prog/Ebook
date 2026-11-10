package com.lesehaus.app.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.data.ShelfDisplay
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.data.scan.ScanState
import com.lesehaus.app.ui.book.BookCover
import com.lesehaus.app.ui.book.bookColor
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.theme.Fonts
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

val CoverWidth = 84.dp
val CoverHeight = 126.dp
val ShelfSidePadding = 14.dp

fun spineWidth(book: Book): Dp {
    val kb = (book.fileSize / 1024f).coerceAtLeast(10f)
    return (16 + (ln(kb) * 2.4f)).coerceIn(18f, 38f).dp
}

fun spineHeight(book: Book): Dp = (118 + abs(book.title.hashCode()) % 30).dp

/** Teilt Bücher in Regalreihen auf – je nach Darstellung und verfügbarer Breite. */
fun chunkBooks(books: List<Book>, display: ShelfDisplay, available: Dp): List<List<Book>> {
    val inner = available - ShelfSidePadding * 2 - 16.dp
    return when (display) {
        ShelfDisplay.COVERS -> {
            val per = ((inner + 10.dp) / (CoverWidth + 10.dp)).toInt().coerceAtLeast(2)
            books.chunked(per)
        }
        ShelfDisplay.SPINES -> {
            val rows = ArrayList<List<Book>>()
            var cur = ArrayList<Book>()
            var width = 0.dp
            for (b in books) {
                val bw = spineWidth(b) + 2.dp
                if (width + bw > inner && cur.isNotEmpty()) {
                    rows.add(cur); cur = ArrayList(); width = 0.dp
                }
                cur.add(b); width += bw
            }
            if (cur.isNotEmpty()) rows.add(cur)
            rows
        }
    }
}

/** Messing-Namensschild und Steuerung über einem Regal. */
@Composable
fun ShelfHeader(
    shelf: Shelf,
    count: Int,
    scan: ScanState?,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onToggleDisplay: () -> Unit,
) {
    val wood = enumOr(shelf.wood, Wood.WALNUT)
    val woodColor = Color(wood.base)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ShelfSidePadding)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .background(Brush.verticalGradient(listOf(woodColor.lighten(0.06f), woodColor.darken(0.1f))))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Messingschild
            Box(
                Modifier
                    .shadow(3.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFE8CC8C), Color(0xFFB08D57), Color(0xFFD9B97F))))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    shelf.name,
                    style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF3B2614)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            val onWood = if (wood == Wood.WHITE || wood == Wood.PINE) Color(0xFF3B2614) else Color(0xFFF2E3C6)
            Text(
                if (count == 1) "1 Buch" else "$count Bücher",
                style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = onWood.copy(alpha = 0.85f)),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleDisplay) {
                Icon(
                    if (enumOr(shelf.displayMode, ShelfDisplay.COVERS) == ShelfDisplay.COVERS) Icons.Outlined.ViewColumn else Icons.Outlined.ViewModule,
                    contentDescription = "Darstellung wechseln",
                    tint = onWood,
                )
            }
            IconButton(onClick = onScan) { Icon(Icons.Outlined.Refresh, contentDescription = "Regal aktualisieren", tint = onWood) }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Tune, contentDescription = "Regal einrichten", tint = onWood) }
        }
        if (scan != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (scan.found > 0) "Sortiere ein: ${scan.processed}/${scan.found} · ${scan.current}" else scan.current,
                style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = Color(0xFFF2E3C6)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (scan.found > 0) LinearProgressIndicator(
                progress = { scan.processed / scan.found.toFloat() },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFE8CC8C),
                trackColor = Color.Black.copy(alpha = 0.3f),
            ) else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color(0xFFE8CC8C), trackColor = Color.Black.copy(alpha = 0.3f))
        }
    }
}

/** Eine Reihe im Regal: Rückwand, Bücher, Regalbrett. */
@Composable
fun ShelfRow(
    shelf: Shelf,
    books: List<Book>,
    isLast: Boolean,
    onBookClick: (Book, Rect) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val wood = enumOr(shelf.wood, Wood.WALNUT)
    val display = enumOr(shelf.displayMode, ShelfDisplay.COVERS)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ShelfSidePadding)
            .then(if (isLast) Modifier.clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)) else Modifier)
            .drawBehind { drawShelfCase(wood, isLast) }
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = if (isLast) 26.dp else 18.dp)
    ) {
        Row(
            horizontalArrangement = if (display == ShelfDisplay.COVERS) Arrangement.spacedBy(10.dp) else Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            books.forEach { b ->
                when (display) {
                    ShelfDisplay.COVERS -> ShelfCoverBook(b, onBookClick, onBookLongClick)
                    ShelfDisplay.SPINES -> ShelfSpineBook(b, onBookClick, onBookLongClick)
                }
            }
            if (display == ShelfDisplay.SPINES && books.size < 6) {
                // Buchstütze
                Canvas(Modifier.size(22.dp, 60.dp)) {
                    val p = Path().apply { moveTo(0f, size.height); lineTo(0f, 0f); lineTo(size.width * 0.35f, 0f); lineTo(size.width, size.height); close() }
                    drawPath(p, Color(0xFFB08D57))
                    drawPath(p, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)))
                }
            }
        }
    }
}

/** Leeres Regal mit Hinweis. */
@Composable
fun EmptyShelfRow(shelf: Shelf, onEdit: () -> Unit) {
    val wood = enumOr(shelf.wood, Wood.WALNUT)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ShelfSidePadding)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .drawBehind { drawShelfCase(wood, true) }
            .combinedClickable(onClick = onEdit)
            .padding(horizontal = 24.dp, vertical = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (shelf.folderList.isEmpty()) "Dieses Regal ist noch leer." else "Keine passenden Bücher gefunden.",
                style = TextStyle(fontFamily = Fonts.Lora, fontSize = 15.sp, color = Color(0xFFF2E3C6)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (shelf.folderList.isEmpty()) "Tippe hier, um einen Ordner mit Büchern zu wählen." else "Tippe hier, um Ordner oder Dateitypen anzupassen.",
                style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = Color(0xFFF2E3C6).copy(alpha = 0.75f)),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShelfCase(wood: Wood, isLast: Boolean) {
    val base = Color(wood.base)
    val dark = Color(wood.dark)
    val light = Color(wood.light)
    val w = size.width
    val h = size.height
    val side = 8.dp.toPx()
    val board = (if (isLast) 20.dp else 12.dp).toPx()
    // Rückwand (dunkler, mit Maserung)
    drawRect(Brush.verticalGradient(listOf(dark.darken(0.35f), dark.darken(0.15f))), Offset(side, 0f), Size(w - side * 2, h - board))
    val rnd = Random(wood.ordinal * 31 + h.toInt())
    repeat(10) {
        val y = rnd.nextFloat() * (h - board)
        drawLine(dark.darken(0.45f).copy(alpha = 0.5f), Offset(side, y), Offset(w - side, y + rnd.nextFloat() * 8f - 4f), 1f)
    }
    // Schatten oben (vom Brett darüber)
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, 16.dp.toPx()), Offset(side, 0f), Size(w - side * 2, 16.dp.toPx()))
    // Seitenwände
    drawRect(Brush.horizontalGradient(listOf(light, base, dark), 0f, side), Offset(0f, 0f), Size(side, h))
    drawRect(Brush.horizontalGradient(listOf(dark, base, light.darken(0.1f)), w - side, w), Offset(w - side, 0f), Size(side, h))
    // Regalbrett
    val by = h - board
    drawRect(Brush.verticalGradient(listOf(light, base, dark), by, h), Offset(0f, by), Size(w, board))
    drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, by), Size(w, 1.5.dp.toPx()))
    repeat(3) {
        val y = by + board * (0.3f + it * 0.2f)
        drawLine(dark.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y + 1f), 1f)
    }
    // Schatten der Bücher auf dem Brett
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), by - 4.dp.toPx(), by), Offset(side, by - 4.dp.toPx()), Size(w - side * 2, 4.dp.toPx()))
}

@Composable
private fun ShelfCoverBook(book: Book, onClick: (Book, Rect) -> Unit, onLongClick: (Book) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val heightFactor = 0.93f + (abs(book.id.hashCode()) % 8) / 100f
    Box(
        Modifier
            .width(CoverWidth)
            .height(CoverHeight * heightFactor)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                shadowElevation = 10f
                shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                clip = true
            }
            .combinedClickable(onClick = { onClick(book, bounds) }, onLongClick = { onLongClick(book) })
    ) {
        BookCover(book, Modifier.fillMaxSize())
        BookBadges(book)
    }
}

@Composable
private fun BookBadges(book: Book) {
    Box(Modifier.fillMaxSize()) {
        if (book.status == "READING" && book.progress > 0f) {
            // Lesebändchen
            Canvas(Modifier.align(Alignment.TopEnd).offset(x = (-10).dp).size(9.dp, 30.dp)) {
                val p = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(size.width / 2, size.height * 0.8f); lineTo(0f, size.height); close()
                }
                drawPath(p, Color(0xFF9B2F3F))
                drawPath(p, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)))
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(book.progress.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .background(Color(0xFFE8CC8C))
            )
        }
        if (book.status == "FINISHED") {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2F5A47)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
        if (book.lastOpenedAt == 0L && System.currentTimeMillis() - book.addedAt < 3 * 24 * 3600_000L) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFB08D57))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) { Text("NEU", style = TextStyle(fontFamily = Fonts.Nunito, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color(0xFF2B1A0C))) }
        }
    }
}

@Composable
private fun ShelfSpineBook(book: Book, onClick: (Book, Rect) -> Unit, onLongClick: (Book) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val color = bookColor(book)
    val w = spineWidth(book)
    val h = spineHeight(book)
    val bands = abs(book.author.hashCode()) % 3
    Box(
        Modifier
            .width(w)
            .height(h)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { shadowElevation = 6f; shape = RoundedCornerShape(2.dp); clip = true }
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(color.darken(0.35f), color.lighten(0.12f), color, color.darken(0.3f))))
                val gold = Color(0xFFE2C27E)
                val bandYs = when (bands) {
                    0 -> listOf(0.08f, 0.11f, 0.89f, 0.92f)
                    1 -> listOf(0.06f, 0.94f)
                    else -> listOf(0.1f, 0.14f, 0.18f, 0.86f)
                }
                bandYs.forEach { y -> drawLine(gold.copy(alpha = 0.85f), Offset(0f, size.height * y), Offset(size.width, size.height * y), 1.2.dp.toPx()) }
                if (book.status == "READING") drawRect(Color(0xFF9B2F3F), Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, size.height * 0.05f))
            }
            .combinedClickable(onClick = { onClick(book, bounds) }, onLongClick = { onLongClick(book) }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title,
            style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFF6E7C4)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .requiredWidth(h - 30.dp)
                .graphicsLayer { rotationZ = 90f },
        )
    }
}
