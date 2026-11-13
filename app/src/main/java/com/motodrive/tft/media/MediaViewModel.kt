package com.motodrive.tft.media

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaController
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
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
    val title: String = "No Track Playing",
    val artist: String = "Connect Phone via Bluetooth",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val sourceApp: String = "Bluetooth Audio",
    val isConnected: Boolean = false,
    val connectedDeviceName: String? = null,
    val isNotificationAccessGranted: Boolean = false,
    val hasActiveTrack: Boolean = false
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var activeController: MediaController? = null

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val track = intent?.getStringExtra("track")
            val artist = intent?.getStringExtra("artist")
            val album = intent?.getStringExtra("album")
            val playing = intent?.getBooleanExtra("playing", false) ?: false
            val duration = intent?.getLongExtra("duration", 0L) ?: 0L
            val position = intent?.getLongExtra("position", 0L) ?: 0L

            if (!track.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        title = track,
                        artist = artist ?: (it.connectedDeviceName ?: "Bluetooth Audio"),
                        album = album.orEmpty(),
                        isPlaying = playing,
                        durationMs = if (duration > 0) duration else it.durationMs,
                        currentPositionMs = position,
                        hasActiveTrack = true,
                        sourceApp = if (it.connectedDeviceName != null) "Bluetooth • ${it.connectedDeviceName}" else "Bluetooth Audio"
                    )
                }
                manageProgressTimer(playing)
            }
        }
    }

    init {
        refreshNotificationAccess()
        registerMusicReceiver()

        // Collect updates from MediaSession notification listener
        viewModelScope.launch {
            MediaSessionBus.updates.collect { update ->
                activeController = update.controller
                _uiState.update {
                    val isRealTrack = update.title != "No Media Active" && update.title.isNotBlank()
                    it.copy(
                        title = if (isRealTrack) update.title else it.title,
                        artist = if (isRealTrack) update.artist else it.artist,
                        isPlaying = update.isPlaying,
                        durationMs = if (update.durationMs > 0) update.durationMs else it.durationMs,
                        currentPositionMs = update.positionMs,
                        hasActiveTrack = isRealTrack || it.hasActiveTrack,
                        sourceApp = if (it.connectedDeviceName != null) "Bluetooth • ${it.connectedDeviceName}" else "Connected Media"
                    )
                }
                manageProgressTimer(update.isPlaying)
            }
        }
    }

    private fun registerMusicReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.htc.music.metachanged")
            addAction("fm.last.android.metachanged")
            addAction("com.sec.android.app.music.metachanged")
            addAction("com.nullsoft.winamp.metachanged")
            addAction("com.amazon.mp3.metachanged")
            addAction("com.miui.player.metachanged")
            addAction("com.real.IMP.metachanged")
            addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
        }
        try {
            getApplication<Application>().registerReceiver(musicReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshNotificationAccess() {
        val context = getApplication<Application>()
        val hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _uiState.update { it.copy(isNotificationAccessGranted = hasAccess) }
    }

    fun updateBluetoothStatus(isConnected: Boolean, deviceName: String?) {
        refreshNotificationAccess()
        _uiState.update { cur ->
            if (!isConnected) {
                cur.copy(
                    isConnected = false,
                    connectedDeviceName = null,
                    hasActiveTrack = false,
                    isPlaying = false,
                    title = "No Device Connected",
                    artist = "Connect your phone via Bluetooth to stream & control music",
                    sourceApp = "Bluetooth Disconnected"
                )
            } else {
                val dev = deviceName ?: "Connected Phone"
                cur.copy(
                    isConnected = true,
                    connectedDeviceName = dev,
                    title = if (!cur.hasActiveTrack) "Ready to Play" else cur.title,
                    artist = if (!cur.hasActiveTrack) "Start playback on $dev" else cur.artist,
                    sourceApp = "Bluetooth • $dev"
                )
            }
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(musicReceiver)
        } catch (_: Exception) {}
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

