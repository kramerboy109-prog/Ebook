package com.lesehaus.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.lesehaus.app.data.DefaultFileTypes
import com.lesehaus.app.data.RoomPresets
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.SettingsStore
import com.lesehaus.app.data.Wood
import com.lesehaus.app.data.covers.CoverService
import com.lesehaus.app.data.db.AppDatabase
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.Shelf
import com.lesehaus.app.data.formats.BookContent
import com.lesehaus.app.data.formats.BookParsers
import com.lesehaus.app.data.scan.ShelfScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LesehausApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.app: AppContainer get() = (applicationContext as LesehausApp).container

class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.create(context)
    val settings = SettingsStore(context)
    val covers = CoverService(context)
    val scanner = ShelfScanner(context, db, covers)

    private val contentCache = LruCache<String, BookContent>(3)
    private val parseMutex = Mutex()

    /** Lädt den Inhalt eines Buchs (mit kleinem Cache für schnelles Wiederöffnen). */
    suspend fun content(book: Book, pdfAsText: Boolean = false): BookContent = withContext(Dispatchers.IO) {
        val key = "${book.id}:${book.uri}:$pdfAsText"
        contentCache.get(key) ?: parseMutex.withLock {
            contentCache.get(key) ?: BookParsers.parse(context, Uri.parse(book.uri), book.fileName, book.title, book.author)
                .also { contentCache.put(key, it) }
        }
    }

    suspend fun createRoom(type: RoomType, name: String = type.label): Long {
        val sort = db.rooms().maxSort() + 1
        val id = db.rooms().insert(RoomPresets.preset(type, name, sort))
        db.shelves().insert(
            Shelf(
                roomId = id,
                name = "Mein Regal",
                wood = when (type) {
                    RoomType.LIBRARY, RoomType.FIREPLACE -> Wood.WALNUT.name
                    RoomType.PIANO -> Wood.MAHOGANY.name
                    RoomType.BEDROOM -> Wood.WHITE.name
                    RoomType.CONSERVATORY -> Wood.SAGE.name
                    RoomType.ATTIC -> Wood.PINE.name
                    else -> Wood.OAK.name
                },
                fileTypes = DefaultFileTypes,
            )
        )
        return id
    }

    suspend fun duplicateRoom(room: ReadingRoom): Long {
        val sort = db.rooms().maxSort() + 1
        return db.rooms().insert(room.copy(id = 0, name = room.name + " (Kopie)", sortIndex = sort, createdAt = System.currentTimeMillis()))
    }

    fun scanShelf(shelf: Shelf) {
        scope.launch { runCatching { scanner.scan(shelf, settings.app.value.onlineCovers) } }
    }

    fun scanAll() {
        scope.launch {
            db.shelves().allShelves().forEach { runCatching { scanner.scan(it, settings.app.value.onlineCovers) } }
        }
    }
}
