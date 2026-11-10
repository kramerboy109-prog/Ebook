package com.lesehaus.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lesehaus.app.AppContainer
import com.lesehaus.app.app
import com.lesehaus.app.data.AmbientSound
import com.lesehaus.app.data.PageAnimation
import com.lesehaus.app.data.PageMode
import com.lesehaus.app.data.ReaderSettings
import com.lesehaus.app.data.ReaderTheme
import com.lesehaus.app.data.TextAlignMode
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.ui.theme.Fonts
import com.lesehaus.app.ui.theme.HandwritingStyle
import com.lesehaus.app.ui.theme.Palette
import com.lesehaus.app.ui.theme.QuoteStyle
import com.lesehaus.app.ui.theme.ReadingFonts
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------
// Hilfsfunktionen
// ------------------------------------------------------------------------------------------------
fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Benny's Library", text))
    Toast.makeText(context, "Kopiert", Toast.LENGTH_SHORT).show()
}

fun shareQuote(context: Context, text: String, title: String, author: String) {
    val body = "„${text.trim()}“\n\n— ${listOf(author, title).filter { it.isNotBlank() }.joinToString(", ")}"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }
    context.startActivity(Intent.createChooser(intent, "Zitat teilen"))
}

fun lookUp(context: Context, text: String) {
    val q = Uri.encode(text.trim().take(80))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://de.wiktionary.org/w/index.php?search=$q"))
    runCatching { context.startActivity(intent) }
}

private val dateFmt = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY)
fun formatDate(ms: Long): String = dateFmt.format(Date(ms))

// ------------------------------------------------------------------------------------------------
// Obere & untere Leiste
// ------------------------------------------------------------------------------------------------
@Composable
fun ReaderTopBar(
    title: String,
    chapter: String,
    bookmarked: Boolean,
    isPdf: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onSearch: () -> Unit,
    onToc: () -> Unit,
    onNotes: () -> Unit,
    onInfo: () -> Unit,
    onFinished: () -> Unit,
    onPdfOriginal: () -> Unit,
    onVoice: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Lesezeichen", tint = if (bookmarked) Color(0xFF9B2F3F) else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Suchen") }
            IconButton(onClick = onToc) { Icon(Icons.AutoMirrored.Outlined.List, "Inhalt") }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Notizen & Markierungen") }, leadingIcon = { Icon(Icons.Outlined.StickyNote2, null) }, onClick = { menu = false; onNotes() })
                    DropdownMenuItem(text = { Text("Buchinfo") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; onInfo() })
                    DropdownMenuItem(text = { Text("Vorlesestimme") }, leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, null) }, onClick = { menu = false; onVoice() })
                    DropdownMenuItem(text = { Text("Als gelesen markieren") }, leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) }, onClick = { menu = false; onFinished() })
                    if (isPdf) DropdownMenuItem(text = { Text("Original-PDF-Ansicht") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { menu = false; onPdfOriginal() })
                }
            }
        }
    }
}

@Composable
fun ReaderBottomBar(
    vm: ReaderViewModel,
    settings: ReaderSettings,
    onSettings: () -> Unit,
    onCozy: () -> Unit,
    onTheme: () -> Unit,
    onTts: () -> Unit,
    onAmbient: () -> Unit,
) {
    val ttsOn by vm.tts.speaking.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 8.dp)) {
            val page = vm.pages.getOrNull(vm.currentPage)
            var sliderPos by remember(vm.currentPage) { mutableStateOf(vm.currentPage.toFloat()) }
            val preview = vm.pages.getOrNull(sliderPos.roundToInt())
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val ch = (page?.chapter ?: 0)
                    val start = vm.pages.indexOfFirst { it.chapter == ch }
                    if (vm.currentPage > start) vm.goToPage(start) else vm.goToPage(vm.pages.indexOfFirst { it.chapter == ch - 1 }.coerceAtLeast(0))
                }) { Icon(Icons.Outlined.SkipPrevious, "Vorheriges Kapitel") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        preview?.let { vm.chapters.getOrNull(it.chapter)?.title } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Seite ${sliderPos.roundToInt() + 1} von ${vm.pages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val ch = (page?.chapter ?: 0)
                    val next = vm.pages.indexOfFirst { it.chapter > ch }
                    if (next >= 0) vm.goToPage(next)
                }) { Icon(Icons.Outlined.SkipNext, "Nächstes Kapitel") }
            }
            if (vm.pages.size > 1) Slider(
                value = sliderPos,
                onValueChange = { sliderPos = it },
                onValueChangeFinished = { vm.goToPage(sliderPos.roundToInt()) },
                valueRange = 0f..(vm.pages.size - 1).toFloat(),
            )
            // Mehrere Seiten auf einmal blättern
            var goTo by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(-50, -20, -10, -5).forEach { n -> JumpChip("$n", vm.currentPage + n >= 0) { vm.goToPage(vm.currentPage + n) } }
                JumpChip("Seite …", true) { goTo = true }
                listOf(5, 10, 20, 50).forEach { n -> JumpChip("+$n", vm.currentPage + n < vm.pages.size) { vm.goToPage(vm.currentPage + n) } }
            }
            if (goTo) GoToPageDialog(vm.currentPage + 1, vm.pages.size, onDismiss = { goTo = false }) { p -> vm.goToPage(p - 1); goTo = false }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarAction(Icons.Outlined.TextFields, "Schrift", false, onSettings)
                BarAction(Icons.Outlined.Palette, "Farben", false, onTheme)
                BarAction(Icons.Outlined.LocalFireDepartment, "Gemütlich", settings.cozyMode, onCozy)
                BarAction(Icons.Outlined.RecordVoiceOver, if (ttsOn) "Stopp" else "Vorlesen", ttsOn, onTts)
                BarAction(Icons.Outlined.MusicNote, "Ambiente", settings.ambientSound != AmbientSound.NONE, onAmbient)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TtsMiniPlayer(vm: ReaderViewModel, modifier: Modifier, onVoice: () -> Unit = {}) {
    val sleepAt by vm.tts.sleepAt.collectAsState()
    val engine by vm.tts.engineLabel.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, modifier = Modifier.padding(start = 8.dp))
            Text(
                if (sleepAt > 0) "Schlaftimer ${SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(sleepAt))}" else engine.ifBlank { "Wird vorgelesen …" },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Bedtime, "Schlaftimer") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(0, 10, 15, 30, 45, 60).forEach { m ->
                        DropdownMenuItem(text = { Text(if (m == 0) "Kein Schlaftimer" else "$m Minuten") }, onClick = { vm.tts.setSleepTimer(m); menu = false })
                    }
                }
            }
            IconButton(onClick = onVoice) { Icon(Icons.Outlined.RecordVoiceOver, "Stimme wählen") }
            IconButton(onClick = { vm.tts.stop() }) { Icon(Icons.Filled.Stop, "Stopp") }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Auswahl
// ------------------------------------------------------------------------------------------------
@Composable
fun SelectionToolbar(
    text: String,
    modifier: Modifier,
    isFavorite: Boolean,
    onHighlight: (Int) -> Unit,
    onUnderline: () -> Unit,
    onNote: () -> Unit,
    onFavorite: () -> Unit,
    onExpandSentence: () -> Unit,
    onExpandParagraph: () -> Unit,
    onExpandPage: () -> Unit,
    onSpeak: () -> Unit,
    onClose: () -> Unit,
    bookTitle: String,
    author: String,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(10.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "„${text.replace('\n', ' ')}“",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Schließen") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Palette.Highlights.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                            .clickable { onHighlight(c.toArgbInt()) }
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onUnderline) { Icon(Icons.Outlined.FormatUnderlined, "Unterstreichen") }
                IconButton(onClick = onNote) { Icon(Icons.Outlined.EditNote, "Notiz") }
                IconButton(onClick = onFavorite) {
                    Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Gefällt mir", tint = Color(0xFFC0392B))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SmallChip("Satz", onExpandSentence)
                SmallChip("Absatz", onExpandParagraph)
                SmallChip("Ganze Seite", onExpandPage)
                IconButton(onClick = { copyText(context, text) }) { Icon(Icons.Outlined.ContentCopy, "Kopieren") }
                IconButton(onClick = { shareQuote(context, text, bookTitle, author) }) { Icon(Icons.Outlined.Share, "Teilen") }
                IconButton(onClick = { lookUp(context, text) }) { Icon(Icons.Outlined.Translate, "Nachschlagen") }
                IconButton(onClick = onSpeak) { Icon(Icons.Outlined.RecordVoiceOver, "Ab hier vorlesen") }
            }
            Text(
                "Tipp: Ziehen nach dem langen Drücken oder Tippen auf ein weiteres Wort erweitert die Auswahl.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
}

@Composable
fun AnnotationPopup(
    annotation: Annotation,
    modifier: Modifier,
    onColor: (Int) -> Unit,
    onEditNote: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    bookTitle: String,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(10.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(4.dp).height(40.dp).background(Color(annotation.color)))
                Spacer(Modifier.width(10.dp))
                Text("„${annotation.text}“", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Schließen") }
            }
            annotation.note?.let {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFFFF4C2), shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp) {
                    Text(it, style = HandwritingStyle.copy(color = Color(0xFF3A2E1E)), modifier = Modifier.padding(10.dp).fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Palette.Highlights.forEach { c ->
                    Box(Modifier.padding(end = 6.dp).size(24.dp).clip(CircleShape).background(c).clickable { onColor(c.toArgbInt()) })
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEditNote) { Icon(Icons.Outlined.EditNote, "Notiz") }
                IconButton(onClick = onFavorite) { Icon(Icons.Outlined.FavoriteBorder, "Gefällt mir", tint = Color(0xFFC0392B)) }
                IconButton(onClick = { shareQuote(context, annotation.text, bookTitle, "") }) { Icon(Icons.Outlined.Share, "Teilen") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Löschen") }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Dialoge
// ------------------------------------------------------------------------------------------------
@Composable
fun NoteDialog(initial: String, quote: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notiz") },
        text = {
            Column {
                Text("„${quote.take(220)}${if (quote.length > 220) "…" else ""}“", style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = HandwritingStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    placeholder = { Text("Deine Gedanken zu dieser Stelle …", style = HandwritingStyle) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun FavoriteDialog(
    vm: ReaderViewModel,
    initial: Selection,
    initialKind: String,
    onDismiss: () -> Unit,
    onSave: (Selection, String, String, String?) -> Unit,
) {
    val context = LocalContext.current
    val allFavs by context.app.db.annotations().observeFavorites().collectAsState(initial = emptyList())
    val collections = remember(allFavs) { (listOf("Lieblingsstellen") + allFavs.map { it.collection }).distinct() }
    var kind by remember { mutableStateOf(initialKind) }
    var collection by remember { mutableStateOf("Lieblingsstellen") }
    var newCollection by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val sel = when (kind) {
        "LINE" -> vm.sentenceOf(initial)
        "PASSAGE" -> vm.paragraphOf(initial)
        "PAGE" -> vm.pages.firstOrNull { it.chapter == initial.chapter && initial.start >= it.start && initial.start < it.end }?.let { Selection(it.chapter, it.start, it.end) } ?: initial
        else -> initial
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Favorite, null, tint = Color(0xFFC0392B)) },
        title = { Text("Gefällt mir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Was möchtest du aufbewahren?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SELECTION" to "Auswahl", "LINE" to "Satz / Zeile", "PASSAGE" to "Passage", "PAGE" to "Ganze Seite").forEach { (k, l) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "„${vm.selectedText(sel).take(600)}${if (vm.selectedText(sel).length > 600) " …" else ""}“",
                        style = QuoteStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(12.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Sammlung", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    collections.forEach { c -> FilterChip(selected = collection == c && newCollection.isBlank(), onClick = { collection = c; newCollection = "" }, label = { Text(c) }) }
                }
                OutlinedTextField(value = newCollection, onValueChange = { newCollection = it }, label = { Text("Neue Sammlung (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Warum gefällt sie dir? (optional)") }, textStyle = HandwritingStyle.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(sel, kind, newCollection.ifBlank { collection }, note)
                Toast.makeText(context, "In „${newCollection.ifBlank { collection }}“ gespeichert", Toast.LENGTH_SHORT).show()
            }) { Text("Aufbewahren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ------------------------------------------------------------------------------------------------
// Darstellung
// ------------------------------------------------------------------------------------------------
@Composable
fun DisplaySheet(app: AppContainer, s: ReaderSettings, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val appSettings by app.settings.app.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val name = (androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: "Schrift.ttf").replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val out = File(dir, name)
            runCatching { context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } }
            if (out.exists() && out.length() > 0) {
                app.settings.updateApp { it.copy(customFonts = (it.customFonts + out.absolutePath).distinct()) }
                app.settings.updateReader { it.copy(fontKey = "file:${out.absolutePath}") }
            }
        }
    }
    fun upd(block: (ReaderSettings) -> ReaderSettings) = app.settings.updateReader(block)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        TabRow(selectedTabIndex = tab) {
            listOf("Schrift", "Layout", "Blättern").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
            when (tab) {
                0 -> {
                    LabeledSlider("Schriftgröße", s.fontSize, 12f..34f, "${s.fontSize.roundToInt()} pt") { v -> upd { it.copy(fontSize = v.roundToInt().toFloat()) } }
                    LabeledSlider("Strichstärke", s.fontWeight.toFloat(), 300f..600f, when { s.fontWeight < 370 -> "Leicht"; s.fontWeight < 450 -> "Normal"; s.fontWeight < 550 -> "Kräftig"; else -> "Fett" }) { v -> upd { it.copy(fontWeight = (v / 50).roundToInt() * 50) } }
                    LabeledSlider("Zeichenabstand", s.letterSpacing, -0.03f..0.12f, "%.2f".format(s.letterSpacing)) { v -> upd { it.copy(letterSpacing = (v * 100).roundToInt() / 100f) } }
                    Spacer(Modifier.height(8.dp))
                    Text("Schriftart", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    (ReadingFonts.builtIn + ReadingFonts.custom(appSettings.customFonts)).forEach { f ->
                        val selected = f.key == s.fontKey
                        Surface(
                            onClick = { upd { it.copy(fontKey = f.key) } },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(f.label, style = TextStyle(fontFamily = f.family, fontSize = 19.sp))
                                Text("Der Abend dämmerte über den Bücherregalen. · ${f.description}", style = TextStyle(fontFamily = f.family, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    TextButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) }) {
                        Text("+ Eigene Schrift (TTF/OTF) hinzufügen")
                    }
                }
                1 -> {
                    LabeledSlider("Zeilenabstand", s.lineHeight, 1.1f..2.2f, "%.2f".format(s.lineHeight)) { v -> upd { it.copy(lineHeight = (v * 20).roundToInt() / 20f) } }
                    LabeledSlider("Absatzabstand", s.paragraphSpacing, 0f..1.5f, "%.1f".format(s.paragraphSpacing)) { v -> upd { it.copy(paragraphSpacing = (v * 10).roundToInt() / 10f) } }
                    LabeledSlider("Seitenrand links/rechts", s.marginH.toFloat(), 8f..64f, "${s.marginH} dp") { v -> upd { it.copy(marginH = v.roundToInt()) } }
                    LabeledSlider("Seitenrand oben/unten", s.marginV.toFloat(), 8f..80f, "${s.marginV} dp") { v -> upd { it.copy(marginV = v.roundToInt()) } }
                    Text("Ausrichtung", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        TextAlignMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = s.align == m,
                                onClick = { upd { it.copy(align = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, TextAlignMode.entries.size),
                                icon = { Icon(if (m == TextAlignMode.JUSTIFY) Icons.Outlined.FormatAlignJustify else Icons.AutoMirrored.Outlined.FormatAlignLeft, null) },
                            ) { Text(m.label) }
                        }
                    }
                    SwitchRow("Silbentrennung", "Für schönen Blocksatz", s.hyphenation) { v -> upd { it.copy(hyphenation = v) } }
                    SwitchRow("Einzug am Absatzanfang", "Klassischer Buchsatz", s.indent) { v -> upd { it.copy(indent = v) } }
                    SwitchRow("Bilder anzeigen", null, s.showImages) { v -> upd { it.copy(showImages = v) } }
                    SwitchRow("Kopfzeile (Kapitel & Uhrzeit)", null, s.showHeader) { v -> upd { it.copy(showHeader = v) } }
                    SwitchRow("Uhrzeit in der Kopfzeile", null, s.showClock) { v -> upd { it.copy(showClock = v) } }
                    SwitchRow("Fußzeile (Seite & Fortschritt)", null, s.showFooter) { v -> upd { it.copy(showFooter = v) } }
                    SwitchRow("Restzeit im Kapitel", "Geschätzt aus deinem Lesetempo", s.showTimeLeft) { v -> upd { it.copy(showTimeLeft = v) } }
                }
                else -> {
                    Text("Lesemodus", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        PageMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(selected = s.pageMode == m, onClick = { upd { it.copy(pageMode = m) } }, shape = SegmentedButtonDefaults.itemShape(i, PageMode.entries.size)) { Text(m.label) }
                        }
                    }
                    Text("Blätter-Animation", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageAnimation.entries.forEach { a -> FilterChip(selected = s.pageAnimation == a, onClick = { upd { it.copy(pageAnimation = a) } }, label = { Text(a.label) }) }
                    }
                    SwitchRow("Tippzonen", "Links tippen = zurück, rechts = weiter, Mitte = Menü", s.tapZones) { v -> upd { it.copy(tapZones = v) } }
                    SwitchRow("Mit Lautstärketasten blättern", null, s.volumeKeys) { v -> upd { it.copy(volumeKeys = v) } }
                    SwitchRow("Vollbild", "Statusleiste beim Lesen ausblenden", s.fullscreen) { v -> upd { it.copy(fullscreen = v) } }
                    SwitchRow("Bildschirm anlassen", null, s.keepScreenOn) { v -> upd { it.copy(keepScreenOn = v) } }
                    LabeledSlider("Vorlese-Tempo", s.ttsRate, 0.5f..2f, "%.1fx".format(s.ttsRate)) { v -> upd { it.copy(ttsRate = (v * 10).roundToInt() / 10f) } }
                    LabeledSlider("Stimmhöhe", s.ttsPitch, 0.5f..1.6f, "%.1f".format(s.ttsPitch)) { v -> upd { it.copy(ttsPitch = (v * 10).roundToInt() / 10f) } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThemeSheet(app: AppContainer, s: ReaderSettings, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun upd(block: (ReaderSettings) -> ReaderSettings) = app.settings.updateReader(block)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("Farbwelt", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderTheme.entries.forEach { t -> ThemeSwatch(t, s.theme == t) { upd { it.copy(theme = t) } } }
            }
            Spacer(Modifier.height(14.dp))
            SwitchRow("Automatischer Nachtmodus", "Ab 20 Uhr dunkles Thema verwenden", s.autoNightTheme) { v -> upd { it.copy(autoNightTheme = v) } }
            if (s.autoNightTheme) {
                Text("Nachtthema", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ReaderTheme.entries.filter { it.dark }.forEach { t -> Box(Modifier.padding(end = 10.dp)) { ThemeSwatch(t, s.nightTheme == t) { upd { it.copy(nightTheme = t) } } } }
                }
            }
            SwitchRow("Papierstruktur", "Feine Körnung wie echtes Papier", s.paperTexture) { v -> upd { it.copy(paperTexture = v) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow("Gemütlicher Lesemodus", "Warmes Kerzenlicht, sanfte Vignette", s.cozyMode) { v -> upd { it.copy(cozyMode = v) } }
            if (s.cozyMode) LabeledSlider("Intensität", s.cozyGlow, 0.1f..1f, "${(s.cozyGlow * 100).roundToInt()} %") { v -> upd { it.copy(cozyGlow = v) } }
            LabeledSlider("Wärmefilter (weniger Blaulicht)", s.warmFilter, 0f..1f, if (s.warmFilter == 0f) "Aus" else "${(s.warmFilter * 100).roundToInt()} %") { v -> upd { it.copy(warmFilter = v) } }
            val sys = s.brightness < 0
            SwitchRow("Systemhelligkeit verwenden", null, sys) { v -> upd { it.copy(brightness = if (v) -1f else 0.5f) } }
            if (!sys) LabeledSlider("Helligkeit", s.brightness, 0.01f..1f, "${(s.brightness * 100).roundToInt()} %") { v -> upd { it.copy(brightness = v) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeSwatch(t: ReaderTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 70.dp, height = 88.dp)
                .shadow(if (selected) 6.dp else 1.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(t.bg))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color(t.accent) else Color.Black.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            Column {
                Text("Aa", style = TextStyle(fontFamily = Fonts.Literata, fontSize = 22.sp, color = Color(t.fg)))
                repeat(3) {
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth(if (it == 2) 0.6f else 1f).height(3.dp).background(Color(t.fg).copy(alpha = 0.35f)))
                }
            }
        }
        Text(t.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun AmbientSheet(app: AppContainer, s: ReaderSettings, vm: ReaderViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun upd(block: (ReaderSettings) -> ReaderSettings) = app.settings.updateReader(block)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("Klangkulisse", style = MaterialTheme.typography.titleLarge)
            Text("Sanfte, live erzeugte Geräusche – ganz ohne Internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AmbientSound.entries.forEach { a ->
                    FilterChip(selected = s.ambientSound == a, onClick = { upd { it.copy(ambientSound = a) } }, label = { Text(a.label) })
                }
            }
            LabeledSlider("Lautstärke", s.ambientVolume, 0f..1f, "${(s.ambientVolume * 100).roundToInt()} %") { v -> upd { it.copy(ambientVolume = v) }; vm.ambient.setVolume(v) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------------------------------------
// Inhalt, Lesezeichen, Notizen
// ------------------------------------------------------------------------------------------------
@Composable
fun TocSheet(
    vm: ReaderViewModel,
    startTab: Int,
    onDismiss: () -> Unit,
    onJump: (Int, Int, Int?) -> Unit,
    onEditNote: (Annotation) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(startTab) }
    val bookmarks by vm.bookmarks.collectAsState()
    val annotations by vm.annotations.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        TabRow(selectedTabIndex = tab) {
            listOf("Inhalt", "Lesezeichen", "Notizen", "♥").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            when (tab) {
                0 -> {
                    val cur = vm.pages.getOrNull(vm.currentPage)?.chapter
                    itemsIndexed(vm.chapters) { i, ch ->
                        val first = vm.pages.indexOfFirst { it.chapter == i }
                        Row(
                            Modifier.fillMaxWidth().clickable { onJump(i, 0, null) }.background(if (cur == i) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent).padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(ch.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (first >= 0) Text("${first + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                1 -> {
                    if (bookmarks.isEmpty()) item { EmptyHint("Noch keine Lesezeichen. Tippe oben auf das Lesezeichen-Symbol, um die aktuelle Seite zu merken.") }
                    items(bookmarks, key = { it.id }) { b ->
                        Row(Modifier.fillMaxWidth().clickable { onJump(b.chapter, b.charOffset, null) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF9B2F3F))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.label.ifBlank { "Lesezeichen" }, style = MaterialTheme.typography.titleSmall)
                                Text(b.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(b.progress * 100).roundToInt()} % · ${formatDate(b.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteBookmark(b) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                        }
                    }
                }
                2 -> {
                    if (annotations.isEmpty()) item { EmptyHint("Lange auf ein Wort drücken, um zu markieren oder eine Notiz zu hinterlassen.") }
                    items(annotations, key = { it.id }) { a ->
                        Column(Modifier.fillMaxWidth().clickable { onJump(a.chapter, a.start, a.end) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 3.dp).width(4.dp).height(36.dp).background(Color(a.color)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(vm.chapters.getOrNull(a.chapter)?.title ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("„${a.text}“", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    a.note?.let { Text(it, style = HandwritingStyle.copy(fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)) }
                                }
                                IconButton(onClick = { onEditNote(a) }) { Icon(Icons.Outlined.EditNote, "Notiz bearbeiten") }
                                IconButton(onClick = { vm.deleteAnnotation(a) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                            }
                        }
                    }
                }
                else -> {
                    if (favorites.isEmpty()) item { EmptyHint("Markiere eine Stelle und tippe auf ♥, um sie für später aufzubewahren.") }
                    items(favorites, key = { it.id }) { f ->
                        Column(Modifier.fillMaxWidth().clickable { onJump(f.chapter, f.start, f.end) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text("„${f.text}“", style = QuoteStyle.copy(fontSize = 17.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface), maxLines = 5, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${f.collection} · ${formatDate(f.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { shareQuote(context, f.text, f.bookTitle, f.bookAuthor) }) { Icon(Icons.Outlined.Share, "Teilen") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
}

@Composable
fun SearchSheet(vm: ReaderViewModel, onDismiss: () -> Unit, onJump: (SearchHit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.search(it) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Im Buch suchen …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            if (vm.searching) Text("Suche …", style = MaterialTheme.typography.labelMedium)
            else if (query.length >= 2) Text("${vm.searchHits.size} Treffer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn {
                items(vm.searchHits) { hit ->
                    Column(Modifier.fillMaxWidth().clickable { onJump(hit) }.padding(vertical = 10.dp)) {
                        Text(hit.chapterTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("… ${hit.snippet} …", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun BookInfoSheet(vm: ReaderViewModel, onDismiss: () -> Unit) {
    val book by vm.book.collectAsState()
    val b = book ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(b.title, style = MaterialTheme.typography.headlineSmall)
            if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            InfoLine("Format", b.format.uppercase())
            InfoLine("Datei", b.fileName)
            InfoLine("Kapitel", vm.chapters.size.toString())
            InfoLine("Seiten (aktuelles Layout)", vm.pages.size.toString())
            InfoLine("Fortschritt", "${(b.progress * 100).roundToInt()} %")
            InfoLine("Lesezeit insgesamt", "${b.readingTimeMs / 60_000} Min.")
            if (b.language.isNotBlank()) InfoLine("Sprache", b.language)
            if (b.publisher.isNotBlank()) InfoLine("Verlag", b.publisher)
            if (b.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(b.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(170.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun JumpChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
fun GoToPageDialog(current: Int, total: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Seite springen") },
        text = {
            Column {
                Text("Du bist auf Seite $current von $total.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) }, singleLine = true,
                    label = { Text("Seite (1–$total)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Text("Oder in Prozent:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 25, 50, 75, 90).forEach { pct -> AssistChip(onClick = { onGo(((total - 1) * pct / 100) + 1) }, label = { Text("$pct %") }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = n != null && n in 1..total, onClick = { n?.let(onGo) }) { Text("Springen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
