package com.artikelmeister.android

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class CSVImportResult(
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val invalidLines: Int
)

data class HeatmapDayItem(
    val dateString: String,
    val displayDate: String,
    val count: Int
) {
    val id: String get() = dateString
}

class DeckStore(private val appContext: Context) {
    var cards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var settings by mutableStateOf(AppSettings())
        private set

    var dailyProgress by mutableStateOf(DailyProgress.empty())
        private set

    var studyHistory by mutableStateOf<Map<String, DailyProgress>>(emptyMap())
        private set

    var studyQueue by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var deletedCardKeys by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    var isSyncingCloud by mutableStateOf(false)
        private set

    var lastCloudSyncTime by mutableStateOf<Long?>(null)
        private set

    var cloudSyncStatusMessage by mutableStateOf<String?>(null)

    var cloudSyncIsError by mutableStateOf(false)

    var currentTime by mutableLongStateOf(System.currentTimeMillis())
        private set

    private var _selectedStudyDeck by mutableStateOf<String?>(null)
    var selectedStudyDeck: String?
        get() = _selectedStudyDeck
        set(value) {
            _selectedStudyDeck = value
            rebuildStudyQueue()
        }

    private var sessionStepCount: Int = 0
    private val cardDueStepMap = mutableMapOf<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var autoSyncJob: Job? = null

    val storageDirectory: File = File(appContext.filesDir, "ArtikelMeister")
    val importedCSVsDirectory: File = File(storageDirectory, "ImportedCSVs")
    val backupsDirectory: File = File(storageDirectory, "Backups")
    private val cardsFile = File(storageDirectory, "cards.json")
    private val settingsFile = File(storageDirectory, "settings.json")
    private val progressFile = File(storageDirectory, "daily_progress.json")
    private val historyFile = File(storageDirectory, "study_history.json")
    private val deletedCardsFile = File(storageDirectory, "deleted_cards.json")

    init {
        ensureDirectoriesExist()
        loadAll()
        refreshDailyRollover()
        rebuildStudyQueue()
        startLiveDueTimer()
    }

    private fun startLiveDueTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                currentTime = now

                val hasNewlyDueLearning = filteredPoolCards.any { card ->
                    (card.stage == CardStage.LEARNING || card.stage == CardStage.RELEARNING) &&
                        card.dueDate <= now &&
                        studyQueue.none { it.id == card.id }
                }

                if (hasNewlyDueLearning) {
                    rebuildStudyQueue(now = now, preserveCurrentFrontCard = studyQueue.isNotEmpty())
                }
            }
        }
    }

    private fun ensureDirectoriesExist() {
        runCatching {
            storageDirectory.mkdirs()
            importedCSVsDirectory.mkdirs()
            backupsDirectory.mkdirs()
        }
    }

    // MARK: - Persistence

    fun loadAll() {
        ensureDirectoriesExist()

        if (settingsFile.exists()) {
            runCatching {
                val obj = JSONObject(settingsFile.readText(Charsets.UTF_8))
                settings = AppSettings.fromJson(obj)
            }.onFailure {
                saveSettingsFileOnly()
            }
        } else {
            saveSettingsFileOnly()
        }

        if (progressFile.exists()) {
            runCatching {
                val obj = JSONObject(progressFile.readText(Charsets.UTF_8))
                dailyProgress = DailyProgress.fromJson(obj)
            }.onFailure {
                saveProgress()
            }
        } else {
            saveProgress()
        }

        if (historyFile.exists()) {
            runCatching {
                val obj = JSONObject(historyFile.readText(Charsets.UTF_8))
                val map = mutableMapOf<String, DailyProgress>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    obj.optJSONObject(k)?.let { map[k] = DailyProgress.fromJson(it) }
                }
                studyHistory = map
            }.onFailure {
                saveHistory()
            }
        } else {
            if (dailyProgress.totalAnswersCount > 0) {
                studyHistory = studyHistory + (dailyProgress.dateString to dailyProgress)
            }
            saveHistory()
        }

        if (cardsFile.exists()) {
            runCatching {
                val arr = JSONArray(cardsFile.readText(Charsets.UTF_8))
                val loaded = mutableListOf<Flashcard>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    Flashcard.fromJson(obj)?.let { loaded.add(it) }
                }
                cards = loaded
            }.onFailure {
                saveCards(triggerCloudSync = false)
            }
        } else {
            saveCards(triggerCloudSync = false)
        }

        if (deletedCardsFile.exists()) {
            runCatching {
                val obj = JSONObject(deletedCardsFile.readText(Charsets.UTF_8))
                val map = mutableMapOf<String, Long>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val ts = IsoDateHelper.parse(obj.opt(k)) ?: 0L
                    if (ts > 0L) map[k] = ts
                }
                deletedCardKeys = map
            }.onFailure {
                saveDeletedCardsFileOnly()
            }
        }
    }

    private fun saveDeletedCardsFileOnly() {
        ensureDirectoriesExist()
        runCatching {
            val obj = JSONObject()
            deletedCardKeys.forEach { (k, v) -> obj.put(k, IsoDateHelper.format(v)) }
            deletedCardsFile.writeText(obj.toString(2), Charsets.UTF_8)
        }
    }

    private fun saveSettingsFileOnly() {
        ensureDirectoriesExist()
        runCatching {
            settingsFile.writeText(settings.toJson().toString(2), Charsets.UTF_8)
        }
    }

    fun saveCards(triggerCloudSync: Boolean = true) {
        ensureDirectoriesExist()
        runCatching {
            val arr = JSONArray()
            cards.forEach { arr.put(it.toJson()) }
            cardsFile.writeText(arr.toString(2), Charsets.UTF_8)
        }
        if (triggerCloudSync) {
            scheduleAutoCloudSync()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings).copy(updatedAt = System.currentTimeMillis())
        saveSettingsFileOnly()
        rebuildStudyQueue()
        scheduleAutoCloudSync()
    }

    fun saveSettings() {
        settings = settings.copy(updatedAt = System.currentTimeMillis())
        saveSettingsFileOnly()
        rebuildStudyQueue()
        scheduleAutoCloudSync()
    }

    fun saveProgress(triggerCloudSync: Boolean = true) {
        ensureDirectoriesExist()
        studyHistory = studyHistory + (dailyProgress.dateString to dailyProgress)
        runCatching {
            progressFile.writeText(dailyProgress.toJson().toString(2), Charsets.UTF_8)
        }
        saveHistory()
        if (triggerCloudSync) {
            scheduleAutoCloudSync()
        }
    }

    fun saveHistory() {
        ensureDirectoriesExist()
        runCatching {
            val obj = JSONObject()
            studyHistory.forEach { (k, v) -> obj.put(k, v.toJson()) }
            historyFile.writeText(obj.toString(2), Charsets.UTF_8)
        }
    }

    fun refreshDailyRollover(now: Long = System.currentTimeMillis()) {
        val today = DailyProgress.todayString(now)
        if (dailyProgress.dateString != today) {
            val updatedHist = studyHistory.toMutableMap()
            updatedHist[dailyProgress.dateString] = dailyProgress
            studyHistory = updatedHist
            dailyProgress = studyHistory[today] ?: DailyProgress.empty(now)
            saveProgress()
        }
    }

    // MARK: - Decks / Categories

    val allDeckNames: List<String>
        get() {
            val set = mutableSetOf("Genel")
            for (custom in settings.customDecks) {
                val trimmed = custom.trim()
                if (trimmed.isNotEmpty()) set.add(trimmed)
            }
            for (c in cards) {
                val trimmed = c.deckName.trim()
                if (trimmed.isNotEmpty()) set.add(trimmed)
            }
            val collator = Collator.getInstance(Locale("tr", "TR"))
            return set.sortedWith { a, b ->
                when {
                    a == "Genel" && b == "Genel" -> 0
                    a == "Genel" -> -1
                    b == "Genel" -> 1
                    else -> collator.compare(a.lowercase(Locale("tr", "TR")), b.lowercase(Locale("tr", "TR")))
                }
            }
        }

    fun cardCount(forDeck: String?): Int {
        if (forDeck == null) return cards.size
        return cards.count { it.deckName == forDeck }
    }

    fun createDeck(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        if (settings.customDecks.none { it.equals(clean, ignoreCase = true) }) {
            settings = settings.copy(
                customDecks = settings.customDecks + clean,
                updatedAt = System.currentTimeMillis()
            )
            saveSettingsFileOnly()
            scheduleAutoCloudSync()
        }
        return allDeckNames.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
    }

    fun renameDeck(oldName: String, newName: String) {
        val cleanNew = newName.trim()
        if (cleanNew.isEmpty() || oldName == cleanNew) return
        val now = System.currentTimeMillis()

        val updatedCustom = settings.customDecks
            .filterNot { it.equals(oldName, ignoreCase = true) }
            .toMutableList()
        if (updatedCustom.none { it.equals(cleanNew, ignoreCase = true) }) {
            updatedCustom.add(cleanNew)
        }
        settings = settings.copy(customDecks = updatedCustom, updatedAt = now)

        cards = cards.map { card ->
            if (card.deckName == oldName) card.copy(deckName = cleanNew, updatedAt = now) else card
        }

        if (_selectedStudyDeck == oldName) {
            _selectedStudyDeck = cleanNew
        }

        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard = true)
    }

    fun deleteDeck(name: String, deleteWords: Boolean = false) {
        if (name == "Genel") return
        val now = System.currentTimeMillis()
        val updatedCustom = settings.customDecks.filterNot { it.equals(name, ignoreCase = true) }
        settings = settings.copy(customDecks = updatedCustom, updatedAt = now)

        if (deleteWords) {
            val removed = cards.filter { it.deckName == name }
            if (removed.isNotEmpty()) {
                val updatedDeleted = deletedCardKeys.toMutableMap()
                removed.forEach { updatedDeleted[it.syncKey] = now }
                deletedCardKeys = updatedDeleted
                saveDeletedCardsFileOnly()
            }
            cards = cards.filterNot { it.deckName == name }
        } else {
            cards = cards.map { card ->
                if (card.deckName == name) card.copy(deckName = "Genel", updatedAt = now) else card
            }
        }

        if (_selectedStudyDeck == name) {
            _selectedStudyDeck = null
        }

        saveSettingsFileOnly()
        saveCards()
        rebuildStudyQueue()
    }

    val filteredPoolCards: List<Flashcard>
        get() {
            val deck = _selectedStudyDeck ?: return cards
            return cards.filter { it.deckName == deck }
        }

    // MARK: - Streak & Heatmap Calculations

    val currentStreakDays: Int
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            var streak = 0
            var checkCal = cal.clone() as Calendar

            val todayKey = sdf.format(checkCal.time)
            val todayCount = (studyHistory[todayKey]?.totalAnswersCount ?: 0) +
                (if (dailyProgress.dateString == todayKey) dailyProgress.totalAnswersCount else 0)

            if (todayCount == 0) {
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
                val yKey = sdf.format(checkCal.time)
                if ((studyHistory[yKey]?.totalAnswersCount ?: 0) == 0) {
                    return 0
                }
            }

            while (true) {
                val key = sdf.format(checkCal.time)
                val count = if (key == dailyProgress.dateString) {
                    max(dailyProgress.totalAnswersCount, studyHistory[key]?.totalAnswersCount ?: 0)
                } else {
                    studyHistory[key]?.totalAnswersCount ?: 0
                }
                if (count > 0) {
                    streak += 1
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
            return streak
        }

    val longestStreakDays: Int
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val activeEpochDays = mutableSetOf<Long>()

            for ((dateStr, prog) in studyHistory) {
                if (prog.totalAnswersCount > 0) {
                    val parsed = runCatching { sdf.parse(dateStr) }.getOrNull()
                    if (parsed != null) {
                        activeEpochDays.add(parsed.time / 86_400_000L)
                    }
                }
            }
            if (dailyProgress.totalAnswersCount > 0) {
                val parsed = runCatching { sdf.parse(dailyProgress.dateString) }.getOrNull()
                if (parsed != null) {
                    activeEpochDays.add(parsed.time / 86_400_000L)
                }
            }

            val sorted = activeEpochDays.sorted()
            if (sorted.isEmpty()) return 0

            var maxStreak = 1
            var current = 1
            for (i in 1 until sorted.size) {
                val diff = sorted[i] - sorted[i - 1]
                if (diff == 1L) {
                    current += 1
                    maxStreak = max(maxStreak, current)
                } else if (diff > 1L) {
                    current = 1
                }
            }
            return max(maxStreak, currentStreakDays)
        }

    fun heatmapDays(daysCount: Int = 35): List<HeatmapDayItem> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormatter = SimpleDateFormat("d MMM", Locale("tr", "TR"))

        val items = mutableListOf<HeatmapDayItem>()
        for (offset in -(daysCount - 1)..0) {
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, offset)
            val date = dayCal.time
            val key = keyFormatter.format(date)
            val disp = displayFormatter.format(date)
            val count = if (key == dailyProgress.dateString) {
                max(dailyProgress.totalAnswersCount, studyHistory[key]?.totalAnswersCount ?: 0)
            } else {
                studyHistory[key]?.totalAnswersCount ?: 0
            }
            items.add(HeatmapDayItem(dateString = key, displayDate = disp, count = count))
        }
        return items
    }

    // MARK: - Study Queue & Anki Scheduling

    val remainingNewToday: Int
        get() {
            refreshDailyRollover()
            return max(0, settings.dailyNewCardLimit - dailyProgress.newCardsStudied)
        }

    val remainingReviewsToday: Int
        get() {
            refreshDailyRollover()
            return max(0, settings.dailyReviewLimit - dailyProgress.reviewsCompleted)
        }

    val dueLearningCardsCount: Int
        get() = filteredPoolCards.count { it.stage == CardStage.LEARNING || it.stage == CardStage.RELEARNING }

    val dueReviewCardsCount: Int
        get() {
            val now = System.currentTimeMillis()
            val totalDue = filteredPoolCards.count { it.stage == CardStage.REVIEW && it.dueDate <= now }
            return min(remainingReviewsToday, totalDue)
        }

    val availableNewCardsCount: Int
        get() {
            val totalNew = filteredPoolCards.count { it.stage == CardStage.NEW }
            return min(remainingNewToday, totalNew)
        }

    val nextPendingLearningCard: Flashcard?
        get() {
            val now = currentTime
            return filteredPoolCards
                .filter { (it.stage == CardStage.LEARNING || it.stage == CardStage.RELEARNING) && it.dueDate > now }
                .minByOrNull { it.dueDate }
        }

    fun forcePullNextLearningCard() {
        val now = System.currentTimeMillis()
        val nextCard = nextPendingLearningCard ?: return
        val idx = cards.indexOfFirst { it.id == nextCard.id }
        if (idx == -1) return
        val mutable = cards.toMutableList()
        mutable[idx] = mutable[idx].copy(dueDate = now)
        cards = mutable
        cardDueStepMap.remove(nextCard.id)
        saveCards()
        rebuildStudyQueue(now = now)
    }

    private fun isLearningCardDue(card: Flashcard, now: Long): Boolean {
        if (card.stage != CardStage.LEARNING && card.stage != CardStage.RELEARNING) return false
        if (card.dueDate <= now) return true
        val targetStep = cardDueStepMap[card.id]
        if (targetStep != null && sessionStepCount >= targetStep) {
            return true
        }
        return false
    }

    fun rebuildStudyQueue(
        now: Long = System.currentTimeMillis(),
        preserveCurrentFrontCard: Boolean = false
    ) {
        refreshDailyRollover(now = now)
        val pool = filteredPoolCards
        val currentFront = if (preserveCurrentFrontCard) studyQueue.firstOrNull() else null

        // 1. Due learning / relearning cards ALWAYS go at the very front!
        val learningDueNow = pool
            .filter { isLearningCardDue(it, now) }
            .sortedBy { it.dueDate }

        // 2. Due review cards (up to remaining daily review quota)
        val reviewsDue = pool
            .filter { it.stage == CardStage.REVIEW && it.dueDate <= now }
            .sortedBy { it.dueDate }
            .take(remainingReviewsToday)

        // 3. New cards (up to remaining daily new quota)
        val allNew = pool.filter { it.stage == CardStage.NEW }.let { list ->
            if (settings.newCardOrderRandom) list.shuffled() else list.sortedBy { it.createdAt }
        }
        val newSlice = allNew.take(remainingNewToday)

        val combined = mutableListOf<Flashcard>()
        combined.addAll(learningDueNow)

        var rIndex = 0
        var nIndex = 0
        while (rIndex < reviewsDue.size || nIndex < newSlice.size) {
            var count = 0
            while (count < 3 && rIndex < reviewsDue.size) {
                combined.add(reviewsDue[rIndex])
                rIndex++
                count++
            }
            if (nIndex < newSlice.size) {
                combined.add(newSlice[nIndex])
                nIndex++
            }
        }

        if (currentFront != null) {
            val latestFront = pool.firstOrNull { it.id == currentFront.id }
            if (latestFront != null) {
                combined.removeAll { it.id == latestFront.id }
                combined.add(0, latestFront)
            }
        }

        studyQueue = combined
    }

    fun answerCard(card: Flashcard, rating: ReviewRating) {
        val now = System.currentTimeMillis()
        refreshDailyRollover(now = now)
        sessionStepCount += 1

        val index = cards.indexOfFirst { it.id == card.id }
        if (index == -1) return
        val previousStage = cards[index].stage

        val result = SM2Scheduler.schedule(card = cards[index], rating = rating, nowMillis = now)
        val mutableCards = cards.toMutableList()
        mutableCards[index] = result.updatedCard
        cards = mutableCards

        if (result.updatedCard.stage == CardStage.LEARNING || result.updatedCard.stage == CardStage.RELEARNING) {
            val otherActiveCardsCount = studyQueue.count { it.id != card.id }
            if (otherActiveCardsCount == 0) {
                cardDueStepMap.remove(card.id)
            } else {
                when (rating) {
                    ReviewRating.AGAIN -> cardDueStepMap[card.id] = sessionStepCount + min(2, otherActiveCardsCount)
                    ReviewRating.HARD -> cardDueStepMap[card.id] = sessionStepCount + min(5, otherActiveCardsCount)
                    ReviewRating.GOOD -> cardDueStepMap[card.id] = sessionStepCount + min(10, otherActiveCardsCount)
                    ReviewRating.EASY -> cardDueStepMap.remove(card.id)
                }
            }
        } else {
            cardDueStepMap.remove(card.id)
        }

        var updatedProg = dailyProgress
        if (previousStage == CardStage.NEW) {
            updatedProg = updatedProg.copy(newCardsStudied = updatedProg.newCardsStudied + 1)
        } else if (previousStage == CardStage.REVIEW) {
            updatedProg = updatedProg.copy(reviewsCompleted = updatedProg.reviewsCompleted + 1)
        }

        updatedProg = when (rating) {
            ReviewRating.AGAIN -> updatedProg.copy(againCount = updatedProg.againCount + 1)
            ReviewRating.HARD -> updatedProg.copy(hardCount = updatedProg.hardCount + 1)
            ReviewRating.GOOD -> updatedProg.copy(goodCount = updatedProg.goodCount + 1)
            ReviewRating.EASY -> updatedProg.copy(easyCount = updatedProg.easyCount + 1)
        }
        dailyProgress = updatedProg

        saveCards()
        saveProgress()
        rebuildStudyQueue(now = now, preserveCurrentFrontCard = false)
    }

    // MARK: - Word CRUD & Editing

    fun addCard(
        artikel: Artikel,
        word: String,
        meaning: String,
        deckName: String = "Genel"
    ): Boolean {
        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        val cleanDeck = deckName.trim().ifEmpty { "Genel" }
        if (cleanWord.isEmpty()) return false
        val now = System.currentTimeMillis()

        val existingIdx = cards.indexOfFirst {
            it.artikel == artikel && it.word.equals(cleanWord, ignoreCase = true)
        }
        if (existingIdx != -1) {
            var existing = cards[existingIdx]
            var changed = false
            if (cleanMeaning.isNotEmpty() && existing.meaning.isEmpty()) {
                existing = existing.copy(meaning = cleanMeaning, updatedAt = now)
                changed = true
            }
            if (cleanDeck != "Genel" && existing.deckName == "Genel") {
                existing = existing.copy(deckName = cleanDeck, updatedAt = now)
                changed = true
            }
            if (changed) {
                val mutable = cards.toMutableList()
                mutable[existingIdx] = existing
                cards = mutable
                saveCards()
                rebuildStudyQueue()
            }
            return false
        }

        val newCard = Flashcard(
            artikel = artikel,
            word = cleanWord,
            meaning = cleanMeaning,
            deckName = cleanDeck,
            createdAt = now,
            updatedAt = now
        )
        if (deletedCardKeys.containsKey(newCard.syncKey)) {
            deletedCardKeys = deletedCardKeys - newCard.syncKey
            saveDeletedCardsFileOnly()
        }
        cards = cards + newCard
        saveCards()
        rebuildStudyQueue()
        return true
    }

    fun updateCard(
        id: String,
        artikel: Artikel,
        word: String,
        meaning: String,
        deckName: String
    ) {
        val idx = cards.indexOfFirst { it.id == id }
        if (idx == -1) return
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return
        val cleanMeaning = meaning.trim()
        val cleanDeck = deckName.trim().ifEmpty { "Genel" }
        val now = System.currentTimeMillis()

        val mutable = cards.toMutableList()
        mutable[idx] = mutable[idx].copy(
            artikel = artikel,
            word = cleanWord,
            meaning = cleanMeaning,
            deckName = cleanDeck,
            updatedAt = now
        )
        cards = mutable
        saveCards()
        rebuildStudyQueue(preserveCurrentFrontCard = true)
    }

    fun deleteCard(id: String) {
        val target = cards.firstOrNull { it.id == id }
        if (target != null) {
            deletedCardKeys = deletedCardKeys + (target.syncKey to System.currentTimeMillis())
            saveDeletedCardsFileOnly()
        }
        cards = cards.filterNot { it.id == id }
        cardDueStepMap.remove(id)
        val sel = _selectedStudyDeck
        if (sel != null && !allDeckNames.contains(sel)) {
            _selectedStudyDeck = null
        }
        saveCards()
        rebuildStudyQueue()
    }

    fun resetAllProgress() {
        val now = System.currentTimeMillis()
        cards = cards.map { card ->
            card.copy(
                updatedAt = now,
                stage = CardStage.NEW,
                easeFactor = 2.5,
                intervalDays = 0.0,
                repetitions = 0,
                lapses = 0,
                dueDate = now,
                lastReviewedAt = null,
                totalReviews = 0
            )
        }
        cardDueStepMap.clear()
        dailyProgress = DailyProgress.empty(now)
        studyHistory = emptyMap()
        saveCards()
        saveProgress()
        rebuildStudyQueue(now = now)
    }

    fun deleteAllCards() {
        val now = System.currentTimeMillis()
        if (cards.isNotEmpty()) {
            val updatedDeleted = deletedCardKeys.toMutableMap()
            cards.forEach { updatedDeleted[it.syncKey] = now }
            deletedCardKeys = updatedDeleted
            saveDeletedCardsFileOnly()
        }
        cards = emptyList()
        cardDueStepMap.clear()
        _selectedStudyDeck = null
        dailyProgress = DailyProgress.empty()
        saveCards()
        saveProgress()
        rebuildStudyQueue()
    }

    // MARK: - Backup / Restore (.json Package Sharing)

    fun exportBackupJSONString(includeProgress: Boolean): String {
        val now = System.currentTimeMillis()
        val exportedCards = cards.map { card ->
            if (includeProgress) {
                card
            } else {
                Flashcard(
                    id = UUID.randomUUID().toString().uppercase(Locale.ROOT),
                    artikel = card.artikel,
                    word = card.word,
                    meaning = card.meaning,
                    deckName = card.deckName,
                    createdAt = now
                )
            }
        }

        val backup = ArtikelMeisterBackup(
            version = 2,
            exportedAt = now,
            includesProgress = includeProgress,
            cards = exportedCards,
            settings = if (includeProgress) settings else null,
            studyHistory = if (includeProgress) studyHistory else null,
            deletedCardKeys = if (includeProgress) deletedCardKeys else null,
            userEmail = GeminiAuthManager.getInstance(appContext).userEmail.ifBlank { null }
        )
        return backup.toJsonString()
    }

    // MARK: - Universal Google Drive Smart Sync & Deduplication Engine

    fun scheduleAutoCloudSync() {
        val authManager = GeminiAuthManager.getInstance(appContext)
        if (!authManager.isGoogleAccountConnected) return
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            delay(2500L)
            syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = true)
        }
    }

    private fun smartMergeCards(local: Flashcard, incoming: Flashcard): Flashcard {
        // Choose the card with further / newer Anki SM-2 study progress
        val incomingHasMoreProgress = when {
            incoming.totalReviews != local.totalReviews -> incoming.totalReviews > local.totalReviews
            (incoming.lastReviewedAt ?: 0L) != (local.lastReviewedAt ?: 0L) ->
                (incoming.lastReviewedAt ?: 0L) > (local.lastReviewedAt ?: 0L)
            else -> incoming.updatedAt > local.updatedAt
        }
        val progressSource = if (incomingHasMoreProgress) incoming else local

        // Choose best meaning (non-empty wins; if both non-empty, newer updatedAt wins)
        val mergedMeaning = when {
            local.meaning.isNotBlank() && incoming.meaning.isBlank() -> local.meaning
            incoming.meaning.isNotBlank() && local.meaning.isBlank() -> incoming.meaning
            incoming.updatedAt > local.updatedAt && incoming.meaning.isNotBlank() -> incoming.meaning
            else -> local.meaning
        }

        // Choose best deck (specific deck over 'Genel' wins; if both specific, newer updatedAt wins)
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

    suspend fun syncWithGoogleDrive(
        authManager: GeminiAuthManager = GeminiAuthManager.getInstance(appContext),
        isSilentAutoSync: Boolean = false
    ) {
        if (!authManager.isGoogleAccountConnected || isSyncingCloud) return
        isSyncingCloud = true
        if (!isSilentAutoSync) {
            cloudSyncIsError = false
            cloudSyncStatusMessage = "Google Drive ('${GoogleDriveSyncService.SYNC_FOLDER_NAME}') ile eşitleniyor..."
        }

        try {
            if (authManager.userEmail.isBlank()) {
                runCatching { authManager.fetchUserProfile() }
            }

            val snapshot = GoogleDriveSyncService.fetchCloudSnapshot(authManager)
            val cloudBackup = snapshot.backup

            var addedFromCloud = 0
            var overlappingDeduplicated = 0

            if (cloudBackup != null) {
                // 1. Merge deleted card tombstones
                val mergedDeleted = deletedCardKeys.toMutableMap()
                cloudBackup.deletedCardKeys?.forEach { (k, ts) ->
                    val existingTs = mergedDeleted[k] ?: 0L
                    if (ts > existingTs) {
                        mergedDeleted[k] = ts
                    }
                }
                deletedCardKeys = mergedDeleted
                saveDeletedCardsFileOnly()

                // 2. Deduplicate & Smart-Merge local cards + cloud cards by syncKey
                val mergedByKey = LinkedHashMap<String, Flashcard>()
                for (localCard in cards) {
                    val key = localCard.syncKey
                    val delTs = mergedDeleted[key] ?: 0L
                    if (delTs > localCard.updatedAt) continue
                    val existing = mergedByKey[key]
                    if (existing == null) {
                        mergedByKey[key] = localCard
                    } else {
                        mergedByKey[key] = smartMergeCards(existing, localCard)
                    }
                }

                for (cloudCard in cloudBackup.cards) {
                    val key = cloudCard.syncKey
                    val delTs = mergedDeleted[key] ?: 0L
                    if (delTs > cloudCard.updatedAt) continue

                    val existingLocal = mergedByKey[key]
                    if (existingLocal != null) {
                        mergedByKey[key] = smartMergeCards(existingLocal, cloudCard)
                        overlappingDeduplicated += 1
                    } else {
                        mergedByKey[key] = cloudCard
                        addedFromCloud += 1
                    }
                }

                cards = mergedByKey.values.toList()

                // 3. Merge studyHistory & today's dailyProgress (streak, daily completed & remaining counts)
                if (cloudBackup.studyHistory != null) {
                    val mergedHistory = studyHistory.toMutableMap()
                    for ((dateKey, cloudProg) in cloudBackup.studyHistory) {
                        val localProg = mergedHistory[dateKey]
                        if (localProg == null) {
                            mergedHistory[dateKey] = cloudProg
                        } else {
                            mergedHistory[dateKey] = DailyProgress(
                                dateString = dateKey,
                                newCardsStudied = max(localProg.newCardsStudied, cloudProg.newCardsStudied),
                                reviewsCompleted = max(localProg.reviewsCompleted, cloudProg.reviewsCompleted),
                                againCount = max(localProg.againCount, cloudProg.againCount),
                                hardCount = max(localProg.hardCount, cloudProg.hardCount),
                                goodCount = max(localProg.goodCount, cloudProg.goodCount),
                                easyCount = max(localProg.easyCount, cloudProg.easyCount)
                            )
                        }
                    }
                    studyHistory = mergedHistory
                    val todayKey = DailyProgress.todayString()
                    mergedHistory[todayKey]?.let { mergedToday ->
                        dailyProgress = DailyProgress(
                            dateString = todayKey,
                            newCardsStudied = max(dailyProgress.newCardsStudied, mergedToday.newCardsStudied),
                            reviewsCompleted = max(dailyProgress.reviewsCompleted, mergedToday.reviewsCompleted),
                            againCount = max(dailyProgress.againCount, mergedToday.againCount),
                            hardCount = max(dailyProgress.hardCount, mergedToday.hardCount),
                            goodCount = max(dailyProgress.goodCount, mergedToday.goodCount),
                            easyCount = max(dailyProgress.easyCount, mergedToday.easyCount)
                        )
                    }
                    saveProgress(triggerCloudSync = false)
                }

                // 4. Merge user preferences & custom decks
                if (cloudBackup.settings != null) {
                    val cloudSettings = cloudBackup.settings
                    val unionDecks = linkedSetOf("Genel")
                    settings.customDecks.map { it.trim() }.filter { it.isNotEmpty() }.forEach { unionDecks.add(it) }
                    cloudSettings.customDecks.map { it.trim() }.filter { it.isNotEmpty() }.forEach { unionDecks.add(it) }
                    cards.map { it.cleanDeckName }.forEach { unionDecks.add(it) }

                    val newerPrefs = if (cloudSettings.updatedAt > settings.updatedAt) cloudSettings else settings
                    settings = newerPrefs.copy(
                        customDecks = unionDecks.toList(),
                        updatedAt = max(settings.updatedAt, cloudSettings.updatedAt)
                    )
                    saveSettingsFileOnly()
                }

                saveCards(triggerCloudSync = false)
                rebuildStudyQueue(preserveCurrentFrontCard = true)
            } else {
                // Ensure any local duplicate keys are also deduplicated before initial upload
                val deduplicatedMap = LinkedHashMap<String, Flashcard>()
                for (c in cards) {
                    val existing = deduplicatedMap[c.syncKey]
                    deduplicatedMap[c.syncKey] = if (existing == null) c else smartMergeCards(existing, c)
                }
                if (deduplicatedMap.size != cards.size) {
                    cards = deduplicatedMap.values.toList()
                    saveCards(triggerCloudSync = false)
                }
            }

            // 5. Upload the unified deduplicated backup back to Google Drive
            val unifiedJson = exportBackupJSONString(includeProgress = true)
            GoogleDriveSyncService.uploadCloudBackup(
                authManager = authManager,
                folderId = snapshot.folderId,
                existingFileId = snapshot.fileId,
                backupJson = unifiedJson
            )

            lastCloudSyncTime = System.currentTimeMillis()
            cloudSyncIsError = false
            cloudSyncStatusMessage = when {
                addedFromCloud > 0 ->
                    "Bulutla eşitlendi: +$addedFromCloud yeni kelime alındı (Toplam ${cards.size} kelime)."
                else ->
                    "Google Drive ile başarıyla eşitlendi (${cards.size} kelime, $currentStreakDays gün seri)."
            }
        } catch (e: Exception) {
            cloudSyncIsError = true
            cloudSyncStatusMessage = "Bulut eşitleme hatası: ${e.localizedMessage ?: "Bağlantı kurulamadı"}"
        } finally {
            isSyncingCloud = false
        }
    }

    suspend fun deleteAccountAndAllData(authManager: GeminiAuthManager) {
        autoSyncJob?.cancel()
        isSyncingCloud = true
        cloudSyncIsError = false
        cloudSyncStatusMessage = "Bulut ve yerel hesap verileri tamamen siliniyor..."
        try {
            GoogleDriveSyncService.deleteAllCloudAccountData(authManager)
            authManager.revokeGoogleTokenBestEffort()
            authManager.clearAuthorization()

            cards = emptyList()
            deletedCardKeys = emptyMap()
            studyHistory = emptyMap()
            dailyProgress = DailyProgress.empty()
            val currentDarkMode = settings.isDarkMode
            settings = AppSettings(isDarkMode = currentDarkMode, updatedAt = System.currentTimeMillis())
            _selectedStudyDeck = null
            cardDueStepMap.clear()

            runCatching { cardsFile.delete() }
            runCatching { settingsFile.delete() }
            runCatching { progressFile.delete() }
            runCatching { historyFile.delete() }
            runCatching { deletedCardsFile.delete() }
            runCatching { importedCSVsDirectory.deleteRecursively() }
            runCatching { backupsDirectory.deleteRecursively() }
            ensureDirectoriesExist()
            saveSettingsFileOnly()
            saveCards(triggerCloudSync = false)
            saveProgress(triggerCloudSync = false)
            rebuildStudyQueue()

            lastCloudSyncTime = null
            cloudSyncIsError = false
            cloudSyncStatusMessage = "Hesap, Google Drive bulut verileri ve yerel veriler tamamen silindi."
        } finally {
            isSyncingCloud = false
        }
    }

    fun importBackupJSONString(jsonStr: String, mergeWithExisting: Boolean): Pair<Int, Int> {
        val backup = ArtikelMeisterBackup.fromJsonString(jsonStr)

        if (!mergeWithExisting) {
            cards = backup.cards
            if (backup.settings != null) {
                settings = backup.settings
                saveSettingsFileOnly()
            }
            if (backup.studyHistory != null) {
                studyHistory = backup.studyHistory
                val today = DailyProgress.todayString()
                backup.studyHistory[today]?.let { dailyProgress = it }
                saveHistory()
            }
            saveCards()
            rebuildStudyQueue()
            return Pair(backup.cards.size, 0)
        } else {
            var added = 0
            var updated = 0
            val mutableCards = cards.toMutableList()
            val updatedDeleted = deletedCardKeys.toMutableMap()

            for (incoming in backup.cards) {
                updatedDeleted.remove(incoming.syncKey)
                val idx = mutableCards.indexOfFirst {
                    it.artikel == incoming.artikel && it.word.equals(incoming.word, ignoreCase = true)
                }
                if (idx != -1) {
                    mutableCards[idx] = smartMergeCards(mutableCards[idx], incoming)
                    updated += 1
                } else {
                    mutableCards.add(incoming)
                    added += 1
                }
            }
            if (updatedDeleted.size != deletedCardKeys.size) {
                deletedCardKeys = updatedDeleted
                saveDeletedCardsFileOnly()
            }
            cards = mutableCards

            if (backup.studyHistory != null) {
                val mutableHist = studyHistory.toMutableMap()
                for ((dateKey, prog) in backup.studyHistory) {
                    val existingProg = mutableHist[dateKey]
                    if (existingProg != null) {
                        if (prog.totalAnswersCount > existingProg.totalAnswersCount) {
                            mutableHist[dateKey] = prog
                        }
                    } else {
                        mutableHist[dateKey] = prog
                    }
                }
                studyHistory = mutableHist
                saveHistory()
            }
            saveCards()
            rebuildStudyQueue()
            return Pair(added, updated)
        }
    }

    // MARK: - CSV Import / Export

    fun importCSVFromUri(uri: Uri, defaultDeckName: String = "Genel"): CSVImportResult {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("CSV dosyası okunamadı.")

        val rawContent = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            String(bytes, Charset.forName("ISO-8859-1"))
        }

        ensureDirectoriesExist()
        val timestamp = System.currentTimeMillis() / 1000
        val backupFile = File(importedCSVsDirectory, "${timestamp}_imported.csv")
        runCatching { backupFile.writeText(rawContent, Charsets.UTF_8) }

        return importCSVContent(rawContent, defaultDeckName)
    }

    fun loadBundledAssetCSV(assetFileName: String, defaultDeckName: String = "Genel"): CSVImportResult {
        val rawContent = appContext.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return importCSVContent(rawContent, defaultDeckName)
    }

    fun importCSVContent(content: String, defaultDeckName: String = "Genel"): CSVImportResult {
        val lines = content.split("\r\n", "\n", "\r")
        var added = 0
        var updated = 0
        var skipped = 0
        var invalid = 0

        val fallbackDeck = defaultDeckName.trim().ifEmpty { "Genel" }
        val mutableCards = cards.toMutableList()

        for ((lineIdx, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val columns = parseCSVRow(trimmed)
            if (columns.size < 2) {
                invalid += 1
                continue
            }

            val rawArtikel = columns[0].replace("\uFEFF", "")
            val rawWord = columns[1]
            val rawMeaning = if (columns.size >= 3) columns[2] else ""
            val rawDeck = if (columns.size >= 4) columns[3] else fallbackDeck

            val parsedArtikel = Artikel.parse(rawArtikel)
            if (parsedArtikel == null) {
                if (lineIdx == 0) continue
                invalid += 1
                continue
            }

            val cleanWord = rawWord.trim()
            val cleanMeaning = rawMeaning.trim()
            val cleanDeck = rawDeck.trim().ifEmpty { fallbackDeck }

            if (cleanWord.isEmpty()) {
                invalid += 1
                continue
            }

            val existingIdx = mutableCards.indexOfFirst {
                it.artikel == parsedArtikel && it.word.equals(cleanWord, ignoreCase = true)
            }
            if (existingIdx != -1) {
                var existing = mutableCards[existingIdx]
                var didUpdate = false
                if (cleanMeaning.isNotEmpty() && existing.meaning != cleanMeaning) {
                    existing = existing.copy(meaning = cleanMeaning)
                    didUpdate = true
                }
                if (cleanDeck != "Genel" && existing.deckName != cleanDeck) {
                    existing = existing.copy(deckName = cleanDeck)
                    didUpdate = true
                }
                if (didUpdate) {
                    mutableCards[existingIdx] = existing.copy(updatedAt = System.currentTimeMillis())
                    updated += 1
                } else {
                    skipped += 1
                }
            } else {
                val now = System.currentTimeMillis()
                val card = Flashcard(
                    artikel = parsedArtikel,
                    word = cleanWord,
                    meaning = cleanMeaning,
                    deckName = cleanDeck,
                    createdAt = now,
                    updatedAt = now
                )
                if (deletedCardKeys.containsKey(card.syncKey)) {
                    deletedCardKeys = deletedCardKeys - card.syncKey
                    saveDeletedCardsFileOnly()
                }
                mutableCards.add(card)
                added += 1
            }
        }

        cards = mutableCards
        saveCards()
        rebuildStudyQueue()
        return CSVImportResult(
            addedCount = added,
            updatedCount = updated,
            skippedCount = skipped,
            invalidLines = invalid
        )
    }

    private fun parseCSVRow(line: String): List<String> {
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

    fun exportCSVString(): String {
        val rows = mutableListOf("artikel,kelime,anlam,deste")
        for (card in cards) {
            val escapedWord = if (card.word.contains(",")) "\"${card.word}\"" else card.word
            val escapedMeaning = if (card.meaning.contains(",")) "\"${card.meaning}\"" else card.meaning
            val escapedDeck = if (card.deckName.contains(",")) "\"${card.deckName}\"" else card.deckName
            rows.add("${card.artikel.rawValue},$escapedWord,$escapedMeaning,$escapedDeck")
        }
        return rows.joinToString("\n")
    }

    fun loadStarterGermanPack(): CSVImportResult {
        val sampleCSV = """
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
        return importCSVContent(sampleCSV, defaultDeckName = "A1 Temel")
    }
}
