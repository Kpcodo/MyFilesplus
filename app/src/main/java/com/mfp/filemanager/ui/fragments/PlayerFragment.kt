package com.mfp.filemanager.ui.fragments

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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
import jp.wasabeef.transformers.coil.BlurTransformation
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
    private var backgroundAnimator: ValueAnimator? = null

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

        binding.btnShuffle.setOnClickListener {
            animateButton(it)
            viewModel.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
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
                    viewModel.shuffleEnabled.collect { enabled ->
                        val color = if (enabled) {
                            ContextCompat.getColor(requireContext(), R.color.teal_200)
                        } else {
                            // Disabled: 50% White
                            android.graphics.Color.argb(128, 255, 255, 255)
                        }
                        binding.btnShuffle.imageTintList = ColorStateList.valueOf(color)
                    }
                }

                launch {
                    viewModel.repeatMode.collect { mode ->
                        val (icon, color) = when (mode) {
                            // Off: 50% White
                            Player.REPEAT_MODE_OFF -> R.drawable.ic_feather_repeat to android.graphics.Color.argb(128, 255, 255, 255)
                            // On: Teal
                            Player.REPEAT_MODE_ONE -> R.drawable.ic_feather_repeat to ContextCompat.getColor(requireContext(), R.color.teal_200)
                            Player.REPEAT_MODE_ALL -> R.drawable.ic_feather_repeat to ContextCompat.getColor(requireContext(), R.color.teal_200)
                            else -> R.drawable.ic_feather_repeat to android.graphics.Color.argb(128, 255, 255, 255)
                        }
                        binding.btnRepeat.setImageResource(icon)
                        binding.btnRepeat.imageTintList = ColorStateList.valueOf(color)
                    }
                }
            }
        }
    }

    private fun updatePlayerBackground(bitmap: Bitmap?) {
        if (bitmap == null) return

        Palette.from(bitmap).generate { palette ->
            // Safety check: if view is destroyed, don't update UI
            if (_binding == null) return@generate
            val context = context ?: return@generate

            // Extract dominant colors for mixed gradient
            val defaultColor = ContextCompat.getColor(context, android.R.color.black)
            
            val colorTop = palette?.getDarkVibrantColor(
                palette.getVibrantColor(
                    palette.getDominantColor(defaultColor)
                )
            ) ?: defaultColor
            
            val colorBottom = palette?.getDarkMutedColor(
                palette.getMutedColor(defaultColor)
            ) ?: defaultColor

            // Create gradient
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorTop, colorBottom)
            )
            
            // Create transition for smooth effect
            val currentBackground = binding.root.background
            val transitionDrawable = TransitionDrawable(
                arrayOf(currentBackground ?: defaultColor.toDrawable(), gradientDrawable)
            )
            
            binding.root.background = transitionDrawable
            transitionDrawable.startTransition(800)
            
            // Start ambient animation on the new drawable after transition
            backgroundAnimator?.cancel()
            backgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                
                // Use a mix color for the middle to create a 'mix max' effect (3-color gradient)
                val colorMix = palette?.getMutedColor(defaultColor) ?: defaultColor
                
                val evaluator = ArgbEvaluator()
                addUpdateListener { animator ->
                    if (_binding == null) {
                        animator.cancel()
                        return@addUpdateListener
                    }
                    val fraction = animator.animatedValue as Float
                    
                    // Animate between two 3-color states
                    // State A: Top -> Mix -> Bottom
                    // State B: Bottom -> Mix -> Top
                    // This creates a richer "breathing" effect through the middle
                    val newTop = evaluator.evaluate(fraction, colorTop, colorBottom) as Int
                    val newMid = evaluator.evaluate(fraction, colorMix, colorTop) as Int // Shifts slightly towards top
                    val newBottom = evaluator.evaluate(fraction, colorBottom, colorTop) as Int
                    
                    gradientDrawable.colors = intArrayOf(newTop, newMid, newBottom)
                }
                start()
            }

            // Also update the Play button tint
            val accentColor = palette?.getVibrantColor(palette.getLightVibrantColor(colorTop)) ?: colorTop
            binding.fabPlayPause.backgroundTintList = ColorStateList.valueOf(accentColor)
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vinylAnimator?.cancel()
        backgroundAnimator?.cancel()
        _binding = null
    }
}
