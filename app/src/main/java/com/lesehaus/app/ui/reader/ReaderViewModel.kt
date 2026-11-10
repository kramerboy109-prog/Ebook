package com.lesehaus.app.ui.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesehaus.app.AppContainer
import com.lesehaus.app.data.ReaderSettings
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Bookmark
import com.lesehaus.app.data.db.Favorite
import com.lesehaus.app.data.db.ReadingSession
import com.lesehaus.app.data.formats.BookContent
import com.lesehaus.app.ui.theme.ReadingFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Selection(val chapter: Int, val start: Int, val end: Int)

data class SearchHit(val chapter: Int, val start: Int, val end: Int, val snippet: String, val chapterTitle: String)

sealed interface LoadState {
    data object Loading : LoadState
    data class Error(val message: String) : LoadState
    data object Ready : LoadState
}

class ReaderViewModel(
    private val app: AppContainer,
    val bookId: Long,
    private val pdfAsText: Boolean,
    private val jumpChapter: Int = -1,
    private val jumpOffset: Int = 0,
    private val jumpEnd: Int = -1,
) : ViewModel() {
    private val db = app.db

    val book: StateFlow<Book?> = db.books().observeBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val annotations: StateFlow<List<Annotation>> = db.annotations().observeAnnotations(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = db.annotations().observeBookmarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites: StateFlow<List<Favorite>> = db.annotations().observeFavoritesOfBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<ReaderSettings> = app.settings.reader

    var loadState by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var content: BookContent? = null
        private set
    var chapters by mutableStateOf<List<PreparedChapter>>(emptyList())
        private set
    var pages by mutableStateOf<List<PageRef>>(emptyList())
        private set
    var paginating by mutableStateOf(false)
        private set
    var paginationProgress by mutableStateOf(0f)
        private set
    var typeSetter by mutableStateOf<TypeSetter?>(null)
        private set

    /** Aktuelle Leseposition (Kapitel + Zeichen). */
    var position by mutableStateOf(0 to 0)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    /** Seite, zu der die UI springen soll (z. B. nach TOC-Auswahl). */
    var jumpRequest by mutableStateOf<Int?>(null)
    /** Kurzes Aufleuchten einer Stelle nach einem Sprung. */
    var flash by mutableStateOf<Selection?>(null)

    var selection by mutableStateOf<Selection?>(null)
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var activeSearch by mutableStateOf<SearchHit?>(null)

    val tts = TtsController(app.context)
    val ambient = AmbientPlayer()

    private var chapterOffsets = IntArray(0)
    private var totalChars = 1
    private var paginateJob: Job? = null
    private var lastLayoutKey: String? = null
    private var sessionStart = System.currentTimeMillis()
    private var sessionPages = 0
    private var lastPageForStats = -1
    private var restored = false
    /** Lesegeschwindigkeit (Zeichen pro Minute) für Restzeit-Schätzung. */
    var charsPerMinute by mutableStateOf(1100f)
        private set

    init {
        viewModelScope.launch {
            val b = db.books().book(bookId)
            if (b == null) {
                loadState = LoadState.Error("Buch nicht gefunden"); return@launch
            }
            position = if (jumpChapter >= 0) jumpChapter to jumpOffset else b.chapter to b.charOffset
            if (jumpChapter >= 0 && jumpEnd > jumpOffset) flash = Selection(jumpChapter, jumpOffset, jumpEnd)
            runCatching {
                app.content(b, pdfAsText)
            }.onSuccess {
                content = it
                prepare(settings.value)
                loadState = LoadState.Ready
            }.onFailure {
                loadState = LoadState.Error(it.message ?: "Das Buch konnte nicht geöffnet werden.")
            }
            tts.setLanguage(b.language)
            // Lesegeschwindigkeit aus der Historie
            val since = System.currentTimeMillis() - 30L * 24 * 3600_000
            val ms = db.stats().timeSince(bookId, since)
            val pagesRead = db.stats().pagesSince(bookId, since)
            if (ms > 5 * 60_000 && pagesRead > 5) charsPerMinute = (pagesRead * 1400f) / (ms / 60_000f)
        }
        tts.onChapterDone = { ch ->
            viewModelScope.launch {
                val next = ch + 1
                if (next < chapters.size) {
                    goTo(next, 0)
                    speakChapter(next, 0)
                } else tts.stop()
            }
        }
    }

    private suspend fun prepare(s: ReaderSettings) {
        val c = content ?: return
        chapters = withContext(Dispatchers.Default) { c.chapters.mapIndexed { i, ch -> prepareChapter(i, ch, s) } }
        chapterOffsets = IntArray(chapters.size)
        var acc = 0
        chapters.forEachIndexed { i, ch -> chapterOffsets[i] = acc; acc += ch.text.length }
        totalChars = acc.coerceAtLeast(1)
    }

    fun globalProgress(chapter: Int, offset: Int): Float =
        if (chapters.isEmpty()) 0f else ((chapterOffsets.getOrElse(chapter) { 0 } + offset).toFloat() / totalChars).coerceIn(0f, 1f)

    /** Startet das Setzen der Seiten, wenn sich Größe oder Einstellungen ändern. */
    fun paginate(context: Context, s: ReaderSettings, widthPx: Int, heightPx: Int, density: Density, textColor: Color, accent: Color) {
        if (content == null || widthPx <= 0 || heightPx <= 0) return
        val key = "${s.layoutKey()}|$widthPx|$heightPx|${density.density}|${density.fontScale}|${app.settings.app.value.customFonts.size}"
        if (key == lastLayoutKey && typeSetter?.textColor == textColor) return
        val needsRelayout = key != lastLayoutKey
        lastLayoutKey = key
        val family = ReadingFonts.resolve(s.fontKey, app.settings.app.value.customFonts)
        val ts = TypeSetter(s, family, textColor, accent, density, widthPx, heightPx)
        if (!needsRelayout && pages.isNotEmpty()) {
            typeSetter = ts
            return
        }
        paginateJob?.cancel()
        val anchor = position
        paginateJob = viewModelScope.launch {
            paginating = true
            paginationProgress = 0f
            prepare(s)
            val all = withContext(Dispatchers.Default) {
                val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 0)
                val out = ArrayList<PageRef>()
                chapters.forEachIndexed { i, ch ->
                    out.addAll(ts.paginate(measurer, ch))
                    paginationProgress = (i + 1f) / chapters.size
                }
                out
            }
            typeSetter = ts
            pages = all
            paginating = false
            val idx = pageIndexFor(anchor.first, anchor.second)
            currentPage = idx
            jumpRequest = idx
            restored = true
        }
    }

    fun pageIndexFor(chapter: Int, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val idx = pages.indexOfFirst { it.chapter == chapter && offset >= it.start && offset < it.end }
        if (idx >= 0) return idx
        val firstOfChapter = pages.indexOfFirst { it.chapter == chapter }
        if (firstOfChapter >= 0) {
            val lastOfChapter = pages.indexOfLast { it.chapter == chapter }
            return if (offset >= pages[lastOfChapter].end) lastOfChapter else firstOfChapter
        }
        return pages.indexOfFirst { it.chapter > chapter }.let { if (it < 0) pages.lastIndex else it }
    }

    /** Wird von der UI gemeldet, wenn eine Seite sichtbar wird. */
    fun onPageShown(index: Int) {
        val p = pages.getOrNull(index) ?: return
        if (!restored) return
        if (lastPageForStats >= 0 && index != lastPageForStats) sessionPages++
        lastPageForStats = index
        currentPage = index
        position = p.chapter to p.start
        val prog = if (index >= pages.lastIndex) 1f else globalProgress(p.chapter, p.start)
        viewModelScope.launch {
            db.books().updatePosition(bookId, p.chapter, p.start, -1, prog, pages.size, System.currentTimeMillis())
            if (index >= pages.lastIndex) book.value?.let { b -> if (b.status != "FINISHED") db.books().update(b.copy(status = "FINISHED", progress = 1f, lastOpenedAt = System.currentTimeMillis())) }
        }
    }

    fun goTo(chapter: Int, offset: Int, flashEnd: Int? = null) {
        val idx = pageIndexFor(chapter, offset)
        jumpRequest = idx
        if (flashEnd != null) flash = Selection(chapter, offset, flashEnd)
    }

    fun goToPage(index: Int) {
        jumpRequest = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    // ---------------- Lesezeichen ----------------
    fun bookmarkOnPage(index: Int): Bookmark? {
        val p = pages.getOrNull(index) ?: return null
        return bookmarks.value.firstOrNull { it.chapter == p.chapter && it.charOffset >= p.start && it.charOffset < p.end }
    }

    fun toggleBookmark(index: Int) {
        val p = pages.getOrNull(index) ?: return
        val existing = bookmarkOnPage(index)
        viewModelScope.launch {
            if (existing != null) db.annotations().deleteBookmark(existing)
            else {
                val ch = chapters[p.chapter]
                db.annotations().insertBookmark(
                    Bookmark(
                        bookId = bookId,
                        chapter = p.chapter,
                        charOffset = p.start,
                        label = ch.title,
                        snippet = ch.plain(p.start, minOf(p.end, p.start + 160)),
                        progress = globalProgress(p.chapter, p.start),
                    )
                )
            }
        }
    }

    fun renameBookmark(b: Bookmark, label: String) = viewModelScope.launch { db.annotations().updateBookmark(b.copy(label = label)) }
    fun deleteBookmark(b: Bookmark) = viewModelScope.launch { db.annotations().deleteBookmark(b) }

    // ---------------- Markierungen & Notizen ----------------
    fun selectedText(sel: Selection): String = chapters.getOrNull(sel.chapter)?.plain(sel.start, sel.end) ?: ""

    fun addAnnotation(sel: Selection, color: Int, style: String, note: String? = null) {
        viewModelScope.launch {
            db.annotations().insertAnnotation(
                Annotation(bookId = bookId, chapter = sel.chapter, start = sel.start, end = sel.end, text = selectedText(sel), color = color, style = style, note = note?.takeIf { it.isNotBlank() })
            )
        }
        selection = null
    }

    fun updateAnnotation(a: Annotation) = viewModelScope.launch { db.annotations().updateAnnotation(a.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteAnnotation(a: Annotation) = viewModelScope.launch { db.annotations().deleteAnnotation(a) }

    fun annotationAt(chapter: Int, offset: Int): Annotation? =
        annotations.value.lastOrNull { it.chapter == chapter && offset >= it.start && offset < it.end }

    // ---------------- Gefällt mir ----------------
    /** Erweitert eine Auswahl auf den ganzen Absatz. */
    fun paragraphOf(sel: Selection): Selection {
        val ch = chapters.getOrNull(sel.chapter) ?: return sel
        val first = ch.blockAt(sel.start) ?: return sel
        val last = ch.blockAt((sel.end - 1).coerceAtLeast(sel.start)) ?: first
        return Selection(sel.chapter, first.start, last.end)
    }

    fun pageOf(index: Int): Selection? = pages.getOrNull(index)?.let { Selection(it.chapter, it.start, it.end) }

    /** Ermittelt die Zeile(n), in der die Auswahl liegt – über Satzgrenzen. */
    fun sentenceOf(sel: Selection): Selection {
        val ch = chapters.getOrNull(sel.chapter) ?: return sel
        val it = java.text.BreakIterator.getSentenceInstance()
        it.setText(ch.text)
        val s = it.preceding((sel.start + 1).coerceIn(1, ch.text.length)).let { p -> if (p == java.text.BreakIterator.DONE) 0 else p }
        val e = it.following((sel.end - 1).coerceIn(0, ch.text.length - 1)).let { p -> if (p == java.text.BreakIterator.DONE) ch.text.length else p }
        return Selection(sel.chapter, s, e)
    }

    fun addFavorite(sel: Selection, kind: String, collection: String, note: String?) {
        val b = book.value ?: return
        viewModelScope.launch {
            db.annotations().insertFavorite(
                Favorite(
                    bookId = bookId, bookTitle = b.title, bookAuthor = b.author, chapter = sel.chapter, start = sel.start, end = sel.end,
                    text = selectedText(sel), kind = kind, collection = collection.ifBlank { "Lieblingsstellen" }, note = note?.takeIf { it.isNotBlank() },
                )
            )
        }
        selection = null
    }

    fun isFavoriteRange(chapter: Int, start: Int, end: Int): Boolean =
        favorites.value.any { it.chapter == chapter && it.start < end && it.end > start }

    // ---------------- Suche ----------------
    private var searchJob: Job? = null
    var searching by mutableStateOf(false)
        private set

    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) { searchHits = emptyList(); return }
        searchJob = viewModelScope.launch {
            searching = true
            searchHits = withContext(Dispatchers.Default) {
                val out = ArrayList<SearchHit>()
                for (ch in chapters) {
                    var i = ch.text.indexOf(query, 0, ignoreCase = true)
                    while (i >= 0 && out.size < 500) {
                        val s = (i - 40).coerceAtLeast(0)
                        val e = (i + query.length + 60).coerceAtMost(ch.text.length)
                        out.add(SearchHit(ch.index, i, i + query.length, ch.plain(s, e).replace('\n', ' '), ch.title))
                        i = ch.text.indexOf(query, i + query.length, ignoreCase = true)
                    }
                }
                out
            }
            searching = false
        }
    }

    fun clearSearch() { searchHits = emptyList(); activeSearch = null }

    // ---------------- Vorlesen ----------------
    private fun paragraphsOf(ch: PreparedChapter): List<NarrationParagraph> =
        ch.blocks.filter { it.spacerEm == 0f && it.kind != com.lesehaus.app.data.formats.BlockKind.IMAGE && it.kind != com.lesehaus.app.data.formats.BlockKind.RULE }
            .map { NarrationParagraph(it.start, it.end, it.kind == com.lesehaus.app.data.formats.BlockKind.H1 || it.kind == com.lesehaus.app.data.formats.BlockKind.H2 || it.kind == com.lesehaus.app.data.formats.BlockKind.H3) }

    private fun speakChapter(chapter: Int, from: Int) {
        val ch = chapters.getOrNull(chapter) ?: return
        tts.speak(chapter, ch.text, from, paragraphsOf(ch), settings.value, app.settings.app.value.aiKey)
    }

    fun startTts() {
        val p = pages.getOrNull(currentPage) ?: return
        speakChapter(p.chapter, p.start)
    }

    fun startTtsAt(sel: Selection) {
        speakChapter(sel.chapter, sel.start)
        selection = null
    }

    // ---------------- Statistik ----------------
    fun onResume() {
        sessionStart = System.currentTimeMillis()
        sessionPages = 0
    }

    fun onPause() {
        val dur = System.currentTimeMillis() - sessionStart
        if (dur < 20_000) return
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sessionStart))
        val pagesRead = sessionPages
        app.scope.launch {
            db.stats().insert(ReadingSession(bookId = bookId, day = day, startedAt = sessionStart, durationMs = dur, pages = pagesRead))
            db.books().addReadingTime(bookId, dur)
        }
        sessionStart = System.currentTimeMillis()
        sessionPages = 0
    }

    fun setBookStatus(status: String) {
        book.value?.let { b -> viewModelScope.launch { db.books().update(b.copy(status = status)) } }
    }

    override fun onCleared() {
        onPause()
        tts.shutdown()
        ambient.stop()
        super.onCleared()
    }
}
