package com.lesehaus.app.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesehaus.app.AppContainer
import com.lesehaus.app.MainActivity
import com.lesehaus.app.app
import com.lesehaus.app.data.PageMode
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Bookmark
import com.lesehaus.app.data.db.Favorite
import com.lesehaus.app.data.db.ReadingSession
import com.lesehaus.app.ui.theme.HandwritingStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class PdfFilter(val label: String) { NONE("Original"), SEPIA("Sepia"), NIGHT("Nacht") }

class PdfViewModel(private val app: AppContainer, val bookId: Long, private val requestedPage: Int = -1) : ViewModel() {
    private val db = app.db
    val book: StateFlow<Book?> = db.books().observeBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bookmarks: StateFlow<List<Bookmark>> = db.annotations().observeBookmarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val annotations: StateFlow<List<Annotation>> = db.annotations().observeAnnotations(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val renderThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    var pageCount by mutableIntStateOf(0)
        private set
    var aspect = HashMap<Int, Float>()
    var error by mutableStateOf<String?>(null)
        private set
    var startPage by mutableIntStateOf(-1)
        private set
    var currentPage by mutableIntStateOf(0)
    /** Sprungwunsch (z. B. aus der Notizliste), -1 = keiner. */
    var jumpSignal by mutableIntStateOf(-1)
    fun startPageJump(page: Int) { jumpSignal = page }
    private var sessionStart = System.currentTimeMillis()
    private var pagesTurned = 0

    init {
        viewModelScope.launch {
            val b = db.books().book(bookId) ?: run { error = "Buch nicht gefunden"; return@launch }
            withContext(renderThread) {
                runCatching {
                    pfd = app.context.contentResolver.openFileDescriptor(Uri.parse(b.uri), "r")
                    renderer = PdfRenderer(pfd!!)
                    val r = renderer!!
                    for (i in 0 until r.pageCount) r.openPage(i).use { p -> aspect[i] = p.width.toFloat() / p.height }
                    r.pageCount
                }.onSuccess { pageCount = it }.onFailure { error = "PDF konnte nicht geöffnet werden (${it.message})" }
            }
            startPage = (if (requestedPage >= 0) requestedPage else b.pdfPage).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            currentPage = startPage
        }
    }

    suspend fun render(page: Int, width: Int): Bitmap? {
        val key = "$page@$width"
        cache.get(key)?.let { return it }
        return withContext(renderThread) {
            cache.get(key) ?: runCatching {
                val r = renderer ?: return@runCatching null
                r.openPage(page).use { p ->
                    val h = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp)
                    bmp
                }
            }.getOrNull()
        }
    }

    fun onPage(i: Int) {
        if (i != currentPage) pagesTurned++
        currentPage = i
        viewModelScope.launch {
            val prog = if (pageCount <= 1) 1f else i / (pageCount - 1f)
            db.books().updatePosition(bookId, 0, 0, i, prog, pageCount, System.currentTimeMillis())
        }
    }

    fun toggleBookmark(i: Int) = viewModelScope.launch {
        val existing = bookmarks.value.firstOrNull { it.pdfPage == i }
        if (existing != null) db.annotations().deleteBookmark(existing)
        else db.annotations().insertBookmark(Bookmark(bookId = bookId, chapter = -1, charOffset = 0, pdfPage = i, label = "Seite ${i + 1}", progress = if (pageCount > 1) i / (pageCount - 1f) else 0f))
    }

    fun saveNote(i: Int, note: String, existing: Annotation?) = viewModelScope.launch {
        if (existing != null) {
            if (note.isBlank()) db.annotations().deleteAnnotation(existing) else db.annotations().updateAnnotation(existing.copy(note = note, updatedAt = System.currentTimeMillis()))
        } else if (note.isNotBlank()) {
            db.annotations().insertAnnotation(Annotation(bookId = bookId, chapter = -1, start = 0, end = 0, text = "Seite ${i + 1}", color = 0xFFFFE27A.toInt(), style = "NOTE", note = note, pdfPage = i))
        }
    }

    fun deleteAnnotation(a: Annotation) = viewModelScope.launch { db.annotations().deleteAnnotation(a) }

    fun favoritePage(i: Int) = viewModelScope.launch {
        val b = book.value ?: return@launch
        val text = withContext(Dispatchers.IO) {
            runCatching {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app.context)
                app.context.contentResolver.openInputStream(Uri.parse(b.uri))?.use { input ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(input, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        com.tom_roush.pdfbox.text.PDFTextStripper().apply { startPage = i + 1; endPage = i + 1 }.getText(doc).trim()
                    }
                }
            }.getOrNull()
        }
        db.annotations().insertFavorite(
            Favorite(bookId = bookId, bookTitle = b.title, bookAuthor = b.author, chapter = -1, start = 0, end = 0, text = text?.takeIf { it.isNotBlank() } ?: "Seite ${i + 1}", kind = "PAGE", pdfPage = i)
        )
    }

    fun onPause() {
        val dur = System.currentTimeMillis() - sessionStart
        if (dur > 20_000) {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sessionStart))
            val p = pagesTurned
            app.scope.launch {
                db.stats().insert(ReadingSession(bookId = bookId, day = day, startedAt = sessionStart, durationMs = dur, pages = p))
                db.books().addReadingTime(bookId, dur)
            }
        }
        sessionStart = System.currentTimeMillis(); pagesTurned = 0
    }

    fun onResume() { sessionStart = System.currentTimeMillis(); pagesTurned = 0 }

    override fun onCleared() {
        onPause()
        val r = renderer
        val f = pfd
        renderer = null
        app.scope.launch(renderThread) {
            runCatching { r?.close(); f?.close() }
        }.invokeOnCompletion { renderThread.close() }
        cache.evictAll()
    }
}

@Composable
fun PdfReaderScreen(bookId: Long, onBack: () -> Unit, onTextMode: () -> Unit, startPage: Int = -1) {
    val context = LocalContext.current
    val container = context.app
    val vm: PdfViewModel = viewModel(key = "pdf_${bookId}_$startPage") { PdfViewModel(container, bookId, startPage) }
    val s by container.settings.reader.collectAsState()
    val book by vm.book.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val annotations by vm.annotations.collectAsState()
    var controls by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(if (effectiveTheme(s).dark) PdfFilter.NIGHT else PdfFilter.NONE) }
    var notesSheet by remember { mutableStateOf(false) }
    var noteEditor by remember { mutableStateOf<Pair<Int, Annotation?>?>(null) }
    var vertical by remember { mutableStateOf(s.pageMode == PageMode.SCROLL) }
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    DisposableEffect(s.keepScreenOn) {
        if (s.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) vm.onPause()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val colorFilter = when (filter) {
        PdfFilter.NONE -> null
        PdfFilter.SEPIA -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.95f, 0.05f, 0f, 0f, 6f,
            0f, 0.88f, 0.04f, 0f, -4f,
            0f, 0f, 0.72f, 0f, -16f,
            0f, 0f, 0f, 1f, 0f,
        )))
        PdfFilter.NIGHT -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -0.85f, 0f, 0f, 0f, 235f,
            0f, -0.85f, 0f, 0f, 225f,
            0f, 0f, -0.85f, 0f, 205f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    val bg = when (filter) { PdfFilter.NIGHT -> Color(0xFF15110E); PdfFilter.SEPIA -> Color(0xFFE9DCC2); else -> Color(0xFF3A332D) }

    Box(Modifier.fillMaxSize().background(bg)) {
        when {
            vm.error != null -> Text(vm.error ?: "", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            vm.pageCount == 0 || vm.startPage < 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> {
                val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
                var navigate by remember { mutableStateOf<(Int) -> Unit>({}) }
                val nav by rememberUpdatedState(navigate)
                DisposableEffect(s.volumeKeys) {
                    val main = activity as? MainActivity
                    main?.keyHandler = { ev ->
                        if (s.volumeKeys && (ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
                            if (ev.action == KeyEvent.ACTION_DOWN) nav(if (ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
                            true
                        } else false
                    }
                    onDispose { main?.keyHandler = null }
                }
                if (vertical) {
                    val listState = rememberLazyListState(vm.startPage)
                    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect { vm.onPage(it) } }
                    LaunchedEffect(Unit) { navigate = { d -> scope.launch { listState.animateScrollToItem((vm.currentPage + d).coerceIn(0, vm.pageCount - 1)) } } }
                    ZoomBox(onTap = { controls = !controls }) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(vm.pageCount) { i ->
                                PdfPage(vm, i, widthPx, colorFilter, Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                    SliderJump(vm, controls) { scope.launch { listState.scrollToItem(it) } }
                } else {
                    val pager = rememberPagerState(vm.startPage) { vm.pageCount }
                    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { vm.onPage(it) } }
                    LaunchedEffect(Unit) { navigate = { d -> scope.launch { pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, vm.pageCount - 1)) } } }
                    HorizontalPager(pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { i ->
                        ZoomBox(onTap = { pos, w ->
                            when {
                                pos < w * 0.25f -> navigate(-1)
                                pos > w * 0.75f -> navigate(1)
                                else -> controls = !controls
                            }
                        }) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PdfPage(vm, i, widthPx, colorFilter, Modifier)
                            }
                        }
                    }
                    SliderJump(vm, controls) { scope.launch { pager.scrollToPage(it) } }
                }
            }
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f), shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    Column(Modifier.weight(1f)) {
                        Text(book?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Seite ${vm.currentPage + 1} von ${vm.pageCount}", style = MaterialTheme.typography.bodySmall)
                    }
                    val marked = bookmarks.any { it.pdfPage == vm.currentPage }
                    IconButton(onClick = { vm.toggleBookmark(vm.currentPage) }) {
                        Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Lesezeichen", tint = if (marked) Color(0xFF9B2F3F) else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { noteEditor = vm.currentPage to annotations.firstOrNull { it.pdfPage == vm.currentPage } }) { Icon(Icons.Outlined.EditNote, "Notiz zur Seite") }
                    IconButton(onClick = { vm.favoritePage(vm.currentPage); android.widget.Toast.makeText(context, "Seite in Lieblingsstellen gespeichert", android.widget.Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Outlined.FavoriteBorder, "Seite gefällt mir", tint = Color(0xFFC0392B))
                    }
                    IconButton(onClick = { filter = PdfFilter.entries[(filter.ordinal + 1) % PdfFilter.entries.size] }) { Icon(Icons.Outlined.Contrast, "Filter: ${filter.label}") }
                    IconButton(onClick = { vertical = !vertical }) { Icon(Icons.Outlined.ViewDay, "Ansicht wechseln") }
                    IconButton(onClick = { notesSheet = true }) { Icon(Icons.Filled.Favorite, "Lesezeichen & Notizen", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onTextMode) { Icon(Icons.Outlined.TextSnippet, "Textmodus") }
                }
            }
        }
    }

    if (notesSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { notesSheet = false }, sheetState = state) {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                item { Text("Lesezeichen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                if (bookmarks.isEmpty()) item { EmptyHint("Noch keine Lesezeichen.") }
                items(bookmarks, key = { "b${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF9B2F3F))
                        Text(b.label, modifier = Modifier.weight(1f).padding(start = 12.dp).pointerInput(b.id) { detectTapGestures { vm.startPageJump(b.pdfPage); notesSheet = false } })
                        IconButton(onClick = { vm.toggleBookmark(b.pdfPage) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text("Notizen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                val notes = annotations.filter { it.pdfPage >= 0 }
                if (notes.isEmpty()) item { EmptyHint("Noch keine Notizen zu Seiten.") }
                items(notes, key = { "a${it.id}" }) { a ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).pointerInput(a.id) { detectTapGestures { vm.startPageJump(a.pdfPage); notesSheet = false } }) {
                        Text("Seite ${a.pdfPage + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(a.note ?: "", style = HandwritingStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
    noteEditor?.let { (page, existing) ->
        NoteDialog(
            initial = existing?.note ?: "",
            quote = "Seite ${page + 1}",
            onDismiss = { noteEditor = null },
            onSave = { vm.saveNote(page, it, existing); noteEditor = null },
        )
    }
}

@Composable
private fun SliderJump(vm: PdfViewModel, visible: Boolean, onJump: (Int) -> Unit) {
    val signal = vm.jumpSignal
    LaunchedEffect(signal) { if (signal >= 0) { onJump(signal); vm.jumpSignal = -1 } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible && vm.pageCount > 1, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f), shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
                var pos by remember(vm.currentPage) { mutableFloatStateOf(vm.currentPage.toFloat()) }
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("Seite ${pos.roundToInt() + 1} / ${vm.pageCount}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Slider(value = pos, onValueChange = { pos = it }, onValueChangeFinished = { onJump(pos.roundToInt()) }, valueRange = 0f..(vm.pageCount - 1).toFloat())
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        listOf(-50, -20, -10, -5, 5, 10, 20, 50).forEach { n ->
                            val target = vm.currentPage + n
                            androidx.compose.material3.AssistChip(onClick = { onJump(target.coerceIn(0, vm.pageCount - 1)) }, enabled = target in 0 until vm.pageCount, label = { Text(if (n > 0) "+$n" else "$n") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(vm: PdfViewModel, index: Int, widthPx: Int, filter: ColorFilter?, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, index, widthPx) { value = vm.render(index, (widthPx * 1.5f).toInt().coerceAtMost(2400)) }
    val ratio = vm.aspect[index] ?: 0.7f
    Box(modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White.copy(alpha = if (filter == null) 1f else 0.1f)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = "Seite ${index + 1}", colorFilter = filter, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
        else CircularProgressIndicator(Modifier.width(28.dp).height(28.dp))
    }
}

/** Zoom & Verschieben per Zwei-Finger-Geste, Doppeltippen zum Zoomen. */
@Composable
private fun ZoomBox(onTap: (Float, Float) -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.2f },
                    onTap = { onTap(it.x, size.width.toFloat()) },
                )
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
    ) { content() }
}

@Composable
private fun ZoomBox(onTap: () -> Unit, content: @Composable () -> Unit) = ZoomBox({ _, _ -> onTap() }, content)
