package com.artikelmeister.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.artikelmeister.android.ui.ArtikelMeisterTheme
import com.artikelmeister.android.ui.RootContainerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize German Text-To-Speech synthesizer
        SpeechManager.getInstance(applicationContext)

        setContent {
            val store = remember { DeckStore(applicationContext) }
            val authManager = remember { GeminiAuthManager.getInstance(applicationContext) }
            val isDark = store.settings.isDarkMode

            LaunchedEffect(isDark) {
                if (isDark) {
                    val darkBarColor = Color.parseColor("#212124")
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(darkBarColor),
                        navigationBarStyle = SystemBarStyle.dark(darkBarColor)
                    )
                } else {
                    val lightBarColor = Color.parseColor("#FFFFFF")
                    val lightDividerScrim = Color.parseColor("#E5E7EB")
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(lightBarColor, lightDividerScrim),
                        navigationBarStyle = SystemBarStyle.light(lightBarColor, lightDividerScrim)
                    )
                }
            }

            ArtikelMeisterTheme(darkTheme = isDark) {
                RootContainerView(
                    store = store,
                    authManager = authManager
                )
            }
        }
    }
}
