import SwiftUI
import AppKit
import Accessibility

struct AIGeneratorSheet: View {
    @EnvironmentObject var store: DeckStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var selectedLevel: CEFRLevel = .a1
    @State private var wordCount = 20
    @State private var topicHint = ""
    @State private var includeArticleless = false
    @State private var targetDeckName = "A1 Seviyesi"
    @State private var hasCustomDeckName = false
    @State private var showTargetDeckMenu = false

    @State private var isGenerating = false
    @State private var progressMessage = ""
    @State private var generationError: String?
    @State private var didFinishGenerating = false
    @State private var newlyAddedWords: [GeneratedWordItem] = []

    private enum ScrollTarget: Hashable {
        case generationError, results
    }

    private var automaticDeckName: String {
        L10n.text("\(selectedLevel.rawValue) Seviyesi", en: "\(selectedLevel.rawValue) Level", de: "Niveau \(selectedLevel.rawValue)")
    }

    private var trimmedDeckName: String {
        targetDeckName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var resultSummary: String {
        newlyAddedWords.isEmpty
            ? L10n.text("Yeni kelime eklenmedi", en: "No new words added", de: "Keine neuen Wörter hinzugefügt")
            : L10n.text("\(newlyAddedWords.count) kelime eklendi", en: "\(newlyAddedWords.count) words added", de: "\(newlyAddedWords.count) Wörter hinzugefügt")
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            scrollableForm
            Divider()
            actionBar
        }
        .frame(minWidth: 640, idealWidth: 720, minHeight: 520, idealHeight: 640)
        .interactiveDismissDisabled(isGenerating)
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image(systemName: "sparkles")
                .font(.title2)
                .foregroundStyle(AppPalette.accentPurple)
                .accessibilityHidden(true)
            Text(L10n.aiSheetTitle())
                .font(.title3.bold())
                .accessibilityAddTraits(.isHeader)
            Spacer()

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .disabled(isGenerating)
            .accessibilityLabel(L10n.close)
            .help(L10n.close)
        }
        .padding(20)
        .background(.ultraThinMaterial)
    }

    private var scrollableForm: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    generationParametersSection
                        .disabled(isGenerating)

                    if let generationError {
                        errorNotice(generationError)
                            .id(ScrollTarget.generationError)
                    }

                    if !newlyAddedWords.isEmpty {
                        generatedResultsSection
                            .id(ScrollTarget.results)
                    }
                }
                .padding(20)
            }
            .onChange(of: isGenerating) {
                if isGenerating {
                    AccessibilityNotification.Announcement(L10n.text("Kelime üretimi başlatıldı...", en: "Word generation started...", de: "Wortgenerierung gestartet...")).post()
                } else if let generationError {
                    withAnimation(reduceMotion ? nil : .default) {
                        proxy.scrollTo(ScrollTarget.generationError, anchor: .top)
                    }
                    AccessibilityNotification.Announcement(generationError).post()
                } else if didFinishGenerating {
                    if !newlyAddedWords.isEmpty {
                        withAnimation(reduceMotion ? nil : .default) {
                            proxy.scrollTo(ScrollTarget.results, anchor: .top)
                        }
                    }
                    AccessibilityNotification.Announcement(resultSummary).post()
                }
            }
        }
    }

    private var actionBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            if isGenerating {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                        .accessibilityHidden(true)
                    Text(progressMessage)
                        .foregroundStyle(.secondary)
                }
                .font(.caption)
            } else if generationError != nil {
                Label(L10n.text("Kelime üretilirken bir hata oluştu.", en: "An error occurred while generating words.", de: "Beim Generieren der Wörter ist ein Fehler aufgetreten."), systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(AppPalette.error)
            } else if didFinishGenerating {
                Text(resultSummary)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(newlyAddedWords.isEmpty ? Color.secondary : AppPalette.success)
            }

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.text(
                        "\(selectedLevel.rawValue) • \(wordCount) kelime • \(trimmedDeckName.isEmpty ? automaticDeckName : trimmedDeckName)",
                        en: "\(selectedLevel.rawValue) • \(wordCount) words • \(trimmedDeckName.isEmpty ? automaticDeckName : trimmedDeckName)",
                        de: "\(selectedLevel.rawValue) • \(wordCount) Wörter • \(trimmedDeckName.isEmpty ? automaticDeckName : trimmedDeckName)"
                    ))
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(L10n.text(
                        "Mevcut \(store.cards.count) kelime hariç tutulacak",
                        en: "Existing \(store.cards.count) words will be excluded",
                        de: "Vorhandene \(store.cards.count) Wörter werden ausgeschlossen"
                    ))
                        .foregroundStyle(.secondary)
                }
                .font(.caption)
                Spacer(minLength: 0)

                Button(L10n.close) {
                    dismiss()
                }
                .buttonStyle(.bordered)
                .keyboardShortcut(.cancelAction)
                .disabled(isGenerating)

                Button {
                    Task {
                        await generateAndAddWords()
                    }
                } label: {
                    Label(L10n.text("Üret ve ekle", en: "Generate & Add", de: "Generieren & hinzufügen"), systemImage: "sparkles")
                }
                .buttonStyle(FilledActionStyle(fill: AppPalette.accentPurple, ink: AppPalette.onPurple))
                .keyboardShortcut(.return, modifiers: .command)
                .disabled(isGenerating)
            }
        }
        .padding(16)
        .background(.ultraThinMaterial)
    }

    private func errorNotice(_ message: String) -> some View {
        Label {
            Text(message)
                .font(.caption)
                .foregroundStyle(.primary)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(AppPalette.error)
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppPalette.errorBadgeBackground, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Generation Parameters

    private var generationParametersSection: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 16) {
                levelSelector
                Divider()
                countSelector
                Divider()
                topicAndDeckFields
            }
            .padding(10)
        } label: {
            Label(L10n.text("Seviye (A1 – C1) ve Kelime Sayısı (1 – 100)", en: "Level (A1 – C1) & Word Count (1 – 100)", de: "Niveau (A1 – C1) & Wortanzahl (1 – 100)"), systemImage: "graduationcap.fill")
        }
    }

    private var levelSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.text("Almanca Dil Seviyesi (CEFR):", en: "German Language Level (CEFR):", de: "Deutsches Sprachniveau (GER):"))
                .font(.subheadline.weight(.semibold))

            HStack(spacing: 10) {
                ForEach(CEFRLevel.allCases) { level in
                    let isSelected = selectedLevel == level
                    let shape = RoundedRectangle(cornerRadius: 10)

                    Button {
                        selectedLevel = level
                        if !hasCustomDeckName {
                            targetDeckName = automaticDeckName
                        }
                    } label: {
                        Text(level.rawValue)
                            .font(.title3.bold())
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                isSelected ? AppPalette.purpleBadgeBackground : Color(nsColor: .controlBackgroundColor),
                                in: shape
                            )
                            .overlay {
                                shape.strokeBorder(
                                    isSelected ? AppPalette.accentPurple : AppPalette.controlOutline,
                                    lineWidth: isSelected ? 2 : 1
                                )
                            }
                            .contentShape(shape)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(L10n.text("Almanca Dil Seviyesi", en: "German Language Level", de: "Deutsches Sprachniveau"))

            Text(selectedLevel.description)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var countSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(L10n.text("Üretilecek Kelime Sayısı (1 – 100):", en: "Number of Words to Generate (1 – 100):", de: "Anzahl der Wörter (1 – 100):"))
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Stepper(value: $wordCount, in: 1...100, step: 1) {
                    Text(L10n.text("\(wordCount) kelime", en: "\(wordCount) words", de: "\(wordCount) Wörter"))
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(AppPalette.accentPurple)
                }
            }

            HStack(spacing: 12) {
                Text("1").font(.caption).foregroundStyle(.secondary)
                Slider(
                    value: Binding(
                        get: { Double(wordCount) },
                        set: { wordCount = max(1, min(100, Int(round($0)))) }
                    ),
                    in: 1...100,
                    step: 1
                )
                .tint(AppPalette.accentPurple)
                .accessibilityLabel(L10n.text("Kelime sayısı", en: "Word count", de: "Wortanzahl"))
                .accessibilityValue(L10n.text("\(wordCount) kelime", en: "\(wordCount) words", de: "\(wordCount) Wörter"))
                Text("100").font(.caption).foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                Text(L10n.text("Hızlı Seçim:", en: "Quick Select:", de: "Schnellauswahl:"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ForEach([5, 10, 20, 30, 50, 75, 100], id: \.self) { preset in
                    Button {
                        wordCount = preset
                    } label: {
                        Text("\(preset)")
                            .fontWeight(wordCount == preset ? .bold : .regular)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .tint(wordCount == preset ? AppPalette.accentPurple : nil)
                    .accessibilityLabel(L10n.text("\(preset) kelime", en: "\(preset) words", de: "\(preset) Wörter"))
                    .accessibilityAddTraits(wordCount == preset ? .isSelected : [])
                }
            }

            Toggle(isOn: $includeArticleless) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.text("Artikelsiz kelimeleri de üret", en: "Also generate article-less words", de: "Auch artikellose Wörter generieren"))
                        .font(.subheadline.weight(.semibold))
                    Text(L10n.text("Fiil, sıfat ve kalıp ifadeler ('-')", en: "Verbs, adjectives, and phrases ('-')", de: "Verben, Adjektive und Ausdrücke ('-')"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .toggleStyle(.switch)
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
    }

    private var topicAndDeckFields: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text(L10n.text("Konu / Tema (İsteğe bağlı):", en: "Topic / Theme (Optional):", de: "Thema (Optional):"))
                    .font(.subheadline.weight(.medium))
                    .accessibilityHidden(true)
                TextField(
                    L10n.text("Konu / Tema (İsteğe bağlı)", en: "Topic / Theme (Optional)", de: "Thema (Optional)"),
                    text: $topicHint,
                    prompt: Text(L10n.text("Örn: Seyahat, İş Hayatı, Mutfak, Akademik", en: "e.g. Travel, Business, Kitchen, Academic", de: "z. B. Reisen, Beruf, Küche, Studium"))
                )
                .textFieldStyle(.roundedBorder)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(L10n.text("Eklenecek Deste / Kategori:", en: "Target Deck / Category:", de: "Zielstapel / Kategorie:"))
                    .font(.subheadline.weight(.medium))
                    .accessibilityHidden(true)
                HStack(spacing: 10) {
                    TextField(
                        L10n.text("Eklenecek Deste / Kategori", en: "Target Deck / Category", de: "Zielstapel / Kategorie"),
                        text: Binding(
                            get: { targetDeckName },
                            set: {
                                targetDeckName = $0
                                hasCustomDeckName = true
                            }
                        ),
                        prompt: Text(L10n.text("Deste adı (örn: A1 Seviyesi)", en: "Deck name (e.g. A1 Level)", de: "Stapelname (z. B. Niveau A1)"))
                    )
                    .textFieldStyle(.roundedBorder)

                    Button {
                        showTargetDeckMenu.toggle()
                    } label: {
                        let usesExistingDeck = hasCustomDeckName && store.allDeckNames.contains(trimmedDeckName)
                        Label(L10n.text("Mevcut Desteler", en: "Existing Decks", de: "Vorhandene Stapel"), systemImage: usesExistingDeck ? "folder.fill" : "folder")
                    }
                    .buttonStyle(.bordered)
                    .popover(isPresented: $showTargetDeckMenu, arrowEdge: .bottom) {
                        deckOptions
                    }
                }
            }
        }
    }

    private var deckOptions: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Button {
                    targetDeckName = automaticDeckName
                    hasCustomDeckName = false
                    showTargetDeckMenu = false
                } label: {
                    Label(L10n.text("\(automaticDeckName) (Otomatik)", en: "\(automaticDeckName) (Auto)", de: "\(automaticDeckName) (Auto)"), systemImage: hasCustomDeckName ? "folder" : "folder.fill")
                }
                .accessibilityAddTraits(hasCustomDeckName ? [] : .isSelected)

                Divider()

                ForEach(store.allDeckNames, id: \.self) { deck in
                    let isSelected = hasCustomDeckName && trimmedDeckName == deck
                    Button {
                        targetDeckName = deck
                        hasCustomDeckName = true
                        showTargetDeckMenu = false
                    } label: {
                        Label(deck, systemImage: isSelected ? "folder.fill" : "folder")
                    }
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .buttonStyle(.borderless)
            .padding(12)
        }
        .frame(idealWidth: 250, minHeight: 72, idealHeight: 220)
    }

    // MARK: - Results

    private var generatedResultsSection: some View {
        GroupBox {
            LazyVStack(alignment: .leading, spacing: 8) {
                ForEach(newlyAddedWords) { item in
                    let article = Artikel.parse(item.artikel)
                    let articleColor = article?.color ?? AppPalette.articleNone

                    HStack(alignment: .top, spacing: 12) {
                        Text(article?.rawValue ?? ArtikelFilter.none)
                            .font(.subheadline.bold())
                            .foregroundStyle(articleColor)
                            .padding(.horizontal, 6)
                            .frame(minWidth: 44)
                            .padding(.vertical, 2)
                            .background(articleColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 5))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.word)
                                .fontWeight(.semibold)
                            Text(item.meaning)
                                .foregroundStyle(.secondary)
                        }
                        .font(.subheadline)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(8)
        } label: {
            Label(L10n.text(
                "Kelime Havuzuna Eklenen Yeni Kelimeler (\(newlyAddedWords.count) adet)",
                en: "New Words Added to Word Pool (\(newlyAddedWords.count))",
                de: "Neue Wörter im Wortschatz (\(newlyAddedWords.count))"
            ), systemImage: "checkmark.circle.fill")
                .foregroundStyle(AppPalette.success)
        }
    }

    private struct FilledActionStyle: ButtonStyle {
        @Environment(\.isEnabled) private var isEnabled

        let fill: Color
        let ink: Color

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .fontWeight(.semibold)
                .foregroundStyle(isEnabled ? ink : Color.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    isEnabled ? fill : Color(nsColor: .quaternaryLabelColor),
                    in: RoundedRectangle(cornerRadius: 6)
                )
                .opacity(configuration.isPressed ? 0.85 : 1)
        }
    }

    // MARK: - Execution

    @MainActor
    private func generateAndAddWords() async {
        guard !isGenerating else { return }
        showTargetDeckMenu = false
        isGenerating = true
        didFinishGenerating = false
        generationError = nil
        newlyAddedWords = []
        progressMessage = L10n.text("Kelimeler üretiliyor...", en: "Generating words...", de: "Wörter werden generiert...")

        defer {
            isGenerating = false
            progressMessage = ""
        }

        do {
            let generated = try await MistralVocabularyGenerator.generateWords(
                level: selectedLevel,
                targetCount: wordCount,
                topicHint: topicHint,
                includeArticleless: includeArticleless,
                existingCards: store.cards
            ) { status in
                Task { @MainActor in
                    self.progressMessage = status
                }
            }

            let resolvedDeck = targetDeckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "\(selectedLevel.rawValue) Seviyesi"
                : targetDeckName.trimmingCharacters(in: .whitespacesAndNewlines)

            _ = store.createDeck(name: resolvedDeck)

            var addedItems: [GeneratedWordItem] = []
            for item in generated {
                if let art = Artikel.parse(item.artikel) {
                    if store.addCard(artikel: art, word: item.word, meaning: item.meaning, deckName: resolvedDeck) {
                        addedItems.append(item)
                    }
                } else if includeArticleless &&
                            MistralVocabularyGenerator.isArticlelessMarker(item.artikel) &&
                            !item.meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    // "-" = article-less (verb/adjective/phrase). Its meaning is the typed
                    // answer during study, so only non-blank meanings are accepted.
                    if store.addCard(artikel: nil, word: item.word, meaning: item.meaning, deckName: resolvedDeck) {
                        addedItems.append(item)
                    }
                }
            }
            newlyAddedWords = addedItems
            didFinishGenerating = true
            if !addedItems.isEmpty && GeminiAuthManager.shared.isGoogleAccountConnected {
                Task {
                    await store.syncWithGoogleDrive(authManager: GeminiAuthManager.shared, isSilentAutoSync: true)
                }
            }
        } catch {
            generationError = error.localizedDescription
        }
    }
}
