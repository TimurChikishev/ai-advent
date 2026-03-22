package com.devchik.ai.di.feature

import com.devchik.ai.BuildKonfig
import com.devchik.ai.core.database.AppDatabase
import com.devchik.ai.feature.chat.agent.ChatAgentProvider
import com.devchik.ai.feature.chat.data.ContextManager
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider
import com.devchik.ai.feature.chat.data.repository.ChatRepositoryImpl
import com.devchik.ai.feature.chat.data.strategy.BranchingStrategy
import com.devchik.ai.feature.chat.data.strategy.SlidingWindowStrategy
import com.devchik.ai.feature.chat.data.strategy.StickyFactsStrategy
import com.devchik.ai.feature.chat.data.strategy.SummaryStrategy
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

/**
 * Koin DI модуль для фичи чата. Связывает все слои:
 *
 * Data layer:
 * - Room DAOs (singleton, из AppDatabase)
 * - Стратегии контекста: [SlidingWindowStrategy], [SummaryStrategy] (singleton)
 * - [ContextManager] (singleton) — диспетчер стратегий, читает настройки из SettingsRepository
 * - [RoomChatHistoryProvider] (singleton) — мост между Koog ChatMemory и Room
 * - [ChatRepositoryImpl] bound as [ChatRepository]
 *
 * Agent layer:
 * - [ChatAgentProvider] (singleton) — создаёт Koog AIAgent по запросу
 *
 * Domain layer:
 * - UseCases (factory — новый экземпляр при каждой инъекции, stateless)
 *
 * Presentation layer:
 * - [ChatViewModel] — параметризован sessionId через Koin params
 * - [SessionListViewModel] — один на экран списка сессий
 */
val featureChatModule = module {
    // --- Data layer ---

    // Room DAOs — singleton, извлекаются из AppDatabase
    single { get<AppDatabase>().chatSessionDao() }
    single { get<AppDatabase>().chatMessageDao() }
    single { get<AppDatabase>().chatSummaryDao() }
    single { get<AppDatabase>().stickyFactDao() }

    // Стратегии управления контекстом — каждая singleton
    single { SlidingWindowStrategy(chatMessageDao = get()) }
    single {
        SummaryStrategy(
            chatMessageDao = get(),
            chatSummaryDao = get(),
            httpClient = get(),
        )
    }
    // StickyFactsStrategy: извлекает ключевые факты из диалога через LLM
    single {
        StickyFactsStrategy(
            chatMessageDao = get(),
            stickyFactDao = get(),
            httpClient = get(),
        )
    }
    // BranchingStrategy: ветвление диалога, контекст работает как sliding window внутри ветки
    single {
        BranchingStrategy(
            chatMessageDao = get(),
            chatSessionDao = get(),
        )
    }

    // ContextManager — диспетчер стратегий.
    // Приоритет: per-session настройки (ChatSessionEntity) > глобальные (SettingsRepository).
    single {
        ContextManager(
            settingsRepository = get(),
            chatSessionDao = get(),
            slidingWindowStrategy = get(),
            summaryStrategy = get(),
            stickyFactsStrategy = get(),
            branchingStrategy = get(),
        )
    }

    // RoomChatHistoryProvider — адаптер для Koog ChatMemory.
    // load() делегирует в ContextManager для оптимизированного контекста.
    single {
        RoomChatHistoryProvider(
            chatMessageDao = get(),
            chatSessionDao = get(),
            contextManager = get(),
        )
    }

    // ChatAgentProvider — фабрика Koog AIAgent с DeepSeek API
    single {
        ChatAgentProvider(
            apiKey = BuildKonfig.DEEPSEEK_API_KEY,
            chatHistoryProvider = get<RoomChatHistoryProvider>(),
        )
    }

    // ChatRepository — мост между domain и data layer
    single {
        ChatRepositoryImpl(
            chatSessionDao = get(),
            chatMessageDao = get(),
            chatHistoryProvider = get(),
            contextManager = get(),
        )
    } bind ChatRepository::class

    // --- Domain layer (stateless, factory scope) ---
    factory { GetSessionsUseCase(repository = get()) }
    factory { CreateSessionUseCase(repository = get()) }
    factory { DeleteSessionUseCase(repository = get()) }
    factory { LoadChatHistoryUseCase(repository = get()) }
    factory { SendMessageUseCase(repository = get()) }

    // --- Presentation layer ---
    // ChatViewModel — получает sessionId через Koin parametersOf(sessionId)
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
