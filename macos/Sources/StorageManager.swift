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

@MainActor
final class DeckStore: ObservableObject {
    @Published var cards: [Flashcard] = []
    @Published var settings: AppSettings = AppSettings()
    @Published var dailyProgress: DailyProgress = DailyProgress.empty()
    @Published var studyHistory: [String: DailyProgress] = [:]
    @Published var studyQueue: [Flashcard] = []
    @Published var currentTime: Date = Date()
    @Published var deletedCardKeys: [String: Date] = [:]

    /// True when cards.json / articleless_cards.json exists but could not be decoded. While set,
    /// local card files are never rewritten and cloud sync never uploads, so a decode failure
    /// cannot wipe local or cloud data.
    @Published private(set) var cardsLoadFailed: Bool = false

    // Cloud Sync State
    @Published var isSyncingCloud: Bool = false
    @Published var cloudSyncStatusMessage: String? = nil
    @Published var cloudSyncIsError: Bool = false
    @Published var lastCloudSyncTime: Date? = nil

    private var autoSyncTask: Task<Void, Never>? = nil

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
    /// Article-less words live in their own optional file so cards.json stays a plain array of
    /// valid der/die/das cards that older app versions can read safely.
    private let articlelessCardsFileURL: URL
    private let settingsFileURL: URL
    private let progressFileURL: URL
    private let historyFileURL: URL
    private let deletedCardsFileURL: URL

    init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        self.storageDirectory = appSupport.appendingPathComponent("ArtikelMeister", isDirectory: true)
        self.importedCSVsDirectory = storageDirectory.appendingPathComponent("ImportedCSVs", isDirectory: true)
        self.backupsDirectory = storageDirectory.appendingPathComponent("Backups", isDirectory: true)
        self.cardsFileURL = storageDirectory.appendingPathComponent("cards.json")
        self.articlelessCardsFileURL = storageDirectory.appendingPathComponent("articleless_cards.json")
        self.settingsFileURL = storageDirectory.appendingPathComponent("settings.json")
        self.progressFileURL = storageDirectory.appendingPathComponent("daily_progress.json")
        self.historyFileURL = storageDirectory.appendingPathComponent("study_history.json")
        self.deletedCardsFileURL = storageDirectory.appendingPathComponent("deleted_cards.json")

        ensureDirectoriesExist()
        loadAll()
        refreshDailyRollover()
        rebuildStudyQueue()
        startLiveDueTimer()
    }

    private func startLiveDueTimer() {
        dueCheckTimer?.invalidate()
        dueCheckTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
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

        var loadFailed = false
        var loaded: [Flashcard] = []
        if fm.fileExists(atPath: cardsFileURL.path) {
            if let data = try? Data(contentsOf: cardsFileURL),
               let list = try? decoder.decode(LossyCardList.self, from: data) {
                loaded += list.cards
            } else {
                loadFailed = true
                preserveCorruptFile(cardsFileURL)
            }
        }
        if fm.fileExists(atPath: articlelessCardsFileURL.path) {
            if let data = try? Data(contentsOf: articlelessCardsFileURL),
               let list = try? decoder.decode(LossyArticlelessCardList.self, from: data) {
                loaded += list.cards
            } else {
                loadFailed = true
                preserveCorruptFile(articlelessCardsFileURL)
            }
        }
        self.cardsLoadFailed = loadFailed
        self.cards = CardMerge.dedupe(loaded)
        if !loadFailed && !fm.fileExists(atPath: cardsFileURL.path) {
            saveCards(triggerCloudSync: false)
        }

        if fm.fileExists(atPath: deletedCardsFileURL.path),
           let data = try? Data(contentsOf: deletedCardsFileURL),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            var map: [String: Date] = [:]
            for (k, v) in json {
                if let d = UniversalDateHelper.parse(v) {
                    map[k] = d
                }
            }
            self.deletedCardKeys = map
        }
    }

    private func preserveCorruptFile(_ url: URL) {
        let backup = url.deletingLastPathComponent()
            .appendingPathComponent("\(url.lastPathComponent).corrupt-\(Int(Date().timeIntervalSince1970))")
        try? FileManager.default.copyItem(at: url, to: backup)
    }

    func saveDeletedCardsFileOnly() {
        ensureDirectoriesExist()
        var strMap: [String: String] = [:]
        for (k, v) in deletedCardKeys {
            strMap[k] = UniversalDateHelper.format(v)
        }
        if let data = try? JSONSerialization.data(withJSONObject: strMap, options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: deletedCardsFileURL, options: .atomic)
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

    func saveCards(triggerCloudSync: Bool = true) {
        ensureDirectoriesExist()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        // Never overwrite files we could not decode.
        if cardsLoadFailed { return }
        // cards.json: ONLY valid der/die/das cards (backward-safe for old clients).
        if let data = try? encoder.encode(cards.filter { !$0.isArticleless }) {
            try? data.write(to: cardsFileURL, options: .atomic)
        }
        let articleless = cards.filter { $0.isArticleless }
        if articleless.isEmpty {
            try? FileManager.default.removeItem(at: articlelessCardsFileURL)
        } else if let data = try? encoder.encode(articleless) {
            try? data.write(to: articlelessCardsFileURL, options: .atomic)
        }
        if triggerCloudSync {
            scheduleAutoCloudSync()
        }
    }

    func updateSettings(transform: (inout AppSettings) -> Void) {
        transform(&settings)
        settings.updatedAt = Date()
        saveSettingsFileOnly()
        rebuildStudyQueue()
        scheduleAutoCloudSync()
    }

    func saveSettings(triggerCloudSync: Bool = true) {
        settings.updatedAt = Date()
        saveSettingsFileOnly()
        rebuildStudyQueue()
        if triggerCloudSync {
            scheduleAutoCloudSync()
        }
    }

    func saveProgress(triggerCloudSync: Bool = true) {
        ensureDirectoriesExist()
        studyHistory[dailyProgress.dateString] = dailyProgress
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(dailyProgress) {
            try? data.write(to: progressFileURL, options: .atomic)
        }
        saveHistory()
        if triggerCloudSync {
            scheduleAutoCloudSync()
        }
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

    /// Counts per article (incl. '-' = article-less) for the whole pool or one deck.
    func artikelCounts(forDeck deckName: String? = nil) -> ArtikelFilter.Counts {
        let pool = deckName == nil ? cards : cards.filter { $0.deckName == deckName }
        return ArtikelFilter.counts(pool)
    }

    var articlelessCount: Int { cards.filter { $0.isArticleless }.count }

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
            settings.updatedAt = Date()
            saveSettingsFileOnly()
            scheduleAutoCloudSync()
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

        let now = Date()
        for idx in cards.indices where cards[idx].deckName == oldName {
            cards[idx].deckName = cleanNew
            cards[idx].updatedAt = now
        }

        if selectedStudyDeck == oldName {
            selectedStudyDeck = cleanNew
        }

        settings.updatedAt = now
        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard: true)
        scheduleAutoCloudSync()
    }

    func deleteDeck(name: String, deleteWords: Bool = false) {
        guard name != "Genel" else { return }
        settings.customDecks.removeAll { $0.lowercased() == name.lowercased() }

        let now = Date()
        if deleteWords {
            let removed = cards.filter { $0.deckName == name }
            for c in removed {
                deletedCardKeys[c.syncKey] = now
            }
            saveDeletedCardsFileOnly()
            cards.removeAll { $0.deckName == name }
        } else {
            for idx in cards.indices where cards[idx].deckName == name {
                cards[idx].deckName = "Genel"
                cards[idx].updatedAt = now
            }
        }

        if selectedStudyDeck == name {
            selectedStudyDeck = nil
        }

        settings.updatedAt = now
        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue()
        scheduleAutoCloudSync()
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

    func addCard(artikel: Artikel?, word: String, meaning: String, deckName: String = "Genel") -> Bool {
        let cleanWord = word.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanMeaning = meaning.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDeck = deckName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel"
            : deckName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanWord.isEmpty else { return false }

        let newKey = Flashcard.keyFor(artikel: artikel, word: cleanWord)
        if let existingIdx = cards.firstIndex(where: { $0.syncKey == newKey }) {
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
                cards[existingIdx].updatedAt = Date()
                saveCards()
                rebuildStudyQueue()
            }
            return false
        }

        let newCard = Flashcard(artikel: artikel, word: cleanWord, meaning: cleanMeaning, deckName: cleanDeck)
        if deletedCardKeys[newCard.syncKey] != nil {
            deletedCardKeys.removeValue(forKey: newCard.syncKey)
            saveDeletedCardsFileOnly()
        }
        cards.append(newCard)
        saveCards()
        rebuildStudyQueue()
        return true
    }

    func updateCard(id: UUID, artikel: Artikel?, word: String, meaning: String, deckName: String) {
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
        cards[idx].updatedAt = Date()
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard: true)
    }

    func deleteCard(id: UUID) {
        if let target = cards.first(where: { $0.id == id }) {
            deletedCardKeys[target.syncKey] = Date()
            saveDeletedCardsFileOnly()
        }
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
            cards[idx].updatedAt = now
        }
        cardDueStepMap.removeAll()
        dailyProgress = DailyProgress.empty(for: now)
        studyHistory.removeAll()
        saveCards()
        saveProgress()
        rebuildStudyQueue(now: now)
    }

    func deleteAllCards() {
        let now = Date()
        for c in cards {
            deletedCardKeys[c.syncKey] = now
        }
        saveDeletedCardsFileOnly()
        cardsLoadFailed = false // explicit user wipe
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
            version: 2,
            exportedAt: now,
            includesProgress: includeProgress,
            cards: exportedCards,
            settings: includeProgress ? settings : nil,
            studyHistory: includeProgress ? studyHistory : nil,
            deletedCardKeys: includeProgress ? deletedCardKeys : nil,
            userEmail: GeminiAuthManager.shared.userEmail.isEmpty ? nil : GeminiAuthManager.shared.userEmail
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(backup)
    }

    func exportBackupJSONString(includeProgress: Bool) -> String {
        guard let data = try? exportBackupJSONData(includeProgress: includeProgress),
              let str = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return str
    }

    func importBackupJSONData(_ data: Data, mergeWithExisting: Bool) throws -> (added: Int, updated: Int) {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let backup = try decoder.decode(ArtikelMeisterBackup.self, from: data)

        if !mergeWithExisting {
            cardsLoadFailed = false // explicit user replace of all cards
            self.cards = CardMerge.dedupe(backup.cards)
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
            for c in backup.cards {
                deletedCardKeys.removeValue(forKey: c.syncKey)
            }
            saveDeletedCardsFileOnly()
            saveCards()
            rebuildStudyQueue()
            return (backup.cards.count, 0)
        } else {
            var added = 0
            var updated = 0
            for incoming in backup.cards {
                deletedCardKeys.removeValue(forKey: incoming.syncKey)
                let incomingKey = incoming.syncKey
                if let idx = cards.firstIndex(where: { $0.syncKey == incomingKey }) {
                    cards[idx] = smartMergeCards(local: cards[idx], incoming: incoming)
                    updated += 1
                } else {
                    cards.append(incoming)
                    added += 1
                }
            }
            saveDeletedCardsFileOnly()
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

    // MARK: - Universal Google Drive Smart Sync & Deduplication Engine

    func scheduleAutoCloudSync() {
        guard GeminiAuthManager.shared.isGoogleAccountConnected else { return }
        autoSyncTask?.cancel()
        autoSyncTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled else { return }
            await self.syncWithGoogleDrive(authManager: .shared, isSilentAutoSync: true)
        }
    }

    func smartMergeCards(local: Flashcard, incoming: Flashcard) -> Flashcard {
        CardMerge.smartMerge(local: local, incoming: incoming)
    }

    func syncWithGoogleDrive(isSilentAutoSync: Bool = false) async {
        await syncWithGoogleDrive(authManager: .shared, isSilentAutoSync: isSilentAutoSync)
    }

    func syncWithGoogleDrive(authManager: GeminiAuthManager, isSilentAutoSync: Bool = false) async {
        guard authManager.isGoogleAccountConnected && !isSyncingCloud else { return }
        if cardsLoadFailed {
            // Local card files could not be decoded: do not merge/upload anything.
            cloudSyncIsError = true
            cloudSyncStatusMessage = "Yerel kelime dosyası okunamadı; veri kaybını önlemek için eşitleme durduruldu."
            return
        }
        isSyncingCloud = true
        if !isSilentAutoSync {
            cloudSyncIsError = false
            cloudSyncStatusMessage = "Google Drive ('\(GoogleDriveSyncService.SYNC_FOLDER_NAME)') ile eşitleniyor..."
        }

        do {
            if authManager.userEmail.isEmpty {
                try? await authManager.fetchUserProfile()
            }

            let snapshot = try await GoogleDriveSyncService.fetchCloudSnapshot(authManager: authManager)
            let cloudBackup = snapshot.backup

            var addedFromCloud = 0
            var overlappingDeduplicated = 0

            if let cloudBackup = cloudBackup {
                // 1. Merge deleted card tombstones
                var mergedDeleted = deletedCardKeys
                if let cloudDeleted = cloudBackup.deletedCardKeys {
                    for (k, ts) in cloudDeleted {
                        let existingTs = mergedDeleted[k] ?? .distantPast
                        if ts > existingTs {
                            mergedDeleted[k] = ts
                        }
                    }
                }
                deletedCardKeys = mergedDeleted
                saveDeletedCardsFileOnly()

                // 2. Deduplicate & Smart-Merge local cards + cloud cards by syncKey
                //    (cloudBackup.cards already contains valid + article-less cards)
                let mergeResult = CardMerge.merge(local: cards, incoming: cloudBackup.cards, tombstones: mergedDeleted)
                addedFromCloud = mergeResult.addedFromIncoming
                overlappingDeduplicated = mergeResult.overlapping
                cards = mergeResult.cards

                // 3. Merge studyHistory & today's dailyProgress
                if let cloudHistory = cloudBackup.studyHistory {
                    var mergedHistory = studyHistory
                    for (dateKey, cloudProg) in cloudHistory {
                        if let localProg = mergedHistory[dateKey] {
                            mergedHistory[dateKey] = DailyProgress(
                                dateString: dateKey,
                                newCardsStudied: max(localProg.newCardsStudied, cloudProg.newCardsStudied),
                                reviewsCompleted: max(localProg.reviewsCompleted, cloudProg.reviewsCompleted),
                                againCount: max(localProg.againCount, cloudProg.againCount),
                                hardCount: max(localProg.hardCount, cloudProg.hardCount),
                                goodCount: max(localProg.goodCount, cloudProg.goodCount),
                                easyCount: max(localProg.easyCount, cloudProg.easyCount)
                            )
                        } else {
                            mergedHistory[dateKey] = cloudProg
                        }
                    }
                    studyHistory = mergedHistory
                    let todayKey = DailyProgress.todayString()
                    if let mergedToday = mergedHistory[todayKey] {
                        dailyProgress = DailyProgress(
                            dateString: todayKey,
                            newCardsStudied: max(dailyProgress.newCardsStudied, mergedToday.newCardsStudied),
                            reviewsCompleted: max(dailyProgress.reviewsCompleted, mergedToday.reviewsCompleted),
                            againCount: max(dailyProgress.againCount, mergedToday.againCount),
                            hardCount: max(dailyProgress.hardCount, mergedToday.hardCount),
                            goodCount: max(dailyProgress.goodCount, mergedToday.goodCount),
                            easyCount: max(dailyProgress.easyCount, mergedToday.easyCount)
                        )
                    }
                    saveProgress(triggerCloudSync: false)
                }

                // 4. Merge user preferences & custom decks
                if let cloudSettings = cloudBackup.settings {
                    var unionDecks = ["Genel"]
                    for d in settings.customDecks where !d.trimmingCharacters(in: .whitespaces).isEmpty && !unionDecks.contains(d) {
                        unionDecks.append(d)
                    }
                    for d in cloudSettings.customDecks where !d.trimmingCharacters(in: .whitespaces).isEmpty && !unionDecks.contains(d) {
                        unionDecks.append(d)
                    }
                    for c in cards where !c.cleanDeckName.isEmpty && !unionDecks.contains(c.cleanDeckName) {
                        unionDecks.append(c.cleanDeckName)
                    }

                    let newerPrefs = (cloudSettings.updatedAt > settings.updatedAt) ? cloudSettings : settings
                    settings = AppSettings(
                        dailyNewCardLimit: newerPrefs.dailyNewCardLimit,
                        dailyReviewLimit: newerPrefs.dailyReviewLimit,
                        autoPlayPronunciation: newerPrefs.autoPlayPronunciation,
                        showMeaningOnFront: newerPrefs.showMeaningOnFront,
                        newCardOrderRandom: newerPrefs.newCardOrderRandom,
                        isDarkMode: newerPrefs.isDarkMode,
                        customDecks: unionDecks,
                        updatedAt: max(settings.updatedAt, cloudSettings.updatedAt)
                    )
                    saveSettingsFileOnly()
                }

                saveCards(triggerCloudSync: false)
                rebuildStudyQueue(preserveCurrentFrontCard: true)
            } else {
                let deduplicated = CardMerge.dedupe(cards)
                if deduplicated.count != cards.count {
                    cards = deduplicated
                    saveCards(triggerCloudSync: false)
                }
            }

            // 5. Upload unified deduplicated backup back to Google Drive
            let unifiedJson = exportBackupJSONString(includeProgress: true)
            _ = try await GoogleDriveSyncService.uploadCloudBackup(
                authManager: authManager,
                folderId: snapshot.folderId,
                existingFileId: snapshot.fileId,
                backupJson: unifiedJson
            )

            lastCloudSyncTime = Date()
            cloudSyncIsError = false
            cloudSyncStatusMessage = (addedFromCloud > 0)
                ? "Bulutla eşitlendi: +\(addedFromCloud) yeni kelime alındı (Toplam \(cards.count) kelime)."
                : "Google Drive ile başarıyla eşitlendi (\(cards.count) kelime, \(currentStreakDays) gün seri)."
        } catch {
            cloudSyncIsError = true
            cloudSyncStatusMessage = "Bulut eşitleme hatası: \(error.localizedDescription)"
        }

        isSyncingCloud = false
    }

    func deleteAccountAndAllData() async {
        await deleteAccountAndAllData(authManager: .shared)
    }

    func deleteAccountAndAllData(authManager: GeminiAuthManager) async {
        autoSyncTask?.cancel()
        isSyncingCloud = true
        cloudSyncIsError = false
        cloudSyncStatusMessage = "Bulut ve yerel hesap verileri tamamen siliniyor..."

        await GoogleDriveSyncService.deleteAllCloudAccountData(authManager: authManager)
        await authManager.revokeGoogleTokenBestEffort()
        authManager.clearAuthorization()

        cards = []
        deletedCardKeys = [:]
        studyHistory = [:]
        dailyProgress = DailyProgress.empty()
        let currentDarkMode = settings.isDarkMode
        settings = AppSettings(isDarkMode: currentDarkMode, updatedAt: Date())
        selectedStudyDeck = nil
        cardDueStepMap.removeAll()

        let fm = FileManager.default
        try? fm.removeItem(at: cardsFileURL)
        try? fm.removeItem(at: articlelessCardsFileURL)
        cardsLoadFailed = false
        try? fm.removeItem(at: settingsFileURL)
        try? fm.removeItem(at: progressFileURL)
        try? fm.removeItem(at: historyFileURL)
        try? fm.removeItem(at: deletedCardsFileURL)
        try? fm.removeItem(at: importedCSVsDirectory)
        try? fm.removeItem(at: backupsDirectory)

        ensureDirectoriesExist()
        saveSettingsFileOnly()
        saveCards(triggerCloudSync: false)
        saveProgress(triggerCloudSync: false)
        saveDeletedCardsFileOnly()
        rebuildStudyQueue()

        lastCloudSyncTime = nil
        cloudSyncIsError = false
        cloudSyncStatusMessage = "Hesap, Google Drive bulut verileri ve yerel veriler tamamen silindi."
        isSyncingCloud = false
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
        // Rows with an empty / non-der/die/das article column become article-less words.
        let outcome = CsvCards.importContent(content, fallbackDeck: defaultDeckName, existing: cards)
        var removedTombstone = false
        for key in outcome.addedKeys where deletedCardKeys.removeValue(forKey: key) != nil {
            removedTombstone = true
        }
        if removedTombstone { saveDeletedCardsFileOnly() }
        cards = outcome.cards
        saveCards()
        rebuildStudyQueue()
        return CSVImportResult(addedCount: outcome.added, updatedCount: outcome.updated, skippedCount: outcome.skipped, invalidLines: outcome.invalid)
    }

    func exportCSVString() -> String {
        CsvCards.exportString(cards)
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
