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
    private static let turkish = Locale(identifier: "tr_TR")

    /// Trim, collapse inner whitespace, lowercase with Turkish rules (I -> dotless i, U+0130 -> i).
    static func normalizeMeaning(_ s: String) -> String {
        let collapsed = s.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        return collapsed.lowercased(with: turkish)
    }

    /// Accepted answers for an article-less card: each meaning split on , ; / (and the whole text).
    static func acceptedMeanings(_ meaning: String) -> Set<String> {
        var out = Set<String>()
        let whole = normalizeMeaning(meaning)
        if !whole.isEmpty { out.insert(whole) }
        for part in meaning.split(whereSeparator: { ",;/".contains($0) }) {
            let n = normalizeMeaning(String(part))
            if !n.isEmpty { out.insert(n) }
        }
        return out
    }

    /// Typed-answer check.
    /// - Valid card: typed (trimmed, case-insensitive) must equal der/die/das (unchanged).
    /// - Article-less card: typed must equal the card's Turkish MEANING (trimmed, inner whitespace
    ///   collapsed, Turkish case rules). With several meanings separated by comma, semicolon or
    ///   '/', ANY ONE is correct. A blank answer is never correct.
    static func isCorrect(card: Flashcard, typed: String) -> Bool {
        if let art = card.artikel {
            return typed.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == art.rawValue
        }
        let t = normalizeMeaning(typed)
        return !t.isEmpty && acceptedMeanings(card.meaning).contains(t)
    }

    /// Text to show as the correct answer in feedback: article, or the meaning for article-less cards.
    static func expectedLabel(card: Flashcard) -> String {
        card.artikel?.rawValue ?? card.meaning.trimmingCharacters(in: .whitespacesAndNewlines)
    }
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
    // MARK: - Validation (runs BEFORE any data is touched) -- mirrors Kotlin CsvCards

    enum Rejection { case badExtension, empty, tooLarge, binary, badEncoding, notCsvFormat, noColumns, noValidRows, tooManyInvalid }

    /// Thrown when a file is not an importable word list. Nothing has been changed when this is thrown.
    struct RejectedError: LocalizedError {
        let reason: Rejection
        let message: String
        var errorDescription: String? { message }
    }

    struct Validation {
        var ok: Bool
        var reason: Rejection? = nil
        var message: String = ""
        var validRows: Int = 0
        var invalidRows: Int = 0
    }

    static let maxBytes = 10 * 1024 * 1024
    /// Reject when MORE than this share of the non-empty data rows is invalid (a header row is not counted).
    static let maxInvalidRatio = 0.5
    private static let maxColumns = 8
    private static let maxArticleChars = 12
    private static let maxWordChars = 80
    private static let maxWordTokens = 6
    private static let maxMeaningChars = 200
    private static let maxDeckChars = 60
    private static let forbiddenWordChars: Set<Character> = ["{", "}", "[", "]", "<", ">", "|", "\\"]

    static func message(_ reason: Rejection, valid: Int = 0, invalid: Int = 0) -> String {
        switch reason {
        case .badExtension: return "Lütfen .csv uzantılı bir dosya seçin. Seçilen dosya CSV değil; hiçbir veri değiştirilmedi."
        case .empty: return "Dosya boş; içe aktarılacak kelime bulunamadı. Hiçbir veri değiştirilmedi."
        case .tooLarge: return "Dosya çok büyük (en fazla 10 MB). Hiçbir veri değiştirilmedi."
        case .binary: return "Bu dosya bir metin/CSV dosyası değil (resim, PDF gibi ikili içerik algılandı). Hiçbir veri değiştirilmedi."
        case .badEncoding: return "Dosyanın karakter kodlaması okunamadı. Dosyayı UTF-8 olarak kaydedip tekrar deneyin. Hiçbir veri değiştirilmedi."
        case .notCsvFormat: return "Bu dosya CSV değil (JSON/XML/HTML benzeri içerik). JSON yedekleri için Ayarlar'daki yedek içe aktarmayı kullanın. Hiçbir veri değiştirilmedi."
        case .noColumns: return "Dosyada sütun yapısı bulunamadı. Satırlar virgül, noktalı virgül veya sekme ile ayrılmış olmalı: artikel,kelime,anlam,deste. Hiçbir veri değiştirilmedi."
        case .noValidRows: return "Dosyada geçerli kelime satırı bulunamadı. Beklenen biçim: artikel,kelime,anlam,deste. Hiçbir veri değiştirilmedi."
        case .tooManyInvalid: return "Dosyadaki satırların çoğu geçersiz (\(invalid) / \(valid + invalid) satır). Doğru bir CSV dosyası seçtiğinizden emin olun. Hiçbir veri değiştirilmedi."
        }
    }

    private static func reject(_ reason: Rejection) -> RejectedError {
        RejectedError(reason: reason, message: message(reason))
    }

    static func hasCsvExtension(_ displayName: String?) -> Bool {
        guard let n = displayName else { return false }
        return n.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasSuffix(".csv")
    }

    /// Throws (badExtension) unless the name ends with .csv. A nil name cannot be checked and is allowed.
    static func requireCsvName(_ displayName: String?) throws {
        if let n = displayName, !hasCsvExtension(n) { throw reject(.badExtension) }
    }

    /// Decodes raw bytes: UTF-8, UTF-16 with BOM, or Windows-1254 (Turkish Excel) as fallback.
    static func decode(_ data: Data) throws -> String {
        if data.isEmpty { throw reject(.empty) }
        if data.count > maxBytes { throw reject(.tooLarge) }
        let b = [UInt8](data.prefix(2))
        if b.count == 2 && b[0] == 0xFF && b[1] == 0xFE {
            guard let s = String(data: data.dropFirst(2), encoding: .utf16LittleEndian) else { throw reject(.badEncoding) }
            return s
        }
        if b.count == 2 && b[0] == 0xFE && b[1] == 0xFF {
            guard let s = String(data: data.dropFirst(2), encoding: .utf16BigEndian) else { throw reject(.badEncoding) }
            return s
        }
        if data.contains(0) { throw reject(.binary) }
        if let s = String(data: data, encoding: .utf8) { return s }
        if let s = String(data: data, encoding: .windowsCP1254) { return s }
        throw reject(.badEncoding)
    }

    static func isPlausibleRow(_ columns: [String]) -> Bool {
        if columns.count < 2 || columns.count > maxColumns { return false }
        let article = columns[0].trimmingCharacters(in: .whitespacesAndNewlines)
        let word = columns[1].trimmingCharacters(in: .whitespacesAndNewlines)
        if word.isEmpty { return false }
        if article.count > maxArticleChars || article.contains(where: { $0.isWhitespace }) { return false }
        if word.count > maxWordChars { return false }
        if word.split(whereSeparator: { $0.isWhitespace }).count > maxWordTokens { return false }
        if word.contains(where: { forbiddenWordChars.contains($0) }) { return false }
        if let last = word.last, ".!?;:".contains(last) { return false }
        if columns.count >= 3 && columns[2].count > maxMeaningChars { return false }
        if columns.count >= 4 && columns[3].count > maxDeckChars { return false }
        return true
    }

    /// Validates decoded text. Pure; never throws.
    static func validate(_ content: String) -> Validation {
        func fail(_ r: Rejection, _ v: Int = 0, _ i: Int = 0) -> Validation {
            Validation(ok: false, reason: r, message: message(r, valid: v, invalid: i), validRows: v, invalidRows: i)
        }
        let text = content.replacingOccurrences(of: "\u{FEFF}", with: "")
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return fail(.empty) }
        if text.utf8.count > maxBytes { return fail(.tooLarge) }

        var nonPrintable = 0
        var total = 0
        for scalar in text.unicodeScalars {
            total += 1
            let v = scalar.value
            if v == 0 || v == 0xFFFD || v == 0x7F || (v < 0x20 && v != 0x0A && v != 0x0D && v != 0x09) { nonPrintable += 1 }
        }
        if text.unicodeScalars.contains(where: { $0.value == 0 }) || Double(nonPrintable) / Double(total) > 0.01 {
            return fail(.binary)
        }

        if let first = text.drop(while: { $0.isWhitespace }).first, first == "{" || first == "[" || first == "<" {
            return fail(.notCsvFormat)
        }

        var valid = 0, invalid = 0, multiColumn = 0
        for (lineIdx, rawLine) in text.components(separatedBy: .newlines).enumerated() {
            var line = rawLine
            while let last = line.last, last.isWhitespace || last.isNewline { line.removeLast() }
            if line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
            let columns = parseRow(line)
            if columns.count >= 2 { multiColumn += 1 }
            if lineIdx == 0 && columns.count >= 2 && isHeader(rawArtikel: columns[0], word: columns[1]) { continue }
            if isPlausibleRow(columns) { valid += 1 } else { invalid += 1 }
        }
        if valid + invalid == 0 { return fail(.empty) }
        if multiColumn == 0 { return fail(.noColumns, valid, invalid) }
        if valid == 0 { return fail(.noValidRows, valid, invalid) }
        if Double(invalid) / Double(valid + invalid) > maxInvalidRatio { return fail(.tooManyInvalid, valid, invalid) }
        return Validation(ok: true, validRows: valid, invalidRows: invalid)
    }

    @discardableResult
    static func requireValid(_ content: String) throws -> Validation {
        let v = validate(content)
        if !v.ok, let r = v.reason { throw RejectedError(reason: r, message: v.message) }
        return v
    }

    static func decodeAndValidate(_ data: Data) throws -> String {
        let text = try decode(data)
        try requireValid(text)
        return text
    }

    // MARK: - Undo (pure)

    struct UndoRecord {
        var addedKeys: Set<String>
        /// Cards the import modified, keyed by syncKey, as they were BEFORE the import.
        var previousCards: [String: Flashcard]
        var previousTombstones: [String: Date]
    }

    /// Returns nil when the import changed nothing. `before` must be the list passed to importContent.
    static func buildUndo(before: [Flashcard], outcome: ImportOutcome, previousTombstones: [String: Date]) -> UndoRecord? {
        if outcome.added == 0 && outcome.updated == 0 { return nil }
        var previous: [String: Flashcard] = [:]
        for i in before.indices where outcome.cards[i] != before[i] {
            previous[before[i].syncKey] = before[i]
        }
        return UndoRecord(addedKeys: Set(outcome.addedKeys), previousCards: previous, previousTombstones: previousTombstones)
    }

    static func applyUndo(_ undo: UndoRecord, cards: [Flashcard], tombstones: [String: Date], now: Date) -> (cards: [Flashcard], tombstones: [String: Date]) {
        var tomb = tombstones
        var kept: [Flashcard] = []
        for card in cards {
            let key = card.syncKey
            if undo.addedKeys.contains(key) {
                tomb[key] = now
            } else if let prev = undo.previousCards[key] {
                var c = card
                c.meaning = prev.meaning
                c.deckName = prev.deckName
                c.updatedAt = now
                kept.append(c)
            } else {
                kept.append(card)
            }
        }
        for (k, ts) in undo.previousTombstones where !undo.addedKeys.contains(k) && tomb[k] == nil {
            tomb[k] = ts
        }
        return (kept, tomb)
    }

    // MARK: - Import

    /// Bundled starter pack (must always pass `validate`).
    static let starterPackCSV: String = """
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
            if lineIdx == 0 && isHeader(rawArtikel: rawArtikel, word: cleanWord) { continue }
            // Garbage rows inside an accepted file are skipped, not imported.
            guard isPlausibleRow(columns) else {
                invalid += 1
                continue
            }
            let cleanMeaning = columns.count >= 3 ? columns[2].trimmingCharacters(in: .whitespacesAndNewlines) : ""
            let deckRaw = (columns.count >= 4 ? columns[3] : fallback).trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanDeck = deckRaw.isEmpty ? fallback : deckRaw

            // Empty OR not der/die/das (typo) -> article-less word.
            let artikel = Artikel.parse(rawArtikel)
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
            (Artikel.parse(a) == nil && ["kelime", "word", "wort"].contains(w))
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
