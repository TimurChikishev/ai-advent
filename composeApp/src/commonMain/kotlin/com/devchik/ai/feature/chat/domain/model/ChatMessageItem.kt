package com.devchik.ai.feature.chat.domain.model

/** Per-request token consumption from the LLM. Persisted in ChatMessageEntity for assistant messages. */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)

/**
 * Domain model for a single chat message. Maps 1:1 with ChatMessageEntity roles.
 *
 * - [User] / [Assistant] — actual dialogue messages, also fed to Koog's ChatMemory.
 * - [System] — UI-only informational messages (tool events, agent completion status).
 *   The marker text [ChatViewModel.CHAT_ENDED_MARKER] signals that the agent has finished.
 * - [Error] — agent/network failures shown in UI.
 */
sealed class ChatMessageItem {
    abstract val content: String

    data class User(override val content: String) : ChatMessageItem()
    data class Assistant(override val content: String, val tokenUsage: TokenUsage? = null) : ChatMessageItem()
    data class System(override val content: String) : ChatMessageItem()
    data class Error(override val content: String) : ChatMessageItem()
}
