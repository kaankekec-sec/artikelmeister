import Foundation
import SwiftUI
import AppKit

struct CSVImportResult {
    var addedCount: Int
    var updatedCount: Int
    var skippedCount: Int
    var invalidLines: Int
}

struct HeatmapDayItem: Identifiable {
    var id: String { dateString }
    let dateString: String
    let displayDate: String
    let count: Int
}

final class DeckStore: ObservableObject {
    @Published var cards: [Flashcard] = []
    @Published var settings: AppSettings = AppSettings()
    @Published var dailyProgress: DailyProgress = DailyProgress.empty()
    @Published var studyHistory: [String: DailyProgress] = [:]
    @Published var studyQueue: [Flashcard] = []
    @Published var currentTime: Date = Date()

    /// `nil` means "Tüm Desteler" (All Decks); otherwise filters study queue to that specific deck name
    @Published var selectedStudyDeck: String? = nil {
        didSet {
            rebuildStudyQueue()
        }
    }

    /// Tracks how many cards have been answered in the current session so `Tekrar` cards never get stuck behind 20+ new cards
    private var sessionStepCount: Int = 0
    private var cardDueStepMap: [UUID: Int] = [:]
    private var dueCheckTimer: Timer?

    let storageDirectory: URL
    let importedCSVsDirectory: URL
    let backupsDirectory: URL
    private let cardsFileURL: URL
    private let settingsFileURL: URL
    private let progressFileURL: URL
    private let historyFileURL: URL

    init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        self.storageDirectory = appSupport.appendingPathComponent("ArtikelMeister", isDirectory: true)
        self.importedCSVsDirectory = storageDirectory.appendingPathComponent("ImportedCSVs", isDirectory: true)
        self.backupsDirectory = storageDirectory.appendingPathComponent("Backups", isDirectory: true)
        self.cardsFileURL = storageDirectory.appendingPathComponent("cards.json")
        self.settingsFileURL = storageDirectory.appendingPathComponent("settings.json")
        self.progressFileURL = storageDirectory.appendingPathComponent("daily_progress.json")
        self.historyFileURL = storageDirectory.appendingPathComponent("study_history.json")

        ensureDirectoriesExist()
        loadAll()
        refreshDailyRollover()
        rebuildStudyQueue()
        startLiveDueTimer()
    }

    private func startLiveDueTimer() {
        dueCheckTimer?.invalidate()
        dueCheckTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let now = Date()
            self.currentTime = now

            let hasNewlyDueLearning = self.filteredPoolCards.contains { card in
                (card.stage == .learning || card.stage == .relearning) &&
                card.dueDate <= now &&
                !self.studyQueue.contains(where: { $0.id == card.id })
            }

            if hasNewlyDueLearning {
                self.rebuildStudyQueue(now: now, preserveCurrentFrontCard: !self.studyQueue.isEmpty)
            }
        }
    }

    private func ensureDirectoriesExist() {
        try? FileManager.default.createDirectory(at: storageDirectory, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: importedCSVsDirectory, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: backupsDirectory, withIntermediateDirectories: true)
    }

    func openStorageDirectoryInFinder() {
        ensureDirectoriesExist()
        NSWorkspace.shared.open(storageDirectory)
    }

    // MARK: - Persistence

    func loadAll() {
        ensureDirectoriesExist()
        let fm = FileManager.default
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        if fm.fileExists(atPath: settingsFileURL.path),
           let data = try? Data(contentsOf: settingsFileURL),
           let loadedSettings = try? decoder.decode(AppSettings.self, from: data) {
            self.settings = loadedSettings
        } else {
            saveSettingsFileOnly()
        }

        if fm.fileExists(atPath: progressFileURL.path),
           let data = try? Data(contentsOf: progressFileURL),
           let loadedProgress = try? decoder.decode(DailyProgress.self, from: data) {
            self.dailyProgress = loadedProgress
        } else {
            saveProgress()
        }

        if fm.fileExists(atPath: historyFileURL.path),
           let data = try? Data(contentsOf: historyFileURL),
           let loadedHistory = try? decoder.decode([String: DailyProgress].self, from: data) {
            self.studyHistory = loadedHistory
        } else {
            if dailyProgress.totalAnswersCount > 0 {
                studyHistory[dailyProgress.dateString] = dailyProgress
            }
            saveHistory()
        }

        if fm.fileExists(atPath: cardsFileURL.path),
           let data = try? Data(contentsOf: cardsFileURL),
           let loadedCards = try? decoder.decode([Flashcard].self, from: data) {
            self.cards = loadedCards
        } else {
            saveCards()
        }
    }

    private func saveSettingsFileOnly() {
        ensureDirectoriesExist()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(settings) {
            try? data.write(to: settingsFileURL, options: .atomic)
        }
    }

    func saveCards() {
        ensureDirectoriesExist()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(cards) {
            try? data.write(to: cardsFileURL, options: .atomic)
        }
    }

    func saveSettings() {
        saveSettingsFileOnly()
        rebuildStudyQueue()
    }

    func saveProgress() {
        ensureDirectoriesExist()
        studyHistory[dailyProgress.dateString] = dailyProgress
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(dailyProgress) {
            try? data.write(to: progressFileURL, options: .atomic)
        }
        saveHistory()
    }

    func saveHistory() {
        ensureDirectoriesExist()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(studyHistory) {
            try? data.write(to: historyFileURL, options: .atomic)
        }
    }

    func refreshDailyRollover(now: Date = Date()) {
        let today = DailyProgress.todayString(from: now)
        if dailyProgress.dateString != today {
            studyHistory[dailyProgress.dateString] = dailyProgress
            dailyProgress = studyHistory[today] ?? DailyProgress.empty(for: now)
            saveProgress()
        }
    }

    // MARK: - Decks / Categories

    var allDeckNames: [String] {
        var set = Set<String>(["Genel"])
        for custom in settings.customDecks {
            let trimmed = custom.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                set.insert(trimmed)
            }
        }
        for c in cards {
            let trimmed = c.deckName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                set.insert(trimmed)
            }
        }
        return set.sorted { a, b in
            if a == "Genel" { return true }
            if b == "Genel" { return false }
            return a.localizedCaseInsensitiveCompare(b) == .orderedAscending
        }
    }

    func cardCount(forDeck deckName: String?) -> Int {
        guard let deckName = deckName else { return cards.count }
        return cards.filter { $0.deckName == deckName }.count
    }

    @discardableResult
    func createDeck(name: String) -> String? {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return nil }
        if !settings.customDecks.contains(where: { $0.lowercased() == clean.lowercased() }) {
            settings.customDecks.append(clean)
            saveSettingsFileOnly()
        }
        return allDeckNames.first(where: { $0.lowercased() == clean.lowercased() }) ?? clean
    }

    func renameDeck(oldName: String, newName: String) {
        let cleanNew = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanNew.isEmpty, oldName != cleanNew else { return }

        settings.customDecks.removeAll { $0.lowercased() == oldName.lowercased() }
        if !settings.customDecks.contains(where: { $0.lowercased() == cleanNew.lowercased() }) {
            settings.customDecks.append(cleanNew)
        }

        for idx in cards.indices where cards[idx].deckName == oldName {
            cards[idx].deckName = cleanNew
        }

        if selectedStudyDeck == oldName {
            selectedStudyDeck = cleanNew
        }

        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard: true)
    }

    func deleteDeck(name: String, deleteWords: Bool = false) {
        guard name != "Genel" else { return }
        settings.customDecks.removeAll { $0.lowercased() == name.lowercased() }

        if deleteWords {
            cards.removeAll { $0.deckName == name }
        } else {
            for idx in cards.indices where cards[idx].deckName == name {
                cards[idx].deckName = "Genel"
            }
        }

        if selectedStudyDeck == name {
            selectedStudyDeck = nil
        }

        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue()
    }

    var filteredPoolCards: [Flashcard] {
        guard let deck = selectedStudyDeck else { return cards }
        return cards.filter { $0.deckName == deck }
    }

    // MARK: - Streak & Heatmap Calculations

    var currentStreakDays: Int {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"

        var streak = 0
        var checkDate = today

        // If today has no activity yet, check if yesterday had activity to keep the active streak alive
        let todayKey = formatter.string(from: today)
        let todayCount = (studyHistory[todayKey]?.totalAnswersCount ?? 0) + (dailyProgress.dateString == todayKey ? dailyProgress.totalAnswersCount : 0)
        if todayCount == 0 {
            guard let yesterday = cal.date(byAdding: .day, value: -1, to: today) else { return 0 }
            let yKey = formatter.string(from: yesterday)
            if (studyHistory[yKey]?.totalAnswersCount ?? 0) == 0 {
                return 0
            }
            checkDate = yesterday
        }

        while true {
            let key = formatter.string(from: checkDate)
            let count = (key == dailyProgress.dateString)
                ? max(dailyProgress.totalAnswersCount, studyHistory[key]?.totalAnswersCount ?? 0)
                : (studyHistory[key]?.totalAnswersCount ?? 0)
            if count > 0 {
                streak += 1
                guard let prev = cal.date(byAdding: .day, value: -1, to: checkDate) else { break }
                checkDate = prev
            } else {
                break
            }
        }
        return streak
    }

    var longestStreakDays: Int {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let cal = Calendar.current

        var activeDates: [Date] = []
        for (dateStr, prog) in studyHistory where prog.totalAnswersCount > 0 {
            if let d = formatter.date(from: dateStr) {
                activeDates.append(cal.startOfDay(for: d))
            }
        }
        if dailyProgress.totalAnswersCount > 0, let d = formatter.date(from: dailyProgress.dateString) {
            let start = cal.startOfDay(for: d)
            if !activeDates.contains(start) {
                activeDates.append(start)
            }
        }

        let sorted = Array(Set(activeDates)).sorted()
        guard !sorted.isEmpty else { return 0 }

        var maxStreak = 1
        var current = 1
        for i in 1..<sorted.count {
            let diff = cal.dateComponents([.day], from: sorted[i - 1], to: sorted[i]).day ?? 0
            if diff == 1 {
                current += 1
                maxStreak = max(maxStreak, current)
            } else if diff > 1 {
                current = 1
            }
        }
        return max(maxStreak, currentStreakDays)
    }

    func heatmapDays(daysCount: Int = 35) -> [HeatmapDayItem] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        let keyFormatter = DateFormatter()
        keyFormatter.dateFormat = "yyyy-MM-dd"

        let displayFormatter = DateFormatter()
        displayFormatter.locale = Locale(identifier: "tr_TR")
        displayFormatter.dateFormat = "d MMM"

        var items: [HeatmapDayItem] = []
        for offset in stride(from: -(daysCount - 1), through: 0, by: 1) {
            guard let date = cal.date(byAdding: .day, value: offset, to: today) else { continue }
            let key = keyFormatter.string(from: date)
            let disp = displayFormatter.string(from: date)
            let count = (key == dailyProgress.dateString)
                ? max(dailyProgress.totalAnswersCount, studyHistory[key]?.totalAnswersCount ?? 0)
                : (studyHistory[key]?.totalAnswersCount ?? 0)
            items.append(HeatmapDayItem(dateString: key, displayDate: disp, count: count))
        }
        return items
    }

    // MARK: - Study Queue & Anki Scheduling

    var remainingNewToday: Int {
        refreshDailyRollover()
        return max(0, settings.dailyNewCardLimit - dailyProgress.newCardsStudied)
    }

    var remainingReviewsToday: Int {
        refreshDailyRollover()
        return max(0, settings.dailyReviewLimit - dailyProgress.reviewsCompleted)
    }

    var dueLearningCardsCount: Int {
        return filteredPoolCards.filter { $0.stage == .learning || $0.stage == .relearning }.count
    }

    var dueReviewCardsCount: Int {
        let now = Date()
        let totalDue = filteredPoolCards.filter { $0.stage == .review && $0.dueDate <= now }.count
        return min(remainingReviewsToday, totalDue)
    }

    var availableNewCardsCount: Int {
        let totalNew = filteredPoolCards.filter { $0.stage == .new }.count
        return min(remainingNewToday, totalNew)
    }

    /// Returns the earliest learning/relearning card in the active deck that is waiting for its countdown timer
    var nextPendingLearningCard: Flashcard? {
        let now = currentTime
        return filteredPoolCards
            .filter { ($0.stage == .learning || $0.stage == .relearning) && $0.dueDate > now }
            .sorted { $0.dueDate < $1.dueDate }
            .first
    }

    /// Forces the next waiting learning card to become immediately due right now
    func forcePullNextLearningCard() {
        let now = Date()
        guard let nextCard = nextPendingLearningCard,
              let idx = cards.firstIndex(where: { $0.id == nextCard.id }) else { return }
        cards[idx].dueDate = now
        cardDueStepMap.removeValue(forKey: nextCard.id)
        saveCards()
        rebuildStudyQueue(now: now)
    }

    private func isLearningCardDue(_ card: Flashcard, now: Date) -> Bool {
        guard card.stage == .learning || card.stage == .relearning else { return false }
        if card.dueDate <= now {
            return true
        }
        if let targetStep = cardDueStepMap[card.id], sessionStepCount >= targetStep {
            return true
        }
        return false
    }

    func rebuildStudyQueue(now: Date = Date(), preserveCurrentFrontCard: Bool = false) {
        refreshDailyRollover(now: now)
        let pool = filteredPoolCards
        let currentFront = preserveCurrentFrontCard ? studyQueue.first : nil

        // 1. Due learning / relearning cards ALWAYS go at the very front!
        let learningDueNow = pool
            .filter { isLearningCardDue($0, now: now) }
            .sorted { $0.dueDate < $1.dueDate }

        // 2. Due review cards (up to remaining daily review quota)
        let reviewsDue = Array(
            pool
                .filter { $0.stage == .review && $0.dueDate <= now }
                .sorted { $0.dueDate < $1.dueDate }
                .prefix(remainingReviewsToday)
        )

        // 3. New cards (up to remaining daily new quota)
        var allNew = pool.filter { $0.stage == .new }
        if settings.newCardOrderRandom {
            allNew.shuffle()
        } else {
            allNew.sort { $0.createdAt < $1.createdAt }
        }
        let newSlice = Array(allNew.prefix(remainingNewToday))

        var combined: [Flashcard] = []
        combined.append(contentsOf: learningDueNow)

        var rIndex = 0
        var nIndex = 0
        while rIndex < reviewsDue.count || nIndex < newSlice.count {
            for _ in 0..<3 where rIndex < reviewsDue.count {
                combined.append(reviewsDue[rIndex])
                rIndex += 1
            }
            if nIndex < newSlice.count {
                combined.append(newSlice[nIndex])
                nIndex += 1
            }
        }

        if let front = currentFront,
           let latestFront = pool.first(where: { $0.id == front.id }) {
            combined.removeAll { $0.id == latestFront.id }
            combined.insert(latestFront, at: 0)
        }

        self.studyQueue = combined
    }

    func answerCard(_ card: Flashcard, rating: ReviewRating) {
        let now = Date()
        refreshDailyRollover(now: now)
        sessionStepCount += 1

        guard let index = cards.firstIndex(where: { $0.id == card.id }) else { return }
        let previousStage = cards[index].stage

        let result = SM2Scheduler.schedule(card: cards[index], rating: rating, now: now)
        cards[index] = result.updatedCard

        if result.updatedCard.stage == .learning || result.updatedCard.stage == .relearning {
            let otherActiveCardsCount = studyQueue.filter { $0.id != card.id }.count
            if otherActiveCardsCount == 0 {
                cardDueStepMap.removeValue(forKey: card.id)
            } else {
                switch rating {
                case .again:
                    cardDueStepMap[card.id] = sessionStepCount + min(2, otherActiveCardsCount)
                case .hard:
                    cardDueStepMap[card.id] = sessionStepCount + min(5, otherActiveCardsCount)
                case .good:
                    cardDueStepMap[card.id] = sessionStepCount + min(10, otherActiveCardsCount)
                case .easy:
                    cardDueStepMap.removeValue(forKey: card.id)
                }
            }
        } else {
            cardDueStepMap.removeValue(forKey: card.id)
        }

        if previousStage == .new {
            dailyProgress.newCardsStudied += 1
        } else if previousStage == .review {
            dailyProgress.reviewsCompleted += 1
        }

        switch rating {
        case .again: dailyProgress.againCount += 1
        case .hard: dailyProgress.hardCount += 1
        case .good: dailyProgress.goodCount += 1
        case .easy: dailyProgress.easyCount += 1
        }

        saveCards()
        saveProgress()
        rebuildStudyQueue(now: now, preserveCurrentFrontCard: false)
    }

    // MARK: - Word CRUD & Editing

    func addCard(artikel: Artikel, word: String, meaning: String, deckName: String = "Genel") -> Bool {
        let cleanWord = word.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanMeaning = meaning.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDeck = deckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel"
            : deckName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanWord.isEmpty else { return false }

        if let existingIdx = cards.firstIndex(where: {
            $0.artikel == artikel && $0.word.lowercased() == cleanWord.lowercased()
        }) {
            var changed = false
            if !cleanMeaning.isEmpty && cards[existingIdx].meaning.isEmpty {
                cards[existingIdx].meaning = cleanMeaning
                changed = true
            }
            if cleanDeck != "Genel" && cards[existingIdx].deckName == "Genel" {
                cards[existingIdx].deckName = cleanDeck
                changed = true
            }
            if changed {
                saveCards()
                rebuildStudyQueue()
            }
            return false
        }

        let newCard = Flashcard(artikel: artikel, word: cleanWord, meaning: cleanMeaning, deckName: cleanDeck)
        cards.append(newCard)
        saveCards()
        rebuildStudyQueue()
        return true
    }

    func updateCard(id: UUID, artikel: Artikel, word: String, meaning: String, deckName: String) {
        guard let idx = cards.firstIndex(where: { $0.id == id }) else { return }
        let cleanWord = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanWord.isEmpty else { return }
        let cleanMeaning = meaning.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDeck = deckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel"
            : deckName.trimmingCharacters(in: .whitespacesAndNewlines)

        cards[idx].artikel = artikel
        cards[idx].word = cleanWord
        cards[idx].meaning = cleanMeaning
        cards[idx].deckName = cleanDeck
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard: true)
    }

    func deleteCard(id: UUID) {
        cards.removeAll { $0.id == id }
        cardDueStepMap.removeValue(forKey: id)
        if let sel = selectedStudyDeck, !allDeckNames.contains(sel) {
            selectedStudyDeck = nil
        }
        saveCards()
        rebuildStudyQueue()
    }

    func resetAllProgress() {
        let now = Date()
        for idx in cards.indices {
            cards[idx].stage = .new
            cards[idx].easeFactor = 2.5
            cards[idx].intervalDays = 0
            cards[idx].repetitions = 0
            cards[idx].lapses = 0
            cards[idx].dueDate = now
            cards[idx].lastReviewedAt = nil
            cards[idx].totalReviews = 0
        }
        cardDueStepMap.removeAll()
        dailyProgress = DailyProgress.empty(for: now)
        studyHistory.removeAll()
        saveCards()
        saveProgress()
        rebuildStudyQueue(now: now)
    }

    func deleteAllCards() {
        cards.removeAll()
        cardDueStepMap.removeAll()
        selectedStudyDeck = nil
        dailyProgress = DailyProgress.empty()
        saveCards()
        saveProgress()
        rebuildStudyQueue()
    }

    // MARK: - Backup / Restore (.json Package Sharing)

    func exportBackupJSONData(includeProgress: Bool) throws -> Data {
        let now = Date()
        let exportedCards: [Flashcard] = cards.map { card in
            if includeProgress {
                return card
            } else {
                // Clean card for sharing deck with friends without personal review stats
                return Flashcard(
                    id: UUID(),
                    artikel: card.artikel,
                    word: card.word,
                    meaning: card.meaning,
                    deckName: card.deckName,
                    createdAt: now
                )
            }
        }

        let backup = ArtikelMeisterBackup(
            version: 1,
            exportedAt: now,
            includesProgress: includeProgress,
            cards: exportedCards,
            settings: includeProgress ? settings : nil,
            studyHistory: includeProgress ? studyHistory : nil
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(backup)
    }

    func importBackupJSONData(_ data: Data, mergeWithExisting: Bool) throws -> (added: Int, updated: Int) {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let backup = try decoder.decode(ArtikelMeisterBackup.self, from: data)

        if !mergeWithExisting {
            self.cards = backup.cards
            if let s = backup.settings {
                self.settings = s
                saveSettingsFileOnly()
            }
            if let h = backup.studyHistory {
                self.studyHistory = h
                let today = DailyProgress.todayString()
                if let todayProg = h[today] {
                    self.dailyProgress = todayProg
                }
                saveHistory()
            }
            saveCards()
            rebuildStudyQueue()
            return (backup.cards.count, 0)
        } else {
            var added = 0
            var updated = 0
            for incoming in backup.cards {
                if let idx = cards.firstIndex(where: {
                    $0.artikel == incoming.artikel && $0.word.lowercased() == incoming.word.lowercased()
                }) {
                    if !incoming.meaning.isEmpty && cards[idx].meaning.isEmpty {
                        cards[idx].meaning = incoming.meaning
                        updated += 1
                    }
                    if incoming.deckName != "Genel" && cards[idx].deckName == "Genel" {
                        cards[idx].deckName = incoming.deckName
                        updated += 1
                    }
                } else {
                    cards.append(incoming)
                    added += 1
                }
            }
            if let h = backup.studyHistory {
                for (dateKey, prog) in h {
                    if let existingProg = studyHistory[dateKey] {
                        if prog.totalAnswersCount > existingProg.totalAnswersCount {
                            studyHistory[dateKey] = prog
                        }
                    } else {
                        studyHistory[dateKey] = prog
                    }
                }
                saveHistory()
            }
            saveCards()
            rebuildStudyQueue()
            return (added, updated)
        }
    }

    // MARK: - CSV Import / Export

    func importCSV(from url: URL, defaultDeckName: String = "Genel") throws -> CSVImportResult {
        let rawContent: String
        if let utf8 = try? String(contentsOf: url, encoding: .utf8) {
            rawContent = utf8
        } else if let latin1 = try? String(contentsOf: url, encoding: .isoLatin1) {
            rawContent = latin1
        } else {
            throw NSError(domain: "CSVImport", code: 1, userInfo: [NSLocalizedDescriptionKey: "CSV dosyası okunamadı. Lütfen UTF-8 formatında olduğundan emin olun."])
        }

        ensureDirectoriesExist()
        let timestamp = Int(Date().timeIntervalSince1970)
        let backupFileName = "\(timestamp)_\(url.lastPathComponent)"
        let backupURL = importedCSVsDirectory.appendingPathComponent(backupFileName)
        try? rawContent.write(to: backupURL, atomically: true, encoding: .utf8)

        return importCSVContent(rawContent, defaultDeckName: defaultDeckName)
    }

    func importCSVContent(_ content: String, defaultDeckName: String = "Genel") -> CSVImportResult {
        let lines = content.components(separatedBy: .newlines)
        var added = 0
        var updated = 0
        var skipped = 0
        var invalid = 0

        let fallbackDeck = defaultDeckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel"
            : defaultDeckName.trimmingCharacters(in: .whitespacesAndNewlines)

        for (lineIdx, rawLine) in lines.enumerated() {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { continue }

            let columns = parseCSVRow(trimmed)
            guard columns.count >= 2 else {
                invalid += 1
                continue
            }

            let rawArtikel = columns[0].replacingOccurrences(of: "\u{FEFF}", with: "")
            let rawWord = columns[1]
            let rawMeaning = columns.count >= 3 ? columns[2] : ""
            let rawDeck = columns.count >= 4 ? columns[3] : fallbackDeck

            guard let parsedArtikel = Artikel.parse(rawArtikel) else {
                if lineIdx == 0 {
                    continue
                }
                invalid += 1
                continue
            }

            let cleanWord = rawWord.trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanMeaning = rawMeaning.trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanDeck = rawDeck.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? fallbackDeck
                : rawDeck.trimmingCharacters(in: .whitespacesAndNewlines)

            guard !cleanWord.isEmpty else {
                invalid += 1
                continue
            }

            if let existingIdx = cards.firstIndex(where: {
                $0.artikel == parsedArtikel && $0.word.lowercased() == cleanWord.lowercased()
            }) {
                var didUpdate = false
                if !cleanMeaning.isEmpty && cards[existingIdx].meaning != cleanMeaning {
                    cards[existingIdx].meaning = cleanMeaning
                    didUpdate = true
                }
                if cleanDeck != "Genel" && cards[existingIdx].deckName != cleanDeck {
                    cards[existingIdx].deckName = cleanDeck
                    didUpdate = true
                }
                if didUpdate {
                    updated += 1
                } else {
                    skipped += 1
                }
            } else {
                let card = Flashcard(artikel: parsedArtikel, word: cleanWord, meaning: cleanMeaning, deckName: cleanDeck)
                cards.append(card)
                added += 1
            }
        }

        saveCards()
        rebuildStudyQueue()
        return CSVImportResult(addedCount: added, updatedCount: updated, skippedCount: skipped, invalidLines: invalid)
    }

    private func parseCSVRow(_ line: String) -> [String] {
        let delimiter: Character = {
            var inQuotes = false
            var commaCount = 0
            var semicolonCount = 0
            var tabCount = 0
            for ch in line {
                if ch == "\"" { inQuotes.toggle() }
                if !inQuotes {
                    if ch == "," { commaCount += 1 }
                    else if ch == ";" { semicolonCount += 1 }
                    else if ch == "\t" { tabCount += 1 }
                }
            }
            if semicolonCount > commaCount && semicolonCount >= tabCount { return ";" }
            if tabCount > commaCount && tabCount > semicolonCount { return "\t" }
            return ","
        }()

        var fields: [String] = []
        var current = ""
        var inQuotes = false

        for ch in line {
            if ch == "\"" {
                inQuotes.toggle()
            } else if ch == delimiter && !inQuotes {
                fields.append(current.trimmingCharacters(in: .whitespacesAndNewlines))
                current = ""
            } else {
                current.append(ch)
            }
        }
        fields.append(current.trimmingCharacters(in: .whitespacesAndNewlines))
        return fields
    }

    func exportCSVString() -> String {
        var rows = ["artikel,kelime,anlam,deste"]
        for card in cards {
            let escapedWord = card.word.contains(",") ? "\"\(card.word)\"" : card.word
            let escapedMeaning = card.meaning.contains(",") ? "\"\(card.meaning)\"" : card.meaning
            let escapedDeck = card.deckName.contains(",") ? "\"\(card.deckName)\"" : card.deckName
            rows.append("\(card.artikel.rawValue),\(escapedWord),\(escapedMeaning),\(escapedDeck)")
        }
        return rows.joined(separator: "\n")
    }

    func loadStarterGermanPack() -> CSVImportResult {
        let sampleCSV = """
        artikel,kelime,anlam,deste
        der,Tisch,Masa,A1 Temel
        die,Katze,Kedi,A1 Temel
        das,Buch,Kitap,A1 Temel
        der,Stuhl,Sandalye,A1 Temel
        die,Sonne,Güneş,A1 Temel
        das,Haus,Ev,A1 Temel
        der,Apfel,Elma,Yiyecek & İçecek
        die,Blume,Çiçek,Doğa
        das,Auto,Araba,Ulaşım
        der,Hund,Köpek,A1 Temel
        die,Schule,Okul,Eğitim
        das,Wasser,Su,Yiyecek & İçecek
        der,Baum,Ağaç,Doğa
        die,Tür,Kapı,A1 Temel
        das,Fenster,Pencere,A1 Temel
        der,Schlüssel,Anahtar,Günlük Eşyalar
        die,Tasche,Çanta,Günlük Eşyalar
        das,Kind,Çocuk,A1 Temel
        der,Zug,Tren,Ulaşım
        die,Stadt,Şehir,Ulaşım
        das,Mädchen,Kız çocuk,A1 Temel
        der,Kaffee,Kahve,Yiyecek & İçecek
        die,Milch,Süt,Yiyecek & İçecek
        das,Brot,Ekmek,Yiyecek & İçecek
        der,Bahnhof,Tren istasyonu,Ulaşım
        die,Zeitung,Gazete,Günlük Eşyalar
        das,Handy,Cep telefonu,Günlük Eşyalar
        der,Computer,Bilgisayar,Günlük Eşyalar
        die,Uhr,Saat,Günlük Eşyalar
        das,Zimmer,Oda,A1 Temel
        """
        return importCSVContent(sampleCSV, defaultDeckName: "A1 Temel")
    }
}
