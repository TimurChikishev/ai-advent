package com.devchik.ai.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devchik.ai.feature.settings.domain.model.AISettings
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Настройки сохранены")
            viewModel.clearSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Мастер-переключатель
            SectionCard(
                title = "Параметры запроса",
                subtitle = if (uiState.settings.isEnabled) "Включены" else "Выключены",
                trailing = {
                    Switch(
                        checked = uiState.settings.isEnabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                },
            )

            HorizontalDivider()

            val enabled = uiState.settings.isEnabled

            // Максимум токенов
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Максимум токенов", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "≈ ${uiState.settings.maxTokens * 3 / 4} слов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${uiState.settings.maxTokens}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = uiState.settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.setMaxTokens(it.toInt()) },
                    valueRange = AISettings.MIN_TOKENS.toFloat()..AISettings.MAX_TOKENS.toFloat(),
                    steps = 31,
                    enabled = enabled,
                )
            }

            HorizontalDivider()

            // Температура
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Температура", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Чем выше — тем креативнее ответы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "%.1f".format(uiState.settings.temperature),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = uiState.settings.temperature,
                    onValueChange = viewModel::setTemperature,
                    valueRange = AISettings.MIN_TEMPERATURE..AISettings.MAX_TEMPERATURE,
                    steps = 19,
                    enabled = enabled,
                )
            }

            HorizontalDivider()

            // Стоп-последовательности
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Стоп-последовательности", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Каждая с новой строки. Модель остановится, встретив любую.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.stopSequencesText,
                    onValueChange = viewModel::setStopSequencesText,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("</answer>\nHuman:") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = enabled,
                )
            }

            HorizontalDivider()

            // System prompt
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("System Prompt", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Инструкция для модели. Определяет формат и стиль ответов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.settings.systemPrompt,
                    onValueChange = viewModel::setSystemPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ты полезный ассистент...") },
                    minLines = 5,
                    maxLines = 12,
                    enabled = enabled,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
