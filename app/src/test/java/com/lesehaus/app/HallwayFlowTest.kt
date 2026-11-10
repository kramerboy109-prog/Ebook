package com.lesehaus.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
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
        val root = compose.onRoot().fetchSemanticsNode().size
        // Die Tür steht mittig im Flur
        val p = Offset(root.width / 2f, root.height * 0.5f)
        compose.onRoot().captureRoboImage("screens/30_hall_before_tap.png")
        compose.onRoot().performTouchInput { click(Offset(p.x, p.y)) }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screens/31_after_door.png")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mein Regal", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // nächste Tür per Wischen
    }
}
