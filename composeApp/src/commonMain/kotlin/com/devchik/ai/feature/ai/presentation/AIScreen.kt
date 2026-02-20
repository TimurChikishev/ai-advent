package com.devchik.ai.feature.ai.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AIScreen(
    viewModel: AIViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // todo
}
