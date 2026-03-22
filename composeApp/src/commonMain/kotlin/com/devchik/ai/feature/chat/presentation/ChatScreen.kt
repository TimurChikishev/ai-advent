package com.devchik.ai.feature.chat.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devchik.ai.feature.chat.domain.model.ContextStrategy
import com.devchik.ai.feature.settings.domain.model.AISettings
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main chat screen. Displays message history, streaming responses, input controls,
 * and a right-side sliding panel for per-session context strategy settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit = {},
    onNavigateToBranch: (String) -> Unit = {},
    viewModel: ChatViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiState.title)
                            if (uiState.sessionTotalTokens.totalTokens > 0) {
                                Text(
                                    text = "Tokens: ${uiState.sessionTotalTokens.totalTokens} " +
                                        "(in: ${uiState.sessionTotalTokens.inputTokens}, " +
                                        "out: ${uiState.sessionTotalTokens.outputTokens})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (uiState.contextStats.strategyLabel.isNotEmpty()) {
                                Text(
                                    text = "${uiState.contextStats.strategyLabel}: ${uiState.contextStats.details}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("←", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleContextSettings() }) {
                            Text("⚙", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }

                    items(uiState.messages) { message ->
                        when (message) {
                            is ChatMessage.UserMessage -> UserBubble(message.text)
                            is ChatMessage.AgentMessage -> AgentBubble(message.text, message.tokenUsage)
                            is ChatMessage.SystemMessage -> SystemBubble(message.text)
                            is ChatMessage.ErrorMessage -> ErrorBubble(message.text)
                        }
                    }

                    if (uiState.streamingContent.isNotEmpty()) {
                        item(key = "streaming") {
                            StreamingBubble(uiState.streamingContent)
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (uiState.isChatEnded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(onClick = { viewModel.restartChat() }) {
                            Text("Начать новый чат")
                        }
                    }
                } else {
                    ChatInputBar(
                        text = uiState.inputText,
                        onTextChange = { viewModel.updateInputText(it) },
                        onSend = { viewModel.sendMessage() },
                        onStop = { viewModel.stopGeneration() },
                        isEnabled = uiState.isInputEnabled,
                        isLoading = uiState.isLoading,
                    )
                }
            }
        }

        // Затемнённый фон при открытой панели — клик закрывает панель
        if (uiState.isContextSettingsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable { viewModel.closeContextSettings() },
            )
        }

        // Правая sliding-панель настроек стратегии контекста
        AnimatedVisibility(
            visible = uiState.isContextSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ContextSettingsPanel(
                currentStrategy = uiState.sessionContextStrategy,
                currentWindowSize = uiState.sessionContextWindowSize,
                onStrategyChange = viewModel::setSessionContextStrategy,
                onWindowSizeChange = viewModel::setSessionContextWindowSize,
                onClose = viewModel::closeContextSettings,
                branches = uiState.branches,
                parentBranch = uiState.parentBranch,
                onCreateBranch = { viewModel.createBranch(onNavigateToBranch) },
                onNavigateToBranch = onNavigateToBranch,
            )
        }
    }
}

/**
 * Правая панель настроек стратегии контекста, привязанных к текущей сессии.
 * null-значения означают "использовать глобальные настройки".
 */
@Composable
private fun ContextSettingsPanel(
    currentStrategy: ContextStrategy?,
    currentWindowSize: Int?,
    onStrategyChange: (ContextStrategy?) -> Unit,
    onWindowSizeChange: (Int?) -> Unit,
    onClose: () -> Unit,
    branches: List<BranchInfo>,
    parentBranch: BranchInfo?,
    onCreateBranch: () -> Unit,
    onNavigateToBranch: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Контекст сессии",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onClose) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }

            HorizontalDivider()

            // Переключатель: использовать per-session или глобальные настройки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Свои настройки", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (currentStrategy != null) "Настройки этой сессии"
                        else "Используются глобальные",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = currentStrategy != null,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onStrategyChange(ContextStrategy.SUMMARY)
                            onWindowSizeChange(AISettings.DEFAULT_CONTEXT_WINDOW_SIZE)
                        } else {
                            onStrategyChange(null)
                            onWindowSizeChange(null)
                        }
                    },
                )
            }

            if (currentStrategy != null) {
                HorizontalDivider()

                Text("Стратегия", style = MaterialTheme.typography.titleSmall)

                ContextStrategy.entries.forEach { strategy ->
                    val isSelected = currentStrategy == strategy
                    val isAvailable = true

                    OutlinedCard(
                        onClick = {
                            if (isAvailable) onStrategyChange(strategy)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isAvailable,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strategy.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (!isAvailable) {
                                    Text(
                                        text = "Скоро",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                            Text(
                                text = strategy.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Размер окна контекста
                val windowSize = currentWindowSize ?: AISettings.DEFAULT_CONTEXT_WINDOW_SIZE
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Окно (N сообщений)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "$windowSize",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Последние N сообщений хранятся полностью",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = windowSize.toFloat(),
                        onValueChange = { onWindowSizeChange(it.toInt()) },
                        valueRange = AISettings.MIN_CONTEXT_WINDOW_SIZE.toFloat()..AISettings.MAX_CONTEXT_WINDOW_SIZE.toFloat(),
                        steps = AISettings.MAX_CONTEXT_WINDOW_SIZE - AISettings.MIN_CONTEXT_WINDOW_SIZE - 1,
                    )
                }
            }

            HorizontalDivider()

            Text("Ветвление", style = MaterialTheme.typography.titleSmall)

            if (parentBranch != null) {
                OutlinedCard(
                    onClick = { onNavigateToBranch(parentBranch.sessionId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Родительский чат",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = parentBranch.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                onClick = onCreateBranch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Создать ветку")
            }

            if (branches.isNotEmpty()) {
                Text(
                    text = "Ветки (${branches.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                branches.forEach { branch ->
                    OutlinedCard(
                        onClick = { onNavigateToBranch(branch.sessionId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = branch.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 4.dp,
                    )
                )
                .background(MaterialTheme.colorScheme.primary)
                .padding(12.dp),
        ) {
            val selectionColors = TextSelectionColors(
                handleColor = MaterialTheme.colorScheme.onPrimary,
                backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )

            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                SelectionContainer {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentBubble(text: String, tokenUsage: TokenUsageInfo? = null) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = 4.dp, bottomEnd = 16.dp,
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                val selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                )

                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    SelectionContainer {
                        val markdownState = rememberMarkdownState(
                            content = text,
                            immediate = true,
                        )
                        Markdown(
                            markdownState = markdownState,
                            colors = markdownColor(
                                text = textColor,
                                codeBackground = MaterialTheme.colorScheme.surfaceVariant
                                    .copy(alpha = 0.5f),
                                inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
                                    .copy(alpha = 0.5f),
                                dividerColor = textColor.copy(alpha = 0.3f),
                            ),
                            typography = markdownTypography(
                                paragraph = MaterialTheme.typography.bodyLarge,
                            ),
                        )
                    }
                }
            }
            if (tokenUsage != null && tokenUsage.totalTokens > 0) {
                Text(
                    text = "Tokens: ${tokenUsage.totalTokens} " +
                        "(in: ${tokenUsage.inputTokens}, out: ${tokenUsage.outputTokens})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SystemBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 4.dp, bottomEnd = 16.dp,
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        ) {
            val selectionColors = TextSelectionColors(
                handleColor = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            )

            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                SelectionContainer {
                    val markdownState = rememberMarkdownState(
                        content = "$text▌",
                        immediate = true,
                    )
                    Markdown(
                        markdownState = markdownState,
                        colors = markdownColor(
                            text = textColor,
                            codeBackground = MaterialTheme.colorScheme.surfaceVariant
                                .copy(alpha = 0.5f),
                            inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
                                .copy(alpha = 0.5f),
                            dividerColor = textColor.copy(alpha = 0.3f),
                        ),
                        typography = markdownTypography(
                            paragraph = MaterialTheme.typography.bodyLarge,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Панель ввода сообщения с тремя состояниями кнопки:
 * - Генерация (isLoading=true) → кнопка "⏹" для остановки
 * - Готов к отправке (isEnabled, текст непустой) → кнопка "➤"
 * - Заблокирован → кнопка "➤" disabled
 */
@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isEnabled: Boolean,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Введите сообщение...") },
            enabled = isEnabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (text.isNotBlank() && isEnabled) onSend() }
            ),
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
        )

        if (isLoading) {
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            ) {
                Text(
                    "⏹",
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                enabled = isEnabled && text.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled && text.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Text(
                    "➤",
                    color = if (isEnabled && text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
