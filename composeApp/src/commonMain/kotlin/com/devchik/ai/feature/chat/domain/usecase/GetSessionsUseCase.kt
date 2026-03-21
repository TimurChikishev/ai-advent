package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetSessionsUseCase(
    private val repository: ChatRepository,
) {
    operator fun invoke(): Flow<List<ChatSession>> = repository.getSessions()
}
