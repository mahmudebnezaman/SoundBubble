package com.codezamlabs.soundbubble.audio

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class StreamType(val audioManagerStream: Int) {
    RING(AudioManager.STREAM_RING),
    MEDIA(AudioManager.STREAM_MUSIC),
    ALARM(AudioManager.STREAM_ALARM),
    CALL(AudioManager.STREAM_VOICE_CALL),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION),
}

@Singleton
class VolumeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AudioManager,
) {
    private val _volumeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val volumeChanges: SharedFlow<Unit> = _volumeChanges.asSharedFlow()

    private var observerRegistered = false

    private val volumeObserver = VolumeChangeObserver(Handler(Looper.getMainLooper()))

    fun registerObserver() {
        if (observerRegistered) return
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver,
        )
        observerRegistered = true
    }

    fun unregisterObserver() {
        if (!observerRegistered) return
        context.contentResolver.unregisterContentObserver(volumeObserver)
        observerRegistered = false
    }

    fun getVolume(streamType: StreamType): Int {
        return try {
            audioManager.getStreamVolume(streamType.audioManagerStream)
        } catch (e: SecurityException) {
            0
        }
    }

    fun getMaxVolume(streamType: StreamType): Int {
        return try {
            audioManager.getStreamMaxVolume(streamType.audioManagerStream)
        } catch (e: SecurityException) {
            1
        }
    }

    fun setVolume(streamType: StreamType, value: Int) {
        try {
            val clamped = value.coerceIn(0, getMaxVolume(streamType))
            audioManager.setStreamVolume(streamType.audioManagerStream, clamped, 0)
        } catch (e: SecurityException) {
            // No permission to modify this stream
        }
    }

    fun getVolumePercent(streamType: StreamType): Float {
        val max = getMaxVolume(streamType)
        if (max <= 0) return 0f
        return getVolume(streamType).toFloat() / max.toFloat()
    }

    fun setVolumeFromPercent(streamType: StreamType, percent: Float) {
        val max = getMaxVolume(streamType)
        val value = (percent.coerceIn(0f, 1f) * max).toInt()
        setVolume(streamType, value)
    }

    fun getRingerMode(): Int {
        return try {
            audioManager.ringerMode
        } catch (e: SecurityException) {
            AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun isNotificationPolicyGranted(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun openDndSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun setRingerMode(mode: Int): Boolean {
        if (mode == AudioManager.RINGER_MODE_SILENT && !isNotificationPolicyGranted()) {
            return false
        }
        return try {
            audioManager.ringerMode = mode
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun showSystemVolumePanel() {
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI,
            )
        } catch (e: SecurityException) {
            // Cannot show volume panel
        }
    }

    private inner class VolumeChangeObserver(
        handler: Handler,
    ) : ContentObserver(handler) {

        override fun deliverSelfNotifications(): Boolean = false

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            _volumeChanges.tryEmit(Unit)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            _volumeChanges.tryEmit(Unit)
        }
    }
}
