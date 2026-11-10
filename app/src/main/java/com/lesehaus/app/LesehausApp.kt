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

    /** Zimmer, aus dem zuletzt ein Buch geöffnet wurde (für den Zimmerklang beim Lesen). */
    @Volatile var currentRoomId: Long = -1L

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
        ensureDecorated(id)
        return id
    }

    /** Legt beim ersten Betreten die frei verschiebbare Einrichtung an (aus der Zimmervorlage). */
    private val decorMutex = Mutex()

    suspend fun ensureDecorated(roomId: Long): Unit = decorMutex.withLock { ensureDecoratedLocked(roomId) }

    private suspend fun ensureDecoratedLocked(roomId: Long) {
        val room = db.rooms().room(roomId) ?: return
        val shelves = db.shelves().shelves(roomId)
        if (room.decorated) {
            // neue, noch nicht platzierte Regale an eine freie Stelle stellen
            val unplaced = shelves.filter { it.posX < 0f }
            if (unplaced.isEmpty()) return
            val items = db.rooms().items(roomId)
            val occupied = ArrayList<Pair<Float, Float>>()
            items.forEach { i ->
                val t = com.lesehaus.app.ui.room.itemTypeOf(i.type) ?: return@forEach
                if (t.layer == com.lesehaus.app.ui.room.ItemLayer.FLOOR && t.h > 0.2f) {
                    val c = i.x * room.roomWidth
                    occupied += (c - t.w * i.scale / 2 to c + t.w * i.scale / 2)
                }
            }
            shelves.filter { it.posX >= 0f }.forEach { s ->
                val st = com.lesehaus.app.data.enumOr(s.style, com.lesehaus.app.ui.shelf.BookcaseStyle.TALL)
                val c = s.posX * room.roomWidth
                occupied += (c - st.w * s.scale / 2 to c + st.w * s.scale / 2)
            }
            unplaced.forEach { s ->
                val st = com.lesehaus.app.data.enumOr(s.style, com.lesehaus.app.ui.shelf.BookcaseStyle.TALL)
                val x = com.lesehaus.app.ui.room.freeSpot(st.w * s.scale + 0.05f, room.roomWidth, occupied)
                occupied += (x * room.roomWidth - st.w / 2 to x * room.roomWidth + st.w / 2)
                db.shelves().update(s.copy(posX = x, posY = if (st.onWall) 0.5f else com.lesehaus.app.ui.room.FLOOR_Y + 0.04f))
            }
            return
        }
        val setup = com.lesehaus.app.ui.room.defaultSetup(room, shelves)
        db.rooms().insertItems(setup.items)
        setup.shelves.forEach { db.shelves().update(it) }
        db.rooms().update(room.copy(decorated = true, roomWidth = setup.roomWidth))
    }

    /** Einrichtung auf die Vorlage zurücksetzen. */
    suspend fun resetDecoration(roomId: Long) {
        val room = db.rooms().room(roomId) ?: return
        db.rooms().items(roomId).forEach { db.rooms().deleteItem(it) }
        db.shelves().shelves(roomId).forEach { db.shelves().update(it.copy(posX = -1f)) }
        db.rooms().update(room.copy(decorated = false))
        ensureDecorated(roomId)
        Unit
    }

    suspend fun duplicateRoom(room: ReadingRoom): Long {
        val sort = db.rooms().maxSort() + 1
        val id = db.rooms().insert(room.copy(id = 0, name = room.name + " (Kopie)", sortIndex = sort, createdAt = System.currentTimeMillis()))
        db.rooms().insertItems(db.rooms().items(room.id).map { it.copy(id = 0, roomId = id) })
        return id
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
