import SwiftUI
import AppKit
import UniformTypeIdentifiers

enum ArtikelGuessResult {
    case none
    case correct
    case wrong
}

struct StudyView: View {
    @EnvironmentObject var store: DeckStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isRevealed: Bool = false
    @State private var typedArtikel: String = ""
    @State private var guessResult: ArtikelGuessResult = .none
    @FocusState private var isInputFocused: Bool
    @AccessibilityFocusState private var isResultFocused: Bool

    @State private var keyboardWindow = StudyKeyboardScope.WindowReference()

    @State private var keyMonitor: Any? = nil
    @State private var importTitle = ""
    @State private var importMessage = ""
    @State private var importUndoSucceeded: Bool?
    @State private var isChoosingCSV = false
    @State private var showImportAlert: Bool = false
    @State private var offerImportUndo: Bool = false
    @State private var showAIGenerator: Bool = false
    @State private var showNewDeckSheet: Bool = false
    @State private var showStudySummary = false

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

            if let importUndoSucceeded {
                importUndoNotice(succeeded: importUndoSucceeded)
            }

            if store.cards.isEmpty {
                emptyDatabaseView
            } else if store.filteredPoolCards.isEmpty {
                ContentUnavailableView(
                    "Bu destede henüz kelime yok",
                    systemImage: "folder",
                    description: Text("Bu desteye kelime eklemek için CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
        .background {
            StudyKeyboardScope(reference: keyboardWindow)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .focusedSceneValue(\.importCSVAction, studyAction(promptAndImportCSV))
        .focusedSceneValue(\.aiGeneratorAction, studyAction { showAIGenerator = true })
        .focusedSceneValue(\.speakWordAction, speakWordAction)
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
        .onChange(of: store.selectedStudyDeck) {
            showStudySummary = false
        }
        .onChange(of: isPresentingModal) { _, isPresented in
            if isPresented {
                showStudySummary = false
            } else {
                focusArtikelInputIfNeeded()
            }
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
        .alert(importTitle, isPresented: $showImportAlert) {
            if offerImportUndo {
                Button("Geri al", role: .destructive) {
                    importUndoSucceeded = store.undoLastImport()
                    offerImportUndo = false
                }
            }
            Button("Tamam", role: .cancel) {
                offerImportUndo = false
                store.finishImportUndoWindow()
            }
        } message: {
            Text(importMessage)
        }
    }

    private var isPresentingModal: Bool {
        showAIGenerator || showNewDeckSheet || showImportAlert || isChoosingCSV
    }

    private func studyAction(_ action: @escaping () -> Void) -> (() -> Void)? {
        guard !isPresentingModal else { return nil }
        return {
            guard !isPresentingModal, NSApp.modalWindow == nil,
                  let window = keyboardWindow.view?.window, window.isKeyWindow,
                  window.attachedSheet == nil else { return }
            action()
        }
    }

    private var speakWordAction: (() -> Void)? {
        guard currentCard != nil else { return nil }
        return studyAction {
            if let card = currentCard {
                speakCard(card)
            }
        }
    }

    private func speakCard(_ card: Flashcard) {
        SpeechManager.shared.speak(isRevealed ? card.fullGermanTerm : card.word)
    }

    private func importUndoNotice(succeeded: Bool) -> some View {
        HStack(spacing: 12) {
            Label(
                succeeded ? "İçe aktarma geri alındı." : "İçe aktarma geri alınamadı.",
                systemImage: succeeded ? "checkmark.circle.fill" : "xmark.circle.fill"
            )
            .foregroundStyle(succeeded ? AppPalette.success : AppPalette.error)

            Spacer()

            Button {
                importUndoSucceeded = nil
            } label: {
                Image(systemName: "xmark")
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Bildirimi kapat")
            .help("Bildirimi kapat")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(succeeded ? AppPalette.successBadgeBackground : AppPalette.errorBadgeBackground)
    }

    private func resetCardInputState() {
        showStudySummary = false
        isResultFocused = false
        isRevealed = false
        typedArtikel = ""
        guessResult = .none
        focusArtikelInputIfNeeded()
    }

    private func focusArtikelInputIfNeeded() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
            // Only article-less cards have a text field; article cards use the tap buttons.
            if !isRevealed && currentCard?.isArticleless == true && !isPresentingModal,
               let window = keyboardWindow.view?.window, window.attachedSheet == nil,
               NSApp.modalWindow == nil {
                isInputFocused = true
            }
        }
    }

    // MARK: - Study Header

    private var headerBar: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                deckMenu
                queueCounters
                Spacer(minLength: 6)
                streakCounter
                aiButton
                    .buttonStyle(.bordered)
                    .tint(AppPalette.accentPurple)
                    .fixedSize()
                csvButton
                    .buttonStyle(.bordered)
                    .fixedSize()
            }

            HStack(spacing: 10) {
                deckMenu
                Spacer(minLength: 6)
                Button {
                    showStudySummary.toggle()
                } label: {
                    Label("Çalışma özeti", systemImage: "chart.bar")
                }
                .buttonStyle(.bordered)
                .fixedSize()
                .popover(isPresented: $showStudySummary, arrowEdge: .bottom) {
                    VStack(alignment: .leading, spacing: 10) {
                        queueCounters
                        streakCounter
                        Text("En uzun seri: \(store.longestStreakDays) gün")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(14)
                    .onExitCommand { showStudySummary = false }
                }
                .onDisappear { showStudySummary = false }

                Menu {
                    aiButton
                    csvButton
                } label: {
                    Label("Diğer çalışma işlemleri", systemImage: "ellipsis.circle")
                        .labelStyle(.iconOnly)
                }
                .menuStyle(.borderedButton)
                .accessibilityLabel("Diğer çalışma işlemleri")
                .help("Diğer çalışma işlemleri")
                .fixedSize()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(NSColor.controlBackgroundColor))
    }

    private var deckMenu: some View {
        Menu {
            Picker("Çalışma destesi", selection: $store.selectedStudyDeck) {
                Label("Tüm Desteler (\(store.cards.count))", systemImage: deckIcon(for: nil))
                    .tag(nil as String?)

                ForEach(store.allDeckNames, id: \.self) { deck in
                    Label("\(deck) (\(store.cardCount(forDeck: deck)))", systemImage: deckIcon(for: deck))
                        .tag(Optional(deck))
                }
            }
            .pickerStyle(.inline)

            Divider()

            Button {
                showNewDeckSheet = true
            } label: {
                Label("Yeni Deste Ekle…", systemImage: "folder.badge.plus")
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: deckIcon(for: store.selectedStudyDeck))
                    .foregroundStyle(AppPalette.accentBlue)
                    .accessibilityHidden(true)
                Text(store.selectedStudyDeck ?? "Tüm Desteler")
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
            }
            .frame(maxWidth: 150, alignment: .leading)
        }
        .menuStyle(.borderedButton)
        .accessibilityLabel("Çalışma destesi")
        .accessibilityValue(store.selectedStudyDeck ?? "Tüm Desteler")
        .help(store.selectedStudyDeck ?? "Tüm Desteler")
        .fixedSize(horizontal: true, vertical: false)
    }

    private func deckIcon(for deck: String?) -> String {
        if deck == nil {
            return store.selectedStudyDeck == nil ? "tray.full.fill" : "tray.full"
        }
        return store.selectedStudyDeck == deck ? "folder.fill" : "folder"
    }

    private var queueCounters: some View {
        HStack(spacing: 6) {
            CounterPill(title: "Yeni", count: store.availableNewCardsCount,
                        color: AppPalette.accentBlue, backgroundColor: AppPalette.blueBadgeBackground)
            CounterPill(title: "Öğreniliyor", count: store.dueLearningCardsCount,
                        color: AppPalette.accentOrange, backgroundColor: AppPalette.orangeBadgeBackground)
            CounterPill(title: "Tekrar", count: store.dueReviewCardsCount,
                        color: AppPalette.success, backgroundColor: AppPalette.successBadgeBackground)
        }
        .fixedSize(horizontal: true, vertical: false)
    }

    private var streakCounter: some View {
        Label("Seri: \(store.currentStreakDays) gün", systemImage: "flame.fill")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.accentOrange)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(AppPalette.orangeBadgeBackground, in: Capsule())
            .fixedSize()
            .help("Kesintisiz çalışma serisi: \(store.currentStreakDays) gün (En uzun rekor: \(store.longestStreakDays) gün)")
    }

    private var aiButton: some View {
        Button {
            showAIGenerator = true
        } label: {
            Label("AI ile kelime üret", systemImage: "wand.and.stars")
        }
    }

    private var csvButton: some View {
        Button(action: promptAndImportCSV) {
            Label("CSV içe aktar…", systemImage: "square.and.arrow.down")
        }
    }

    // MARK: - Active Flashcard View

    private func centeredStudyContent<Content: View>(
        @ViewBuilder content: @escaping (CGFloat) -> Content
    ) -> some View {
        GeometryReader { geometry in
            ScrollView {
                content(geometry.size.width)
                    .padding(24)
                    .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
        }
    }

    private func activeCardSessionView(card: Flashcard) -> some View {
        centeredStudyContent { width in
            VStack(spacing: 16) {
                VStack(spacing: 16) {
                    cardMetadata(card)

                    if isRevealed {
                        revealedCard(card)
                            .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.96)))
                    } else {
                        questionCard(card)
                    }

                    if !card.isArticleless {
                        HStack(spacing: 16) {
                            ArtikelLegendDot(artikel: .der, label: "der (Eril)")
                            ArtikelLegendDot(artikel: .die, label: "die (Dişil)")
                            ArtikelLegendDot(artikel: .das, label: "das (Nötr)")
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }
                .padding(22)
                .frame(maxWidth: 680, minHeight: 280)
                .background(
                    Color(NSColor.controlBackgroundColor),
                    in: RoundedRectangle(cornerRadius: 22)
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 22)
                        .strokeBorder(Color.primary.opacity(0.12))
                        .allowsHitTesting(false)
                }

                VStack(spacing: 8) {
                    if isRevealed {
                        ratingControls(for: card, availableWidth: width - 48)
                    } else if card.isArticleless {
                        Button {
                            checkAndRevealCard(card)
                        } label: {
                            HStack(spacing: 8) {
                                Label("Kontrol Et", systemImage: "checkmark")
                                    .font(.headline)
                                Text("(Enter)")
                                    .font(.subheadline)
                            }
                            .foregroundStyle(AppPalette.onBlue)
                            .padding(.horizontal, 24)
                            .frame(minHeight: 32)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppPalette.accentBlue)
                        .controlSize(.large)
                        .keyboardShortcut(.defaultAction)
                        .disabled(isPresentingModal || showStudySummary)
                        .accessibilityLabel("Kontrol Et")
                    } else {
                        Text("Bir artikel seçin; cevap hemen açılır.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(minHeight: 82)
            }
        }
    }

    private func cardMetadata(_ card: Flashcard) -> some View {
        HStack(spacing: 8) {
            Text(card.stage.title.uppercased())
                .font(.caption2.weight(.bold))
                .foregroundStyle(card.stage.color)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(card.stage.badgeBackground, in: Capsule())
                .fixedSize()

            Label(card.deckName, systemImage: deckIcon(for: card.deckName))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .help(card.deckName)

            if card.totalReviews > 0 {
                Text("Kolaylık: \(Int(card.easeFactor * 100))% • Tekrar: \(card.totalReviews)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .help("Kolaylık, kartın sonraki tekrar aralığını hesaplamak için kullanılır.")
            }

            Spacer(minLength: 4)

            ListenButton {
                speakCard(card)
            }
            .fixedSize()
        }
    }

    private func questionCard(_ card: Flashcard) -> some View {
        VStack(spacing: 16) {
            Text(card.isArticleless
                 ? "Kelimenin Türkçe anlamını yazıp Enter'a basın:"
                 : "Doğru artikeli seçin:")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            if card.isArticleless {
                studyWord(card.word)
                    .multilineTextAlignment(.center)
                answerField(card)
            } else {
                // Article cards reveal on tap: one tap is the whole answer, no "Kontrol Et" step.
                HStack(spacing: 12) {
                    ForEach(Artikel.allCases) { art in
                        Button {
                            selectArtikelAndReveal(art, card: card)
                        } label: {
                            Text(art.rawValue)
                        }
                        .buttonStyle(ArticleGuessButtonStyle(color: art.color))
                        .accessibilityLabel(art.rawValue)
                        .accessibilityHint("Artikel olarak seç ve cevabı göster")
                    }
                }
                studyWord(card.word)
                    .multilineTextAlignment(.center)
            }

            if store.settings.showMeaningOnFront && !card.meaning.isEmpty && !card.isArticleless {
                Text(card.meaning)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func answerField(_ card: Flashcard) -> some View {
        TextField("anlam...", text: $typedArtikel)
            .textFieldStyle(.plain)
            .font(.system(size: 28, weight: .bold, design: .rounded))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 8)
            .frame(maxWidth: 240, minHeight: 54)
            .background(Color(NSColor.textBackgroundColor), in: RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        isInputFocused ? AppPalette.accentBlue : AppPalette.controlOutline,
                        lineWidth: isInputFocused ? 2.5 : 1
                    )
                    .allowsHitTesting(false)
            }
            .accessibilityLabel("Türkçe anlam")
            .focused($isInputFocused)
            .onSubmit {
                checkAndRevealCard(card)
            }
    }

    private func studyWord(_ word: String) -> some View {
        Text(word)
            .font(.system(size: 42, weight: .bold, design: .rounded))
            .foregroundStyle(.primary)
            .fixedSize(horizontal: false, vertical: true)
            .textSelection(.enabled)
    }

    private func revealedCard(_ card: Flashcard) -> some View {
        VStack(spacing: 14) {
            resultBanner(card)

            HStack(alignment: .firstTextBaseline, spacing: 12) {
                if let artikel = card.artikel {
                    Text(artikel.rawValue)
                        .font(.system(size: 44, weight: .heavy, design: .rounded))
                        .foregroundStyle(artikel.color)
                        .fixedSize()
                        .textSelection(.enabled)
                }
                studyWord(card.word)
            }
            .multilineTextAlignment(.center)

            if !card.meaning.isEmpty {
                Divider()
                    .frame(width: 160)
                    .accessibilityHidden(true)
                Text(card.meaning)
                    .font(.system(size: 21, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func resultBanner(_ card: Flashcard) -> some View {
        let isCorrect = guessResult == .correct
        let answer = typedArtikel.trimmingCharacters(in: .whitespacesAndNewlines)
        let status: String
        if isCorrect {
            status = card.artikel.map { "DOĞRU! (\($0.grammaticalLabel))" } ?? "DOĞRU!"
        } else {
            status = answer.isEmpty ? "BOŞ BIRAKILDI" : "YANLIŞ (\(answer))"
        }
        let expectedAnswer = AnswerChecker.expectedLabel(card: card)
        let answerLabel = card.isArticleless ? "Doğru Anlam: \(expectedAnswer)" : "Doğru Artikel: \(expectedAnswer)"

        return HStack(alignment: .top, spacing: 8) {
            Image(systemName: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(status)
                if !isCorrect {
                    Text(answerLabel)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .font(.subheadline.weight(.bold))
        .foregroundStyle(isCorrect ? AppPalette.feedbackCorrect : AppPalette.feedbackIncorrect)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            isCorrect ? AppPalette.successBadgeBackground : AppPalette.errorBadgeBackground,
            in: RoundedRectangle(cornerRadius: 12)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(status). \(answerLabel)")
        .accessibilityAddTraits(.isHeader)
        .accessibilityFocused($isResultFocused)
        .task {
            await Task.yield()
            guard !Task.isCancelled, isRevealed, currentCard?.id == card.id else { return }
            isResultFocused = true
        }
    }

    private func ratingControls(for card: Flashcard, availableWidth: CGFloat) -> some View {
        let columnCount = guessResult == .wrong || availableWidth < 660 ? 2 : 4
        let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: columnCount)

        return VStack(spacing: 8) {
            if guessResult == .wrong {
                Text("Yanlış cevaplandığı için sadece ‘Tekrar (1)’ veya ‘Zor (2)’ seçilebilir:")
                    .foregroundStyle(AppPalette.feedbackIncorrect)
            } else {
                Text("Hatırlama zorluğunu seçin (Klavye: 1–4):")
                    .foregroundStyle(.secondary)
            }

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(availableRatingsForCurrentState, id: \.rawValue) { rating in
                    RatingButton(
                        rating: rating,
                        intervalText: SM2Scheduler.previewInterval(for: card, rating: rating)
                    ) {
                        submitRating(card: card, rating: rating)
                    }
                }
            }
            .frame(maxWidth: guessResult == .wrong ? 340 : 640)
        }
        .font(.caption)
        .multilineTextAlignment(.center)
    }

    // MARK: - Waiting for Learning Timer View

    private func waitingForLearningCardView(pendingCard: Flashcard) -> some View {
        centeredStudyContent { _ in
            VStack(spacing: 18) {
                Image(systemName: "timer")
                    .font(.system(size: 52))
                    .foregroundStyle(AppPalette.accentOrange)
                    .accessibilityHidden(true)

                Text("Öğrenme Kartı Zamanlayıcısı")
                    .font(.title2.bold())

                Text("Sıradaki tekrar kelimesi vaat edilen süre dolduğunda otomatik olarak ekrana gelecektir:")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 460)

                LearningCountdown(dueDate: pendingCard.dueDate, currentTime: store.currentTime)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) { waitingActions }
                    VStack(spacing: 12) { waitingActions }
                }
                .controlSize(.large)
            }
            .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private var waitingActions: some View {
        Button {
            store.forcePullNextLearningCard()
        } label: {
            Label("Süreyi Beklemeden Şimdi Çalış", systemImage: "bolt.fill")
                .foregroundStyle(AppPalette.onOrange)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppPalette.accentOrange)

        if store.filteredPoolCards.contains(where: { $0.stage == .new }) && store.remainingNewToday == 0 {
            increaseLimitButton()
                .buttonStyle(.bordered)
        }
    }

    private func increaseLimitButton(prominent: Bool = false) -> some View {
        Button {
            store.settings.dailyNewCardLimit += 10
            store.saveSettings()
        } label: {
            Label("Günlük yeni kart sınırını 10 artır", systemImage: "plus.circle")
                .foregroundStyle(prominent ? AppPalette.onBlue : Color.primary)
        }
    }

    // MARK: - Empty & Completed Views

    private var emptyDatabaseView: some View {
        centeredStudyContent { _ in
            VStack(spacing: 18) {
                BloodyBrainLogoView(size: 100, animated: false)
                    .accessibilityHidden(true)

                Text("Henüz Kelime Eklenmedi")
                    .font(.title2.bold())

                Text("Örnek kelimelerle başlayabilir, CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 480)

                Button {
                    let result = store.loadStarterGermanPack()
                    showImportResult(
                        title: "Örnek kelimeler eklendi",
                        message: "Yeni eklenen kelime: \(result.addedCount)",
                        canUndo: store.canUndoImport
                    )
                } label: {
                    Label("Örnek 30 Kelime", systemImage: "books.vertical")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppPalette.accentBlue)
                .controlSize(.large)

                secondaryStudyActions

                Button {
                    store.openStorageDirectoryInFinder()
                } label: {
                    Label("Yerel Depolama Klasörünü Finder'da Göster", systemImage: "folder")
                        .font(.caption)
                }
                .buttonStyle(.link)
            }
            .multilineTextAlignment(.center)
        }
    }

    private var secondaryStudyActions: some View {
        HStack(spacing: 12) {
            aiButton
                .tint(AppPalette.accentPurple)
            csvButton
        }
        .buttonStyle(.bordered)
    }

    private var sessionCompletedView: some View {
        centeredStudyContent { _ in
            let hasNewCards = store.filteredPoolCards.contains(where: { $0.stage == .new })

            VStack(spacing: 18) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(AppPalette.success)
                    .accessibilityHidden(true)

                Text(store.selectedStudyDeck.map { "'\($0)' Destesi Tamamlandı!" } ?? "Bugünkü Çalışma Tamamlandı!")
                    .font(.title2.bold())

                Text("Günlük hedeflerinizi ve bekleyen tekrarlarınızı tamamladınız. Kartlarınız hatırlama durumunuza göre sonraki günlere planlandı.")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 480)

                HStack(spacing: 12) {
                    StatMiniBox(title: "Bugün öğrenilen (tüm desteler)", value: "\(store.dailyProgress.newCardsStudied)",
                                color: AppPalette.accentBlue, backgroundColor: AppPalette.blueBadgeBackground)
                    StatMiniBox(title: "Bugün tekrar edilen (tüm desteler)", value: "\(store.dailyProgress.reviewsCompleted)",
                                color: AppPalette.success, backgroundColor: AppPalette.successBadgeBackground)
                    StatMiniBox(title: store.selectedStudyDeck == nil ? "Toplam kelime" : "Destedeki kelime",
                                value: "\(store.filteredPoolCards.count)", color: AppPalette.accentPurple,
                                backgroundColor: AppPalette.purpleBadgeBackground)
                }
                .frame(maxWidth: 500)
                .accessibilityElement(children: .contain)
                .accessibilityLabel("Çalışma özeti")

                if hasNewCards {
                    increaseLimitButton(prominent: true)
                        .buttonStyle(.borderedProminent)
                        .tint(AppPalette.accentBlue)
                }

                secondaryStudyActions
            }
            .multilineTextAlignment(.center)
        }
    }

    // MARK: - Actions & Keyboard Shortcuts

    private func selectArtikelAndReveal(_ artikel: Artikel, card: Flashcard) {
        checkAndRevealCard(card, answer: artikel.rawValue)
    }

    /// [answer] lets the article buttons pass their value directly. Reading `typedArtikel` right
    /// after setting it would race the @State update and could grade the previous answer.
    private func checkAndRevealCard(_ card: Flashcard, answer: String? = nil) {
        guard !isRevealed else { return }
        let value = answer ?? typedArtikel
        if let answer {
            typedArtikel = answer
        }
        showStudySummary = false
        isInputFocused = false

        if AnswerChecker.isCorrect(card: card, typed: value) {
            guessResult = .correct
        } else {
            guessResult = .wrong
        }

        withAnimation(.easeInOut(duration: 0.18)) {
            isRevealed = true
        }
        if let window = keyboardWindow.view?.window,
           window.firstResponder is NSTextView || window.firstResponder is NSTextField {
            window.makeFirstResponder(nil)
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
        importUndoSucceeded = nil
        isChoosingCSV = true
        defer { isChoosingCSV = false }

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
                let message = """
                Yeni eklenen kelime: \(res.addedCount)
                Güncellenen kelime: \(res.updatedCount)
                Zaten mevcut (atlanan): \(res.skippedCount)
                Geçersiz/Atlanan satır: \(res.invalidLines)
                """
                showImportResult(title: "CSV içe aktarıldı", message: message, canUndo: store.canUndoImport)
            } catch {
                showImportResult(
                    title: "CSV okunamadı",
                    message: "Dosyanın CSV biçiminde olduğunu ve okunabildiğini kontrol edip tekrar deneyin.\n\nAyrıntı: \(error.localizedDescription)"
                )
            }
        }
    }

    private func showImportResult(title: String, message: String, canUndo: Bool = false) {
        importTitle = title
        importMessage = message
        importUndoSucceeded = nil
        offerImportUndo = canUndo
        showImportAlert = true
    }

    private func setupKeyboardShortcuts() {
        removeKeyboardShortcuts()
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            let commandModifiers: NSEvent.ModifierFlags = [.command, .option, .control, .shift]
            guard let window = keyboardWindow.view?.window, event.window === window, window.isKeyWindow,
                  window.attachedSheet == nil, NSApp.modalWindow == nil, !isPresentingModal,
                  event.modifierFlags.intersection(commandModifiers).isEmpty else { return event }

            if showStudySummary && event.keyCode == 53 {
                showStudySummary = false
                return nil
            }
            guard !showStudySummary,
                  !(window.firstResponder is NSTextView), !(window.firstResponder is NSTextField),
                  isRevealed, let card = currentCard else { return event }

            // Return is handled by the answer field and the default button on the front of the card.
            let key = event.charactersIgnoringModifiers?.lowercased() ?? ""
            if key == "s" {
                if !event.isARepeat {
                    speakCard(card)
                }
                return nil
            }
            guard key.count == 1, let number = Int(key), let rating = ReviewRating(rawValue: number),
                  availableRatingsForCurrentState.contains(rating) else { return event }

            if !event.isARepeat {
                submitRating(card: card, rating: rating)
            }
            return nil
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

/// Article answer button: tinted outline at rest, solid on press with a restrained 3% scale
/// (the reveal itself is the main animation).
private struct ArticleGuessButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 21, weight: .bold, design: .rounded))
            .foregroundStyle(configuration.isPressed ? Color.white : color)
            .frame(width: 104, height: 46)
            .background(configuration.isPressed ? color : color.opacity(0.12))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(color.opacity(0.55), lineWidth: 1.5)
                    .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct StudyKeyboardScope: NSViewRepresentable {
    final class WindowReference {
        weak var view: NSView?
    }

    let reference: WindowReference

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        reference.view = view
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

private struct LearningCountdown: View {
    let dueDate: Date
    let currentTime: Date

    var body: some View {
        let remainingSeconds = max(0, Int(ceil(dueDate.timeIntervalSince(currentTime))))
        let minutes = remainingSeconds / 60
        let seconds = remainingSeconds % 60

        Text(String(format: "%02d:%02d", minutes, seconds))
            .font(.system(size: 44, weight: .heavy, design: .monospaced))
            .foregroundStyle(AppPalette.accentOrange)
            .padding(.horizontal, 24)
            .padding(.vertical, 10)
            .background(AppPalette.orangeBadgeBackground, in: RoundedRectangle(cornerRadius: 16))
            .accessibilityLabel("Kalan süre: \(minutes) dakika \(seconds) saniye")
    }
}

private struct ListenButton: View {
    let action: () -> Void
    @State private var isHovered = false
    @FocusState private var isFocused: Bool

    var body: some View {
        Button(action: action) {
            Label {
                Text("Dinle")
                    .font(.caption.weight(.medium))
            } icon: {
                Image(systemName: "speaker.wave.2")
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .foregroundStyle(isHovered || isFocused ? AppPalette.accentBlue : Color.primary)
            .background(
                isHovered || isFocused ? AppPalette.blueBadgeBackground : Color.secondary.opacity(0.12),
                in: Capsule()
            )
            .overlay {
                Capsule()
                    .strokeBorder(isFocused ? AppPalette.accentBlue : .clear)
                    .allowsHitTesting(false)
            }
        }
        .buttonStyle(.plain)
        .focused($isFocused)
        .onHover { isHovered = $0 }
        .accessibilityLabel("Almanca telaffuzu dinle")
        .help("Almanca telaffuzu dinle")
    }
}

private struct CounterPill: View {
    let title: String
    let count: Int
    let color: Color
    let backgroundColor: Color

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
                .accessibilityHidden(true)
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
        .background(backgroundColor, in: Capsule())
        .fixedSize(horizontal: true, vertical: false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title): \(count)")
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
                .accessibilityHidden(true)
            Text(label)
                .lineLimit(1)
        }
    }
}

private struct RatingButton: View {
    let rating: ReviewRating
    let intervalText: String
    let action: () -> Void
    @State private var isHovered = false
    @FocusState private var isFocused: Bool

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(intervalText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(rating.color)
                    .fixedSize(horizontal: false, vertical: true)

                Text("\(rating.title) (\(rating.shortcutKey))")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppPalette.onRating(rating))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background(rating.color, in: RoundedRectangle(cornerRadius: 9))
            }
            .padding(4)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(isHovered || isFocused ? rating.badgeBackground : .clear)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isFocused ? AppPalette.controlOutline : .clear, lineWidth: 2)
                    .allowsHitTesting(false)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .focused($isFocused)
        .onHover { isHovered = $0 }
        .accessibilityLabel("\(rating.title), \(intervalText)")
    }
}

private struct StatMiniBox: View {
    let title: String
    let value: String
    let color: Color
    let backgroundColor: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundStyle(color)
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(8)
        .frame(maxWidth: .infinity, minHeight: 66)
        .background(backgroundColor, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title): \(value)")
    }
}
