import SwiftUI
import AppKit
import Accessibility
import UniformTypeIdentifiers

struct WordListView: View {
    @EnvironmentObject var store: DeckStore
    @State private var searchText: String = ""
    @State private var selectedDeckFilter: String? = nil
    /// nil = all; "der"/"die"/"das"; ArtikelFilter.none ("-") = article-less words
    @State private var selectedArtikelFilter: String? = nil
    @State private var selectedStageFilter: CardStage? = nil

    // Manual new word form state
    @State private var newArtikel: Artikel? = .der
    @State private var newWord: String = ""
    @State private var newMeaning: String = ""
    @State private var newDeckName: String = "Genel"

    // Editing word & deck creation state
    @State private var editingCard: Flashcard? = nil
    @State private var showNewDeckSheet: Bool = false
    @State private var deckToRename: String? = nil

    @State private var alertTitle = ""
    @State private var alertMessage: String? = nil
    @State private var showAlert: Bool = false
    /// True only while the alert shows a successful CSV import result that can still be undone.
    @State private var offerImportUndo: Bool = false
    @State private var showAIGenerator: Bool = false

    @State private var cardToDelete: Flashcard?
    @State private var showDeleteConfirmation = false
    @State private var showingCSVPanel = false
    @State private var feedback: InlineFeedback?
    @FocusState private var focusedField: Field?

    private enum Field: Hashable {
        case search, newWord
    }

    private struct InlineFeedback: Identifiable {
        let id = UUID()
        let message: String
        let isDuplicate: Bool
    }

    private var hasSearchQuery: Bool {
        !searchText.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var hasContentFilters: Bool {
        hasSearchQuery || selectedArtikelFilter != nil || selectedStageFilter != nil
    }

    private var isPresentingModal: Bool {
        showAIGenerator || editingCard != nil || showNewDeckSheet || deckToRename != nil
            || showAlert || showDeleteConfirmation || showingCSVPanel
    }

    private var filteredCards: [Flashcard] {
        let query = hasSearchQuery ? searchText.lowercased() : ""
        return store.cards.filter { card in
            if let deckFilter = selectedDeckFilter, card.deckName != deckFilter {
                return false
            }
            if !ArtikelFilter.matches(card, filter: selectedArtikelFilter) {
                return false
            }
            if let stageFilter = selectedStageFilter, card.stage != stageFilter {
                return false
            }
            return query.isEmpty
                || card.word.lowercased().contains(query)
                || card.meaning.lowercased().contains(query)
                || card.displayArtikel.lowercased().contains(query)
                || card.deckName.lowercased().contains(query)
        }
    }

    var body: some View {
        let cards = filteredCards

        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 12) {
                header(resultCount: cards.count)
                quickEntry
                deckFilters
                searchFilters
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(nsColor: .controlBackgroundColor))

            Divider()

            if cards.isEmpty {
                emptyState
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(cards.enumerated()), id: \.element.id) { index, card in
                            WordListRow(
                                card: card,
                                dueText: dueDescription(for: card),
                                isStriped: !index.isMultiple(of: 2),
                                onEdit: { editingCard = card },
                                onDelete: {
                                    cardToDelete = card
                                    showDeleteConfirmation = true
                                }
                            )
                        }
                    }
                }
                .background(Color(nsColor: .windowBackgroundColor))
            }
        }
        .tint(AppPalette.accentBlue)
        .background {
            if !isPresentingModal {
                Color.clear
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    .focusedSceneValue(\.newWordAction, { focusedField = .newWord })
                    .focusedSceneValue(\.findWordsAction, { focusedField = .search })
                    .focusedSceneValue(\.importCSVAction, importCSVFromDialog)
                    .focusedSceneValue(\.aiGeneratorAction, { showAIGenerator = true })
            }
        }
        .task(id: feedback?.id) {
            guard let id = feedback?.id else { return }
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled, feedback?.id == id else { return }
            feedback = nil
        }
        .sheet(isPresented: $showAIGenerator) {
            AIGeneratorSheet()
                .environmentObject(store)
        }
        .sheet(item: $editingCard) { cardToEdit in
            EditWordSheet(card: cardToEdit)
                .environmentObject(store)
        }
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { createdDeck in
                newDeckName = createdDeck
                selectedDeckFilter = createdDeck
            }
            .environmentObject(store)
        }
        .sheet(item: Binding(
            get: { deckToRename.map { IdentifiableDeckName(name: $0) } },
            set: { deckToRename = $0?.name }
        )) { item in
            RenameDeckSheet(oldName: item.name) { renamed in
                if selectedDeckFilter == item.name {
                    selectedDeckFilter = renamed
                }
                if newDeckName == item.name {
                    newDeckName = renamed
                }
            }
            .environmentObject(store)
        }
        .alert(alertTitle, isPresented: $showAlert) {
            if offerImportUndo {
                Button("Geri al") {
                    store.undoLastImport()
                    offerImportUndo = false
                }
            }
            Button("Tamam", role: .cancel) {
                offerImportUndo = false
                store.finishImportUndoWindow()
            }
        } message: {
            Text(alertMessage ?? "")
        }
        .alert("Kelimeyi Sil", isPresented: $showDeleteConfirmation, presenting: cardToDelete) { card in
            Button("İptal", role: .cancel) { cardToDelete = nil }
            Button("Kelimeyi Sil", role: .destructive) {
                store.deleteCard(id: card.id)
                cardToDelete = nil
            }
        } message: { card in
            Text("„\(card.fullGermanTerm)“ kelimesini silmek istediğinize emin misiniz?")
        }
    }

    private func header(resultCount: Int) -> some View {
        let title = HStack(spacing: 8) {
            Text("Kelime Havuzu")
                .font(.title3.bold())
                .accessibilityAddTraits(.isHeader)

            Text(hasContentFilters || selectedDeckFilter != nil
                 ? "\(resultCount) / \(store.cards.count) kelime"
                 : "\(store.cards.count) kelime")
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 9)
                .padding(.vertical, 3)
                .foregroundStyle(AppPalette.accentBlue)
                .background(AppPalette.blueBadgeBackground, in: Capsule())
        }
        .fixedSize()

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                title
                Spacer(minLength: 8)
                headerActions
            }
            VStack(alignment: .leading, spacing: 8) {
                title
                headerActions
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var headerActions: some View {
        HStack(spacing: 8) {
            Button {
                showAIGenerator = true
            } label: {
                Label("AI ile Kelime Üret", systemImage: "sparkles")
                    .foregroundStyle(AppPalette.accentPurple)
            }
            .tint(AppPalette.accentPurple)

            Button(action: importCSVFromDialog) {
                Label("CSV İçe Aktar", systemImage: "square.and.arrow.down")
            }

            Button(action: exportCSVToDialog) {
                Label("CSV Dışa Aktar", systemImage: "square.and.arrow.up")
            }
            .keyboardShortcut("e", modifiers: [.command, .shift])
            .help("CSV Dışa Aktar (⇧⌘E)")
            .disabled(store.cards.isEmpty || isPresentingModal)

            Button {
                store.openStorageDirectoryInFinder()
            } label: {
                Label("Klasör", systemImage: "folder")
            }
            .accessibilityLabel("Yerel Depolama Klasörünü Finder’da Aç")
            .help("Yerel Depolama Klasörünü Finder'da Aç")
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
        .fixedSize(horizontal: true, vertical: false)
    }

    private var quickEntry: some View {
        VStack(alignment: .leading, spacing: 6) {
            WordEntryLayout {
                HStack(spacing: 8) {
                    ArtikelSegmentedControl(selection: $newArtikel)
                        .frame(width: 190)

                    TextField("Almanca kelime (örn. Tisch)", text: $newWord)
                        .textFieldStyle(.roundedBorder)
                        .focused($focusedField, equals: .newWord)
                        .frame(minWidth: 140, maxWidth: 200)

                    TextField("Türkçe anlamı / açıklama", text: $newMeaning)
                        .textFieldStyle(.roundedBorder)
                        .frame(minWidth: 140)
                }

                HStack(spacing: 8) {
                    entryDeckMenu
                    Button(action: submitQuickAdd) {
                        Label("Ekle", systemImage: "plus")
                            .foregroundStyle(AppPalette.onBlue)
                    }
                    .accessibilityLabel("Kelime Ekle")
                    .buttonStyle(.borderedProminent)
                    .disabled(
                        newWord.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                            (newArtikel == nil && newMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    )
                }
                .fixedSize()
            }

            // Article-less cards are studied by typing the Turkish meaning. Without one
            // the card can never be answered, so the form explains the requirement
            // instead of silently creating an unusable card.
            if newArtikel == nil {
                let meaningMissing = newMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                Label(
                    "Artikelsiz kelimelerde Türkçe anlam zorunludur; çalışırken bu anlamı yazacaksın.",
                    systemImage: meaningMissing ? "exclamationmark.circle.fill" : "info.circle"
                )
                .font(.caption2)
                .foregroundStyle(meaningMissing ? AppPalette.accentOrange : Color.secondary)
            }

            if let feedback {
                Label(feedback.message, systemImage: feedback.isDuplicate ? "doc.on.doc" : "checkmark.circle")
                    .font(.caption)
                    .foregroundStyle(feedback.isDuplicate ? AppPalette.accentBlue : AppPalette.success)
            }
        }
    }

    private var entryDeckMenu: some View {
        Menu {
            ForEach(store.allDeckNames, id: \.self) { deck in
                Button {
                    newDeckName = deck
                } label: {
                    if newDeckName == deck {
                        Label(deck, systemImage: "checkmark")
                    } else {
                        Text(deck)
                    }
                }
            }
            Divider()
            Button {
                showNewDeckSheet = true
            } label: {
                Label("Yeni Deste Ekle…", systemImage: "folder.badge.plus")
            }
        } label: {
            Label {
                Text(newDeckName)
                    .lineLimit(1)
            } icon: {
                Image(systemName: "folder.fill")
                    .foregroundStyle(AppPalette.accentBlue)
            }
            .frame(width: 115, alignment: .leading)
        }
        .menuStyle(.borderedButton)
        .accessibilityLabel("Eklenecek Kelimenin Destesi")
        .accessibilityValue(newDeckName)
        .help("Eklenecek kelimenin destesini seçin")
    }

    private var deckFilters: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    DeckFilterChip(
                        title: "Tüm Desteler",
                        count: store.cards.count,
                        isSelected: selectedDeckFilter == nil
                    ) {
                        selectedDeckFilter = nil
                    }

                    ForEach(store.allDeckNames, id: \.self) { deck in
                        DeckFilterChip(
                            title: deck,
                            count: store.cardCount(forDeck: deck),
                            isSelected: selectedDeckFilter == deck
                        ) {
                            selectedDeckFilter = deck
                            newDeckName = deck
                        }
                        .contextMenu { deckActions(for: deck) }
                    }

                    Button {
                        showNewDeckSheet = true
                    } label: {
                        Label("Yeni Deste Ekle", systemImage: "folder.badge.plus")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .foregroundStyle(AppPalette.accentBlue)
                            .background(AppPalette.blueBadgeBackground, in: Capsule())
                            .overlay {
                                Capsule().strokeBorder(AppPalette.accentBlue, lineWidth: 1)
                            }
                    }
                    .buttonStyle(.plain)
                    .fixedSize()
                }
            }

            if let deck = selectedDeckFilter {
                Menu {
                    deckActions(for: deck)
                } label: {
                    Label("Deste Seçenekleri", systemImage: "ellipsis.circle")
                        .labelStyle(.iconOnly)
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .accessibilityLabel("‘\(deck)’ Destesi Seçenekleri")
                .help("Deste Seçenekleri")
            }
        }
    }

    @ViewBuilder
    private func deckActions(for deck: String) -> some View {
        Button {
            deckToRename = deck
        } label: {
            Label("Deste Adını Değiştir…", systemImage: "pencil")
        }
        if deck != "Genel" {
            Divider()
            Button(role: .destructive) {
                store.deleteDeck(name: deck, deleteWords: false)
                if selectedDeckFilter == deck {
                    selectedDeckFilter = nil
                }
            } label: {
                Label("Desteyi Sil (Kelimeleri 'Genel'e Taşı)", systemImage: "folder.badge.minus")
            }
        }
    }

    private var searchFilters: some View {
        HStack(spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                TextField("Kelime, anlam veya deste ara…", text: $searchText)
                    .textFieldStyle(.plain)
                    .focused($focusedField, equals: .search)
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                        focusedField = .search
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Aramayı temizle")
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))

            Picker("Artikel filtresi", selection: $selectedArtikelFilter) {
                Text("Tüm Artikeller").tag(String?.none)
                ForEach(Artikel.allCases) { artikel in
                    Text(artikel.rawValue).tag(String?.some(artikel.rawValue))
                }
                Text("Artikelsiz").tag(String?.some(ArtikelFilter.none))
            }
            .labelsHidden()

            Picker("Durum filtresi", selection: $selectedStageFilter) {
                Text("Tüm Durumlar").tag(CardStage?.none)
                ForEach(CardStage.allCases, id: \.rawValue) { stage in
                    Text(stage.title).tag(CardStage?.some(stage))
                }
            }
            .labelsHidden()
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        if store.cards.isEmpty {
            ContentUnavailableView {
                Label("Henüz kayıtlı kelime yok", systemImage: "tray")
            } description: {
                Text("İlk kelimeni ekle, bir CSV dosyası içe aktar ya da Gemini AI ile kelime üret.")
            } actions: {
                emptyAddActions
            }
        } else if let deck = selectedDeckFilter, !hasContentFilters {
            ContentUnavailableView {
                Label("'\(deck)' destesinde henüz kelime yok", systemImage: "folder")
            } description: {
                Text("Bu desteye kelime ekleyebilir veya Gemini AI ile yeni kelimeler üretebilirsin.")
            } actions: {
                emptyAddActions
            }
        } else {
            ContentUnavailableView {
                Label("Sonuç bulunamadı", systemImage: "magnifyingglass")
            } description: {
                Text("Arama veya filtre kriterlerine uygun kelime yok.")
            } actions: {
                Button("Filtreleri temizle") {
                    searchText = ""
                    selectedArtikelFilter = nil
                    selectedStageFilter = nil
                    selectedDeckFilter = nil
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var emptyAddActions: some View {
        HStack {
            Button {
                focusedField = .newWord
            } label: {
                Label("Kelime Ekle", systemImage: "plus")
            }

            Button {
                showAIGenerator = true
            } label: {
                Label("AI ile Kelime Üret", systemImage: "sparkles")
                    .foregroundStyle(AppPalette.accentPurple)
            }
            .tint(AppPalette.accentPurple)
        }
        .buttonStyle(.bordered)
    }

    private func submitQuickAdd() {
        let word = newWord.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !word.isEmpty else { return }
        // Article-less cards are answered by typing the meaning, so a blank meaning is unusable.
        guard newArtikel != nil || !newMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let added = store.addCard(artikel: newArtikel, word: newWord, meaning: newMeaning, deckName: newDeckName)
        if added {
            newWord = ""
            newMeaning = ""
        }
        showFeedback(added ? "„\(word)“ eklendi" : "„\(word)“ zaten havuzda", isDuplicate: !added)
    }

    private func showFeedback(_ message: String, isDuplicate: Bool = false) {
        feedback = InlineFeedback(message: message, isDuplicate: isDuplicate)
        AccessibilityNotification.Announcement(message).post()
    }

    private func dueDescription(for card: Flashcard) -> String {
        if card.stage == .new {
            return "Sırada (Yeni)"
        }
        let now = store.currentTime
        if card.dueDate <= now {
            return "Şimdi"
        }
        let diff = card.dueDate.timeIntervalSince(now)
        if diff < 60 {
            let secs = max(1, Int(ceil(diff)))
            return "\(secs) sn sonra"
        } else if diff < 3600 {
            let mins = max(1, Int(round(diff / 60)))
            return "\(mins) dk sonra"
        } else if diff < 86_400 {
            let hours = max(1, Int(round(diff / 3600)))
            return "\(hours) sa sonra"
        } else {
            let days = max(1, Int(round(diff / 86_400)))
            return "\(days) gün sonra"
        }
    }

    private func importCSVFromDialog() {
        guard !isPresentingModal else { return }
        showingCSVPanel = true
        defer { showingCSVPanel = false }

        let targetDeck = selectedDeckFilter ?? newDeckName
        let panel = NSOpenPanel()
        panel.title = "CSV Dosyası İçe Aktar"
        panel.message = """
        Sütun sırası: Artikel, Kelime, Anlam, Deste (isteğe bağlı).
        Deste belirtilmeyen kelimeler „\(targetDeck)“ destesine aktarılır.
        """
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .tabSeparatedText]
        panel.allowsMultipleSelection = false

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let res = try store.importCSV(from: url, defaultDeckName: targetDeck)
                alertTitle = "CSV içe aktarıldı"
                alertMessage = """
                Yeni eklenen: \(res.addedCount)
                Güncellenen: \(res.updatedCount)
                Atlanan (mevcut): \(res.skippedCount)
                Geçersiz satır: \(res.invalidLines)
                """
                offerImportUndo = store.canUndoImport
                showAlert = true
            } catch {
                alertTitle = "CSV içe aktarılamadı"
                offerImportUndo = false
                alertMessage = error.localizedDescription
                showAlert = true
            }
        }
    }

    private func exportCSVToDialog() {
        guard !isPresentingModal, !store.cards.isEmpty else { return }
        showingCSVPanel = true
        defer { showingCSVPanel = false }

        let panel = NSSavePanel()
        panel.title = "Kelimeleri CSV Olarak Dışa Aktar"
        panel.nameFieldStringValue = "almanca_kelimeler.csv"
        panel.allowedContentTypes = [.commaSeparatedText]

        if panel.runModal() == .OK, let url = panel.url {
            let content = store.exportCSVString()
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                showFeedback("Kelimeler CSV olarak dışa aktarıldı.")
            } catch {
                alertTitle = "CSV dışa aktarılamadı"
                offerImportUndo = false
                alertMessage = "Dışa aktarma hatası: \(error.localizedDescription)"
                showAlert = true
            }
        }
    }
}

/// Reflows the controls without replacing the text fields when the window is resized.
private struct WordEntryLayout: Layout {
    private let spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let actions = subviews[1].sizeThatFits(.unspecified)
        let minimumFieldsWidth = subviews[0].sizeThatFits(.zero).width
        let singleRowWidth = minimumFieldsWidth + spacing + actions.width
        let proposedWidth = proposal.width.flatMap { $0.isFinite ? $0 : nil } ?? singleRowWidth
        let width = max(proposedWidth, max(minimumFieldsWidth, actions.width))
        let fitsOnOneRow = width >= singleRowWidth
        let fields = subviews[0].sizeThatFits(.init(
            width: fitsOnOneRow ? width - actions.width - spacing : width,
            height: nil
        ))
        let height = fitsOnOneRow ? max(fields.height, actions.height) : fields.height + spacing + actions.height
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let actions = subviews[1].sizeThatFits(.unspecified)
        let minimumFieldsWidth = subviews[0].sizeThatFits(.zero).width
        let fitsOnOneRow = bounds.width >= minimumFieldsWidth + spacing + actions.width
        let fieldsProposal = ProposedViewSize(
            width: fitsOnOneRow ? bounds.width - actions.width - spacing : bounds.width,
            height: nil
        )
        if fitsOnOneRow {
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: fieldsProposal)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing, proposal: .unspecified)
        } else {
            let fields = subviews[0].sizeThatFits(fieldsProposal)
            subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: fieldsProposal)
            subviews[1].place(
                at: CGPoint(x: bounds.minX, y: bounds.minY + fields.height + spacing),
                anchor: .topLeading,
                proposal: .unspecified
            )
        }
    }
}

private struct WordListRow: View {
    let card: Flashcard
    let dueText: String
    let isStriped: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var isHovered = false
    @FocusState private var focusedTarget: FocusTarget?

    /// Fixed metadata column widths so pills line up across rows.
    private enum Column {
        static let deck: CGFloat = 150
        static let stage: CGFloat = 124
        static let due: CGFloat = 84
    }

    private enum FocusTarget: Hashable {
        case row
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text(card.displayArtikel)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(card.artikel?.color ?? AppPalette.articleNone)
                    .frame(width: 44)
                    .padding(.vertical, 4)
                    .background(
                        card.artikel?.badgeBackground ?? AppPalette.articleNoneBadgeBackground,
                        in: RoundedRectangle(cornerRadius: 6)
                    )
                    .accessibilityLabel(card.artikel == nil ? "Artikelsiz kelime" : card.displayArtikel)

                VStack(alignment: .leading, spacing: 2) {
                    Text(card.word)
                        .font(.body.weight(.semibold))
                        .lineLimit(2)
                    if !card.meaning.isEmpty {
                        Text(card.meaning)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
                .layoutPriority(1)

                Spacer(minLength: 0)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) {
                        deckBadge
                        stageBadge
                        dueLabel
                    }
                    VStack(alignment: .trailing, spacing: 3) {
                        deckBadge
                        stageBadge
                        dueLabel
                    }
                }

                rowActions
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                isHovered || focusedTarget == .row
                    ? AppPalette.blueBadgeBackground
                    : Color.primary.opacity(isStriped ? 0.035 : 0)
            )
            .contentShape(Rectangle())
            .onHover { isHovered = $0 }
            .onTapGesture(count: 2, perform: onEdit)
            .focusable(interactions: .activate)
            .focused($focusedTarget, equals: .row)
            .onKeyPress(.return) {
                guard focusedTarget == .row else { return .ignored }
                onEdit()
                return .handled
            }

            Divider().opacity(0.4)
        }
    }

    private var deckBadge: some View {
        Label(card.deckName, systemImage: "folder")
            .font(.caption2.weight(.medium))
            .lineLimit(1)
            .truncationMode(.tail)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(AppPalette.articleNone)
            .background(AppPalette.articleNoneBadgeBackground, in: Capsule())
            .help(card.deckName)
            .frame(width: Column.deck, alignment: .leading)
    }

    private var stageBadge: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(card.stage.color)
                .frame(width: 6, height: 6)
                .accessibilityHidden(true)
            Text(card.stage.title)
                .font(.caption2.weight(.semibold))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .foregroundStyle(.primary)
        .background(card.stage.badgeBackground, in: Capsule())
        .frame(width: Column.stage, alignment: .leading)
    }

    private var dueLabel: some View {
        Text(dueText)
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .frame(width: Column.due, alignment: .trailing)
    }

    private var rowActions: some View {
        HStack(spacing: 8) {
            Button {
                SpeechManager.shared.speak(card.fullGermanTerm)
            } label: {
                Image(systemName: "speaker.wave.2")
            }
            .accessibilityLabel("\(card.fullGermanTerm) kelimesini seslendir")
            .help("Seslendir")

            Button(action: onEdit) {
                Image(systemName: "pencil")
                    .foregroundStyle(AppPalette.accentBlue)
            }
            .accessibilityLabel("\(card.fullGermanTerm) kelimesini düzenle")
            .help("Kelimeyi Düzenle")

            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
                    .foregroundStyle(AppPalette.error)
            }
            .accessibilityLabel("\(card.fullGermanTerm) kelimesini sil")
            .help("Kelimeyi Sil")
        }
        .buttonStyle(.borderless)
        .fixedSize()
    }
}

struct IdentifiableDeckName: Identifiable {
    let name: String
    var id: String { name }
}

private struct DeckFilterChip: View {
    let title: String
    let count: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: isSelected ? "folder.fill" : "folder")
                    .font(.caption2)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.caption.weight(isSelected ? .bold : .medium))
                    .lineLimit(1)
                Text("\(count)")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(isSelected ? AppPalette.onBlue.opacity(0.15) : Color.secondary.opacity(0.15))
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(isSelected ? AppPalette.accentBlue : Color(nsColor: .textBackgroundColor))
            .foregroundStyle(isSelected ? AppPalette.onBlue : Color.primary)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(isSelected ? AppPalette.accentBlue : AppPalette.controlOutline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .fixedSize()
        .accessibilityLabel("\(title), \(count) kelime")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - Stable four-segment article picker

/// der / die / das / "-" picker with fixed geometry.
///
/// The native `Picker(.segmented)` derived its intrinsic width from the selected label and the
/// previous `.frame + .fixedSize()` combination fought that, so every tap made the whole box grow
/// and shrink. This control gives every segment `maxWidth: .infinity` inside one fixed frame and
/// only animates the selection colour, never the layout.
struct ArtikelSegmentedControl: View {
    @Binding var selection: Artikel?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 3) {
            ForEach(Artikel.allCases) { art in
                segment(art, label: art.rawValue, color: art.color)
            }
            segment(nil, label: ArtikelFilter.none, color: AppPalette.articleNone)
        }
        .padding(3)
        .frame(height: 32)
        .background(Color(NSColor.textBackgroundColor))
        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .strokeBorder(AppPalette.controlOutline, lineWidth: 1)
                .allowsHitTesting(false)
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: selection)
    }

    private func segment(_ art: Artikel?, label: String, color: Color) -> some View {
        let isSelected = selection == art
        return Button {
            selection = art
        } label: {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(isSelected ? color : Color.clear)
                .foregroundStyle(isSelected ? Color.white : color)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(art == nil ? "Artikelsiz" : "\(art!.rawValue) \(art!.grammaticalLabel)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .help(art == nil ? "Artikelsiz (-)" : art!.grammaticalLabel)
    }
}

// MARK: - New Deck Creation Modal Sheet

struct NewDeckSheet: View {
    @EnvironmentObject var store: DeckStore
    @Environment(\.dismiss) private var dismiss
    @State private var deckName: String = ""
    @FocusState private var isFocused: Bool
    var onCreated: ((String) -> Void)? = nil

    private let suggestedDecks = [
        "A1 Kelimeler", "A2 Kelimeler", "B1 Kelimeler",
        "B2 Kelimeler", "C1 Kelimeler", "Günlük Konuşma",
        "İş Almancası", "Yiyecek & İçecek", "Seyahat"
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label("Yeni Deste Oluştur", systemImage: "folder.badge.plus")
                    .font(.title3.bold())
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Kapat")
            }

            Text("Kelime haznenizi konulara veya seviyelere göre ayırmak için yeni bir deste adı girin:")
                .font(.caption)
                .foregroundColor(.secondary)

            TextField("Deste adı (örn: B1 Sınav, Mutfak, Akademik)", text: $deckName)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)

            VStack(alignment: .leading, spacing: 6) {
                Text("Hızlı Öneriler:")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(.secondary)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 115), spacing: 6)], spacing: 6) {
                    ForEach(suggestedDecks, id: \.self) { suggestion in
                        Button {
                            deckName = suggestion
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: deckName == suggestion ? "folder.fill" : "folder")
                                    .font(.caption2)
                                    .accessibilityHidden(true)
                                Text(suggestion)
                                    .font(.caption)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(deckName == suggestion ? AppPalette.blueBadgeBackground : Color.secondary.opacity(0.1))
                            .foregroundStyle(deckName == suggestion ? AppPalette.accentBlue : Color.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(deckName == suggestion ? .isSelected : [])
                    }
                }
            }

            Divider()

            HStack {
                Button("İptal") {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Spacer()

                Button {
                    saveAndDismiss()
                } label: {
                    Label("Deste Oluştur", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(deckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 430)
        .tint(AppPalette.accentBlue)
        .defaultFocus($isFocused, true)
    }

    private func saveAndDismiss() {
        guard let created = store.createDeck(name: deckName) else { return }
        onCreated?(created)
        dismiss()
    }
}

struct RenameDeckSheet: View {
    @EnvironmentObject var store: DeckStore
    @Environment(\.dismiss) private var dismiss
    let oldName: String
    @State private var newName: String
    @FocusState private var isFocused: Bool
    var onRenamed: ((String) -> Void)? = nil

    init(oldName: String, onRenamed: ((String) -> Void)? = nil) {
        self.oldName = oldName
        _newName = State(initialValue: oldName)
        self.onRenamed = onRenamed
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("'\(oldName)' Destesinin Adını Değiştir")
                .font(.headline)

            TextField("Yeni deste adı", text: $newName)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)

            HStack {
                Button("İptal") {
                    dismiss()
                }
                .buttonStyle(.bordered)
                Spacer()
                Button(action: commitRename) {
                    Text("Kaydet")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 360)
        .tint(AppPalette.accentBlue)
        .defaultFocus($isFocused, true)
    }

    private func commitRename() {
        let cleaned = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        store.renameDeck(oldName: oldName, newName: cleaned)
        onRenamed?(cleaned)
        dismiss()
    }
}

// MARK: - Edit Word Modal Sheet

struct EditWordSheet: View {
    @EnvironmentObject var store: DeckStore
    @Environment(\.dismiss) private var dismiss

    let card: Flashcard
    @State private var editArtikel: Artikel?
    @State private var editWord: String
    @State private var editMeaning: String
    @State private var editDeckName: String
    @State private var showNewDeckSheet: Bool = false
    @FocusState private var isWordFocused: Bool

    init(card: Flashcard) {
        self.card = card
        _editArtikel = State(initialValue: card.artikel)
        _editWord = State(initialValue: card.word)
        _editMeaning = State(initialValue: card.meaning)
        _editDeckName = State(initialValue: card.deckName)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label("Kelimeyi Düzenle", systemImage: "pencil.circle.fill")
                    .font(.title3.bold())
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Kapat")
            }

            Divider()

            // Fixed-height scrollable body: the old auto-sized sheet grew/shrank whenever the
            // picker or the inline validation line changed, which made resizing feel broken.
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Artikel:")
                            .font(.caption.weight(.semibold))
                        Spacer()
                        Text(editArtikel == nil ? "Artikelsiz (-)" : editArtikel!.grammaticalLabel)
                            .font(.caption)
                            .foregroundColor(editArtikel == nil ? AppPalette.articleNone : editArtikel!.color)
                    }

                    // Short labels keep the control readable in a 280pt row; long
                    // "der (Maskulin (Eril))" segments were squeezing and clipping the sheet.
                    ArtikelSegmentedControl(selection: $editArtikel)
                        .frame(width: 280)

                    Text("Almanca Kelime:")
                        .font(.caption.weight(.semibold))
                    TextField("Almanca kelime", text: $editWord)
                        .textFieldStyle(.roundedBorder)
                        .focused($isWordFocused)

                    Text("Türkçe Anlamı / Açıklama:")
                        .font(.caption.weight(.semibold))
                    TextField("Türkçe anlamı", text: $editMeaning)
                        .textFieldStyle(.roundedBorder)

                    // Reserved single line: showing/hiding the warning must not resize the sheet.
                    Text(
                        editArtikel == nil && editMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? "Artikelsiz kelimede Türkçe anlam zorunludur."
                            : " "
                    )
                    .font(.caption)
                    .foregroundStyle(AppPalette.accentOrange)
                    .frame(height: 16, alignment: .leading)
                    .accessibilityHidden(editArtikel != nil || !editMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    Text("Deste / Kategori:")
                        .font(.caption.weight(.semibold))
                    HStack(spacing: 8) {
                        Menu {
                            ForEach(store.allDeckNames, id: \.self) { existingDeck in
                                Button(existingDeck) {
                                    editDeckName = existingDeck
                                }
                            }
                            Divider()
                            Button {
                                showNewDeckSheet = true
                            } label: {
                                Label("Yeni Deste Ekle...", systemImage: "folder.badge.plus")
                            }
                        } label: {
                            HStack {
                                Image(systemName: "folder.fill")
                                    .foregroundStyle(AppPalette.accentBlue)
                                Text(editDeckName)
                                Spacer()
                            }
                        }
                        .menuStyle(.borderedButton)

                        Button {
                            showNewDeckSheet = true
                        } label: {
                            Label("Yeni Deste", systemImage: "folder.badge.plus")
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: 370)

            Divider()

            HStack {
                Button("İptal") {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Spacer()

                Button {
                    _ = store.createDeck(name: editDeckName)
                    store.updateCard(
                        id: card.id,
                        artikel: editArtikel,
                        word: editWord,
                        meaning: editMeaning,
                        deckName: editDeckName
                    )
                    dismiss()
                } label: {
                    Label("Değişiklikleri Kaydet", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(
                    showNewDeckSheet ||
                        editWord.trimmingCharacters(in: .whitespaces).isEmpty ||
                        (editArtikel == nil && editMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                )
            }
        }
        .padding(22)
        .frame(width: 520, height: 560)
        .tint(AppPalette.accentBlue)
        .defaultFocus($isWordFocused, true)
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { created in
                editDeckName = created
            }
            .environmentObject(store)
        }
    }
}
