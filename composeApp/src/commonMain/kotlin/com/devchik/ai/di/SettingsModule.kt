package com.devchik.ai.di

import com.devchik.ai.core.datastore.createDataStore
import com.devchik.ai.feature.settings.data.SettingsRepositoryImpl
import com.devchik.ai.feature.settings.domain.SettingsRepository
import com.devchik.ai.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsModule = module {
    single { createDataStore() }
    single { SettingsRepositoryImpl(get()) } bind SettingsRepository::class
    viewModel { SettingsViewModel(get()) }
}
