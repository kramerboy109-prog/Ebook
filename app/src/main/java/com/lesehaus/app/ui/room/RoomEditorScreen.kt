package com.lesehaus.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lesehaus.app.app
import com.lesehaus.app.data.AmbientSound
import com.lesehaus.app.data.CurtainPalette
import com.lesehaus.app.data.FloorType
import com.lesehaus.app.data.Furniture
import com.lesehaus.app.data.RoomPresets
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.WallPalette
import com.lesehaus.app.data.WallPattern
import com.lesehaus.app.data.Weather
import com.lesehaus.app.data.WindowView
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.enumOr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RoomEditorScreen(roomId: Long, onBack: () -> Unit, onDeleted: () -> Unit, onEditShelf: (Long) -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val stored by app.db.rooms().observeRoom(roomId).collectAsState(initial = null)
    val shelves by app.db.shelves().observeShelves(roomId).collectAsState(initial = emptyList())
    val items by app.db.rooms().observeItems(roomId).collectAsState(initial = emptyList())
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(roomId) { app.ensureDecorated(roomId) }
    var room by remember { mutableStateOf<ReadingRoom?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(stored?.id) { if (room == null) room = stored }
    // Änderungen sanft speichern
    LaunchedEffect(room) {
        val r = room ?: return@LaunchedEffect
        delay(250)
        if (r != stored) app.db.rooms().room(r.id)?.let { cur -> app.db.rooms().update(r.copy(decorated = cur.decorated)) }
    }
    val r = room ?: return
    fun upd(block: (ReadingRoom) -> ReadingRoom) { room = block(r) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zimmer gestalten") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { scope.launch { app.duplicateRoom(r) } }) { Icon(Icons.Outlined.ContentCopy, "Duplizieren") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Zimmer entfernen") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Live-Vorschau
            RoomScene(
                r,
                items = items,
                bookcases = shelves.map { BookcaseData(it, emptyList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(r.roomWidth.coerceIn(1.2f, 3f) / 1.15f)
                    .padding(horizontal = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                animate = true,
            )
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                OutlinedTextField(value = r.name, onValueChange = { v -> upd { it.copy(name = v) } }, label = { Text("Name des Zimmers") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Section("Zimmertyp")
                Text("Ein Typ bringt passende Farben und Stimmung mit. Möbel verschiebst du direkt im Zimmer über „Einrichten“.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoomType.entries.forEach { t ->
                        FilterChip(selected = r.type == t.name, onClick = {
                            val p = RoomPresets.preset(t, r.name)
                            upd { it.copy(type = t.name, wallColor = p.wallColor, wallPattern = p.wallPattern, floorType = p.floorType, furniture = p.furniture, ambientSound = p.ambientSound, curtainColor = p.curtainColor, lightWarmth = p.lightWarmth, lightLevel = p.lightLevel, weather = p.weather, windowView = p.windowView) }
                        }, label = { Text("${t.emoji} ${t.label}") })
                    }
                }
                Section("Wandfarbe")
                ColorRow(WallPalette, r.wallColor) { c -> upd { it.copy(wallColor = c) } }
                Section("Wandgestaltung")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WallPattern.entries.forEach { p -> FilterChip(selected = r.wallPattern == p.name, onClick = { upd { it.copy(wallPattern = p.name) } }, label = { Text(p.label) }) }
                }
                Section("Boden")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FloorType.entries.forEach { p -> FilterChip(selected = r.floorType == p.name, onClick = { upd { it.copy(floorType = p.name) } }, label = { Text(p.label) }) }
                }
                Section("Stoff (Vorhänge, Polster, Teppich)")
                ColorRow(CurtainPalette, r.curtainColor) { c -> upd { it.copy(curtainColor = c) } }
                Section("Fenster & Aussicht")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WindowView.entries.forEach { p -> FilterChip(selected = r.windowView == p.name, onClick = { upd { it.copy(windowView = p.name) } }, label = { Text(p.label) }) }
                }
                if (enumOr(r.windowView, WindowView.AUTO) != WindowView.NONE) {
                    Section("Wetter draußen")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Weather.entries.forEach { p -> FilterChip(selected = r.weather == p.name, onClick = { upd { it.copy(weather = p.name) } }, label = { Text(p.label) }) }
                    }
                }
                Section("Licht")
                Text("Helligkeit", style = MaterialTheme.typography.labelLarge)
                Slider(value = r.lightLevel, onValueChange = { v -> upd { it.copy(lightLevel = v) } })
                Text("Wärme", style = MaterialTheme.typography.labelLarge)
                Slider(value = r.lightWarmth, onValueChange = { v -> upd { it.copy(lightWarmth = v) } })
                Section("Zimmergröße")
                Text("Wie weit du dich im Zimmer umschauen kannst.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = r.roomWidth, onValueChange = { v -> upd { it.copy(roomWidth = v) } }, valueRange = 1.0f..3.5f)
                Section("Tür im Flur")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.lesehaus.app.data.DoorStyle.entries.forEach { d -> FilterChip(selected = r.doorStyle == d.name, onClick = { upd { it.copy(doorStyle = d.name) } }, label = { Text(d.label) }) }
                }
                Spacer(Modifier.height(6.dp))
                ColorRow(com.lesehaus.app.data.DoorPalette.filter { it != 0 }, r.doorColor) { c -> upd { it.copy(doorColor = c) } }
                TextButton(onClick = { upd { it.copy(doorColor = 0) } }) { Text("Türfarbe passend zum Zimmer") }
                Section("Einrichtung")
                Text("Möbel, Bilder, Pflanzen und Regale verschiebst du direkt im Zimmer (Pinsel-Symbol → Einrichten).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirmReset = true }) { Text("Einrichtung auf die Vorlage „${RoomType.entries.firstOrNull { it.name == r.type }?.label ?: r.type}“ zurücksetzen") }
                Section("Raumklang")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmbientSound.entries.forEach { a -> FilterChip(selected = r.ambientSound == a.name, onClick = { upd { it.copy(ambientSound = a.name) } }, label = { Text(a.label) }) }
                }
                Section("Bücherregale in diesem Zimmer")
                shelves.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEditShelf(s.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(enumOr(s.wood, com.lesehaus.app.data.Wood.WALNUT).base)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${s.folderList.size} Ordner · ${s.fileTypeSet.size} Dateitypen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = i > 0, onClick = { scope.launch { swapShelves(app, shelves, i, i - 1) } }) { Icon(Icons.Outlined.KeyboardArrowUp, "Nach oben") }
                        IconButton(enabled = i < shelves.lastIndex, onClick = { scope.launch { swapShelves(app, shelves, i, i + 1) } }) { Icon(Icons.Outlined.KeyboardArrowDown, "Nach unten") }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Einrichtung zurücksetzen?") },
            text = { Text("Alle Möbel und Bilder werden durch die Vorlage ersetzt, Regale neu aufgestellt. Bücher bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.db.rooms().update(r); app.resetDecoration(r.id); room = app.db.rooms().room(r.id) }; confirmReset = false }) { Text("Zurücksetzen") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Zimmer entfernen?") },
            text = { Text("„${r.name}“ und seine Regale werden entfernt. Deine Bücher, Notizen und Lieblingsstellen bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.db.rooms().delete(r); onDeleted() } }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Behalten") } },
        )
    }
}

private suspend fun swapShelves(app: com.lesehaus.app.AppContainer, shelves: List<com.lesehaus.app.data.db.Shelf>, a: Int, b: Int) {
    val list = shelves.toMutableList()
    val tmp = list[a]; list[a] = list[b]; list[b] = tmp
    list.forEachIndexed { i, s -> if (s.sortIndex != i) app.db.shelves().update(s.copy(sortIndex = i)) }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            val sel = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .shadow(if (sel) 4.dp else 0.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f), CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
