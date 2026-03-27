package com.soundbubble.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.soundbubble.data.bubbleDataStore
import com.soundbubble.service.BubbleService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!Settings.canDrawOverlays(context)) return

        val serviceEnabled = runBlocking {
            try {
                context.bubbleDataStore.data.map { prefs ->
                    prefs[booleanPreferencesKey("service_enabled")] ?: false
                }.first()
            } catch (e: Exception) {
                false
            }
        }

        if (serviceEnabled) {
            BubbleService.start(context)
        }
    }
}
