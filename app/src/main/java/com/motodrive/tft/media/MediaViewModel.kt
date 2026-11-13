package com.motodrive.tft.media

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaUiState(
    val title: String = "M Sport Highway Ride",
    val artist: String = "Connected Motorcycle Audio",
    val album: String = "MotoDrive Acoustic",
    val isPlaying: Boolean = false,
    val durationMs: Long = 214000L,
    val currentPositionMs: Long = 45000L,
    val sourceApp: String = "Bluetooth Audio"
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var activeController: MediaController? = null

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    init {
        // Collect updates from MediaSession notification listener
        viewModelScope.launch {
            MediaSessionBus.updates.collect { update ->
                activeController = update.controller
                _uiState.update {
                    it.copy(
                        title = update.title,
                        artist = update.artist,
                        isPlaying = update.isPlaying,
                        durationMs = if (update.durationMs > 0) update.durationMs else it.durationMs,
                        currentPositionMs = update.positionMs
                    )
                }
                manageProgressTimer(update.isPlaying)
            }
        }
    }

    private fun manageProgressTimer(isPlaying: Boolean) {
        progressJob?.cancel()
        if (isPlaying) {
            progressJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { current ->
                        val nextPos = (current.currentPositionMs + 1000L).coerceAtMost(current.durationMs)
                        current.copy(currentPositionMs = nextPos)
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val currentlyPlaying = _uiState.value.isPlaying
        val controller = activeController
        if (controller != null) {
            val controls = controller.transportControls
            if (currentlyPlaying) controls.pause() else controls.play()
        } else {
            // Dispatch standard system media key event to whatever audio app is active
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        val newState = !currentlyPlaying
        _uiState.update { it.copy(isPlaying = newState) }
        manageProgressTimer(newState)
    }

    fun playNext() {
        val controller = activeController
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun playPrevious() {
        val controller = activeController
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = activeController
        if (controller != null) {
            controller.transportControls.seekTo(positionMs)
        }
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    fun launchMusicApp(packageName: String) {
        try {
            val context = getApplication<Application>()
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

