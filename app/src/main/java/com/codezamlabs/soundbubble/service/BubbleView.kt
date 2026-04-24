package com.codezamlabs.soundbubble.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.content.res.AppCompatResources
import com.codezamlabs.soundbubble.R
import com.codezamlabs.soundbubble.data.BubbleShape
import kotlin.math.abs

class BubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private var onTap: () -> Unit = {},
    private var onDragEnd: (x: Int, y: Int) -> Unit = { _, _ -> },
) : View(context) {

    private val density = resources.displayMetrics.density
    private val visualPadding = 12f * density

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40000000
        maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF4F6EF7.toInt()
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

    var bubbleShape: BubbleShape = BubbleShape.CIRCLE
    var buttonThickness: Float = 0.5f

    private var configuredOpacity: Float = 1.0f
    private var snappedToRight = false

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private var fadeOutRunnable: Runnable? = null
    private var inactivityAlphaAnimator: ObjectAnimator? = null
    private var inactivitySlideAnimator: ValueAnimator? = null
    private var activeSlideAnimator: ValueAnimator? = null

    private val screenWidth: Int
        get() = resources.displayMetrics.widthPixels

    // Width of the vertical pill in px — used both for drawing and half-visible slide calculation
    private val pillWidthPx: Float
        get() = ((layoutParams.width - 2 * visualPadding) * buttonThickness)
            .coerceAtLeast(density * 10)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun updateColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun updateOpacity(opacity: Float) {
        configuredOpacity = opacity.coerceIn(0.2f, 1.0f)
        alpha = configuredOpacity
        scheduleInactivityFade()
    }

    fun updateSize(sizePx: Int) {
        layoutParams.width = sizePx
        layoutParams.height = sizePx
        updateViewLayout()
        invalidate()
    }

    fun updateShape(shape: BubbleShape) {
        cancelInactivityAnimations()
        activeSlideAnimator?.cancel()
        bubbleShape = shape
        invalidate()
        // Reset to active edge so the service's wasAtLeftEdge/wasAtRightEdge check passes
        // correctly. This also fixes BUTTON-half-visible → CIRCLE showing half off-screen.
        layoutParams.x = if (snappedToRight) {
            screenWidth - layoutParams.width + visualPadding.toInt()
        } else {
            -visualPadding.toInt()
        }
        updateViewLayout()
        scheduleInactivityFade()
    }

    fun updateThickness(thickness: Float) {
        buttonThickness = thickness.coerceIn(0.3f, 0.7f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        when (bubbleShape) {
            BubbleShape.CIRCLE -> drawCircleBubble(canvas, cx, cy)
            BubbleShape.BUTTON -> drawPillBubble(canvas, cx, cy)
        }
    }

    private fun drawCircleBubble(canvas: Canvas, cx: Float, cy: Float) {
        val radius = (width / 2f) - visualPadding

        canvas.drawCircle(cx + 1.5f * density, cy + 2.5f * density, radius, shadowPaint)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, radius - borderPaint.strokeWidth / 2f, borderPaint)

        speakerDrawable?.let { drawable ->
            val iconSize = (radius * 2f * 0.45f).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }
    }

    private fun drawPillBubble(canvas: Canvas, cx: Float, cy: Float) {
        // Vertical pill: height > width
        val pillH = height - 2 * visualPadding
        val pillW = (width - 2 * visualPadding) * buttonThickness.coerceIn(0.3f, 0.7f)
        val cornerRadius = pillW / 2f

        // Edge-align the pill so it sits flush with the screen edge (no gap).
        // Left snap: pill left edge at visualPadding within view → flush with screen left.
        // Right snap: pill right edge at (width - visualPadding) → flush with screen right.
        val pillCx = if (snappedToRight) width - visualPadding - pillW / 2f
                     else visualPadding + pillW / 2f

        val shadowRect = RectF(
            pillCx - pillW / 2f + 1.5f * density,
            cy - pillH / 2f + 2.5f * density,
            pillCx + pillW / 2f + 1.5f * density,
            cy + pillH / 2f + 2.5f * density,
        )
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)

        val pillRect = RectF(pillCx - pillW / 2f, cy - pillH / 2f, pillCx + pillW / 2f, cy + pillH / 2f)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, paint)

        val inset = borderPaint.strokeWidth / 2f
        val borderRect = RectF(
            pillRect.left + inset,
            pillRect.top + inset,
            pillRect.right - inset,
            pillRect.bottom - inset,
        )
        canvas.drawRoundRect(borderRect, cornerRadius - inset, cornerRadius - inset, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animateToActive()
                cancelInactivityAnimations()
                snapAnimator?.cancel()
                // Do NOT cancel activeSlideAnimator here — let the slide-back complete on tap.
                // It will be cancelled in ACTION_MOVE if the user actually drags.
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
                    activeSlideAnimator?.cancel()  // drag overrides the slide-back
                    animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start()
                }
                if (hasMoved) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    updateViewLayout()
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
                scheduleInactivityFade()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge() {
        val bubbleWidth = layoutParams.width
        val bubbleCenterX = layoutParams.x + bubbleWidth / 2
        snappedToRight = bubbleCenterX >= screenWidth / 2

        val targetX = if (snappedToRight) {
            screenWidth - bubbleWidth + visualPadding.toInt()
        } else {
            -visualPadding.toInt()
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                updateViewLayout()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Determine which edge we're snapped to from the initial position
        val bubbleCenterX = layoutParams.x + layoutParams.width / 2
        snappedToRight = bubbleCenterX >= screenWidth / 2
        scheduleInactivityFade()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelInactivityAnimations()
        activeSlideAnimator?.cancel()
    }

    private fun scheduleInactivityFade() {
        cancelInactivityAnimations()
        fadeOutRunnable = Runnable { animateToInactive() }
        inactivityHandler.postDelayed(fadeOutRunnable!!, 4000L)
    }

    private fun cancelInactivityAnimations() {
        fadeOutRunnable?.let { inactivityHandler.removeCallbacks(it) }
        fadeOutRunnable = null
        inactivityAlphaAnimator?.cancel()
        inactivitySlideAnimator?.cancel()
    }

    private fun animateToInactive() {
        inactivityAlphaAnimator = ObjectAnimator.ofFloat(
            this, "alpha", alpha, configuredOpacity * 0.35f,
        ).apply { duration = 300; start() }

        // BUTTON shape: slide half off the screen edge for a "drawer tab" feel
        if (bubbleShape == BubbleShape.BUTTON) {
            val targetX = getHalfVisibleX()
            inactivitySlideAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    layoutParams.x = animatedValue as Int
                    updateViewLayout()
                }
                start()
            }
        }
    }

    private fun animateToActive() {
        cancelInactivityAnimations()
        activeSlideAnimator?.cancel()

        ObjectAnimator.ofFloat(this, "alpha", alpha, configuredOpacity).apply {
            duration = 150
            start()
        }

        // BUTTON shape: slide back to the full edge position
        if (bubbleShape == BubbleShape.BUTTON) {
            val targetX = getActiveEdgeX()
            if (layoutParams.x != targetX) {
                activeSlideAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
                    duration = 220
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        layoutParams.x = animatedValue as Int
                        updateViewLayout()
                    }
                    start()
                }
            }
        }
    }

    /**
     * Half-visible x: positions the pill so exactly half of it peeks out from the screen edge.
     * For BUTTON: pill is edge-aligned, so its center is at (visualPadding + pillW/2) within the view.
     * Putting that center at the screen edge gives the drawer-tab effect.
     * For CIRCLE: view center at screen edge → half circle visible.
     */
    private fun getHalfVisibleX(): Int {
        val vw = layoutParams.width
        return if (bubbleShape == BubbleShape.BUTTON) {
            val pw = pillWidthPx
            if (snappedToRight) {
                (screenWidth - vw + visualPadding + pw / 2f).toInt()
            } else {
                -(visualPadding + pw / 2f).toInt()
            }
        } else {
            if (snappedToRight) screenWidth - vw / 2 else -vw / 2
        }
    }

    private fun getActiveEdgeX(): Int {
        val vw = layoutParams.width
        return if (snappedToRight) {
            screenWidth - vw + visualPadding.toInt()
        } else {
            -visualPadding.toInt()
        }
    }

    private fun updateViewLayout() {
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (e: IllegalArgumentException) {
            // View not attached
        }
    }
}
