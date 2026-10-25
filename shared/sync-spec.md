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
  "stats": {
    "streak": 5,
    "lastStudyDate": "2026-10-25",
    "studyDays": ["2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25"]
  }
}
```

---

## 3. Conflict Resolution & Merging Rules

When local client state meets remote cloud state:

1. **Card Deduplication (Word & Artikel):**
   - Matching key: `artikel.trim().lowercase() + " " + word.trim().lowercase()`.
   - If a card exists on both devices, it is not duplicated.
   - For learning metrics (`interval`, `repetitions`, `easeFactor`), the record with the most recent `lastReviewed` timestamp or higher repetition count is preserved.

2. **Deck Union:**
   - Decks from both local and cloud are merged by `id` / `name`. Decks present on one device are automatically added to the other.

3. **Streak & Study History:**
   - Daily study history dates are merged using set union.
   - Streak is recalculated based on consecutive study dates up to the current day.
