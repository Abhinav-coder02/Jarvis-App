package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class JarvisState {
    SLEEPING_LISTENING_FOR_WAKE_WORD,
    LISTENING_FOR_COMMAND,
    PROCESSING,
    SPEAKING
}

class JarvisService : Service() {

    private val binder = JarvisBinder()
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var ttsManager: TtsManager
    private lateinit var commandProcessor: CommandProcessor

    private val _currentState = MutableStateFlow(JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD)
    val currentState: StateFlow<JarvisState> = _currentState

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    inner class JarvisBinder : Binder() {
        fun getService(): JarvisService = this@JarvisService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        addLog("Jarvis Core initialized.")
        commandProcessor = CommandProcessor(this)
        ttsManager = TtsManager(this) {
            speakText("Jarvis is online and monitoring.")
        }

        startForegroundServiceWithNotification()
        initSpeechRecognizer()
        startListeningForWakeWord()
    }

    private fun addLog(message: String) {
        val currentLogs = _logFlow.value.toMutableList()
        currentLogs.add(0, "[${System.currentTimeMillis() % 100000}] $message")
        if (currentLogs.size > 50) currentLogs.removeLast()
        _logFlow.value = currentLogs
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "jarvis_service_channel"
        val channelName = "Jarvis Assistant Engine"
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Engine Active")
            .setContentText("Listening for wake word 'Jarvis'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(JarvisRecognitionListener())
        } else {
            addLog("Speech recognition not available on this device.")
        }
    }

    private fun startListeningForWakeWord() {
        _currentState.value = JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun triggerManualListening() {
        ttsManager.stop()
        playChime()
        startListeningForCommand()
    }

    private fun startListeningForCommand() {
        _currentState.value = JarvisState.LISTENING_FOR_COMMAND
        addLog("Listening for command...")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun playChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e("JarvisService", "Failed to play chime", e)
        }
    }

    private fun handleCommandInput(text: String) {
        _currentState.value = JarvisState.PROCESSING
        addLog("User: $text")

        CoroutineScope(Dispatchers.Main).launch {
            val responseText = commandProcessor.processCommand(text)
            speakText(responseText)
        }
    }

    private fun speakText(text: String) {
        _currentState.value = JarvisState.SPEAKING
        addLog("Jarvis: $text")
        ttsManager.speak(text) {
            startListeningForWakeWord()
        }
    }

    private inner class JarvisRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Restart wake word listener on error timeout or empty match
            if (_currentState.value == JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD ||
                _currentState.value == JarvisState.LISTENING_FOR_COMMAND) {
                startListeningForWakeWord()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]

                if (_currentState.value == JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD) {
                    if (recognizedText.lowercase().contains("jarvis")) {
                        addLog("Wake word 'Jarvis' detected!")
                        playChime()
                        startListeningForCommand()
                    } else {
                        startListeningForWakeWord()
                    }
                } else if (_currentState.value == JarvisState.LISTENING_FOR_COMMAND) {
                    handleCommandInput(recognizedText)
                }
            } else {
                startListeningForWakeWord()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty() && _currentState.value == JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD) {
                val text = matches[0].lowercase()
                if (text.contains("jarvis")) {
                    speechRecognizer?.stopListening()
                    addLog("Wake word 'Jarvis' detected in partial results!")
                    playChime()
                    startListeningForCommand()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        ttsManager.shutdown()
        addLog("Jarvis Service Destroyed.")
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}