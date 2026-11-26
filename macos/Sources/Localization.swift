import Foundation
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case tr
    case en
    case de

    var id: String { rawValue }

    var localeIdentifier: String {
        switch self {
        case .tr: return "tr_TR"
        case .en: return "en_US"
        case .de: return "de_DE"
        }
    }

    var displayName: String {
        switch self {
        case .tr: return "Türkçe"
        case .en: return "English"
        case .de: return "Deutsch"
        }
    }

    static func resolveSystemLanguage(preferredLanguages: [String] = Locale.preferredLanguages) -> AppLanguage {
        for lang in preferredLanguages {
            let lower = lang.lowercased()
            if lower.hasPrefix("de") { return .de }
            if lower.hasPrefix("en") { return .en }
            if lower.hasPrefix("tr") { return .tr }
        }
        return .tr
    }
}

final class LanguageManager: ObservableObject {
    static let shared = LanguageManager()

    private static let defaultsKey = "com.artikelmeister.appLanguage"

    @Published var language: AppLanguage {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: Self.defaultsKey)
            Self.cachedResolved = language
        }
    }

    private static var cachedResolved: AppLanguage = {
        let raw = UserDefaults.standard.string(forKey: defaultsKey)
        return raw.flatMap(AppLanguage.init(rawValue:)) ?? AppLanguage.resolveSystemLanguage()
    }()

    static var current: AppLanguage {
        cachedResolved
    }

    var resolvedLanguage: AppLanguage {
        language
    }

    var activeLocale: Locale {
        Locale(identifier: language.localeIdentifier)
    }

    init() {
        let raw = UserDefaults.standard.string(forKey: Self.defaultsKey)
        let saved = raw.flatMap(AppLanguage.init(rawValue:)) ?? AppLanguage.resolveSystemLanguage()
        self.language = saved
        Self.cachedResolved = saved
    }
}

enum L10n {
    static func text(_ tr: String, en: String, de: String, lang: AppLanguage = LanguageManager.current) -> String {
        switch lang {
        case .tr:
            return tr
        case .en:
            return en
        case .de:
            return de
        }
    }

    /// Used by unit tests to verify every translation key is non-empty in all three languages.
    static var allCoreKeysForTesting: [(String, (AppLanguage) -> String)] {
        [
            ("appTitle", { text("ArtikelMeister", en: "ArtikelMeister", de: "ArtikelMeister", lang: $0) }),
            ("tabStudy", { tabStudy($0) }),
            ("tabWords", { tabWords($0) }),
            ("tabStats", { tabStats($0) }),
            ("tabSettings", { tabSettings($0) }),
            ("updateRequiredTitle", { updateRequiredTitle($0) }),
            ("updateButton", { updateButton($0) }),
            ("recheckButton", { recheckButton($0) }),
            ("settingsLanguage", { settingsLanguage($0) }),
            ("studyCheck", { studyCheck($0) }),
            ("wordsTitle", { wordsTitle($0) }),
            ("aiSheetTitle", { aiSheetTitle($0) })
        ]
    }

    // MARK: - Tabs & Navigation
    static func tabStudy(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Çalış", en: "Study", de: "Lernen", lang: l)
    }
    static func tabWords(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Kelime Havuzu", en: "Word Pool", de: "Wortschatz", lang: l)
    }
    static func tabStats(_ l: AppLanguage = LanguageManager.current) -> String {
        text("İstatistikler", en: "Statistics", de: "Statistiken", lang: l)
    }
    static func tabSettings(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Ayarlar", en: "Settings", de: "Einstellungen", lang: l)
    }

    // MARK: - Common
    static var cancel: String { text("İptal", en: "Cancel", de: "Abbrechen") }
    static var close: String { text("Kapat", en: "Close", de: "Schließen") }
    static var save: String { text("Kaydet", en: "Save", de: "Speichern") }
    static var ok: String { text("Tamam", en: "OK", de: "OK") }
    static var undo: String { text("Geri al", en: "Undo", de: "Rückgängig") }
    static var allDecks: String { text("Tüm Desteler", en: "All Decks", de: "Alle Stapel") }
    static var newDeckAdd: String { text("Yeni Deste Ekle", en: "New Deck", de: "Neuer Stapel") }
    static var newDeckAddEllipsis: String { text("Yeni Deste Ekle…", en: "New Deck…", de: "Neuer Stapel…") }
    static var menuSection: String { text("MENÜ", en: "MENU", de: "MENÜ") }
    static var decksSection: String { text("DESTELER", en: "DECKS", de: "STAPEL") }

    // MARK: - Update Gate
    static func updateRequiredTitle(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Güncelleme Gerekli", en: "Update Required", de: "Update erforderlich", lang: l)
    }
    static func updateRequiredMessage(_ tag: String) -> String {
        text(
            "ArtikelMeister \(tag) sürümü yayınlandı. Devam etmek için lütfen uygulamayı güncelleyin.",
            en: "ArtikelMeister \(tag) is now available. Please update the app to continue.",
            de: "ArtikelMeister \(tag) ist verfügbar. Bitte aktualisiere die App, um fortzufahren."
        )
    }
    static func currentVersionLabel(_ v: String) -> String {
        text("Mevcut: v\(v)", en: "Current: v\(v)", de: "Aktuell: v\(v)")
    }
    static func latestVersionLabel(_ tag: String) -> String {
        text("Yeni: \(tag)", en: "New: \(tag)", de: "Neu: \(tag)")
    }
    static func updateButton(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Güncelle", en: "Update Now", de: "Jetzt aktualisieren", lang: l)
    }
    static func recheckButton(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Tekrar Kontrol Et", en: "Check Again", de: "Erneut prüfen", lang: l)
    }
    static var checkingUpdate: String {
        text("Kontrol ediliyor...", en: "Checking...", de: "Wird geprüft...")
    }

    // MARK: - Study View
    static func studyCheck(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Kontrol Et", en: "Check", de: "Prüfen", lang: l)
    }
    static var studyEmptyDeckTitle: String {
        text("Bu destede henüz kelime yok", en: "No words in this deck yet", de: "Noch keine Wörter in diesem Stapel")
    }
    static var studyEmptyDeckDesc: String {
        text(
            "Bu desteye kelime eklemek için CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.",
            en: "Import a CSV or generate words with AI to add words to this deck.",
            de: "Importiere eine CSV-Datei oder generiere Wörter mit KI, um diesen Stapel zu füllen."
        )
    }
    static var studySummaryLabel: String {
        text("Çalışma özeti", en: "Study summary", de: "Lernübersicht")
    }
    static var studyOtherActions: String {
        text("Diğer çalışma işlemleri", en: "Other study actions", de: "Weitere Lernaktionen")
    }
    static var studyDeckLabel: String {
        text("Çalışma destesi", en: "Study deck", de: "Lernstapel")
    }
    static func streakLabel(_ days: Int) -> String {
        text("Seri: \(days) gün", en: "Streak: \(days) d", de: "Serie: \(days) T.")
    }
    static func longestStreakLabel(_ days: Int) -> String {
        text("En uzun seri: \(days) gün", en: "Longest streak: \(days) days", de: "Längste Serie: \(days) Tage")
    }
    static var aiGenerateButton: String {
        text("AI ile kelime üret", en: "Generate with AI", de: "Mit KI generieren")
    }
    static var csvImportEllipsis: String {
        text("CSV içe aktar…", en: "Import CSV…", de: "CSV importieren…")
    }
    static var listenLabel: String {
        text("Dinle", en: "Listen", de: "Anhören")
    }
    static var listenHelp: String {
        text("Almanca telaffuzu dinle", en: "Listen to German pronunciation", de: "Deutsche Aussprache anhören")
    }
    static var meaningPlaceholder: String {
        text("Anlamı yazın...", en: "Type meaning...", de: "Bedeutung eingeben...")
    }
    static var correctBanner: String {
        text("DOĞRU!", en: "CORRECT!", de: "RICHTIG!")
    }
    static var blankBanner: String {
        text("BOŞ BIRAKILDI", en: "LEFT BLANK", de: "LEER GELASSEN")
    }
    static func wrongBanner(_ answer: String) -> String {
        text("YANLIŞ (\(answer))", en: "WRONG (\(answer))", de: "FALSCH (\(answer))")
    }
    static func expectedMeaning(_ expected: String) -> String {
        text("Doğru Anlam: \(expected)", en: "Correct Meaning: \(expected)", de: "Richtige Bedeutung: \(expected)")
    }
    static func expectedArtikel(_ expected: String) -> String {
        text("Doğru Artikel: \(expected)", en: "Correct Article: \(expected)", de: "Richtiger Artikel: \(expected)")
    }
    static var wrongRatingHint: String {
        text(
            "Yanlış cevapta yalnızca ‘Tekrar (1)’ veya ‘Zor (2)’ seçilebilir:",
            en: "On a wrong answer, only ‘Again (1)’ or ‘Hard (2)’ is available:",
            de: "Bei falscher Antwort sind nur ‚Nochmal (1)‘ oder ‚Schwer (2)‘ verfügbar:"
        )
    }
    static var waitingTimerTitle: String {
        text("Öğrenme Kartı Zamanlayıcısı", en: "Learning Card Timer", de: "Lernkarten-Timer")
    }
    static var waitingTimerDesc: String {
        text(
            "Sıradaki kelime süre dolduğunda otomatik açılır.",
            en: "The next word opens automatically when the timer ends.",
            de: "Das nächste Wort erscheint automatisch, wenn der Timer abläuft."
        )
    }
    static var increaseLimitButton: String {
        text("Günlük yeni kart sınırını 10 artır", en: "Increase daily new card limit by 10", de: "Tägliches Neukarten-Limit um 10 erhöhen")
    }
    static var emptyDbTitle: String {
        text("Henüz Kelime Eklenmedi", en: "No Words Yet", de: "Noch keine Wörter")
    }
    static var emptyDbDesc: String {
        text(
            "Örnek kelimelerle başlayabilir, CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.",
            en: "Start with sample words, import a CSV, or generate words with AI.",
            de: "Starte mit Beispielwörtern, importiere eine CSV-Datei oder generiere Wörter mit KI."
        )
    }
    static var starterPackButton: String {
        text("Örnek 30 Kelime", en: "30 Sample Words", de: "30 Beispielwörter")
    }
    static var showStorageInFinder: String {
        text("Yerel Depolama Klasörünü Finder'da Göster", en: "Show Local Storage Folder in Finder", de: "Lokalen Speicherordner im Finder zeigen")
    }
    static func deckCompletedTitle(_ deck: String?) -> String {
        if let deck {
            return text("'\(deck)' Destesi Tamamlandı!", en: "'\(deck)' Deck Completed!", de: "Stapel ‚\(deck)‘ abgeschlossen!")
        }
        return text("Bugünkü Çalışma Tamamlandı!", en: "Today's Study Completed!", de: "Heutiges Lernen abgeschlossen!")
    }
    static var completedDesc: String {
        text(
            "Günlük hedeflerinizi ve bekleyen tekrarlarınızı tamamladınız.",
            en: "You have completed your daily goals and due reviews.",
            de: "Du hast deine Tagesziele und fälligen Wiederholungen abgeschlossen."
        )
    }

    // MARK: - Word Pool
    static func wordsTitle(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Kelime Havuzu", en: "Word Pool", de: "Wortschatz", lang: l)
    }
    static var addButton: String { text("Ekle", en: "Add", de: "Hinzufügen") }
    static var editWordTitle: String { text("Kelimeyi Düzenle", en: "Edit Word", de: "Wort bearbeiten") }
    static var saveChanges: String { text("Değişiklikleri Kaydet", en: "Save Changes", de: "Änderungen speichern") }
    static var germanWordLabel: String { text("Almanca Kelime", en: "German Word", de: "Deutsches Wort") }
    static var meaningLabel: String { text("Anlamı", en: "Meaning", de: "Bedeutung") }
    static var deckLabel: String { text("Deste", en: "Deck", de: "Stapel") }
    static var newDeckShort: String { text("Yeni Deste", en: "New Deck", de: "Neuer Stapel") }
    static var searchPlaceholder: String {
        text("Kelime, anlam veya deste ara…", en: "Search word, meaning, or deck…", de: "Wort, Bedeutung oder Stapel suchen…")
    }
    static var allArticles: String { text("Tüm Artikeller", en: "All Articles", de: "Alle Artikel") }
    static var articleless: String { text("Artikelsiz", en: "Article-less", de: "Ohne Artikel") }
    static var allStages: String { text("Tüm Durumlar", en: "All Stages", de: "Alle Phasen") }
    static var articlelessMeaningRequired: String {
        text(
            "Artikelsiz kelimelerde anlam zorunludur.",
            en: "Meaning is required for article-less words.",
            de: "Bei artikellosen Wörtern ist eine Bedeutung erforderlich."
        )
    }

    // MARK: - Settings & Stats
    static func settingsLanguage(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Dil", en: "Language", de: "Sprache", lang: l)
    }
    static var appearanceTitle: String { text("Görünüm", en: "Appearance", de: "Erscheinungsbild") }
    static var dailyLimitsTitle: String { text("Günlük Çalışma Limitleri", en: "Daily Study Limits", de: "Tägliche Lernlimits") }
    static var dailyNewLimitTitle: String { text("Günlük Yeni Kelime Sayısı", en: "Daily New Words", de: "Neue Wörter pro Tag") }
    static var dailyReviewLimitTitle: String { text("Günlük Maksimum Tekrar Sayısı", en: "Daily Max Reviews", de: "Max. Wiederholungen pro Tag") }
    static var cardPrefsTitle: String { text("Kart Sunum Tercihleri", en: "Card Preferences", de: "Karten-Einstellungen") }
    static var autoPlayPref: String {
        text(
            "Kart çevrildiğinde Almanca kelimeyi otomatik seslendir",
            en: "Automatically pronounce German word on reveal",
            de: "Deutsches Wort beim Aufdecken automatisch vorlesen"
        )
    }
    static var showMeaningPref: String {
        text(
            "Kartın ön yüzünde anlamı (ipucu olarak) göster",
            en: "Show meaning on the front of the card (as a hint)",
            de: "Bedeutung auf der Vorderseite als Hinweis anzeigen"
        )
    }
    static var randomOrderPref: String {
        text(
            "Yeni kelimeleri eklenme sırası yerine rastgele sırayla getir",
            en: "Show new words in random order instead of added order",
            de: "Neue Wörter zufällig statt in Hinzufüge-Reihenfolge zeigen"
        )
    }
    static var cloudSyncTitle: String { text("Google Drive ile eşitleme", en: "Google Drive Sync", de: "Google Drive-Synchronisierung") }
    static var backupTitle: String { text("Yedekleme ve Deste Paylaşımı", en: "Backup & Deck Sharing", de: "Sicherung & Stapel teilen") }
    static var dataManagementTitle: String { text("Veri Yönetimi", en: "Data Management", de: "Datenverwaltung") }
    static var statsTitle: String { text("Çalışma İstatistikleri", en: "Study Statistics", de: "Lernstatistiken") }

    // MARK: - AI Generator
    static func aiSheetTitle(_ l: AppLanguage = LanguageManager.current) -> String {
        text("Mistral AI ile Seviyeye Göre Kelime Üret", en: "Generate Words by Level with Mistral AI", de: "Wörter nach Niveau mit Mistral AI generieren", lang: l)
    }
    static var aiTargetMeaningLanguagePrompt: String {
        switch LanguageManager.current {
        case .tr:
            return "Türkçe"
        case .en:
            return "İngilizce (English)"
        case .de:
            return "Almanca kısa tanım/eşanlamlı (Deutsch)"
        }
    }
}
