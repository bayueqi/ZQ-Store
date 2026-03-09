package com.samyak.repostore.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

/**
 * Custom ShimmerFrameLayout - A lightweight shimmer effect implementation
 * without Facebook SDK dependency (for F-Droid compatibility)
 */
class ShimmerFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val shimmerPaint = Paint()
    private val shimmerMatrix = Matrix()
    private var shimmerAnimator: ValueAnimator? = null
    private var shimmerTranslate = 0f
    private var isShimmerStarted = false

    private val shimmerColor = 0x55FFFFFF
    private val shimmerAngle = 20f
    private val shimmerDuration = 1500L

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        shimmerPaint.apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        val width = width.toFloat()
        if (width <= 0) return

        val shimmerWidth = width / 3
        shimmerPaint.shader = LinearGradient(
            0f, 0f, shimmerWidth, 0f,
            intArrayOf(0x00FFFFFF, shimmerColor, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        
        if (isShimmerStarted && width > 0) {
            val shimmerWidth = width / 3f
            val translateX = -shimmerWidth + (width + shimmerWidth * 2) * shimmerTranslate
            
            shimmerMatrix.reset()
            shimmerMatrix.setRotate(shimmerAngle, shimmerWidth / 2, height / 2f)
            shimmerMatrix.postTranslate(translateX, 0f)
            shimmerPaint.shader?.setLocalMatrix(shimmerMatrix)
            
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shimmerPaint)
        }
    }

    fun startShimmer() {
        if (isShimmerStarted) return
        isShimmerStarted = true
        
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = shimmerDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                shimmerTranslate = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopShimmer() {
        isShimmerStarted = false
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        invalidate()
    }

    fun isShimmerStarted(): Boolean = isShimmerStarted

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShimmerStarted) {
            startShimmer()
        }
    }

    override fun onDetachedFromWindow() {
        shimmerAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
