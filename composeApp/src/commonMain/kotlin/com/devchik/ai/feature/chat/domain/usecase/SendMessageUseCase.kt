package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.model.TokenUsage
import com.devchik.ai.feature.chat.domain.repository.ChatRepository

/**
 * UseCase for persisting individual chat messages.
 *
 * Called by [ChatViewModel] after each event:
 * - [saveUserMessage]: when user sends a message (before agent processes it).
 * - [saveAssistantMessage]: when agent produces a full response (with optional token usage).
 * - [saveSystemMessage]: for tool call events and agent completion markers.
 * - [saveErrorMessage]: on agent execution failure or network errors.
 */
class SendMessageUseCase(
    private val repository: ChatRepository,
) {
    suspend fun saveUserMessage(sessionId: String, content: String) {
        repository.appendUserMessage(sessionId, content)
    }

    suspend fun saveAssistantMessage(sessionId: String, content: String, tokenUsage: TokenUsage? = null) {
        repository.appendAssistantMessage(sessionId, content, tokenUsage)
    }

    suspend fun saveSystemMessage(sessionId: String, content: String) {
        repository.appendSystemMessage(sessionId, content)
    }

    suspend fun saveErrorMessage(sessionId: String, content: String) {
        repository.appendErrorMessage(sessionId, content)
    }
}
