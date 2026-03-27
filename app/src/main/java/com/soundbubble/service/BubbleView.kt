package com.soundbubble.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.appcompat.content.res.AppCompatResources
import com.soundbubble.R
import kotlin.math.abs

class BubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private var onTap: () -> Unit = {},
    private var onDragEnd: (x: Int, y: Int) -> Unit = { _, _ -> },
) : View(context) {

    private val density = resources.displayMetrics.density

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40000000
        maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF4F6EF7.toInt()
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x18FFFFFF
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x40FFFFFF
    }

    private val speakerDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_volume_up_white)

    private val touchSlop = (10 * density).toInt()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false
    private var snapAnimator: ValueAnimator? = null

    private val screenWidth: Int
        get() = resources.displayMetrics.widthPixels

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun updateColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun updateOpacity(opacity: Float) {
        alpha = opacity.coerceIn(0.2f, 1.0f)
    }

    fun updateSize(sizePx: Int) {
        layoutParams.width = sizePx
        layoutParams.height = sizePx
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (e: IllegalArgumentException) {
            // View not attached
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.88f

        // Drop shadow
        canvas.drawCircle(cx + 1.5f * density, cy + 2.5f * density, radius, shadowPaint)

        // Main circle
        canvas.drawCircle(cx, cy, radius, paint)

        // Glass border ring
        canvas.drawCircle(cx, cy, radius - borderPaint.strokeWidth / 2f, borderPaint)

        // Draw the vector drawable icon — 45% of bubble diameter
        speakerDrawable?.let { drawable ->
            val iconSize = (radius * 2f * 0.45f).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasMoved = false
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!hasMoved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    hasMoved = true
                    animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start()
                }
                if (hasMoved) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (e: IllegalArgumentException) {
                        // View not attached
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                if (!hasMoved) {
                    onTap()
                } else {
                    snapToEdge()
                    onDragEnd(layoutParams.x, layoutParams.y)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge() {
        val bubbleWidth = layoutParams.width
        val bubbleCenterX = layoutParams.x + bubbleWidth / 2
        val targetX = if (bubbleCenterX < screenWidth / 2) 0 else screenWidth - bubbleWidth

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(this@BubbleView, layoutParams)
                } catch (e: IllegalArgumentException) {
                    // View not attached
                }
            }
            start()
        }
    }
}
