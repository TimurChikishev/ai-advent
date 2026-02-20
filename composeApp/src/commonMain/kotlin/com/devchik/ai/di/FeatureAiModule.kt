package com.devchik.ai.di

import com.devchik.ai.feature.ai.data.AIRepositoryImpl
import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.presentation.AIViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val featureAiModule = module {
    single { AIRepositoryImpl(get()) } bind AIRepository::class

    viewModel { AIViewModel(get()) }
}