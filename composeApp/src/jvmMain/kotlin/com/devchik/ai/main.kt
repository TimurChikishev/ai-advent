package com.devchik.ai

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.devchik.ai.app.App
import com.devchik.ai.di.initKoin

fun main() {
    initKoin {}
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ai",
        ) {
            App()
        }
    }
}