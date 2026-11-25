package com.artikelmeister.android.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.artikelmeister.android.GeminiAuthMode
import com.artikelmeister.android.GeminiScopeException
import com.artikelmeister.android.GeminiVocabularyGenerator
import com.artikelmeister.android.GeneratedWordItem
import com.artikelmeister.android.SyncErrors
import com.artikelmeister.android.runCatchingCancellable
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    var selectedLevel by rememberSaveable { mutableStateOf(CEFRLevel.A1) }
    var wordCount by rememberSaveable { mutableStateOf(20) }
    var topicHint by rememberSaveable { mutableStateOf("") }
    var includeArticleless by rememberSaveable { mutableStateOf(false) }
    var targetDeckName by rememberSaveable { mutableStateOf("A1 Seviyesi") }
    var hasCustomDeckName by rememberSaveable { mutableStateOf(false) }
    var showAuthConfig by rememberSaveable { mutableStateOf(!authManager.isAuthorized) }
    var showAdvancedApiKey by rememberSaveable { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var generationError by remember { mutableStateOf<String?>(null) }
    var googleSignInError by remember { mutableStateOf<String?>(null) }
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    var generationCompleted by remember { mutableStateOf(false) }
    var newlyAddedWords by remember { mutableStateOf<List<GeneratedWordItem>>(emptyList()) }
    var outcomeScrollOffset by remember { mutableStateOf<Int?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleSignInError = null
        store.completeGoogleSignIn(result.data, authManager, silentSync = true) { error ->
            if (error == null) showAuthConfig = false else googleSignInError = error
        }
    }

    var showModelPicker by remember { mutableStateOf(false) }
    var showTargetDeckMenu by remember { mutableStateOf(false) }
    val targetDeckSummary = targetDeckName.trim().ifEmpty { "${selectedLevel.rawValue} Seviyesi" }

    LaunchedEffect(Unit) {
        if (authManager.isAuthorized) {
            runCatchingCancellable { authManager.verifyCredentials() }
        }
    }

    fun generateAndAddWords() {
        if (isGenerating) return
        isGenerating = true
        showModelPicker = false
        showTargetDeckMenu = false
        scope.launch {
            generationError = null
            generationCompleted = false
            newlyAddedWords = emptyList()
            outcomeScrollOffset = null
            progressMessage = "Gemini bağlantısı kuruluyor..."

            try {
                val generated = GeminiVocabularyGenerator.generateWords(
                    level = selectedLevel,
                    targetCount = wordCount,
                    topicHint = topicHint,
                    includeArticleless = includeArticleless,
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
                val message = SyncErrors.userMessage(e, "Kelime üretilirken bir hata oluştu.")
                if (message == null) throw e
                generationError = message
                // Missing Gemini scopes: reveal the "Google Hesabını Yeniden Bağla" re-consent button.
                if (e is GeminiScopeException) showAuthConfig = true
            } finally {
                isGenerating = false
                progressMessage = ""
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isGenerating) {
                Toast.makeText(context, "Üretim tamamlanana kadar pencere kapatılamaz.", Toast.LENGTH_SHORT).show()
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
                shape = RoundedCornerShape(18.dp),
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
                                .padding(horizontal = 18.dp, vertical = if (compactHeight) 2.dp else 10.dp)
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                if (!compactHeight) {
                                    Text(
                                        text = "Kendi Gemini kullanım hakkınızla A1–C1 seviyesinde, listenizde olmayan yeni kelimeler üretin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                            IconButton(
                                onClick = { if (!isGenerating) onDismiss() },
                                enabled = !isGenerating
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat"
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
                        // 1. Gemini Authorization Box
                        GroupBoxCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val authorizationStatus = if (authManager.isAuthorized) {
                                    "Gemini Yetkilendirmesi Aktif"
                                } else {
                                    "Gemini Yetkilendirmesi Gerekli"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            enabled = !isGenerating,
                                            role = Role.Button,
                                            onClickLabel = if (showAuthConfig) "Yetkilendirme ayarlarını gizle" else "Yetkilendirme ayarlarını göster"
                                        ) {
                                            showAuthConfig = !showAuthConfig
                                            if (!showAuthConfig) showModelPicker = false
                                        }
                                        .minimumInteractiveComponentSize()
                                        .semantics { stateDescription = if (showAuthConfig) "Açık" else "Kapalı" }
                                ) {
                                    Icon(
                                        imageVector = if (authManager.isAuthorized) Icons.Default.VerifiedUser else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (authManager.isAuthorized) colors.success else colors.accentOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = if (authManager.isAuthorized && !showAuthConfig) {
                                            "$authorizationStatus • ${authManager.authMode.title} • ${authManager.selectedModel}"
                                        } else {
                                            authorizationStatus
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = colors.secondaryText,
                                        modifier = Modifier.rotate(if (showAuthConfig) 180f else 0f)
                                    )
                                }

                                AnimatedVisibility(visible = showAuthConfig) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        HorizontalDivider(color = colors.cardBorderSubtle)

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "Model",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.primaryText
                                            )

                                            Box {
                                                Surface(
                                                    color = colors.textBackground,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                    modifier = Modifier
                                                        .clickable(
                                                            enabled = !isGenerating,
                                                            role = Role.Button,
                                                            onClickLabel = "Gemini modelini seç"
                                                        ) { showModelPicker = true }
                                                        .minimumInteractiveComponentSize()
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                    ) {
                                                        Text(
                                                            text = authManager.selectedModel,
                                                            style = MaterialTheme.typography.labelSmall,
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
                                                    expanded = showModelPicker && !isGenerating,
                                                    onDismissRequest = { showModelPicker = false }
                                                ) {
                                                    authManager.availableModels.forEach { model ->
                                                        val isSelected = authManager.selectedModel == model
                                                        DropdownMenuItem(
                                                            enabled = !isGenerating,
                                                            modifier = Modifier.semantics { selected = isSelected },
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

                                        // Google sign-in
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Button(
                                                onClick = {
                                                    googleSignInError = null
                                                    googleSignInLauncher.launch(authManager.getGoogleSignInIntent(context))
                                                },
                                                enabled = !isGenerating && !authManager.isAuthenticating,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.accentBlue,
                                                    contentColor = colors.onBlue
                                                )
                                            ) {
                                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (authManager.isAuthorized && authManager.authMode == GeminiAuthMode.GOOGLE_OAUTH) "Google Hesabını Yeniden Bağla" else "Google Hesabı ile Giriş Yap (Tek Tık)",
                                                    style = MaterialTheme.typography.labelSmall,
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
                                                    Text("Yetkilendiriliyor...", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                                                }
                                            }

                                            TextButton(
                                                onClick = { showAdvancedApiKey = !showAdvancedApiKey },
                                                enabled = !isGenerating
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (showAdvancedApiKey) "Gelişmiş Seçenekleri Gizle" else "Gelişmiş: API Anahtarı ile Doğrula",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        googleSignInError?.let { message ->
                                            AIGeneratorErrorMessage(message)
                                        }

                                        // API key configuration
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
                                                    text = "Google hesabınızla Google AI Studio üzerinden ücretsiz Gemini API anahtarınızı alıp aşağıya yapıştırabilirsiniz.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.secondaryText
                                                )

                                                val validationError = apiKeyError
                                                OutlinedTextField(
                                                    value = authManager.apiKey,
                                                    onValueChange = {
                                                        authManager.apiKey = it
                                                        apiKeyError = null
                                                    },
                                                    enabled = !isGenerating,
                                                    label = { Text("Gemini API Anahtarı") },
                                                    placeholder = { Text("AIza...") },
                                                    isError = validationError != null,
                                                    supportingText = if (validationError != null) {
                                                        {
                                                            Text(
                                                                text = validationError,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                                            )
                                                        }
                                                    } else null,
                                                    visualTransformation = PasswordVisualTransformation(),
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .semantics { validationError?.let { error(it) } }
                                                )

                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { authManager.openGoogleAIStudioInBrowser(context) },
                                                        enabled = !isGenerating
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Google AI Studio'dan Al", style = MaterialTheme.typography.labelSmall)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                runCatchingCancellable {
                                                                    apiKeyError = null
                                                                    authManager.authMode = GeminiAuthMode.API_KEY
                                                                    authManager.verifyCredentials()
                                                                    showAuthConfig = false
                                                                }.onFailure { err ->
                                                                    apiKeyError = SyncErrors.userMessage(err, "Yetkilendirme doğrulanamadı.")
                                                                }
                                                            }
                                                        },
                                                        enabled = !isGenerating && authManager.apiKey.trim().isNotEmpty(),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colors.accentBlue,
                                                            contentColor = colors.onBlue
                                                        )
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Doğrula & Kaydet", style = MaterialTheme.typography.labelSmall)
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
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.secondaryText,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (authManager.isAuthorized) {
                                                    TextButton(
                                                        onClick = { authManager.clearAuthorization() },
                                                        enabled = !isGenerating
                                                    ) {
                                                        Text("Yetkiyi Kaldır", style = MaterialTheme.typography.labelSmall, color = colors.vividRed)
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
                                    text = "Almanca Dil Seviyesi (CEFR):",
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
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) colors.purpleBadgeBackground
                                                    else colors.textBackground
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) colors.accentPurple else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .selectable(
                                                    selected = isSelected,
                                                    enabled = !isGenerating,
                                                    role = Role.RadioButton
                                                ) {
                                                    selectedLevel = lvl
                                                    if (!hasCustomDeckName) {
                                                        targetDeckName = "${lvl.rawValue} Seviyesi"
                                                    }
                                                }
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Text(
                                                text = lvl.rawValue,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.primaryText,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = selectedLevel.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )

                                HorizontalDivider(color = colors.cardBorderSubtle)

                                Text(
                                    text = "Üretilecek Kelime Sayısı (1 – 100):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { wordCount = (wordCount - 1).coerceAtLeast(1) },
                                        enabled = !isGenerating && wordCount > 1
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Kelime sayısını azalt")
                                    }
                                    Text(
                                        text = "$wordCount kelime",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.accentPurple,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { wordCount = (wordCount + 1).coerceAtMost(100) },
                                        enabled = !isGenerating && wordCount < 100
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Kelime sayısını artır")
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("1", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
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
                                                contentDescription = "Kelime sayısı"
                                                stateDescription = "$wordCount kelime"
                                            }
                                    )
                                    Text("100", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                                }

                                // Quick count preset chips
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Hızlı Seçim:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    listOf(5, 10, 20, 30, 50, 75, 100).forEach { preset ->
                                        FilterChip(
                                            selected = wordCount == preset,
                                            enabled = !isGenerating,
                                            onClick = { wordCount = preset },
                                            label = { Text("$preset kelime") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.purpleBadgeBackground,
                                                selectedLabelColor = colors.primaryText
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = !isGenerating,
                                                selected = wordCount == preset,
                                                borderColor = MaterialTheme.colorScheme.outline,
                                                selectedBorderColor = colors.accentPurple
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
                                            text = "Artikelsiz kelimeleri de üret",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primaryText
                                        )
                                        Text(
                                            text = "Fiiller, sıfatlar ve kalıp ifadeler '-' olarak eklenir; çalışırken Türkçe anlamı yazılır.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.secondaryText
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                            contentDescription = "Artikelsiz kelimeleri de üret"
                                        }
                                    )
                                }

                                HorizontalDivider(color = colors.cardBorderSubtle)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = topicHint,
                                        onValueChange = { topicHint = it },
                                        enabled = !isGenerating,
                                        label = { Text("Konu / Tema (İsteğe bağlı)") },
                                        placeholder = { Text("Örn: Seyahat, İş Hayatı, Mutfak, Akademik") },
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
                                        label = { Text("Eklenecek Deste / Kategori") },
                                        placeholder = { Text("Deste adı (örn: A1 Seviyesi)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Box {
                                        OutlinedButton(
                                            onClick = { showTargetDeckMenu = true },
                                            enabled = !isGenerating
                                        ) {
                                            val usesExistingDeck = hasCustomDeckName && targetDeckSummary in store.allDeckNames
                                            Icon(
                                                if (usesExistingDeck) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Mevcut Desteler", style = MaterialTheme.typography.labelSmall)
                                        }
                                        DropdownMenu(
                                            expanded = showTargetDeckMenu && !isGenerating,
                                            onDismissRequest = { showTargetDeckMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("${selectedLevel.rawValue} Seviyesi (Otomatik)") },
                                                leadingIcon = {
                                                    Icon(
                                                        if (!hasCustomDeckName) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                        contentDescription = null
                                                    )
                                                },
                                                enabled = !isGenerating,
                                                modifier = Modifier.semantics { selected = !hasCustomDeckName },
                                                onClick = {
                                                    targetDeckName = "${selectedLevel.rawValue} Seviyesi"
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
                                title = "Kelime Havuzuna Eklenen Yeni Kelimeler (${newlyAddedWords.size} adet)",
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
                            generationError != null -> "Kelime üretilirken bir hata oluştu."
                            generationCompleted && newlyAddedWords.isEmpty() -> "Yeni kelime eklenmedi"
                            generationCompleted -> "${newlyAddedWords.size} kelime eklendi"
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
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Kapat", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    generateAndAddWords()
                                },
                                enabled = !isGenerating && authManager.isAuthorized,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentPurple,
                                    contentColor = colors.onPurple
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Üret ve ekle",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "${selectedLevel.rawValue} • $wordCount kelime • $targetDeckSummary",
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
