package com.artikelmeister.android.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.ArtikelFilter
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.CsvCards
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.Flashcard
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.R
import com.artikelmeister.android.SpeechManager
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// =====================================================================================
// Article filter options (UI-side). Designed for 4 article groups: der / die / das / '-'.
// =====================================================================================

private const val FILTER_ALL = ""

/** Filter key for article-less words ('-'); see ArtikelFilter in CardLogic.kt / CONTRACT.md. */
private const val FILTER_NO_ARTICLE = ArtikelFilter.NONE

private val trLocale = Locale("tr", "TR")

/** Medium width class: inline quick-add, no FAB, full "AI ile üret" label. */
private const val WIDE_LAYOUT_MIN_WIDTH_DP = 600

/** Expanded width class: CSV import/export also shown as buttons instead of in the overflow. */
private const val EXPANDED_LAYOUT_MIN_WIDTH_DP = 840
private const val AI_GENERATE_LABEL = "AI ile kelime üret"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordListView(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onOpenDrawer: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedDeckFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var artikelFilterKey by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var selectedStageFilter by rememberSaveable { mutableStateOf<CardStage?>(null) }

    // Quick-add form state (shared between the inline bar on wide screens and the dialog on phones)
    // null = article-less ('-') word.
    var newArtikel by rememberSaveable { mutableStateOf<Artikel?>(Artikel.DER) }
    var newWord by rememberSaveable { mutableStateOf("") }
    var newMeaning by rememberSaveable { mutableStateOf("") }
    var newDeckName by rememberSaveable { mutableStateOf("Genel") }

    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showNewDeckSheet by remember { mutableStateOf(false) }
    var deckToRename by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var alertTitle by remember { mutableStateOf<String?>(null) }
    var showImportGuide by rememberSaveable { mutableStateOf(false) }
    // True only while alertMessage is a successful CSV import result that can still be undone.
    var offerImportUndo by remember { mutableStateOf(false) }
    var showAIGenerator by remember { mutableStateOf(false) }

    // Deferred deletion: cards are hidden immediately and only removed from the store when the
    // Undo snackbar times out / is dismissed (or when the screen leaves composition). This keeps
    // SRS progress & avoids writing a sync tombstone if the user taps "Geri al".
    val pendingDeleteIds = remember { mutableStateListOf<String>() }

    fun commitDelete(id: String) {
        if (pendingDeleteIds.remove(id)) store.deleteCard(id)
    }

    fun requestDelete(card: Flashcard) {
        if (pendingDeleteIds.contains(card.id)) return
        // Finish (commit) any previous pending delete before starting a new undo window.
        snackbarHostState.currentSnackbarData?.dismiss()
        pendingDeleteIds.add(card.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.words_deleted_snackbar, card.fullGermanTerm),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteIds.remove(card.id)
            } else {
                commitDelete(card.id)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingDeleteIds.toList().forEach { store.deleteCard(it) }
            pendingDeleteIds.clear()
        }
    }

    val filteredCards = remember(
        store.cards,
        selectedDeckFilter,
        artikelFilterKey,
        selectedStageFilter,
        searchText,
        pendingDeleteIds.toList()
    ) {
        val q = searchText.trim().lowercase(trLocale)
        store.cards.filter { card ->
            if (pendingDeleteIds.contains(card.id)) return@filter false
            if (selectedDeckFilter != null && card.deckName != selectedDeckFilter) return@filter false
            if (!ArtikelFilter.matches(card, artikelFilterKey.ifEmpty { null })) return@filter false
            if (selectedStageFilter != null && card.stage != selectedStageFilter) return@filter false
            if (q.isNotEmpty()) {
                return@filter card.word.lowercase(trLocale).contains(q) ||
                    card.meaning.lowercase(trLocale).contains(q) ||
                    card.displayArtikel.lowercase(Locale.ROOT).contains(q) ||
                    card.deckName.lowercase(trLocale).contains(q)
            }
            true
        }
    }
    val visibleTotal = store.cards.size - pendingDeleteIds.size
    val artikelCounts = remember(store.cards, selectedDeckFilter, pendingDeleteIds.toList()) {
        ArtikelFilter.counts(
            store.cards.filter { c ->
                !pendingDeleteIds.contains(c.id) &&
                    (selectedDeckFilter == null || c.deckName == selectedDeckFilter)
            }
        )
    }
    val hasActiveFilters = searchText.isNotBlank() || artikelFilterKey != FILTER_ALL ||
        selectedStageFilter != null || selectedDeckFilter != null

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val targetDeck = selectedDeckFilter ?: newDeckName
                val res = store.importCSVFromUri(uri, defaultDeckName = targetDeck)
                alertMessage = context.getString(
                    R.string.words_import_result,
                    res.addedCount, res.updatedCount, res.skippedCount, res.invalidLines
                )
                alertTitle = "CSV içe aktarıldı"
                offerImportUndo = store.canUndoImport
            }.onFailure { err ->
                alertTitle = "CSV içe aktarılamadı"
                offerImportUndo = false
                alertMessage = err.localizedMessage ?: context.getString(R.string.words_import_failed)
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csvContent = store.exportCSVString()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.words_export_success))
                }
            }.onFailure { err ->
                alertTitle = null
                alertMessage = context.getString(R.string.words_export_failed, err.localizedMessage ?: "")
            }
        }
    }

    val showImport = { showImportGuide = true }
    val launchExport = { exportCsvLauncher.launch(context.getString(R.string.words_export_file_name)) }

    fun submitQuickAdd(): Boolean? {
        val word = newWord.trim()
        if (word.isEmpty()) return null
        if (newArtikel == null && newMeaning.isBlank()) return null
        val added = store.addCard(newArtikel, word, newMeaning, newDeckName)
        if (added) {
            newWord = ""
            newMeaning = ""
        }
        return added
    }

    fun submitInlineWord() {
        val term = Flashcard(artikel = newArtikel, word = newWord.trim()).fullGermanTerm
        val added = submitQuickAdd() ?: return
        val message = context.getString(
            if (added) R.string.words_added_feedback else R.string.words_duplicate_feedback,
            term
        )
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
    ) {
        val isWide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH_DP.dp
        val isExpanded = maxWidth >= EXPANDED_LAYOUT_MIN_WIDTH_DP.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // ------------------------------------------------------------------ Header
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.controlBackground)
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)
            ) {
                WordPoolTitleRow(
                    totalCount = visibleTotal,
                    filteredCount = filteredCards.size,
                    isFiltered = hasActiveFilters,
                    isWide = isWide,
                    showAllActions = isExpanded,
                    canExport = store.cards.isNotEmpty(),
                    onOpenDrawer = onOpenDrawer,
                    onAiGenerate = { showAIGenerator = true },
                    onImport = showImport,
                    onExport = launchExport
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (isWide) {
                        QuickAddInlineBar(
                            store = store,
                            artikel = newArtikel,
                            onArtikelChange = { newArtikel = it },
                            word = newWord,
                            onWordChange = { newWord = it },
                            meaning = newMeaning,
                            onMeaningChange = { newMeaning = it },
                            deckName = newDeckName,
                            onDeckChange = { newDeckName = it },
                            onNewDeck = { showNewDeckSheet = true },
                            onSubmit = ::submitInlineWord
                        )
                    }

                    // Search + stage filter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SearchField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.weight(1f)
                        )
                        StageFilterButton(
                            selected = selectedStageFilter,
                            onSelect = { selectedStageFilter = it }
                        )
                    }

                    // Article filter: Tümü / der / die / das (/ '-')
                    ArtikelFilterSegments(
                        selectedKey = artikelFilterKey,
                        counts = artikelCounts,
                        onSelect = { artikelFilterKey = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isWide) Modifier.widthIn(max = 520.dp) else Modifier)
                    )
                }

                DeckChipRow(
                    store = store,
                    // Counts exclude cards that are hidden while their Undo snackbar is showing.
                    countFor = { deck ->
                        store.cards.count { c ->
                            (deck == null || c.deckName == deck) && !pendingDeleteIds.contains(c.id)
                        }
                    },
                    selectedDeck = selectedDeckFilter,
                    onSelectDeck = { deck ->
                        selectedDeckFilter = deck
                        if (deck != null) newDeckName = deck
                    },
                    onNewDeck = { showNewDeckSheet = true },
                    onRenameDeck = { deckToRename = it },
                    onDeleteDeck = { deck ->
                        store.deleteDeck(name = deck, deleteWords = false)
                        if (selectedDeckFilter == deck) selectedDeckFilter = null
                    }
                )
            }

            HorizontalDivider(color = colors.cardBorderSubtle)

            // ------------------------------------------------------------------ Content
            if (filteredCards.isEmpty()) {
                val emptyModifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isWide) 0.dp else 72.dp)
                val isPoolEmpty = visibleTotal == 0
                val isUnfilteredEmptyDeck = selectedDeckFilter != null && searchText.isBlank() &&
                    artikelFilterKey == FILTER_ALL && selectedStageFilter == null
                Box(contentAlignment = Alignment.Center, modifier = emptyModifier) {
                    when {
                        isPoolEmpty || isUnfilteredEmptyDeck -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            StateMessageView(
                                icon = if (isPoolEmpty) Icons.Default.Inbox else Icons.Default.Folder,
                                title = if (isPoolEmpty) {
                                    stringResource(R.string.words_empty_title)
                                } else {
                                    stringResource(R.string.words_empty_deck_title, selectedDeckFilter.orEmpty())
                                },
                                message = stringResource(
                                    if (isPoolEmpty) R.string.words_empty_message else R.string.words_empty_deck_message
                                ),
                                primaryActionLabel = if (isWide) stringResource(R.string.words_add_word) else null,
                                primaryActionIcon = Icons.Default.Add,
                                onPrimaryAction = { showAddDialog = true },
                                secondaryActionLabel = if (isPoolEmpty) stringResource(R.string.words_csv_import) else null,
                                onSecondaryAction = showImport
                            )
                            TextButton(
                                onClick = { showAIGenerator = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.accentPurple)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AI_GENERATE_LABEL)
                            }
                        }
                        else -> StateMessageView(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.words_no_results_title),
                            message = stringResource(R.string.words_no_results_message),
                            primaryActionLabel = stringResource(R.string.words_clear_filters),
                            onPrimaryAction = {
                                searchText = ""
                                artikelFilterKey = FILTER_ALL
                                selectedStageFilter = null
                                selectedDeckFilter = null
                            }
                        )
                    }
                }
            } else {
                val resources = context.resources
                LazyColumn(
                    contentPadding = PaddingValues(bottom = if (isWide) 16.dp else 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredCards, key = { _, card -> card.id }) { index, card ->
                        WordRowItem(
                            card = card,
                            index = index,
                            dueText = dueDescription(resources, card, store.currentTime),
                            onSpeak = { SpeechManager.speak(card.fullGermanTerm) },
                            onEdit = { editingCard = card },
                            onDelete = { requestDelete(card) }
                        )
                        HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.4f))
                    }
                }
            }
        }

        if (!isWide) {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.words_add_word)) },
                containerColor = colors.accentBlue,
                contentColor = colors.onBlue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isWide) 8.dp else 80.dp)
                .widthIn(max = 560.dp)
        )
    }

    // ---------------------------------------------------------------------- Dialogs
    if (showAddDialog) {
        AddWordDialog(
            store = store,
            artikel = newArtikel,
            onArtikelChange = { newArtikel = it },
            word = newWord,
            onWordChange = { newWord = it },
            meaning = newMeaning,
            onMeaningChange = { newMeaning = it },
            deckName = newDeckName,
            onDeckChange = { newDeckName = it },
            onNewDeck = { showNewDeckSheet = true },
            onSubmit = { submitQuickAdd() },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showAIGenerator) {
        AIGeneratorDialog(
            store = store,
            authManager = authManager,
            onDismiss = { showAIGenerator = false }
        )
    }

    editingCard?.let { cardToEdit ->
        EditWordDialog(
            store = store,
            card = cardToEdit,
            onDismiss = { editingCard = null },
            onDelete = {
                editingCard = null
                requestDelete(cardToEdit)
            }
        )
    }

    if (showNewDeckSheet) {
        NewDeckDialog(
            store = store,
            onDismiss = { showNewDeckSheet = false },
            onCreated = { created ->
                newDeckName = created
                selectedDeckFilter = created
                showNewDeckSheet = false
            }
        )
    }

    deckToRename?.let { oldName ->
        RenameDeckDialog(
            store = store,
            oldName = oldName,
            onDismiss = { deckToRename = null },
            onRenamed = { renamed ->
                if (selectedDeckFilter == oldName) selectedDeckFilter = renamed
                if (newDeckName == oldName) newDeckName = renamed
                deckToRename = null
            }
        )
    }

    if (showImportGuide) {
        val targetDeck = selectedDeckFilter ?: newDeckName
        AlertDialog(
            onDismissRequest = { showImportGuide = false },
            title = { Text(stringResource(R.string.words_csv_import)) },
            text = {
                Text(
                    "Sütun sırası: Artikel, Kelime, Anlam, Deste (isteğe bağlı).\n\n" +
                        "Deste belirtilmeyen kelimeler „$targetDeck“ destesine aktarılır."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportGuide = false
                    importCsvLauncher.launch(CsvCards.ACCEPTED_MIME_TYPES)
                }) {
                    Text("Dosya seç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportGuide = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    alertMessage?.let { message ->
        val infoTitle = stringResource(R.string.dialog_info_title)
        AlertDialog(
            onDismissRequest = { alertMessage = null; offerImportUndo = false; store.finishImportUndoWindow() },
            title = { Text(alertTitle ?: infoTitle) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { alertMessage = null; offerImportUndo = false; store.finishImportUndoWindow() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = if (offerImportUndo && store.canUndoImport) {
                {
                    TextButton(onClick = {
                        val undone = store.undoLastImport()
                        offerImportUndo = false
                        alertTitle = null
                        alertMessage = if (undone) "İçe aktarma geri alındı." else null
                    }) {
                        Text(stringResource(R.string.action_undo))
                    }
                }
            } else null
        )
    }
}

// =====================================================================================
// Header pieces
// =====================================================================================

@Composable
private fun WordPoolTitleRow(
    totalCount: Int,
    filteredCount: Int,
    isFiltered: Boolean,
    isWide: Boolean,
    showAllActions: Boolean,
    canExport: Boolean,
    onOpenDrawer: (() -> Unit)?,
    onAiGenerate: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val colors = LocalAppColors.current
    var showOverflow by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        if (onOpenDrawer != null) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.action_open_menu),
                    tint = colors.primaryText
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.words_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (isFiltered) {
                    stringResource(R.string.words_count_filtered, filteredCount, totalCount)
                } else {
                    stringResource(R.string.words_count, totalCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                maxLines = 1
            )
        }

        if (isWide) {
            FilledTonalButton(
                onClick = onAiGenerate,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.purpleBadgeBackground,
                    contentColor = colors.accentPurple
                ),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AI_GENERATE_LABEL)
            }
        } else {
            FilledTonalIconButton(
                onClick = onAiGenerate,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.purpleBadgeBackground,
                    contentColor = colors.accentPurple
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = AI_GENERATE_LABEL)
            }
        }

        if (showAllActions) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Filled.MoveToInbox, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.words_csv_import), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onExport, enabled = canExport) {
                Icon(Icons.Filled.Outbox, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.words_csv_export), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = colors.primaryText
                    )
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_csv_import)) },
                        leadingIcon = { Icon(Icons.Filled.MoveToInbox, contentDescription = null) },
                        onClick = { showOverflow = false; onImport() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_csv_export)) },
                        leadingIcon = { Icon(Icons.Filled.Outbox, contentDescription = null) },
                        enabled = canExport,
                        onClick = { showOverflow = false; onExport() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckActionMenuItems(
    deck: String,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    DropdownMenuItem(
        text = { Text(stringResource(R.string.words_rename_deck)) },
        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        onClick = onRename
    )
    if (deck != "Genel") {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.words_delete_deck), color = colors.error) },
            leadingIcon = { Icon(Icons.Filled.FolderDelete, contentDescription = null, tint = colors.error) },
            onClick = onDelete
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.words_search_hint),
        modifier = modifier,
        imeAction = ImeAction.Search,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingAction = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = stringResource(R.string.words_search_clear),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null
    )
}

@Composable
private fun StageFilterButton(
    selected: CardStage?,
    onSelect: (CardStage?) -> Unit
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val filterLabel = stringResource(R.string.words_filter_stage)

    Box {
        if (selected == null) {
            val allStages = stringResource(R.string.words_filter_all_stages)
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.semantics { stateDescription = allStages }
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = filterLabel,
                    tint = colors.primaryText
                )
            }
        } else {
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = { Text(selected.title) },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.blueBadgeBackground,
                    selectedLabelColor = colors.accentBlue,
                    selectedLeadingIconColor = colors.accentBlue
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = true,
                    selectedBorderColor = colors.accentBlue
                ),
                modifier = Modifier
                    .widthIn(max = 168.dp)
                    .semantics { contentDescription = filterLabel }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.words_filter_all_stages)) },
                trailingIcon = { if (selected == null) Icon(Icons.Default.Check, contentDescription = null) },
                onClick = { onSelect(null); expanded = false },
                modifier = Modifier.semantics { this.selected = selected == null }
            )
            CardStage.entries.forEach { stage ->
                DropdownMenuItem(
                    text = { Text(stage.title) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colors.stageColor(stage), CircleShape)
                        )
                    },
                    trailingIcon = { if (selected == stage) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { onSelect(stage); expanded = false },
                    modifier = Modifier.semantics { this.selected = selected == stage }
                )
            }
        }
    }
}

/**
 * Segmented filter: Tümü / der / die / das, plus a reserved '-' segment for article-less words.
 * Uses equal-weight segments so 5 options still fit a 360dp phone without scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtikelFilterSegments(
    selectedKey: String,
    counts: ArtikelFilter.Counts,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    data class Option(val key: String, val label: String, val a11y: String, val color: Color, val background: Color)

    val options = buildList {
        val allLabel = stringResource(R.string.words_filter_all)
        add(Option(FILTER_ALL, allLabel, allLabel, colors.accentBlue, colors.blueBadgeBackground))
        Artikel.entries.forEach { art ->
            add(
                Option(
                    art.rawValue, art.rawValue, art.rawValue,
                    colors.artikelColor(art), colors.artikelBadgeBackground(art)
                )
            )
        }
        // 4th article group: article-less words ('-').
        add(
            Option(
                FILTER_NO_ARTICLE,
                stringResource(R.string.words_filter_no_article),
                stringResource(R.string.words_filter_no_article_a11y),
                colors.artikelColor(null),
                colors.artikelBadgeBackground(null)
            )
        )
    }
    val groupLabel = stringResource(R.string.words_filter_artikel_a11y)

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.semantics { contentDescription = groupLabel }
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selectedKey == option.key,
                onClick = { onSelect(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = option.background,
                    activeContentColor = option.color,
                    activeBorderColor = option.color,
                    inactiveContainerColor = colors.textBackground,
                    inactiveContentColor = colors.primaryText,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.semantics {
                    contentDescription = "${option.a11y}, ${counts.forFilter(option.key.ifEmpty { null })}"
                }
            ) {
                // Label + count stacked: 5 equal segments still fit a 360dp phone.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${counts.forFilter(option.key.ifEmpty { null })}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckChipRow(
    store: DeckStore,
    countFor: (String?) -> Int,
    selectedDeck: String?,
    onSelectDeck: (String?) -> Unit,
    onNewDeck: () -> Unit,
    onRenameDeck: (String) -> Unit,
    onDeleteDeck: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            item(key = "__new_deck") {
                OutlinedButton(
                    onClick = onNewDeck,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    border = BorderStroke(1.dp, colors.accentBlue)
                ) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.words_new_deck), style = MaterialTheme.typography.labelMedium)
                }
            }
            item(key = "__all_decks") {
                DeckChip(
                    title = stringResource(R.string.words_all_decks),
                    count = countFor(null),
                    isSelected = selectedDeck == null,
                    onClick = { onSelectDeck(null) }
                )
            }
            items(store.allDeckNames, key = { it }) { deck ->
                DeckChip(
                    title = deck,
                    count = countFor(deck),
                    isSelected = selectedDeck == deck,
                    onClick = { if (selectedDeck != deck) onSelectDeck(deck) }
                )
            }
        }

        if (selectedDeck != null) {
            var showMenu by remember(selectedDeck) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.words_deck_options, selectedDeck),
                        tint = colors.primaryText
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DeckActionMenuItems(
                        deck = selectedDeck,
                        onRename = { showMenu = false; onRenameDeck(selectedDeck) },
                        onDelete = { showMenu = false; onDeleteDeck(selectedDeck) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckChip(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val a11y = stringResource(R.string.words_deck_chip_a11y, title, count)
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) colors.accentBlue else colors.primaryText,
                modifier = Modifier
                    .background(
                        if (isSelected) colors.blueBadgeBackground else colors.secondaryText.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .padding(horizontal = 6.dp)
            )
        },
        leadingIcon = {
            Icon(
                if (isSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.textBackground,
            labelColor = colors.primaryText,
            iconColor = colors.secondaryText,
            selectedContainerColor = colors.accentBlue,
            selectedLabelColor = colors.onBlue,
            selectedLeadingIconColor = colors.onBlue
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = colors.accentBlue
        ),
        modifier = Modifier.semantics { contentDescription = a11y }
    )
}

// =====================================================================================
// Quick add (inline on wide screens, dialog on phones)
// =====================================================================================

/** Segmented der/die/das picker for creating/editing a word. Room reserved for a 4th '-' option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtikelPicker(
    selected: Artikel?,
    onSelect: (Artikel?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    // null = article-less word ('-').
    val options: List<Artikel?> = Artikel.entries + listOf(null)
    val noArticleA11y = stringResource(R.string.words_filter_no_article_a11y)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, art ->
            val artColor = colors.artikelColor(art)
            SegmentedButton(
                selected = selected == art,
                onClick = { onSelect(art) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = artColor,
                    activeContentColor = colors.onArtikel(art),
                    activeBorderColor = artColor,
                    inactiveContainerColor = colors.textBackground,
                    inactiveContentColor = artColor,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = if (art == null) {
                    Modifier.semantics { contentDescription = noArticleA11y }
                } else {
                    Modifier
                }
            ) {
                Text(
                    text = art?.rawValue ?: ArtikelFilter.NONE,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeckSelector(
    store: DeckStore,
    deckName: String,
    onDeckChange: (String) -> Unit,
    onNewDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.words_deck_label)
    Box(modifier = modifier) {
        Surface(
            color = colors.textBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = { expanded = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "$label: $deckName" }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = deckName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.secondaryText
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            store.allDeckNames.forEach { deck ->
                val isSelected = deckName == deck
                DropdownMenuItem(
                    text = { Text(deck) },
                    leadingIcon = {
                        Icon(
                            if (isSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = null
                        )
                    },
                    trailingIcon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { onDeckChange(deck); expanded = false },
                    modifier = Modifier.semantics { selected = isSelected }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.words_new_deck_ellipsis)) },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                onClick = { expanded = false; onNewDeck() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddInlineBar(
    store: DeckStore,
    artikel: Artikel?,
    onArtikelChange: (Artikel?) -> Unit,
    word: String,
    onWordChange: (String) -> Unit,
    meaning: String,
    onMeaningChange: (String) -> Unit,
    deckName: String,
    onDeckChange: (String) -> Unit,
    onNewDeck: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = LocalAppColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ArtikelPicker(
            selected = artikel,
            onSelect = onArtikelChange,
            modifier = Modifier.width(210.dp)
        )
        CompactTextField(
            value = word,
            onValueChange = onWordChange,
            placeholder = stringResource(R.string.words_german_word_hint),
            imeAction = ImeAction.Next,
            modifier = Modifier
                .widthIn(min = 160.dp)
                .weight(1f)
        )
        CompactTextField(
            value = meaning,
            onValueChange = onMeaningChange,
            placeholder = stringResource(R.string.words_meaning_hint),
            imeAction = ImeAction.Done,
            onDone = onSubmit,
            modifier = Modifier
                .widthIn(min = 160.dp)
                .weight(1f)
        )
        DeckSelector(
            store = store,
            deckName = deckName,
            onDeckChange = onDeckChange,
            onNewDeck = onNewDeck,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        Button(
            onClick = onSubmit,
            enabled = word.trim().isNotEmpty() && (artikel != null || meaning.isNotBlank()),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentBlue,
                contentColor = colors.onBlue
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.words_add), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (artikel == null) {
        Text(
            text = stringResource(R.string.words_articleless_meaning_required),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
    }
}

@Composable
private fun AddWordDialog(
    store: DeckStore,
    artikel: Artikel?,
    onArtikelChange: (Artikel?) -> Unit,
    word: String,
    onWordChange: (String) -> Unit,
    meaning: String,
    onMeaningChange: (String) -> Unit,
    deckName: String,
    onDeckChange: (String) -> Unit,
    onNewDeck: () -> Unit,
    onSubmit: () -> Boolean?,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    var feedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val context = LocalContext.current

    fun submit() {
        val term = Flashcard(artikel = artikel, word = word.trim()).fullGermanTerm
        val added = onSubmit() ?: return
        val message = context.getString(
            if (added) R.string.words_added_feedback else R.string.words_duplicate_feedback,
            term
        )
        feedback = message to added
    }

    WordFormDialog(
        onDismiss = onDismiss,
        spacing = 12.dp,
        content = {
            DialogHeader(
                icon = Icons.Default.Add,
                title = stringResource(R.string.words_add_word),
                onClose = onDismiss
            )

            ArtikelPicker(
                selected = artikel,
                onSelect = onArtikelChange,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = word,
                onValueChange = { onWordChange(it); feedback = null },
                label = { Text(stringResource(R.string.words_german_word_label)) },
                placeholder = { Text(stringResource(R.string.words_german_word_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = meaning,
                onValueChange = onMeaningChange,
                label = { Text(stringResource(R.string.words_meaning_hint)) },
                supportingText = if (artikel == null) {
                    { Text(stringResource(R.string.words_articleless_meaning_required)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            DeckSelector(
                store = store,
                deckName = deckName,
                onDeckChange = onDeckChange,
                onNewDeck = onNewDeck,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            AnimatedVisibility(visible = feedback != null, enter = fadeIn(), exit = fadeOut()) {
                val (text, ok) = feedback ?: ("" to true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Icon(
                        imageVector = if (ok) Icons.Default.CheckCircle else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = if (ok) colors.success else colors.warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) colors.success else colors.warning
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { submit() },
                    enabled = word.trim().isNotEmpty() && (artikel != null || meaning.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.onBlue
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.words_add))
                }
            }
        }
    )
}

// =====================================================================================
// Word row
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WordRowItem(
    card: Flashcard,
    index: Int,
    dueText: String,
    onSpeak: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    // Opaque row background so the red swipe-to-delete layer never shows through.
    val bg = if (index % 2 == 0) {
        colors.windowBackground
    } else {
        colors.primaryText.copy(alpha = 0.035f).compositeOver(colors.windowBackground)
    }
    var showMenu by remember { mutableStateOf(false) }

    val speakLabel = stringResource(R.string.words_row_speak)
    val editLabel = stringResource(R.string.words_row_edit)
    val deleteLabel = stringResource(R.string.words_row_delete)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.error)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = deleteLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.onError)
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable(onClickLabel = editLabel, onClick = onEdit)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(speakLabel) { onSpeak(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true }
                    )
                }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
        ) {
            ArtikelBadge(card)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.meaning.isNotEmpty()) {
                    Text(
                        text = card.meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Metadata wraps instead of overflowing on narrow phones.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetaPill(text = card.deckName)
                    MetaPill(text = card.stage.title, stage = card.stage)
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            IconButton(onClick = onSpeak) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.words_row_speak_a11y, card.fullGermanTerm),
                    tint = colors.accentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.words_row_options_a11y, card.fullGermanTerm),
                        tint = colors.secondaryText
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(editLabel) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(deleteLabel, color = colors.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtikelBadge(card: Flashcard) {
    val colors = LocalAppColors.current
    val artColor = colors.artikelColor(card.artikel)
    val noArticleA11y = stringResource(R.string.words_filter_no_article_a11y)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(48.dp)
            .background(colors.artikelBadgeBackground(card.artikel), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = card.displayArtikel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = artColor,
            modifier = if (card.isArticleless) {
                Modifier.semantics { contentDescription = noArticleA11y }
            } else {
                Modifier
            }
        )
    }
}

@Composable
private fun MetaPill(text: String, stage: CardStage? = null) {
    val colors = LocalAppColors.current
    val background = stage?.let { colors.stageBadgeBackground(it) }
        ?: colors.secondaryText.copy(alpha = 0.13f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        if (stage == null) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Box(Modifier.size(7.dp).background(colors.stageColor(stage), CircleShape))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (stage == null) colors.secondaryText else colors.primaryText,
            maxLines = if (stage == null) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
}

private fun dueDescription(res: Resources, card: Flashcard, nowMillis: Long): String {
    if (card.stage == CardStage.NEW) return res.getString(R.string.words_due_new)
    if (card.dueDate <= nowMillis) return res.getString(R.string.words_due_now)
    val diffSec = (card.dueDate - nowMillis) / 1000.0
    return when {
        diffSec < 60 -> res.getString(R.string.words_due_seconds, max(1, ceil(diffSec).toInt()))
        diffSec < 3600 -> res.getString(R.string.words_due_minutes, max(1, (diffSec / 60.0).roundToInt()))
        diffSec < 86_400 -> res.getString(R.string.words_due_hours, max(1, (diffSec / 3600.0).roundToInt()))
        else -> res.getString(R.string.words_due_days, max(1, (diffSec / 86_400.0).roundToInt()))
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(12.dp)
    val outline = MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
        cursorBrush = SolidColor(colors.accentBlue),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { onDone?.invoke() ?: defaultKeyboardAction(ImeAction.Done) }
        ),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(colors.textBackground)
                    .border(1.dp, outline, shape)
                    .padding(start = 12.dp, end = if (trailingAction == null) 12.dp else 0.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                    innerTextField()
                }
                trailingAction?.invoke()
            }
        }
    )
}

@Composable
private fun DialogHeader(
    icon: ImageVector,
    title: String,
    onClose: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.accentBlue)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = stringResource(R.string.action_close),
                tint = colors.secondaryText
            )
        }
    }
}

@Composable
private fun WordFormDialog(
    onDismiss: () -> Unit,
    spacing: Dp,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(vertical = 8.dp)
        ) {
            Surface(
                color = colors.controlBackground,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 20.dp, bottom = spacing)
                    ) {
                        content()
                    }
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        actions()
                    }
                }
            }
        }
    }
}

// =====================================================================================
// New / rename deck dialogs (also used by the sidebar in SplashView.kt)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDeckDialog(
    store: DeckStore,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var deckName by remember { mutableStateOf("") }

    // Proper-noun deck name suggestions (data, intentionally not localized).
    val suggestedDecks = listOf(
        "A1 Kelimeler", "A2 Kelimeler", "B1 Kelimeler",
        "B2 Kelimeler", "C1 Kelimeler", "Günlük Konuşma",
        "İş Almancası", "Yiyecek & İçecek", "Seyahat"
    )

    WordFormDialog(
        onDismiss = onDismiss,
        spacing = 14.dp,
        content = {
            DialogHeader(
                icon = Icons.Default.CreateNewFolder,
                title = stringResource(R.string.deck_new_title),
                onClose = onDismiss
            )

            Text(
                text = stringResource(R.string.deck_new_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText
            )

            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                label = { Text(stringResource(R.string.words_deck_label)) },
                placeholder = { Text(stringResource(R.string.deck_new_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.deck_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryText
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    suggestedDecks.forEach { suggestion ->
                        FilterChip(
                            selected = deckName == suggestion,
                            onClick = { deckName = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.textBackground,
                                labelColor = colors.primaryText,
                                selectedContainerColor = colors.blueBadgeBackground,
                                selectedLabelColor = colors.accentBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = deckName == suggestion,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = colors.accentBlue
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.cardBorderSubtle)
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val created = store.createDeck(deckName)
                        if (created != null) onCreated(created)
                    },
                    enabled = deckName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.onBlue
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.deck_create))
                }
            }
        }
    )
}

@Composable
fun RenameDeckDialog(
    store: DeckStore,
    oldName: String,
    onDismiss: () -> Unit,
    onRenamed: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var newName by remember(oldName) { mutableStateOf(oldName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                DialogHeader(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.deck_rename_title, oldName),
                    onClose = onDismiss
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.deck_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleaned = newName.trim()
                            if (cleaned.isNotEmpty()) {
                                store.renameDeck(oldName = oldName, newName = cleaned)
                                onRenamed(cleaned)
                            }
                        },
                        enabled = newName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.onBlue
                        )
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

// =====================================================================================
// Edit word dialog
// =====================================================================================

@Composable
fun EditWordDialog(
    store: DeckStore,
    card: Flashcard,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    var editArtikel by remember(card.id) { mutableStateOf(card.artikel) }
    var editWord by remember(card.id) { mutableStateOf(card.word) }
    var editMeaning by remember(card.id) { mutableStateOf(card.meaning) }
    var editDeckName by remember(card.id) { mutableStateOf(card.deckName) }
    var showNewDeckSheet by remember { mutableStateOf(false) }

    WordFormDialog(
        onDismiss = onDismiss,
        spacing = 12.dp,
        content = {
            DialogHeader(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.edit_word_title),
                onClose = onDismiss
            )

            Text(
                text = stringResource(R.string.words_artikel_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText
            )
            ArtikelPicker(
                selected = editArtikel,
                onSelect = { editArtikel = it },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editWord,
                onValueChange = { editWord = it },
                label = { Text(stringResource(R.string.words_german_word_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editMeaning,
                onValueChange = { editMeaning = it },
                label = { Text(stringResource(R.string.edit_word_meaning_label)) },
                supportingText = if (editArtikel == null) {
                    { Text(stringResource(R.string.words_articleless_meaning_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.edit_word_deck_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText
            )
            DeckSelector(
                store = store,
                deckName = editDeckName,
                onDeckChange = { editDeckName = it },
                onNewDeck = { showNewDeckSheet = true },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = colors.cardBorderSubtle)
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.edit_word_delete),
                            tint = colors.error
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        store.createDeck(editDeckName)
                        store.updateCard(
                            id = card.id,
                            artikel = editArtikel,
                            word = editWord,
                            meaning = editMeaning,
                            deckName = editDeckName
                        )
                        onDismiss()
                    },
                    enabled = editWord.trim().isNotEmpty() &&
                        (editArtikel != null || editMeaning.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.onBlue
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_word_save))
                }
            }
        }
    )

    if (showNewDeckSheet) {
        NewDeckDialog(
            store = store,
            onDismiss = { showNewDeckSheet = false },
            onCreated = { created ->
                editDeckName = created
                showNewDeckSheet = false
            }
        )
    }
}

// =====================================================================================
// Previews
// =====================================================================================

@Preview(name = "Kelime satırı — koyu", showBackground = true, widthDp = 360)
@Composable
private fun WordRowPreviewDark() {
    ArtikelMeisterTheme(darkTheme = true) {
        Column(modifier = Modifier.background(LocalAppColors.current.windowBackground)) {
            listOf(
                Flashcard(artikel = Artikel.DER, word = "Tisch", meaning = "Masa", deckName = "A1"),
                Flashcard(artikel = Artikel.DIE, word = "Lampe", meaning = "Lamba", deckName = "A1"),
                Flashcard(artikel = Artikel.DAS, word = "Buch", meaning = "Kitap", deckName = "A1")
            ).forEachIndexed { i, card ->
                WordRowItem(card, i, "Şimdi", onSpeak = {}, onEdit = {}, onDelete = {})
            }
        }
    }
}

@Preview(name = "Kelime satırı — açık", showBackground = true, widthDp = 360)
@Composable
private fun WordRowPreviewLight() {
    ArtikelMeisterTheme(darkTheme = false) {
        Column(modifier = Modifier.background(LocalAppColors.current.windowBackground)) {
            WordRowItem(
                Flashcard(artikel = Artikel.DIE, word = "Geschwindigkeitsbegrenzung", meaning = "Hız sınırı", deckName = "B2 Kelimeler"),
                0, "3 gün sonra", onSpeak = {}, onEdit = {}, onDelete = {}
            )
        }
    }
}
