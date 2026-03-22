package com.devchik.ai.feature.chat.domain.model

/** Domain model for a chat session entry in the session list. */
data class ChatSession(
    val id: String,
    val title: String,
    /** Truncated preview of the last user/assistant message (up to 100 chars). */
    val lastMessage: String,
    val updatedAt: Long,
)
