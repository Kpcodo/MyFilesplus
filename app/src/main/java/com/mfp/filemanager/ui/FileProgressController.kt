package com.mfp.filemanager.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileOperationManager
import com.mfp.filemanager.data.OperationStatus
import com.mfp.filemanager.data.OperationType
import com.mfp.filemanager.data.clipboard.TransferStatus
import com.mfp.filemanager.databinding.LayoutFileProgressBinding
import kotlin.math.roundToInt

class FileProgressController(
    private val binding: LayoutFileProgressBinding,
    private val onVisibilityChanged: (Boolean) -> Unit
) {

    private var currentVisualProgress: Float = 0f
    private var isVisible: Boolean = false
    
    // Animation
    private var progressAnimator: ValueAnimator? = null
    private var shimmerAnimator: ObjectAnimator? = null
    private val hideDelayMs = 200L
    private var showStartTime: Long = 0
    private val MIN_DISPLAY_DURATION = 1200L // 1.2 seconds minimum visibility

    init {
        setupShimmer()
        setupCancelButton()
        // Initial state
        binding.progressOverlayRoot.visibility = View.GONE
        binding.progressOverlayRoot.alpha = 0f
        binding.progressCard.translationY = 500f // Start off-screen
    }

    private fun setupShimmer() {
        // ... (No change) ...
        // Keeping the code compact here for replacement
        binding.shimmerView.post {
            val width = binding.progressBarContainer.width.toFloat()
            if (width > 0) {
                shimmerAnimator = ObjectAnimator.ofFloat(
                    binding.shimmerView, 
                    "translationX", 
                    -width, 
                    width
                ).apply {
                    duration = 1500
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
    }

    private fun setupCancelButton() {
        binding.btnCancel.setOnClickListener {
            // Call Data Layer to cancel
            FileOperationManager.cancelOperation()
        }
    }

    fun update(status: OperationStatus) {
        val targetProgress = calculateTargetProgress(status)
        
        // Handle Visibility (Show if running or animating to finish)
        if (status.isRunning || (isVisible && currentVisualProgress < 0.99f)) {
            showUI()
        }

        // Animate Progress
        animateProgressTo(targetProgress)

        // Update Text & Icons
        updateContent(status)

        // Check for Completion / Auto-Hide
        if (!status.isRunning && currentVisualProgress >= 0.99f) {
            hideUI()
        }
    }

    private fun calculateTargetProgress(status: OperationStatus): Float {
        return if (status.isRunning) {
             // Real progress 0.0 -> 1.0 (Ensure we don't go backwards excessively if not intended, 
             // but follow source of truth)
             status.progress.coerceIn(0f, 1f)
        } else if (isVisible) {
             1.0f // Finish up
        } else {
             0.0f
        }
    }

    private fun animateProgressTo(target: Float) {
        if (target == currentVisualProgress) return
        
        // If we are significantly behind, animate faster? 
        // Or just standard spring-like motion.
        
        // Cancel previous animator if running
        progressAnimator?.cancel()

        progressAnimator = ValueAnimator.ofFloat(currentVisualProgress, target).apply {
            duration = 300 // Smooth update
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                currentVisualProgress = value
                updateProgressBar(value)
                
                // Re-check hide condition during animation
                val globalProgress = FileOperationManager.progress.value
                val isGlobalRunning = globalProgress?.status == TransferStatus.STARTING || 
                                      globalProgress?.status == TransferStatus.IN_PROGRESS
                                      
                if (value >= 0.99f && !isGlobalRunning) { 
                    hideUI()
                }
            }
            start()
        }
    }

    private fun updateProgressBar(visualProgress: Float) {
        // bindings.progressFill width = visualProgress * totalWidth
        val totalWidth = binding.progressBarContainer.width
        if (totalWidth > 0) {
            val params = binding.progressFill.layoutParams
            params.width = (totalWidth * visualProgress).toInt()
            binding.progressFill.layoutParams = params
        }
        
        // Update percentage text
        binding.textPercentage.text = "${(visualProgress * 100).toInt()}%"
    }

    private fun updateContent(status: OperationStatus) {
        // Icon & Title
        val (iconRes, titleText) = when (status.type) {
            OperationType.COPY -> Pair(R.drawable.ic_content_copy_24, "Copying files...")
            OperationType.MOVE -> Pair(R.drawable.ic_move_to_24, "Moving files...")
            OperationType.DELETE, OperationType.TRASH -> Pair(R.drawable.ic_delete, "Deleting files...")
            OperationType.EXTRACT -> Pair(R.drawable.ic_archive_24, "Extracting...")
            OperationType.RESTORE -> Pair(R.drawable.ic_restore_24, "Restoring...")
            else -> Pair(R.drawable.ic_content_copy_24, "Processing...")
        }
        
        binding.operationIcon.setImageResource(iconRes)
        binding.operationTitle.text = titleText
        
        // Count
        binding.textCount.text = "${status.processedCount} / ${status.totalCount}"
    }

    private fun showUI() {
        if (isVisible) return
        isVisible = true
        showStartTime = System.currentTimeMillis()
        onVisibilityChanged(true)
        
        binding.progressOverlayRoot.visibility = View.VISIBLE
        
        // Fade in Overlay
        binding.progressOverlayRoot.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
            
        // Slide up Card
        binding.progressCard.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
            
        // Restart shimmer if needed
        shimmerAnimator?.start()
    }

    private fun hideUI() {
        if (!isVisible) return
        
        val elapsed = System.currentTimeMillis() - showStartTime
        val delay = if (elapsed < MIN_DISPLAY_DURATION) {
            MIN_DISPLAY_DURATION - elapsed
        } else {
            hideDelayMs
        }
        
        // Wait then animate out
        binding.progressOverlayRoot.postDelayed({
             performHideAnimation()
        }, delay)
    }
    
    private fun performHideAnimation() {
         if (!isVisible) return
         
         binding.progressOverlayRoot.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
            
         binding.progressCard.animate()
            .translationY(binding.progressOverlayRoot.height.toFloat()) // Slide down off screen
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.progressOverlayRoot.visibility = View.GONE
                isVisible = false
                currentVisualProgress = 0f
                updateProgressBar(0f)
                onVisibilityChanged(false)
            }
            .start()
    }
}
