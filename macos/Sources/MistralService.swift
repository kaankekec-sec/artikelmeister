import Foundation

// MARK: - Mistral Vocabulary Generator Service

enum MistralVocabularyGenerator {
    static let proxyBaseURL = "https://artikelmeister-mistral-proxy.artikelmeister.workers.dev"
    static let lockedModel = "deepseek-v4-1-flash-1017"

    @MainActor
    static func generateWords(
        level: CEFRLevel,
        targetCount: Int,
        topicHint: String = "",
        includeArticleless: Bool = false,
        existingCards: [Flashcard],
        onProgress: @escaping (String) -> Void
    ) async throws -> [GeneratedWordItem] {
        let clampedCount = max(1, min(100, targetCount))

        var excludedWordsLower = Set(existingCards.map { $0.word.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
        var excludedListForPrompt = existingCards.map { $0.word.trimmingCharacters(in: .whitespacesAndNewlines) }

        var collected: [GeneratedWordItem] = []
        var consecutiveErrors = 0
        var attempts = 0
        let maxConsecutiveErrors = 4
        let maxAttempts = 12

        while collected.count < clampedCount && consecutiveErrors < maxConsecutiveErrors && attempts < maxAttempts {
            attempts += 1
            let remainingNeeded = clampedCount - collected.count
            let chunkSize = min(40, remainingNeeded + 5)

            let progressPct = Int((Double(collected.count) / Double(clampedCount)) * 100)
            if collected.isEmpty {
                onProgress(L10n.text("\(level.rawValue) kelimeleri üretiliyor (%0)...", en: "Generating \(level.rawValue) words (0%)...", de: "\(level.rawValue)-Wörter werden generiert (0 %)…"))
            } else {
                onProgress(L10n.text("\(collected.count)/\(clampedCount) kelime üretildi (%\(progressPct))...", en: "Generated \(collected.count)/\(clampedCount) words (\(progressPct)%)...", de: "\(collected.count)/\(clampedCount) Wörter generiert (\(progressPct) %)…"))
            }

            do {
                let batch = try await requestBatchFromProxy(
                    level: level,
                    count: chunkSize,
                    topicHint: topicHint,
                    includeArticleless: includeArticleless,
                    excludedWords: excludedListForPrompt
                )

                var batchAdded = 0
                for item in batch {
                    guard collected.count < clampedCount else { break }
                    let parsedArtikel = Artikel.parse(item.artikel)
                    let articlelessItem = includeArticleless && parsedArtikel == nil && isArticlelessMarker(item.artikel)
                    guard parsedArtikel != nil || articlelessItem else { continue }

                    let cleanWord = item.word.trimmingCharacters(in: .whitespacesAndNewlines)
                    let cleanMeaning = item.meaning.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !cleanWord.isEmpty else { continue }
                    if articlelessItem && cleanMeaning.isEmpty { continue }

                    let normalizedWord = parsedArtikel != nil ? stripLeadingArtikelIfPresent(cleanWord) : cleanWord
                    guard !normalizedWord.isEmpty else { continue }

                    let lowerKey = normalizedWord.lowercased()
                    if !excludedWordsLower.contains(lowerKey) {
                        excludedWordsLower.insert(lowerKey)
                        excludedListForPrompt.append(normalizedWord)
                        collected.append(
                            GeneratedWordItem(
                                artikel: parsedArtikel?.rawValue ?? ArtikelFilter.none,
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
                if consecutiveErrors >= maxConsecutiveErrors {
                    break
                }
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }

        if collected.isEmpty {
            throw NSError(
                domain: "MistralGenerator",
                code: 422,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Kelime üretilemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.", en: "Could not generate words. Check your internet connection and try again.", de: "Wörter konnten nicht generiert werden. Prüfe deine Internetverbindung und versuche es erneut.")]
            )
        }

        onProgress(L10n.text("\(collected.count)/\(clampedCount) kelime başarıyla tamamlandı!", en: "\(collected.count)/\(clampedCount) words completed!", de: "\(collected.count)/\(clampedCount) Wörter fertiggestellt!"))
        return collected
    }

    private static func stripLeadingArtikelIfPresent(_ rawWord: String) -> String {
        let parts = rawWord.split(separator: " ", maxSplits: 1).map(String.init)
        if parts.count == 2, Artikel.parse(parts[0]) != nil {
            return parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return rawWord
    }

    static func isArticlelessMarker(_ raw: String) -> Bool {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return s.isEmpty || s == ArtikelFilter.none || s == "none" || s == "artikelsiz" || s == "kein"
    }

    @MainActor
    private static func requestBatchFromProxy(
        level: CEFRLevel,
        count: Int,
        topicHint: String,
        includeArticleless: Bool,
        excludedWords: [String]
    ) async throws -> [GeneratedWordItem] {
        let exclusionText: String
        if excludedWords.isEmpty {
            exclusionText = "Henüz kayıtlı kelime yok."
        } else {
            let slice = excludedWords.suffix(300).joined(separator: ", ")
            exclusionText = "KULLANICININ MEVCUT KELİME LİSTESİ (BU KELİMELERİ ASLA DAHİL ETME):\n[\(slice)]"
        }

        let topicInstruction = topicHint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Genel \(level.rawValue) müfredatına uygun çeşitli konular."
            : "Öncelikli konu/tema: \(topicHint)"

        let targetMeaningLang = L10n.aiTargetMeaningLanguagePrompt
        let systemPrompt = """
        Sen uzman bir Almanca öğretmenisin. Yalnızca geçerli JSON döndür.
        Format: {"words": [{"artikel": "der|die|das|-", "word": "...", "meaning": "..."}]}
        """

        let userPrompt: String
        if includeArticleless {
            userPrompt = """
            CEFR \(level.rawValue) seviyesine uygun, tam \(count) adet Almanca kelime ve \(targetMeaningLang) karşılığını üret.
            Kelimelerin çoğu isim olsun; ayrıca fiil, sıfat, zarf veya kalıp ifade gibi artikelsiz kelimelere de yer ver (yaklaşık üçte biri).
            KURALLAR:
            1. İsimlerde "artikel": "der", "die" veya "das"; artikelsiz kelimelerde "artikel": "-".
            2. "word": İsimlerde sadece ismin kendisi (baş harfi büyük, örn: "Tisch"); fiillerde mastar hali (örn: "laufen"); sıfatlarda yalın hali.
            3. "meaning": Kısa ve net \(targetMeaningLang) anlamı. Artikel almayan kelimelerde anlam ZORUNLU.
            4. \(topicInstruction)
            5. Aşağıdaki mevcut kelimelerden HİÇBİRİNİ kullanma:
            \(exclusionText)
            """
        } else {
            userPrompt = """
            CEFR \(level.rawValue) seviyesine uygun, artikeli yalnızca "der", "die" veya "das" olan tam \(count) adet tekil Almanca isim ve \(targetMeaningLang) karşılığını üret.
            KURALLAR:
            1. "artikel": "der", "die" veya "das"
            2. "word": Sadece ismin kendisi (baş harfi büyük, artikelsiz, örn: "Tisch")
            3. "meaning": Kısa ve net \(targetMeaningLang) anlamı
            4. \(topicInstruction)
            5. Aşağıdaki mevcut kelimelerden HİÇBİRİNİ kullanma:
            \(exclusionText)
            """
        }

        let payload: [String: Any] = [
            "model": lockedModel,
            "temperature": 0.7,
            "max_tokens": 4096,
            "reasoning_effort": "none",
            "response_format": ["type": "json_object"],
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": userPrompt]
            ]
        ]

        guard let url = URL(string: "\(proxyBaseURL)/v1/chat/completions") else {
            throw NSError(
                domain: "MistralGenerator",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Geçersiz sunucu adresi.", en: "Invalid server URL.", de: "Ungültige Serveradresse.")]
            )
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 60
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw NSError(
                domain: "MistralGenerator",
                code: 500,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Sunucudan geçersiz yanıt alındı.", en: "Invalid response from server.", de: "Ungültige Antwort vom Server.")]
            )
        }

        guard (200...299).contains(http.statusCode) else {
            let detail = extractErrorMessage(from: data) ?? "HTTP \(http.statusCode)"
            throw NSError(
                domain: "MistralGenerator",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Sunucu hatası (\(http.statusCode)): \(detail)", en: "Server error (\(http.statusCode)): \(detail)", de: "Serverfehler (\(http.statusCode)): \(detail)")]
            )
        }

        return try parseChatCompletionWords(data: data)
    }

    static func parseChatCompletionWords(data: Data) throws -> [GeneratedWordItem] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = root["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let rawContent = message["content"] as? String else {
            throw NSError(
                domain: "MistralGenerator",
                code: 422,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Yanıt çözümlenemedi.", en: "Could not parse response.", de: "Antwort konnte nicht geparst werden.")]
            )
        }

        return try decodeWordsContent(rawContent)
    }

    static func decodeWordsContent(_ rawContent: String) throws -> [GeneratedWordItem] {
        var cleaned = rawContent.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasPrefix("```") {
            if let firstNewline = cleaned.firstIndex(of: "\n") {
                cleaned = String(cleaned[cleaned.index(after: firstNewline)...])
            }
            if cleaned.hasSuffix("```") {
                cleaned = String(cleaned.dropLast(3))
            }
            cleaned = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        }

        guard let contentData = cleaned.data(using: .utf8) else {
            throw NSError(
                domain: "MistralGenerator",
                code: 422,
                userInfo: [NSLocalizedDescriptionKey: L10n.text("Yanıt UTF-8 olarak okunamadı.", en: "Could not read response as UTF-8.", de: "Antwort konnte nicht als UTF-8 gelesen werden.")]
            )
        }

        struct WordsEnvelope: Decodable {
            let words: [GeneratedWordItem]
        }

        if let envelope = try? JSONDecoder().decode(WordsEnvelope.self, from: contentData) {
            return envelope.words
        }
        if let directArray = try? JSONDecoder().decode([GeneratedWordItem].self, from: contentData) {
            return directArray
        }

        throw NSError(
            domain: "MistralGenerator",
            code: 422,
            userInfo: [NSLocalizedDescriptionKey: L10n.text("Kelime listesi JSON formatında çözümlenemedi.", en: "Could not decode word list as JSON.", de: "Wortliste konnte nicht als JSON dekodiert werden.")]
        )
    }

    static func extractErrorMessage(from data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        if let err = json["error"] as? [String: Any], let msg = err["message"] as? String {
            return msg
        }
        if let msg = json["message"] as? String {
            return msg
        }
        if let detail = json["detail"] as? String {
            return detail
        }
        return nil
    }
}
