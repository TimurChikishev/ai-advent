package com.devchik.ai.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.chat.domain.model.ContextStrategy
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.domain.model.AISettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state для экрана настроек.
 *
 * @property settings Текущие настройки AI (локальная копия — не сохранена, пока не нажата "Сохранить").
 * @property stopSequencesText Текстовое представление стоп-последовательностей (каждая с новой строки).
 * @property isSaved Флаг для отображения Snackbar "Настройки сохранены". Сбрасывается через [clearSaved].
 */
data class SettingsUiState(
    val settings: AISettings = AISettings(),
    val stopSequencesText: String = AISettings().stopSequences.joinToString("\n"),
    val isSaved: Boolean = false,
)

/**
 * ViewModel для экрана настроек AI.
 *
 * Жизненный цикл:
 * 1. В init подписывается на [SettingsRepository.settings] и синхронизирует UI state.
 * 2. Пользователь редактирует настройки — изменения накапливаются в _uiState (не сохраняются).
 * 3. При нажатии "Сохранить" — [save] записывает все настройки в DataStore атомарно.
 *
 * Все set*-методы обновляют только локальный state — для персистенции нужен вызов [save].
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Подписываемся на поток настроек из DataStore — обновляет UI при внешних изменениях
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

    /** Переключатель мастер-настройки (включить/выключить кастомные параметры) */
    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(isEnabled = enabled)) }
    }

    /** Максимальное количество токенов в ответе LLM (clamped к допустимому диапазону) */
    fun setMaxTokens(value: Int) {
        val clamped = value.coerceIn(AISettings.MIN_TOKENS, AISettings.MAX_TOKENS)
        _uiState.update { it.copy(settings = it.settings.copy(maxTokens = clamped)) }
    }

    /** Стоп-последовательности: парсинг многострочного текста в список */
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

    /** Температура генерации (clamped к 0.0..2.0) */
    fun setTemperature(value: Float) {
        val clamped = value.coerceIn(AISettings.MIN_TEMPERATURE, AISettings.MAX_TEMPERATURE)
        _uiState.update { it.copy(settings = it.settings.copy(temperature = clamped)) }
    }

    /** System prompt для LLM */
    fun setSystemPrompt(text: String) {
        _uiState.update { it.copy(settings = it.settings.copy(systemPrompt = text)) }
    }

    /** Выбор стратегии управления контекстом (Sliding Window, Summary, и т.д.) */
    fun setContextStrategy(strategy: ContextStrategy) {
        _uiState.update { it.copy(settings = it.settings.copy(contextStrategy = strategy)) }
    }

    /** Размер окна контекста — сколько последних сообщений хранить полностью */
    fun setContextWindowSize(value: Int) {
        val clamped = value.coerceIn(
            AISettings.MIN_CONTEXT_WINDOW_SIZE,
            AISettings.MAX_CONTEXT_WINDOW_SIZE,
        )
        _uiState.update { it.copy(settings = it.settings.copy(contextWindowSize = clamped)) }
    }

    /** Сохраняет все текущие настройки в DataStore. Устанавливает isSaved для Snackbar. */
    fun save() {
        viewModelScope.launch {
            repository.updateSettings(_uiState.value.settings)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** Сбрасывает флаг isSaved после отображения Snackbar */
    fun clearSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
