package com.mfp.filemanager

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.mfp.filemanager.databinding.ActivityMainBinding
import com.mfp.filemanager.data.SettingsRepository
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.activity.viewModels
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.ui.viewmodels.MainViewModel
import androidx.navigation.NavController
import androidx.navigation.navOptions
import android.content.res.Configuration
import coil.load
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import com.mfp.filemanager.data.FileOperationManager
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.clipboard.TransferStatus
import com.mfp.filemanager.ui.FileProgressController
import com.mfp.filemanager.data.OperationStatus
import com.mfp.filemanager.data.OperationType
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val audioViewModel: AudioViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var fileProgressController: FileProgressController

    private var isSwipeNavEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsRepository = SettingsRepository(applicationContext)
        val themeMode = runBlocking { settingsRepository.themeMode.first() }
        isSwipeNavEnabled = runBlocking { settingsRepository.swipeNavigationEnabled.first() }
        
        when (themeMode) {
            1 -> setTheme(R.style.Theme_FileManager) // Light
            2 -> setTheme(R.style.Theme_FileManager_Dark) // Dark (Grey)
            3 -> setTheme(R.style.Theme_FileManager_Amoled) // Amoled (Black)
            else -> {
                // System Default
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isNight) {
                    setTheme(R.style.Theme_FileManager_Dark)
                } else {
                    setTheme(R.style.Theme_FileManager)
                }
            }
        }

        super.onCreate(savedInstanceState)

        // Check Permissions
        try {
            if (!com.mfp.filemanager.security.PermissionHelper.hasStoragePermission(this) || 
                !com.mfp.filemanager.security.PermissionHelper.hasUsageStatsPermission(this)) {
                startActivity(android.content.Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        } catch (e: Exception) {
             e.printStackTrace()
             // Fallback to onboarding if check fails
             try {
                startActivity(android.content.Intent(this, OnboardingActivity::class.java))
                finish()
                return
             } catch (e2: Exception) {
                 e2.printStackTrace()
             }
        }
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            val menuIds = listOf(R.id.nav_home, R.id.nav_music, R.id.nav_trash, R.id.nav_settings)
            val rootPagerId = R.id.nav_main_pager

            // Define Navigation Listener
            val navigationListener = com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener { item ->
                val currentId = navController.currentDestination?.id ?: -1
                if (currentId != rootPagerId) {
                     val popped = navController.popBackStack(rootPagerId, false)
                     if (!popped) {
                         navController.navigate(rootPagerId)
                     }
                }
                
                val index = menuIds.indexOf(item.itemId)
                if (index != -1) {
                    mainViewModel.requestTabChange(index)
                    return@OnItemSelectedListener true
                }
                false
            }
            
            binding.bottomNavigation.setOnItemSelectedListener(navigationListener)
            
            binding.bottomNavigation.setOnItemReselectedListener { item ->
                 val currentId = navController.currentDestination?.id ?: -1
                 if (currentId != rootPagerId) {
                     navController.popBackStack(rootPagerId, false)
                 }
                 val index = menuIds.indexOf(item.itemId)
                 if (index != -1) mainViewModel.requestTabChange(index)
            }

            // Sync UI when ViewPager swipes (ViewModel source of truth)
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    mainViewModel.currentTab.collect { index ->
                        if (index in menuIds.indices) {
                            val id = menuIds[index]
                            if (binding.bottomNavigation.selectedItemId != id) {
                                // Avoid loop: Temporarily remove listener to update UI only
                                binding.bottomNavigation.setOnItemSelectedListener(null)
                                binding.bottomNavigation.selectedItemId = id
                                binding.bottomNavigation.setOnItemSelectedListener(navigationListener)
                            }
                        }
                    }
                }
            }
            
            // Handle Back Button specifically for Pager logic
            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (navController.currentDestination?.id == rootPagerId) {
                        if (mainViewModel.currentTab.value != 0) {
                            mainViewModel.requestTabChange(0)
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            })

            this.navController = navController

            setupMiniPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
            // Vital crash: If UI fails to load, we can't do much. 
            // Try restart or finish to avoid stubborn black screen?
            // startActivity(android.content.Intent(this, OnboardingActivity::class.java)) // Maybe downgrade to onboarding?
            // For now, allow text trace.
            android.widget.Toast.makeText(this, "Error initializing UI: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }

        // Initialize Repositories (To be injected into Fragments later via Hilt or custom Factory)
        // val settingsRepository = SettingsRepository(applicationContext) // Already initialized above

        // Observe Theme and apply dynamically - Redundant now as it's handled on activity recreation
        /*
        val settingsViewModel = SettingsViewModelFactory(settingsRepository).create(com.mfp.filemanager.ui.viewmodels.SettingsViewModel::class.java)
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                 settingsViewModel.settingsState.collect { state ->
                     val mode = when (state.themeMode) {
                         1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                         2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                         3 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES // AMOLED is also Dark
                         else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                     }
                     if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
                         androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                     }
                 }
            }
        }
        */
        
        audioViewModel.initializeController(this)
        
        // Optimize Mini Player reveal animation
        val transition = android.animation.LayoutTransition()
        transition.setDuration(android.animation.LayoutTransition.APPEARING, 300)
        transition.setDuration(android.animation.LayoutTransition.DISAPPEARING, 300)
        transition.enableTransitionType(android.animation.LayoutTransition.CHANGING)
        binding.bottomBarContainer.layoutTransition = transition
        setupGlassMorphism()
        
        // Initialize Progress Controller
        // 'file_progress_layout' in activity_main.xml includes '@layout/layout_file_progress'
        // ViewBinding automatically generates a field 'fileProgressLayout' of type 'LayoutFileProgressBinding'
        // So we can pass it directly.
        fileProgressController = FileProgressController(binding.fileProgressLayout) { isVisible ->
            if (isVisible) {
                 binding.bottomBarContainer.animate()
                    .translationY(binding.bottomBarContainer.height.toFloat().coerceAtLeast(200f))
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .start()
            } else {
                // Only show if we are not in player mode
                if (navController.currentDestination?.id != R.id.nav_player) {
                     binding.bottomBarContainer.animate()
                        .translationY(0f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }
        }
    }

    private fun setupMiniPlayer() {
        val playerNavOptions = navOptions {
            anim {
                enter = R.anim.pop_enter
                exit = R.anim.pop_exit
                popEnter = R.anim.pop_pop_enter
                popExit = R.anim.pop_pop_exit
            }
        }

        val miniPlayerAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMiniAlbumArt, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        // Helper for pop effect
        fun animateClick(view: View) {
             view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(80)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(80)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }

        // Set initial state to GONE to prevent flash on recreation
        binding.layoutMiniPlayer.visibility = View.GONE

        binding.layoutMiniPlayer.setOnClickListener {
            navController.navigate(R.id.nav_player, null, playerNavOptions)
        }

        binding.btnMiniPlayPause.setOnClickListener {
            animateClick(it)
            audioViewModel.togglePlayPause()
        }
        
        binding.btnMiniPrev.setOnClickListener {
            animateClick(it)
            audioViewModel.playPrevious()
        }

        binding.btnMiniNext.setOnClickListener {
            animateClick(it)
            audioViewModel.playNext()
        }

        binding.btnMiniClose.setOnClickListener {
            animateClick(it)
            audioViewModel.stopPlayer()
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    audioViewModel.currentTrack.collect { metadata ->
                        // Only show if we have valid metadata (Title is mandatory)
                        if (metadata != null && !metadata.title.isNullOrBlank()) {
                            if (!binding.layoutMiniPlayer.isVisible) {
                                binding.layoutMiniPlayer.visibility = View.VISIBLE
                                // Start from behind the bottom navigation bar with fade and scale
                                val hideTranslation = 500f
                                binding.layoutMiniPlayer.translationY = hideTranslation
                                binding.layoutMiniPlayer.alpha = 0f
                                binding.layoutMiniPlayer.scaleX = 0.8f
                                binding.layoutMiniPlayer.scaleY = 0.8f
                                
                                binding.layoutMiniPlayer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                            binding.textMiniTitle.text = metadata.title ?: "Unknown"
                            binding.textMiniArtist.text = metadata.artist ?: "Unknown"
                            binding.imgMiniAlbumArt.load(metadata.artworkUri) {
                                placeholder(R.drawable.ic_music_note_24)
                                error(R.drawable.ic_music_note_24)
                                listener(onSuccess = { _, result ->
                                    val drawable = result.drawable
                                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    
                                    bitmap?.let { b ->
                                        androidx.palette.graphics.Palette.from(b).generate { palette ->
                                            val color = palette?.getVibrantColor(android.graphics.Color.GRAY) 
                                                ?: palette?.getDominantColor(android.graphics.Color.GRAY)
                                                ?: android.graphics.Color.GRAY
                                            
                                            binding.imgMiniAlbumArt.strokeColor = android.content.res.ColorStateList.valueOf(color)
                                        }
                                    }
                                })
                            }
                        } else { // Metadata null (stopped or cleared)
                             if (binding.layoutMiniPlayer.isVisible) {
                                 val hideTranslation = 500f
                                 binding.layoutMiniPlayer.animate()
                                    .translationY(hideTranslation)
                                    .alpha(0f)
                                    .scaleX(0.8f)
                                    .scaleY(0.8f)
                                    .setDuration(250)
                                    .withEndAction { 
                                        binding.layoutMiniPlayer.visibility = View.GONE
                                        miniPlayerAnimator.cancel()
                                        binding.imgMiniAlbumArt.rotation = 0f
                                        // Reset alpha/scale for layout preview or next show
                                        binding.layoutMiniPlayer.alpha = 1f
                                        binding.layoutMiniPlayer.scaleX = 1f
                                        binding.layoutMiniPlayer.scaleY = 1f
                                    }
                                    .start()
                             }
                        }
                    }
                }

                launch {
                    audioViewModel.isPlaying.collect { isPlaying ->
                        binding.btnMiniPlayPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
                        )
                        
                        if (isPlaying) {
                            if (miniPlayerAnimator.isPaused) miniPlayerAnimator.resume() else miniPlayerAnimator.start()
                        } else {
                            miniPlayerAnimator.pause()
                        }

                        binding.btnMiniPlayPause.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(120)
                            .withEndAction {
                                binding.btnMiniPlayPause.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(120)
                                    .start()
                            }
                            .start()
                    }
                }

                launch {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val isPlayer = entry.destination.id == R.id.nav_player
                        
                        if (isPlayer) {
                             // Hide entire dock (MiniPlayer + NavBar)
                            binding.bottomBarContainer.animate()
                                .translationY(binding.bottomBarContainer.height.toFloat().coerceAtLeast(200f))
                                .setDuration(350)
                                .withEndAction {
                                    binding.bottomBarContainer.visibility = View.GONE
                                }
                                .start()
                        } else {
                            // Restore Dock
                            binding.bottomBarContainer.visibility = View.VISIBLE
                            binding.bottomBarContainer.animate()
                                .translationY(0f)
                                .setDuration(350)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                                
                            // Manage Taskbar Visibility based on Swipe Mode
                            binding.bottomNavContainer.visibility = if (isSwipeNavEnabled) View.GONE else View.VISIBLE
                        }
                    }
                }

                launch {
                    FileOperationManager.progress.collect { progress ->
                        val clipboard = FileOperationManager.clipboard.value
                        val transferStatus = progress?.status ?: TransferStatus.COMPLETED

                        val isRunning = transferStatus == TransferStatus.STARTING || transferStatus == TransferStatus.IN_PROGRESS
                        
                        val activeType = FileOperationManager.activeOperationType.value
                        val type = if (activeType != OperationType.NONE) {
                            activeType
                        } else {
                            when (clipboard?.operation) {
                                ClipboardOperation.COPY -> OperationType.COPY
                                ClipboardOperation.MOVE -> OperationType.MOVE
                                else -> OperationType.NONE
                            }
                        }


                        val p = if (progress != null && progress.totalBytes > 0) {
                            progress.transferredBytes.toFloat() / progress.totalBytes
                        } else {
                            0f
                        }

                        val status = OperationStatus(
                            isRunning = isRunning,
                            type = type,
                            progress = p,
                            processedCount = progress?.completedFiles ?: 0,
                            totalCount = progress?.totalFiles ?: 0
                        )

                        val finalStatus = if (transferStatus == TransferStatus.COMPLETED) {
                             status.copy(isRunning = false, progress = 1f)
                        } else {
                             status
                        }

                        if (::fileProgressController.isInitialized) {
                            fileProgressController.update(finalStatus)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.mfp.filemanager.data.cache.AppCache.clear()
    }





    private fun setupGlassMorphism() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Amplify blur to 80f for 'Liquid Glass' distortion
            val blurEffect = RenderEffect.createBlurEffect(
                80f, 80f,
                Shader.TileMode.CLAMP
            )
            binding.taskbarBlurLayer.setRenderEffect(blurEffect)
            // Apply same effect to mini player
            binding.miniPlayerBlurLayer.setRenderEffect(blurEffect)
        }
        

        
        // Observe Swipe Navigation Setting
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                 SettingsRepository(applicationContext).swipeNavigationEnabled.collect { enabled ->
                     isSwipeNavEnabled = enabled
                     binding.bottomNavContainer.visibility = if (enabled) View.GONE else View.VISIBLE
                     
                     // Adjust Mini Player Margin to make interface suitable
                     val params = binding.layoutMiniPlayer.layoutParams as android.widget.FrameLayout.LayoutParams
                     params.bottomMargin = if (enabled) {
                         val d = resources.displayMetrics.density
                         (24 * d).toInt() // Standard padding if Taskbar is gone
                     } else {
                         val d = resources.displayMetrics.density
                         (110 * d).toInt() // Float above Taskbar if visible
                     }
                     binding.layoutMiniPlayer.layoutParams = params
                 }
            }
        }
    }
}
