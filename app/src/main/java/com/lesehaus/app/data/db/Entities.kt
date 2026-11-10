package com.lesehaus.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein eingerichtetes Zimmer im Lesehaus (reines Ambiente + Ort für Bücherregale). */
@Entity(tableName = "rooms")
data class ReadingRoom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val wallColor: Int,
    val wallPattern: String,
    val floorType: String,
    val windowView: String,
    val weather: String,
    val lightLevel: Float,
    val lightWarmth: Float,
    val furniture: String,
    val ambientSound: String,
    val curtainColor: Int,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val furnitureList: List<String> get() = furniture.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Ein Bücherregal in einem Zimmer. Durchsucht beliebig viele Ordner nach Büchern. */
@Entity(
    tableName = "shelves",
    foreignKeys = [ForeignKey(
        entity = ReadingRoom::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("roomId")],
)
data class Shelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val name: String,
    val wood: String,
    /** Ordner-URIs (Storage Access Framework), zeilenweise getrennt. */
    val folders: String = "",
    val recursive: Boolean = true,
    /** Erlaubte Dateiendungen, kommagetrennt. */
    val fileTypes: String,
    val displayMode: String = "COVERS",
    val sortOrder: String = "TITLE",
    val sortIndex: Int = 0,
    val lastScan: Long = 0,
) {
    val folderList: List<String> get() = folders.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val fileTypeSet: Set<String> get() = fileTypes.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}

@Entity(tableName = "books", indices = [Index(value = ["uri"], unique = true)])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val format: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val language: String = "",
    val publisher: String = "",
    val coverPath: String? = null,
    val coverSource: String = "NONE",
    val coverColor: Int = 0,
    val fileSize: Long = 0,
    val lastModified: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0,
    val progress: Float = 0f,
    val chapter: Int = 0,
    val charOffset: Int = 0,
    val pdfPage: Int = 0,
    val totalPages: Int = 0,
    val status: String = "UNREAD",
    val rating: Int = 0,
    val readingTimeMs: Long = 0,
    val onlineCoverTried: Boolean = false,
)

@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelfId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = Shelf::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class ShelfBook(val shelfId: Long, val bookId: Long)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapter: Int,
    val charOffset: Int,
    val pdfPage: Int = -1,
    val label: String = "",
    val snippet: String = "",
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Markierung, Unterstreichung oder Notiz an einer Textstelle. */
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapter: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val color: Int,
    val style: String = "HIGHLIGHT",
    val note: String? = null,
    val pdfPage: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** „Gefällt mir“-Stelle: Zeile, Passage, Seite oder freie Auswahl – zum wiederholten Lesen. */
@Entity(tableName = "favorites", indices = [Index("bookId")])
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long?,
    val bookTitle: String,
    val bookAuthor: String,
    val chapter: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val kind: String,
    val pdfPage: Int = -1,
    val collection: String = "Lieblingsstellen",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val timesRead: Int = 0,
    val lastReadAt: Long = 0,
)

@Entity(tableName = "reading_sessions", indices = [Index("day"), Index("bookId")])
data class ReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val day: String,
    val startedAt: Long,
    val durationMs: Long,
    val pages: Int,
)
