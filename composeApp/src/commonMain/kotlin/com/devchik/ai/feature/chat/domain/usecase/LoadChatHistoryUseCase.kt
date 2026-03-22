package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import com.devchik.ai.feature.chat.domain.repository.ContextStats

class LoadChatHistoryUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(sessionId: String): List<ChatMessageItem> =
        repository.loadHistory(sessionId)

    suspend fun getContextStats(sessionId: String): ContextStats =
        repository.getContextStats(sessionId)
}
