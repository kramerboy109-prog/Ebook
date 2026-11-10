package com.lesehaus.app.data.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.lesehaus.app.data.db.AppDatabase
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.db.ShelfBook
import com.lesehaus.app.data.covers.CoverService
import com.lesehaus.app.data.formats.BookParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FoundFile(val uri: Uri, val name: String, val size: Long, val modified: Long)

data class ScanState(val shelfId: Long, val found: Int, val processed: Int, val current: String)

class ShelfScanner(
    private val context: Context,
    private val db: AppDatabase,
    private val covers: CoverService,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<Long, ScanState>>(emptyMap())
    val state: StateFlow<Map<Long, ScanState>> = _state.asStateFlow()

    /** Listet Dateien in einem per SAF freigegebenen Ordner (schnell über DocumentsContract). */
    fun listFiles(treeUri: Uri, recursive: Boolean, exts: Set<String>): List<FoundFile> {
        val out = ArrayList<FoundFile>()
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!granted) throw SecurityException("Kein Zugriff mehr auf $treeUri")
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (queue.isNotEmpty()) {
            val (docId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            runCatching {
                context.contentResolver.query(children, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive && depth < 12 && !name.startsWith(".")) queue.add(id to depth + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in exts) {
                                out.add(
                                    FoundFile(
                                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                        name,
                                        if (c.isNull(3)) 0 else c.getLong(3),
                                        if (c.isNull(4)) 0 else c.getLong(4),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun prettyTitle(fileName: String): Pair<String, String> {
        val base = fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        // „Autor - Titel“ erkennen
        val parts = base.split(" - ", limit = 2)
        return if (parts.size == 2 && parts[0].length in 3..40 && parts[0].count { it == ' ' } <= 3) parts[1].trim() to parts[0].trim()
        else base to ""
    }

    /** Scannt ein Regal: fügt neue Bücher hinzu, entfernt verschwundene. */
    suspend fun scan(shelf: Shelf, onlineCovers: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val exts = shelf.fileTypeSet
            update(shelf.id, ScanState(shelf.id, 0, 0, "Ordner werden durchsucht …"))
            var listingFailed = false
            val files = shelf.folderList.flatMap { f ->
                runCatching { listFiles(Uri.parse(f), shelf.recursive, exts) }
                    .getOrElse { listingFailed = true; emptyList() }
            }.distinctBy { it.uri.toString() }
            val existingIds = db.books().bookIdsOfShelf(shelf.id).toMutableSet()
            var processed = 0
            for (f in files) {
                processed++
                update(shelf.id, ScanState(shelf.id, files.size, processed, f.name))
                val uriStr = f.uri.toString()
                var book = db.books().bookByUri(uriStr)
                if (book == null) {
                    val (t, a) = prettyTitle(f.name)
                    val meta = BookParsers.readMeta(context, f.uri, f.name)
                    val inserted = db.books().insert(
                        Book(
                            uri = uriStr,
                            fileName = f.name,
                            format = BookParsers.formatOf(f.name),
                            title = meta.title ?: t,
                            author = meta.author ?: a,
                            description = meta.description ?: "",
                            language = meta.language ?: "",
                            publisher = meta.publisher ?: "",
                            fileSize = f.size,
                            lastModified = f.modified,
                        )
                    )
                    book = db.books().book(inserted) ?: db.books().bookByUri(uriStr) ?: continue
                    meta.coverBytes?.let { bytes ->
                        covers.store(book.id, bytes)?.let { (path, color) ->
                            book = book!!.copy(coverPath = path, coverColor = color, coverSource = "FILE")
                            db.books().update(book!!)
                        }
                    }
                }
                val b = book!!
                if (b.coverPath == null && onlineCovers && !b.onlineCoverTried) {
                    val found = runCatching { covers.search(b.title, b.author).firstOrNull() }.getOrNull()
                    val bytes = found?.let { covers.download(it.url) }
                    val stored = bytes?.let { covers.store(b.id, it) }
                    db.books().update(
                        if (stored != null) b.copy(coverPath = stored.first, coverColor = stored.second, coverSource = "ONLINE", onlineCoverTried = true)
                        else b.copy(onlineCoverTried = true)
                    )
                }
                db.books().link(ShelfBook(shelf.id, b.id))
                existingIds.remove(b.id)
            }
            // Bücher, deren Dateien nicht mehr gefunden wurden, aus diesem Regal nehmen.
            // Die Bücher selbst (inkl. Notizen) bleiben erhalten und tauchen wieder auf, sobald die Datei zurück ist.
            if (shelf.folderList.isNotEmpty() && !listingFailed) {
                existingIds.forEach { db.books().unlink(shelf.id, it) }
            }
            db.shelves().setLastScan(shelf.id, System.currentTimeMillis())
            update(shelf.id, null)
            files.size
        }
    }

    private fun update(shelfId: Long, s: ScanState?) {
        _state.value = _state.value.toMutableMap().also { if (s == null) it.remove(shelfId) else it[shelfId] = s }
    }
}
