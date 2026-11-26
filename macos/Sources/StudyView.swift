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
                    L10n.studyEmptyDeckTitle,
                    systemImage: "folder",
                    description: Text(L10n.studyEmptyDeckDesc)
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
                Button(L10n.undo, role: .destructive) {
                    importUndoSucceeded = store.undoLastImport()
                    offerImportUndo = false
                }
            }
            Button(L10n.ok, role: .cancel) {
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
                succeeded
                    ? L10n.text("İçe aktarma geri alındı.", en: "Import undone.", de: "Import rückgängig gemacht.")
                    : L10n.text("İçe aktarma geri alınamadı.", en: "Could not undo import.", de: "Import konnte nicht rückgängig gemacht werden."),
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
            .accessibilityLabel(L10n.text("Bildirimi kapat", en: "Dismiss notice", de: "Hinweis schließen"))
            .help(L10n.text("Bildirimi kapat", en: "Dismiss notice", de: "Hinweis schließen"))
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
                    Label(L10n.studySummaryLabel, systemImage: "chart.bar")
                }
                .buttonStyle(.bordered)
                .fixedSize()
                .popover(isPresented: $showStudySummary, arrowEdge: .bottom) {
                    VStack(alignment: .leading, spacing: 10) {
                        queueCounters
                        streakCounter
                        Text(L10n.longestStreakLabel(store.longestStreakDays))
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
                    Label(L10n.studyOtherActions, systemImage: "ellipsis.circle")
                        .labelStyle(.iconOnly)
                }
                .menuStyle(.borderedButton)
                .accessibilityLabel(L10n.studyOtherActions)
                .help(L10n.studyOtherActions)
                .fixedSize()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }

    private var deckMenu: some View {
        Menu {
            Picker(L10n.studyDeckLabel, selection: $store.selectedStudyDeck) {
                Label("\(L10n.allDecks) (\(store.cards.count))", systemImage: deckIcon(for: nil))
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
                Label(L10n.newDeckAddEllipsis, systemImage: "folder.badge.plus")
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: deckIcon(for: store.selectedStudyDeck))
                    .foregroundStyle(AppPalette.accentBlue)
                    .accessibilityHidden(true)
                Text(store.selectedStudyDeck ?? L10n.allDecks)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
            }
            .frame(maxWidth: 150, alignment: .leading)
        }
        .menuStyle(.borderedButton)
        .accessibilityLabel(L10n.studyDeckLabel)
        .accessibilityValue(store.selectedStudyDeck ?? L10n.allDecks)
        .help(store.selectedStudyDeck ?? L10n.allDecks)
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
            CounterPill(title: CardStage.new.title, count: store.availableNewCardsCount,
                        color: AppPalette.accentBlue, backgroundColor: AppPalette.blueBadgeBackground)
            CounterPill(title: CardStage.learning.title, count: store.dueLearningCardsCount,
                        color: AppPalette.accentOrange, backgroundColor: AppPalette.orangeBadgeBackground)
            CounterPill(title: CardStage.review.title, count: store.dueReviewCardsCount,
                        color: AppPalette.success, backgroundColor: AppPalette.successBadgeBackground)
        }
        .fixedSize(horizontal: true, vertical: false)
    }

    private var streakCounter: some View {
        Label(L10n.streakLabel(store.currentStreakDays), systemImage: "flame.fill")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.accentOrange)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(AppPalette.orangeBadgeBackground, in: Capsule())
            .fixedSize()
            .help("\(L10n.streakLabel(store.currentStreakDays)) (\(L10n.longestStreakLabel(store.longestStreakDays)))")
    }

    private var aiButton: some View {
        Button {
            showAIGenerator = true
        } label: {
            Label(L10n.aiGenerateButton, systemImage: "wand.and.stars")
        }
    }

    private var csvButton: some View {
        Button(action: promptAndImportCSV) {
            Label(L10n.csvImportEllipsis, systemImage: "square.and.arrow.down")
        }
    }

    // MARK: - Active Flashcard View

    private func centeredStudyContent<Content: View>(
        @ViewBuilder content: @escaping (CGFloat) -> Content
    ) -> some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    content(geometry.size.width)
                    Spacer(minLength: 0)
                }
                .padding(24)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
        }
    }

    private func activeCardSessionView(card: Flashcard) -> some View {
        centeredStudyContent { width in
            let cardShape = RoundedRectangle(cornerRadius: 24, style: .continuous)

            VStack(spacing: 20) {
                VStack(spacing: 0) {
                    cardMetadata(card)

                    Spacer(minLength: 16)

                    if isRevealed {
                        revealedCard(card)
                            .transition(
                                reduceMotion
                                    ? .opacity
                                    : .opacity.combined(with: .scale(scale: 0.96))
                            )
                    } else {
                        questionCard(card)
                    }

                    Spacer(minLength: 16)
                }
                .padding(24)
                .frame(maxWidth: 660, minHeight: 290)
                .background(.ultraThinMaterial, in: cardShape)
                .overlay {
                    cardShape
                        .strokeBorder(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.28),
                                    Color.primary.opacity(0.10),
                                    Color.primary.opacity(0.04)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                        .allowsHitTesting(false)
                }
                .shadow(color: Color.black.opacity(0.08), radius: 18, x: 0, y: 8)

                VStack(spacing: 10) {
                    if isRevealed {
                        ratingControls(for: card, availableWidth: width - 48)
                            .transition(
                                reduceMotion
                                    ? .opacity
                                    : .opacity.combined(with: .move(edge: .bottom))
                            )
                    } else if card.isArticleless {
                        Button {
                            checkAndRevealCard(card)
                        } label: {
                            HStack(spacing: 8) {
                                Label(L10n.studyCheck(), systemImage: "checkmark")
                                    .font(.headline)
                                Text("(Enter)")
                                    .font(.subheadline)
                            }
                            .foregroundStyle(AppPalette.onBlue)
                            .padding(.horizontal, 24)
                            .frame(minHeight: 34)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppPalette.accentBlue)
                        .controlSize(.large)
                        .keyboardShortcut(.defaultAction)
                        .disabled(isPresentingModal || showStudySummary)
                        .accessibilityLabel(L10n.studyCheck())
                    } else {
                        // Article cards reveal on tap: one tap is the whole answer, no "Kontrol Et" step.
                        HStack(spacing: 14) {
                            ForEach(Artikel.allCases) { art in
                                ArticleGuessButton(artikel: art) {
                                    selectArtikelAndReveal(art, card: card)
                                }
                            }
                        }
                    }
                }
                .frame(minHeight: 84, alignment: .top)
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
                Text(L10n.text(
                    "Kolaylık: \(Int(card.easeFactor * 100))% • Tekrar: \(card.totalReviews)",
                    en: "Ease: \(Int(card.easeFactor * 100))% • Reviews: \(card.totalReviews)",
                    de: "Leichtigkeit: \(Int(card.easeFactor * 100))% • Wdh.: \(card.totalReviews)"
                ))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .help(L10n.text(
                        "Kolaylık, kartın sonraki tekrar aralığını hesaplamak için kullanılır.",
                        en: "Ease is used to calculate the card's next review interval.",
                        de: "Die Leichtigkeit bestimmt das nächste Wiederholungsintervall der Karte."
                    ))
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
            studyWord(card.word)
                .multilineTextAlignment(.center)

            if card.isArticleless {
                answerField(card)
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
        TextField(L10n.meaningPlaceholder, text: $typedArtikel)
            .textFieldStyle(.plain)
            .font(.system(size: 26, weight: .bold, design: .rounded))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 12)
            .frame(maxWidth: 260, minHeight: 52)
            .background(
                Color(NSColor.textBackgroundColor).opacity(0.85),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(
                        isInputFocused ? AppPalette.accentBlue : AppPalette.controlOutline.opacity(0.6),
                        lineWidth: isInputFocused ? 2.5 : 1
                    )
                    .allowsHitTesting(false)
            }
            .accessibilityLabel(L10n.meaningLabel)
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
            status = card.artikel.map { "\(L10n.correctBanner) (\($0.grammaticalLabel))" } ?? L10n.correctBanner
        } else {
            status = answer.isEmpty ? L10n.blankBanner : L10n.wrongBanner(answer)
        }
        let expectedAnswer = AnswerChecker.expectedLabel(card: card)
        let answerLabel = card.isArticleless ? L10n.expectedMeaning(expectedAnswer) : L10n.expectedArtikel(expectedAnswer)

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
                Text(L10n.wrongRatingHint)
                    .foregroundStyle(AppPalette.feedbackIncorrect)
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

                Text(L10n.waitingTimerTitle)
                    .font(.title2.bold())

                Text(L10n.waitingTimerDesc)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 460)

                LearningCountdown(dueDate: pendingCard.dueDate, currentTime: store.currentTime)

                if store.filteredPoolCards.contains(where: { $0.stage == .new }) && store.remainingNewToday == 0 {
                    increaseLimitButton()
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                }
            }
            .multilineTextAlignment(.center)
        }
    }

    private func increaseLimitButton(prominent: Bool = false) -> some View {
        Button {
            store.settings.dailyNewCardLimit += 10
            store.saveSettings()
        } label: {
            Label(L10n.increaseLimitButton, systemImage: "plus.circle")
                .foregroundStyle(prominent ? AppPalette.onBlue : Color.primary)
        }
    }

    // MARK: - Empty & Completed Views

    private var emptyDatabaseView: some View {
        centeredStudyContent { _ in
            VStack(spacing: 18) {
                BloodyBrainLogoView(size: 100, animated: false)
                    .accessibilityHidden(true)

                Text(L10n.emptyDbTitle)
                    .font(.title2.bold())

                Text(L10n.emptyDbDesc)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 480)

                Button {
                    let result = store.loadStarterGermanPack()
                    showImportResult(
                        title: L10n.text("Örnek kelimeler eklendi", en: "Sample words added", de: "Beispielwörter hinzugefügt"),
                        message: L10n.text("Yeni eklenen kelime: \(result.addedCount)", en: "New words added: \(result.addedCount)", de: "Neue Wörter hinzugefügt: \(result.addedCount)"),
                        canUndo: store.canUndoImport
                    )
                } label: {
                    Label(L10n.starterPackButton, systemImage: "books.vertical")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppPalette.accentBlue)
                .controlSize(.large)

                secondaryStudyActions

                Button {
                    store.openStorageDirectoryInFinder()
                } label: {
                    Label(L10n.showStorageInFinder, systemImage: "folder")
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

                Text(L10n.deckCompletedTitle(store.selectedStudyDeck))
                    .font(.title2.bold())

                Text(L10n.completedDesc)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 480)

                HStack(spacing: 12) {
                    StatMiniBox(title: L10n.text("Bugün öğrenilen (tüm desteler)", en: "Learned today (all decks)", de: "Heute gelernt (alle Stapel)"), value: "\(store.dailyProgress.newCardsStudied)",
                                color: AppPalette.accentBlue, backgroundColor: AppPalette.blueBadgeBackground)
                    StatMiniBox(title: L10n.text("Bugün tekrar edilen (tüm desteler)", en: "Reviewed today (all decks)", de: "Heute wiederholt (alle Stapel)"), value: "\(store.dailyProgress.reviewsCompleted)",
                                color: AppPalette.success, backgroundColor: AppPalette.successBadgeBackground)
                    StatMiniBox(title: store.selectedStudyDeck == nil ? L10n.text("Toplam kelime", en: "Total words", de: "Wörter gesamt") : L10n.text("Destedeki kelime", en: "Words in deck", de: "Wörter im Stapel"),
                                value: "\(store.filteredPoolCards.count)", color: AppPalette.accentPurple,
                                backgroundColor: AppPalette.purpleBadgeBackground)
                }
                .frame(maxWidth: 500)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(L10n.studySummaryLabel)

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

        withAnimation(reduceMotion ? nil : .spring(response: 0.30, dampingFraction: 0.82)) {
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
        panel.title = L10n.text("Almanca Kelime CSV Dosyası Seçin", en: "Select German Vocabulary CSV File", de: "Deutsche Vokabel-CSV-Datei auswählen")
        panel.message = L10n.text(
            "1. Sütun: Artikel (der/die/das), 2. Sütun: Kelime, 3. Sütun: Anlam, 4. Sütun (İsteğe Bağlı): Deste",
            en: "Column 1: Article (der/die/das), Column 2: Word, Column 3: Meaning, Column 4 (Optional): Deck",
            de: "Spalte 1: Artikel (der/die/das), Spalte 2: Wort, Spalte 3: Bedeutung, Spalte 4 (Optional): Stapel"
        )
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .tabSeparatedText]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let targetDeck = store.selectedStudyDeck ?? "Genel"
                let res = try store.importCSV(from: url, defaultDeckName: targetDeck)
                let message = L10n.text(
                    """
                    Yeni eklenen kelime: \(res.addedCount)
                    Güncellenen kelime: \(res.updatedCount)
                    Zaten mevcut (atlanan): \(res.skippedCount)
                    Geçersiz/Atlanan satır: \(res.invalidLines)
                    """,
                    en: """
                    New words added: \(res.addedCount)
                    Words updated: \(res.updatedCount)
                    Already existing (skipped): \(res.skippedCount)
                    Invalid/skipped rows: \(res.invalidLines)
                    """,
                    de: """
                    Neu hinzugefügte Wörter: \(res.addedCount)
                    Aktualisierte Wörter: \(res.updatedCount)
                    Bereits vorhanden (übersprungen): \(res.skippedCount)
                    Ungültige/übersprungene Zeilen: \(res.invalidLines)
                    """
                )
                showImportResult(title: L10n.text("CSV içe aktarıldı", en: "CSV imported", de: "CSV importiert"), message: message, canUndo: store.canUndoImport)
            } catch {
                showImportResult(
                    title: L10n.text("CSV okunamadı", en: "Could not read CSV", de: "CSV konnte nicht gelesen werden"),
                    message: L10n.text(
                        "Dosyanın CSV biçiminde olduğunu ve okunabildiğini kontrol edip tekrar deneyin.\n\nAyrıntı: \(error.localizedDescription)",
                        en: "Check that the file is in CSV format and readable, then try again.\n\nDetails: \(error.localizedDescription)",
                        de: "Prüfe, ob die Datei im CSV-Format vorliegt und lesbar ist, und versuche es erneut.\n\nDetails: \(error.localizedDescription)"
                    )
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

private struct ArticleGuessButton: View {
    let artikel: Artikel
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Text(artikel.rawValue)
        }
        .buttonStyle(
            ArticleGuessButtonStyle(
                color: artikel.color,
                badgeBackground: artikel.badgeBackground,
                isHovered: isHovered
            )
        )
        .onHover { isHovered = $0 }
        .accessibilityLabel(artikel.rawValue)
        .accessibilityHint(L10n.text("Artikel olarak seç ve cevabı göster", en: "Select as article and reveal answer", de: "Als Artikel wählen und Antwort zeigen"))
    }
}

/// Article answer button: translucent tinted glass at rest, solid on press with a subtle
/// spring scale on hover/press.
private struct ArticleGuessButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let color: Color
    let badgeBackground: Color
    let isHovered: Bool

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        let isPressed = configuration.isPressed

        configuration.label
            .font(.system(size: 21, weight: .bold, design: .rounded))
            .foregroundStyle(isPressed ? AppPalette.onAccent : color)
            .frame(minWidth: 116, minHeight: 50)
            .background {
                if isPressed {
                    shape.fill(color)
                } else {
                    shape
                        .fill(.ultraThinMaterial)
                        .overlay {
                            shape.fill(badgeBackground.opacity(isHovered ? 0.85 : 0.55))
                        }
                }
            }
            .overlay {
                shape
                    .strokeBorder(
                        LinearGradient(
                            colors: [
                                color.opacity(isPressed ? 0.9 : (isHovered ? 0.75 : 0.45)),
                                color.opacity(isPressed ? 0.7 : (isHovered ? 0.35 : 0.18))
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: isHovered || isPressed ? 1.5 : 1
                    )
                    .allowsHitTesting(false)
            }
            .shadow(
                color: color.opacity(isHovered && !isPressed ? 0.18 : 0.05),
                radius: isHovered ? 10 : 4,
                x: 0,
                y: isHovered ? 4 : 2
            )
            .scaleEffect(
                reduceMotion ? 1 : (isPressed ? 0.96 : (isHovered ? 1.02 : 1))
            )
            .animation(
                reduceMotion ? nil : .spring(response: 0.22, dampingFraction: 0.75),
                value: isPressed
            )
            .animation(
                reduceMotion ? nil : .easeOut(duration: 0.15),
                value: isHovered
            )
            .contentShape(shape)
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
            .accessibilityLabel(L10n.text(
                "Kalan süre: \(minutes) dakika \(seconds) saniye",
                en: "Time remaining: \(minutes) minutes \(seconds) seconds",
                de: "Verbleibende Zeit: \(minutes) Minuten \(seconds) Sekunden"
            ))
    }
}

private struct ListenButton: View {
    let action: () -> Void
    @State private var isHovered = false
    @FocusState private var isFocused: Bool

    var body: some View {
        Button(action: action) {
            Label {
                Text(L10n.listenLabel)
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
        .accessibilityLabel(L10n.listenHelp)
        .help(L10n.listenHelp)
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

private struct RatingButton: View {
    @Environment(\.colorScheme) private var colorScheme
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
                    .foregroundStyle(colorScheme == .light ? rating.color : AppPalette.onRating(rating))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background {
                        if colorScheme == .light {
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .fill(Color.white.opacity(0.86))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                                        .fill(rating.color.opacity(isHovered ? 0.18 : 0.12))
                                }
                                .overlay {
                                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                                        .strokeBorder(rating.color.opacity(isHovered ? 0.42 : 0.25), lineWidth: 1)
                                }
                                .shadow(color: rating.color.opacity(isHovered ? 0.10 : 0.04), radius: isHovered ? 5 : 2, x: 0, y: 1)
                        } else {
                            RoundedRectangle(cornerRadius: 9)
                                .fill(rating.color)
                        }
                    }
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
