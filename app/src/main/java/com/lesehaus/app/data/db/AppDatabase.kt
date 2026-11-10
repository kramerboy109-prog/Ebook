package com.lesehaus.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DayTotal(val day: String, val total: Long, val pages: Int)
data class BookTime(val bookId: Long, val total: Long)
data class ShelfCount(val shelfId: Long, val count: Int)

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY sortIndex, id")
    fun observeRooms(): Flow<List<ReadingRoom>>

    @Query("SELECT * FROM rooms ORDER BY sortIndex, id")
    suspend fun rooms(): List<ReadingRoom>

    @Query("SELECT * FROM rooms WHERE id = :id")
    fun observeRoom(id: Long): Flow<ReadingRoom?>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun room(id: Long): ReadingRoom?

    @Insert
    suspend fun insert(room: ReadingRoom): Long

    @Update
    suspend fun update(room: ReadingRoom)

    @Delete
    suspend fun delete(room: ReadingRoom)

    @Query("SELECT COALESCE(MAX(sortIndex), 0) FROM rooms")
    suspend fun maxSort(): Int

    @Query("SELECT * FROM room_items WHERE roomId = :roomId ORDER BY z, id")
    fun observeItems(roomId: Long): Flow<List<RoomItem>>

    @Query("SELECT * FROM room_items WHERE roomId = :roomId ORDER BY z, id")
    suspend fun items(roomId: Long): List<RoomItem>

    @Query("SELECT * FROM room_items ORDER BY roomId, z, id")
    fun observeAllItems(): Flow<List<RoomItem>>

    @Insert
    suspend fun insertItem(item: RoomItem): Long

    @Insert
    suspend fun insertItems(items: List<RoomItem>)

    @Update
    suspend fun updateItem(item: RoomItem)

    @Delete
    suspend fun deleteItem(item: RoomItem)
}

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelves WHERE roomId = :roomId ORDER BY sortIndex, id")
    fun observeShelves(roomId: Long): Flow<List<Shelf>>

    @Query("SELECT * FROM shelves ORDER BY roomId, sortIndex, id")
    fun observeAllShelves(): Flow<List<Shelf>>

    @Query("SELECT * FROM shelves ORDER BY roomId, sortIndex, id")
    suspend fun allShelves(): List<Shelf>

    @Query("SELECT * FROM shelves WHERE roomId = :roomId ORDER BY sortIndex, id")
    suspend fun shelves(roomId: Long): List<Shelf>

    @Query("SELECT * FROM shelves WHERE id = :id")
    suspend fun shelf(id: Long): Shelf?

    @Query("SELECT * FROM shelves WHERE id = :id")
    fun observeShelf(id: Long): Flow<Shelf?>

    @Insert
    suspend fun insert(shelf: Shelf): Long

    @Update
    suspend fun update(shelf: Shelf)

    @Delete
    suspend fun delete(shelf: Shelf)

    @Query("UPDATE shelves SET lastScan = :time WHERE id = :id")
    suspend fun setLastScan(id: Long, time: Long)

    @Query("SELECT shelfId, COUNT(*) AS count FROM shelf_books GROUP BY shelfId")
    fun observeCounts(): Flow<List<ShelfCount>>
}

@Dao
interface BookDao {
    @Query("SELECT b.* FROM books b INNER JOIN shelf_books sb ON sb.bookId = b.id WHERE sb.shelfId = :shelfId")
    fun observeBooksOfShelf(shelfId: Long): Flow<List<Book>>

    @Query("SELECT b.* FROM books b INNER JOIN shelf_books sb ON sb.bookId = b.id INNER JOIN shelves s ON s.id = sb.shelfId WHERE s.roomId = :roomId")
    fun observeBooksOfRoom(roomId: Long): Flow<List<Book>>

    @Query("SELECT sb.* FROM shelf_books sb INNER JOIN shelves s ON s.id = sb.shelfId WHERE s.roomId = :roomId")
    fun observeLinksOfRoom(roomId: Long): Flow<List<ShelfBook>>

    @Query("SELECT b.id FROM books b INNER JOIN shelf_books sb ON sb.bookId = b.id WHERE sb.shelfId = :shelfId")
    suspend fun bookIdsOfShelf(shelfId: Long): List<Long>

    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books")
    suspend fun all(): List<Book>

    @Query("SELECT * FROM books WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: Long): Book?

    @Query("SELECT * FROM books WHERE uri = :uri")
    suspend fun bookByUri(uri: String): Book?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: ShelfBook)

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun unlink(shelfId: Long, bookId: Long)

    @Query("DELETE FROM books WHERE id NOT IN (SELECT bookId FROM shelf_books)")
    suspend fun deleteOrphans()

    @Query("SELECT COUNT(*) FROM books WHERE status = 'FINISHED'")
    fun observeFinishedCount(): Flow<Int>

    @Query("UPDATE books SET chapter = :chapter, charOffset = :offset, pdfPage = :pdfPage, progress = :progress, totalPages = :total, lastOpenedAt = :now, status = CASE WHEN status = 'UNREAD' THEN 'READING' ELSE status END WHERE id = :id")
    suspend fun updatePosition(id: Long, chapter: Int, offset: Int, pdfPage: Int, progress: Float, total: Int, now: Long)

    @Query("UPDATE books SET readingTimeMs = readingTimeMs + :ms WHERE id = :id")
    suspend fun addReadingTime(id: Long, ms: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapter, charOffset, pdfPage")
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks")
    suspend fun allBookmarks(): List<Bookmark>

    @Insert
    suspend fun insertBookmark(b: Bookmark): Long

    @Update
    suspend fun updateBookmark(b: Bookmark)

    @Delete
    suspend fun deleteBookmark(b: Bookmark)

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY chapter, start, pdfPage")
    fun observeAnnotations(bookId: Long): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAllAnnotations(): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations")
    suspend fun allAnnotations(): List<Annotation>

    @Insert
    suspend fun insertAnnotation(a: Annotation): Long

    @Update
    suspend fun updateAnnotation(a: Annotation)

    @Delete
    suspend fun deleteAnnotation(a: Annotation)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<Favorite>>

    @Query("SELECT * FROM favorites")
    suspend fun allFavorites(): List<Favorite>

    @Query("SELECT * FROM favorites WHERE bookId = :bookId ORDER BY chapter, start")
    fun observeFavoritesOfBook(bookId: Long): Flow<List<Favorite>>

    @Insert
    suspend fun insertFavorite(f: Favorite): Long

    @Update
    suspend fun updateFavorite(f: Favorite)

    @Delete
    suspend fun deleteFavorite(f: Favorite)

    @Query("UPDATE favorites SET timesRead = timesRead + 1, lastReadAt = :now WHERE id = :id")
    suspend fun markFavoriteRead(id: Long, now: Long)
}

@Dao
interface StatsDao {
    @Insert
    suspend fun insert(s: ReadingSession)

    @Query("SELECT day, SUM(durationMs) AS total, SUM(pages) AS pages FROM reading_sessions GROUP BY day ORDER BY day DESC LIMIT 400")
    fun observeDays(): Flow<List<DayTotal>>

    @Query("SELECT bookId, SUM(durationMs) AS total FROM reading_sessions GROUP BY bookId ORDER BY total DESC")
    fun observeBookTimes(): Flow<List<BookTime>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions")
    fun observeTotal(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE bookId = :bookId AND startedAt > :since")
    suspend fun timeSince(bookId: Long, since: Long): Long

    @Query("SELECT COALESCE(SUM(pages), 0) FROM reading_sessions WHERE bookId = :bookId AND startedAt > :since")
    suspend fun pagesSince(bookId: Long, since: Long): Int
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rooms ADD COLUMN doorStyle TEXT NOT NULL DEFAULT 'PANEL'")
        db.execSQL("ALTER TABLE rooms ADD COLUMN doorColor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rooms ADD COLUMN roomWidth REAL NOT NULL DEFAULT 1.5")
        db.execSQL("ALTER TABLE rooms ADD COLUMN decorated INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shelves ADD COLUMN style TEXT NOT NULL DEFAULT 'TALL'")
        db.execSQL("ALTER TABLE shelves ADD COLUMN arrangement TEXT NOT NULL DEFAULT 'ALPHA'")
        db.execSQL("ALTER TABLE shelves ADD COLUMN posX REAL NOT NULL DEFAULT -1.0")
        db.execSQL("ALTER TABLE shelves ADD COLUMN posY REAL NOT NULL DEFAULT 0.75")
        db.execSQL("ALTER TABLE shelves ADD COLUMN scale REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE shelves ADD COLUMN decor INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE TABLE IF NOT EXISTS `room_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `roomId` INTEGER NOT NULL, `type` TEXT NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `scale` REAL NOT NULL, `flip` INTEGER NOT NULL, `z` INTEGER NOT NULL, `color` INTEGER NOT NULL, `variant` INTEGER NOT NULL, `imagePath` TEXT, `label` TEXT, FOREIGN KEY(`roomId`) REFERENCES `rooms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_room_items_roomId` ON `room_items` (`roomId`)")
    }
}

@Database(
    entities = [ReadingRoom::class, Shelf::class, RoomItem::class, Book::class, ShelfBook::class, Bookmark::class, Annotation::class, Favorite::class, ReadingSession::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rooms(): RoomDao
    abstract fun shelves(): ShelfDao
    abstract fun books(): BookDao
    abstract fun annotations(): AnnotationDao
    abstract fun stats(): StatsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lesehaus.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
