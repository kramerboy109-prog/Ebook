package com.lesehaus.app.ui.shelf

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesehaus.app.app
import com.lesehaus.app.data.ShelfDisplay
import com.lesehaus.app.data.ShelfSort
import com.lesehaus.app.data.SupportedFormats
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.ui.room.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lesbarer Name eines SAF-Ordners, z. B. „Interner Speicher › Books › Romane“. */
fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    val (vol, path) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val volName = if (vol == "primary") "Interner Speicher" else if (vol.equals("home", true)) "Dokumente" else "Speicher $vol"
    (listOf(volName) + path.split('/').filter { it.isNotBlank() }).joinToString(" › ")
}.getOrDefault(uri)

@Composable
fun ShelfEditorScreen(shelfId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val stored by app.db.shelves().observeShelf(shelfId).collectAsState(initial = null)
    val scans by app.scanner.state.collectAsState()
    var shelf by remember { mutableStateOf<Shelf?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(stored?.id) { if (shelf == null) shelf = stored }
    LaunchedEffect(shelf) {
        val s = shelf ?: return@LaunchedEffect
        delay(250)
        val cur = app.db.shelves().shelf(s.id)
        if (cur != null && s != cur) app.db.shelves().update(s.copy(lastScan = cur.lastScan))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val s = shelf
        if (uri != null && s != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val updated = s.copy(folders = (s.folderList + uri.toString()).distinct().joinToString("\n"))
            shelf = updated
            scope.launch {
                app.db.shelves().update(updated)
                app.scanShelf(updated)
            }
        }
    }
    val s = shelf ?: return
    fun upd(block: (Shelf) -> Shelf) { shelf = block(s) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bücherregal einrichten") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Regal entfernen") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(value = s.name, onValueChange = { v -> upd { it.copy(name = v) } }, label = { Text("Name des Regals") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Section("Wo sucht dieses Regal nach Büchern?")
            Text(
                "Wähle einen oder mehrere Ordner. Das Regal zeigt alle passenden Dateien darin als Bücher an.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            s.folderList.forEach { f ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Text(folderLabel(f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = {
                            val updated = s.copy(folders = (s.folderList - f).joinToString("\n"))
                            shelf = updated
                            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            scope.launch { app.db.shelves().update(updated); app.scanShelf(updated) }
                        }) { Icon(Icons.Outlined.Delete, "Ordner entfernen") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(null) }) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ordner hinzufügen")
                }
                OutlinedButton(onClick = { app.scanShelf(s) }, enabled = s.folderList.isNotEmpty() && scans[s.id] == null) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (scans[s.id] != null) "Sucht …" else "Jetzt suchen")
                }
            }
            scans[s.id]?.let { st ->
                Text(if (st.found > 0) "${st.processed} von ${st.found}: ${st.current}" else st.current, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().clickable { upd { it.copy(recursive = !it.recursive) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = s.recursive, onCheckedChange = { v -> upd { it.copy(recursive = v) } })
                Text("Auch Unterordner durchsuchen")
            }

            Section("Welche Dateien sind Bücher?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SupportedFormats.forEach { (ext, label) ->
                    val on = ext in s.fileTypeSet
                    FilterChip(
                        selected = on,
                        onClick = { upd { it.copy(fileTypes = (if (on) it.fileTypeSet - ext else it.fileTypeSet + ext).joinToString(",")) } },
                        label = { Text("$label (.$ext)") },
                        leadingIcon = if (on) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                    )
                }
            }
            Row {
                TextButton(onClick = { upd { it.copy(fileTypes = SupportedFormats.keys.joinToString(",")) } }) { Text("Alle") }
                TextButton(onClick = { upd { it.copy(fileTypes = "epub,fb2") } }) { Text("Nur E-Books") }
                TextButton(onClick = { upd { it.copy(fileTypes = "pdf") } }) { Text("Nur PDF") }
                TextButton(onClick = { upd { it.copy(fileTypes = "txt,md,rtf,docx,odt,html,htm") } }) { Text("Nur Texte") }
            }

            Section("Holz")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Wood.entries.forEach { w ->
                    val sel = s.wood == w.name
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { upd { it.copy(wood = w.name) } }) {
                        Box(
                            Modifier
                                .size(width = 64.dp, height = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(Color(w.light), Color(w.base), Color(w.dark))))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        )
                        Text(w.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Section("Darstellung")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShelfDisplay.entries.forEach { d -> FilterChip(selected = s.displayMode == d.name, onClick = { upd { it.copy(displayMode = d.name) } }, label = { Text(d.label) }) }
            }
            Section("Sortierung")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShelfSort.entries.forEach { d -> FilterChip(selected = s.sortOrder == d.name, onClick = { upd { it.copy(sortOrder = d.name) } }, label = { Text(d.label) }) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Regal entfernen?") },
            text = { Text("Das Regal verschwindet aus dem Zimmer. Deine Dateien bleiben unberührt, Notizen und Lieblingsstellen bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.db.shelves().delete(s); onBack() } }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Behalten") } },
        )
    }
}
