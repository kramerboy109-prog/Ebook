package com.lesehaus.app.ui.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.ui.reader.NoteDialog
import com.lesehaus.app.ui.reader.formatDate
import com.lesehaus.app.ui.theme.HandwritingStyle
import kotlinx.coroutines.launch

/** Erstellt eine Markdown-Zusammenfassung aller Notizen & Markierungen eines Buchs. */
fun annotationsMarkdown(book: Book?, items: List<Annotation>): String = buildString {
    append("# ").append(book?.title ?: "Notizen").append('\n')
    if (!book?.author.isNullOrBlank()) append("*").append(book?.author).append("*\n")
    append('\n')
    items.sortedWith(compareBy({ it.chapter }, { it.start }, { it.pdfPage })).forEach { a ->
        if (a.pdfPage >= 0) append("**Seite ${a.pdfPage + 1}**\n\n") else append("> ").append(a.text.replace("\n", "\n> ")).append("\n\n")
        a.note?.let { append(it).append("\n\n") }
        append("<sub>").append(formatDate(a.createdAt)).append("</sub>\n\n---\n\n")
    }
}

@Composable
fun NotesScreen(bottomBar: @Composable () -> Unit, onOpen: (Annotation) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val annotations by app.db.annotations().observeAllAnnotations().collectAsState(initial = emptyList())
    val books by app.db.books().observeAll().collectAsState(initial = emptyList())
    val bookMap = remember(books) { books.associateBy { it.id } }
    var filterBook by remember { mutableStateOf<Long?>(null) }
    var onlyNotes by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Annotation?>(null) }
    var exportBook by remember { mutableStateOf<Long?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val id = exportBook
        if (uri != null && id != null) {
            val md = annotationsMarkdown(bookMap[id], annotations.filter { it.bookId == id })
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) } }
                .onSuccess { Toast.makeText(context, "Exportiert", Toast.LENGTH_SHORT).show() }
        }
    }
    val booksWithNotes = remember(annotations, bookMap) { annotations.map { it.bookId }.distinct().mapNotNull { bookMap[it] } }
    val shown = annotations.filter {
        (filterBook == null || it.bookId == filterBook) && (!onlyNotes || !it.note.isNullOrBlank()) &&
            (query.isBlank() || it.text.contains(query, true) || (it.note ?: "").contains(query, true))
    }
    val grouped = shown.groupBy { it.bookId }

    Scaffold(bottomBar = bottomBar) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text("Notizen & Markierungen", style = MaterialTheme.typography.headlineMedium)
                    Text("${annotations.size} Einträge in ${booksWithNotes.size} Büchern", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (annotations.isNotEmpty()) item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Notizen durchsuchen …") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = onlyNotes, onClick = { onlyNotes = !onlyNotes }, label = { Text("Nur mit Notiz") }) }
                    item { FilterChip(selected = filterBook == null, onClick = { filterBook = null }, label = { Text("Alle Bücher") }) }
                    items(booksWithNotes) { b -> FilterChip(selected = filterBook == b.id, onClick = { filterBook = b.id }, label = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                }
            }
            if (annotations.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.StickyNote2, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Drücke beim Lesen lange auf ein Wort, um Textstellen farbig zu markieren oder eine Notiz an den Rand zu schreiben.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            grouped.forEach { (bookId, list) ->
                val b = bookMap[bookId]
                item(key = "b$bookId") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b?.title ?: "Unbekanntes Buch", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!b?.author.isNullOrBlank()) Text(b?.author ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val md = annotationsMarkdown(b, list)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, md) }
                            context.startActivity(android.content.Intent.createChooser(intent, "Notizen teilen"))
                        }) { Icon(Icons.Outlined.Share, "Teilen") }
                        IconButton(onClick = { exportBook = bookId; exporter.launch("${(b?.title ?: "Notizen").take(60)} – Notizen.md") }) { Icon(Icons.Outlined.FileDownload, "Als Markdown exportieren") }
                    }
                }
                items(list, key = { it.id }) { a ->
                    Surface(
                        onClick = { onOpen(a) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Box(Modifier.width(5.dp).height(46.dp).clip(RoundedCornerShape(3.dp)).background(Color(a.color)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                if (a.pdfPage >= 0) Text("Seite ${a.pdfPage + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                else Text("„${a.text}“", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), maxLines = 5, overflow = TextOverflow.Ellipsis)
                                a.note?.let { Text(it, style = HandwritingStyle.copy(fontSize = 19.sp, color = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(top = 4.dp)) }
                                Text(formatDate(a.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                IconButton(onClick = { editing = a }) { Icon(Icons.Outlined.EditNote, "Notiz bearbeiten") }
                                IconButton(onClick = { scope.launch { app.db.annotations().deleteAnnotation(a) } }) { Icon(Icons.Outlined.Delete, "Löschen") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    editing?.let { a ->
        NoteDialog(initial = a.note ?: "", quote = a.text, onDismiss = { editing = null }, onSave = {
            scope.launch { app.db.annotations().updateAnnotation(a.copy(note = it.ifBlank { null }, updatedAt = System.currentTimeMillis())) }
            editing = null
        })
    }
}
