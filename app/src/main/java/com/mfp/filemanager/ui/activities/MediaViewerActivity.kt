package com.mfp.filemanager.ui.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import android.content.res.Configuration
import com.mfp.filemanager.data.MediaItem
import com.mfp.filemanager.databinding.ActivityMediaViewerBinding
import com.mfp.filemanager.ui.adapters.MediaPagerAdapter
import com.mfp.filemanager.ui.fragments.MediaVideoFragment

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding
    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { toggleControls(false) }
    
    private var areControlsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Window Insets to Top Bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBarOverlay) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }
        
        // Postpone enter transition for shared elements
        postponeEnterTransition()

        val mediaItems = intent.getParcelableArrayListExtra<MediaItem>("media_items") ?: emptyList()
        val startIndex = intent.getIntExtra("start_index", 0)

        if (mediaItems.isEmpty()) {
            finish()
            return
        }

        val adapter = MediaPagerAdapter(this, mediaItems)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startIndex, false)
        
        setupUI(mediaItems[startIndex])
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setupUI(mediaItems[position])
                // Reset controls visibility on page change
                showControls()
            }
        })
        
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnShare.setOnClickListener {
            val currentItem = mediaItems[binding.viewPager.currentItem]
            com.mfp.filemanager.data.FileUtils.shareFile(this, 
                com.mfp.filemanager.data.FileModel(
                    0L, 
                    currentItem.name, 
                    currentItem.uri.path ?: "", 
                    0L, 0L, null, 
                    if (currentItem is MediaItem.Video) com.mfp.filemanager.data.FileType.VIDEO else com.mfp.filemanager.data.FileType.IMAGE, 
                    false
                )
            )
        }
        
        binding.btnInfo.setOnClickListener {
            val currentItem = mediaItems[binding.viewPager.currentItem]
            showDetailsDialog(currentItem)
        }
        
        // Start postponed transition
        binding.viewPager.post {
            startPostponedEnterTransition()
        }
        
        hideControlsDelayed()
    }
    
    private fun setupUI(item: MediaItem) {
        binding.textTitle.text = item.name
    }

    fun toggleControls(show: Boolean? = null) {
        val targetState = show ?: !areControlsVisible
        areControlsVisible = targetState
        
        // Animate Top Bar
        // Animate Top Bar
        if (targetState) {
            // Cancel any running animation
            binding.topBarOverlay.animate().cancel()
            
            binding.topBarOverlay.visibility = android.view.View.VISIBLE
            binding.topBarOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            showSystemUI()
        } else {
            binding.topBarOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    binding.topBarOverlay.visibility = android.view.View.GONE
                }
                .start()
            hideSystemUI()
        }
        
        // Notify current fragment (mainly regarding bottom/center video controls)
        val currentFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}")
        if (currentFragment is MediaVideoFragment) {
            currentFragment.setControlsVisibility(targetState)
        }
        
        if (targetState) {
            hideControlsDelayed()
        } else {
            handler.removeCallbacks(hideControlsRunnable)
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Redraw system UI to ensure correct flags are kept
        showSystemUI()
        // If controls were hidden, re-hide them to sync state
        if (!areControlsVisible) {
            hideSystemUI()
        }
    }
    
    fun showControls() {
        toggleControls(true)
    }
    
    fun hideControlsDelayed() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }
    
    fun cancelAutoHide() {
        handler.removeCallbacks(hideControlsRunnable)
    }
    
    private fun showSystemUI() {
        // Show status bar and navigation bar
        val controller = WindowInsetsControllerCompat(window, binding.root)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // In Landscape, Hide Status Bar, Show Nav Bar
            controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            // In Portrait, Show Both
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        
        // Ensure status bar content (icons) are contrasting
        // Since our top bar overlay is dark (#80000000), we want Light Status Bar content to be FALSE (i.e. White icons)
        // regardless of system theme
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        
        // Ensure background is transparent to show our overlay behind it
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    
    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun showDetailsDialog(item: MediaItem) {
        val details = "Name: ${item.name}\nPath: ${item.uri.path}"
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
}
