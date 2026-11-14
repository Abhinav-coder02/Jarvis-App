package com.example.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(context: Context, private val onInitSuccess: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Language not supported")
            } else {
                isReady = true
                onInitSuccess()
            }
        } else {
            Log.e("TtsManager", "TTS Initialization failed")
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        if (isReady && tts != null) {
            val utteranceId = System.currentTimeMillis().toString()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onComplete()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onComplete()
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            onComplete()
        }
    }

    fun stop() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}