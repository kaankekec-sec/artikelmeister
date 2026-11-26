package com.artikelmeister.android.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import com.artikelmeister.android.R
import java.util.Locale
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Shapes
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
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.ReviewRating
import com.artikelmeister.android.VersionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// =====================================================================================
// Color tokens
// =====================================================================================

/** App-wide colors. Pair a solid fill with its on-color, or a badge fill with its role color. */
@Immutable
data class ArtikelMeisterColors(
    val isDark: Boolean,
    val windowBackground: Color,
    val controlBackground: Color,
    val textBackground: Color,
    val sidebarBackground: Color,
    val cardBorderSubtle: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentBlue: Color,
    val accentPurple: Color,
    val accentOrange: Color,
    val success: Color,
    val error: Color,
    val onAccent: Color,
    val artikelDer: Color,
    val artikelDie: Color,
    val artikelDas: Color,
    val artikelNone: Color,
    val blueBadgeBackground: Color,
    val purpleBadgeBackground: Color,
    val orangeBadgeBackground: Color,
    val successBadgeBackground: Color,
    val errorBadgeBackground: Color,
    val derBadgeBackground: Color,
    val dieBadgeBackground: Color,
    val dasBadgeBackground: Color,
    val articleNoneBadgeBackground: Color
) {
    val onBlue: Color get() = onAccent
    val onPurple: Color get() = onAccent
    val onOrange: Color get() = onAccent
    val onSuccess: Color get() = onAccent
    val onError: Color get() = onAccent
    val onDer: Color get() = onAccent
    val onDie: Color get() = onAccent
    val onDas: Color get() = onAccent
    val onArticleNone: Color get() = onAccent

    val feedbackCorrect: Color get() = success
    val feedbackIncorrect: Color get() = error
    val warning: Color get() = accentOrange
    val info: Color get() = accentBlue
    val vividGreen: Color get() = success
    val vividRed: Color get() = error
}

private val DarkAppColors = ArtikelMeisterColors(
    isDark = true,
    windowBackground = Color(0xFF0F1115),
    controlBackground = Color(0xFF1B1F27),
    textBackground = Color(0xFF151820),
    sidebarBackground = Color(0xFF13161C),
    cardBorderSubtle = Color(0x18FFFFFF),
    primaryText = Color(0xFFF4F6FA),
    secondaryText = Color(0xFF9DA3B0),
    accentBlue = Color(0xFF429EFF),
    accentPurple = Color(0xFFC080FF),
    accentOrange = Color(0xFFFF9F0A),
    success = Color(0xFF30D158),
    error = Color(0xFFFF6C60),
    onAccent = Color(0xFF0B1018),
    artikelDer = Color(0xFF5D9AFF),
    artikelDie = Color(0xFFF56FCA),
    artikelDas = Color(0xFFE8B90E),
    artikelNone = Color(0xFFA1A1AA),
    blueBadgeBackground = Color(0xFF182842),
    purpleBadgeBackground = Color(0xFF2D1E3E),
    orangeBadgeBackground = Color(0xFF342516),
    successBadgeBackground = Color(0xFF173123),
    errorBadgeBackground = Color(0xFF3B2025),
    derBadgeBackground = Color(0xFF182742),
    dieBadgeBackground = Color(0xFF371E33),
    dasBadgeBackground = Color(0xFF342A15),
    articleNoneBadgeBackground = Color(0xFF262932)
)

private val LightAppColors = ArtikelMeisterColors(
    isDark = false,
    windowBackground = Color(0xFFF3F5F8),
    controlBackground = Color(0xFFFFFFFF),
    textBackground = Color(0xFFECEFF4),
    sidebarBackground = Color(0xFFF8F9FC),
    cardBorderSubtle = Color(0x160F172A),
    primaryText = Color(0xFF141720),
    secondaryText = Color(0xFF565C6B),
    accentBlue = Color(0xFF005BCD),
    accentPurple = Color(0xFF7130D4),
    accentOrange = Color(0xFF984900),
    success = Color(0xFF147236),
    error = Color(0xFFB42318),
    onAccent = Color.White,
    artikelDer = Color(0xFF194FB8),
    artikelDie = Color(0xFFA51E75),
    artikelDas = Color(0xFF765500),
    artikelNone = Color(0xFF515968),
    blueBadgeBackground = Color(0xFFE7EFFD),
    purpleBadgeBackground = Color(0xFFF1E7FC),
    orangeBadgeBackground = Color(0xFFF9EDDB),
    successBadgeBackground = Color(0xFFE5F3E9),
    errorBadgeBackground = Color(0xFFFCEAE7),
    derBadgeBackground = Color(0xFFE8F0FD),
    dieBadgeBackground = Color(0xFFF8EAF4),
    dasBadgeBackground = Color(0xFFF8F0D7),
    articleNoneBadgeBackground = Color(0xFFEEF0F4)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** Article identity, including null for article-less words. */
fun ArtikelMeisterColors.artikelColor(artikel: Artikel?): Color = when (artikel) {
    Artikel.DER -> artikelDer
    Artikel.DIE -> artikelDie
    Artikel.DAS -> artikelDas
    null -> artikelNone
}

fun ArtikelMeisterColors.onArtikel(artikel: Artikel?): Color = when (artikel) {
    Artikel.DER -> onDer
    Artikel.DIE -> onDie
    Artikel.DAS -> onDas
    null -> onArticleNone
}

fun ArtikelMeisterColors.artikelBadgeBackground(artikel: Artikel?): Color = when (artikel) {
    Artikel.DER -> derBadgeBackground
    Artikel.DIE -> dieBadgeBackground
    Artikel.DAS -> dasBadgeBackground
    null -> articleNoneBadgeBackground
}

@Composable
@ReadOnlyComposable
fun Artikel.themeColor(): Color = LocalAppColors.current.artikelColor(this)

fun ArtikelMeisterColors.stageColor(stage: CardStage): Color = when (stage) {
    CardStage.NEW -> accentBlue
    CardStage.LEARNING -> accentOrange
    CardStage.REVIEW -> success
    CardStage.RELEARNING -> error
}

fun ArtikelMeisterColors.stageBadgeBackground(stage: CardStage): Color = when (stage) {
    CardStage.NEW -> blueBadgeBackground
    CardStage.LEARNING -> orangeBadgeBackground
    CardStage.REVIEW -> successBadgeBackground
    CardStage.RELEARNING -> errorBadgeBackground
}

fun ArtikelMeisterColors.ratingColor(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> error
    ReviewRating.HARD -> accentOrange
    ReviewRating.GOOD -> success
    ReviewRating.EASY -> accentBlue
}

fun ArtikelMeisterColors.onRating(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> onError
    ReviewRating.HARD -> onOrange
    ReviewRating.GOOD -> onSuccess
    ReviewRating.EASY -> onBlue
}

fun ArtikelMeisterColors.ratingBadgeBackground(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> errorBadgeBackground
    ReviewRating.HARD -> orangeBadgeBackground
    ReviewRating.GOOD -> successBadgeBackground
    ReviewRating.EASY -> blueBadgeBackground
}

/** Intro colors are fixed because the intro surface is dark in every appearance. */
object SplashPalette {
    val gradientTop = Color(0xFF14242B)
    val gradientMiddle = Color(0xFF080F1C)
    val gradientBottom = Color(0xFF03050D)
    val label = DarkAppColors.secondaryText

    fun artikelColor(artikel: Artikel): Color = DarkAppColors.artikelColor(artikel)
    fun artikelBadgeBackground(artikel: Artikel): Color = DarkAppColors.artikelBadgeBackground(artikel)
}

/** Use the same pair for each cell and corresponding legend swatch. */
@Immutable
data class HeatmapColors(val background: Color, val foreground: Color)

private val DarkHeatmap = listOf(
    HeatmapColors(Color(0xFF2D333B), Color.White),
    HeatmapColors(Color(0xFF1D4F35), Color.White),
    HeatmapColors(Color(0xFF29784B), Color.White),
    HeatmapColors(Color(0xFF41A765), DarkAppColors.onAccent),
    HeatmapColors(Color(0xFF64D981), DarkAppColors.onAccent)
)

private val LightHeatmap = listOf(
    HeatmapColors(Color(0xFFEAEDF1), DarkAppColors.onAccent),
    HeatmapColors(Color(0xFFD8F1DE), DarkAppColors.onAccent),
    HeatmapColors(Color(0xFFA5DDB5), DarkAppColors.onAccent),
    HeatmapColors(Color(0xFF4A9C64), DarkAppColors.onAccent),
    HeatmapColors(Color(0xFF19723D), Color.White)
)

fun ArtikelMeisterColors.heatmapColors(count: Int): HeatmapColors {
    val level = when {
        count <= 0 -> 0
        count < 5 -> 1
        count < 15 -> 2
        count < 35 -> 3
        else -> 4
    }
    return (if (isDark) DarkHeatmap else LightHeatmap)[level]
}

private val DarkColorPalette = darkColorScheme(
    primary = DarkAppColors.accentBlue,
    secondary = DarkAppColors.accentPurple,
    tertiary = DarkAppColors.accentOrange,
    error = DarkAppColors.error,
    background = DarkAppColors.windowBackground,
    surface = DarkAppColors.controlBackground,
    surfaceVariant = Color(0xFF2C2C30),
    surfaceContainer = DarkAppColors.controlBackground,
    surfaceContainerHigh = Color(0xFF28282C),
    surfaceContainerHighest = Color(0xFF323236),
    inverseSurface = DarkAppColors.primaryText,
    inverseOnSurface = LightAppColors.primaryText,
    inversePrimary = LightAppColors.accentBlue,
    onPrimary = DarkAppColors.onBlue,
    onSecondary = DarkAppColors.onPurple,
    onTertiary = DarkAppColors.onOrange,
    onError = DarkAppColors.onError,
    onBackground = DarkAppColors.primaryText,
    onSurface = DarkAppColors.primaryText,
    onSurfaceVariant = DarkAppColors.secondaryText,
    outline = DarkAppColors.secondaryText,
    outlineVariant = DarkAppColors.cardBorderSubtle
)

private val LightColorPalette = lightColorScheme(
    primary = LightAppColors.accentBlue,
    secondary = LightAppColors.accentPurple,
    tertiary = LightAppColors.accentOrange,
    error = LightAppColors.error,
    background = LightAppColors.windowBackground,
    surface = LightAppColors.controlBackground,
    surfaceVariant = LightAppColors.textBackground,
    surfaceContainer = LightAppColors.controlBackground,
    surfaceContainerHigh = Color(0xFFF6F7FB),
    surfaceContainerHighest = Color(0xFFEBEDF4),
    inverseSurface = DarkAppColors.controlBackground,
    inverseOnSurface = DarkAppColors.primaryText,
    inversePrimary = DarkAppColors.accentBlue,
    onPrimary = LightAppColors.onBlue,
    onSecondary = LightAppColors.onPurple,
    onTertiary = LightAppColors.onOrange,
    onError = LightAppColors.onError,
    onBackground = LightAppColors.primaryText,
    onSurface = LightAppColors.primaryText,
    onSurfaceVariant = LightAppColors.secondaryText,
    outline = LightAppColors.secondaryText,
    outlineVariant = LightAppColors.cardBorderSubtle
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

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

// =====================================================================================
// Theme mode adapter (System / Light / Dark)
// =====================================================================================

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val rawValue: String, @StringRes val labelRes: Int) {
    TR("tr", R.string.language_tr),
    EN("en", R.string.language_en),
    DE("de", R.string.language_de);

    companion object {
        fun defaultFromSystemLocale(): AppLanguage {
            val sysLang = Resources.getSystem().configuration.locales.get(0)?.language
                ?: Locale.getDefault().language
            return when (sysLang.lowercase(Locale.ROOT)) {
                "de" -> DE
                "en" -> EN
                "tr" -> TR
                else -> TR
            }
        }

        fun fromRaw(raw: String?): AppLanguage =
            entries.firstOrNull { it.rawValue == raw } ?: defaultFromSystemLocale()
    }
}

@StringRes
fun Artikel.grammaticalLabelRes(): Int = when (this) {
    Artikel.DER -> R.string.artikel_der_label
    Artikel.DIE -> R.string.artikel_die_label
    Artikel.DAS -> R.string.artikel_das_label
}

@StringRes
fun CardStage.titleRes(): Int = when (this) {
    CardStage.NEW -> R.string.stage_new
    CardStage.LEARNING -> R.string.stage_learning
    CardStage.REVIEW -> R.string.stage_review
    CardStage.RELEARNING -> R.string.stage_relearning
}

@StringRes
fun ReviewRating.titleRes(): Int = when (this) {
    ReviewRating.AGAIN -> R.string.rating_again
    ReviewRating.HARD -> R.string.rating_hard
    ReviewRating.GOOD -> R.string.rating_good
    ReviewRating.EASY -> R.string.rating_easy
}

@StringRes
fun com.artikelmeister.android.CEFRLevel.titleRes(): Int = when (this) {
    com.artikelmeister.android.CEFRLevel.A1 -> R.string.cefr_a1_title
    com.artikelmeister.android.CEFRLevel.A2 -> R.string.cefr_a2_title
    com.artikelmeister.android.CEFRLevel.B1 -> R.string.cefr_b1_title
    com.artikelmeister.android.CEFRLevel.B2 -> R.string.cefr_b2_title
    com.artikelmeister.android.CEFRLevel.C1 -> R.string.cefr_c1_title
}

@StringRes
fun com.artikelmeister.android.CEFRLevel.descriptionRes(): Int = when (this) {
    com.artikelmeister.android.CEFRLevel.A1 -> R.string.cefr_a1_desc
    com.artikelmeister.android.CEFRLevel.A2 -> R.string.cefr_a2_desc
    com.artikelmeister.android.CEFRLevel.B1 -> R.string.cefr_b1_desc
    com.artikelmeister.android.CEFRLevel.B2 -> R.string.cefr_b2_desc
    com.artikelmeister.android.CEFRLevel.C1 -> R.string.cefr_c1_desc
}

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
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_SEEN_INTRO = "has_seen_intro_splash"
    private const val KEY_LAST_VERSION_CHECK_AT = "last_version_check_at"
    private const val KEY_REQUIRED_UPDATE_TAG = "required_update_tag"
    private const val KEY_REQUIRED_UPDATE_URL = "required_update_url"
    private const val VERSION_CHECK_THROTTLE_MS = 2L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null

    /** null = user has never picked a mode on this device (legacy behaviour). */
    var themeMode by mutableStateOf<ThemeMode?>(null)
        private set

    var appLanguage by mutableStateOf(AppLanguage.TR)
        private set

    var hasSeenIntro by mutableStateOf(false)
        private set

    var requiredUpdateTag by mutableStateOf<String?>(null)
        private set

    var requiredUpdateUrl by mutableStateOf<String?>(null)
        private set

    var isCheckingForUpdate by mutableStateOf(false)
        private set

    fun init(context: Context, currentVersion: String = resolveAppVersion(context)) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        themeMode = p.getString(KEY_THEME_MODE, null)?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrNull()
        }
        val rawLang = p.getString(KEY_APP_LANGUAGE, null)
        val resolvedLang = AppLanguage.fromRaw(rawLang)
        if (rawLang == null || rawLang == "system") {
            p.edit().putString(KEY_APP_LANGUAGE, resolvedLang.rawValue).apply()
        }
        appLanguage = resolvedLang
        hasSeenIntro = p.getBoolean(KEY_SEEN_INTRO, false)
        val cachedTag = p.getString(KEY_REQUIRED_UPDATE_TAG, null)
        val cachedUrl = p.getString(KEY_REQUIRED_UPDATE_URL, null)
        if (cachedTag != null && VersionChecker.isUpdateRequired(currentVersion, cachedTag)) {
            requiredUpdateTag = cachedTag
            requiredUpdateUrl = VersionChecker.sanitizeReleaseUrl(cachedUrl)
        } else if (cachedTag != null) {
            p.edit().remove(KEY_REQUIRED_UPDATE_TAG).remove(KEY_REQUIRED_UPDATE_URL).apply()
        }
    }

    fun wrapContext(base: Context): Context {
        val p = base.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawLang = p.getString(KEY_APP_LANGUAGE, null)
        val lang = AppLanguage.fromRaw(rawLang)
        if (rawLang == null || rawLang == "system") {
            p.edit().putString(KEY_APP_LANGUAGE, lang.rawValue).apply()
        }
        appLanguage = lang
        val locale = when (lang) {
            AppLanguage.TR -> Locale.forLanguageTag("tr-TR")
            AppLanguage.EN -> Locale.forLanguageTag("en-US")
            AppLanguage.DE -> Locale.forLanguageTag("de-DE")
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun selectAppLanguage(language: AppLanguage) {
        appLanguage = language
        prefs?.edit()?.putString(KEY_APP_LANGUAGE, language.rawValue)?.apply()
    }

    fun localizedContext(context: Context): Context {
        val locale = when (appLanguage) {
            AppLanguage.TR -> Locale.forLanguageTag("tr-TR")
            AppLanguage.EN -> Locale.forLanguageTag("en-US")
            AppLanguage.DE -> Locale.forLanguageTag("de-DE")
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getString(context: Context, @StringRes resId: Int, vararg args: Any): String {
        val ctx = localizedContext(context)
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    fun resolveAppVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.3.0"

    suspend fun checkForRequiredUpdate(currentVersion: String, force: Boolean = false) {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val lastCheck = p.getLong(KEY_LAST_VERSION_CHECK_AT, 0L)
        if (!force && requiredUpdateTag == null && (now - lastCheck) in 0 until VERSION_CHECK_THROTTLE_MS) {
            return
        }
        isCheckingForUpdate = true
        try {
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(VersionChecker.LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "ArtikelMeister/$currentVersion")
                    }
                    try {
                        if (conn.responseCode !in 200..299) return@runCatching null
                        val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                        VersionChecker.parseLatestReleaseJson(body)
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()
            } ?: return // Fail-open on network/rate-limit errors

            p.edit().putLong(KEY_LAST_VERSION_CHECK_AT, System.currentTimeMillis()).apply()
            if (VersionChecker.isUpdateRequired(currentVersion, info.tagName)) {
                requiredUpdateTag = info.tagName
                requiredUpdateUrl = info.htmlUrl
                p.edit()
                    .putString(KEY_REQUIRED_UPDATE_TAG, info.tagName)
                    .putString(KEY_REQUIRED_UPDATE_URL, info.htmlUrl)
                    .apply()
            } else {
                requiredUpdateTag = null
                requiredUpdateUrl = null
                p.edit()
                    .remove(KEY_REQUIRED_UPDATE_TAG)
                    .remove(KEY_REQUIRED_UPDATE_URL)
                    .apply()
            }
        } finally {
            isCheckingForUpdate = false
        }
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
fun ArtikelMeisterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Swap foregrounds and fills together so they stay paired during an appearance change.
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

// =====================================================================================
// Shared components
// =====================================================================================

/**
 * Consistent grouped section card used across StatsView, SettingsView, and AIGeneratorSheet.
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
                    .padding(bottom = 10.dp, start = 4.dp)
                    .semantics { heading() }
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondaryText
                )
            }
        }
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
            FilledTonalButton(
                onClick = onPrimaryAction,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
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
