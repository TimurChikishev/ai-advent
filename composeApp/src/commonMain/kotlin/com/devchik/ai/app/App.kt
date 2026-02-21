package com.devchik.ai.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devchik.ai.feature.ai.presentation.AIScreen
import com.devchik.ai.feature.settings.presentation.SettingsScreen

private enum class Screen { Chat, Settings }

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    MaterialTheme {
        when (currentScreen) {
            Screen.Chat -> AIScreen(
                onOpenSettings = { currentScreen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(
                onBack = { currentScreen = Screen.Chat },
            )
        }
    }
}
