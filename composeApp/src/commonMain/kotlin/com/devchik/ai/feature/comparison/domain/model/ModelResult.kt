package com.devchik.ai.feature.comparison.domain.model

data class ModelResult(
    val config: ModelConfig,
    val content: String = "",
    val streamingContent: String = "",
    val tokensUsed: Int = 0,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val wordCount: Int
        get() = if (content.isBlank()) 0
        else content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }

    val isStreaming: Boolean get() = streamingContent.isNotEmpty()
    val displayContent: String get() = if (isStreaming) streamingContent else content
}
