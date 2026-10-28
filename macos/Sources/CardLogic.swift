import Foundation

// Pure card logic (no UI / storage dependencies): merge & dedup, CSV import/export,
// answer checking and the article filter. Mirrors android/.../CardLogic.kt.

enum CardMerge {
    static func smartMerge(local: Flashcard, incoming: Flashcard) -> Flashcard {
        // Choose the card with further / newer Anki SM-2 study progress
        let incomingHasMoreProgress: Bool
        if incoming.totalReviews != local.totalReviews {
            incomingHasMoreProgress = incoming.totalReviews > local.totalReviews
        } else if (incoming.lastReviewedAt ?? .distantPast) != (local.lastReviewedAt ?? .distantPast) {
            incomingHasMoreProgress = (incoming.lastReviewedAt ?? .distantPast) > (local.lastReviewedAt ?? .distantPast)
        } else {
            incomingHasMoreProgress = incoming.updatedAt > local.updatedAt
        }
        let progressSource = incomingHasMoreProgress ? incoming : local

        // Choose best meaning (non-empty wins; if both non-empty, newer updatedAt wins)
        let mergedMeaning: String
        let localMeaningTrimmed = local.meaning.trimmingCharacters(in: .whitespacesAndNewlines)
        let incomingMeaningTrimmed = incoming.meaning.trimmingCharacters(in: .whitespacesAndNewlines)
        if !localMeaningTrimmed.isEmpty && incomingMeaningTrimmed.isEmpty {
            mergedMeaning = local.meaning
        } else if !incomingMeaningTrimmed.isEmpty && localMeaningTrimmed.isEmpty {
            mergedMeaning = incoming.meaning
        } else if incoming.updatedAt > local.updatedAt && !incomingMeaningTrimmed.isEmpty {
            mergedMeaning = incoming.meaning
        } else {
            mergedMeaning = local.meaning
        }

        // Choose best deck (specific deck over 'Genel' wins; if both specific, newer updatedAt wins)
        let mergedDeck: String
        if local.cleanDeckName != "Genel" && incoming.cleanDeckName == "Genel" {
            mergedDeck = local.cleanDeckName
        } else if incoming.cleanDeckName != "Genel" && local.cleanDeckName == "Genel" {
            mergedDeck = incoming.cleanDeckName
        } else if incoming.updatedAt > local.updatedAt {
            mergedDeck = incoming.cleanDeckName
        } else {
            mergedDeck = local.cleanDeckName
        }

        return Flashcard(
            id: local.id,
            artikel: local.artikel,
            word: local.word,
            meaning: mergedMeaning,
            deckName: mergedDeck,
            createdAt: min(local.createdAt, incoming.createdAt),
            updatedAt: max(local.updatedAt, incoming.updatedAt),
            stage: progressSource.stage,
            easeFactor: progressSource.easeFactor,
            intervalDays: progressSource.intervalDays,
            repetitions: progressSource.repetitions,
            lapses: progressSource.lapses,
            dueDate: progressSource.dueDate,
            lastReviewedAt: progressSource.lastReviewedAt,
            totalReviews: progressSource.totalReviews
        )
    }

    /// Collapses duplicates (same syncKey) inside one list, keeping first-seen order.
    static func dedupe(_ cards: [Flashcard]) -> [Flashcard] {
        var map: [String: Flashcard] = [:]
        var order: [String] = []
        for c in cards {
            if let existing = map[c.syncKey] {
                map[c.syncKey] = smartMerge(local: existing, incoming: c)
            } else {
                map[c.syncKey] = c
                order.append(c.syncKey)
            }
        }
        return order.compactMap { map[$0] }
    }

    struct MergeResult {
        var cards: [Flashcard]
        var addedFromIncoming: Int
        var overlapping: Int
    }

    /// Union of local + incoming cards by syncKey (valid and article-less cards share the key
    /// space but never collide). A tombstone newer than a card drops it.
    static func merge(local: [Flashcard], incoming: [Flashcard], tombstones: [String: Date]) -> MergeResult {
        var byKey: [String: Flashcard] = [:]
        var order: [String] = []
        for c in local {
            let key = c.syncKey
            if (tombstones[key] ?? .distantPast) > c.updatedAt { continue }
            if let existing = byKey[key] {
                byKey[key] = smartMerge(local: existing, incoming: c)
            } else {
                byKey[key] = c
                order.append(key)
            }
        }
        var added = 0
        var overlapping = 0
        for c in incoming {
            let key = c.syncKey
            if (tombstones[key] ?? .distantPast) > c.updatedAt { continue }
            if let existing = byKey[key] {
                byKey[key] = smartMerge(local: existing, incoming: c)
                overlapping += 1
            } else {
                byKey[key] = c
                order.append(key)
                added += 1
            }
        }
        return MergeResult(cards: order.compactMap { byKey[$0] }, addedFromIncoming: added, overlapping: overlapping)
    }
}

enum AnswerChecker {
    /// Typed/selected answers that mean "this word has no article".
    static let noneTokens: Set<String> = ["-", "\u{2013}", "\u{2014}"]

    /// Typed-answer check. Case-insensitive and whitespace-trimmed.
    /// - Valid card: typed must equal der/die/das.
    /// - Article-less card: correct answer is "-" (en/em dash accepted) or an empty answer.
    static func isCorrect(card: Flashcard, typed: String) -> Bool {
        let t = typed.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let art = card.artikel {
            return t == art.rawValue
        }
        return t.isEmpty || noneTokens.contains(t)
    }

    /// Text to show as the "correct article" in feedback: "der"/"die"/"das" or "-".
    static func expectedLabel(card: Flashcard) -> String { card.displayArtikel }
}

enum ArtikelFilter {
    /// Filter value for article-less words. Other values: "der", "die", "das"; nil = all.
    static let none = "-"

    struct Counts: Equatable {
        var total: Int
        var der: Int
        var die: Int
        var das: Int
        var none: Int

        /// Count for a filter value (nil/blank = all).
        func forFilter(_ filter: String?) -> Int {
            switch filter?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case nil, "": return total
            case "der": return der
            case "die": return die
            case "das": return das
            case ArtikelFilter.none: return none
            default: return 0
            }
        }
    }

    static func matches(_ card: Flashcard, filter: String?) -> Bool {
        guard let f = filter?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !f.isEmpty else { return true }
        if f == none { return card.isArticleless }
        return card.artikel?.rawValue == f
    }

    static func apply(_ cards: [Flashcard], filter: String?) -> [Flashcard] {
        cards.filter { matches($0, filter: filter) }
    }

    static func counts(_ cards: [Flashcard]) -> Counts {
        Counts(
            total: cards.count,
            der: cards.filter { $0.artikel == .der }.count,
            die: cards.filter { $0.artikel == .die }.count,
            das: cards.filter { $0.artikel == .das }.count,
            none: cards.filter { $0.isArticleless }.count
        )
    }
}

enum CsvCards {
    struct ImportOutcome {
        var cards: [Flashcard]
        var added: Int
        var updated: Int
        var skipped: Int
        var invalid: Int
        /// syncKeys of newly added cards (caller clears matching tombstones).
        var addedKeys: [String]
    }

    static func importContent(_ content: String, fallbackDeck: String, existing: [Flashcard], now: Date = Date()) -> ImportOutcome {
        let lines = content.components(separatedBy: .newlines)
        var added = 0, updated = 0, skipped = 0, invalid = 0
        var addedKeys: [String] = []
        let trimmedFallback = fallbackDeck.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallback = trimmedFallback.isEmpty ? "Genel" : trimmedFallback
        var cards = existing
        var indexByKey: [String: Int] = [:]
        for (i, c) in cards.enumerated() where indexByKey[c.syncKey] == nil { indexByKey[c.syncKey] = i }

        for (lineIdx, rawLine) in lines.enumerated() {
            // Trim trailing whitespace only: a leading tab/comma means "empty article column".
            var line = rawLine.replacingOccurrences(of: "\u{FEFF}", with: "")
            while let last = line.last, last.isWhitespace || last.isNewline { line.removeLast() }
            if line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }

            let columns = parseRow(line)
            guard columns.count >= 2 else {
                invalid += 1
                continue
            }
            let rawArtikel = columns[0]
            let cleanWord = columns[1].trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanMeaning = columns.count >= 3 ? columns[2].trimmingCharacters(in: .whitespacesAndNewlines) : ""
            let deckRaw = (columns.count >= 4 ? columns[3] : fallback).trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanDeck = deckRaw.isEmpty ? fallback : deckRaw

            if lineIdx == 0 && isHeader(rawArtikel: rawArtikel, word: cleanWord) { continue }

            // Empty OR not der/die/das (typo) -> article-less word.
            let artikel = Artikel.parseStrict(rawArtikel)
            guard !cleanWord.isEmpty else {
                invalid += 1
                continue
            }

            let key = Flashcard.keyFor(artikel: artikel, word: cleanWord)
            if let idx = indexByKey[key] {
                var didUpdate = false
                if !cleanMeaning.isEmpty && cards[idx].meaning != cleanMeaning {
                    cards[idx].meaning = cleanMeaning
                    didUpdate = true
                }
                if cleanDeck != "Genel" && cards[idx].deckName != cleanDeck {
                    cards[idx].deckName = cleanDeck
                    didUpdate = true
                }
                if didUpdate {
                    cards[idx].updatedAt = now
                    updated += 1
                } else {
                    skipped += 1
                }
            } else {
                cards.append(Flashcard(artikel: artikel, word: cleanWord, meaning: cleanMeaning, deckName: cleanDeck, createdAt: now, updatedAt: now))
                indexByKey[key] = cards.count - 1
                addedKeys.append(key)
                added += 1
            }
        }
        return ImportOutcome(cards: cards, added: added, updated: updated, skipped: skipped, invalid: invalid, addedKeys: addedKeys)
    }

    private static func isHeader(rawArtikel: String, word: String) -> Bool {
        let a = rawArtikel.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let w = word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return a == "artikel" || a == "article" ||
            (Artikel.parseStrict(a) == nil && ["kelime", "word", "wort"].contains(w))
    }

    static func exportString(_ cards: [Flashcard]) -> String {
        func esc(_ s: String) -> String { s.contains(",") ? "\"\(s)\"" : s }
        var rows = ["artikel,kelime,anlam,deste"]
        for card in cards {
            // Article-less words are written with an EMPTY article column.
            rows.append("\(card.artikel?.rawValue ?? ""),\(esc(card.word)),\(esc(card.meaning)),\(esc(card.deckName))")
        }
        return rows.joined(separator: "\n")
    }

    static func parseRow(_ line: String) -> [String] {
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
}
