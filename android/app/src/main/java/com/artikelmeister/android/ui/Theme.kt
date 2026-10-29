package com.artikelmeister.android.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.DeckStore

// =====================================================================================
// Color tokens
// =====================================================================================

/**
 * App-wide color tokens. Existing fields are kept (StudyView & others read them);
 * new semantic + article tokens were added with defaults so callers keep compiling.
 *
 * Design note — article vs. feedback colors:
 *  The old palette painted `die` red and `das` green, i.e. exactly the same hues as
 *  "wrong" and "correct" feedback. Articles now use blue / magenta / gold, which are
 *  clearly separated from success-green and error-red in both light and dark themes.
 *  `vividGreen` / `vividRed` remain as aliases of `success` / `error` for old call sites.
 */
@Immutable
data class ArtikelMeisterColors(
    val isDark: Boolean = true,
    val windowBackground: Color,
    val controlBackground: Color,
    val textBackground: Color,
    val sidebarBackground: Color,
    val cardBorderSubtle: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentBlue: Color = Color(0xFF0A84FF),
    val accentPurple: Color = Color(0xFFA855F7),
    val accentOrange: Color = Color(0xFFFF9F0A),
    // Semantic feedback colors
    val success: Color = Color(0xFF30D158),
    val error: Color = Color(0xFFFF453A),
    val warning: Color = Color(0xFFFF9F0A),
    val info: Color = Color(0xFF0A84FF),
    /** Content color for text/icons placed on a filled accent/semantic/article color. */
    val onAccent: Color = Color.White,
    // Grammatical gender (article) colors
    val artikelDer: Color = Color(0xFF4C8DFF),
    val artikelDie: Color = Color(0xFFF062C0),
    val artikelDas: Color = Color(0xFFE8B90E),
    /** Article-less words ("-"), e.g. plurals-only / proper nouns. */
    val artikelNone: Color = Color(0xFFA1A1AA),
    // Legacy aliases (kept so StudyView etc. compile unchanged)
    val vividGreen: Color = success,
    val vividRed: Color = error
)

private val DarkAppColors = ArtikelMeisterColors(
    isDark = true,
    windowBackground = Color(0xFF161618),
    controlBackground = Color(0xFF212124),
    textBackground = Color(0xFF121214),
    sidebarBackground = Color(0xFF1C1C1F),
    cardBorderSubtle = Color(0x22FFFFFF),
    primaryText = Color(0xFFF5F5F7),
    secondaryText = Color(0xFFA3A3AB),
    accentBlue = Color(0xFF0A84FF),
    accentPurple = Color(0xFFA855F7),
    accentOrange = Color(0xFFFF9F0A),
    success = Color(0xFF30D158),
    error = Color(0xFFFF453A),
    warning = Color(0xFFFF9F0A),
    info = Color(0xFF0A84FF),
    artikelDer = Color(0xFF4C8DFF),
    artikelDie = Color(0xFFF062C0),
    artikelDas = Color(0xFFE8B90E),
    artikelNone = Color(0xFFA1A1AA)
)

private val LightAppColors = ArtikelMeisterColors(
    isDark = false,
    windowBackground = Color(0xFFF2F4F8),
    controlBackground = Color(0xFFFFFFFF),
    textBackground = Color(0xFFEFF1F6),
    sidebarBackground = Color(0xFFF7F8FB),
    cardBorderSubtle = Color(0x1C000000),
    primaryText = Color(0xFF15171E),
    secondaryText = Color(0xFF555A68),
    accentBlue = Color(0xFF0066EB),
    accentPurple = Color(0xFF8438FF),
    accentOrange = Color(0xFFC26A00),
    success = Color(0xFF1E8E3E),
    error = Color(0xFFD92D20),
    warning = Color(0xFFC26A00),
    info = Color(0xFF0066EB),
    artikelDer = Color(0xFF1F63E0),
    artikelDie = Color(0xFFC0268F),
    artikelDas = Color(0xFF946C00),
    artikelNone = Color(0xFF6B7280)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** Theme color for an article. Use this instead of `Artikel.color` (hardcoded in Models.kt). */
fun ArtikelMeisterColors.artikelColor(artikel: Artikel?): Color = when (artikel) {
    Artikel.DER -> artikelDer
    Artikel.DIE -> artikelDie
    Artikel.DAS -> artikelDas
    null -> artikelNone // article-less word ('-')
}

@Composable
@ReadOnlyComposable
fun Artikel.themeColor(): Color = LocalAppColors.current.artikelColor(this)

private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF0A84FF),
    secondary = Color(0xFFA855F7),
    tertiary = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A),
    background = Color(0xFF161618),
    surface = Color(0xFF212124),
    surfaceVariant = Color(0xFF2C2C30),
    surfaceContainer = Color(0xFF212124),
    surfaceContainerHigh = Color(0xFF28282C),
    surfaceContainerHighest = Color(0xFF323236),
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF15171E),
    inversePrimary = Color(0xFF0066EB),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onError = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFA3A3AB),
    outline = Color(0x38FFFFFF),
    outlineVariant = Color(0x22FFFFFF)
)

private val LightColorPalette = lightColorScheme(
    primary = Color(0xFF0066EB),
    secondary = Color(0xFF8438FF),
    tertiary = Color(0xFFC26A00),
    error = Color(0xFFD92D20),
    background = Color(0xFFF2F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFF1F6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F7FB),
    surfaceContainerHighest = Color(0xFFEBEDF4),
    inverseSurface = Color(0xFF212124),
    inverseOnSurface = Color(0xFFF5F5F7),
    inversePrimary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onError = Color.White,
    onBackground = Color(0xFF15171E),
    onSurface = Color(0xFF15171E),
    onSurfaceVariant = Color(0xFF555A68),
    outline = Color(0x2E000000),
    outlineVariant = Color(0x1C000000)
)

// =====================================================================================
// Typography (minimum 12sp everywhere)
// =====================================================================================

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp
)

val AppTypography = Typography(
    displayLarge = style(42, 48, FontWeight.Bold, -0.25f),
    displayMedium = style(34, 40, FontWeight.Bold),
    displaySmall = style(32, 38, FontWeight.Bold),
    headlineLarge = style(30, 36, FontWeight.Bold),
    headlineMedium = style(28, 34, FontWeight.Bold),
    headlineSmall = style(22, 28, FontWeight.Bold),
    titleLarge = style(20, 26, FontWeight.Bold),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold, 0.1f),
    bodyLarge = style(15, 22, FontWeight.Normal, 0.15f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.15f),
    bodySmall = style(13, 18, FontWeight.Normal, 0.2f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1f),
    labelMedium = style(13, 18, FontWeight.Medium, 0.2f),
    // M3 default is 11sp — overridden: nothing in this app goes below 12sp.
    // Normal weight: it doubles as the caption style for migrated 9–12sp text.
    labelSmall = style(12, 16, FontWeight.Normal, 0.2f)
)

// =====================================================================================
// Theme mode adapter (System / Light / Dark)
// =====================================================================================

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * ADAPTER — UI-only preferences that are not (yet) part of `AppSettings` in Models.kt.
 *
 * `AppSettings` only has `isDarkMode: Boolean`, so the tri-state theme mode is kept in a
 * private SharedPreferences file. Contract with the legacy flag:
 *  - never chosen (`themeMode == null`) -> follow legacy `AppSettings.isDarkMode`
 *  - LIGHT / DARK -> also written back to `isDarkMode` so cloud sync / macOS stay consistent
 *  - SYSTEM -> follows the OS; `isDarkMode` is left untouched
 * Once `AppSettings` gains a `themeMode` field this object can be deleted.
 */
object UiPreferences {
    private const val PREFS_NAME = "artikelmeister_ui_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SEEN_INTRO = "has_seen_intro_splash"

    private var prefs: SharedPreferences? = null

    /** null = user has never picked a mode on this device (legacy behaviour). */
    var themeMode by mutableStateOf<ThemeMode?>(null)
        private set

    var hasSeenIntro by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        themeMode = p.getString(KEY_THEME_MODE, null)?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrNull()
        }
        hasSeenIntro = p.getBoolean(KEY_SEEN_INTRO, false)
    }

    /** The mode to show as selected in the UI. */
    fun effectiveThemeMode(legacyIsDark: Boolean): ThemeMode =
        themeMode ?: if (legacyIsDark) ThemeMode.DARK else ThemeMode.LIGHT

    fun setThemeMode(mode: ThemeMode, store: DeckStore?) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
        val legacyValue = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> null
        }
        if (store != null && legacyValue != null && store.settings.isDarkMode != legacyValue) {
            store.updateSettings { it.copy(isDarkMode = legacyValue) }
        }
    }

    fun markIntroSeen() {
        if (hasSeenIntro) return
        hasSeenIntro = true
        prefs?.edit()?.putBoolean(KEY_SEEN_INTRO, true)?.apply()
    }
}

/** Resolves the effective dark/light decision for the current theme mode. */
@Composable
fun resolveDarkTheme(legacyIsDark: Boolean): Boolean = when (UiPreferences.themeMode) {
    null -> legacyIsDark
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// =====================================================================================
// Theme
// =====================================================================================

@Composable
private fun animated(target: Color, spec: AnimationSpec<Color>, label: String): Color {
    val value by animateColorAsState(target, spec, label = label)
    return value
}

/**
 * App theme. System bar styling lives in exactly one place: MainActivity (enableEdgeToEdge,
 * keyed on the resolved dark value). Compose paints the bar areas itself.
 */
@Composable
fun ArtikelMeisterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val t = if (darkTheme) DarkAppColors else LightAppColors
    val spec = tween<Color>(durationMillis = 280)

    val success = animated(t.success, spec, "success")
    val error = animated(t.error, spec, "error")
    val appColors = t.copy(
        windowBackground = animated(t.windowBackground, spec, "windowBg"),
        controlBackground = animated(t.controlBackground, spec, "controlBg"),
        textBackground = animated(t.textBackground, spec, "textBg"),
        sidebarBackground = animated(t.sidebarBackground, spec, "sidebarBg"),
        cardBorderSubtle = animated(t.cardBorderSubtle, spec, "border"),
        primaryText = animated(t.primaryText, spec, "primaryText"),
        secondaryText = animated(t.secondaryText, spec, "secondaryText"),
        accentBlue = animated(t.accentBlue, spec, "blue"),
        accentPurple = animated(t.accentPurple, spec, "purple"),
        accentOrange = animated(t.accentOrange, spec, "orange"),
        success = success,
        error = error,
        warning = animated(t.warning, spec, "warning"),
        info = animated(t.info, spec, "info"),
        artikelDer = animated(t.artikelDer, spec, "der"),
        artikelDie = animated(t.artikelDie, spec, "die"),
        artikelDas = animated(t.artikelDas, spec, "das"),
        artikelNone = animated(t.artikelNone, spec, "none"),
        vividGreen = success,
        vividRed = error
    )

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
            typography = AppTypography,
            content = content
        )
    }
}

// =====================================================================================
// Shared components
// =====================================================================================

/**
 * macOS-style GroupBox container used across StatsView, SettingsView, and AIGeneratorSheet
 */
@Composable
fun GroupBoxCard(
    title: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LocalAppColors.current.accentBlue,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp, start = 2.dp)
                    .semantics { heading() }
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )
            }
        }
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                content = content
            )
        }
    }
}

/** Visual tone of a [StateMessageView]. */
enum class StateTone { NEUTRAL, ERROR }

/**
 * Reusable empty / error state: icon in a soft circle, title, optional message and up to two
 * actions. Use for empty lists, "no search results", failed loads, offline, etc.
 */
@Composable
fun StateMessageView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: StateTone = StateTone.NEUTRAL,
    primaryActionLabel: String? = null,
    primaryActionIcon: ImageVector? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val toneColor = if (tone == StateTone.ERROR) colors.error else colors.secondaryText
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(toneColor.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (tone == StateTone.ERROR) colors.error else colors.primaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .semantics { heading() }
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            FilledTonalButton(onClick = onPrimaryAction) {
                if (primaryActionIcon != null) {
                    Icon(
                        primaryActionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(primaryActionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.TextButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}
