package com.devchik.ai.core.app

import android.app.Application
import com.devchik.ai.di.initKoin
import org.koin.android.ext.koin.androidContext

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@App)
        }
    }
}