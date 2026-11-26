import SwiftUI
import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

@main
struct ArtikelMeisterApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var store = DeckStore()
    @StateObject private var authManager = GeminiAuthManager.shared
    @StateObject private var languageManager = LanguageManager.shared

    var body: some Scene {
        WindowGroup("ArtikelMeister") {
            RootContainerView()
                .environmentObject(store)
                .environmentObject(authManager)
                .environmentObject(languageManager)
        }
        .defaultSize(width: 1040, height: 660)
        .windowStyle(.titleBar)
        .commands {
            AppCommands {
                store.openStorageDirectoryInFinder()
            }
        }
    }
}
