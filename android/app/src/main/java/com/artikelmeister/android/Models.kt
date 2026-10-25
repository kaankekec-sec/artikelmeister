package com.artikelmeister.android

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

enum class Artikel(val rawValue: String) {
    DER("der"),
    DIE("die"),
    DAS("das");

    val id: String get() = rawValue

    val color: Color
        get() = when (this) {
            DER -> Color(0.16f, 0.48f, 0.96f) // Blue for masculine
            DIE -> Color(0.90f, 0.22f, 0.35f) // Red/Rose for feminine
            DAS -> Color(0.15f, 0.68f, 0.42f) // Green for neuter
        }

    val badgeBackground: Color
        get() = color.copy(alpha = 0.15f)

    val grammaticalLabel: String
        get() = when (this) {
            DER -> "Maskulin (Eril)"
            DIE -> "Feminin (Dişil)"
            DAS -> "Neutrum (Nötr)"
        }

    companion object {
        fun parse(raw: String): Artikel? {
            return when (raw.trim().lowercase(Locale.ROOT)) {
                "der", "m", "maskulin", "masculine" -> DER
                "die", "f", "feminin", "feminine" -> DIE
                "das", "n", "neutrum", "neuter" -> DAS
                else -> null
            }
        }
    }
}

enum class CardStage(val rawValue: String) {
    NEW("new"),
    LEARNING("learning"),
    REVIEW("review"),
    RELEARNING("relearning");

    val title: String
        get() = when (this) {
            NEW -> "Yeni"
            LEARNING -> "Öğreniliyor"
            REVIEW -> "Tekrar"
            RELEARNING -> "Yeniden Öğreniliyor"
        }

    val color: Color
        get() = when (this) {
            NEW -> Color(0.20f, 0.52f, 0.96f)
            LEARNING -> Color(0.96f, 0.58f, 0.12f)
            REVIEW -> Color(0.18f, 0.72f, 0.40f)
            RELEARNING -> Color(0.92f, 0.24f, 0.26f)
        }

    companion object {
        fun fromRaw(raw: String): CardStage {
            return entries.firstOrNull { it.rawValue.equals(raw.trim(), ignoreCase = true) } ?: NEW
        }
    }
}

enum class ReviewRating(val rawValue: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    val title: String
        get() = when (this) {
            AGAIN -> "Tekrar"
            HARD -> "Zor"
            GOOD -> "İyi"
            EASY -> "Kolay"
        }

    val shortcutKey: String
        get() = rawValue.toString()

    val color: Color
        get() = when (this) {
            AGAIN -> Color(0.90f, 0.25f, 0.25f)
            HARD -> Color(0.92f, 0.55f, 0.15f)
            GOOD -> Color(0.18f, 0.68f, 0.38f)
            EASY -> Color(0.20f, 0.52f, 0.95f)
        }
}

data class Flashcard(
    val id: String = UUID.randomUUID().toString().uppercase(Locale.ROOT),
    val artikel: Artikel,
    val word: String,
    val meaning: String = "",
    val deckName: String = "Genel",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Spaced Repetition (Anki SM-2) state
    val stage: CardStage = CardStage.NEW,
    val easeFactor: Double = 2.5,
    val intervalDays: Double = 0.0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val dueDate: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val totalReviews: Int = 0
) {
    val cleanDeckName: String
        get() = deckName.trim().ifEmpty { "Genel" }

    val fullGermanTerm: String
        get() = "${artikel.rawValue} $word"

    val syncKey: String
        get() = "${artikel.rawValue.lowercase(Locale.ROOT)}_${word.trim().lowercase(Locale.ROOT)}"

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("artikel", artikel.rawValue)
            put("word", word)
            put("meaning", meaning)
            put("deckName", cleanDeckName)
            put("createdAt", IsoDateHelper.format(createdAt))
            put("updatedAt", IsoDateHelper.format(updatedAt))
            put("stage", stage.rawValue)
            put("easeFactor", easeFactor)
            put("intervalDays", intervalDays)
            put("repetitions", repetitions)
            put("lapses", lapses)
            put("dueDate", IsoDateHelper.format(dueDate))
            if (lastReviewedAt != null) {
                put("lastReviewedAt", IsoDateHelper.format(lastReviewedAt))
            }
            put("totalReviews", totalReviews)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): Flashcard? {
            val rawArt = obj.optString("artikel", "")
            val parsedArt = Artikel.parse(rawArt) ?: return null
            val word = obj.optString("word", "").trim()
            if (word.isEmpty()) return null
            val id = obj.optString("id", UUID.randomUUID().toString().uppercase(Locale.ROOT))
            val meaning = obj.optString("meaning", "")
            val rawDeck = obj.optString("deckName", "Genel").trim().ifEmpty { "Genel" }
            val createdAt = IsoDateHelper.parse(obj.opt("createdAt")) ?: System.currentTimeMillis()
            val lastReviewedAt = if (obj.has("lastReviewedAt") && !obj.isNull("lastReviewedAt")) {
                IsoDateHelper.parse(obj.opt("lastReviewedAt"))
            } else null
            val updatedAt = IsoDateHelper.parse(obj.opt("updatedAt")) ?: lastReviewedAt ?: createdAt
            val stage = CardStage.fromRaw(obj.optString("stage", "new"))
            val easeFactor = obj.optDouble("easeFactor", 2.5).let { if (it.isNaN()) 2.5 else it }
            val intervalDays = obj.optDouble("intervalDays", 0.0).let { if (it.isNaN()) 0.0 else it }
            val repetitions = obj.optInt("repetitions", 0)
            val lapses = obj.optInt("lapses", 0)
            val dueDate = IsoDateHelper.parse(obj.opt("dueDate")) ?: System.currentTimeMillis()
            val totalReviews = obj.optInt("totalReviews", 0)

            return Flashcard(
                id = id,
                artikel = parsedArt,
                word = word,
                meaning = meaning,
                deckName = rawDeck,
                createdAt = createdAt,
                updatedAt = updatedAt,
                stage = stage,
                easeFactor = easeFactor,
                intervalDays = intervalDays,
                repetitions = repetitions,
                lapses = lapses,
                dueDate = dueDate,
                lastReviewedAt = lastReviewedAt,
                totalReviews = totalReviews
            )
        }
    }
}

data class AppSettings(
    val dailyNewCardLimit: Int = 20,
    val dailyReviewLimit: Int = 100,
    val autoPlayPronunciation: Boolean = false,
    val showMeaningOnFront: Boolean = false,
    val newCardOrderRandom: Boolean = false,
    val isDarkMode: Boolean = true,
    val customDecks: List<String> = listOf("Genel"),
    val updatedAt: Long = 0L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("dailyNewCardLimit", dailyNewCardLimit)
            put("dailyReviewLimit", dailyReviewLimit)
            put("autoPlayPronunciation", autoPlayPronunciation)
            put("showMeaningOnFront", showMeaningOnFront)
            put("newCardOrderRandom", newCardOrderRandom)
            put("isDarkMode", isDarkMode)
            put("customDecks", JSONArray(customDecks))
            if (updatedAt > 0L) {
                put("updatedAt", IsoDateHelper.format(updatedAt))
            }
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): AppSettings {
            val decksArr = obj.optJSONArray("customDecks")
            val decks = mutableListOf<String>()
            if (decksArr != null) {
                for (i in 0 until decksArr.length()) {
                    val d = decksArr.optString(i, "").trim()
                    if (d.isNotEmpty()) decks.add(d)
                }
            }
            if (decks.isEmpty()) decks.add("Genel")
            val updatedAt = IsoDateHelper.parse(obj.opt("updatedAt")) ?: 0L
            return AppSettings(
                dailyNewCardLimit = obj.optInt("dailyNewCardLimit", 20),
                dailyReviewLimit = obj.optInt("dailyReviewLimit", 100),
                autoPlayPronunciation = obj.optBoolean("autoPlayPronunciation", false),
                showMeaningOnFront = obj.optBoolean("showMeaningOnFront", false),
                newCardOrderRandom = obj.optBoolean("newCardOrderRandom", false),
                isDarkMode = obj.optBoolean("isDarkMode", true),
                customDecks = decks,
                updatedAt = updatedAt
            )
        }
    }
}

data class DailyProgress(
    val dateString: String,
    val newCardsStudied: Int = 0,
    val reviewsCompleted: Int = 0,
    val againCount: Int = 0,
    val hardCount: Int = 0,
    val goodCount: Int = 0,
    val easyCount: Int = 0
) {
    val totalAnswersCount: Int
        get() = againCount + hardCount + goodCount + easyCount

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("dateString", dateString)
            put("newCardsStudied", newCardsStudied)
            put("reviewsCompleted", reviewsCompleted)
            put("againCount", againCount)
            put("hardCount", hardCount)
            put("goodCount", goodCount)
            put("easyCount", easyCount)
        }
    }

    companion object {
        fun todayString(nowMillis: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(nowMillis))
        }

        fun empty(nowMillis: Long = System.currentTimeMillis()): DailyProgress {
            return DailyProgress(dateString = todayString(nowMillis))
        }

        fun fromJson(obj: JSONObject): DailyProgress {
            return DailyProgress(
                dateString = obj.optString("dateString", todayString()),
                newCardsStudied = obj.optInt("newCardsStudied", 0),
                reviewsCompleted = obj.optInt("reviewsCompleted", 0),
                againCount = obj.optInt("againCount", 0),
                hardCount = obj.optInt("hardCount", 0),
                goodCount = obj.optInt("goodCount", 0),
                easyCount = obj.optInt("easyCount", 0)
            )
        }
    }
}

data class ArtikelMeisterBackup(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val includesProgress: Boolean,
    val cards: List<Flashcard>,
    val settings: AppSettings? = null,
    val studyHistory: Map<String, DailyProgress>? = null,
    val deletedCardKeys: Map<String, Long>? = null,
    val userEmail: String? = null
) {
    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("version", version)
            put("exportedAt", IsoDateHelper.format(exportedAt))
            put("includesProgress", includesProgress)
            if (!userEmail.isNullOrBlank()) {
                put("userEmail", userEmail)
            }
            val cardsArray = JSONArray()
            cards.forEach { cardsArray.put(it.toJson()) }
            put("cards", cardsArray)
            if (settings != null) {
                put("settings", settings.toJson())
            }
            if (studyHistory != null) {
                val histObj = JSONObject()
                studyHistory.forEach { (k, v) -> histObj.put(k, v.toJson()) }
                put("studyHistory", histObj)
            }
            if (!deletedCardKeys.isNullOrEmpty()) {
                val delObj = JSONObject()
                deletedCardKeys.forEach { (k, v) -> delObj.put(k, IsoDateHelper.format(v)) }
                put("deletedCardKeys", delObj)
            }
        }
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): ArtikelMeisterBackup {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val exportedAt = IsoDateHelper.parse(root.opt("exportedAt")) ?: System.currentTimeMillis()
            val includesProgress = root.optBoolean("includesProgress", true)
            val userEmail = root.optString("userEmail", "").ifBlank { null }

            val cardsList = mutableListOf<Flashcard>()
            val cardsArr = root.optJSONArray("cards")
            if (cardsArr != null) {
                for (i in 0 until cardsArr.length()) {
                    val obj = cardsArr.optJSONObject(i) ?: continue
                    Flashcard.fromJson(obj)?.let { cardsList.add(it) }
                }
            }

            val settings = root.optJSONObject("settings")?.let { AppSettings.fromJson(it) }
            val historyMap = root.optJSONObject("studyHistory")?.let { histObj ->
                val map = mutableMapOf<String, DailyProgress>()
                val keys = histObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    histObj.optJSONObject(k)?.let { map[k] = DailyProgress.fromJson(it) }
                }
                map
            }
            val deletedMap = root.optJSONObject("deletedCardKeys")?.let { delObj ->
                val map = mutableMapOf<String, Long>()
                val keys = delObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val ts = IsoDateHelper.parse(delObj.opt(k)) ?: 0L
                    if (ts > 0L) map[k] = ts
                }
                map
            }

            return ArtikelMeisterBackup(
                version = version,
                exportedAt = exportedAt,
                includesProgress = includesProgress,
                cards = cardsList,
                settings = settings,
                studyHistory = historyMap,
                deletedCardKeys = deletedMap,
                userEmail = userEmail
            )
        }
    }
}

object IsoDateHelper {
    fun format(millis: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
    }

    fun parse(raw: Any?): Long? {
        if (raw == null) return null
        if (raw is Number) {
            val d = raw.toDouble()
            // Swift CFAbsoluteTime or Unix epoch check
            return if (d > 1_000_000_000_000.0) d.toLong() else (d * 1000.0).toLong()
        }
        val str = raw.toString().trim()
        if (str.isEmpty()) return null
        return try {
            Instant.parse(str).toEpochMilli()
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(str)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Anki SM-2 inspired spaced repetition scheduler (1:1 port of SM2Scheduler in Models.swift)
 */
object SM2Scheduler {
    data class ScheduleResult(
        val updatedCard: Flashcard,
        val intervalDescription: String
    )

    fun previewInterval(
        card: Flashcard,
        rating: ReviewRating,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        return schedule(card, rating, nowMillis).intervalDescription
    }

    fun schedule(
        card: Flashcard,
        rating: ReviewRating,
        nowMillis: Long = System.currentTimeMillis()
    ): ScheduleResult {
        var updated = card.copy(
            updatedAt = nowMillis,
            lastReviewedAt = nowMillis,
            totalReviews = card.totalReviews + 1
        )

        val minuteMs = 60_000L
        val dayMs = 86_400_000L

        when (card.stage) {
            CardStage.NEW, CardStage.LEARNING -> {
                when (rating) {
                    ReviewRating.AGAIN -> {
                        updated = updated.copy(
                            stage = CardStage.LEARNING,
                            repetitions = 0,
                            intervalDays = 0.5 / (24.0 * 60.0), // 30 sec
                            dueDate = nowMillis + 30_000L
                        )
                        return ScheduleResult(updated, "30 sn")
                    }

                    ReviewRating.HARD -> {
                        updated = updated.copy(
                            stage = CardStage.LEARNING,
                            intervalDays = 2.0 / (24.0 * 60.0), // 2 min
                            dueDate = nowMillis + 2 * minuteMs
                        )
                        return ScheduleResult(updated, "2 dk")
                    }

                    ReviewRating.GOOD -> {
                        return if (card.stage == CardStage.NEW && card.repetitions == 0) {
                            updated = updated.copy(
                                stage = CardStage.LEARNING,
                                repetitions = 1,
                                intervalDays = 5.0 / (24.0 * 60.0), // 5 min
                                dueDate = nowMillis + 5 * minuteMs
                            )
                            ScheduleResult(updated, "5 dk")
                        } else {
                            updated = updated.copy(
                                stage = CardStage.REVIEW,
                                repetitions = max(1, card.repetitions + 1),
                                intervalDays = 1.0,
                                dueDate = nowMillis + 1 * dayMs
                            )
                            ScheduleResult(updated, "1 gün")
                        }
                    }

                    ReviewRating.EASY -> {
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            repetitions = max(1, card.repetitions + 1),
                            intervalDays = 4.0,
                            easeFactor = max(1.3, card.easeFactor + 0.15),
                            dueDate = nowMillis + 4 * dayMs
                        )
                        return ScheduleResult(updated, "4 gün")
                    }
                }
            }

            CardStage.RELEARNING -> {
                when (rating) {
                    ReviewRating.AGAIN -> {
                        updated = updated.copy(
                            intervalDays = 0.5 / (24.0 * 60.0),
                            dueDate = nowMillis + 30_000L
                        )
                        return ScheduleResult(updated, "30 sn")
                    }

                    ReviewRating.HARD -> {
                        updated = updated.copy(
                            intervalDays = 2.0 / (24.0 * 60.0),
                            dueDate = nowMillis + 2 * minuteMs
                        )
                        return ScheduleResult(updated, "2 dk")
                    }

                    ReviewRating.GOOD -> {
                        val nextDays = max(1.0, round(card.intervalDays * 0.5))
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            intervalDays = nextDays,
                            dueDate = nowMillis + (nextDays * dayMs).toLong()
                        )
                        return ScheduleResult(updated, formatDays(nextDays))
                    }

                    ReviewRating.EASY -> {
                        val nextDays = max(2.0, round(card.intervalDays * 1.2))
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            intervalDays = nextDays,
                            dueDate = nowMillis + (nextDays * dayMs).toLong()
                        )
                        return ScheduleResult(updated, formatDays(nextDays))
                    }
                }
            }

            CardStage.REVIEW -> {
                val currentInterval = max(1.0, card.intervalDays)
                val elapsedDays: Double = if (card.lastReviewedAt != null) {
                    max(currentInterval, (nowMillis - card.lastReviewedAt).toDouble() / dayMs.toDouble())
                } else {
                    currentInterval
                }

                when (rating) {
                    ReviewRating.AGAIN -> {
                        updated = updated.copy(
                            stage = CardStage.RELEARNING,
                            lapses = card.lapses + 1,
                            repetitions = 0,
                            easeFactor = max(1.3, card.easeFactor - 0.20),
                            intervalDays = max(1.0, round(currentInterval * 0.4)),
                            dueDate = nowMillis + 30_000L
                        )
                        return ScheduleResult(updated, "30 sn")
                    }

                    ReviewRating.HARD -> {
                        val nextDays = min(3650.0, max(currentInterval + 1.0, round(currentInterval * 1.2)))
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            easeFactor = max(1.3, card.easeFactor - 0.15),
                            intervalDays = nextDays,
                            dueDate = nowMillis + (nextDays * dayMs).toLong()
                        )
                        return ScheduleResult(updated, formatDays(nextDays))
                    }

                    ReviewRating.GOOD -> {
                        val bonus = (elapsedDays - currentInterval) * 0.5
                        val nextDays = min(
                            3650.0,
                            max(currentInterval + 1.0, round((currentInterval + bonus) * card.easeFactor))
                        )
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            repetitions = card.repetitions + 1,
                            intervalDays = nextDays,
                            dueDate = nowMillis + (nextDays * dayMs).toLong()
                        )
                        return ScheduleResult(updated, formatDays(nextDays))
                    }

                    ReviewRating.EASY -> {
                        val bonus = (elapsedDays - currentInterval)
                        val nextDays = min(
                            3650.0,
                            max(currentInterval + 2.0, round((currentInterval + bonus) * card.easeFactor * 1.3))
                        )
                        updated = updated.copy(
                            stage = CardStage.REVIEW,
                            repetitions = card.repetitions + 1,
                            easeFactor = max(1.3, card.easeFactor + 0.15),
                            intervalDays = nextDays,
                            dueDate = nowMillis + (nextDays * dayMs).toLong()
                        )
                        return ScheduleResult(updated, formatDays(nextDays))
                    }
                }
            }
        }
    }

    fun formatDays(days: Double): String {
        val intDays = days.roundToInt()
        return when {
            intDays < 30 -> "${max(1, intDays)} gün"
            intDays < 365 -> String.format(Locale("tr", "TR"), "%.1f ay", intDays / 30.0)
            else -> String.format(Locale("tr", "TR"), "%.1f yıl", intDays / 365.0)
        }
    }
}
