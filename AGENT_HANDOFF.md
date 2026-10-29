# ArtikelMeister: Agent Handoff Log

Written 2026-10-29 for any AI agent or developer continuing this project on another platform.
Covers everything done after commit `4e83c00` ("feat: integrate native Google Play Services Sign-In ...") up to `9354454`.
All of it is merged into local `main`. **Nothing was pushed.** `android/release/*` (APK, baseline profiles) has uncommitted user changes: leave it alone.

## Project in one paragraph
German article (der/die/das) learning app, flashcards with SM2 spaced repetition. Monorepo: `android/` (Kotlin, Compose, Material3, single module, package `com.artikelmeister.android`), `macos/` (SwiftUI, built with `macos/scripts/build_dmg.sh`), `shared/` (sync spec `shared/sync-spec.md`, logos, sample data). Android and macOS are parallel hand-written ports (no shared code). The study flow: show the German word, user guesses the article, then the Turkish meaning appears. UI text is Turkish. Drive sync file: `artikelmeister_universal_sync.json`, schemaVersion 1.

## What was built (chronological)

### 1. Article-less words ("artikelsiz", shown as `-`)
- CSV import is ONE mixed file. Article column empty or not der/die/das (aliases m/maskulin/masculine, f/feminin/feminine, n/neutrum/neuter still map to der/die/das) => word imported as article-less. CSV export writes an empty article column; round-trips.
- Model: `Flashcard.artikel` is now nullable (`Artikel?`), helpers `isArticleless`, `displayArtikel`, `Flashcard.keyFor`. Sync/dedup key for article-less = `none_<word>` (case-insensitive, trimmed); article-less "Tisch" and "der Tisch" stay distinct on purpose.
- **Backward-safe persistence (important design decision):** persisting `"artikel": ""` inside `cards[]` would make old macOS builds fail the whole decode and wipe the local deck, and old Android silently drops such cards. So `cards[]`/`cards.json` only hold valid der/die/das cards; article-less cards live in an optional top-level array `articlelessCards` in the sync JSON and in `articleless_cards.json` locally. Old readers ignore it. schemaVersion stays 1. Documented in `shared/sync-spec.md`.
- Decoders (both platforms) are lenient: a bad card is skipped. On a local decode failure the app keeps a `.corrupt-<ts>` copy, stops writing cards and stops cloud sync. A cloud file that cannot be downloaded/decoded now aborts the sync instead of being treated as "no backup" (previously could overwrite cloud data).
- Study: article-less cards show only the German word and a single-line text input; the user types the TURKISH MEANING. `AnswerChecker.isCorrect(card, typed)`: trimmed, whitespace-collapsed, Turkish-locale case folding (I/ı, İ/i), meaning split on `,` `;` `/` and any one part (or the whole text) is correct. Blank is never correct. Same Again/Hard/Good/Easy grading as normal cards.
- Word Pool: 5-segment article filter Tümü/der/die/das/`-` with counts; `-` option in add/edit pickers (meaning required for article-less); grey badge.
- Key files: `CardLogic.kt` (new; CsvCards, AnswerChecker, ArtikelFilter, merge/dedup), `Models.kt`, `StorageManager.kt`, macOS `CardLogic.swift`, `Models.swift`, `StorageManager.swift`. `CONTRACT.md` (repo root) documents the exact names/signatures.

### 2. Android UI pass
- Word Pool top bar: no horizontal scroll at 360dp. Title + count, one primary "AI" button, a ⋮ menu (CSV import/export, new deck, rename/delete deck); labels expand from 600dp, CSV buttons inline from 840dp. Visible row ⋮ menu, speak button, tap-to-edit, swipe-to-delete, "Geri al" snackbar (delete is deferred until snackbar times out / tab leaves, so undo needs no data-layer support).
- Theme: System/Light/Dark setting (`UiPreferences` adapter in `Theme.kt`, also writes legacy `isDarkMode` so sync/macOS stay consistent); system-bar styling only in `MainActivity` (`enableEdgeToEdge`); M3 Typography with 12sp minimum; 48dp touch targets; TalkBack semantics in touched files.
- Palette: der = blue, die = magenta, das = gold, `-` = grey; semantic `success/error/warning/info`; `vividGreen/vividRed` kept as aliases. Reason: old der/das colours clashed with correct(green)/wrong(red). Use `art.themeColor()` / `colors.artikelColor(artikel)`.
- Splash: Android 12 platform splash; animated intro only on first launch (tap to skip). Tabs saved across restarts, crossfade, back handling. Reusable `StateMessageView` for empty/error states. Many strings moved to `res/values/strings.xml` (not all).

### 3. Bug fixes the user reported on Android
1. **"Bulut eşitleme hatası: The coroutine scope left the composition".** Root cause: after Google sign-in, `hasCompletedOnboarding = true` removed the onboarding screen, cancelling its `rememberCoroutineScope` mid-sync, and `syncWithGoogleDrive` showed the `CancellationException` as an error. Fix: `DeckStore.completeGoogleSignIn / requestCloudSync / requestDeleteAccountAndAllData` run in the store's own scope; cancellation is rethrown, never shown; `isSyncingCloud` reset in `finally`; `CoroutineSupport.kt` (`runCatchingCancellable`, `SyncErrors`) replaces `runCatching` around suspend calls.
2. **CSV import of a non-CSV file wrecked the deck.** Causes: picker allowed `*/*`; the article-less feature made every row with an invalid article a card; no validation or backup. Fix: pickers use `CsvCards.ACCEPTED_MIME_TYPES`; `.csv` extension is the primary gate (Turkish error); `CsvCards.validate/decode` rejects empty/oversized (10 MB), binary (NUL or >1% non-printable), undecodable text (UTF-8, UTF-16 BOM, Windows-1254), JSON/XML/HTML-looking content, no delimiter, no valid rows, or >50% invalid rows; implausible rows inside an accepted file are skipped. Rejection changes nothing and triggers no sync. Before every import a snapshot goes to `Backups/pre_import_*.json` (last 3); result dialogs have "Geri al"; cloud upload is deferred while an import can still be undone. The JSON backup import also now requires a `cards`/`articlelessCards` array and snapshots before a replace. Mirrored on macOS.
3. **Gemini "HTTP 403 ... insufficient authentication scopes".** Root cause: native Play Services sign-in requested only `drive.file`; the manual deep-link OAuth flow already also requested `generative-language.retriever` and `cloud-platform`. Fix: native sign-in requests Drive + Gemini scopes (falls back to Drive-only token if consent/Cloud project rejects them); Drive calls deliberately keep the Drive-only scope set; new `validGeminiAccessToken` for Gemini calls; 403 insufficient-scopes throws `GeminiScopeException` with a Turkish re-consent message and the existing "Google Hesabını Yeniden Bağla" button.

### 4. macOS
- Article-less logic mirrored (Swift), CSV validation + undo, cancellation handling in sync catch, view compile fixes, and palette parity with Android (`AppPalette` in `Models.swift`).
- `macos/ArtikelMeister.dmg` rebuilt 2026-10-29 04:05 via `scripts/build_dmg.sh` (needs sandbox disabled: SwiftUI macros). `CardLogic.swift` added to the script's explicit source list. The old dmg was backed up under the session temp dir (not in the repo). `*.dmg` is gitignored.

## Tests
- Android JVM unit tests (51, 0 failures when last run by the engineer agent): `ArticlelessTest`, `SyncAndScopesTest`, `CsvImportSafetyTest`. `junit` and `org.json` added as `testImplementation`.
- macOS: `macos/Tests/run_tests.sh` (96 checks), builds only `Models.swift` + `CardLogic.swift`; `swiftc -typecheck macos/Sources/*.swift` clean.
- Gradle only works with Android Studio's JBR 21 (`~/Library/Java/JavaVirtualMachines/jbr-21.0.x`); system JDK 25/27 breaks the Kotlin DSL / Kotlin 2.0.21. Needs `local.properties` (gitignored).
- The user confirmed on 2026-10-29 that the Android build on their phone works. The macOS app and Drive sync were never run by agents.

## Known gaps / ideas for next steps
- `DeckStore` is created per Activity (`remember` in `setContent`); after activity recreation the old store's scope keeps running next to the new one. Follow-up: process-scoped store / ViewModel. `DeckStore` (`StorageManager.kt`, ~1400 lines) is still a god object; no DI, no ViewModel, no Navigation Compose.
- Hardcoded Turkish strings remain in `StatsAndSettingsView`, `AIGeneratorSheet`, onboarding, sidebar, `StudyView`; no `values-en`.
- Study screen's own top bar is still crowded at 360dp; some Stats/AI-sheet click targets are under 48dp.
- `Models.kt` still has old hardcoded red/green in `Artikel.color`, `CardStage.color`, `ReviewRating.color`; `AppSettings.themeMode` could replace `UiPreferences`.
- Default Gemini model `gemini-3.8-flash` (and `gemini-1.5-*`/`2.0-*` in the model list) could not be verified from the dev environment. If AI calls still fail with 404/403, check this first, then the Google Cloud OAuth consent screen: `generative-language.retriever` and `cloud-platform` are sensitive scopes and must be configured there.
- OAuth client id/secret are embedded in both apps (macOS obfuscated by XOR, which is not security) and present in git history. The owner knowingly accepted this risk (small prepaid balance). Do not "fix" it without asking.
- Not verified on device: article-less Study text input path on macOS, swipe-to-delete, System theme following the device, TalkBack, Drive sync live, old-reader behaviour against `articlelessCards` (derived from reading old code, not from running old builds).

## Working agreements with the owner
- Owner communicates in Turkish. Ask before outward-facing/irreversible actions. Do not touch `android/release/*`.
- Commits made during this work were co-authored by Claude models (see commit trailers).
- Commits `0754f0d` and `5d8fc9b` (README revisions, 2026-10-26) are the owner's own commits that were already on `origin/main`; they are not part of the agent work.
- A later macOS-only commit (`d99f73f`) adds the System/Light/Dark theme setting (`ThemeMode` in `StorageManager.swift`, stored device-locally in UserDefaults and mirrored into `AppSettings.isDarkMode` for explicit light/dark), an animated sidebar toggle, and a wider 4-segment article picker in the add-word row.

## Commit list since `4e83c00`
```
0754f0d Revise README for clarity and feature updates
5d8fc9b Remove redundant code block from README
aa7c8b0 android: support article-less words (model, CSV, lenient decode, backward-safe persistence, merge, study check)
48be9bb feat(android/ui): redesign Word Pool for phones, theme tokens, typography, platform splash
cb22273 macos: article-less words (model, lenient decode, no-overwrite, merge, CSV, filter, tests); sync-spec + CONTRACT
314152e feat(android/ui): theme mode setting, saveable tabs with crossfade/back, first-launch intro
a6b7cb9 fix(android/ui): Word Pool width classes, pending-delete counts, short nav label
88b13ed Article-less study: type the Turkish meaning (tr-locale compare, multi-meaning); restore CSV article aliases; fix macOS view compile; update contract
c6b75e2 fix(android/ui): settings limit rows and stats heatmap at 360dp
5c92e6e Merge branch 'worktree-agent-a319095fcf2074170' into worktree-agent-adb7596b149467d70
268af3e feat(android/ui): article-less ('-') words in Word Pool and Study
d81474b fix: cloud sync cancellation shown as error; Gemini scopes for native sign-in (steps 1+3)
6260dee fix: validate CSV imports before touching data, add import snapshot and undo (step 2)
36d94d6 feat(macos): align der/die/das/'-' and success/error colours with the Android palette (step 4)
9354454 fix: review follow-ups (token persistence order, undo vs cloud sync, backup JSON guard)
```
