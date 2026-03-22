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
