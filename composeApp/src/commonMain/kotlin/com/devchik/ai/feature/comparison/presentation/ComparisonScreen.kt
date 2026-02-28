package com.devchik.ai.feature.comparison.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devchik.ai.feature.comparison.domain.model.ModelResult
import com.devchik.ai.feature.comparison.domain.model.ModelTier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit = {},
    viewModel: ComparisonViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Model Comparison",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = TextPrimary, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            PromptInputSection(
                prompt = uiState.prompt,
                onPromptChange = viewModel::setPrompt,
                onRun = viewModel::runComparison,
                isRunning = uiState.isRunning,
            )

            Spacer(Modifier.height(16.dp))

            ModelCardsRow(results = uiState.results, onOpenDetail = onOpenDetail)

            if (uiState.hasResults || uiState.isRunning) {
                Spacer(Modifier.height(16.dp))
                ComparisonMetricsSection(uiState = uiState)
            }

            Spacer(Modifier.height(16.dp))

            ActionButtonsRow(
                canAnalyze = uiState.hasResults && !uiState.isAnalyzing && !uiState.isRunning,
                isAnalyzing = uiState.isAnalyzing,
                onAnalyze = viewModel::analyze,
            )

            if (uiState.displayAnalysis.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                AnalysisSection(analysis = uiState.displayAnalysis, isStreaming = uiState.isAnalyzing)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PromptInputSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Введите запрос для сравнения моделей...", color = TextSecondary)
            },
            enabled = !isRunning,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (prompt.isNotBlank() && !isRunning) onRun() }
            ),
            singleLine = false,
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = InputBg,
                unfocusedContainerColor = InputBg,
                disabledContainerColor = InputBg,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextSecondary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = AccentYellow,
            ),
            shape = RoundedCornerShape(8.dp),
        )

        Button(
            onClick = onRun,
            enabled = prompt.isNotBlank() && !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF238636),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF238636).copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(18.dp)
                        .height(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Выполняется...",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            } else {
                Text(
                    text = "▶  Запустить сравнение",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ModelCardsRow(results: List<ModelResult>, onOpenDetail: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        results.forEachIndexed { index, result ->
            ModelCard(
                result = result,
                onClick = { onOpenDetail(index) },
                modifier = Modifier.width(280.dp),
            )
        }
    }
}

@Composable
private fun ModelCard(
    result: ModelResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tierColor = when (result.config.tier) {
        ModelTier.Weak -> WeakColor
        ModelTier.Medium -> MediumColor
        ModelTier.Strong -> StrongColor
    }
    val tierLabel = when (result.config.tier) {
        ModelTier.Weak -> "Weak Model"
        ModelTier.Medium -> "Medium Model"
        ModelTier.Strong -> "Strong Model"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBgColor)
            .clickable(enabled = result.content.isNotEmpty() || result.error != null, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tierLabel,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            if (result.tokensUsed > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tierColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${result.tokensUsed} tokens",
                        color = tierColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = result.config.displayName + " ↗",
            color = tierColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "${result.config.parameters} · ${result.config.providerName}",
            color = TextSecondary,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = CardBorderColor)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                result.isLoading && result.displayContent.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = tierColor,
                        strokeWidth = 2.dp,
                    )
                }
                result.error != null -> {
                    Text(
                        text = "Ошибка: ${result.error}",
                        color = Color(0xFFFF7B7B),
                        fontSize = 12.sp,
                    )
                }
                result.displayContent.isNotEmpty() -> {
                    if (result.isStreaming) {
                        Text(
                            text = "${result.streamingContent}▌",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    } else {
                        val markdownState = rememberMarkdownState(
                            content = result.content,
                            immediate = true,
                        )
                        Markdown(
                            markdownState = markdownState,
                            colors = markdownColor(
                                text = TextPrimary,
                                codeBackground = InputBg,
                                inlineCodeBackground = InputBg,
                                dividerColor = CardBorderColor,
                            ),
                            typography = markdownTypography(
                                paragraph = MaterialTheme.typography.bodySmall,
                            ),
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Ожидание запроса...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = CardBorderColor)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (result.durationMs > 0) "${result.durationMs}ms" else "–",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(text = "·", color = TextSecondary, fontSize = 11.sp)
                Text(
                    text = if (result.wordCount > 0) "${result.wordCount} words" else "–",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            if (result.content.isNotEmpty()) {
                Text(
                    text = "Читать →",
                    color = tierColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ComparisonMetricsSection(uiState: ComparisonUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBgColor)
            .padding(16.dp),
    ) {
        Text(
            text = "Comparison Metrics",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MetricItem(
                label = "Duration",
                value = "${uiState.totalDurationMs}ms",
                color = Color(0xFF58A6FF),
            )
            MetricItem(
                label = "Tokens",
                value = "${uiState.totalTokens}",
                color = Color(0xFFBC8CFF),
            )
            MetricItem(
                label = "Cost",
                value = "\$0.0000",
                color = AccentYellow,
            )
        }

        Spacer(Modifier.height(12.dp))

        DurationBars(results = uiState.results)
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun DurationBars(results: List<ModelResult>) {
    val maxDuration = results.maxOfOrNull { it.durationMs }?.takeIf { it > 0 } ?: 1L

    results.forEach { result ->
        val tierColor = when (result.config.tier) {
            ModelTier.Weak -> WeakColor
            ModelTier.Medium -> MediumColor
            ModelTier.Strong -> StrongColor
        }
        val tierLabel = when (result.config.tier) {
            ModelTier.Weak -> "Weak"
            ModelTier.Medium -> "Medium"
            ModelTier.Strong -> "Strong"
        }
        val fraction = (result.durationMs.toFloat() / maxDuration).coerceIn(0f, 1f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = tierLabel,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(48.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBorderColor),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tierColor),
                    )
                }
            }
            Text(
                text = if (result.durationMs > 0) "${result.durationMs}ms" else "–",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(60.dp),
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    canAnalyze: Boolean,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onAnalyze,
            enabled = canAnalyze,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentYellow,
                contentColor = Color(0xFF0D1117),
                disabledContainerColor = AccentYellow.copy(alpha = 0.4f),
                disabledContentColor = Color(0xFF0D1117).copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                    color = Color(0xFF0D1117),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isAnalyzing) "Анализирую..." else "Analyze (Ctrl+A)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary,
            ),
        ) {
            Text(
                text = "Export (Ctrl+E)",
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun AnalysisSection(analysis: String, isStreaming: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBgColor)
            .padding(16.dp),
    ) {
        Text(
            text = "Comparison Analysis",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(12.dp))

        if (isStreaming) {
            Text(
                text = "$analysis▌",
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        } else {
            val markdownState = rememberMarkdownState(content = analysis, immediate = true)
            Markdown(
                markdownState = markdownState,
                colors = markdownColor(
                    text = TextPrimary,
                    codeBackground = InputBg,
                    inlineCodeBackground = InputBg,
                    dividerColor = CardBorderColor,
                ),
                typography = markdownTypography(
                    paragraph = MaterialTheme.typography.bodyMedium,
                ),
            )
        }
    }
}
