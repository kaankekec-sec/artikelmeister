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
            actionButton(L10n.text("Yeni Kelime", en: "New Word", de: "Neues Wort"), action: newWord)
                .keyboardShortcut("n")
            actionButton(L10n.csvImportEllipsis, action: importCSV)
                .keyboardShortcut("i", modifiers: [.command, .shift])
            actionButton(L10n.text("AI ile Kelime Üret…", en: "Generate Words with AI…", de: "Wörter mit KI generieren…"), action: aiGenerator)
                .keyboardShortcut("g", modifiers: [.command, .shift])
            Divider()
            Button(L10n.text("Yerel Depolama Klasörünü Aç", en: "Open Local Storage Folder", de: "Lokalen Speicherordner öffnen"), action: openStorageDirectory)
                .keyboardShortcut("o", modifiers: [.command, .shift])
        }

        CommandGroup(after: .textEditing) {
            actionButton(L10n.text("Bul", en: "Find", de: "Suchen"), action: findWords)
                .keyboardShortcut("f")
            actionButton(L10n.text("Kelimeyi Seslendir", en: "Speak Word", de: "Wort vorlesen"), action: speakWord)
                .keyboardShortcut("s", modifiers: [.command, .shift])
        }

        CommandMenu(L10n.text("Görünüm", en: "View", de: "Ansicht")) {
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
        Button(tab.localizedTitle) {
            tabSelection?.wrappedValue = tab
        }
        .disabled(tabSelection == nil)
    }

    private func actionButton(_ title: String, action: (() -> Void)?) -> some View {
        Button(title) {
            action?()
        }
        .disabled(action == nil)
    }
}
