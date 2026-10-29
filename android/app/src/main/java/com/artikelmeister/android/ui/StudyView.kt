package com.artikelmeister.android.ui

import androidx.compose.material3.MaterialTheme
import com.artikelmeister.android.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.selectable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CsvCards
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.Flashcard
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.ReviewRating
import com.artikelmeister.android.SM2Scheduler
import com.artikelmeister.android.SpeechManager
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
    var isRevealed by remember { mutableStateOf(false) }
    var typedArtikel by remember { mutableStateOf("") }
    var guessResult by remember { mutableStateOf(ArtikelGuessResult.NONE) }

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
                importMessage = """
                    Yeni eklenen kelime: ${res.addedCount}
                    Güncellenen kelime: ${res.updatedCount}
                    Zaten mevcut (atlanan): ${res.skippedCount}
                    Geçersiz/Atlanan satır: ${res.invalidLines}
                """.trimIndent()
                offerImportUndo = store.canUndoImport
                showImportAlert = true
            }.onFailure { err ->
                offerImportUndo = false
                importMessage = "Hata: ${err.localizedMessage ?: "CSV okunamadı"}"
                showImportAlert = true
            }
        }
    }

    fun resetCardInputState() {
        isRevealed = false
        typedArtikel = ""
        guessResult = ArtikelGuessResult.NONE
    }

    fun checkAndRevealCard(card: Flashcard) {
        if (isRevealed) return
        guessResult = if (com.artikelmeister.android.AnswerChecker.isCorrect(card, typedArtikel)) {
            ArtikelGuessResult.CORRECT
        } else {
            ArtikelGuessResult.WRONG
        }
        isRevealed = true
        if (store.settings.autoPlayPronunciation) {
            SpeechManager.speak(card.fullGermanTerm)
        }
    }

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
            store = store,
            onOpenDrawer = onOpenDrawer,
            onShowNewDeck = { showNewDeckSheet = true },
            onShowAIGenerator = { showAIGenerator = true },
            onImportCSV = { csvLauncher.launch(CsvCards.ACCEPTED_MIME_TYPES) }
        )

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
                        onShowAIGenerator = { showAIGenerator = true },
                        onImportCSV = { csvLauncher.launch(CsvCards.ACCEPTED_MIME_TYPES) },
                        onLoadStarterPack = {
                            val res = store.loadStarterGermanPack()
                            importMessage = "Örnek paket yüklendi: ${res.addedCount} yeni kelime eklendi."
                            offerImportUndo = store.canUndoImport
                            showImportAlert = true
                        }
                    )
                }

                currentCard != null -> {
                    ActiveCardSessionView(
                        card = currentCard,
                        store = store,
                        isRevealed = isRevealed,
                        typedArtikel = typedArtikel,
                        onTypedArtikelChange = { typedArtikel = it },
                        guessResult = guessResult,
                        availableRatings = availableRatingsForCurrentState,
                        onCheckAndReveal = { checkAndRevealCard(currentCard) },
                        onSubmitRating = { rating -> submitRating(currentCard, rating) }
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
                        onShowAIGenerator = { showAIGenerator = true },
                        onImportCSV = { csvLauncher.launch(CsvCards.ACCEPTED_MIME_TYPES) }
                    )
                }
            }
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
            onDismissRequest = { showImportAlert = false; offerImportUndo = false; store.finishImportUndoWindow() },
            title = { Text("CSV İçe Aktarma Sonucu", fontWeight = FontWeight.Bold) },
            text = { Text(importMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { showImportAlert = false; offerImportUndo = false; store.finishImportUndoWindow() }) {
                    Text("Tamam")
                }
            },
            dismissButton = if (offerImportUndo && store.canUndoImport) {
                {
                    TextButton(onClick = {
                        val undone = store.undoLastImport()
                        offerImportUndo = false
                        importMessage = if (undone) "İçe aktarma geri alındı." else importMessage
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
    store: DeckStore,
    onOpenDrawer: (() -> Unit)?,
    onShowNewDeck: () -> Unit,
    onShowAIGenerator: () -> Unit,
    onImportCSV: () -> Unit
) {
    val colors = LocalAppColors.current
    var showDeckMenu by remember { mutableStateOf(false) }
    var showQueueStats by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Menü Butonu
            Surface(
                color = colors.textBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onOpenDrawer?.invoke() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.action_open_menu),
                        tint = colors.primaryText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 2. Aç/Kapa Chevron Butonu (İstatistikler)
            val chevronRotation by animateFloatAsState(
                targetValue = if (showQueueStats) 180f else 0f,
                label = "chevronRotation"
            )
            Surface(
                color = if (showQueueStats) colors.accentBlue.copy(alpha = 0.15f) else colors.textBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (showQueueStats) colors.accentBlue.copy(alpha = 0.5f) else colors.cardBorderSubtle
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clickable { showQueueStats = !showQueueStats }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showQueueStats) "İstatistikleri Gizle" else "İstatistikleri Göster",
                        tint = if (showQueueStats) colors.accentBlue else colors.primaryText,
                        modifier = Modifier
                            .size(19.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            // 3. Deste Seçici Menüsü
            Box {
                Surface(
                    color = colors.textBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.cardBorderSubtle),
                    modifier = Modifier.clickable { showDeckMenu = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.accentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = store.selectedStudyDeck ?: "Tüm Desteler",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 85.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showDeckMenu,
                    onDismissRequest = { showDeckMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Tüm Desteler (${store.cards.size})") },
                        trailingIcon = {
                            if (store.selectedStudyDeck == null) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            store.selectedStudyDeck = null
                            showDeckMenu = false
                        }
                    )
                    HorizontalDivider()
                    store.allDeckNames.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text("$deck (${store.cardCount(deck)})") },
                            trailingIcon = {
                                if (store.selectedStudyDeck == deck) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                store.selectedStudyDeck = deck
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

            // 4. AI Kelime Butonu
            Button(
                onClick = onShowAIGenerator,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPurple,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "AI Kelime",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // 5. CSV Yükle Butonu
            OutlinedButton(
                onClick = onImportCSV,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "CSV",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primaryText,
                    maxLines = 1
                )
            }
        }

        // Açılır / Kapanır İstatistikler ve Sayaçlar Paneli
        AnimatedVisibility(
            visible = showQueueStats,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                HorizontalDivider(
                    color = colors.cardBorderSubtle.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Queue Counters: Yeni, Öğreniliyor, Tekrar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CounterPill(
                            title = "Yeni",
                            count = store.availableNewCardsCount,
                            color = colors.accentBlue
                        )
                        CounterPill(
                            title = "Öğreniliyor",
                            count = store.dueLearningCardsCount,
                            color = colors.accentOrange
                        )
                        CounterPill(
                            title = "Tekrar",
                            count = store.dueReviewCardsCount,
                            color = colors.vividGreen
                        )
                    }

                    // Streak Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(colors.accentOrange.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = colors.accentOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${store.currentStreakDays} Gün",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentOrange,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveCardSessionView(
    card: Flashcard,
    store: DeckStore,
    isRevealed: Boolean,
    typedArtikel: String,
    onTypedArtikelChange: (String) -> Unit,
    guessResult: ArtikelGuessResult,
    availableRatings: List<ReviewRating>,
    onCheckAndReveal: () -> Unit,
    onSubmitRating: (ReviewRating) -> Unit
) {
    val colors = LocalAppColors.current

    val cardBorderColor = when {
        !isRevealed -> colors.cardBorderSubtle
        guessResult == ArtikelGuessResult.CORRECT -> colors.vividGreen.copy(alpha = 0.85f)
        guessResult == ArtikelGuessResult.WRONG -> colors.vividRed.copy(alpha = 0.85f)
        else -> colors.cardBorderSubtle
    }

    val wordHighlightColor = when {
        !isRevealed -> colors.primaryText
        guessResult == ArtikelGuessResult.CORRECT -> colors.vividGreen
        guessResult == ArtikelGuessResult.WRONG -> colors.vividRed
        else -> colors.primaryText
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Main Flashcard Box (1:1 with StudyView.swift)
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(if (isRevealed) 3.5.dp else 1.dp, cardBorderColor),
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
                // Top metadata bar inside card
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = card.stage.title.uppercase(Locale("tr", "TR")),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = card.stage.color,
                        modifier = Modifier
                            .background(card.stage.color.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(colors.secondaryText.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
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
                            text = "Kolaylık: ${(card.easeFactor * 100).toInt()}% • Tekrar: ${card.totalReviews}",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = colors.secondaryText,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Dinle Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.secondaryText.copy(alpha = 0.12f))
                            .clickable {
                                val spokenText = if (isRevealed) card.fullGermanTerm else card.word
                                SpeechManager.speak(spokenText)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Almanca telaffuzu dinle",
                            tint = colors.primaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Dinle",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Artikel.entries.forEach { art ->
                                        val isSelected = typedArtikel.trim().equals(art.rawValue, ignoreCase = true)
                                        val artColor = art.themeColor()
                                        Surface(
                                            color = if (isSelected) artColor else colors.textBackground,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.5.dp,
                                                color = if (isSelected) artColor else artColor.copy(alpha = 0.55f)
                                            ),
                                            shadowElevation = if (isSelected) 4.dp else 0.dp,
                                            modifier = Modifier
                                                .width(76.dp)
                                                .height(48.dp)
                                                .selectable(
                                                    selected = isSelected,
                                                    role = Role.RadioButton,
                                                    onClick = { onTypedArtikelChange(art.rawValue) }
                                                )
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = art.rawValue,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) colors.onAccent else artColor
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
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            }

                            // The meaning is the answer for article-less cards -> never on the front.
                            if (!card.isArticleless && store.settings.showMeaningOnFront && card.meaning.isNotEmpty()) {
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
                            // Feedback Status Banner (Doğru / Yanlış)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .background(wordHighlightColor.copy(alpha = 0.15f), CircleShape)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (guessResult == ArtikelGuessResult.CORRECT) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Cancel
                                    },
                                    contentDescription = null,
                                    tint = wordHighlightColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                val statusText = if (guessResult == ArtikelGuessResult.CORRECT) {
                                    if (card.isArticleless) "DOĞRU!" else "DOĞRU! (${card.artikel?.grammaticalLabel})"
                                } else {
                                    val userCleaned = typedArtikel.trim()
                                    if (userCleaned.isEmpty()) {
                                        "BOŞ BIRAKILDI — Doğru ${if (card.isArticleless) "Anlam" else "Artikel"}: ${com.artikelmeister.android.AnswerChecker.expectedLabel(card)}"
                                    } else {
                                        "YANLIŞ ($userCleaned) — Doğru ${if (card.isArticleless) "Anlam" else "Artikel"}: ${com.artikelmeister.android.AnswerChecker.expectedLabel(card)}"
                                    }
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = wordHighlightColor
                                )
                            }

                            // Revealed Artikel + Word lit up in Green (if correct) or Red (if wrong)
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
                                        color = wordHighlightColor
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = card.word,
                                    fontSize = revealedWordFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = wordHighlightColor,
                                    textAlign = TextAlign.Start,
                                    softWrap = true,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
        ) {
            if (!isRevealed) {
                Button(
                    onClick = onCheckAndReveal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(240.dp)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
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
                if (guessResult == ArtikelGuessResult.WRONG) {
                    Text(
                        text = "Yanlış cevaplandığı için sadece 'Tekrar' veya 'Zor' seçilebilir:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.vividRed,
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .widthIn(max = if (guessResult == ArtikelGuessResult.WRONG) 340.dp else 640.dp)
                        .fillMaxWidth()
                ) {
                    availableRatings.forEach { rating ->
                        val nextInterval = SM2Scheduler.previewInterval(card = card, rating = rating)
                        RatingButton(
                            rating = rating,
                            intervalText = nextInterval,
                            onClick = { onSubmitRating(rating) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

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
            .padding(28.dp)
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
            color = colors.primaryText
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
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = colors.accentOrange,
            modifier = Modifier
                .background(colors.accentOrange.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { store.forcePullNextLearningCard() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Süreyi Beklemeden Şimdi Çalış", fontWeight = FontWeight.SemiBold)
            }

            if (store.filteredPoolCards.any { it.stage == com.artikelmeister.android.CardStage.NEW } && store.remainingNewToday == 0) {
                OutlinedButton(
                    onClick = {
                        store.updateSettings { it.copy(dailyNewCardLimit = it.dailyNewCardLimit + 10) }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+10 Yeni Kelime Aç", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        BloodyBrainLogoView(size = 105.dp, animated = true)

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Henüz Kelime Eklenmedi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "İlk sütunu artikel (der, die, das), ikinci sütunu Almanca kelime olan bir CSV dosyası yükleyerek veya Gemini AI ile üreterek hemen başlayabilirsiniz.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onShowAIGenerator,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gemini AI ile Kelime Üret", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onImportCSV,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CSV Dosyası Yükle", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onLoadStarterPack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Örnek 30 Kelime", fontWeight = FontWeight.SemiBold)
            }
        }
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
            tint = colors.vividGreen,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatMiniBox(
                title = "Yeni Öğrenilen",
                value = "${store.dailyProgress.newCardsStudied}",
                color = colors.accentBlue
            )
            StatMiniBox(
                title = "Tekrar Edilen",
                value = "${store.dailyProgress.reviewsCompleted}",
                color = colors.vividGreen
            )
            StatMiniBox(
                title = "Deste Kelimesi",
                value = "${store.filteredPoolCards.size}",
                color = colors.accentPurple
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (store.filteredPoolCards.any { it.stage == com.artikelmeister.android.CardStage.NEW }) {
                Button(
                    onClick = {
                        store.updateSettings { it.copy(dailyNewCardLimit = it.dailyNewCardLimit + 10) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+10 Yeni Kelime Aç", fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onShowAIGenerator,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Kelime Üret (A1–C1)", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onImportCSV,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CSV İçe Aktar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// MARK: - Subcomponents

@Composable
private fun CounterPill(
    title: String,
    count: Int,
    color: Color
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryText,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
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
            color = colors.secondaryText,
            maxLines = 1
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
    ) {
        Text(
            text = intervalText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = rating.color,
            maxLines = 1
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(rating.color)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(
                text = rating.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatMiniBox(
    title: String,
    value: String,
    color: Color
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(110.dp)
            .height(66.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(6.dp)
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
            maxLines = 1
        )
    }
}

@Composable
private fun ArtikelBadge(
    artikel: Artikel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Surface(
        color = if (isSelected) artikel.themeColor() else colors.textBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.5.dp,
            color = if (isSelected) artikel.themeColor() else artikel.themeColor().copy(alpha = 0.55f)
        ),
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        modifier = modifier
            .width(76.dp)
            .height(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = artikel.rawValue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else artikel.themeColor()
            )
        }
    }
}

// MARK: - Android Studio Compose Previews

@Preview(name = "1. Kart Soru Ekranı (Tahmin Bekliyor)", showBackground = true, backgroundColor = 0xFF161618)
@Composable
fun StudyCardQuestionPreview() {
    val sampleCard = Flashcard(
        artikel = Artikel.DAS,
        word = "Arbeitsalltag",
        meaning = "İş hayatı / Günlük çalışma rutini",
        deckName = "B1 Wortschatz"
    )
    ArtikelMeisterTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161618))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color(0xFF212124),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 310.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(22.dp)
                ) {
                    Text(
                        text = "Artikel'i Seçin",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF98989F)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ArtikelBadge(
                                artikel = Artikel.DER,
                                isSelected = false,
                                onClick = {}
                            )
                            ArtikelBadge(
                                artikel = Artikel.DIE,
                                isSelected = false,
                                onClick = {}
                            )
                            ArtikelBadge(
                                artikel = Artikel.DAS,
                                isSelected = true,
                                onClick = {}
                            )
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Text(
                            text = sampleCard.word,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF5F5F7)
                        )
                    }

                    Text(
                        text = "Kelimeyi hatırlamaya çalışın ve uygun artikeli seçin",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF98989F)
                    )
                }
            }
        }
    }
}

@Preview(name = "2. Kart Cevap Açık (Puanlama)", showBackground = true, backgroundColor = 0xFF161618)
@Composable
fun StudyCardRevealedPreview() {
    val sampleCard = Flashcard(
        artikel = Artikel.DAS,
        word = "Buch",
        meaning = "Kitap",
        deckName = "A1 Wortschatz"
    )
    ArtikelMeisterTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161618))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color(0xFF212124),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(3.5.dp, Color(0.12f, 0.78f, 0.35f, 0.85f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 310.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(22.dp)
                ) {
                    Text(
                        text = "Doğru!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0.12f, 0.78f, 0.35f)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "das",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Artikel.DAS.themeColor()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = sampleCard.word,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0.12f, 0.78f, 0.35f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = sampleCard.meaning,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF98989F)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReviewRating.entries.forEach { rating ->
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = rating.color),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(text = rating.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "3. Üst Bar Önizleme", showBackground = true, backgroundColor = 0xFF212124)
@Composable
fun StudyHeaderBarPreview() {
    ArtikelMeisterTheme {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212124))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                color = Color(0xFF121214),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = Color(0xFFF5F5F7), modifier = Modifier.size(17.dp))
                }
            }
            Surface(
                color = Color(0xFF121214),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFF5F5F7), modifier = Modifier.size(19.dp))
                }
            }
            Surface(
                color = Color(0xFF121214),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tüm Desteler", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF5F5F7))
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF98989F),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI Kelime", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = {},
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = Color(0xFFF5F5F7),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF5F5F7))
            }
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
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
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
