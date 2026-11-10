package com.lesehaus.app.ui.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.data.db.Favorite
import com.lesehaus.app.ui.reader.AmbientOverlay
import com.lesehaus.app.ui.reader.effectiveTheme
import com.lesehaus.app.ui.reader.formatDate
import com.lesehaus.app.ui.reader.rememberPaperBrush
import com.lesehaus.app.ui.reader.shareQuote
import com.lesehaus.app.ui.theme.Fonts
import com.lesehaus.app.ui.theme.HandwritingStyle
import com.lesehaus.app.ui.theme.QuoteStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun kindLabel(k: String) = when (k) {
    "LINE" -> "Satz"
    "PASSAGE" -> "Passage"
    "PAGE" -> "Seite"
    else -> "Auswahl"
}

@Composable
fun FavoritesScreen(
    bottomBar: @Composable () -> Unit,
    onReread: (startId: Long, collection: String?, shuffle: Boolean) -> Unit,
    onOpenInBook: (Favorite) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val favorites by app.db.annotations().observeFavorites().collectAsState(initial = emptyList())
    var collection by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<Favorite?>(null) }
    val collections = remember(favorites) { favorites.map { it.collection }.distinct() }
    val shown = favorites.filter {
        (collection == null || it.collection == collection) &&
            (query.isBlank() || it.text.contains(query, true) || it.bookTitle.contains(query, true) || it.bookAuthor.contains(query, true) || (it.note ?: "").contains(query, true))
    }
    Scaffold(bottomBar = bottomBar) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text("Lieblingsstellen", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (favorites.isEmpty()) "Sätze, Passagen und Seiten, die dir gefallen – zum Wiederlesen."
                        else "${favorites.size} aufbewahrte Stellen aus ${favorites.map { it.bookTitle }.distinct().size} Büchern",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (shown.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onReread(shown.first().id, collection, false) }) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Wiederlesen")
                        }
                        FilledTonalButton(onClick = { onReread(shown.random().id, collection, true) }) {
                            Icon(Icons.Outlined.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Zufällig")
                        }
                    }
                }
            }
            if (favorites.isNotEmpty()) item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Durchsuchen …") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = collection == null, onClick = { collection = null }, label = { Text("Alle") }) }
                    items(collections) { c -> FilterChip(selected = collection == c, onClick = { collection = c }, label = { Text(c) }) }
                }
            }
            if (favorites.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FormatQuote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Drücke beim Lesen lange auf ein Wort, wähle eine Stelle aus und tippe auf das Herz. Du kannst ganze Zeilen, Passagen oder Seiten aufbewahren.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.id }) { f ->
                FavoriteCard(
                    f,
                    onClick = { onReread(f.id, collection, false) },
                    onOpen = { onOpenInBook(f) },
                    onShare = { shareQuote(context, f.text, f.bookTitle, f.bookAuthor) },
                    onMove = { moveTarget = f },
                    onDelete = { scope.launch { app.db.annotations().deleteFavorite(f) } },
                )
            }
        }
    }
    moveTarget?.let { f ->
        var name by remember { mutableStateOf(f.collection) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Sammlung") },
            text = {
                Column {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        collections.forEach { c -> FilterChip(selected = name == c, onClick = { name = c }, label = { Text(c) }) }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.db.annotations().updateFavorite(f.copy(collection = name.ifBlank { "Lieblingsstellen" })) }; moveTarget = null }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun FavoriteCard(f: Favorite, onClick: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("“", style = TextStyle(fontFamily = Fonts.Playfair, fontSize = 48.sp, lineHeight = 40.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                Spacer(Modifier.width(6.dp))
                Text(
                    f.text,
                    style = QuoteStyle.copy(fontSize = 18.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 8, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Im Buch öffnen") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) }, onClick = { menu = false; onOpen() }, enabled = f.bookId != null)
                        DropdownMenuItem(text = { Text("Teilen") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Sammlung ändern") }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            f.note?.let { Text(it, style = HandwritingStyle.copy(fontSize = 19.sp, color = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(top = 4.dp, end = 12.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(
                listOf(f.bookAuthor, f.bookTitle).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${kindLabel(f.kind)} · ${f.collection} · ${formatDate(f.createdAt)}" + if (f.timesRead > 0) " · ${f.timesRead}× wiedergelesen" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Vollbild-Modus zum wiederholten Lesen der Lieblingsstellen. */
@Composable
fun RereadScreen(startId: Long, collection: String?, shuffle: Boolean, onBack: () -> Unit, onOpenInBook: (Favorite) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val all by app.db.annotations().observeFavorites().collectAsState(initial = null)
    val s by app.settings.reader.collectAsState()
    val theme = effectiveTheme(s)
    val bg = Color(theme.bg)
    val fg = Color(theme.fg)
    val accent = Color(theme.accent)
    val paper = rememberPaperBrush(theme.dark)
    val list = remember(all == null) {
        val base = all.orEmpty().filter { collection == null || it.collection == collection }
        if (shuffle) base.shuffled() else base
    }
    if (all == null) return
    if (list.isEmpty()) { onBack(); return }
    val start = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(start) { list.size }
    var auto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i -> list.getOrNull(i)?.let { app.db.annotations().markFavoriteRead(it.id, System.currentTimeMillis()) } }
    }
    LaunchedEffect(auto, pager.settledPage) {
        if (auto) {
            val f = list[pager.settledPage]
            delay((6000L + f.text.length * 45L).coerceAtMost(40_000L))
            pager.animateScrollToPage((pager.settledPage + 1) % list.size, animationSpec = tween(900))
        }
    }
    Box(Modifier.fillMaxSize().background(bg).background(paper)) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { i ->
            val f = list[i]
            val off = (pager.currentPage - i) + pager.currentPageOffsetFraction
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - abs(off).coerceIn(0f, 1f) * 0.7f; translationX = off * size.width * 0.4f }
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 34.dp, vertical = 90.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("❦", style = TextStyle(fontSize = 26.sp, color = accent), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
                val size = when { f.text.length < 160 -> 27.sp; f.text.length < 500 -> 22.sp; else -> 19.sp }
                Text(
                    f.text,
                    style = TextStyle(fontFamily = Fonts.Garamond, fontStyle = if (f.text.length < 500) FontStyle.Italic else FontStyle.Normal, fontSize = size, lineHeight = size * 1.45f, color = fg, textAlign = if (f.text.length < 300) TextAlign.Center else TextAlign.Justify),
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    "— " + listOf(f.bookAuthor, f.bookTitle).filter { it.isNotBlank() }.joinToString(", "),
                    style = TextStyle(fontFamily = Fonts.Lora, fontSize = 15.sp, color = fg.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
                f.note?.let {
                    Spacer(Modifier.height(20.dp))
                    Text(it, style = HandwritingStyle.copy(color = accent, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        AmbientOverlay(s.copy(cozyMode = s.cozyMode || theme.dark), theme)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconColors = IconButtonDefaults.iconButtonColors(contentColor = fg.copy(alpha = 0.8f))
            IconButton(onClick = onBack, colors = iconColors) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { auto = !auto }, colors = iconColors) { Icon(if (auto) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (auto) "Automatik aus" else "Automatisch weiterblättern") }
            IconButton(onClick = { list.getOrNull(pager.currentPage)?.let { shareQuote(context, it.text, it.bookTitle, it.bookAuthor) } }, colors = iconColors) { Icon(Icons.Outlined.Share, "Teilen") }
            IconButton(onClick = { list.getOrNull(pager.currentPage)?.let(onOpenInBook) }, colors = iconColors) { Icon(Icons.AutoMirrored.Outlined.MenuBook, "Im Buch öffnen") }
        }
        val progress by animateFloatAsState((pager.currentPage + 1f) / list.size, label = "p")
        Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${pager.currentPage + 1} / ${list.size}", style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = fg.copy(alpha = 0.5f)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(120.dp).height(2.dp).background(fg.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(accent))
            }
        }
    }
}
