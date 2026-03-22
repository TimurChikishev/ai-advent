package com.devchik.ai.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.TokenUsage
import com.devchik.ai.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.devchik.ai.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for a single chat session. Manages UI state and orchestrates the Koog agent lifecycle.
 *
 * Lifecycle:
 * 1. On init, loads persisted history from DB via [loadChatHistoryUseCase] and restores UI state
 *    (messages, token totals, isChatEnded flag).
 * 2. On first user message, creates the agent via [chatAgentProvider.provideAgent] and calls
 *    agent.run(). The agent runs in a loop until ExitTool is called.
 * 3. Multi-turn dialogue is achieved through the onAssistantMessage callback: the agent suspends
 *    after each response, and resumes when [currentUserResponse] is set by [sendMessage].
 * 4. All messages are persisted incrementally via [sendMessageUseCase] as they occur.
 *
 * Threading: agent.run() executes on [Dispatchers.Default]. UI state updates happen via
 * [_uiState.update] which is thread-safe. DB writes in callbacks are suspend functions
 * protected by [RoomChatHistoryProvider]'s mutex.
 */
class ChatViewModel(
    private val chatAgentProvider: ChatAgentProvider,
    private val loadChatHistoryUseCase: LoadChatHistoryUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sessionId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            title = chatAgentProvider.title,
            messages = listOf(ChatMessage.SystemMessage(chatAgentProvider.description)),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    /**
     * Restores chat state from the database.
     * Rebuilds the message list, accumulates token totals from saved assistant messages,
     * and detects [CHAT_ENDED_MARKER] to restore isChatEnded / isInputEnabled.
     */
    private fun loadHistory() {
        viewModelScope.launch {
            val history = loadChatHistoryUseCase(sessionId)
            if (history.isNotEmpty()) {
                val uiMessages = mutableListOf<ChatMessage>()
                uiMessages.add(ChatMessage.SystemMessage(chatAgentProvider.description))
                var chatEnded = false
                var totalInput = 0
                var totalOutput = 0
                var totalAll = 0
                for (item in history) {
                    when (item) {
                        is ChatMessageItem.User -> uiMessages.add(ChatMessage.UserMessage(item.content))
                        is ChatMessageItem.Assistant -> {
                            val tokenInfo = item.tokenUsage?.let {
                                totalInput += it.inputTokens
                                totalOutput += it.outputTokens
                                totalAll += it.totalTokens
                                TokenUsageInfo(it.inputTokens, it.outputTokens, it.totalTokens)
                            }
                            uiMessages.add(ChatMessage.AgentMessage(item.content, tokenInfo))
                        }
                        is ChatMessageItem.System -> {
                            uiMessages.add(ChatMessage.SystemMessage(item.content))
                            if (item.content == CHAT_ENDED_MARKER) chatEnded = true
                        }
                        is ChatMessageItem.Error -> uiMessages.add(ChatMessage.ErrorMessage(item.content))
                    }
                }
                _uiState.update {
                    it.copy(
                        messages = uiMessages,
                        isChatEnded = chatEnded,
                        isInputEnabled = !chatEnded,
                        sessionTotalTokens = TokenUsageInfo(totalInput, totalOutput, totalAll),
                    )
                }
            }
        }
    }

    companion object {
        const val CHAT_ENDED_MARKER = "Агент завершил работу."
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Handles user message submission. Two modes:
     * - **userResponseRequested=true**: agent is already running and suspended in onAssistantMessage.
     *   Sets currentUserResponse to unblock the agent coroutine.
     * - **userResponseRequested=false**: first message — launches [runAgent] to start the agent loop.
     */
    fun sendMessage() {
        val userInput = _uiState.value.inputText.trim()
        if (userInput.isEmpty()) return

        viewModelScope.launch {
            sendMessageUseCase.saveUserMessage(sessionId, userInput)
        }

        if (_uiState.value.userResponseRequested) {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage.UserMessage(userInput),
                    inputText = "",
                    isLoading = true,
                    userResponseRequested = false,
                    currentUserResponse = userInput,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage.UserMessage(userInput),
                    inputText = "",
                    isInputEnabled = false,
                    isLoading = true,
                )
            }

            viewModelScope.launch {
                runAgent(userInput)
            }
        }
    }

    fun restartChat() {
        _uiState.update {
            ChatUiState(
                title = chatAgentProvider.title,
                messages = listOf(ChatMessage.SystemMessage(chatAgentProvider.description)),
            )
        }
    }

    /**
     * Creates and runs the Koog agent for this session.
     *
     * The agent loop:
     * 1. Agent sends user input to LLM via streaming.
     * 2. LLM response arrives as StreamFrame chunks → onStreamingDelta updates streamingContent.
     * 3. After full response, onTokenUsage fires (from StreamFrame.End) → stored in pendingTokenUsage.
     * 4. onAssistantMessage fires with complete text → saves to DB with token usage,
     *    updates UI, then suspends until user provides next input via currentUserResponse.
     * 5. If LLM calls ExitTool, agent.run() returns and we mark chat as ended.
     *
     * Token flow: onTokenUsage fires *before* onAssistantMessage for the same response,
     * so we buffer in pendingTokenUsage and consume it when saving the assistant message.
     */
    private suspend fun runAgent(userInput: String) {
        withContext(Dispatchers.Default) {
            try {
                var pendingTokenUsage: TokenUsageInfo? = null

                val agent = chatAgentProvider.provideAgent(
                    onToolCallEvent = { message ->
                        viewModelScope.launch {
                            val text = "🔧 $message"
                            sendMessageUseCase.saveSystemMessage(sessionId, text)
                            _uiState.update {
                                it.copy(messages = it.messages + ChatMessage.SystemMessage(text))
                            }
                        }
                    },
                    onErrorEvent = { errorMessage ->
                        viewModelScope.launch {
                            sendMessageUseCase.saveErrorMessage(sessionId, errorMessage)
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + ChatMessage.ErrorMessage(errorMessage),
                                    isInputEnabled = true,
                                    isLoading = false,
                                )
                            }
                        }
                    },
                    // Blocking callback: agent suspends here until user types next message.
                    // Uses streamedContent (accumulated chunks) if available, falling back to
                    // the message param (for non-streaming responses).
                    onAssistantMessage = { message ->
                        val streamedContent = _uiState.value.streamingContent
                        val displayMessage = streamedContent.ifEmpty { message }
                        val tokenUsage = pendingTokenUsage
                        pendingTokenUsage = null

                        val domainTokenUsage = tokenUsage?.let {
                            TokenUsage(it.inputTokens, it.outputTokens, it.totalTokens)
                        }
                        sendMessageUseCase.saveAssistantMessage(sessionId, displayMessage, domainTokenUsage)

                        _uiState.update { state ->
                            val newSessionTokens = if (tokenUsage != null) {
                                TokenUsageInfo(
                                    inputTokens = state.sessionTotalTokens.inputTokens + tokenUsage.inputTokens,
                                    outputTokens = state.sessionTotalTokens.outputTokens + tokenUsage.outputTokens,
                                    totalTokens = state.sessionTotalTokens.totalTokens + tokenUsage.totalTokens,
                                )
                            } else state.sessionTotalTokens

                            state.copy(
                                messages = state.messages + ChatMessage.AgentMessage(
                                    text = displayMessage,
                                    tokenUsage = tokenUsage,
                                ),
                                streamingContent = "",
                                isInputEnabled = true,
                                isLoading = false,
                                userResponseRequested = true,
                                lastRequestTokens = tokenUsage,
                                sessionTotalTokens = newSessionTokens,
                            )
                        }

                        // Suspend until sendMessage() sets currentUserResponse
                        val userResponse = _uiState
                            .first { it.currentUserResponse != null }
                            .currentUserResponse
                            ?: throw IllegalArgumentException("User response is null")

                        _uiState.update { it.copy(currentUserResponse = null) }

                        userResponse
                    },
                    onStreamingDelta = { chunk ->
                        _uiState.update {
                            it.copy(streamingContent = it.streamingContent + chunk)
                        }
                    },
                    onTokenUsage = { usage ->
                        pendingTokenUsage = TokenUsageInfo(
                            inputTokens = usage.inputTokens,
                            outputTokens = usage.outputTokens,
                            totalTokens = usage.totalTokens,
                        )
                    },
                )

                val result = agent.run(userInput, sessionId)

                val resultText = "✅ Результат: $result"
                val doneText = "Агент завершил работу."
                sendMessageUseCase.saveSystemMessage(sessionId, resultText)
                sendMessageUseCase.saveSystemMessage(sessionId, doneText)

                _uiState.update {
                    it.copy(
                        messages = it.messages +
                            ChatMessage.SystemMessage(resultText) +
                            ChatMessage.SystemMessage(doneText),
                        isInputEnabled = false,
                        isLoading = false,
                        isChatEnded = true,
                    )
                }
            } catch (e: Exception) {
                val errorText = "Ошибка: ${e.message}"
                sendMessageUseCase.saveErrorMessage(sessionId, errorText)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage.ErrorMessage(errorText),
                        isInputEnabled = true,
                        isLoading = false,
                    )
                }
            }
        }
    }
}
