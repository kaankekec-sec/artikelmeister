import SwiftUI
import AppKit
import Accessibility

struct AIGeneratorSheet: View {
    @EnvironmentObject var store: DeckStore
    @ObservedObject var authManager = GeminiAuthManager.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var selectedLevel: CEFRLevel = .a1
    @State private var wordCount = 20
    @State private var topicHint = ""
    @State private var includeArticleless = false
    @State private var targetDeckName = "A1 Seviyesi"
    @State private var hasCustomDeckName = false
    @State private var showTargetDeckMenu = false
    @State private var showAuthConfig = false
    @State private var showAdvancedApiKey = false

    @State private var isGenerating = false
    @State private var progressMessage = ""
    @State private var authorizationError: String?
    @State private var startupValidationError: String?
    @State private var acceptsStartupValidationError = true
    @State private var generationError: String?
    @State private var didFinishGenerating = false
    @State private var newlyAddedWords: [GeneratedWordItem] = []

    private enum ScrollTarget: Hashable {
        case generationError, results
    }

    private var automaticDeckName: String {
        "\(selectedLevel.rawValue) Seviyesi"
    }

    private var trimmedDeckName: String {
        targetDeckName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var resultSummary: String {
        newlyAddedWords.isEmpty ? "Yeni kelime eklenmedi" : "\(newlyAddedWords.count) kelime eklendi"
    }

    private var visibleAuthorizationError: String? {
        authorizationError ?? (showAuthConfig && authManager.isAuthorized ? startupValidationError : nil)
    }

    private var activeCredential: String {
        if authManager.authMode == .apiKey { return authManager.apiKey }
        return authManager.oauthRefreshToken.isEmpty ? authManager.oauthAccessToken : authManager.oauthRefreshToken
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
        .onChange(of: authManager.selectedModel) {
            authManager.saveToKeychain()
        }
        .onAppear {
            if authManager.isAuthorized {
                Task {
                    do {
                        try await authManager.verifyCredentials()
                    } catch {
                        if acceptsStartupValidationError {
                            startupValidationError = error.localizedDescription
                        }
                    }
                }
            }
        }
        .onChange(of: authManager.authMode) { clearAuthorizationError() }
        .onChange(of: activeCredential) { clearAuthorizationError() }
        .onChange(of: visibleAuthorizationError) {
            if visibleAuthorizationError != nil {
                AccessibilityNotification.Announcement("Gemini yetkilendirmesi doğrulanamadı").post()
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Image(systemName: "sparkles")
                .font(.title2)
                .foregroundStyle(AppPalette.accentPurple)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Gemini AI ile Seviyeye Göre Kelime Üret")
                    .font(.title3.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("Kendi Gemini kullanım hakkınızla A1–C1 seviyesinde, listenizde olmayan yeni kelimeler üretin")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
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
            .accessibilityLabel("Kapat")
            .help("Kapat")
        }
        .padding(20)
        .background(Color(nsColor: .controlBackgroundColor))
    }

    private var scrollableForm: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    authorizationSection
                        .disabled(isGenerating)

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
                    AccessibilityNotification.Announcement("Gemini bağlantısı kuruluyor...").post()
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
                Label("Kelime üretilirken bir hata oluştu.", systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(AppPalette.error)
            } else if didFinishGenerating {
                Text(resultSummary)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(newlyAddedWords.isEmpty ? Color.secondary : AppPalette.success)
            }

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(selectedLevel.rawValue) • \(wordCount) kelime • \(trimmedDeckName.isEmpty ? automaticDeckName : trimmedDeckName)")
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Mevcut \(store.cards.count) kelime hariç tutulacak")
                        .foregroundStyle(.secondary)
                }
                .font(.caption)
                Spacer(minLength: 0)

                Button("Kapat") {
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
                    Label("Üret ve ekle", systemImage: "sparkles")
                }
                .buttonStyle(FilledActionStyle(fill: AppPalette.accentPurple, ink: AppPalette.onPurple))
                .keyboardShortcut(.return, modifiers: .command)
                .disabled(isGenerating || !authManager.isAuthorized)
            }
        }
        .padding(16)
        .background(Color(nsColor: .controlBackgroundColor))
    }

    // MARK: - Gemini Authorization

    private var authorizationSection: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                if authManager.isAuthorized {
                    DisclosureGroup(isExpanded: $showAuthConfig) {
                        authorizationControls
                            .padding(.top, 8)
                    } label: {
                        authorizationStatus
                    }
                    .transaction { transaction in
                        if reduceMotion {
                            transaction.animation = nil
                            transaction.disablesAnimations = true
                        }
                    }
                } else {
                    authorizationStatus
                    authorizationControls
                }

                if let visibleAuthorizationError {
                    errorNotice(visibleAuthorizationError)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
        }
    }

    private var authorizationStatus: some View {
        HStack(spacing: 8) {
            Image(systemName: authManager.isAuthorized ? "checkmark.shield.fill" : "lock.shield")
                .font(.title3)
                .foregroundStyle(authManager.isAuthorized ? AppPalette.success : AppPalette.accentOrange)
                .accessibilityHidden(true)
            Text(authManager.isAuthorized ? "Gemini Yetkilendirmesi Aktif" : "Gemini Yetkilendirmesi Gerekli")
                .font(.headline)
            if authManager.isAuthorized {
                Text("• \(authManager.authMode.title) • \(authManager.selectedModel)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint(authManager.isAuthorized ? "Yetkilendirme Ayarları" : "")
    }

    private var authorizationControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Model", selection: $authManager.selectedModel) {
                ForEach(authManager.availableModels, id: \.self) { model in
                    Text(model).tag(model)
                }
            }
            .pickerStyle(.menu)
            .id(isGenerating)

            Button {
                Task {
                    do {
                        clearAuthorizationError()
                        try await authManager.startBrowserOAuthFlow()
                        showAuthConfig = false
                        await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: true)
                    } catch {
                        authorizationError = error.localizedDescription
                    }
                }
            } label: {
                Label(
                    authManager.isAuthorized && authManager.authMode == .googleOAuth
                        ? "Google Hesabını Yeniden Bağla" : "Google Hesabı ile Giriş Yap (Tek Tık)",
                    systemImage: "person.crop.circle"
                )
            }
            .buttonStyle(FilledActionStyle(fill: AppPalette.accentBlue, ink: AppPalette.onBlue))
            .disabled(authManager.isAuthenticating)

            if authManager.isAuthenticating {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                        .accessibilityHidden(true)
                    Text("Yetkilendiriliyor...")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            DisclosureGroup(isExpanded: $showAdvancedApiKey) {
                apiKeyControls
                    .padding(.top, 8)
            } label: {
                Label("Gelişmiş: API Anahtarı ile Doğrula", systemImage: "slider.horizontal.3")
                    .font(.caption)
            }
            .transaction { transaction in
                if reduceMotion {
                    transaction.animation = nil
                    transaction.disablesAnimations = true
                }
            }

            if let statusMessage = authManager.authStatusMessage {
                HStack {
                    Text(statusMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    if authManager.isAuthorized {
                        Button("Yetkiyi Kaldır", role: .destructive) {
                            authManager.clearAuthorization()
                            clearAuthorizationError()
                        }
                        .buttonStyle(.link)
                        .font(.caption)
                    }
                }
            }
        }
    }

    private var apiKeyControls: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Google AI Studio API Anahtarı:")
                .font(.caption.weight(.semibold))
            Text("Google hesabınızla Google AI Studio üzerinden ücretsiz Gemini API anahtarınızı alıp aşağıya yapıştırabilirsiniz.")
                .font(.caption2)
                .foregroundStyle(.secondary)

            SecureField("Gemini API Anahtarı", text: $authManager.apiKey, prompt: Text("AIza..."))
                .textFieldStyle(.roundedBorder)

            HStack(spacing: 10) {
                Button {
                    authManager.openGoogleAIStudioInBrowser()
                } label: {
                    Label("Google AI Studio'dan Al", systemImage: "arrow.up.right.square")
                }
                .buttonStyle(.bordered)

                Button {
                    Task {
                        do {
                            clearAuthorizationError()
                            authManager.authMode = .apiKey
                            try await authManager.verifyCredentials()
                            showAuthConfig = false
                        } catch {
                            authorizationError = error.localizedDescription
                        }
                    }
                } label: {
                    Label("Doğrula & Kaydet", systemImage: "checkmark.circle")
                }
                .buttonStyle(FilledActionStyle(fill: AppPalette.accentBlue, ink: AppPalette.onBlue))
                .disabled(authManager.apiKey.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(10)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
    }

    private func clearAuthorizationError() {
        authorizationError = nil
        startupValidationError = nil
        acceptsStartupValidationError = false
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
            Label("Seviye (A1 – C1) ve Kelime Sayısı (1 – 100)", systemImage: "graduationcap.fill")
        }
    }

    private var levelSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Almanca Dil Seviyesi (CEFR):")
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
            .accessibilityLabel("Almanca Dil Seviyesi")

            Text(selectedLevel.description)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var countSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Üretilecek Kelime Sayısı (1 – 100):")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Stepper(value: $wordCount, in: 1...100, step: 1) {
                    Text("\(wordCount) kelime")
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
                .accessibilityLabel("Kelime sayısı")
                .accessibilityValue("\(wordCount) kelime")
                Text("100").font(.caption).foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                Text("Hızlı Seçim:")
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
                    .accessibilityLabel("\(preset) kelime")
                    .accessibilityAddTraits(wordCount == preset ? .isSelected : [])
                }
            }

            Toggle(isOn: $includeArticleless) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Artikelsiz kelimeleri de üret")
                        .font(.subheadline.weight(.semibold))
                    Text("Fiiller, sıfatlar ve kalıp ifadeler '-' olarak eklenir; çalışırken Türkçe anlam yazılır.")
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
                Text("Konu / Tema (İsteğe bağlı):")
                    .font(.subheadline.weight(.medium))
                    .accessibilityHidden(true)
                TextField(
                    "Konu / Tema (İsteğe bağlı)",
                    text: $topicHint,
                    prompt: Text("Örn: Seyahat, İş Hayatı, Mutfak, Akademik")
                )
                .textFieldStyle(.roundedBorder)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Eklenecek Deste / Kategori:")
                    .font(.subheadline.weight(.medium))
                    .accessibilityHidden(true)
                HStack(spacing: 10) {
                    TextField(
                        "Eklenecek Deste / Kategori",
                        text: Binding(
                            get: { targetDeckName },
                            set: {
                                targetDeckName = $0
                                hasCustomDeckName = true
                            }
                        ),
                        prompt: Text("Deste adı (örn: A1 Seviyesi)")
                    )
                    .textFieldStyle(.roundedBorder)

                    Button {
                        showTargetDeckMenu.toggle()
                    } label: {
                        let usesExistingDeck = hasCustomDeckName && store.allDeckNames.contains(trimmedDeckName)
                        Label("Mevcut Desteler", systemImage: usesExistingDeck ? "folder.fill" : "folder")
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
                    Label("\(automaticDeckName) (Otomatik)", systemImage: hasCustomDeckName ? "folder" : "folder.fill")
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
            Label("Kelime Havuzuna Eklenen Yeni Kelimeler (\(newlyAddedWords.count) adet)", systemImage: "checkmark.circle.fill")
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
        guard !isGenerating, authManager.isAuthorized else { return }
        clearAuthorizationError()
        showTargetDeckMenu = false
        isGenerating = true
        didFinishGenerating = false
        generationError = nil
        newlyAddedWords = []
        progressMessage = "Gemini bağlantısı kuruluyor..."

        defer {
            isGenerating = false
            progressMessage = ""
        }

        do {
            let generated = try await GeminiVocabularyGenerator.generateWords(
                level: selectedLevel,
                targetCount: wordCount,
                topicHint: topicHint,
                includeArticleless: includeArticleless,
                existingCards: store.cards,
                authManager: authManager
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
                            GeminiVocabularyGenerator.isArticlelessMarker(item.artikel) &&
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
            if !addedItems.isEmpty {
                Task {
                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: true)
                }
            }
        } catch {
            generationError = error.localizedDescription
        }
    }
}
