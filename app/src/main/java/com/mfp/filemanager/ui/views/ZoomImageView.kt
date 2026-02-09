package com.mfp.filemanager.ui.views

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr),
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    private enum class State {
        NONE, DRAG, ZOOM
    }

    private var mode = State.NONE
    
    // Matrix for image transformation
    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var saveScale = 1f
    
    // Gesture Detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    // Zoom limits
    private var minScale = 1f
    private var maxScale = 5f
    
    // Dimensions
    private var viewWidth = 0
    private var viewHeight = 0
    private var origWidth = 0f
    private var origHeight = 0f
    
    private val lastPoint = PointF()
    private val startPoint = PointF()
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    init {
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(context, this)
        gestureDetector = GestureDetector(context, this)
        gestureDetector.setOnDoubleTapListener(this)
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        // Reset zoom ensures origWidth/Height are updated based on new drawable logic
        resetZoom()
    }

    // Measure view dimensions once layout is done
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewWidth = width
        viewHeight = height
        
        // Initial fit setup
        if (viewWidth > 0 && viewHeight > 0) {
            // Only reset if we are near minScale (fit state), 
            // OR if it's potentially the first run (origWidth might be 0).
            if (saveScale <= minScale || origWidth == 0f) {
                resetZoom()
            } else {
                fixTrans()
            }
        }
    }

    // Helper to request parent ViewPager to handle or ignore touch events
    private fun requestDisallowParentIntercept(disallow: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val curr = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint.set(curr)
                startPoint.set(lastPoint)
                mode = State.DRAG
                // Disallow parent intercept IF we are zoomed in.
                // This prevents ViewPager from stealing the touch immediately.
                if (saveScale > 1f) {
                     requestDisallowParentIntercept(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == State.DRAG) {
                    val deltaX = curr.x - lastPoint.x
                    val deltaY = curr.y - lastPoint.y
                    
                    val fixTransX = getFixDragTrans(deltaX, viewWidth.toFloat(), origWidth * saveScale)
                    val fixTransY = getFixDragTrans(deltaY, viewHeight.toFloat(), origHeight * saveScale)
                    
                    matrix.postTranslate(fixTransX, fixTransY)
                    fixTrans()
                    lastPoint.set(curr.x, curr.y)
                    
                    if (saveScale > 1f) {
                        // STRICT REQUIREMENT: DISABLE SWIPE TO NEXT OR PREVIOUS FILES WHEN AN IMAGE IS ZOOMED
                        // The user explicitly asked to disable next/prev swipe when zoomed.
                        // So we always disallow parent intercept if zoomed in.
                        requestDisallowParentIntercept(true)
                    } else {
                        // Not zoomed (saveScale <= 1f), let parent handle swipe
                        requestDisallowParentIntercept(false)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = State.NONE
                // We rely on GestureDetector.onSingleTapConfirmed for clicks
                // to correctly handle double-tap exclusion.
            }
        }
        imageMatrix = matrix
        return true
    }

    // ScaleListener Implementation
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var mScaleFactor = detector.scaleFactor
        val prevScale = saveScale
        saveScale *= mScaleFactor

        if (saveScale > maxScale) {
            saveScale = maxScale
            mScaleFactor = maxScale / prevScale
        } else if (saveScale < minScale) {
            saveScale = minScale
            mScaleFactor = minScale / prevScale
        }

        // 2-Finger Pan Logic
        val focusX = detector.focusX
        val focusY = detector.focusY
        val dx = focusX - lastFocusX
        val dy = focusY - lastFocusY

        // Apply translation (pan) for moving focal point
        matrix.postTranslate(dx, dy)

        // Always scale around focus point. fixTrans() handles centering.
        matrix.postScale(mScaleFactor, mScaleFactor, focusX, focusY)
        
        fixTrans()
        
        // Update last focus for next event
        lastFocusX = focusX
        lastFocusY = focusY
        
        if (saveScale > 1f) {
             requestDisallowParentIntercept(true)
        }
        
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        mode = State.ZOOM
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        requestDisallowParentIntercept(true)
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        mode = State.NONE
        // If ended at scale 1, allow parent
        if (saveScale <= 1f) {
            requestDisallowParentIntercept(false)
        }
    }

    private fun fixTrans() {
        matrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        
        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), origHeight * saveScale)
        
        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }
    
    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        if (contentSize <= viewSize) return 0f
        return delta
    }

    // Double Tap Implementation
    override fun onDoubleTap(e: MotionEvent): Boolean {
        val targetScale: Float
        val x = e.x
        val y = e.y

        if (saveScale >= maxScale * 0.95f) {
            targetScale = minScale
        } else {
            targetScale = maxScale
        }

        // Animated Zoom
        postOnAnimation(ZoomRunnable(targetScale, x, y))
        
        if (targetScale > 1f) requestDisallowParentIntercept(true)
        else requestDisallowParentIntercept(false)
        
        return true
    }

    private inner class ZoomRunnable(
        private val targetScale: Float,
        private val focusX: Float,
        private val focusY: Float
    ) : Runnable {
        private val startTime = System.currentTimeMillis()
        private val startScale = saveScale
        private val duration = 250f // ms
        private val interpolator = android.view.animation.AccelerateDecelerateInterpolator()

        override fun run() {
            val t = (System.currentTimeMillis() - startTime) / duration
            val interpolatedT = interpolator.getInterpolation(t.coerceIn(0f, 1f))
            
            // Calculate scale factor relative to current saveScale
            // We want to go from startScale to targetScale.
            // currentScale = start + t * (target - start)
            // But matrix works with delta.
            // newMatrix = oldMatrix * delta.
            // saveScale is tracking absolute.
            // delta = currentScale / saveScale
            
            val currentScale = startScale + interpolatedT * (targetScale - startScale)
            val deltaScale = currentScale / saveScale
            
            saveScale = currentScale
            
            if (targetScale == minScale && t >= 1f) {
                // If zooming out and finished, ensure perfect reset
                resetZoom()
            } else {
                matrix.postScale(deltaScale, deltaScale, focusX, focusY)
                fixTrans()
                imageMatrix = matrix
            }

            if (t < 1f) {
                 postOnAnimation(this)
            }
        }
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        performClick()
        return true
    }
    
    // Standard gesture listener (required but unused for scroll as done in onTouch)
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = true
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) { performLongClick() }
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    fun resetZoom() {
        saveScale = 1f
        matrix.reset()
        // Re-calculate initial fit
        drawable?.let {
             val drawableWidth = it.intrinsicWidth.toFloat()
             val drawableHeight = it.intrinsicHeight.toFloat()
             
             val scale: Float
             var dx = 0f
             var dy = 0f
             
             if (drawableWidth > 0 && drawableHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                 if (drawableWidth * viewHeight > viewWidth * drawableHeight) {
                     scale = viewWidth.toFloat() / drawableWidth
                     dy = (viewHeight - drawableHeight * scale) * 0.5f
                 } else {
                     scale = viewHeight.toFloat() / drawableHeight
                     dx = (viewWidth - drawableWidth * scale) * 0.5f
                 }
                 
                 matrix.setScale(scale, scale)
                 matrix.postTranslate(dx, dy)
                 
                 // CRITICAL FIX: Treat 'origWidth' as the base width displayed on screen (Fitted Width).
                 // This ensures 'saveScale' (starts at 1f) correctly represents zoom relative to screen fit.
                 origWidth = drawableWidth * scale
                 origHeight = drawableHeight * scale
                 
             } else {
                 matrix.reset()
                 origWidth = 0f
                 origHeight = 0f
             }

             saveScale = 1f 
        }
        imageMatrix = matrix
        requestDisallowParentIntercept(false)
    }
}
