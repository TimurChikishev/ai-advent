package com.devchik.ai.di.feature

import com.devchik.ai.BuildKonfig
import com.devchik.ai.core.database.AppDatabase
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider
import com.devchik.ai.feature.chat.data.repository.ChatRepositoryImpl
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import com.devchik.ai.feature.chat.domain.usecase.CreateSessionUseCase
import com.devchik.ai.feature.chat.domain.usecase.DeleteSessionUseCase
import com.devchik.ai.feature.chat.domain.usecase.GetSessionsUseCase
import com.devchik.ai.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.devchik.ai.feature.chat.domain.usecase.SendMessageUseCase
import com.devchik.ai.feature.chat.presentation.ChatViewModel
import com.devchik.ai.feature.chat.presentation.SessionListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val featureChatModule = module {
    single { get<AppDatabase>().chatSessionDao() }
    single { get<AppDatabase>().chatMessageDao() }
    single { RoomChatHistoryProvider(chatMessageDao = get(), chatSessionDao = get()) }
    single {
        ChatAgentProvider(
            apiKey = BuildKonfig.DEEPSEEK_API_KEY,
            chatHistoryProvider = get<RoomChatHistoryProvider>(),
        )
    }

    single {
        ChatRepositoryImpl(
            chatSessionDao = get(),
            chatMessageDao = get(),
            chatHistoryProvider = get(),
        )
    } bind ChatRepository::class

    factory { GetSessionsUseCase(repository = get()) }
    factory { CreateSessionUseCase(repository = get()) }
    factory { DeleteSessionUseCase(repository = get()) }
    factory { LoadChatHistoryUseCase(repository = get()) }
    factory { SendMessageUseCase(repository = get()) }

    viewModel { params ->
        ChatViewModel(
            chatAgentProvider = get(),
            loadChatHistoryUseCase = get(),
            sendMessageUseCase = get(),
            sessionId = params.get<String>(),
        )
    }
    viewModel {
        SessionListViewModel(
            getSessionsUseCase = get(),
            createSessionUseCase = get(),
            deleteSessionUseCase = get(),
        )
    }
}
