package com.devchik.ai.feature.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.domain.model.AIMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AIUiState(
    val messages: List<AIMessage> = emptyList(),
    val streamingContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AIViewModel(
    private val repository: AIRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = AIMessage(role = AIMessage.Role.User, content = text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                streamingContent = "",
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            repository.sendMessage(_uiState.value.messages)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .onCompletion {
                    val content = _uiState.value.streamingContent
                    if (content.isNotEmpty()) {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + AIMessage(
                                    role = AIMessage.Role.Assistant,
                                    content = content,
                                ),
                                streamingContent = "",
                                isLoading = false,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                .collect { chunk ->
                    _uiState.update { it.copy(streamingContent = it.streamingContent + chunk) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
