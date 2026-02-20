package com.devchik.ai.feature.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.domain.model.AIMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AIUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AIViewModel(
    private val repository: AIRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()
}
