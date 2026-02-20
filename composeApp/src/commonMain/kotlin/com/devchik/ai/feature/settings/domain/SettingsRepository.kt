package com.devchik.ai.feature.settings.domain

import com.devchik.ai.feature.settings.domain.model.AISettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AISettings>
    suspend fun updateSettings(settings: AISettings)
}
