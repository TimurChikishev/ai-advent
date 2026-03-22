package com.devchik.ai.feature.chat.presentation

data class TokenUsageInfo(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)

data class ChatUiState(
    val title: String = "Koog Chat",
    val messages: List<ChatMessage> = emptyList(),
    val streamingContent: String = "",
    val inputText: String = "",
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isChatEnded: Boolean = false,
    val userResponseRequested: Boolean = false,
    val currentUserResponse: String? = null,
    val lastRequestTokens: TokenUsageInfo? = null,
    val sessionTotalTokens: TokenUsageInfo = TokenUsageInfo(),
)

sealed class ChatMessage {
    data class UserMessage(val text: String) : ChatMessage()
    data class AgentMessage(val text: String, val tokenUsage: TokenUsageInfo? = null) : ChatMessage()
    data class SystemMessage(val text: String) : ChatMessage()
    data class ErrorMessage(val text: String) : ChatMessage()
}
