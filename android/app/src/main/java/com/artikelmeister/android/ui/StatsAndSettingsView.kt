package com.artikelmeister.android.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.DailyProgress
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.HeatmapDayItem
import com.artikelmeister.android.R
import com.artikelmeister.android.ReviewRating
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsView(
    store: DeckStore,
    onOpenDrawer: (() -> Unit)? = null,
    onSelectTab: ((AppTab) -> Unit)? = null
) {
    val colors = LocalAppColors.current

    val totalCards = store.cards.size
    val newCount = store.cards.count { it.stage == CardStage.NEW }
    val learningCount = store.cards.count { it.stage == CardStage.LEARNING || it.stage == CardStage.RELEARNING }
    val reviewCount = store.cards.count { it.stage == CardStage.REVIEW && it.intervalDays < 21.0 }
    val matureCount = store.cards.count { it.stage == CardStage.REVIEW && it.intervalDays >= 21.0 }

    val derCount = store.cards.count { it.artikel == Artikel.DER }
    val dieCount = store.cards.count { it.artikel == Artikel.DIE }
    val dasCount = store.cards.count { it.artikel == Artikel.DAS }

    val articlelessCount = totalCards - derCount - dieCount - dasCount
    val today = store.dailyProgress
    val todayIsInHistory = (store.studyHistory[today.dateString]?.totalAnswersCount ?: 0) > 0
    val activeStudyDaysCount = store.studyHistory.values.count { it.totalAnswersCount > 0 } +
        (if (!todayIsInHistory && today.totalAnswersCount > 0) 1 else 0)

    StatsSettingsScreen(title = stringResource(R.string.stats_title), onOpenDrawer = onOpenDrawer) {
        if (activeStudyDaysCount == 0) {
            StateMessageView(
                icon = Icons.Default.CalendarMonth,
                title = stringResource(R.string.stats_no_records_title),
                message = stringResource(
                    if (onSelectTab != null) R.string.stats_no_records_msg_tab
                    else R.string.stats_no_records_msg_switch
                ),
                modifier = Modifier.fillMaxWidth(),
                primaryActionLabel = stringResource(R.string.tab_study),
                onPrimaryAction = onSelectTab?.let { select -> { select(AppTab.STUDY) } }
            )
        } else {
            // Today's Activity
            GroupBoxCard(
                title = stringResource(R.string.stats_today_activity, store.dailyProgress.dateString),
                icon = Icons.Default.CalendarMonth,
                iconTint = colors.accentBlue
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.stats_new_learned_prefix))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${store.dailyProgress.newCardsStudied}")
                                }
                                append(" / ${store.settings.dailyNewCardLimit}")
                            },
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = colors.primaryText
                        )
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.stats_reviews_done_prefix))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${store.dailyProgress.reviewsCompleted}")
                                }
                                append(" / ${store.settings.dailyReviewLimit}")
                            },
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = colors.primaryText
                        )
                    }

                    FeedbackBadge(
                        title = stringResource(ReviewRating.AGAIN.titleRes()),
                        count = store.dailyProgress.againCount,
                        rating = ReviewRating.AGAIN
                    )
                    FeedbackBadge(
                        title = stringResource(ReviewRating.HARD.titleRes()),
                        count = store.dailyProgress.hardCount,
                        rating = ReviewRating.HARD
                    )
                    FeedbackBadge(
                        title = stringResource(ReviewRating.GOOD.titleRes()),
                        count = store.dailyProgress.goodCount,
                        rating = ReviewRating.GOOD
                    )
                    FeedbackBadge(
                        title = stringResource(ReviewRating.EASY.titleRes()),
                        count = store.dailyProgress.easyCount,
                        rating = ReviewRating.EASY
                    )
                }
            }

            // Streak & 35-Day Activity Heatmap
            GroupBoxCard(
                title = stringResource(R.string.stats_heatmap_title),
                icon = Icons.Default.LocalFireDepartment,
                iconTint = colors.accentOrange
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (store.currentStreakDays > 0) colors.accentOrange else colors.secondaryText,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.stats_days_value, store.currentStreakDays),
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.stats_current_streak_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = colors.accentOrange,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.stats_days_value, store.longestStreakDays),
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.stats_longest_streak),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = colors.vividGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.stats_days_value, activeStudyDaysCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.stats_active_days_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.cardBorderSubtle)

                    ActivityHeatmap(store.heatmapDays(35))
                }
            }
        }

        if (totalCards == 0) {
            StateMessageView(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.stats_empty_title),
                message = stringResource(
                    if (onSelectTab != null) R.string.stats_empty_msg_wide else R.string.stats_empty_msg_compact
                ),
                modifier = Modifier.fillMaxWidth(),
                primaryActionLabel = stringResource(R.string.tab_words),
                onPrimaryAction = onSelectTab?.let { select -> { select(AppTab.WORDS) } }
            )
        } else {
            // Card stages
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title = stringResource(R.string.stats_stage_new_title),
                    value = "$newCount",
                    subtitle = stringResource(R.string.stats_stage_new_sub),
                    color = colors.accentBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stats_stage_learning_title),
                    value = "$learningCount",
                    subtitle = stringResource(R.string.stats_stage_learning_sub),
                    color = colors.accentOrange,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title = stringResource(R.string.stats_stage_young_title),
                    value = "$reviewCount",
                    subtitle = stringResource(R.string.stats_stage_young_sub),
                    color = colors.vividGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.stats_stage_mature),
                    value = "$matureCount",
                    subtitle = stringResource(R.string.stats_stage_mature_sub),
                    color = colors.accentPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            // Artikel Distribution
            GroupBoxCard(
                title = stringResource(R.string.stats_artikel_dist_title, totalCards),
                icon = Icons.Default.BarChart,
                iconTint = colors.accentBlue
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DistributionRow(
                        label = stringResource(R.string.stats_artikel_der_label),
                        count = derCount,
                        total = totalCards,
                        color = Artikel.DER.themeColor()
                    )
                    DistributionRow(
                        label = stringResource(R.string.stats_artikel_die_label),
                        count = dieCount,
                        total = totalCards,
                        color = Artikel.DIE.themeColor()
                    )
                    DistributionRow(
                        label = stringResource(R.string.stats_artikel_das_label),
                        count = dasCount,
                        total = totalCards,
                        color = Artikel.DAS.themeColor()
                    )
                    if (articlelessCount > 0) {
                        DistributionRow(
                            label = stringResource(R.string.stats_artikel_none),
                            count = articlelessCount,
                            total = totalCards,
                            color = colors.artikelNone
                        )
                    }
                }
            }

            // Deck Distribution (#5)
            GroupBoxCard(
                title = stringResource(R.string.stats_deck_dist_title, store.allDeckNames.size),
                icon = Icons.Default.Folder,
                iconTint = colors.accentBlue
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    store.allDeckNames.forEach { deck ->
                        val count = store.cards.count { it.deckName == deck }
                        DistributionRow(
                            label = deck,
                            count = count,
                            total = totalCards,
                            color = colors.accentBlue
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityHeatmap(days: List<HeatmapDayItem>) {
    val colors = LocalAppColors.current
    val ranges = listOf(0 to "0", 1 to "1–4", 5 to "5–14", 15 to "15–34", 35 to "35+")
    val locale = Locale.getDefault()
    val fullDateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMMM uuuu", locale) }
    val cellMinWidth = with(LocalDensity.current) { 36.sp.toDp() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.stats_heatmap_heading),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondaryText,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ranges.forEach { (count, label) ->
                val palette = colors.heatmapColors(count)
                val shape = RoundedCornerShape(6.dp)
                Text(
                    text = stringResource(R.string.stats_heatmap_answers, label),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = palette.foreground,
                    modifier = Modifier
                        .background(palette.background, shape)
                        .border(0.75.dp, colors.cardBorderSubtle, shape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth, cellMinWidth * 7 + 36.dp)
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.width(gridWidth)
                ) {
                    days.chunked(7).forEach { week ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { day ->
                                val date = LocalDate.parse(day.dateString)
                                HeatmapCell(
                                    label = "${date.dayOfMonth}/${date.monthValue}",
                                    fullDate = date.format(fullDateFormat),
                                    count = day.count,
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

@Composable
private fun HeatmapCell(label: String, fullDate: String, count: Int, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val palette = colors.heatmapColors(count)
    val shape = RoundedCornerShape(8.dp)
    val cellA11y = stringResource(R.string.stats_heatmap_cell_a11y, fullDate, count)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = cellA11y }
            .background(palette.background, shape)
            .border(0.75.dp, colors.cardBorderSubtle, shape)
            .padding(horizontal = 3.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.Medium,
            color = palette.foreground
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = palette.foreground
        )
    }
}

// MARK: - SettingsView (1:1 Port of SettingsView in StatsAndSettingsView.swift)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsView(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onOpenDrawer: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    var showResetConfirmation by remember { mutableStateOf(false) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var mergeOnBackupImport by rememberSaveable { mutableStateOf(true) }
    var pendingImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImportName by rememberSaveable { mutableStateOf("") }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusDetail by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }
    var pendingExportIncludeProgress by rememberSaveable { mutableStateOf(true) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonStr = store.exportBackupJSONString(includeProgress = pendingExportIncludeProgress)
                val output = requireNotNull(context.contentResolver.openOutputStream(uri))
                output.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                backupStatusIsError = false
                backupStatusDetail = null
                backupStatusMessage = context.getString(
                    if (pendingExportIncludeProgress) R.string.settings_backup_exported_full
                    else R.string.settings_backup_exported_deck
                )
            }.onFailure { error ->
                backupStatusIsError = true
                backupStatusDetail = error.localizedMessage
                backupStatusMessage = context.getString(R.string.settings_backup_save_error)
            }
        }
    }

    fun importBackup(uri: Uri, merge: Boolean) {
        runCatching {
            val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: throw IllegalArgumentException(context.getString(R.string.settings_file_read_error))

            val (added, updated) = store.importBackupJSONString(jsonStr, mergeWithExisting = merge)
            backupStatusIsError = false
            backupStatusDetail = null
            backupStatusMessage = if (merge) {
                context.getString(R.string.settings_backup_merged, added, updated)
            } else {
                context.getString(R.string.settings_backup_restored, added)
            }
        }.onFailure { error ->
            backupStatusIsError = true
            backupStatusDetail = error.localizedMessage
            backupStatusMessage = context.getString(R.string.settings_backup_import_error)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (mergeOnBackupImport) {
                importBackup(uri, merge = true)
            } else {
                pendingImportName = runCatching {
                    val columns = arrayOf(OpenableColumns.DISPLAY_NAME)
                    context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') }.orEmpty()
                pendingImportUri = uri
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Runs in the store's own scope: sign-in changes screens and a composition scope would cancel the sync.
        store.completeGoogleSignIn(result.data, authManager)
    }

    StatsSettingsScreen(title = stringResource(R.string.settings_title), onOpenDrawer = onOpenDrawer) {
        // Appearance: System / Light / Dark + Language
        GroupBoxCard(
            title = stringResource(R.string.theme_section_title),
            icon = Icons.Default.Palette,
            iconTint = colors.accentPurple
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeModeSelector(store = store)
                HorizontalDivider(color = colors.cardBorderSubtle)
                LanguageSelector()
            }
        }

        // Daily Limits
        GroupBoxCard(
            title = stringResource(R.string.settings_daily_limits_title),
            icon = Icons.Default.Tune,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val newLimitA11y = stringResource(R.string.settings_daily_new_a11y)
                val newLimitValue = stringResource(R.string.settings_daily_new_value, store.settings.dailyNewCardLimit)
                DailyLimitControl(
                    label = stringResource(R.string.settings_daily_new_label),
                    valueText = newLimitValue,
                    value = store.settings.dailyNewCardLimit.coerceIn(1, 500).toFloat(),
                    valueRange = 1f..500f,
                    sliderA11y = newLimitA11y,
                    decA11y = stringResource(R.string.settings_daily_new_dec_a11y),
                    incA11y = stringResource(R.string.settings_daily_new_inc_a11y),
                    onDecrease = {
                        store.updateSettings {
                            it.copy(dailyNewCardLimit = (it.dailyNewCardLimit - 5).coerceIn(1, 500))
                        }
                    },
                    onIncrease = {
                        store.updateSettings {
                            it.copy(dailyNewCardLimit = (it.dailyNewCardLimit + 5).coerceIn(1, 500))
                        }
                    },
                    onSlide = { v ->
                        store.updateSettings { it.copy(dailyNewCardLimit = v.roundToInt().coerceIn(1, 500)) }
                    }
                )

                HorizontalDivider(color = colors.cardBorderSubtle)

                val reviewLimitA11y = stringResource(R.string.settings_daily_review_a11y)
                val reviewLimitValue = stringResource(R.string.settings_daily_review_value, store.settings.dailyReviewLimit)
                DailyLimitControl(
                    label = stringResource(R.string.settings_daily_review_label),
                    valueText = reviewLimitValue,
                    value = store.settings.dailyReviewLimit.coerceIn(10, 1000).toFloat(),
                    valueRange = 10f..1000f,
                    sliderA11y = reviewLimitA11y,
                    decA11y = stringResource(R.string.settings_daily_review_dec_a11y),
                    incA11y = stringResource(R.string.settings_daily_review_inc_a11y),
                    onDecrease = {
                        store.updateSettings {
                            it.copy(dailyReviewLimit = (it.dailyReviewLimit - 10).coerceIn(10, 1000))
                        }
                    },
                    onIncrease = {
                        store.updateSettings {
                            it.copy(dailyReviewLimit = (it.dailyReviewLimit + 10).coerceIn(10, 1000))
                        }
                    },
                    onSlide = { v ->
                        val stepped = ((v / 10f).roundToInt() * 10).coerceIn(10, 1000)
                        store.updateSettings { it.copy(dailyReviewLimit = stepped) }
                    }
                )
            }
        }

        // Card Presentation Preferences
        GroupBoxCard(
            title = stringResource(R.string.settings_card_prefs),
            icon = Icons.Default.Style,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    label = stringResource(R.string.settings_auto_pronounce),
                    checked = store.settings.autoPlayPronunciation,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(autoPlayPronunciation = v) }
                    }
                )
                HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.5f))
                SettingToggleRow(
                    label = stringResource(R.string.settings_show_meaning_front),
                    checked = store.settings.showMeaningOnFront,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(showMeaningOnFront = v) }
                    }
                )
                HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.5f))
                SettingToggleRow(
                    label = stringResource(R.string.settings_random_new),
                    checked = store.settings.newCardOrderRandom,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(newCardOrderRandom = v) }
                    }
                )
            }
        }

        // Universal Google Profile & Cloud Synchronization
        GroupBoxCard(
            title = stringResource(R.string.settings_drive_sync_title),
            icon = Icons.Default.AccountCircle,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (authManager.isGoogleAccountConnected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.successBadgeBackground, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.success.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = colors.vividGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authManager.userName.ifBlank { stringResource(R.string.settings_drive_connected_fallback) },
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                            Text(
                                text = authManager.userEmail.ifBlank { stringResource(R.string.settings_drive_profile_active) },
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = colors.secondaryText
                            )
                            Text(
                                text = stringResource(R.string.settings_drive_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { store.requestCloudSync(authManager) },
                            enabled = !store.isSyncingCloud && !isDeletingAccount,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentBlue,
                                contentColor = colors.onBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            if (store.isSyncingCloud) {
                                CircularProgressIndicator(
                                    color = LocalContentColor.current,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (store.isSyncingCloud) R.string.settings_drive_syncing_btn
                                    else R.string.settings_drive_sync_now_btn
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                authManager.signOut(context) {
                                    store.cloudSyncStatusMessage = context.getString(R.string.settings_drive_signed_out_msg)
                                    store.cloudSyncIsError = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.cardBorderSubtle),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_drive_sign_out_btn), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            googleSignInLauncher.launch(authManager.getGoogleSignInIntent(context))
                        },
                        enabled = !authManager.isAuthenticating && !store.isSyncingCloud,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.onBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        if (authManager.isAuthenticating || store.isSyncingCloud) {
                            CircularProgressIndicator(
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                authManager.isAuthenticating -> stringResource(R.string.settings_drive_connecting)
                                store.isSyncingCloud -> stringResource(R.string.settings_drive_syncing_status)
                                else -> stringResource(R.string.settings_drive_signin_sync_btn)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                val cloudIsBusy = isDeletingAccount || authManager.isAuthenticating || store.isSyncingCloud
                val cloudIsError = store.cloudSyncIsError && !cloudIsBusy
                val cloudStatus = store.cloudSyncStatusMessage
                val cloudMessage = when {
                    isDeletingAccount -> stringResource(R.string.settings_deleting_account)
                    authManager.isAuthenticating -> stringResource(R.string.settings_drive_connecting)
                    store.isSyncingCloud -> stringResource(R.string.settings_drive_syncing_status)
                    cloudIsError -> stringResource(R.string.settings_drive_error_generic)
                    else -> cloudStatus
                }
                SettingsStatusMessage(
                    message = cloudMessage,
                    isError = cloudIsError,
                    isBusy = cloudIsBusy,
                    detail = cloudStatus.takeIf { cloudIsError }
                )
            }
        }

        // Backup & Restore / Deck Sharing
        GroupBoxCard(
            title = stringResource(R.string.settings_backup_section_title),
            icon = Icons.Default.Archive,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.settings_backup_package_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            pendingExportIncludeProgress = true
                            val todayStr = DailyProgress.todayString()
                            exportBackupLauncher.launch("ArtikelMeister_TamYedek_$todayStr.json")
                        },
                        enabled = store.cards.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.onBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Outbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_export_full_btn), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            pendingExportIncludeProgress = false
                            val todayStr = DailyProgress.todayString()
                            exportBackupLauncher.launch("ArtikelMeister_DestePaketi_$todayStr.json")
                        },
                        enabled = store.cards.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Outbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_export_deck_btn), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_import_backup_btn), style = MaterialTheme.typography.labelLarge)
                    }
                }

                SettingToggleRow(
                    label = stringResource(R.string.settings_merge_toggle),
                    checked = mergeOnBackupImport,
                    onCheckedChange = { mergeOnBackupImport = it }
                )

                Text(
                    text = stringResource(R.string.settings_merge_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                SettingsStatusMessage(
                    message = backupStatusMessage,
                    isError = backupStatusIsError,
                    detail = backupStatusDetail
                )
            }
        }

        // Data Management
        GroupBoxCard(
            title = stringResource(R.string.settings_data_mgmt_title),
            icon = Icons.Default.Storage,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirmation = true },
                        enabled = store.cards.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_reset_progress_btn), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAllConfirmation = true },
                        enabled = store.cards.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_delete_all_btn), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAccountConfirmation = true },
                        enabled = !isDeletingAccount && (authManager.isAuthorized || store.cards.isNotEmpty() || store.studyHistory.isNotEmpty()),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        if (isDeletingAccount) {
                            CircularProgressIndicator(
                                color = colors.error,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PersonRemove,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (isDeletingAccount) R.string.settings_deleting_account
                                else R.string.settings_delete_account
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_replace_dialog_title)) },
            text = {
                val fileName = pendingImportName
                Text(
                    if (fileName.isNotBlank()) {
                        stringResource(R.string.settings_replace_dialog_named, fileName)
                    } else {
                        stringResource(R.string.settings_replace_dialog_unnamed)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImportUri = null
                        importBackup(uri, merge = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                ) {
                    Text(stringResource(R.string.settings_replace_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountConfirmation = false },
            title = {
                Text(
                    stringResource(R.string.settings_delete_acct_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_delete_acct_msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        store.requestDeleteAccountAndAllData(authManager) {
                            isDeletingAccount = false
                            showDeleteAccountConfirmation = false
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                ) {
                    Text(
                        stringResource(
                            if (isDeletingAccount) R.string.settings_delete_acct_deleting
                            else R.string.settings_delete_acct_confirm
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmation = false },
                    enabled = !isDeletingAccount
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(
                    stringResource(R.string.settings_reset_progress_confirm_msg),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        store.resetAllProgress()
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                ) {
                    Text(stringResource(R.string.settings_reset_progress_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = {
                Text(
                    stringResource(R.string.settings_delete_all_confirm_msg),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        store.deleteAllCards()
                        showDeleteAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                ) {
                    Text(stringResource(R.string.settings_delete_all_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    // Whole row is the toggle target (label + switch announced together by TalkBack).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primaryText,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onBlue,
                checkedTrackColor = colors.accentBlue
            )
        )
    }
}

/** System / Light / Dark theme picker backed by the [UiPreferences] adapter. */
@Composable
private fun ThemeModeSelector(store: DeckStore) {
    val colors = LocalAppColors.current
    val selected = UiPreferences.effectiveThemeMode(store.settings.isDarkMode)
    val options = listOf(
        Triple(ThemeMode.SYSTEM, R.string.theme_mode_system, Icons.Default.BrightnessAuto),
        Triple(ThemeMode.LIGHT, R.string.theme_mode_light, Icons.Default.LightMode),
        Triple(ThemeMode.DARK, R.string.theme_mode_dark, Icons.Default.DarkMode)
    )
    Surface(
        color = colors.textBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.cardBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            options.forEach { (mode, labelRes, icon) ->
                val isSelected = selected == mode
                val bg by animateColorAsState(
                    targetValue = if (isSelected) colors.blueBadgeBackground else Color.Transparent,
                    animationSpec = tween(160),
                    label = "themeSegBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accentBlue.copy(alpha = 0.4f) else Color.Transparent,
                    animationSpec = tween(160),
                    label = "themeSegBorder"
                )
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { UiPreferences.setThemeMode(mode, store) }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) colors.accentBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.accentBlue else colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageFlagIcon(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val borderColor = if (colors.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f)
    val shape = RoundedCornerShape(4.dp)

    Canvas(
        modifier = modifier
            .size(width = 26.dp, height = 18.dp)
            .clip(shape)
            .border(0.75.dp, borderColor, shape)
    ) {
        val w = size.width
        val h = size.height

        when (language) {
            AppLanguage.TR -> {
                drawRect(color = Color(0xFFE30A17))
                // Crescent
                val outerRadius = h * 0.27f
                val innerRadius = h * 0.215f
                drawCircle(
                    color = Color.White,
                    radius = outerRadius,
                    center = Offset(w * 0.40f, h * 0.50f)
                )
                drawCircle(
                    color = Color(0xFFE30A17),
                    radius = innerRadius,
                    center = Offset(w * 0.465f, h * 0.50f)
                )
                // 5-pointed star
                val starCx = w * 0.65f
                val starCy = h * 0.50f
                val starOuter = h * 0.135f
                val starInner = h * 0.054f
                val starPath = Path().apply {
                    for (i in 0 until 10) {
                        val angleDeg = -18.0 + i * 36.0
                        val angleRad = Math.toRadians(angleDeg)
                        val r = if (i % 2 == 0) starOuter else starInner
                        val x = starCx + (r * kotlin.math.cos(angleRad)).toFloat()
                        val y = starCy + (r * kotlin.math.sin(angleRad)).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path = starPath, color = Color.White)
            }

            AppLanguage.EN -> {
                // Clean flat Duolingo-style UK Union Jack
                drawRect(color = Color(0xFF012169))
                val whiteDiag = h * 0.22f
                val redDiag = h * 0.11f
                drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = whiteDiag)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = whiteDiag)
                drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), strokeWidth = redDiag)
                drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), strokeWidth = redDiag)
                // White cross
                val whiteCross = h * 0.30f
                drawRect(Color.White, topLeft = Offset((w - whiteCross) / 2f, 0f), size = Size(whiteCross, h))
                drawRect(Color.White, topLeft = Offset(0f, (h - whiteCross) / 2f), size = Size(w, whiteCross))
                // Red cross
                val redCross = h * 0.17f
                drawRect(Color(0xFFC8102E), topLeft = Offset((w - redCross) / 2f, 0f), size = Size(redCross, h))
                drawRect(Color(0xFFC8102E), topLeft = Offset(0f, (h - redCross) / 2f), size = Size(w, redCross))
            }

            AppLanguage.DE -> {
                val bandH = h / 3f
                drawRect(color = Color(0xFF2B2B2B), topLeft = Offset(0f, 0f), size = Size(w, bandH))
                drawRect(color = Color(0xFFDD0000), topLeft = Offset(0f, bandH), size = Size(w, bandH))
                drawRect(color = Color(0xFFFFCE00), topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH + 1f))
            }
        }

        // Subtle Duolingo-style top inner highlight
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                startY = 0f,
                endY = h * 0.45f
            ),
            size = Size(w, h * 0.45f)
        )
    }
}

@Composable
private fun LanguageSelector() {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val selected = UiPreferences.appLanguage

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.language_label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText
        )
        Surface(
            color = colors.textBackground,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = selected == lang
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) colors.blueBadgeBackground else Color.Transparent,
                        animationSpec = tween(160),
                        label = "langSegBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) colors.accentBlue.copy(alpha = 0.4f) else Color.Transparent,
                        animationSpec = tween(160),
                        label = "langSegBorder"
                    )
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (!isSelected) {
                                        UiPreferences.selectAppLanguage(lang)
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            LanguageFlagIcon(language = lang)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(lang.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.accentBlue else colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLimitControl(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    sliderA11y: String,
    decA11y: String,
    incA11y: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSlide: (Float) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = colors.blueBadgeBackground,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.accentBlue.copy(alpha = 0.25f))
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = colors.accentBlue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = onDecrease,
                shape = RoundedCornerShape(12.dp),
                color = colors.textBackground,
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = decA11y }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Slider(
                value = value,
                onValueChange = onSlide,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentBlue,
                    activeTrackColor = colors.accentBlue
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = sliderA11y
                        stateDescription = valueText
                    }
            )

            Surface(
                onClick = onIncrease,
                shape = RoundedCornerShape(12.dp),
                color = colors.textBackground,
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = incA11y }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Helper Subviews

@Composable
private fun StatsSettingsScreen(
    title: String,
    onOpenDrawer: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.controlBackground)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (onOpenDrawer != null) {
                    val menuDesc = stringResource(R.string.action_open_menu)
                    Surface(
                        onClick = onOpenDrawer,
                        shape = RoundedCornerShape(14.dp),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    modifier = Modifier
                        .padding(start = if (onOpenDrawer == null) 8.dp else 0.dp)
                        .semantics { heading() }
                )
            }
            HorizontalDivider(color = colors.cardBorderSubtle)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsStatusMessage(
    message: String?,
    isError: Boolean,
    isBusy: Boolean = false,
    detail: String? = null
) {
    val colors = LocalAppColors.current
    val foreground = when {
        isError -> colors.error
        isBusy -> colors.accentBlue
        else -> colors.success
    }
    val background = when {
        isError -> colors.errorBadgeBackground
        isBusy -> colors.blueBadgeBackground
        else -> colors.successBadgeBackground
    }
    Box(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        if (message != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val icon = when {
                    isError -> Icons.Default.Warning
                    isBusy -> Icons.Default.CloudSync
                    else -> Icons.Default.CheckCircle
                }
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = foreground)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.controlBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.cardBorderSubtle),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 3.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge.tabular(),
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun FeedbackBadge(
    title: String,
    count: Int,
    rating: ReviewRating
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    val accent = colors.ratingColor(rating)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(colors.ratingBadgeBackground(rating), shape)
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText
        )
    }
}

@Composable
private fun DistributionRow(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val colors = LocalAppColors.current
    val ratio = if (total > 0) count.toFloat() / total.toFloat() else 0f
    val pct = (ratio * 100f).roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.stats_word_count_pct, count, pct),
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = colors.secondaryText
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.secondaryText.copy(alpha = 0.14f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
