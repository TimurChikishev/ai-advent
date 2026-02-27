package com.devchik.ai.feature.ai.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devchik.ai.feature.ai.domain.model.AIMessage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: AIViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
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
                .imePadding(),
        ) {
            val isStreaming = uiState.streamingContent.isNotEmpty()
            val displayMessages = if (isStreaming) {
                uiState.messages + AIMessage(
                    role = AIMessage.Role.Assistant,
                    content = uiState.streamingContent,
                )
            } else {
                uiState.messages
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "top_spacer") { Spacer(Modifier.height(8.dp)) }

                itemsIndexed(
                    items = displayMessages,
                    key = { index, _ -> "msg_$index" },
                ) { index, message ->
                    val isLastAndStreaming = isStreaming && index == displayMessages.lastIndex
                    MessageBubble(
                        text = message.content,
                        isUser = message.role == AIMessage.Role.User,
                        isStreaming = isLastAndStreaming,
                    )
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
            }

            ChatInput(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                isLoading = uiState.isLoading,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    isStreaming: Boolean,
) {
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.75f else 0.9f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp),
        ) {
            val selectionColors = if (isUser) {
                TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                )
            } else {
                TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                )
            }

            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                SelectionContainer {
                    if (isUser || isStreaming) {
                        Text(
                            text = if (isStreaming) "$text▌" else text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
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
        }
    }
}

@Composable
private fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (text.isNotBlank() && !isLoading) onSend() }
            ),
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
        )

        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isLoading,
        ) {
            Text("➤", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
