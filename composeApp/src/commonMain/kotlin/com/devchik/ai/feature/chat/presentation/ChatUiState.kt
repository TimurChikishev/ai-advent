package com.devchik.ai.feature.chat.presentation

import com.devchik.ai.feature.chat.domain.model.ContextStrategy

/** Информация о ветке диалога для отображения в UI. */
data class BranchInfo(
    val sessionId: String,
    val title: String,
)

/** Presentation-layer token usage data. Mirrors domain [TokenUsage] for UI consumption. */
data class TokenUsageInfo(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)

/**
 * Статистика управления контекстом для отображения в TopAppBar чата.
 *
 * Обновляется после загрузки истории и после каждого ответа ассистента.
 * Данные приходят от активной [ContextBuildStrategy] через [ContextManager].
 */
data class ContextStatsInfo(
    val totalMessages: Int = 0,
    val contextMessages: Int = 0,
    val strategyLabel: String = "",
    val details: String = "",
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
    val streamingContent: String = "",
    val inputText: String = "",
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isChatEnded: Boolean = false,
    val userResponseRequested: Boolean = false,
    val currentUserResponse: String? = null,
    val lastRequestTokens: TokenUsageInfo? = null,
    val sessionTotalTokens: TokenUsageInfo = TokenUsageInfo(),
    val contextStats: ContextStatsInfo = ContextStatsInfo(),
    /** Правая панель настроек стратегии контекста: видимость */
    val isContextSettingsOpen: Boolean = false,
    /** Per-session стратегия контекста. null = глобальные настройки. */
    val sessionContextStrategy: ContextStrategy? = null,
    /** Per-session размер окна контекста. null = глобальные настройки. */
    val sessionContextWindowSize: Int? = null,
    /** Список дочерних веток текущей сессии. */
    val branches: List<BranchInfo> = emptyList(),
    /** Родительская сессия, если текущая сессия — ветка. */
    val parentBranch: BranchInfo? = null,
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
