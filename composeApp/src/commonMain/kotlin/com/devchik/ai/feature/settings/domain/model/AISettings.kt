package com.devchik.ai.feature.settings.domain.model

data class AISettings(
    val isEnabled: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val stopSequences: List<String> = listOf("</answer>", "Human:", "User:"),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
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
    }
}
