package com.motodrive.tft.media

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MotoMediaNotificationListenerService : NotificationListenerService() {

    private var activeController: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            updateMediaState(state, activeController?.metadata)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            updateMediaState(activeController?.playbackState, metadata)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        findActiveMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        findActiveMediaSession()
    }

    private fun findActiveMediaSession() {
        try {
            val sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val componentName = android.content.ComponentName(this, MotoMediaNotificationListenerService::class.java)
            val controllers = sessionManager.getActiveSessions(componentName)
            if (controllers.isNotEmpty()) {
                val controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.first()
                if (controller != activeController) {
                    activeController?.unregisterCallback(callback)
                    activeController = controller
                    activeController?.registerCallback(callback)
                    updateMediaState(activeController?.playbackState, activeController?.metadata)
                }
            }
        } catch (e: SecurityException) {
            // Notification access not yet granted by user in Android settings
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMediaState(state: PlaybackState?, metadata: MediaMetadata?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "No Media Active"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Bluetooth / Connected App"
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L

        MediaSessionBus.postMediaUpdate(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            durationMs = duration,
            positionMs = position,
            controller = activeController
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        activeController?.unregisterCallback(callback)
    }
}

