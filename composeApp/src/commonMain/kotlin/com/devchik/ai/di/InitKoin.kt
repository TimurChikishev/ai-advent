package com.devchik.ai.di

import com.devchik.ai.di.core.coreNetworkModule
import com.devchik.ai.di.core.coreStoragePlatformModule
import com.devchik.ai.di.feature.featureAiModule
import com.devchik.ai.di.feature.featureChatModule
import com.devchik.ai.di.feature.featureComparisonModule
import com.devchik.ai.di.feature.featureSettingsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration



fun initKoin(
    appDeclaration: KoinAppDeclaration = {}
) {
    startKoin {
        appDeclaration()

        modules(
            coreNetworkModule,
            coreStoragePlatformModule,
            featureSettingsModule,
            featureAiModule,
            featureChatModule,
            featureComparisonModule,
        )
    }
}

fun initKoinIos() = initKoin {}