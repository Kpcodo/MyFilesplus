package com.mfp.filemanager.ui.fragments

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import androidx.media3.common.Player
import coil.load
import com.mfp.filemanager.R
import com.mfp.filemanager.databinding.FragmentPlayerBinding
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioViewModel by activityViewModels()
    
    // Animators
    private var vinylAnimator: ObjectAnimator? = null
    private var liquidBlobAnimators = mutableListOf<ValueAnimator>()
    private var lastPaletteColors = listOf<Int>()
    private var currentAccentColor: Int = 0 // Initialized in onViewCreated

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentAccentColor = ContextCompat.getColor(requireContext(), R.color.default_accent)
        setupVinylAnimation()
        setupListeners()
        setupObservers()
        setupSwipeRefresh()
        animateEntrance()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            // Simulated refresh for player
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun animateEntrance() {
        // Prepare initial states
        binding.toolbar.translationY = -100f
        binding.toolbar.alpha = 0f
        binding.layoutVinylContainer.scaleX = 0.8f
        binding.layoutVinylContainer.scaleY = 0.8f
        binding.layoutVinylContainer.alpha = 0f
        binding.layoutInfo.alpha = 0f
        binding.cardControls.translationY = 200f
        binding.cardControls.alpha = 0f

        // Animate in
        binding.toolbar.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(100).start()
        binding.layoutVinylContainer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600).setInterpolator(OvershootInterpolator()).start()
        binding.layoutInfo.animate().alpha(1f).setDuration(500).setStartDelay(300).start()
        binding.cardControls.animate().translationY(0f).alpha(1f).setDuration(600).setStartDelay(200).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun setupVinylAnimation() {
        vinylAnimator = ObjectAnimator.ofFloat(binding.layoutVinylContainer, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Helper function for button press animation
        fun animateButton(view: View) {
            view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }

        binding.fabPlayPause.setOnClickListener {
            // Bounce animation on user interaction
            it.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(120)
                .withEndAction {
                    it.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                }
                .start()
            
            viewModel.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            animateButton(it)
            viewModel.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            animateButton(it)
            viewModel.playPrevious()
        }

        binding.containerShuffle.setOnClickListener {
            animateButton(it)
            viewModel.toggleShuffle()
        }

        binding.containerRepeat.setOnClickListener {
            animateButton(it)
            viewModel.toggleRepeatMode()
        }

        binding.seekbarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnFavorite.setOnClickListener {
            animateButton(it)
            // Toggle favorite (Visual only for now if repo not ready)
            it.isActivated = !it.isActivated
            // Use filled heart if available, else standard heart with tint
            binding.btnFavorite.setImageResource(R.drawable.ic_feather_heart)
            binding.btnFavorite.imageTintList = ContextCompat.getColorStateList(requireContext(), if (it.isActivated) R.color.default_accent else android.R.color.white)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentTrack.collect { metadata ->
                        metadata?.let {
                            binding.textTitle.text = it.title ?: "Unknown Title"
                            binding.textArtist.text = it.artist ?: "Unknown Artist"
                            
                            // Load Art
                            val artUri = it.artworkUri
                            binding.imgAlbumArt.load(artUri) {
                                allowHardware(false) // Crucial for Palette API
                                target(
                                    onStart = {
                                        binding.imgAlbumArt.scaleType = ImageView.ScaleType.FIT_CENTER
                                        binding.imgAlbumArt.setPadding(50, 50, 50, 50)
                                        binding.imgAlbumArt.setImageResource(R.drawable.ic_music_note_24)
                                    },
                                    onError = {
                                        binding.imgAlbumArt.scaleType = ImageView.ScaleType.FIT_CENTER
                                        binding.imgAlbumArt.setPadding(50, 50, 50, 50)
                                        binding.imgAlbumArt.setImageResource(R.drawable.ic_music_note_24)
                                    },
                                    onSuccess = { result ->
                                        binding.imgAlbumArt.scaleType = ImageView.ScaleType.CENTER_CROP
                                        binding.imgAlbumArt.setPadding(0, 0, 0, 0)
                                        binding.imgAlbumArt.setImageDrawable(result)
                                        
                                        val bitmap = (result as? BitmapDrawable)?.bitmap
                                        updatePlayerBackground(bitmap)
                                    }
                                )
                            }
                        }
                    }
                }

                launch {
                    viewModel.isPlaying.collect { isPlaying ->
                        binding.imgPlayPause.setImageResource(
                            if (isPlaying) R.drawable.ic_feather_pause else R.drawable.ic_feather_play
                        )
                        
                        // Optical centering for the triangle (Play icon)
                        // When NOT playing, we show the Play icon which is a triangle and needs a slight right shift
                        val density = resources.displayMetrics.density
                        binding.imgPlayPause.translationX = if (isPlaying) 0f else 2f * density
                        
                        // Vinyl Animation control
                        if (isPlaying) {
                            if (vinylAnimator?.isPaused == true) vinylAnimator?.resume() else vinylAnimator?.start()
                            binding.imgTonearm.animate().rotation(40f).setDuration(600).start()
                        } else {
                            vinylAnimator?.pause()
                            binding.imgTonearm.animate().rotation(0f).setDuration(600).start()
                        }
                    }
                }

                launch {
                    viewModel.position.collect { position ->
                        val duration = viewModel.duration.value.toFloat().coerceAtLeast(1f)
                        val safePosition = position.toFloat().coerceIn(0f, duration).toInt()
                        binding.seekbarProgress.progress = safePosition
                        binding.textCurrentTime.text = formatTime(position)
                    }
                }

                launch {
                    viewModel.duration.collect { duration ->
                        binding.seekbarProgress.max = duration.toInt().coerceAtLeast(1)
                        binding.textTotalTime.text = formatTime(duration)
                    }
                }

                launch {
                    viewModel.shuffleEnabled.collect { _ ->
                        updateShuffleRepeatColors()
                    }
                }

                launch {
                    viewModel.repeatMode.collect { _ ->
                        updateShuffleRepeatColors()
                    }
                }
            }
        }
    }

    private fun updateShuffleRepeatColors() {
        val shuffleEnabled = viewModel.shuffleEnabled.value
        val repeatMode = viewModel.repeatMode.value
        
        // Update Shuffle
        val (shuffleIconColor, shuffleBgColor) = if (shuffleEnabled) {
            Color.WHITE to currentAccentColor
        } else {
            Color.argb(128, 255, 255, 255) to Color.TRANSPARENT
        }
        binding.btnShuffle.imageTintList = ColorStateList.valueOf(shuffleIconColor)
        binding.containerShuffle.setCardBackgroundColor(shuffleBgColor)

        // Update Repeat
        val repeatEnabled = repeatMode != Player.REPEAT_MODE_OFF
        val (repeatIconColor, repeatBgColor) = if (repeatEnabled) {
            Color.WHITE to currentAccentColor
        } else {
            Color.argb(128, 255, 255, 255) to Color.TRANSPARENT
        }
        
        val iconRes = if (repeatMode == Player.REPEAT_MODE_ONE) {
            R.drawable.ic_feather_repeat_one
        } else {
            R.drawable.ic_feather_repeat
        }
        
        binding.btnRepeat.setImageResource(iconRes)
        binding.btnRepeat.imageTintList = ColorStateList.valueOf(repeatIconColor)
        binding.containerRepeat.setCardBackgroundColor(repeatBgColor)
    }

    private fun updatePlayerBackground(bitmap: Bitmap?) {
        if (bitmap == null) return

        Palette.from(bitmap).generate { palette ->
            if (_binding == null) return@generate
            val context = context ?: return@generate

            val defaultColor = ContextCompat.getColor(context, android.R.color.black)
            val colors = mutableListOf<Int>()
            palette?.vibrantSwatch?.rgb?.let { colors.add(it) }
            palette?.darkVibrantSwatch?.rgb?.let { colors.add(it) }
            palette?.mutedSwatch?.rgb?.let { colors.add(it) }
            palette?.lightVibrantSwatch?.rgb?.let { colors.add(it) }
            palette?.dominantSwatch?.rgb?.let { colors.add(it) }
            
            if (colors.size < 3) {
                 colors.add(palette?.getVibrantColor(defaultColor) ?: defaultColor)
                 colors.add(palette?.getMutedColor(defaultColor) ?: defaultColor)
                 colors.add(palette?.getDominantColor(defaultColor) ?: defaultColor)
            }
            // Base background: a very dark version of the dominant color instead of pure black
            val baseColor = Color.argb(255, (Color.red(colors[0]) * 0.1).toInt(), (Color.green(colors[0]) * 0.1).toInt(), (Color.blue(colors[0]) * 0.1).toInt())
            binding.root.background = ColorDrawable(baseColor)

            // Update accent for buttons
            val accentColor = palette?.getVibrantColor(palette.getLightVibrantColor(colors[0])) ?: colors[0]
            currentAccentColor = accentColor
            binding.fabPlayPause.setCardBackgroundColor(accentColor)
            updateShuffleRepeatColors()

            // Initialize/Update Liquid Blobs
            setupLiquidBlobs(context, colors)
        }
    }

    private fun setupLiquidBlobs(context: Context, colors: List<Int>) {
        val container = binding.playerBackground
        container.removeAllViews()
        liquidBlobAnimators.forEach { it.cancel() }
        liquidBlobAnimators.clear()

        // Apply heavy blur to the container if supported (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            container.setRenderEffect(
                RenderEffect.createBlurEffect(
                    150f, 150f, Shader.TileMode.CLAMP
                )
            )
        }

        // Create 8 blobs (increased from 5 to fill more space)
        for (i in 0 until 8) {
            val blob = View(context)
            val size = 800 + (Math.random() * 600).toInt() // Increased size: 800..1400
            val params = FrameLayout.LayoutParams(size, size)
            blob.layoutParams = params
            
            val color = colors[i % colors.size]
            
            // Soft radial gradient for the blob
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size / 2f
                setColors(intArrayOf(
                    Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT
                ))
            }
            blob.background = drawable
            
            // Random initial position (wider range to cover corners)
            blob.translationX = (Math.random() * 2000 - 1000).toFloat()
            blob.translationY = (Math.random() * 2000 - 1000).toFloat()
            blob.alpha = 0.7f
            
            container.addView(blob)
            
            // Animate each blob uniquely
            animateBlob(blob)
        }
    }

    private fun animateBlob(blob: View) {
        // Faster duration: 4s to 8s
        val duration = 4000L + (Math.random() * 4000L).toLong()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            
            val startX = (Math.random() * 2000 - 1000).toFloat()
            val startY = (Math.random() * 2000 - 1000).toFloat()
            val endX = (Math.random() * 2000 - 1000).toFloat()
            val endY = (Math.random() * 2000 - 1000).toFloat()
            
            addUpdateListener { anim ->
                if (_binding == null) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val fraction = anim.animatedValue as Float
                blob.translationX = startX + (endX - startX) * fraction
                blob.translationY = startY + (endY - startY) * fraction
                blob.scaleX = 1f + 0.5f * Math.sin(fraction.toDouble() * Math.PI).toFloat()
                blob.scaleY = 1f + 0.5f * Math.sin(fraction.toDouble() * Math.PI).toFloat()
            }
        }
        animator.start()
        liquidBlobAnimators.add(animator)
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vinylAnimator?.cancel()
        liquidBlobAnimators.forEach { it.cancel() }
        liquidBlobAnimators.clear()
        _binding = null
    }
}
