package com.artikelmeister.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

private const val MAX_IMPORT_SNAPSHOTS = 3

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
    // Article-less words live in their own optional file so cards.json stays a plain array
    // of valid der/die/das cards that older app versions can read safely.
    private val articlelessCardsFile = File(storageDirectory, "articleless_cards.json")

    /**
     * Set when cards.json / articleless_cards.json exists but could not be decoded.
     * While set, local card files are never rewritten and cloud sync never uploads,
     * so a decode failure cannot wipe local or cloud data.
     */
    var cardsLoadFailed by mutableStateOf(false)
        private set
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

        var loadFailed = false
        val loaded = mutableListOf<Flashcard>()
        if (cardsFile.exists()) {
            runCatching {
                val arr = JSONArray(cardsFile.readText(Charsets.UTF_8))
                loaded.addAll(CardCodec.decodeArray(arr, articleless = false))
            }.onFailure {
                loadFailed = true
                preserveCorruptFile(cardsFile)
            }
        }
        if (articlelessCardsFile.exists()) {
            runCatching {
                val arr = JSONArray(articlelessCardsFile.readText(Charsets.UTF_8))
                loaded.addAll(CardCodec.decodeArray(arr, articleless = true))
            }.onFailure {
                loadFailed = true
                preserveCorruptFile(articlelessCardsFile)
            }
        }
        cardsLoadFailed = loadFailed
        cards = CardMerge.dedupe(loaded)
        if (!loadFailed && !cardsFile.exists()) {
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

    private fun preserveCorruptFile(file: File) {
        runCatching {
            file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"), overwrite = false)
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
        if (cardsLoadFailed) {
            // Never overwrite files we could not decode.
            return
        }
        runCatching {
            // cards.json: ONLY valid der/die/das cards (backward-safe for old clients).
            cardsFile.writeText(
                CardCodec.encodeArray(cards.filterNot { it.isArticleless }).toString(2),
                Charsets.UTF_8
            )
            val articleless = cards.filter { it.isArticleless }
            if (articleless.isNotEmpty()) {
                articlelessCardsFile.writeText(CardCodec.encodeArray(articleless).toString(2), Charsets.UTF_8)
            } else if (articlelessCardsFile.exists()) {
                articlelessCardsFile.delete()
            }
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

    /** Counts per article (incl. '-' = article-less) for the whole pool or one deck. */
    fun artikelCounts(forDeck: String? = null): ArtikelFilter.Counts {
        val pool = if (forDeck == null) cards else cards.filter { it.deckName == forDeck }
        return ArtikelFilter.counts(pool)
    }

    val articlelessCount: Int
        get() = cards.count { it.isArticleless }

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
        artikel: Artikel?,
        word: String,
        meaning: String,
        deckName: String = "Genel"
    ): Boolean {
        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        val cleanDeck = deckName.trim().ifEmpty { "Genel" }
        if (cleanWord.isEmpty()) return false
        val now = System.currentTimeMillis()

        val newKey = Flashcard.keyFor(artikel, cleanWord)
        val existingIdx = cards.indexOfFirst { it.syncKey == newKey }
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
        artikel: Artikel?,
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
        cardsLoadFailed = false // explicit user wipe
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

    private fun smartMergeCards(local: Flashcard, incoming: Flashcard): Flashcard =
        CardMerge.smartMerge(local, incoming)

    suspend fun syncWithGoogleDrive(
        authManager: GeminiAuthManager = GeminiAuthManager.getInstance(appContext),
        isSilentAutoSync: Boolean = false
    ) {
        if (!authManager.isGoogleAccountConnected || isSyncingCloud) return
        if (cardsLoadFailed) {
            // Local card files could not be decoded: do not merge/upload anything.
            cloudSyncIsError = true
            cloudSyncStatusMessage = "Yerel kelime dosyası okunamadı; veri kaybını önlemek için eşitleme durduruldu."
            return
        }
        isSyncingCloud = true
        if (!isSilentAutoSync) {
            cloudSyncIsError = false
            cloudSyncStatusMessage = "Google Drive ('${GoogleDriveSyncService.SYNC_FOLDER_NAME}') ile eşitleniyor..."
        }

        try {
            if (authManager.userEmail.isBlank()) {
                runCatchingCancellable { authManager.fetchUserProfile() }
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
                //    (cloudBackup.cards already contains valid + article-less cards)
                val mergeResult = CardMerge.merge(cards, cloudBackup.cards, mergedDeleted)
                addedFromCloud = mergeResult.addedFromIncoming
                overlappingDeduplicated = mergeResult.overlapping
                cards = mergeResult.cards

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
                val deduplicated = CardMerge.dedupe(cards)
                if (deduplicated.size != cards.size) {
                    cards = deduplicated
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
            // Cancellation (e.g. the calling composition left the screen) is NOT a sync error:
            // never show it to the user. Rethrow so structured concurrency keeps working;
            // isSyncingCloud is reset in finally either way.
            val message = SyncErrors.userMessage(e, "Bağlantı kurulamadı")
            if (message == null) {
                if (!isSilentAutoSync) cloudSyncStatusMessage = null
                throw e
            }
            cloudSyncIsError = true
            cloudSyncStatusMessage = "Bulut eşitleme hatası: $message"
        } finally {
            isSyncingCloud = false
        }
    }

    /** Starts a sync in the store's own long-lived scope (survives the calling screen leaving composition). */
    fun requestCloudSync(authManager: GeminiAuthManager, isSilentAutoSync: Boolean = false) {
        scope.launch { syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = isSilentAutoSync) }
    }

    /**
     * Handles the native Google sign-in result and the first sync in the store's scope.
     * Sign-in flips hasCompletedOnboarding, which removes the onboarding screen from composition;
     * doing this work in that screen's rememberCoroutineScope used to cancel the sync mid-flight
     * ("The coroutine scope left the composition").
     * [onFinished] runs on the main thread with null on success or a user-facing error message.
     */
    fun completeGoogleSignIn(
        data: android.content.Intent?,
        authManager: GeminiAuthManager,
        silentSync: Boolean = false,
        onFinished: (String?) -> Unit = {}
    ) {
        scope.launch {
            var error: String? = null
            try {
                authManager.handleGoogleSignInResult(data, appContext)
            } catch (e: Exception) {
                error = SyncErrors.userMessage(e, "Google girişi başarısız oldu.")
                if (error == null) throw e
                cloudSyncStatusMessage = error
                cloudSyncIsError = true
            }
            if (error == null) {
                // Sync reports its own errors via cloudSyncStatusMessage; the sign-in itself succeeded.
                syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = silentSync)
            }
            onFinished(error)
        }
    }

    /** Account deletion must not be half-cancelled by a screen change, so it runs in the store scope. */
    fun requestDeleteAccountAndAllData(authManager: GeminiAuthManager, onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                deleteAccountAndAllData(authManager)
            } catch (e: Exception) {
                val message = SyncErrors.userMessage(e, "Hesap silinemedi.")
                if (message == null) throw e
                cloudSyncIsError = true
                cloudSyncStatusMessage = "Hesap silme hatası: $message"
            } finally {
                onFinished()
            }
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
            runCatching { articlelessCardsFile.delete() }
            cardsLoadFailed = false
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
            cardsLoadFailed = false // explicit user replace of all cards
            cards = CardMerge.dedupe(backup.cards)
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
                val incomingKey = incoming.syncKey
                val idx = mutableCards.indexOfFirst { it.syncKey == incomingKey }
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

    /** Reads the picked file's display name (null when the provider does not expose one). */
    private fun displayNameOf(uri: Uri): String? {
        val fromProvider = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    /**
     * Imports a picked CSV. Gates, all BEFORE any data is touched: .csv extension (primary gate), then
     * decoding + content validation. Throws [CsvCards.RejectedException] (Turkish message) on rejection.
     */
    fun importCSVFromUri(uri: Uri, defaultDeckName: String = "Genel"): CSVImportResult {
        CsvCards.requireCsvName(displayNameOf(uri))

        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("CSV dosyası okunamadı.")

        val rawContent = CsvCards.decodeAndValidate(bytes)

        // Only validated files are kept in ImportedCSVs.
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

    // ---- Import safety net -------------------------------------------------------------------

    private var importUndo by mutableStateOf<CsvCards.UndoRecord?>(null)

    /** True while the last successful CSV import can still be undone ("Geri al"). */
    val canUndoImport: Boolean get() = importUndo != null

    /** Full pre-import snapshot (cards, progress, settings, tombstones) as a backup file; keeps the last few. */
    private fun writeImportSnapshot() {
        runCatching {
            ensureDirectoriesExist()
            val file = File(backupsDirectory, "pre_import_${System.currentTimeMillis()}.json")
            file.writeText(exportBackupJSONString(includeProgress = true), Charsets.UTF_8)
            backupsDirectory.listFiles { f -> f.name.startsWith("pre_import_") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_IMPORT_SNAPSHOTS)
                ?.forEach { it.delete() }
        }
    }

    /**
     * Reverts the last CSV import: removes cards it added (with tombstones so cloud sync cannot bring them
     * back), restores the previous meaning/deck of cards it updated (with a fresh updatedAt so the older
     * values win the next cloud merge) and restores the previous tombstones. Returns false if nothing to undo.
     */
    fun undoLastImport(): Boolean {
        val undo = importUndo ?: return false
        val (restored, tombstones) = CsvCards.applyUndo(undo, cards, deletedCardKeys, System.currentTimeMillis())
        cards = restored
        deletedCardKeys = tombstones
        saveDeletedCardsFileOnly()
        val sel = _selectedStudyDeck
        if (sel != null && !allDeckNames.contains(sel)) _selectedStudyDeck = null
        importUndo = null
        saveCards()
        rebuildStudyQueue()
        return true
    }

    /**
     * Validates, snapshots, then imports. Validation failure throws [CsvCards.RejectedException] and leaves
     * cards, decks, tombstones and cloud state completely untouched (no save, no sync).
     */
    fun importCSVContent(content: String, defaultDeckName: String = "Genel"): CSVImportResult {
        CsvCards.requireValid(content)

        writeImportSnapshot()
        val before = cards
        val previousTombstones = deletedCardKeys

        // Rows with an empty / non-der/die/das article column become article-less words.
        val outcome = CsvCards.importContent(content, defaultDeckName, cards)

        CsvCards.buildUndo(before, outcome, previousTombstones)?.let { importUndo = it }

        var removedTombstone = false
        val updatedDeleted = deletedCardKeys.toMutableMap()
        for (key in outcome.addedKeys) {
            if (updatedDeleted.remove(key) != null) removedTombstone = true
        }
        if (removedTombstone) {
            deletedCardKeys = updatedDeleted
            saveDeletedCardsFileOnly()
        }
        cards = outcome.cards
        saveCards()
        rebuildStudyQueue()
        return CSVImportResult(
            addedCount = outcome.added,
            updatedCount = outcome.updated,
            skippedCount = outcome.skipped,
            invalidLines = outcome.invalid
        )
    }

    fun exportCSVString(): String = CsvCards.exportString(cards)

    fun loadStarterGermanPack(): CSVImportResult {
        val sampleCSV = CsvCards.STARTER_PACK_CSV
        return importCSVContent(sampleCSV, defaultDeckName = "A1 Temel")
    }
}
