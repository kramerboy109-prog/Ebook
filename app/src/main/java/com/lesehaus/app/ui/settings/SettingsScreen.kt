package com.lesehaus.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lesehaus.app.app
import com.lesehaus.app.data.AppThemeMode
import com.lesehaus.app.data.Backup
import com.lesehaus.app.data.SupportedFormats
import com.lesehaus.app.ui.reader.LabeledSlider
import com.lesehaus.app.ui.reader.SwitchRow
import com.lesehaus.app.ui.room.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val s by app.settings.app.collectAsState()
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = Backup.export(app)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.onSuccess { Toast.makeText(context, "Sicherung gespeichert", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Fehler: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } } ?: error("Datei leer")
                Backup.import(app, json)
            }.onSuccess { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Import fehlgeschlagen: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Section("Erscheinungsbild")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppThemeMode.entries.forEach { m -> FilterChip(selected = s.themeMode == m, onClick = { app.settings.updateApp { it.copy(themeMode = m) } }, label = { Text(m.label) }) }
            }
            SwitchRow("Lebendige Zimmer", "Kaminfeuer, Regen, Kerzen, Tiere und Uhren bewegen sich", s.roomAnimations) { v -> app.settings.updateApp { it.copy(roomAnimations = v) } }
            SwitchRow("Buch-Öffnen-Animation", "Das Buch wird aus dem Regal gezogen und aufgeschlagen", s.bookAnimations) { v -> app.settings.updateApp { it.copy(bookAnimations = v) } }
            SwitchRow("Raumklang beim Betreten", "Jedes Zimmer spielt leise seinen Klang", s.roomSounds) { v -> app.settings.updateApp { it.copy(roomSounds = v) } }
            SwitchRow("Zimmerklang beim Lesen", "Ohne eigene Klangkulisse spielt leise der Klang des Zimmers, aus dem das Buch stammt", s.roomAmbienceInReader) { v -> app.settings.updateApp { it.copy(roomAmbienceInReader = v) } }

            Section("Flur")
            Text("So sieht der Gang mit deinen Zimmertüren aus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tapete", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            com.lesehaus.app.ui.room.ColorRow(com.lesehaus.app.data.WallPalette, s.hallWall) { c -> app.settings.updateApp { it.copy(hallWall = c) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                com.lesehaus.app.data.WallPattern.entries.forEach { p -> FilterChip(selected = s.hallPattern == p.name, onClick = { app.settings.updateApp { it.copy(hallPattern = p.name) } }, label = { Text(p.label) }) }
            }
            Text("Boden", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.lesehaus.app.data.FloorType.entries.forEach { f -> FilterChip(selected = s.hallFloor == f.name, onClick = { app.settings.updateApp { it.copy(hallFloor = f.name) } }, label = { Text(f.label) }) }
            }
            Text("Decke", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.lesehaus.app.ui.room.Ceiling.entries.forEach { c -> FilterChip(selected = s.hallCeiling == c.name, onClick = { app.settings.updateApp { it.copy(hallCeiling = c.name) } }, label = { Text(c.label) }) }
            }
            Text("Läufer & Vorhänge", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            com.lesehaus.app.ui.room.ColorRow(com.lesehaus.app.data.CurtainPalette, s.hallRunner) { c -> app.settings.updateApp { it.copy(hallRunner = c) } }
            SwitchRow("Holzvertäfelung im Flur", null, s.hallWainscot) { v -> app.settings.updateApp { it.copy(hallWainscot = v) } }

            Section("Bibliothek")
            SwitchRow("Regale automatisch aktualisieren", "Beim Betreten eines Zimmers nach neuen Büchern suchen", s.autoScan) { v -> app.settings.updateApp { it.copy(autoScan = v) } }
            SwitchRow("Cover online suchen", "Fehlende Cover bei Open Library & Google Books finden (Titel/Autor werden übertragen)", s.onlineCovers) { v -> app.settings.updateApp { it.copy(onlineCovers = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { app.scanAll(); Toast.makeText(context, "Alle Regale werden aktualisiert …", Toast.LENGTH_SHORT).show() }) { Text("Alle Regale aktualisieren") }
                OutlinedButton(onClick = {
                    scope.launch {
                        app.db.books().all().filter { it.coverPath == null }.forEach { app.db.books().update(it.copy(onlineCoverTried = false)) }
                        app.scanAll()
                        Toast.makeText(context, "Suche fehlende Cover …", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = s.onlineCovers) { Text("Cover nachladen") }
            }

            Section("Lesen")
            LabeledSlider("Tägliches Leseziel", s.dailyGoalMinutes.toFloat(), 5f..120f, "${s.dailyGoalMinutes} Min.") { v -> app.settings.updateApp { it.copy(dailyGoalMinutes = (v / 5).toInt() * 5) } }
            Text("Schrift, Farben, Ränder, Blättern und Vorlesen stellst du direkt im Buch über das Menü (Mitte antippen) ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (s.customFonts.isNotEmpty()) {
                Section("Eigene Schriften")
                s.customFonts.forEach { path ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(File(path).nameWithoutExtension, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            File(path).delete()
                            app.settings.updateApp { it.copy(customFonts = it.customFonts - path) }
                            app.settings.updateReader { if (it.fontKey == "file:$path") it.copy(fontKey = "literata") else it }
                        }) { Icon(Icons.Outlined.Delete, "Entfernen") }
                    }
                }
            }

            Section("Sicherung")
            Text("Zimmer, Regale, Lesefortschritt, Lesezeichen, Notizen, Markierungen und Lieblingsstellen als Datei sichern oder wiederherstellen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exporter.launch("BennysLibrary-Sicherung-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") }) { Text("Sichern") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Wiederherstellen") }
            }

            Section("Über Benny's Library")
            Text("Version 1.0 · Unterstützte Formate: " + SupportedFormats.values.distinct().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Alle Daten bleiben auf deinem Gerät. Schriften: Literata, EB Garamond, Lora, Crimson Pro, Atkinson Hyperlegible, Nunito, Playfair Display und Caveat – lizenziert unter der SIL Open Font License.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
}
