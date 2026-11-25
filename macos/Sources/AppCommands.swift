import SwiftUI

private struct AppTabSelectionKey: FocusedValueKey {
    typealias Value = Binding<AppTab>
}

private struct NewWordActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

private struct FindWordsActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

private struct ImportCSVActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

private struct AIGeneratorActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

private struct SpeakWordActionKey: FocusedValueKey {
    typealias Value = () -> Void
}

extension FocusedValues {
    var appTabSelection: Binding<AppTab>? {
        get { self[AppTabSelectionKey.self] }
        set { self[AppTabSelectionKey.self] = newValue }
    }

    var newWordAction: (() -> Void)? {
        get { self[NewWordActionKey.self] }
        set { self[NewWordActionKey.self] = newValue }
    }

    var findWordsAction: (() -> Void)? {
        get { self[FindWordsActionKey.self] }
        set { self[FindWordsActionKey.self] = newValue }
    }

    var importCSVAction: (() -> Void)? {
        get { self[ImportCSVActionKey.self] }
        set { self[ImportCSVActionKey.self] = newValue }
    }

    var aiGeneratorAction: (() -> Void)? {
        get { self[AIGeneratorActionKey.self] }
        set { self[AIGeneratorActionKey.self] = newValue }
    }

    var speakWordAction: (() -> Void)? {
        get { self[SpeakWordActionKey.self] }
        set { self[SpeakWordActionKey.self] = newValue }
    }
}

struct AppCommands: Commands {
    let openStorageDirectory: () -> Void

    @FocusedValue(\.appTabSelection) private var tabSelection
    @FocusedValue(\.newWordAction) private var newWord
    @FocusedValue(\.findWordsAction) private var findWords
    @FocusedValue(\.importCSVAction) private var importCSV
    @FocusedValue(\.aiGeneratorAction) private var aiGenerator
    @FocusedValue(\.speakWordAction) private var speakWord

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            actionButton("Yeni Kelime", action: newWord)
                .keyboardShortcut("n")
            actionButton("CSV İçe Aktar…", action: importCSV)
                .keyboardShortcut("i", modifiers: [.command, .shift])
            actionButton("AI ile Kelime Üret…", action: aiGenerator)
                .keyboardShortcut("g", modifiers: [.command, .shift])
            Divider()
            Button("Yerel Depolama Klasörünü Aç", action: openStorageDirectory)
                .keyboardShortcut("o", modifiers: [.command, .shift])
        }

        CommandGroup(after: .textEditing) {
            actionButton("Bul", action: findWords)
                .keyboardShortcut("f")
            actionButton("Kelimeyi Seslendir", action: speakWord)
                .keyboardShortcut("s", modifiers: [.command, .shift])
        }

        CommandMenu("Görünüm") {
            tabButton(.study)
                .keyboardShortcut("c", modifiers: [.command, .option])
            tabButton(.words)
                .keyboardShortcut("k", modifiers: [.command, .option])
            tabButton(.stats)
                .keyboardShortcut("r", modifiers: [.command, .option])
        }

        CommandGroup(replacing: .appSettings) {
            tabButton(.settings)
                .keyboardShortcut(",")
        }
    }

    private func tabButton(_ tab: AppTab) -> some View {
        Button(tab.rawValue) {
            tabSelection?.wrappedValue = tab
        }
        .disabled(tabSelection == nil)
    }

    private func actionButton(_ title: LocalizedStringKey, action: (() -> Void)?) -> some View {
        Button(title) {
            action?()
        }
        .disabled(action == nil)
    }
}
