package com.devchik.ai.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devchik.ai.feature.chat.domain.model.ContextStrategy
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.domain.model.AISettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Реализация репозитория настроек на базе Jetpack DataStore (Preferences).
 *
 * Маппинг: DataStore preferences (примитивы) ↔ доменная модель [AISettings].
 *
 * Особенности:
 * - [contextStrategy] хранится как String (enum name), десериализуется обратно через entries.find.
 * - [stopSequences] хранятся как одна строка с разделителем "|||".
 * - Все поля имеют fallback на дефолтные значения из [AISettings] при отсутствии в DataStore.
 *
 * @param dataStore Jetpack DataStore, предоставляемый через DI (платформозависимая инициализация).
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    /** Реактивный поток настроек — обновляется при каждом изменении DataStore */
    override val settings: Flow<AISettings> = dataStore.data.map { prefs ->
        AISettings(
            isEnabled = prefs[Keys.IS_ENABLED] ?: true,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 1024,
            temperature = prefs[Keys.TEMPERATURE] ?: AISettings.DEFAULT_TEMPERATURE,
            stopSequences = prefs[Keys.STOP_SEQUENCES]
                ?.split(STOP_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?: AISettings().stopSequences,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: AISettings.DEFAULT_SYSTEM_PROMPT,
            // Десериализация enum: ищем по name, fallback на SUMMARY если не найден
            contextStrategy = prefs[Keys.CONTEXT_STRATEGY]
                ?.let { name -> ContextStrategy.entries.find { it.name == name } }
                ?: ContextStrategy.SUMMARY,
            contextWindowSize = prefs[Keys.CONTEXT_WINDOW_SIZE]
                ?: AISettings.DEFAULT_CONTEXT_WINDOW_SIZE,
        )
    }

    /** Атомарно записывает все настройки в DataStore */
    override suspend fun updateSettings(settings: AISettings) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = settings.isEnabled
            prefs[Keys.MAX_TOKENS] = settings.maxTokens
            prefs[Keys.TEMPERATURE] = settings.temperature
            prefs[Keys.STOP_SEQUENCES] = settings.stopSequences.joinToString(STOP_SEPARATOR)
            prefs[Keys.SYSTEM_PROMPT] = settings.systemPrompt
            // Сериализация enum в строку — стабильна при переименовании displayName
            prefs[Keys.CONTEXT_STRATEGY] = settings.contextStrategy.name
            prefs[Keys.CONTEXT_WINDOW_SIZE] = settings.contextWindowSize
        }
    }

    /** Ключи для DataStore Preferences */
    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val STOP_SEQUENCES = stringPreferencesKey("stop_sequences")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        /** Хранит ContextStrategy.name (например "SLIDING_WINDOW", "SUMMARY") */
        val CONTEXT_STRATEGY = stringPreferencesKey("context_strategy")
        /** Количество последних сообщений в окне контекста */
        val CONTEXT_WINDOW_SIZE = intPreferencesKey("context_window_size")
    }

    companion object {
        /** Разделитель для сериализации списка стоп-последовательностей в одну строку */
        private const val STOP_SEPARATOR = "|||"
    }
}
