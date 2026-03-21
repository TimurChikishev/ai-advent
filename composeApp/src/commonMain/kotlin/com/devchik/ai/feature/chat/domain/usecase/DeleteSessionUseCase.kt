package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.repository.ChatRepository

class DeleteSessionUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteSession(id)
}
