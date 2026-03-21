package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.repository.ChatRepository

class SendMessageUseCase(
    private val repository: ChatRepository,
) {
    suspend fun saveUserMessage(sessionId: String, content: String) {
        repository.appendUserMessage(sessionId, content)
    }

    suspend fun saveAssistantMessage(sessionId: String, content: String) {
        repository.appendAssistantMessage(sessionId, content)
    }

    suspend fun saveSystemMessage(sessionId: String, content: String) {
        repository.appendSystemMessage(sessionId, content)
    }

    suspend fun saveErrorMessage(sessionId: String, content: String) {
        repository.appendErrorMessage(sessionId, content)
    }
}
