import Foundation

// CSV import safety: validation, rejection, undo. Mirrors android CsvImportSafetyTest.
// Uses check(...) / card(...) from main.swift. Run via macos/Tests/run_tests.sh (cwd = macos/).

func runCsvSafetyTests() {
    func valid(_ s: String) -> Bool { CsvCards.validate(s).ok }
    func reason(_ s: String) -> CsvCards.Rejection? { CsvCards.validate(s).reason }

    check(valid("der,Tisch,Masa,A1\ndie,Katze,Kedi,A1\ndas,Buch,Kitap,A1"), "safety: valid csv")
    check(valid("artikel,kelime,anlam,deste\nder,Tisch,Masa,A1"), "safety: header allowed")
    check(valid("der\tTisch\tMasa\ndie\tKatze\tKedi"), "safety: tab delimited")
    check(valid("der;Tisch;Masa\ndie;Katze;Kedi"), "safety: semicolon delimited")
    check(valid("der,Tisch,Masa\n,gehen,gitmek\n-,schnell,hizli"), "safety: articleless mixed")
    check(valid(CsvCards.starterPackCSV), "safety: starter pack validates")
    for name in ["../shared/samples/examplesheet.csv", "../shared/samples/wortschatzkapitel7-8.csv"] {
        let path = FileManager.default.currentDirectoryPath + "/" + name
        if let d = FileManager.default.contents(atPath: path), let t = try? CsvCards.decodeAndValidate(d) {
            check(valid(t), "safety: bundled sample \(name)")
        } else {
            check(false, "safety: bundled sample readable and decodable \(name)")
        }
    }

    check(reason("{\"cards\":[]}") == .notCsvFormat, "safety: json rejected")
    check(reason("") == .empty && reason("  \n ") == .empty, "safety: empty rejected")
    check(reason("Tisch\nHaus\nKatze") == .noColumns, "safety: single column rejected")
    let junk = "der,Tisch,Masa\n"
        + (0..<6).map { "this is some random sentence number \($0) that is not a word entry at all." }.joined(separator: "\n")
        + "\n[1, 2, 3] <tag> {x}, bogus\n[4] <b>, bogus2\n{y}, bogus3"
    check(reason(junk) == .tooManyInvalid, "safety: mostly junk rejected")
    check(valid("der,Tisch,Masa\nthis is just one single column line without any delimiter"), "safety: exactly half invalid allowed")
    let prose = "Es war einmal ein kleines Dorf am Rande eines grossen Waldes, in dem viele Tiere lebten.\nHallo, wie geht es dir heute? Mir geht es gut, danke schoen!\nDer Winter kam frueh in diesem Jahr, aber niemand beschwerte sich."
    check(!valid(prose), "safety: prose rejected")

    // bytes
    let png = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D])
    check((try? CsvCards.decodeAndValidate(png)) == nil, "safety: png bytes rejected")
    var binNoNul = Data((0..<256).map { UInt8($0 % 31 + 1) })
    binNoNul.append(Data("x".utf8))
    check((try? CsvCards.decodeAndValidate(binNoNul)) == nil, "safety: binary without NUL rejected")
    check((try? CsvCards.decodeAndValidate(Data())) == nil, "safety: empty data rejected")
    var utf16 = Data([0xFF, 0xFE])
    utf16.append("der,Tisch,Masa\ndie,Katze,Kedi".data(using: .utf16LittleEndian)!)
    check((try? CsvCards.decodeAndValidate(utf16)) != nil, "safety: utf16 with BOM accepted")
    let cp1254 = "der,Schl\u{00FC}ssel,Anahtar\ndie,T\u{00FC}r,Kap\u{0131}".data(using: .windowsCP1254)!
    check((try? CsvCards.decodeAndValidate(cp1254)).map { $0.contains("Kap\u{0131}") } == true, "safety: windows-1254 decodes")

    // extension gate
    check(CsvCards.hasCsvExtension("words.csv") && CsvCards.hasCsvExtension("WORDS.CSV"), "safety: csv extension")
    check(!CsvCards.hasCsvExtension("photo.png") && !CsvCards.hasCsvExtension("a.json") && !CsvCards.hasCsvExtension(nil), "safety: non csv extension")
    check((try? CsvCards.requireCsvName("photo.png")) == nil, "safety: png name throws")
    check((try? CsvCards.requireCsvName(nil)) != nil, "safety: nil name allowed")

    // junk row inside an accepted file is skipped
    let mixed = CsvCards.importContent("der,Tisch,Masa\ndie,Katze,Kedi\ndas,Buch,Kitap\nthis is a long sentence that should never become a flashcard, ok.", fallbackDeck: "Genel", existing: [])
    check(mixed.added == 3 && mixed.invalid == 1, "safety: junk row skipped")

    // undo
    let t0 = Date(timeIntervalSince1970: 1000)
    let existing = [card(.der, "Tisch", meaning: "Masa", updated: t0), card(.die, "Katze", meaning: "Kedi", updated: t0)]
    let buchKey = Flashcard.keyFor(artikel: .das, word: "Buch")
    let tombBefore: [String: Date] = [buchKey: Date(timeIntervalSince1970: 500)]
    let now = Date(timeIntervalSince1970: 5000)
    let outcome = CsvCards.importContent("der,Tisch,Anderes,Yeni Deste\ndas,Buch,Kitap,A1\ndie,Blume,Cicek,A1", fallbackDeck: "Genel", existing: existing, now: now)
    check(outcome.added == 2 && outcome.updated == 1, "undo: import outcome")
    if let undo = CsvCards.buildUndo(before: existing, outcome: outcome, previousTombstones: tombBefore) {
        var tombAfter = tombBefore
        tombAfter.removeValue(forKey: buchKey)
        let later = Date(timeIntervalSince1970: 9000)
        let r = CsvCards.applyUndo(undo, cards: outcome.cards, tombstones: tombAfter, now: later)
        check(r.cards.map { $0.word } == ["Tisch", "Katze"], "undo: added removed")
        check(r.cards[0].meaning == "Masa" && r.cards[0].deckName == "Genel" && r.cards[0].updatedAt == later, "undo: updated restored and bumped")
        check(outcome.addedKeys.allSatisfy { (r.tombstones[$0] ?? .distantPast) > now }, "undo: added keys tombstoned newer than cards")
        let extra = outcome.cards + [card(.das, "Haus", meaning: "Ev")]
        check(CsvCards.applyUndo(undo, cards: extra, tombstones: [:], now: later).cards.map { $0.word } == ["Tisch", "Katze", "Haus"], "undo: keeps later changes")
    } else {
        check(false, "undo: record built")
    }
    let same = CsvCards.importContent("der,Tisch,Masa", fallbackDeck: "Genel", existing: existing, now: now)
    check(CsvCards.buildUndo(before: existing, outcome: same, previousTombstones: [:]) == nil, "undo: no-op import has no undo")
}
