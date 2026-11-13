package com.motodrive.tft.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MediaUpdate(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val controller: MediaController?
)

object MediaSessionBus {
    private val _updates = MutableSharedFlow<MediaUpdate>(extraBufferCapacity = 1)
    val updates = _updates.asSharedFlow()

    fun postMediaUpdate(
        title: String,
        artist: String,
        isPlaying: Boolean,
        durationMs: Long,
        positionMs: Long,
        controller: MediaController?
    ) {
        _updates.tryEmit(
            MediaUpdate(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                durationMs = durationMs,
                positionMs = positionMs,
                controller = controller
            )
        )
    }
}

