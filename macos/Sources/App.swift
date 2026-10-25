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

    var body: some Scene {
        WindowGroup("ArtikelMeister — Almanca Artikel & Kelime Çalışması") {
            RootContainerView()
                .environmentObject(store)
                .environmentObject(authManager)
        }
        .defaultSize(width: 1040, height: 660)
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Yerel Depolama Klasörünü Aç") {
                    store.openStorageDirectoryInFinder()
                }
                .keyboardShortcut("o", modifiers: [.command, .shift])
            }
        }
    }
}
