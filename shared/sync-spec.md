# ArtikelMeister Universal Cloud Sync Specification

This document defines the cross-platform cloud synchronization format and rules used across all ArtikelMeister client applications (macOS, Android, iOS, Windows).

---

## 1. Google Drive Structure

- **Target Folder Name:** `ArtikelMeister Cloud Sync` (created at user's Google Drive root)
- **Target File Name:** `artikelmeister_universal_sync.json`
- **MIME Type:** `application/json`

---

## 2. JSON Data Schema (Version 1)

```json
{
  "schemaVersion": 1,
  "appVersion": "1.0.0",
  "lastSyncedAt": "2026-10-25T20:00:00Z",
  "decks": [
    {
      "id": "A1-Basic-Nouns",
      "name": "A1 Temel İsimler",
      "createdAt": "2026-10-20T10:00:00Z"
    }
  ],
  "cards": [
    {
      "id": "uuid-here",
      "deckId": "A1-Basic-Nouns",
      "artikel": "der",
      "word": "Tisch",
      "meaning": "masa",
      "interval": 6,
      "repetitions": 2,
      "easeFactor": 2.5,
      "nextReviewDate": "2026-10-31T09:00:00Z",
      "lastReviewed": "2026-10-25T09:00:00Z",
      "history": []
    }
  ],
  "articlelessCards": [
    {
      "id": "uuid-here",
      "deckId": "A1-Basic-Nouns",
      "word": "Hallo",
      "meaning": "merhaba",
      "interval": 0,
      "repetitions": 0,
      "easeFactor": 2.5,
      "nextReviewDate": "2026-10-31T09:00:00Z",
      "history": []
    }
  ],
  "stats": {
    "streak": 5,
    "lastStudyDate": "2026-10-25",
    "studyDays": ["2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25"]
  }
}
```

### 2.1 Words without an article ("artikelsiz") - optional `articlelessCards`

Some words have no article. They are stored in a separate OPTIONAL top-level array
`articlelessCards`, NEVER in `cards`. `schemaVersion` stays `1`.

- `cards[]` only ever contains cards whose `artikel` is `der`, `die` or `das`. Older clients
  (which do not know `articlelessCards`) ignore the extra key. Writing `"artikel": ""` into
  `cards[]` is forbidden: old Android silently drops such cards and deletes them from the cloud
  copy on the next upload; old macOS fails to decode the whole `cards` array and may wipe the
  local deck / overwrite the cloud file.
- Entries of `articlelessCards` have the same fields as card entries but no `artikel` field
  (if present it is ignored). The key is omitted when there are no article-less words.
- Local persistence mirrors this: `cards.json` (plain array, valid cards only) plus an optional
  `articleless_cards.json` (plain array, article-less cards only).
- CSV (user-facing, ONE file, mixed rows): a row whose article column is empty OR not
  `der`/`die`/`das` (case-insensitive, trimmed; typos included) is imported as an article-less
  word. Export writes an empty article column for them (`,Hallo,merhaba,Genel`) and round-trips.

### 2.2 Lenient decoding and no-overwrite rule (all clients)

- Decoders MUST skip a bad card (missing/invalid fields, empty word, invalid `artikel` inside
  `cards[]`) instead of failing the whole decode.
- If a local file or the cloud file cannot be read/decoded at all (not JSON, `cards` not an
  array, HTTP error on download), the client MUST NOT overwrite it: it keeps a `.corrupt-<ts>`
  copy of a bad local file, blocks local card writes and cloud uploads, and reports an error.
  A bad or unreadable cloud file is never treated as "no cloud backup".

---

## 3. Conflict Resolution & Merging Rules

When local client state meets remote cloud state:

1. **Card Deduplication (Word & Artikel):**
   - Matching key: `artikel.trim().lowercase() + " " + word.trim().lowercase()`.
   - Article-less cards (`articlelessCards`): matching key is the word only,
     `word.trim().lowercase()`. Article-less `Tisch` and `der Tisch` are distinct cards (intended).
     Implementations use the internal key `none_<word>` (valid cards: `<artikel>_<word>`), which is
     also used for `deletedCardKeys` tombstones. Both lists are merged/deduplicated with the same
     rules as `cards`.
   - If a card exists on both devices, it is not duplicated.
   - For learning metrics (`interval`, `repetitions`, `easeFactor`), the record with the most recent `lastReviewed` timestamp or higher repetition count is preserved.

2. **Deck Union:**
   - Decks from both local and cloud are merged by `id` / `name`. Decks present on one device are automatically added to the other.

3. **Streak & Study History:**
   - Daily study history dates are merged using set union.
   - Streak is recalculated based on consecutive study dates up to the current day.
