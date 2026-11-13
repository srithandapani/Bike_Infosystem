package com.motodrive.tft

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
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

class MainActivity : FragmentActivity() {

    private val speedometerViewModel: SpeedometerViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()
    private val connectivityViewModel: ConnectivityViewModel by viewModels()
    private val weatherViewModel: WeatherViewModel by viewModels()
    private val voiceAssistant: MotoDriveVoiceAssistant by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var appleBleManager: AppleBleManager

    private var isRidingMode by mutableStateOf(false)

    // Google Play Services Location Resolution Launcher
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            speedometerViewModel.startLocationUpdates()
        }
    }

    // Official System Bluetooth Enable Request Launcher
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            connectivityViewModel.initializeBluetoothReadiness()
        } else {
            connectivityViewModel.refreshBluetoothState()
        }
    }

    // Automotive Permission Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            checkLocationSettingsAndStart()
            navigationViewModel.initNavigator(this@MainActivity)
        }
        val btConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else true
        if (btConnectGranted) {
            checkAndPromptBluetooth()
        } else {
            connectivityViewModel.refreshBluetoothState()
        }
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

        // Initialize Google Navigation SDK
        navigationViewModel.initNavigator(this)

        // Setup Voice Command Listener
        voiceAssistant.setCommandListener { command ->
            handleVoiceCommand(command)
        }

        // Bridge live GPS updates to In-App Navigation Engine & Weather Service
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                speedometerViewModel.uiState.collect { state ->
                    state.lastLocation?.let { loc ->
                        navigationViewModel.updateCurrentLocation(loc)
                        weatherViewModel.fetchWeatherForCoordinates(loc.latitude, loc.longitude)
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

        // Sync Bluetooth & iPhone connection state to MediaViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectivityViewModel.uiState.collect { conn ->
                        val iphoneConnected = appleBleManager.connectionState.value.isConnected
                        val isConnected = conn.isConnected || iphoneConnected
                        val devName = if (iphoneConnected) appleBleManager.connectionState.value.deviceName else conn.connectedDeviceName
                        mediaViewModel.updateBluetoothStatus(isConnected, devName)
                    }
                }
                launch {
                    appleBleManager.connectionState.collect { iphoneState ->
                        if (iphoneState.isConnected) {
                            mediaViewModel.updateBluetoothStatus(true, iphoneState.deviceName)
                        } else {
                            val conn = connectivityViewModel.uiState.value
                            mediaViewModel.updateBluetoothStatus(conn.isConnected, conn.connectedDeviceName)
                        }
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
                        onOpenNotificationAccess = { mediaViewModel.openNotificationSettings(this@MainActivity) },
                        onStartNavigation = { navigationViewModel.startNavigation(it) },
                        onStartPlaceNavigation = { navigationViewModel.startNavigationToPlace(it) },
                        onCancelNavigation = { navigationViewModel.cancelNavigation() },
                        onSaveLocationAs = { navigationViewModel.saveCurrentLocationAs(it) },
                        onSearchQueryChange = { navigationViewModel.updateSearchQuery(it) },
                        onLaunchGoogleMaps = { destination ->
                            navigationViewModel.startNavigation(destination)
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
                        onNotifySnapchatChange = { settingsViewModel.updateNotifySnapchat(it) },
                        onConnectBtDevice = { connectivityViewModel.connectToDevice(it) },
                        onStartBtDiscovery = { connectivityViewModel.startDiscovery() },
                        onCancelBtDiscovery = { connectivityViewModel.cancelDiscovery() }
                    )
                }
            }
        }
    }

    private fun handleVoiceCommand(command: VoiceCommandResult) {
        when (command) {
            is VoiceCommandResult.OpenNavigation -> {
                val dest = if (!command.destination.isNullOrEmpty()) command.destination else "Home"
                navigationViewModel.startNavigation(dest)
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

    private fun checkLocationSettingsAndStart() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                speedometerViewModel.startLocationUpdates()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        try {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {}
                    }
                } else {
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
    }

    private fun checkAndPromptBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            if (!adapter.isEnabled) {
                try {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            } else {
                connectivityViewModel.initializeBluetoothReadiness()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

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
            checkLocationSettingsAndStart()
            checkAndPromptBluetooth()
            connectivityViewModel.refreshBluetoothState()
            appleBleManager.connectToBondedIphone()
            navigationViewModel.initNavigator(this)
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationSettingsAndStart()
        checkAndPromptBluetooth()
        connectivityViewModel.refreshBluetoothState()
        mediaViewModel.refreshNotificationAccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        appleBleManager.disconnect()
    }
}
