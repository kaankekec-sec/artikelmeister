package com.artikelmeister.android.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
    val context = LocalContext.current
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
                val targetDeck = store.selectedStudyDeck ?: context.getString(R.string.common_default_deck)
                val res = store.importCSVFromUri(uri, defaultDeckName = targetDeck)
                importTitle = context.getString(R.string.study_csv_imported_title)
                importMessage = context.getString(
                    R.string.study_csv_imported_msg,
                    res.addedCount, res.updatedCount, res.skippedCount, res.invalidLines
                )
                offerImportUndo = store.canUndoImport
                showImportAlert = true
            }.onFailure { error ->
                offerImportUndo = false
                importTitle = context.getString(R.string.study_csv_read_error_title)
                importMessage = (error as? CsvCards.RejectedException)?.message
                    ?: context.getString(R.string.study_csv_read_error_fallback)
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
            onShowAIGenerator = openAIGenerator
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
                            importTitle = context.getString(R.string.study_starter_added_title)
                            importMessage = context.getString(R.string.study_starter_added_msg, res.addedCount)
                            offerImportUndo = store.canUndoImport
                            showImportAlert = true
                        }
                    )
                }

                currentCard != null -> {
                    androidx.compose.runtime.key(currentCard.id) {
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
                }

                store.selectedStudyDeck != null && store.filteredPoolCards.isEmpty() -> {
                    StateMessageView(
                        icon = Icons.Outlined.Folder,
                        title = stringResource(R.string.study_empty_deck_title),
                        message = stringResource(R.string.study_empty_deck_msg),
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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = if (offerImportUndo && store.canUndoImport) {
                {
                    TextButton(onClick = {
                        val undone = store.undoLastImport()
                        closeImportAlert()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(if (undone) R.string.study_undo_done else R.string.study_undo_failed)
                            )
                        }
                    }) {
                        Text(stringResource(R.string.action_undo))
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
    queueSummary: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    var showDeckMenu by remember { mutableStateOf(false) }
    var showQueueStats by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val showActionLabels = maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.3f
        val chevronRotation by animateFloatAsState(
            targetValue = if (showQueueStats) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "chevronRotation"
        )
        val summaryOpenDesc = stringResource(R.string.study_summary_state_open)
        val summaryClosedDesc = stringResource(R.string.study_summary_state_closed)
        val summaryToggleLabel = stringResource(R.string.study_summary_toggle)
        val controlShape = RoundedCornerShape(14.dp)

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onOpenDrawer != null) {
                    val menuDesc = stringResource(R.string.action_open_menu)
                    Surface(
                        onClick = onOpenDrawer,
                        shape = controlShape,
                        color = colors.textBackground,
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = menuDesc }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Left: Clean deck selector pill sharing the exact 48dp height & 14dp corner radius
                Box(Modifier.weight(1f, fill = false)) {
                    Surface(
                        onClick = { showDeckMenu = true },
                        shape = controlShape,
                        color = colors.textBackground,
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier
                            .widthIn(min = 48.dp, max = 240.dp)
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedDeck == null) Icons.Outlined.Folder else Icons.Filled.Folder,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = selectedDeck ?: stringResource(R.string.study_all_decks),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showDeckMenu,
                        onDismissRequest = { showDeckMenu = false }
                    ) {
                        StudyDeckMenuItem(
                            label = stringResource(R.string.study_all_decks_count, totalCardCount),
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
                            text = { Text(stringResource(R.string.study_new_deck_menu)) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showDeckMenu = false
                                onShowNewDeck()
                            }
                        )
                    }
                }

                // AI Word Generator button immediately to the right of the deck selector
                val aiLabel = stringResource(R.string.action_ai_generate)
                Surface(
                    onClick = onShowAIGenerator,
                    shape = controlShape,
                    color = colors.purpleBadgeBackground,
                    border = BorderStroke(1.dp, colors.accentPurple.copy(alpha = 0.28f)),
                    modifier = if (showActionLabels) {
                        Modifier.height(48.dp)
                    } else {
                        Modifier
                            .size(48.dp)
                            .semantics { contentDescription = aiLabel }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = if (showActionLabels) 14.dp else 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = if (showActionLabels) null else aiLabel,
                            tint = colors.accentPurple,
                            modifier = Modifier.size(19.dp)
                        )
                        if (showActionLabels) {
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = aiLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accentPurple
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Far right: Study Summary toggle button

                Surface(
                    onClick = { showQueueStats = !showQueueStats },
                    shape = controlShape,
                    color = if (showQueueStats) colors.blueBadgeBackground else colors.textBackground,
                    border = BorderStroke(
                        1.dp,
                        if (showQueueStats) colors.accentBlue.copy(alpha = 0.35f) else colors.cardBorderSubtle
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = summaryToggleLabel
                            stateDescription = if (showQueueStats) summaryOpenDesc else summaryClosedDesc
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (showQueueStats) colors.accentBlue else colors.secondaryText,
                            modifier = Modifier
                                .size(21.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

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
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        HorizontalDivider(
            color = colors.cardBorderSubtle,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CounterPill(stringResource(R.string.study_summary_new), newCount, colors.accentBlue, colors.blueBadgeBackground)
            CounterPill(stringResource(R.string.study_summary_learning), learningCount, colors.accentOrange, colors.orangeBadgeBackground)
            CounterPill(stringResource(R.string.study_summary_review), reviewCount, colors.success, colors.successBadgeBackground)
            CounterPill(stringResource(R.string.study_summary_streak), streakDays, colors.accentOrange, colors.orangeBadgeBackground, isStreak = true)
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

private data class BottomActionsState(
    val isRevealed: Boolean,
    val isArticleless: Boolean,
    val ratings: List<ReviewRating>
)

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
            guessResult == ArtikelGuessResult.CORRECT -> colors.feedbackCorrect.copy(alpha = 0.72f)
            guessResult == ArtikelGuessResult.WRONG -> colors.feedbackIncorrect.copy(alpha = 0.72f)
            else -> colors.cardBorderSubtle
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardBorderColor"
    )
    val cardBorderWidth by animateDpAsState(
        targetValue = if (isRevealed) 1.75.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "cardBorderWidth"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.992f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardRevealScale"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Flashcard
            Surface(
                color = colors.controlBackground,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(cardBorderWidth, cardBorderColor),
                shadowElevation = if (colors.isDark) (if (isRevealed) 10.dp else 4.dp) else (if (isRevealed) 6.dp else 2.dp),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 288.dp)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Quiet, unified single-baseline card header aligned to the top
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 10.dp)
                                .clip(CircleShape)
                                .background(colors.textBackground)
                                .border(1.dp, colors.cardBorderSubtle, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(colors.stageColor(card.stage), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(card.stage.titleRes()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.stageColor(card.stage),
                                maxLines = 1
                            )
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText.copy(alpha = 0.55f)
                            )
                            Text(
                                text = card.deckName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ListenPill(
                            onClick = {
                                val spokenText = if (isRevealed) card.fullGermanTerm else card.word
                                SpeechManager.speak(spokenText)
                            }
                        )
                    }

                    // Truly vertically centered centerpiece (symmetric top/bottom clearance)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(top = 52.dp, bottom = 52.dp)
                    ) {
                        // Keep live region in place so TalkBack announces reveal status.
                        val userAnswer = typedArtikel.trim()
                        val statusAnnouncement = if (isRevealed) {
                            when {
                                isCorrect -> card.artikel?.let {
                                    stringResource(R.string.study_correct_with_label, stringResource(it.grammaticalLabelRes()))
                                } ?: stringResource(R.string.study_correct)
                                else -> {
                                    val answerKind = stringResource(
                                        if (card.isArticleless) R.string.study_answer_kind_meaning
                                        else R.string.study_answer_kind_artikel
                                    )
                                    val wrongPart = if (userAnswer.isEmpty()) {
                                        stringResource(R.string.study_blank)
                                    } else {
                                        stringResource(R.string.study_wrong, userAnswer)
                                    }
                                    "$wrongPart. ${stringResource(R.string.study_correct_answer_is, answerKind, AnswerChecker.expectedLabel(card))}"
                                }
                            }
                        } else ""

                        Box(
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                                if (statusAnnouncement.isNotEmpty()) {
                                    contentDescription = statusAnnouncement
                                }
                            }
                        ) {
                            if (isRevealed) {
                                val feedbackColor = if (isCorrect) colors.feedbackCorrect else colors.feedbackIncorrect
                                Surface(
                                    shape = CircleShape,
                                    color = feedbackColor.copy(alpha = if (colors.isDark) 0.16f else 0.11f),
                                    border = BorderStroke(1.dp, feedbackColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(
                                            horizontal = if (isCorrect) 12.dp else 10.dp,
                                            vertical = 4.dp
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = feedbackColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        if (!isCorrect) {
                                            Text(
                                                text = userAnswer.ifEmpty { "—" },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = feedbackColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 180.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Center Content: Front (German Word / Articleless Input) vs Back (Revealed Result)
                        AnimatedContent(
                            targetState = isRevealed,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180)))
                                    .togetherWith(fadeOut(tween(130)))
                            },
                            label = "card_flip"
                        ) { revealed ->
                            if (!revealed) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (card.isArticleless) {
                                        ArticlelessFront(
                                            card = card,
                                            typed = typedArtikel,
                                            onTypedChange = onTypedArtikelChange,
                                            onSubmit = onCheckAndReveal
                                        )
                                    } else {
                                        val wordFontSize = when {
                                            card.word.length > 22 -> 19.sp
                                            card.word.length > 16 -> 23.sp
                                            card.word.length > 12 -> 28.sp
                                            card.word.length > 8 -> 33.sp
                                            else -> 38.sp
                                        }

                                        Text(
                                            text = card.word,
                                            fontSize = wordFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primaryText,
                                            textAlign = TextAlign.Center,
                                            softWrap = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                        )
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
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                            modifier = Modifier.width(64.dp)
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Action Area: der/die/das buttons or "Kontrol Et" before reveal; SM-2 ratings after.
            // Snapshot ratings inside targetState so resetting guessResult on reveal->unreveal never
            // flashes 4 buttons inside the exiting 2-button branch.
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val actionState = BottomActionsState(
                isRevealed = isRevealed,
                isArticleless = card.isArticleless,
                ratings = if (isRevealed) availableRatings else emptyList()
            )

            AnimatedContent(
                targetState = actionState,
                contentKey = { it.isRevealed to it.isArticleless },
                transitionSpec = {
                    (fadeIn(tween(190, delayMillis = 35)) + scaleIn(initialScale = 0.97f, animationSpec = tween(190, delayMillis = 35)))
                        .togetherWith(fadeOut(tween(110)))
                        .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) }))
                },
                label = "bottom_actions"
            ) { state ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                ) {
                    if (!state.isRevealed) {
                        if (state.isArticleless) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onCheckAndReveal()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentBlue,
                                    contentColor = colors.onBlue
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .widthIn(min = 200.dp, max = 280.dp)
                                    .heightIn(min = 54.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.study_check_button),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .fillMaxWidth()
                                    .selectableGroup()
                            ) {
                                Artikel.entries.forEach { art ->
                                    val isSelected = typedArtikel.trim().equals(art.rawValue, ignoreCase = true)
                                    ArtikelChoiceButton(
                                        artikel = art,
                                        isSelected = isSelected,
                                        onClick = { onSelectArtikel(art) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        val ratingsToRender = state.ratings
                        val useSingleRowRatings = ratingsToRender.size <= 2 ||
                            (configuration.screenWidthDp >= 400 && density.fontScale <= 1.25f)
                        val containerMaxWidth = if (ratingsToRender.size <= 2) 340.dp else 640.dp

                        if (useSingleRowRatings) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .widthIn(max = containerMaxWidth)
                                    .fillMaxWidth()
                            ) {
                                ratingsToRender.forEach { rating ->
                                    RatingButton(
                                        rating = rating,
                                        intervalText = SM2Scheduler.previewInterval(card = card, rating = rating),
                                        onClick = { onSubmitRating(rating) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .widthIn(max = containerMaxWidth)
                                    .fillMaxWidth()
                            ) {
                                ratingsToRender.chunked(2).forEach { rowRatings ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowRatings.forEach { rating ->
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
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtikelChoiceButton(
    artikel: Artikel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val artColor = colors.artikelColor(artikel)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artikelButtonScale"
    )

    Surface(
        color = if (isSelected) artColor else colors.artikelBadgeBackground(artikel),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.25.dp,
            color = if (isSelected) artColor else artColor.copy(alpha = 0.50f)
        ),
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        modifier = modifier
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = artikel.rawValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) colors.onArtikel(artikel) else artColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Quiet compact audio trigger in the card header. The outer Box provides a 48dp touch target while
 * keeping the visible pill on the exact same baseline as the card metadata pill.
 */
@Composable
private fun ListenPill(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val listenA11y = stringResource(R.string.study_listen_a11y)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = listenA11y, role = Role.Button, onClick = onClick)
    ) {
        Surface(
            color = colors.textBackground,
            shape = CircleShape,
            border = BorderStroke(1.dp, colors.cardBorderSubtle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.study_listen),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                    maxLines = 1
                )
            }
        }
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
            text = stringResource(R.string.study_learning_timer_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = timeString,
            style = MaterialTheme.typography.headlineLarge.tabular(),
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = colors.accentOrange,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(colors.orangeBadgeBackground, RoundedCornerShape(16.dp))
                .border(1.dp, colors.accentOrange.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )

        if (store.filteredPoolCards.any { it.stage == CardStage.NEW } && store.remainingNewToday == 0) {
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    store.updateSettings { it.copy(dailyNewCardLimit = it.dailyNewCardLimit + 10) }
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.study_increase_limit_10),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
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
            text = stringResource(R.string.study_empty_db_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.study_empty_db_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onLoadStarterPack,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.study_starter_pack_btn), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                stringResource(R.string.study_done_deck_title, store.selectedStudyDeck.orEmpty())
            } else {
                stringResource(R.string.study_done_today_title)
            },
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniBox(
                title = stringResource(R.string.study_done_learned_today),
                value = "${store.dailyProgress.newCardsStudied}",
                color = colors.accentBlue,
                background = colors.blueBadgeBackground
            )
            StatMiniBox(
                title = stringResource(R.string.study_done_reviewed_today),
                value = "${store.dailyProgress.reviewsCompleted}",
                color = colors.success,
                background = colors.successBadgeBackground
            )
            StatMiniBox(
                title = stringResource(
                    if (store.selectedStudyDeck == null) R.string.study_total_words else R.string.study_deck_words
                ),
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.study_increase_limit_10), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
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
        OutlinedButton(
            onClick = onShowAIGenerator,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.cardBorderSubtle),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_ai_generate), fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onImportCSV,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.cardBorderSubtle),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.study_csv_import_btn), fontWeight = FontWeight.SemiBold)
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
            text = if (isStreak) stringResource(R.string.study_streak_days_unit, count) else "$count",
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = color
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
    val accent = colors.ratingColor(rating)
    val containerColor = if (colors.isDark) accent.copy(alpha = 0.16f) else lerp(Color.White, accent, 0.11f)
    val borderColor = if (colors.isDark) accent.copy(alpha = 0.45f) else accent.copy(alpha = 0.25f)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ratingButtonScale"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp)
        ) {
            Text(
                text = stringResource(rating.titleRes()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = intervalText,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    Surface(
        color = background,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
        modifier = Modifier
            .widthIn(min = 96.dp, max = 150.dp)
            .heightIn(min = 68.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.tabular(),
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
            onShowAIGenerator = {}
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
