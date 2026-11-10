package com.lesehaus.app.ui.book

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lesehaus.app.app
import com.lesehaus.app.data.covers.OnlineCover
import com.lesehaus.app.data.db.Book
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BookDetailSheet(bookId: Long, onDismiss: () -> Unit, onRead: (Book) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val book by app.db.books().observeBook(bookId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var onlinePicker by remember { mutableStateOf(false) }
    var title by remember(book?.id) { mutableStateOf(book?.title ?: "") }
    var author by remember(book?.id) { mutableStateOf(book?.author ?: "") }
    val b = book

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val bk = book
        if (uri != null && bk != null) scope.launch {
            val stored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.covers.storeFromUri(bk.id, uri) }
            if (stored != null) app.db.books().update(bk.copy(coverPath = stored.first, coverColor = stored.second, coverSource = "CUSTOM"))
        }
    }

    ModalBottomSheet(onDismissRequest = {
        val bk = book
        if (bk != null && (title != bk.title || author != bk.author) && title.isNotBlank()) scope.launch { app.db.books().update(bk.copy(title = title.trim(), author = author.trim())) }
        onDismiss()
    }, sheetState = state) {
        if (b == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row {
                BookCover(
                    b,
                    Modifier
                        .width(120.dp)
                        .aspectRatio(0.66f)
                        .shadow(10.dp, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    titleSize = 14.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.format.uppercase() + " · " + humanSize(b.fileSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(b.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (b.status) { "FINISHED" -> "Gelesen"; "READING" -> "${(b.progress * 100).roundToInt()} % gelesen"; else -> "Noch nicht begonnen" } +
                            if (b.readingTimeMs > 60_000) " · ${b.readingTimeMs / 60_000} Min." else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        (1..5).forEach { i ->
                            IconButton(onClick = { scope.launch { app.db.books().update(b.copy(rating = if (b.rating == i) 0 else i)) } }, modifier = Modifier.size(34.dp)) {
                                Icon(if (i <= b.rating) Icons.Filled.Star else Icons.Outlined.StarOutline, "$i Sterne", tint = Color(0xFFE0A93B))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onRead(b) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text(if (b.progress > 0f) "Weiterlesen" else "Lesen beginnen")
            }
            Spacer(Modifier.height(14.dp))
            Text("Status", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("UNREAD" to "Ungelesen", "READING" to "Lese ich gerade", "FINISHED" to "Gelesen").forEach { (k, l) ->
                    FilterChip(selected = b.status == k, onClick = { scope.launch { app.db.books().update(b.copy(status = k)) } }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Cover", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onlinePicker = true }, label = { Text("Online suchen") }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) })
                AssistChip(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, label = { Text("Eigenes Bild") }, leadingIcon = { Icon(Icons.Outlined.Image, null) })
                AssistChip(onClick = {
                    scope.launch {
                        val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.lesehaus.app.data.formats.BookParsers.readMeta(context, android.net.Uri.parse(b.uri), b.fileName)
                        }
                        val stored = meta.coverBytes?.let { app.covers.store(b.id, it) }
                        if (stored != null) app.db.books().update(b.copy(coverPath = stored.first, coverColor = stored.second, coverSource = "FILE"))
                        else Toast.makeText(context, "Die Datei enthält kein Cover", Toast.LENGTH_SHORT).show()
                    }
                }, label = { Text("Aus der Datei") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) })
                AssistChip(onClick = {
                    scope.launch { app.covers.delete(b.id); app.db.books().update(b.copy(coverPath = null, coverColor = 0, coverSource = "GENERATED")) }
                }, label = { Text("Stoffeinband") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Autor:in") }, modifier = Modifier.fillMaxWidth())
            if (b.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(b.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                scope.launch { app.db.books().update(b.copy(progress = 0f, chapter = 0, charOffset = 0, pdfPage = 0, status = "UNREAD")) }
            }) {
                Icon(Icons.Outlined.RestartAlt, null)
                Spacer(Modifier.width(6.dp))
                Text("Fortschritt zurücksetzen")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (onlinePicker && b != null) {
        OnlineCoverDialog(
            title = title.ifBlank { b.title },
            author = author.ifBlank { b.author },
            onDismiss = { onlinePicker = false },
            onPick = { cover ->
                onlinePicker = false
                scope.launch {
                    val bytes = app.covers.download(cover.url)
                    val stored = bytes?.let { app.covers.store(b.id, it) }
                    if (stored != null) app.db.books().update(b.copy(coverPath = stored.first, coverColor = stored.second, coverSource = "ONLINE"))
                    else Toast.makeText(context, "Cover konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
fun OnlineCoverDialog(title: String, author: String, onDismiss: () -> Unit, onPick: (OnlineCover) -> Unit) {
    val app = LocalContext.current.app
    var q by remember { mutableStateOf(title) }
    var a by remember { mutableStateOf(author) }
    var results by remember { mutableStateOf<List<OnlineCover>?>(null) }
    var trigger by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        results = null
        results = app.covers.search(q, a)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover online suchen") },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("Autor:in") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { trigger++ }) { Text("Erneut suchen") }
                val r = results
                when {
                    r == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    r.isEmpty() -> Text("Nichts gefunden. Versuche einen kürzeren Titel oder prüfe die Internetverbindung.", style = MaterialTheme.typography.bodySmall)
                    else -> LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.heightIn(max = 380.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(r) { c ->
                            Column(Modifier.clickable { onPick(c) }) {
                                AsyncImage(model = c.url, contentDescription = c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(4.dp)))
                                Text(c.source, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "–"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
