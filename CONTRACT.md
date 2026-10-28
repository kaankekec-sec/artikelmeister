# Article-less words ("artikelsiz") - engineering contract

Package `com.artikelmeister.android` (Kotlin) / module sources `macos/Sources` (Swift). Names are identical on both platforms unless noted.

## Model
| Kotlin | Swift |
|---|---|
| `Flashcard.artikel: Artikel?` (null = article-less) | `Flashcard.artikel: Artikel?` |
| `Flashcard.isArticleless: Boolean` | `Flashcard.isArticleless: Bool` |
| `Flashcard.displayArtikel: String` ("der"/"die"/"das" or "-") | `Flashcard.displayArtikel` |
| `Flashcard.fullGermanTerm` (word only when article-less; use for TTS) | same |
| `Flashcard.syncKey` (`der_tisch` / `none_tisch`), `Flashcard.keyFor(artikel, word)` | same (`Flashcard.keyFor(artikel:word:)`) |
| `Artikel.parseStrict(raw): Artikel?` (only der/die/das, trimmed, case-insens.) | `Artikel.parseStrict(_:)` |
| `Artikel` enum itself is UNCHANGED (still only DER/DIE/DAS, so `Artikel.entries` pickers show no extra button) | same |

## Filter ('-')
- Filter value for article-less words: `ArtikelFilter.NONE == "-"` (Swift `ArtikelFilter.none`). Other filter values: `"der"`, `"die"`, `"das"`; `null`/`nil`/blank = all.
- `ArtikelFilter.matches(card, filter)`, `ArtikelFilter.apply(cards, filter)`
- `ArtikelFilter.counts(cards): ArtikelFilter.Counts(total, der, die, das, none)`; `Counts.forFilter(filter)`.
- Store: `DeckStore.artikelCounts(forDeck: String? = null): ArtikelFilter.Counts`, `DeckStore.articlelessCount: Int` (Kotlin `store.artikelCounts()`, Swift `store.artikelCounts(forDeck:)`).

## Study / answer check
- `AnswerChecker.isCorrect(card, typed): Boolean` (Swift `AnswerChecker.isCorrect(card:typed:)`): trimmed + case-insensitive. Valid card: typed == der/die/das. Article-less card: correct if typed is `"-"` (also en/em dash) OR blank.
- `AnswerChecker.expectedLabel(card)` -> "der"/"die"/"das" or "-" for feedback text.
- UI contract: for article-less cards offer a "-" choice that sets the typed value to `"-"` (`AnswerChecker.NONE_TOKENS`); blank also counts as correct.
- Queue and SM-2 need no UI work (cards are in the same `store.cards` list; `SM2Scheduler` untouched and tested with article-less cards).

## Store API changes
- `DeckStore.addCard(artikel: Artikel?, word, meaning, deckName)` and `updateCard(id, artikel: Artikel?, ...)` accept null/nil. Dedup key = `syncKey` (article-less: word only, case-insensitive).
- `DeckStore.cardsLoadFailed` (Kotlin `val`, Swift `@Published private(set)`): true when local card files could not be decoded; writes and cloud sync are blocked. UI may show a warning (optional).
- CSV: `importCSVContent` / `exportCSVString` signatures unchanged; empty or non-der/die/das article column -> article-less word; export writes an empty article column.
- Persistence: valid cards -> `cards.json` / Drive `cards[]`; article-less -> `articleless_cards.json` / Drive `articlelessCards`. See `shared/sync-spec.md` section 2.1/2.2.

## What WordListView.kt needs (designer-owned, currently does NOT compile)
`Flashcard.artikel` is now nullable. Exact compile errors (`card.artikel` receiver nullable):
- line ~142 `card.artikel.rawValue` (search) -> `card.displayArtikel`
- lines ~806/810/813 badge: `card.artikel.badgeBackground / .rawValue / .color` -> use `card.artikel?.color ?: <neutral grey>`, `card.displayArtikel` for the text
- lines ~1506/1510/1513 (preview): same as above
- line ~136 filter: replace `selectedArtikelFilter: Artikel?` + `card.artikel != selectedArtikelFilter` with a `String?` filter and `ArtikelFilter.matches(card, filter)`; add a "-" menu entry (label e.g. "Artikelsiz", value `ArtikelFilter.NONE`) and show counts from `store.artikelCounts()` / `Counts.forFilter(...)`.
- add-word sheet (~line 295-312) and edit dialog (~1254-1326): add a "-" option mapping to `artikel = null` (`addCard(null, ...)`, `updateCard(..., artikel = null, ...)`); `editArtikel` is already `Artikel?` after the type change (`mutableStateOf(card.artikel)`).
StatsAndSettingsView.kt (lines 112-114 `it.artikel == Artikel.DER`) still compiles; optionally show `store.articlelessCount` in the distribution.
SplashView.kt `Artikel.entries` usages are unaffected.

## macOS views (not touched, per ownership; they do not compile until updated)
- StudyView.swift:361 `card.artikel.grammaticalLabel` -> `card.artikel?.grammaticalLabel ?? "Artikelsiz"`; :366/:369/:384 `card.artikel.rawValue` -> `card.displayArtikel`; :671-672 answer check -> `AnswerChecker.isCorrect(card: card, typed: typedArtikel)`.
- WordListView.swift:32 (filter), :42, :326-331 (badge), :737 (`State(initialValue: card.artikel)` needs `Artikel?`), :174/:830 pass `Artikel?`; Pickers need a "-" tag (`Artikel?.none`).
- `macos/scripts/build_dmg.sh` already lists the new `Sources/CardLogic.swift`.

## Files
- Android logic: `Models.kt`, new `CardLogic.kt` (CardCodec, CardMerge, AnswerChecker, ArtikelFilter, CsvCards), `StorageManager.kt`, `GeminiService.kt` (Drive snapshot never treats unreadable cloud file as "no backup"), minimal `StudyView.kt` fixes.
- Swift logic: `Models.swift`, new `CardLogic.swift`, `StorageManager.swift`, `GeminiService.swift` (same snapshot fix).
- Tests: `android/app/src/test/.../ArticlelessTest.kt` (21 JVM tests; added `junit` + `org.json` testImplementation), `macos/Tests/main.swift` + `macos/Tests/run_tests.sh` (54 checks).
