import SwiftUI
import AppKit

enum ArtikelGuessResult {
    case none
    case correct
    case wrong
}

struct StudyView: View {
    @EnvironmentObject var store: DeckStore
    @State private var isRevealed: Bool = false
    @State private var typedArtikel: String = ""
    @State private var guessResult: ArtikelGuessResult = .none
    @FocusState private var isInputFocused: Bool

    @State private var keyMonitor: Any? = nil
    @State private var importMessage: String? = nil
    @State private var showImportAlert: Bool = false
    @State private var showAIGenerator: Bool = false
    @State private var showNewDeckSheet: Bool = false

    var currentCard: Flashcard? {
        store.studyQueue.first
    }

    var availableRatingsForCurrentState: [ReviewRating] {
        if guessResult == .wrong {
            return [.again, .hard]
        }
        return ReviewRating.allCases
    }

    var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()

            if store.cards.isEmpty {
                emptyDatabaseView
            } else if let card = currentCard {
                activeCardSessionView(card: card)
            } else if let pendingCard = store.nextPendingLearningCard {
                waitingForLearningCardView(pendingCard: pendingCard)
            } else {
                sessionCompletedView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(NSColor.windowBackgroundColor))
        .onAppear {
            store.rebuildStudyQueue()
            setupKeyboardShortcuts()
            focusArtikelInputIfNeeded()
        }
        .onDisappear {
            removeKeyboardShortcuts()
        }
        .onChange(of: currentCard?.id) {
            resetCardInputState()
        }
        .sheet(isPresented: $showAIGenerator) {
            AIGeneratorSheet()
                .environmentObject(store)
        }
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { createdDeck in
                store.selectedStudyDeck = createdDeck
            }
            .environmentObject(store)
        }
        .alert("CSV İçe Aktarma Sonucu", isPresented: $showImportAlert) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text(importMessage ?? "")
        }
    }

    private func resetCardInputState() {
        isRevealed = false
        typedArtikel = ""
        guessResult = .none
        focusArtikelInputIfNeeded()
    }

    private func focusArtikelInputIfNeeded() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
            if !isRevealed && currentCard != nil {
                isInputFocused = true
            }
        }
    }

    // MARK: - Top Status Header (Optimized for all window widths)

    private var headerBar: some View {
        HStack(spacing: 10) {
            // Deck Selector Menu with "+ Yeni Deste Ekle" at bottom
            Menu {
                Button {
                    store.selectedStudyDeck = nil
                } label: {
                    HStack {
                        Text("Tüm Desteler (\(store.cards.count))")
                        if store.selectedStudyDeck == nil {
                            Image(systemName: "checkmark")
                        }
                    }
                }

                Divider()

                ForEach(store.allDeckNames, id: \.self) { deck in
                    Button {
                        store.selectedStudyDeck = deck
                    } label: {
                        HStack {
                            Text("\(deck) (\(store.cardCount(forDeck: deck)))")
                            if store.selectedStudyDeck == deck {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }

                Divider()

                Button {
                    showNewDeckSheet = true
                } label: {
                    Label("Yeni Deste Ekle...", systemImage: "folder.badge.plus")
                }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "folder.fill")
                        .foregroundColor(.accentColor)
                    Text(store.selectedStudyDeck ?? "Tüm Desteler")
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
                .frame(maxWidth: 150, alignment: .leading)
            }
            .menuStyle(.borderedButton)
            .fixedSize(horizontal: true, vertical: false)

            // Queue Counters (fixedSize prevents vertical character wrapping!)
            HStack(spacing: 6) {
                CounterPill(
                    title: "Yeni",
                    count: store.availableNewCardsCount,
                    color: .blue
                )
                CounterPill(
                    title: "Öğreniliyor",
                    count: store.dueLearningCardsCount,
                    color: .orange
                )
                CounterPill(
                    title: "Tekrar",
                    count: store.dueReviewCardsCount,
                    color: .green
                )
            }
            .fixedSize(horizontal: true, vertical: false)

            Spacer(minLength: 6)

            // Right Action & Streak Controls
            HStack(spacing: 8) {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .foregroundColor(.orange)
                    Text("\(store.currentStreakDays) Gün")
                        .font(.caption.weight(.bold))
                        .foregroundColor(.orange)
                        .lineLimit(1)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(Color.orange.opacity(0.14))
                .clipShape(Capsule())
                .fixedSize(horizontal: true, vertical: false)
                .help("Kesintisiz çalışma serisi: \(store.currentStreakDays) gün (En uzun rekor: \(store.longestStreakDays) gün)")

                Button {
                    showAIGenerator = true
                } label: {
                    Label("AI Kelime Üret", systemImage: "wand.and.stars")
                        .lineLimit(1)
                }
                .buttonStyle(.borderedProminent)
                .tint(.purple)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)

                Button {
                    promptAndImportCSV()
                } label: {
                    Label("CSV Yükle", systemImage: "square.and.arrow.down")
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
            }
            .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(NSColor.controlBackgroundColor))
    }

    // MARK: - Active Flashcard View

    private var cardBorderColor: Color {
        guard isRevealed else { return Color.primary.opacity(0.12) }
        switch guessResult {
        case .correct: return Color.green.opacity(0.85)
        case .wrong: return Color.red.opacity(0.85)
        case .none: return Color.primary.opacity(0.25)
        }
    }

    private var wordHighlightColor: Color {
        guard isRevealed else { return .primary }
        switch guessResult {
        case .correct: return Color(red: 0.12, green: 0.78, blue: 0.35) // Vivid green
        case .wrong: return Color(red: 0.92, green: 0.18, blue: 0.22)   // Vivid red
        case .none: return .primary
        }
    }

    private func activeCardSessionView(card: Flashcard) -> some View {
        GeometryReader { geo in
            let isCompactHeight = geo.size.height < 520
            VStack(spacing: isCompactHeight ? 14 : 20) {
                Spacer(minLength: 8)

                // Main Flashcard Box
                ZStack {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color(NSColor.controlBackgroundColor))
                        .shadow(
                            color: isRevealed
                                ? (guessResult == .correct ? Color.green.opacity(0.22) : Color.red.opacity(0.22))
                                : Color.black.opacity(0.08),
                            radius: 16,
                            x: 0,
                            y: 5
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .stroke(cardBorderColor, lineWidth: isRevealed ? 3.5 : 1)
                        )

                    VStack(spacing: isCompactHeight ? 12 : 18) {
                        // Top metadata bar inside card
                        HStack(spacing: 8) {
                            Text(card.stage.title.uppercased())
                                .font(.caption2.weight(.bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(card.stage.color.opacity(0.15))
                                .foregroundColor(card.stage.color)
                                .clipShape(Capsule())
                                .fixedSize()

                            Label(card.deckName, systemImage: "folder")
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Color.secondary.opacity(0.12))
                                .foregroundColor(.secondary)
                                .clipShape(Capsule())
                                .lineLimit(1)

                            if card.totalReviews > 0 {
                                Text("Kolaylık: \(Int(card.easeFactor * 100))% • Tekrar: \(card.totalReviews)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }

                            Spacer()

                            Button {
                                let spokenText = isRevealed ? card.fullGermanTerm : card.word
                                SpeechManager.shared.speak(spokenText)
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "speaker.wave.2.fill")
                                    Text("Dinle")
                                        .font(.caption.weight(.medium))
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.secondary.opacity(0.12))
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                            .fixedSize()
                            .help("Almanca telaffuzu dinle")
                        }

                        Spacer(minLength: 4)

                        // Center Content: Front (Input Box + Word) vs Back (Green/Red Result)
                        if !isRevealed {
                            VStack(spacing: 16) {
                                Text(card.isArticleless
                                     ? "Kelimenin Türkçe anlamını yazıp Enter'a basın:"
                                     : "Kelimenin artikelini (der / die / das) boşluğa yazıp Enter'a basın:")
                                    .font(.subheadline.weight(.medium))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)

                                HStack(alignment: .center, spacing: 14) {
                                    TextField(card.isArticleless ? "anlam..." : "artikel...", text: $typedArtikel)
                                        .textFieldStyle(.plain)
                                        .font(.system(size: 28, weight: .bold, design: .rounded))
                                        .multilineTextAlignment(.center)
                                        .frame(width: card.isArticleless ? 240 : 132, height: 54)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color(NSColor.textBackgroundColor))
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(Color.accentColor.opacity(0.75), lineWidth: 2.5)
                                        )
                                        .focused($isInputFocused)
                                        .onSubmit {
                                            checkAndRevealCard(card)
                                        }

                                    Text(card.word)
                                        .font(.system(size: 42, weight: .bold, design: .rounded))
                                        .foregroundColor(.primary)
                                        .lineLimit(2)
                                        .minimumScaleFactor(0.45)
                                        .multilineTextAlignment(.leading)
                                        .textSelection(.enabled)
                                }
                                .padding(.horizontal, 8)

                                if store.settings.showMeaningOnFront && !card.meaning.isEmpty && !card.isArticleless {
                                    Text(card.meaning)
                                        .font(.title3)
                                        .foregroundColor(.secondary)
                                        .lineLimit(2)
                                        .minimumScaleFactor(0.75)
                                        .multilineTextAlignment(.center)
                                }
                            }
                        } else {
                            VStack(spacing: 14) {
                                // Feedback Status Banner (Doğru / Yanlış)
                                HStack(spacing: 8) {
                                    Image(systemName: guessResult == .correct ? "checkmark.circle.fill" : "xmark.octagon.fill")
                                        .font(.headline)
                                    if guessResult == .correct {
                                        Text("DOĞRU! (\(card.artikel?.grammaticalLabel ?? card.meaning))")
                                            .font(.subheadline.weight(.bold))
                                    } else {
                                        let userCleaned = typedArtikel.trimmingCharacters(in: .whitespacesAndNewlines)
                                        if userCleaned.isEmpty {
                                            Text("BOŞ BIRAKILDI — Doğru \(card.isArticleless ? "Anlam" : "Artikel"): \(AnswerChecker.expectedLabel(card: card))")
                                                .font(.subheadline.weight(.bold))
                                        } else {
                                            Text("YANLIŞ (\(userCleaned)) — Doğru \(card.isArticleless ? "Anlam" : "Artikel"): \(AnswerChecker.expectedLabel(card: card))")
                                                .font(.subheadline.weight(.bold))
                                        }
                                    }
                                }
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .foregroundColor(wordHighlightColor)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(wordHighlightColor.opacity(0.15))
                                .clipShape(Capsule())

                                // Revealed Artikel + Word lit up in Green (if correct) or Red (if wrong)
                                HStack(alignment: .firstTextBaseline, spacing: 12) {
                                    if let art = card.artikel {
                                        Text(art.rawValue)
                                            .font(.system(size: 44, weight: .heavy, design: .rounded))
                                            .foregroundColor(wordHighlightColor)
                                    }

                                    Text(card.word)
                                        .font(.system(size: 42, weight: .bold, design: .rounded))
                                        .foregroundColor(wordHighlightColor)
                                        .lineLimit(2)
                                        .minimumScaleFactor(0.45)
                                }
                                .multilineTextAlignment(.center)
                                .textSelection(.enabled)
                                .padding(.horizontal, 8)

                                if !card.meaning.isEmpty {
                                    Divider()
                                        .frame(width: 160)

                                    Text(card.meaning)
                                        .font(.system(size: 21, weight: .medium, design: .rounded))
                                        .foregroundColor(.secondary)
                                        .lineLimit(2)
                                        .minimumScaleFactor(0.7)
                                        .multilineTextAlignment(.center)
                                }
                            }
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                        }

                        Spacer(minLength: 4)

                        // Legend for artikel colors
                        HStack(spacing: 16) {
                            ArtikelLegendDot(artikel: .der, label: "der (Eril)")
                            ArtikelLegendDot(artikel: .die, label: "die (Dişil)")
                            ArtikelLegendDot(artikel: .das, label: "das (Nötr)")
                        }
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize()
                    }
                    .padding(isCompactHeight ? 22 : 28)
                }
                .frame(maxWidth: 680, maxHeight: isCompactHeight ? 330 : 390)
                .padding(.horizontal, 24)

                // Bottom Action Area
                VStack(spacing: 8) {
                    if !isRevealed {
                        Button {
                            checkAndRevealCard(card)
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark.seal.fill")
                                Text("Kontrol Et")
                                    .font(.headline)
                                Text("(Enter)")
                                    .font(.subheadline)
                                    .opacity(0.75)
                            }
                            .frame(width: 240, height: 38)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                    } else {
                        if guessResult == .wrong {
                            Text("Yanlış cevaplandığı için sadece 'Tekrar (1)' veya 'Zor (2)' seçilebilir:")
                                .font(.caption.weight(.medium))
                                .foregroundColor(.red)
                                .lineLimit(1)
                        } else {
                            Text("Hatırlama zorluğunu seçin (Klavye: 1 - 4):")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }

                        HStack(spacing: 12) {
                            ForEach(availableRatingsForCurrentState, id: \.rawValue) { rating in
                                let nextInterval = SM2Scheduler.previewInterval(for: card, rating: rating)
                                RatingButton(
                                    rating: rating,
                                    intervalText: nextInterval
                                ) {
                                    submitRating(card: card, rating: rating)
                                }
                            }
                        }
                        .frame(maxWidth: guessResult == .wrong ? 340 : 640)
                        .padding(.horizontal, 24)
                    }
                }
                .frame(height: 82)

                Spacer(minLength: 8)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    // MARK: - Waiting for Learning Timer View

    private func waitingForLearningCardView(pendingCard: Flashcard) -> some View {
        let remainingSeconds = max(0, Int(ceil(pendingCard.dueDate.timeIntervalSince(store.currentTime))))
        let mins = remainingSeconds / 60
        let secs = remainingSeconds % 60
        let timeString = String(format: "%02d:%02d", mins, secs)

        return VStack(spacing: 18) {
            Spacer()
            Image(systemName: "timer")
                .font(.system(size: 52))
                .foregroundColor(.orange)

            Text("Öğrenme Kartı Zamanlayıcısı")
                .font(.title2.bold())

            Text("Sıradaki tekrar kelimesi vaat edilen süre dolduğunda otomatik olarak ekrana gelecektir:")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 460)

            Text(timeString)
                .font(.system(size: 44, weight: .heavy, design: .monospaced))
                .foregroundColor(.orange)
                .padding(.horizontal, 24)
                .padding(.vertical, 10)
                .background(Color.orange.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 16))

            HStack(spacing: 12) {
                Button {
                    store.forcePullNextLearningCard()
                } label: {
                    Label("Süreyi Beklemeden Şimdi Çalış", systemImage: "bolt.fill")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.borderedProminent)
                .tint(.orange)
                .controlSize(.large)

                if store.filteredPoolCards.contains(where: { $0.stage == .new }) && store.remainingNewToday == 0 {
                    Button {
                        store.settings.dailyNewCardLimit += 10
                        store.saveSettings()
                    } label: {
                        Label("+10 Yeni Kelime Aç", systemImage: "plus.circle")
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }
            }

            Spacer()
        }
        .padding(28)
    }

    // MARK: - Empty & Completed Views

    private var emptyDatabaseView: some View {
        VStack(spacing: 18) {
            Spacer()
            BloodyBrainLogoView(size: 100, animated: true)

            Text("Henüz Kelime Eklenmedi")
                .font(.title2.bold())

            Text("İlk sütunu artikel (der, die, das), ikinci sütunu Almanca kelime olan bir CSV dosyası yükleyerek veya Gemini AI ile üreterek hemen başlayabilirsiniz.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 480)

            HStack(spacing: 12) {
                Button {
                    showAIGenerator = true
                } label: {
                    Label("Gemini AI ile Kelime Üret", systemImage: "wand.and.stars")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.borderedProminent)
                .tint(.purple)
                .controlSize(.large)

                Button {
                    promptAndImportCSV()
                } label: {
                    Label("CSV Dosyası Yükle", systemImage: "doc.badge.plus")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                Button {
                    let res = store.loadStarterGermanPack()
                    importMessage = "Örnek paket yüklendi: \(res.addedCount) yeni kelime yerel depolama alanına kaydedildi."
                    showImportAlert = true
                } label: {
                    Label("Örnek 30 Kelime", systemImage: "sparkles")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
            }

            Button {
                store.openStorageDirectoryInFinder()
            } label: {
                Label("Yerel Depolama Klasörünü Finder'da Göster", systemImage: "folder")
                    .font(.caption)
            }
            .buttonStyle(.link)
            .padding(.top, 4)

            Spacer()
        }
        .padding(28)
    }

    private var sessionCompletedView: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 56))
                .foregroundColor(.green)

            Text(store.selectedStudyDeck != nil ? "'\(store.selectedStudyDeck!)' Destesi Tamamlandı!" : "Bugünkü Çalışma Tamamlandı!")
                .font(.title2.bold())

            Text("Günlük hedeflerinizi ve bekleyen tekrarlarınızı tamamladınız. Aralıklı tekrar (SM-2) algoritması kartlarınızı hatırlama zorluğunuza göre sonraki günlere planladı.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 480)

            HStack(spacing: 20) {
                StatMiniBox(title: "Yeni Öğrenilen", value: "\(store.dailyProgress.newCardsStudied)", color: .blue)
                StatMiniBox(title: "Tekrar Edilen", value: "\(store.dailyProgress.reviewsCompleted)", color: .green)
                StatMiniBox(title: "Deste Kelimesi", value: "\(store.filteredPoolCards.count)", color: .purple)
            }
            .padding(.vertical, 6)

            HStack(spacing: 12) {
                if store.filteredPoolCards.contains(where: { $0.stage == .new }) {
                    Button {
                        store.settings.dailyNewCardLimit += 10
                        store.saveSettings()
                    } label: {
                        Label("+10 Yeni Kelime Aç", systemImage: "plus.circle.fill")
                    }
                    .buttonStyle(.borderedProminent)
                }

                Button {
                    showAIGenerator = true
                } label: {
                    Label("AI Kelime Üret (A1–C1)", systemImage: "wand.and.stars")
                }
                .buttonStyle(.borderedProminent)
                .tint(.purple)

                Button {
                    promptAndImportCSV()
                } label: {
                    Label("CSV İçe Aktar", systemImage: "square.and.arrow.down")
                }
                .buttonStyle(.bordered)
            }

            Spacer()
        }
        .padding(28)
    }

    // MARK: - Actions & Keyboard Shortcuts

    private func checkAndRevealCard(_ card: Flashcard) {
        guard !isRevealed else { return }
        isInputFocused = false

        if AnswerChecker.isCorrect(card: card, typed: typedArtikel) {
            guessResult = .correct
        } else {
            guessResult = .wrong
        }

        withAnimation(.easeInOut(duration: 0.18)) {
            isRevealed = true
        }
        if store.settings.autoPlayPronunciation {
            SpeechManager.shared.speak(card.fullGermanTerm)
        }
    }

    private func submitRating(card: Flashcard, rating: ReviewRating) {
        if guessResult == .wrong && (rating == .good || rating == .easy) {
            return
        }
        resetCardInputState()
        store.answerCard(card, rating: rating)
    }

    private func promptAndImportCSV() {
        let panel = NSOpenPanel()
        panel.title = "Almanca Kelime CSV Dosyası Seçin"
        panel.message = "1. Sütun: Artikel (der/die/das), 2. Sütun: Kelime, 3. Sütun: Anlam, 4. Sütun (İsteğe Bağlı): Deste"
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .tabSeparatedText]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let targetDeck = store.selectedStudyDeck ?? "Genel"
                let res = try store.importCSV(from: url, defaultDeckName: targetDeck)
                importMessage = """
                Yeni eklenen kelime: \(res.addedCount)
                Güncellenen kelime: \(res.updatedCount)
                Zaten mevcut (atlanan): \(res.skippedCount)
                Geçersiz/Atlanan satır: \(res.invalidLines)
                """
                showImportAlert = true
            } catch {
                importMessage = "Hata: \(error.localizedDescription)"
                showImportAlert = true
            }
        }
    }

    private func setupKeyboardShortcuts() {
        removeKeyboardShortcuts()
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            guard let card = store.studyQueue.first else { return event }

            if !isRevealed {
                if event.keyCode == 36 || event.keyCode == 76 {
                    checkAndRevealCard(card)
                    return nil
                }
                return event
            }

            let chars = event.charactersIgnoringModifiers?.lowercased() ?? ""
            switch chars {
            case "1":
                submitRating(card: card, rating: .again)
                return nil
            case "2":
                submitRating(card: card, rating: .hard)
                return nil
            case "3":
                if guessResult != .wrong {
                    submitRating(card: card, rating: .good)
                    return nil
                }
            case "4":
                if guessResult != .wrong {
                    submitRating(card: card, rating: .easy)
                    return nil
                }
            case "s":
                SpeechManager.shared.speak(card.fullGermanTerm)
                return nil
            default:
                break
            }
            return event
        }
    }

    private func removeKeyboardShortcuts() {
        if let monitor = keyMonitor {
            NSEvent.removeMonitor(monitor)
            keyMonitor = nil
        }
    }
}

// MARK: - Subcomponents

private struct CounterPill: View {
    let title: String
    let count: Int
    let color: Color

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundColor(.secondary)
                .lineLimit(1)
            Text("\(count)")
                .font(.caption.weight(.bold))
                .foregroundColor(color)
                .lineLimit(1)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(color.opacity(0.1))
        .clipShape(Capsule())
        .fixedSize(horizontal: true, vertical: false)
    }
}

private struct ArtikelLegendDot: View {
    let artikel: Artikel
    let label: String

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(artikel.color)
                .frame(width: 8, height: 8)
            Text(label)
                .lineLimit(1)
        }
    }
}

private struct RatingButton: View {
    let rating: ReviewRating
    let intervalText: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Text(intervalText)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(rating.color)
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Text(rating.title)
                        .font(.subheadline.weight(.bold))
                        .lineLimit(1)
                    Text("(\(rating.shortcutKey))")
                        .font(.caption2)
                        .opacity(0.75)
                        .lineLimit(1)
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(rating.color)
                .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
        }
        .buttonStyle(.plain)
    }
}

private struct StatMiniBox: View {
    let title: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundColor(color)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
        .frame(width: 125, height: 66)
        .background(color.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
