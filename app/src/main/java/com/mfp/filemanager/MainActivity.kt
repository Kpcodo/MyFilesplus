package com.mfp.filemanager

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import com.mfp.filemanager.ui.components.bounceClick
import androidx.compose.runtime.CompositionLocalProvider
import com.mfp.filemanager.ui.components.LocalAnimationSpeed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.ui.viewmodels.SettingsViewModel
import com.mfp.filemanager.ui.viewmodels.SettingsViewModelFactory
import com.mfp.filemanager.ui.screens.SettingsScreen
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.ui.screens.AllCategoriesScreen
import com.mfp.filemanager.ui.screens.FileBrowserScreen
import com.mfp.filemanager.ui.screens.FileListScreen
import com.mfp.filemanager.ui.screens.HomeScreen
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import com.mfp.filemanager.ui.screens.ImageViewerScreen
import com.mfp.filemanager.ui.screens.RecentsScreen
import com.mfp.filemanager.ui.AppPermissionHandler
import com.mfp.filemanager.ui.PermissionType
import com.mfp.filemanager.ui.theme.FileManagerTheme
import java.net.URLDecoder
import java.net.URLEncoder
import android.widget.Toast
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = FileRepository(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        val viewModelFactory = HomeViewModelFactory(repository, settingsRepository)

        // Coil ImageLoader is handled by FileManagerApplication
        // val imageLoader = ImageLoader.Builder(context = this)
        //     .components { add(VideoFrameDecoder.Factory()) }
        //     .crossfade(true)
        //     .build()
        // Coil.setImageLoader(imageLoader)

        // Settings Init
        val settingsViewModelFactory = SettingsViewModelFactory(settingsRepository)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory)
            val settingsState by settingsViewModel.settingsState.collectAsState()

            FileManagerTheme(
                themeMode = settingsState.themeMode,
                accentColor = settingsState.accentColor
            ) {
                // Animate background color change to smooth the transition
                val animatedBackgroundColor by animateColorAsState(
                    targetValue = MaterialTheme.colorScheme.background,
                    animationSpec = tween(300),
                    label = "BackgroundColorAnimation"
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = animatedBackgroundColor
                ) {
                    AppPermissionHandler(
                        onPermissionGranted = {
                            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                            CompositionLocalProvider(
                                LocalAnimationSpeed provides settingsState.animationSpeed
                            ) {
                                MainScreen(viewModel, settingsViewModel)
                            }
                        }
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HomeViewModel, settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    var showSearchOverlay by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsState by settingsViewModel.settingsState.collectAsState()

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Recents,
        BottomNavItem.Trash,
        BottomNavItem.Settings
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val blurModifier = if (showSearchOverlay && settingsState.isSwipeNavigationEnabled && settingsState.isBlurEnabled) {
             Modifier.blur(30.dp) 
        } else if (showSearchOverlay && settingsState.isBlurEnabled) {
             Modifier.blur(30.dp)
        } else {
             Modifier
        }
        
        // Simplify logic: if showing search AND blur enabled -> Blur. Else -> Empty Modifier.
        val effectiveBlur = if (showSearchOverlay && settingsState.isBlurEnabled) Modifier.blur(30.dp) else Modifier

        Scaffold(
            modifier = Modifier.then(effectiveBlur),
            topBar = {
                // If Swipe Navigation is enabled, Show a Global Top Bar
                if (settingsState.isSwipeNavigationEnabled) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    val title = when (currentRoute) {
                        "home" -> "Home"
                        "recents" -> "Recents"
                        "trash" -> "Bin"
                        "settings" -> "Settings"
                        else -> "File Manager" // Fallback or empty
                    }

                    // Only show for main tabs
                    if (currentRoute in listOf("home", "recents", "trash", "settings")) {
                       androidx.compose.material3.CenterAlignedTopAppBar(
                           title = { Text(title) }
                       )
                    }
                }
            },
            bottomBar = {
                // Hide Bottom Bar if Swipe Navigation is enabled
                if (!settingsState.isSwipeNavigationEnabled) {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                },
                                icon = { 
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon, 
                                        contentDescription = item.title,
                                        modifier = Modifier.bounceClick()
                                    )
                                 },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val swipeModifier = if (settingsState.isSwipeNavigationEnabled) {
                Modifier.pointerInput(Unit) {
                    var totalDrag = 0f
                    var hasNavigated = false
                    
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            totalDrag = 0f 
                            hasNavigated = false
                        },
                        onDragEnd = { 
                            totalDrag = 0f 
                            hasNavigated = false
                        }
                    ) { change, dragAmount ->
                        if (hasNavigated) return@detectHorizontalDragGestures
                        
                        totalDrag += dragAmount
                        
                        // Detect Swipe
                        val threshold = 50.dp.toPx()
                        if (abs(totalDrag) > threshold) {
                            change.consume()
                            val navBackStackEntry = navController.currentBackStackEntry
                            val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                            if (totalDrag < 0) { // Swipe Left (Next)
                                when (currentRoute) {
                                    "home" -> navController.navigate("recents") { popUpTo("home"); launchSingleTop = true }
                                    "recents" -> navController.navigate("trash") { popUpTo("home"); launchSingleTop = true }
                                    "trash" -> navController.navigate("settings") { popUpTo("home"); launchSingleTop = true }
                                }
                            } else { // Swipe Right (Previous)
                                when (currentRoute) {
                                    "recents" -> navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                                    "trash" -> navController.navigate("recents") { popUpTo("home"); launchSingleTop = true }
                                    "settings" -> navController.navigate("trash") { popUpTo("home"); launchSingleTop = true }
                                }
                            }
                            hasNavigated = true
                        }
                    }
                }
            } else {
                Modifier
            }

            AppNavigation(
                navController = navController,
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                modifier = Modifier.padding(innerPadding).then(swipeModifier),
                onRequestSearch = { showSearchOverlay = true },
                isSwipeEnabled = settingsState.isSwipeNavigationEnabled
            )
        }

        // Search Overlay
        com.mfp.filemanager.ui.SearchOverlay(
            viewModel = viewModel,
            isVisible = showSearchOverlay,
            onClose = { showSearchOverlay = false },
            onFileClick = { file ->
                showSearchOverlay = false
                // Handle file click navigation
                if (file.type == FileType.IMAGE) {
                    val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                    navController.navigate("image_viewer/$encodedPath")
                } else {
                    com.mfp.filemanager.data.FileUtils.openFile(context, file)
                }
            }
        )
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onRequestSearch: () -> Unit,
    isSwipeEnabled: Boolean
) {
    val context = LocalContext.current
    val settingsState by settingsViewModel.settingsState.collectAsState()
    
    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable(
            route = "home",
            enterTransition = {
                val from = initialState.destination.route
                if (from == "recents" || from == "trash" || from == "settings") {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                } else {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) // Fallback or distinct
                }
            },
            exitTransition = {
                val to = targetState.destination.route
                if (to == "recents" || to == "trash" || to == "settings") {
                     slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow))
                } else {
                     slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        ) {
            HomeScreen(
                viewModel = viewModel,
                onCategoryClick = { type ->
                    navController.navigate("fileList/${type.name}")
                },
                onInternalStorageClick = {
                    val rootPath = Environment.getExternalStorageDirectory().path
                    val encodedPath = URLEncoder.encode(rootPath, "UTF-8")
                    navController.navigate("file_browser/$encodedPath")
                },
                onViewAllClick = {
                    navController.navigate("categories_all")
                },
                onOtherStorageClick = {
                    val otherVolumes = viewModel.getOtherVolumes()
                    if (otherVolumes.isNotEmpty()) {
                        // For now, open the first available external volume
                        val volume = otherVolumes[0]
                        val rootPath = volume.file.path
                        val encodedPath = URLEncoder.encode(rootPath, "UTF-8")
                        val encodedTitle = URLEncoder.encode(volume.name, "UTF-8")
                        navController.navigate("file_browser/$encodedPath?title=$encodedTitle")
                    } else {
                        Toast.makeText(context, "No external storage found", Toast.LENGTH_SHORT).show()
                    }
                },
                onSearchClick = onRequestSearch,
                onForecastClick = { 
                    navController.navigate("forecast_detail")
                }
            )
        }

        composable(
            route = "recents",
            enterTransition = {
                val from = initialState.destination.route
                if (from == "trash" || from == "settings") {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                } else {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
            exitTransition = {
                 val to = targetState.destination.route
                 if (to == "trash" || to == "settings") {
                      slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow))
                 } else {
                      slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                 }
            }
        ) {
            RecentsScreen(
                viewModel = viewModel,
                showTopBar = !isSwipeEnabled,
                swipeDeleteEnabled = settingsState.swipeDeleteEnabled,
                swipeDeleteDirection = settingsState.swipeDeleteDirection,
                onFileClick = { file ->
                    if (file.type == FileType.IMAGE) {
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("image_viewer/$encodedPath")
                    } else {
                        com.mfp.filemanager.data.FileUtils.openFile(context, file)
                    }
                }
            )
        }

        composable("categories_all") {
            AllCategoriesScreen(
                onCategoryClick = { type -> navController.navigate("fileList/${type.name}") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = "trash",
            enterTransition = {
                val from = initialState.destination.route
                if (from == "settings") {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                } else {
                     slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
            exitTransition = {
                 val to = targetState.destination.route
                 if (to == "settings") {
                      slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow))
                 } else {
                      slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow))
                 }
            }
        ) {
            com.mfp.filemanager.ui.screens.TrashScreen(
                viewModel = viewModel,
                showTopBar = !isSwipeEnabled,
                onBack = { navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }}
            )
        }

        // Ghost Files Route Removed

        composable("forecast_detail") {
            com.mfp.filemanager.ui.screens.ForecastScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCleanupClick = { navController.navigate("cleanup_recommendations") }
            )
        }

        composable("cleanup_recommendations") {
            com.mfp.filemanager.ui.screens.CleanupRecommendationsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "settings",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) }
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                showTopBar = !isSwipeEnabled,
                onBack = { navController.navigate("home") }
            )
        }

        composable(
            route = "fileList/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) }
        ) { backStackEntry ->
            val typeName = backStackEntry.arguments?.getString("type")
            val type = runCatching { FileType.valueOf(typeName ?: "") }.getOrDefault(FileType.UNKNOWN)
            FileListScreen(
                viewModel = viewModel,
                fileType = type,
                onBack = { navController.popBackStack() },
                onFileClick = { file ->
                    if (file.type == FileType.IMAGE) {
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("image_viewer/$encodedPath")
                    } else {
                        com.mfp.filemanager.data.FileUtils.openFile(context, file)
                    }
                },
                onSearchClick = onRequestSearch
            )
        }

        composable(
            route = "file_browser/{path}?title={title}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
            ),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, spring(stiffness = Spring.StiffnessMediumLow)) }
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            val encodedTitle = backStackEntry.arguments?.getString("title")
            val title = if (encodedTitle != null) URLDecoder.decode(encodedTitle, "UTF-8") else null
            
            FileBrowserScreen(
                viewModel = viewModel,
                path = path,
                title = title,
                onBack = { navController.popBackStack() },
                onFileClick = { file ->
                    if (file.isDirectory) {
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        // Recursively pass the same title if it's the root of external storage?
                        // Or maybe not. If we click a folder, we generally want the folder name.
                        // But for "Other Storage" root -> shows "Samsung USB".
                        // Subfolder -> shows "MyFolder". 
                        // Implementation of FileBrowserTopAppBar handles this: 
                        // if title is null, it shows folder name. 
                        // So we DON'T pass title for subfolders, letting them show their own names.
                        navController.navigate("file_browser/$encodedPath")
                    } else {
                        if (file.type == FileType.IMAGE) {
                            val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                            navController.navigate("image_viewer/$encodedPath")
                        } else {
                            com.mfp.filemanager.data.FileUtils.openFile(context, file)
                        }
                    }
                },
                onDirectoryClick = { 
                    val encodedPath = URLEncoder.encode(it.path, "UTF-8")
                    navController.navigate("file_browser/$encodedPath")
                },
                onSearchClick = onRequestSearch
            )
        }

        composable(
            route = "image_viewer/{path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            ImageViewerScreen(
                viewModel = viewModel,
                path = path,
                onBack = { navController.popBackStack() }
            )
        }

        composable("other_storage") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Other Storage Devices")
            }
        }
    }
}

sealed class BottomNavItem(
    val route: String, 
    val selectedIcon: ImageVector, 
    val unselectedIcon: ImageVector,
    val title: String
) {
    object Home : BottomNavItem("home", Icons.Filled.Home, Icons.Outlined.Home, "Home")
    object Recents : BottomNavItem("recents", Icons.Filled.History, Icons.Outlined.History, "Recents") 
    object Trash : BottomNavItem("trash", Icons.Filled.Delete, Icons.Outlined.Delete, "Bin")
    object Settings : BottomNavItem("settings", Icons.Filled.Settings, Icons.Outlined.Settings, "Settings")
}
