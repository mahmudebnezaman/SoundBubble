package com.codezamlabs.soundbubble.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.bubbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "bubble_settings")

data class BubbleSettings(
    val opacity: Float = 0.5f,
    val size: Float = 60f,
    val color: Int = 0xFF1E293B.toInt(),
    val positionX: Int = 0,
    val positionY: Int = 200,
    val serviceEnabled: Boolean = false,
    val shape: BubbleShape = BubbleShape.CIRCLE,
    val buttonThickness: Float = 0.5f,
)

@Singleton
class BubbleSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val BUBBLE_SIZE = floatPreferencesKey("bubble_size")
        val BUBBLE_COLOR = intPreferencesKey("bubble_color")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val BUBBLE_SHAPE = stringPreferencesKey("bubble_shape")
        val BUTTON_THICKNESS = floatPreferencesKey("button_thickness")
    }

    val settingsFlow: Flow<BubbleSettings> = context.bubbleDataStore.data.map { prefs ->
        BubbleSettings(
            opacity = prefs[Keys.BUBBLE_OPACITY] ?: 0.5f,
            size = prefs[Keys.BUBBLE_SIZE] ?: 60f,
            color = prefs[Keys.BUBBLE_COLOR] ?: 0xFF1E293B.toInt(),
            positionX = prefs[Keys.BUBBLE_X] ?: 0,
            positionY = prefs[Keys.BUBBLE_Y] ?: 200,
            serviceEnabled = prefs[Keys.SERVICE_ENABLED] ?: false,
            shape = runCatching {
                BubbleShape.valueOf(prefs[Keys.BUBBLE_SHAPE] ?: BubbleShape.CIRCLE.name)
            }.getOrDefault(BubbleShape.CIRCLE),
            buttonThickness = prefs[Keys.BUTTON_THICKNESS] ?: 0.5f,
        )
    }

    val opacityFlow: Flow<Float> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.BUBBLE_OPACITY] ?: 0.5f
    }

    val sizeFlow: Flow<Float> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.BUBBLE_SIZE] ?: 60f
    }

    val colorFlow: Flow<Int> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.BUBBLE_COLOR] ?: 0xFF1E293B.toInt()
    }

    val positionXFlow: Flow<Int> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.BUBBLE_X] ?: 0
    }

    val positionYFlow: Flow<Int> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.BUBBLE_Y] ?: 200
    }

    val serviceEnabledFlow: Flow<Boolean> = context.bubbleDataStore.data.map { prefs ->
        prefs[Keys.SERVICE_ENABLED] ?: false
    }

    suspend fun setOpacity(value: Float) {
        context.bubbleDataStore.edit { it[Keys.BUBBLE_OPACITY] = value.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setSize(value: Float) {
        context.bubbleDataStore.edit { it[Keys.BUBBLE_SIZE] = value.coerceIn(40f, 100f) }
    }

    suspend fun setColor(value: Int) {
        context.bubbleDataStore.edit { it[Keys.BUBBLE_COLOR] = value }
    }

    suspend fun setPosition(x: Int, y: Int) {
        context.bubbleDataStore.edit {
            it[Keys.BUBBLE_X] = x
            it[Keys.BUBBLE_Y] = y
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.bubbleDataStore.edit { it[Keys.SERVICE_ENABLED] = enabled }
    }

    suspend fun setShape(shape: BubbleShape) {
        context.bubbleDataStore.edit { it[Keys.BUBBLE_SHAPE] = shape.name }
    }

    suspend fun setButtonThickness(value: Float) {
        context.bubbleDataStore.edit { it[Keys.BUTTON_THICKNESS] = value.coerceIn(0.3f, 0.7f) }
    }
}
