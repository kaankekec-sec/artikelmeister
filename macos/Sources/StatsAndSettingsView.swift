import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct StatsView: View {
    @EnvironmentObject var store: DeckStore

    private var totalCards: Int { store.cards.count }
    private var newCount: Int { store.cards.filter { $0.stage == .new }.count }
    private var learningCount: Int { store.cards.filter { $0.stage == .learning || $0.stage == .relearning }.count }
    private var reviewCount: Int { store.cards.filter { $0.stage == .review && $0.intervalDays < 21 }.count }
    private var matureCount: Int { store.cards.filter { $0.stage == .review && $0.intervalDays >= 21 }.count }

    private var derCount: Int { store.cards.filter { $0.artikel == .der }.count }
    private var dieCount: Int { store.cards.filter { $0.artikel == .die }.count }
    private var dasCount: Int { store.cards.filter { $0.artikel == .das }.count }

    private var activeStudyDaysCount: Int {
        store.studyHistory.values.filter { $0.totalAnswersCount > 0 }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Çalışma İstatistikleri")
                    .font(.title2.bold())

                // Summary Cards
                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible())
                ], spacing: 16) {
                    StatCard(title: "Yeni (Çalışılmamış)", value: "\(newCount)", subtitle: "Sırada bekleyen", color: .blue)
                    StatCard(title: "Öğreniliyor", value: "\(learningCount)", subtitle: "Kısa aralıklı", color: .orange)
                    StatCard(title: "Genç Kartlar", value: "\(reviewCount)", subtitle: "Aralık < 21 gün", color: .green)
                    StatCard(title: "Olgunlaşmış (Kalıcı)", value: "\(matureCount)", subtitle: "Aralık ≥ 21 gün", color: .purple)
                }

                // Streak & 35-Day Activity Heatmap (#4)
                GroupBox(label: Label("Günlük Seri (Streak) ve 35 Günlük Çalışma Isı Haritası", systemImage: "flame.fill")) {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(spacing: 24) {
                            HStack(spacing: 12) {
                                Image(systemName: "flame.fill")
                                    .font(.system(size: 30))
                                    .foregroundColor(store.currentStreakDays > 0 ? .orange : .secondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(store.currentStreakDays) Gün")
                                        .font(.title2.bold())
                                    Text("Kesintisiz Günlük Seri")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Divider().frame(height: 40)

                            HStack(spacing: 12) {
                                Image(systemName: "trophy.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(.yellow)
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
                                    .font(.system(size: 26))
                                    .foregroundColor(.green)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(activeStudyDaysCount) Gün")
                                        .font(.title3.bold())
                                    Text("Toplam Aktif Çalışılan Gün")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()
                        }

                        Divider()

                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Son 35 Günlük Aktivite Yoğunluğu")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(.secondary)
                                Spacer()
                                HStack(spacing: 4) {
                                    Text("Az").font(.caption2).foregroundColor(.secondary)
                                    ForEach([0, 3, 12, 25, 50], id: \.self) { sample in
                                        RoundedRectangle(cornerRadius: 3)
                                            .fill(heatmapColor(for: sample))
                                            .frame(width: 12, height: 12)
                                    }
                                    Text("Çok").font(.caption2).foregroundColor(.secondary)
                                }
                            }

                            let days = store.heatmapDays(daysCount: 35)
                            LazyVGrid(
                                columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 7),
                                spacing: 8
                            ) {
                                ForEach(days, id: \.dateString) { item in
                                    HeatmapCell(
                                        dateString: item.dateString,
                                        displayDate: item.displayDate,
                                        count: item.count,
                                        color: heatmapColor(for: item.count)
                                    )
                                }
                            }
                        }
                    }
                    .padding(12)
                }

                // Today's Activity
                GroupBox(label: Label("Bugünkü Performans (\(store.dailyProgress.dateString))", systemImage: "calendar")) {
                    HStack(spacing: 24) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Yeni Öğrenilen: **\(store.dailyProgress.newCardsStudied)** / \(store.settings.dailyNewCardLimit)")
                            Text("Tamamlanan Tekrar: **\(store.dailyProgress.reviewsCompleted)** / \(store.settings.dailyReviewLimit)")
                        }
                        Divider().frame(height: 44)
                        HStack(spacing: 18) {
                            FeedbackBadge(title: "Tekrar (1)", count: store.dailyProgress.againCount, color: ReviewRating.again.color)
                            FeedbackBadge(title: "Zor (2)", count: store.dailyProgress.hardCount, color: ReviewRating.hard.color)
                            FeedbackBadge(title: "İyi (3)", count: store.dailyProgress.goodCount, color: ReviewRating.good.color)
                            FeedbackBadge(title: "Kolay (4)", count: store.dailyProgress.easyCount, color: ReviewRating.easy.color)
                        }
                        Spacer()
                    }
                    .padding(12)
                }

                // Artikel Distribution
                GroupBox(label: Label("Artikel Dağılımı (Toplam \(totalCards) Kelime)", systemImage: "chart.bar.fill")) {
                    VStack(spacing: 12) {
                        DistributionRow(label: "der (Eril)", count: derCount, total: max(1, totalCards), color: Artikel.der.color)
                        DistributionRow(label: "die (Dişil)", count: dieCount, total: max(1, totalCards), color: Artikel.die.color)
                        DistributionRow(label: "das (Nötr)", count: dasCount, total: max(1, totalCards), color: Artikel.das.color)
                    }
                    .padding(12)
                }

                // Deck Distribution (#5)
                GroupBox(label: Label("Deste / Kategori Dağılımı (\(store.allDeckNames.count) Deste)", systemImage: "folder.fill")) {
                    VStack(spacing: 10) {
                        ForEach(store.allDeckNames, id: \.self) { deck in
                            let count = store.cards.filter { $0.deckName == deck }.count
                            DistributionRow(
                                label: deck,
                                count: count,
                                total: max(1, totalCards),
                                color: .indigo
                            )
                        }
                    }
                    .padding(12)
                }
            }
            .padding(24)
        }
    }

    private func heatmapColor(for count: Int) -> Color {
        if count <= 0 {
            return Color.secondary.opacity(0.14)
        } else if count < 5 {
            return Color.green.opacity(0.35)
        } else if count < 15 {
            return Color.green.opacity(0.55)
        } else if count < 35 {
            return Color.green.opacity(0.78)
        } else {
            return Color.green
        }
    }
}

private struct HeatmapCell: View {
    let dateString: String
    let displayDate: String
    let count: Int
    let color: Color

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(displayDate)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(count > 0 ? .primary : .secondary)
                Text(count > 0 ? "\(count) yanıt" : "—")
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .foregroundColor(count > 0 ? .primary : .secondary.opacity(0.7))
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(color)
        .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .stroke(Color.primary.opacity(count > 0 ? 0.12 : 0.04), lineWidth: 1)
        )
        .help("\(dateString): \(count) kart yanıtlandı")
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
    @State private var backupStatusMessage: String? = nil
    @State private var backupStatusIsError: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Ayarlar ve Bulut Eşitleme")
                    .font(.title2.bold())

                // Universal Google Profile & Cloud Synchronization
                GroupBox(label: Label(
                    "Evrensel Profil & Google Drive Bulut Eşitleme",
                    systemImage: authManager.isGoogleAccountConnected ? "checkmark.icloud.fill" : "person.crop.circle"
                )) {
                    VStack(alignment: .leading, spacing: 14) {
                        if authManager.isGoogleAccountConnected {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(Color.green.opacity(0.18))
                                        .frame(width: 40, height: 40)
                                    Image(systemName: "checkmark.icloud.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(.green)
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(!authManager.userName.isEmpty ? authManager.userName : "Google Hesabı Bağlı")
                                        .font(.subheadline.bold())
                                    Text(!authManager.userEmail.isEmpty ? authManager.userEmail : "Google Profil Aktif")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    Text("Bulut Eşitleme Aktif")
                                        .font(.caption2.weight(.medium))
                                        .foregroundColor(.green)
                                }

                                Spacer()
                            }
                            .padding(12)
                            .background(Color.green.opacity(0.08))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.green.opacity(0.25), lineWidth: 1)
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
                                                .frame(width: 14, height: 14)
                                        } else {
                                            Image(systemName: "arrow.triangle.2.circlepath")
                                        }
                                        Text(store.isSyncingCloud ? "Eşitleniyor..." : "Şimdi Bulutla Eşitle")
                                            .fontWeight(.semibold)
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(.blue)
                                .disabled(store.isSyncingCloud || isDeletingAccount)

                                Button {
                                    authManager.clearAuthorization()
                                    store.cloudSyncStatusMessage = "Google hesabından çıkış yapıldı (yerel veriler korundu)."
                                    store.cloudSyncIsError = false
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "rectangle.portrait.and.arrow.right")
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
                                            .frame(width: 14, height: 14)
                                    } else {
                                        Image(systemName: "person.crop.circle.badge.plus")
                                    }
                                    Text(authManager.isAuthenticating ? "Google ile Bağlanılıyor..." : "Google Hesabı ile Giriş Yap & Eşitle")
                                        .fontWeight(.semibold)
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.blue)
                            .disabled(authManager.isAuthenticating || store.isSyncingCloud)
                        }

                        if let statusMsg = store.cloudSyncStatusMessage {
                            HStack(spacing: 8) {
                                Image(systemName: store.cloudSyncIsError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                                    .foregroundColor(store.cloudSyncIsError ? .red : .green)
                                Text(statusMsg)
                                    .font(.caption.weight(.medium))
                                    .foregroundColor(store.cloudSyncIsError ? .red : .primary)
                                Spacer()
                            }
                            .padding(10)
                            .background((store.cloudSyncIsError ? Color.red : Color.green).opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                }

                // Appearance (shares store.themeMode with the sidebar toggle)
                GroupBox(label: Label("Görünüm", systemImage: "circle.lefthalf.filled")) {
                    VStack(alignment: .leading, spacing: 8) {
                        Picker("Tema", selection: Binding(
                            get: { store.themeMode },
                            set: { newMode in
                                withAnimation(.easeInOut(duration: 0.3)) { store.themeMode = newMode }
                            }
                        )) {
                            ForEach(ThemeMode.allCases) { mode in
                                Text(mode.title).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()

                        Text("Sistem varsayılanı, Mac'inizin açık/koyu ayarını takip eder. Kenar çubuğundaki düğme de aynı ayarı değiştirir.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(14)
                }

                // Daily Limits
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
                        }

                        Slider(
                            value: Binding(
                                get: { Double(store.settings.dailyNewCardLimit) },
                                set: {
                                    store.settings.dailyNewCardLimit = Int($0)
                                    store.saveSettings()
                                }
                            ),
                            in: 1...200,
                            step: 1
                        )

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
                        }

                        Slider(
                            value: Binding(
                                get: { Double(store.settings.dailyReviewLimit) },
                                set: {
                                    store.settings.dailyReviewLimit = Int($0)
                                    store.saveSettings()
                                }
                            ),
                            in: 10...500,
                            step: 10
                        )
                    }
                    .padding(14)
                }

                // Card Presentation Preferences
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

                // One-Click Backup & Restore / Deck Sharing (#6)
                GroupBox(label: Label("Yedekleme ve Deste Paylaşımı", systemImage: "externaldrive.fill.badge.timemachine")) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Tüm kelime haznenizi, deste kategorilerinizi ve öğrenme geçmişinizi tek tıkla `.json` paketi olarak yedekleyebilir veya arkadaşlarınızla paylaşmak üzere kişisel ilerlemenizden arındırılmış temiz deste paketi oluşturabilirsiniz.")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        HStack(spacing: 10) {
                            Button {
                                exportBackupPackage(includeProgress: true)
                            } label: {
                                Label("Tam Yedek İndir (.json)", systemImage: "square.and.arrow.up.on.square.fill")
                                    .lineLimit(1)
                            }
                            .buttonStyle(.borderedProminent)
                            .fixedSize()
                            .disabled(store.cards.isEmpty)

                            Button {
                                exportBackupPackage(includeProgress: false)
                            } label: {
                                Label("Paylaşılabilir Deste Paketi (.json)", systemImage: "person.2.fill")
                                    .lineLimit(1)
                            }
                            .buttonStyle(.bordered)
                            .fixedSize()
                            .disabled(store.cards.isEmpty)

                            Button {
                                importBackupPackage()
                            } label: {
                                Label("Yedek / Paket Yükle (.json)", systemImage: "square.and.arrow.down.on.square.fill")
                                    .lineLimit(1)
                            }
                            .buttonStyle(.bordered)
                            .fixedSize()
                        }

                        Toggle(
                            "İçe aktarırken mevcut kelimelerimi koru ve yeni gelenleri birleştir (Kapatılırsa yedek dosyası tüm verilerin üzerine yazılır)",
                            isOn: $mergeOnBackupImport
                        )
                        .font(.caption)

                        if let msg = backupStatusMessage {
                            HStack(spacing: 8) {
                                Image(systemName: backupStatusIsError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                                    .foregroundColor(backupStatusIsError ? .red : .green)
                                Text(msg)
                                    .font(.caption.weight(.medium))
                                    .foregroundColor(backupStatusIsError ? .red : .primary)
                                Spacer()
                            }
                            .padding(8)
                            .background((backupStatusIsError ? Color.red : Color.green).opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                }

                // Local Storage Management
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
                            }
                            .buttonStyle(.borderedProminent)
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
                                    } else {
                                        Image(systemName: "person.crop.circle.badge.minus")
                                    }
                                    Text(isDeletingAccount ? "Siliniyor..." : "Hesap Verilerini Sil")
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                            .disabled(isDeletingAccount || (store.cards.isEmpty && !authManager.isGoogleAccountConnected))
                        }
                    }
                    .padding(14)
                }
            }
            .padding(24)
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

    private func exportBackupPackage(includeProgress: Bool) {
        let data: Data
        do {
            data = try store.exportBackupJSONData(includeProgress: includeProgress)
        } catch {
            backupStatusIsError = true
            backupStatusMessage = "Yedek verisi oluşturulamadı: \(error.localizedDescription)"
            return
        }

        let todayStr = DailyProgress.todayString()
        let savePanel = NSSavePanel()
        savePanel.title = includeProgress ? "Tam Yedek Dışa Aktar (.json)" : "Paylaşılabilir Deste Paketi Dışa Aktar (.json)"
        savePanel.nameFieldStringValue = includeProgress
            ? "ArtikelMeister_TamYedek_\(todayStr).json"
            : "ArtikelMeister_DestePaketi_\(todayStr).json"
        savePanel.allowedContentTypes = [.json]

        if savePanel.runModal() == .OK, let url = savePanel.url {
            do {
                try data.write(to: url, options: .atomic)
                backupStatusIsError = false
                backupStatusMessage = includeProgress
                    ? "Tam yedek başarıyla kaydedildi: \(url.lastPathComponent)"
                    : "Paylaşılabilir deste paketi (kişisel ilerleme olmadan) kaydedildi: \(url.lastPathComponent)"
            } catch {
                backupStatusIsError = true
                backupStatusMessage = "Dosya kaydedilemedi: \(error.localizedDescription)"
            }
        }
    }

    private func importBackupPackage() {
        let openPanel = NSOpenPanel()
        openPanel.title = "ArtikelMeister Yedek veya Deste Paketi (.json) Seç"
        openPanel.allowedContentTypes = [.json]
        openPanel.allowsMultipleSelection = false
        openPanel.canChooseDirectories = false

        if openPanel.runModal() == .OK, let url = openPanel.url {
            do {
                let data = try Data(contentsOf: url)
                let result = try store.importBackupJSONData(data, mergeWithExisting: mergeOnBackupImport)
                backupStatusIsError = false
                if mergeOnBackupImport {
                    backupStatusMessage = "Paket birleştirildi: \(result.added) yeni kelime eklendi, \(result.updated) mevcut kelime kontrol edildi."
                } else {
                    backupStatusMessage = "Yedek başarıyla geri yüklendi: Toplam \(result.added) kelime aktif."
                }
            } catch {
                backupStatusIsError = true
                backupStatusMessage = "Yedek dosyası okunamadı: \(error.localizedDescription)"
            }
        }
    }
}

// MARK: - Helper Subviews

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
    let title: String
    let count: Int
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.title3.bold())
                .foregroundColor(color)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(color.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
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
