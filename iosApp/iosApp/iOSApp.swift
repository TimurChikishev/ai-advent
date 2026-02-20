import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        InitKoinKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}