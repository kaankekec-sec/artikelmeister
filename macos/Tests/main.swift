import Foundation

// Dependency-free test runner for the article-less logic (Models.swift + CardLogic.swift).
// Run: macos/Tests/run_tests.sh

var failures = 0
var total = 0
func check(_ cond: @autoclosure () -> Bool, _ name: String, line: Int = #line) {
    total += 1
    if !cond() {
        failures += 1
        print("FAIL [line \(line)]: \(name)")
    }
}

func card(_ art: Artikel?, _ word: String, meaning: String = "", updated: Date = Date(timeIntervalSince1970: 1000), reviews: Int = 0) -> Flashcard {
    Flashcard(artikel: art, word: word, meaning: meaning, createdAt: updated, updatedAt: updated, dueDate: updated, totalReviews: reviews)
}

func decodeBackup(_ json: String) throws -> ArtikelMeisterBackup {
    let d = JSONDecoder()
    d.dateDecodingStrategy = .iso8601
    return try d.decode(ArtikelMeisterBackup.self, from: Data(json.utf8))
}

// ---------- CSV ----------
do {
    let out = CsvCards.importContent("artikel,kelime,anlam,deste\nder,Tisch,Masa,A1\nDIE,Katze,Kedi,A1", fallbackDeck: "Genel", existing: [])
    check(out.added == 2 && out.invalid == 0, "csv valid rows")
    check(out.cards[0].artikel == .der && out.cards[1].artikel == .die, "csv valid articles")
    check(out.cards[0].deckName == "A1", "csv deck")
}
do {
    let out = CsvCards.importContent(",Tisch,Masa,A1\njustoneword", fallbackDeck: "Genel", existing: [])
    check(out.added == 1 && out.invalid == 1, "csv empty article -> articleless, 1-col invalid")
    check(out.cards[0].isArticleless && out.cards[0].word == "Tisch" && out.cards[0].meaning == "Masa", "csv articleless fields")
}
do {
    let out = CsvCards.importContent("m,A\nMaskulin,B\nf,C\nfeminine,D\nN,E\nneutrum,F\nneuter,G\nmasculine,H\nfeminin,I", fallbackDeck: "Genel", existing: [])
    check(out.cards.map { $0.artikel } == [.der, .der, .die, .die, .das, .das, .das, .der, .die], "csv old aliases")
    let x = CsvCards.importContent("x,A\nmann,B", fallbackDeck: "Genel", existing: [])
    check(x.cards.allSatisfy { $0.isArticleless }, "csv unknown stays articleless")
}
do {
    let out = CsvCards.importContent("artikel,kelime\ndre,Tisch\nderr,Haus", fallbackDeck: "Genel", existing: [])
    check(out.added == 2 && out.cards.allSatisfy { $0.isArticleless }, "csv typo -> articleless, header skipped")
}
do {
    let out = CsvCards.importContent("  DeR  , Tisch ,Masa\nDAS,Buch\n", fallbackDeck: "Genel", existing: [])
    check(out.cards[0].artikel == .der && out.cards[0].word == "Tisch" && out.cards[1].artikel == .das, "csv mixed case / whitespace")
}
do {
    let out = CsvCards.importContent("\tTisch\tMasa", fallbackDeck: "Genel", existing: [])
    check(out.added == 1 && out.cards[0].isArticleless && out.cards[0].word == "Tisch" && out.cards[0].meaning == "Masa", "csv tab with empty first column")
}
do {
    let out = CsvCards.importContent("der,\n,,", fallbackDeck: "Genel", existing: [])
    check(out.added == 0 && out.invalid == 2, "csv empty word invalid")
}
do {
    let out = CsvCards.importContent("der,Tisch,Masa\n,Tisch,masa2\n,tisch ,masa3", fallbackDeck: "Genel", existing: [])
    check(out.cards.count == 2 && out.updated == 1, "csv articleless Tisch distinct from der Tisch, dedup by word")
    check(out.cards.first(where: { $0.isArticleless })?.meaning == "masa3", "csv dedup updates meaning")
}
do {
    let cards = [card(.der, "Tisch", meaning: "Masa"), card(nil, "Hallo", meaning: "Merhaba"), card(nil, "Guten Tag", meaning: "Iyi gunler, dostum")]
    let csv = CsvCards.exportString(cards)
    check(csv.components(separatedBy: "\n")[2].hasPrefix(",Hallo,"), "csv export empty article column")
    let back = CsvCards.importContent(csv, fallbackDeck: "Genel", existing: [])
    check(back.added == 3, "csv roundtrip count")
    check(back.cards.map { $0.syncKey } == cards.map { $0.syncKey }, "csv roundtrip keys")
    check(back.cards[2].meaning == "Iyi gunler, dostum", "csv roundtrip quoted meaning")
}

// ---------- Answer checking ----------
do {
    let c = card(.der, "Tisch")
    check(AnswerChecker.isCorrect(card: c, typed: " DER "), "answer valid case/trim")
    check(!AnswerChecker.isCorrect(card: c, typed: "die"), "answer valid wrong")
    check(!AnswerChecker.isCorrect(card: c, typed: ""), "answer valid empty wrong")
    check(!AnswerChecker.isCorrect(card: c, typed: "-"), "answer valid '-' wrong")
    let n = card(nil, "Hallo", meaning: "Merhaba, selam; selamlar / Hos geldin")
    check(AnswerChecker.isCorrect(card: n, typed: "merhaba"), "meaning basic")
    check(AnswerChecker.isCorrect(card: n, typed: "  SELAM "), "meaning trim/case")
    check(AnswerChecker.isCorrect(card: n, typed: "selamlar"), "meaning ; part")
    check(AnswerChecker.isCorrect(card: n, typed: "hos   geldin"), "meaning / part, collapse whitespace")
    check(AnswerChecker.isCorrect(card: n, typed: "Merhaba, selam; selamlar / Hos geldin"), "meaning whole text")
    check(!AnswerChecker.isCorrect(card: n, typed: "merhab"), "meaning wrong")
    check(!AnswerChecker.isCorrect(card: n, typed: ""), "meaning blank wrong")
    check(!AnswerChecker.isCorrect(card: n, typed: "-"), "meaning '-' wrong")
    check(!AnswerChecker.isCorrect(card: card(nil, "X"), typed: ""), "empty meaning + blank wrong")
    check(n.fullGermanTerm == "Hallo" && AnswerChecker.expectedLabel(card: n) == "Merhaba, selam; selamlar / Hos geldin", "label / fullGermanTerm")
    let tr = card(nil, "Fisch", meaning: "ISIK, \u{0130}ncir")
    check(AnswerChecker.isCorrect(card: tr, typed: "\u{0131}s\u{0131}k"), "turkish dotless i")
    check(AnswerChecker.isCorrect(card: tr, typed: "ISIK"), "turkish I")
    check(AnswerChecker.isCorrect(card: tr, typed: "incir"), "turkish dotted capital")
    check(AnswerChecker.isCorrect(card: tr, typed: "\u{0130}NC\u{0130}R"), "turkish all caps dotted")
    check(!AnswerChecker.isCorrect(card: tr, typed: "isik"), "turkish i != dotless")
}

// ---------- Filter ----------
do {
    let cards = [card(.der, "a"), card(.die, "b"), card(nil, "c"), card(nil, "d")]
    check(ArtikelFilter.apply(cards, filter: ArtikelFilter.none).map { $0.word } == ["c", "d"], "filter '-'")
    check(ArtikelFilter.apply(cards, filter: nil).count == 4, "filter all")
    check(ArtikelFilter.apply(cards, filter: "der").map { $0.word } == ["a"], "filter der")
    let k = ArtikelFilter.counts(cards)
    check(k == ArtikelFilter.Counts(total: 4, der: 1, die: 1, das: 0, none: 2), "counts")
    check(k.forFilter("-") == 2 && k.forFilter(nil) == 4, "counts forFilter")
}

// ---------- Lenient decode / persistence format ----------
do {
    let cards = [card(.der, "Tisch"), card(nil, "Hallo")]
    let backup = ArtikelMeisterBackup(cards: cards)
    let enc = JSONEncoder()
    let data = try enc.encode(backup)
    let root = try JSONSerialization.jsonObject(with: data) as! [String: Any]
    let valid = root["cards"] as! [[String: Any]]
    check(valid.count == 1 && valid[0]["artikel"] as? String == "der", "encode: cards[] only valid cards")
    check((root["articlelessCards"] as? [[String: Any]])?.count == 1, "encode: articlelessCards present")
    let al = (root["articlelessCards"] as! [[String: Any]])[0]
    check(al["artikel"] == nil, "encode: articleless has no artikel field")
    let back = try decodeBackup(String(data: data, encoding: .utf8)!)
    check(back.cards.count == 2 && back.cards.filter { $0.isArticleless }.count == 1, "backup roundtrip")

    let none = try enc.encode(ArtikelMeisterBackup(cards: [card(.das, "Buch")]))
    let r2 = try JSONSerialization.jsonObject(with: none) as! [String: Any]
    check(r2["articlelessCards"] == nil, "encode: no articlelessCards key when none")
}
do {
    let json = """
    {"version":2,"cards":[
      {"artikel":"der","word":"Tisch","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      {"artikel":"","word":"Bad","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      {"artikel":"xyz","word":"Bad2","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      "garbage",
      {"artikel":"die","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      {"artikel":"die","word":"Katze","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0}
    ],
    "articlelessCards":[
      {"word":"Hallo","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      {"word":"","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0},
      42,
      {"artikel":"der","word":"Ignored","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0}
    ]}
    """
    let b = try decodeBackup(json)
    check(b.cards.filter { !$0.isArticleless }.map { $0.word } == ["Tisch", "Katze"], "lenient decode: valid cards kept, bad skipped")
    check(b.cards.filter { $0.isArticleless }.map { $0.word } == ["Hallo", "Ignored"], "lenient decode: articleless list, artikel ignored")
}
do {
    // old-format file (no articlelessCards key)
    let b = try decodeBackup("""
    {"cards":[{"artikel":"das","word":"Buch","stage":"new","easeFactor":2.5,"intervalDays":0,"repetitions":0,"lapses":0}]}
    """)
    check(b.cards.count == 1 && b.cards[0].artikel == .das, "decode old file without articlelessCards")
    // non-array cards -> throws (callers abort instead of overwriting)
    check((try? decodeBackup("{\"cards\":42}")) == nil, "non-array cards throws")
    // local cards.json (plain array)
    let list = try? JSONDecoder().decode(LossyCardList.self, from: Data("[1,{\"artikel\":\"der\",\"word\":\"X\",\"stage\":\"new\",\"easeFactor\":2.5,\"intervalDays\":0,\"repetitions\":0,\"lapses\":0}]".utf8))
    check(list?.cards.count == 1, "LossyCardList skips garbage")
    check((try? JSONDecoder().decode(LossyCardList.self, from: Data("not json".utf8))) == nil, "LossyCardList throws on non-JSON")
}

// ---------- Merge / dedup ----------
do {
    check(card(nil, "Hallo").syncKey == card(nil, " HALLO ").syncKey, "key articleless word-only ci")
    check(card(nil, "Tisch").syncKey != card(.der, "Tisch").syncKey, "key articleless != der")
    let local = [card(.der, "Tisch", meaning: "m1", updated: Date(timeIntervalSince1970: 1000), reviews: 1), card(nil, "Hallo")]
    let cloud = [
        card(nil, "hallo", meaning: "Merhaba", updated: Date(timeIntervalSince1970: 2000), reviews: 3),
        card(nil, "Tisch", meaning: "article-less tisch", updated: Date(timeIntervalSince1970: 1500)),
        card(.die, "Katze"),
    ]
    let r = CardMerge.merge(local: local, incoming: cloud, tombstones: [:])
    check(r.cards.count == 4 && r.addedFromIncoming == 2 && r.overlapping == 1, "merge union counts")
    let hallo = r.cards.first { $0.isArticleless && $0.word.lowercased() == "hallo" }
    check(hallo?.meaning == "Merhaba" && hallo?.totalReviews == 3, "merge: more progress / meaning wins")
    check(r.cards.filter { $0.word.lowercased() == "tisch" }.count == 2, "merge keeps der Tisch + articleless Tisch")

    let c = card(nil, "Hallo", updated: Date(timeIntervalSince1970: 1000))
    check(CardMerge.merge(local: [c], incoming: [], tombstones: [c.syncKey: Date(timeIntervalSince1970: 5000)]).cards.isEmpty, "tombstone drops articleless")
    check(CardMerge.dedupe([card(nil, "Hallo"), card(nil, "hallo "), card(.der, "Hallo")]).count == 2, "dedupe")
}

// ---------- SM2 ----------
do {
    let c = card(nil, "Hallo")
    let now = Date(timeIntervalSince1970: 10_000_000)
    for rating in ReviewRating.allCases {
        let r = SM2Scheduler.schedule(card: c, rating: rating, now: now)
        check(r.updatedCard.artikel == nil && r.updatedCard.totalReviews == 1, "sm2 keeps articleless (\(rating))")
    }
    check(SM2Scheduler.schedule(card: c, rating: .good, now: now).updatedCard.stage == .learning, "sm2 good -> learning")
    check(SM2Scheduler.schedule(card: c, rating: .easy, now: now).updatedCard.stage == .review, "sm2 easy -> review")
    var review = c
    review.stage = .review; review.intervalDays = 10; review.repetitions = 3; review.lastReviewedAt = now.addingTimeInterval(-86_400)
    let again = SM2Scheduler.schedule(card: review, rating: .again, now: now).updatedCard
    check(again.stage == .relearning && again.lapses == 1, "sm2 again -> relearning")
}

runCsvSafetyTests()
print("\(total - failures)/\(total) checks passed")
exit(failures == 0 ? 0 : 1)
