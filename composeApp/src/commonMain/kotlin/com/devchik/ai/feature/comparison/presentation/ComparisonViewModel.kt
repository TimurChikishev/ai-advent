package com.devchik.ai.feature.comparison.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devchik.ai.BuildKonfig
import com.devchik.ai.feature.comparison.domain.ComparisonRepository
import com.devchik.ai.feature.comparison.domain.model.ModelConfig
import com.devchik.ai.feature.comparison.domain.model.ModelResult
import com.devchik.ai.feature.comparison.domain.model.ModelTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComparisonUiState(
    val prompt: String = "",
    val results: List<ModelResult> = ComparisonViewModel.DEFAULT_MODELS.map { ModelResult(config = it) },
    val analysis: String = "",
    val streamingAnalysis: String = "",
    val isRunning: Boolean = false,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
) {
    val totalDurationMs: Long get() = results.sumOf { it.durationMs }
    val totalTokens: Int get() = results.sumOf { it.tokensUsed }
    val isAnyLoading: Boolean get() = results.any { it.isLoading }
    val hasResults: Boolean get() = results.any { it.content.isNotEmpty() }
    val displayAnalysis: String get() = streamingAnalysis.ifEmpty { analysis }
}

class ComparisonViewModel(
    private val repository: ComparisonRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState())
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    fun setPrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun runComparison() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isBlank()) return

        _uiState.update { state ->
            state.copy(
                isRunning = true,
                analysis = "",
                streamingAnalysis = "",
                results = DEFAULT_MODELS.map {
                    ModelResult(config = it, isLoading = true)
                },
            )
        }

        DEFAULT_MODELS.forEachIndexed { index, config ->
            viewModelScope.launch {
                val startMark = kotlin.time.TimeSource.Monotonic.markNow()
                var fullContent = ""
                var hasError = false

                repository.query(config, prompt)
                    .catch { e ->
                        hasError = true
                        updateResult(index) { it.copy(isLoading = false, error = e.message) }
                    }
                    .onCompletion {
                        if (!hasError) {
                            val duration = startMark.elapsedNow().inWholeMilliseconds
                            val tokens = repository.getTokenCount(config, prompt, fullContent)
                            updateResult(index) { result ->
                                result.copy(
                                    content = fullContent,
                                    streamingContent = "",
                                    tokensUsed = tokens,
                                    durationMs = duration,
                                    isLoading = false,
                                )
                            }
                        }
                        checkAllFinished()
                    }
                    .collect { chunk ->
                        fullContent += chunk
                        updateResult(index) { it.copy(streamingContent = fullContent) }
                    }
            }
        }
    }

    fun analyze() {
        if (!_uiState.value.hasResults) return
        val prompt = _uiState.value.prompt.trim()
        val results = _uiState.value.results.filter { it.content.isNotEmpty() }

        _uiState.update { it.copy(isAnalyzing = true, analysis = "", streamingAnalysis = "") }

        viewModelScope.launch {
            var fullAnalysis = ""
            var hasError = false
            repository.analyze(
                prompt = prompt,
                results = results.map { it.config.displayName to it.content },
            )
                .catch { e ->
                    hasError = true
                    _uiState.update { it.copy(isAnalyzing = false, error = e.message) }
                }
                .onCompletion {
                    if (!hasError) {
                        _uiState.update {
                            it.copy(
                                analysis = fullAnalysis,
                                streamingAnalysis = "",
                                isAnalyzing = false,
                            )
                        }
                    }
                }
                .collect { chunk ->
                    fullAnalysis += chunk
                    _uiState.update { it.copy(streamingAnalysis = fullAnalysis) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateResult(index: Int, transform: (ModelResult) -> ModelResult) {
        _uiState.update { state ->
            state.copy(
                results = state.results.toMutableList().also { list ->
                    if (index < list.size) list[index] = transform(list[index])
                }
            )
        }
    }

    private fun checkAllFinished() {
        val allDone = _uiState.value.results.none { it.isLoading }
        if (allDone) {
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    companion object {
val DEFAULT_MODELS get() = listOf(
    ModelConfig(
        tier = ModelTier.Weak,
        modelId = "qwen2.5-7b-instruct",
        displayName = "Qwen2.5 7B",
        parameters = "7B",
        providerName = "Alibaba / Qwen",
        apiEndpoint = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        apiKey = BuildKonfig.QWEN_API_KEY,
        maxTokens = 1024,
        temperature = 0.7f,
    ),
    ModelConfig(
        tier = ModelTier.Medium,
        modelId = "qwen-turbo",
        displayName = "Qwen Turbo",
        parameters = "~14B",
        providerName = "Alibaba / Qwen",
        apiEndpoint = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        apiKey = BuildKonfig.QWEN_API_KEY,
        maxTokens = 2048,
        temperature = 0.7f,
    ),
    ModelConfig(
        tier = ModelTier.Strong,
        modelId = "deepseek-reasoner",
        displayName = "DeepSeek R1",
        parameters = "671B",
        providerName = "DeepSeek",
        apiEndpoint = "https://api.deepseek.com",
        apiKey = BuildKonfig.DEEPSEEK_API_KEY,
        maxTokens = 4096,
        temperature = 0.7f,
    ),
)
    }
}
