package com.artikelmeister.android.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.ArtikelFilter
import com.artikelmeister.android.CEFRLevel
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.GeneratedWordItem
import com.artikelmeister.android.MistralVocabularyGenerator
import com.artikelmeister.android.R
import com.artikelmeister.android.SyncErrors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AIGeneratorDialog(
    store: DeckStore,
    @Suppress("UNUSED_PARAMETER") authManager: GeminiAuthManager,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLevel by rememberSaveable { mutableStateOf(CEFRLevel.A1) }
    var wordCount by rememberSaveable { mutableStateOf(20) }
    var topicHint by rememberSaveable { mutableStateOf("") }
    var includeArticleless by rememberSaveable { mutableStateOf(false) }
    var targetDeckName by rememberSaveable { mutableStateOf(context.getString(R.string.ai_auto_deck_name, CEFRLevel.A1.rawValue)) }
    var hasCustomDeckName by rememberSaveable { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generationCompleted by remember { mutableStateOf(false) }
    var newlyAddedWords by remember { mutableStateOf<List<GeneratedWordItem>>(emptyList()) }
    var outcomeScrollOffset by remember { mutableStateOf<Int?>(null) }

    var showTargetDeckMenu by remember { mutableStateOf(false) }
    val targetDeckSummary = targetDeckName.trim().ifEmpty { stringResource(R.string.ai_auto_deck_name, selectedLevel.rawValue) }

    fun generateAndAddWords() {
        if (isGenerating) return
        isGenerating = true
        showTargetDeckMenu = false
        scope.launch {
            generationError = null
            generationCompleted = false
            newlyAddedWords = emptyList()
            outcomeScrollOffset = null
            progressMessage = context.getString(R.string.ai_connecting)

            try {
                val generated = MistralVocabularyGenerator.generateWords(
                    level = selectedLevel,
                    targetCount = wordCount,
                    topicHint = topicHint,
                    includeArticleless = includeArticleless,
                    existingCards = store.cards,
                    onProgress = { done, total ->
                        progressMessage = context.getString(R.string.ai_generating_progress, done, total)
                    }
                )

                val resolvedDeck = targetDeckName.trim().ifEmpty { context.getString(R.string.ai_auto_deck_name, selectedLevel.rawValue) }
                store.createDeck(resolvedDeck)

                val addedItems = mutableListOf<GeneratedWordItem>()
                for (item in generated) {
                    val art = Artikel.parse(item.artikel)
                    if (art != null) {
                        if (store.addCard(art, item.word, item.meaning, resolvedDeck)) {
                            addedItems.add(item)
                        }
                    } else if (includeArticleless && item.meaning.isNotBlank()) {
                        // "-" = article-less (verb/adjective/phrase). Its Turkish meaning is the
                        // answer typed during study, so the generator only returns non-blank ones.
                        if (store.addCard(null, item.word, item.meaning, resolvedDeck)) {
                            addedItems.add(item)
                        }
                    }
                }
                newlyAddedWords = addedItems
                generationCompleted = true
            } catch (e: Exception) {
                // Dismissing the sheet cancels generation; that is never an error to show.
                val message = SyncErrors.userMessage(e, context.getString(R.string.ai_gen_error_generic))
                if (message == null) throw e
                generationError = message
            } finally {
                isGenerating = false
                progressMessage = ""
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isGenerating) {
                Toast.makeText(context, context.getString(R.string.ai_gen_cannot_close_toast), Toast.LENGTH_SHORT).show()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        val focusManager = LocalFocusManager.current
        val isDark = colors.isDark
        val barColorArgb = colors.controlBackground.toArgb()
        if (!view.isInEditMode) {
            SideEffect {
                val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
                @Suppress("DEPRECATION")
                dialogWindow.statusBarColor = barColorArgb
                @Suppress("DEPRECATION")
                dialogWindow.navigationBarColor = barColorArgb
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dialogWindow.isNavigationBarContrastEnforced = false
                    dialogWindow.isStatusBarContrastEnforced = false
                }
                val ctrl = WindowCompat.getInsetsController(dialogWindow, view)
                ctrl.isAppearanceLightStatusBars = !isDark
                ctrl.isAppearanceLightNavigationBars = !isDark
            }
        }

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            val sheetHeight = maxHeight.coerceAtMost(720.dp)
            val compactHeight = sheetHeight < 300.dp
            val scrollState = rememberScrollState()

            LaunchedEffect(outcomeScrollOffset) {
                outcomeScrollOffset?.let { scrollState.animateScrollTo(it.coerceIn(0, scrollState.maxValue)) }
            }

            Surface(
                color = colors.windowBackground,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .heightIn(max = sheetHeight)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (sheetHeight >= 150.dp) {
                        // Sheet Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.controlBackground)
                                .padding(horizontal = 16.dp, vertical = if (compactHeight) 4.dp else 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colors.purpleBadgeBackground, RoundedCornerShape(10.dp))
                                    .border(1.dp, colors.accentPurple.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accentPurple,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.ai_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { if (!isGenerating) onDismiss() },
                                enabled = !isGenerating
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close)
                                )
                            }
                        }

                        HorizontalDivider(color = colors.cardBorderSubtle)
                    }

                    // Scrollable Body
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(if (compactHeight) 10.dp else 18.dp)
                    ) {
                        // 1. Level (A1-C1) & Count (1-100) Configuration Box
                        GroupBoxCard(
                            title = stringResource(R.string.ai_section_level_count),
                            icon = Icons.Default.School,
                            iconTint = colors.accentPurple
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = stringResource(R.string.ai_cefr_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().selectableGroup()
                                ) {
                                    CEFRLevel.entries.forEach { lvl ->
                                        val isSelected = selectedLevel == lvl
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) colors.purpleBadgeBackground
                                                    else colors.textBackground
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) colors.accentPurple.copy(alpha = 0.7f) else colors.cardBorderSubtle,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .selectable(
                                                    selected = isSelected,
                                                    enabled = !isGenerating,
                                                    role = Role.RadioButton
                                                ) {
                                                    selectedLevel = lvl
                                                    if (!hasCustomDeckName) {
                                                        targetDeckName = context.getString(R.string.ai_auto_deck_name, lvl.rawValue)
                                                    }
                                                }
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Text(
                                                text = lvl.rawValue,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) colors.accentPurple else colors.primaryText,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(selectedLevel.descriptionRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )

                                HorizontalDivider(color = colors.cardBorderSubtle)

                                Text(
                                    text = stringResource(R.string.ai_word_count_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        onClick = { wordCount = (wordCount - 1).coerceAtLeast(1) },
                                        enabled = !isGenerating && wordCount > 1,
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.textBackground,
                                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Remove,
                                                contentDescription = stringResource(R.string.ai_decrease_count_a11y),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.ai_preset_words, wordCount),
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentPurple,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Surface(
                                        onClick = { wordCount = (wordCount + 1).coerceAtMost(100) },
                                        enabled = !isGenerating && wordCount < 100,
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.textBackground,
                                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = stringResource(R.string.ai_increase_count_a11y),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                val wordCountA11y = stringResource(R.string.ai_word_count_a11y)
                                val wordCountState = stringResource(R.string.ai_preset_words, wordCount)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("1", style = MaterialTheme.typography.labelSmall.tabular(), color = colors.secondaryText)
                                    Slider(
                                        value = wordCount.toFloat(),
                                        onValueChange = { wordCount = it.roundToInt().coerceIn(1, 100) },
                                        enabled = !isGenerating,
                                        valueRange = 1f..100f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accentPurple,
                                            activeTrackColor = colors.accentPurple
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics {
                                                contentDescription = wordCountA11y
                                                stateDescription = wordCountState
                                            }
                                    )
                                    Text("100", style = MaterialTheme.typography.labelSmall.tabular(), color = colors.secondaryText)
                                }

                                // Quick count preset chips
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_quick_select),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    listOf(5, 10, 20, 30, 50, 75, 100).forEach { preset ->
                                        FilterChip(
                                            selected = wordCount == preset,
                                            enabled = !isGenerating,
                                            onClick = { wordCount = preset },
                                            label = { Text(stringResource(R.string.ai_preset_words, preset), style = MaterialTheme.typography.labelMedium.tabular()) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = colors.textBackground,
                                                labelColor = colors.primaryText,
                                                selectedContainerColor = colors.purpleBadgeBackground,
                                                selectedLabelColor = colors.accentPurple
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = !isGenerating,
                                                selected = wordCount == preset,
                                                borderColor = colors.cardBorderSubtle,
                                                selectedBorderColor = colors.accentPurple.copy(alpha = 0.55f)
                                            )
                                        )
                                    }
                                }

                                // Article-less words (verbs, adjectives, phrases) are optional because
                                // they change the study interaction: the answer is the typed meaning.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.textBackground)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ai_include_articleless_title),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primaryText
                                        )
                                        Text(
                                            text = stringResource(R.string.ai_include_articleless_desc),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.secondaryText
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val includeArticlelessA11y = stringResource(R.string.ai_include_articleless_title)
                                    Switch(
                                        checked = includeArticleless,
                                        onCheckedChange = { includeArticleless = it },
                                        enabled = !isGenerating,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.onAccent,
                                            checkedTrackColor = colors.accentPurple,
                                            uncheckedThumbColor = colors.secondaryText,
                                            uncheckedTrackColor = colors.secondaryText.copy(alpha = 0.25f),
                                            uncheckedBorderColor = colors.cardBorderSubtle
                                        ),
                                        modifier = Modifier.semantics {
                                            contentDescription = includeArticlelessA11y
                                        }
                                    )
                                }

                                HorizontalDivider(color = colors.cardBorderSubtle)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = topicHint,
                                        onValueChange = { topicHint = it },
                                        enabled = !isGenerating,
                                        label = { Text(stringResource(R.string.ai_topic_label)) },
                                        placeholder = { Text(stringResource(R.string.ai_topic_placeholder)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = targetDeckName,
                                        onValueChange = {
                                            targetDeckName = it
                                            hasCustomDeckName = true
                                        },
                                        enabled = !isGenerating,
                                        label = { Text(stringResource(R.string.ai_target_deck_title)) },
                                        placeholder = { Text(stringResource(R.string.ai_target_deck_placeholder)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Box {
                                        OutlinedButton(
                                            onClick = { showTargetDeckMenu = true },
                                            enabled = !isGenerating,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, colors.cardBorderSubtle),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            val usesExistingDeck = hasCustomDeckName && targetDeckSummary in store.allDeckNames
                                            Icon(
                                                if (usesExistingDeck) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.ai_existing_decks), style = MaterialTheme.typography.labelMedium)
                                        }
                                        DropdownMenu(
                                            expanded = showTargetDeckMenu && !isGenerating,
                                            onDismissRequest = { showTargetDeckMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.ai_auto_deck_chip, selectedLevel.rawValue)) },
                                                leadingIcon = {
                                                    Icon(
                                                        if (!hasCustomDeckName) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                        contentDescription = null
                                                    )
                                                },
                                                enabled = !isGenerating,
                                                modifier = Modifier.semantics { selected = !hasCustomDeckName },
                                                onClick = {
                                                    targetDeckName = context.getString(R.string.ai_auto_deck_name, selectedLevel.rawValue)
                                                    hasCustomDeckName = false
                                                    showTargetDeckMenu = false
                                                }
                                            )
                                            HorizontalDivider()
                                            store.allDeckNames.forEach { deck ->
                                                val isSelected = hasCustomDeckName && targetDeckSummary == deck
                                                DropdownMenuItem(
                                                    text = { Text(deck) },
                                                    leadingIcon = {
                                                        Icon(
                                                            if (isSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    enabled = !isGenerating,
                                                    modifier = Modifier.semantics { selected = isSelected },
                                                    onClick = {
                                                        targetDeckName = deck
                                                        hasCustomDeckName = true
                                                        showTargetDeckMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        generationError?.let { message ->
                            AIGeneratorErrorMessage(
                                message = message,
                                modifier = Modifier.onPlaced {
                                    if (outcomeScrollOffset == null) {
                                        outcomeScrollOffset = it.positionInParent().y.roundToInt()
                                    }
                                },
                                announce = false
                            )
                        }

                        if (newlyAddedWords.isNotEmpty()) {
                            GroupBoxCard(
                                title = stringResource(R.string.ai_added_list_title, newlyAddedWords.size),
                                icon = Icons.Default.CheckCircle,
                                iconTint = colors.feedbackCorrect,
                                modifier = Modifier.onPlaced {
                                    if (outcomeScrollOffset == null) {
                                        outcomeScrollOffset = it.positionInParent().y.roundToInt()
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    newlyAddedWords.forEach { item ->
                                        val art = Artikel.parse(item.artikel)
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics(mergeDescendants = true) {}
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .widthIn(min = 44.dp)
                                                    .background(colors.artikelBadgeBackground(art), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = art?.rawValue ?: ArtikelFilter.NONE,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.artikelColor(art)
                                                )
                                            }
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = item.word,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.primaryText
                                                )
                                                Text(
                                                    text = item.meaning,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.secondaryText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.cardBorderSubtle)

                    // Bottom Action Bar
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 2.dp else 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = sheetHeight * 0.55f)
                            .background(colors.controlBackground)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = if (compactHeight) 2.dp else 8.dp)
                    ) {
                        val statusMessage = when {
                            isGenerating -> progressMessage
                            generationError != null -> stringResource(R.string.ai_gen_error_generic)
                            generationCompleted && newlyAddedWords.isEmpty() -> stringResource(R.string.ai_result_none)
                            generationCompleted -> stringResource(R.string.ai_result_added, newlyAddedWords.size)
                            else -> ""
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                            }
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.accentPurple,
                                    strokeWidth = 2.dp
                                )
                            }
                            if (statusMessage.isNotEmpty()) {
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        generationError != null -> MaterialTheme.colorScheme.onSurface
                                        newlyAddedWords.isNotEmpty() -> colors.feedbackCorrect
                                        else -> colors.secondaryText
                                    }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    generateAndAddWords()
                                },
                                enabled = !isGenerating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentPurple,
                                    contentColor = colors.onPurple
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.ai_generate_and_add),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "${selectedLevel.rawValue} • ${stringResource(R.string.ai_preset_words, wordCount)} • $targetDeckSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AIGeneratorErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    announce: Boolean = true
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.errorBadgeBackground, RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                error(message)
                if (announce) liveRegion = LiveRegionMode.Polite
            }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = colors.feedbackIncorrect,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
