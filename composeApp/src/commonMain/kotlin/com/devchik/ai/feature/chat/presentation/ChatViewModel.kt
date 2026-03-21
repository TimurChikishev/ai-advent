package com.devchik.ai.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            title = chatAgentProvider.title,
            messages = listOf(ChatMessage.SystemMessage(chatAgentProvider.description)),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val userInput = _uiState.value.inputText.trim()
        if (userInput.isEmpty()) return

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
                val agent = chatAgentProvider.provideAgent(
                    onToolCallEvent = { message ->
                        viewModelScope.launch {
                            _uiState.update {
                                it.copy(messages = it.messages + ChatMessage.SystemMessage("🔧 $message"))
                            }
                        }
                    },
                    onErrorEvent = { errorMessage ->
                        viewModelScope.launch {
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

                        _uiState.update {
                            it.copy(
                                messages = it.messages + ChatMessage.AgentMessage(displayMessage),
                                streamingContent = "",
                                isInputEnabled = true,
                                isLoading = false,
                                userResponseRequested = true,
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
                )

                val result = agent.run(userInput)

                _uiState.update {
                    it.copy(
                        messages = it.messages +
                            ChatMessage.SystemMessage("✅ Результат: $result") +
                            ChatMessage.SystemMessage("Агент завершил работу."),
                        isInputEnabled = false,
                        isLoading = false,
                        isChatEnded = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage.ErrorMessage("Ошибка: ${e.message}"),
                        isInputEnabled = true,
                        isLoading = false,
                    )
                }
            }
        }
    }
}
