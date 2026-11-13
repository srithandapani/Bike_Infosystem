package com.motodrive.tft.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.motodrive.tft.core.components.*
import com.motodrive.tft.core.theme.*
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.launcher.AppLauncherScreen
import com.motodrive.tft.media.FullScreenMediaScreen
import com.motodrive.tft.media.MediaPlayerCompactCard
import com.motodrive.tft.media.MediaUiState
import com.motodrive.tft.navigation.NavigationMapView
import com.motodrive.tft.navigation.NavigationUiState
import com.motodrive.tft.navigation.PlaceSearchResult
import com.motodrive.tft.riding.RidingModeScreen
import com.motodrive.tft.settings.AppSettings
import com.motodrive.tft.settings.SettingsScreen
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
    onStartPlaceNavigation: (PlaceSearchResult) -> Unit = {},
    onCancelNavigation: () -> Unit,
    onSaveLocationAs: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
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

    // Auto riding mode detection: above 5 km/h, auto-simplify cockpit to minimize distraction
    val isMoving by remember(speedUiState.currentSpeedKmH) {
        derivedStateOf { speedUiState.currentSpeedKmH > 5.0f }
    }

    // Full Riding Cockpit View
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
            // THIN CINEMATIC STATUS BAR (Top)
            StatusBar(
                currentTimeMillis = System.currentTimeMillis(),
                is24Hour = settings.is24HourClock,
                isGpsLocked = speedUiState.hasGpsFix,
                satellitesCount = if (speedUiState.hasGpsFix) 12 else 0,
                iphoneBleState = iphoneBleState,
                weatherState = weatherUiState,
                isRidingMode = isMoving
            )

            // MAIN COCKPIT VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "TabTransition"
                ) { target ->
                    when (target) {
                        MotoDriveTab.NAVIGATION -> {
                            // Cinematic Landscape Cockpit: ~65% Map / Navigation + ~35% Speed & Guidance
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Left 65%: Embedded Google Navigation / Map View
                                NavigationMapView(
                                    currentLocation = speedUiState.lastLocation,
                                    currentSpeedKmH = speedUiState.currentSpeedKmH,
                                    uiState = navUiState,
                                    onStartNavigation = onStartNavigation,
                                    onStartPlaceNavigation = onStartPlaceNavigation,
                                    onCancelNavigation = onCancelNavigation,
                                    onSaveLocationAs = onSaveLocationAs,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onVoiceClick = onTriggerVoice,
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .fillMaxHeight()
                                )

                                // Right 35%: TVS Ronin Speedometer Cluster & Turn Guidance
                                Column(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Turn Guidance Card (If Navigating)
                                    if (navUiState.isNavigating) {
                                        TurnCard(
                                            direction = navUiState.nextTurnDirection,
                                            distanceToTurn = navUiState.distanceToNextTurn,
                                            roadName = navUiState.nextRoadName,
                                            remainingKm = navUiState.remainingDistanceKm,
                                            eta = navUiState.estimatedArrivalTime,
                                            speedUnit = settings.speedUnit,
                                            isNavigating = true
                                        )
                                    }

                                    // Cinematic Speedometer Gauge
                                    SpeedGauge(
                                        speed = Units.speed(speedUiState.currentSpeedKmH, settings.speedUnit),
                                        speedUnit = settings.speedUnit,
                                        clusterStyle = settings.clusterStyle,
                                        speedLimitKmH = navUiState.speedLimitKmH,
                                        tripDistance = Units.distance(speedUiState.tripDistanceKm, settings.speedUnit),
                                        averageSpeed = Units.speed(speedUiState.averageSpeedKmH, settings.speedUnit),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Compact Media Player (Hidden when moving to maintain motorcycle focus)
                                    if (!isMoving && !navUiState.isNavigating) {
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
            }

            // MINIMAL COCKPIT BOTTOM NAVIGATION BAR
            MSportBottomBar(
                currentTab = activeTab,
                onTabSelected = { activeTab = it },
                isRidingMode = isRidingMode,
                onToggleRidingMode = onToggleRidingMode,
                onTriggerVoice = onTriggerVoice,
                unreadAlertsCount = iphoneNotifications.size
            )
        }

        // TVS RONIN VOICE ASSISTANT POPUP OVERLAY
        AnimatedVisibility(
            visible = voiceUiState.isListening,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { onStopVoice() },
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.widthIn(min = 340.dp, max = 460.dp),
                    cornerRadius = 24.dp,
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
                                letterSpacing = 2.sp
                            )
                        )

                        Text(
                            text = voiceUiState.spokenText.ifBlank { "Speak destination or command..." },
                            style = MotoDriveTypography.titleLarge.copy(fontSize = 18.sp),
                            color = colors.primaryText
                        )
                    }
                }
            }
        }
    }
}
