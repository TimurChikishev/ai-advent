package com.devchik.ai.feature.ai.domain

import com.devchik.ai.feature.ai.domain.model.AIMessage
import kotlinx.coroutines.flow.Flow

interface AIRepository {
    fun sendMessage(messages: List<AIMessage>): Flow<String>
}
