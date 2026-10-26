package com.artikelmeister.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.GoogleDriveSyncService
import com.artikelmeister.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

private data class AndroidLogoDropletSpec(
    val normX: Float,
    val startNormY: Float,
    val endNormY: Float,
    val widthFactor: Float,
    val heightFactor: Float,
    val isTeardrop: Boolean,
    val speedMultiplier: Float,
    val phaseOffset: Float
)

private val logoDropletSpecs = listOf(
    // 1. Middle-left main teardrop below stem (canonical logo position: x=0.3922, y=0.8073)
    AndroidLogoDropletSpec(
        normX = 0.3922f, startNormY = 0.7420f, endNormY = 0.8950f,
        widthFactor = 0.045f, heightFactor = 0.064f, isTeardrop = true,
        speedMultiplier = 1.0f, phaseOffset = 0.00f
    ),
    // 2. Center-right small droplet below long stem (canonical logo position: x=0.6310, y=0.8465)
    AndroidLogoDropletSpec(
        normX = 0.6310f, startNormY = 0.8120f, endNormY = 0.9150f,
        widthFactor = 0.022f, heightFactor = 0.022f, isTeardrop = false,
        speedMultiplier = 1.0f, phaseOffset = 0.32f
    ),
    // 3. Right small droplet below stem (canonical logo position: x=0.6980, y=0.8019)
    AndroidLogoDropletSpec(
        normX = 0.6980f, startNormY = 0.7620f, endNormY = 0.8850f,
        widthFactor = 0.024f, heightFactor = 0.024f, isTeardrop = false,
        speedMultiplier = 1.0f, phaseOffset = 0.61f
    ),
    // 4. Far-right small droplet below cerebellum (canonical logo position: x=0.7456, y=0.7384)
    AndroidLogoDropletSpec(
        normX = 0.7456f, startNormY = 0.6920f, endNormY = 0.8350f,
        widthFactor = 0.022f, heightFactor = 0.022f, isTeardrop = false,
        speedMultiplier = 1.0f, phaseOffset = 0.83f
    ),
    // 5. Left bulb subtle secondary drip (below the large left hanging bulb at x=0.2650)
    AndroidLogoDropletSpec(
        normX = 0.2650f, startNormY = 0.7180f, endNormY = 0.8550f,
        widthFactor = 0.026f, heightFactor = 0.036f, isTeardrop = true,
        speedMultiplier = 1.0f, phaseOffset = 0.47f
    )
)

/**
 * 1:1 Port of BloodyBrainLogoView from SplashView.swift & generate_icon.swift
 */
@Composable
fun BloodyBrainLogoView(
    size: Dp = 140.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!animated) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bloody_brain_icon),
                contentDescription = "ArtikelMeister Logo",
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bloody_brain_transition")

    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val masterDripCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "masterDripCycle"
    )

    val auraScale = 0.96f + (pulseFraction * 0.09f)
    val badgeScale = 0.985f + (pulseFraction * 0.028f)
    val auraAlpha = 0.28f + (pulseFraction * 0.24f)
    val neonGlowAlpha = 0.14f + (pulseFraction * 0.52f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // 1. Breathing neon crimson outer aura behind the dark squircle
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(auraScale)
        ) {
            val s = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(1.0f, 0.16f, 0.18f).copy(alpha = auraAlpha),
                        Color(0.75f, 0.04f, 0.10f).copy(alpha = 0.10f + 0.10f * pulseFraction),
                        Color.Transparent
                    ),
                    center = center,
                    radius = s * 0.66f
                ),
                radius = s * 0.66f,
                center = center
            )
        }

        // 2. Exact dark-slate squircle & neon brain base + pulsing neon overlay + live falling droplets
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .scale(badgeScale)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bloody_brain_squircle_nodrops),
                contentDescription = "ArtikelMeister Logo",
                modifier = Modifier.fillMaxSize()
            )

            Image(
                painter = painterResource(id = R.drawable.bloody_brain_neon_nodrops),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(neonGlowAlpha)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height

                for (drop in logoDropletSpecs) {
                    val phase = (masterDripCycle * drop.speedMultiplier + drop.phaseOffset) % 1.0f

                    val fallProgress: Float
                    val dropScale: Float
                    val dropAlpha: Float

                    if (phase < 0.20f) {
                        val p = phase / 0.20f
                        fallProgress = p * 0.06f
                        dropScale = 0.45f + 0.55f * p
                        dropAlpha = 0.55f + 0.45f * p
                    } else if (phase < 0.86f) {
                        val p = (phase - 0.20f) / 0.66f
                        fallProgress = 0.06f + 0.94f * p.pow(1.42f)
                        dropScale = 1.0f
                        dropAlpha = 1.0f
                    } else {
                        val p = (phase - 0.86f) / 0.14f
                        fallProgress = 1.0f + 0.08f * p
                        dropScale = 1.0f - 0.25f * p
                        dropAlpha = 1.0f - p
                    }

                    val cx = w * drop.normX
                    val cy = h * (drop.startNormY + (drop.endNormY - drop.startNormY) * fallProgress)
                    val dw = w * drop.widthFactor * dropScale
                    val dh = h * drop.heightFactor * dropScale

                    // Soft neon red halo around droplet
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(1.0f, 0.18f, 0.18f).copy(alpha = (dropAlpha * 0.45f).coerceIn(0f, 1f)),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = maxOf(dw, dh) * 0.9f
                        ),
                        radius = maxOf(dw, dh) * 0.9f,
                        center = Offset(cx, cy),
                        blendMode = BlendMode.Plus
                    )

                    if (drop.isTeardrop) {
                        val topY = cy - dh * 0.5f
                        val path = Path().apply {
                            moveTo(cx, topY)
                            cubicTo(
                                cx - dw * 0.62f, topY + dh * 0.58f,
                                cx - dw * 0.38f, topY + dh,
                                cx, topY + dh
                            )
                            cubicTo(
                                cx + dw * 0.38f, topY + dh,
                                cx + dw * 0.62f, topY + dh * 0.58f,
                                cx, topY
                            )
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(1.0f, 0.176f, 0.176f).copy(alpha = dropAlpha.coerceIn(0f, 1f))
                        )
                    } else {
                        drawOval(
                            color = Color(1.0f, 0.176f, 0.176f).copy(alpha = dropAlpha.coerceIn(0f, 1f)),
                            topLeft = Offset(cx - dw * 0.5f, cy - dh * 0.5f),
                            size = Size(dw, dh)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Port of SplashIntroView from SplashView.swift
 */
@Composable
fun SplashIntroView(onFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }

    val appearScale by animateFloatAsState(
        targetValue = if (started) 1.0f else 0.74f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "appearScale"
    )
    val appearOpacity by animateFloatAsState(
        targetValue = if (started) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "appearOpacity"
    )
    val progressValue by animateFloatAsState(
        targetValue = if (started) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
        label = "progressValue"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(2350L)
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0.08f, 0.14f, 0.17f),
                        Color(0.03f, 0.06f, 0.11f),
                        Color(0.01f, 0.02f, 0.05f)
                    )
                )
            )
            .clickable { onFinished() }
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            BloodyBrainLogoView(
                size = 196.dp,
                animated = true,
                modifier = Modifier.scale(appearScale)
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "ARTIKELMEISTER",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(1.0f, 0.38f, 0.40f),
                            Color(1.0f, 0.16f, 0.18f)
                        )
                    )
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Almanca Artikel & Nöro-Hafıza Çalışma Sistemi",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.72f * appearOpacity)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Artikel.entries.forEach { art ->
                    Text(
                        text = art.rawValue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = art.color,
                        modifier = Modifier
                            .background(art.color.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Neon crimson loading progress bar
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressValue)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(1.0f, 0.18f, 0.18f),
                                    Color(0.78f, 0.04f, 0.10f)
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Geçmek için tıklayın",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.38f)
            )
        }
    }
}

enum class AppTab(val rawValue: String) {
    STUDY("Çalış"),
    WORDS("Kelime Havuzu"),
    STATS("İstatistikler"),
    SETTINGS("Ayarlar");

    val icon: ImageVector
        get() = when (this) {
            STUDY -> Icons.Default.Style
            WORDS -> Icons.Default.ViewList
            STATS -> Icons.Default.BarChart
            SETTINGS -> Icons.Default.Settings
        }
}

@Composable
fun RootContainerView(
    store: DeckStore,
    authManager: GeminiAuthManager
) {
    var showSplash by remember { mutableStateOf(true) }

    val shouldShowOnboarding = !authManager.hasCompletedOnboarding && !authManager.isGoogleAccountConnected

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showSplash -> {
                SplashIntroView(onFinished = { showSplash = false })
            }
            shouldShowOnboarding -> {
                OnboardingSetupScreen(
                    store = store,
                    authManager = authManager,
                    onComplete = {
                        authManager.completeOnboarding()
                    }
                )
            }
            else -> {
                MainContentView(
                    store = store,
                    authManager = authManager
                )
            }
        }
    }
}

@Composable
fun MainContentView(
    store: DeckStore,
    authManager: GeminiAuthManager
) {
    val colors = LocalAppColors.current
    var selectedTab by remember { mutableStateOf(AppTab.STUDY) }
    var showNewDeckSheet by remember { mutableStateOf(false) }
    var deckToRename by remember { mutableStateOf<String?>(null) }
    var showManualSignInModal by remember { mutableStateOf(false) }

    // Automatically sync with Google Drive on startup or whenever Google account connects
    LaunchedEffect(authManager.isGoogleAccountConnected) {
        if (authManager.isGoogleAccountConnected) {
            store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
    ) {
        val isWideScreen = maxWidth >= 700.dp

        if (isWideScreen) {
            // Tablet / Landscape / Desktop: Exact macOS Side-by-Side Split View
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                SidebarContent(
                    store = store,
                    authManager = authManager,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    onShowNewDeck = { showNewDeckSheet = true },
                    onRenameDeck = { deckToRename = it },
                    onShowSignInDialog = { showManualSignInModal = true },
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                )

                VerticalDivider(color = colors.cardBorderSubtle)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ActiveTabContent(
                        selectedTab = selectedTab,
                        store = store,
                        authManager = authManager,
                        onOpenDrawer = null
                    )
                }
            }
        } else {
            // Phone Portrait: Collapsible macOS Sidebar Drawer + Bottom Navigation Bar
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = colors.sidebarBackground,
                        modifier = Modifier.width(265.dp)
                    ) {
                        SidebarContent(
                            store = store,
                            authManager = authManager,
                            selectedTab = selectedTab,
                            onSelectTab = {
                                selectedTab = it
                                scope.launch { drawerState.close() }
                            },
                            onShowNewDeck = {
                                scope.launch { drawerState.close() }
                                showNewDeckSheet = true
                            },
                            onRenameDeck = {
                                scope.launch { drawerState.close() }
                                deckToRename = it
                            },
                            onShowSignInDialog = {
                                scope.launch { drawerState.close() }
                                showManualSignInModal = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = colors.windowBackground,
                    topBar = {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .background(colors.controlBackground)
                        )
                    },
                    bottomBar = {
                        Column(modifier = Modifier.background(colors.controlBackground)) {
                            HorizontalDivider(color = colors.cardBorderSubtle)
                            NavigationBar(
                                containerColor = colors.controlBackground,
                                tonalElevation = 0.dp
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.rawValue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                if (tab == AppTab.STUDY && store.studyQueue.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "${store.studyQueue.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) colors.accentBlue else colors.secondaryText
                                                    )
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = tab.rawValue,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = colors.accentBlue,
                                            selectedTextColor = colors.accentBlue,
                                            indicatorColor = colors.accentBlue.copy(alpha = 0.15f),
                                            unselectedIconColor = colors.secondaryText,
                                            unselectedTextColor = colors.secondaryText
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ActiveTabContent(
                            selectedTab = selectedTab,
                            store = store,
                            authManager = authManager,
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    }
                }
            }
        }
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

    deckToRename?.let { targetDeck ->
        RenameDeckDialog(
            store = store,
            oldName = targetDeck,
            onDismiss = { deckToRename = null },
            onRenamed = { deckToRename = null }
        )
    }

    if (showManualSignInModal) {
        OnboardingSetupScreen(
            store = store,
            authManager = authManager,
            onComplete = {
                showManualSignInModal = false
                authManager.completeOnboarding()
            }
        )
    }
}

@Composable
private fun ActiveTabContent(
    selectedTab: AppTab,
    store: DeckStore,
    authManager: GeminiAuthManager,
    onOpenDrawer: (() -> Unit)?
) {
    when (selectedTab) {
        AppTab.STUDY -> StudyView(store = store, authManager = authManager, onOpenDrawer = onOpenDrawer)
        AppTab.WORDS -> WordListView(store = store, authManager = authManager, onOpenDrawer = onOpenDrawer)
        AppTab.STATS -> StatsView(store = store, onOpenDrawer = onOpenDrawer)
        AppTab.SETTINGS -> SettingsView(store = store, authManager = authManager, onOpenDrawer = onOpenDrawer)
    }
}

/**
 * 1:1 Port of the Left Sidebar in MainContentView (SplashView.swift)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarContent(
    store: DeckStore,
    authManager: GeminiAuthManager,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onShowNewDeck: () -> Unit,
    onRenameDeck: (String) -> Unit,
    onShowSignInDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .background(colors.sidebarBackground)
    ) {
        // Sidebar Bloody Brain Emblem Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BloodyBrainLogoView(size = 40.dp, animated = false)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "ArtikelMeister",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Text(
                    text = "Nöro-Hafıza",
                    fontSize = 11.sp,
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }
        }

        HorizontalDivider(color = colors.cardBorderSubtle)

        // Scrollable Menu + Decks List
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Main Navigation Tabs
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "MENÜ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )

                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.accentBlue else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.rawValue,
                            tint = if (isSelected) Color.White else colors.accentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = tab.rawValue,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.primaryText,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (tab == AppTab.STUDY && store.studyQueue.isNotEmpty()) {
                            Text(
                                text = "${store.studyQueue.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else colors.accentBlue,
                                modifier = Modifier
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.24f)
                                        else colors.accentBlue.copy(alpha = 0.18f),
                                        CircleShape
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        } else if (tab == AppTab.WORDS) {
                            Text(
                                text = "${store.cards.size}",
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else colors.secondaryText
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = colors.cardBorderSubtle,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // Section 2: Decks List + "+ Yeni Deste Ekle" Button Right Underneath
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DESTELER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${store.allDeckNames.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )
                }

                // All Decks Row
                val allSelected = store.selectedStudyDeck == null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (allSelected) colors.accentBlue.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .clickable { store.selectedStudyDeck = null }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (allSelected) colors.accentBlue else colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tüm Desteler",
                        fontSize = 12.sp,
                        fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (allSelected) colors.accentBlue else colors.primaryText,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${store.cards.size}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.secondaryText
                    )
                }

                // Individual Decks with long-press context menu
                store.allDeckNames.forEach { deck ->
                    val isDeckSelected = store.selectedStudyDeck == deck
                    var showContextMenu by remember { mutableStateOf(false) }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isDeckSelected) colors.accentBlue.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = { store.selectedStudyDeck = deck },
                                    onLongClick = { showContextMenu = true }
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeckSelected) Icons.Default.Folder else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (isDeckSelected) colors.accentBlue else colors.secondaryText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = deck,
                                fontSize = 12.sp,
                                fontWeight = if (isDeckSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDeckSelected) colors.accentBlue else colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${store.cardCount(deck)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.secondaryText
                            )
                        }

                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Deste Adını Değiştir...") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showContextMenu = false
                                    onRenameDeck(deck)
                                }
                            )
                            if (deck != "Genel") {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Desteyi Sil (Kelimeleri 'Genel'e Taşı)",
                                            color = colors.vividRed
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = colors.vividRed
                                        )
                                    },
                                    onClick = {
                                        showContextMenu = false
                                        store.deleteDeck(name = deck, deleteWords = false)
                                    }
                                )
                            }
                        }
                    }
                }

                // Prominent "+ Yeni Deste Ekle" button right below the decks list
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentBlue.copy(alpha = 0.12f))
                        .border(1.dp, colors.accentBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { onShowNewDeck() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = colors.accentBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yeni Deste Ekle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentBlue,
                        maxLines = 1
                    )
                }
            }
        }

        HorizontalDivider(color = colors.cardBorderSubtle)

        // Universal Google Profile & Cloud Sync Status Card in Drawer
        UniversalProfileSidebarCard(
            store = store,
            authManager = authManager,
            onShowSignInDialog = onShowSignInDialog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // Animated Dark / Light Theme Toggle towards the bottom of the drawer
        AnimatedThemeToggleCard(
            isDarkMode = store.settings.isDarkMode,
            onToggleTheme = {
                store.updateSettings { it.copy(isDarkMode = !it.isDarkMode) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        HorizontalDivider(color = colors.cardBorderSubtle)

        // Bottom Legend
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Artikel.entries.forEach { art ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(art.color, CircleShape)
                    )
                    Text(
                        text = art.rawValue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.secondaryText
                    )
                }
            }
            Text(
                text = if (authManager.isGoogleAccountConnected) "Google Drive & Yerel Depolama Aktif" else "Yerel Depolama Aktif",
                fontSize = 11.sp,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun UniversalProfileSidebarCard(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onShowSignInDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val isConnected = authManager.isGoogleAccountConnected

    Surface(
        color = colors.controlBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isConnected) colors.vividGreen.copy(alpha = 0.35f) else colors.accentBlue.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (isConnected) {
                    scope.launch {
                        store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = false)
                    }
                } else {
                    onShowSignInDialog()
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (isConnected) colors.vividGreen.copy(alpha = 0.14f)
                        else colors.accentBlue.copy(alpha = 0.14f),
                        CircleShape
                    )
            ) {
                if (store.isSyncingCloud) {
                    CircularProgressIndicator(
                        color = colors.accentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (isConnected) colors.vividGreen else colors.accentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) {
                        authManager.userEmail.ifBlank { authManager.userName.ifBlank { "Google Hesabı Bağlı" } }
                    } else {
                        "Google ile Giriş Yap"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        store.isSyncingCloud -> "Drive ile eşitleniyor..."
                        isConnected -> "Drive Eşitleme & AI Aktif"
                        else -> "Bulut Eşitleme & AI için dokun"
                    },
                    fontSize = 10.sp,
                    color = if (isConnected) colors.vividGreen else colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isConnected && !store.isSyncingCloud) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Şimdi Eşitle",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingSetupScreen(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onComplete: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            errorMessage = null
            runCatching {
                authManager.handleGoogleSignInResult(result.data, context)
                store.syncWithGoogleDrive(authManager = authManager, isSilentAutoSync = false)
                onComplete()
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Giriş yapılamadı."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            BloodyBrainLogoView(size = 84.dp, animated = true)

            Text(
                text = "ArtikelMeister",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Almanca Artikel & Hafıza Çalışma Sistemi",
                fontSize = 13.sp,
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            )

            Surface(
                color = colors.controlBackground,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.cardBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "Kurulum ve Bulut Senkronizasyonu",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText
                    )

                    Text(
                        text = "Google hesabınızla bağlanarak kelimelerinizi ve çalışma serinizi tüm cihazlarınızla eşitleyebilir veya uygulamayı doğrudan çevrimdışı modda kullanabilirsiniz.",
                        fontSize = 12.sp,
                        color = colors.secondaryText,
                        lineHeight = 17.sp
                    )

                    HorizontalDivider(color = colors.cardBorderSubtle)

                    OnboardingFeatureRow(
                        icon = Icons.Default.CloudSync,
                        iconTint = colors.accentBlue,
                        title = "Bulut Senkronizasyonu",
                        desc = "Kelimeleriniz, seriniz (streak) ve öğrenme geçmişiniz tüm cihazlarınız arasında otomatik eşitlenir."
                    )

                    OnboardingFeatureRow(
                        icon = Icons.Default.CheckCircle,
                        iconTint = colors.vividGreen,
                        title = "Kelimeleri Otomatik Birleştirme",
                        desc = "Farklı cihazlardan eklenen kelimeler mükerrer kaydedilmeden birleştirilir ve güncel öğrenme ilerlemeniz korunur."
                    )

                    OnboardingFeatureRow(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = colors.accentPurple,
                        title = "Yapay Zeka ile Kelime Üretimi",
                        desc = "Almanca seviyenize uygun yeni kelimeleri yapay zeka ile anında üretip çalışabilirsiniz."
                    )
                }
            }

            errorMessage?.let { err ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .background(colors.vividRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.vividRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = colors.vividRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = colors.vividRed
                    )
                }
            }

            if (authManager.isAuthenticating || store.isSyncingCloud) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.accentBlue,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (store.isSyncingCloud) {
                            "Verileriniz bulut ile eşitleniyor..."
                        } else {
                            authManager.authStatusMessage ?: "Google ile giriş yapılıyor..."
                        },
                        fontSize = 12.sp,
                        color = colors.secondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Button(
                    onClick = {
                        errorMessage = null
                        googleSignInLauncher.launch(authManager.getGoogleSignInIntent(context))
                    },
                    enabled = !authManager.isAuthenticating && !store.isSyncingCloud,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Hesabı ile Giriş Yap & Eşitle",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onComplete,
                    enabled = !authManager.isAuthenticating && !store.isSyncingCloud,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.cardBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Çevrimdışı Modda Devam Et",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(iconTint.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun AnimatedThemeToggleCard(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    val iconRotation by animateFloatAsState(
        targetValue = if (isDarkMode) 0f else 180f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "themeIconRotation"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (isDarkMode) 24.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "themeThumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (isDarkMode) colors.accentPurple.copy(alpha = 0.25f) else colors.accentOrange.copy(alpha = 0.22f),
        animationSpec = tween(280),
        label = "themeTrackColor"
    )

    val trackBorderColor by animateColorAsState(
        targetValue = if (isDarkMode) colors.accentPurple.copy(alpha = 0.55f) else colors.accentOrange.copy(alpha = 0.55f),
        animationSpec = tween(280),
        label = "themeTrackBorderColor"
    )

    val activeAccentColor by animateColorAsState(
        targetValue = if (isDarkMode) colors.accentPurple else colors.accentOrange,
        animationSpec = tween(280),
        label = "themeActiveAccentColor"
    )

    Surface(
        color = colors.controlBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.cardBorderSubtle),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleTheme() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Left Icon Badge with rotation animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(activeAccentColor.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = if (isDarkMode) "Karanlık Mod" else "Aydınlık Mod",
                    tint = activeAccentColor,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(iconRotation)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDarkMode) "Karanlık Mod" else "Aydınlık Mod",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Text(
                    text = if (isDarkMode) "Aydınlık temaya geç" else "Karanlık temaya geç",
                    fontSize = 10.sp,
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }

            // Custom Animated Toggle Pill
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(50.dp)
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .border(1.dp, trackBorderColor, CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(activeAccentColor)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(13.dp)
                            .rotate(iconRotation)
                    )
                }
            }
        }
    }
}
