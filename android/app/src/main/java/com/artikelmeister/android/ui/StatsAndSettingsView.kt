package com.artikelmeister.android.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
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

    StatsSettingsScreen(title = "Çalışma İstatistikleri", onOpenDrawer = onOpenDrawer) {
        if (activeStudyDaysCount == 0) {
            StateMessageView(
                icon = Icons.Default.CalendarMonth,
                title = "Henüz çalışma kaydı yok",
                message = if (onSelectTab != null) {
                    "Çalışmaya başladığınızda yanıtlarınız burada görünür."
                } else {
                    "İlk yanıtınızı vermek için Çalış sekmesine geçin."
                },
                modifier = Modifier.fillMaxWidth(),
                primaryActionLabel = stringResource(R.string.tab_study),
                onPrimaryAction = onSelectTab?.let { select -> { select(AppTab.STUDY) } }
            )
        } else {
            // Today's Activity
            GroupBoxCard(
                title = "Bugünkü çalışma (${store.dailyProgress.dateString})",
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
                                append("Yeni Öğrenilen: ")
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
                                append("Tamamlanan Tekrar: ")
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
                        title = "Tekrar",
                        count = store.dailyProgress.againCount,
                        rating = ReviewRating.AGAIN
                    )
                    FeedbackBadge(
                        title = "Zor",
                        count = store.dailyProgress.hardCount,
                        rating = ReviewRating.HARD
                    )
                    FeedbackBadge(
                        title = "İyi",
                        count = store.dailyProgress.goodCount,
                        rating = ReviewRating.GOOD
                    )
                    FeedbackBadge(
                        title = "Kolay",
                        count = store.dailyProgress.easyCount,
                        rating = ReviewRating.EASY
                    )
                }
            }

            // Streak & 35-Day Activity Heatmap
            GroupBoxCard(
                title = "Günlük Seri (Streak) ve 35 Günlük Çalışma Isı Haritası",
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
                                    text = "${store.currentStreakDays} Gün",
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "Kesintisiz Günlük Seri",
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
                                    text = "${store.longestStreakDays} Gün",
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "En Uzun Seri Rekoru",
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
                                    text = "$activeStudyDaysCount Gün",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "Toplam Aktif Çalışılan Gün",
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
                title = "Henüz kelime yok",
                message = if (onSelectTab != null) {
                    "İstatistiklerinizi görmek için kelime havuzuna kelime ekleyin."
                } else {
                    "İstatistiklerinizi görmek için Kelimeler sekmesinden kelime ekleyin."
                },
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
                    title = "Yeni (Çalışılmamış)",
                    value = "$newCount",
                    subtitle = "Sırada bekleyen",
                    color = colors.accentBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Öğreniliyor",
                    value = "$learningCount",
                    subtitle = "Kısa aralıklı",
                    color = colors.accentOrange,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title = "Genç Kartlar",
                    value = "$reviewCount",
                    subtitle = "Aralık < 21 gün",
                    color = colors.vividGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Olgun kartlar",
                    value = "$matureCount",
                    subtitle = "Aralık ≥ 21 gün",
                    color = colors.accentPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            // Artikel Distribution
            GroupBoxCard(
                title = "Artikel Dağılımı (Toplam $totalCards Kelime)",
                icon = Icons.Default.BarChart,
                iconTint = colors.accentBlue
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DistributionRow(
                        label = "der (Eril)",
                        count = derCount,
                        total = totalCards,
                        color = Artikel.DER.themeColor()
                    )
                    DistributionRow(
                        label = "die (Dişil)",
                        count = dieCount,
                        total = totalCards,
                        color = Artikel.DIE.themeColor()
                    )
                    DistributionRow(
                        label = "das (Nötr)",
                        count = dasCount,
                        total = totalCards,
                        color = Artikel.DAS.themeColor()
                    )
                    if (articlelessCount > 0) {
                        DistributionRow(
                            label = "Artikelsiz",
                            count = articlelessCount,
                            total = totalCards,
                            color = colors.artikelNone
                        )
                    }
                }
            }

            // Deck Distribution (#5)
            GroupBoxCard(
                title = "Deste / Kategori Dağılımı (${store.allDeckNames.size} Deste)",
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
    val outline = MaterialTheme.colorScheme.outline
    val ranges = listOf(0 to "0", 1 to "1–4", 5 to "5–14", 15 to "15–34", 35 to "35+")
    val locale = Locale.forLanguageTag("tr-TR")
    val fullDateFormat = remember { DateTimeFormatter.ofPattern("d MMMM uuuu", locale) }
    val cellMinWidth = with(LocalDensity.current) { 36.sp.toDp() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Son 35 Günlük Aktivite Yoğunluğu",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondaryText,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ranges.forEach { (count, label) ->
                val palette = colors.heatmapColors(count)
                val shape = RoundedCornerShape(4.dp)
                Text(
                    text = "$label yanıt",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.foreground,
                    modifier = Modifier
                        .background(palette.background, shape)
                        .border(1.dp, outline, shape)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
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
    val palette = LocalAppColors.current.heatmapColors(count)
    val shape = RoundedCornerShape(7.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = "$fullDate, $count yanıt" }
            .background(palette.background, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 3.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = palette.foreground
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
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
                backupStatusMessage = if (pendingExportIncludeProgress) {
                    "Tam yedek dışa aktarıldı."
                } else {
                    "Deste paketi dışa aktarıldı."
                }
            }.onFailure { error ->
                backupStatusIsError = true
                backupStatusDetail = error.localizedMessage
                backupStatusMessage = "Dosya kaydedilemedi. Başka bir konum seçip tekrar deneyin."
            }
        }
    }

    fun importBackup(uri: Uri, merge: Boolean) {
        runCatching {
            val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: throw IllegalArgumentException("Dosya okunamadı.")

            val (added, updated) = store.importBackupJSONString(jsonStr, mergeWithExisting = merge)
            backupStatusIsError = false
            backupStatusDetail = null
            backupStatusMessage = if (merge) {
                "Paket birleştirildi: $added yeni kelime eklendi, $updated mevcut kelime kontrol edildi."
            } else {
                "Yedek geri yüklendi: Toplam $added kelime aktif."
            }
        }.onFailure { error ->
            backupStatusIsError = true
            backupStatusDetail = error.localizedMessage
            backupStatusMessage = "Dosya içe aktarılamadı. Bir ArtikelMeister .json yedeği veya deste paketi seçip tekrar deneyin."
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

    StatsSettingsScreen(title = "Ayarlar", onOpenDrawer = onOpenDrawer) {
        // Appearance: System / Light / Dark
        GroupBoxCard(
            title = stringResource(R.string.theme_section_title),
            icon = Icons.Default.Palette,
            iconTint = colors.accentPurple
        ) {
            ThemeModeSelector(store = store)
        }

        // Daily Limits
        GroupBoxCard(
            title = "Günlük Çalışma Limitleri",
            icon = Icons.Default.Tune,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Daily New Card Limit
                // Label stacked above the stepper so it never gets squeezed on 360dp phones.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Günlük Yeni Kelime Sayısı",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Text(
                            text = "Her gün çalışma sırasına eklenecek maksimum yeni kelime adedi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        IconButton(
                            onClick = {
                                store.updateSettings {
                                    it.copy(dailyNewCardLimit = (it.dailyNewCardLimit - 5).coerceIn(1, 500))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Günlük yeni kelime limitini azalt", tint = colors.primaryText)
                        }
                        Text(
                            text = "${store.settings.dailyNewCardLimit} kelime / gün",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.primaryText,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = {
                                store.updateSettings {
                                    it.copy(dailyNewCardLimit = (it.dailyNewCardLimit + 5).coerceIn(1, 500))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Günlük yeni kelime limitini artır", tint = colors.primaryText)
                        }
                    }
                }

                Slider(
                    value = store.settings.dailyNewCardLimit.coerceIn(1, 500).toFloat(),
                    modifier = Modifier.semantics {
                        contentDescription = "Günlük yeni kelime limiti"
                        stateDescription = "${store.settings.dailyNewCardLimit} kelime / gün"
                    },
                    onValueChange = { v ->
                        store.updateSettings { it.copy(dailyNewCardLimit = v.roundToInt().coerceIn(1, 500)) }
                    },
                    valueRange = 1f..500f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentBlue,
                        activeTrackColor = colors.accentBlue
                    )
                )

                HorizontalDivider(color = colors.cardBorderSubtle)

                // Daily Review Limit
                // Label stacked above the stepper so it never gets squeezed on 360dp phones.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Günlük Maksimum Tekrar Sayısı",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Text(
                            text = "Daha önce öğrenilmiş kelimelerden bir günde sorulacak maksimum tekrar kartı sayısı.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        IconButton(
                            onClick = {
                                store.updateSettings {
                                    it.copy(dailyReviewLimit = (it.dailyReviewLimit - 10).coerceIn(10, 1000))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Günlük tekrar limitini azalt", tint = colors.primaryText)
                        }
                        Text(
                            text = "${store.settings.dailyReviewLimit} tekrar / gün",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.primaryText,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = {
                                store.updateSettings {
                                    it.copy(dailyReviewLimit = (it.dailyReviewLimit + 10).coerceIn(10, 1000))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Günlük tekrar limitini artır", tint = colors.primaryText)
                        }
                    }
                }

                Slider(
                    value = store.settings.dailyReviewLimit.coerceIn(10, 1000).toFloat(),
                    modifier = Modifier.semantics {
                        contentDescription = "Günlük tekrar limiti"
                        stateDescription = "${store.settings.dailyReviewLimit} tekrar / gün"
                    },
                    onValueChange = { v ->
                        val stepped = ((v / 10f).roundToInt() * 10).coerceIn(10, 1000)
                        store.updateSettings { it.copy(dailyReviewLimit = stepped) }
                    },
                    valueRange = 10f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentBlue,
                        activeTrackColor = colors.accentBlue
                    )
                )
            }
        }

        // Card Presentation Preferences
        GroupBoxCard(
            title = "Kart Sunum Tercihleri",
            icon = Icons.Default.Style,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    label = "Kart çevrildiğinde Almanca kelimeyi otomatik seslendir",
                    checked = store.settings.autoPlayPronunciation,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(autoPlayPronunciation = v) }
                    }
                )
                HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.5f))
                SettingToggleRow(
                    label = "Kartın ön yüzünde Türkçe anlamı (ipucu olarak) göster",
                    checked = store.settings.showMeaningOnFront,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(showMeaningOnFront = v) }
                    }
                )
                HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.5f))
                SettingToggleRow(
                    label = "Yeni kelimeleri eklenme sırası yerine rastgele sırayla getir",
                    checked = store.settings.newCardOrderRandom,
                    onCheckedChange = { v ->
                        store.updateSettings { it.copy(newCardOrderRandom = v) }
                    }
                )
            }
        }

        // Universal Google Profile & Cloud Synchronization
        GroupBoxCard(
            title = "Google Drive ile eşitleme",
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
                            .background(colors.successBadgeBackground, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.success, RoundedCornerShape(10.dp))
                            .padding(12.dp)
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
                                text = authManager.userName.ifBlank { "Google Hesabı Bağlı" },
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                            Text(
                                text = authManager.userEmail.ifBlank { "Google Profil Aktif" },
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = colors.secondaryText
                            )
                            Text(
                                text = "Bulut Eşitleme Aktif",
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
                            shape = RoundedCornerShape(8.dp)
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
                                text = if (store.isSyncingCloud) "Eşitleniyor..." else "Şimdi Bulutla Eşitle",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                authManager.signOut(context) {
                                    store.cloudSyncStatusMessage = "Google hesabından çıkış yapıldı (yerel veriler korundu)."
                                    store.cloudSyncIsError = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hesaptan Çıkış Yap", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text(
                        text = "Google hesabınızla giriş yaparak tüm kelimelerinizi, çalışma geçmişinizi, günlük serinizi (streak) ve tercihlerinizi Google Drive bulutunuzla otomatik eşitleyebilirsiniz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText
                    )

                    Button(
                        onClick = {
                            googleSignInLauncher.launch(authManager.getGoogleSignInIntent(context))
                        },
                        enabled = !authManager.isAuthenticating && !store.isSyncingCloud,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.onBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
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
                                authManager.isAuthenticating -> "Google ile bağlanılıyor..."
                                store.isSyncingCloud -> "Google Drive ile eşitleniyor..."
                                else -> "Google hesabıyla giriş yap ve eşitle"
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
                    isDeletingAccount -> "Hesap verileri siliniyor..."
                    authManager.isAuthenticating -> "Google ile bağlanılıyor..."
                    store.isSyncingCloud -> "Google Drive ile eşitleniyor..."
                    cloudIsError -> "Google Drive işlemi tamamlanamadı. Ayrıntıları kontrol edip tekrar deneyin."
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
            title = "Yedekleme ve Deste Paylaşımı",
            icon = Icons.Default.Archive,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Yedek ve deste paketi .json dosyası olarak kaydedilir. Deste paketi kişisel ilerlemenizi içermez.",
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Outbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tam yedeği dışa aktar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            pendingExportIncludeProgress = false
                            val todayStr = DailyProgress.todayString()
                            exportBackupLauncher.launch("ArtikelMeister_DestePaketi_$todayStr.json")
                        },
                        enabled = store.cards.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Outbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deste paketini dışa aktar", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MoveToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yedeği veya paketi içe aktar", style = MaterialTheme.typography.labelLarge)
                    }
                }

                SettingToggleRow(
                    label = "Birleştir",
                    checked = mergeOnBackupImport,
                    onCheckedChange = { mergeOnBackupImport = it }
                )

                Text(
                    text = "Birleştirme kapalıysa içe aktarma, onayınızdan sonra mevcut verileri değiştirir.",
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
            title = "Veri Yönetimi",
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
                        border = BorderStroke(1.dp, colors.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Öğrenme İlerlemesini Sıfırla", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAllConfirmation = true },
                        enabled = store.cards.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tüm Kelimeleri Sil", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAccountConfirmation = true },
                        enabled = !isDeletingAccount && (authManager.isAuthorized || store.cards.isNotEmpty() || store.studyHistory.isNotEmpty()),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error),
                        shape = RoundedCornerShape(8.dp)
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
                            text = if (isDeletingAccount) "Hesap Verileri Siliniyor..." else "Hesap Verilerini Sil",
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
            title = { Text("Mevcut veriler değiştirilsin mi?") },
            text = {
                val fileName = pendingImportName
                Text(
                    if (fileName.isNotBlank()) {
                        "“$fileName” dosyasını içe aktarmak mevcut verilerinizi değiştirir. Bu işlem geri alınamaz."
                    } else {
                        "Seçilen dosyayı içe aktarmak mevcut verilerinizi değiştirir. Bu işlem geri alınamaz."
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
                    Text("Mevcut verileri değiştir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("İptal") }
            }
        )
    }

    if (showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountConfirmation = false },
            title = {
                Text(
                    "Hesap Verilerini Silmek İstediğinize Emin Misiniz?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            text = {
                Text(
                    text = "Bu işlem geri alınamaz:\n" +
                        "• Google Drive'daki \"ArtikelMeister Cloud Sync\" klasörünüz ve bulut yedeğiniz kalıcı olarak silinir.\n" +
                        "• Bu cihazdaki tüm kelimeleriniz, desteleriniz, günlük seriniz (streak) ve tercih ayarlarınız sıfırlanır.\n" +
                        "• Google hesabı bağlantınız sonlandırılır.",
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
                        if (isDeletingAccount) "Siliniyor..." else "Evet, Tüm Hesap Verilerini Sil",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmation = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("İptal")
                }
            }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(
                    "Tüm kelimelerin öğrenme geçmişini sıfırlamak istediğinize emin misiniz?",
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
                    Text("İlerlemeyi Sıfırla (Kelimeler Kalır)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = {
                Text(
                    "Yerel depolamadaki tüm kelimeleri silmek istediğinize emin misiniz?",
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
                    Text("Tüm Kelimeleri Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("İptal")
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(store: DeckStore) {
    val colors = LocalAppColors.current
    val selected = UiPreferences.effectiveThemeMode(store.settings.isDarkMode)
    val options = listOf(
        Triple(ThemeMode.SYSTEM, R.string.theme_mode_system, Icons.Default.BrightnessAuto),
        Triple(ThemeMode.LIGHT, R.string.theme_mode_light, Icons.Default.LightMode),
        Triple(ThemeMode.DARK, R.string.theme_mode_dark, Icons.Default.DarkMode)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, labelRes, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { UiPreferences.setThemeMode(mode, store) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.blueBadgeBackground,
                        activeContentColor = colors.accentBlue,
                        activeBorderColor = colors.cardBorderSubtle,
                        inactiveContainerColor = colors.textBackground,
                        inactiveContentColor = colors.primaryText,
                        inactiveBorderColor = colors.cardBorderSubtle
                    )
                ) {
                    Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            text = stringResource(R.string.theme_mode_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText
        )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onOpenDrawer == null) 20.dp else 12.dp, end = 20.dp)
                .padding(vertical = 12.dp)
        ) {
            if (onOpenDrawer != null) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.action_open_menu),
                        tint = colors.primaryText
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() }
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondaryText
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color
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
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(colors.ratingBadgeBackground(rating), shape)
            .border(1.dp, colors.ratingColor(rating), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                text = "$count kelime (${(ratio * 100f).roundToInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.secondaryText.copy(alpha = 0.15f))
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
