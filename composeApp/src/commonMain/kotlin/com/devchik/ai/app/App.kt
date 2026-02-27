package com.devchik.ai.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devchik.ai.feature.ai.presentation.AIScreen
import com.devchik.ai.feature.menu.presentation.MenuScreen
import com.devchik.ai.feature.settings.presentation.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object MenuRoute

@Serializable
object ChatRoute

@Serializable
object SettingsRoute

@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = MenuRoute) {
            composable<MenuRoute> {
                MenuScreen(
                    onOpenChat = { navController.navigate(ChatRoute) },
                )
            }
            composable<ChatRoute> {
                AIScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
