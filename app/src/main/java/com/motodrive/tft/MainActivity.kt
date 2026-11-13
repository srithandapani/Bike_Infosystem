package com.motodrive.tft

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.motodrive.tft.connectivity.ConnectivityViewModel
import com.motodrive.tft.connectivity.apple.AppleBleManager
import com.motodrive.tft.core.theme.MotoDriveTheme
import com.motodrive.tft.core.theme.LocalCockpitColors
import com.motodrive.tft.dashboard.DashboardMainScreen
import com.motodrive.tft.media.MediaViewModel
import com.motodrive.tft.navigation.NavigationViewModel
import com.motodrive.tft.settings.SettingsViewModel
import com.motodrive.tft.speedometer.SpeedometerViewModel
import com.motodrive.tft.voice.MotoDriveVoiceAssistant
import com.motodrive.tft.voice.VoiceCommandResult
import com.motodrive.tft.weather.WeatherViewModel

class MainActivity : ComponentActivity() {

    private val speedometerViewModel: SpeedometerViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()
    private val connectivityViewModel: ConnectivityViewModel by viewModels()
    private val weatherViewModel: WeatherViewModel by viewModels()
    private val voiceAssistant: MotoDriveVoiceAssistant by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var appleBleManager: AppleBleManager

    private var isRidingMode by mutableStateOf(false)

    // Automotive Permission Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            speedometerViewModel.startLocationUpdates()
        }
        connectivityViewModel.refreshBluetoothState()
        appleBleManager.connectToBondedIphone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize iPhone Apple BLE Manager (ANCS + AMS)
        appleBleManager = AppleBleManager(this)

        // Enable immersive full-screen sticky mode for dedicated motorcycle TFT feel
        setupImmersiveCockpitMode()

        // Keep screen on while mounted on motorcycle
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request necessary automotive permissions gracefully
        checkAndRequestPermissions()

        // Setup Voice Command Listener
        voiceAssistant.setCommandListener { command ->
            handleVoiceCommand(command)
        }

        // Bridge live GPS updates to In-App Navigation Engine
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                speedometerViewModel.uiState.collect { state ->
                    state.lastLocation?.let { loc ->
                        navigationViewModel.updateLocationForNavigation(loc)
                    }
                }
            }
        }

        // Keep speedometer unit in sync with persisted setting (was only synced on change)
        lifecycleScope.launch {
            settingsViewModel.settings.collect { speedometerViewModel.setSpeedUnit(it.speedUnit) }
        }

        // Surface navigation errors (e.g. destination not found, no route)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationViewModel.uiState.collect { st ->
                    st.errorMessage?.let {
                        android.widget.Toast.makeText(this@MainActivity, it, android.widget.Toast.LENGTH_LONG).show()
                        navigationViewModel.clearError()
                    }
                }
            }
        }

        // Auto-connect to paired iPhone on launch
        appleBleManager.connectToBondedIphone()

        setContent {
            val settings by settingsViewModel.settings.collectAsState()

            MotoDriveTheme(themeStyle = settings.themeStyle) {
                val speedUiState by speedometerViewModel.uiState.collectAsState()
                val navUiState by navigationViewModel.uiState.collectAsState()
                val mediaUiState by mediaViewModel.uiState.collectAsState()
                val connectivityUiState by connectivityViewModel.uiState.collectAsState()
                val weatherUiState by weatherViewModel.uiState.collectAsState()
                val voiceUiState by voiceAssistant.uiState.collectAsState()

                val iphoneBleState by appleBleManager.connectionState.collectAsState()
                val iphoneNotifications by appleBleManager.notifications.collectAsState()
                val iphoneTrack by appleBleManager.trackInfo.collectAsState()

                val colors = LocalCockpitColors.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background
                ) {
                    DashboardMainScreen(
                        speedUiState = speedUiState,
                        navUiState = navUiState,
                        mediaUiState = mediaUiState,
                        connectivityUiState = connectivityUiState,
                        weatherUiState = weatherUiState,
                        voiceUiState = voiceUiState,
                        settings = settings,
                        isRidingMode = isRidingMode,
                        iphoneBleState = iphoneBleState,
                        iphoneNotifications = iphoneNotifications,
                        iphoneTrack = iphoneTrack,
                        onConnectIphone = { appleBleManager.connectToBondedIphone() },
                        onDismissIphoneNotification = { appleBleManager.dismissNotification(it) },
                        onClearAllIphoneNotifications = { appleBleManager.clearAllNotifications() },
                        onToggleRidingMode = { isRidingMode = !isRidingMode },
                        onTriggerVoice = { voiceAssistant.startListening() },
                        onStopVoice = { voiceAssistant.stopListening() },
                        onTogglePlayPause = {
                            if (iphoneBleState.isConnected) {
                                appleBleManager.togglePlayPause()
                            } else {
                                mediaViewModel.togglePlayPause()
                            }
                        },
                        onNextTrack = {
                            if (iphoneBleState.isConnected) {
                                appleBleManager.nextTrack()
                            } else {
                                mediaViewModel.playNext()
                            }
                        },
                        onPreviousTrack = {
                            if (iphoneBleState.isConnected) {
                                appleBleManager.previousTrack()
                            } else {
                                mediaViewModel.playPrevious()
                            }
                        },
                        onSeekTrack = { mediaViewModel.seekTo(it) },
                        onLaunchMusicApp = { mediaViewModel.launchMusicApp(it) },
                        onStartNavigation = { navigationViewModel.startNavigation(it, speedUiState.lastLocation) },
                        onCancelNavigation = { navigationViewModel.cancelNavigation() },
                        onLaunchGoogleMaps = { destination ->
                            navigationViewModel.startNavigation(destination, speedUiState.lastLocation)
                        },
                        onResetTrip = { speedometerViewModel.resetTrip() },
                        onQuickCall = { connectivityViewModel.makeQuickCall(it) },
                        onSpeedUnitChange = {
                            settingsViewModel.updateSpeedUnit(it)
                            speedometerViewModel.setSpeedUnit(it)
                        },
                        onTempUnitChange = {
                            settingsViewModel.updateTemperatureUnit(it)
                            weatherViewModel.setUnit(it)
                        },
                        on24HourChange = { settingsViewModel.update24HourClock(it) },
                        onKeepScreenOnChange = { enabled ->
                            settingsViewModel.updateKeepScreenOn(enabled)
                            if (enabled) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        },
                        onBtAutoLaunchChange = { settingsViewModel.updateBluetoothAutoLaunch(it) },
                        onClusterStyleChange = { settingsViewModel.updateClusterStyle(it) },
                        onThemeStyleChange = { settingsViewModel.updateThemeStyle(it) },
                        onIphoneNotificationsChange = { settingsViewModel.updateIphoneNotificationsEnabled(it) },
                        onShowNotificationPreviewsChange = { settingsViewModel.updateShowNotificationPreviews(it) },
                        onNotifyIncomingCallsChange = { settingsViewModel.updateNotifyIncomingCalls(it) },
                        onNotifyWhatsAppChange = { settingsViewModel.updateNotifyWhatsApp(it) },
                        onNotifyInstagramChange = { settingsViewModel.updateNotifyInstagram(it) },
                        onNotifySmsChange = { settingsViewModel.updateNotifySms(it) },
                        onNotifySnapchatChange = { settingsViewModel.updateNotifySnapchat(it) }
                    )
                }
            }
        }
    }

    private fun handleVoiceCommand(command: VoiceCommandResult) {
        when (command) {
            is VoiceCommandResult.OpenNavigation -> {
                val dest = if (!command.destination.isNullOrEmpty()) command.destination else "Home"
                navigationViewModel.startNavigation(dest, speedometerViewModel.uiState.value.lastLocation)
            }
            is VoiceCommandResult.PlayMusic -> {
                if (appleBleManager.connectionState.value.isConnected) {
                    appleBleManager.play()
                } else {
                    mediaViewModel.togglePlayPause()
                }
            }
            is VoiceCommandResult.PauseMusic -> {
                if (appleBleManager.connectionState.value.isConnected) {
                    appleBleManager.pause()
                } else {
                    mediaViewModel.togglePlayPause()
                }
            }
            is VoiceCommandResult.NextSong -> {
                if (appleBleManager.connectionState.value.isConnected) {
                    appleBleManager.nextTrack()
                } else {
                    mediaViewModel.playNext()
                }
            }
            is VoiceCommandResult.ToggleRidingMode -> isRidingMode = !isRidingMode
            else -> {}
        }
    }

    private fun setupImmersiveCockpitMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            speedometerViewModel.startLocationUpdates()
            connectivityViewModel.refreshBluetoothState()
            appleBleManager.connectToBondedIphone()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appleBleManager.disconnect()
    }
}
