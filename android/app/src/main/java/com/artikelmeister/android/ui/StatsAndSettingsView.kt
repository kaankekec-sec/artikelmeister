package com.artikelmeister.android.ui

import com.artikelmeister.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.DailyProgress
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.GoogleDriveSyncService
import com.artikelmeister.android.ReviewRating
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsView(
    store: DeckStore,
    onOpenDrawer: (() -> Unit)? = null
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

    val activeStudyDaysCount = store.studyHistory.values.count { it.totalAnswersCount > 0 }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onOpenDrawer != null) {
                IconButton(
                    onClick = onOpenDrawer
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.action_open_menu),
                        tint = colors.primaryText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Çalışma İstatistikleri",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
        }

        // Summary Cards (2x2 or 4-col responsive)
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
                title = "Olgunlaşmış (Kalıcı)",
                value = "$matureCount",
                subtitle = "Aralık ≥ 21 gün",
                color = colors.accentPurple,
                modifier = Modifier.weight(1f)
            )
        }

        // Streak & 35-Day Activity Heatmap (#4)
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
                            tint = Color(0xFFFFD60A),
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

                // Heatmap Header & Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Son 35 Günlük Aktivite Yoğunluğu",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Az", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                        listOf(0, 3, 12, 25, 50).forEach { sample ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        heatmapColor(sample, colors.secondaryText, colors.vividGreen),
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                        Text("Çok", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                    }
                }

                // 35-Day Grid (5 rows x 7 columns)
                val days = store.heatmapDays(35)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.chunked(7).forEach { weekChunk ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            weekChunk.forEach { item ->
                                HeatmapCell(
                                    displayDate = item.displayDate,
                                    count = item.count,
                                    color = heatmapColor(item.count, colors.secondaryText, colors.vividGreen),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Today's Activity
        GroupBoxCard(
            title = "Bugünkü Performans (${store.dailyProgress.dateString})",
            icon = Icons.Default.CalendarMonth,
            iconTint = colors.accentBlue
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeedbackBadge(
                        title = "Tekrar (1)",
                        count = store.dailyProgress.againCount,
                        color = ReviewRating.AGAIN.color
                    )
                    FeedbackBadge(
                        title = "Zor (2)",
                        count = store.dailyProgress.hardCount,
                        color = ReviewRating.HARD.color
                    )
                    FeedbackBadge(
                        title = "İyi (3)",
                        count = store.dailyProgress.goodCount,
                        color = ReviewRating.GOOD.color
                    )
                    FeedbackBadge(
                        title = "Kolay (4)",
                        count = store.dailyProgress.easyCount,
                        color = ReviewRating.EASY.color
                    )
                }
            }
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
                    total = max(1, totalCards),
                    color = Artikel.DER.themeColor()
                )
                DistributionRow(
                    label = "die (Dişil)",
                    count = dieCount,
                    total = max(1, totalCards),
                    color = Artikel.DIE.themeColor()
                )
                DistributionRow(
                    label = "das (Nötr)",
                    count = dasCount,
                    total = max(1, totalCards),
                    color = Artikel.DAS.themeColor()
                )
            }
        }

        // Deck Distribution (#5)
        GroupBoxCard(
            title = "Deste / Kategori Dağılımı (${store.allDeckNames.size} Deste)",
            icon = Icons.Default.Folder,
            iconTint = Color(0xFF6366F1)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                store.allDeckNames.forEach { deck ->
                    val count = store.cards.count { it.deckName == deck }
                    DistributionRow(
                        label = deck,
                        count = count,
                        total = max(1, totalCards),
                        color = Color(0xFF6366F1)
                    )
                }
            }
        }
    }
}

private fun heatmapColor(count: Int, secondary: Color, green: Color): Color {
    return when {
        count <= 0 -> secondary.copy(alpha = 0.14f)
        count < 5 -> green.copy(alpha = 0.35f)
        count < 15 -> green.copy(alpha = 0.55f)
        count < 35 -> green.copy(alpha = 0.78f)
        else -> green
    }
}

@Composable
private fun HeatmapCell(
    displayDate: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    // Compact cell (7 per row on phones): day number + count only; full text for TalkBack.
    val dayLabel = displayDate.trim().substringBefore(' ')
    val a11yText = if (count > 0) "$displayDate: $count yanıt" else "$displayDate: —"
    Column(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = a11yText }
            .clip(RoundedCornerShape(7.dp))
            .background(color)
            .border(
                1.dp,
                colors.primaryText.copy(alpha = if (count > 0) 0.14f else 0.04f),
                RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (count > 0) colors.primaryText else colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (count > 0) "$count" else "—",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) colors.primaryText else colors.secondaryText.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    val scope = rememberCoroutineScope()

    var showResetConfirmation by remember { mutableStateOf(false) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var mergeOnBackupImport by remember { mutableStateOf(true) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }
    var pendingExportIncludeProgress by remember { mutableStateOf(true) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonStr = store.exportBackupJSONString(includeProgress = pendingExportIncludeProgress)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                backupStatusIsError = false
                backupStatusMessage = if (pendingExportIncludeProgress) {
                    "Tam yedek başarıyla kaydedildi."
                } else {
                    "Paylaşılabilir deste paketi (kişisel ilerleme olmadan) kaydedildi."
                }
            }.onFailure { err ->
                backupStatusIsError = true
                backupStatusMessage = "Dosya kaydedilemedi: ${err.localizedMessage}"
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IllegalArgumentException("Dosya okunamadı.")

                val (added, updated) = store.importBackupJSONString(jsonStr, mergeWithExisting = mergeOnBackupImport)
                backupStatusIsError = false
                backupStatusMessage = if (mergeOnBackupImport) {
                    "Paket birleştirildi: $added yeni kelime eklendi, $updated mevcut kelime kontrol edildi."
                } else {
                    "Yedek başarıyla geri yüklendi: Toplam $added kelime aktif."
                }
            }.onFailure { err ->
                backupStatusIsError = true
                backupStatusMessage = "Yedek dosyası okunamadı: ${err.localizedMessage}"
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            runCatching {
                authManager.handleGoogleSignInResult(result.data, context)
                store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = false)
            }.onFailure { err ->
                store.cloudSyncStatusMessage = err.localizedMessage ?: "Google girişi başarısız oldu."
                store.cloudSyncIsError = true
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onOpenDrawer != null) {
                IconButton(
                    onClick = onOpenDrawer
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.action_open_menu),
                        tint = colors.primaryText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Ayarlar ve Hesap Yönetimi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
        }

        // Universal Google Profile & Cloud Synchronization
        GroupBoxCard(
            title = "Evrensel Profil & Google Drive Bulut Eşitleme",
            icon = if (authManager.isGoogleAccountConnected) Icons.Default.CloudDone else Icons.Default.AccountCircle,
            iconTint = if (authManager.isGoogleAccountConnected) colors.vividGreen else colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (authManager.isGoogleAccountConnected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.vividGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .border(1.dp, colors.vividGreen.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.vividGreen.copy(alpha = 0.18f), CircleShape)
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
                                color = colors.vividGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = false)
                                }
                            },
                            enabled = !store.isSyncingCloud && !isDeletingAccount,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (store.isSyncingCloud) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (store.isSyncingCloud) "Eşitleniyor..." else "Şimdi Bulutla Eşitle",
                                style = MaterialTheme.typography.labelSmall,
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
                            Text("Hesaptan Çıkış Yap", style = MaterialTheme.typography.labelSmall)
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
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (authManager.isAuthenticating || store.isSyncingCloud) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (authManager.isAuthenticating) "Google ile Bağlanılıyor..." else "Google Hesabı ile Giriş Yap & Eşitle",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                store.cloudSyncStatusMessage?.let { statusMsg ->
                    val statusColor = if (store.cloudSyncIsError) colors.vividRed else colors.vividGreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = if (store.cloudSyncIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMsg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (store.cloudSyncIsError) colors.vividRed else colors.primaryText
                        )
                    }
                }
            }
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
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease), tint = colors.primaryText)
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase), tint = colors.primaryText)
                        }
                    }
                }

                Slider(
                    value = store.settings.dailyNewCardLimit.coerceIn(1, 200).toFloat(),
                    onValueChange = { v ->
                        store.updateSettings { it.copy(dailyNewCardLimit = v.roundToInt().coerceIn(1, 500)) }
                    },
                    valueRange = 1f..200f,
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
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease), tint = colors.primaryText)
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase), tint = colors.primaryText)
                        }
                    }
                }

                Slider(
                    value = store.settings.dailyReviewLimit.coerceIn(10, 500).toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 10f).roundToInt() * 10).coerceIn(10, 1000)
                        store.updateSettings { it.copy(dailyReviewLimit = stepped) }
                    },
                    valueRange = 10f..500f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentBlue,
                        activeTrackColor = colors.accentBlue
                    )
                )
            }
        }

        // Appearance: System / Light / Dark
        GroupBoxCard(
            title = stringResource(R.string.theme_section_title),
            icon = Icons.Default.Palette,
            iconTint = colors.accentPurple
        ) {
            ThemeModeSelector(store = store)
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

        // Backup & Restore / Deck Sharing
        GroupBoxCard(
            title = "Yedekleme ve Deste Paylaşımı",
            icon = Icons.Default.Save,
            iconTint = colors.accentBlue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Tüm kelimelerinizi ve ilerlemenizi yedekleyebilir veya arkadaşlarınızla paylaşabileceğiniz deste paketleri oluşturabilirsiniz.",
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
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tam Yedek İndir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deste Paketi Paylaş", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yedek veya Deste Yükle", style = MaterialTheme.typography.labelSmall)
                    }
                }

                SettingToggleRow(
                    label = "İçe aktarırken mevcut kelimelerimi koru ve yeni gelenleri birleştir (Kapatılırsa yedek dosyası tüm verilerin üzerine yazılır)",
                    checked = mergeOnBackupImport,
                    onCheckedChange = { mergeOnBackupImport = it }
                )

                backupStatusMessage?.let { msg ->
                    val statusColor = if (backupStatusIsError) colors.vividRed else colors.vividGreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = if (backupStatusIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (backupStatusIsError) colors.vividRed else colors.primaryText
                        )
                    }
                }
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
                        border = BorderStroke(1.dp, colors.vividRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colors.vividRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Öğrenme İlerlemesini Sıfırla", color = colors.vividRed, style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAllConfirmation = true },
                        enabled = store.cards.isNotEmpty(),
                        border = BorderStroke(1.dp, colors.vividRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.vividRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tüm Kelimeleri Sil", color = colors.vividRed, style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = { showDeleteAccountConfirmation = true },
                        enabled = !isDeletingAccount && (authManager.isAuthorized || store.cards.isNotEmpty() || store.studyHistory.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.vividRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isDeletingAccount) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeletingAccount) "Hesap Verileri Siliniyor..." else "Hesap Verilerini Sil",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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
                        scope.launch {
                            isDeletingAccount = true
                            store.deleteAccountAndAllData(authManager)
                            isDeletingAccount = false
                            showDeleteAccountConfirmation = false
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.vividRed)
                ) {
                    Text(
                        if (isDeletingAccount) "Siliniyor..." else "Evet, Tüm Hesap Verilerini Sil",
                        color = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = colors.vividRed)
                ) {
                    Text("İlerlemeyi Sıfırla (Kelimeler Kalır)", color = Color.White)
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
                    colors = ButtonDefaults.buttonColors(containerColor = colors.vividRed)
                ) {
                    Text("Tüm Kelimeleri Sil", color = Color.White)
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
                checkedThumbColor = Color.White,
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
                        activeContainerColor = colors.accentBlue.copy(alpha = 0.16f),
                        activeContentColor = colors.accentBlue,
                        activeBorderColor = colors.cardBorderSubtle,
                        inactiveContainerColor = colors.textBackground,
                        inactiveContentColor = colors.primaryText,
                        inactiveBorderColor = colors.cardBorderSubtle
                    )
                ) {
                    Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeedbackBadge(
    title: String,
    count: Int,
    color: Color
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
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
