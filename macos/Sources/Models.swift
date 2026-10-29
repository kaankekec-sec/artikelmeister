import Foundation
import SwiftUI

/// Shared palette, identical to android ui/Theme.kt (dark / light variants). Article colours are clearly
/// separated from the success green and error red, so correct/wrong feedback never clashes with die/das.
enum AppPalette {
    private static func rgb(_ hex: UInt32) -> NSColor {
        NSColor(
            srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }

    /// Appearance-aware colour: `dark` in dark mode, `light` otherwise.
    static func dynamic(dark: UInt32, light: UInt32) -> Color {
        Color(NSColor(name: nil) { appearance in
            appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? rgb(dark) : rgb(light)
        })
    }

    static let der = dynamic(dark: 0x4C8DFF, light: 0x1F63E0)      // blue
    static let die = dynamic(dark: 0xF062C0, light: 0xC0268F)      // magenta
    static let das = dynamic(dark: 0xE8B90E, light: 0x946C00)      // gold
    static let articleNone = dynamic(dark: 0xA1A1AA, light: 0x6B7280) // grey ('-')
    static let success = dynamic(dark: 0x30D158, light: 0x1E8E3E)
    static let error = dynamic(dark: 0xFF453A, light: 0xD92D20)
}

enum Artikel: String, Codable, CaseIterable, Identifiable {
    case der = "der"
    case die = "die"
    case das = "das"

    var id: String { rawValue }

    var color: Color {
        switch self {
        case .der: return AppPalette.der // blue (masculine)
        case .die: return AppPalette.die // magenta (feminine)
        case .das: return AppPalette.das // gold (neuter)
        }
    }

    var badgeBackground: Color {
        color.opacity(0.15)
    }

    var grammaticalLabel: String {
        switch self {
        case .der: return "Maskulin (Eril)"
        case .die: return "Feminin (Dişil)"
        case .das: return "Neutrum (Nötr)"
        }
    }

    static func parse(_ raw: String) -> Artikel? {
        let cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch cleaned {
        case "der", "m", "maskulin", "masculine": return .der
        case "die", "f", "feminin", "feminine": return .die
        case "das", "n", "neutrum", "neuter": return .das
        default: return nil
        }
    }
}

enum CardStage: String, Codable, CaseIterable {
    case new = "new"
    case learning = "learning"
    case review = "review"
    case relearning = "relearning"

    var title: String {
        switch self {
        case .new: return "Yeni"
        case .learning: return "Öğreniliyor"
        case .review: return "Tekrar"
        case .relearning: return "Yeniden Öğreniliyor"
        }
    }

    var color: Color {
        switch self {
        case .new: return .blue
        case .learning: return .orange
        case .review: return .green
        case .relearning: return .red
        }
    }
}

enum ReviewRating: Int, CaseIterable {
    case again = 1
    case hard = 2
    case good = 3
    case easy = 4

    var title: String {
        switch self {
        case .again: return "Tekrar"
        case .hard: return "Zor"
        case .good: return "İyi"
        case .easy: return "Kolay"
        }
    }

    var shortcutKey: String {
        "\(rawValue)"
    }

    var color: Color {
        switch self {
        case .again: return AppPalette.error
        case .hard: return Color(red: 0.92, green: 0.55, blue: 0.15)
        case .good: return AppPalette.success
        case .easy: return Color(red: 0.20, green: 0.52, blue: 0.95)
        }
    }
}

enum UniversalDateHelper {
    static let isoFormatterWithFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func format(_ date: Date) -> String {
        isoFormatter.string(from: date)
    }

    static func parse(_ raw: Any?) -> Date? {
        guard let raw = raw else { return nil }
        if let d = raw as? Date { return d }
        if let str = raw as? String {
            if let d = isoFormatterWithFraction.date(from: str) { return d }
            if let d = isoFormatter.date(from: str) { return d }
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZZZZZ"
            if let d = f.date(from: str) { return d }
            f.dateFormat = "yyyy-MM-dd"
            if let d = f.date(from: str) { return d }
            return nil
        }
        if let num = raw as? NSNumber {
            let val = num.doubleValue
            if val > 1_000_000_000_000 {
                return Date(timeIntervalSince1970: val / 1000.0)
            } else {
                return Date(timeIntervalSince1970: val)
            }
        }
        return nil
    }
}

struct Flashcard: Identifiable, Codable, Equatable {
    var id: UUID
    /// nil = article-less word ("artikelsiz").
    var artikel: Artikel?
    var word: String
    var meaning: String
    var deckName: String
    var createdAt: Date
    var updatedAt: Date

    // Spaced Repetition (Anki SM-2) state
    var stage: CardStage
    var easeFactor: Double      // Default 2.5, min 1.3
    var intervalDays: Double    // Interval in days (can be fractional for intraday learning)
    var repetitions: Int        // Consecutive successful recalls
    var lapses: Int             // Times forgotten
    var dueDate: Date           // Next scheduled review timestamp
    var lastReviewedAt: Date?
    var totalReviews: Int

    var cleanDeckName: String {
        let cleaned = deckName.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? "Genel" : cleaned
    }

    var isArticleless: Bool { artikel == nil }

    /// "der"/"die"/"das", or "-" for article-less words (display only).
    var displayArtikel: String { artikel?.rawValue ?? ArtikelFilter.none }

    var fullGermanTerm: String {
        if let artikel = artikel { return "\(artikel.rawValue) \(word)" }
        return word
    }

    /// Dedup / tombstone key. Valid cards: "der_tisch". Article-less: "none_tisch" (word only),
    /// so article-less 'Tisch' and 'der Tisch' stay distinct. Mirrors Android Flashcard.keyFor.
    var syncKey: String { Flashcard.keyFor(artikel: artikel, word: word) }

    static func keyFor(artikel: Artikel?, word: String) -> String {
        let w = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let artikel = artikel { return "\(artikel.rawValue)_\(w)" }
        return "none_\(w)"
    }

    init(
        id: UUID = UUID(),
        artikel: Artikel?,
        word: String,
        meaning: String = "",
        deckName: String = "Genel",
        createdAt: Date = Date(),
        updatedAt: Date? = nil,
        stage: CardStage = .new,
        easeFactor: Double = 2.5,
        intervalDays: Double = 0,
        repetitions: Int = 0,
        lapses: Int = 0,
        dueDate: Date = Date(),
        lastReviewedAt: Date? = nil,
        totalReviews: Int = 0
    ) {
        self.id = id
        self.artikel = artikel
        self.word = word
        self.meaning = meaning
        let cleanedDeck = deckName.trimmingCharacters(in: .whitespacesAndNewlines)
        self.deckName = cleanedDeck.isEmpty ? "Genel" : cleanedDeck
        self.createdAt = createdAt
        self.updatedAt = updatedAt ?? lastReviewedAt ?? createdAt
        self.stage = stage
        self.easeFactor = easeFactor
        self.intervalDays = intervalDays
        self.repetitions = repetitions
        self.lapses = lapses
        self.dueDate = dueDate
        self.lastReviewedAt = lastReviewedAt
        self.totalReviews = totalReviews
    }

    enum CodingKeys: String, CodingKey {
        case id, artikel, word, meaning, deckName, createdAt, updatedAt
        case stage, easeFactor, intervalDays, repetitions, lapses, dueDate, lastReviewedAt, totalReviews
    }

    init(from decoder: Decoder) throws {
        try self.init(from: decoder, articleless: false)
    }

    /// - articleless: true when decoding an entry of "articlelessCards" (the "artikel" field
    ///   is ignored). When false (entries of "cards"), a missing/invalid article THROWS so the
    ///   lenient list decoder can skip just this card.
    init(from decoder: Decoder, articleless: Bool) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let uuid = try? container.decode(UUID.self, forKey: .id) {
            self.id = uuid
        } else if let idStr = try? container.decode(String.self, forKey: .id), let uuid = UUID(uuidString: idStr) {
            self.id = uuid
        } else {
            self.id = UUID()
        }
        if articleless {
            self.artikel = nil
        } else {
            self.artikel = try container.decode(Artikel.self, forKey: .artikel)
        }
        let decodedWord = try container.decode(String.self, forKey: .word)
        guard !decodedWord.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DecodingError.dataCorruptedError(forKey: .word, in: container, debugDescription: "empty word")
        }
        self.word = decodedWord
        self.meaning = try container.decodeIfPresent(String.self, forKey: .meaning) ?? ""
        let rawDeck = try container.decodeIfPresent(String.self, forKey: .deckName)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Genel"
        self.deckName = rawDeck.isEmpty ? "Genel" : rawDeck

        let parsedCreatedAt: Date
        if let dateVal = try? container.decode(Date.self, forKey: .createdAt) {
            parsedCreatedAt = dateVal
        } else if let strVal = try? container.decode(String.self, forKey: .createdAt), let d = UniversalDateHelper.parse(strVal) {
            parsedCreatedAt = d
        } else if let numVal = try? container.decode(Double.self, forKey: .createdAt) {
            parsedCreatedAt = UniversalDateHelper.parse(numVal) ?? Date()
        } else {
            parsedCreatedAt = Date()
        }
        self.createdAt = parsedCreatedAt

        let parsedLastReviewed: Date?
        if let dateVal = try? container.decodeIfPresent(Date.self, forKey: .lastReviewedAt) {
            parsedLastReviewed = dateVal
        } else if let strVal = try? container.decodeIfPresent(String.self, forKey: .lastReviewedAt), let d = UniversalDateHelper.parse(strVal) {
            parsedLastReviewed = d
        } else if let numVal = try? container.decodeIfPresent(Double.self, forKey: .lastReviewedAt) {
            parsedLastReviewed = UniversalDateHelper.parse(numVal)
        } else {
            parsedLastReviewed = nil
        }
        self.lastReviewedAt = parsedLastReviewed

        let parsedUpdatedAt: Date
        if let dateVal = try? container.decode(Date.self, forKey: .updatedAt) {
            parsedUpdatedAt = dateVal
        } else if let strVal = try? container.decode(String.self, forKey: .updatedAt), let d = UniversalDateHelper.parse(strVal) {
            parsedUpdatedAt = d
        } else if let numVal = try? container.decode(Double.self, forKey: .updatedAt) {
            parsedUpdatedAt = UniversalDateHelper.parse(numVal) ?? (parsedLastReviewed ?? parsedCreatedAt)
        } else {
            parsedUpdatedAt = parsedLastReviewed ?? parsedCreatedAt
        }
        self.updatedAt = parsedUpdatedAt

        self.stage = try container.decode(CardStage.self, forKey: .stage)
        self.easeFactor = try container.decode(Double.self, forKey: .easeFactor)
        self.intervalDays = try container.decode(Double.self, forKey: .intervalDays)
        self.repetitions = try container.decode(Int.self, forKey: .repetitions)
        self.lapses = try container.decode(Int.self, forKey: .lapses)

        let parsedDueDate: Date
        if let dateVal = try? container.decode(Date.self, forKey: .dueDate) {
            parsedDueDate = dateVal
        } else if let strVal = try? container.decode(String.self, forKey: .dueDate), let d = UniversalDateHelper.parse(strVal) {
            parsedDueDate = d
        } else if let numVal = try? container.decode(Double.self, forKey: .dueDate) {
            parsedDueDate = UniversalDateHelper.parse(numVal) ?? Date()
        } else {
            parsedDueDate = Date()
        }
        self.dueDate = parsedDueDate

        self.totalReviews = try container.decodeIfPresent(Int.self, forKey: .totalReviews) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id.uuidString, forKey: .id)
        // Article-less cards are persisted in the separate "articlelessCards" list and carry
        // no "artikel" field.
        if let artikel = artikel {
            try container.encode(artikel, forKey: .artikel)
        }
        try container.encode(word, forKey: .word)
        try container.encode(meaning, forKey: .meaning)
        try container.encode(cleanDeckName, forKey: .deckName)
        try container.encode(UniversalDateHelper.format(createdAt), forKey: .createdAt)
        try container.encode(UniversalDateHelper.format(updatedAt), forKey: .updatedAt)
        try container.encode(stage, forKey: .stage)
        try container.encode(easeFactor, forKey: .easeFactor)
        try container.encode(intervalDays, forKey: .intervalDays)
        try container.encode(repetitions, forKey: .repetitions)
        try container.encode(lapses, forKey: .lapses)
        try container.encode(UniversalDateHelper.format(dueDate), forKey: .dueDate)
        if let last = lastReviewedAt {
            try container.encode(UniversalDateHelper.format(last), forKey: .lastReviewedAt)
        }
        try container.encode(totalReviews, forKey: .totalReviews)
    }
}

/// Lenient decoder for a JSON array of cards: a bad entry is skipped, it never fails the
/// whole decode (throws only if the value is not an array at all).
struct LossyCardList: Decodable {
    let cards: [Flashcard]

    init(cards: [Flashcard]) { self.cards = cards }

    init(from decoder: Decoder) throws {
        try self.init(from: decoder, articleless: false)
    }

    init(from decoder: Decoder, articleless: Bool) throws {
        var container = try decoder.unkeyedContainer()
        var result: [Flashcard] = []
        while !container.isAtEnd {
            // superDecoder() always advances past the element, even when the card is invalid.
            let element = try container.superDecoder()
            if let card = try? Flashcard(from: element, articleless: articleless) {
                result.append(card)
            }
        }
        self.cards = result
    }
}

/// Same as LossyCardList but for "articlelessCards" entries.
struct LossyArticlelessCardList: Decodable {
    let cards: [Flashcard]
    init(from decoder: Decoder) throws {
        self.cards = try LossyCardList(from: decoder, articleless: true).cards
    }
}

struct AppSettings: Codable, Equatable {
    var dailyNewCardLimit: Int = 20
    var dailyReviewLimit: Int = 100
    var autoPlayPronunciation: Bool = false
    var showMeaningOnFront: Bool = false
    var newCardOrderRandom: Bool = false
    var isDarkMode: Bool = true
    var customDecks: [String] = ["Genel"]
    var updatedAt: Date = Date()

    enum CodingKeys: String, CodingKey {
        case dailyNewCardLimit, dailyReviewLimit, autoPlayPronunciation, showMeaningOnFront, newCardOrderRandom, isDarkMode, customDecks, updatedAt
    }

    init(
        dailyNewCardLimit: Int = 20,
        dailyReviewLimit: Int = 100,
        autoPlayPronunciation: Bool = false,
        showMeaningOnFront: Bool = false,
        newCardOrderRandom: Bool = false,
        isDarkMode: Bool = true,
        customDecks: [String] = ["Genel"],
        updatedAt: Date = Date()
    ) {
        self.dailyNewCardLimit = dailyNewCardLimit
        self.dailyReviewLimit = dailyReviewLimit
        self.autoPlayPronunciation = autoPlayPronunciation
        self.showMeaningOnFront = showMeaningOnFront
        self.newCardOrderRandom = newCardOrderRandom
        self.isDarkMode = isDarkMode
        self.customDecks = customDecks
        self.updatedAt = updatedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.dailyNewCardLimit = try c.decodeIfPresent(Int.self, forKey: .dailyNewCardLimit) ?? 20
        self.dailyReviewLimit = try c.decodeIfPresent(Int.self, forKey: .dailyReviewLimit) ?? 100
        self.autoPlayPronunciation = try c.decodeIfPresent(Bool.self, forKey: .autoPlayPronunciation) ?? false
        self.showMeaningOnFront = try c.decodeIfPresent(Bool.self, forKey: .showMeaningOnFront) ?? false
        self.newCardOrderRandom = try c.decodeIfPresent(Bool.self, forKey: .newCardOrderRandom) ?? false
        self.isDarkMode = try c.decodeIfPresent(Bool.self, forKey: .isDarkMode) ?? true
        self.customDecks = try c.decodeIfPresent([String].self, forKey: .customDecks) ?? ["Genel"]

        if let dateVal = try? c.decode(Date.self, forKey: .updatedAt) {
            self.updatedAt = dateVal
        } else if let strVal = try? c.decode(String.self, forKey: .updatedAt), let d = UniversalDateHelper.parse(strVal) {
            self.updatedAt = d
        } else if let numVal = try? c.decode(Double.self, forKey: .updatedAt) {
            self.updatedAt = UniversalDateHelper.parse(numVal) ?? Date()
        } else {
            self.updatedAt = Date()
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(dailyNewCardLimit, forKey: .dailyNewCardLimit)
        try c.encode(dailyReviewLimit, forKey: .dailyReviewLimit)
        try c.encode(autoPlayPronunciation, forKey: .autoPlayPronunciation)
        try c.encode(showMeaningOnFront, forKey: .showMeaningOnFront)
        try c.encode(newCardOrderRandom, forKey: .newCardOrderRandom)
        try c.encode(isDarkMode, forKey: .isDarkMode)
        try c.encode(customDecks, forKey: .customDecks)
        try c.encode(UniversalDateHelper.format(updatedAt), forKey: .updatedAt)
    }
}

struct DailyProgress: Codable, Equatable {
    var dateString: String
    var newCardsStudied: Int
    var reviewsCompleted: Int
    var againCount: Int
    var hardCount: Int
    var goodCount: Int
    var easyCount: Int

    var totalAnswersCount: Int {
        againCount + hardCount + goodCount + easyCount
    }

    static func todayString(from date: Date = Date()) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func empty(for date: Date = Date()) -> DailyProgress {
        DailyProgress(
            dateString: todayString(from: date),
            newCardsStudied: 0,
            reviewsCompleted: 0,
            againCount: 0,
            hardCount: 0,
            goodCount: 0,
            easyCount: 0
        )
    }
}

struct ArtikelMeisterBackup: Codable {
    var version: Int = 2
    var exportedAt: Date = Date()
    var includesProgress: Bool = true
    /// ALL cards in memory (valid + article-less); split into two arrays when encoded.
    var cards: [Flashcard]
    var settings: AppSettings? = nil
    var studyHistory: [String: DailyProgress]? = nil
    var deletedCardKeys: [String: Date]? = nil
    var userEmail: String? = nil

    enum CodingKeys: String, CodingKey {
        case version, exportedAt, includesProgress, cards, articlelessCards, settings, studyHistory, deletedCardKeys, userEmail
    }

    init(
        version: Int = 2,
        exportedAt: Date = Date(),
        includesProgress: Bool = true,
        cards: [Flashcard],
        settings: AppSettings? = nil,
        studyHistory: [String: DailyProgress]? = nil,
        deletedCardKeys: [String: Date]? = nil,
        userEmail: String? = nil
    ) {
        self.version = version
        self.exportedAt = exportedAt
        self.includesProgress = includesProgress
        self.cards = cards
        self.settings = settings
        self.studyHistory = studyHistory
        self.deletedCardKeys = deletedCardKeys
        self.userEmail = userEmail
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        if let d = try? c.decode(Date.self, forKey: .exportedAt) {
            self.exportedAt = d
        } else if let s = try? c.decode(String.self, forKey: .exportedAt), let d = UniversalDateHelper.parse(s) {
            self.exportedAt = d
        } else if let n = try? c.decode(Double.self, forKey: .exportedAt) {
            self.exportedAt = UniversalDateHelper.parse(n) ?? Date()
        } else {
            self.exportedAt = Date()
        }
        self.includesProgress = try c.decodeIfPresent(Bool.self, forKey: .includesProgress) ?? true
        // Lenient: bad cards are skipped; only a non-array value throws (callers then abort
        // instead of overwriting data).
        var allCards: [Flashcard] = []
        if c.contains(.cards), (try? c.decodeNil(forKey: .cards)) == false {
            allCards += try LossyCardList(from: try c.superDecoder(forKey: .cards)).cards
        }
        if c.contains(.articlelessCards), (try? c.decodeNil(forKey: .articlelessCards)) == false {
            allCards += try LossyArticlelessCardList(from: try c.superDecoder(forKey: .articlelessCards)).cards
        }
        self.cards = allCards
        self.settings = try c.decodeIfPresent(AppSettings.self, forKey: .settings)
        self.studyHistory = try c.decodeIfPresent([String: DailyProgress].self, forKey: .studyHistory)

        if let rawDeleted = try? c.decodeIfPresent([String: String].self, forKey: .deletedCardKeys) {
            var parsedMap: [String: Date] = [:]
            for (k, v) in rawDeleted {
                if let d = UniversalDateHelper.parse(v) {
                    parsedMap[k] = d
                }
            }
            self.deletedCardKeys = parsedMap
        } else if let rawDeletedDates = try? c.decodeIfPresent([String: Date].self, forKey: .deletedCardKeys) {
            self.deletedCardKeys = rawDeletedDates
        } else {
            self.deletedCardKeys = nil
        }
        self.userEmail = try c.decodeIfPresent(String.self, forKey: .userEmail)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(version, forKey: .version)
        try c.encode(UniversalDateHelper.format(exportedAt), forKey: .exportedAt)
        try c.encode(includesProgress, forKey: .includesProgress)
        // "cards" ONLY holds valid der/die/das cards (older clients choke on others).
        try c.encode(cards.filter { !$0.isArticleless }, forKey: .cards)
        let articleless = cards.filter { $0.isArticleless }
        if !articleless.isEmpty {
            try c.encode(articleless, forKey: .articlelessCards)
        }
        if let settings = settings {
            try c.encode(settings, forKey: .settings)
        }
        if let studyHistory = studyHistory {
            try c.encode(studyHistory, forKey: .studyHistory)
        }
        if let deletedCardKeys = deletedCardKeys {
            var rawMap: [String: String] = [:]
            for (k, v) in deletedCardKeys {
                rawMap[k] = UniversalDateHelper.format(v)
            }
            try c.encode(rawMap, forKey: .deletedCardKeys)
        }
        if let userEmail = userEmail, !userEmail.isEmpty {
            try c.encode(userEmail, forKey: .userEmail)
        }
    }
}

/// Anki SM-2 inspired spaced repetition scheduler
enum SM2Scheduler {
    struct ScheduleResult {
        var updatedCard: Flashcard
        var intervalDescription: String
    }

    static func previewInterval(for card: Flashcard, rating: ReviewRating, now: Date = Date()) -> String {
        return schedule(card: card, rating: rating, now: now).intervalDescription
    }

    static func schedule(card: Flashcard, rating: ReviewRating, now: Date = Date()) -> ScheduleResult {
        var updated = card
        updated.lastReviewedAt = now
        updated.totalReviews += 1

        let minute: TimeInterval = 60
        let day: TimeInterval = 86_400

        switch card.stage {
        case .new, .learning:
            switch rating {
            case .again:
                updated.stage = .learning
                updated.repetitions = 0
                updated.intervalDays = 0.5 / (24.0 * 60.0) // 30 sec
                updated.dueDate = now.addingTimeInterval(30)
                return ScheduleResult(updatedCard: updated, intervalDescription: "30 sn")

            case .hard:
                updated.stage = .learning
                updated.intervalDays = 2.0 / (24.0 * 60.0) // 2 min
                updated.dueDate = now.addingTimeInterval(2 * minute)
                return ScheduleResult(updatedCard: updated, intervalDescription: "2 dk")

            case .good:
                if card.stage == .new && card.repetitions == 0 {
                    updated.stage = .learning
                    updated.repetitions = 1
                    updated.intervalDays = 5.0 / (24.0 * 60.0) // 5 min
                    updated.dueDate = now.addingTimeInterval(5 * minute)
                    return ScheduleResult(updatedCard: updated, intervalDescription: "5 dk")
                } else {
                    updated.stage = .review
                    updated.repetitions = max(1, card.repetitions + 1)
                    updated.intervalDays = 1.0
                    updated.dueDate = now.addingTimeInterval(1 * day)
                    return ScheduleResult(updatedCard: updated, intervalDescription: "1 gün")
                }

            case .easy:
                updated.stage = .review
                updated.repetitions = max(1, card.repetitions + 1)
                updated.intervalDays = 4.0
                updated.easeFactor = max(1.3, card.easeFactor + 0.15)
                updated.dueDate = now.addingTimeInterval(4 * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: "4 gün")
            }

        case .relearning:
            switch rating {
            case .again:
                updated.intervalDays = 0.5 / (24.0 * 60.0)
                updated.dueDate = now.addingTimeInterval(30)
                return ScheduleResult(updatedCard: updated, intervalDescription: "30 sn")

            case .hard:
                updated.intervalDays = 2.0 / (24.0 * 60.0)
                updated.dueDate = now.addingTimeInterval(2 * minute)
                return ScheduleResult(updatedCard: updated, intervalDescription: "2 dk")

            case .good:
                updated.stage = .review
                let nextDays = max(1.0, round(card.intervalDays * 0.5))
                updated.intervalDays = nextDays
                updated.dueDate = now.addingTimeInterval(nextDays * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: formatDays(nextDays))

            case .easy:
                updated.stage = .review
                let nextDays = max(2.0, round(card.intervalDays * 1.2))
                updated.intervalDays = nextDays
                updated.dueDate = now.addingTimeInterval(nextDays * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: formatDays(nextDays))
            }

        case .review:
            let currentInterval = max(1.0, card.intervalDays)
            // Calculate actual elapsed days if overdue
            let elapsedDays: Double
            if let last = card.lastReviewedAt {
                elapsedDays = max(currentInterval, now.timeIntervalSince(last) / day)
            } else {
                elapsedDays = currentInterval
            }

            switch rating {
            case .again:
                updated.stage = .relearning
                updated.lapses += 1
                updated.repetitions = 0
                updated.easeFactor = max(1.3, card.easeFactor - 0.20)
                // Keep previous interval reference scaled down for when relearned
                updated.intervalDays = max(1.0, round(currentInterval * 0.4))
                updated.dueDate = now.addingTimeInterval(30)
                return ScheduleResult(updatedCard: updated, intervalDescription: "30 sn")

            case .hard:
                updated.stage = .review
                updated.easeFactor = max(1.3, card.easeFactor - 0.15)
                let nextDays = max(currentInterval + 1.0, round(currentInterval * 1.2))
                updated.intervalDays = min(3650, nextDays)
                updated.dueDate = now.addingTimeInterval(updated.intervalDays * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: formatDays(updated.intervalDays))

            case .good:
                updated.stage = .review
                updated.repetitions += 1
                let bonus = (elapsedDays - currentInterval) * 0.5
                let nextDays = max(currentInterval + 1.0, round((currentInterval + bonus) * card.easeFactor))
                updated.intervalDays = min(3650, nextDays)
                updated.dueDate = now.addingTimeInterval(updated.intervalDays * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: formatDays(updated.intervalDays))

            case .easy:
                updated.stage = .review
                updated.repetitions += 1
                updated.easeFactor = max(1.3, card.easeFactor + 0.15)
                let bonus = (elapsedDays - currentInterval)
                let nextDays = max(currentInterval + 2.0, round((currentInterval + bonus) * card.easeFactor * 1.3))
                updated.intervalDays = min(3650, nextDays)
                updated.dueDate = now.addingTimeInterval(updated.intervalDays * day)
                return ScheduleResult(updatedCard: updated, intervalDescription: formatDays(updated.intervalDays))
            }
        }
    }

    static func formatDays(_ days: Double) -> String {
        let intDays = Int(round(days))
        if intDays < 30 {
            return "\(max(1, intDays)) gün"
        } else if intDays < 365 {
            let months = Double(intDays) / 30.0
            return String(format: "%.1f ay", months)
        } else {
            let years = Double(intDays) / 365.0
            return String(format: "%.1f yıl", years)
        }
    }
}
