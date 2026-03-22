package com.devchik.ai.feature.chat.domain.model

data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)

sealed class ChatMessageItem {
    abstract val content: String

    data class User(override val content: String) : ChatMessageItem()
    data class Assistant(override val content: String, val tokenUsage: TokenUsage? = null) : ChatMessageItem()
    data class System(override val content: String) : ChatMessageItem()
    data class Error(override val content: String) : ChatMessageItem()
}
