package com.lesehaus.app.ui.reader

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lesehaus.app.MainActivity
import com.lesehaus.app.app
import com.lesehaus.app.data.PageAnimation
import com.lesehaus.app.data.PageMode
import com.lesehaus.app.data.ReaderSettings
import com.lesehaus.app.data.ReaderTheme
import com.lesehaus.app.data.formats.BlockKind
import com.lesehaus.app.ui.theme.Fonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

fun effectiveTheme(s: ReaderSettings): ReaderTheme {
    if (!s.autoNightTheme) return s.theme
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (h >= 20 || h < 7) s.nightTheme else s.theme
}

/** Feine Papierstruktur als kachelbares Bild. */
@Composable
fun rememberPaperBrush(dark: Boolean): ShaderBrush = remember(dark) {
    val size = 160
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = Random(17)
    for (x in 0 until size) for (y in 0 until size) {
        val n = rnd.nextFloat()
        val a = (n * if (dark) 14 else 22).toInt()
        val c = if (dark) 255 else 60
        bmp.setPixel(x, y, android.graphics.Color.argb(a, c, (c * 0.9).toInt(), (c * 0.7).toInt()))
    }
    // ein paar Fasern
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply { strokeWidth = 1f; isAntiAlias = true }
    repeat(14) {
        paint.color = android.graphics.Color.argb(if (dark) 10 else 16, 120, 90, 50)
        val x = rnd.nextFloat() * size
        val y = rnd.nextFloat() * size
        canvas.drawLine(x, y, x + rnd.nextFloat() * 14 - 7, y + rnd.nextFloat() * 6 - 3, paint)
    }
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

@Composable
fun ReaderScreen(
    bookId: Long,
    pdfAsText: Boolean,
    onBack: () -> Unit,
    onOpenPdfOriginal: () -> Unit,
    jumpChapter: Int = -1,
    jumpOffset: Int = 0,
    jumpEnd: Int = -1,
) {
    val context = LocalContext.current
    val container = context.app
    val vm: ReaderViewModel = viewModel(key = "reader_${bookId}_${pdfAsText}_${jumpChapter}_$jumpOffset") { ReaderViewModel(container, bookId, pdfAsText, jumpChapter, jumpOffset, jumpEnd) }
    val s by vm.settings.collectAsState()
    val appSettings by container.settings.app.collectAsState()
    val book by vm.book.collectAsState()
    val annotations by vm.annotations.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val ttsSpeaking by vm.tts.speaking.collectAsState()
    val ttsRange by vm.tts.range.collectAsState()
    val theme = effectiveTheme(s)
    val bg = Color(theme.bg)
    val fg = Color(theme.fg)
    val accent = Color(theme.accent)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var controlsVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var annotationPopup by remember { mutableStateOf<com.lesehaus.app.data.db.Annotation?>(null) }
    var favoriteDialog by remember { mutableStateOf<Pair<Selection, String>?>(null) }
    var noteDialog by remember { mutableStateOf<NoteTarget?>(null) }

    // ---------- Fenster: Helligkeit, Display an, Vollbild, Lautstärketasten ----------
    val activity = context as? Activity
    val view = LocalView.current
    DisposableEffect(s.brightness, s.keepScreenOn) {
        val w = activity?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = if (s.brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else s.brightness.coerceIn(0.01f, 1f) }
        if (s.keepScreenOn) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            w?.attributes = w?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(controlsVisible, s.fullscreen, theme) {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.isAppearanceLightStatusBars = !theme.dark
            c.isAppearanceLightNavigationBars = !theme.dark
            if (s.fullscreen && !controlsVisible) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (w != null) WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(s.ambientSound, s.ambientVolume, appSettings.roomAmbienceInReader) {
        // Ohne eigene Klangkulisse spielt leise der Klang des Zimmers, aus dem das Buch stammt
        val roomSound = if (s.ambientSound == com.lesehaus.app.data.AmbientSound.NONE && appSettings.roomAmbienceInReader && container.currentRoomId >= 0)
            container.db.rooms().room(container.currentRoomId)?.let { com.lesehaus.app.data.enumOr(it.ambientSound, com.lesehaus.app.data.AmbientSound.NONE) }
        else null
        if (roomSound != null && roomSound != com.lesehaus.app.data.AmbientSound.NONE) vm.ambient.play(roomSound, s.ambientVolume * 0.6f)
        else vm.ambient.play(s.ambientSound, s.ambientVolume)
    }
    val ttsError by vm.tts.error.collectAsState()
    LaunchedEffect(ttsError) {
        ttsError?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); vm.tts.clearError() }
    }
    DisposableEffect(Unit) { onDispose { vm.ambient.stop(); vm.tts.stop() } }
    LaunchedEffect(vm.flash) {
        if (vm.flash != null) {
            snapshotFlow { vm.pages.isNotEmpty() && !vm.paginating }.first { it }
            delay(3000)
            vm.flash = null
        }
    }

    // Navigation (von Tippzonen und Lautstärketasten genutzt)
    var navigate by remember { mutableStateOf<(Int) -> Unit>({}) }
    val currentNavigate by rememberUpdatedState(navigate)
    DisposableEffect(s.volumeKeys) {
        val main = activity as? MainActivity
        main?.keyHandler = { ev ->
            if (s.volumeKeys && (ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP) && sheet == null) {
                if (ev.action == KeyEvent.ACTION_DOWN) currentNavigate(if (ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
                true
            } else false
        }
        onDispose { main?.keyHandler = null }
    }

    val paper = rememberPaperBrush(theme.dark)

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        when (val ls = vm.loadState) {
            LoadState.Loading -> BookLoading(fg, accent, "Buch wird aufgeschlagen …", null)
            is LoadState.Error -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hoppla", style = TextStyle(fontFamily = Fonts.Playfair, fontSize = 28.sp, color = fg))
                Spacer(Modifier.height(8.dp))
                Text(ls.message, style = TextStyle(fontFamily = Fonts.Lora, fontSize = 16.sp, color = fg.copy(alpha = 0.8f)))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Zurück zum Regal") }
            }
            LoadState.Ready -> {
                val insets = if (s.fullscreen) WindowInsets.displayCutout else WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(insets)
                ) {
                    val density = LocalDensity.current
                    val headerH = if (s.showHeader) 26.dp else 6.dp
                    val footerH = if (s.showFooter) 26.dp else 6.dp
                    val textW = maxWidth - (s.marginH * 2).dp
                    val textH = maxHeight - (s.marginV * 2).dp - headerH - footerH
                    val wPx = with(density) { textW.roundToPx() }
                    val hPx = with(density) { textH.roundToPx() }
                    LaunchedEffect(s, wPx, hPx, fg, density, appSettings.customFonts) {
                        vm.paginate(context, s, wPx, hPx, density, fg, accent)
                    }
                    val ts = vm.typeSetter
                    if (ts != null && vm.pages.isNotEmpty()) {
                        val pageDeco = PageDecorations(
                            annotations = annotations,
                            selection = vm.selection,
                            search = vm.activeSearch,
                            tts = ttsRange,
                            flash = vm.flash,
                            favorites = favorites.map { Triple(it.chapter, it.start, it.end) },
                            accent = accent,
                            dark = theme.dark,
                        )
                        val handler = PageGestureHandler(
                            onTapZone = { zone ->
                                when {
                                    vm.selection != null -> vm.selection = null
                                    annotationPopup != null -> annotationPopup = null
                                    controlsVisible -> controlsVisible = false
                                    !s.tapZones || zone == 0 -> controlsVisible = true
                                    else -> navigate(zone)
                                }
                            },
                            onTapText = { chapter, offset ->
                                val sel = vm.selection
                                if (sel != null) {
                                    if (sel.chapter == chapter) {
                                        vm.selection = Selection(chapter, minOf(sel.start, offset.first), maxOf(sel.end, offset.second))
                                    } else vm.selection = null
                                    true
                                } else {
                                    val a = vm.annotationAt(chapter, offset.first)
                                    if (a != null) { annotationPopup = a; true } else false
                                }
                            },
                            onLongPress = { chapter, range ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                controlsVisible = false
                                annotationPopup = null
                                vm.selection = Selection(chapter, range.first, range.second)
                            },
                            onDragSelect = { chapter, range ->
                                val sel = vm.selection
                                if (sel != null && sel.chapter == chapter) {
                                    vm.selection = Selection(chapter, minOf(sel.start, range.first), maxOf(sel.end, range.second))
                                }
                            },
                        )
                        val frame = PageFrame(headerH, footerH, textW, textH, s, fg, accent, bg, paper)
                        when (s.pageMode) {
                            PageMode.PAGED -> PagedReader(vm, ts, frame, pageDeco, handler, bookmarks.map { it.chapter to it.charOffset }, onNavigateReady = { navigate = it })
                            PageMode.SCROLL -> ScrollReader(vm, ts, frame, pageDeco, handler, onNavigateReady = { navigate = it })
                        }
                    }
                    if (vm.paginating) {
                        Box(Modifier.fillMaxSize().background(bg.copy(alpha = if (vm.pages.isEmpty()) 1f else 0.85f))) {
                            BookLoading(fg, accent, "Seiten werden gesetzt …", vm.paginationProgress)
                        }
                    }
                }
            }
        }

        // Gemütlicher Modus & Wärmefilter
        AmbientOverlay(s, theme)

        // Folgt dem Vorlesen auf die richtige Seite
        LaunchedEffect(ttsRange?.chapter, ttsRange?.start) {
            val r = ttsRange ?: return@LaunchedEffect
            val p = vm.pages.getOrNull(vm.currentPage)
            if (p == null || p.chapter != r.chapter || r.start < p.start || r.start >= p.end) vm.goTo(r.chapter, r.start)
        }

        // ---------- Bedienelemente ----------
        val currentPageRef = vm.pages.getOrNull(vm.currentPage)
        AnimatedVisibility(
            visible = controlsVisible && vm.selection == null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = book?.title ?: "",
                chapter = currentPageRef?.let { vm.chapters.getOrNull(it.chapter)?.title } ?: "",
                bookmarked = vm.bookmarkOnPage(vm.currentPage) != null,
                isPdf = book?.format == "pdf",
                onBack = onBack,
                onBookmark = { vm.toggleBookmark(vm.currentPage) },
                onSearch = { sheet = ReaderSheet.SEARCH },
                onToc = { sheet = ReaderSheet.TOC },
                onNotes = { sheet = ReaderSheet.NOTES },
                onInfo = { sheet = ReaderSheet.INFO },
                onFinished = { vm.setBookStatus("FINISHED") },
                onVoice = { sheet = ReaderSheet.VOICE },
                onPdfOriginal = onOpenPdfOriginal,
            )
        }
        AnimatedVisibility(
            visible = controlsVisible && vm.selection == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                vm = vm,
                settings = s,
                onSettings = { sheet = ReaderSheet.DISPLAY },
                onCozy = { container.settings.updateReader { it.copy(cozyMode = !it.cozyMode) } },
                onTheme = { sheet = ReaderSheet.THEME },
                onTts = { if (ttsSpeaking) vm.tts.stop() else vm.startTts(); controlsVisible = false },
                onAmbient = { sheet = ReaderSheet.AMBIENT },
            )
        }
        if (ttsSpeaking && !controlsVisible && vm.selection == null) {
            TtsMiniPlayer(vm, Modifier.align(Alignment.BottomCenter), onVoice = { sheet = ReaderSheet.VOICE })
        }
        // Auswahl-Werkzeuge
        vm.selection?.let { sel ->
            SelectionToolbar(
                text = vm.selectedText(sel),
                modifier = Modifier.align(Alignment.BottomCenter),
                isFavorite = vm.isFavoriteRange(sel.chapter, sel.start, sel.end),
                onHighlight = { color -> vm.addAnnotation(sel, color, "HIGHLIGHT") },
                onUnderline = { vm.addAnnotation(sel, 0xFFB08D57.toInt(), "UNDERLINE") },
                onNote = { noteDialog = NoteTarget.New(sel) },
                onFavorite = { favoriteDialog = sel to "SELECTION" },
                onExpandSentence = { vm.selection = vm.sentenceOf(sel) },
                onExpandParagraph = { vm.selection = vm.paragraphOf(sel) },
                onExpandPage = { vm.pageOf(vm.currentPage)?.let { vm.selection = it } },
                onSpeak = { vm.startTtsAt(sel) },
                onClose = { vm.selection = null },
                bookTitle = book?.title ?: "",
                author = book?.author ?: "",
            )
        }
        annotationPopup?.let { a ->
            AnnotationPopup(
                annotation = a,
                modifier = Modifier.align(Alignment.BottomCenter),
                onColor = { c -> vm.updateAnnotation(a.copy(color = c, style = "HIGHLIGHT")); annotationPopup = null },
                onEditNote = { noteDialog = NoteTarget.Existing(a); annotationPopup = null },
                onFavorite = { favoriteDialog = Selection(a.chapter, a.start, a.end) to "SELECTION"; annotationPopup = null },
                onDelete = { vm.deleteAnnotation(a); annotationPopup = null },
                onClose = { annotationPopup = null },
                bookTitle = book?.title ?: "",
            )
        }
    }

    // ---------- Sheets & Dialoge ----------
    when (sheet) {
        ReaderSheet.DISPLAY -> DisplaySheet(container, s, onDismiss = { sheet = null })
        ReaderSheet.THEME -> ThemeSheet(container, s, onDismiss = { sheet = null })
        ReaderSheet.AMBIENT -> AmbientSheet(container, s, vm, onDismiss = { sheet = null })
        ReaderSheet.TOC, ReaderSheet.NOTES -> TocSheet(
            vm = vm,
            startTab = if (sheet == ReaderSheet.NOTES) 2 else 0,
            onDismiss = { sheet = null },
            onJump = { ch, off, end -> vm.goTo(ch, off, end); sheet = null; controlsVisible = false },
            onEditNote = { noteDialog = NoteTarget.Existing(it) },
        )
        ReaderSheet.SEARCH -> SearchSheet(vm, onDismiss = { sheet = null }, onJump = { hit ->
            vm.activeSearch = hit
            vm.goTo(hit.chapter, hit.start)
            sheet = null
            controlsVisible = false
        })
        ReaderSheet.INFO -> BookInfoSheet(vm, onDismiss = { sheet = null })
        ReaderSheet.VOICE -> VoiceSheet(container, s, vm, book?.language ?: "", onDismiss = { sheet = null })
        null -> Unit
    }
    favoriteDialog?.let { (sel, kind) ->
        FavoriteDialog(
            vm = vm,
            initial = sel,
            initialKind = kind,
            onDismiss = { favoriteDialog = null },
            onSave = { finalSel, k, collection, note -> vm.addFavorite(finalSel, k, collection, note); favoriteDialog = null },
        )
    }
    noteDialog?.let { target ->
        NoteDialog(
            initial = (target as? NoteTarget.Existing)?.annotation?.note ?: "",
            quote = when (target) {
                is NoteTarget.New -> vm.selectedText(target.selection)
                is NoteTarget.Existing -> target.annotation.text
            },
            onDismiss = { noteDialog = null },
            onSave = { text ->
                when (target) {
                    is NoteTarget.New -> vm.addAnnotation(target.selection, 0xFFFFE27A.toInt(), "NOTE", text)
                    is NoteTarget.Existing -> vm.updateAnnotation(target.annotation.copy(note = text.ifBlank { null }))
                }
                noteDialog = null
            },
        )
    }
}

enum class ReaderSheet { DISPLAY, THEME, AMBIENT, TOC, NOTES, SEARCH, INFO, VOICE }

sealed interface NoteTarget {
    data class New(val selection: Selection) : NoteTarget
    data class Existing(val annotation: com.lesehaus.app.data.db.Annotation) : NoteTarget
}

data class PageFrame(
    val headerH: Dp,
    val footerH: Dp,
    val textW: Dp,
    val textH: Dp,
    val s: ReaderSettings,
    val fg: Color,
    val accent: Color,
    val bg: Color,
    val paper: ShaderBrush,
)

data class PageDecorations(
    val annotations: List<com.lesehaus.app.data.db.Annotation>,
    val selection: Selection?,
    val search: SearchHit?,
    val tts: SpokenRange?,
    val flash: Selection?,
    val favorites: List<Triple<Int, Int, Int>>,
    val accent: Color,
    val dark: Boolean,
) {
    fun forChapter(ch: Int): List<Decoration> {
        val out = ArrayList<Decoration>()
        favorites.filter { it.first == ch }.forEach {
            out += Decoration(it.second, it.third, SpanStyle(textDecoration = TextDecoration.Underline))
        }
        annotations.filter { it.chapter == ch }.forEach { a ->
            val c = Color(a.color)
            out += when (a.style) {
                "UNDERLINE" -> Decoration(a.start, a.end, SpanStyle(textDecoration = TextDecoration.Underline, color = if (dark) c.copy(alpha = 1f) else Color.Unspecified))
                "NOTE" -> Decoration(a.start, a.end, SpanStyle(background = c.copy(alpha = if (dark) 0.22f else 0.35f), textDecoration = TextDecoration.Underline))
                else -> Decoration(a.start, a.end, SpanStyle(background = c.copy(alpha = if (dark) 0.35f else 0.55f)))
            }
        }
        flash?.takeIf { it.chapter == ch }?.let { out += Decoration(it.start, it.end, SpanStyle(background = accent.copy(alpha = 0.3f))) }
        search?.takeIf { it.chapter == ch }?.let { out += Decoration(it.start, it.end, SpanStyle(background = Color(0xFFFFB74D).copy(alpha = 0.7f))) }
        tts?.takeIf { it.chapter == ch }?.let {
            out += Decoration(it.start, it.end, SpanStyle(background = accent.copy(alpha = 0.12f)))
            if (it.wordStart >= 0) out += Decoration(it.wordStart, it.wordEnd, SpanStyle(background = accent.copy(alpha = 0.35f)))
        }
        selection?.takeIf { it.chapter == ch }?.let { out += Decoration(it.start, it.end, SpanStyle(background = accent.copy(alpha = 0.33f))) }
        return out
    }
}

class PageGestureHandler(
    /** -1 zurück, 0 Mitte, 1 vor */
    val onTapZone: (Int) -> Unit,
    /** true, wenn verarbeitet */
    val onTapText: (chapter: Int, range: Pair<Int, Int>) -> Boolean,
    val onLongPress: (chapter: Int, range: Pair<Int, Int>) -> Unit,
    val onDragSelect: (chapter: Int, range: Pair<Int, Int>) -> Unit,
)

private sealed interface PressResult {
    data class Up(val pos: Offset) : PressResult
    data object Moved : PressResult
}

// ------------------------------------------------------------------------------------------------
// Blättern
// ------------------------------------------------------------------------------------------------
@Composable
private fun PagedReader(
    vm: ReaderViewModel,
    ts: TypeSetter,
    frame: PageFrame,
    deco: PageDecorations,
    handler: PageGestureHandler,
    bookmarks: List<Pair<Int, Int>>,
    onNavigateReady: ((Int) -> Unit) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = vm.currentPage.coerceIn(0, (vm.pages.size - 1).coerceAtLeast(0))) { vm.pages.size }
    val scope = rememberCoroutineScope()
    val anim = frame.s.pageAnimation
    LaunchedEffect(pagerState, anim) {
        onNavigateReady { dir ->
            scope.launch {
                val target = (pagerState.currentPage + dir).coerceIn(0, vm.pages.size - 1)
                if (anim == PageAnimation.NONE) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target, animationSpec = tween(if (anim == PageAnimation.CURL) 520 else 380))
            }
        }
    }
    LaunchedEffect(vm.jumpRequest) {
        vm.jumpRequest?.let {
            pagerState.scrollToPage(it.coerceIn(0, (vm.pages.size - 1).coerceAtLeast(0)))
            vm.onPageShown(pagerState.currentPage)
            vm.jumpRequest = null
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.onPageShown(it) }
    }
    val chapterStarts = remember(vm.pages) { chapterPageInfo(vm.pages) }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = vm.selection == null,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val page = vm.pages[index]
        // Aktuelle bzw. gerade umgeblätterte Seite liegt oben, die nächste darunter
        val onTop by remember(index) { derivedStateOf { (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction >= 0f } }
        val bookmarked = bookmarks.any { it.first == page.chapter && it.second >= page.start && it.second < page.end }
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(if (onTop) 1f else 0f)
                .graphicsLayer {
                    val off = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                    when (anim) {
                        PageAnimation.CURL -> {
                            translationX = off * size.width
                            if (off > 0f) {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = -95f * off.coerceIn(0f, 1f)
                                cameraDistance = 18f * density
                                if (off >= 0.999f) alpha = 0f
                            }
                        }
                        PageAnimation.STACK -> if (off < 0f) {
                            translationX = off * size.width
                            val f = 1f + off.coerceIn(-1f, 0f)
                            scaleX = 0.92f + 0.08f * f
                            scaleY = scaleX
                            alpha = 0.5f + 0.5f * f
                        }
                        PageAnimation.FADE -> {
                            translationX = off * size.width
                            alpha = 1f - abs(off).coerceIn(0f, 1f)
                        }
                        else -> Unit
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (anim == PageAnimation.CURL || anim == PageAnimation.STACK) {
                        val off = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                        if (off > 0f && off < 1f) {
                            // Schatten auf der umgeblätterten Seite
                            drawRect(Color.Black.copy(alpha = 0.35f * off))
                            drawRect(
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), size.width * 0.85f, size.width),
                            )
                        }
                    }
                }
        ) {
            PageView(vm, ts, page, index, frame, deco, handler, bookmarked, chapterStarts)
        }
    }
}

private fun chapterPageInfo(pages: List<PageRef>): Map<Int, Pair<Int, Int>> {
    val m = HashMap<Int, Pair<Int, Int>>()
    pages.forEachIndexed { i, p ->
        val cur = m[p.chapter]
        m[p.chapter] = if (cur == null) i to 1 else cur.first to cur.second + 1
    }
    return m
}

// ------------------------------------------------------------------------------------------------
// Scrollen
// ------------------------------------------------------------------------------------------------
@Composable
private fun ScrollReader(
    vm: ReaderViewModel,
    ts: TypeSetter,
    frame: PageFrame,
    deco: PageDecorations,
    handler: PageGestureHandler,
    onNavigateReady: ((Int) -> Unit) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.currentPage.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(listState) {
        onNavigateReady { dir ->
            scope.launch { listState.animateScrollBy(dir * with(density) { frame.textH.toPx() } * 0.85f) }
        }
    }
    LaunchedEffect(vm.jumpRequest) {
        vm.jumpRequest?.let {
            listState.scrollToItem(it.coerceIn(0, (vm.pages.size - 1).coerceAtLeast(0)))
            vm.onPageShown(it)
            vm.jumpRequest = null
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { vm.onPageShown(it) }
    }
    val chapterStarts = remember(vm.pages) { chapterPageInfo(vm.pages) }
    Box(Modifier.fillMaxSize().background(frame.paper.takeIf { frame.s.paperTexture } ?: Brush.linearGradient(listOf(frame.bg, frame.bg)))) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = frame.headerH + frame.s.marginV.dp, bottom = frame.footerH + frame.s.marginV.dp),
        ) {
            itemsIndexed(vm.pages, key = { i, p -> "${p.chapter}_${p.start}_$i" }) { index, page ->
                val showChapterGap = index > 0 && vm.pages[index - 1].chapter != page.chapter
                Column(Modifier.fillMaxWidth().padding(horizontal = frame.s.marginH.dp)) {
                    if (showChapterGap) ChapterDivider(frame.accent)
                    PageTextBlock(vm, ts, page, index, frame, deco, handler, scroll = true)
                }
            }
        }
        val p = vm.pages.getOrNull(vm.currentPage)
        if (p != null) {
            Box(Modifier.fillMaxWidth().height(frame.headerH + frame.s.marginV.dp).background(Brush.verticalGradient(listOf(frame.bg, frame.bg.copy(alpha = 0f)))))
            Column(Modifier.fillMaxWidth().padding(horizontal = frame.s.marginH.dp, vertical = (frame.s.marginV / 2).dp)) {
                PageHeader(vm, p, frame)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(frame.footerH + frame.s.marginV.dp)
                    .background(Brush.verticalGradient(listOf(frame.bg.copy(alpha = 0f), frame.bg)))
            )
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = frame.s.marginH.dp, vertical = (frame.s.marginV / 2).dp)) {
                PageFooter(vm, p, vm.currentPage, frame, chapterStarts)
            }
        }
    }
}

@Composable
private fun ChapterDivider(accent: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text("❦", style = TextStyle(fontSize = 22.sp, color = accent))
    }
}

// ------------------------------------------------------------------------------------------------
// Seite
// ------------------------------------------------------------------------------------------------
@Composable
private fun PageView(
    vm: ReaderViewModel,
    ts: TypeSetter,
    page: PageRef,
    index: Int,
    frame: PageFrame,
    deco: PageDecorations,
    handler: PageGestureHandler,
    bookmarked: Boolean,
    chapterStarts: Map<Int, Pair<Int, Int>>,
) {
    val s = frame.s
    Box(
        Modifier
            .fillMaxSize()
            .background(frame.bg)
            .then(if (s.paperTexture) Modifier.background(frame.paper) else Modifier)
            .drawBehind {
                // leichter Bundsteg-Schatten links für Buch-Gefühl
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = if (frame.s.theme.dark) 0.25f else 0.06f), Color.Transparent), 0f, size.width * 0.06f))
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = s.marginH.dp, vertical = s.marginV.dp)) {
            Box(Modifier.fillMaxWidth().height(frame.headerH), contentAlignment = Alignment.TopStart) {
                if (s.showHeader) PageHeader(vm, page, frame)
            }
            PageTextBlock(vm, ts, page, index, frame, deco, handler, scroll = false)
            Box(Modifier.fillMaxWidth().height(frame.footerH), contentAlignment = Alignment.BottomStart) {
                if (s.showFooter) PageFooter(vm, page, index, frame, chapterStarts)
            }
        }
        // Lesezeichen-Bändchen
        val ribbon by animateFloatAsState(if (bookmarked) 1f else 0f, tween(450), label = "ribbon")
        if (ribbon > 0.01f) {
            Canvas(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).size(18.dp, 56.dp)) {
                val h = size.height * ribbon
                val p = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, h)
                    lineTo(size.width / 2, h - size.width * 0.5f); lineTo(0f, h); close()
                }
                drawPath(p, Color(0xFF9B2F3F))
                drawPath(p, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.2f))))
            }
        }
    }
}

@Composable
private fun PageHeader(vm: ReaderViewModel, page: PageRef, frame: PageFrame) {
    val title = vm.chapters.getOrNull(page.chapter)?.title ?: ""
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = frame.fg.copy(alpha = 0.45f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (frame.s.showClock) {
            val time by produceState(now()) { while (true) { delay(20_000); value = now() } }
            Text(time, style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 10.5.sp, color = frame.fg.copy(alpha = 0.45f)))
        }
    }
}

private fun now(): String = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date())

@Composable
private fun PageFooter(vm: ReaderViewModel, page: PageRef, index: Int, frame: PageFrame, chapterStarts: Map<Int, Pair<Int, Int>>) {
    val info = chapterStarts[page.chapter]
    val inChapter = info?.let { index - it.first + 1 } ?: 1
    val chapterPages = info?.second ?: 1
    val progress = if (index >= vm.pages.lastIndex) 1f else vm.globalProgress(page.chapter, page.start)
    val style = TextStyle(fontFamily = Fonts.Nunito, fontSize = 10.5.sp, color = frame.fg.copy(alpha = 0.5f))
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1} / ${vm.pages.size}", style = style)
            Spacer(Modifier.weight(1f))
            if (frame.s.showTimeLeft) {
                val ch = vm.chapters.getOrNull(page.chapter)
                val remaining = ((ch?.text?.length ?: 0) - page.start).coerceAtLeast(0)
                val min = (remaining / vm.charsPerMinute).toInt()
                Text(
                    when {
                        chapterPages - inChapter == 0 -> "Letzte Seite im Kapitel"
                        min < 1 -> "Kapitel gleich zu Ende"
                        else -> "noch ~$min Min. im Kapitel"
                    },
                    style = style.copy(fontStyle = FontStyle.Italic, fontFamily = Fonts.Lora),
                )
                Spacer(Modifier.weight(1f))
            }
            Text("${(progress * 100).toInt()} %", style = style)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(frame.fg.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(frame.accent.copy(alpha = 0.6f)))
        }
    }
}

/** Der eigentliche Seitentext inkl. Gesten. */
@Composable
private fun PageTextBlock(
    vm: ReaderViewModel,
    ts: TypeSetter,
    page: PageRef,
    index: Int,
    frame: PageFrame,
    deco: PageDecorations,
    handler: PageGestureHandler,
    scroll: Boolean,
) {
    val ch = vm.chapters.getOrNull(page.chapter) ?: return
    val density = LocalDensity.current
    val decorations = remember(deco, page.chapter) { deco.forChapter(page.chapter) }
    val text = remember(ts, page, decorations, ch) { ts.build(ch, page.start, page.renderEnd, decorations) }
    val inline = remember(ts, page, ch) {
        ch.blocks.filter { it.kind == BlockKind.IMAGE && it.start >= page.start && it.end <= page.renderEnd }.associate { b ->
            "img:${b.start}" to InlineTextContent(ts.placeholder(b)) {
                AsyncImage(model = File(b.imagePath ?: ""), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val heightDp = with(density) { (if (scroll) page.heightPx else ts.pageHeightPx).toDp() }
    val widthDp = with(density) { ts.pageWidthPx.toDp() }
    val currentHandler by rememberUpdatedState(handler)
    val currentPage by rememberUpdatedState(page)
    var boxWidth by remember { mutableStateOf(1f) }

    fun rangeAt(pos: Offset, word: Boolean): Pair<Int, Int>? {
        val l = layout ?: return null
        val p = currentPage
        if (pos.y > (if (scroll) p.heightPx.toFloat() else l.getLineBottom(maxOf(0, l.getLineForOffset((p.end - p.start).coerceAtLeast(1) - 1))))) return null
        val off = l.getOffsetForPosition(pos).coerceIn(0, (p.end - p.start - 1).coerceAtLeast(0))
        return if (word) {
            val wb = l.getWordBoundary(off)
            val s = (wb.start + p.start).coerceIn(p.start, p.end)
            val e = (wb.end + p.start).coerceIn(s, p.end)
            if (e > s) s to e else (p.start + off) to (p.start + off + 1).coerceAtMost(p.end)
        } else (p.start + off) to (p.start + off + 1)
    }

    val clipDp = with(density) { page.heightPx.coerceAtLeast(1).toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .onGloballyPositioned { boxWidth = it.size.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var r: PressResult? = null
                        while (r == null) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.first()
                            if (!c.pressed) r = PressResult.Up(c.position)
                            else if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) r = PressResult.Moved
                        }
                        r
                    }
                    when (result) {
                        null -> {
                            // Langer Druck → Wort auswählen, dann ziehen zum Erweitern
                            val r = rangeAt(down.position, true)
                            if (r != null) {
                                currentHandler.onLongPress(currentPage.chapter, r)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.first()
                                    if (!c.pressed) break
                                    c.consume()
                                    rangeAt(c.position, true)?.let { currentHandler.onDragSelect(currentPage.chapter, it) }
                                }
                            }
                        }
                        is PressResult.Up -> {
                            val r = rangeAt(result.pos, true)
                            val handled = r != null && currentHandler.onTapText(currentPage.chapter, r)
                            if (!handled) {
                                val x = result.pos.x / boxWidth
                                currentHandler.onTapZone(if (x < 0.3f) -1 else if (x > 0.7f) 1 else 0)
                            }
                        }
                        PressResult.Moved -> Unit
                    }
                }
            }
    ) {
        // Nur die gemessenen Zeilen zeigen; der Absatzrest gehört zur nächsten Seite
        Box(Modifier.fillMaxWidth().height(clipDp).clipToBounds()) {
            BasicText(
                text = text,
                style = ts.baseStyle,
                inlineContent = inline,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .requiredWidth(widthDp)
                    .wrapContentHeight(Alignment.Top, unbounded = true),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Atmosphäre
// ------------------------------------------------------------------------------------------------
@Composable
fun AmbientOverlay(s: ReaderSettings, theme: ReaderTheme) {
    val t by produceState(0f, s.cozyMode) {
        if (s.cozyMode) {
            val start = System.nanoTime()
            while (true) {
                androidx.compose.animation.core.withInfiniteAnimationFrameNanos { value = (it - start) / 1e9f }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (s.warmFilter > 0f) drawRect(Color(0xFFFF8A2B).copy(alpha = 0.22f * s.warmFilter), blendMode = androidx.compose.ui.graphics.BlendMode.Multiply)
        if (s.cozyMode) {
            val flick = 0.85f + 0.08f * kotlin.math.sin(t * 7.1f) * kotlin.math.sin(t * 3.3f) + 0.05f * kotlin.math.sin(t * 13f)
            val g = s.cozyGlow
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color(0xFF1A0C02).copy(alpha = (if (theme.dark) 0.55f else 0.35f) * g)),
                    Offset(size.width / 2, size.height * 0.55f),
                    size.maxDimension * 0.72f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFFFA94D).copy(alpha = 0.22f * g * flick), Color.Transparent),
                    Offset(size.width * 0.1f, size.height * 1.02f),
                    size.maxDimension * 0.55f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFFFC27A).copy(alpha = 0.12f * g * flick), Color.Transparent),
                    Offset(size.width * 0.95f, size.height * 0.95f),
                    size.maxDimension * 0.4f,
                )
            )
            drawRect(Color(0xFFFF9A3C).copy(alpha = 0.06f * g))
        }
    }
}

@Composable
fun BookLoading(fg: Color, accent: Color, label: String, progress: Float?) {
    val t by produceState(0f) {
        val start = System.nanoTime()
        while (true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos { value = (it - start) / 1e9f }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(96.dp, 64.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                // Buchrücken & Seiten
                drawRect(accent, Offset(cx - w * 0.46f, h * 0.15f), androidx.compose.ui.geometry.Size(w * 0.92f, h * 0.8f))
                drawRect(Color(0xFFF7F0E2), Offset(cx - w * 0.43f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.86f, h * 0.8f))
                drawLine(accent.copy(alpha = 0.5f), Offset(cx, h * 0.1f), Offset(cx, h * 0.9f), 2f)
                // blätternde Seite
                val ph = (t * 0.9f) % 1f
                val x = cx + (w * 0.42f) * kotlin.math.cos(ph * Math.PI).toFloat()
                val p = Path().apply {
                    moveTo(cx, h * 0.1f); lineTo(x, h * 0.1f - kotlin.math.sin(ph * Math.PI).toFloat() * h * 0.1f)
                    lineTo(x, h * 0.9f - kotlin.math.sin(ph * Math.PI).toFloat() * h * 0.1f); lineTo(cx, h * 0.9f); close()
                }
                drawPath(p, Color(0xFFEDE3CF))
                drawPath(p, Color(0x33000000), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text(label, style = TextStyle(fontFamily = Fonts.Lora, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = fg.copy(alpha = 0.8f)))
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(160.dp).height(3.dp), color = accent, trackColor = fg.copy(alpha = 0.1f))
            }
        }
    }
}
