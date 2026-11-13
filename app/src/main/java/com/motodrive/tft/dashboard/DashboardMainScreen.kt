package com.motodrive.tft.dashboard

import com.motodrive.tft.core.util.Units

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.connectivity.ConnectivityUiState
import com.motodrive.tft.connectivity.PhoneConnectivityScreen
import com.motodrive.tft.connectivity.apple.IphoneBleConnectionState
import com.motodrive.tft.connectivity.apple.IphoneNotificationCenterScreen
import com.motodrive.tft.connectivity.apple.IphoneNotificationItem
import com.motodrive.tft.connectivity.apple.IphoneTrackInfo
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.components.MSportBottomBar
import com.motodrive.tft.core.components.MSportTopBar
import com.motodrive.tft.core.components.MotoDriveTab
import com.motodrive.tft.core.theme.*
import com.motodrive.tft.launcher.AppLauncherScreen
import com.motodrive.tft.media.FullScreenMediaScreen
import com.motodrive.tft.media.MediaPlayerCompactCard
import com.motodrive.tft.media.MediaUiState
import com.motodrive.tft.navigation.NavigationMapView
import com.motodrive.tft.navigation.NavigationUiState
import com.motodrive.tft.riding.RidingModeScreen
import com.motodrive.tft.settings.AppSettings
import com.motodrive.tft.settings.SettingsScreen
import com.motodrive.tft.speedometer.SpeedometerGauge
import com.motodrive.tft.speedometer.SpeedometerUiState
import com.motodrive.tft.trip.TripComputerScreen
import com.motodrive.tft.voice.VoiceUiState
import com.motodrive.tft.weather.WeatherUiState

@Composable
fun DashboardMainScreen(
    speedUiState: SpeedometerUiState,
    navUiState: NavigationUiState,
    mediaUiState: MediaUiState,
    connectivityUiState: ConnectivityUiState,
    weatherUiState: WeatherUiState,
    voiceUiState: VoiceUiState,
    settings: AppSettings,
    isRidingMode: Boolean,
    iphoneBleState: IphoneBleConnectionState = IphoneBleConnectionState(),
    iphoneNotifications: List<IphoneNotificationItem> = emptyList(),
    iphoneTrack: IphoneTrackInfo = IphoneTrackInfo(),
    onConnectIphone: () -> Unit = {},
    onDismissIphoneNotification: (Int) -> Unit = {},
    onClearAllIphoneNotifications: () -> Unit = {},
    onToggleRidingMode: () -> Unit,
    onTriggerVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeekTrack: (Long) -> Unit,
    onLaunchMusicApp: (String) -> Unit,
    onStartNavigation: (String) -> Unit,
    onCancelNavigation: () -> Unit,
    onLaunchGoogleMaps: ((String) -> Unit)? = null,
    onResetTrip: () -> Unit,
    onQuickCall: (String) -> Unit,
    onSpeedUnitChange: (String) -> Unit,
    onTempUnitChange: (String) -> Unit,
    on24HourChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBtAutoLaunchChange: (Boolean) -> Unit,
    onClusterStyleChange: (String) -> Unit = {},
    onThemeStyleChange: (String) -> Unit = {},
    onIphoneNotificationsChange: (Boolean) -> Unit = {},
    onShowNotificationPreviewsChange: (Boolean) -> Unit = {},
    onNotifyIncomingCallsChange: (Boolean) -> Unit = {},
    onNotifyWhatsAppChange: (Boolean) -> Unit = {},
    onNotifyInstagramChange: (Boolean) -> Unit = {},
    onNotifySmsChange: (Boolean) -> Unit = {},
    onNotifySnapchatChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(MotoDriveTab.NAVIGATION) }
    val colors = LocalCockpitColors.current

    // If Riding Mode is active, display the streamlined motorcycle riding cockpit
    if (isRidingMode) {
        RidingModeScreen(
            currentLocation = speedUiState.lastLocation,
            speedUiState = speedUiState,
            navUiState = navUiState,
            mediaUiState = mediaUiState,
            onTogglePlayPause = onTogglePlayPause,
            onNextTrack = onNextTrack,
            onExitRidingMode = onToggleRidingMode,
            onStartNavigation = onStartNavigation,
            onCancelNavigation = onCancelNavigation,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP STATUS BAR (TVS RONIN Branding, Clock, GPS, BT, iPhone, Battery)
            MSportTopBar(
                bluetoothConnected = connectivityUiState.pairedDevices.any { it.isConnected } || iphoneBleState.isConnected,
                gpsFix = speedUiState.hasGpsFix,
                batteryPercent = 88,
                isCharging = false,
                networkStatus = connectivityUiState.networkType,
                temperatureText = "${weatherUiState.temperatureCelsius.toInt()}${settings.temperatureUnit}",
                is24Hour = settings.is24HourClock
            )

            // MAIN CONTENT VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                when (activeTab) {
                    MotoDriveTab.NAVIGATION -> {
                        // Responsive Navigation Map + Instrument Panel (Landscape & Portrait)
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isPortrait = maxHeight > maxWidth
                            if (isPortrait) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Top 60%: Responsive Embedded Navigation Map
                                    NavigationMapView(
                                        currentLocation = speedUiState.lastLocation,
                                        headingDegrees = speedUiState.headingDegrees,
                                        uiState = navUiState,
                                        onStartNavigation = onStartNavigation,
                                        onCancelNavigation = onCancelNavigation,
                                        modifier = Modifier
                                            .weight(0.60f)
                                            .fillMaxWidth()
                                    )

                                    // Bottom 40%: Split Gauge and Compact Media
                                    Row(
                                        modifier = Modifier
                                            .weight(0.40f)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SpeedometerGauge(
                                            speedKmH = Units.speed(speedUiState.currentSpeedKmH, settings.speedUnit),
                                            headingDegrees = speedUiState.headingDegrees,
                                            headingCardinal = speedUiState.headingCardinal,
                                            tripDistanceKm = Units.distance(speedUiState.tripDistanceKm, settings.speedUnit),
                                            rideDurationSeconds = speedUiState.rideDurationSeconds,
                                            hasGpsFix = speedUiState.hasGpsFix,
                                            speedUnit = settings.speedUnit,
                                            maxSpeedKmH = Units.speed(speedUiState.maxSpeedKmH, settings.speedUnit),
                                            averageSpeedKmH = Units.speed(speedUiState.averageSpeedKmH, settings.speedUnit),
                                            clusterStyle = settings.clusterStyle,
                                            modifier = Modifier
                                                .weight(0.55f)
                                                .fillMaxHeight()
                                        )

                                        MediaPlayerCompactCard(
                                            uiState = mediaUiState,
                                            onTogglePlayPause = onTogglePlayPause,
                                            onNext = onNextTrack,
                                            onPrevious = onPreviousTrack,
                                            onOpenFullScreen = { activeTab = MotoDriveTab.MEDIA },
                                            modifier = Modifier
                                                .weight(0.45f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            } else {
                                // Landscape Motorcycle Cockpit Layout (70% Map + 30% Instrument Panel)
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NavigationMapView(
                                        currentLocation = speedUiState.lastLocation,
                                        headingDegrees = speedUiState.headingDegrees,
                                        uiState = navUiState,
                                        onStartNavigation = onStartNavigation,
                                        onCancelNavigation = onCancelNavigation,
                                        modifier = Modifier
                                            .weight(0.70f)
                                            .fillMaxHeight()
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(0.30f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SpeedometerGauge(
                                            speedKmH = Units.speed(speedUiState.currentSpeedKmH, settings.speedUnit),
                                            headingDegrees = speedUiState.headingDegrees,
                                            headingCardinal = speedUiState.headingCardinal,
                                            tripDistanceKm = Units.distance(speedUiState.tripDistanceKm, settings.speedUnit),
                                            rideDurationSeconds = speedUiState.rideDurationSeconds,
                                            hasGpsFix = speedUiState.hasGpsFix,
                                            speedUnit = settings.speedUnit,
                                            maxSpeedKmH = Units.speed(speedUiState.maxSpeedKmH, settings.speedUnit),
                                            averageSpeedKmH = Units.speed(speedUiState.averageSpeedKmH, settings.speedUnit),
                                            clusterStyle = settings.clusterStyle,
                                            modifier = Modifier.weight(1f)
                                        )

                                        MediaPlayerCompactCard(
                                            uiState = if (iphoneBleState.isConnected && iphoneTrack.title != "No Track Playing") {
                                                mediaUiState.copy(
                                                    title = iphoneTrack.title,
                                                    artist = iphoneTrack.artist,
                                                    album = iphoneTrack.album
                                                )
                                            } else {
                                                mediaUiState
                                            },
                                            onTogglePlayPause = onTogglePlayPause,
                                            onNext = onNextTrack,
                                            onPrevious = onPreviousTrack,
                                            onOpenFullScreen = { activeTab = MotoDriveTab.MEDIA }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MotoDriveTab.MEDIA -> {
                        FullScreenMediaScreen(
                            uiState = if (iphoneBleState.isConnected && iphoneTrack.title != "No Track Playing") {
                                mediaUiState.copy(
                                    title = iphoneTrack.title,
                                    artist = iphoneTrack.artist,
                                    album = iphoneTrack.album
                                )
                            } else {
                                mediaUiState
                            },
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNextTrack,
                            onPrevious = onPreviousTrack,
                            onSeekTo = onSeekTrack,
                            onLaunchMusicApp = onLaunchMusicApp
                        )
                    }

                    MotoDriveTab.ALERTS -> {
                        IphoneNotificationCenterScreen(
                            connectionState = iphoneBleState,
                            notifications = iphoneNotifications,
                            showPreviews = settings.showNotificationPreviews,
                            onConnectIphone = onConnectIphone,
                            onDismissNotification = onDismissIphoneNotification,
                            onClearAll = onClearAllIphoneNotifications
                        )
                    }

                    MotoDriveTab.PHONE -> {
                        PhoneConnectivityScreen(
                            uiState = connectivityUiState,
                            onQuickCall = onQuickCall
                        )
                    }

                    MotoDriveTab.APPS -> {
                        AppLauncherScreen(
                            onOpenNavigation = { activeTab = MotoDriveTab.NAVIGATION }
                        )
                    }

                    MotoDriveTab.TRIP -> {
                        TripComputerScreen(
                            speedUiState = speedUiState,
                            isRidingMode = isRidingMode,
                            onResetTrip = onResetTrip
                        )
                    }

                    MotoDriveTab.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            onSpeedUnitChange = onSpeedUnitChange,
                            onTempUnitChange = onTempUnitChange,
                            on24HourChange = on24HourChange,
                            onKeepScreenOnChange = onKeepScreenOnChange,
                            onBtAutoLaunchChange = onBtAutoLaunchChange,
                            onClusterStyleChange = onClusterStyleChange,
                            onThemeStyleChange = onThemeStyleChange,
                            onIphoneNotificationsChange = onIphoneNotificationsChange,
                            onShowNotificationPreviewsChange = onShowNotificationPreviewsChange,
                            onNotifyIncomingCallsChange = onNotifyIncomingCallsChange,
                            onNotifyWhatsAppChange = onNotifyWhatsAppChange,
                            onNotifyInstagramChange = onNotifyInstagramChange,
                            onNotifySmsChange = onNotifySmsChange,
                            onNotifySnapchatChange = onNotifySnapchatChange
                        )
                    }
                }
            }

            // BOTTOM QUICK ACCESS NAVIGATION BAR
            MSportBottomBar(
                currentTab = activeTab,
                onTabSelected = { activeTab = it },
                isRidingMode = isRidingMode,
                onToggleRidingMode = onToggleRidingMode,
                onTriggerVoice = onTriggerVoice,
                unreadAlertsCount = iphoneNotifications.size
            )
        }

        // MOTODRIVE VOICE ASSISTANT POPUP OVERLAY
        AnimatedVisibility(
            visible = voiceUiState.isListening,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.8f))
                    .clickable { onStopVoice() },
                contentAlignment = Alignment.Center
            ) {
                GlowCard(
                    modifier = Modifier.widthIn(min = 340.dp, max = 460.dp),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = colors.cardElevated,
                    borderColor = colors.electricAccent,
                    borderWidth = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.primaryAccent)
                                .border(2.dp, colors.electricAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listening",
                                tint = colors.primaryText,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "TVS RONIN VOICE",
                            style = MotoDriveTypography.labelLarge.copy(
                                color = colors.electricAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        Text(
                            text = voiceUiState.spokenText.ifBlank { "Listening for motorcycle command..." },
                            style = MotoDriveTypography.titleLarge.copy(fontSize = 18.sp),
                            color = colors.primaryText
                        )
                    }
                }
            }
        }
    }
}
