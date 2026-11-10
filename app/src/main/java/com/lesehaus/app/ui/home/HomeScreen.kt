package com.lesehaus.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.data.RoomPresets
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.ui.book.BookCover
import com.lesehaus.app.ui.book.BookDetailSheet
import com.lesehaus.app.ui.book.BookOpeningOverlay
import com.lesehaus.app.ui.book.OpeningBook
import com.lesehaus.app.ui.reader.effectiveTheme
import com.lesehaus.app.ui.room.RoomScene
import com.lesehaus.app.ui.theme.Fonts
import com.lesehaus.app.ui.theme.QuoteStyle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Schönen Tag"
    in 18..22 -> "Guten Abend"
    else -> "Gute Nacht"
}

@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    onOpenRoom: (Long) -> Unit,
    onEditRoom: (Long) -> Unit,
    onOpenBook: (Book) -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onFavorite: (Long) -> Unit,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val rooms by app.db.rooms().observeRooms().collectAsState(initial = null)
    val recent by app.db.books().observeRecent(8).collectAsState(initial = emptyList())
    val favorites by app.db.annotations().observeFavorites().collectAsState(initial = emptyList())
    val stats by remember {
        app.db.shelves().observeAllShelves().combine(app.db.shelves().observeCounts()) { shelves, counts ->
            val countMap = counts.associate { it.shelfId to it.count }
            shelves.groupBy { it.roomId }.mapValues { (_, l) -> l.size to l.sumOf { countMap[it.id] ?: 0 } }
        }
    }.collectAsState(initial = emptyMap())
    val days by app.db.stats().observeDays().collectAsState(initial = emptyList())
    val allItems by app.db.rooms().observeAllItems().collectAsState(initial = emptyList())
    val itemsByRoom = remember(allItems) { allItems.groupBy { it.roomId } }
    val appSettings by app.settings.app.collectAsState()
    val reader by app.settings.reader.collectAsState()
    var addRoom by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<OpeningBook?>(null) }
    var detail by remember { mutableStateOf<Book?>(null) }

    var nearest by remember { mutableStateOf<ReadingRoom?>(null) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(bottomBar = bottomBar) { pad ->
            Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).background(Color(0xFF120C09))) {
                Hallway3D(
                    rooms = rooms.orEmpty(),
                    settings = appSettings,
                    animate = appSettings.roomAnimations,
                    modifier = Modifier.fillMaxSize(),
                    onNearest = { nearest = it },
                    onEnter = { onOpenRoom(it.id) },
                    onEdit = { onEditRoom(it.id) },
                    onAddRoom = { addRoom = true },
                )
                // Titel
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    Column(Modifier.padding(end = 96.dp)) {
                        Text(
                            "Benny's Library",
                            style = TextStyle(fontFamily = Fonts.Playfair, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Color(0xFFF6E7C4), shadow = Shadow(Color.Black, blurRadius = 10f)),
                        )
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val todayMin = (days.firstOrNull { it.day == today }?.total ?: 0L) / 60_000
                        Text(
                            greeting() + " · " + if (todayMin > 0) "heute $todayMin von ${appSettings.dailyGoalMinutes} Min. gelesen" else "Wohin möchtest du heute?",
                            style = TextStyle(fontFamily = Fonts.Lora, fontSize = 14.sp, color = Color(0xFFEBD9B4), shadow = Shadow(Color.Black, blurRadius = 6f)),
                        )
                    }
                    Row(Modifier.align(Alignment.TopEnd)) {
                        val ic = androidx.compose.material3.IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFF6E7C4))
                        IconButton(onClick = onLibrary, colors = ic) { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, "Alle Bücher") }
                        IconButton(onClick = onSettings, colors = ic) { Icon(Icons.Outlined.Settings, "Einstellungen") }
                    }
                }
                // Unten: Tür vor dir, Weiterlesen, Zitat des Tages
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val cur = nearest
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f), onClick = { cur?.let { onOpenRoom(it.id) } ?: run { addRoom = true } }) {
                        Text(
                            if (cur != null) {
                                val (sh, bk) = stats[cur.id] ?: (0 to 0)
                                "${cur.name} · $sh ${if (sh == 1) "Regal" else "Regale"} · $bk ${if (bk == 1) "Buch" else "Bücher"}  →"
                            } else "Neues Zimmer einrichten  +",
                            style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = Color(0xFFF2E3C6)),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Wische hoch, um den Flur entlangzugehen · Tür antippen zum Eintreten", style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 11.sp, color = Color(0xFFE8D6B4).copy(alpha = 0.8f), shadow = Shadow(Color.Black, blurRadius = 6f)))
                    Spacer(Modifier.height(8.dp))
                    recent.firstOrNull()?.let { b ->
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (appSettings.bookAnimations && bounds.width > 0) opening = OpeningBook(b, bounds) else onOpenBook(b) },
                                onLongClick = { detail = b },
                            ),
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                BookCover(b, Modifier.width(44.dp).aspectRatio(0.67f).onGloballyPositioned { bounds = it.boundsInRoot() }.clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)), titleSize = 7.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Weiterlesen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        Box(Modifier.fillMaxWidth(b.progress.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                                    }
                                }
                                if (favorites.isNotEmpty()) {
                                    val dayIndex = (System.currentTimeMillis() / 86_400_000L).toInt()
                                    val fav = favorites[dayIndex % favorites.size]
                                    IconButton(onClick = { onFavorite(fav.id) }) { Icon(Icons.Outlined.FormatQuote, "Lieblingsstelle des Tages", tint = MaterialTheme.colorScheme.primary) }
                                }
                            }
                        }
                    }
                }
            }
        }
        opening?.let { o -> BookOpeningOverlay(o, Color(effectiveTheme(reader).bg)) { onOpenBook(o.book) } }
    }
    androidx.compose.runtime.LaunchedEffect(opening) { if (opening != null) { kotlinx.coroutines.delay(2500); opening = null } }
    if (addRoom) AddRoomDialog(onDismiss = { addRoom = false }) { type, name ->
        addRoom = false
        scope.launch { val id = app.createRoom(type, name); onOpenRoom(id) }
    }
    detail?.let { b -> BookDetailSheet(b.id, onDismiss = { detail = null }, onRead = { detail = null; onOpenBook(it) }) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun ContinueCard(book: Book, onClick: (Book, Rect) -> Unit, onLong: (Book) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).combinedClickable(onClick = { onClick(book, bounds) }, onLongClick = { onLong(book) }),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                book,
                Modifier
                    .width(86.dp)
                    .aspectRatio(0.67f)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .shadow(8.dp, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
                titleSize = 11.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(book.progress.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(4.dp))
                Text("${(book.progress * 100).roundToInt()} % gelesen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Weiterlesen →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RoomCard(room: ReadingRoom, stats: Pair<Int, Int>?, animate: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val type = runCatching { RoomType.valueOf(room.type) }.getOrNull()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        RoomScene(room, Modifier.fillMaxWidth().aspectRatio(1.75f), animate = animate)
        // Türrahmen-Optik
        Box(
            Modifier
                .matchParentSize()
                .border(6.dp, Brush.verticalGradient(listOf(Color(0xFF8C5F43), Color(0xFF4A2E1F))), RoundedCornerShape(20.dp))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    room.name,
                    style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color(0xFFF6E7C4), shadow = Shadow(Color.Black, blurRadius = 6f)),
                )
                val (shelves, books) = stats ?: (0 to 0)
                Text(
                    listOfNotNull(type?.let { "${it.emoji} ${it.label}" }?.takeIf { type.label != room.name }, "$shelves ${if (shelves == 1) "Regal" else "Regale"}", "$books ${if (books == 1) "Buch" else "Bücher"}").joinToString(" · "),
                    style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 13.sp, color = Color(0xFFE8D6B4)),
                )
            }
        }
    }
}

@Composable
fun AddRoomDialog(onDismiss: () -> Unit, onCreate: (RoomType, String) -> Unit) {
    var type by remember { mutableStateOf(RoomType.FIREPLACE) }
    var name by remember { mutableStateOf(RoomType.FIREPLACE.label) }
    var nameEdited by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Zimmer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RoomScene(RoomPresets.preset(type).copy(id = type.ordinal.toLong()), Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(12.dp)), animate = true)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoomType.entries.forEach { t ->
                        FilterChip(selected = t == type, onClick = { type = t; if (!nameEdited) name = t.label }, label = { Text("${t.emoji} ${t.label}") })
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it; nameEdited = true }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(type, name.ifBlank { type.label }) }) { Text("Einrichten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ------------------------------------------------------------------------------------------------
// Willkommen
// ------------------------------------------------------------------------------------------------
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf(RoomType.FIREPLACE, RoomType.LIVING) }
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF1A120D))) {
        RoomScene(RoomPresets.preset(RoomType.FIREPLACE).copy(id = 99), Modifier.fillMaxWidth().aspectRatio(0.95f), animate = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Transparent, 0.6f to Color(0xFF1A120D))))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 300.dp, bottom = 40.dp),
        ) {
            Text("Willkommen in Benny's Library", style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = Color(0xFFF6E7C4), shadow = Shadow(Color.Black, blurRadius = 10f)))
            Spacer(Modifier.height(10.dp))
            Text(
                "Richte dir gemütliche Zimmer ein, stelle Bücherregale auf und lass sie deine Bücher aus beliebigen Ordnern einsortieren. Welche Zimmer möchtest du für den Anfang?",
                style = TextStyle(fontFamily = Fonts.Lora, fontSize = 16.sp, lineHeight = 23.sp, color = Color(0xFFE8D6B4)),
            )
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.height(330.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false) {
                items(RoomType.entries) { t ->
                    val on = t in selected
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color(0xFFB08D57).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
                            .border(if (on) 2.dp else 1.dp, if (on) Color(0xFFE8CC8C) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                            .clickable { if (on) selected.remove(t) else selected.add(t) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(t.emoji, fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(t.label, style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = Color(0xFFF2E3C6), textAlign = TextAlign.Center))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        (selected.ifEmpty { listOf(RoomType.LIVING) }).forEach { app.createRoom(it) }
                        app.settings.updateApp { it.copy(onboarded = true) }
                        onDone()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Haus einrichten", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tipp: Im Zimmer tippst du auf ein Regal, um Ordner mit deinen E-Books, PDFs und Texten auszuwählen.",
                style = TextStyle(fontFamily = Fonts.Lora, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = Color(0xFFCDB89A)),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Alle Bücher
// ------------------------------------------------------------------------------------------------
@Composable
fun LibraryScreen(onBack: () -> Unit, onOpenBook: (Book) -> Unit) {
    val app = LocalContext.current.app
    val books by app.db.books().observeAll().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var detail by remember { mutableStateOf<Book?>(null) }
    val shown = books.filter {
        (query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true)) &&
            (filter == "ALL" || it.status == filter)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alle Bücher") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Titel oder Autor:in …") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "Alle", "READING" to "Lese ich", "UNREAD" to "Ungelesen", "FINISHED" to "Gelesen").forEach { (k, l) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                }
            }
            if (shown.isEmpty()) {
                Text(
                    if (books.isEmpty()) "Noch keine Bücher. Stelle in einem Zimmer ein Regal auf und wähle einen Ordner." else "Keine Treffer.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(32.dp),
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(shown, key = { it.id }) { b ->
                    Column(Modifier.combinedClickable(onClick = { onOpenBook(b) }, onLongClick = { detail = b })) {
                        BookCover(b, Modifier.fillMaxWidth().aspectRatio(0.67f).shadow(6.dp, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)), titleSize = 11.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    detail?.let { b -> BookDetailSheet(b.id, onDismiss = { detail = null }, onRead = { detail = null; onOpenBook(it) }) }
}
