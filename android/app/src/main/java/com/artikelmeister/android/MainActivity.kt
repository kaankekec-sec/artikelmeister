package com.artikelmeister.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.artikelmeister.android.ui.ArtikelMeisterTheme
import com.artikelmeister.android.ui.RootContainerView
import com.artikelmeister.android.ui.UiPreferences
import com.artikelmeister.android.ui.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The Android 12+ system splash (values-v31/themes.xml) covers cold start; on older
        // versions the dark brand windowBackground does. Edge-to-edge is set up before content.
        applySystemBars(isDark = true)
        super.onCreate(savedInstanceState)
        UiPreferences.init(applicationContext)
        handleOAuthIntent(intent)

        // Initialize German Text-To-Speech synthesizer
        SpeechManager.getInstance(applicationContext)

        setContent {
            val store = remember { DeckStore(applicationContext) }
            val authManager = remember { GeminiAuthManager.getInstance(applicationContext) }
            val isDark = resolveDarkTheme(legacyIsDark = store.settings.isDarkMode)

            // Single source of truth for system bar appearance. Bars are transparent;
            // Compose draws the surfaces behind them.
            DisposableEffect(isDark) {
                applySystemBars(isDark)
                onDispose { }
            }

            ArtikelMeisterTheme(darkTheme = isDark) {
                RootContainerView(
                    store = store,
                    authManager = authManager
                )
            }
        }
    }

    private fun applySystemBars(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, LIGHT_BARS_FALLBACK_SCRIM)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "artikelmeister" && uri.host == "oauth2redirect") {
            GeminiAuthManager.getInstance(applicationContext).handleOAuthDeepLink(uri)
        }
    }

    private companion object {
        /** Used only on API levels that cannot draw dark icons on a light navigation bar. */
        val LIGHT_BARS_FALLBACK_SCRIM: Int = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
