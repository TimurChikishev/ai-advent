package com.devchik.ai.di

import com.devchik.ai.core.network.createHttpClient
import org.koin.dsl.module

val networkModule = module {
    single { createHttpClient() }
}
