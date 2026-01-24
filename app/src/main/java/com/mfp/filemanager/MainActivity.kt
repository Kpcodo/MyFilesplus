package com.mfp.filemanager

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicNote
import java.io.File

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
import com.mfp.filemanager.ui.animations.bounceClick
import androidx.compose.runtime.CompositionLocalProvider
import com.mfp.filemanager.ui.animations.LocalAnimationSpeed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
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
import com.mfp.filemanager.ui.screens.FileBrowserScreen
import com.mfp.filemanager.ui.screens.HomeScreen
import com.mfp.filemanager.ui.screens.RecentsScreen
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import com.mfp.filemanager.ui.screens.MediaViewerScreen
import com.mfp.filemanager.ui.screens.TextViewerScreen
import com.mfp.filemanager.ui.screens.MusicScreen
import com.mfp.filemanager.ui.screens.FullPlayerScreen
import com.mfp.filemanager.security.AppPermissionHandler
import com.mfp.filemanager.ui.theme.FileManagerTheme
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.ui.components.MiniPlayer
import androidx.compose.runtime.LaunchedEffect
import java.net.URLDecoder
import java.net.URLEncoder
import android.widget.Toast
import com.mfp.filemanager.ui.animations.MotionHardwareProvider
import com.mfp.filemanager.ui.animations.AppMotion
import com.mfp.filemanager.ui.animations.LocalMotionScale
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
                                MotionHardwareProvider {
                                    MainScreen(viewModel, settingsViewModel)
                                }
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
        BottomNavItem.Music,
        BottomNavItem.Trash,
        BottomNavItem.Settings
    )

    val audioViewModel: AudioViewModel = viewModel()
    val isPlaying by audioViewModel.isPlaying.collectAsState()
    val currentTrack by audioViewModel.currentTrack.collectAsState()

    LaunchedEffect(Unit) {
        audioViewModel.initializeController(context)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    Box(modifier = Modifier.fillMaxSize()) {
        // Animate blur radius for smooth transitions
        // Reduce blur radius slightly for better performance
        val blurRadius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (showSearchOverlay && settingsState.isBlurEnabled) 6.dp else 0.dp,
            animationSpec = androidx.compose.animation.core.tween(250),
            label = "BlurAnimation"
        )
        val effectiveBlur = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier

        Scaffold(
            modifier = Modifier.fillMaxSize().then(effectiveBlur),
            topBar = {
                // If Swipe Navigation is enabled, Show a Global Top Bar
                if (settingsState.isSwipeNavigationEnabled) {
                    
                    val title = when (currentRoute) {
                        "home" -> "Home"
                        "music" -> "Music"
                        "trash" -> "Bin"
                        "settings" -> "Settings"
                        else -> "File Manager" // Fallback or empty
                    }

                    // Only show for main tabs
                    if (currentRoute in listOf("home", "music", "trash", "settings")) {
                       androidx.compose.material3.CenterAlignedTopAppBar(
                           title = { Text(title) }
                       )
                    }
                }
            },
            bottomBar = {
                // NavigationBar only, to keep padding constant and avoid jumps
                if (!settingsState.isSwipeNavigationEnabled && currentRoute != "full_player") {
                    NavigationBar {
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
                                    "home" -> navController.navigate("music") { popUpTo("home"); launchSingleTop = true }
                                    "music" -> navController.navigate("trash") { popUpTo("home"); launchSingleTop = true }
                                    "trash" -> navController.navigate("settings") { popUpTo("home"); launchSingleTop = true }
                                }
                            } else { // Swipe Right (Previous)
                                when (currentRoute) {
                                    "music" -> navController.navigate("home") { popUpTo("home"); launchSingleTop = true }
                                    "trash" -> navController.navigate("music") { popUpTo("home"); launchSingleTop = true }
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

            // High Performance Content Layout
            val hasMiniPlayer = currentTrack != null && currentRoute != "full_player"
            
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavigation(
                    navController = navController,
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    modifier = Modifier
                        .padding(
                            bottom = innerPadding.calculateBottomPadding() + (if (hasMiniPlayer) 64.dp else 0.dp),
                            top = innerPadding.calculateTopPadding()
                        )
                        .then(swipeModifier),
                    onRequestSearch = { showSearchOverlay = true },
                    isSwipeEnabled = settingsState.isSwipeNavigationEnabled,
                    audioViewModel = audioViewModel
                )

                // Floating MiniPlayer Overlay - Decoupled from Scaffold layout to prevent jitter
                AnimatedVisibility(
                    visible = hasMiniPlayer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(300))
                ) {
                    MiniPlayer(
                        metadata = currentTrack,
                        isPlaying = isPlaying,
                        onTogglePlay = { audioViewModel.togglePlayPause() },
                        onNext = { audioViewModel.playNext() },
                        onPrevious = { audioViewModel.playPrevious() },
                        onClick = { navController.navigate("full_player") }
                    )
                }
            }
        }

        // Search Overlay
        com.mfp.filemanager.ui.search.SearchOverlay(
            viewModel = viewModel,
            isVisible = showSearchOverlay,
            onClose = { showSearchOverlay = false },
            onFileClick = { file ->
                showSearchOverlay = false
                if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                    viewModel.setMediaContext(viewModel.searchResults.value)
                    val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                    navController.navigate("media_viewer/$encodedPath")
                } else if (file.type == FileType.AUDIO) {
                    audioViewModel.playFile(File(file.path))
                    navController.navigate("full_player")
                } else {
                    // Navigate to the file's location (parent folder)
                    val targetFile = java.io.File(file.path)
                    val folderPath = if (file.isDirectory) file.path else targetFile.parent ?: file.path
                    val encodedPath = URLEncoder.encode(folderPath, "UTF-8")
                    navController.navigate("file_browser/$encodedPath")
                }
            }
        )

        // Unified Global Operation Progress Banner
        val operationStatus by viewModel.operationStatus.collectAsState()

        // Only show padding if bottom bar is visible (not media viewer, and swipe nav disabled)
        val hasBottomBar = !settingsState.isSwipeNavigationEnabled && currentRoute in listOf("home", "trash", "settings")
        
        com.mfp.filemanager.ui.components.OperationProgressBanner(
            status = operationStatus,
            onCancel = { viewModel.cancelOperation() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasBottomBar) 80.dp else 16.dp)
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
    isSwipeEnabled: Boolean,
    audioViewModel: AudioViewModel
) {
    val context = LocalContext.current
    val settingsState by settingsViewModel.settingsState.collectAsState()
    val motionScale = LocalMotionScale.current
    val fadeDuration = (AppMotion.BaseDuration.Fast * motionScale).toInt()
    
    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable(
            route = "home",
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) }
        ) {
            HomeScreen(
                viewModel = viewModel,
                onInternalStorageClick = {
                    val rootPath = Environment.getExternalStorageDirectory().path
                    val encodedPath = URLEncoder.encode(rootPath, "UTF-8")
                    navController.navigate("file_browser/$encodedPath")
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
                },
                onRecentFileClick = { file ->
                    if (file.type == FileType.AUDIO) {
                        audioViewModel.playFile(File(file.path))
                        navController.navigate("full_player")
                    } else if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                        viewModel.setMediaContext(viewModel.recentFiles.value)
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("media_viewer/$encodedPath")
                    } else if (com.mfp.filemanager.data.FileUtils.isTextFile(file)) {
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("text_viewer/$encodedPath")
                    } else {
                        com.mfp.filemanager.data.FileUtils.openFile(context, file)
                    }
                },
                onViewAllRecentsClick = {
                     navController.navigate("recents")
                }
            )
        }

        composable(
            route = "recents",
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { fadeOut(tween(fadeDuration)) }
        ) {
            RecentsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFileClick = { file ->
                    if (file.type == FileType.AUDIO) {
                        audioViewModel.playFile(File(file.path))
                        navController.navigate("full_player")
                    } else if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                        viewModel.setMediaContext(viewModel.recentFiles.value)
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("media_viewer/$encodedPath")
                    } else if (com.mfp.filemanager.data.FileUtils.isTextFile(file)) {
                        val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                        navController.navigate("text_viewer/$encodedPath")
                    } else {
                        com.mfp.filemanager.data.FileUtils.openFile(context, file)
                    }
                }
            )
        }


        composable(
            route = "trash",
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) }
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
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { fadeOut(tween(fadeDuration)) }
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                showTopBar = !isSwipeEnabled,
                onBack = { navController.navigate("home") }
            )
        }


        composable(
            route = "file_browser/{path}?title={title}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
            ),
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { fadeOut(tween(fadeDuration)) }
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
                        if (file.type == FileType.AUDIO) {
                            audioViewModel.playFile(File(file.path))
                            navController.navigate("full_player")
                        } else if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                            viewModel.setMediaContext(viewModel.files.value)
                            val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                            navController.navigate("media_viewer/$encodedPath")
                        } else if (com.mfp.filemanager.data.FileUtils.isTextFile(file)) {
                            val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                            navController.navigate("text_viewer/$encodedPath")
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
            route = "media_viewer/{path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween((fadeDuration * 0.7f).toInt())) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { fadeOut(tween((fadeDuration * 0.7f).toInt())) }
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            MediaViewerScreen(
                viewModel = viewModel,
                initialPath = path,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "text_viewer/{path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { fadeOut(tween(fadeDuration)) }
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            TextViewerScreen(
                path = path,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "music",
            enterTransition = { fadeIn(tween(fadeDuration)) },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { with(AppMotion.Transitions) { enterFromFullScreen(motionScale) } },
            popExitTransition = { fadeOut(tween(fadeDuration)) }
        ) {
            MusicScreen(
                viewModel = audioViewModel,
                onTrackClick = { navController.navigate("full_player") }
            )
        }

        composable(
            route = "full_player",
            enterTransition = { with(AppMotion.Transitions) { slideInUpEnter(motionScale) } },
            exitTransition = { fadeOut(tween(fadeDuration)) },
            popEnterTransition = { fadeIn(tween(fadeDuration)) },
            popExitTransition = { with(AppMotion.Transitions) { exitToLibrary(motionScale) } }
        ) {
            FullPlayerScreen(
                viewModel = audioViewModel,
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
    object Music : BottomNavItem("music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote, "Music")
    object Trash : BottomNavItem("trash", Icons.Filled.Delete, Icons.Outlined.Delete, "Bin")
    object Settings : BottomNavItem("settings", Icons.Filled.Settings, Icons.Outlined.Settings, "Settings")
}
