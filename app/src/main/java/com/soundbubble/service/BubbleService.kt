package com.soundbubble.service

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
import com.soundbubble.MainActivity
import com.soundbubble.R
import com.soundbubble.audio.VolumeManager
import com.soundbubble.data.BubbleSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bubble_channel"

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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createBubble()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
        val defaultSizePx = (60 * density).toInt()

        val params = WindowManager.LayoutParams(
            defaultSizePx,
            defaultSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        layoutParams = params

        val view = BubbleView(
            context = this,
            windowManager = wm,
            layoutParams = params,
            onTap = { volumeManager.showSystemVolumePanel() },
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
                val sizePx = (settings.size * density).toInt()

                bubbleView?.updateColor(settings.color)
                bubbleView?.updateOpacity(settings.opacity)
                bubbleView?.updateSize(sizePx)

                layoutParams?.let { params ->
                    params.x = settings.positionX
                    params.y = settings.positionY
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
