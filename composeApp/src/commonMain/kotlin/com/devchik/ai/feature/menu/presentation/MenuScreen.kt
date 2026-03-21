package com.devchik.ai.feature.menu.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onOpenChat: () -> Unit,
    onOpenKoogChat: () -> Unit,
    onOpenComparison: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Меню") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🤖",
                style = MaterialTheme.typography.displayLarge,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "AI Ассистент",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Умный помощник на базе DeepSeek",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "Открыть чат",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onOpenKoogChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "🧠 Koog Agent Chat",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onOpenComparison,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "⚖️ Сравнить модели",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
