package com.artikelmeister.android.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CEFRLevel
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.GeminiAuthMode
import com.artikelmeister.android.GeminiVocabularyGenerator
import com.artikelmeister.android.GeneratedWordItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AIGeneratorDialog(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLevel by remember { mutableStateOf(CEFRLevel.A1) }
    var wordCount by remember { mutableIntStateOf(20) }
    var topicHint by remember { mutableStateOf("") }
    var targetDeckName by remember { mutableStateOf("A1 Seviyesi") }
    var hasCustomDeckName by remember { mutableStateOf(false) }
    var showAuthConfig by remember { mutableStateOf(!authManager.isAuthorized) }
    var showAdvancedApiKey by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var newlyAddedWords by remember { mutableStateOf<List<GeneratedWordItem>>(emptyList()) }

    var showModelPicker by remember { mutableStateOf(false) }
    var showTargetDeckMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!authManager.isAuthorized) {
            showAuthConfig = true
        } else {
            runCatching { authManager.verifyCredentials() }
        }
    }

    fun generateAndAddWords() {
        scope.launch {
            errorMessage = null
            isGenerating = true
            newlyAddedWords = emptyList()
            progressMessage = "Gemini bağlantısı kuruluyor..."

            try {
                val generated = GeminiVocabularyGenerator.generateWords(
                    level = selectedLevel,
                    targetCount = wordCount,
                    topicHint = topicHint,
                    existingCards = store.cards,
                    authManager = authManager,
                    onProgress = { status ->
                        progressMessage = status
                    }
                )

                val resolvedDeck = targetDeckName.trim().ifEmpty { "${selectedLevel.rawValue} Seviyesi" }
                store.createDeck(resolvedDeck)

                val addedItems = mutableListOf<GeneratedWordItem>()
                for (item in generated) {
                    val art = Artikel.parse(item.artikel)
                    if (art != null) {
                        if (store.addCard(art, item.word, item.meaning, resolvedDeck)) {
                            addedItems.add(item)
                        }
                    }
                }
                newlyAddedWords = addedItems
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Kelime üretilirken bir hata oluştu."
            } finally {
                isGenerating = false
                progressMessage = ""
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        val view = LocalView.current
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            Surface(
                color = colors.windowBackground,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Sheet Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.controlBackground)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accentPurple,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gemini AI ile Seviyeye Göre Kelime Üret",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Text(
                            text = "Kendi Gemini kullanım hakkınızla A1–C1 seviyesinde, listenizde olmayan yeni kelimeler üretin",
                            fontSize = 11.sp,
                            color = colors.secondaryText
                        )
                    }
                    IconButton(
                        onClick = { if (!isGenerating) onDismiss() },
                        enabled = !isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Kapat",
                            tint = colors.secondaryText
                        )
                    }
                }

                HorizontalDivider(color = colors.cardBorderSubtle)

                // Scrollable Body
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    // 1. Gemini Authorization Box
                    GroupBoxCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (authManager.isAuthorized) Icons.Default.VerifiedUser else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (authManager.isAuthorized) colors.vividGreen else colors.accentOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (authManager.isAuthorized) "Gemini Yetkilendirmesi Aktif" else "Gemini Yetkilendirmesi Gerekli",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primaryText
                                        )
                                        Text(
                                            text = "${authManager.authMode.title} • Model: ${authManager.selectedModel}",
                                            fontSize = 11.sp,
                                            color = colors.secondaryText
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showAuthConfig = !showAuthConfig },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (showAuthConfig) "Gizle" else "Yetkilendirme Ayarları",
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showAuthConfig) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    HorizontalDivider(color = colors.cardBorderSubtle)

                                    // Model Picker (Selectable list - No text input!)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Aktif Gemini Modeli:",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primaryText
                                        )

                                        Box {
                                            Surface(
                                                color = colors.textBackground,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                                                modifier = Modifier.clickable { showModelPicker = true }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                ) {
                                                    Text(
                                                        text = authManager.selectedModel,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.accentPurple
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = colors.secondaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = showModelPicker,
                                                onDismissRequest = { showModelPicker = false }
                                            ) {
                                                authManager.availableModels.forEach { model ->
                                                    val isSelected = authManager.selectedModel == model
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = model,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) colors.accentPurple else colors.primaryText
                                                            )
                                                        },
                                                        trailingIcon = {
                                                            if (isSelected) {
                                                                Icon(
                                                                    Icons.Default.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = colors.accentPurple,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            authManager.selectedModel = model
                                                            authManager.saveToKeystore()
                                                            showModelPicker = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Primary Auth: Google OAuth 2.0 (Only method shown directly)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        errorMessage = null
                                                        authManager.startBrowserOAuthFlow(context)
                                                        showAuthConfig = false
                                                        store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = true)
                                                    }.onFailure { err ->
                                                        errorMessage = err.localizedMessage
                                                    }
                                                }
                                            },
                                            enabled = !authManager.isAuthenticating,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.accentBlue,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (authManager.isAuthorized && authManager.authMode == GeminiAuthMode.GOOGLE_OAUTH) "Google Hesabını Yeniden Bağla" else "Google Hesabı ile Giriş Yap (Tek Tık)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        if (authManager.isAuthenticating) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Yetkilendiriliyor...", fontSize = 11.sp, color = colors.secondaryText)
                                            }
                                        }

                                        TextButton(
                                            onClick = { showAdvancedApiKey = !showAdvancedApiKey }
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (showAdvancedApiKey) "Gelişmiş Seçenekleri Gizle" else "Gelişmiş: API Anahtarı ile Doğrula",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Advanced Section: API Key configuration (Moved here, Custom Client ID deleted)
                                    AnimatedVisibility(visible = showAdvancedApiKey) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(colors.textBackground, RoundedCornerShape(10.dp))
                                                .border(1.dp, colors.cardBorderSubtle, RoundedCornerShape(10.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Google AI Studio API Anahtarı:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.primaryText
                                            )
                                            Text(
                                                text = "Google hesabınızla Google AI Studio üzerinden ücretsiz Gemini API anahtarınızı alıp aşağıya yapıştırabilirsiniz.",
                                                fontSize = 11.sp,
                                                color = colors.secondaryText
                                            )

                                            OutlinedTextField(
                                                value = authManager.apiKey,
                                                onValueChange = { authManager.apiKey = it },
                                                placeholder = { Text("Gemini API Anahtarı (AIza...)", fontSize = 12.sp) },
                                                visualTransformation = PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { authManager.openGoogleAIStudioInBrowser(context) }
                                                ) {
                                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Google AI Studio'dan Al", fontSize = 12.sp)
                                                }

                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            runCatching {
                                                                errorMessage = null
                                                                authManager.authMode = GeminiAuthMode.API_KEY
                                                                authManager.verifyCredentials()
                                                                showAuthConfig = false
                                                            }.onFailure { err ->
                                                                errorMessage = err.localizedMessage
                                                            }
                                                        }
                                                    },
                                                    enabled = authManager.apiKey.trim().isNotEmpty(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.accentBlue,
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Doğrula & Kaydet", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }

                                    authManager.authStatusMessage?.let { statusMsg ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = statusMsg,
                                                fontSize = 11.sp,
                                                color = colors.secondaryText,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (authManager.isAuthorized) {
                                                TextButton(
                                                    onClick = { authManager.clearAuthorization() }
                                                ) {
                                                    Text("Yetkiyi Kaldır", fontSize = 11.sp, color = colors.vividRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Level (A1-C1) & Count (1-100) Configuration Box
                    GroupBoxCard(
                        title = "Seviye (A1 – C1) ve Kelime Sayısı (1 – 100)",
                        icon = Icons.Default.School,
                        iconTint = colors.accentPurple
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Almanca Dil Seviyesi:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CEFRLevel.entries.forEach { lvl ->
                                    val isSelected = selectedLevel == lvl
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) colors.accentPurple.copy(alpha = 0.2f)
                                                else colors.textBackground
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) colors.accentPurple else colors.cardBorderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                selectedLevel = lvl
                                                if (!hasCustomDeckName) {
                                                    targetDeckName = "${lvl.rawValue} Seviyesi"
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = lvl.rawValue,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) colors.accentPurple else colors.primaryText
                                        )
                                        Text(
                                            text = lvl.shortSubtitle,
                                            fontSize = 10.sp,
                                            color = if (isSelected) colors.accentPurple else colors.secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Text(
                                text = selectedLevel.description,
                                fontSize = 11.sp,
                                color = colors.secondaryText
                            )

                            HorizontalDivider(color = colors.cardBorderSubtle)

                            // Word Count Selector (1 - 100)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Üretilecek Kelime Sayısı (1 – 100):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { wordCount = (wordCount - 1).coerceAtLeast(1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Azalt", tint = colors.primaryText)
                                    }
                                    Text(
                                        text = "$wordCount kelime",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.accentPurple,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { wordCount = (wordCount + 1).coerceAtMost(100) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Artır", tint = colors.primaryText)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("1", fontSize = 11.sp, color = colors.secondaryText)
                                Slider(
                                    value = wordCount.toFloat(),
                                    onValueChange = { wordCount = it.roundToInt().coerceIn(1, 100) },
                                    valueRange = 1f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accentPurple,
                                        activeTrackColor = colors.accentPurple
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("100", fontSize = 11.sp, color = colors.secondaryText)
                            }

                            // Quick count preset chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Hızlı Seçim:",
                                    fontSize = 11.sp,
                                    color = colors.secondaryText,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                listOf(5, 10, 20, 30, 50, 75, 100).forEach { preset ->
                                    val isPreset = wordCount == preset
                                    Surface(
                                        color = if (isPreset) colors.accentPurple.copy(alpha = 0.2f) else colors.textBackground,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPreset) colors.accentPurple else colors.cardBorderSubtle
                                        ),
                                        modifier = Modifier.clickable { wordCount = preset }
                                    ) {
                                        Text(
                                            text = "$preset",
                                            fontSize = 11.sp,
                                            fontWeight = if (isPreset) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isPreset) colors.accentPurple else colors.primaryText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.cardBorderSubtle)

                            // Optional Theme / Topic & Target Deck
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Konu / Tema (İsteğe bağlı):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText
                                )
                                OutlinedTextField(
                                    value = topicHint,
                                    onValueChange = { topicHint = it },
                                    placeholder = {
                                        Text(
                                            "Örn: Seyahat, İş Hayatı, Mutfak, Akademik (Boş bırakılırsa genel)",
                                            fontSize = 12.sp
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Eklenecek Deste / Kategori:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = targetDeckName,
                                        onValueChange = {
                                            targetDeckName = it
                                            hasCustomDeckName = true
                                        },
                                        placeholder = { Text("Deste adı (örn: A1 Seviyesi)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box {
                                        OutlinedButton(onClick = { showTargetDeckMenu = true }) {
                                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Mevcut Desteler", fontSize = 12.sp)
                                        }
                                        DropdownMenu(
                                            expanded = showTargetDeckMenu,
                                            onDismissRequest = { showTargetDeckMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("${selectedLevel.rawValue} Seviyesi (Otomatik)") },
                                                onClick = {
                                                    targetDeckName = "${selectedLevel.rawValue} Seviyesi"
                                                    hasCustomDeckName = false
                                                    showTargetDeckMenu = false
                                                }
                                            )
                                            HorizontalDivider()
                                            store.allDeckNames.forEach { d ->
                                                DropdownMenuItem(
                                                    text = { Text(d) },
                                                    onClick = {
                                                        targetDeckName = d
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
                    }

                    // 3. Error / Status / Results
                    errorMessage?.let { err ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.vividRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.vividRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = colors.vividRed
                            )
                        }
                    }

                    if (newlyAddedWords.isNotEmpty()) {
                        GroupBoxCard(
                            title = "Kelime Haznesine Eklenen Yeni Kelimeler (${newlyAddedWords.size} adet)",
                            icon = Icons.Default.CheckCircle,
                            iconTint = colors.vividGreen
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Aşağıdaki kelimeler listenize eklendi:",
                                    fontSize = 11.sp,
                                    color = colors.secondaryText
                                )
                                newlyAddedWords.forEach { item ->
                                    val art = Artikel.parse(item.artikel) ?: Artikel.DER
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .width(44.dp)
                                                .background(art.badgeBackground, RoundedCornerShape(5.dp))
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = art.rawValue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = art.color
                                            )
                                        }
                                        Text(
                                            text = item.word,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primaryText
                                        )
                                        Text("—", color = colors.secondaryText)
                                        Text(
                                            text = item.meaning,
                                            fontSize = 13.sp,
                                            color = colors.secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.cardBorderSubtle)

                // Bottom Action Bar
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.controlBackground)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isGenerating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = progressMessage,
                                fontSize = 12.sp,
                                color = colors.secondaryText
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
                            border = BorderStroke(1.dp, colors.cardBorderSubtle),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Kapat", fontSize = 12.sp, color = colors.primaryText)
                        }

                        Button(
                            onClick = { generateAndAddWords() },
                            enabled = !isGenerating && authManager.isAuthorized,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPurple,
                                contentColor = Color.White,
                                disabledContainerColor = colors.textBackground,
                                disabledContentColor = colors.secondaryText.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedLevel.rawValue} Seviyesinde $wordCount Kelime Üret ve Ekle",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
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
}
