package com.lesehaus.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.lesehaus.app.data.AppThemeMode
import com.lesehaus.app.data.ReaderTheme
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.data.ShelfDisplay
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Favorite
import com.lesehaus.app.data.db.ReadingSession
import com.lesehaus.app.data.db.ShelfBook
import com.lesehaus.app.ui.favorites.FavoritesScreen
import com.lesehaus.app.ui.favorites.RereadScreen
import com.lesehaus.app.ui.home.HomeScreen
import com.lesehaus.app.ui.home.OnboardingScreen
import com.lesehaus.app.ui.notes.NotesScreen
import com.lesehaus.app.ui.reader.ReaderScreen
import com.lesehaus.app.ui.room.RoomEditorScreen
import com.lesehaus.app.ui.room.RoomScreen
import com.lesehaus.app.ui.shelf.ShelfEditorScreen
import com.lesehaus.app.ui.stats.StatsScreen
import com.lesehaus.app.ui.theme.LesehausTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<LesehausApp>().container

    private val sampleText = """
Erstes Kapitel

Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte, fand er sich in seinem Bett zu einem ungeheueren Ungeziefer verwandelt. Er lag auf seinem panzerartig harten Rücken und sah, wenn er den Kopf ein wenig hob, seinen gewölbten, braunen, von bogenförmigen Versteifungen geteilten Bauch, auf dessen Höhe sich die Bettdecke, zum gänzlichen Niedergleiten bereit, kaum noch erhalten konnte. Seine vielen, im Vergleich zu seinem sonstigen Umfang kläglich dünnen Beine flimmerten ihm hilflos vor den Augen.

»Was ist mit mir geschehen?« dachte er. Es war kein Traum. Sein Zimmer, ein richtiges, nur etwas zu kleines Menschenzimmer, lag ruhig zwischen den vier wohlbekannten Wänden. Über dem Tisch, auf dem eine auseinandergepackte Musterkollektion von Tuchwaren ausgebreitet war – Samsa war Reisender –, hing das Bild, das er vor kurzem aus einer illustrierten Zeitschrift ausgeschnitten und in einem hübschen, vergoldeten Rahmen untergebracht hatte.

Gregors Blick richtete sich dann zum Fenster, und das trübe Wetter – man hörte Regentropfen auf das Fensterblech aufschlagen – machte ihn ganz melancholisch. »Wie wäre es, wenn ich noch ein wenig weiterschliefe und alle Narrheiten vergäße«, dachte er, aber das war gänzlich undurchführbar, denn er war gewöhnt, auf der rechten Seite zu schlafen, konnte sich aber in seinem gegenwärtigen Zustand nicht in diese Lage bringen.

Mit welcher Kraft er sich auch auf die rechte Seite warf, immer wieder schaukelte er in die Rückenlage zurück. Er versuchte es wohl hundertmal, schloß die Augen, um die zappelnden Beine nicht sehen zu müssen, und ließ erst ab, als er in der Seite einen noch nie gefühlten, leichten, dumpfen Schmerz zu fühlen begann.

Zweites Kapitel

Erst in der Abenddämmerung erwachte Gregor aus seinem schweren ohnmachtsähnlichen Schlaf. Er wäre gewiß nicht viel später auch ohne Störung erwacht, denn er fühlte sich genügend ausgeruht und ausgeschlafen.
""".trimIndent()

    private var roomId = 0L
    private var shelfId = 0L
    private var bookId = 0L

    private val titles = listOf(
        "Die Verwandlung" to "Franz Kafka", "Faust" to "Johann Wolfgang von Goethe", "Effi Briest" to "Theodor Fontane",
        "Der Schimmelreiter" to "Theodor Storm", "Emilia Galotti" to "Gotthold Ephraim Lessing", "Die Leiden des jungen Werthers" to "Johann Wolfgang von Goethe",
        "Der Sandmann" to "E. T. A. Hoffmann", "Aus dem Leben eines Taugenichts" to "Joseph von Eichendorff", "Kinder- und Hausmärchen" to "Brüder Grimm",
        "Woyzeck" to "Georg Büchner", "Der Process" to "Franz Kafka", "Michael Kohlhaas" to "Heinrich von Kleist", "Immensee" to "Theodor Storm",
        "Buddenbrooks" to "Thomas Mann",
    )

    @Before
    fun seed() = runBlocking {
        app.settings.updateApp { it.copy(onboarded = true, themeMode = AppThemeMode.LIGHT, roomAnimations = false) }
        roomId = app.createRoom(RoomType.FIREPLACE)
        app.createRoom(RoomType.PIANO)
        app.createRoom(RoomType.BEDROOM)
        shelfId = app.db.shelves().shelves(roomId).first().id
        val dir = ApplicationProvider.getApplicationContext<LesehausApp>().filesDir
        val txt = File(dir, "Kafka - Die Verwandlung.txt").apply { writeText(sampleText) }
        titles.forEachIndexed { i, (t, a) ->
            val uri = if (i == 0) Uri.fromFile(txt).toString() else "file:///nirgendwo/$i.epub"
            val id = app.db.books().insert(
                Book(
                    uri = uri, fileName = if (i == 0) txt.name else "$t.epub", format = if (i == 0) "txt" else "epub", title = t, author = a,
                    fileSize = 50_000L * (i + 3), progress = if (i % 4 == 1) 0.42f else 0f, status = when (i % 4) { 1 -> "READING"; 3 -> "FINISHED"; else -> "UNREAD" },
                    lastOpenedAt = if (i % 4 == 1) System.currentTimeMillis() - i * 1000L else 0, addedAt = System.currentTimeMillis() - 10L * 86_400_000,
                )
            )
            if (i == 0) bookId = id
            app.db.books().link(ShelfBook(shelfId, id))
        }
        app.db.annotations().insertFavorite(Favorite(bookId = null, bookTitle = "Faust", bookAuthor = "Johann Wolfgang von Goethe", chapter = 0, start = 0, end = 0, text = "Zwei Seelen wohnen, ach! in meiner Brust, die eine will sich von der andern trennen.", kind = "LINE"))
        app.db.annotations().insertFavorite(Favorite(bookId = null, bookTitle = "Die Leiden des jungen Werthers", bookAuthor = "Johann Wolfgang von Goethe", chapter = 0, start = 0, end = 0, text = "Ich kehre in mich selbst zurück und finde eine Welt!", kind = "SELECTION", collection = "Gedanken", note = "Passt zu jedem Regentag."))
        app.db.annotations().insertAnnotation(Annotation(bookId = bookId, chapter = 0, start = 14, end = 80, text = "Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte", color = 0xFFFFE27A.toInt(), note = "Was für ein erster Satz!"))
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (d in 0 until 9) {
            val t = System.currentTimeMillis() - d * 86_400_000L
            app.db.stats().insert(ReadingSession(bookId = bookId, day = fmt.format(Date(t)), startedAt = t, durationMs = (8 + (d * 7) % 30) * 60_000L, pages = 12))
        }
    }

    private fun waitForText(text: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun onboarding() {
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { OnboardingScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/01_onboarding.png")
    }

    @Test
    fun home() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } }
        waitForText("Kaminzimmer")
        compose.onRoot().captureRoboImage("screens/02_home.png")
    }

    @Test
    fun room() {
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { RoomScreen(roomId, {}, {}, {}, {}) } }
        waitForText("Mein Regal")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/03_room_covers.png")
        runBlocking { app.db.shelves().shelf(shelfId)?.let { app.db.shelves().update(it.copy(displayMode = ShelfDisplay.SPINES.name)) } }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/04_room_spines.png")
    }

    @Test
    fun editors() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { RoomEditorScreen(roomId, {}, {}, {}) } }
        waitForText("Zimmer gestalten")
        compose.onRoot().captureRoboImage("screens/05_room_editor.png")
    }

    @Test
    fun shelfEditor() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { ShelfEditorScreen(shelfId) {} } }
        waitForText("Bücherregal einrichten")
        compose.onRoot().captureRoboImage("screens/06_shelf_editor.png")
    }

    @Test
    fun reader() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { ReaderScreen(bookId, false, {}, {}) } }
        waitForText("Gregor Samsa", 30_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/07_reader.png")
        // Menü öffnen (Mitte antippen)
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/08_reader_controls.png")
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        // Wort lange drücken → Auswahlwerkzeuge
        compose.onAllNodesWithText("Gregor Samsa", substring = true).fetchSemanticsNodes()
        compose.onRoot().performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.3f)) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/09_reader_selection.png")
    }

    @Test
    fun readerCozyNight() {
        app.settings.updateReader { it.copy(theme = ReaderTheme.FIRELIGHT, cozyMode = true, fontKey = "garamond") }
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { ReaderScreen(bookId, false, {}, {}) } }
        waitForText("Gregor Samsa", 30_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/10_reader_cozy.png")
    }

    @Test
    fun favoritesAndNotes() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { FavoritesScreen({}, { _, _, _ -> }, {}) } }
        waitForText("Zwei Seelen")
        compose.onRoot().captureRoboImage("screens/11_favorites.png")
    }

    @Test
    fun reread() {
        val id = runBlocking { app.db.annotations().allFavorites().first().id }
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { RereadScreen(id, null, false, {}, {}) } }
        waitForText("Seelen")
        compose.onRoot().captureRoboImage("screens/12_reread.png")
    }

    @Test
    fun notes() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { NotesScreen({}) {} } }
        waitForText("erster Satz")
        compose.onRoot().captureRoboImage("screens/13_notes.png")
    }

    @Test
    fun stats() {
        compose.setContent { LesehausTheme(AppThemeMode.LIGHT) { StatsScreen {} } }
        waitForText("Lesestatistik")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/14_stats.png")
    }
}
