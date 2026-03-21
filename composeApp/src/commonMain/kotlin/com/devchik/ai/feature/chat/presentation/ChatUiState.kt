package com.devchik.ai.feature.chat.presentation

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
)

sealed class ChatMessage {
    data class UserMessage(val text: String) : ChatMessage()
    data class AgentMessage(val text: String) : ChatMessage()
    data class SystemMessage(val text: String) : ChatMessage()
    data class ErrorMessage(val text: String) : ChatMessage()
}
