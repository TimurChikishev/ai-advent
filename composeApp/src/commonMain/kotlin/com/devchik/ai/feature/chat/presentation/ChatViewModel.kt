package com.devchik.ai.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.ContextStrategy
import com.devchik.ai.feature.chat.domain.model.TokenUsage
import com.devchik.ai.feature.chat.domain.repository.SessionContextSettings
import com.devchik.ai.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.devchik.ai.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** Job агента — хранится для возможности отмены генерации пользователем. */
    private var agentJob: Job? = null

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
            refreshContextStats()
            loadSessionContextSettings()
            loadBranchInfo()
        }
    }

    /** Загружает per-session настройки стратегии из БД */
    private suspend fun loadSessionContextSettings() {
        try {
            val settings = loadChatHistoryUseCase.getSessionContextSettings(sessionId)
            _uiState.update {
                it.copy(
                    sessionContextStrategy = settings.contextStrategy,
                    sessionContextWindowSize = settings.contextWindowSize,
                )
            }
        } catch (_: Exception) {
            // Non-critical
        }
    }

    private suspend fun refreshContextStats() {
        try {
            val stats = loadChatHistoryUseCase.getContextStats(sessionId)
            _uiState.update {
                it.copy(
                    contextStats = ContextStatsInfo(
                        totalMessages = stats.totalMessages,
                        contextMessages = stats.contextMessages,
                        strategyLabel = stats.strategyLabel,
                        details = stats.details,
                    )
                )
            }
        } catch (_: Exception) {
            // Non-critical: stats display failure shouldn't break chat
        }
    }

    companion object {
        const val CHAT_ENDED_MARKER = "Агент завершил работу."
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Открыть/закрыть правую панель настроек стратегии */
    fun toggleContextSettings() {
        _uiState.update { it.copy(isContextSettingsOpen = !it.isContextSettingsOpen) }
    }

    fun closeContextSettings() {
        _uiState.update { it.copy(isContextSettingsOpen = false) }
    }

    /** Изменить стратегию контекста для текущей сессии и сохранить в БД */
    fun setSessionContextStrategy(strategy: ContextStrategy?) {
        _uiState.update { it.copy(sessionContextStrategy = strategy) }
        saveSessionContextSettings()
    }

    /** Изменить размер окна контекста для текущей сессии и сохранить в БД */
    fun setSessionContextWindowSize(size: Int?) {
        _uiState.update { it.copy(sessionContextWindowSize = size) }
        saveSessionContextSettings()
    }

    private fun saveSessionContextSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            loadChatHistoryUseCase.updateSessionContextSettings(
                sessionId,
                SessionContextSettings(
                    contextStrategy = state.sessionContextStrategy,
                    contextWindowSize = state.sessionContextWindowSize,
                ),
            )
            refreshContextStats()
        }
    }

    /** Загружает ветки текущей сессии и информацию о родителе (если текущая — ветка) */
    private suspend fun loadBranchInfo() {
        try {
            val branches = loadChatHistoryUseCase.getBranches(sessionId)
            val parent = loadChatHistoryUseCase.getParentSession(sessionId)
            _uiState.update {
                it.copy(
                    branches = branches.map { s -> BranchInfo(sessionId = s.id, title = s.title) },
                    parentBranch = parent?.let { p -> BranchInfo(sessionId = p.id, title = p.title) },
                )
            }
        } catch (_: Exception) { }
    }

    /**
     * Создаёт ветку от текущего момента диалога.
     * Возвращает sessionId новой ветки для навигации.
     */
    fun createBranch(onBranchCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val branchCount = _uiState.value.branches.size + 1
                val title = "${_uiState.value.title} — Branch $branchCount"
                val branchSessionId = loadChatHistoryUseCase.createBranch(sessionId, title)
                loadBranchInfo()
                onBranchCreated(branchSessionId)
            } catch (_: Exception) { }
        }
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

            agentJob = viewModelScope.launch {
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
     * Останавливает текущую генерацию ответа.
     *
     * Отменяет Job агента и сохраняет накопленный streaming-контент как частичное
     * сообщение ассистента. UI возвращается в состояние готовности ввода.
     * Если streaming-контента нет (агент ещё не начал отвечать), сохраняется
     * системное сообщение о прерванной генерации.
     */
    fun stopGeneration() {
        agentJob?.cancel()
        agentJob = null

        val partialContent = _uiState.value.streamingContent

        viewModelScope.launch {
            if (partialContent.isNotBlank()) {
                sendMessageUseCase.saveAssistantMessage(sessionId, partialContent, null)
            }
            val stopText = "⏹ Генерация остановлена"
            sendMessageUseCase.saveSystemMessage(sessionId, stopText)

            _uiState.update { state ->
                val newMessages = state.messages.toMutableList()
                if (partialContent.isNotBlank()) {
                    newMessages.add(ChatMessage.AgentMessage(text = partialContent))
                }
                newMessages.add(ChatMessage.SystemMessage(stopText))

                state.copy(
                    messages = newMessages,
                    streamingContent = "",
                    isInputEnabled = true,
                    isLoading = false,
                    userResponseRequested = false,
                )
            }
            refreshContextStats()
        }
    }

    /**
     * Создаёт и запускает Koog-агента для текущей сессии.
     *
     * Цикл агента (бесконечный, пока пользователь не остановит):
     * 1. Agent отправляет ввод пользователя в LLM через streaming.
     * 2. Ответ LLM приходит чанками StreamFrame → onStreamingDelta обновляет streamingContent.
     * 3. После полного ответа срабатывает onTokenUsage (из StreamFrame.End) → буферизуется в pendingTokenUsage.
     * 4. onAssistantMessage получает полный текст → сохраняет в БД с token usage,
     *    обновляет UI, затем приостанавливается до следующего ввода пользователя.
     *
     * Агент не завершается сам — только через отмену Job (stopGeneration) или ошибку.
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
                    onAssistantMessage = { message ->
                        val streamedContent = _uiState.value.streamingContent
                        val displayMessage = streamedContent.ifEmpty { message }
                        val tokenUsage = pendingTokenUsage
                        pendingTokenUsage = null

                        val domainTokenUsage = tokenUsage?.let {
                            TokenUsage(it.inputTokens, it.outputTokens, it.totalTokens)
                        }
                        sendMessageUseCase.saveAssistantMessage(sessionId, displayMessage, domainTokenUsage)
                        refreshContextStats()

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

                agent.run(userInput, sessionId)
            } catch (e: CancellationException) {
                /* Пользователь остановил генерацию — stopGeneration() уже обработал UI/DB */
                throw e
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
