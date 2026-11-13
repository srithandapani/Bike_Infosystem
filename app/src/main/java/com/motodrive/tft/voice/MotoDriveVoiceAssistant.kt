package com.motodrive.tft.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

sealed class VoiceCommandResult {
    data class OpenNavigation(val destination: String?) : VoiceCommandResult()
    object PlayMusic : VoiceCommandResult()
    object PauseMusic : VoiceCommandResult()
    object NextSong : VoiceCommandResult()
    data class SpeakTime(val timeText: String) : VoiceCommandResult()
    object OpenWeather : VoiceCommandResult()
    object OpenTrip : VoiceCommandResult()
    object OpenSettings : VoiceCommandResult()
    object ToggleRidingMode : VoiceCommandResult()
    data class Unrecognized(val rawText: String) : VoiceCommandResult()
}

data class VoiceUiState(
    val isListening: Boolean = false,
    val spokenText: String = "",
    val feedbackMessage: String = "Say 'Navigate to...', 'Play Music', or 'What time is it'",
    val isTtsReady: Boolean = false
)

class MotoDriveVoiceAssistant(application: Application) : AndroidViewModel(application), RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var onCommandParsed: ((VoiceCommandResult) -> Unit)? = null

    init {
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(application, this)
    }

    fun setCommandListener(listener: (VoiceCommandResult) -> Unit) {
        this.onCommandParsed = listener
    }

    private fun initSpeechRecognizer() {
        val context = getApplication<Application>()
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@MotoDriveVoiceAssistant)
            }
        }
    }

    fun startListening() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            recognizer.startListening(intent)
            _uiState.update { it.copy(isListening = true, spokenText = "", feedbackMessage = "Listening for command...") }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isListening = false, feedbackMessage = "Microphone unavailable") }
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        _uiState.update { it.copy(isListening = false, spokenText = text) }
        processVoiceCommand(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        _uiState.update { it.copy(spokenText = text) }
    }

    private fun processVoiceCommand(command: String) {
        val lower = command.lowercase(Locale.getDefault())

        val result: VoiceCommandResult = when {
            lower.contains("navigate to") || lower.contains("go to") || lower.contains("direction to") -> {
                val dest = lower.substringAfter("to").trim()
                speak("Setting navigation to $dest")
                VoiceCommandResult.OpenNavigation(dest)
            }
            lower.contains("navigation") || lower.contains("open map") -> {
                speak("Opening navigation map")
                VoiceCommandResult.OpenNavigation(null)
            }
            lower.contains("play") || lower.contains("resume") -> {
                speak("Playing media")
                VoiceCommandResult.PlayMusic
            }
            lower.contains("pause") || lower.contains("stop music") -> {
                speak("Pausing media")
                VoiceCommandResult.PauseMusic
            }
            lower.contains("next") || lower.contains("skip") -> {
                speak("Skipping track")
                VoiceCommandResult.NextSong
            }
            lower.contains("time") || lower.contains("what time") -> {
                val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                val timeStr = timeSdf.format(Date())
                speak("The time is $timeStr")
                VoiceCommandResult.SpeakTime(timeStr)
            }
            lower.contains("weather") -> {
                speak("Opening weather forecast")
                VoiceCommandResult.OpenWeather
            }
            lower.contains("trip") -> {
                speak("Displaying trip computer")
                VoiceCommandResult.OpenTrip
            }
            lower.contains("riding mode") || lower.contains("ride mode") -> {
                speak("Toggling riding mode")
                VoiceCommandResult.ToggleRidingMode
            }
            lower.contains("setting") -> {
                speak("Opening settings")
                VoiceCommandResult.OpenSettings
            }
            else -> {
                speak("Command not recognized. Please repeat.")
                VoiceCommandResult.Unrecognized(command)
            }
        }

        onCommandParsed?.invoke(result)
    }

    fun speak(text: String) {
        _uiState.update { it.copy(feedbackMessage = text) }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "RONIN_TTS")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            _uiState.update { it.copy(isTtsReady = true) }
        }
    }

    override fun onError(error: Int) {
        _uiState.update { it.copy(isListening = false, feedbackMessage = "Voice error ($error)") }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _uiState.update { it.copy(isListening = false) }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

