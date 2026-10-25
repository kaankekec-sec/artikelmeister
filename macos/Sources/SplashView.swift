import SwiftUI
import AppKit

struct BloodyBrainLogoView: View {
    var size: CGFloat = 140
    var animated: Bool = true

    @State private var pulse: Bool = false
    @State private var dripOffset1: CGFloat = 0
    @State private var dripOffset2: CGFloat = 0
    @State private var dripOpacity1: Double = 1.0
    @State private var dripOpacity2: Double = 1.0

    var body: some View {
        ZStack {
            // Outer crimson aura
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color(red: 0.90, green: 0.04, blue: 0.12).opacity(animated && pulse ? 0.55 : 0.30),
                            Color(red: 0.45, green: 0.01, blue: 0.05).opacity(0.12),
                            Color.clear
                        ],
                        center: .center,
                        startRadius: size * 0.08,
                        endRadius: size * 0.65
                    )
                )
                .frame(width: size * 1.3, height: size * 1.3)
                .scaleEffect(animated && pulse ? 1.08 : 0.94)

            // Dripping blood streams & animated falling droplets behind/below brain
            ZStack {
                // Static attached blood drips hanging from the cortex
                BloodDripStreamShape()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.92, green: 0.03, blue: 0.09),
                                Color(red: 0.52, green: 0.0, blue: 0.04)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: size * 0.72, height: size * 0.48)
                    .offset(y: size * 0.22)

                // Animated falling blood drop 1 (left)
                BloodTeardropShape()
                    .fill(Color(red: 0.88, green: 0.02, blue: 0.08))
                    .frame(width: size * 0.08, height: size * 0.12)
                    .offset(x: -size * 0.18, y: size * 0.28 + dripOffset1)
                    .opacity(dripOpacity1)

                // Animated falling blood drop 2 (right-center)
                BloodTeardropShape()
                    .fill(Color(red: 0.85, green: 0.02, blue: 0.07))
                    .frame(width: size * 0.09, height: size * 0.13)
                    .offset(x: size * 0.08, y: size * 0.26 + dripOffset2)
                    .opacity(dripOpacity2)
            }

            // Main Brain Icon Image (loaded from bundle AppIcon if available, plus vector brain overlay)
            ZStack {
                // Anatomical Brain Silhouette & Folds
                Image(systemName: "brain.head.profile")
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * 0.74, height: size * 0.74)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [
                                Color(red: 0.98, green: 0.30, blue: 0.36),
                                Color(red: 0.82, green: 0.06, blue: 0.14),
                                Color(red: 0.45, green: 0.01, blue: 0.05)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color(red: 0.90, green: 0.02, blue: 0.08).opacity(0.7), radius: size * 0.08, x: 0, y: 4)

                // Secondary brain cortex detail overlay
                Image(systemName: "brain")
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * 0.70, height: size * 0.62)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [
                                Color(red: 1.0, green: 0.38, blue: 0.42),
                                Color(red: 0.85, green: 0.04, blue: 0.10),
                                Color(red: 0.42, green: 0.0, blue: 0.04)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .offset(y: -size * 0.03)

                // Crimson blood splatter accents on the cortex
                Circle()
                    .fill(Color(red: 0.88, green: 0.0, blue: 0.06).opacity(0.75))
                    .frame(width: size * 0.11, height: size * 0.11)
                    .offset(x: -size * 0.16, y: size * 0.04)
                    .blur(radius: 1)

                Circle()
                    .fill(Color(red: 0.75, green: 0.0, blue: 0.05).opacity(0.8))
                    .frame(width: size * 0.14, height: size * 0.14)
                    .offset(x: size * 0.12, y: size * 0.07)
                    .blur(radius: 1.5)
            }
            .scaleEffect(animated && pulse ? 1.05 : 0.97)
        }
        .frame(width: size, height: size)
        .onAppear {
            guard animated else { return }
            withAnimation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true)) {
                pulse = true
            }
            withAnimation(.easeIn(duration: 1.35).repeatForever(autoreverses: false)) {
                dripOffset1 = size * 0.26
                dripOpacity1 = 0.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                withAnimation(.easeIn(duration: 1.35).repeatForever(autoreverses: false)) {
                    dripOffset2 = size * 0.28
                    dripOpacity2 = 0.0
                }
            }
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
            control1: CGPoint(x: -w * 0.15, y: h * 0.55),
            control2: CGPoint(x: w * 0.1, y: h)
        )
        path.addCurve(
            to: CGPoint(x: w * 0.5, y: 0),
            control1: CGPoint(x: w * 0.9, y: h),
            control2: CGPoint(x: w * 1.15, y: h * 0.55)
        )
        path.closeSubpath()
        return path
    }
}

private struct BloodDripStreamShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height

        let columns: [(CGFloat, CGFloat, CGFloat)] = [
            (0.22, 0.72, 0.07),
            (0.40, 0.95, 0.08),
            (0.58, 0.64, 0.065),
            (0.76, 0.84, 0.075)
        ]

        for (cxFactor, lenFactor, radiusFactor) in columns {
            let cx = w * cxFactor
            let endY = h * lenFactor
            let r = w * radiusFactor

            path.move(to: CGPoint(x: cx - r * 0.5, y: 0))
            path.addLine(to: CGPoint(x: cx - r * 0.4, y: endY - r))
            path.addQuadCurve(
                to: CGPoint(x: cx, y: endY),
                control: CGPoint(x: cx - r, y: endY)
            )
            path.addQuadCurve(
                to: CGPoint(x: cx + r * 0.4, y: endY - r),
                control: CGPoint(x: cx + r, y: endY)
            )
            path.addLine(to: CGPoint(x: cx + r * 0.5, y: 0))
            path.closeSubpath()
        }
        return path
    }
}

struct SplashIntroView: View {
    let onFinished: () -> Void

    @State private var appearScale: CGFloat = 0.72
    @State private var appearOpacity: Double = 0.0
    @State private var textOffset: CGFloat = 18
    @State private var progressValue: CGFloat = 0.0

    var body: some View {
        ZStack {
            // Deep dark blood-obsidian background
            LinearGradient(
                colors: [
                    Color(red: 0.11, green: 0.01, blue: 0.03),
                    Color(red: 0.04, green: 0.01, blue: 0.02),
                    Color.black
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 26) {
                Spacer()

                BloodyBrainLogoView(size: 185, animated: true)
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
                                    Color(red: 1.0, green: 0.45, blue: 0.50),
                                    Color(red: 0.90, green: 0.12, blue: 0.22)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    Text("Almanca Artikel & Nöro-Hafıza Çalışma Sistemi")
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(Color.white.opacity(0.68))

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

                // Loading progress bar
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.white.opacity(0.12))
                        .frame(width: 240, height: 6)

                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.95, green: 0.08, blue: 0.18),
                                    Color(red: 0.65, green: 0.02, blue: 0.08)
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: 240 * progressValue, height: 6)
                }
                .padding(.top, 12)
                .opacity(appearOpacity)

                Spacer()

                Text("Geçmek için tıklayın")
                    .font(.caption2)
                    .foregroundColor(Color.white.opacity(0.35))
                    .padding(.bottom, 16)
            }
            .padding(32)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onFinished()
        }
        .onAppear {
            withAnimation(.spring(response: 0.65, dampingFraction: 0.72)) {
                appearScale = 1.0
                appearOpacity = 1.0
                textOffset = 0
            }
            withAnimation(.easeInOut(duration: 2.1)) {
                progressValue = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.25) {
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
    @State private var selectedTab: AppTab = .study
    @State private var showNewDeckSheet: Bool = false
    @State private var deckToRename: String? = nil

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
                    Text("Yerel Depolama Aktif")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(12)
            }
            .frame(width: 215)
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
        .sheet(isPresented: $showNewDeckSheet) {
            NewDeckSheet { createdDeck in
                store.selectedStudyDeck = createdDeck
            }
            .environmentObject(store)
        }
        .sheet(item: Binding(
            get: { deckToRename.map { IdentifiableDeckName(name: $0) } },
            set: { deckToRename = $0?.name }
        )) { item in
            RenameDeckSheet(oldName: item.name)
                .environmentObject(store)
        }
    }
}

struct RootContainerView: View {
    @State private var showSplash: Bool = true

    var body: some View {
        ZStack {
            MainContentView()
                .opacity(showSplash ? 0 : 1)

            if showSplash {
                SplashIntroView {
                    withAnimation(.easeInOut(duration: 0.45)) {
                        showSplash = false
                    }
                }
                .transition(.opacity)
                .zIndex(10)
            }
        }
        .frame(minWidth: 920, minHeight: 600)
    }
}
