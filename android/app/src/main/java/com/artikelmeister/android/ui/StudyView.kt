package com.artikelmeister.android.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.AnswerChecker
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.CsvCards
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.Flashcard
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.R
import com.artikelmeister.android.ReviewRating
import com.artikelmeister.android.SM2Scheduler
import com.artikelmeister.android.SpeechManager
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private const val AI_ACTION_LABEL = "AI ile kelime üret"
private const val CSV_IMPORT_LABEL = "CSV içe aktar…"
private const val INCREASE_LIMIT_LABEL = "Günlük yeni kart sınırını 10 artır"

enum class ArtikelGuessResult {
    NONE,
    CORRECT,
    WRONG
}

@Composable
fun StudyView(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onOpenDrawer: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    var isRevealed by remember { mutableStateOf(false) }
    var typedArtikel by remember { mutableStateOf("") }
    var guessResult by remember { mutableStateOf(ArtikelGuessResult.NONE) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var importTitle by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var showImportAlert by remember { mutableStateOf(false) }
    var offerImportUndo by remember { mutableStateOf(false) }
    var showAIGenerator by remember { mutableStateOf(false) }
    var showNewDeckSheet by remember { mutableStateOf(false) }

    val currentCard = store.studyQueue.firstOrNull()

    val availableRatingsForCurrentState = remember(guessResult) {
        if (guessResult == ArtikelGuessResult.WRONG) {
            listOf(ReviewRating.AGAIN, ReviewRating.HARD)
        } else {
            ReviewRating.entries
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val targetDeck = store.selectedStudyDeck ?: "Genel"
                val res = store.importCSVFromUri(uri, defaultDeckName = targetDeck)
                importTitle = "CSV içe aktarıldı"
                importMessage = """
                    Yeni eklenen kelime: ${res.addedCount}
                    Güncellenen kelime: ${res.updatedCount}
                    Zaten mevcut (atlanan): ${res.skippedCount}
                    Geçersiz/Atlanan satır: ${res.invalidLines}
                """.trimIndent()
                offerImportUndo = store.canUndoImport
                showImportAlert = true
            }.onFailure { error ->
                offerImportUndo = false
                importTitle = "CSV okunamadı"
                importMessage = (error as? CsvCards.RejectedException)?.message
                    ?: "Dosyanın CSV biçiminde olduğunu ve okunabildiğini kontrol edip tekrar deneyin."
                showImportAlert = true
            }
        }
    }

    fun closeImportAlert() {
        showImportAlert = false
        offerImportUndo = false
        store.finishImportUndoWindow()
    }

    val importCSV = { csvLauncher.launch(CsvCards.ACCEPTED_MIME_TYPES) }
    val openAIGenerator = { showAIGenerator = true }

    fun resetCardInputState() {
        isRevealed = false
        typedArtikel = ""
        guessResult = ArtikelGuessResult.NONE
    }

    /**
     * Reveals the card for an explicit answer. Article buttons pass their value directly, so the
     * result cannot read a stale `typedArtikel` from the previous composition (that would make the
     * instantly-revealed card judge the wrong answer).
     */
    fun revealCard(card: Flashcard, answer: String) {
        if (isRevealed) return
        typedArtikel = answer
        guessResult = if (com.artikelmeister.android.AnswerChecker.isCorrect(card, answer)) {
            ArtikelGuessResult.CORRECT
        } else {
            ArtikelGuessResult.WRONG
        }
        isRevealed = true
        if (store.settings.autoPlayPronunciation) {
            SpeechManager.speak(card.fullGermanTerm)
        }
    }

    fun checkAndRevealCard(card: Flashcard) = revealCard(card, typedArtikel)

    fun submitRating(card: Flashcard, rating: ReviewRating) {
        if (guessResult == ArtikelGuessResult.WRONG && (rating == ReviewRating.GOOD || rating == ReviewRating.EASY)) {
            return
        }
        resetCardInputState()
        store.answerCard(card, rating)
    }

    LaunchedEffect(Unit) {
        store.rebuildStudyQueue()
    }

    LaunchedEffect(currentCard?.id) {
        resetCardInputState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
    ) {
        // Top Status Header Bar
        StudyHeaderBar(
            selectedDeck = store.selectedStudyDeck,
            deckNames = { store.allDeckNames },
            totalCardCount = store.cards.size,
            deckCardCount = { store.cardCount(it) },
            onSelectDeck = { store.selectedStudyDeck = it },
            onOpenDrawer = onOpenDrawer,
            onShowNewDeck = { showNewDeckSheet = true },
            onShowAIGenerator = openAIGenerator,
            onImportCSV = importCSV
        ) {
            StudyQueueSummary(
                newCount = store.availableNewCardsCount,
                learningCount = store.dueLearningCardsCount,
                reviewCount = store.dueReviewCardsCount,
                streakDays = store.currentStreakDays
            )
        }

        HorizontalDivider(color = colors.cardBorderSubtle)

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                store.cards.isEmpty() -> {
                    EmptyDatabaseView(
                        onShowAIGenerator = openAIGenerator,
                        onImportCSV = importCSV,
                        onLoadStarterPack = {
                            val res = store.loadStarterGermanPack()
                            importTitle = "Örnek kelimeler eklendi"
                            importMessage = "Yeni eklenen kelime: ${res.addedCount}"
                            offerImportUndo = store.canUndoImport
                            showImportAlert = true
                        }
                    )
                }

                currentCard != null -> {
                    ActiveCardSessionView(
                        card = currentCard,
                        showMeaningOnFront = store.settings.showMeaningOnFront,
                        isRevealed = isRevealed,
                        typedArtikel = typedArtikel,
                        onTypedArtikelChange = { typedArtikel = it },
                        guessResult = guessResult,
                        availableRatings = availableRatingsForCurrentState,
                        onCheckAndReveal = { checkAndRevealCard(currentCard) },
                        onSelectArtikel = { art -> revealCard(currentCard, art.rawValue) },
                        onSubmitRating = { rating -> submitRating(currentCard, rating) }
                    )
                }

                store.selectedStudyDeck != null && store.filteredPoolCards.isEmpty() -> {
                    StateMessageView(
                        icon = Icons.Outlined.Folder,
                        title = "Bu destede henüz kelime yok",
                        message = "Bu desteye kelime eklemek için CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.",
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    )
                }

                store.nextPendingLearningCard != null -> {
                    WaitingForLearningCardView(
                        pendingCard = store.nextPendingLearningCard!!,
                        store = store
                    )
                }

                else -> {
                    SessionCompletedView(
                        store = store,
                        onShowAIGenerator = openAIGenerator,
                        onImportCSV = importCSV
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            )
        }
    }

    if (showAIGenerator) {
        AIGeneratorDialog(
            store = store,
            authManager = authManager,
            onDismiss = { showAIGenerator = false }
        )
    }

    if (showNewDeckSheet) {
        NewDeckDialog(
            store = store,
            onDismiss = { showNewDeckSheet = false },
            onCreated = { created ->
                store.selectedStudyDeck = created
                showNewDeckSheet = false
            }
        )
    }

    if (showImportAlert) {
        AlertDialog(
            onDismissRequest = ::closeImportAlert,
            title = { Text(importTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = importMessage.orEmpty(),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = ::closeImportAlert) {
                    Text("Tamam")
                }
            },
            dismissButton = if (offerImportUndo && store.canUndoImport) {
                {
                    TextButton(onClick = {
                        val undone = store.undoLastImport()
                        closeImportAlert()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (undone) "İçe aktarma geri alındı." else "İçe aktarma geri alınamadı."
                            )
                        }
                    }) {
                        Text("Geri al")
                    }
                }
            } else null
        )
    }
}

@Composable
private fun StudyHeaderBar(
    selectedDeck: String?,
    deckNames: () -> List<String>,
    totalCardCount: Int,
    deckCardCount: (String) -> Int,
    onSelectDeck: (String?) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onShowNewDeck: () -> Unit,
    onShowAIGenerator: () -> Unit,
    onImportCSV: () -> Unit,
    queueSummary: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    var showDeckMenu by remember { mutableStateOf(false) }
    var showQueueStats by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlBackground)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val inlineSummary = maxWidth >= 420.dp
        val showActionLabels = maxWidth >= 680.dp && LocalDensity.current.fontScale <= 1.3f
        val queueToggle: @Composable () -> Unit = {
            val chevronRotation by animateFloatAsState(
                targetValue = if (showQueueStats) 180f else 0f,
                label = "chevronRotation"
            )
            TextButton(
                onClick = { showQueueStats = !showQueueStats },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (showQueueStats) "Açık" else "Kapalı" },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Çalışma özeti", style = MaterialTheme.typography.labelMedium)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp).rotate(chevronRotation)
                )
            }
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_open_menu))
                    }
                }

                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showDeckMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.widthIn(min = 48.dp, max = 240.dp).heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedDeck == null) Icons.Outlined.Folder else Icons.Filled.Folder,
                            contentDescription = null,
                            tint = colors.accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedDeck ?: "Tüm Desteler",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showDeckMenu,
                        onDismissRequest = { showDeckMenu = false }
                    ) {
                        StudyDeckMenuItem(
                            label = "Tüm Desteler ($totalCardCount)",
                            isSelected = selectedDeck == null,
                            onClick = {
                                onSelectDeck(null)
                                showDeckMenu = false
                            }
                        )
                        HorizontalDivider()
                        deckNames().forEach { deck ->
                            StudyDeckMenuItem(
                                label = "$deck (${deckCardCount(deck)})",
                                isSelected = selectedDeck == deck,
                                onClick = {
                                    onSelectDeck(deck)
                                    showDeckMenu = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Yeni Deste Ekle...") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showDeckMenu = false
                                onShowNewDeck()
                            }
                        )
                    }
                }

                if (inlineSummary) queueToggle()

                if (showActionLabels) {
                    Button(
                        onClick = onShowAIGenerator,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPurple,
                            contentColor = colors.onPurple
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AI_ACTION_LABEL)
                    }
                    OutlinedButton(onClick = onImportCSV, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(CSV_IMPORT_LABEL)
                    }
                } else {
                    IconButton(onClick = onShowAIGenerator, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = AI_ACTION_LABEL, tint = colors.accentPurple)
                    }
                    IconButton(onClick = onImportCSV, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoveToInbox, contentDescription = CSV_IMPORT_LABEL)
                    }
                }
            }

            if (!inlineSummary) queueToggle()

            AnimatedVisibility(
                visible = showQueueStats,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                queueSummary()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyQueueSummary(newCount: Int, learningCount: Int, reviewCount: Int, streakDays: Int) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        HorizontalDivider(
            color = colors.cardBorderSubtle,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CounterPill("Yeni", newCount, colors.accentBlue, colors.blueBadgeBackground)
            CounterPill("Öğreniliyor", learningCount, colors.accentOrange, colors.orangeBadgeBackground)
            CounterPill("Tekrar", reviewCount, colors.success, colors.successBadgeBackground)
            CounterPill("Seri", streakDays, colors.accentOrange, colors.orangeBadgeBackground, isStreak = true)
        }
    }
}

@Composable
private fun StudyDeckMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (isSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
        },
        modifier = Modifier.semantics { selected = isSelected },
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveCardSessionView(
    card: Flashcard,
    showMeaningOnFront: Boolean,
    isRevealed: Boolean,
    typedArtikel: String,
    onTypedArtikelChange: (String) -> Unit,
    guessResult: ArtikelGuessResult,
    availableRatings: List<ReviewRating>,
    onCheckAndReveal: () -> Unit,
    onSelectArtikel: (Artikel) -> Unit,
    onSubmitRating: (ReviewRating) -> Unit
) {
    val colors = LocalAppColors.current

    val isCorrect = guessResult == ArtikelGuessResult.CORRECT
    val focusManager = LocalFocusManager.current

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            !isRevealed -> colors.cardBorderSubtle
            guessResult == ArtikelGuessResult.CORRECT -> colors.feedbackCorrect.copy(alpha = 0.85f)
            guessResult == ArtikelGuessResult.WRONG -> colors.feedbackIncorrect.copy(alpha = 0.85f)
            else -> colors.cardBorderSubtle
        },
        animationSpec = tween(durationMillis = 180),
        label = "cardBorderColor"
    )
    val cardBorderWidth by animateDpAsState(
        targetValue = if (isRevealed) 3.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 180),
        label = "cardBorderWidth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Flashcard
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(cardBorderWidth, cardBorderColor),
            shadowElevation = if (colors.isDark) (if (isRevealed) 12.dp else 6.dp) else (if (isRevealed) 6.dp else 2.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .heightIn(min = 310.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Top metadata bar inside card. The listen control is a separate fixed column so it
                // stays pinned to the top-right on narrow phones instead of wrapping into the pills.
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = card.stage.title.uppercase(Locale("tr", "TR")),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.stageColor(card.stage),
                            modifier = Modifier
                                .background(colors.stageBadgeBackground(card.stage), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(colors.secondaryText.copy(alpha = 0.12f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = card.deckName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondaryText,
                                maxLines = 1
                            )
                        }

                        if (card.totalReviews > 0) {
                            Text(
                                text = "Kolaylık (tekrar aralığı çarpanı): %${(card.easeFactor * 100).toInt()} • Tekrar: ${card.totalReviews}",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = colors.secondaryText,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ListenPill(
                        onClick = {
                            val spokenText = if (isRevealed) card.fullGermanTerm else card.word
                            SpeechManager.speak(spokenText)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Keep the live region in place so TalkBack can announce its text change.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                ) {
                    if (isRevealed) {
                        val feedbackColor = if (isCorrect) colors.feedbackCorrect else colors.feedbackIncorrect
                        val feedbackBackground =
                            if (isCorrect) colors.successBadgeBackground else colors.errorBadgeBackground
                        val userAnswer = typedArtikel.trim()
                        val statusText = when {
                            isCorrect -> card.artikel?.let { "DOĞRU! (${it.grammaticalLabel})" } ?: "DOĞRU!"
                            userAnswer.isEmpty() -> "BOŞ BIRAKILDI"
                            else -> "YANLIŞ ($userAnswer)"
                        }

                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .background(feedbackBackground, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = feedbackColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = feedbackColor
                                )
                                if (!isCorrect) {
                                    val answerKind = if (card.isArticleless) "Anlam" else "Artikel"
                                    Text(
                                        text = "Doğru $answerKind: ${AnswerChecker.expectedLabel(card)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = feedbackColor
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Center Content: Front (3 Vertical Buttons + Word) vs Back (Revealed Result)
                AnimatedContent(
                    targetState = isRevealed,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180)))
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "card_flip"
                ) { revealed ->
                    if (!revealed) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (card.isArticleless) {
                                    stringResource(R.string.study_meaning_prompt)
                                } else {
                                    "Doğru artikeli seçin:"
                                },
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = colors.secondaryText,
                                textAlign = TextAlign.Center
                            )

                            if (card.isArticleless) {
                                ArticlelessFront(
                                    card = card,
                                    typed = typedArtikel,
                                    onTypedChange = onTypedArtikelChange,
                                    onSubmit = onCheckAndReveal
                                )
                            } else {
                                // Row: Left 3 Artikel Buttons stacked vertically, Right: Word (die is horizontally aligned with the word)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                ) {
                                    // 3 vertical buttons: der (top), die (middle), das (bottom)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.selectableGroup()
                                    ) {
                                        Artikel.entries.forEach { art ->
                                            val isSelected = typedArtikel.trim().equals(art.rawValue, ignoreCase = true)
                                            val artColor = colors.artikelColor(art)
                                            Surface(
                                                color = if (isSelected) artColor else colors.artikelBadgeBackground(art),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.5.dp,
                                                    color = artColor
                                                ),
                                                shadowElevation = if (isSelected) 4.dp else 0.dp,
                                                modifier = Modifier
                                                    .width(76.dp)
                                                    .heightIn(min = 48.dp)
                                                    .selectable(
                                                        selected = isSelected,
                                                        role = Role.RadioButton,
                                                        onClick = { onSelectArtikel(art) }
                                                    )
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = art.rawValue,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) colors.onArtikel(art) else artColor
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(18.dp))

                                    // Word on right, dynamically scaled for long words like Arbeitsalltag
                                    val wordFontSize = when {
                                        card.word.length > 22 -> 18.sp
                                        card.word.length > 16 -> 22.sp
                                        card.word.length > 12 -> 25.sp
                                        card.word.length > 8 -> 29.sp
                                        else -> 34.sp
                                    }

                                    Text(
                                        text = card.word,
                                        fontSize = wordFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText,
                                        textAlign = TextAlign.Start,
                                        softWrap = true,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }

                            // The meaning is the answer for article-less cards -> never on the front.
                            if (!card.isArticleless && showMeaningOnFront && card.meaning.isNotEmpty()) {
                                Text(
                                    text = card.meaning,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Keep article colors tied to their grammatical category.
                            val revealedWordFontSize = when {
                                card.word.length > 22 -> 20.sp
                                card.word.length > 16 -> 23.sp
                                card.word.length > 12 -> 26.sp
                                card.word.length > 8 -> 30.sp
                                else -> 36.sp
                            }
                            val revealedArtikelFontSize = when {
                                card.word.length > 22 -> 23.sp
                                card.word.length > 16 -> 26.sp
                                card.word.length > 12 -> 29.sp
                                card.word.length > 8 -> 33.sp
                                else -> 40.sp
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                card.artikel?.let { art ->
                                    Text(
                                        text = art.rawValue,
                                        fontSize = revealedArtikelFontSize,
                                        fontWeight = FontWeight.Black,
                                        color = colors.artikelColor(art)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = card.word,
                                    fontSize = revealedWordFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    textAlign = TextAlign.Start,
                                    softWrap = true,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            if (card.meaning.isNotEmpty()) {
                                HorizontalDivider(
                                    color = colors.cardBorderSubtle,
                                    modifier = Modifier.width(160.dp)
                                )
                                Text(
                                    text = card.meaning,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Legend for artikel colors (irrelevant for article-less cards)
                if (!card.isArticleless) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ArtikelLegendDot(artikel = Artikel.DER, label = "der (Eril)")
                        ArtikelLegendDot(artikel = Artikel.DIE, label = "die (Dişil)")
                        ArtikelLegendDot(artikel = Artikel.DAS, label = "das (Nötr)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Action Area (Clean mobile buttons, no keyboard shortcuts)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
        ) {
            if (!isRevealed) {
                // Article cards reveal on tap: the three article buttons above are the answer
                // controls, so a second "Kontrol Et" step only adds friction. Article-less cards
                // still need the explicit check because the answer is typed text.
                if (card.isArticleless) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onCheckAndReveal()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.onBlue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .widthIn(min = 200.dp, max = 280.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kontrol Et",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Bir artikel seçin; cevap hemen gösterilir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                if (guessResult == ArtikelGuessResult.WRONG) {
                    Text(
                        text = "Yanlış cevaplandığı için sadece 'Tekrar' veya 'Zor' seçilebilir:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Hatırlama zorluğunu seçin:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .widthIn(max = if (availableRatings.size == 2) 340.dp else 640.dp)
                        .fillMaxWidth()
                ) {
                    val ratingsPerRow = if (maxWidth >= 400.dp && LocalDensity.current.fontScale <= 1.3f) 4 else 2
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = ratingsPerRow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableRatings.forEach { rating ->
                            RatingButton(
                                rating = rating,
                                intervalText = SM2Scheduler.previewInterval(card = card, rating = rating),
                                onClick = { onSubmitRating(rating) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Small secondary action inside the card header. Kept at 48dp so the touch target follows the
 * app-wide accessibility rule while still reading as a quiet pill next to the metadata.
 */
@Composable
private fun ListenPill(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.secondaryText.copy(alpha = 0.12f))
            .clickable(onClickLabel = "Almanca telaffuzu dinle", role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Dinle",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = colors.primaryText,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingForLearningCardView(
    pendingCard: Flashcard,
    store: DeckStore
) {
    val colors = LocalAppColors.current
    val remainingSeconds = max(0, ceil((pendingCard.dueDate - store.currentTime) / 1000.0).toInt())
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeString = String.format(Locale.US, "%02d:%02d", mins, secs)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            // Scrollable: on a 360dp phone the timer, copy and both actions can exceed the
            // available height once the bottom navigation bar is subtracted.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = colors.accentOrange,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Öğrenme Kartı Zamanlayıcısı",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Sıradaki tekrar kelimesi vaat edilen süre dolduğunda otomatik olarak ekrana gelecektir:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = timeString,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = colors.accentOrange,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(colors.orangeBadgeBackground, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { store.forcePullNextLearningCard() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentOrange,
                    contentColor = colors.onOrange
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Süreyi Beklemeden Şimdi Çalış",
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            if (store.filteredPoolCards.any { it.stage == CardStage.NEW } && store.remainingNewToday == 0) {
                OutlinedButton(
                    onClick = {
                        store.updateSettings { it.copy(dailyNewCardLimit = it.dailyNewCardLimit + 10) }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = INCREASE_LIMIT_LABEL,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDatabaseView(
    onShowAIGenerator: () -> Unit,
    onImportCSV: () -> Unit,
    onLoadStarterPack: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        BloodyBrainLogoView(size = 105.dp, animated = false)

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Henüz Kelime Eklenmedi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Örnek kelimelerle başlayabilir, CSV içe aktarabilir veya AI ile kelime üretebilirsiniz.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onLoadStarterPack, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Örnek 30 Kelime", fontWeight = FontWeight.SemiBold)
        }

        StudyAddWordsActions(onShowAIGenerator, onImportCSV)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCompletedView(
    store: DeckStore,
    onShowAIGenerator: () -> Unit,
    onImportCSV: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(60.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (store.selectedStudyDeck != null) {
                "'${store.selectedStudyDeck}' Destesi Tamamlandı!"
            } else {
                "Bugünkü Çalışma Tamamlandı!"
            },
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Günlük hedeflerinizi ve bekleyen tekrarlarınızı tamamladınız. Kartlarınız hatırlama durumunuza göre sonraki günlere planlandı.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniBox(
                title = "Bugün öğrenilen (tüm desteler)",
                value = "${store.dailyProgress.newCardsStudied}",
                color = colors.accentBlue,
                background = colors.blueBadgeBackground
            )
            StatMiniBox(
                title = "Bugün tekrar edilen (tüm desteler)",
                value = "${store.dailyProgress.reviewsCompleted}",
                color = colors.success,
                background = colors.successBadgeBackground
            )
            StatMiniBox(
                title = if (store.selectedStudyDeck == null) "Toplam kelime" else "Destedeki kelime",
                value = "${store.filteredPoolCards.size}",
                color = colors.accentPurple,
                background = colors.purpleBadgeBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (store.filteredPoolCards.any { it.stage == CardStage.NEW }) {
            Button(
                onClick = {
                    store.updateSettings { it.copy(dailyNewCardLimit = it.dailyNewCardLimit + 10) }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(INCREASE_LIMIT_LABEL, fontWeight = FontWeight.SemiBold)
            }
        }

        StudyAddWordsActions(onShowAIGenerator, onImportCSV)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyAddWordsActions(onShowAIGenerator: () -> Unit, onImportCSV: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onShowAIGenerator, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(AI_ACTION_LABEL, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(onClick = onImportCSV, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(CSV_IMPORT_LABEL, fontWeight = FontWeight.SemiBold)
        }
    }
}

// MARK: - Subcomponents

@Composable
private fun CounterPill(
    title: String,
    count: Int,
    color: Color,
    background: Color,
    isStreak: Boolean = false
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .background(background, CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        if (isStreak) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(7.dp).background(color, CircleShape))
        }
        Text(title, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        Text(
            text = if (isStreak) "$count gün" else "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ArtikelLegendDot(
    artikel: Artikel,
    label: String
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(artikel.themeColor(), CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText
        )
    }
}

@Composable
private fun RatingButton(
    rating: ReviewRating,
    intervalText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ratingColor(rating),
            contentColor = colors.onRating(rating)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = rating.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = intervalText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatMiniBox(
    title: String,
    value: String,
    color: Color,
    background: Color
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .widthIn(min = 96.dp, max = 150.dp)
            .heightIn(min = 66.dp)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText,
            textAlign = TextAlign.Center
        )
    }
}

// MARK: - Android Studio Compose Previews

@Preview(name = "Kart sorusu · Açık", widthDp = 360, heightDp = 680, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Kart sorusu · Koyu ve büyük yazı", widthDp = 360, heightDp = 680, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StudyCardQuestionPreview() {
    StudyCardPreview(revealed = false)
}

@Preview(name = "Kart cevabı · Koyu", widthDp = 412, heightDp = 740, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Kart cevabı · Dar ve büyük yazı", widthDp = 360, heightDp = 760, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun StudyCardRevealedPreview() {
    StudyCardPreview(revealed = true)
}

@Preview(name = "Türkçe anlam girişi · Yatay", widthDp = 640, heightDp = 320, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StudyArticlelessCardPreview() {
    StudyCardPreview(revealed = false, articleless = true)
}

@Composable
private fun StudyCardPreview(revealed: Boolean, articleless: Boolean = false) {
    ArtikelMeisterTheme {
        val sampleCard = remember(articleless) {
            Flashcard(
                artikel = if (articleless) null else Artikel.DAS,
                word = "Kraftfahrzeughaftpflichtversicherung",
                meaning = "Motorlu taşıtlar için zorunlu mali sorumluluk sigortası",
                deckName = "B1 Wortschatz"
            )
        }
        Surface(color = LocalAppColors.current.windowBackground) {
            ActiveCardSessionView(
                card = sampleCard,
                showMeaningOnFront = false,
                isRevealed = revealed,
                typedArtikel = if (articleless) "" else "das",
                onTypedArtikelChange = {},
                guessResult = if (revealed) ArtikelGuessResult.CORRECT else ArtikelGuessResult.NONE,
                availableRatings = ReviewRating.entries,
                onCheckAndReveal = {},
                onSelectArtikel = {},
                onSubmitRating = {}
            )
        }
    }
}

@Preview(name = "Üst çubuk · Dar", widthDp = 360, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Üst çubuk · Geniş", widthDp = 840, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StudyHeaderBarPreview() {
    ArtikelMeisterTheme {
        val onOpenDrawer: (() -> Unit)? = if (LocalConfiguration.current.screenWidthDp < 700) {
            {}
        } else {
            null
        }
        StudyHeaderBar(
            selectedDeck = "B1 Wortschatz",
            deckNames = { listOf("Genel", "B1 Wortschatz") },
            totalCardCount = 50,
            deckCardCount = { 25 },
            onSelectDeck = {},
            onOpenDrawer = onOpenDrawer,
            onShowNewDeck = {},
            onShowAIGenerator = {},
            onImportCSV = {}
        ) {
            StudyQueueSummary(newCount = 10, learningCount = 3, reviewCount = 8, streakDays = 4)
        }
    }
}

/**
 * Front of an article-less card: the German word alone (no der/die/das buttons) and a
 * single-line field for the Turkish meaning. IME "Done" submits like "Kontrol Et".
 */
@Composable
private fun ArticlelessFront(
    card: Flashcard,
    typed: String,
    onTypedChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val colors = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val wordFontSize = when {
        card.word.length > 22 -> 20.sp
        card.word.length > 16 -> 24.sp
        card.word.length > 12 -> 28.sp
        else -> 34.sp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = card.word,
            fontSize = wordFontSize,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = typed,
            onValueChange = onTypedChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.study_meaning_placeholder)) },
            label = { Text(stringResource(R.string.study_meaning_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        )
    }
}
