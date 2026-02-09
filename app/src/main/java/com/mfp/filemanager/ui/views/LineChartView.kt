package com.mfp.filemanager.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.mfp.filemanager.R

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = ContextCompat.getColor(context, R.color.forecast_chart_line)
        strokeCap = Paint.Cap.ROUND
    }

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.forecast_chart_line)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.forecast_text_secondary_adaptive).withAlpha(40)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.forecast_text_secondary_adaptive)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val path = Path()
    private val gradientPath = Path()
    
    private var animationProgress: Float = 1f
    private var rawDataPoints: List<PointF> = emptyList()
    private var nowPointIndex: Int = -1

    fun setData(points: List<PointF>, nowIndex: Int) {
        this.rawDataPoints = points
        this.nowPointIndex = nowIndex
        
        // Reset and Animate
        animationProgress = 0f
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200 // 1.2s smooth animation
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { 
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f

        val graphWidth = w - paddingLeft - paddingRight
        val graphHeight = h - paddingTop - paddingBottom
        val baselineY = paddingTop + graphHeight

        // Draw subtle Grid (Always visible)
        for (i in 0..4) {
            val gy = paddingTop + graphHeight * (i / 4f)
            canvas.drawLine(paddingLeft, gy, w - paddingRight, gy, gridPaint)
        }

        path.reset()
        gradientPath.reset()

        // Create a single, highly smoothed elastic line
        if (rawDataPoints.size > 1) {
            for (i in 0 until rawDataPoints.size) {
                val p = rawDataPoints[i]
                val x = paddingLeft + p.x * graphWidth
                
                // Animate Y from bottom (baseline) to target
                val targetY = paddingTop + p.y * graphHeight
                val y = baselineY + (targetY - baselineY) * animationProgress

                if (i == 0) {
                    path.moveTo(x, y)
                    gradientPath.moveTo(x, h - paddingBottom)
                    gradientPath.lineTo(x, y)
                } else {
                    val prev = rawDataPoints[i - 1]
                    val px = paddingLeft + prev.x * graphWidth
                    
                    val prevTargetY = paddingTop + prev.y * graphHeight
                    val py = baselineY + (prevTargetY - baselineY) * animationProgress
                    
                    // Smooth Cubic Interpolation for "Elastic" feel
                    path.cubicTo(px + (x-px)*0.4f, py, px + (x-px)*0.6f, y, x, y)
                    gradientPath.cubicTo(px + (x-px)*0.4f, py, px + (x-px)*0.6f, y, x, y)
                }
                
                if (i == rawDataPoints.size - 1) {
                    gradientPath.lineTo(x, h - paddingBottom)
                    gradientPath.close()
                }
            }
            
            // Apply Premium Gradient
            gradientPaint.shader = LinearGradient(
                0f, paddingTop, 0f, h - paddingBottom,
                ContextCompat.getColor(context, R.color.forecast_chart_gradient_top).withAlpha(120),
                ContextCompat.getColor(context, R.color.forecast_chart_gradient_bottom).withAlpha(0),
                Shader.TileMode.CLAMP
            )

            canvas.drawPath(gradientPath, gradientPaint)
            canvas.drawPath(path, linePaint)
        }

        // Indicator
        if (nowPointIndex in rawDataPoints.indices) {
            val nowPoint = rawDataPoints[nowPointIndex]
            val x = paddingLeft + nowPoint.x * graphWidth
            val targetY = paddingTop + nowPoint.y * graphHeight
            val y = baselineY + (targetY - baselineY) * animationProgress
            
            val originalGridAlpha = gridPaint.alpha
            gridPaint.alpha = 80 // Static alpha
            canvas.drawLine(x, paddingTop, x, h - paddingBottom, gridPaint)
            gridPaint.alpha = originalGridAlpha
            
            // Animate dot appearing logic if desired, or just follow line
            canvas.drawCircle(x, y, 10f * animationProgress, dotPaint)
            
            val originalDotAlpha = dotPaint.alpha
            dotPaint.alpha = 40
            canvas.drawCircle(x, y, 18f * animationProgress, dotPaint)
            dotPaint.alpha = originalDotAlpha
        }

        // Clean Labels (Always visible)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.alpha = 255
        canvas.drawText("30d ago", paddingLeft, h - 10f, textPaint)
        
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("1y later", w - paddingRight, h - 10f, textPaint)
    }

}
