package com.devchik.ai.feature.settings.domain.model

import com.devchik.ai.feature.chat.domain.model.ContextStrategy

/**
 * Доменная модель настроек AI — хранит все пользовательские параметры.
 *
 * Персистится через DataStore в [SettingsRepositoryImpl].
 * Используется:
 * - [AIRepositoryImpl] — maxTokens, temperature, stopSequences, systemPrompt для простого AI чата.
 * - [ContextManager] — contextStrategy, contextWindowSize для управления контекстом Koog чата.
 * - [SettingsViewModel] / [SettingsScreen] — для отображения и редактирования в UI.
 *
 * @property isEnabled Мастер-переключатель: если false, используются дефолтные параметры API.
 * @property maxTokens Максимальное количество токенов в ответе LLM.
 * @property temperature Температура генерации (0.0 = детерминированно, 2.0 = максимальная креативность).
 * @property stopSequences Последовательности, при встрече которых LLM прекращает генерацию.
 * @property systemPrompt System prompt — инструкция для LLM (роль, формат ответов).
 * @property contextStrategy Активная стратегия управления контекстом для Koog чата.
 * @property contextWindowSize Количество последних сообщений, хранимых полностью (для всех стратегий).
 */
data class AISettings(
    val isEnabled: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val stopSequences: List<String> = listOf("</answer>", "Human:", "User:"),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val contextStrategy: ContextStrategy = ContextStrategy.SUMMARY,
    val contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """Ты полезный ассистент. Правила ответа:
1. Отвечай структурированно: сначала краткий вывод, затем детали.
2. Используй markdown для форматирования кода и списков.
3. Заверши ответ тегом </answer>."""

        const val MIN_TOKENS = 64
        const val MAX_TOKENS = 8192

        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val DEFAULT_TEMPERATURE = 1.0f

        /** Сколько последних сообщений хранить "как есть" по умолчанию */
        const val DEFAULT_CONTEXT_WINDOW_SIZE = 10
        /** Минимум: хотя бы одна пара user+assistant */
        const val MIN_CONTEXT_WINDOW_SIZE = 2
        /** Максимум: ограничение для UI слайдера */
        const val MAX_CONTEXT_WINDOW_SIZE = 50
    }
}
