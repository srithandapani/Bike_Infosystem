package com.motodrive.tft.connectivity.apple

enum class IphoneNotificationCategory {
    INCOMING_CALL,
    WHATSAPP,
    INSTAGRAM,
    SMS,
    SNAPCHAT,
    OTHER
}

data class IphoneNotificationItem(
    val notificationUid: Int,
    val category: IphoneNotificationCategory,
    val appName: String,
    val sender: String,
    val messagePreview: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class IphoneTrackInfo(
    val title: String = "No Track Playing",
    val artist: String = "iPhone Audio",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationSeconds: Int = 0
)

data class IphoneBleConnectionState(
    val isConnected: Boolean = false,
    val deviceName: String = "Not Connected",
    val deviceAddress: String = "",
    val isAncsReady: Boolean = false,
    val isAmsReady: Boolean = false,
    val isScanning: Boolean = false,
    val statusMessage: String = "Ready to pair with iPhone"
)

