package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import com.devchik.ai.feature.chat.domain.repository.ContextStats
import com.devchik.ai.feature.chat.domain.repository.SessionContextSettings

class LoadChatHistoryUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(sessionId: String): List<ChatMessageItem> =
        repository.loadHistory(sessionId)

    suspend fun getContextStats(sessionId: String): ContextStats =
        repository.getContextStats(sessionId)

    suspend fun getSessionContextSettings(sessionId: String): SessionContextSettings =
        repository.getSessionContextSettings(sessionId)

    suspend fun updateSessionContextSettings(sessionId: String, settings: SessionContextSettings) =
        repository.updateSessionContextSettings(sessionId, settings)

    suspend fun createBranch(sourceSessionId: String, title: String): String =
        repository.createBranch(sourceSessionId, title)

    suspend fun getBranches(sessionId: String): List<ChatSession> =
        repository.getBranches(sessionId)

    suspend fun getParentSession(sessionId: String): ChatSession? =
        repository.getParentSession(sessionId)
}
