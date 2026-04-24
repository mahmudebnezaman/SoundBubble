package com.codezamlabs.soundbubble.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codezamlabs.soundbubble.data.BubbleSettings
import com.codezamlabs.soundbubble.data.BubbleSettingsRepository
import com.codezamlabs.soundbubble.data.BubbleShape
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

    fun setShape(shape: BubbleShape) {
        viewModelScope.launch {
            settingsRepository.setShape(shape)
        }
    }

    fun setButtonThickness(value: Float) {
        viewModelScope.launch {
            settingsRepository.setButtonThickness(value)
        }
    }
}
