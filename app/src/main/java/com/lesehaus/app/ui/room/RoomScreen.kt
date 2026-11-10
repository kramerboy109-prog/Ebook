package com.lesehaus.app.ui.room

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.data.AmbientSound
import com.lesehaus.app.data.DefaultFileTypes
import com.lesehaus.app.data.ShelfDisplay
import com.lesehaus.app.data.ShelfSort
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.book.BookDetailSheet
import com.lesehaus.app.ui.book.BookOpeningOverlay
import com.lesehaus.app.ui.book.OpeningBook
import com.lesehaus.app.ui.reader.AmbientPlayer
import com.lesehaus.app.ui.reader.effectiveTheme
import com.lesehaus.app.ui.shelf.EmptyShelfRow
import com.lesehaus.app.ui.shelf.ShelfHeader
import com.lesehaus.app.ui.shelf.ShelfRow
import com.lesehaus.app.ui.shelf.chunkBooks
import com.lesehaus.app.ui.theme.Fonts
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

fun sortBooks(books: List<Book>, sort: ShelfSort): List<Book> = when (sort) {
    ShelfSort.TITLE -> books.sortedBy { it.title.lowercase() }
    ShelfSort.AUTHOR -> books.sortedWith(compareBy({ it.author.split(' ').lastOrNull()?.lowercase() ?: "" }, { it.title.lowercase() }))
    ShelfSort.RECENT -> books.sortedByDescending { it.lastOpenedAt }
    ShelfSort.ADDED -> books.sortedByDescending { it.addedAt }
    ShelfSort.PROGRESS -> books.sortedByDescending { it.progress }
}

@Composable
fun RoomScreen(
    roomId: Long,
    onBack: () -> Unit,
    onEditRoom: () -> Unit,
    onEditShelf: (Long) -> Unit,
    onOpenBook: (Book) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val room by app.db.rooms().observeRoom(roomId).collectAsState(initial = null)
    val shelves by app.db.shelves().observeShelves(roomId).collectAsState(initial = emptyList())
    val booksByShelf by remember(roomId) {
        app.db.books().observeBooksOfRoom(roomId).combine(app.db.books().observeLinksOfRoom(roomId)) { books, links ->
            val byId = books.associateBy { it.id }
            links.groupBy { it.shelfId }.mapValues { (_, l) -> l.mapNotNull { byId[it.bookId] } }
        }
    }.collectAsState(initial = emptyMap())
    val scans by app.scanner.state.collectAsState()
    val appSettings by app.settings.app.collectAsState()
    val readerSettings by app.settings.reader.collectAsState()
    val scope = rememberCoroutineScope()

    var opening by remember { mutableStateOf<OpeningBook?>(null) }
    var detail by remember { mutableStateOf<Book?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(appSettings.roomSounds) }

    // Regale beim Betreten automatisch aktualisieren
    LaunchedEffect(shelves.map { it.id }) {
        if (appSettings.autoScan) shelves.filter { it.folderList.isNotEmpty() && System.currentTimeMillis() - it.lastScan > 10 * 60_000 }.forEach { app.scanShelf(it) }
    }
    // Raumklang
    val player = remember { AmbientPlayer() }
    LaunchedEffect(soundOn, room?.ambientSound) {
        val r = room
        if (soundOn && r != null) player.play(enumOr(r.ambientSound, AmbientSound.NONE), 0.35f) else player.stop()
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val r = room ?: return
    Box(Modifier.fillMaxSize().background(Color(0xFF1A120D))) {
        val listState = rememberLazyListState()
        val parallax by remember { derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sceneHeight = maxHeight * 0.62f
            RoomScene(
                r,
                Modifier
                    .fillMaxWidth()
                    .height(sceneHeight)
                    .graphicsLayer { translationY = -parallax * 0.35f },
                animate = appSettings.roomAnimations,
            )
            // Übergang Szene → Regale
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maxHeight)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 0.62f to Color(0xFF1A120D))),
            )
            val width = maxWidth
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = sceneHeight * 0.58f, bottom = 120.dp),
            ) {
                if (searchOpen) item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("In diesem Zimmer suchen …") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xEE2A1E17), unfocusedContainerColor = Color(0xDD2A1E17),
                            focusedTextColor = Color(0xFFF2E3C6), unfocusedTextColor = Color(0xFFF2E3C6),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                shelves.forEach { shelf ->
                    val display = enumOr(shelf.displayMode, ShelfDisplay.COVERS)
                    val books = sortBooks(booksByShelf[shelf.id].orEmpty(), enumOr(shelf.sortOrder, ShelfSort.TITLE))
                        .filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) }
                    item(key = "h${shelf.id}") {
                        ShelfHeader(
                            shelf = shelf,
                            count = books.size,
                            scan = scans[shelf.id],
                            onScan = { app.scanShelf(shelf) },
                            onEdit = { onEditShelf(shelf.id) },
                            onToggleDisplay = {
                                scope.launch {
                                    app.db.shelves().update(shelf.copy(displayMode = if (display == ShelfDisplay.COVERS) ShelfDisplay.SPINES.name else ShelfDisplay.COVERS.name))
                                }
                            },
                        )
                    }
                    if (books.isEmpty()) {
                        item(key = "e${shelf.id}") { EmptyShelfRow(shelf) { onEditShelf(shelf.id) } }
                    } else {
                        val rows = chunkBooks(books, display, width)
                        rows.forEachIndexed { i, row ->
                            item(key = "r${shelf.id}_$i") {
                                ShelfRow(
                                    shelf = shelf,
                                    books = row,
                                    isLast = i == rows.lastIndex,
                                    onBookClick = { b, rect ->
                                        if (appSettings.bookAnimations && rect.width > 0) opening = OpeningBook(b, rect) else onOpenBook(b)
                                    },
                                    onBookLongClick = { detail = it },
                                )
                            }
                        }
                    }
                }
                item(key = "add") {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val id = app.db.shelves().insert(
                                    Shelf(roomId = roomId, name = "Neues Regal", wood = Wood.entries[shelves.size % Wood.entries.size].name, fileTypes = DefaultFileTypes, sortIndex = shelves.size)
                                )
                                onEditShelf(id)
                            }
                        }) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Bücherregal aufstellen")
                        }
                    }
                }
            }
        }
        // Kopfzeile
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFF6E7C4))
            IconButton(onClick = onBack, colors = iconColors) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Column(Modifier.weight(1f)) {
                Text(
                    r.name,
                    style = TextStyle(
                        fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Color(0xFFF6E7C4),
                        shadow = Shadow(Color.Black, blurRadius = 8f),
                    ),
                )
                val total = booksByShelf.values.sumOf { it.size }
                Text(
                    "${shelves.size} ${if (shelves.size == 1) "Regal" else "Regale"} · $total ${if (total == 1) "Buch" else "Bücher"}",
                    style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = Color(0xFFE8D6B4), shadow = Shadow(Color.Black, blurRadius = 6f)),
                )
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }, colors = iconColors) { Icon(Icons.Outlined.Search, "Suchen") }
            IconButton(onClick = { soundOn = !soundOn }, colors = iconColors) { Icon(if (soundOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, "Raumklang") }
            IconButton(onClick = onEditRoom, colors = iconColors) { Icon(Icons.Outlined.Brush, "Zimmer gestalten") }
        }

        opening?.let { o ->
            BookOpeningOverlay(o, Color(effectiveTheme(readerSettings).bg)) {
                onOpenBook(o.book)
            }
        }
    }
    LaunchedEffect(opening) {
        // Overlay nach der Rückkehr aus dem Reader entfernen
        if (opening != null) {
            kotlinx.coroutines.delay(2500)
            opening = null
        }
    }
    detail?.let { b ->
        BookDetailSheet(
            bookId = b.id,
            onDismiss = { detail = null },
            onRead = { book -> detail = null; onOpenBook(book) },
        )
    }
}
