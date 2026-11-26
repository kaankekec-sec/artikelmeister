package com.artikelmeister.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object MistralVocabularyGenerator {
    const val PROXY_URL = "https://artikelmeister-mistral-proxy.artikelmeister.workers.dev"
    const val MODEL = "deepseek-v4-1-flash-1017"

    suspend fun generateWords(
        level: CEFRLevel,
        targetCount: Int,
        topicHint: String,
        includeArticleless: Boolean = false,
        existingCards: List<Flashcard>,
        onProgress: (Int, Int) -> Unit
    ): List<GeneratedWordItem> = withContext(Dispatchers.IO) {
        val clampedCount = max(1, min(100, targetCount))
        val existingKeys = existingCards.map { it.syncKey }.toMutableSet()
        val existingSample = existingCards.take(120).joinToString(", ") {
            if (it.isArticleless) it.word else "${it.displayArtikel} ${it.word}"
        }
        val collected = mutableListOf<GeneratedWordItem>()
        var attempts = 0
        var consecutiveErrors = 0
        val maxAttempts = 12
        val maxConsecutiveErrors = 4
        var lastError: Exception? = null

        while (collected.size < clampedCount && attempts < maxAttempts) {
            attempts++
            val remaining = clampedCount - collected.size
            val batchTarget = min(35, remaining)
            val requestCount = min(40, batchTarget + 5)
            onProgress(collected.size, clampedCount)

            val avoidList = (existingSample.split(", ").filter { it.isNotBlank() } +
                collected.map { if (it.artikel == "-") it.word else "${it.artikel} ${it.word}" })
                .distinct()
                .take(140)
                .joinToString(", ")

            val systemPrompt = buildSystemPrompt(includeArticleless, activeMeaningLanguageName())
            val userPrompt = buildUserPrompt(
                level = level,
                count = requestCount,
                topicHint = topicHint,
                includeArticleless = includeArticleless,
                avoidWords = avoidList,
                meaningLanguage = activeMeaningLanguageName()
            )

            try {
                val content = callChatCompletions(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                )
                val parsed = parseGeneratedWordsJson(content, includeArticleless = includeArticleless)

                for (item in parsed) {
                    val art = Artikel.parse(item.artikel)
                    val key = Flashcard.keyFor(art, item.word)
                    if (key !in existingKeys && item.word.isNotBlank()) {
                        if (art == null && item.meaning.isBlank()) continue
                        existingKeys.add(key)
                        collected.add(item)
                        if (collected.size >= clampedCount) break
                    }
                }
                consecutiveErrors = 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                consecutiveErrors++
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    if (collected.isNotEmpty()) break else throw e
                }
            }
        }

        if (collected.isEmpty()) {
            throw lastError ?: IllegalStateException(
                localizedText(
                    tr = "AI geçerli kelime üretemedi. Tekrar deneyin.",
                    en = "AI could not generate valid words. Please try again.",
                    de = "Die KI konnte keine gültigen Wörter erstellen. Bitte erneut versuchen."
                )
            )
        }
        collected.take(clampedCount)
    }

    private fun localizedText(tr: String, en: String, de: String): String =
        when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "en" -> en
            "de" -> de
            else -> tr
        }

    private fun activeMeaningLanguageName(): String = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "en" -> "English"
        "de" -> "German"
        else -> "Turkish"
    }

    private fun buildSystemPrompt(includeArticleless: Boolean, meaningLanguage: String): String {
        val articleRule = if (includeArticleless) {
            "For nouns, set \"artikel\" to \"der\", \"die\", or \"das\" and capitalize the German noun. " +
                "You may also include useful verbs, adjectives, or short phrases with \"artikel\": \"-\"."
        } else {
            "Include ONLY German nouns with \"artikel\" strictly set to \"der\", \"die\", or \"das\", and capitalize the German noun."
        }
        return "You are a German vocabulary generator. Return ONLY valid JSON with no markdown or commentary. " +
            "Output format: {\"words\": [{\"artikel\": \"der|die|das${if (includeArticleless) "|-" else ""}\", \"word\": \"GermanWord\", \"meaning\": \"$meaningLanguage meaning\"}]}. " +
            "$articleRule Provide accurate, natural $meaningLanguage meanings/definitions."
    }

    private fun buildUserPrompt(
        level: CEFRLevel,
        count: Int,
        topicHint: String,
        includeArticleless: Boolean,
        avoidWords: String,
        meaningLanguage: String
    ): String {
        val sb = StringBuilder()
        sb.append("Generate $count distinct German vocabulary items for CEFR level ${level.rawValue} (${level.shortSubtitle}) with $meaningLanguage meanings.")
        if (topicHint.trim().isNotEmpty()) {
            sb.append(" Topic focus: ${topicHint.trim()}.")
        }
        if (includeArticleless) {
            sb.append(" Mix German nouns (der/die/das) with useful verbs, adjectives, or phrases (artikel: \"-\").")
        }
        if (avoidWords.isNotEmpty()) {
            sb.append(" Do NOT repeat any of these existing words: $avoidWords.")
        }
        return sb.toString()
    }

    private fun callChatCompletions(
        systemPrompt: String,
        userPrompt: String
    ): String {
        val endpoint = "$PROXY_URL/v1/chat/completions"
        val payload = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0.7)
            put("max_tokens", 4096)
            put("reasoning_effort", "none")
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
            )
        }

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""

            if (status !in 200..299) {
                val detail = extractErrorMessage(body)
                throw IllegalStateException(
                    localizedText(
                        tr = "AI servisi hatası (HTTP $status): $detail",
                        en = "AI service error (HTTP $status): $detail",
                        de = "KI-Dienstfehler (HTTP $status): $detail"
                    )
                )
            }

            val root = JSONObject(body)
            val choices = root.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            message?.optString("content")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    localizedText(
                        tr = "AI servisi boş yanıt döndürdü.",
                        en = "AI service returned an empty response.",
                        de = "KI-Dienst hat eine leere Antwort zurückgegeben."
                    )
                )
        } finally {
            conn.disconnect()
        }
    }

    internal fun extractErrorMessage(body: String): String {
        return runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: body.take(180)
        }.getOrDefault(
            body.take(180).ifBlank {
                localizedText(tr = "Bilinmeyen hata", en = "Unknown error", de = "Unbekannter Fehler")
            }
        )
    }

    /**
     * Parses both `{"words": [...]}` and raw `[...]` JSON arrays, stripping optional markdown fences.
     * Pure & unit-testable on the JVM.
     */
    fun parseGeneratedWordsJson(rawContent: String, includeArticleless: Boolean): List<GeneratedWordItem> {
        var cleaned = rawContent.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
        }

        val array: JSONArray = when {
            cleaned.startsWith("{") -> {
                val obj = runCatching { JSONObject(cleaned) }.getOrNull() ?: return emptyList()
                obj.optJSONArray("words")
                    ?: obj.optJSONArray("items")
                    ?: obj.optJSONArray("vocabulary")
                    ?: return emptyList()
            }
            cleaned.startsWith("[") -> {
                runCatching { JSONArray(cleaned) }.getOrNull() ?: return emptyList()
            }
            else -> {
                val firstBracket = cleaned.indexOf('[')
                val lastBracket = cleaned.lastIndexOf(']')
                if (firstBracket in 0 until lastBracket) {
                    runCatching { JSONArray(cleaned.substring(firstBracket, lastBracket + 1)) }.getOrNull()
                        ?: return emptyList()
                } else {
                    return emptyList()
                }
            }
        }

        val out = mutableListOf<GeneratedWordItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawArtikel = obj.optString("artikel", "").trim()
            val rawWord = obj.optString("word", "").trim()
            val rawMeaning = obj.optString("meaning", "").trim()
            if (rawWord.isEmpty()) continue

            val parsedArt = Artikel.parse(rawArtikel)
            if (parsedArt != null) {
                out.add(GeneratedWordItem(artikel = parsedArt.rawValue, word = rawWord, meaning = rawMeaning))
            } else if (includeArticleless && (rawArtikel == "-" || rawArtikel.isEmpty()) && rawMeaning.isNotEmpty()) {
                out.add(GeneratedWordItem(artikel = ArtikelFilter.NONE, word = rawWord, meaning = rawMeaning))
            }
        }
        return out
    }
}
