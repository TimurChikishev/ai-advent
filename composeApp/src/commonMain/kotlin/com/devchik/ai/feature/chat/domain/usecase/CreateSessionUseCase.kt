package com.devchik.ai.feature.chat.domain.usecase

import com.devchik.ai.feature.chat.domain.repository.ChatRepository

class CreateSessionUseCase(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(title: String): String = repository.createSession(title)
}
