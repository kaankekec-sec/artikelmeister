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
                Text("Çalışma İstatistikleri")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)

                let activeDays = activeStudyDaysCount
                let hasStudyHistory = activeDays > 0 || store.dailyProgress.totalAnswersCount > 0
                if store.cards.isEmpty {
                    emptyStatistics(
                        "Henüz kelime yok",
                        systemImage: "folder",
                        description: "Başlamak için Kelimeler sekmesinden kelime ekleyin.",
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
            Button(destination.rawValue) {
                tabSelection?.wrappedValue = destination
            }
            .buttonStyle(.bordered)
            .disabled(tabSelection == nil)
        }
    }

    private var todayActivity: some View {
        GroupBox(label: Label("Bugünkü çalışma (\(store.dailyProgress.dateString))", systemImage: "calendar")) {
            HStack(spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Yeni Öğrenilen: **\(store.dailyProgress.newCardsStudied)** / \(store.settings.dailyNewCardLimit)")
                    Text("Tamamlanan Tekrar: **\(store.dailyProgress.reviewsCompleted)** / \(store.settings.dailyReviewLimit)")
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
        return GroupBox(label: Label("Günlük Seri (Streak) ve 35 Günlük Çalışma Isı Haritası", systemImage: "flame.fill")) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 24) {
                    HStack(spacing: 12) {
                        Image(systemName: "flame.fill")
                            .accessibilityHidden(true)
                            .font(.system(size: 30))
                            .foregroundColor(currentStreakDays > 0 ? AppPalette.accentOrange : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(currentStreakDays) Gün")
                                .font(.title2.bold())
                            Text("Kesintisiz Günlük Seri")
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
                            Text("\(store.longestStreakDays) Gün")
                                .font(.title3.bold())
                            Text("En Uzun Seri Rekoru")
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
                            Text("\(activeDays) Gün")
                                .font(.title3.bold())
                            Text("Toplam Aktif Çalışılan Gün")
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
                        "Henüz çalışma kaydı yok",
                        systemImage: "calendar",
                        description: "İlk yanıtınızı vermek için Çalışma sekmesine geçin.",
                        destination: .study
                    )
                }
            }
            .padding(12)
        }
    }

    private var activityHeatmap: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Son 35 Günlük Aktivite Yoğunluğu")
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
            legendSwatch(0, label: "0 yanıt")
            legendSwatch(1, label: "1–4 yanıt")
            legendSwatch(5, label: "5–14 yanıt")
            legendSwatch(15, label: "15–34 yanıt")
            legendSwatch(35, label: "35+ yanıt")
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Yanıt sayısı ölçeği")
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
            StatCard(title: "Yeni (Çalışılmamış)", value: "\(cardCounts.newCount)", subtitle: "Sırada bekleyen", color: CardStage.new.color)
            StatCard(title: "Öğreniliyor", value: "\(cardCounts.learningCount)", subtitle: "Kısa aralıklı", color: CardStage.learning.color)
            StatCard(title: "Genç Kartlar", value: "\(cardCounts.reviewCount)", subtitle: "Aralık < 21 gün", color: CardStage.review.color)
            StatCard(title: "Olgun kartlar", value: "\(cardCounts.matureCount)", subtitle: "Aralık ≥ 21 gün", color: AppPalette.accentPurple)
        }
    }

    private func distributions(totalCards: Int, deckNames: [String]) -> some View {
        VStack(alignment: .leading, spacing: 24) {
            // Artikel Distribution
            GroupBox(label: Label("Artikel Dağılımı (Toplam \(totalCards) Kelime)", systemImage: "chart.bar.fill")) {
                VStack(spacing: 12) {
                    DistributionRow(label: "der (Eril)", count: cardCounts.articles[.der, default: 0], total: totalCards, color: Artikel.der.color)
                    DistributionRow(label: "die (Dişil)", count: cardCounts.articles[.die, default: 0], total: totalCards, color: Artikel.die.color)
                    DistributionRow(label: "das (Nötr)", count: cardCounts.articles[.das, default: 0], total: totalCards, color: Artikel.das.color)
                    if cardCounts.articlelessCount > 0 {
                        DistributionRow(label: "Artikelsiz", count: cardCounts.articlelessCount, total: totalCards, color: AppPalette.articleNone)
                    }
                }
                .padding(12)
            }

            // Deck Distribution (#5)
            GroupBox(label: Label("Deste / Kategori Dağılımı (\(deckNames.count) Deste)", systemImage: "folder.fill")) {
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
        formatter.locale = Locale(identifier: "tr_TR")
        formatter.dateStyle = .long
        return formatter
    }()

    var body: some View {
        let colors = AppPalette.heatmapColors(count)
        let fullDate = Self.dateParser.date(from: dateString).map {
            Self.spokenDateFormatter.string(from: $0)
        } ?? dateString
        let summary = "\(fullDate): \(count) yanıt"

        VStack(alignment: .leading, spacing: 2) {
            Text(displayDate)
                .font(.callout.weight(.medium))
            Text("\(count) yanıt")
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
            VStack(alignment: .leading, spacing: 24) {
                Text("Ayarlar")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)

                appearanceSettings
                dailyLimits
                cardPreferences
                cloudSettings
                backupSettings
                dataManagement
            }
            .padding(24)
        }
        .onChange(of: cloudStatusMessage) { _, message in
            if let message {
                AccessibilityNotification.Announcement(message).post()
            }
        }
        .confirmationDialog(
            "Tüm kelimelerin öğrenme geçmişini sıfırlamak istediğinize emin misiniz?",
            isPresented: $showResetConfirmation,
            titleVisibility: .visible
        ) {
            Button("İlerlemeyi Sıfırla (Kelimeler Kalır)", role: .destructive) {
                store.resetAllProgress()
            }
            Button("İptal", role: .cancel) {}
        }
        .confirmationDialog(
            "Yerel depolamadaki tüm kelimeleri silmek istediğinize emin misiniz?",
            isPresented: $showDeleteAllConfirmation,
            titleVisibility: .visible
        ) {
            Button("Tüm Kelimeleri Sil", role: .destructive) {
                store.deleteAllCards()
            }
            Button("İptal", role: .cancel) {}
        }
        .confirmationDialog(
            "Hesap Verilerini Silmek İstediğinize Emin Misiniz?",
            isPresented: $showDeleteAccountConfirmation,
            titleVisibility: .visible
        ) {
            Button("Evet, Tüm Hesap Verilerini Sil", role: .destructive) {
                isDeletingAccount = true
                Task {
                    await store.deleteAccountAndAllData(authManager: authManager)
                    isDeletingAccount = false
                }
            }
            Button("İptal", role: .cancel) {}
        } message: {
            Text("Bu işlem geri alınamaz:\n• Google Drive'daki \"ArtikelMeister Cloud Sync\" klasörünüz ve bulut yedeğiniz kalıcı olarak silinir.\n• Bu cihazdaki tüm kelimeleriniz, desteleriniz, günlük seriniz (streak) ve tercih ayarlarınız sıfırlanır.\n• Google hesabı bağlantınız sonlandırılır.")
        }
    }

    private var dailyLimits: some View {
        GroupBox(label: Label("Günlük Çalışma Limitleri", systemImage: "slider.horizontal.3")) {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Günlük Yeni Kelime Sayısı")
                            .font(.headline)
                        Text("Her gün çalışma sırasına eklenecek maksimum yeni kelime adedi.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
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
                        Text("\(store.settings.dailyNewCardLimit) kelime / gün")
                            .font(.headline.monospacedDigit())
                            .frame(width: 130, alignment: .trailing)
                    }
                    .accessibilityLabel("Günlük yeni kelime limiti")
                    .accessibilityValue("\(store.settings.dailyNewCardLimit) kelime / gün")
                }

                Slider(
                    value: Binding(
                        get: { Double(store.settings.dailyNewCardLimit) },
                        set: {
                            store.settings.dailyNewCardLimit = Int($0)
                            store.saveSettings()
                        }
                    ),
                    in: 1...500,
                    step: 1
                )
                .tint(AppPalette.accentBlue)
                .accessibilityLabel("Günlük yeni kelime limiti")
                .accessibilityValue("\(store.settings.dailyNewCardLimit) kelime / gün")

                Divider()

                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Günlük Maksimum Tekrar Sayısı")
                            .font(.headline)
                        Text("Daha önce öğrenilmiş kelimelerden bir günde sorulacak maksimum tekrar kartı sayısı.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
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
                        Text("\(store.settings.dailyReviewLimit) tekrar / gün")
                            .font(.headline.monospacedDigit())
                            .frame(width: 130, alignment: .trailing)
                    }
                    .accessibilityLabel("Günlük tekrar limiti")
                    .accessibilityValue("\(store.settings.dailyReviewLimit) tekrar / gün")
                }

                Slider(
                    value: Binding(
                        get: { Double(store.settings.dailyReviewLimit) },
                        set: {
                            store.settings.dailyReviewLimit = Int($0)
                            store.saveSettings()
                        }
                    ),
                    in: 10...1000,
                    step: 10
                )
                .tint(AppPalette.accentBlue)
                .accessibilityLabel("Günlük tekrar limiti")
                .accessibilityValue("\(store.settings.dailyReviewLimit) tekrar / gün")
            }
            .padding(14)
        }
    }

    private var appearanceSettings: some View {
        GroupBox(label: Label("Görünüm", systemImage: "circle.lefthalf.filled")) {
            VStack(alignment: .leading, spacing: 8) {
                Picker("Tema", selection: Binding(
                    get: { store.themeMode },
                    set: { newMode in
                        withAnimation(.easeInOut(duration: 0.3)) { store.themeMode = newMode }
                    }
                )) {
                    ForEach(ThemeMode.allCases) { mode in
                        Text(mode == .system ? "Sistem" : mode.title).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .accessibilityLabel("Tema")

                Text("Sistem seçeneği, Mac'inizin görünüm ayarını takip eder.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(14)
        }
    }

    private var cardPreferences: some View {
        GroupBox(label: Label("Kart Sunum Tercihleri", systemImage: "rectangle.on.rectangle")) {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(
                    "Kart çevrildiğinde Almanca kelimeyi otomatik seslendir",
                    isOn: Binding(
                        get: { store.settings.autoPlayPronunciation },
                        set: {
                            store.settings.autoPlayPronunciation = $0
                            store.saveSettings()
                        }
                    )
                )

                Toggle(
                    "Kartın ön yüzünde Türkçe anlamı (ipucu olarak) göster",
                    isOn: Binding(
                        get: { store.settings.showMeaningOnFront },
                        set: {
                            store.settings.showMeaningOnFront = $0
                            store.saveSettings()
                        }
                    )
                )

                Toggle(
                    "Yeni kelimeleri eklenme sırası yerine rastgele sırayla getir",
                    isOn: Binding(
                        get: { store.settings.newCardOrderRandom },
                        set: {
                            store.settings.newCardOrderRandom = $0
                            store.saveSettings()
                        }
                    )
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
        }
    }

    private var cloudSettings: some View {
        GroupBox(label: Label(
            "Google Drive ile eşitleme",
            systemImage: "person.crop.circle"
        )) {
            VStack(alignment: .leading, spacing: 14) {
                if authManager.isGoogleAccountConnected {
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.icloud.fill")
                            .accessibilityHidden(true)
                            .font(.system(size: 20))
                            .foregroundStyle(AppPalette.success)
                            .frame(width: 40, height: 40)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(!authManager.userName.isEmpty ? authManager.userName : "Google Hesabı Bağlı")
                                .font(.subheadline.bold())
                            Text(!authManager.userEmail.isEmpty ? authManager.userEmail : "Google Profil Aktif")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text("Bulut Eşitleme Aktif")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(AppPalette.success)
                        }

                        Spacer()
                    }
                    .padding(12)
                    .background(AppPalette.successBadgeBackground)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(AppPalette.success.opacity(0.35), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10))

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
                                Text(store.isSyncingCloud ? "Eşitleniyor..." : "Şimdi Bulutla Eşitle")
                                    .fontWeight(.semibold)
                            }
                            .foregroundStyle(AppPalette.onBlue)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(AppPalette.accentBlue)
                        .disabled(store.isSyncingCloud || isDeletingAccount)

                        Button {
                            authManager.clearAuthorization()
                            store.cloudSyncStatusMessage = "Google hesabından çıkış yapıldı (yerel veriler korundu)."
                            store.cloudSyncIsError = false
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                    .accessibilityHidden(true)
                                Text("Hesaptan Çıkış Yap")
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                } else {
                    Text("Google hesabınızla giriş yaparak tüm kelimelerinizi, çalışma geçmişinizi, günlük serinizi (streak) ve tercihlerinizi Google Drive bulutunuzla otomatik eşitleyebilirsiniz.")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Button {
                        Task {
                            do {
                                try await authManager.startBrowserOAuthFlow()
                                await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                            } catch {
                                store.cloudSyncIsError = true
                                store.cloudSyncStatusMessage = "Bulut bağlantı hatası: \(error.localizedDescription)"
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
            .padding(14)
        }
    }

    private var signInButtonTitle: String {
        if authManager.isAuthenticating { return "Google hesabına bağlanılıyor..." }
        if store.isSyncingCloud { return "Eşitleniyor..." }
        return "Google Hesabı ile Giriş Yap & Eşitle"
    }

    private var cloudStatusMessage: String? {
        if isDeletingAccount { return "Hesap verileri siliniyor..." }
        if authManager.isAuthenticating { return "Google hesabına bağlanılıyor..." }
        if store.isSyncingCloud { return "Google Drive ile eşitleniyor..." }
        guard let message = store.cloudSyncStatusMessage else { return nil }
        if store.cloudSyncIsError {
            if message.hasPrefix("Bulut bağlantı hatası:") {
                return "Google hesabına bağlanılamadı. Tekrar giriş yapmayı deneyin."
            }
            if message.hasPrefix("Bulut eşitleme hatası:") {
                return "Google Drive ile eşitlenemedi. Bağlantınızı kontrol edip tekrar deneyin."
            }
            if message.hasPrefix("Yerel kelime dosyası okunamadı") {
                return "Yerel kelime dosyası okunamadı. Dosyanızı kontrol edip tekrar deneyin."
            }
            return "İşlem tamamlanamadı. Tekrar deneyin."
        }
        return message
    }

    private var backupSettings: some View {
        GroupBox(label: Label("Yedekleme ve Deste Paylaşımı", systemImage: "archivebox")) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Yedekler ve deste paketleri .json biçimindedir. Deste paketi kişisel ilerlemeyi içermez.")
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

                Toggle("Birleştir", isOn: $mergeOnBackupImport)
                    .accessibilityHint("Açıkken içe aktarılan kelimeler mevcut kelimelerle birleştirilir.")
                Text("Birleştirme kapalıysa içe aktarma, onayınızdan sonra mevcut verileri değiştirir.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let message = backupStatusMessage {
                    SettingsStatusRow(message: message, detail: backupStatusDetail, isError: backupStatusIsError, inProgress: false)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
        }
        .alert(
            "Mevcut veriler değiştirilsin mi?",
            isPresented: Binding(
                get: { pendingReplacementURL != nil },
                set: { if !$0 { pendingReplacementURL = nil } }
            ),
            presenting: pendingReplacementURL
        ) { url in
            Button("Mevcut verileri değiştir", role: .destructive) {
                importBackupPackage(from: url, mergeWithExisting: false)
            }
            Button("İptal", role: .cancel) {}
        } message: { url in
            Text("“\(url.lastPathComponent)” içe aktarıldığında mevcut verileriniz değiştirilecek.")
        }
    }

    @ViewBuilder
    private var backupActions: some View {
        Button {
            exportBackupPackage(includeProgress: true)
        } label: {
            Label("Tam yedeği dışa aktar", systemImage: "square.and.arrow.up")
                .foregroundStyle(AppPalette.onBlue)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppPalette.accentBlue)
        .disabled(store.cards.isEmpty)

        Button {
            exportBackupPackage(includeProgress: false)
        } label: {
            Label("Deste paketini dışa aktar", systemImage: "square.and.arrow.up")
        }
        .buttonStyle(.bordered)
        .disabled(store.cards.isEmpty)

        Button {
            importBackupPackage()
        } label: {
            Label("Yedeği veya paketi içe aktar", systemImage: "square.and.arrow.down")
        }
        .buttonStyle(.bordered)
    }

    private var dataManagement: some View {
        GroupBox(label: Label("Veri Yönetimi", systemImage: "internaldrive")) {
            VStack(alignment: .leading, spacing: 12) {
                Text("İçe aktarılan tüm kelimeler ve Anki öğrenme geçmişiniz bilgisayarınızda yerel olarak şu klasörde saklanır:")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                HStack {
                    Text(store.storageDirectory.path)
                        .font(.system(.caption, design: .monospaced))
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(NSColor.textBackgroundColor))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .textSelection(.enabled)

                    Button {
                        store.openStorageDirectoryInFinder()
                    } label: {
                        Label("Finder'da Aç", systemImage: "folder")
                            .accessibilityLabel("Yerel depolama klasörünü Finder'da aç")
                    }
                    .buttonStyle(.bordered)
                }

                Divider().padding(.vertical, 4)

                HStack(spacing: 12) {
                    Button(role: .destructive) {
                        showResetConfirmation = true
                    } label: {
                        Label("Öğrenme İlerlemesini Sıfırla", systemImage: "arrow.counterclockwise")
                    }
                    .buttonStyle(.bordered)
                    .disabled(store.cards.isEmpty)

                    Button(role: .destructive) {
                        showDeleteAllConfirmation = true
                    } label: {
                        Label("Tüm Kelimeleri Sil", systemImage: "trash")
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
                            Text(isDeletingAccount ? "Hesap verileri siliniyor..." : "Hesap Verilerini Sil")
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(isDeletingAccount || (store.cards.isEmpty && !authManager.isGoogleAccountConnected))
                }
            }
            .padding(14)
        }
    }

    private func exportBackupPackage(includeProgress: Bool) {
        let data: Data
        do {
            data = try store.exportBackupJSONData(includeProgress: includeProgress)
        } catch {
            showBackupStatus("Yedek oluşturulamadı. Tekrar deneyin.", isError: true)
            return
        }

        let todayStr = DailyProgress.todayString()
        let savePanel = NSSavePanel()
        savePanel.title = includeProgress ? "Tam yedeği dışa aktar" : "Deste paketini dışa aktar"
        savePanel.prompt = "Dışa aktar"
        savePanel.nameFieldStringValue = includeProgress
            ? "ArtikelMeister_TamYedek_\(todayStr).json"
            : "ArtikelMeister_DestePaketi_\(todayStr).json"
        savePanel.allowedContentTypes = [.json]

        if savePanel.runModal() == .OK, let url = savePanel.url {
            do {
                try data.write(to: url, options: .atomic)
                let message = includeProgress
                    ? "Tam yedek dışa aktarıldı: \(url.lastPathComponent)"
                    : "Deste paketi dışa aktarıldı: \(url.lastPathComponent)"
                showBackupStatus(message, isError: false)
            } catch {
                showBackupStatus("Dosya kaydedilemedi. Başka bir konum seçip tekrar deneyin.", isError: true)
            }
        }
    }

    private func importBackupPackage() {
        let openPanel = NSOpenPanel()
        openPanel.title = "Yedeği veya paketi içe aktar"
        openPanel.prompt = "İçe aktar"
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
                ? "Paket birleştirildi: \(result.added) yeni kelime eklendi, \(result.updated) mevcut kelime kontrol edildi."
                : "Yedek içe aktarıldı. Toplam \(result.added) kelime var."
            showBackupStatus(message, isError: false)
        } catch {
            showBackupStatus(
                "Dosya içe aktarılamadı. Geçerli bir .json yedeği veya deste paketi seçip tekrar deneyin.",
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
        .background(Color(NSColor.controlBackgroundColor))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(color.opacity(0.25), lineWidth: 1)
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
                Text("\(count) kelime (\(Int(round(ratio * 100)))%)")
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
