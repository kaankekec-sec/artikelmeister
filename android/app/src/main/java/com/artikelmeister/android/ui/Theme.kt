package com.artikelmeister.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

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
    val vividGreen: Color = Color(0.12f, 0.78f, 0.35f),
    val vividRed: Color = Color(0.92f, 0.18f, 0.22f)
)

val LocalAppColors = staticCompositionLocalOf {
    ArtikelMeisterColors(
        isDark = true,
        windowBackground = Color(0xFF161618),
        controlBackground = Color(0xFF212124),
        textBackground = Color(0xFF121214),
        sidebarBackground = Color(0xFF1C1C1F),
        cardBorderSubtle = Color(0x24FFFFFF),
        primaryText = Color(0xFFF5F5F7),
        secondaryText = Color(0xFF98989F)
    )
}

private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF0A84FF),
    secondary = Color(0xFFA855F7),
    tertiary = Color(0xFFFF9F0A),
    background = Color(0xFF161618),
    surface = Color(0xFF212124),
    surfaceVariant = Color(0xFF2C2C30),
    surfaceContainer = Color(0xFF212124),
    surfaceContainerHigh = Color(0xFF28282C),
    surfaceContainerHighest = Color(0xFF323236),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF9E9EA6),
    outline = Color(0x38FFFFFF),
    outlineVariant = Color(0x22FFFFFF)
)

private val LightColorPalette = lightColorScheme(
    primary = Color(0xFF0066EB),
    secondary = Color(0xFF8438FF),
    tertiary = Color(0xFFD97300),
    background = Color(0xFFF2F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFF1F6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F7FB),
    surfaceContainerHighest = Color(0xFFEBEDF4),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF15171E),
    onSurface = Color(0xFF15171E),
    onSurfaceVariant = Color(0xFF5C6170),
    outline = Color(0x2E000000),
    outlineVariant = Color(0x1C000000)
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ArtikelMeisterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val targetColors = if (darkTheme) {
        ArtikelMeisterColors(
            isDark = true,
            windowBackground = Color(0xFF161618),
            controlBackground = Color(0xFF212124),
            textBackground = Color(0xFF121214),
            sidebarBackground = Color(0xFF1C1C1F),
            cardBorderSubtle = Color(0x22FFFFFF),
            primaryText = Color(0xFFF5F5F7),
            secondaryText = Color(0xFF9E9EA6),
            accentBlue = Color(0xFF0A84FF),
            accentPurple = Color(0xFFA855F7),
            accentOrange = Color(0xFFFF9F0A),
            vividGreen = Color(0.12f, 0.78f, 0.35f),
            vividRed = Color(0.92f, 0.18f, 0.22f)
        )
    } else {
        ArtikelMeisterColors(
            isDark = false,
            windowBackground = Color(0xFFF2F4F8),
            controlBackground = Color(0xFFFFFFFF),
            textBackground = Color(0xFFEFF1F6),
            sidebarBackground = Color(0xFFF7F8FB),
            cardBorderSubtle = Color(0x1C000000),
            primaryText = Color(0xFF15171E),
            secondaryText = Color(0xFF5C6170),
            accentBlue = Color(0xFF0066EB),
            accentPurple = Color(0xFF8438FF),
            accentOrange = Color(0xFFD97300),
            vividGreen = Color(0xFF159442),
            vividRed = Color(0xFFD92632)
        )
    }

    val animSpec = tween<Color>(durationMillis = 280)
    val windowBg by animateColorAsState(targetColors.windowBackground, animSpec, label = "windowBg")
    val controlBg by animateColorAsState(targetColors.controlBackground, animSpec, label = "controlBg")
    val textBg by animateColorAsState(targetColors.textBackground, animSpec, label = "textBg")
    val sidebarBg by animateColorAsState(targetColors.sidebarBackground, animSpec, label = "sidebarBg")
    val borderSubtle by animateColorAsState(targetColors.cardBorderSubtle, animSpec, label = "borderSubtle")
    val primaryTxt by animateColorAsState(targetColors.primaryText, animSpec, label = "primaryTxt")
    val secondaryTxt by animateColorAsState(targetColors.secondaryText, animSpec, label = "secondaryTxt")
    val blue by animateColorAsState(targetColors.accentBlue, animSpec, label = "blue")
    val purple by animateColorAsState(targetColors.accentPurple, animSpec, label = "purple")
    val orange by animateColorAsState(targetColors.accentOrange, animSpec, label = "orange")
    val green by animateColorAsState(targetColors.vividGreen, animSpec, label = "green")
    val red by animateColorAsState(targetColors.vividRed, animSpec, label = "red")

    val animatedAppColors = ArtikelMeisterColors(
        isDark = darkTheme,
        windowBackground = windowBg,
        controlBackground = controlBg,
        textBackground = textBg,
        sidebarBackground = sidebarBg,
        cardBorderSubtle = borderSubtle,
        primaryText = primaryTxt,
        secondaryText = secondaryTxt,
        accentBlue = blue,
        accentPurple = purple,
        accentOrange = orange,
        vividGreen = green,
        vividRed = red
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            val barColorArgb = targetColors.controlBackground.toArgb()
            @Suppress("DEPRECATION")
            window.statusBarColor = barColorArgb
            @Suppress("DEPRECATION")
            window.navigationBarColor = barColorArgb
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                window.navigationBarDividerColor = targetColors.cardBorderSubtle.toArgb()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides animatedAppColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

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
                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
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
                    fontSize = 13.sp,
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
