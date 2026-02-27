package com.devchik.ai.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.domain.model.AISettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

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
        )
    }

    override suspend fun updateSettings(settings: AISettings) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = settings.isEnabled
            prefs[Keys.MAX_TOKENS] = settings.maxTokens
            prefs[Keys.TEMPERATURE] = settings.temperature
            prefs[Keys.STOP_SEQUENCES] = settings.stopSequences.joinToString(STOP_SEPARATOR)
            prefs[Keys.SYSTEM_PROMPT] = settings.systemPrompt
        }
    }

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val STOP_SEQUENCES = stringPreferencesKey("stop_sequences")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    }

    companion object {
        private const val STOP_SEPARATOR = "|||"
    }
}
