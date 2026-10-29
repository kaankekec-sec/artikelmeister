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
| `Artikel.parse(raw)` is used by CSV import too: der/die/das plus aliases m/maskulin/masculine, f/feminin/feminine, n/neutrum/neuter; anything else or empty -> article-less | same |
| `Artikel` enum itself is UNCHANGED (still only DER/DIE/DAS, so `Artikel.entries` pickers show no extra button) | same |

## Filter ('-')
- Filter value for article-less words: `ArtikelFilter.NONE == "-"` (Swift `ArtikelFilter.none`). Other filter values: `"der"`, `"die"`, `"das"`; `null`/`nil`/blank = all.
- `ArtikelFilter.matches(card, filter)`, `ArtikelFilter.apply(cards, filter)`
- `ArtikelFilter.counts(cards): ArtikelFilter.Counts(total, der, die, das, none)`; `Counts.forFilter(filter)`.
- Store: `DeckStore.artikelCounts(forDeck: String? = null): ArtikelFilter.Counts`, `DeckStore.articlelessCount: Int` (Kotlin `store.artikelCounts()`, Swift `store.artikelCounts(forDeck:)`).

## Study / answer check
- `AnswerChecker.isCorrect(card: Flashcard, typed: String): Boolean` (Swift `AnswerChecker.isCorrect(card:typed:)`).
  - Valid card: unchanged (typed trimmed + case-insensitive == der/die/das).
  - Article-less card: the user sees the German word and TYPES THE TURKISH MEANING (card.meaning). Compared after trim, inner-whitespace collapse and Turkish-locale lowercase (tr_TR: I->dotless i, U+0130->i). If the meaning has several parts separated by `,` `;` `/`, any ONE part (or the whole text) is correct. Blank is never correct; '-' is not special any more.
- `AnswerChecker.expectedLabel(card): String` -> article ("der"/...) or the meaning for article-less cards (use in the "correct answer" feedback; label word "Anlam" vs "Artikel").
- Helpers: `AnswerChecker.normalizeMeaning(s)`, `AnswerChecker.acceptedMeanings(meaning): Set<String>`.
- Queue and SM-2 need no UI work (same `store.cards` list; SM2 tested with article-less cards).
- For article-less cards do NOT show the meaning on the front (it is the answer), i.e. ignore `showMeaningOnFront` when `card.isArticleless`.

## Store API changes
- `DeckStore.addCard(artikel: Artikel?, word, meaning, deckName)` and `updateCard(id, artikel: Artikel?, ...)` accept null/nil. Dedup key = `syncKey` (article-less: word only, case-insensitive).
- `DeckStore.cardsLoadFailed` (Kotlin `val`, Swift `@Published private(set)`): true when local card files could not be decoded; writes and cloud sync are blocked. UI may show a warning (optional).
- CSV: `importCSVContent` / `exportCSVString` signatures unchanged; empty or non-der/die/das article column -> article-less word; export writes an empty article column.
- Persistence: valid cards -> `cards.json` / Drive `cards[]`; article-less -> `articleless_cards.json` / Drive `articlelessCards`. See `shared/sync-spec.md` section 2.1/2.2.

## What the designer must do on Android (neither file compiles / works until done)
`Flashcard.artikel` is now nullable.

WordListView.kt (compile errors at `card.artikel` ~142, 806, 810, 813, 1506, 1510, 1513):
- search (~142): `card.artikel.rawValue` -> `card.displayArtikel`.
- badge (~806-813, preview ~1506-1513): text `card.displayArtikel`; colors `card.artikel?.color ?: <neutral grey>` and `card.artikel?.badgeBackground ?: <grey alpha .15>`.
- '-' filter chip/menu entry: change `selectedArtikelFilter` to `String?` (null = all) and filter with `ArtikelFilter.matches(card, selectedArtikelFilter)`. Entries: "Tüm Artikeller" (null), der/die/das (`art.rawValue`), and "-" (`ArtikelFilter.NONE`, label e.g. "- Artikelsiz"). Show counts from `store.artikelCounts()` via `Counts.forFilter(filter)`.
- add-word sheet (~295-312) and edit dialog (~1254-1326): add a "-" option mapping to `artikel = null` (`store.addCard(null, ...)`, `store.updateCard(..., artikel = null, ...)`); `newArtikel` must become `Artikel?` (`mutableStateOf<Artikel?>(Artikel.DER)`), `editArtikel` is inferred `Artikel?`.
- Optional: StatsAndSettingsView distribution can show `store.articlelessCount` (lines 112-114 still compile).

StudyView.kt (compiles; minimal logic only, UI for article-less is still missing):
- `checkAndRevealCard` already calls `AnswerChecker.isCorrect(card, typedArtikel)` and the feedback text uses `AnswerChecker.expectedLabel(card)`.
- For `card.isArticleless` the front must show a single-line text input (not the der/die/das buttons) for the Turkish meaning, wired to `typedArtikel` / `onTypedArtikelChange`, submit on IME Done -> `checkAndRevealCard(card)`; prompt e.g. "Kelimenin Türkçe anlamını yazın". Hide the der/die/das buttons and legend for these cards; hide the meaning on the front.
- On reveal: show only the word (the code already renders an empty article text, remove the empty article text/spacer); feedback shows "Doğru Anlam: <meaning>".

## macOS views (done, logic-only)
`StudyView.swift` and `WordListView.swift` were minimally fixed and the whole `macos/Sources/*.swift` set typechecks (`swiftc -typecheck`). StudyView reuses the existing TextField for article-less cards (placeholder "anlam...", wider field, meaning-prompt text, no meaning on front, feedback via `AnswerChecker.expectedLabel`). WordListView: filter is a `String?` ("-" entry), add/edit pickers have a "-" option, badge shows "-" in grey. No visual redesign.

## Files
- Android logic: `Models.kt`, new `CardLogic.kt` (CardCodec, CardMerge, AnswerChecker, ArtikelFilter, CsvCards), `StorageManager.kt`, `GeminiService.kt` (Drive snapshot never treats unreadable cloud file as "no backup"), minimal `StudyView.kt` fixes.
- Swift logic: `Models.swift`, new `CardLogic.swift`, `StorageManager.swift`, `GeminiService.swift` (same snapshot fix).
- Tests: `android/app/src/test/.../ArticlelessTest.kt` (23 JVM tests; added `junit` + `org.json` testImplementation), `macos/Tests/main.swift` + `macos/Tests/run_tests.sh` (66 checks).
