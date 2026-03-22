package com.devchik.ai.feature.chat.presentation

/** Presentation-layer token usage data. Mirrors domain [TokenUsage] for UI consumption. */
data class TokenUsageInfo(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)

/**
 * Complete UI state for [ChatScreen].
 *
 * State flow:
 * 1. Initial: description SystemMessage, isInputEnabled=true.
 * 2. User sends message → isInputEnabled=false, isLoading=true, agent starts.
 * 3. Streaming → streamingContent accumulates chunks (shown as StreamingBubble).
 * 4. Agent responds → AgentMessage added, streamingContent cleared,
 *    userResponseRequested=true (agent suspends waiting for next input).
 * 5. User sends next message → currentUserResponse set, agent resumes.
 * 6. Agent calls ExitTool → isChatEnded=true, input field hidden, "Начать новый чат" shown.
 *
 * On session restore (loadHistory): messages rebuilt from DB, isChatEnded and
 * sessionTotalTokens restored from persisted data.
 */
data class ChatUiState(
    val title: String = "Koog Chat",
    val messages: List<ChatMessage> = emptyList(),
    /** Accumulates streaming text chunks before full response is finalized. */
    val streamingContent: String = "",
    val inputText: String = "",
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
    /** True after agent terminates via ExitTool. Hides input, shows restart button. */
    val isChatEnded: Boolean = false,
    /**
     * True when the agent is suspended in [ChatAgentProvider]'s onAssistantMessage callback,
     * waiting for the user's next message. The next sendMessage() writes to [currentUserResponse]
     * which unblocks the agent.
     */
    val userResponseRequested: Boolean = false,
    /** Bridge between sendMessage() and the suspended agent callback. Set by UI, consumed by agent. */
    val currentUserResponse: String? = null,
    val lastRequestTokens: TokenUsageInfo? = null,
    /** Accumulated token usage across all assistant responses in this session. */
    val sessionTotalTokens: TokenUsageInfo = TokenUsageInfo(),
)

/**
 * Presentation-layer message types displayed in the chat LazyColumn.
 * Maps from domain [ChatMessageItem] during history load, or created live during agent execution.
 */
sealed class ChatMessage {
    data class UserMessage(val text: String) : ChatMessage()
    data class AgentMessage(val text: String, val tokenUsage: TokenUsageInfo? = null) : ChatMessage()
    data class SystemMessage(val text: String) : ChatMessage()
    data class ErrorMessage(val text: String) : ChatMessage()
}
