package com.devchik.ai.feature.comparison.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
fun ComparisonDetailScreen(
    modelIndex: Int,
    onBack: () -> Unit,
    viewModel: ComparisonViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.results.getOrNull(modelIndex) ?: return

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

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tierLabel,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = result.config.displayName,
                            color = tierColor,
                            fontSize = 12.sp,
                        )
                    }
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
                .verticalScroll(rememberScrollState()),
        ) {
            MetaInfoRow(result = result, tierColor = tierColor)

            Spacer(Modifier.height(12.dp))

            ResponseContent(result = result)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MetaInfoRow(result: ModelResult, tierColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetaChip(
            label = "Провайдер",
            value = result.config.providerName,
            color = tierColor,
        )
        MetaChip(
            label = "Параметры",
            value = result.config.parameters,
            color = tierColor,
        )
        if (result.tokensUsed > 0) {
            MetaChip(
                label = "Токены",
                value = "${result.tokensUsed}",
                color = tierColor,
            )
        }
        if (result.durationMs > 0) {
            MetaChip(
                label = "Время",
                value = "${result.durationMs}ms",
                color = tierColor,
            )
        }
        if (result.wordCount > 0) {
            MetaChip(
                label = "Слов",
                value = "${result.wordCount}",
                color = tierColor,
            )
        }
    }
}

@Composable
private fun MetaChip(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun ResponseContent(result: ModelResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBgColor)
            .padding(16.dp),
    ) {
        HorizontalDivider(color = CardBorderColor)
        Spacer(Modifier.height(12.dp))

        when {
            result.error != null -> {
                Text(
                    text = result.error,
                    color = Color(0xFFFF7B7B),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
            result.isStreaming -> {
                Text(
                    text = "${result.streamingContent}▌",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
            result.content.isNotEmpty() -> {
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
                        paragraph = MaterialTheme.typography.bodyMedium,
                    ),
                )
            }
            else -> {
                Text(
                    text = "Ответ ещё не получен",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
