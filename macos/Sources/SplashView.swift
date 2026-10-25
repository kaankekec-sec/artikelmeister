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
        return NSApp.applicationIconImage
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
        if animated {
            TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: false)) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                let pulse = 0.5 + 0.5 * sin(t * 2.8)
                let secondaryPulse = 0.5 + 0.5 * cos(t * 4.2)

                ZStack {
                    // 1. Breathing neon crimson outer aura behind the dark squircle
                    RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
                        .fill(
                            RadialGradient(
                                colors: [
                                    Color(red: 1.0, green: 0.16, blue: 0.18).opacity(0.28 + 0.24 * pulse),
                                    Color(red: 0.75, green: 0.04, blue: 0.10).opacity(0.10 + 0.10 * pulse),
                                    Color.clear
                                ],
                                center: .center,
                                startRadius: size * 0.15,
                                endRadius: size * 0.66
                            )
                        )
                        .frame(width: size * 1.24, height: size * 1.24)
                        .scaleEffect(0.96 + 0.09 * pulse)

                    // 2. Exact dark-slate squircle & neon brain base (with the 4 detached drops separated for live animation)
                    ZStack {
                        if let baseImg = AppLogoAssets.squircleNoDrops ?? AppLogoAssets.squircleFull {
                            Image(nsImage: baseImg)
                                .resizable()
                                .interpolation(.high)
                                .scaledToFit()
                                .frame(width: size, height: size)
                        }

                        // 3. Pulsing neon-red cortex & stems glow overlay (exact pixel form of the brain)
                        if let neonBrainImg = AppLogoAssets.brainNoDrops {
                            Image(nsImage: neonBrainImg)
                                .resizable()
                                .interpolation(.high)
                                .scaledToFit()
                                .frame(width: size, height: size)
                                .blendMode(.plusLighter)
                                .opacity(0.14 + 0.52 * pulse)
                                .shadow(
                                    color: Color(red: 1.0, green: 0.18, blue: 0.18).opacity(0.35 + 0.40 * secondaryPulse),
                                    radius: size * (0.025 + 0.025 * pulse)
                                )
                        }

                        // 4. Live animated blood droplets dripping from the exact stem tips of the logo
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
                    .scaleEffect(0.985 + 0.028 * pulse)
                    .shadow(
                        color: Color.black.opacity(0.45),
                        radius: size * 0.06,
                        x: 0,
                        y: size * 0.03
                    )
                }
                .frame(width: size, height: size)
            }
        } else {
            ZStack {
                if let fullImg = AppLogoAssets.squircleFull {
                    Image(nsImage: fullImg)
                        .resizable()
                        .interpolation(.high)
                        .scaledToFit()
                        .frame(width: size, height: size)
                        .shadow(color: Color.black.opacity(0.28), radius: size * 0.04, x: 0, y: size * 0.02)
                }
            }
            .frame(width: size, height: size)
        }
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
        return path
    }
}

struct SplashIntroView: View {
    let onFinished: () -> Void

    @State private var appearScale: CGFloat = 0.74
    @State private var appearOpacity: Double = 0.0
    @State private var textOffset: CGFloat = 18
    @State private var progressValue: CGFloat = 0.0
    @State private var ringScale: CGFloat = 0.82

    var body: some View {
        ZStack {
            // Deep dark slate-obsidian background matching the new logo squircle
            LinearGradient(
                colors: [
                    Color(red: 0.08, green: 0.14, blue: 0.17),
                    Color(red: 0.03, green: 0.06, blue: 0.11),
                    Color(red: 0.01, green: 0.02, blue: 0.05)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Subtle ambient neon red radial backdrop glow
            RadialGradient(
                colors: [
                    Color(red: 1.0, green: 0.15, blue: 0.18).opacity(0.16 * appearOpacity),
                    Color(red: 0.55, green: 0.02, blue: 0.06).opacity(0.06 * appearOpacity),
                    Color.clear
                ],
                center: .center,
                startRadius: 20,
                endRadius: 340
            )
            .scaleEffect(ringScale)
            .ignoresSafeArea()

            VStack(spacing: 26) {
                Spacer()

                BloodyBrainLogoView(size: 196, animated: true)
                    .scaleEffect(appearScale)
                    .opacity(appearOpacity)

                VStack(spacing: 10) {
                    Text("ARTIKELMEISTER")
                        .font(.system(size: 38, weight: .black, design: .rounded))
                        .tracking(3)
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    Color.white,
                                    Color(red: 1.0, green: 0.38, blue: 0.40),
                                    Color(red: 1.0, green: 0.16, blue: 0.18)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    Text("Almanca Artikel & Nöro-Hafıza Çalışma Sistemi")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(Color.white.opacity(0.72))

                    HStack(spacing: 14) {
                        Text("der")
                            .font(.caption.bold())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Artikel.der.color.opacity(0.25))
                            .foregroundColor(Artikel.der.color)
                            .clipShape(Capsule())

                        Text("die")
                            .font(.caption.bold())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Artikel.die.color.opacity(0.25))
                            .foregroundColor(Artikel.die.color)
                            .clipShape(Capsule())

                        Text("das")
                            .font(.caption.bold())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Artikel.das.color.opacity(0.25))
                            .foregroundColor(Artikel.das.color)
                            .clipShape(Capsule())
                    }
                    .padding(.top, 4)
                }
                .offset(y: textOffset)
                .opacity(appearOpacity)

                // Neon crimson loading progress bar
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.white.opacity(0.12))
                        .frame(width: 240, height: 6)

                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 1.0, green: 0.18, blue: 0.18),
                                    Color(red: 0.78, green: 0.04, blue: 0.10)
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: 240 * progressValue, height: 6)
                        .shadow(color: Color(red: 1.0, green: 0.18, blue: 0.18).opacity(0.6), radius: 6)
                }
                .padding(.top, 12)
                .opacity(appearOpacity)

                Spacer()

                Text("Geçmek için tıklayın")
                    .font(.caption2)
                    .foregroundColor(Color.white.opacity(0.38))
                    .padding(.bottom, 16)
            }
            .padding(32)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onFinished()
        }
        .onAppear {
            withAnimation(.spring(response: 0.65, dampingFraction: 0.74)) {
                appearScale = 1.0
                appearOpacity = 1.0
                textOffset = 0
            }
            withAnimation(.easeInOut(duration: 1.3).repeatForever(autoreverses: true)) {
                ringScale = 1.12
            }
            withAnimation(.easeInOut(duration: 2.1)) {
                progressValue = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.35) {
                onFinished()
            }
        }
    }
}

enum AppTab: String, CaseIterable, Identifiable {
    case study = "Çalış"
    case words = "Kelime Havuzu"
    case stats = "İstatistikler"
    case settings = "Ayarlar"

    var id: String { rawValue }

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
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    @State private var selectedTab: AppTab = .study
    @State private var showNewDeckSheet: Bool = false
    @State private var deckToRename: String? = nil
    @State private var showManualSignInModal: Bool = false

    var body: some View {
        HStack(spacing: 0) {
            // Left Sidebar
            VStack(alignment: .leading, spacing: 0) {
                // Sidebar Bloody Brain Emblem Header
                HStack(spacing: 10) {
                    BloodyBrainLogoView(size: 40, animated: false)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("ArtikelMeister")
                            .font(.headline.bold())
                            .lineLimit(1)
                        Text("Nöro-Hafıza")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)

                Divider()

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        // Section 1: Main Navigation Tabs
                        VStack(alignment: .leading, spacing: 4) {
                            Text("MENÜ")
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
                                        Text(tab.rawValue)
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
                                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            .fill(isSelected ? Color.accentColor : Color.clear)
                                    )
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
                                Text("DESTELER")
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
                                        .font(.caption)
                                        .foregroundColor(allSelected ? .accentColor : .secondary)
                                        .frame(width: 16)
                                    Text("Tüm Desteler")
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
                                .background(
                                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                                        .fill(allSelected ? Color.accentColor.opacity(0.14) : Color.clear)
                                )
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)

                            // Individual Decks
                            ForEach(store.allDeckNames, id: \.self) { deck in
                                let isDeckSelected = (store.selectedStudyDeck == deck)
                                Button {
                                    store.selectedStudyDeck = deck
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: isDeckSelected ? "folder.fill" : "folder")
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
                                    .background(
                                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                                            .fill(isDeckSelected ? Color.accentColor.opacity(0.14) : Color.clear)
                                    )
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button {
                                        deckToRename = deck
                                    } label: {
                                        Label("Deste Adını Değiştir...", systemImage: "pencil")
                                    }
                                    if deck != "Genel" {
                                        Divider()
                                        Button(role: .destructive) {
                                            store.deleteDeck(name: deck, deleteWords: false)
                                        } label: {
                                            Label("Desteyi Sil (Kelimeleri 'Genel'e Taşı)", systemImage: "folder.badge.minus")
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
                                    Text("Yeni Deste Ekle")
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
                    isDarkMode: store.settings.isDarkMode,
                    onToggleTheme: {
                        store.settings.isDarkMode.toggle()
                        store.saveSettings()
                    }
                )
                .padding(.horizontal, 10)
                .padding(.bottom, 6)

                Divider()

                // Bottom Legend
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 6) {
                        Circle().fill(Artikel.der.color).frame(width: 7, height: 7)
                        Text("der").font(.caption2.bold())
                        Circle().fill(Artikel.die.color).frame(width: 7, height: 7)
                        Text("die").font(.caption2.bold())
                        Circle().fill(Artikel.das.color).frame(width: 7, height: 7)
                        Text("das").font(.caption2.bold())
                    }
                    .foregroundColor(.secondary)
                    Text(authManager.isGoogleAccountConnected ? "Google Drive & Yerel Depolama Aktif" : "Yerel Depolama Aktif")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(12)
            }
            .frame(width: 220)
            .background(Color(NSColor.controlBackgroundColor))

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
                            ? (!authManager.userEmail.isEmpty ? authManager.userEmail : (!authManager.userName.isEmpty ? authManager.userName : "Google Hesabı Bağlı"))
                            : "Google ile Giriş Yap"
                    )
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                    Text(
                        store.isSyncingCloud
                            ? "Drive ile eşitleniyor..."
                            : (isConnected ? "Drive Eşitleme & AI Aktif" : "Bulut Eşitleme & AI için dokun")
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
                    .fill(Color(NSColor.textBackgroundColor))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 1)
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

                    Image(systemName: isDarkMode ? "moon.stars.fill" : "sun.max.fill")
                        .font(.system(size: 13))
                        .foregroundColor(isDarkMode ? .purple : .orange)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(isDarkMode ? "Karanlık Mod" : "Aydınlık Mod")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    Text(isDarkMode ? "Aydınlık temaya geç" : "Karanlık temaya geç")
                        .font(.system(size: 9.5))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }

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
            .padding(.horizontal, 8)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(NSColor.textBackgroundColor))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 1)
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

                    VStack(spacing: 6) {
                        Text("ArtikelMeister")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(.primary)

                        Text("Almanca Artikel & Hafıza Çalışma Sistemi")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }

                    // Card with 3 features
                    VStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Kurulum ve Bulut Senkronizasyonu")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.primary)

                            Text("Google hesabınızla bağlanarak kelimelerinizi ve çalışma serinizi tüm cihazlarınızla eşitleyebilir veya uygulamayı doğrudan çevrimdışı modda kullanabilirsiniz.")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                                .lineSpacing(2)
                        }

                        Divider()

                        OnboardingFeatureRow(
                            icon: "arrow.triangle.2.circlepath.icloud.fill",
                            iconColor: .blue,
                            title: "Bulut Senkronizasyonu",
                            desc: "Kelimeleriniz, seriniz (streak) ve öğrenme geçmişiniz tüm cihazlarınız arasında otomatik eşitlenir."
                        )

                        OnboardingFeatureRow(
                            icon: "checkmark.circle.fill",
                            iconColor: .green,
                            title: "Kelimeleri Otomatik Birleştirme",
                            desc: "Farklı cihazlardan eklenen kelimeler mükerrer kaydedilmeden birleştirilir ve güncel öğrenme ilerlemeniz korunur."
                        )

                        OnboardingFeatureRow(
                            icon: "sparkles",
                            iconColor: .purple,
                            title: "Yapay Zeka ile Kelime Üretimi",
                            desc: "Almanca seviyenize uygun yeni kelimeleri yapay zeka ile anında üretip çalışabilirsiniz."
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
                                    ? "Verileriniz bulut ile eşitleniyor..."
                                    : (authManager.authStatusMessage ?? "Google ile giriş yapılıyor...")
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
                                Text("Google Hesabı ile Giriş Yap & Eşitle")
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
                            Text("Çevrimdışı Modda Devam Et")
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

// MARK: - Root Container View
struct RootContainerView: View {
    @EnvironmentObject var store: DeckStore
    @EnvironmentObject var authManager: GeminiAuthManager
    @State private var showSplash: Bool = true

    var shouldShowOnboarding: Bool {
        !authManager.hasCompletedOnboarding && !authManager.isGoogleAccountConnected
    }

    var body: some View {
        ZStack {
            if showSplash {
                SplashIntroView {
                    withAnimation(.easeInOut(duration: 0.45)) {
                        showSplash = false
                    }
                }
                .transition(.opacity)
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
        .preferredColorScheme(store.settings.isDarkMode ? .dark : .light)
    }
}
