package com.devchik.ai.feature.ai.domain

import com.devchik.ai.feature.ai.domain.model.AIMessage

interface AIRepository {
    suspend fun sendMessage(prompt: String): Result<AIMessage>
}
