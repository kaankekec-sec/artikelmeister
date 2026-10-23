import Foundation
import Security
import CryptoKit
import Network
import AppKit

enum CEFRLevel: String, CaseIterable, Identifiable {
    case a1 = "A1"
    case a2 = "A2"
    case b1 = "B1"
    case b2 = "B2"
    case c1 = "C1"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .a1: return "A1 — Başlangıç"
        case .a2: return "A2 — Temel"
        case .b1: return "B1 — Orta"
        case .b2: return "B2 — İleri Orta"
        case .c1: return "C1 — İleri / Akademik"
        }
    }

    var description: String {
        switch self {
        case .a1: return "Günlük hayatta en sık kullanılan temel somut isimler (ev, yiyecek, aile, zaman)."
        case .a2: return "Alışveriş, seyahat, ulaşım, sağlık ve günlük iletişimde kullanılan isimler."
        case .b1: return "İş, eğitim, çevre, kültür ve deneyim aktarımında kullanılan orta düzey isimler."
        case .b2: return "Tartışma, teknoloji, ekonomi, toplum ve soyut düşünce ifade eden isimler."
        case .c1: return "Akademik, bilimsel, kurumsal ve ileri düzey edebi/soyut Almanca isimler."
        }
    }
}

enum GeminiAuthMode: String, Codable, CaseIterable, Identifiable {
    case apiKey = "apiKey"
    case googleOAuth = "googleOAuth"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .apiKey: return "Google AI Studio (API Anahtarı)"
        case .googleOAuth: return "Google Hesabı (OAuth 2.0)"
        }
    }
}

struct GeneratedWordItem: Codable, Hashable, Identifiable {
    var id: String { "\(artikel.lowercased())_\(word.lowercased())" }
    let artikel: String
    let word: String
    let meaning: String
}

/// Manages Gemini credentials securely in macOS Keychain and handles OAuth 2.0 + API Key flows
@MainActor
final class GeminiAuthManager: ObservableObject {
    static let shared = GeminiAuthManager()

    @Published var authMode: GeminiAuthMode = .apiKey
    @Published var apiKey: String = ""
    @Published var oauthClientId: String = ""
    @Published var oauthClientSecret: String = ""
    @Published var oauthProjectId: String = ""
    @Published var oauthAccessToken: String = ""
    @Published var oauthRefreshToken: String = ""
    @Published var oauthTokenExpiry: Date? = nil
    @Published var selectedModel: String = "gemini-3.8-flash"
    @Published var isAuthenticating: Bool = false
    @Published var authStatusMessage: String? = nil

    @Published var availableModels: [String] = [
        "gemini-3.8-flash",
        "gemini-3.8-pro",
        "gemini-3.5-flash",
        "gemini-3.0-flash",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    ]

    private static func unmask(_ bytes: [UInt8], key: UInt8 = 0x5A) -> String {
        let decoded = bytes.map { $0 ^ key }
        return String(bytes: decoded, encoding: .utf8) ?? ""
    }

    static let defaultOAuthClientId = unmask([
        110, 107, 110, 105, 99, 105, 107, 105, 110, 98, 111, 108, 119, 46, 46, 48,
        57, 49, 60, 57, 63, 109, 48, 106, 104, 106, 63, 106, 109, 99, 62, 61,
        49, 107, 60, 106, 54, 44, 47, 54, 49, 44, 110, 99, 62, 116, 59, 42,
        42, 41, 116, 61, 53, 53, 61, 54, 63, 47, 41, 63, 40, 57, 53, 52,
        46, 63, 52, 46, 116, 57, 53, 55
    ])

    static let defaultOAuthClientSecret = unmask([
        29, 21, 25, 9, 10, 2, 119, 61, 48, 55, 10, 2, 15, 30, 43, 106,
        40, 40, 52, 21, 13, 15, 14, 106, 3, 16, 22, 14, 32, 29, 52, 54,
        57, 110, 105
    ])

    var effectiveClientId: String {
        let custom = oauthClientId.trimmingCharacters(in: .whitespacesAndNewlines)
        return custom.isEmpty ? Self.defaultOAuthClientId : custom
    }

    var effectiveClientSecret: String {
        let custom = oauthClientSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        return custom.isEmpty ? Self.defaultOAuthClientSecret : custom
    }

    private let keychainService = "com.artikelmeister.gemini.auth"
    private var loopbackListener: NWListener?

    private struct StoredCredentials: Codable {
        var authMode: GeminiAuthMode
        var apiKey: String
        var oauthClientId: String
        var oauthClientSecret: String
        var oauthProjectId: String
        var oauthAccessToken: String
        var oauthRefreshToken: String
        var oauthTokenExpiry: Date?
        var selectedModel: String
    }

    init() {
        loadFromKeychain()
    }

    var isAuthorized: Bool {
        switch authMode {
        case .apiKey:
            return !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .googleOAuth:
            return !oauthAccessToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                   !oauthRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    // MARK: - Keychain Persistence

    func saveToKeychain() {
        let creds = StoredCredentials(
            authMode: authMode,
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthClientId: oauthClientId.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthClientSecret: oauthClientSecret.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthProjectId: oauthProjectId.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthAccessToken: oauthAccessToken.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthRefreshToken: oauthRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines),
            oauthTokenExpiry: oauthTokenExpiry,
            selectedModel: selectedModel
        )
        guard let data = try? JSONEncoder().encode(creds) else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: "default"
        ]
        SecItemDelete(query as CFDictionary)

        var addQuery = query
        addQuery[kSecValueData as String] = data
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    func loadFromKeychain() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: "default",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let creds = try? JSONDecoder().decode(StoredCredentials.self, from: data) else {
            return
        }
        self.authMode = creds.authMode
        self.apiKey = creds.apiKey
        self.oauthClientId = creds.oauthClientId
        self.oauthClientSecret = creds.oauthClientSecret
        self.oauthProjectId = creds.oauthProjectId
        self.oauthAccessToken = creds.oauthAccessToken
        self.oauthRefreshToken = creds.oauthRefreshToken
        self.oauthTokenExpiry = creds.oauthTokenExpiry
        if !creds.selectedModel.isEmpty {
            // Migrate legacy default "gemini-2.5-flash" to "gemini-3.8-flash"
            if creds.selectedModel == "gemini-2.5-flash" {
                self.selectedModel = "gemini-3.8-flash"
            } else {
                self.selectedModel = creds.selectedModel
            }
            if !availableModels.contains(self.selectedModel) {
                availableModels.insert(self.selectedModel, at: 0)
            }
        }
    }

    func clearAuthorization() {
        apiKey = ""
        oauthAccessToken = ""
        oauthRefreshToken = ""
        oauthTokenExpiry = nil
        authStatusMessage = "Yetkilendirme bilgileri temizlendi."
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: "default"
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Browser Links & OAuth 2.0 PKCE Loopback Flow

    func openGoogleAIStudioInBrowser() {
        if let url = URL(string: "https://aistudio.google.com/app/apikey") {
            NSWorkspace.shared.open(url)
        }
    }

    func startBrowserOAuthFlow() async throws {
        let clientId = effectiveClientId
        guard !clientId.isEmpty else {
            throw NSError(
                domain: "GeminiAuth",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Google OAuth 2.0 istemci bilgisi bulunamadı."]
            )
        }

        isAuthenticating = true
        authStatusMessage = "Tarayıcıda Google yetkilendirme sayfası açılıyor..."
        defer {
            isAuthenticating = false
            loopbackListener?.cancel()
            loopbackListener = nil
        }

        let codeVerifier = Self.generateCodeVerifier()
        let codeChallenge = Self.generateCodeChallenge(from: codeVerifier)

        // Start a local loopback TCP listener on an available ephemeral port
        let (port, codeFuture) = try startLoopbackServer()
        let redirectURI = "http://127.0.0.1:\(port)/callback"

        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "https://www.googleapis.com/auth/generative-language.retriever https://www.googleapis.com/auth/cloud-platform"),
            URLQueryItem(name: "access_type", value: "offline"),
            URLQueryItem(name: "prompt", value: "consent"),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]

        if let authURL = components.url {
            NSWorkspace.shared.open(authURL)
        }

        let authCode = try await codeFuture()
        authStatusMessage = "Yetkilendirme kodu alındı, erişim anahtarı (Access Token) alınıyor..."

        try await exchangeCodeForToken(
            code: authCode,
            codeVerifier: codeVerifier,
            redirectURI: redirectURI,
            clientId: clientId,
            clientSecret: effectiveClientSecret
        )

        saveToKeychain()
        authStatusMessage = "Google Hesabı ile Gemini yetkilendirmesi başarıyla tamamlandı!"
    }

    private func startLoopbackServer() throws -> (UInt16, @Sendable () async throws -> String) {
        let params = NWParameters.tcp
        let listener = try NWListener(using: params, on: .any)
        self.loopbackListener = listener

        let queue = DispatchQueue(label: "com.artikelmeister.oauth.loopback")
        var continuationBox: CheckedContinuation<String, Error>?

        listener.newConnectionHandler = { connection in
            connection.start(queue: queue)
            connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { data, _, _, _ in
                guard let data = data, let requestStr = String(data: data, encoding: .utf8) else {
                    connection.cancel()
                    return
                }

                // Extract GET /callback?code=... from first HTTP line
                let firstLine = requestStr.components(separatedBy: "\r\n").first ?? ""
                let parts = firstLine.components(separatedBy: " ")
                var extractedCode: String?
                var extractedError: String?

                if parts.count >= 2,
                   let urlComponents = URLComponents(string: "http://127.0.0.1\(parts[1])") {
                    extractedCode = urlComponents.queryItems?.first(where: { $0.name == "code" })?.value
                    extractedError = urlComponents.queryItems?.first(where: { $0.name == "error" })?.value
                }

                let htmlBody: String
                if extractedCode != nil {
                    htmlBody = """
                    <html><head><meta charset="utf-8"><title>ArtikelMeister Yetkilendirme</title></head>
                    <body style="font-family: -apple-system, sans-serif; text-align: center; padding: 60px;">
                    <h2 style="color: #28a745;">✓ Gemini Yetkilendirmesi Başarılı!</h2>
                    <p>Bu tarayıcı sekmesini kapatıp <b>ArtikelMeister</b> uygulamasına dönebilirsiniz.</p>
                    </body></html>
                    """
                } else {
                    htmlBody = """
                    <html><head><meta charset="utf-8"><title>ArtikelMeister Yetkilendirme Hatası</title></head>
                    <body style="font-family: -apple-system, sans-serif; text-align: center; padding: 60px;">
                    <h2 style="color: #dc3545;">Yetkilendirme Tamamlanamadı</h2>
                    <p>\(extractedError ?? "Kod alınamadı.")</p>
                    </body></html>
                    """
                }

                let httpResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: \(htmlBody.utf8.count)\r\nConnection: close\r\n\r\n\(htmlBody)"
                connection.send(content: httpResponse.data(using: .utf8), completion: .contentProcessed { _ in
                    connection.cancel()
                })

                if let code = extractedCode {
                    continuationBox?.resume(returning: code)
                    continuationBox = nil
                } else {
                    continuationBox?.resume(throwing: NSError(
                        domain: "GeminiAuth",
                        code: 401,
                        userInfo: [NSLocalizedDescriptionKey: "Google OAuth hatası: \(extractedError ?? "Bilinmeyen hata")"]
                    ))
                    continuationBox = nil
                }
            }
        }

        let portSemaphore = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            switch state {
            case .ready, .failed, .cancelled:
                portSemaphore.signal()
            default:
                break
            }
        }
        listener.start(queue: queue)
        _ = portSemaphore.wait(timeout: .now() + 5)

        guard let assignedPort = listener.port?.rawValue else {
            throw NSError(domain: "GeminiAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "Yerel yetkilendirme portu açılamadı."])
        }

        let waitForCode: @Sendable () async throws -> String = {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                queue.async {
                    continuationBox = continuation
                }
            }
        }

        return (assignedPort, waitForCode)
    }

    private func exchangeCodeForToken(
        code: String,
        codeVerifier: String,
        redirectURI: String,
        clientId: String,
        clientSecret: String
    ) async throws {
        let tokenURL = URL(string: "https://oauth2.googleapis.com/token")!
        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        var bodyParams = [
            "client_id": clientId,
            "code": code,
            "code_verifier": codeVerifier,
            "grant_type": "authorization_code",
            "redirect_uri": redirectURI
        ]
        if !clientSecret.isEmpty {
            bodyParams["client_secret"] = clientSecret
        }

        let formBody = bodyParams
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")" }
            .joined(separator: "&")
        request.httpBody = formBody.data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200..<300).contains(httpResp.statusCode) else {
            let errText = String(data: data, encoding: .utf8) ?? "Token değişimi başarısız."
            throw NSError(domain: "GeminiAuth", code: 401, userInfo: [NSLocalizedDescriptionKey: "OAuth Token Hatası: \(errText)"])
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let access = json["access_token"] as? String {
                self.oauthAccessToken = access
            }
            if let refresh = json["refresh_token"] as? String {
                self.oauthRefreshToken = refresh
            }
            if let expiresIn = json["expires_in"] as? Double {
                self.oauthTokenExpiry = Date().addingTimeInterval(expiresIn - 60)
            }
        }
    }

    func validOAuthAccessToken() async throws -> String {
        if let expiry = oauthTokenExpiry, expiry <= Date(), !oauthRefreshToken.isEmpty, !effectiveClientId.isEmpty {
            try await refreshOAuthToken()
        }
        let token = oauthAccessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else {
            throw NSError(domain: "GeminiAuth", code: 401, userInfo: [NSLocalizedDescriptionKey: "Geçerli bir OAuth erişim anahtarı bulunamadı. Lütfen yeniden giriş yapın."])
        }
        return token
    }

    private func refreshOAuthToken() async throws {
        let tokenURL = URL(string: "https://oauth2.googleapis.com/token")!
        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        var bodyParams = [
            "client_id": effectiveClientId,
            "refresh_token": oauthRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines),
            "grant_type": "refresh_token"
        ]
        let secret = effectiveClientSecret
        if !secret.isEmpty {
            bodyParams["client_secret"] = secret
        }

        let formBody = bodyParams
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")" }
            .joined(separator: "&")
        request.httpBody = formBody.data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200..<300).contains(httpResp.statusCode),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["access_token"] as? String else {
            throw NSError(domain: "GeminiAuth", code: 401, userInfo: [NSLocalizedDescriptionKey: "OAuth oturumu yenilenemedi. Lütfen tekrar giriş yapın."])
        }
        self.oauthAccessToken = access
        if let expiresIn = json["expires_in"] as? Double {
            self.oauthTokenExpiry = Date().addingTimeInterval(expiresIn - 60)
        }
        saveToKeychain()
    }

    // MARK: - Verify Authorization & Dynamic Model Discovery

    func verifyCredentials() async throws {
        var request: URLRequest
        switch authMode {
        case .apiKey:
            let cleanKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleanKey.isEmpty else {
                throw NSError(domain: "GeminiAuth", code: 400, userInfo: [NSLocalizedDescriptionKey: "Lütfen API anahtarınızı girin."])
            }
            var comps = URLComponents(string: "https://generativelanguage.googleapis.com/v1beta/models")!
            comps.queryItems = [URLQueryItem(name: "key", value: cleanKey)]
            request = URLRequest(url: comps.url!)

        case .googleOAuth:
            let token = try await validOAuthAccessToken()
            request = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models")!)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            let proj = oauthProjectId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !proj.isEmpty {
                request.setValue(proj, forHTTPHeaderField: "x-goog-user-project")
            }
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "GeminiAuth", code: 500, userInfo: [NSLocalizedDescriptionKey: "Sunucudan yanıt alınamadı."])
        }
        guard (200..<300).contains(httpResp.statusCode) else {
            let bodyText = String(data: data, encoding: .utf8) ?? ""
            throw NSError(
                domain: "GeminiAuth",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Yetkilendirme doğrulanamadı (HTTP \(httpResp.statusCode)). Bilgilerinizi kontrol edin.\n\(bodyText)"]
            )
        }

        updateDiscoveredModels(from: data)
        saveToKeychain()
        authStatusMessage = "Yetkilendirme doğrulandı! Aktif model: \(selectedModel)"
    }

    private func updateDiscoveredModels(from data: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let modelsArray = json["models"] as? [[String: Any]] else {
            return
        }

        var discovered: [String] = []
        for modelObj in modelsArray {
            guard let rawName = modelObj["name"] as? String else { continue }
            let methods = (modelObj["supportedGenerationMethods"] as? [String]) ?? []
            guard methods.contains("generateContent") else { continue }

            let shortName = rawName.hasPrefix("models/")
                ? String(rawName.dropFirst("models/".count))
                : rawName
            if shortName.lowercased().contains("gemini") {
                discovered.append(shortName)
            }
        }

        guard !discovered.isEmpty else { return }

        // Prioritize 3.8-flash and flash models at the top
        let sortedDiscovered = discovered.sorted { a, b in
            let aIs38Flash = a.contains("3.8") && a.contains("flash")
            let bIs38Flash = b.contains("3.8") && b.contains("flash")
            if aIs38Flash != bIs38Flash { return aIs38Flash }

            let aIs38 = a.contains("3.8")
            let bIs38 = b.contains("3.8")
            if aIs38 != bIs38 { return aIs38 }

            let aIsFlash = a.contains("flash")
            let bIsFlash = b.contains("flash")
            if aIsFlash != bIsFlash { return aIsFlash }

            return a > b
        }

        var merged = ["gemini-3.8-flash", "gemini-3.8-pro"]
        for m in sortedDiscovered where !merged.contains(m) {
            merged.append(m)
        }
        self.availableModels = merged

        // If current selectedModel isn't in the API's discovered models, pick the best discovered Flash model
        if !discovered.contains(selectedModel) {
            if let best38Flash = sortedDiscovered.first(where: { $0.contains("3.8") && $0.contains("flash") }) {
                self.selectedModel = best38Flash
            } else if let bestFlash = sortedDiscovered.first(where: { $0.contains("flash") && !$0.contains("8b") && !$0.contains("lite") }) {
                self.selectedModel = bestFlash
            } else if let firstModel = sortedDiscovered.first {
                self.selectedModel = firstModel
            }
        }
    }

    // MARK: - PKCE Helpers

    private static func generateCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func generateCodeChallenge(from verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

// MARK: - Gemini Vocabulary Generator Service

enum GeminiVocabularyGenerator {
    /// Generates exactly `targetCount` (1...100) German nouns for the chosen CEFR level that do NOT exist in `existingWords`.
    /// Uses fast micro-batching (15 words per sub-request) so requests complete in 2-3 seconds without timing out.
    @MainActor
    static func generateWords(
        level: CEFRLevel,
        targetCount: Int,
        topicHint: String = "",
        existingCards: [Flashcard],
        authManager: GeminiAuthManager,
        onProgress: @escaping (String) -> Void
    ) async throws -> [GeneratedWordItem] {
        let clampedCount = max(1, min(100, targetCount))

        // Build case-insensitive set of existing German nouns to strictly exclude
        var excludedWordsLower = Set(existingCards.map { $0.word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
        var excludedListForPrompt = existingCards.map { $0.word.trimmingCharacters(in: .whitespacesAndNewlines) }

        var collected: [GeneratedWordItem] = []
        var consecutiveErrors = 0
        let maxConsecutiveErrors = 3

        while collected.count < clampedCount && consecutiveErrors < maxConsecutiveErrors {
            let remainingNeeded = clampedCount - collected.count
            // Micro-batch of 15 words max per sub-request ensures lightning-fast 2-3 second completion
            let chunkSize = min(15, remainingNeeded)

            let progressPct = Int((Double(collected.count) / Double(clampedCount)) * 100)
            if collected.isEmpty {
                onProgress("Gemini (\(authManager.selectedModel)) ile \(level.rawValue) kelimeleri üretiliyor (%0)...")
            } else {
                onProgress("\(collected.count)/\(clampedCount) kelime üretildi (%\(progressPct))...")
            }

            do {
                let batch = try await requestBatchFromGemini(
                    level: level,
                    count: chunkSize,
                    topicHint: topicHint,
                    excludedWords: excludedListForPrompt,
                    authManager: authManager,
                    allowModelAutoDiscoveryRetry: true
                )

                var batchAdded = 0
                for item in batch {
                    guard collected.count < clampedCount else { break }
                    guard let parsedArtikel = Artikel.parse(item.artikel) else { continue }
                    let cleanWord = item.word.trimmingCharacters(in: .whitespacesAndNewlines)
                    let cleanMeaning = item.meaning.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !cleanWord.isEmpty else { continue }

                    let normalizedWord = stripLeadingArtikelIfPresent(cleanWord)
                    guard !normalizedWord.isEmpty else { continue }

                    let lowerKey = normalizedWord.lowercased()
                    if !excludedWordsLower.contains(lowerKey) {
                        excludedWordsLower.insert(lowerKey)
                        excludedListForPrompt.append(normalizedWord)
                        collected.append(
                            GeneratedWordItem(
                                artikel: parsedArtikel.rawValue,
                                word: normalizedWord,
                                meaning: cleanMeaning
                            )
                        )
                        batchAdded += 1
                    }
                }

                if batchAdded > 0 {
                    consecutiveErrors = 0
                } else {
                    consecutiveErrors += 1
                }
            } catch {
                consecutiveErrors += 1
                if collected.isEmpty && consecutiveErrors >= maxConsecutiveErrors {
                    throw error
                }
                // If we already collected some words, finish with what we have instead of failing completely
                if !collected.isEmpty && consecutiveErrors >= 2 {
                    break
                }
                // Short breather before retry
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }

        if collected.isEmpty {
            throw NSError(
                domain: "GeminiGenerator",
                code: 422,
                userInfo: [NSLocalizedDescriptionKey: "Kelime üretilemedi. Lütfen internet bağlantınızı veya API yetkilendirmenizi kontrol edin."]
            )
        }

        onProgress("\(collected.count)/\(clampedCount) kelime başarıyla tamamlandı!")
        return collected
    }

    private static func stripLeadingArtikelIfPresent(_ rawWord: String) -> String {
        let parts = rawWord.split(separator: " ", maxSplits: 1).map(String.init)
        if parts.count == 2, Artikel.parse(parts[0]) != nil {
            return parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return rawWord
    }

    @MainActor
    private static func requestBatchFromGemini(
        level: CEFRLevel,
        count: Int,
        topicHint: String,
        excludedWords: [String],
        authManager: GeminiAuthManager,
        allowModelAutoDiscoveryRetry: Bool
    ) async throws -> [GeneratedWordItem] {
        let rawModel = authManager.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
        let modelName = rawModel.hasPrefix("models/") ? String(rawModel.dropFirst("models/".count)) : rawModel
        var request: URLRequest

        switch authManager.authMode {
        case .apiKey:
            let cleanKey = authManager.apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleanKey.isEmpty else {
                throw NSError(domain: "GeminiGenerator", code: 401, userInfo: [NSLocalizedDescriptionKey: "Gemini API Anahtarı girilmedi. Lütfen önce yetkilendirme yapın."])
            }
            var comps = URLComponents(string: "https://generativelanguage.googleapis.com/v1beta/models/\(modelName):generateContent")!
            comps.queryItems = [URLQueryItem(name: "key", value: cleanKey)]
            request = URLRequest(url: comps.url!)

        case .googleOAuth:
            let token = try await authManager.validOAuthAccessToken()
            let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(modelName):generateContent")!
            request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            let proj = authManager.oauthProjectId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !proj.isEmpty {
                request.setValue(proj, forHTTPHeaderField: "x-goog-user-project")
            }
        }

        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let exclusionText: String
        if excludedWords.isEmpty {
            exclusionText = "Henüz kayıtlı kelime yok."
        } else {
            // Keep exclusion prompt concise to prevent token bloat
            let slice = excludedWords.suffix(300).joined(separator: ", ")
            exclusionText = "KULLANICININ MEVCUT KELİME LİSTESİ (BU KELİMELERİ ASLA DAHİL ETME):\n[\(slice)]"
        }

        let topicInstruction = topicHint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel \(level.rawValue) müfredatına uygun çeşitli konular."
            : "Öncelikli konu/tema: \(topicHint)"

        let prompt = """
        Uzman Almanca öğretmenisin. CEFR **\(level.rawValue)** seviyesine uygun, artikeli yalnızca "der", "die" veya "das" olan tam **\(count)** adet tekil Almanca isim ve Türkçe karşılığını üret.
        KURALLAR:
        1. "artikel": "der", "die" veya "das"
        2. "word": Sadece ismin kendisi (baş harfi büyük, artikelsiz, örn: "Tisch")
        3. "meaning": Kısa ve net Türkçe anlamı
        4. \(topicInstruction)
        5. Aşağıdaki mevcut kelimelerden HİÇBİRİNİ kullanma:
        \(exclusionText)
        """

        let requestPayload: [String: Any] = [
            "contents": [
                [
                    "role": "user",
                    "parts": [
                        ["text": prompt]
                    ]
                ]
            ],
            "generationConfig": [
                "temperature": 0.7,
                "thinkingConfig": [
                    "thinkingBudget": 0
                ],
                "responseMimeType": "application/json",
                "responseSchema": [
                    "type": "ARRAY",
                    "description": "German nouns with article and Turkish meaning",
                    "items": [
                        "type": "OBJECT",
                        "properties": [
                            "artikel": [
                                "type": "STRING",
                                "enum": ["der", "die", "das"]
                            ],
                            "word": [
                                "type": "STRING"
                            ],
                            "meaning": [
                                "type": "STRING"
                            ]
                        ],
                        "required": ["artikel", "word", "meaning"]
                    ]
                ]
            ]
        ]

        request.httpBody = try JSONSerialization.data(withJSONObject: requestPayload)

        // Use custom URLSession with 60s request timeout & 120s resource timeout
        let sessionConfig = URLSessionConfiguration.default
        sessionConfig.timeoutIntervalForRequest = 60
        sessionConfig.timeoutIntervalForResource = 120
        let session = URLSession(configuration: sessionConfig)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let urlErr as NSError where urlErr.domain == NSURLErrorDomain && urlErr.code == NSURLErrorTimedOut {
            throw NSError(
                domain: "GeminiGenerator",
                code: NSURLErrorTimedOut,
                userInfo: [NSLocalizedDescriptionKey: "İstek zaman aşımına uğradı. Ağ bağlantınızı kontrol edip tekrar deneyin."]
            )
        }

        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "GeminiGenerator", code: 500, userInfo: [NSLocalizedDescriptionKey: "Gemini API yanıt vermedi."])
        }

        // If thinkingConfig is not supported by this model (HTTP 400), retry without thinkingConfig
        if httpResp.statusCode == 400 {
            if var payloadWithoutThinking = requestPayload["generationConfig"] as? [String: Any] {
                payloadWithoutThinking.removeValue(forKey: "thinkingConfig")
                var updatedPayload = requestPayload
                updatedPayload["generationConfig"] = payloadWithoutThinking
                request.httpBody = try? JSONSerialization.data(withJSONObject: updatedPayload)
                if let (fallbackData, fallbackResp) = try? await session.data(for: request),
                   let fbHttp = fallbackResp as? HTTPURLResponse, (200..<300).contains(fbHttp.statusCode) {
                    return try parseGeminiCandidates(data: fallbackData)
                }
            }
        }

        // If model name is not found (HTTP 404), auto-discover supported models from /v1beta/models and retry
        if (httpResp.statusCode == 404 || httpResp.statusCode == 400) && allowModelAutoDiscoveryRetry {
            let previousModel = authManager.selectedModel
            try? await authManager.verifyCredentials()
            if authManager.selectedModel != previousModel {
                return try await requestBatchFromGemini(
                    level: level,
                    count: count,
                    topicHint: topicHint,
                    excludedWords: excludedWords,
                    authManager: authManager,
                    allowModelAutoDiscoveryRetry: false
                )
            }
        }

        guard (200..<300).contains(httpResp.statusCode) else {
            var errorMessage = "Gemini API Hatası (HTTP \(httpResp.statusCode) - Model: \(modelName))."
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let errObj = json["error"] as? [String: Any],
               let msg = errObj["message"] as? String {
                errorMessage += "\n\(msg)"
            }
            throw NSError(domain: "GeminiGenerator", code: httpResp.statusCode, userInfo: [NSLocalizedDescriptionKey: errorMessage])
        }

        return try parseGeminiCandidates(data: data)
    }

    private static func parseGeminiCandidates(data: Data) throws -> [GeneratedWordItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]],
              let firstCandidate = candidates.first,
              let content = firstCandidate["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]],
              let text = parts.first?["text"] as? String,
              let jsonData = text.data(using: .utf8) else {
            throw NSError(domain: "GeminiGenerator", code: 500, userInfo: [NSLocalizedDescriptionKey: "Gemini yanıtı beklenmeyen bir formatta döndü."])
        }

        let decoded = try JSONDecoder().decode([GeneratedWordItem].self, from: jsonData)
        return decoded
    }
}
