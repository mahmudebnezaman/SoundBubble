package com.soundbubble.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundbubble.data.BubbleSettings
import com.soundbubble.data.BubbleSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BubbleSettingsViewModel @Inject constructor(
    private val settingsRepository: BubbleSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<BubbleSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BubbleSettings(),
        )

    fun setOpacity(value: Float) {
        viewModelScope.launch {
            settingsRepository.setOpacity(value)
        }
    }

    fun setSize(value: Float) {
        viewModelScope.launch {
            settingsRepository.setSize(value)
        }
    }

    fun setColor(value: Int) {
        viewModelScope.launch {
            settingsRepository.setColor(value)
        }
    }
}
