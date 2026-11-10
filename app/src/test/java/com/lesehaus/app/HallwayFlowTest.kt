package com.lesehaus.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import com.lesehaus.app.ui.room.RoomScreen
import com.lesehaus.app.ui.home.HomeScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.lesehaus.app.data.AppThemeMode
import com.lesehaus.app.data.RoomType
import com.lesehaus.app.ui.AppNavigation
import com.lesehaus.app.ui.home.hallDoorScreenCenter
import com.lesehaus.app.ui.theme.LesehausTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = com.github.takahirom.roborazzi.RobolectricDeviceQualifiers.Pixel7)
class HallwayFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapDoorOpensRoom() {
        val app = ApplicationProvider.getApplicationContext<LesehausApp>().container
        app.settings.updateApp { it.copy(onboarded = true, themeMode = AppThemeMode.DARK) }
        runBlocking { app.createRoom(RoomType.FIREPLACE); app.createRoom(RoomType.PIANO) }
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { AppNavigation() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Benny's Library").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val rooms = runBlocking { app.db.rooms().rooms() }
        val root = compose.onRoot().fetchSemanticsNode().size
        val navBar = 80f * compose.density.density
        val p = hallDoorScreenCenter(rooms, app.settings.app.value, 0, root.width.toFloat(), root.height - navBar)
        compose.onRoot().captureRoboImage("screens/30_hall_before_tap.png")
        compose.onRoot().performTouchInput { click(Offset(p.x, p.y)) }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/31_after_door.png")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mein Regal", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun swipeAndShot(name: String, from: Offset, to: Offset) {
        compose.onRoot().performTouchInput { swipe(Offset(from.x * width, from.y * height), Offset(to.x * width, to.y * height), 350) }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(name)
    }

    @Test
    fun hallShapesAndTurning() {
        val app = ApplicationProvider.getApplicationContext<LesehausApp>().container
        app.settings.updateApp { it.copy(onboarded = true, themeMode = AppThemeMode.DARK, hallSides = "LEFT", hallEnd = "STAIRS", hallWidth = 2.0f) }
        runBlocking { listOf(RoomType.FIREPLACE, RoomType.PIANO, RoomType.LIVING).forEach { app.createRoom(it) } }
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { AppNavigation() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Benny's Library").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/32_hall_left_stairs.png")
        // nach links drehen (Finger nach rechts ziehen), dann den Gang entlang
        swipeAndShot("screens/33_hall_turned.png", Offset(0.4f, 0.45f), Offset(0.62f, 0.46f))
        swipeAndShot("screens/34_hall_walked.png", Offset(0.5f, 0.6f), Offset(0.52f, 0.35f))
    }

    @Test
    fun hallEnds() {
        val app = ApplicationProvider.getApplicationContext<LesehausApp>().container
        app.settings.updateApp { it.copy(onboarded = true, themeMode = AppThemeMode.DARK) }
        runBlocking { listOf(RoomType.FIREPLACE, RoomType.PIANO).forEach { app.createRoom(it) } }
        var variant by mutableStateOf(0)
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { key(variant) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } }
        listOf("FIREPLACE", "ARCH", "MIRROR", "CLOCK").forEachIndexed { i, end ->
            app.settings.updateApp { it.copy(hallSides = if (i % 2 == 0) "BOTH" else "RIGHT", hallEnd = end, hallWidth = 1.2f + i * 0.3f) }
            variant = i + 1
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("screens/35_hall_end_${end.lowercase()}.png")
        }
    }

    @Test
    fun hallEditor() {
        val app = ApplicationProvider.getApplicationContext<LesehausApp>().container
        app.settings.updateApp { it.copy(onboarded = true, themeMode = AppThemeMode.DARK) }
        val hallId = runBlocking { app.createRoom(RoomType.FIREPLACE); app.ensureHall().id }
        compose.setContent { LesehausTheme(AppThemeMode.DARK) { RoomScreen(hallId, {}, {}, {}, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Einrichten").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("screens/36_hall_editor.png")
    }
}
