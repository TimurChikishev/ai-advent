package com.devchik.ai.di.feature

import com.devchik.ai.feature.settings.data.SettingsRepositoryImpl
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val featureSettingsModule = module {
    single { SettingsRepositoryImpl(get()) } bind SettingsRepository::class
    viewModel { SettingsViewModel(get()) }
}
