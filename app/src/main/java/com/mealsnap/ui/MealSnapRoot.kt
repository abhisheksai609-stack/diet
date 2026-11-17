package com.mealsnap.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.mealsnap.ui.coach.ChatScreen
import com.mealsnap.ui.coach.CoachScreen
import com.mealsnap.ui.coach.ProfileScreen
import com.mealsnap.ui.components.AddMealSheet
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
    const val COACH = "coach"
    const val PROFILE = "profile"
    const val CHAT = "chat"
    const val EDITOR = "editor?mealId={mealId}&photo={photo}"

    /** A new meal from a photo file; a blank [photoPath] opens an empty manual-entry form. */
    fun editorNew(photoPath: String) = "editor?mealId=-1&photo=${Uri.encode(photoPath)}"
    fun editorExisting(mealId: Long) = "editor?mealId=$mealId&photo="
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Default.Home),
    Tab(Routes.HISTORY, "Trends", Icons.Default.DateRange),
    Tab(Routes.COACH, "Coach", Icons.Default.Favorite),
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

    var showAddSheet by remember { mutableStateOf(false) }

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

    fun launchGallery() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Opens Settings on top of whatever is showing, so Back returns to it. */
    fun openSettings() {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    MealSnapScaffold(
        route = route,
        onTab = { target ->
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onAddMeal = { showAddSheet = true },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenMeal = { navController.navigate(Routes.editorExisting(it)) },
                    onTakePhoto = { launchCamera() },
                    onUploadPhoto = { launchGallery() },
                    onManual = { navController.navigate(Routes.editorNew("")) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenMeal = { navController.navigate(Routes.editorExisting(it)) })
            }
            composable(Routes.COACH) {
                CoachScreen(
                    onEditProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                    onOpenSettings = { openSettings() },
                )
            }
            composable(Routes.PROFILE) { ProfileScreen(onDone = { navController.popBackStack() }) }
            composable(Routes.CHAT) { ChatScreen(onBack = { navController.popBackStack() }, onOpenSettings = { openSettings() }) }
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

    if (showAddSheet) {
        AddMealSheet(
            onDismiss = { showAddSheet = false },
            onTakePhoto = { showAddSheet = false; launchCamera() },
            onUploadPhoto = { showAddSheet = false; launchGallery() },
            onManual = { showAddSheet = false; navController.navigate(Routes.editorNew("")) },
        )
    }
}

/** App chrome: Fitbit-style bottom navigation and the "Add meal" button. Shared with the screenshot tests. */
@Composable
fun MealSnapScaffold(
    route: String?,
    onTab: (String) -> Unit,
    onAddMeal: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val showChrome = route == Routes.TODAY || route == Routes.HISTORY || route == Routes.COACH || route == Routes.SETTINGS

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showChrome) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { onTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Routes.TODAY) {
                ExtendedFloatingActionButton(
                    onClick = onAddMeal,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add meal") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        },
        content = content,
    )
}
