package com.soundbubble.viewmodel

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundbubble.audio.StreamType
import com.soundbubble.audio.VolumeManager
import com.soundbubble.data.BubbleSettingsRepository
import com.soundbubble.service.BubbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolumeState(
    val current: Int = 0,
    val max: Int = 1,
)

data class MainUiState(
    val volumes: Map<StreamType, VolumeState> = StreamType.entries.associateWith { VolumeState() },
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val serviceRunning: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val needsDndAccess: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val volumeManager: VolumeManager,
    private val settingsRepository: BubbleSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        volumeManager.registerObserver()

        viewModelScope.launch {
            settingsRepository.serviceEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(serviceRunning = enabled) }
            }
        }

        viewModelScope.launch {
            volumeManager.volumeChanges.collect {
                refreshVolumes()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        volumeManager.unregisterObserver()
    }

    fun refreshVolumes() {
        val volumes = StreamType.entries.associateWith { stream ->
            VolumeState(
                current = volumeManager.getVolume(stream),
                max = volumeManager.getMaxVolume(stream),
            )
        }
        val ringerMode = volumeManager.getRingerMode()
        _uiState.update { it.copy(volumes = volumes, ringerMode = ringerMode) }
    }

    fun checkOverlayPermission(context: Context) {
        val granted = Settings.canDrawOverlays(context)
        _uiState.update { it.copy(overlayPermissionGranted = granted) }
    }

    fun refreshState(context: Context) {
        refreshVolumes()
        checkOverlayPermission(context)
        if (_uiState.value.needsDndAccess && volumeManager.isNotificationPolicyGranted()) {
            _uiState.update { it.copy(needsDndAccess = false) }
        }
    }

    fun setVolume(streamType: StreamType, value: Int) {
        volumeManager.setVolume(streamType, value)
        _uiState.update { state ->
            val updated = state.volumes.toMutableMap()
            updated[streamType] = VolumeState(
                current = volumeManager.getVolume(streamType),
                max = volumeManager.getMaxVolume(streamType),
            )
            state.copy(volumes = updated)
        }
    }

    fun setRingerMode(context: Context, mode: Int) {
        val success = volumeManager.setRingerMode(mode)
        if (!success && mode == AudioManager.RINGER_MODE_SILENT) {
            _uiState.update { it.copy(needsDndAccess = true) }
            volumeManager.openDndSettings(context)
        } else {
            _uiState.update {
                it.copy(
                    ringerMode = volumeManager.getRingerMode(),
                    needsDndAccess = false,
                )
            }
        }
    }

    fun dismissDndPrompt() {
        _uiState.update { it.copy(needsDndAccess = false) }
    }

    fun startBubbleService(context: Context) {
        BubbleService.start(context)
        viewModelScope.launch {
            settingsRepository.setServiceEnabled(true)
        }
        _uiState.update { it.copy(serviceRunning = true) }
    }

    fun stopBubbleService(context: Context) {
        BubbleService.stop(context)
        viewModelScope.launch {
            settingsRepository.setServiceEnabled(false)
        }
        _uiState.update { it.copy(serviceRunning = false) }
    }
}
