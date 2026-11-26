import Accessibility
import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct StatsView: View {
    @EnvironmentObject var store: DeckStore
    @FocusedValue(\.appTabSelection) private var tabSelection
    @State private var cardCounts = StatsCardCounts()

    private var activeStudyDaysCount: Int {
        store.studyHistory.values.lazy.filter { $0.totalAnswersCount > 0 }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text(L10n.statsTitle)
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)

                let activeDays = activeStudyDaysCount
                let hasStudyHistory = activeDays > 0 || store.dailyProgress.totalAnswersCount > 0
                if store.cards.isEmpty {
                    emptyStatistics(
                        L10n.text("Henüz kelime yok", en: "No words yet", de: "Noch keine Wörter"),
                        systemImage: "folder",
                        description: L10n.text("Başlamak için Kelime Havuzu sekmesinden kelime ekleyin.", en: "Add words from the Word Pool tab to get started.", de: "Füge Wörter im Tab ‚Wortschatz‘ hinzu, um zu starten."),
                        destination: .words
                    )
                }

                if !store.cards.isEmpty || hasStudyHistory {
                    todayActivity
                    streakAndHeatmap(activeDays: activeDays, hasStudyHistory: hasStudyHistory)
                }

                if !store.cards.isEmpty {
                    stageSummary
                    distributions(totalCards: store.cards.count, deckNames: cardCounts.deckNames(including: store.settings.customDecks))
                }
            }
            .padding(24)
        }
        .onReceive(store.$cards) { cards in
            cardCounts = cards.reduce(into: StatsCardCounts()) { counts, card in
                switch card.stage {
                case .new:
                    counts.newCount += 1
                case .learning, .relearning:
                    counts.learningCount += 1
                case .review where card.intervalDays < 21:
                    counts.reviewCount += 1
                case .review where card.intervalDays >= 21:
                    counts.matureCount += 1
                case .review:
                    break
                }

                if let artikel = card.artikel {
                    counts.articles[artikel, default: 0] += 1
                } else {
                    counts.articlelessCount += 1
                }
                counts.decks[card.deckName, default: 0] += 1
            }
        }
    }

    private func emptyStatistics(
        _ title: String,
        systemImage: String,
        description: String,
        destination: AppTab
    ) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: systemImage)
        } description: {
            Text(description)
        } actions: {
            Button(destination.localizedTitle) {
                tabSelection?.wrappedValue = destination
            }
            .buttonStyle(.bordered)
            .disabled(tabSelection == nil)
        }
    }

    private var todayActivity: some View {
        GroupBox(label: Label(L10n.text("Bugünkü çalışma (\(store.dailyProgress.dateString))", en: "Today's study (\(store.dailyProgress.dateString))", de: "Heutiges Lernen (\(store.dailyProgress.dateString))"), systemImage: "calendar")) {
            HStack(spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    (
                        Text(L10n.text("Yeni Öğrenilen: ", en: "New Learned: ", de: "Neu gelernt: "))
                        + Text("\(store.dailyProgress.newCardsStudied)").bold()
                        + Text(" / \(store.settings.dailyNewCardLimit)")
                    )
                    (
                        Text(L10n.text("Tamamlanan Tekrar: ", en: "Completed Reviews: ", de: "Abgeschlossene Wdh.: "))
                        + Text("\(store.dailyProgress.reviewsCompleted)").bold()
                        + Text(" / \(store.settings.dailyReviewLimit)")
                    )
                }
                Divider().frame(height: 44)
                HStack(spacing: 18) {
                    FeedbackBadge(rating: .again, count: store.dailyProgress.againCount)
                    FeedbackBadge(rating: .hard, count: store.dailyProgress.hardCount)
                    FeedbackBadge(rating: .good, count: store.dailyProgress.goodCount)
                    FeedbackBadge(rating: .easy, count: store.dailyProgress.easyCount)
                }
                Spacer()
            }
            .padding(12)
        }
    }

    private func streakAndHeatmap(activeDays: Int, hasStudyHistory: Bool) -> some View {
        let currentStreakDays = store.currentStreakDays
        return GroupBox(label: Label(L10n.text("Günlük Seri (Streak) ve 35 Günlük Çalışma Isı Haritası", en: "Daily Streak & 35-Day Study Heatmap", de: "Tages-Serie & 35-Tage-Lern-Heatmap"), systemImage: "flame.fill")) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 24) {
                    HStack(spacing: 12) {
                        Image(systemName: "flame.fill")
                            .accessibilityHidden(true)
                            .font(.system(size: 30))
                            .foregroundColor(currentStreakDays > 0 ? AppPalette.accentOrange : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.text("\(currentStreakDays) Gün", en: "\(currentStreakDays) Days", de: currentStreakDays == 1 ? "1 Tag" : "\(currentStreakDays) Tage"))
                                .font(.title2.bold())
                            Text(L10n.text("Kesintisiz Günlük Seri", en: "Unbroken Daily Streak", de: "Laufende Tages-Serie"))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Divider().frame(height: 40)

                    HStack(spacing: 12) {
                        Image(systemName: "trophy.fill")
                            .accessibilityHidden(true)
                            .font(.system(size: 26))
                            .foregroundColor(AppPalette.das)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.text("\(store.longestStreakDays) Gün", en: "\(store.longestStreakDays) Days", de: store.longestStreakDays == 1 ? "1 Tag" : "\(store.longestStreakDays) Tage"))
                                .font(.title3.bold())
                            Text(L10n.text("En Uzun Seri Rekoru", en: "Longest Streak Record", de: "Längste Serie"))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Divider().frame(height: 40)

                    HStack(spacing: 12) {
                        Image(systemName: "calendar.badge.checkmark")
                            .accessibilityHidden(true)
                            .font(.system(size: 26))
                            .foregroundColor(AppPalette.success)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.text("\(activeDays) Gün", en: "\(activeDays) Days", de: activeDays == 1 ? "1 Tag" : "\(activeDays) Tage"))
                                .font(.title3.bold())
                            Text(L10n.text("Toplam Aktif Çalışılan Gün", en: "Total Active Study Days", de: "Aktive Lerntage gesamt"))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()
                }

                Divider()

                if hasStudyHistory {
                    activityHeatmap
                } else {
                    emptyStatistics(
                        L10n.text("Henüz çalışma kaydı yok", en: "No study records yet", de: "Noch keine Lernaufzeichnungen"),
                        systemImage: "calendar",
                        description: L10n.text("İlk yanıtınızı vermek için Çalışma sekmesine geçin.", en: "Switch to the Study tab to give your first answer.", de: "Wechsle zum Tab ‚Lernen‘, um deine erste Antwort zu geben."),
                        destination: .study
                    )
                }
            }
            .padding(12)
        }
    }

    private var activityHeatmap: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.text("Son 35 Günlük Aktivite Yoğunluğu", en: "Last 35 Days Activity Intensity", de: "Aktivitätsintensität der letzten 35 Tage"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .accessibilityAddTraits(.isHeader)

            heatmapLegend

            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 7),
                spacing: 8
            ) {
                ForEach(store.heatmapDays(daysCount: 35), id: \.dateString) { item in
                    HeatmapCell(
                        dateString: item.dateString,
                        displayDate: item.displayDate,
                        count: item.count
                    )
                }
            }
        }
    }

    private var heatmapLegend: some View {
        HStack(spacing: 8) {
            legendSwatch(0, label: L10n.text("0 yanıt", en: "0 answers", de: "0 Antworten"))
            legendSwatch(1, label: L10n.text("1–4 yanıt", en: "1–4 answers", de: "1–4 Antworten"))
            legendSwatch(5, label: L10n.text("5–14 yanıt", en: "5–14 answers", de: "5–14 Antworten"))
            legendSwatch(15, label: L10n.text("15–34 yanıt", en: "15–34 answers", de: "15–34 Antworten"))
            legendSwatch(35, label: L10n.text("35+ yanıt", en: "35+ answers", de: "35+ Antworten"))
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.text("Yanıt sayısı ölçeği", en: "Answer count scale", de: "Antwortanzahl-Skala"))
    }

    private func legendSwatch(_ sample: Int, label: String) -> some View {
        let colors = AppPalette.heatmapColors(sample)
        return HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3)
                .fill(colors.background)
                .overlay {
                    RoundedRectangle(cornerRadius: 3)
                        .strokeBorder(AppPalette.controlOutline, lineWidth: 1)
                }
                .frame(width: 12, height: 12)
                .accessibilityHidden(true)
            Text(label)
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }

    private var stageSummary: some View {
        // Summary Cards
        LazyVGrid(columns: [
            GridItem(.flexible()),
            GridItem(.flexible()),
            GridItem(.flexible()),
            GridItem(.flexible())
        ], spacing: 16) {
            StatCard(title: L10n.text("Yeni (Çalışılmamış)", en: "New (Unstudied)", de: "Neu (Ungelernt)"), value: "\(cardCounts.newCount)", subtitle: L10n.text("Sırada bekleyen", en: "Waiting in queue", de: "In Warteschlange"), color: CardStage.new.color)
            StatCard(title: L10n.text("Öğreniliyor", en: "Learning", de: "Lernen"), value: "\(cardCounts.learningCount)", subtitle: L10n.text("Kısa aralıklı", en: "Short interval", de: "Kurzes Intervall"), color: CardStage.learning.color)
            StatCard(title: L10n.text("Genç Kartlar", en: "Young Cards", de: "Junge Karten"), value: "\(cardCounts.reviewCount)", subtitle: L10n.text("Aralık < 21 gün", en: "Interval < 21 days", de: "Intervall < 21 Tage"), color: CardStage.review.color)
            StatCard(title: L10n.text("Olgun kartlar", en: "Mature Cards", de: "Reife Karten"), value: "\(cardCounts.matureCount)", subtitle: L10n.text("Aralık ≥ 21 gün", en: "Interval ≥ 21 days", de: "Intervall ≥ 21 Tage"), color: AppPalette.accentPurple)
        }
    }

    private func distributions(totalCards: Int, deckNames: [String]) -> some View {
        VStack(alignment: .leading, spacing: 24) {
            // Artikel Distribution
            GroupBox(label: Label(L10n.text("Artikel Dağılımı (Toplam \(totalCards) Kelime)", en: "Article Distribution (\(totalCards) Words Total)", de: "Artikelverteilung (\(totalCards) Wörter gesamt)"), systemImage: "chart.bar.fill")) {
                VStack(spacing: 12) {
                    DistributionRow(label: L10n.text("der (Eril)", en: "der (Masculine)", de: "der (Maskulin)"), count: cardCounts.articles[.der, default: 0], total: totalCards, color: Artikel.der.color)
                    DistributionRow(label: L10n.text("die (Dişil)", en: "die (Feminine)", de: "die (Feminin)"), count: cardCounts.articles[.die, default: 0], total: totalCards, color: Artikel.die.color)
                    DistributionRow(label: L10n.text("das (Nötr)", en: "das (Neuter)", de: "das (Neutrum)"), count: cardCounts.articles[.das, default: 0], total: totalCards, color: Artikel.das.color)
                    if cardCounts.articlelessCount > 0 {
                        DistributionRow(label: L10n.articleless, count: cardCounts.articlelessCount, total: totalCards, color: AppPalette.articleNone)
                    }
                }
                .padding(12)
            }

            // Deck Distribution (#5)
            GroupBox(label: Label(L10n.text("Deste / Kategori Dağılımı (\(deckNames.count) Deste)", en: "Deck / Category Distribution (\(deckNames.count) Decks)", de: "Stapel- / Kategorienverteilung (\(deckNames.count) Stapel)"), systemImage: "folder.fill")) {
                VStack(spacing: 10) {
                    ForEach(deckNames, id: \.self) { deck in
                        let count = cardCounts.decks[deck, default: 0]
                        DistributionRow(
                            label: deck,
                            count: count,
                            total: totalCards,
                            color: AppPalette.accentPurple
                        )
                    }
                }
                .padding(12)
            }
        }
    }
}

private struct StatsCardCounts {
    var newCount = 0
    var learningCount = 0
    var reviewCount = 0
    var matureCount = 0
    var articlelessCount = 0
    var articles: [Artikel: Int] = [:]
    var decks: [String: Int] = [:]

    func deckNames(including customDecks: [String]) -> [String] {
        let names = Set((Array(decks.keys) + customDecks).map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
        })
        return ["Genel"] + names.filter { !$0.isEmpty && $0 != "Genel" }.sorted {
            $0.localizedCaseInsensitiveCompare($1) == .orderedAscending
        }
    }
}

private struct HeatmapCell: View {
    let dateString: String
    let displayDate: String
    let count: Int

    private static let dateParser: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let spokenDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        return formatter
    }()

    var body: some View {
        let colors = AppPalette.heatmapColors(count)
        let formatter = Self.spokenDateFormatter
        let _ = (formatter.locale = LanguageManager.shared.activeLocale)
        let fullDate = Self.dateParser.date(from: dateString).map {
            formatter.string(from: $0)
        } ?? dateString
        let answerUnit = L10n.text("\(count) yanıt", en: "\(count) answers", de: "\(count) Antworten")
        let summary = "\(fullDate): \(answerUnit)"

        VStack(alignment: .leading, spacing: 2) {
            Text(displayDate)
                .font(.callout.weight(.medium))
            Text(answerUnit)
                .font(.callout.bold())
        }
        .monospacedDigit()
        .foregroundStyle(colors.foreground)
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(colors.background, in: RoundedRectangle(cornerRadius: 7))
        .overlay {
            RoundedRectangle(cornerRadius: 7)
                .strokeBorder(AppPalette.controlOutline, lineWidth: 1)
                .accessibilityHidden(true)
        }
        .help(summary)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(summary))
    }
}

struct SettingsView: View {
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    @EnvironmentObject var languageManager: LanguageManager
    @State private var showResetConfirmation = false
    @State private var showDeleteAllConfirmation = false
    @State private var showDeleteAccountConfirmation = false
    @State private var isDeletingAccount = false
    @State private var mergeOnBackupImport = true
    @State private var pendingReplacementURL: URL?
    @State private var backupStatusMessage: String?
    @State private var backupStatusDetail: String?
    @State private var backupStatusIsError: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(AppPalette.accentBlue.opacity(0.14))
                            .frame(width: 36, height: 36)
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(AppPalette.accentBlue)
                    }
                    .accessibilityHidden(true)

                    Text(L10n.tabSettings())
                        .font(.title2.bold())
                        .accessibilityAddTraits(.isHeader)
                }

                appearanceSettings
                dailyLimits
                cardPreferences
                cloudSettings
                backupSettings
                dataManagement
            }
            .padding(24)
            .frame(maxWidth: 820, alignment: .leading)
        }
        .onChange(of: cloudStatusMessage) { _, message in
            if let message {
                AccessibilityNotification.Announcement(message).post()
            }
        }
        .confirmationDialog(
            L10n.text("Tüm kelimelerin öğrenme geçmişini sıfırlamak istediğinize emin misiniz?", en: "Are you sure you want to reset the learning history of all words?", de: "Möchtest du den Lernverlauf aller Wörter wirklich zurücksetzen?"),
            isPresented: $showResetConfirmation,
            titleVisibility: .visible
        ) {
            Button(L10n.text("İlerlemeyi Sıfırla (Kelimeler Kalır)", en: "Reset Progress (Keep Words)", de: "Fortschritt zurücksetzen (Wörter bleiben)"), role: .destructive) {
                store.resetAllProgress()
            }
            Button(L10n.cancel, role: .cancel) {}
        }
        .confirmationDialog(
            L10n.text("Yerel depolamadaki tüm kelimeleri silmek istediğinize emin misiniz?", en: "Are you sure you want to delete all words from local storage?", de: "Möchtest du wirklich alle Wörter aus dem lokalen Speicher löschen?"),
            isPresented: $showDeleteAllConfirmation,
            titleVisibility: .visible
        ) {
            Button(L10n.text("Tüm Kelimeleri Sil", en: "Delete All Words", de: "Alle Wörter löschen"), role: .destructive) {
                store.deleteAllCards()
            }
            Button(L10n.cancel, role: .cancel) {}
        }
        .confirmationDialog(
            L10n.text("Hesap Verilerini Silmek İstediğinize Emin Misiniz?", en: "Are You Sure You Want to Delete Account Data?", de: "Kontodaten wirklich löschen?"),
            isPresented: $showDeleteAccountConfirmation,
            titleVisibility: .visible
        ) {
            Button(L10n.text("Evet, Tüm Hesap Verilerini Sil", en: "Yes, Delete All Account Data", de: "Ja, alle Kontodaten löschen"), role: .destructive) {
                isDeletingAccount = true
                Task {
                    await store.deleteAccountAndAllData(authManager: authManager)
                    isDeletingAccount = false
                }
            }
            Button(L10n.cancel, role: .cancel) {}
        } message: {
            Text(L10n.text(
                "Bu işlem geri alınamaz:\n• Google Drive'daki \"ArtikelMeister Cloud Sync\" klasörünüz ve bulut yedeğiniz kalıcı olarak silinir.\n• Bu cihazdaki tüm kelimeleriniz, desteleriniz, günlük seriniz (streak) ve tercih ayarlarınız sıfırlanır.\n• Google hesabı bağlantınız sonlandırılır.",
                en: "This action cannot be undone:\n• Your \"ArtikelMeister Cloud Sync\" folder and cloud backup on Google Drive will be permanently deleted.\n• All words, decks, daily streak, and preferences on this device will be reset.\n• Your Google account connection will be disconnected.",
                de: "Dieser Vorgang kann nicht rückgängig gemacht werden:\n• Dein Ordner „ArtikelMeister Cloud Sync“ und dein Cloud-Backup auf Google Drive werden dauerhaft gelöscht.\n• Alle Wörter, Stapel, deine Tages-Serie und Einstellungen auf diesem Gerät werden zurückgesetzt.\n• Deine Google-Kontoverbindung wird getrennt."
            ))
        }
    }

    private var appearanceSettings: some View {
        GlassSettingsCard(
            title: L10n.appearanceTitle,
            systemImage: "circle.lefthalf.filled",
            tint: AppPalette.accentPurple
        ) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L10n.text("Tema", en: "Theme", de: "Design"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 10) {
                        ForEach(ThemeMode.allCases) { mode in
                            let isSelected = store.themeMode == mode
                            Button {
                                withAnimation(.easeInOut(duration: 0.25)) {
                                    store.themeMode = mode
                                }
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: themeIcon(for: mode))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(isSelected ? AppPalette.accentPurple : .secondary)
                                    Text(mode.title)
                                        .font(.subheadline.weight(isSelected ? .semibold : .medium))
                                        .foregroundStyle(.primary)
                                        .lineLimit(1)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .padding(.horizontal, 12)
                                .background {
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .fill(.ultraThinMaterial)
                                        .overlay {
                                            if isSelected {
                                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                                    .fill(AppPalette.accentPurple.opacity(0.14))
                                            }
                                        }
                                }
                                .overlay {
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .strokeBorder(
                                            isSelected ? AppPalette.accentPurple.opacity(0.65) : Color.primary.opacity(0.08),
                                            lineWidth: isSelected ? 1.5 : 0.75
                                        )
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(isSelected ? .isSelected : [])
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel(L10n.appearanceTitle)
                }

                Divider().opacity(0.5)

                VStack(alignment: .leading, spacing: 8) {
                    Label(L10n.settingsLanguage(), systemImage: "globe")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 10) {
                        ForEach(AppLanguage.allCases) { lang in
                            let isSelected = languageManager.language == lang
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    languageManager.language = lang
                                }
                            } label: {
                                HStack(spacing: 10) {
                                    LanguageFlagView(language: lang)
                                    Text(lang.displayName)
                                        .font(.subheadline.weight(isSelected ? .semibold : .medium))
                                        .foregroundStyle(.primary)
                                        .lineLimit(1)
                                    Spacer(minLength: 0)
                                    if isSelected {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.subheadline)
                                            .foregroundStyle(AppPalette.accentBlue)
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 9)
                                .frame(maxWidth: .infinity)
                                .background {
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .fill(.ultraThinMaterial)
                                        .overlay {
                                            if isSelected {
                                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                                    .fill(AppPalette.accentBlue.opacity(0.13))
                                            }
                                        }
                                }
                                .overlay {
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .strokeBorder(
                                            isSelected ? AppPalette.accentBlue.opacity(0.65) : Color.primary.opacity(0.08),
                                            lineWidth: isSelected ? 1.5 : 0.75
                                        )
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(isSelected ? .isSelected : [])
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel(L10n.settingsLanguage())
                }
            }
        }
    }

    private func themeIcon(for mode: ThemeMode) -> String {
        switch mode {
        case .system: return "laptopcomputer"
        case .light: return "sun.max.fill"
        case .dark: return "moon.stars.fill"
        }
    }

    private var dailyLimits: some View {
        GlassSettingsCard(
            title: L10n.dailyLimitsTitle,
            systemImage: "slider.horizontal.3",
            tint: AppPalette.accentBlue
        ) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(L10n.dailyNewLimitTitle)
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        Stepper(
                            value: Binding(
                                get: { store.settings.dailyNewCardLimit },
                                set: {
                                    store.settings.dailyNewCardLimit = max(1, min(500, $0))
                                    store.saveSettings()
                                }
                            ),
                            in: 1...500,
                            step: 5
                        ) {
                            Text("\(store.settings.dailyNewCardLimit) / \(L10n.text("gün", en: "day", de: "Tag"))")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundStyle(AppPalette.accentBlue)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(AppPalette.blueBadgeBackground, in: Capsule())
                                .fixedSize()
                        }
                        .accessibilityLabel(L10n.dailyNewLimitTitle)
                        .accessibilityValue("\(store.settings.dailyNewCardLimit)")
                    }

                    HStack(spacing: 10) {
                        Text("1")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 20, alignment: .leading)
                        Slider(
                            value: Binding(
                                get: { Double(store.settings.dailyNewCardLimit) },
                                set: {
                                    store.settings.dailyNewCardLimit = max(1, min(500, Int($0.rounded())))
                                    store.saveSettings()
                                }
                            ),
                            in: 1...500
                        )
                        .tint(AppPalette.accentBlue)
                        .accessibilityLabel(L10n.dailyNewLimitTitle)
                        .accessibilityValue("\(store.settings.dailyNewCardLimit)")
                        Text("500")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 28, alignment: .trailing)
                    }
                }
                .padding(12)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.75)
                )

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(L10n.dailyReviewLimitTitle)
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        Stepper(
                            value: Binding(
                                get: { store.settings.dailyReviewLimit },
                                set: {
                                    store.settings.dailyReviewLimit = max(10, min(1000, $0))
                                    store.saveSettings()
                                }
                            ),
                            in: 10...1000,
                            step: 10
                        ) {
                            Text("\(store.settings.dailyReviewLimit) / \(L10n.text("gün", en: "day", de: "Tag"))")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundStyle(AppPalette.accentBlue)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(AppPalette.blueBadgeBackground, in: Capsule())
                                .fixedSize()
                        }
                        .accessibilityLabel(L10n.dailyReviewLimitTitle)
                        .accessibilityValue("\(store.settings.dailyReviewLimit)")
                    }

                    HStack(spacing: 10) {
                        Text("10")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 20, alignment: .leading)
                        Slider(
                            value: Binding(
                                get: { Double(store.settings.dailyReviewLimit) },
                                set: {
                                    let snapped = Int(($0 / 10.0).rounded()) * 10
                                    store.settings.dailyReviewLimit = max(10, min(1000, snapped))
                                    store.saveSettings()
                                }
                            ),
                            in: 10...1000
                        )
                        .tint(AppPalette.accentBlue)
                        .accessibilityLabel(L10n.dailyReviewLimitTitle)
                        .accessibilityValue("\(store.settings.dailyReviewLimit)")
                        Text("1000")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 32, alignment: .trailing)
                    }
                }
                .padding(12)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.75)
                )
            }
        }
    }

    private var cardPreferences: some View {
        GlassSettingsCard(
            title: L10n.cardPrefsTitle,
            systemImage: "rectangle.on.rectangle",
            tint: AppPalette.accentOrange
        ) {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(
                    L10n.autoPlayPref,
                    isOn: Binding(
                        get: { store.settings.autoPlayPronunciation },
                        set: {
                            store.settings.autoPlayPronunciation = $0
                            store.saveSettings()
                        }
                    )
                )

                Divider().opacity(0.45)

                Toggle(
                    L10n.showMeaningPref,
                    isOn: Binding(
                        get: { store.settings.showMeaningOnFront },
                        set: {
                            store.settings.showMeaningOnFront = $0
                            store.saveSettings()
                        }
                    )
                )

                Divider().opacity(0.45)

                Toggle(
                    L10n.randomOrderPref,
                    isOn: Binding(
                        get: { store.settings.newCardOrderRandom },
                        set: {
                            store.settings.newCardOrderRandom = $0
                            store.saveSettings()
                        }
                    )
                )
            }
            .toggleStyle(.switch)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var cloudSettings: some View {
        GlassSettingsCard(
            title: L10n.cloudSyncTitle,
            systemImage: "icloud.fill",
            tint: AppPalette.success
        ) {
            VStack(alignment: .leading, spacing: 14) {
                if authManager.isGoogleAccountConnected {
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.icloud.fill")
                            .accessibilityHidden(true)
                            .font(.system(size: 20))
                            .foregroundStyle(AppPalette.success)
                            .frame(width: 40, height: 40)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(!authManager.userName.isEmpty ? authManager.userName : L10n.text("Google Hesabı Bağlı", en: "Google Account Connected", de: "Google-Konto verbunden"))
                                .font(.subheadline.bold())
                            Text(!authManager.userEmail.isEmpty ? authManager.userEmail : L10n.text("Google Profil Aktif", en: "Google Profile Active", de: "Google-Profil aktiv"))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        Spacer()
                    }
                    .padding(12)
                    .background(AppPalette.successBadgeBackground)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(AppPalette.success.opacity(0.35), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    HStack(spacing: 10) {
                        Button {
                            Task {
                                await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                            }
                        } label: {
                            HStack(spacing: 6) {
                                if store.isSyncingCloud {
                                    ProgressView()
                                        .scaleEffect(0.7)
                                        .tint(AppPalette.onBlue)
                                        .frame(width: 14, height: 14)
                                        .accessibilityHidden(true)
                                } else {
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .accessibilityHidden(true)
                                }
                                Text(store.isSyncingCloud
                                     ? L10n.text("Eşitleniyor...", en: "Syncing...", de: "Synchronisiere...")
                                     : L10n.text("Şimdi Bulutla Eşitle", en: "Sync with Cloud Now", de: "Jetzt mit Cloud synchronisieren"))
                                    .fontWeight(.semibold)
                            }
                            .foregroundStyle(AppPalette.onBlue)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppPalette.accentBlue)
                        .disabled(store.isSyncingCloud || isDeletingAccount)

                        Button {
                            authManager.clearAuthorization()
                            store.cloudSyncStatusMessage = L10n.text("Google hesabından çıkış yapıldı (yerel veriler korundu).", en: "Signed out of Google account (local data preserved).", de: "Vom Google-Konto abgemeldet (lokale Daten erhalten).")
                            store.cloudSyncIsError = false
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                    .accessibilityHidden(true)
                                Text(L10n.text("Hesaptan Çıkış Yap", en: "Sign Out", de: "Abmelden"))
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                } else {
                    Text(L10n.text("Kelimelerinizi ve çalışma geçmişinizi Google Drive ile eşitleyin.", en: "Sync your words and study history with Google Drive.", de: "Synchronisiere deine Wörter und deinen Lernverlauf mit Google Drive."))
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Button {
                        Task {
                            do {
                                try await authManager.startBrowserOAuthFlow()
                                await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                            } catch {
                                store.cloudSyncIsError = true
                                store.cloudSyncStatusMessage = L10n.text("Bulut bağlantı hatası: \(error.localizedDescription)", en: "Cloud connection error: \(error.localizedDescription)", de: "Cloud-Verbindungsfehler: \(error.localizedDescription)")
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if authManager.isAuthenticating || store.isSyncingCloud {
                                ProgressView()
                                    .scaleEffect(0.7)
                                    .tint(AppPalette.onBlue)
                                    .frame(width: 14, height: 14)
                                    .accessibilityHidden(true)
                            } else {
                                Image(systemName: "person.crop.circle.badge.plus")
                                    .accessibilityHidden(true)
                            }
                            Text(signInButtonTitle)
                                .fontWeight(.semibold)
                        }
                        .foregroundStyle(AppPalette.onBlue)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppPalette.accentBlue)
                    .disabled(authManager.isAuthenticating || store.isSyncingCloud)
                }

                if let message = cloudStatusMessage {
                    let inProgress = authManager.isAuthenticating || store.isSyncingCloud || isDeletingAccount
                    let isError = !inProgress && store.cloudSyncIsError
                    SettingsStatusRow(
                        message: message,
                        detail: isError ? store.cloudSyncStatusMessage : nil,
                        isError: isError,
                        inProgress: inProgress
                    )
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var signInButtonTitle: String {
        if authManager.isAuthenticating {
            return L10n.text("Google hesabına bağlanılıyor...", en: "Connecting to Google account...", de: "Verbindung zum Google-Konto wird hergestellt...")
        }
        if store.isSyncingCloud {
            return L10n.text("Eşitleniyor...", en: "Syncing...", de: "Synchronisiere...")
        }
        return L10n.text("Google Hesabı ile Giriş Yap & Eşitle", en: "Sign in with Google & Sync", de: "Mit Google anmelden & synchronisieren")
    }

    private var cloudStatusMessage: String? {
        if isDeletingAccount {
            return L10n.text("Hesap verileri siliniyor...", en: "Deleting account data...", de: "Kontodaten werden gelöscht...")
        }
        if authManager.isAuthenticating {
            return L10n.text("Google hesabına bağlanılıyor...", en: "Connecting to Google account...", de: "Verbindung zum Google-Konto wird hergestellt...")
        }
        if store.isSyncingCloud {
            return L10n.text("Google Drive ile eşitleniyor...", en: "Syncing with Google Drive...", de: "Synchronisiere mit Google Drive...")
        }
        guard let message = store.cloudSyncStatusMessage else { return nil }
        if store.cloudSyncIsError {
            if message.hasPrefix("Bulut bağlantı hatası:") || message.hasPrefix("Cloud connection error:") || message.hasPrefix("Cloud-Verbindungsfehler:") {
                return L10n.text("Google hesabına bağlanılamadı. Tekrar giriş yapmayı deneyin.", en: "Could not connect to Google account. Try signing in again.", de: "Verbindung zum Google-Konto fehlgeschlagen. Bitte erneut anmelden.")
            }
            if message.hasPrefix("Bulut eşitleme hatası:") || message.hasPrefix("Cloud sync error:") || message.hasPrefix("Cloud-Sync-Fehler:") {
                return L10n.text("Google Drive ile eşitlenemedi. Bağlantınızı kontrol edip tekrar deneyin.", en: "Could not sync with Google Drive. Check your connection and try again.", de: "Synchronisierung mit Google Drive fehlgeschlagen. Prüfe deine Verbindung.")
            }
            if message.hasPrefix("Yerel kelime dosyası okunamadı") || message.hasPrefix("Local word file could not be read") || message.hasPrefix("Lokale Wortdatei konnte nicht gelesen werden") {
                return L10n.text("Yerel kelime dosyası okunamadı. Dosyanızı kontrol edip tekrar deneyin.", en: "Local word file could not be read. Check your file and try again.", de: "Lokale Wortdatei konnte nicht gelesen werden. Prüfe deine Datei.")
            }
            return L10n.text("İşlem tamamlanamadı. Tekrar deneyin.", en: "Operation could not be completed. Please try again.", de: "Vorgang konnte nicht abgeschlossen werden. Bitte erneut versuchen.")
        }
        return message
    }

    private var backupSettings: some View {
        GlassSettingsCard(
            title: L10n.backupTitle,
            systemImage: "archivebox.fill",
            tint: AppPalette.accentBlue
        ) {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.text("Yedekler ve deste paketleri .json biçimindedir. Deste paketi kişisel ilerlemeyi içermez.", en: "Backups and deck packages are in .json format. Deck packages do not include personal progress.", de: "Backups und Stapelpakete liegen im .json-Format vor. Stapelpakete enthalten keinen persönlichen Fortschritt."))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 10) {
                        backupActions
                    }
                    .fixedSize(horizontal: true, vertical: false)

                    VStack(alignment: .leading, spacing: 8) {
                        backupActions
                    }
                }

                Toggle(L10n.text("Birleştir", en: "Merge", de: "Zusammenführen"), isOn: $mergeOnBackupImport)
                    .toggleStyle(.switch)
                    .accessibilityHint(L10n.text("Açıkken içe aktarılan kelimeler mevcut kelimelerle birleştirilir.", en: "When enabled, imported words are merged with existing words.", de: "Wenn aktiviert, werden importierte Wörter mit vorhandenen zusammengeführt."))
                Text(L10n.text("Birleştirme kapalıysa içe aktarma, onayınızdan sonra mevcut verileri değiştirir.", en: "When merge is off, importing replaces existing data after your confirmation.", de: "Wenn deaktiviert, ersetzt der Import vorhandene Daten nach deiner Bestätigung."))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let message = backupStatusMessage {
                    SettingsStatusRow(message: message, detail: backupStatusDetail, isError: backupStatusIsError, inProgress: false)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .alert(
            L10n.text("Mevcut veriler değiştirilsin mi?", en: "Replace existing data?", de: "Vorhandene Daten ersetzen?"),
            isPresented: Binding(
                get: { pendingReplacementURL != nil },
                set: { if !$0 { pendingReplacementURL = nil } }
            ),
            presenting: pendingReplacementURL
        ) { url in
            Button(L10n.text("Mevcut verileri değiştir", en: "Replace existing data", de: "Vorhandene Daten ersetzen"), role: .destructive) {
                importBackupPackage(from: url, mergeWithExisting: false)
            }
            Button(L10n.cancel, role: .cancel) {}
        } message: { url in
            Text(L10n.text("“\(url.lastPathComponent)” içe aktarıldığında mevcut verileriniz değiştirilecek.", en: "Importing “\(url.lastPathComponent)” will replace your existing data.", de: "Beim Importieren von „\(url.lastPathComponent)“ werden deine vorhandenen Daten ersetzt."))
        }
    }

    @ViewBuilder
    private var backupActions: some View {
        Button {
            exportBackupPackage(includeProgress: true)
        } label: {
            Label(L10n.text("Tam yedeği dışa aktar", en: "Export Full Backup", de: "Vollständiges Backup exportieren"), systemImage: "square.and.arrow.up")
                .foregroundStyle(AppPalette.onBlue)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppPalette.accentBlue)
        .disabled(store.cards.isEmpty)

        Button {
            exportBackupPackage(includeProgress: false)
        } label: {
            Label(L10n.text("Deste paketini dışa aktar", en: "Export Deck Package", de: "Stapelpaket exportieren"), systemImage: "square.and.arrow.up")
        }
        .buttonStyle(.bordered)
        .disabled(store.cards.isEmpty)

        Button {
            importBackupPackage()
        } label: {
            Label(L10n.text("Yedeği veya paketi içe aktar", en: "Import Backup or Package", de: "Backup oder Paket importieren"), systemImage: "square.and.arrow.down")
        }
        .buttonStyle(.bordered)
    }

    private var dataManagement: some View {
        GlassSettingsCard(
            title: L10n.dataManagementTitle,
            systemImage: "internaldrive.fill",
            tint: AppPalette.error
        ) {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.text("Yerel depolama klasörü:", en: "Local storage folder:", de: "Lokaler Speicherordner:"))
                    .font(.caption.weight(.medium))
                    .foregroundColor(.secondary)

                HStack {
                    Text(store.storageDirectory.path)
                        .font(.system(.caption, design: .monospaced))
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                        )
                        .textSelection(.enabled)

                    Button {
                        store.openStorageDirectoryInFinder()
                    } label: {
                        Label(L10n.text("Finder'da Aç", en: "Open in Finder", de: "Im Finder öffnen"), systemImage: "folder")
                            .accessibilityLabel(L10n.showStorageInFinder)
                    }
                    .buttonStyle(.bordered)
                }

                Divider().opacity(0.45).padding(.vertical, 2)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 10) {
                        destructiveDataActions
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        destructiveDataActions
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var destructiveDataActions: some View {
        Button(role: .destructive) {
            showResetConfirmation = true
        } label: {
            Label(L10n.text("Öğrenme İlerlemesini Sıfırla", en: "Reset Study Progress", de: "Lernfortschritt zurücksetzen"), systemImage: "arrow.counterclockwise")
        }
        .buttonStyle(.bordered)
        .disabled(store.cards.isEmpty)

        Button(role: .destructive) {
            showDeleteAllConfirmation = true
        } label: {
            Label(L10n.text("Tüm Kelimeleri Sil", en: "Delete All Words", de: "Alle Wörter löschen"), systemImage: "trash")
        }
        .buttonStyle(.bordered)
        .disabled(store.cards.isEmpty)

        Button(role: .destructive) {
            showDeleteAccountConfirmation = true
        } label: {
            HStack(spacing: 4) {
                if isDeletingAccount {
                    ProgressView()
                        .scaleEffect(0.6)
                        .frame(width: 12, height: 12)
                        .accessibilityHidden(true)
                } else {
                    Image(systemName: "person.crop.circle.badge.xmark")
                        .accessibilityHidden(true)
                }
                Text(isDeletingAccount
                     ? L10n.text("Hesap verileri siliniyor...", en: "Deleting account data...", de: "Kontodaten werden gelöscht...")
                     : L10n.text("Hesap Verilerini Sil", en: "Delete Account Data", de: "Kontodaten löschen"))
            }
        }
        .buttonStyle(.bordered)
        .disabled(isDeletingAccount || (store.cards.isEmpty && !authManager.isGoogleAccountConnected))
    }

    private func exportBackupPackage(includeProgress: Bool) {
        let data: Data
        do {
            data = try store.exportBackupJSONData(includeProgress: includeProgress)
        } catch {
            showBackupStatus(L10n.text("Yedek oluşturulamadı. Tekrar deneyin.", en: "Could not create backup. Please try again.", de: "Backup konnte nicht erstellt werden. Bitte erneut versuchen."), isError: true)
            return
        }

        let todayStr = DailyProgress.todayString()
        let savePanel = NSSavePanel()
        savePanel.title = includeProgress
            ? L10n.text("Tam yedeği dışa aktar", en: "Export Full Backup", de: "Vollständiges Backup exportieren")
            : L10n.text("Deste paketini dışa aktar", en: "Export Deck Package", de: "Stapelpaket exportieren")
        savePanel.prompt = L10n.text("Dışa aktar", en: "Export", de: "Exportieren")
        savePanel.nameFieldStringValue = includeProgress
            ? "ArtikelMeister_FullBackup_\(todayStr).json"
            : "ArtikelMeister_DeckPackage_\(todayStr).json"
        savePanel.allowedContentTypes = [.json]

        if savePanel.runModal() == .OK, let url = savePanel.url {
            do {
                try data.write(to: url, options: .atomic)
                let message = includeProgress
                    ? L10n.text("Tam yedek dışa aktarıldı: \(url.lastPathComponent)", en: "Full backup exported: \(url.lastPathComponent)", de: "Vollständiges Backup exportiert: \(url.lastPathComponent)")
                    : L10n.text("Deste paketi dışa aktarıldı: \(url.lastPathComponent)", en: "Deck package exported: \(url.lastPathComponent)", de: "Stapelpaket exportiert: \(url.lastPathComponent)")
                showBackupStatus(message, isError: false)
            } catch {
                showBackupStatus(L10n.text("Dosya kaydedilemedi. Başka bir konum seçip tekrar deneyin.", en: "Could not save file. Choose another location and try again.", de: "Datei konnte nicht gespeichert werden. Wähle einen anderen Speicherort."), isError: true)
            }
        }
    }

    private func importBackupPackage() {
        let openPanel = NSOpenPanel()
        openPanel.title = L10n.text("Yedeği veya paketi içe aktar", en: "Import Backup or Package", de: "Backup oder Paket importieren")
        openPanel.prompt = L10n.text("İçe aktar", en: "Import", de: "Importieren")
        openPanel.allowedContentTypes = [.json]
        openPanel.allowsMultipleSelection = false
        openPanel.canChooseDirectories = false

        guard openPanel.runModal() == .OK, let url = openPanel.url else { return }
        if mergeOnBackupImport {
            importBackupPackage(from: url, mergeWithExisting: true)
        } else {
            pendingReplacementURL = url
        }
    }

    private func importBackupPackage(from url: URL, mergeWithExisting: Bool) {
        do {
            let data = try Data(contentsOf: url)
            let result = try store.importBackupJSONData(data, mergeWithExisting: mergeWithExisting)
            let message = mergeWithExisting
                ? L10n.text("Paket birleştirildi: \(result.added) yeni kelime eklendi, \(result.updated) mevcut kelime kontrol edildi.", en: "Package merged: \(result.added) new words added, \(result.updated) existing words checked.", de: "Paket zusammengeführt: \(result.added) neue Wörter hinzugefügt, \(result.updated) vorhandene geprüft.")
                : L10n.text("Yedek içe aktarıldı. Toplam \(result.added) kelime var.", en: "Backup imported. Total \(result.added) words.", de: "Backup importiert. Insgesamt \(result.added) Wörter.")
            showBackupStatus(message, isError: false)
        } catch {
            showBackupStatus(
                L10n.text("Dosya içe aktarılamadı. Geçerli bir .json yedeği veya deste paketi seçip tekrar deneyin.", en: "Could not import file. Select a valid .json backup or deck package and try again.", de: "Datei konnte nicht importiert werden. Wähle ein gültiges .json-Backup oder Stapelpaket."),
                isError: true,
                detail: error.localizedDescription
            )
        }
    }

    private func showBackupStatus(_ message: String, isError: Bool, detail: String? = nil) {
        backupStatusIsError = isError
        backupStatusMessage = message
        backupStatusDetail = detail
        AccessibilityNotification.Announcement(message).post()
    }
}

// MARK: - Liquid Glass Settings Card & Duolingo-Style Flag View

private struct GlassSettingsCard<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    let title: String
    let systemImage: String
    let tint: Color
    @ViewBuilder let content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(tint.opacity(colorScheme == .dark ? 0.22 : 0.14))
                        .frame(width: 28, height: 28)
                    Image(systemName: systemImage)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(tint)
                }
                .accessibilityHidden(true)

                Text(title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.primary)
            }

            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: shape)
        .overlay {
            shape.strokeBorder(
                LinearGradient(
                    colors: [
                        Color.white.opacity(colorScheme == .dark ? 0.16 : 0.55),
                        Color.primary.opacity(colorScheme == .dark ? 0.06 : 0.08)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                lineWidth: 1
            )
            .allowsHitTesting(false)
        }
        .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.22 : 0.05), radius: 10, x: 0, y: 4)
    }
}

struct LanguageFlagView: View {
    let language: AppLanguage

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        Canvas { context, size in
            let w = size.width
            let h = size.height

            switch language {
            case .tr:
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.890, green: 0.039, blue: 0.090)))
                let outerRadius = h * 0.27
                let outerCenter = CGPoint(x: w * 0.40, y: h * 0.50)
                let outerCircle = Path(ellipseIn: CGRect(
                    x: outerCenter.x - outerRadius,
                    y: outerCenter.y - outerRadius,
                    width: outerRadius * 2,
                    height: outerRadius * 2
                ))
                context.fill(outerCircle, with: .color(.white))

                let innerRadius = h * 0.215
                let innerCenter = CGPoint(x: w * 0.445, y: h * 0.50)
                let innerCircle = Path(ellipseIn: CGRect(
                    x: innerCenter.x - innerRadius,
                    y: innerCenter.y - innerRadius,
                    width: innerRadius * 2,
                    height: innerRadius * 2
                ))
                context.fill(innerCircle, with: .color(Color(red: 0.890, green: 0.039, blue: 0.090)))

                let starCenter = CGPoint(x: w * 0.625, y: h * 0.50)
                let starOuter = h * 0.13
                let starInner = h * 0.052
                var starPath = Path()
                for i in 0..<10 {
                    let angle = (Double(i) * .pi / 5.0) - .pi
                    let r = (i % 2 == 0) ? starOuter : starInner
                    let pt = CGPoint(
                        x: starCenter.x + CGFloat(cos(angle)) * r,
                        y: starCenter.y + CGFloat(sin(angle)) * r
                    )
                    if i == 0 { starPath.move(to: pt) } else { starPath.addLine(to: pt) }
                }
                starPath.closeSubpath()
                context.fill(starPath, with: .color(.white))

            case .en:
                let navy = Color(red: 0.004, green: 0.129, blue: 0.412)
                let red = Color(red: 0.784, green: 0.063, blue: 0.180)
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(navy))

                var diagWhite = Path()
                diagWhite.move(to: CGPoint(x: 0, y: 0))
                diagWhite.addLine(to: CGPoint(x: w, y: h))
                diagWhite.move(to: CGPoint(x: w, y: 0))
                diagWhite.addLine(to: CGPoint(x: 0, y: h))
                context.stroke(diagWhite, with: .color(.white), lineWidth: h * 0.24)

                context.stroke(diagWhite, with: .color(red), lineWidth: h * 0.11)

                context.fill(Path(CGRect(x: 0, y: h * 0.34, width: w, height: h * 0.32)), with: .color(.white))
                context.fill(Path(CGRect(x: w * 0.38, y: 0, width: w * 0.24, height: h)), with: .color(.white))

                context.fill(Path(CGRect(x: 0, y: h * 0.40, width: w, height: h * 0.20)), with: .color(red))
                context.fill(Path(CGRect(x: w * 0.42, y: 0, width: w * 0.16, height: h)), with: .color(red))

            case .de:
                let bandH = h / 3.0
                context.fill(Path(CGRect(x: 0, y: 0, width: w, height: bandH)), with: .color(Color(red: 0.169, green: 0.169, blue: 0.169)))
                context.fill(Path(CGRect(x: 0, y: bandH, width: w, height: bandH)), with: .color(Color(red: 0.867, green: 0.0, blue: 0.0)))
                context.fill(Path(CGRect(x: 0, y: bandH * 2, width: w, height: bandH + 0.5)), with: .color(Color(red: 1.0, green: 0.808, blue: 0.0)))
            }
        }
        .frame(width: 28, height: 20)
        .clipShape(shape)
        .overlay(
            shape.strokeBorder(
                LinearGradient(
                    colors: [Color.white.opacity(0.45), Color.black.opacity(0.18)],
                    startPoint: .top,
                    endPoint: .bottom
                ),
                lineWidth: 0.75
            )
        )
        .shadow(color: .black.opacity(0.12), radius: 2, x: 0, y: 1)
        .accessibilityHidden(true)
    }
}

// MARK: - Helper Subviews

private struct SettingsStatusRow: View {
    let message: String
    let detail: String?
    let isError: Bool
    let inProgress: Bool

    private var foreground: Color {
        if inProgress { return AppPalette.accentBlue }
        return isError ? AppPalette.error : AppPalette.success
    }

    private var background: Color {
        if inProgress { return AppPalette.blueBadgeBackground }
        return isError ? AppPalette.errorBadgeBackground : AppPalette.successBadgeBackground
    }

    var body: some View {
        HStack(alignment: detail == nil ? .center : .firstTextBaseline, spacing: 8) {
            Image(systemName: inProgress ? "arrow.triangle.2.circlepath" : isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundStyle(foreground)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(message)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(foreground)
                if let detail {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(10)
        .background(background, in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct StatCard: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(color)
            Text(subtitle)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(color.opacity(0.28), lineWidth: 1)
        )
    }
}

private struct FeedbackBadge: View {
    let rating: ReviewRating
    let count: Int

    var body: some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.title3.bold())
                .foregroundStyle(.primary)
            Text(rating.title)
                .font(.caption2)
                .foregroundStyle(rating.color)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(rating.badgeBackground, in: RoundedRectangle(cornerRadius: 8))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(rating.title): \(count)")
    }
}

private struct DistributionRow: View {
    let label: String
    let count: Int
    let total: Int
    let color: Color

    var ratio: Double {
        guard total > 0 else { return 0 }
        return Double(count) / Double(total)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                    .font(.subheadline.weight(.medium))
                Spacer()
                Text(L10n.text("\(count) kelime (\(Int(round(ratio * 100)))%)", en: "\(count) words (\(Int(round(ratio * 100)))%)", de: "\(count) Wörter (\(Int(round(ratio * 100)))%)"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.secondary.opacity(0.15))
                    Capsule()
                        .fill(color)
                        .frame(width: max(0, geo.size.width * ratio))
                }
            }
            .frame(height: 8)
        }
    }
}
