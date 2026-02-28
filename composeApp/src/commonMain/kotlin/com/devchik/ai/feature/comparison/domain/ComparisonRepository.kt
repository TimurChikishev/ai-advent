package com.devchik.ai.feature.comparison.domain

import com.devchik.ai.feature.comparison.domain.model.ModelConfig
import kotlinx.coroutines.flow.Flow

interface ComparisonRepository {
    fun query(config: ModelConfig, prompt: String): Flow<String>
    fun analyze(prompt: String, results: List<Pair<String, String>>): Flow<String>
    suspend fun getTokenCount(config: ModelConfig, prompt: String, response: String): Int
}
