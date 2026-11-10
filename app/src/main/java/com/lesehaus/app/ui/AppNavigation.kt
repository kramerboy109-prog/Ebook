package com.lesehaus.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lesehaus.app.app
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.ui.favorites.FavoritesScreen
import com.lesehaus.app.ui.favorites.RereadScreen
import com.lesehaus.app.ui.home.HomeScreen
import com.lesehaus.app.ui.home.LibraryScreen
import com.lesehaus.app.ui.home.OnboardingScreen
import com.lesehaus.app.ui.notes.NotesScreen
import com.lesehaus.app.ui.reader.PdfReaderScreen
import com.lesehaus.app.ui.reader.ReaderScreen
import com.lesehaus.app.ui.room.RoomEditorScreen
import com.lesehaus.app.ui.room.RoomScreen
import com.lesehaus.app.ui.settings.SettingsScreen
import com.lesehaus.app.ui.shelf.ShelfEditorScreen
import com.lesehaus.app.ui.stats.StatsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Haus", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("favorites", "Lieblingsstellen", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab("notes", "Notizen", Icons.Outlined.StickyNote2, Icons.Filled.StickyNote2),
    Tab("stats", "Statistik", Icons.Outlined.Insights, Icons.Filled.Insights),
)

fun NavHostController.openBook(book: Book) {
    if (book.format == "pdf") navigate("pdf/${book.id}") else navigate("reader/${book.id}")
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val app = LocalContext.current.app
    val settings by app.settings.app.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = current == t.route,
                    onClick = {
                        if (current != t.route) nav.navigate(t.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (current == t.route) t.selectedIcon else t.icon, t.label) },
                    label = { Text(t.label, maxLines = 1) },
                )
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = if (settings.onboarded) "home" else "onboarding",
        enterTransition = { fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.97f) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.97f) },
    ) {
        composable("onboarding") {
            OnboardingScreen(onDone = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("home") {
            HomeScreen(
                bottomBar = bottomBar,
                onOpenRoom = { nav.navigate("room/$it") },
                onEditRoom = { nav.navigate("roomEdit/$it") },
                onOpenBook = { nav.openBook(it) },
                onSettings = { nav.navigate("settings") },
                onLibrary = { nav.navigate("library") },
                onFavorite = { nav.navigate("reread/$it") },
            )
        }
        composable("favorites") {
            FavoritesScreen(
                bottomBar = bottomBar,
                onReread = { id, collection, shuffle -> nav.navigate("reread/$id?collection=${collection ?: ""}&shuffle=$shuffle") },
                onOpenInBook = { f -> f.bookId?.let { id -> openFavorite(nav, id, f.chapter, f.start, f.end, f.pdfPage) } },
            )
        }
        composable("notes") {
            NotesScreen(bottomBar = bottomBar, onOpen = { a -> openFavorite(nav, a.bookId, a.chapter, a.start, a.end, a.pdfPage) })
        }
        composable("stats") { StatsScreen(bottomBar = bottomBar) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("library") { LibraryScreen(onBack = { nav.popBackStack() }, onOpenBook = { nav.openBook(it) }) }
        composable("room/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: 0L
            RoomScreen(
                roomId = id,
                onBack = { nav.popBackStack() },
                onEditRoom = { nav.navigate("roomEdit/$id") },
                onHallStyle = { nav.navigate("settings") },
                onEditShelf = { nav.navigate("shelfEdit/$it") },
                onOpenBook = { nav.openBook(it) },
            )
        }
        composable("roomEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: 0L
            RoomEditorScreen(
                roomId = id,
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack("home", inclusive = false) },
                onEditShelf = { nav.navigate("shelfEdit/$it") },
            )
        }
        composable("shelfEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            ShelfEditorScreen(shelfId = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable(
            "reader/{id}?text={text}&ch={ch}&off={off}&end={end}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("text") { type = NavType.BoolType; defaultValue = false },
                navArgument("ch") { type = NavType.IntType; defaultValue = -1 },
                navArgument("off") { type = NavType.IntType; defaultValue = 0 },
                navArgument("end") { type = NavType.IntType; defaultValue = -1 },
            ),
            enterTransition = { fadeIn(tween(200)) },
        ) { e ->
            val id = e.arguments?.getLong("id") ?: 0L
            ReaderScreen(
                bookId = id,
                pdfAsText = e.arguments?.getBoolean("text") ?: false,
                onBack = { nav.popBackStack() },
                onOpenPdfOriginal = { nav.navigate("pdf/$id") { popUpTo("reader/{id}?text={text}&ch={ch}&off={off}&end={end}") { inclusive = true } } },
                jumpChapter = e.arguments?.getInt("ch") ?: -1,
                jumpOffset = e.arguments?.getInt("off") ?: 0,
                jumpEnd = e.arguments?.getInt("end") ?: -1,
            )
        }
        composable(
            "pdf/{id}?page={page}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("page") { type = NavType.IntType; defaultValue = -1 }),
            enterTransition = { fadeIn(tween(200)) },
        ) { e ->
            val id = e.arguments?.getLong("id") ?: 0L
            val page = e.arguments?.getInt("page") ?: -1
            PdfReaderScreen(
                bookId = id,
                startPage = page,
                onBack = { nav.popBackStack() },
                onTextMode = { nav.navigate("reader/$id?text=true") { popUpTo("pdf/{id}?page={page}") { inclusive = true } } },
            )
        }
        composable(
            "reread/{id}?collection={collection}&shuffle={shuffle}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("collection") { type = NavType.StringType; defaultValue = "" },
                navArgument("shuffle") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { e ->
            RereadScreen(
                startId = e.arguments?.getLong("id") ?: 0L,
                collection = e.arguments?.getString("collection")?.takeIf { it.isNotBlank() },
                shuffle = e.arguments?.getBoolean("shuffle") ?: false,
                onBack = { nav.popBackStack() },
                onOpenInBook = { f -> f.bookId?.let { id -> openFavorite(nav, id, f.chapter, f.start, f.end, f.pdfPage) } },
            )
        }
    }
}

private fun openFavorite(nav: NavHostController, bookId: Long, chapter: Int, start: Int, end: Int, pdfPage: Int) {
    if (pdfPage >= 0 && chapter < 0) nav.navigate("pdf/$bookId?page=$pdfPage")
    else nav.navigate("reader/$bookId?ch=$chapter&off=$start&end=$end")
}
