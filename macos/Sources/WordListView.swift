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
    @State private var deckScrollAnchorIndex: Int = 0
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
            .background(.regularMaterial)

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
        .alert(L10n.text("Kelimeyi Sil", en: "Delete Word", de: "Wort löschen"), isPresented: $showDeleteConfirmation, presenting: cardToDelete) { card in
            Button(L10n.cancel, role: .cancel) { cardToDelete = nil }
            Button(L10n.text("Kelimeyi Sil", en: "Delete Word", de: "Wort löschen"), role: .destructive) {
                store.deleteCard(id: card.id)
                cardToDelete = nil
            }
        } message: { card in
            Text(L10n.text(
                "„\(card.fullGermanTerm)“ kelimesini silmek istediğinize emin misiniz?",
                en: "Are you sure you want to delete “\(card.fullGermanTerm)”?",
                de: "Möchtest du „\(card.fullGermanTerm)“ wirklich löschen?"
            ))
        }
    }

    private func header(resultCount: Int) -> some View {
        let wordUnit = L10n.text("kelime", en: "words", de: "Wörter")
        let title = HStack(spacing: 8) {
            Text(L10n.wordsTitle())
                .font(.title3.bold())
                .accessibilityAddTraits(.isHeader)

            Text(hasContentFilters || selectedDeckFilter != nil
                 ? "\(resultCount) / \(store.cards.count) \(wordUnit)"
                 : "\(store.cards.count) \(wordUnit)")
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
                Label(L10n.text("AI ile Kelime Üret", en: "Generate with AI", de: "Mit KI generieren"), systemImage: "sparkles")
                    .foregroundStyle(AppPalette.accentPurple)
            }
            .tint(AppPalette.accentPurple)

            Button(action: importCSVFromDialog) {
                Label(L10n.text("CSV İçe Aktar", en: "Import CSV", de: "CSV importieren"), systemImage: "square.and.arrow.down")
            }

            Button(action: exportCSVToDialog) {
                Label(L10n.text("CSV Dışa Aktar", en: "Export CSV", de: "CSV exportieren"), systemImage: "square.and.arrow.up")
            }
            .keyboardShortcut("e", modifiers: [.command, .shift])
            .help(L10n.text("CSV Dışa Aktar (⇧⌘E)", en: "Export CSV (⇧⌘E)", de: "CSV exportieren (⇧⌘E)"))
            .disabled(store.cards.isEmpty || isPresentingModal)

            Button {
                store.openStorageDirectoryInFinder()
            } label: {
                Label(L10n.text("Klasör", en: "Folder", de: "Ordner"), systemImage: "folder")
            }
            .accessibilityLabel(L10n.showStorageInFinder)
            .help(L10n.showStorageInFinder)
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

                    TextField(L10n.text("Almanca kelime (örn. Tisch)", en: "German word (e.g. Tisch)", de: "Deutsches Wort (z. B. Tisch)"), text: $newWord)
                        .textFieldStyle(.roundedBorder)
                        .focused($focusedField, equals: .newWord)
                        .frame(minWidth: 140, maxWidth: 200)

                    TextField(L10n.text("Anlamı / açıklama", en: "Meaning / note", de: "Bedeutung / Notiz"), text: $newMeaning)
                        .textFieldStyle(.roundedBorder)
                        .frame(minWidth: 140)
                }

                HStack(spacing: 8) {
                    entryDeckMenu
                    Button(action: submitQuickAdd) {
                        Label(L10n.addButton, systemImage: "plus")
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
                    L10n.articlelessMeaningRequired,
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
        .accessibilityLabel(L10n.text("Eklenecek Kelimenin Destesi", en: "Target Deck for New Word", de: "Zielstapel für neues Wort"))
        .accessibilityValue(newDeckName)
        .help(L10n.text("Eklenecek kelimenin destesini seçin", en: "Select the deck for the new word", de: "Wähle den Stapel für das neue Wort"))
    }

    private var deckFilters: some View {
        let deckNames = store.allDeckNames
        let scrollItemIDs = ["__all__"] + deckNames + ["__new_deck__"]

        return HStack(spacing: 8) {
            ScrollViewReader { proxy in
                HStack(spacing: 4) {
                    if deckNames.count > 1 {
                        Button {
                            let nextIndex = max(0, deckScrollAnchorIndex - 2)
                            deckScrollAnchorIndex = nextIndex
                            withAnimation(.easeInOut(duration: 0.2)) {
                                proxy.scrollTo(scrollItemIDs[nextIndex], anchor: .leading)
                            }
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 22, height: 22)
                                .background(.quaternary.opacity(0.6), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L10n.text("Desteleri sola kaydır", en: "Scroll decks left", de: "Stapel nach links scrollen"))
                        .help(L10n.text("Desteleri sola kaydır", en: "Scroll decks left", de: "Stapel nach links scrollen"))
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            DeckFilterChip(
                                title: L10n.allDecks,
                                count: store.cards.count,
                                isSelected: selectedDeckFilter == nil
                            ) {
                                selectedDeckFilter = nil
                                deckScrollAnchorIndex = 0
                            }
                            .id("__all__")

                            ForEach(Array(deckNames.enumerated()), id: \.element) { idx, deck in
                                DeckFilterChip(
                                    title: deck,
                                    count: store.cardCount(forDeck: deck),
                                    isSelected: selectedDeckFilter == deck
                                ) {
                                    selectedDeckFilter = deck
                                    newDeckName = deck
                                    deckScrollAnchorIndex = idx + 1
                                }
                                .id(deck)
                                .contextMenu { deckActions(for: deck) }
                            }

                            Button {
                                showNewDeckSheet = true
                            } label: {
                                Label(L10n.newDeckAdd, systemImage: "folder.badge.plus")
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
                            .id("__new_deck__")
                        }
                        .background(HorizontalWheelScrollBridge())
                    }
                    .onChange(of: selectedDeckFilter) { _, newValue in
                        let targetID = newValue ?? "__all__"
                        if let idx = scrollItemIDs.firstIndex(of: targetID) {
                            deckScrollAnchorIndex = idx
                        }
                        withAnimation(.easeInOut(duration: 0.2)) {
                            proxy.scrollTo(targetID, anchor: .center)
                        }
                    }

                    if deckNames.count > 1 {
                        Button {
                            let maxIndex = max(0, scrollItemIDs.count - 1)
                            let nextIndex = min(maxIndex, deckScrollAnchorIndex + 2)
                            deckScrollAnchorIndex = nextIndex
                            withAnimation(.easeInOut(duration: 0.2)) {
                                proxy.scrollTo(scrollItemIDs[nextIndex], anchor: .trailing)
                            }
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 22, height: 22)
                                .background(.quaternary.opacity(0.6), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L10n.text("Desteleri sağa kaydır", en: "Scroll decks right", de: "Stapel nach rechts scrollen"))
                        .help(L10n.text("Desteleri sağa kaydır", en: "Scroll decks right", de: "Stapel nach rechts scrollen"))
                    }
                }
            }

            if let deck = selectedDeckFilter {
                Menu {
                    deckActions(for: deck)
                } label: {
                    Label(L10n.text("Deste Seçenekleri", en: "Deck Options", de: "Stapel-Optionen"), systemImage: "ellipsis.circle")
                        .labelStyle(.iconOnly)
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .accessibilityLabel(L10n.text("‘\(deck)’ Destesi Seçenekleri", en: "‘\(deck)’ Deck Options", de: "Optionen für Stapel ‚\(deck)‘"))
                .help(L10n.text("Deste Seçenekleri", en: "Deck Options", de: "Stapel-Optionen"))
            }
        }
    }

    @ViewBuilder
    private func deckActions(for deck: String) -> some View {
        Button {
            deckToRename = deck
        } label: {
            Label(L10n.text("Deste Adını Değiştir…", en: "Rename Deck…", de: "Stapel umbenennen…"), systemImage: "pencil")
        }
        if deck != "Genel" {
            Divider()
            Button(role: .destructive) {
                store.deleteDeck(name: deck, deleteWords: false)
                if selectedDeckFilter == deck {
                    selectedDeckFilter = nil
                }
            } label: {
                Label(L10n.text("Desteyi Sil (Kelimeleri 'Genel'e Taşı)", en: "Delete Deck (Move Words to 'Genel')", de: "Stapel löschen (Wörter nach ‚Genel‘ verschieben)"), systemImage: "folder.badge.minus")
            }
        }
    }

    private var searchFilters: some View {
        HStack(spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                TextField(L10n.searchPlaceholder, text: $searchText)
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
                    .accessibilityLabel(L10n.text("Aramayı temizle", en: "Clear search", de: "Suche leeren"))
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))

            Picker(L10n.text("Artikel filtresi", en: "Article filter", de: "Artikelfilter"), selection: $selectedArtikelFilter) {
                Text(L10n.allArticles).tag(String?.none)
                ForEach(Artikel.allCases) { artikel in
                    Text(artikel.rawValue).tag(String?.some(artikel.rawValue))
                }
                Text(L10n.articleless).tag(String?.some(ArtikelFilter.none))
            }
            .labelsHidden()

            Picker(L10n.text("Durum filtresi", en: "Stage filter", de: "Phasenfilter"), selection: $selectedStageFilter) {
                Text(L10n.allStages).tag(CardStage?.none)
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
                Label(L10n.text("Henüz kayıtlı kelime yok", en: "No saved words yet", de: "Noch keine Wörter gespeichert"), systemImage: "tray")
            } description: {
                Text(L10n.text(
                    "İlk kelimeni ekle, bir CSV dosyası içe aktar ya da AI ile kelime üret.",
                    en: "Add your first word, import a CSV file, or generate words with AI.",
                    de: "Füge dein erstes Wort hinzu, importiere eine CSV-Datei oder generiere Wörter mit KI."
                ))
            } actions: {
                emptyAddActions
            }
        } else if let deck = selectedDeckFilter, !hasContentFilters {
            ContentUnavailableView {
                Label(L10n.text("'\(deck)' destesinde henüz kelime yok", en: "No words in '\(deck)' deck yet", de: "Noch keine Wörter im Stapel ‚\(deck)‘"), systemImage: "folder")
            } description: {
                Text(L10n.text(
                    "Bu desteye kelime ekleyebilir veya AI ile yeni kelimeler üretebilirsin.",
                    en: "Add words to this deck or generate new ones with AI.",
                    de: "Füge diesem Stapel Wörter hinzu oder generiere neue mit KI."
                ))
            } actions: {
                emptyAddActions
            }
        } else {
            ContentUnavailableView {
                Label(L10n.text("Sonuç bulunamadı", en: "No results found", de: "Keine Ergebnisse gefunden"), systemImage: "magnifyingglass")
            } description: {
                Text(L10n.text("Arama veya filtre kriterlerine uygun kelime yok.", en: "No words match your search or filter criteria.", de: "Keine Wörter entsprechen deinen Such- oder Filterkriterien."))
            } actions: {
                Button(L10n.text("Filtreleri temizle", en: "Clear filters", de: "Filter zurücksetzen")) {
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
                Label(L10n.text("Kelime Ekle", en: "Add Word", de: "Wort hinzufügen"), systemImage: "plus")
            }

            Button {
                showAIGenerator = true
            } label: {
                Label(L10n.text("AI ile Kelime Üret", en: "Generate with AI", de: "Mit KI generieren"), systemImage: "sparkles")
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
        showFeedback(
            added
                ? L10n.text("„\(word)“ eklendi", en: "Added “\(word)”", de: "„\(word)“ hinzugefügt")
                : L10n.text("„\(word)“ zaten havuzda", en: "“\(word)” is already in the pool", de: "„\(word)“ ist bereits im Wortschatz"),
            isDuplicate: !added
        )
    }

    private func showFeedback(_ message: String, isDuplicate: Bool = false) {
        feedback = InlineFeedback(message: message, isDuplicate: isDuplicate)
        AccessibilityNotification.Announcement(message).post()
    }

    private func dueDescription(for card: Flashcard) -> String {
        if card.stage == .new {
            return L10n.text("Sırada (Yeni)", en: "Queued (New)", de: "In Warteschlange (Neu)")
        }
        let now = store.currentTime
        if card.dueDate <= now {
            return L10n.text("Şimdi", en: "Now", de: "Jetzt")
        }
        let diff = card.dueDate.timeIntervalSince(now)
        if diff < 60 {
            let secs = max(1, Int(ceil(diff)))
            return L10n.text("\(secs) sn sonra", en: "in \(secs)s", de: "in \(secs) Sek.")
        } else if diff < 3600 {
            let mins = max(1, Int(round(diff / 60)))
            return L10n.text("\(mins) dk sonra", en: "in \(mins)m", de: "in \(mins) Min.")
        } else if diff < 86_400 {
            let hours = max(1, Int(round(diff / 3600)))
            return L10n.text("\(hours) sa sonra", en: "in \(hours)h", de: "in \(hours) Std.")
        } else {
            let days = max(1, Int(round(diff / 86_400)))
            return L10n.text("\(days) gün sonra", en: "in \(days)d", de: days == 1 ? "in 1 Tag" : "in \(days) Tagen")
        }
    }

    private func importCSVFromDialog() {
        guard !isPresentingModal else { return }
        showingCSVPanel = true
        defer { showingCSVPanel = false }

        let targetDeck = selectedDeckFilter ?? newDeckName
        let panel = NSOpenPanel()
        panel.title = L10n.text("CSV Dosyası İçe Aktar", en: "Import CSV File", de: "CSV-Datei importieren")
        panel.message = L10n.text(
            """
            Sütun sırası: Artikel, Kelime, Anlam, Deste (isteğe bağlı).
            Deste belirtilmeyen kelimeler „\(targetDeck)“ destesine aktarılır.
            """,
            en: """
            Columns: Article, Word, Meaning, Deck (optional).
            Words without a deck are imported into “\(targetDeck)”.
            """,
            de: """
            Spalten: Artikel, Wort, Bedeutung, Stapel (optional).
            Wörter ohne Stapel werden in „\(targetDeck)“ importiert.
            """
        )
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .tabSeparatedText]
        panel.allowsMultipleSelection = false

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let res = try store.importCSV(from: url, defaultDeckName: targetDeck)
                alertTitle = L10n.text("CSV içe aktarıldı", en: "CSV imported", de: "CSV importiert")
                alertMessage = L10n.text(
                    """
                    Yeni eklenen: \(res.addedCount)
                    Güncellenen: \(res.updatedCount)
                    Atlanan (mevcut): \(res.skippedCount)
                    Geçersiz satır: \(res.invalidLines)
                    """,
                    en: """
                    Added: \(res.addedCount)
                    Updated: \(res.updatedCount)
                    Skipped (existing): \(res.skippedCount)
                    Invalid rows: \(res.invalidLines)
                    """,
                    de: """
                    Hinzugefügt: \(res.addedCount)
                    Aktualisiert: \(res.updatedCount)
                    Übersprungen (vorhanden): \(res.skippedCount)
                    Ungültige Zeilen: \(res.invalidLines)
                    """
                )
                offerImportUndo = store.canUndoImport
                showAlert = true
            } catch {
                alertTitle = L10n.text("CSV içe aktarılamadı", en: "Could not import CSV", de: "CSV-Import fehlgeschlagen")
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
        panel.title = L10n.text("Kelimeleri CSV Olarak Dışa Aktar", en: "Export Words as CSV", de: "Wörter als CSV exportieren")
        panel.nameFieldStringValue = "almanca_kelimeler.csv"
        panel.allowedContentTypes = [.commaSeparatedText]

        if panel.runModal() == .OK, let url = panel.url {
            let content = store.exportCSVString()
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                showFeedback(L10n.text("Kelimeler CSV olarak dışa aktarıldı.", en: "Words exported as CSV.", de: "Wörter als CSV exportiert."))
            } catch {
                alertTitle = L10n.text("CSV dışa aktarılamadı", en: "Could not export CSV", de: "CSV-Export fehlgeschlagen")
                offerImportUndo = false
                alertMessage = L10n.text("Dışa aktarma hatası: \(error.localizedDescription)", en: "Export error: \(error.localizedDescription)", de: "Exportfehler: \(error.localizedDescription)")
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
            .accessibilityLabel(L10n.text("\(card.fullGermanTerm) kelimesini seslendir", en: "Pronounce \(card.fullGermanTerm)", de: "\(card.fullGermanTerm) vorlesen"))
            .help(L10n.text("Seslendir", en: "Pronounce", de: "Vorlesen"))

            Button(action: onEdit) {
                Image(systemName: "pencil")
                    .foregroundStyle(AppPalette.accentBlue)
            }
            .accessibilityLabel(L10n.text("\(card.fullGermanTerm) kelimesini düzenle", en: "Edit \(card.fullGermanTerm)", de: "\(card.fullGermanTerm) bearbeiten"))
            .help(L10n.editWordTitle)

            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
                    .foregroundStyle(AppPalette.error)
            }
            .accessibilityLabel(L10n.text("\(card.fullGermanTerm) kelimesini sil", en: "Delete \(card.fullGermanTerm)", de: "\(card.fullGermanTerm) löschen"))
            .help(L10n.text("Kelimeyi Sil", en: "Delete Word", de: "Wort löschen"))
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

/// Converts vertical mouse scroll-wheel events over a horizontal `ScrollView` into smooth horizontal scrolling.
private struct HorizontalWheelScrollBridge: NSViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        context.coordinator.targetView = view
        context.coordinator.installMonitor()
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        context.coordinator.targetView = nsView
    }

    static func dismantleNSView(_ nsView: NSView, coordinator: Coordinator) {
        coordinator.removeMonitor()
    }

    final class Coordinator {
        weak var targetView: NSView?
        private var monitor: Any?

        deinit {
            removeMonitor()
        }

        func installMonitor() {
            guard monitor == nil else { return }
            monitor = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { [weak self] event in
                guard let self,
                      let view = self.targetView,
                      let window = view.window,
                      event.window === window,
                      let scrollView = view.enclosingScrollView else {
                    return event
                }
                let clipView = scrollView.contentView
                let locationInClip = clipView.convert(event.locationInWindow, from: nil)
                guard clipView.bounds.contains(locationInClip) else {
                    return event
                }
                guard abs(event.scrollingDeltaY) > abs(event.scrollingDeltaX),
                      abs(event.scrollingDeltaY) > 0.01,
                      let documentView = scrollView.documentView else {
                    return event
                }
                let maxX = max(0, documentView.bounds.width - clipView.bounds.width)
                guard maxX > 0.5 else {
                    return event
                }
                let multiplier: CGFloat = event.hasPreciseScrollingDeltas ? 1.0 : 18.0
                let delta = event.scrollingDeltaY * multiplier
                let currentX = clipView.bounds.origin.x
                let newX = min(maxX, max(0, currentX - delta))
                if abs(newX - currentX) > 0.01 {
                    clipView.scroll(to: NSPoint(x: newX, y: clipView.bounds.origin.y))
                    scrollView.reflectScrolledClipView(clipView)
                }
                return nil
            }
        }

        func removeMonitor() {
            if let monitor {
                NSEvent.removeMonitor(monitor)
                self.monitor = nil
            }
        }
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

    private var suggestedDecks: [String] {
        [
            L10n.text("A1 Kelimeler", en: "A1 Words", de: "A1 Wörter"),
            L10n.text("A2 Kelimeler", en: "A2 Words", de: "A2 Wörter"),
            L10n.text("B1 Kelimeler", en: "B1 Words", de: "B1 Wörter"),
            L10n.text("B2 Kelimeler", en: "B2 Words", de: "B2 Wörter"),
            L10n.text("C1 Kelimeler", en: "C1 Words", de: "C1 Wörter"),
            L10n.text("Günlük Konuşma", en: "Daily Conversation", de: "Alltagsgespräche"),
            L10n.text("İş Almancası", en: "Business German", de: "Wirtschaftsdeutsch"),
            L10n.text("Yiyecek & İçecek", en: "Food & Drink", de: "Essen & Trinken"),
            L10n.text("Seyahat", en: "Travel", de: "Reisen")
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label(L10n.text("Yeni Deste Oluştur", en: "Create New Deck", de: "Neuen Stapel erstellen"), systemImage: "folder.badge.plus")
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
                .accessibilityLabel(L10n.close)
            }

            TextField(L10n.text("Deste adı (örn: B1 Sınav, Mutfak, Akademik)", en: "Deck name (e.g. B1 Exam, Kitchen, Academic)", de: "Stapelname (z. B. B1-Prüfung, Küche, Studium)"), text: $deckName)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)

            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.text("Hızlı Öneriler:", en: "Quick Suggestions:", de: "Schnellvorschläge:"))
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
                Button(L10n.cancel) {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Spacer()

                Button {
                    saveAndDismiss()
                } label: {
                    Label(L10n.text("Deste Oluştur", en: "Create Deck", de: "Stapel erstellen"), systemImage: "checkmark.circle.fill")
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
            Text(L10n.text("'\(oldName)' Destesinin Adını Değiştir", en: "Rename '\(oldName)' Deck", de: "Stapel ‚\(oldName)‘ umbenennen"))
                .font(.headline)

            TextField(L10n.text("Yeni deste adı", en: "New deck name", de: "Neuer Stapelname"), text: $newName)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)

            HStack {
                Button(L10n.cancel) {
                    dismiss()
                }
                .buttonStyle(.bordered)
                Spacer()
                Button(action: commitRename) {
                    Text(L10n.save)
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

    private var isSaveDisabled: Bool {
        showNewDeckSheet ||
            editWord.trimmingCharacters(in: .whitespaces).isEmpty ||
            (editArtikel == nil && editMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    private var artikelColor: Color {
        editArtikel?.color ?? AppPalette.articleNone
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 10) {
                Text(editArtikel?.rawValue ?? ArtikelFilter.none)
                    .font(.subheadline.bold())
                    .foregroundStyle(artikelColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        editArtikel?.badgeBackground ?? AppPalette.articleNoneBadgeBackground,
                        in: Capsule()
                    )

                Text(L10n.editWordTitle)
                    .font(.title3.bold())

                Spacer()

                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.close)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.ultraThinMaterial)

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Artikel Section
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Artikel")
                                .font(.subheadline.weight(.semibold))
                            Spacer()
                            Text(editArtikel == nil ? L10n.text("Artikelsiz (-)", en: "Article-less (-)", de: "Ohne Artikel (-)") : editArtikel!.grammaticalLabel)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(artikelColor)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(
                                    editArtikel?.badgeBackground ?? AppPalette.articleNoneBadgeBackground,
                                    in: Capsule()
                                )
                        }

                        ArtikelSegmentedControl(selection: $editArtikel)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(14)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                    )

                    // Word & Meaning Section
                    VStack(alignment: .leading, spacing: 10) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L10n.germanWordLabel)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            TextField(L10n.germanWordLabel, text: $editWord)
                                .textFieldStyle(.roundedBorder)
                                .focused($isWordFocused)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text(L10n.meaningLabel)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            TextField(L10n.meaningLabel, text: $editMeaning)
                                .textFieldStyle(.roundedBorder)
                        }

                        if editArtikel == nil && editMeaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Label(L10n.articlelessMeaningRequired, systemImage: "exclamationmark.circle.fill")
                                .font(.caption)
                                .foregroundStyle(AppPalette.accentOrange)
                        }
                    }
                    .padding(14)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                    )

                    // Deck Section
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.deckLabel)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)

                        HStack(spacing: 8) {
                            Menu {
                                ForEach(store.allDeckNames, id: \.self) { existingDeck in
                                    Button {
                                        editDeckName = existingDeck
                                    } label: {
                                        if editDeckName == existingDeck {
                                            Label(existingDeck, systemImage: "checkmark")
                                        } else {
                                            Text(existingDeck)
                                        }
                                    }
                                }
                                Divider()
                                Button {
                                    showNewDeckSheet = true
                                } label: {
                                    Label(L10n.newDeckAddEllipsis, systemImage: "folder.badge.plus")
                                }
                            } label: {
                                HStack {
                                    Image(systemName: "folder.fill")
                                        .foregroundStyle(AppPalette.accentBlue)
                                    Text(editDeckName)
                                        .lineLimit(1)
                                    Spacer()
                                }
                            }
                            .menuStyle(.borderedButton)

                            Button {
                                showNewDeckSheet = true
                            } label: {
                                Label(L10n.newDeckShort, systemImage: "folder.badge.plus")
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(14)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                    )
                }
                .padding(18)
            }

            Divider()

            // Footer
            HStack {
                Button(L10n.cancel) {
                    dismiss()
                }
                .buttonStyle(.bordered)
                .keyboardShortcut(.cancelAction)

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
                    Label(L10n.saveChanges, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(AppPalette.onBlue)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(isSaveDisabled)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.ultraThinMaterial)
        }
        .frame(minWidth: 440, idealWidth: 480, maxWidth: 540, minHeight: 380, idealHeight: 450, maxHeight: 540)
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
