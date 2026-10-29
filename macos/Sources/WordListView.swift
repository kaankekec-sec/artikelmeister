import SwiftUI
import AppKit

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
    @State private var assignNewDeckToEntryForm: Bool = false
    @State private var deckToRename: String? = nil

    @State private var alertMessage: String? = nil
    @State private var showAlert: Bool = false
    /// True only while the alert shows a successful CSV import result that can still be undone.
    @State private var offerImportUndo: Bool = false
    @State private var showAIGenerator: Bool = false

    private var filteredCards: [Flashcard] {
        store.cards.filter { card in
            if let deckFilter = selectedDeckFilter, card.deckName != deckFilter {
                return false
            }
            if !ArtikelFilter.matches(card, filter: selectedArtikelFilter) {
                return false
            }
            if let stageFilter = selectedStageFilter, card.stage != stageFilter {
                return false
            }
            if !searchText.trimmingCharacters(in: .whitespaces).isEmpty {
                let query = searchText.lowercased()
                let matchesWord = card.word.lowercased().contains(query)
                let matchesMeaning = card.meaning.lowercased().contains(query)
                let matchesArtikel = card.displayArtikel.lowercased().contains(query)
                let matchesDeck = card.deckName.lowercased().contains(query)
                return matchesWord || matchesMeaning || matchesArtikel || matchesDeck
            }
            return true
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top Toolbar: Header + Quick Add + Deck Chips + Search/Filters
            VStack(alignment: .leading, spacing: 12) {
                // Row 1: Title + Main Actions
                HStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Text("Kelime Havuzu")
                            .font(.title3.bold())
                            .lineLimit(1)
                            .fixedSize()

                        Text("\(store.cards.count) kelime")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 9)
                            .padding(.vertical, 3)
                            .background(Color.accentColor.opacity(0.15))
                            .foregroundColor(.accentColor)
                            .clipShape(Capsule())
                            .fixedSize()
                    }

                    Spacer(minLength: 8)

                    HStack(spacing: 8) {
                        Button {
                            showAIGenerator = true
                        } label: {
                            Label("AI Kelime Üret", systemImage: "wand.and.stars")
                                .lineLimit(1)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.purple)
                        .controlSize(.small)
                        .fixedSize()

                        Button {
                            importCSVFromDialog()
                        } label: {
                            Label("CSV İçe Aktar", systemImage: "square.and.arrow.down")
                                .lineLimit(1)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .fixedSize()

                        Button {
                            exportCSVToDialog()
                        } label: {
                            Label("CSV Dışa Aktar", systemImage: "square.and.arrow.up")
                                .lineLimit(1)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .fixedSize()
                        .disabled(store.cards.isEmpty)

                        Button {
                            store.openStorageDirectoryInFinder()
                        } label: {
                            Label("Klasör", systemImage: "folder")
                                .lineLimit(1)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .fixedSize()
                        .help("Yerel Depolama Klasörünü Finder'da Aç")
                    }
                }

                // Row 2: Quick manual word entry bar (clean, no label overflow)
                HStack(spacing: 8) {
                    Picker("", selection: $newArtikel) {
                        ForEach(Artikel.allCases) { art in
                            Text(art.rawValue).tag(Artikel?.some(art))
                        }
                        Text(ArtikelFilter.none).tag(Artikel?.none)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    // Four segments (der/die/das/-) need more than the old 140pt; keep their natural
                    // width so they never draw over the German word field.
                    .frame(width: 176)
                    .fixedSize()
                    .layoutPriority(1)

                    TextField("Almanca kelime (örn: Tisch)", text: $newWord)
                        .textFieldStyle(.roundedBorder)
                        .frame(minWidth: 140, maxWidth: 200)

                    TextField("Türkçe anlamı / açıklama", text: $newMeaning)
                        .textFieldStyle(.roundedBorder)
                        .frame(minWidth: 140)

                    // Deck selector menu for new word with "+ Yeni Deste Ekle..." at bottom
                    Menu {
                        ForEach(store.allDeckNames, id: \.self) { deck in
                            Button {
                                newDeckName = deck
                            } label: {
                                HStack {
                                    Text(deck)
                                    if newDeckName == deck {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                        Divider()
                        Button {
                            assignNewDeckToEntryForm = true
                            showNewDeckSheet = true
                        } label: {
                            Label("Yeni Deste Ekle...", systemImage: "folder.badge.plus")
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "folder.fill")
                                .foregroundColor(.accentColor)
                            Text(newDeckName)
                                .lineLimit(1)
                        }
                        .frame(width: 115, alignment: .leading)
                    }
                    .menuStyle(.borderedButton)
                    .fixedSize()
                    .help("Eklenecek kelimenin destesini seçin")

                    Button {
                        if store.addCard(artikel: newArtikel, word: newWord, meaning: newMeaning, deckName: newDeckName) {
                            newWord = ""
                            newMeaning = ""
                        }
                    } label: {
                        Label("Ekle", systemImage: "plus")
                            .lineLimit(1)
                    }
                    .buttonStyle(.borderedProminent)
                    .fixedSize()
                    .disabled(newWord.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                // Row 3: Decks Bar with "+ Yeni Deste Ekle" button right alongside decks
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
                            .contextMenu {
                                Button {
                                    deckToRename = deck
                                } label: {
                                    Label("Deste Adını Değiştir...", systemImage: "pencil")
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
                        }

                        Button {
                            assignNewDeckToEntryForm = true
                            showNewDeckSheet = true
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "plus.circle.fill")
                                Text("Yeni Deste Ekle")
                                    .font(.caption.weight(.semibold))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.accentColor.opacity(0.15))
                            .foregroundColor(.accentColor)
                            .clipShape(Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(Color.accentColor.opacity(0.35), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .fixedSize()
                    }
                }

                // Row 4: Search & Artikel / Stage Filters (with .labelsHidden() so no label clutter)
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.secondary)
                        TextField("Kelime, anlam veya deste ara...", text: $searchText)
                            .textFieldStyle(.plain)
                        if !searchText.isEmpty {
                            Button {
                                searchText = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(NSColor.textBackgroundColor))
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    Picker("", selection: $selectedArtikelFilter) {
                        Text("Tüm Artikeller").tag(String?.none)
                        ForEach(Artikel.allCases) { art in
                            Text(art.rawValue).tag(String?.some(art.rawValue))
                        }
                        Text(ArtikelFilter.none).tag(String?.some(ArtikelFilter.none))
                    }
                    .labelsHidden()
                    .frame(width: 130)

                    Picker("", selection: $selectedStageFilter) {
                        Text("Tüm Durumlar").tag(CardStage?.none)
                        ForEach(CardStage.allCases, id: \.rawValue) { stg in
                            Text(stg.title).tag(CardStage?.some(stg))
                        }
                    }
                    .labelsHidden()
                    .frame(width: 135)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(NSColor.controlBackgroundColor))

            Divider()

            // Word Table List
            if filteredCards.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "tray")
                        .font(.system(size: 38))
                        .foregroundColor(.secondary)
                    if let deck = selectedDeckFilter {
                        Text("'\(deck)' destesinde henüz kelime yok.")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        Text("Yukarıdaki ekleme çubuğundan veya Gemini AI ile bu desteye yeni kelimeler ekleyebilirsiniz.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text(store.cards.isEmpty ? "Henüz kayıtlı kelime yok." : "Arama kriterlerine uygun kelime bulunamadı.")
                            .font(.headline)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(filteredCards.enumerated()), id: \.element.id) { index, card in
                            HStack(spacing: 12) {
                                // Artikel Badge
                                Text(card.displayArtikel)
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(card.artikel?.color ?? AppPalette.articleNone)
                                    .frame(width: 44, alignment: .center)
                                    .padding(.vertical, 4)
                                    .background(card.artikel?.badgeBackground ?? AppPalette.articleNone.opacity(0.15))
                                    .clipShape(RoundedRectangle(cornerRadius: 6))

                                // Word & Meaning
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(card.word)
                                        .font(.body.weight(.semibold))
                                        .lineLimit(1)
                                    if !card.meaning.isEmpty {
                                        Text(card.meaning)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                }

                                Spacer(minLength: 8)

                                // Deck Tag, Stage & Actions
                                HStack(spacing: 8) {
                                    Label(card.deckName, systemImage: "folder")
                                        .font(.caption2.weight(.medium))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(Color.secondary.opacity(0.12))
                                        .foregroundColor(.secondary)
                                        .clipShape(Capsule())
                                        .lineLimit(1)

                                    Text(card.stage.title)
                                        .font(.caption2.weight(.semibold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(card.stage.color.opacity(0.14))
                                        .foregroundColor(card.stage.color)
                                        .clipShape(Capsule())
                                        .fixedSize()

                                    Text(dueDescription(for: card))
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                        .frame(width: 92, alignment: .trailing)
                                        .lineLimit(1)

                                    Button {
                                        SpeechManager.shared.speak(card.fullGermanTerm)
                                    } label: {
                                        Image(systemName: "speaker.wave.2")
                                    }
                                    .buttonStyle(.borderless)
                                    .help("Seslendir")

                                    Button {
                                        editingCard = card
                                    } label: {
                                        Image(systemName: "pencil")
                                            .foregroundColor(.accentColor)
                                    }
                                    .buttonStyle(.borderless)
                                    .help("Kelimeyi Düzenle")

                                    Button(role: .destructive) {
                                        store.deleteCard(id: card.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundColor(.red.opacity(0.8))
                                    }
                                    .buttonStyle(.borderless)
                                    .help("Kelimeyi Sil")
                                }
                                .fixedSize(horizontal: true, vertical: false)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(
                                index.isMultiple(of: 2)
                                    ? Color.clear
                                    : Color.primary.opacity(0.035)
                            )
                            .contentShape(Rectangle())
                            .onTapGesture(count: 2) {
                                editingCard = card
                            }
                            Divider().opacity(0.4)
                        }
                    }
                }
                .background(Color(NSColor.windowBackgroundColor))
            }
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
        .alert("Bilgi", isPresented: $showAlert) {
            if offerImportUndo {
                Button("Geri al", role: .destructive) {
                    store.undoLastImport()
                    offerImportUndo = false
                }
            }
            Button("Tamam", role: .cancel) { offerImportUndo = false; store.finishImportUndoWindow() }
        } message: {
            Text(alertMessage ?? "")
        }
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
        let panel = NSOpenPanel()
        panel.title = "CSV Dosyası İçe Aktar"
        panel.message = "1. Sütun: Artikel, 2. Sütun: Kelime, 3. Sütun: Anlam, 4. Sütun (İsteğe Bağlı): Deste"
        panel.allowedContentTypes = [.commaSeparatedText, .plainText, .tabSeparatedText]
        panel.allowsMultipleSelection = false

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let targetDeck = selectedDeckFilter ?? newDeckName
                let res = try store.importCSV(from: url, defaultDeckName: targetDeck)
                alertMessage = """
                Yeni eklenen: \(res.addedCount)
                Güncellenen: \(res.updatedCount)
                Atlanan (mevcut): \(res.skippedCount)
                Geçersiz satır: \(res.invalidLines)
                """
                offerImportUndo = store.canUndoImport
                showAlert = true
            } catch {
                offerImportUndo = false
                alertMessage = error.localizedDescription
                showAlert = true
            }
        }
    }

    private func exportCSVToDialog() {
        let panel = NSSavePanel()
        panel.title = "Kelimeleri CSV Olarak Dışa Aktar"
        panel.nameFieldStringValue = "almanca_kelimeler.csv"
        panel.allowedContentTypes = [.commaSeparatedText]

        if panel.runModal() == .OK, let url = panel.url {
            let content = store.exportCSVString()
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                alertMessage = "Kelimeler başarıyla dışa aktarıldı:\n\(url.path)"
                offerImportUndo = false
                showAlert = true
            } catch {
                alertMessage = "Dışa aktarma hatası: \(error.localizedDescription)"
                showAlert = true
            }
        }
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
                Text(title)
                    .font(.caption.weight(isSelected ? .bold : .medium))
                    .lineLimit(1)
                Text("\(count)")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(isSelected ? Color.white.opacity(0.22) : Color.secondary.opacity(0.15))
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(isSelected ? Color.accentColor : Color(NSColor.textBackgroundColor))
            .foregroundColor(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(isSelected ? Color.accentColor : Color.secondary.opacity(0.2), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .fixedSize()
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
            }

            Text("Kelime haznenizi konulara veya seviyelere göre ayırmak için yeni bir deste adı girin:")
                .font(.caption)
                .foregroundColor(.secondary)

            TextField("Deste adı (örn: B1 Sınav, Mutfak, Akademik)", text: $deckName)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)
                .onSubmit {
                    saveAndDismiss()
                }

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
                                Image(systemName: "folder")
                                    .font(.caption2)
                                Text(suggestion)
                                    .font(.caption)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(deckName == suggestion ? Color.accentColor.opacity(0.2) : Color.secondary.opacity(0.1))
                            .foregroundColor(deckName == suggestion ? .accentColor : .primary)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                        .buttonStyle(.plain)
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
                }
                .buttonStyle(.borderedProminent)
                .disabled(deckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 430)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                isFocused = true
            }
        }
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
                .onSubmit {
                    commitRename()
                }

            HStack {
                Button("İptal") {
                    dismiss()
                }
                .buttonStyle(.bordered)
                Spacer()
                Button("Kaydet") {
                    commitRename()
                }
                .buttonStyle(.borderedProminent)
                .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 360)
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
            }

            Divider()

            VStack(alignment: .leading, spacing: 12) {
                Text("Artikel:")
                    .font(.caption.weight(.semibold))
                Picker("Artikel", selection: $editArtikel) {
                    ForEach(Artikel.allCases) { art in
                        Text("\(art.rawValue) (\(art.grammaticalLabel))").tag(Artikel?.some(art))
                    }
                    Text(ArtikelFilter.none).tag(Artikel?.none)
                }
                .pickerStyle(.segmented)
                .labelsHidden()

                Text("Almanca Kelime:")
                    .font(.caption.weight(.semibold))
                TextField("Almanca kelime", text: $editWord)
                    .textFieldStyle(.roundedBorder)

                Text("Türkçe Anlamı / Açıklama:")
                    .font(.caption.weight(.semibold))
                TextField("Türkçe anlamı", text: $editMeaning)
                    .textFieldStyle(.roundedBorder)

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
                                .foregroundColor(.accentColor)
                            Text(editDeckName)
                            Spacer()
                        }
                    }
                    .menuStyle(.borderedButton)

                    Button {
                        showNewDeckSheet = true
                    } label: {
                        Label("Yeni Deste", systemImage: "plus")
                    }
                    .buttonStyle(.bordered)
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
                }
                .buttonStyle(.borderedProminent)
                .disabled(editWord.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(22)
        .frame(width: 440)
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { created in
                editDeckName = created
            }
            .environmentObject(store)
        }
    }
}
