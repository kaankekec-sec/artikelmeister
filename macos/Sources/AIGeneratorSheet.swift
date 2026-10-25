import SwiftUI
import AppKit

struct AIGeneratorSheet: View {
    @EnvironmentObject var store: DeckStore
    @ObservedObject var authManager = GeminiAuthManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var selectedLevel: CEFRLevel = .a1
    @State private var wordCount: Int = 20
    @State private var topicHint: String = ""
    @State private var targetDeckName: String = "A1 Seviyesi"
    @State private var hasCustomDeckName: Bool = false
    @State private var showAuthConfig: Bool = false
    @State private var showAdvancedApiKey: Bool = false

    @State private var isGenerating: Bool = false
    @State private var progressMessage: String = ""
    @State private var errorMessage: String? = nil
    @State private var newlyAddedWords: [GeneratedWordItem] = []

    var body: some View {
        VStack(spacing: 0) {
            // Sheet Header
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: "sparkles")
                        .font(.title2)
                        .foregroundColor(.purple)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Gemini AI ile Seviyeye Göre Kelime Üret")
                            .font(.title3.bold())
                        Text("Kendi Gemini kullanım hakkınızla A1–C1 seviyesinde, listenizde olmayan yeni kelimeler üretin")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()

                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            .padding(20)
            .background(Color(NSColor.controlBackgroundColor))

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // 1. Gemini Authorization Box
                    authorizationSection

                    // 2. Level (A1-C1) & Count (1-100) Configuration Box
                    generationParametersSection

                    // 3. Error / Status / Results
                    if let error = errorMessage {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.red)
                                .textSelection(.enabled)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.red.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    if !newlyAddedWords.isEmpty {
                        generatedResultsSection
                    }
                }
                .padding(20)
            }

            Divider()

            // Bottom Action Bar
            HStack {
                Label(
                    "Mevcut \(store.cards.count) kelime hariç tutulacak",
                    systemImage: "shield.lefthalf.filled"
                )
                .font(.caption)
                .foregroundColor(.secondary)

                Spacer()

                if isGenerating {
                    HStack(spacing: 8) {
                        ProgressView()
                            .controlSize(.small)
                        Text(progressMessage)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Button("Kapat") {
                    dismiss()
                }
                .buttonStyle(.bordered)
                .disabled(isGenerating)

                Button {
                    Task {
                        await generateAndAddWords()
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "wand.and.stars")
                        Text("\(selectedLevel.rawValue) Seviyesinde \(wordCount) Kelime Üret ve Ekle")
                            .fontWeight(.semibold)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.purple)
                .disabled(isGenerating || !authManager.isAuthorized)
            }
            .padding(16)
            .background(Color(NSColor.controlBackgroundColor))
        }
        .frame(width: 720, height: 640)
        .onAppear {
            if !authManager.isAuthorized {
                showAuthConfig = true
            } else {
                Task {
                    try? await authManager.verifyCredentials()
                }
            }
        }
    }

    // MARK: - Section 1: Gemini Authorization

    private var authorizationSection: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: authManager.isAuthorized ? "checkmark.shield.fill" : "lock.shield")
                            .foregroundColor(authManager.isAuthorized ? .green : .orange)
                            .font(.title3)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(authManager.isAuthorized ? "Gemini Yetkilendirmesi Aktif" : "Gemini Yetkilendirmesi Gerekli")
                                .font(.headline)
                            Text(authManager.authMode.title + " • Model: \(authManager.selectedModel)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    Picker("Model", selection: $authManager.selectedModel) {
                        ForEach(authManager.availableModels, id: \.self) { model in
                            Text(model).tag(model)
                        }
                    }
                    .frame(width: 210)
                    .onChange(of: authManager.selectedModel) {
                        authManager.saveToKeychain()
                    }

                    Button(showAuthConfig ? "Gizle" : "Yetkilendirme Ayarları") {
                        withAnimation {
                            showAuthConfig.toggle()
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }

                if showAuthConfig || !authManager.isAuthorized {
                    Divider()

                    HStack(spacing: 12) {
                        Text("Aktif Gemini Modeli:")
                            .font(.caption.weight(.semibold))

                        Picker("", selection: $authManager.selectedModel) {
                            ForEach(authManager.availableModels, id: \.self) { model in
                                Text(model).tag(model)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(width: 200)
                        .onChange(of: authManager.selectedModel) {
                            authManager.saveToKeychain()
                        }

                        Spacer()
                    }

                    // Primary Auth: Google OAuth 2.0 (Only method shown directly)
                    HStack(spacing: 12) {
                        Button {
                            Task {
                                do {
                                    errorMessage = nil
                                    try await authManager.startBrowserOAuthFlow()
                                    showAuthConfig = false
                                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: true)
                                } catch {
                                    errorMessage = error.localizedDescription
                                }
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "key.fill")
                                Text(authManager.isAuthorized && authManager.authMode == .googleOAuth ? "Google Hesabını Yeniden Bağla" : "Google Hesabı ile Giriş Yap (Tek Tık)")
                                    .fontWeight(.semibold)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.blue)
                        .controlSize(.regular)
                        .disabled(authManager.isAuthenticating)

                        if authManager.isAuthenticating {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Yetkilendiriliyor...")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        Spacer()

                        Button {
                            withAnimation {
                                showAdvancedApiKey.toggle()
                            }
                        } label: {
                            Label(showAdvancedApiKey ? "Gelişmiş Seçenekleri Gizle" : "Gelişmiş: API Anahtarı ile Doğrula", systemImage: "slider.horizontal.3")
                                .font(.caption)
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.accentColor)
                    }

                    // Advanced Section: API Key configuration
                    if showAdvancedApiKey {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Google AI Studio API Anahtarı:")
                                .font(.caption.weight(.semibold))
                            Text("Google hesabınızla Google AI Studio üzerinden ücretsiz Gemini API anahtarınızı alıp aşağıya yapıştırabilirsiniz.")
                                .font(.caption2)
                                .foregroundColor(.secondary)

                            HStack(spacing: 10) {
                                SecureField("Gemini API Anahtarı (AIza...)", text: $authManager.apiKey)
                                    .textFieldStyle(.roundedBorder)

                                Button {
                                    authManager.openGoogleAIStudioInBrowser()
                                } label: {
                                    Label("Google AI Studio'dan Al", systemImage: "safari")
                                }
                                .buttonStyle(.bordered)

                                Button {
                                    Task {
                                        do {
                                            errorMessage = nil
                                            authManager.authMode = .apiKey
                                            try await authManager.verifyCredentials()
                                            showAuthConfig = false
                                        } catch {
                                            errorMessage = error.localizedDescription
                                        }
                                    }
                                } label: {
                                    Label("Doğrula & Kaydet", systemImage: "checkmark.circle")
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(authManager.apiKey.trimmingCharacters(in: .whitespaces).isEmpty)
                            }
                        }
                        .padding(10)
                        .background(Color(NSColor.controlBackgroundColor).opacity(0.5))
                        .cornerRadius(8)
                    }

                    if let statusMsg = authManager.authStatusMessage {
                        HStack {
                            Text(statusMsg)
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer()
                            if authManager.isAuthorized {
                                Button("Yetkiyi Kaldır", role: .destructive) {
                                    authManager.clearAuthorization()
                                }
                                .buttonStyle(.link)
                                .font(.caption)
                            }
                        }
                    }
                }
            }
            .padding(10)
        }
    }

    // MARK: - Section 2: Level & Word Count Selection

    private var generationParametersSection: some View {
        GroupBox(label: Label("Seviye (A1 – C1) ve Kelime Sayısı (1 – 100)", systemImage: "graduationcap.fill")) {
            VStack(alignment: .leading, spacing: 16) {
                // CEFR Level Selector
                VStack(alignment: .leading, spacing: 8) {
                    Text("Almanca Dil Seviyesi (CEFR):")
                        .font(.subheadline.weight(.semibold))

                    HStack(spacing: 10) {
                        ForEach(CEFRLevel.allCases) { lvl in
                            Button {
                                selectedLevel = lvl
                                if !hasCustomDeckName {
                                    targetDeckName = "\(lvl.rawValue) Seviyesi"
                                }
                            } label: {
                                VStack(spacing: 4) {
                                    Text(lvl.rawValue)
                                        .font(.title3.bold())
                                    Text(lvl.title.components(separatedBy: "—").last?.trimmingCharacters(in: .whitespaces) ?? "")
                                        .font(.caption2)
                                        .lineLimit(1)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    selectedLevel == lvl
                                        ? Color.purple.opacity(0.2)
                                        : Color(NSColor.controlBackgroundColor)
                                )
                                .foregroundColor(selectedLevel == lvl ? .purple : .primary)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(selectedLevel == lvl ? Color.purple : Color.secondary.opacity(0.2), lineWidth: selectedLevel == lvl ? 2 : 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    Text(selectedLevel.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Divider()

                // Word Count Selector (1 - 100)
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Üretilecek Kelime Sayısı (1 – 100):")
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        Stepper(value: $wordCount, in: 1...100, step: 1) {
                            Text("\(wordCount) kelime")
                                .font(.headline.monospacedDigit())
                                .foregroundColor(.purple)
                        }
                    }

                    HStack(spacing: 12) {
                        Text("1").font(.caption).foregroundColor(.secondary)
                        Slider(
                            value: Binding(
                                get: { Double(wordCount) },
                                set: { wordCount = max(1, min(100, Int(round($0)))) }
                            ),
                            in: 1...100,
                            step: 1
                        )
                        .tint(.purple)
                        Text("100").font(.caption).foregroundColor(.secondary)
                    }

                    // Quick count preset chips
                    HStack(spacing: 8) {
                        Text("Hızlı Seçim:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        ForEach([5, 10, 20, 30, 50, 75, 100], id: \.self) { preset in
                            Button("\(preset)") {
                                wordCount = preset
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .tint(wordCount == preset ? .purple : .secondary)
                        }
                    }
                }

                Divider()

                // Optional Theme / Topic & Target Deck
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 10) {
                        Text("Konu / Tema (İsteğe bağlı):")
                            .font(.subheadline.weight(.medium))
                            .frame(width: 190, alignment: .leading)
                        TextField("Örn: Seyahat, İş Hayatı, Mutfak, Akademik (Boş bırakılırsa genel)", text: $topicHint)
                            .textFieldStyle(.roundedBorder)
                    }

                    HStack(spacing: 10) {
                        Label("Eklenecek Deste / Kategori:", systemImage: "folder.fill")
                            .font(.subheadline.weight(.medium))
                            .frame(width: 190, alignment: .leading)

                        TextField("Deste adı (örn: A1 Seviyesi, Seyahat)", text: Binding(
                            get: { targetDeckName },
                            set: {
                                targetDeckName = $0
                                hasCustomDeckName = true
                            }
                        ))
                        .textFieldStyle(.roundedBorder)

                        Menu("Mevcut Desteler") {
                            Button("\(selectedLevel.rawValue) Seviyesi (Otomatik)") {
                                targetDeckName = "\(selectedLevel.rawValue) Seviyesi"
                                hasCustomDeckName = false
                            }
                            Divider()
                            ForEach(store.allDeckNames, id: \.self) { d in
                                Button(d) {
                                    targetDeckName = d
                                    hasCustomDeckName = true
                                }
                            }
                        }
                        .frame(width: 145)
                    }
                }
            }
            .padding(10)
        }
    }

    // MARK: - Section 3: Newly Added Words Preview

    private var generatedResultsSection: some View {
        GroupBox(label: Label("Kelime Haznesine Eklenen Yeni Kelimeler (\(newlyAddedWords.count) adet)", systemImage: "checkmark.circle.fill")) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Aşağıdaki kelimeler mevcut listenizle karşılaştırıldı, tekrarsız olarak yerel depolama alanına kaydedildi ve çalışma sırasına eklendi:")
                    .font(.caption)
                    .foregroundColor(.secondary)

                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(newlyAddedWords) { item in
                        let art = Artikel.parse(item.artikel) ?? .der
                        HStack(spacing: 12) {
                            Text(art.rawValue)
                                .font(.subheadline.bold())
                                .foregroundColor(art.color)
                                .frame(width: 44)
                                .padding(.vertical, 2)
                                .background(art.badgeBackground)
                                .clipShape(RoundedRectangle(cornerRadius: 5))

                            Text(item.word)
                                .font(.subheadline.weight(.semibold))

                            Text("—")
                                .foregroundColor(.secondary)

                            Text(item.meaning)
                                .font(.subheadline)
                                .foregroundColor(.secondary)

                            Spacer()
                        }
                        .padding(.vertical, 2)
                    }
                }
                .padding(8)
            }
            .padding(8)
        }
    }

    // MARK: - Execution

    @MainActor
    private func generateAndAddWords() async {
        errorMessage = nil
        isGenerating = true
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
                }
            }
            self.newlyAddedWords = addedItems
            if !addedItems.isEmpty {
                Task {
                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: true)
                }
            }
        } catch {
            self.errorMessage = error.localizedDescription
        }
    }
}
