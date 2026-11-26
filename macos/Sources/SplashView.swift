import SwiftUI
import AppKit

private enum AppLogoAssets {
    static let squircleFull: NSImage? = loadLogoImage(named: "app_logo_squircle")
    static let squircleNoDrops: NSImage? = loadLogoImage(named: "app_logo_squircle_nodrops")
    static let brainNoDrops: NSImage? = loadLogoImage(named: "app_logo_brain_nodrops")

    private static func loadLogoImage(named name: String) -> NSImage? {
        if let url = Bundle.main.url(forResource: name, withExtension: "png"),
           let img = NSImage(contentsOf: url) {
            return img
        }
        let fallbackPaths = [
            "shared/logo/\(name).png",
            "../shared/logo/\(name).png",
            "/Users/kaankekec/Documents/antigravity/adventurous-hypatia/shared/logo/\(name).png"
        ]
        for path in fallbackPaths {
            if FileManager.default.fileExists(atPath: path),
               let img = NSImage(contentsOfFile: path) {
                return img
            }
        }
        return nil
    }
}

private struct LogoDropletSpec {
    let normX: CGFloat
    let startNormY: CGFloat
    let endNormY: CGFloat
    let widthFactor: CGFloat
    let heightFactor: CGFloat
    let isTeardrop: Bool
    let period: Double
    let phaseOffset: Double
}

struct BloodyBrainLogoView: View {
    var size: CGFloat = 140
    var animated: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isPulsing = false

    private static let droplets: [LogoDropletSpec] = [
        // 1. Middle-left main teardrop below stem (canonical logo position: x=0.3922, y=0.8073)
        LogoDropletSpec(
            normX: 0.3922, startNormY: 0.7420, endNormY: 0.8950,
            widthFactor: 0.045, heightFactor: 0.064, isTeardrop: true,
            period: 1.95, phaseOffset: 0.00
        ),
        // 2. Center-right small droplet below long stem (canonical logo position: x=0.6310, y=0.8465)
        LogoDropletSpec(
            normX: 0.6310, startNormY: 0.8120, endNormY: 0.9150,
            widthFactor: 0.022, heightFactor: 0.022, isTeardrop: false,
            period: 1.75, phaseOffset: 0.32
        ),
        // 3. Right small droplet below stem (canonical logo position: x=0.6980, y=0.8019)
        LogoDropletSpec(
            normX: 0.6980, startNormY: 0.7620, endNormY: 0.8850,
            widthFactor: 0.024, heightFactor: 0.024, isTeardrop: false,
            period: 1.85, phaseOffset: 0.61
        ),
        // 4. Far-right small droplet below cerebellum (canonical logo position: x=0.7456, y=0.7384)
        LogoDropletSpec(
            normX: 0.7456, startNormY: 0.6920, endNormY: 0.8350,
            widthFactor: 0.022, heightFactor: 0.022, isTeardrop: false,
            period: 2.05, phaseOffset: 0.83
        ),
        // 5. Left bulb subtle secondary drip (below the large left hanging bulb at x=0.2650)
        LogoDropletSpec(
            normX: 0.2650, startNormY: 0.7180, endNormY: 0.8550,
            widthFactor: 0.026, heightFactor: 0.036, isTeardrop: true,
            period: 2.25, phaseOffset: 0.47
        )
    ]

    var body: some View {
        Group {
            if animated && !reduceMotion {
                animatedLogo
            } else if let fullImage = AppLogoAssets.squircleFull ?? NSApp?.applicationIconImage {
                logoImage(fullImage)
                    .shadow(color: .black.opacity(0.28), radius: size * 0.04, x: 0, y: size * 0.02)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private var animatedLogo: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 1.0, green: 0.16, blue: 0.18).opacity(isPulsing ? 0.52 : 0.28),
                            Color(red: 0.75, green: 0.04, blue: 0.10).opacity(isPulsing ? 0.20 : 0.10),
                            .clear
                        ],
                        center: .center,
                        startRadius: size * 0.15,
                        endRadius: size * 0.66
                    )
                )
                .frame(width: size * 1.24, height: size * 1.24)
                .scaleEffect(isPulsing ? 1.05 : 0.96)

            ZStack {
                if let baseImage = AppLogoAssets.squircleNoDrops ?? AppLogoAssets.squircleFull ?? NSApp?.applicationIconImage {
                    logoImage(baseImage)
                }

                if let neonImage = AppLogoAssets.brainNoDrops {
                    logoImage(neonImage)
                        .blendMode(.plusLighter)
                        .opacity(isPulsing ? 0.66 : 0.14)
                        .shadow(
                            color: Color(red: 1.0, green: 0.18, blue: 0.18).opacity(isPulsing ? 0.75 : 0.35),
                            radius: size * (isPulsing ? 0.05 : 0.025)
                        )
                }

                TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: false)) { timeline in
                    droplets(at: timeline.date.timeIntervalSinceReferenceDate)
                }
            }
            .scaleEffect(isPulsing ? 1.013 : 0.985)
            .shadow(color: .black.opacity(0.45), radius: size * 0.06, x: 0, y: size * 0.03)
        }
        .animation(.easeInOut(duration: 1.12).repeatForever(autoreverses: true), value: isPulsing)
        .onAppear { isPulsing = true }
        .onDisappear { isPulsing = false }
    }

    private func logoImage(_ image: NSImage) -> some View {
        Image(nsImage: image)
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
    }

    private func droplets(at t: TimeInterval) -> some View {
        Canvas { context, canvasSize in
            let w = canvasSize.width
            let h = canvasSize.height

            for drop in Self.droplets {
                let rawPhase = fmod(t / drop.period + drop.phaseOffset, 1.0)
                let phase = rawPhase < 0 ? rawPhase + 1.0 : rawPhase

                // Phase 0.0..0.20: swells at stem tip; 0.20..0.86: accelerates down; 0.86..1.0: fades out
                let fallProgress: CGFloat
                let scale: CGFloat
                let alpha: Double

                if phase < 0.20 {
                    let p = CGFloat(phase / 0.20)
                    fallProgress = p * 0.06
                    scale = 0.45 + 0.55 * p
                    alpha = Double(0.55 + 0.45 * p)
                } else if phase < 0.86 {
                    let p = CGFloat((phase - 0.20) / 0.66)
                    fallProgress = 0.06 + 0.94 * pow(p, 1.42)
                    scale = 1.0
                    alpha = 1.0
                } else {
                    let p = CGFloat((phase - 0.86) / 0.14)
                    fallProgress = 1.0 + 0.08 * p
                    scale = 1.0 - 0.25 * p
                    alpha = Double(1.0 - p)
                }

                let cx = w * drop.normX
                let cy = h * (drop.startNormY + (drop.endNormY - drop.startNormY) * fallProgress)
                let dw = w * drop.widthFactor * scale
                let dh = h * drop.heightFactor * scale

                let dropRect = CGRect(x: cx - dw * 0.5, y: cy - dh * 0.5, width: dw, height: dh)
                let path: Path
                if drop.isTeardrop {
                    path = BloodTeardropShape().path(in: dropRect)
                } else {
                    path = Path(ellipseIn: dropRect)
                }

                // Soft neon red halo around droplet
                let glowRect = dropRect.insetBy(dx: -dw * 0.35, dy: -dh * 0.35)
                let glowPath = Path(ellipseIn: glowRect)
                context.fill(
                    glowPath,
                    with: .radialGradient(
                        Gradient(colors: [
                            Color(red: 1.0, green: 0.18, blue: 0.18).opacity(alpha * 0.45),
                            Color.clear
                        ]),
                        center: CGPoint(x: cx, y: cy),
                        startRadius: 0,
                        endRadius: max(dw, dh) * 0.9
                    )
                )

                // Bright neon red droplet core matching the exact brain color (#FF2D2D)
                context.fill(
                    path,
                    with: .color(Color(red: 1.0, green: 0.176, blue: 0.176).opacity(alpha))
                )
            }
        }
        .frame(width: size, height: size)
        .allowsHitTesting(false)
    }
}

private struct BloodTeardropShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height
        path.move(to: CGPoint(x: w * 0.5, y: 0))
        path.addCurve(
            to: CGPoint(x: w * 0.5, y: h),
            control1: CGPoint(x: -w * 0.12, y: h * 0.58),
            control2: CGPoint(x: w * 0.12, y: h)
        )
        path.addCurve(
            to: CGPoint(x: w * 0.5, y: 0),
            control1: CGPoint(x: w * 0.88, y: h),
            control2: CGPoint(x: w * 1.12, y: h * 0.58)
        )
        path.closeSubpath()
        return path.applying(CGAffineTransform(translationX: rect.minX, y: rect.minY))
    }
}

struct SplashIntroView: View {
    let onFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false
    @State private var hasFinished = false

    var body: some View {
        ZStack {
            AppPalette.splashBackground
                .ignoresSafeArea()

            LinearGradient(
                colors: [
                    AppPalette.splashGradientTop,
                    AppPalette.splashGradientMiddle,
                    AppPalette.splashGradientBottom
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            RadialGradient(
                colors: [
                    Color(red: 1.0, green: 0.15, blue: 0.18).opacity(0.16),
                    Color(red: 0.55, green: 0.02, blue: 0.06).opacity(0.06),
                    .clear
                ],
                center: .center,
                startRadius: 20,
                endRadius: 340
            )
            .ignoresSafeArea()

            VStack(spacing: 26) {
                Spacer()

                BloodyBrainLogoView(size: 196, animated: !reduceMotion)
                    .scaleEffect(reduceMotion || hasAppeared ? 1 : 0.74)

                VStack(spacing: 10) {
                    Text("ARTIKELMEISTER")
                        .font(.system(size: 38, weight: .black, design: .rounded))
                        .tracking(3)
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    .white,
                                    Color(red: 1.0, green: 0.38, blue: 0.40),
                                    Color(red: 1.0, green: 0.16, blue: 0.18)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .accessibilityAddTraits(.isHeader)

                    Text(L10n.text(
                        "Almanca Artikel & Nöro-Hafıza Çalışma Sistemi",
                        en: "German Article & Neuro-Memory Study System",
                        de: "Deutsches Artikel- & Neuro-Gedächtnis-Lernsystem"
                    ))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.white.opacity(0.72))

                    HStack(spacing: 14) {
                        ForEach(Artikel.allCases) { artikel in
                            Text(artikel.rawValue)
                                .font(.caption.bold())
                                .foregroundStyle(AppPalette.splashArtikelColor(artikel))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(AppPalette.splashArtikelBadgeBackground(artikel), in: Capsule())
                        }
                    }
                    .padding(.top, 4)
                }
                .offset(y: reduceMotion || hasAppeared ? 0 : 18)
                .opacity(reduceMotion || hasAppeared ? 1 : 0)

                Spacer()
            }
            .padding(32)
        }
        .environment(\.colorScheme, .dark)
        .task(id: reduceMotion) {
            if !reduceMotion {
                withAnimation(.spring(response: 0.65, dampingFraction: 0.74)) {
                    hasAppeared = true
                }
                try? await Task.sleep(for: .milliseconds(1700))
            }
            guard !Task.isCancelled else { return }
            finish()
        }
    }

    private func finish() {
        guard !hasFinished else { return }
        hasFinished = true
        onFinished()
    }
}

enum AppTab: String, CaseIterable, Identifiable {
    case study = "study"
    case words = "words"
    case stats = "stats"
    case settings = "settings"

    var id: String { rawValue }

    var localizedTitle: String {
        switch self {
        case .study: return L10n.tabStudy()
        case .words: return L10n.tabWords()
        case .stats: return L10n.tabStats()
        case .settings: return L10n.tabSettings()
        }
    }

    var icon: String {
        switch self {
        case .study: return "rectangle.stack.fill"
        case .words: return "list.bullet.rectangle.portrait"
        case .stats: return "chart.bar.xaxis"
        case .settings: return "gearshape.fill"
        }
    }
}

struct MainContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    @EnvironmentObject var languageManager: LanguageManager
    @State private var selectedTab: AppTab = .study
    @State private var showNewDeckSheet: Bool = false
    @State private var deckToRename: String? = nil
    @State private var showManualSignInModal: Bool = false

    private var tabSelectionForCommands: Binding<AppTab>? {
        guard !showNewDeckSheet, deckToRename == nil, !showManualSignInModal else { return nil }
        return $selectedTab
    }

    var body: some View {
        HStack(spacing: 0) {
            // Left Sidebar
            VStack(alignment: .leading, spacing: 0) {
                // Sidebar Bloody Brain Emblem Header
                HStack(spacing: 10) {
                    BloodyBrainLogoView(size: 36, animated: false)
                    Text("ArtikelMeister")
                        .font(.headline.bold())
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)

                Divider()

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        // Section 1: Main Navigation Tabs
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L10n.menuSection)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.secondary)
                                .padding(.horizontal, 10)
                                .padding(.bottom, 2)

                            ForEach(AppTab.allCases) { tab in
                                let isSelected = (selectedTab == tab)
                                Button {
                                    selectedTab = tab
                                } label: {
                                    HStack(spacing: 9) {
                                        Image(systemName: tab.icon)
                                            .font(.subheadline)
                                            .foregroundColor(isSelected ? .white : .accentColor)
                                            .frame(width: 18)
                                        Text(tab.localizedTitle)
                                            .font(.subheadline.weight(isSelected ? .semibold : .medium))
                                            .foregroundColor(isSelected ? .white : .primary)
                                            .lineLimit(1)
                                        Spacer()
                                        if tab == .study && !store.studyQueue.isEmpty {
                                            Text("\(store.studyQueue.count)")
                                                .font(.caption2.bold())
                                                .padding(.horizontal, 7)
                                                .padding(.vertical, 2)
                                                .background(isSelected ? Color.white.opacity(0.24) : Color.accentColor.opacity(0.18))
                                                .foregroundColor(isSelected ? .white : .accentColor)
                                                .clipShape(Capsule())
                                        } else if tab == .words {
                                            Text("\(store.cards.count)")
                                                .font(.caption2)
                                                .foregroundColor(isSelected ? .white.opacity(0.85) : .secondary)
                                        }
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 7)
                                    .background(
                                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                                            .fill(
                                                isSelected
                                                    ? AnyShapeStyle(
                                                        LinearGradient(
                                                            colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                                                            startPoint: .topLeading,
                                                            endPoint: .bottomTrailing
                                                        )
                                                    )
                                                    : AnyShapeStyle(Color.clear)
                                            )
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                                            .strokeBorder(
                                                isSelected ? Color.white.opacity(0.22) : Color.clear,
                                                lineWidth: 0.75
                                            )
                                    )
                                    .shadow(color: isSelected ? Color.accentColor.opacity(0.22) : .clear, radius: 4, y: 1)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        Divider()
                            .padding(.horizontal, 6)

                        // Section 2: Decks List + "+ Yeni Deste Ekle" Button Right Underneath
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(L10n.decksSection)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text("\(store.allDeckNames.count)")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.horizontal, 10)
                            .padding(.bottom, 2)

                            // All Decks Row
                            let allSelected = (store.selectedStudyDeck == nil)
                            Button {
                                store.selectedStudyDeck = nil
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: allSelected ? "tray.full.fill" : "tray.full")
                                        .accessibilityHidden(true)
                                        .font(.caption)
                                        .foregroundColor(allSelected ? .accentColor : .secondary)
                                        .frame(width: 16)
                                    Text(L10n.allDecks)
                                        .font(.caption.weight(allSelected ? .bold : .medium))
                                        .foregroundColor(allSelected ? .accentColor : .primary)
                                        .lineLimit(1)
                                    Spacer()
                                    Text("\(store.cards.count)")
                                        .font(.caption2.monospacedDigit())
                                        .foregroundColor(.secondary)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background {
                                    if allSelected {
                                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            .fill(.thinMaterial)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                    .fill(Color.accentColor.opacity(0.14))
                                            )
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                    .strokeBorder(Color.accentColor.opacity(0.32), lineWidth: 0.75)
                                            )
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(allSelected ? .isSelected : [])

                            // Individual Decks
                            ForEach(store.allDeckNames, id: \.self) { deck in
                                let isDeckSelected = (store.selectedStudyDeck == deck)
                                Button {
                                    store.selectedStudyDeck = deck
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: isDeckSelected ? "folder.fill" : "folder")
                                            .accessibilityHidden(true)
                                            .font(.caption)
                                            .foregroundColor(isDeckSelected ? .accentColor : .secondary)
                                            .frame(width: 16)
                                        Text(deck)
                                            .font(.caption.weight(isDeckSelected ? .bold : .medium))
                                            .foregroundColor(isDeckSelected ? .accentColor : .primary)
                                            .lineLimit(1)
                                        Spacer()
                                        Text("\(store.cardCount(forDeck: deck))")
                                            .font(.caption2.monospacedDigit())
                                            .foregroundColor(.secondary)
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background {
                                        if isDeckSelected {
                                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                .fill(.thinMaterial)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                        .fill(Color.accentColor.opacity(0.14))
                                                )
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                        .strokeBorder(Color.accentColor.opacity(0.32), lineWidth: 0.75)
                                                )
                                        }
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(isDeckSelected ? .isSelected : [])
                                .contextMenu {
                                    Button {
                                        deckToRename = deck
                                    } label: {
                                        Label(L10n.text("Deste Adını Değiştir...", en: "Rename Deck...", de: "Stapel umbenennen..."), systemImage: "pencil")
                                    }
                                    if deck != "Genel" {
                                        Divider()
                                        Button(role: .destructive) {
                                            store.deleteDeck(name: deck, deleteWords: false)
                                        } label: {
                                            Label(L10n.text("Desteyi Sil (Kelimeleri 'Genel'e Taşı)", en: "Delete Deck (Move Words to 'Genel')", de: "Stapel löschen (Wörter nach ‚Genel‘ verschieben)"), systemImage: "folder.badge.minus")
                                        }
                                    }
                                }
                            }

                            // Prominent "+ Yeni Deste Ekle" button right below the decks list
                            Button {
                                showNewDeckSheet = true
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.caption)
                                    Text(L10n.newDeckAdd)
                                        .font(.caption.weight(.semibold))
                                        .lineLimit(1)
                                    Spacer()
                                }
                                .foregroundColor(.accentColor)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .fill(Color.accentColor.opacity(0.12))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .stroke(Color.accentColor.opacity(0.3), lineWidth: 1)
                                )
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .padding(.top, 4)
                        }
                    }
                    .padding(10)
                }

                Divider()

                // Universal Google Profile & Cloud Sync Status Card in Sidebar
                UniversalProfileSidebarCard(
                    store: store,
                    authManager: authManager,
                    onShowSignInDialog: {
                        showManualSignInModal = true
                    }
                )
                .padding(.horizontal, 10)
                .padding(.vertical, 5)

                // Animated Dark / Light Theme Toggle Card
                AnimatedThemeToggleCard(
                    isDarkMode: colorScheme == .dark,
                    onToggleTheme: {
                        // Flip the look that is on screen now, even when the mode is "system".
                        withAnimation(.easeInOut(duration: 0.3)) {
                            store.themeMode = (colorScheme == .dark) ? .light : .dark
                        }
                    }
                )
                .padding(.horizontal, 10)
                .padding(.bottom, 6)

                Divider()

                // Bottom Legend
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        Circle().fill(Artikel.der.color).frame(width: 7, height: 7)
                        Text("der").font(.caption2.bold())
                    }
                    HStack(spacing: 4) {
                        Circle().fill(Artikel.die.color).frame(width: 7, height: 7)
                        Text("die").font(.caption2.bold())
                    }
                    HStack(spacing: 4) {
                        Circle().fill(Artikel.das.color).frame(width: 7, height: 7)
                        Text("das").font(.caption2.bold())
                    }
                }
                .foregroundColor(.secondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .frame(width: 224)
            .background(.ultraThinMaterial)

            Divider()

            // Right Detail Area
            Group {
                switch selectedTab {
                case .study:
                    StudyView()
                case .words:
                    WordListView()
                case .stats:
                    StatsView()
                case .settings:
                    SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(minWidth: 920, minHeight: 600)
        .focusedSceneValue(\.appTabSelection, tabSelectionForCommands)
        .onAppear {
            if authManager.isGoogleAccountConnected {
                Task {
                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                }
            }
        }
        .onChange(of: authManager.isGoogleAccountConnected) { _, connected in
            if connected {
                Task {
                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                }
            }
        }
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { createdDeck in
                store.selectedStudyDeck = createdDeck
            }
            .environmentObject(store)
            .environmentObject(authManager)
        }
        .sheet(item: Binding(
            get: { deckToRename.map { IdentifiableDeckName(name: $0) } },
            set: { deckToRename = $0?.name }
        )) { item in
            RenameDeckSheet(oldName: item.name)
                .environmentObject(store)
                .environmentObject(authManager)
        }
        .sheet(isPresented: $showManualSignInModal) {
            OnboardingSetupScreen {
                showManualSignInModal = false
                authManager.completeOnboarding()
            }
            .environmentObject(store)
            .environmentObject(authManager)
        }
    }
}

// MARK: - Universal Profile Sidebar Card
struct UniversalProfileSidebarCard: View {
    @ObservedObject var store: DeckStore
    @ObservedObject var authManager: GeminiAuthManager
    let onShowSignInDialog: () -> Void

    var isConnected: Bool {
        authManager.isGoogleAccountConnected
    }

    var body: some View {
        Button {
            if isConnected {
                Task {
                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                }
            } else {
                onShowSignInDialog()
            }
        } label: {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(isConnected ? Color.green.opacity(0.16) : Color.blue.opacity(0.14))
                        .frame(width: 28, height: 28)

                    if store.isSyncingCloud {
                        ProgressView()
                            .scaleEffect(0.6)
                            .frame(width: 14, height: 14)
                    } else {
                        Image(systemName: isConnected ? "checkmark.icloud.fill" : "person.crop.circle")
                            .font(.system(size: 14))
                            .foregroundColor(isConnected ? .green : .blue)
                    }
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(
                        isConnected
                            ? (!authManager.userEmail.isEmpty ? authManager.userEmail : (!authManager.userName.isEmpty ? authManager.userName : L10n.text("Google Hesabı Bağlı", en: "Google Account Connected", de: "Google-Konto verbunden")))
                            : L10n.text("Google ile Giriş Yap", en: "Sign in with Google", de: "Mit Google anmelden")
                    )
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                    Text(
                        store.isSyncingCloud
                            ? L10n.text("Drive ile eşitleniyor...", en: "Syncing with Drive...", de: "Synchronisiere mit Drive...")
                            : (isConnected ? L10n.text("Eşitlemek için tıkla", en: "Click to sync", de: "Zum Synchronisieren klicken") : L10n.text("Google ile giriş yap", en: "Sign in with Google", de: "Mit Google anmelden"))
                    )
                    .font(.system(size: 9.5))
                    .foregroundColor(isConnected ? .green : .secondary)
                    .lineLimit(1)
                }

                Spacer(minLength: 0)

                if isConnected && !store.isSyncingCloud {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(.regularMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.75)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Animated Theme Toggle Card
struct AnimatedThemeToggleCard: View {
    let isDarkMode: Bool
    let onToggleTheme: () -> Void

    var body: some View {
        Button {
            onToggleTheme()
        } label: {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(isDarkMode ? Color.purple.opacity(0.18) : Color.orange.opacity(0.18))
                        .frame(width: 28, height: 28)

                    // Both symbols stay in the tree so they can rotate/scale/fade across each other.
                    Image(systemName: "moon.stars.fill")
                        .font(.system(size: 13))
                        .foregroundColor(.purple)
                        .opacity(isDarkMode ? 1 : 0)
                        .scaleEffect(isDarkMode ? 1 : 0.3)
                        .rotationEffect(.degrees(isDarkMode ? 0 : -90))

                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 13))
                        .foregroundColor(.orange)
                        .opacity(isDarkMode ? 0 : 1)
                        .scaleEffect(isDarkMode ? 0.3 : 1)
                        .rotationEffect(.degrees(isDarkMode ? 90 : 0))
                }

                Text(isDarkMode ? L10n.text("Karanlık Mod", en: "Dark Mode", de: "Dunkelmodus") : L10n.text("Aydınlık Mod", en: "Light Mode", de: "Hellmodus"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .id("title-\(isDarkMode)")
                    .transition(.opacity)

                Spacer(minLength: 0)

                // Custom Pill Toggle
                ZStack(alignment: isDarkMode ? .trailing : .leading) {
                    Capsule()
                        .fill(isDarkMode ? Color.purple.opacity(0.25) : Color.orange.opacity(0.22))
                        .frame(width: 36, height: 20)
                        .overlay(
                            Capsule()
                                .stroke(isDarkMode ? Color.purple.opacity(0.5) : Color.orange.opacity(0.5), lineWidth: 1)
                        )

                    Circle()
                        .fill(isDarkMode ? Color.purple : Color.orange)
                        .frame(width: 16, height: 16)
                        .padding(.horizontal, 2)
                        .shadow(radius: 1)
                }
                .animation(.spring(response: 0.28, dampingFraction: 0.7), value: isDarkMode)
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.75), value: isDarkMode)
            .padding(.horizontal, 8)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(.regularMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.75)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Onboarding Feature Row
struct OnboardingFeatureRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let desc: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle()
                    .fill(iconColor.opacity(0.14))
                    .frame(width: 32, height: 32)
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(iconColor)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.primary)
                Text(desc)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Onboarding Setup Screen
struct OnboardingSetupScreen: View {
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    let onComplete: () -> Void

    @State private var errorMessage: String? = nil

    var body: some View {
        ZStack {
            Color(NSColor.windowBackgroundColor)
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    Spacer(minLength: 16)

                    BloodyBrainLogoView(size: 84, animated: true)

                    Text("ArtikelMeister")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)

                    // Card with 3 features
                    VStack(alignment: .leading, spacing: 16) {
                        Text(L10n.text("Kurulum ve Bulut Senkronizasyonu", en: "Setup & Cloud Sync", de: "Einrichtung & Cloud-Synchronisierung"))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.primary)

                        Divider()

                        OnboardingFeatureRow(
                            icon: "arrow.triangle.2.circlepath.icloud.fill",
                            iconColor: .blue,
                            title: L10n.text("Bulut Senkronizasyonu", en: "Cloud Sync", de: "Cloud-Synchronisierung"),
                            desc: L10n.text("Kelimeleriniz, seriniz (streak) ve öğrenme geçmişiniz tüm cihazlarınız arasında otomatik eşitlenir.", en: "Your words, streak, and study history sync automatically across all your devices.", de: "Deine Wörter, deine Serie und dein Lernverlauf werden automatisch auf allen Geräten synchronisiert.")
                        )

                        OnboardingFeatureRow(
                            icon: "checkmark.circle.fill",
                            iconColor: .green,
                            title: L10n.text("Kelimeleri Otomatik Birleştirme", en: "Automatic Word Merging", de: "Automatisches Zusammenführen"),
                            desc: L10n.text("Farklı cihazlardan eklenen kelimeler mükerrer kaydedilmeden birleştirilir ve güncel öğrenme ilerlemeniz korunur.", en: "Words added from different devices are merged without duplicates while preserving your latest study progress.", de: "Wörter von verschiedenen Geräten werden ohne Duplikate zusammengeführt und dein aktueller Lernfortschritt bleibt erhalten.")
                        )

                        OnboardingFeatureRow(
                            icon: "sparkles",
                            iconColor: .purple,
                            title: L10n.text("Yapay Zeka ile Kelime Üretimi", en: "AI Word Generation", de: "KI-Wortgenerierung"),
                            desc: L10n.text("Almanca seviyenize uygun yeni kelimeleri yapay zeka ile anında üretip çalışabilirsiniz.", en: "Instantly generate and study new words tailored to your German level with AI.", de: "Generiere und lerne sofort neue Wörter passend zu deinem Deutschniveau mit KI.")
                        )
                    }
                    .padding(20)
                    .frame(maxWidth: 480, alignment: .leading)
                    .background(Color(NSColor.controlBackgroundColor))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                    )

                    if let err = errorMessage {
                        HStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(err)
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                        }
                        .padding(12)
                        .frame(maxWidth: 480, alignment: .leading)
                        .background(Color.red.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(Color.red.opacity(0.3), lineWidth: 1)
                        )
                    }

                    if authManager.isAuthenticating || store.isSyncingCloud {
                        HStack(spacing: 10) {
                            ProgressView()
                                .scaleEffect(0.8)
                                .frame(width: 18, height: 18)
                            Text(
                                store.isSyncingCloud
                                    ? L10n.text("Verileriniz bulut ile eşitleniyor...", en: "Syncing your data with the cloud...", de: "Daten werden mit der Cloud synchronisiert...")
                                    : (authManager.authStatusMessage ?? L10n.text("Google ile giriş yapılıyor...", en: "Signing in with Google...", de: "Anmeldung bei Google läuft..."))
                            )
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                    }

                    VStack(spacing: 10) {
                        Button {
                            errorMessage = nil
                            Task {
                                do {
                                    try await authManager.startBrowserOAuthFlow()
                                    await store.syncWithGoogleDrive(authManager: authManager, isSilentAutoSync: false)
                                    onComplete()
                                } catch {
                                    errorMessage = error.localizedDescription
                                }
                            }
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "person.crop.circle")
                                    .font(.system(size: 16))
                                Text(L10n.text("Google Hesabı ile Giriş Yap & Eşitle", en: "Sign in with Google & Sync", de: "Mit Google anmelden & synchronisieren"))
                                    .font(.system(size: 14, weight: .bold))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.blue)
                        .disabled(authManager.isAuthenticating || store.isSyncingCloud)

                        Button {
                            onComplete()
                        } label: {
                            Text(L10n.text("Çevrimdışı Modda Devam Et", en: "Continue Offline", de: "Offline fortfahren"))
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.secondary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 32)
                        }
                        .buttonStyle(.bordered)
                        .disabled(authManager.isAuthenticating || store.isSyncingCloud)
                    }
                    .frame(maxWidth: 480)

                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 24)
                .frame(maxWidth: .infinity)
            }
        }
        .frame(minWidth: 540, minHeight: 620)
    }
}

// MARK: - App Version Manager & Update Gate

@MainActor
final class AppVersionManager: ObservableObject {
    static let shared = AppVersionManager()

    @Published var requiredUpdate: ReleaseUpdateInfo? = nil
    @Published var isChecking: Bool = false

    let currentVersion: String

    private let defaults = UserDefaults.standard
    private let lastCheckKey = "com.artikelmeister.version.lastCheckAt"
    private let cachedTagKey = "com.artikelmeister.version.cachedRequiredTag"
    private let cachedUrlKey = "com.artikelmeister.version.cachedRequiredUrl"
    private let throttleInterval: TimeInterval = 2 * 3600

    init() {
        let bundleVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        let cleaned = bundleVersion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.currentVersion = cleaned.isEmpty ? "1.3.0" : cleaned
        restoreStickyUpdateIfNeeded()
    }

    private func restoreStickyUpdateIfNeeded() {
        guard let cachedTag = defaults.string(forKey: cachedTagKey),
              !cachedTag.isEmpty else {
            return
        }
        if VersionChecker.isUpdateRequired(currentVersion: currentVersion, latestTag: cachedTag),
           let ver = SemVer.parse(cachedTag) {
            let rawUrl = defaults.string(forKey: cachedUrlKey) ?? VersionChecker.fallbackReleaseUrl
            let safeUrl = rawUrl.hasPrefix(VersionChecker.repoReleasePrefix)
                ? rawUrl
                : VersionChecker.fallbackReleaseUrl
            requiredUpdate = ReleaseUpdateInfo(tagName: cachedTag, version: ver, htmlUrl: safeUrl)
        } else {
            clearStickyUpdate()
        }
    }

    private func saveStickyUpdate(_ info: ReleaseUpdateInfo) {
        defaults.set(info.tagName, forKey: cachedTagKey)
        defaults.set(info.htmlUrl, forKey: cachedUrlKey)
    }

    private func clearStickyUpdate() {
        defaults.removeObject(forKey: cachedTagKey)
        defaults.removeObject(forKey: cachedUrlKey)
    }

    func checkForUpdate(force: Bool = false) async {
        guard !isChecking else { return }

        if !force {
            let lastCheck = defaults.double(forKey: lastCheckKey)
            if lastCheck > 0, Date().timeIntervalSince1970 - lastCheck < throttleInterval {
                return
            }
        }

        guard let url = URL(string: VersionChecker.latestReleaseEndpoint) else { return }

        isChecking = true
        defer { isChecking = false }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 8
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("ArtikelMeister/\(currentVersion)", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
                  let jsonString = String(data: data, encoding: .utf8),
                  let info = VersionChecker.parseLatestReleaseJson(jsonString) else {
                // Fail-open on HTTP/rate-limit/parse errors
                return
            }

            defaults.set(Date().timeIntervalSince1970, forKey: lastCheckKey)

            if VersionChecker.isUpdateRequired(currentVersion: currentVersion, latestTag: info.tagName) {
                saveStickyUpdate(info)
                requiredUpdate = info
            } else {
                clearStickyUpdate()
                requiredUpdate = nil
            }
        } catch {
            // Fail-open on offline/timeout/network errors
        }
    }
}

private struct UpdateRequiredGateView: View {
    let update: ReleaseUpdateInfo
    let currentVersion: String
    let isChecking: Bool
    let onUpdate: () -> Void
    let onRecheck: () -> Void

    var body: some View {
        ZStack {
            Color(NSColor.windowBackgroundColor)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                ZStack {
                    Circle()
                        .fill(AppPalette.accentBlue.opacity(0.14))
                        .frame(width: 72, height: 72)
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(AppPalette.accentBlue)
                }

                VStack(spacing: 8) {
                    Text(L10n.updateRequiredTitle())
                        .font(.title.bold())
                    Text(L10n.updateRequiredMessage(update.tagName))
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }

                HStack(spacing: 16) {
                    Label(L10n.currentVersionLabel(currentVersion), systemImage: "app.badge")
                    Label(L10n.latestVersionLabel(update.tagName), systemImage: "sparkles")
                        .foregroundStyle(AppPalette.accentBlue)
                }
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(.regularMaterial, in: Capsule())
                .overlay(
                    Capsule().strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                )

                VStack(spacing: 10) {
                    Button(action: onUpdate) {
                        Label(L10n.updateButton(), systemImage: "arrow.up.right.square.fill")
                            .font(.headline)
                            .foregroundStyle(AppPalette.onBlue)
                            .frame(width: 240, height: 34)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppPalette.accentBlue)

                    Button(action: onRecheck) {
                        HStack(spacing: 6) {
                            if isChecking {
                                ProgressView()
                                    .controlSize(.small)
                            } else {
                                Image(systemName: "arrow.clockwise")
                            }
                            Text(isChecking ? L10n.checkingUpdate : L10n.recheckButton())
                        }
                        .frame(width: 240, height: 28)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isChecking)
                }
            }
            .padding(32)
        }
    }
}

// MARK: - Root Container View
struct RootContainerView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    @EnvironmentObject var languageManager: LanguageManager
    @StateObject private var versionManager = AppVersionManager.shared
    @State private var showSplash: Bool = true

    var shouldShowOnboarding: Bool {
        !authManager.hasCompletedOnboarding && !authManager.isGoogleAccountConnected
    }

    var body: some View {
        ZStack {
            if let update = versionManager.requiredUpdate, !showSplash {
                UpdateRequiredGateView(
                    update: update,
                    currentVersion: versionManager.currentVersion,
                    isChecking: versionManager.isChecking,
                    onUpdate: {
                        if let url = URL(string: update.htmlUrl) {
                            NSWorkspace.shared.open(url)
                        }
                    },
                    onRecheck: {
                        Task {
                            await versionManager.checkForUpdate(force: true)
                        }
                    }
                )
                .transition(.opacity)
                .zIndex(20)
            } else if showSplash {
                SplashIntroView {
                    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.45)) {
                        showSplash = false
                    }
                }
                .transition(reduceMotion ? .identity : .opacity)
                .zIndex(10)
            } else if shouldShowOnboarding {
                OnboardingSetupScreen {
                    withAnimation(.easeInOut(duration: 0.35)) {
                        authManager.completeOnboarding()
                    }
                }
                .transition(.opacity)
            } else {
                MainContentView()
                    .transition(.opacity)
            }
        }
        .frame(minWidth: 920, minHeight: 600)
        .preferredColorScheme(store.themeMode.colorScheme)
        .environment(\.locale, languageManager.activeLocale)
        .id(languageManager.resolvedLanguage)
        .animation(.easeInOut(duration: 0.3), value: store.themeMode)
        .task {
            await versionManager.checkForUpdate(force: false)
        }
    }
}
