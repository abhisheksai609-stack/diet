package com.mealsnap.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mealsnap.MealSnapApplication
import com.mealsnap.R
import com.mealsnap.ui.editor.MealEditorScreen
import com.mealsnap.ui.history.HistoryScreen
import com.mealsnap.ui.settings.SettingsScreen
import com.mealsnap.ui.today.TodayScreen
import kotlinx.coroutines.launch
import java.io.File

object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?mealId={mealId}&photo={photo}"

    fun editorNew(photoPath: String) = "editor?mealId=-1&photo=${Uri.encode(photoPath)}"
    fun editorExisting(mealId: Long) = "editor?mealId=$mealId&photo="
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Default.Home),
    Tab(Routes.HISTORY, "History", Icons.Default.DateRange),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun MealSnapRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as MealSnapApplication
    val photos = app.container.photos
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    // Survives the activity being recreated while the camera app is in the foreground.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCapture
        pendingCapture = null
        if (path != null) {
            if (success) navController.navigate(Routes.editorNew(path)) else File(path).delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                photos.copyToCapture(uri)?.let { navController.navigate(Routes.editorNew(it.absolutePath)) }
            }
        }
    }

    fun launchCamera() {
        val file = photos.newCaptureFile().also { it.createNewFile() }
        pendingCapture = file.absolutePath
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    val showChrome = route == Routes.TODAY || route == Routes.HISTORY || route == Routes.SETTINGS

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Routes.TODAY) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_gallery), contentDescription = "Pick a photo from gallery")
                    }
                    ExtendedFloatingActionButton(
                        onClick = ::launchCamera,
                        icon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null) },
                        text = { Text("Snap meal") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(onOpenMeal = { navController.navigate(Routes.editorExisting(it)) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenMeal = { navController.navigate(Routes.editorExisting(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("mealId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("photo") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                MealEditorScreen(
                    mealId = entry.arguments?.getLong("mealId") ?: -1L,
                    photo = entry.arguments?.getString("photo").orEmpty(),
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
