package com.devchik.ai.feature.settings.domain.model

data class AISettings(
    val isEnabled: Boolean = true,
    val maxTokens: Int = 1024,
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
    }
}
