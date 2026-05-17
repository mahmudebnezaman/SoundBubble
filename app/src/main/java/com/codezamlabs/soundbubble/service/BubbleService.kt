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
        repositionBubbleForNewOrientation()
    }

    private fun repositionBubbleForNewOrientation() {
        val wm = windowManager ?: return
        val view = bubbleView ?: return
        val params = layoutParams ?: return

        val density = resources.displayMetrics.density
        val visualPaddingPx = (12 * density).toInt()

        val newScreenWidth: Int
        val newScreenHeight: Int
        val navLeft: Int
        val navRight: Int
        val navBottom: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Single snapshot — bounds and insets are always consistent with each other.
            val metrics = wm.currentWindowMetrics
            newScreenWidth = metrics.bounds.width()
            newScreenHeight = metrics.bounds.height()
            // On 3-button-nav devices the bar moves to the SIDE in landscape, so we must
            // read left/right insets too — not just bottom.
            val insets = metrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.navigationBars())
            navLeft   = insets.left
            navRight  = insets.right
            navBottom = insets.bottom
        } else {
            val dm = resources.displayMetrics
            newScreenWidth = dm.widthPixels
            newScreenHeight = dm.heightPixels
            navLeft   = 0
            navRight  = 0
            navBottom = getNavBarHeight()
        }

        // params.x is always negative at the left edge and positive at the right edge,
        // so this check is robust across portrait↔landscape aspect-ratio changes.
        val wasOnRight = params.x > 0

        // Offset by the side nav bar so the bubble is never hidden behind it.
        params.x = if (wasOnRight) {
            newScreenWidth - navRight - params.width + visualPaddingPx
        } else {
            navLeft - visualPaddingPx
        }

        params.y = params.y.coerceIn(0, newScreenHeight - params.height - navBottom)

        bubbleView?.setSnappedToRight(wasOnRight)

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

                // Use currentWindowMetrics (same source as repositionBubbleForNewOrientation)
                // so screenWidth and nav insets are always consistent with the position that
                // was just saved.  displayMetrics.widthPixels can lag behind rotation and
                // makes the wasAtRightEdge check fail, sending the bubble off-screen.
                val screenWidth: Int
                val navLeft: Int
                val navRight: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val metrics = windowManager?.currentWindowMetrics
                    screenWidth = metrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
                    val insets = metrics?.windowInsets
                        ?.getInsets(android.view.WindowInsets.Type.navigationBars())
                    navLeft  = insets?.left  ?: 0
                    navRight = insets?.right ?: 0
                } else {
                    screenWidth = resources.displayMetrics.widthPixels
                    navLeft  = 0
                    navRight = 0
                }

                // Capture before bubbleView.apply so that updateShape() — which modifies
                // layoutParams.x using stale displayMetrics — cannot corrupt these values.
                val oldWidth = layoutParams?.width ?: 0
                val oldX     = layoutParams?.x     ?: 0

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

                    params.width = sizePx
                    params.height = sizePx

                    when {
                        wasAtRightEdge -> {
                            params.x = screenWidth - navRight - sizePx + visualPaddingPx
                        }
                        wasAtLeftEdge || settings.positionX == 0 -> {
                            params.x = navLeft - visualPaddingPx
                        }
                        else -> {
                            params.x = settings.positionX
                        }
                    }

                    // Sync BubbleView's snappedToRight so rotation picks up the correct edge.
                    // onAttachedToWindow() fires before observeSettings() restores the saved
                    // position, so the view's flag can be stale (false) even when the bubble
                    // is visually on the right. Keep it in sync every time we move the bubble.
                    bubbleView?.setSnappedToRight(params.x > 0)

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
