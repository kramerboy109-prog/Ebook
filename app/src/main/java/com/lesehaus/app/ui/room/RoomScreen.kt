package com.lesehaus.app.ui.room

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.data.AmbientSound
import com.lesehaus.app.data.CurtainPalette
import com.lesehaus.app.data.DefaultFileTypes
import com.lesehaus.app.data.ShelfSort
import com.lesehaus.app.data.Weather
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.RoomItem
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.enumOr
import com.lesehaus.app.ui.book.BookDetailSheet
import com.lesehaus.app.ui.book.BookOpeningOverlay
import com.lesehaus.app.ui.book.OpeningBook
import com.lesehaus.app.ui.reader.AmbientPlayer
import com.lesehaus.app.ui.reader.effectiveTheme
import com.lesehaus.app.ui.shelf.BookArrangement
import com.lesehaus.app.ui.shelf.BookcaseCloseUp
import com.lesehaus.app.ui.shelf.BookcaseStyle
import com.lesehaus.app.ui.shelf.arrangeBooks
import com.lesehaus.app.ui.shelf.bookcaseRect
import com.lesehaus.app.ui.shelf.drawBookcase
import com.lesehaus.app.ui.theme.Fonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

fun sortBooks(books: List<Book>, sort: ShelfSort): List<Book> = when (sort) {
    ShelfSort.TITLE -> books.sortedBy { it.title.lowercase() }
    ShelfSort.AUTHOR -> books.sortedWith(compareBy({ it.author.split(' ').lastOrNull()?.lowercase() ?: "" }, { it.title.lowercase() }))
    ShelfSort.RECENT -> books.sortedByDescending { it.lastOpenedAt }
    ShelfSort.ADDED -> books.sortedByDescending { it.addedAt }
    ShelfSort.PROGRESS -> books.sortedByDescending { it.progress }
}

/** Lädt Bilder verkleinert als ImageBitmap (für Fotos an der Wand und Cover im Regal). */
@Composable
fun rememberImages(paths: List<String>, maxSize: Int): Map<String, ImageBitmap> {
    val state by produceState(emptyMap<String, ImageBitmap>(), paths) {
        value = withContext(Dispatchers.IO) {
            paths.distinct().mapNotNull { p ->
                runCatching {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(p, o)
                    var s = 1
                    while (o.outWidth / s > maxSize || o.outHeight / s > maxSize) s *= 2
                    BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()?.let { p to it }
                }.getOrNull()
            }.toMap()
        }
    }
    return state
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(roomId) { app.ensureDecorated(roomId); app.currentRoomId = roomId }
    val room by app.db.rooms().observeRoom(roomId).collectAsState(initial = null)
    val dbItems by app.db.rooms().observeItems(roomId).collectAsState(initial = emptyList())
    val shelves by app.db.shelves().observeShelves(roomId).collectAsState(initial = emptyList())
    val booksByShelf by remember(roomId) {
        app.db.books().observeBooksOfRoom(roomId).combine(app.db.books().observeLinksOfRoom(roomId)) { books, links ->
            val byId = books.associateBy { it.id }
            links.groupBy { it.shelfId }.mapValues { (_, l) -> l.mapNotNull { byId[it.bookId] }.distinctBy { it.id } }
        }
    }.collectAsState(initial = emptyMap())
    val scans by app.scanner.state.collectAsState()
    val appSettings by app.settings.app.collectAsState()
    val readerSettings by app.settings.reader.collectAsState()

    var editMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RoomTarget?>(null) }
    var dragItem by remember { mutableStateOf<RoomItem?>(null) }
    var dragShelf by remember { mutableStateOf<Shelf?>(null) }
    var closeUp by remember { mutableStateOf<Long?>(null) }
    var opening by remember { mutableStateOf<OpeningBook?>(null) }
    var detail by remember { mutableStateOf<Book?>(null) }
    var catalog by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(appSettings.roomSounds) }
    var effects by remember { mutableStateOf(listOf<RoomEffect>()) }
    var confirmDeleteShelf by remember { mutableStateOf<Shelf?>(null) }
    var pendingPhoto by remember { mutableStateOf<RoomItem?>(null) }
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(shelves.map { it.id }) {
        if (appSettings.autoScan) shelves.filter { it.folderList.isNotEmpty() && System.currentTimeMillis() - it.lastScan > 10 * 60_000 }.forEach { app.scanShelf(it) }
        if (shelves.any { it.posX < 0f }) app.ensureDecorated(roomId)
    }
    LaunchedEffect(Unit) { delay(5000); showHint = false }

    // Klang: Raumklang + Plattenspieler
    val player = remember { AmbientPlayer() }
    val recordOn = dbItems.any { it.type == ItemType.RECORD_PLAYER.name && it.label == "on" }
    LaunchedEffect(soundOn, room?.ambientSound, recordOn) {
        val r = room
        when {
            recordOn -> player.play(AmbientSound.PIANO, 0.45f)
            soundOn && r != null -> player.play(enumOr(r.ambientSound, AmbientSound.NONE), 0.35f)
            else -> player.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val target = pendingPhoto
        pendingPhoto = null
        if (uri != null && target != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "photos").apply { mkdirs() }
                    val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                    f.absolutePath
                }.getOrNull()
            }
            if (path != null) {
                if (target.id == 0L) app.db.rooms().insertItem(target.copy(imagePath = path))
                else app.db.rooms().updateItem(target.copy(imagePath = path))
            }
        }
    }

    val r = room ?: return
    val items = remember(dbItems, dragItem) { dragItem?.let { d -> dbItems.map { if (it.id == d.id) d else it } } ?: dbItems }
    val shelvesShown = remember(shelves, dragShelf) { dragShelf?.let { d -> shelves.map { if (it.id == d.id) d else it } } ?: shelves }
    val sortedBooks = remember(booksByShelf, shelves) { shelves.associate { s -> s.id to sortBooks(booksByShelf[s.id].orEmpty(), enumOr(s.sortOrder, ShelfSort.TITLE)) } }
    val bookcases = remember(shelvesShown, sortedBooks) {
        shelvesShown.map { s -> BookcaseData(s, arrangeBooks(sortedBooks[s.id].orEmpty(), enumOr(s.arrangement, BookArrangement.ALPHA), s.decor, s.id)) }
    }
    val photos = rememberImages(items.mapNotNull { it.imagePath }, 700)
    val coverPaths = remember(bookcases) {
        bookcases.filter { enumOr(it.shelf.arrangement, BookArrangement.ALPHA) == BookArrangement.COVERS }.flatMap { b -> sortedBooks[b.shelf.id].orEmpty().take(60).mapNotNull { it.coverPath } }
    }
    val covers = rememberImages(coverPaths, 260)

    Box(Modifier.fillMaxSize().background(Color(0xFF120C09))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }
            // Bezugshöhe: im Hochformat aus der Breite abgeleitet, damit Möbel nicht riesig wirken
            val roomH = minOf(viewH, viewW * 1.35f)
            val roomTop = (viewH - roomH) * 0.55f
            val roomW = maxOf(r.roomWidth * roomH, viewW)
            val scroll = rememberScrollState()
            val fit = viewW / roomW
            val zoom = remember { Animatable(1f) }
            LaunchedEffect(overview) {
                if (overview) { scroll.animateScrollTo(0); zoom.animateTo(fit, tween(500)) } else zoom.animateTo(1f, tween(450))
            }
            // Beim ersten Öffnen zum ersten Regal schauen
            LaunchedEffect(roomW, shelves.firstOrNull()?.posX) {
                val s = shelves.firstOrNull { it.posX >= 0f } ?: return@LaunchedEffect
                if (scroll.value == 0) scroll.scrollTo(((s.posX * roomW) - viewW / 2).roundToInt().coerceIn(0, scroll.maxValue))
            }
            val currentItems by rememberUpdatedState(items)
            val currentCases by rememberUpdatedState(bookcases)
            val currentEdit by rememberUpdatedState(editMode)
            val currentSelected by rememberUpdatedState(selected)
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll, enabled = !overview && dragItem == null && dragShelf == null)
            ) {
                Box(
                    Modifier
                        .width(with(density) { roomW.toDp() })
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = zoom.value; scaleY = zoom.value
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationY = (viewH - viewH * zoom.value) / 2f
                        }
                        .pointerInput(roomW, roomH) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val target = hitTest(down.position - Offset(0f, roomTop), currentItems, currentCases, roomW, roomH, includeDecals = currentEdit)
                                if (currentEdit && target != null) {
                                    // Auswählen und verschieben/zoomen
                                    selected = target
                                    down.consume()
                                    var moved = false
                                    var lastDist = 0f
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            val d = (pressed[0].position - pressed[1].position).getDistance()
                                            if (lastDist > 0f) {
                                                val f = d / lastDist
                                                when (target) {
                                                    is RoomTarget.Item -> { val base = dragItem ?: target.item; dragItem = base.copy(scale = (base.scale * f).coerceIn(0.3f, 4f)) }
                                                    is RoomTarget.Case -> { val base = dragShelf ?: target.shelf; dragShelf = base.copy(scale = (base.scale * f).coerceIn(0.5f, 2.2f)) }
                                                }
                                            }
                                            lastDist = d
                                            moved = true
                                        } else {
                                            lastDist = 0f
                                            val c = pressed[0]
                                            val delta = c.position - c.previousPosition
                                            if (delta.getDistance() > 0f) moved = true
                                            when (target) {
                                                is RoomTarget.Item -> {
                                                    val base = dragItem ?: target.item
                                                    val type = itemTypeOf(base.type)
                                                    dragItem = base.copy(x = (base.x + delta.x / roomW).coerceIn(0f, 1f), y = clampY(type?.layer, base.y + delta.y / roomH, type?.h ?: 0.1f, base.scale))
                                                }
                                                is RoomTarget.Case -> {
                                                    val base = dragShelf ?: target.shelf
                                                    val st = enumOr(base.style, BookcaseStyle.TALL)
                                                    dragShelf = base.copy(posX = (base.posX + delta.x / roomW).coerceIn(0f, 1f), posY = clampY(if (st.onWall) ItemLayer.WALL else ItemLayer.FLOOR, base.posY + delta.y / roomH, st.h, base.scale))
                                                }
                                            }
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                    val di = dragItem
                                    val ds = dragShelf
                                    if (moved) scope.launch {
                                        di?.let { app.db.rooms().updateItem(it); selected = RoomTarget.Item(it) }
                                        ds?.let { app.db.shelves().update(it); selected = RoomTarget.Case(it) }
                                        dragItem = null; dragShelf = null
                                    } else { dragItem = null; dragShelf = null }
                                } else {
                                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                                    if (currentEdit) { selected = null; return@awaitEachGesture }
                                    when (target) {
                                        is RoomTarget.Case -> closeUp = target.shelf.id
                                        is RoomTarget.Item -> scope.launch {
                                            val it = target.item
                                            val type = itemTypeOf(it.type)
                                            when {
                                                type == ItemType.CAT || type == ItemType.DOG -> {
                                                    val rect = itemRect(it, roomW, roomH) ?: return@launch
                                                    effects = effects.filter { e -> System.nanoTime() - e.startNanos < 2_000_000_000L } + RoomEffect(Offset(rect.center.x / roomW, rect.top / roomH), System.nanoTime())
                                                }
                                                type == ItemType.RECORD_PLAYER -> app.db.rooms().updateItem(it.copy(label = if (it.label == "on") null else "on"))
                                                type == ItemType.WINDOW -> {
                                                    val next = Weather.entries[(enumOr(r.weather, Weather.CLEAR).ordinal + 1) % Weather.entries.size]
                                                    app.db.rooms().update(r.copy(weather = next.name))
                                                    android.widget.Toast.makeText(context, "Draußen: ${next.label}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                (type?.light ?: 0f) > 0f -> app.db.rooms().updateItem(it.copy(label = if (it.isOn) "off" else null))
                                                type == ItemType.STRING_LIGHTS -> app.db.rooms().updateItem(it.copy(label = if (it.isOn) "off" else null))
                                            }
                                        }
                                        null -> Unit
                                    }
                                }
                            }
                        }
                ) {
                    RoomCanvas(
                        room = r,
                        items = items,
                        bookcases = bookcases,
                        modifier = Modifier.fillMaxSize(),
                        animate = appSettings.roomAnimations,
                        images = photos,
                        covers = covers,
                        selected = if (editMode) selected?.let { sel ->
                            when (sel) {
                                is RoomTarget.Item -> RoomTarget.Item(items.firstOrNull { it.id == sel.item.id } ?: sel.item)
                                is RoomTarget.Case -> RoomTarget.Case(shelvesShown.firstOrNull { it.id == sel.shelf.id } ?: sel.shelf)
                            }
                        } else null,
                        effects = effects,
                        refHeight = roomH,
                    )
                }
            }
            // Hinweis zum Umschauen
            AnimatedVisibility(showHint && roomW > viewW * 1.1f && !editMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    Text(
                        "← Wische, um dich umzusehen →\nTippe auf ein Regal, um die Bücher anzusehen",
                        style = TextStyle(fontFamily = Fonts.Lora, fontSize = 14.sp, color = Color(0xFFF6E7C4), textAlign = TextAlign.Center),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            // Regale-Schnellzugriff
            if (!editMode && shelves.isNotEmpty()) {
                LazyRow(
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shelves, key = { it.id }) { s ->
                        val count = booksByShelf[s.id]?.size ?: 0
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    if (overview) overview = false
                                    if (s.posX >= 0f) scroll.animateScrollTo(((s.posX * roomW) - viewW / 2).roundToInt().coerceIn(0, scroll.maxValue))
                                    closeUp = s.id
                                }
                            },
                            label = { Text("${s.name} · $count") },
                            leadingIcon = { Box(Modifier.size(12.dp).background(Color(enumOr(s.wood, Wood.WALNUT).base), CircleShape)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xE62A1E17), labelColor = Color(0xFFF2E3C6)),
                            border = null,
                        )
                    }
                }
            }
        }

        // Kopfzeile
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFF6E7C4))
            IconButton(onClick = { if (editMode) { editMode = false; selected = null } else onBack() }, colors = iconColors) {
                Icon(if (editMode) Icons.Outlined.Check else Icons.AutoMirrored.Filled.ArrowBack, if (editMode) "Fertig" else "Zurück")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (editMode) "Einrichten" else r.name,
                    style = TextStyle(fontFamily = Fonts.Playfair, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Color(0xFFF6E7C4), shadow = Shadow(Color.Black, blurRadius = 8f)),
                )
                val total = booksByShelf.values.sumOf { it.size }
                Text(
                    if (editMode) "Ziehen zum Verschieben · zwei Finger zum Skalieren" else "${shelves.size} ${if (shelves.size == 1) "Regal" else "Regale"} · $total ${if (total == 1) "Buch" else "Bücher"}",
                    style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = Color(0xFFE8D6B4), shadow = Shadow(Color.Black, blurRadius = 6f)),
                )
            }
            if (!editMode) {
                IconButton(onClick = { overview = !overview }, colors = iconColors) { Icon(if (overview) Icons.Outlined.ZoomInMap else Icons.Outlined.ZoomOutMap, if (overview) "Hineingehen" else "Übersicht") }
                IconButton(onClick = { soundOn = !soundOn }, colors = iconColors) { Icon(if (soundOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, "Raumklang") }
                IconButton(onClick = { editMode = true; overview = false }, colors = iconColors) { Icon(Icons.Outlined.Brush, "Einrichten") }
            } else {
                TextButton(onClick = onEditRoom) { Text("Wände & Boden", color = Color(0xFFF6E7C4)) }
            }
        }

        // Werkzeuge im Einrichtungsmodus
        if (editMode) {
            Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), horizontalAlignment = Alignment.End) {
                val sel = selected
                when (sel) {
                    is RoomTarget.Item -> {
                        val item = items.firstOrNull { it.id == sel.item.id }
                        if (item != null) ItemToolbar(
                            item = item,
                            onChange = { scope.launch { app.db.rooms().updateItem(it) }; selected = RoomTarget.Item(it) },
                            onDelete = { scope.launch { app.db.rooms().deleteItem(item) }; selected = null },
                            onPhoto = { pendingPhoto = item; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onFront = { scope.launch { val maxZ = (items.maxOfOrNull { it.z } ?: 0) + 1; app.db.rooms().updateItem(item.copy(z = maxZ)) } },
                            onBack = { scope.launch { val minZ = (items.minOfOrNull { it.z } ?: 0) - 1; app.db.rooms().updateItem(item.copy(z = minZ)) } },
                        )
                    }
                    is RoomTarget.Case -> {
                        val shelf = shelves.firstOrNull { it.id == sel.shelf.id }
                        if (shelf != null) ShelfToolbar(
                            shelf = shelf,
                            onChange = { scope.launch { app.db.shelves().update(it) }; selected = RoomTarget.Case(it) },
                            onSettings = { onEditShelf(shelf.id) },
                            onDelete = { confirmDeleteShelf = shelf },
                        )
                    }
                    null -> Unit
                }
                Spacer(Modifier.height(10.dp))
                ExtendedFloatingActionButton(onClick = { catalog = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Hinzufügen") })
            }
        }

        // Regal-Nahansicht
        val closeShelf = shelves.firstOrNull { it.id == closeUp }
        AnimatedVisibility(
            visible = closeShelf != null,
            enter = fadeIn(tween(250)) + scaleIn(tween(380), initialScale = 0.6f),
            exit = fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.7f),
        ) {
            val s = closeShelf ?: shelves.firstOrNull() ?: return@AnimatedVisibility
            BookcaseCloseUp(
                shelf = s,
                books = sortedBooks[s.id].orEmpty(),
                scan = scans[s.id],
                onClose = { closeUp = null },
                onBookClick = { b, rect -> if (appSettings.bookAnimations && rect.width > 0) opening = OpeningBook(b, rect) else onOpenBook(b) },
                onBookLongClick = { detail = it },
                onArrangement = { a -> scope.launch { app.db.shelves().update(s.copy(arrangement = a.name)) } },
                onSettings = { onEditShelf(s.id) },
                onScan = { app.scanShelf(s) },
            )
        }

        opening?.let { o -> BookOpeningOverlay(o, Color(effectiveTheme(readerSettings).bg)) { onOpenBook(o.book) } }
    }
    LaunchedEffect(opening) { if (opening != null) { delay(2500); opening = null } }
    LaunchedEffect(effects) { if (effects.isNotEmpty()) { delay(2000); effects = effects.filter { System.nanoTime() - it.startNanos < 1_900_000_000L } } }
    detail?.let { b -> BookDetailSheet(bookId = b.id, onDismiss = { detail = null }, onRead = { book -> detail = null; onOpenBook(book) }) }

    if (catalog) CatalogSheet(
        room = r,
        onDismiss = { catalog = false },
        onAdd = { type, variant ->
            catalog = false
            scope.launch {
                val maxZ = (items.maxOfOrNull { it.z } ?: 0) + 1
                val y = when (type.layer) {
                    ItemLayer.WALL -> if (type == ItemType.WINDOW) 0.56f else if (type == ItemType.STRING_LIGHTS) 0.12f else 0.42f
                    ItemLayer.CEILING -> 0.3f
                    ItemLayer.DECAL -> 0.95f
                    ItemLayer.FLOOR -> 0.84f
                }
                val item = RoomItem(roomId = roomId, type = type.name, x = 0.5f, y = y, z = maxZ, variant = variant)
                if (type == ItemType.PHOTO) {
                    pendingPhoto = item
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    val id = app.db.rooms().insertItem(item)
                    selected = RoomTarget.Item(item.copy(id = id))
                }
            }
        },
        onAddShelf = { style ->
            catalog = false
            scope.launch {
                val id = app.db.shelves().insert(
                    Shelf(roomId = roomId, name = "Neues Regal", wood = Wood.entries[shelves.size % Wood.entries.size].name, fileTypes = DefaultFileTypes, sortIndex = shelves.size, style = style.name, posX = 0.5f, posY = if (style.onWall) 0.5f else FLOOR_Y + 0.06f)
                )
                onEditShelf(id)
            }
        },
    )
    confirmDeleteShelf?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDeleteShelf = null },
            title = { Text("Regal entfernen?") },
            text = { Text("„${s.name}“ verschwindet aus dem Zimmer. Deine Dateien, Notizen und Lieblingsstellen bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.db.shelves().delete(s) }; selected = null; confirmDeleteShelf = null }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmDeleteShelf = null }) { Text("Behalten") } },
        )
    }
}

private fun clampY(layer: ItemLayer?, y: Float, h: Float, scale: Float): Float = when (layer) {
    ItemLayer.WALL -> y.coerceIn(h * scale + 0.03f, FLOOR_Y - 0.02f)
    ItemLayer.CEILING -> y.coerceIn(h * scale + 0.04f, 0.65f)
    ItemLayer.DECAL -> y.coerceIn(FLOOR_Y + 0.08f, 1.05f)
    else -> y.coerceIn(FLOOR_Y + 0.015f, 1.0f)
}

@Composable
private fun ItemToolbar(
    item: RoomItem,
    onChange: (RoomItem) -> Unit,
    onDelete: () -> Unit,
    onPhoto: () -> Unit,
    onFront: () -> Unit,
    onBack: () -> Unit,
) {
    val type = itemTypeOf(item.type) ?: return
    var colors by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 10.dp) {
        Column(Modifier.padding(8.dp)) {
            Text(type.label + (type.variants.getOrNull(item.variant)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(item.copy(scale = (item.scale / 1.12f).coerceAtLeast(0.3f))) }) { Icon(Icons.Outlined.Remove, "Kleiner") }
                IconButton(onClick = { onChange(item.copy(scale = (item.scale * 1.12f).coerceAtMost(4f))) }) { Icon(Icons.Outlined.Add, "Größer") }
                IconButton(onClick = { onChange(item.copy(flip = !item.flip)) }) { Icon(Icons.Outlined.Flip, "Spiegeln") }
                IconButton(onClick = onBack) { Icon(Icons.Outlined.FlipToBack, "Nach hinten") }
                IconButton(onClick = onFront) { Icon(Icons.Outlined.FlipToFront, "Nach vorn") }
                if (type.variants.size > 1) IconButton(onClick = { onChange(item.copy(variant = (item.variant + 1) % type.variants.size)) }) { Icon(Icons.Outlined.Style, "Variante") }
                if (type.tintable) IconButton(onClick = { colors = !colors }) { Icon(Icons.Outlined.Palette, "Farbe") }
                if (type.light > 0f || type == ItemType.STRING_LIGHTS) IconButton(onClick = { onChange(item.copy(label = if (item.isOn) "off" else null)) }) { Icon(Icons.Outlined.Lightbulb, "Licht an/aus") }
                if (type == ItemType.PHOTO) IconButton(onClick = onPhoto) { Icon(Icons.Outlined.Image, "Bild wählen") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Entfernen") }
            }
            if (colors) Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf(0) + CurtainPalette).forEach { c ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (c == 0) Brush.linearGradient(listOf(Color.White, Color.Gray)) else Brush.linearGradient(listOf(Color(c), Color(c))))
                            .border(if (item.color == c) 3.dp else 1.dp, if (item.color == c) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f), CircleShape)
                            .clickable { onChange(item.copy(color = c)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfToolbar(shelf: Shelf, onChange: (Shelf) -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    var styleMenu by remember { mutableStateOf(false) }
    var arrMenu by remember { mutableStateOf(false) }
    var woodMenu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 10.dp) {
        Column(Modifier.padding(8.dp)) {
            Text("${shelf.name} · ${enumOr(shelf.style, BookcaseStyle.TALL).label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(shelf.copy(scale = (shelf.scale / 1.1f).coerceAtLeast(0.5f))) }) { Icon(Icons.Outlined.Remove, "Kleiner") }
                IconButton(onClick = { onChange(shelf.copy(scale = (shelf.scale * 1.1f).coerceAtMost(2.2f))) }) { Icon(Icons.Outlined.Add, "Größer") }
                Box {
                    TextButton(onClick = { styleMenu = true }) { Text("Bauform") }
                    DropdownMenu(styleMenu, { styleMenu = false }) {
                        BookcaseStyle.entries.forEach { st ->
                            DropdownMenuItem(text = { Text(st.label) }, onClick = {
                                styleMenu = false
                                val y = if (st.onWall) shelf.posY.coerceAtMost(FLOOR_Y - 0.03f).coerceAtLeast(st.h + 0.05f) else shelf.posY.coerceAtLeast(FLOOR_Y + 0.02f)
                                onChange(shelf.copy(style = st.name, posY = y))
                            })
                        }
                    }
                }
                Box {
                    TextButton(onClick = { arrMenu = true }) { Text("Anordnung") }
                    DropdownMenu(arrMenu, { arrMenu = false }) {
                        BookArrangement.entries.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { arrMenu = false; onChange(shelf.copy(arrangement = a.name)) }) }
                        DropdownMenuItem(text = { Text(if (shelf.decor) "Deko ausblenden" else "Deko anzeigen") }, onClick = { arrMenu = false; onChange(shelf.copy(decor = !shelf.decor)) })
                    }
                }
                Box {
                    TextButton(onClick = { woodMenu = true }) { Text("Holz") }
                    DropdownMenu(woodMenu, { woodMenu = false }) {
                        Wood.entries.forEach { w -> DropdownMenuItem(text = { Text(w.label) }, leadingIcon = { Box(Modifier.size(16.dp).background(Color(w.base), CircleShape)) }, onClick = { woodMenu = false; onChange(shelf.copy(wood = w.name)) }) }
                    }
                }
            }
            Row {
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, "Ordner & Dateitypen") }
                Text("Ordner & Dateitypen", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically).clickable(onClick = onSettings))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Regal entfernen") }
            }
        }
    }
}

@Composable
private fun CatalogSheet(room: com.lesehaus.app.data.db.ReadingRoom, onDismiss: () -> Unit, onAdd: (ItemType, Int) -> Unit, onAddShelf: (BookcaseStyle) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Regale") + ItemCategory.entries.map { it.label }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text("Einrichtung hinzufügen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(430.dp)) {
            if (tab == 0) {
                items(BookcaseStyle.entries) { st ->
                    CatalogCell(st.label, onClick = { onAddShelf(st) }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val s = minOf(size.width / st.w, size.height / st.h) * 0.9f
                            val shelf = Shelf(roomId = 0, name = "", wood = Wood.WALNUT.name, fileTypes = "", style = st.name, posX = 0.5f, posY = 1f, scale = s / size.height)
                            val rr = bookcaseRect(shelf, size.width, size.height)
                            val demo = (1..40).map { i -> Book(id = i.toLong(), uri = "", fileName = "", format = "", title = "Buch $i", fileSize = (i * 37_000L) % 900_000 + 50_000) }
                            drawBookcase(shelf, rr, arrangeBooks(demo, BookArrangement.MIXED, true, 3), emptyMap())
                        }
                    }
                }
            } else {
                val cat = ItemCategory.entries[tab - 1]
                val list = ItemType.entries.filter { it.category == cat }.flatMap { t -> if (t.variants.size > 1 && t != ItemType.PHOTO) t.variants.indices.map { t to it } else listOf(t to 0) }
                items(list) { (t, v) ->
                    CatalogCell(t.label + (if (t.variants.size > 1 && t != ItemType.PHOTO) "\n" + t.variants[v] else ""), onClick = { onAdd(t, v) }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val s = minOf(size.width / t.w, size.height / t.h) * 0.8f
                            val item = RoomItem(roomId = 0, type = t.name, x = 0.5f, y = 0.9f, scale = s / size.height, variant = v)
                            val rr = itemRect(t, 0.5f, (size.height + t.h * s) / 2f / size.height, s / size.height, size.width, size.height)
                            val ctx = DrawCtx(room, size.height)
                            drawItemStatic(item, t, rr, ctx)
                            if (t.animated) drawItemAnimated(item, t, rr, 1.3f, ctx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCell(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF6E4A35), Color(0xFF3F2A1E))))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { content() }
        Text(label, style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 12.sp, color = Color(0xFFF2E3C6), textAlign = TextAlign.Center), maxLines = 2)
    }
}
