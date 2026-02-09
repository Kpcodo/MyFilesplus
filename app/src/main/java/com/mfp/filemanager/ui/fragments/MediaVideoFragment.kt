package com.mfp.filemanager.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import com.mfp.filemanager.R
import com.mfp.filemanager.databinding.FragmentMediaVideoBinding
import com.mfp.filemanager.ui.activities.MediaViewerActivity
import java.util.Locale

class MediaVideoFragment : Fragment() {

    private var _binding: FragmentMediaVideoBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var mediaUri: android.net.Uri? = null
    private var hasStartedPlayback = false
    
    // Sensor for detecting rotation direction
    private var orientationListener: android.view.OrientationEventListener? = null
    private var lastDetectedOrientation: Int = android.view.OrientationEventListener.ORIENTATION_UNKNOWN
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            if (player?.isPlaying == true) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    companion object {
        private const val ARG_MEDIA_ITEM = "media_item"

        fun newInstance(item: com.mfp.filemanager.data.MediaItem.Video): MediaVideoFragment {
            val fragment = MediaVideoFragment()
            val args = Bundle()
            args.putParcelable(ARG_MEDIA_ITEM, item)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("DEPRECATION")
        val item = arguments?.getParcelable(ARG_MEDIA_ITEM) as? com.mfp.filemanager.data.MediaItem.Video ?: return
        mediaUri = item.uri

        // Load thumbnail initially
        binding.previewImage.load(item.uri) {
            crossfade(true)
        }
        
        setupControls()
        
        // Initialize orientation listener to track physical device rotation
        orientationListener = object : android.view.OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                lastDetectedOrientation = orientation
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Do NOT auto-play on resume unless we were Playing.
        // For ViewPager, we might only want to initialize when visible?
        // ViewPager2 handles Fragment lifecycle correctly (RESUMED only when visible).
        
        if (player == null) {
            initializePlayer()
        }
        
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        orientationListener?.disable()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext())
                .build()
                .apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                }
            binding.playerView.player = player
            
            val mediaItem = MediaItem.fromUri(mediaUri!!)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        updateDuration()
                        binding.previewImage.isVisible = false
                    }
                    if (playbackState == Player.STATE_ENDED) {
                         showPreviewState()
                         player?.seekTo(0L)
                         player?.pause()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val icon = if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
                    binding.btnPlayPauseSmall.setImageResource(icon)
                    binding.btnPlayPause.setImageResource(icon)
                    binding.btnPlayPauseLand.setImageResource(icon)
                    
                    if (isPlaying) {
                        startProgressUpdater()
                        (activity as? MediaViewerActivity)?.hideControlsDelayed()
                    } else {
                        handler.removeCallbacks(updateProgressRunnable)
                        
                        // Only show controls if we are strictly PAUSED or ENDED.
                        // If we are buffering (e.g. during seek), do NOT show controls.
                        val state = player?.playbackState
                        val isBuffering = state == Player.STATE_BUFFERING
                        
                        if (!isBuffering) {
                            (activity as? MediaViewerActivity)?.showControls()
                        }
                    }
                }
            })
        }
    }
    
    private fun releasePlayer() {
        player?.release()
        player = null
        handler.removeCallbacks(updateProgressRunnable)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        // Center Play Button
        // Center Play Button
        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) {
                pauseVideo()
            } else {
                playVideo()
            }
        }
        
        // Small Play Button (Bottom)
        binding.btnPlayPauseSmall.setOnClickListener {
            if (player?.isPlaying == true) {
                pauseVideo()
            } else {
                playVideo()
            }
        }
        
        binding.btnRewind.setOnClickListener {
            player?.seekTo((player?.currentPosition ?: 0L) - 10000)
            updateProgress()
            (activity as? MediaViewerActivity)?.hideControlsDelayed()
        }
        
        binding.btnForward.setOnClickListener {
            player?.seekTo((player?.currentPosition ?: 0L) + 10000)
            updateProgress()
            (activity as? MediaViewerActivity)?.hideControlsDelayed()
        }
        
        binding.btnRotate.setOnClickListener {
             val currentOrientation = requireActivity().resources.configuration.orientation
             if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                 // Detect which landscape side to rotate to based on physical orientation
                 if (lastDetectedOrientation in 60..120) {
                     // 90 degrees: Top of phone is to the right -> Reverse Landscape
                     requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                 } else if (lastDetectedOrientation in 240..300) {
                     // 270 degrees: Top of phone is to the left -> Landscape (Standard)
                     requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                 } else {
                     // Upright, Upside down, or Unknown -> Default to standard Landscape
                     requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                 }
             } else {
                 requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
             }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
             override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                 if (fromUser) binding.textCurrentTime.text = formatTime(progress.toLong())
             }
             override fun onStartTrackingTouch(seekBar: SeekBar?) {
                 (activity as? MediaViewerActivity)?.cancelAutoHide()
             }
             override fun onStopTrackingTouch(seekBar: SeekBar?) {
                 player?.seekTo(seekBar?.progress?.toLong() ?: 0L)
                 if (player?.isPlaying == true) {
                     (activity as? MediaViewerActivity)?.hideControlsDelayed()
                 }
             }
        })

        // Landscape Controls
        binding.btnPlayPauseLand.setOnClickListener {
            if (player?.isPlaying == true) {
                pauseVideo()
            } else {
                playVideo()
            }
        }
        
        binding.btnRewindLand.setOnClickListener {
            player?.seekTo((player?.currentPosition ?: 0L) - 10000)
            updateProgress()
            (activity as? MediaViewerActivity)?.hideControlsDelayed()
        }
        
        binding.btnForwardLand.setOnClickListener {
            player?.seekTo((player?.currentPosition ?: 0L) + 10000)
            updateProgress()
            (activity as? MediaViewerActivity)?.hideControlsDelayed()
        }

        // Initialize Gesture Detector for custom controls
        val gestureDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            private var isVolumeGesture = false
            private var isBrightnessGesture = false
            
            override fun onDown(e: android.view.MotionEvent): Boolean {
                // Determine gesture type on down event for subsequent scrolls
                val width = binding.playerView.width
                if (e.x < width / 2) {
                    isBrightnessGesture = true
                    isVolumeGesture = false
                } else {
                    isBrightnessGesture = false
                    isVolumeGesture = true
                }
                return true
            }

            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                (activity as? MediaViewerActivity)?.toggleControls()
                return true
            }

            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val width = binding.playerView.width
                if (e.x < width / 2) {
                    // Rewind
                    player?.seekTo((player?.currentPosition ?: 0L) - 10000)
                    updateProgress()
                    showDoubleTapFeedback(true)
                } else {
                    // Forward
                    player?.seekTo((player?.currentPosition ?: 0L) + 10000)
                    updateProgress()
                    showDoubleTapFeedback(false)
                }
                (activity as? MediaViewerActivity)?.hideControlsDelayed()
                return true
            }

            override fun onScroll(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null) return false
                
                // Disable gestures (Volume/Brightness) if playback hasn't started
                // This prevents accidental triggers when swiping through the ViewPager
                if (!hasStartedPlayback) return false
                
                // Disable ViewPager interception
                binding.playerView.parent.requestDisallowInterceptTouchEvent(true)
                
                val deltaY = distanceY / binding.playerView.height // Normalize scroll distance
                
                if (isBrightnessGesture) {
                    adjustBrightness(deltaY)
                } else if (isVolumeGesture) {
                    adjustVolume(deltaY)
                }
                
                return true
            }
        })
        
        binding.playerView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                // Hide gesture feedback on release
                 binding.gestureFeedbackOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                     binding.gestureFeedbackOverlay.isVisible = false
                 }.start()
                 // Re-enable parent interception? usually handled by new DOWN event sequence
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }
    
    private fun showDoubleTapFeedback(isRewind: Boolean) {
        val view = if (isRewind) binding.rewindOverlay else binding.forwardOverlay
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .withEndAction {
                view.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setStartDelay(200)
                    .withEndAction { view.visibility = View.GONE }
                    .start()
            }
            .start()
    }
    
    private fun adjustBrightness(delta: Float) {
        val lp = requireActivity().window.attributes
        var brightness = if (lp.screenBrightness < 0) {
             android.provider.Settings.System.getInt(
                requireContext().contentResolver, 
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } else {
            lp.screenBrightness
        }
        
        brightness = (brightness + delta).coerceIn(0.01f, 1f)
        lp.screenBrightness = brightness
        requireActivity().window.attributes = lp
        
        // Rotate the sun icon. "50% slower" interpretation:
        // A full range (0-100) often maps to significant rotation (e.g. 180 or 360).
        // We'll map 0-100% to 0-100 degrees roughly, or slighly more.
        binding.gestureIcon.rotation = brightness * 90f 
        
        showGestureFeedback(R.drawable.ic_feather_sun, (brightness * 100).toInt())
    }
    
    private fun adjustVolume(delta: Float) {
        // Reset rotation for volume
        binding.gestureIcon.rotation = 0f
        
        val audioManager = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        
        // Use a finer granularity for volume adjustments if possible, but step is usually 1
        // We accumulate delta if needed, but for now simple addition
        // 1 full swipe height = 1.0 delta. Let's scale it.
        // change = delta * maxVolume (so full screen swipe traverses full volume range)
        
        val change = delta * maxVolume
        // Since we are called repeatedly, we might want to track accumulated change, 
        // but simple delta application works if "distanceY" is per-event. 
        // Note: distanceY in onScroll is "distance along Y axis that has been scrolled since the last call to onScroll".
        // So yes, it is incremental.
        
        // HOWEVER, adjusting system volume repeatedly with small floats vs integers might be choppy.
        // We will just set it based on a "virtual" float tracking if we wanted perfect smoothness,
        // but direct setStreamVolume is easiest.
        
        val newVolume = (currentVolume + change).coerceIn(0f, maxVolume.toFloat())
        
        // Only apply if index changed to avoid spamming system calls?
        // Actually, let's just use adjustStreamVolume? No, setStreamVolume is better precise control if we had float support.
        // AudioManager only supports Int.
        // We will try to add sensitivity.
        
        if (java.lang.Math.abs(change) > 0.1) {
             val direction = if (change > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
             // adjustStreamVolume shows system UI by default usually. 
             // We want to suppress system UI and show ours.
             // setStreamVolume(..., flags = 0)
             
             // We need to maintain a "float" volume locally to smooth it out?
             // Let's just do a simple calculation based on range.
        }
        
        // Better approach: Calculate target Percentage
        // But we only have delta.
        
        // Let's implement a simpler "step" approach.
        // We need a class-level variable to accumulate small scrolls.
        // For simplicity in this functional context:
        val step = if (delta > 0) 1 else -1
        if (java.lang.Math.abs(delta * 100) > 1) { // Sensitivity threshold
             audioManager.adjustStreamVolume(
                 android.media.AudioManager.STREAM_MUSIC,
                 if (delta > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
                 0 // No flags, hide system UI
             )
        }
        
        val newVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val percentage = (newVol.toFloat() / maxVolume.toFloat() * 100).toInt()
        
        val icon = if (percentage == 0) R.drawable.ic_feather_volume_x else R.drawable.ic_feather_volume_2
        showGestureFeedback(icon, percentage)
    }
    
    private fun showGestureFeedback(iconRes: Int, progress: Int) {
        binding.gestureFeedbackOverlay.visibility = View.VISIBLE
        binding.gestureFeedbackOverlay.alpha = 1f
        binding.gestureIcon.setImageResource(iconRes)
        binding.gestureProgress.progress = progress
    }
    
    private fun playVideo() {
        player?.play()
        binding.previewImage.isVisible = false
        hasStartedPlayback = true
        showPlaybackControls()
    }
    
    private fun pauseVideo() {
        player?.pause()
        // No UI change here, managed by listener
    }

    private fun showPreviewState() {
        hasStartedPlayback = false
        binding.previewImage.isVisible = true
        
        // Reset Center Button
        binding.btnPlayPause.alpha = 1f
        binding.btnPlayPause.visibility = View.VISIBLE
        binding.btnPlayPause.translationX = 0f
        binding.btnPlayPause.translationY = 0f
        binding.btnPlayPause.scaleX = 1f
        binding.btnPlayPause.scaleY = 1f
        
        // Hide Controls
        binding.bottomControls.visibility = View.GONE
        binding.btnRewind.isVisible = false
        binding.btnForward.isVisible = false
        binding.btnPlayPauseSmall.setImageResource(R.drawable.ic_pause_24) // Reset default

        (activity as? MediaViewerActivity)?.showControls()
    }
    
    private fun showPlaybackControls() {
        binding.btnRewind.isVisible = true
        binding.btnForward.isVisible = true
        
        // Ensure bottom controls visible (but alpha 0 initially for animation)
        binding.bottomControls.alpha = 0f
        binding.bottomControls.visibility = View.VISIBLE
        
        // Slide In Bottom Controls -> Changed to Fade In
        binding.bottomControls.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
            
        // Initial setup for orientation
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
             binding.centerControls.isVisible = false
             binding.landscapeControls.isVisible = true
             binding.btnPlayPauseSmall.isVisible = false
        } else {
             binding.centerControls.isVisible = true
             binding.landscapeControls.isVisible = false
             binding.btnPlayPauseSmall.isVisible = true
             
             binding.btnPlayPause.alpha = 1f
             binding.btnPlayPause.visibility = View.VISIBLE
             binding.btnPlayPause.scaleX = 1f
             binding.btnPlayPause.scaleY = 1f
        }
            
        (activity as? MediaViewerActivity)?.hideControlsDelayed()
    }
    
    // Called by Activity when it toggles global controls
    fun setControlsVisibility(visible: Boolean) {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (visible) {
             if (hasStartedPlayback) {
                 binding.bottomControls.animate().alpha(1f).withStartAction { 
                     binding.bottomControls.isVisible = true 
                 }.start()
             }
             
             if (isLandscape && hasStartedPlayback) {
                 // Landscape Playing
                 binding.centerControls.isVisible = false
                 binding.landscapeControls.isVisible = true
                 binding.btnPlayPauseSmall.isVisible = false
             } else {
                 // Portrait OR Preview
                 binding.centerControls.animate().alpha(1f).withStartAction {
                     binding.centerControls.isVisible = true
                 }.start()
                 
                 binding.landscapeControls.isVisible = false
                 binding.btnPlayPauseSmall.isVisible = true
                 
                 // Ensure Center Button is reset
                 binding.btnPlayPause.scaleX = 1f
                 binding.btnPlayPause.scaleY = 1f
                 binding.btnPlayPause.alpha = 1f
             }
        } else {
             // Hide controls
             binding.bottomControls.animate().alpha(0f).withEndAction { 
                 binding.bottomControls.isVisible = false 
             }.start()
             
             // Hide center controls as well (Portrait included)
             binding.centerControls.animate().alpha(0f).withEndAction {
                 binding.centerControls.isVisible = false
             }.start()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateUIForOrientation()
    }

    private fun updateUIForOrientation() {
        if (!isAdded) return
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        // Determine if controls should be visible based on current state
        val controlsLikelyVisible = binding.bottomControls.visibility == View.VISIBLE && binding.bottomControls.alpha > 0.01f
        // If playback hasn't started, controls (at least center) are always visible in preview
        val visible = controlsLikelyVisible || !hasStartedPlayback
        
        if (visible) {
             if (isLandscape && hasStartedPlayback) {
                binding.centerControls.isVisible = false
                binding.landscapeControls.isVisible = true
                binding.btnPlayPauseSmall.isVisible = false
             } else {
                binding.centerControls.isVisible = true
                binding.landscapeControls.isVisible = false
                binding.btnPlayPauseSmall.isVisible = true
                
                binding.btnPlayPause.alpha = 1f
                binding.btnPlayPause.visibility = View.VISIBLE
                binding.btnPlayPause.scaleX = 1f
                binding.btnPlayPause.scaleY = 1f
             }
        } else {
             // Keep everything hidden if it was hidden
             // But layout structure should be ready?
             // If hidden, it doesn't matter much, but we can set the "Ground Truth" for when it fades in
             if (isLandscape && hasStartedPlayback) {
                binding.centerControls.isVisible = false
                binding.landscapeControls.isVisible = true
                binding.btnPlayPauseSmall.isVisible = false
             } else {
                binding.landscapeControls.isVisible = false
                binding.btnPlayPauseSmall.isVisible = true
                // Center controls might need to be hidden if we are in "Hidden" state
             }
        }
    }

    private fun startProgressUpdater() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun updateProgress() {
        player?.let {
            val position = it.currentPosition
            binding.textCurrentTime.text = formatTime(position)
            binding.seekBar.progress = position.toInt()
        }
    }
    
    private fun updateDuration() {
         player?.let {
             val duration = it.duration
             if (duration > 0) {
                 binding.seekBar.max = duration.toInt()
                 binding.textTotalTime.text = formatTime(duration)
             }
         }
    }

    private fun formatTime(millis: Long): String {
        return if (millis <= 0) "0:00" else {
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            if (hours > 0) {
                 String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                 String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }
    }
}
