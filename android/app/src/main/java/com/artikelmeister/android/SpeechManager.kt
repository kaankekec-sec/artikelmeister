package com.artikelmeister.android

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechManager private constructor(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val result = engine.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale("de", "DE"))
            }
            engine.setSpeechRate(0.92f)
            engine.setPitch(1.0f)
            isReady = true
            pendingText?.let { text ->
                pendingText = null
                speak(text)
            }
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !isReady) {
            pendingText = clean
            return
        }
        if (engine.isSpeaking) {
            engine.stop()
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "artikelmeister_utterance")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        @Volatile
        private var instance: SpeechManager? = null

        fun getInstance(context: Context): SpeechManager {
            return instance ?: synchronized(this) {
                instance ?: SpeechManager(context).also { instance = it }
            }
        }

        fun speak(text: String) {
            instance?.speak(text)
        }
    }
}
