package com.lesehaus.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein eingerichtetes Zimmer in Benny's Library (reines Ambiente + Ort für Bücherregale). */
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
    /** Türstil im Flur. */
    @ColumnInfo(defaultValue = "PANEL") val doorStyle: String = "PANEL",
    /** Türfarbe (0 = passend zum Zimmer). */
    @ColumnInfo(defaultValue = "0") val doorColor: Int = 0,
    /** Breite des Zimmers im Verhältnis zur Höhe (zum Umschauen). */
    @ColumnInfo(defaultValue = "1.5") val roomWidth: Float = 1.5f,
    /** true, sobald die frei platzierbare Einrichtung angelegt wurde. */
    @ColumnInfo(defaultValue = "0") val decorated: Boolean = false,
    /** Tiefe des Zimmers (in Wandhöhen) für die räumliche Ansicht. */
    @ColumnInfo(defaultValue = "2.2") val roomDepth: Float = 2.2f,
    /** Deckengestaltung: PLAIN, BEAMS, COFFERED. */
    @ColumnInfo(defaultValue = "PLAIN") val ceiling: String = "PLAIN",
    /** Holzvertäfelung im unteren Wandbereich. */
    @ColumnInfo(defaultValue = "0") val wainscot: Boolean = false,
    /** Bodentönung (0 = Standard). */
    @ColumnInfo(defaultValue = "0") val floorTint: Int = 0,
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
    /** Bauform des Regals im Zimmer. */
    @ColumnInfo(defaultValue = "TALL") val style: String = "TALL",
    /** Anordnung der Bücher (alphabetisch, Regenbogen, gemischt …). */
    @ColumnInfo(defaultValue = "ALPHA") val arrangement: String = "ALPHA",
    /** Position im Zimmer (x: Mitte als Anteil der Zimmerbreite, y: Unterkante als Anteil der Höhe); x < 0 = noch nicht platziert. */
    @ColumnInfo(defaultValue = "-1.0") val posX: Float = -1f,
    @ColumnInfo(defaultValue = "0.75") val posY: Float = 0.75f,
    @ColumnInfo(defaultValue = "1.0") val scale: Float = 1f,
    /** Dekoration in den Fächern (Pflanzen, Kerzen, Figuren). */
    @ColumnInfo(defaultValue = "1") val decor: Boolean = true,
    /** Wand im Raum: BACK, LEFT, RIGHT, FLOOR (freistehend); leer = alte Flachansicht-Koordinaten. */
    @ColumnInfo(defaultValue = "") val wall: String = "",
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

/** Ein frei platzierbarer Einrichtungsgegenstand (Möbel, Bild, Pflanze, Fenster …). */
@Entity(
    tableName = "room_items",
    foreignKeys = [ForeignKey(entity = ReadingRoom::class, parentColumns = ["id"], childColumns = ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
data class RoomItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val type: String,
    /** Mitte als Anteil der Zimmerbreite. */
    val x: Float,
    /** Unterkante als Anteil der Zimmerhöhe. */
    val y: Float,
    val scale: Float = 1f,
    val flip: Boolean = false,
    val z: Int = 0,
    /** Farbe (0 = Standard). */
    val color: Int = 0,
    val variant: Int = 0,
    /** Eigenes Bild (z. B. Foto im Rahmen). */
    val imagePath: String? = null,
    val label: String? = null,
    /** Wand im Raum: BACK, LEFT, RIGHT, FLOOR, CEILING; leer = alte Flachansicht-Koordinaten. */
    @ColumnInfo(defaultValue = "") val wall: String = "",
    /** Höhe über dem Boden für freistehende Dinge (z. B. Tasse auf dem Tisch). */
    @ColumnInfo(defaultValue = "0") val elev: Float = 0f,
)
