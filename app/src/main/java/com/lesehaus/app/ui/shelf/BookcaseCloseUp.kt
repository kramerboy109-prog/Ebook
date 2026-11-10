package com.lesehaus.app.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.data.scan.ScanState
import com.lesehaus.app.ui.book.BookCover
import com.lesehaus.app.ui.room.darken
import com.lesehaus.app.ui.room.lighten
import com.lesehaus.app.ui.theme.Fonts
import kotlin.random.Random

private val RowHeight = 172.dp

/**
 * Nahansicht eines Regals: Das Regal füllt den Bildschirm, die Bücher sind groß,
 * mit lesbaren Titeln und lassen sich antippen.
 */
@Composable
fun BookcaseCloseUp(
    shelf: Shelf,
    books: List<Book>,
    scan: ScanState?,
    onClose: () -> Unit,
    onBookClick: (Book, Rect) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onArrangement: (BookArrangement) -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val wood = enumOr(shelf.wood, Wood.WALNUT)
    val arrangement = enumOr(shelf.arrangement, BookArrangement.ALPHA)
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shown = remember(books, query) { if (query.isBlank()) books else books.filter { it.title.contains(query, true) || it.author.contains(query, true) } }
    val pieces = remember(shown, arrangement, shelf.decor) { arrangeBooks(shown, arrangement, shelf.decor, shelf.id) }
    val onWood = if (wood == Wood.WHITE || wood == Wood.PINE) Color(0xFF3B2614) else Color(0xFFF2E3C6)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(wood.dark).darken(0.5f), Color(wood.dark).darken(0.3f))))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = RowHeight / 10f * 0.92f
            val inner = maxWidth - 40.dp
            val capacity = inner / unit
            val rows = remember(pieces, capacity) {
                fillCompartments(pieces, List(pieces.size.coerceAtLeast(1)) { capacity }).filter { it.isNotEmpty() }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 132.dp, bottom = 60.dp),
            ) {
                itemsIndexed(rows) { i, row ->
                    CloseUpRow(row, wood, unit, i, onBookClick, onBookLongClick)
                }
                if (rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (shelf.folderList.isEmpty()) "Dieses Regal ist noch leer." else "Keine passenden Bücher gefunden.",
                            style = TextStyle(fontFamily = Fonts.Lora, fontSize = 17.sp, color = onWood),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tippe oben auf ⚙, um Ordner mit Büchern auszuwählen.",
                            style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = onWood.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
        // Kopf mit Messingschild
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(wood.base), Color(wood.base).darken(0.15f))))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            val colors = IconButtonDefaults.iconButtonColors(contentColor = onWood)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, colors = colors) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück ins Zimmer") }
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .shadow(3.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFE8CC8C), Color(0xFFB08D57), Color(0xFFD9B97F))))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(shelf.name, style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3B2614)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(0.01f))
                Text(if (books.size == 1) "1 Buch" else "${books.size} Bücher", style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = onWood.copy(alpha = 0.85f)), modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }, colors = colors) { Icon(Icons.Outlined.Search, "Suchen") }
                Box {
                    IconButton(onClick = { menu = true }, colors = colors) { Icon(Icons.Outlined.AutoAwesome, "Anordnung") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        BookArrangement.entries.forEach { a ->
                            DropdownMenuItem(
                                text = { Column { Text((if (a == arrangement) "✓ " else "") + a.label); Text(a.description, fontSize = 12.sp) } },
                                onClick = { menu = false; onArrangement(a) },
                            )
                        }
                    }
                }
                IconButton(onClick = onScan, colors = colors) { Icon(Icons.Outlined.Refresh, "Aktualisieren") }
                IconButton(onClick = onSettings, colors = colors) { Icon(Icons.Outlined.Tune, "Regal einrichten") }
            }
            if (searchOpen) OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Titel oder Autor:in …") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = onWood, unfocusedTextColor = onWood),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            if (scan != null) {
                Text(if (scan.found > 0) "Sortiere ein: ${scan.processed}/${scan.found}" else scan.current, style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = onWood), modifier = Modifier.padding(start = 12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(3.dp), color = Color(0xFFE8CC8C))
            }
        }
    }
}

@Composable
private fun CloseUpRow(
    row: List<Piece>,
    wood: Wood,
    unit: Dp,
    index: Int,
    onBookClick: (Book, Rect) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val base = Color(wood.base)
    val dark = Color(wood.dark)
    val light = Color(wood.light)
    val innerH = RowHeight
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowHeight + 22.dp)
            .drawBehind {
                val side = 14.dp.toPx()
                val board = 18.dp.toPx()
                val w = size.width
                val h = size.height
                drawRect(Brush.verticalGradient(listOf(dark.darken(0.45f), dark.darken(0.25f))), Offset(side, 0f), Size(w - side * 2, h - board))
                val rnd = Random(index * 13 + wood.ordinal)
                repeat(9) {
                    val y = rnd.nextFloat() * (h - board)
                    drawLine(dark.darken(0.5f).copy(alpha = 0.5f), Offset(side, y), Offset(w - side, y + rnd.nextFloat() * 8f - 4f), 1.2f)
                }
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, 26.dp.toPx()), Offset(side, 0f), Size(w - side * 2, 26.dp.toPx()))
                drawRect(Brush.horizontalGradient(listOf(light, base, dark), 0f, side), Offset.Zero, Size(side, h))
                drawRect(Brush.horizontalGradient(listOf(dark, base, light.darken(0.1f)), w - side, w), Offset(w - side, 0f), Size(side, h))
                val by = h - board
                drawRect(Brush.verticalGradient(listOf(light, base, dark), by, h), Offset(0f, by), Size(w, board))
                drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, by), Size(w, 2.dp.toPx()))
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), by - 5.dp.toPx(), by), Offset(side, by - 5.dp.toPx()), Size(w - side * 2, 5.dp.toPx()))
            }
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
            row.forEach { p -> PieceView(p, unit, innerH, onBookClick, onBookLongClick) }
        }
    }
}

@Composable
private fun PieceView(p: Piece, unit: Dp, rowH: Dp, onBookClick: (Book, Rect) -> Unit, onBookLongClick: (Book) -> Unit) {
    when (p) {
        is Piece.Standing -> SpineView(p.book, unit * p.units, rowH * p.height * 0.92f, onBookClick, onBookLongClick)
        is Piece.Leaning -> Box(Modifier.width(unit * p.units).height(rowH * 0.92f), contentAlignment = Alignment.BottomStart) {
            Box(Modifier.graphicsLayer { transformOrigin = TransformOrigin(1f, 1f); rotationZ = 16f }) {
                SpineView(p.book, unit * spineUnits(p.book), rowH * p.height * 0.92f, onBookClick, onBookLongClick)
            }
        }
        is Piece.Stack -> Column(Modifier.width(unit * p.units), horizontalAlignment = Alignment.CenterHorizontally) {
            p.books.asReversed().forEachIndexed { i, b ->
                var bounds by remember { mutableStateOf(Rect.Zero) }
                val wFactor = 0.82f + (kotlin.math.abs(b.title.hashCode()) % 15) / 100f
                Canvas(
                    Modifier
                        .padding(start = if (i % 2 == 0) 3.dp else 0.dp)
                        .width(unit * p.units * wFactor)
                        .height(unit * spineUnits(b) * 1.5f)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .combinedClickable(onClick = { onBookClick(b, bounds) }, onLongClick = { onBookLongClick(b) })
                ) { drawHorizontalSpine(b, Rect(Offset.Zero, size)) }
            }
        }
        is Piece.Cover -> {
            var bounds by remember { mutableStateOf(Rect.Zero) }
            Box(Modifier.width(unit * p.units).height(rowH * 0.9f), contentAlignment = Alignment.BottomCenter) {
                BookCover(
                    p.book,
                    Modifier
                        .height(rowH * 0.88f)
                        .width(rowH * 0.88f * 0.66f)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .graphicsLayer { shadowElevation = 10f; shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp); clip = true }
                        .combinedClickable(onClick = { onBookClick(p.book, bounds) }, onLongClick = { onBookLongClick(p.book) }),
                    titleSize = 11.dp,
                )
            }
        }
        is Piece.Decor -> Canvas(Modifier.width(unit * p.units).height(rowH * 0.6f)) { drawDecor(p.kind, Rect(Offset.Zero, size)) }
        Piece.Bookend -> Canvas(Modifier.width(unit * p.units).height(rowH * 0.45f)) {
            val path = Path().apply { moveTo(0f, size.height); lineTo(0f, 0f); lineTo(size.width * 0.4f, 0f); lineTo(size.width, size.height); close() }
            drawPath(path, Color(0xFFB08D57))
            drawPath(path, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)))
        }
    }
}

@Composable
private fun SpineView(book: Book, w: Dp, h: Dp, onBookClick: (Book, Rect) -> Unit, onBookLongClick: (Book) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Canvas(
        Modifier
            .width(w)
            .height(h)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { shadowElevation = 6f; shape = RoundedCornerShape(2.dp); clip = true }
            .combinedClickable(onClick = { onBookClick(book, bounds) }, onLongClick = { onBookLongClick(book) })
    ) { drawSpine(book, Rect(Offset.Zero, size)) }
}
