import Foundation
import SwiftUI

enum Artikel: String, Codable, CaseIterable, Identifiable {
    case der = "der"
    case die = "die"
    case das = "das"

    var id: String { rawValue }

    var color: Color {
        switch self {
        case .der: return Color(red: 0.16, green: 0.48, blue: 0.96) // Blue for masculine
        case .die: return Color(red: 0.90, green: 0.22, blue: 0.35) // Red/Rose for feminine
        case .das: return Color(red: 0.15, green: 0.68, blue: 0.42) // Green for neuter
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
        case .again: return Color(red: 0.90, green: 0.25, blue: 0.25)
        case .hard: return Color(red: 0.92, green: 0.55, blue: 0.15)
        case .good: return Color(red: 0.18, green: 0.68, blue: 0.38)
        case .easy: return Color(red: 0.20, green: 0.52, blue: 0.95)
        }
    }
}

struct Flashcard: Identifiable, Codable, Equatable {
    var id: UUID
    var artikel: Artikel
    var word: String
    var meaning: String
    var deckName: String
    var createdAt: Date

    // Spaced Repetition (Anki SM-2) state
    var stage: CardStage
    var easeFactor: Double      // Default 2.5, min 1.3
    var intervalDays: Double    // Interval in days (can be fractional for intraday learning)
    var repetitions: Int        // Consecutive successful recalls
    var lapses: Int             // Times forgotten
    var dueDate: Date           // Next scheduled review timestamp
    var lastReviewedAt: Date?
    var totalReviews: Int

    init(
        id: UUID = UUID(),
        artikel: Artikel,
        word: String,
        meaning: String = "",
        deckName: String = "Genel",
        createdAt: Date = Date(),
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
        case id, artikel, word, meaning, deckName, createdAt
        case stage, easeFactor, intervalDays, repetitions, lapses, dueDate, lastReviewedAt, totalReviews
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.artikel = try container.decode(Artikel.self, forKey: .artikel)
        self.word = try container.decode(String.self, forKey: .word)
        self.meaning = try container.decodeIfPresent(String.self, forKey: .meaning) ?? ""
        let rawDeck = try container.decodeIfPresent(String.self, forKey: .deckName)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Genel"
        self.deckName = rawDeck.isEmpty ? "Genel" : rawDeck
        self.createdAt = try container.decode(Date.self, forKey: .createdAt)
        self.stage = try container.decode(CardStage.self, forKey: .stage)
        self.easeFactor = try container.decode(Double.self, forKey: .easeFactor)
        self.intervalDays = try container.decode(Double.self, forKey: .intervalDays)
        self.repetitions = try container.decode(Int.self, forKey: .repetitions)
        self.lapses = try container.decode(Int.self, forKey: .lapses)
        self.dueDate = try container.decode(Date.self, forKey: .dueDate)
        self.lastReviewedAt = try container.decodeIfPresent(Date.self, forKey: .lastReviewedAt)
        self.totalReviews = try container.decodeIfPresent(Int.self, forKey: .totalReviews) ?? 0
    }

    var fullGermanTerm: String {
        "\(artikel.rawValue) \(word)"
    }
}

struct AppSettings: Codable, Equatable {
    var dailyNewCardLimit: Int = 20
    var dailyReviewLimit: Int = 100
    var autoPlayPronunciation: Bool = false
    var showMeaningOnFront: Bool = false
    var newCardOrderRandom: Bool = false
    var customDecks: [String] = ["Genel"]

    enum CodingKeys: String, CodingKey {
        case dailyNewCardLimit, dailyReviewLimit, autoPlayPronunciation, showMeaningOnFront, newCardOrderRandom, customDecks
    }

    init(
        dailyNewCardLimit: Int = 20,
        dailyReviewLimit: Int = 100,
        autoPlayPronunciation: Bool = false,
        showMeaningOnFront: Bool = false,
        newCardOrderRandom: Bool = false,
        customDecks: [String] = ["Genel"]
    ) {
        self.dailyNewCardLimit = dailyNewCardLimit
        self.dailyReviewLimit = dailyReviewLimit
        self.autoPlayPronunciation = autoPlayPronunciation
        self.showMeaningOnFront = showMeaningOnFront
        self.newCardOrderRandom = newCardOrderRandom
        self.customDecks = customDecks
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.dailyNewCardLimit = try c.decodeIfPresent(Int.self, forKey: .dailyNewCardLimit) ?? 20
        self.dailyReviewLimit = try c.decodeIfPresent(Int.self, forKey: .dailyReviewLimit) ?? 100
        self.autoPlayPronunciation = try c.decodeIfPresent(Bool.self, forKey: .autoPlayPronunciation) ?? false
        self.showMeaningOnFront = try c.decodeIfPresent(Bool.self, forKey: .showMeaningOnFront) ?? false
        self.newCardOrderRandom = try c.decodeIfPresent(Bool.self, forKey: .newCardOrderRandom) ?? false
        self.customDecks = try c.decodeIfPresent([String].self, forKey: .customDecks) ?? ["Genel"]
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
    var version: Int = 1
    var exportedAt: Date = Date()
    var includesProgress: Bool
    var cards: [Flashcard]
    var settings: AppSettings?
    var studyHistory: [String: DailyProgress]?
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
