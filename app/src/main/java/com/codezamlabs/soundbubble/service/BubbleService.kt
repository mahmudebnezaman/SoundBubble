package com.codezamlabs.soundbubble.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.codezamlabs.soundbubble.MainActivity
import com.codezamlabs.soundbubble.R
import com.codezamlabs.soundbubble.audio.VolumeManager
import com.codezamlabs.soundbubble.data.BubbleSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BubbleService : Service() {

    @Inject
    lateinit var settingsRepository: BubbleSettingsRepository

    @Inject
    lateinit var volumeManager: VolumeManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var windowManager: WindowManager? = null
    private var bubbleView: BubbleView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val restoreVolumeRunnable = object : Runnable {
        override fun run() {
            if (volumeManager.isRinging()) {
                mainHandler.postDelayed(this, 300)
            } else {
                volumeManager.restoreRingVolumeIfSilenced()
            }
        }
    }

    companion object {
        private const val TAG = "SoundBubble"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bubble_channel"
        const val ACTION_REPOSITION = "com.codezamlabs.soundbubble.ACTION_REPOSITION"

        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun reposition(context: Context) {
            val intent = Intent(context, BubbleService::class.java).apply {
                action = ACTION_REPOSITION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
        startForegroundWithNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createBubble()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REPOSITION) {
            repositionToSafe()
        }
        return START_STICKY
    }

    private fun getNavBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager!!.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else {
            val resourceId = resources
                .getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        }
    }

    private fun repositionToSafe() {
        val params = layoutParams ?: return
        val view = bubbleView ?: return
        val density = resources.displayMetrics.density
        val visualPaddingPx = (12 * density).toInt()
        val screenHeight = resources.displayMetrics.heightPixels
        val navBarHeight = getNavBarHeight()
        params.x = -visualPaddingPx
        params.y = (screenHeight * 0.3f).toInt()
            .coerceIn(0, screenHeight - params.height - navBarHeight)
        bubbleView?.setSnappedToRight(false)
        try {
            windowManager?.updateViewLayout(view, params)
            serviceScope.launch { settingsRepository.setPosition(params.x, params.y) }
        } catch (e: IllegalArgumentException) { }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Delay 100 ms so Display.getRealMetrics() and currentWindowMetrics both reflect
        // the new orientation before we calculate the new bubble position.
        mainHandler.postDelayed({ repositionBubbleForNewOrientation() }, 100)
    }

    /**
     * Returns the true physical screen size in pixels.
     * getRealMetrics() always gives the full physical display dimensions, which matches the
     * coordinate space of a TYPE_APPLICATION_OVERLAY window with FLAG_LAYOUT_IN_SCREEN —
     * unlike currentWindowMetrics.bounds which may return app-content-area bounds
     * (e.g. excluding a side nav bar) and would produce wrong snap positions.
     */
    @Suppress("DEPRECATION")
    private fun getPhysicalScreenSize(): Pair<Int, Int> {
        val wm = windowManager ?: return Pair(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
        )
        val realMetrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(realMetrics)
        Log.d(TAG, "getPhysicalScreenSize: real=${realMetrics.widthPixels}x${realMetrics.heightPixels} " +
            "dm=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        return Pair(realMetrics.widthPixels, realMetrics.heightPixels)
    }

    /**
     * Returns nav bar insets (left, right, bottom) for the current orientation.
     *
     * Primary source: currentWindowMetrics.windowInsets — correct for most devices.
     * Fallback: Display.rotation + system resource dimensions — used when the primary
     * source returns all-zero (can happen with some 3-button-nav overlay window contexts).
     */
    @Suppress("DEPRECATION")
    private fun getNavInsets(): Triple<Int, Int, Int> {
        val wm = windowManager ?: return Triple(0, 0, 0)

        var navLeft = 0; var navRight = 0; var navBottom = 0
        var insetsSource = "none"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.navigationBars())
            navLeft   = insets.left
            navRight  = insets.right
            navBottom = insets.bottom
            insetsSource = "currentWindowMetrics"
            Log.d(TAG, "getNavInsets currentWindowMetrics → left=$navLeft right=$navRight bottom=$navBottom")
        }

        // Fallback: if insets came back all-zero, derive from rotation + system resources.
        // ROTATION_90  = physical bottom → RIGHT side in landscape → navRight
        // ROTATION_270 = physical bottom → LEFT  side in landscape → navLeft
        if (navLeft == 0 && navRight == 0 && navBottom == 0) {
            val sideRes = resources.getIdentifier("navigation_bar_width", "dimen", "android")
            val btmRes  = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navSide = if (sideRes > 0) resources.getDimensionPixelSize(sideRes) else 0
            val navBtm  = if (btmRes  > 0) resources.getDimensionPixelSize(btmRes)  else 0
            val rotation = wm.defaultDisplay.rotation
            Log.d(TAG, "getNavInsets fallback → rotation=$rotation navSide=$navSide navBtm=$navBtm sideRes=$sideRes btmRes=$btmRes")
            when (rotation) {
                android.view.Surface.ROTATION_90  -> navRight  = navSide
                android.view.Surface.ROTATION_270 -> navLeft   = navSide
                else                              -> navBottom = navBtm
            }
            insetsSource = "fallback rotation=$rotation"
        }

        Log.d(TAG, "getNavInsets FINAL source=$insetsSource → left=$navLeft right=$navRight bottom=$navBottom")
        return Triple(navLeft, navRight, navBottom)
    }

    private fun repositionBubbleForNewOrientation() {
        val wm = windowManager ?: return
        val view = bubbleView ?: return
        val params = layoutParams ?: return

        val density = resources.displayMetrics.density
        val visualPaddingPx = (12 * density).toInt()

        val (newScreenWidth, newScreenHeight) = getPhysicalScreenSize()
        val (navLeft, navRight, navBottom) = getNavInsets()

        val wasOnRight = params.x > 0

        @Suppress("DEPRECATION")
        val rotation = windowManager?.defaultDisplay?.rotation ?: -1
        Log.d(TAG, "repositionForOrientation: rotation=$rotation oldX=${params.x} wasOnRight=$wasOnRight " +
            "physW=$newScreenWidth physH=$newScreenHeight " +
            "navL=$navLeft navR=$navRight navB=$navBottom " +
            "density=$density visualPaddingPx=$visualPaddingPx bubbleW=${params.width}")

        params.x = if (wasOnRight) {
            newScreenWidth - navRight - params.width + visualPaddingPx
        } else {
            navLeft - visualPaddingPx
        }

        params.y = params.y.coerceIn(0, newScreenHeight - params.height - navBottom)

        bubbleView?.setSnappedToRight(wasOnRight)

        Log.d(TAG, "repositionForOrientation: newX=${params.x} newY=${params.y}")

        try {
            wm.updateViewLayout(view, params)
            serviceScope.launch { settingsRepository.setPosition(params.x, params.y) }
        } catch (e: IllegalArgumentException) {
            // View not attached
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        mainHandler.removeCallbacks(restoreVolumeRunnable)
        volumeManager.restoreRingVolumeIfSilenced()
        removeBubble()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bubble_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.bubble_service_channel_desc)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bubble_service_notification_title))
            .setContentText(getString(R.string.bubble_service_notification_text))
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createBubble() {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        // Add padding to the window size to accommodate the shadow blur
        val visualPaddingPx = (12 * density).toInt()
        val shadowPaddingPx = visualPaddingPx * 2
        val defaultSizePx = (60 * density).toInt() + shadowPaddingPx

        val params = WindowManager.LayoutParams(
            defaultSizePx,
            defaultSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Initial X is negative visual padding so it touches the left edge
            x = -visualPaddingPx
            y = 200
        }

        layoutParams = params

        val view = BubbleView(
            context = this,
            windowManager = wm,
            layoutParams = params,
            onTap = {
                if (volumeManager.isRinging()) {
                    volumeManager.silenceRinger()
                    mainHandler.removeCallbacks(restoreVolumeRunnable)
                    mainHandler.postDelayed(restoreVolumeRunnable, 300)
                } else {
                    volumeManager.showSystemVolumePanel()
                }
            },
            onDragEnd = { x, y ->
                serviceScope.launch {
                    settingsRepository.setPosition(x, y)
                }
            },
        )

        bubbleView = view

        try {
            wm.addView(view, params)
        } catch (e: WindowManager.BadTokenException) {
            // Overlay permission not granted
        }
    }

    private fun removeBubble() {
        bubbleView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                // View already removed
            }
        }
        bubbleView = null
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                val density = resources.displayMetrics.density
                val visualPaddingPx = (12 * density).toInt()
                val shadowPaddingPx = visualPaddingPx * 2
                val sizePx = (settings.size * density).toInt() + shadowPaddingPx

                // Use the same physical-screen source as repositionBubbleForNewOrientation()
                // so the wasAtRightEdge check always sees the position that was just saved.
                val (physicalWidth, _) = getPhysicalScreenSize()
                val screenWidth = physicalWidth
                val (navLeft, navRight, _) = getNavInsets()

                // Capture before bubbleView.apply so that updateShape() — which modifies
                // layoutParams.x using stale displayMetrics — cannot corrupt these values.
                val oldWidth = layoutParams?.width ?: 0
                val oldX     = layoutParams?.x     ?: 0
                Log.d(TAG, "observeSettings: screenWidth=$screenWidth navL=$navLeft navR=$navRight oldX=$oldX oldWidth=$oldWidth posX=${settings.positionX}")

                bubbleView?.apply {
                    updateColor(settings.color)
                    updateOpacity(settings.opacity)
                    updateShape(settings.shape)
                    updateThickness(settings.buttonThickness)
                    updateInactivityFadeEnabled(settings.inactivityFadeEnabled)
                    updateLockPosition(settings.lockPosition)
                }

                layoutParams?.let { params ->
                    // Edge snap positions account for side nav bars (3-button nav in landscape).
                    val rightEdgeX = screenWidth - navRight - oldWidth + visualPaddingPx
                    val leftEdgeX  = navLeft - visualPaddingPx

                    val wasAtRightEdge = oldWidth > 0 && Math.abs(oldX - rightEdgeX) < 15
                    val wasAtLeftEdge  = oldWidth > 0 && Math.abs(oldX - leftEdgeX)  < 15

                    Log.d(TAG, "observeSettings edge check: rightEdgeX=$rightEdgeX leftEdgeX=$leftEdgeX " +
                        "wasAtRightEdge=$wasAtRightEdge wasAtLeftEdge=$wasAtLeftEdge sizePx=$sizePx")

                    params.width = sizePx
                    params.height = sizePx

                    val onRight: Boolean
                    when {
                        wasAtRightEdge -> {
                            params.x = screenWidth - navRight - sizePx + visualPaddingPx
                            onRight = true
                        }
                        wasAtLeftEdge || settings.positionX == 0 -> {
                            params.x = navLeft - visualPaddingPx
                            onRight = false
                        }
                        else -> {
                            params.x = settings.positionX
                            // Free-floating: the saved x is definitive — positive means right side
                            onRight = params.x > 0
                        }
                    }

                    // Sync BubbleView's snappedToRight. We track it explicitly from the branch
                    // taken above rather than using params.x > 0, because when navLeft > 0
                    // (side nav bar in landscape), the left-snap position is positive too.
                    bubbleView?.setSnappedToRight(onRight)
                    Log.d(TAG, "observeSettings: final params.x=${params.x} onRight=$onRight")

                    // Update repository if the coordinate changed to keep it synced
                    if (params.x != settings.positionX) {
                        serviceScope.launch {
                            settingsRepository.setPosition(params.x, settings.positionY)
                        }
                    }

                    params.y = settings.positionY
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

                    bubbleView?.let { view ->
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (e: IllegalArgumentException) {
                            // View not attached
                        }
                    }
                }
            }
        }
    }
}
