# ArtikelMeister

Got tired of guessing *der*, *die*, *das* so I made an app for it.

It has a bloody brain icon because remembering German genders hurts.

### Features

- **Spaced Repetition:** Drills the articles you struggle with using smart review intervals.
- **Active Recall:** Type the article before flipping the card.
- **Universal Cloud Sync:** Synchronizes flashcards, streaks, and progress seamlessly across macOS and Android via Google Drive.
- **Smart Duplicate Prevention:** Merges words added from different devices without duplicate entries.
- **Gemini AI Generator:** Automatically creates CEFR-graded German vocabulary with meanings and articles.
- **Offline First:** Fully functional without an internet connection, storing data locally on your device.
- **Deck Management:** Organize words into custom decks or import/export via CSV.

---

### Project Structure

```text
ArtikelMeister/
├── macos/              # macOS desktop application (Swift / SwiftUI / AppKit)
│   ├── Sources/        # Application source code
│   └── scripts/        # Build and packaging scripts (DMG installer)
├── android/            # Android mobile application (Jetpack Compose & Kotlin)
│   ├── app/            # Application module
│   └── build.gradle.kts
├── shared/             # Cross-platform shared specifications and sample data
│   ├── samples/        # Sample vocabulary CSV sheets
│   └── sync-spec.md    # Universal Google Drive sync format specification
└── README.md
```

---

### Building & Running

#### macOS
Requires macOS 13 or newer. Universal binary supports both Apple Silicon (arm64) and Intel (x86_64).

```bash
cd macos
chmod +x scripts/build_dmg.sh
./scripts/build_dmg.sh
```

#### Android
Requires Android Studio Ladybug (or newer) with Android SDK 35 (minSdkVersion 26).

1. Open the `android/` directory in Android Studio.
2. Build and run on a connected device or emulator:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

---

### License

MIT

---

Google is forcing me to include a formal privacy policy for OAuth verification, so here is the serious legal text:

---

# Privacy Policy for ArtikelMeister

**Last Updated:** October 24, 2026

ArtikelMeister ("the Application", "we", "us", or "our") is an open-source, client-side macOS application designed to help users learn German noun articles and vocabulary using spaced repetition and optional Google Gemini AI features. 

We respect your privacy. This Privacy Policy explains how our desktop application handles user data, authentication credentials, and Google user data in compliance with the **Google API Services User Data Policy**, including the **Limited Use** requirements.

---

### 1. Overview & Core Architecture
ArtikelMeister is fundamentally a **local-first, offline-capable desktop application**:
- All study data, decks, flashcards, Anki SM-2 spaced repetition intervals, and daily streaks are stored strictly on your local macOS filesystem in `~/Library/Application Support/ArtikelMeister/`.
- We do not operate any external tracking servers, analytics endpoints, advertising SDKs, or remote databases.
- The Application does not collect, log, transmit, or monetize your personal information.

---

### 2. Information We Access and How It Is Used

#### A. Google User Data (OAuth 2.0 & Gemini API)
If you optionally connect your Google Account via Google OAuth 2.0 or provide a Gemini API Key:
- **Scope of Access:** The Application requests access solely to Google Generative Language APIs (Gemini) for the express purpose of generating CEFR-graded German vocabulary words at your explicit instruction.
- **Data Accessed:** We do not request, access, read, or store any personal profile information, Google Drive files, Gmail messages, contacts, or calendar data.
- **Storage of Tokens:** Any OAuth access tokens, refresh tokens, or API keys you provide are stored securely on your local device using **macOS Keychain Services** (`kSecClassGenericPassword`). They are never uploaded to any third-party server.
- **Transmission:** API queries are made directly from your Mac to Google's official endpoints (`generativelanguage.googleapis.com`) over secure TLS/HTTPS connections.

#### B. Local Application Data
- Flashcard items, meanings, grammatical genders (`der`, `die`, `das`), and scheduling metadata are stored locally as standard JSON files on your computer.
- You have complete control over this data and may export, back up, or delete it at any time.

---

### 3. Data Sharing and Disclosure
We do not sell, rent, trade, share, or disclose any user data to third parties. 

- **No Third-Party Analytics:** The Application contains zero tracking libraries, cookies, telemetry scripts, or ad networks.
- **Direct Interaction with Google APIs:** When utilizing the AI vocabulary generator, the generated prompts (consisting only of target language level, word count, and excluded existing words) are transmitted directly to Google's servers in accordance with [Google's Privacy Policy](https://policies.google.com/privacy).

---

### 4. Google API Limited Use Disclosure
ArtikelMeister's use and transfer of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements:
- Google user data is used solely to provide user-facing vocabulary generation features.
- Google user data is never used for serving advertisements, training third-party AI models, or surveillance.
- Humans are not permitted to read your tokens or private API transactions.

---

### 5. Data Retention, Revocation, and Deletion

You retain full ownership and control over your credentials and data:
- **Revoking OAuth Access:** You can disconnect your Google account at any time within the Application by clicking "Yetkiyi Kaldır" (Sign Out) in the Gemini Settings, which immediately deletes all stored tokens from your macOS Keychain. You can also revoke access remotely at [Google Account Permissions](https://myaccount.google.com/permissions).
- **Deleting Application Data:** To delete all study history, words, and settings, click "Tüm Kelimeleri Sil" (Delete All Cards) in the Application Settings, or delete the local storage directory:
  ```bash
  rm -rf ~/Library/Application\ Support/ArtikelMeister
  ```

---

### 6. Security
All authentication tokens are protected by macOS system-level hardware encryption via the **macOS Keychain**. All network communications with Google APIs use industry-standard HTTPS/TLS encryption.

---

### 7. Changes to This Privacy Policy
Any updates to this policy will be reflected directly in this file in the GitHub repository. Continued use of the Application signifies acceptance of any updated terms.

---

### 8. Contact
If you have questions, feedback, or concerns regarding this Privacy Policy, please open an issue in the official GitHub repository:  
https://github.com/kaankekec-sec/artikelmeister
