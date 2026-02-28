package com.devchik.ai.di

import com.devchik.ai.feature.comparison.data.ComparisonRepositoryImpl
import com.devchik.ai.feature.comparison.domain.ComparisonRepository
import com.devchik.ai.feature.comparison.presentation.ComparisonViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val featureComparisonModule = module {
    single { ComparisonRepositoryImpl(get()) } bind ComparisonRepository::class

    viewModel { ComparisonViewModel(get()) }
}
