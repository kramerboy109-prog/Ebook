package com.lesehaus.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lesehaus.app.data.db.AppDatabase
import com.lesehaus.app.ui.reader.DialogSplitter
import com.lesehaus.app.ui.favorites.QuoteCardStyle
import com.lesehaus.app.ui.favorites.renderQuoteCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MigrationAndNarrationTest {

    @Test
    fun migratesVersion1Database() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            listOf(
            "CREATE TABLE IF NOT EXISTS `rooms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `wallColor` INTEGER NOT NULL, `wallPattern` TEXT NOT NULL, `floorType` TEXT NOT NULL, `windowView` TEXT NOT NULL, `weather` TEXT NOT NULL, `lightLevel` REAL NOT NULL, `lightWarmth` REAL NOT NULL, `furniture` TEXT NOT NULL, `ambientSound` TEXT NOT NULL, `curtainColor` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `shelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `roomId` INTEGER NOT NULL, `name` TEXT NOT NULL, `wood` TEXT NOT NULL, `folders` TEXT NOT NULL, `recursive` INTEGER NOT NULL, `fileTypes` TEXT NOT NULL, `displayMode` TEXT NOT NULL, `sortOrder` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `lastScan` INTEGER NOT NULL, FOREIGN KEY(`roomId`) REFERENCES `rooms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_shelves_roomId` ON `shelves` (`roomId`)",
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL, `format` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT NOT NULL, `publisher` TEXT NOT NULL, `coverPath` TEXT, `coverSource` TEXT NOT NULL, `coverColor` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `progress` REAL NOT NULL, `chapter` INTEGER NOT NULL, `charOffset` INTEGER NOT NULL, `pdfPage` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `status` TEXT NOT NULL, `rating` INTEGER NOT NULL, `readingTimeMs` INTEGER NOT NULL, `onlineCoverTried` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_uri` ON `books` (`uri`)",
            "CREATE TABLE IF NOT EXISTS `shelf_books` (`shelfId` INTEGER NOT NULL, `bookId` INTEGER NOT NULL, PRIMARY KEY(`shelfId`, `bookId`), FOREIGN KEY(`shelfId`) REFERENCES `shelves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_shelf_books_bookId` ON `shelf_books` (`bookId`)",
            "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `charOffset` INTEGER NOT NULL, `pdfPage` INTEGER NOT NULL, `label` TEXT NOT NULL, `snippet` TEXT NOT NULL, `progress` REAL NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)",
            "CREATE TABLE IF NOT EXISTS `annotations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `text` TEXT NOT NULL, `color` INTEGER NOT NULL, `style` TEXT NOT NULL, `note` TEXT, `pdfPage` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)",
            "CREATE TABLE IF NOT EXISTS `favorites` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER, `bookTitle` TEXT NOT NULL, `bookAuthor` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `text` TEXT NOT NULL, `kind` TEXT NOT NULL, `pdfPage` INTEGER NOT NULL, `collection` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `timesRead` INTEGER NOT NULL, `lastReadAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_favorites_bookId` ON `favorites` (`bookId`)",
            "CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `day` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `pages` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_day` ON `reading_sessions` (`day`)",
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)",
            ).forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO rooms (name, type, wallColor, wallPattern, floorType, windowView, weather, lightLevel, lightWarmth, furniture, ambientSound, curtainColor, sortIndex, createdAt) VALUES ('Kaminzimmer', 'FIREPLACE', 0, 'DAMASK', 'PARQUET', 'AUTO', 'SNOW', 0.5, 0.8, 'FIREPLACE,CAT', 'FIRE', 0, 0, 0)")
            db.execSQL("INSERT INTO shelves (roomId, name, wood, folders, recursive, fileTypes, displayMode, sortOrder, sortIndex, lastScan) VALUES (1, 'Mein Regal', 'WALNUT', '', 1, 'epub', 'COVERS', 'TITLE', 0, 0)")
            db.version = 1
        }
        val room = androidx.room.Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(com.lesehaus.app.data.db.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val r = room.rooms().rooms().single()
            assertEquals("Kaminzimmer", r.name)
            assertEquals(false, r.decorated)
            assertEquals("PANEL", r.doorStyle)
            val s = room.shelves().allShelves().single()
            assertEquals("TALL", s.style)
            assertEquals(-1f, s.posX)
            assertTrue(room.rooms().items(r.id).isEmpty())
        }
        room.close()
    }

    @Test
    fun splitsDialogue() {
        val text = "»Komm herein«, sagte sie. Dann lachte er: „Gern!“"
        val segs = DialogSplitter.split(text, 0, text.length, BooleanArray(1), CharArray(1))
        val parts = segs.map { text.substring(it.start, it.end).trim() to it.dialog }
        assertEquals(listOf("»Komm herein«" to true, ", sagte sie. Dann lachte er:" to false, "„Gern!“" to true), parts)
    }

    @Test
    fun rendersQuoteCards() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File("screens").mkdirs()
        QuoteCardStyle.entries.forEach { st ->
            val bmp = renderQuoteCard(ctx, "Zwei Seelen wohnen, ach! in meiner Brust, die eine will sich von der andern trennen.", "Faust", "Johann Wolfgang von Goethe", st)
            assertEquals(1080, bmp.width)
            File("screens/20_quote_${st.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
