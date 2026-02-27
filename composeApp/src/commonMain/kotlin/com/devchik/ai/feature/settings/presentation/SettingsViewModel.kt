package com.devchik.ai.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.domain.model.AISettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AISettings = AISettings(),
    val stopSequencesText: String = AISettings().stopSequences.joinToString("\n"),
    val isSaved: Boolean = false,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        stopSequencesText = settings.stopSequences.joinToString("\n"),
                    )
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(isEnabled = enabled)) }
    }

    fun setMaxTokens(value: Int) {
        val clamped = value.coerceIn(AISettings.MIN_TOKENS, AISettings.MAX_TOKENS)
        _uiState.update { it.copy(settings = it.settings.copy(maxTokens = clamped)) }
    }

    fun setStopSequencesText(text: String) {
        _uiState.update {
            it.copy(
                stopSequencesText = text,
                settings = it.settings.copy(
                    stopSequences = text.lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() },
                ),
            )
        }
    }

    fun setTemperature(value: Float) {
        val clamped = value.coerceIn(AISettings.MIN_TEMPERATURE, AISettings.MAX_TEMPERATURE)
        _uiState.update { it.copy(settings = it.settings.copy(temperature = clamped)) }
    }

    fun setSystemPrompt(text: String) {
        _uiState.update { it.copy(settings = it.settings.copy(systemPrompt = text)) }
    }

    fun save() {
        viewModelScope.launch {
            repository.updateSettings(_uiState.value.settings)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun clearSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
