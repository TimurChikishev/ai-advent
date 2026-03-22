package com.devchik.ai.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.usecase.CreateSessionUseCase
import com.devchik.ai.feature.chat.domain.usecase.DeleteSessionUseCase
import com.devchik.ai.feature.chat.domain.usecase.GetSessionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionListUiState(
    val sessions: List<SessionItem> = emptyList(),
    val isLoading: Boolean = true,
)

data class SessionItem(
    val id: String,
    val title: String,
    val lastMessage: String,
    val updatedAt: Long,
)

/**
 * ViewModel for the session list screen.
 * Reactively observes all chat sessions via [GetSessionsUseCase] (backed by Room Flow).
 * Session creation returns the new ID so the UI can navigate to it immediately.
 */
class SessionListViewModel(
    private val getSessionsUseCase: GetSessionsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSessionsUseCase().collect { sessions ->
                val items = sessions.map { it.toSessionItem() }
                _uiState.update { it.copy(sessions = items, isLoading = false) }
            }
        }
    }

    /** Creates a new session and returns its ID for immediate navigation. */
    suspend fun createNewSession(): String {
        return createSessionUseCase("Новый чат")
    }

    /** Deletes session by ID. Cascade delete removes all messages (FK constraint in Room). */
    fun deleteSession(id: String) {
        viewModelScope.launch {
            deleteSessionUseCase(id)
        }
    }
}

private fun ChatSession.toSessionItem() = SessionItem(
    id = id,
    title = title,
    lastMessage = lastMessage,
    updatedAt = updatedAt,
)
