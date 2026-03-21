package com.devchik.ai.di.feature

import com.devchik.ai.BuildKonfig
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
import com.devchik.ai.feature.chat.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureChatModule = module {
    single { ChatAgentProvider(apiKey = BuildKonfig.DEEPSEEK_API_KEY) }

    viewModel { ChatViewModel(get()) }
}
