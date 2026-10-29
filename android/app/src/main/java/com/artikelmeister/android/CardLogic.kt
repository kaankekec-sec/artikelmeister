package com.artikelmeister.android

import org.json.JSONArray
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Pure (Android-framework free) card logic: lenient JSON decoding, merge / dedup,
 * CSV import/export, answer checking and the article filter. Kept separate from
 * DeckStore so it is unit-testable on the plain JVM.
 */

object CardCodec {
    /** Lenient decode: every bad entry is skipped; never throws. */
    fun decodeArray(arr: JSONArray?, articleless: Boolean): List<Flashcard> {
        if (arr == null) return emptyList()
        val out = mutableListOf<Flashcard>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val card = runCatching { Flashcard.fromJson(obj, articleless) }.getOrNull() ?: continue
            out.add(card)
        }
        return out
    }

    fun encodeArray(cards: List<Flashcard>): JSONArray {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        return arr
    }
}

object CardMerge {
    fun smartMerge(local: Flashcard, incoming: Flashcard): Flashcard {
        val incomingHasMoreProgress = when {
            incoming.totalReviews != local.totalReviews -> incoming.totalReviews > local.totalReviews
            (incoming.lastReviewedAt ?: 0L) != (local.lastReviewedAt ?: 0L) ->
                (incoming.lastReviewedAt ?: 0L) > (local.lastReviewedAt ?: 0L)
            else -> incoming.updatedAt > local.updatedAt
        }
        val progressSource = if (incomingHasMoreProgress) incoming else local

        val mergedMeaning = when {
            local.meaning.isNotBlank() && incoming.meaning.isBlank() -> local.meaning
            incoming.meaning.isNotBlank() && local.meaning.isBlank() -> incoming.meaning
            incoming.updatedAt > local.updatedAt && incoming.meaning.isNotBlank() -> incoming.meaning
            else -> local.meaning
        }

        val mergedDeck = when {
            local.cleanDeckName != "Genel" && incoming.cleanDeckName == "Genel" -> local.cleanDeckName
            incoming.cleanDeckName != "Genel" && local.cleanDeckName == "Genel" -> incoming.cleanDeckName
            incoming.updatedAt > local.updatedAt -> incoming.cleanDeckName
            else -> local.cleanDeckName
        }

        return Flashcard(
            id = local.id,
            artikel = local.artikel,
            word = local.word,
            meaning = mergedMeaning,
            deckName = mergedDeck,
            createdAt = min(local.createdAt, incoming.createdAt),
            updatedAt = max(local.updatedAt, incoming.updatedAt),
            stage = progressSource.stage,
            easeFactor = progressSource.easeFactor,
            intervalDays = progressSource.intervalDays,
            repetitions = progressSource.repetitions,
            lapses = progressSource.lapses,
            dueDate = progressSource.dueDate,
            lastReviewedAt = progressSource.lastReviewedAt,
            totalReviews = progressSource.totalReviews
        )
    }

    /** Collapses duplicates (same syncKey) inside one list. */
    fun dedupe(cards: List<Flashcard>): List<Flashcard> {
        val map = LinkedHashMap<String, Flashcard>()
        for (c in cards) {
            val existing = map[c.syncKey]
            map[c.syncKey] = if (existing == null) c else smartMerge(existing, c)
        }
        return map.values.toList()
    }

    data class MergeResult(val cards: List<Flashcard>, val addedFromIncoming: Int, val overlapping: Int)

    /**
     * Union of local + incoming cards by syncKey (valid and article-less cards share the
     * key space but never collide). Tombstones newer than a card drop it.
     */
    fun merge(local: List<Flashcard>, incoming: List<Flashcard>, tombstones: Map<String, Long>): MergeResult {
        val byKey = LinkedHashMap<String, Flashcard>()
        for (c in local) {
            val key = c.syncKey
            if ((tombstones[key] ?: 0L) > c.updatedAt) continue
            val existing = byKey[key]
            byKey[key] = if (existing == null) c else smartMerge(existing, c)
        }
        var added = 0
        var overlapping = 0
        for (c in incoming) {
            val key = c.syncKey
            if ((tombstones[key] ?: 0L) > c.updatedAt) continue
            val existing = byKey[key]
            if (existing != null) {
                byKey[key] = smartMerge(existing, c)
                overlapping += 1
            } else {
                byKey[key] = c
                added += 1
            }
        }
        return MergeResult(byKey.values.toList(), added, overlapping)
    }
}

object AnswerChecker {
    private val TURKISH = Locale("tr", "TR")
    private val SEPARATORS = Regex("[,;/]")
    private val WHITESPACE = Regex("\\s+")

    /** Trim, collapse inner whitespace, lowercase with Turkish rules (I->i dotless, \u0130->i). */
    fun normalizeMeaning(s: String): String = s.trim().replace(WHITESPACE, " ").lowercase(TURKISH)

    /** Accepted answers for an article-less card: each of the meanings split on , ; / (and the whole text). */
    fun acceptedMeanings(meaning: String): Set<String> {
        val out = linkedSetOf<String>()
        normalizeMeaning(meaning).takeIf { it.isNotEmpty() }?.let { out.add(it) }
        meaning.split(SEPARATORS).map { normalizeMeaning(it) }.filter { it.isNotEmpty() }.forEach { out.add(it) }
        return out
    }

    /**
     * Typed-answer check.
     * - Valid card: typed (trimmed, case-insensitive) must equal der/die/das (unchanged).
     * - Article-less card: typed must equal the card's Turkish MEANING (trimmed, inner whitespace
     *   collapsed, Turkish case rules). If the meaning lists several meanings separated by
     *   comma, semicolon or '/', ANY ONE of them is correct. A blank answer is never correct.
     */
    fun isCorrect(card: Flashcard, typed: String): Boolean {
        val art = card.artikel
        if (art != null) return typed.trim().lowercase(Locale.ROOT) == art.rawValue
        val t = normalizeMeaning(typed)
        return t.isNotEmpty() && t in acceptedMeanings(card.meaning)
    }

    /** Text to show as the correct answer in feedback: article, or the meaning for article-less cards. */
    fun expectedLabel(card: Flashcard): String = card.artikel?.rawValue ?: card.meaning.trim()
}

object ArtikelFilter {
    /** Filter value for article-less words. Other values: "der", "die", "das"; null = all. */
    const val NONE = "-"

    data class Counts(val total: Int, val der: Int, val die: Int, val das: Int, val none: Int) {
        /** Count for a filter value (null/blank = all). */
        fun forFilter(filter: String?): Int = when (filter?.trim()?.lowercase(Locale.ROOT)) {
            null, "" -> total
            "der" -> der
            "die" -> die
            "das" -> das
            NONE -> none
            else -> 0
        }
    }

    fun matches(card: Flashcard, filter: String?): Boolean {
        val f = filter?.trim()?.lowercase(Locale.ROOT)
        return when {
            f.isNullOrEmpty() -> true
            f == NONE -> card.isArticleless
            else -> card.artikel?.rawValue == f
        }
    }

    fun apply(cards: List<Flashcard>, filter: String?): List<Flashcard> = cards.filter { matches(it, filter) }

    fun counts(cards: List<Flashcard>): Counts = Counts(
        total = cards.size,
        der = cards.count { it.artikel == Artikel.DER },
        die = cards.count { it.artikel == Artikel.DIE },
        das = cards.count { it.artikel == Artikel.DAS },
        none = cards.count { it.isArticleless }
    )
}

object CsvCards {
    // ------------------------------------------------------------------ Validation (runs BEFORE any data is touched)

    enum class Rejection { BAD_EXTENSION, EMPTY, TOO_LARGE, BINARY, BAD_ENCODING, NOT_CSV_FORMAT, NO_COLUMNS, NO_VALID_ROWS, TOO_MANY_INVALID }

    /** Thrown when a file is not an importable word list. Nothing has been changed when this is thrown. */
    class RejectedException(val reason: Rejection, message: String) : IllegalArgumentException(message)

    data class Validation(
        val ok: Boolean,
        val reason: Rejection? = null,
        val message: String = "",
        val validRows: Int = 0,
        val invalidRows: Int = 0
    )

    const val MAX_BYTES = 10 * 1024 * 1024
    /** Reject when MORE than this share of the non-empty data rows is invalid (a header row is not counted). */
    const val MAX_INVALID_RATIO = 0.5
    private const val MAX_COLUMNS = 8
    private const val MAX_ARTICLE_CHARS = 12
    private const val MAX_WORD_CHARS = 80
    private const val MAX_WORD_TOKENS = 6
    private const val MAX_MEANING_CHARS = 200
    private const val MAX_DECK_CHARS = 60
    private val WHITESPACE = Regex("\\s+")
    private const val FORBIDDEN_WORD_CHARS = "{}[]<>|\\"

    val ACCEPTED_MIME_TYPES = arrayOf(
        "text/csv", "text/comma-separated-values", "application/csv",
        "text/plain", "application/vnd.ms-excel",
        // Some providers (Drive, file managers) report .csv as octet-stream; the extension check is the real gate.
        "application/octet-stream"
    )

    private fun message(reason: Rejection, valid: Int = 0, invalid: Int = 0): String = when (reason) {
        Rejection.BAD_EXTENSION -> "Lütfen .csv uzantılı bir dosya seçin. Seçilen dosya CSV değil; hiçbir veri değiştirilmedi."
        Rejection.EMPTY -> "Dosya boş; içe aktarılacak kelime bulunamadı. Hiçbir veri değiştirilmedi."
        Rejection.TOO_LARGE -> "Dosya çok büyük (en fazla 10 MB). Hiçbir veri değiştirilmedi."
        Rejection.BINARY -> "Bu dosya bir metin/CSV dosyası değil (resim, PDF gibi ikili içerik algılandı). Hiçbir veri değiştirilmedi."
        Rejection.BAD_ENCODING -> "Dosyanın karakter kodlaması okunamadı. Dosyayı UTF-8 olarak kaydedip tekrar deneyin. Hiçbir veri değiştirilmedi."
        Rejection.NOT_CSV_FORMAT -> "Bu dosya CSV değil (JSON/XML/HTML benzeri içerik). JSON yedekleri için Ayarlar'daki yedek içe aktarmayı kullanın. Hiçbir veri değiştirilmedi."
        Rejection.NO_COLUMNS -> "Dosyada sütun yapısı bulunamadı. Satırlar virgül, noktalı virgül veya sekme ile ayrılmış olmalı: artikel,kelime,anlam,deste. Hiçbir veri değiştirilmedi."
        Rejection.NO_VALID_ROWS -> "Dosyada geçerli kelime satırı bulunamadı. Beklenen biçim: artikel,kelime,anlam,deste. Hiçbir veri değiştirilmedi."
        Rejection.TOO_MANY_INVALID -> "Dosyadaki satırların çoğu geçersiz ($invalid / ${valid + invalid} satır). Doğru bir CSV dosyası seçtiğinizden emin olun. Hiçbir veri değiştirilmedi."
    }

    private fun reject(reason: Rejection): Nothing = throw RejectedException(reason, message(reason))

    /** True when a display name ends in .csv (case-insensitive). */
    fun hasCsvExtension(displayName: String?): Boolean =
        displayName != null && displayName.trim().lowercase(Locale.ROOT).endsWith(".csv")

    /**
     * Throws [RejectedException] (BAD_EXTENSION) unless the name ends with .csv. A null name cannot be
     * checked and is allowed (content validation still applies).
     */
    fun requireCsvName(displayName: String?) {
        if (displayName != null && !hasCsvExtension(displayName)) reject(Rejection.BAD_EXTENSION)
    }

    private fun strictDecode(bytes: ByteArray, charset: java.nio.charset.Charset, offset: Int = 0): String? = try {
        charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }

    /**
     * Decodes raw file bytes: UTF-8, UTF-16 with BOM, or Windows-1254 (Turkish Excel) as fallback.
     * Throws [RejectedException] for empty, oversized, binary or undecodable input.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) reject(Rejection.EMPTY)
        if (bytes.size > MAX_BYTES) reject(Rejection.TOO_LARGE)
        fun b(i: Int) = bytes[i].toInt() and 0xFF
        // BOMs first: UTF-16 text legitimately contains NUL bytes.
        if (bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE) {
            return strictDecode(bytes, Charsets.UTF_16LE, 2) ?: reject(Rejection.BAD_ENCODING)
        }
        if (bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF) {
            return strictDecode(bytes, Charsets.UTF_16BE, 2) ?: reject(Rejection.BAD_ENCODING)
        }
        if (bytes.any { it == 0.toByte() }) reject(Rejection.BINARY)
        strictDecode(bytes, Charsets.UTF_8)?.let { return it }
        val cp1254 = java.nio.charset.Charset.forName("windows-1254")
        return strictDecode(bytes, cp1254) ?: reject(Rejection.BAD_ENCODING)
    }

    /**
     * A row is plausible vocabulary data: 2..8 columns, a short single-token article column, a short
     * word without markup or sentence punctuation, and sane meaning/deck lengths. Used by both
     * validation and import so garbage rows inside an accepted file are skipped, not imported.
     */
    fun isPlausibleRow(columns: List<String>): Boolean {
        if (columns.size < 2 || columns.size > MAX_COLUMNS) return false
        val article = columns[0].trim()
        val word = columns[1].trim()
        if (word.isEmpty()) return false
        if (article.length > MAX_ARTICLE_CHARS || article.any { it.isWhitespace() }) return false
        if (word.length > MAX_WORD_CHARS) return false
        if (word.split(WHITESPACE).size > MAX_WORD_TOKENS) return false
        if (word.any { it in FORBIDDEN_WORD_CHARS }) return false
        if (word.last() in ".!?;:") return false
        if (columns.size >= 3 && columns[2].length > MAX_MEANING_CHARS) return false
        if (columns.size >= 4 && columns[3].length > MAX_DECK_CHARS) return false
        return true
    }

    /** Validates decoded text. Pure; never throws. */
    fun validate(content: String): Validation {
        fun fail(r: Rejection, v: Int = 0, i: Int = 0) = Validation(false, r, message(r, v, i), v, i)
        val text = content.replace("﻿", "")
        if (text.isBlank()) return fail(Rejection.EMPTY)
        if (text.length > MAX_BYTES) return fail(Rejection.TOO_LARGE)

        var nonPrintable = 0
        for (ch in text) {
            val bad = ch == '\u0000' || ch == '�' || ch == '\u007F' ||
                (ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t')
            if (bad) nonPrintable++
        }
        if (text.contains('\u0000') || nonPrintable.toDouble() / text.length > 0.01) return fail(Rejection.BINARY)

        val first = text.trimStart()[0]
        if (first == '{' || first == '[' || first == '<') return fail(Rejection.NOT_CSV_FORMAT)

        var valid = 0
        var invalid = 0
        var multiColumn = 0
        for ((lineIdx, raw) in text.split("\r\n", "\n", "\r").withIndex()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val columns = parseRow(line)
            if (columns.size >= 2) multiColumn++
            if (lineIdx == 0 && columns.size >= 2 && isHeader(columns[0], columns[1].trim())) continue
            if (isPlausibleRow(columns)) valid++ else invalid++
        }
        if (valid + invalid == 0) return fail(Rejection.EMPTY)
        if (multiColumn == 0) return fail(Rejection.NO_COLUMNS, valid, invalid)
        if (valid == 0) return fail(Rejection.NO_VALID_ROWS, valid, invalid)
        if (invalid.toDouble() / (valid + invalid) > MAX_INVALID_RATIO) return fail(Rejection.TOO_MANY_INVALID, valid, invalid)
        return Validation(true, null, "", valid, invalid)
    }

    /** Validates and throws [RejectedException] on rejection. */
    fun requireValid(content: String): Validation {
        val v = validate(content)
        if (!v.ok) throw RejectedException(v.reason!!, v.message)
        return v
    }

    /** Decode + validate raw bytes (the extension is checked separately). Returns the decoded text. */
    fun decodeAndValidate(bytes: ByteArray): String {
        val text = decode(bytes)
        requireValid(text)
        return text
    }

    /** Bundled starter pack (must always pass [validate]). */
    val STARTER_PACK_CSV: String = """
        artikel,kelime,anlam,deste
        der,Tisch,Masa,A1 Temel
        die,Katze,Kedi,A1 Temel
        das,Buch,Kitap,A1 Temel
        der,Stuhl,Sandalye,A1 Temel
        die,Sonne,Güneş,A1 Temel
        das,Haus,Ev,A1 Temel
        der,Apfel,Elma,Yiyecek & İçecek
        die,Blume,Çiçek,Doğa
        das,Auto,Araba,Ulaşım
        der,Hund,Köpek,A1 Temel
        die,Schule,Okul,Eğitim
        das,Wasser,Su,Yiyecek & İçecek
        der,Baum,Ağaç,Doğa
        die,Tür,Kapı,A1 Temel
        das,Fenster,Pencere,A1 Temel
        der,Schlüssel,Anahtar,Günlük Eşyalar
        die,Tasche,Çanta,Günlük Eşyalar
        das,Kind,Çocuk,A1 Temel
        der,Zug,Tren,Ulaşım
        die,Stadt,Şehir,Ulaşım
        das,Mädchen,Kız çocuk,A1 Temel
        der,Kaffee,Kahve,Yiyecek & İçecek
        die,Milch,Süt,Yiyecek & İçecek
        das,Brot,Ekmek,Yiyecek & İçecek
        der,Bahnhof,Tren istasyonu,Ulaşım
        die,Zeitung,Gazete,Günlük Eşyalar
        das,Handy,Cep telefonu,Günlük Eşyalar
        der,Computer,Bilgisayar,Günlük Eşyalar
        die,Uhr,Saat,Günlük Eşyalar
        das,Zimmer,Oda,A1 Temel
    """.trimIndent()

    // ------------------------------------------------------------------ Undo (pure)

    /** What an import changed, so it can be undone precisely without touching later edits. */
    class UndoRecord(
        val addedKeys: Set<String>,
        /** Cards the import modified, keyed by syncKey, as they were BEFORE the import. */
        val previousCards: Map<String, Flashcard>,
        val previousTombstones: Map<String, Long>
    )

    /** Returns null when the import changed nothing. [before] must be the list passed to [importContent]. */
    fun buildUndo(before: List<Flashcard>, outcome: ImportOutcome, previousTombstones: Map<String, Long>): UndoRecord? {
        if (outcome.added == 0 && outcome.updated == 0) return null
        val previous = HashMap<String, Flashcard>()
        for (i in before.indices) {
            if (outcome.cards[i] != before[i]) previous[before[i].syncKey] = before[i]
        }
        return UndoRecord(outcome.addedKeys, previous, previousTombstones)
    }

    /**
     * Removes cards the import added (tombstoned with [now] so cloud sync cannot bring them back), restores the
     * previous meaning/deck of updated cards (with updatedAt = [now] so the old values win the next cloud merge)
     * and restores tombstones the import had cleared. Cards and tombstones changed after the import are kept.
     */
    fun applyUndo(
        undo: UndoRecord,
        cards: List<Flashcard>,
        tombstones: Map<String, Long>,
        now: Long
    ): Pair<List<Flashcard>, Map<String, Long>> {
        val tomb = tombstones.toMutableMap()
        val kept = ArrayList<Flashcard>(cards.size)
        for (card in cards) {
            val key = card.syncKey
            val prev = undo.previousCards[key]
            when {
                key in undo.addedKeys -> tomb[key] = now
                prev != null -> kept.add(card.copy(meaning = prev.meaning, deckName = prev.deckName, updatedAt = now))
                else -> kept.add(card)
            }
        }
        for ((k, ts) in undo.previousTombstones) {
            if (k !in undo.addedKeys) tomb.putIfAbsent(k, ts)
        }
        return Pair(kept, tomb)
    }

    // ------------------------------------------------------------------ Import

    data class ImportOutcome(
        val cards: List<Flashcard>,
        val added: Int,
        val updated: Int,
        val skipped: Int,
        val invalid: Int,
        /** syncKeys of newly added cards (caller clears matching tombstones). */
        val addedKeys: Set<String>
    )

    fun importContent(
        content: String,
        fallbackDeck: String,
        existing: List<Flashcard>,
        now: Long = System.currentTimeMillis()
    ): ImportOutcome {
        val lines = content.split("\r\n", "\n", "\r")
        var added = 0
        var updated = 0
        var skipped = 0
        var invalid = 0
        val addedKeys = linkedSetOf<String>()
        val fallback = fallbackDeck.trim().ifEmpty { "Genel" }
        val cards = existing.toMutableList()
        val indexByKey = HashMap<String, Int>()
        cards.forEachIndexed { i, c -> indexByKey.putIfAbsent(c.syncKey, i) }

        for ((lineIdx, rawLine) in lines.withIndex()) {
            // trimEnd only: a leading tab/comma means "empty article column".
            val line = rawLine.replace("﻿", "").trimEnd()
            if (line.isBlank()) continue

            val columns = parseRow(line)
            if (columns.size < 2) {
                invalid += 1
                continue
            }
            val rawArtikel = columns[0]
            val cleanWord = columns[1].trim()
            if (lineIdx == 0 && isHeader(rawArtikel, cleanWord)) continue
            if (!isPlausibleRow(columns)) {
                invalid += 1
                continue
            }
            val cleanMeaning = if (columns.size >= 3) columns[2].trim() else ""
            val cleanDeck = (if (columns.size >= 4) columns[3] else fallback).trim().ifEmpty { fallback }

            // Empty OR not der/die/das (or an old alias m/f/n/...) -> article-less word.
            val artikel = Artikel.parse(rawArtikel)
            if (cleanWord.isEmpty()) {
                invalid += 1
                continue
            }

            val key = Flashcard.keyFor(artikel, cleanWord)
            val idx = indexByKey[key]
            if (idx != null) {
                var card = cards[idx]
                var didUpdate = false
                if (cleanMeaning.isNotEmpty() && card.meaning != cleanMeaning) {
                    card = card.copy(meaning = cleanMeaning)
                    didUpdate = true
                }
                if (cleanDeck != "Genel" && card.deckName != cleanDeck) {
                    card = card.copy(deckName = cleanDeck)
                    didUpdate = true
                }
                if (didUpdate) {
                    cards[idx] = card.copy(updatedAt = now)
                    updated += 1
                } else {
                    skipped += 1
                }
            } else {
                val card = Flashcard(
                    artikel = artikel,
                    word = cleanWord,
                    meaning = cleanMeaning,
                    deckName = cleanDeck,
                    createdAt = now,
                    updatedAt = now
                )
                cards.add(card)
                indexByKey[key] = cards.size - 1
                addedKeys.add(key)
                added += 1
            }
        }
        return ImportOutcome(cards, added, updated, skipped, invalid, addedKeys)
    }

    private fun isHeader(rawArtikel: String, word: String): Boolean {
        val a = rawArtikel.trim().lowercase(Locale.ROOT)
        val w = word.trim().lowercase(Locale.ROOT)
        return a == "artikel" || a == "article" ||
            (Artikel.parse(a) == null && w in setOf("kelime", "word", "wort"))
    }

    fun exportString(cards: List<Flashcard>): String {
        fun esc(s: String) = if (s.contains(",")) "\"$s\"" else s
        val rows = mutableListOf("artikel,kelime,anlam,deste")
        for (card in cards) {
            // Article-less words are written with an EMPTY article column.
            rows.add("${card.artikel?.rawValue ?: ""},${esc(card.word)},${esc(card.meaning)},${esc(card.deckName)}")
        }
        return rows.joinToString("\n")
    }

    fun parseRow(line: String): List<String> {
        val delimiter: Char = run {
            var inQuotes = false
            var commaCount = 0
            var semicolonCount = 0
            var tabCount = 0
            for (ch in line) {
                if (ch == '"') inQuotes = !inQuotes
                if (!inQuotes) {
                    when (ch) {
                        ',' -> commaCount++
                        ';' -> semicolonCount++
                        '\t' -> tabCount++
                    }
                }
            }
            when {
                semicolonCount > commaCount && semicolonCount >= tabCount -> ';'
                tabCount > commaCount && tabCount > semicolonCount -> '\t'
                else -> ','
            }
        }

        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString().trim())
        return fields
    }
}
