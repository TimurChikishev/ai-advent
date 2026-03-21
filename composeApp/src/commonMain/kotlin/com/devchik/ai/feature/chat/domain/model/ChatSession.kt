package com.devchik.ai.feature.chat.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val lastMessage: String,
    val updatedAt: Long,
)
