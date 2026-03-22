package com.devchik.ai.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.devchik.ai.feature.ai.presentation.AIScreen
import com.devchik.ai.feature.chat.presentation.ChatScreen
import com.devchik.ai.feature.chat.presentation.ChatViewModel
import com.devchik.ai.feature.chat.presentation.SessionListScreen
import com.devchik.ai.feature.comparison.presentation.ComparisonDetailScreen
import com.devchik.ai.feature.comparison.presentation.ComparisonScreen
import com.devchik.ai.feature.comparison.presentation.ComparisonViewModel
import com.devchik.ai.feature.menu.presentation.MenuScreen
import com.devchik.ai.feature.settings.presentation.SettingsScreen
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object MenuRoute

@Serializable
object ChatRoute

@Serializable
object SettingsRoute

@Serializable
object KoogChatRoute

@Serializable
data class KoogChatSessionRoute(val sessionId: String)

@Serializable
object ComparisonRoute

@Serializable
data class ComparisonDetailRoute(val modelIndex: Int)

@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = MenuRoute) {
            composable<MenuRoute> {
                MenuScreen(
                    onOpenChat = { navController.navigate(ChatRoute) },
                    onOpenKoogChat = { navController.navigate(KoogChatRoute) },
                    onOpenComparison = { navController.navigate(ComparisonRoute) },
                )
            }
            composable<ChatRoute> {
                AIScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<KoogChatRoute> {
                SessionListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSession = { sessionId ->
                        navController.navigate(KoogChatSessionRoute(sessionId))
                    },
                )
            }
            composable<KoogChatSessionRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<KoogChatSessionRoute>()
                val viewModel = koinViewModel<ChatViewModel>(
                    viewModelStoreOwner = backStackEntry,
                    parameters = { parametersOf(route.sessionId) },
                )
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToBranch = { branchSessionId ->
                        navController.navigate(KoogChatSessionRoute(branchSessionId))
                    },
                    viewModel = viewModel,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ComparisonRoute> { backStackEntry ->
                val viewModel = koinViewModel<ComparisonViewModel>(
                    viewModelStoreOwner = backStackEntry,
                )
                ComparisonScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { index -> navController.navigate(ComparisonDetailRoute(index)) },
                    viewModel = viewModel,
                )
            }
            composable<ComparisonDetailRoute> { backStackEntry ->
                val comparisonEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<ComparisonRoute>()
                }
                val viewModel = koinViewModel<ComparisonViewModel>(
                    viewModelStoreOwner = comparisonEntry,
                )
                val route = backStackEntry.toRoute<ComparisonDetailRoute>()
                ComparisonDetailScreen(
                    modelIndex = route.modelIndex,
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
        }
    }
}
