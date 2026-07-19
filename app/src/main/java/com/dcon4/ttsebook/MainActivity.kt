package com.dcon4.ttsebook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.ui.screen.LibraryScreen
import com.dcon4.ttsebook.ui.screen.ReaderScreen
import com.dcon4.ttsebook.ui.screen.SearchScreen
import com.dcon4.ttsebook.ui.screen.SettingsScreen
import com.dcon4.ttsebook.ui.theme.TtsEbookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        DebugLogger.log(TAG, "Notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            TtsEbookTheme {
                TtsEbookNavHost()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun TtsEbookNavHost() {
    val navController = rememberNavController()
    var selectedBookId by remember { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onBookSelected = { bookId ->
                    selectedBookId = bookId
                    navController.navigate("reader/$bookId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            ReaderScreen(
                bookId = bookId,
                onNavigateToSearch = {
                    navController.navigate("search/$bookId")
                },
                onNavigateToBookmarks = { },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "search/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            SearchScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onResultSelected = { chapterIndex, paragraphIndex ->
                    navController.navigate("reader/$bookId/$chapterIndex/$paragraphIndex") {
                        popUpTo("reader/$bookId") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "reader/{bookId}/{chapterIndex}/{paragraphIndex}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("paragraphIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            val paragraphIndex = backStackEntry.arguments?.getInt("paragraphIndex") ?: 0
            ReaderScreen(
                bookId = bookId,
                initialChapterIndex = chapterIndex,
                initialParagraphIndex = paragraphIndex,
                onNavigateToSearch = {
                    navController.navigate("search/$bookId")
                },
                onNavigateToBookmarks = { },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
