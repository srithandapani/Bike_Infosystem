package com.motodrive.tft.connectivity

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BluetoothConnectionStatus(val label: String) {
    BLUETOOTH_OFF("Bluetooth OFF"),
    BLUETOOTH_ON("Bluetooth ON"),
    SEARCHING("Searching for Devices"),
    READY_TO_CONNECT("Ready to Connect"),
    PAIRING("Pairing Device"),
    CONNECTED("Connected"),
    DISCONNECTED("Disconnected")
}

data class ConnectedBtDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val isHelmet: Boolean,
    val isBonding: Boolean = false
)

data class ConnectivityUiState(
    val isBluetoothEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val bluetoothStatus: BluetoothConnectionStatus = BluetoothConnectionStatus.BLUETOOTH_OFF,
    val connectedDeviceName: String = "No Device Connected",
    val isSearching: Boolean = false,
    val pairedDevices: List<ConnectedBtDevice> = emptyList(),
    val discoveredDevices: List<ConnectedBtDevice> = emptyList(),
    val networkType: String = "5G LTE",
    val isNetworkConnected: Boolean = true,
    val incomingCallerName: String? = null,
    val incomingCallerNumber: String? = null,
    val isCallActive: Boolean = false
)

@SuppressLint("MissingPermission")
class ConnectivityViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var a2dpProfile: BluetoothA2dp? = null
    private var headsetProfile: BluetoothHeadset? = null
    private val discoveredDevicesList = mutableListOf<ConnectedBtDevice>()

    var onDeviceBonded: ((BluetoothDevice) -> Unit)? = null

    private val _uiState = MutableStateFlow(ConnectivityUiState())
    val uiState: StateFlow<ConnectivityUiState> = _uiState.asStateFlow()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProfile = proxy as? BluetoothHeadset
            }
            refreshBluetoothState()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
            }
            refreshBluetoothState()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isEnabled = state == BluetoothAdapter.STATE_ON
                    if (!isEnabled) {
                        _uiState.update {
                            it.copy(
                                isBluetoothEnabled = false,
                                isConnected = false,
                                isSearching = false,
                                bluetoothStatus = BluetoothConnectionStatus.BLUETOOTH_OFF,
                                connectedDeviceName = "Bluetooth OFF",
                                pairedDevices = emptyList(),
                                discoveredDevices = emptyList()
                            )
                        }
                    } else {
                        bindBluetoothProfiles()
                        initializeBluetoothReadiness()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _uiState.update {
                        it.copy(
                            isSearching = true,
                            bluetoothStatus = BluetoothConnectionStatus.SEARCHING
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _uiState.update { it.copy(isSearching = false) }
                    refreshBluetoothState()
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    dev?.let { handleDiscoveredDevice(it, extraName) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    dev?.let { handleBondStateChanged(it, bondState, prevBondState) }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            bluetoothStatus = BluetoothConnectionStatus.DISCONNECTED,
                            connectedDeviceName = "Disconnected"
                        )
                    }
                    refreshBluetoothState(keepDisconnectedStatus = true)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshBluetoothState()
                }
            }
        }
    }

    init {
        registerBluetoothReceiver()
        bindBluetoothProfiles()
        refreshBluetoothState()
        refreshNetworkState()
        registerPhoneStateListener()
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        getApplication<Application>().registerReceiver(btReceiver, filter)
    }

    private fun handleBondStateChanged(dev: BluetoothDevice, bondState: Int, prevBondState: Int) {
        val devName = dev.name ?: "Bluetooth Device"
        when (bondState) {
            BluetoothDevice.BOND_BONDING -> {
                _uiState.update { state ->
                    val updatedDiscovered = state.discoveredDevices.map {
                        if (it.address == dev.address) it.copy(isBonding = true) else it
                    }
                    state.copy(
                        bluetoothStatus = BluetoothConnectionStatus.PAIRING,
                        connectedDeviceName = "Pairing with $devName...",
                        discoveredDevices = updatedDiscovered
                    )
                }
            }
            BluetoothDevice.BOND_BONDED -> {
                discoveredDevicesList.removeAll { it.address == dev.address }
                refreshBluetoothState()
                _uiState.update { state ->
                    state.copy(
                        discoveredDevices = discoveredDevicesList.toList(),
                        bluetoothStatus = BluetoothConnectionStatus.READY_TO_CONNECT,
                        connectedDeviceName = "Paired with $devName. Connecting..."
                    )
                }
                connectProfiles(dev)
                onDeviceBonded?.invoke(dev)
            }
            BluetoothDevice.BOND_NONE -> {
                discoveredDevicesList.removeAll { it.address == dev.address }
                refreshBluetoothState()
                _uiState.update { state ->
                    state.copy(discoveredDevices = discoveredDevicesList.toList())
                }
            }
        }
    }

    private fun handleDiscoveredDevice(dev: BluetoothDevice, extraName: String? = null) {
        val addr = dev.address ?: return
        // Do not add already bonded devices to nearby discovered list
        val isAlreadyBonded = bluetoothAdapter?.bondedDevices?.any { it.address == addr } == true
        if (isAlreadyBonded) return

        val rawName = dev.name ?: extraName
        val name = if (!rawName.isNullOrBlank()) rawName else "Nearby Bluetooth Device"
        val isHelmet = name.contains("Cardo", ignoreCase = true) ||
                name.contains("Sena", ignoreCase = true) ||
                name.contains("Helmet", ignoreCase = true) ||
                name.contains("Headset", ignoreCase = true)

        val existingIndex = discoveredDevicesList.indexOfFirst { it.address == addr }
        if (existingIndex >= 0) {
            val existing = discoveredDevicesList[existingIndex]
            if (existing.name == "Nearby Bluetooth Device" && name != "Nearby Bluetooth Device") {
                discoveredDevicesList[existingIndex] = existing.copy(name = name, isHelmet = isHelmet)
                _uiState.update { it.copy(discoveredDevices = discoveredDevicesList.toList()) }
            }
        } else {
            val isBonding = dev.bondState == BluetoothDevice.BOND_BONDING
            discoveredDevicesList.add(ConnectedBtDevice(name, addr, false, isHelmet, isBonding = isBonding))
            _uiState.update { it.copy(discoveredDevices = discoveredDevicesList.toList()) }
        }
    }

    fun startDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            discoveredDevicesList.clear()
            adapter.startDiscovery()
            _uiState.update {
                it.copy(
                    isSearching = true,
                    bluetoothStatus = BluetoothConnectionStatus.SEARCHING,
                    discoveredDevices = emptyList()
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            _uiState.update { it.copy(isSearching = false) }
        } catch (_: SecurityException) {}
    }

    fun connectToDevice(device: ConnectedBtDevice, fromUserClick: Boolean = true) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        try {
            // Cancel discovery before initiating bond or profile connection to prevent HCI timeouts
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            _uiState.update { it.copy(isSearching = false) }

            val remoteDevice = adapter.getRemoteDevice(device.address)
            if (remoteDevice.bondState == BluetoothDevice.BOND_NONE) {
                val bondInitiated = remoteDevice.createBond()
                if (bondInitiated) {
                    _uiState.update { state ->
                        val updatedDiscovered = state.discoveredDevices.map {
                            if (it.address == device.address) it.copy(isBonding = true) else it
                        }
                        state.copy(
                            bluetoothStatus = BluetoothConnectionStatus.PAIRING,
                            connectedDeviceName = "Pairing with ${device.name}...",
                            discoveredDevices = updatedDiscovered
                        )
                    }
                }
                return
            }

            _uiState.update {
                it.copy(
                    bluetoothStatus = BluetoothConnectionStatus.READY_TO_CONNECT,
                    connectedDeviceName = "Connecting to ${device.name}..."
                )
            }

            connectProfiles(remoteDevice)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun connectProfiles(remoteDevice: BluetoothDevice): Boolean {
        var invoked = false
        try {
            if (a2dpProfile == null || headsetProfile == null) {
                bindBluetoothProfiles()
            }

            // 1. Try A2DP connect
            val a2dp = a2dpProfile
            if (a2dp != null) {
                try {
                    val connectA2dpMethod = a2dp.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectA2dpMethod.isAccessible = true
                    val res = connectA2dpMethod.invoke(a2dp, remoteDevice) as? Boolean ?: true
                    if (res) invoked = true
                } catch (_: Exception) {}
            }

            // 2. Try Headset connect
            val headset = headsetProfile
            if (headset != null) {
                try {
                    val connectHeadsetMethod = headset.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectHeadsetMethod.isAccessible = true
                    val res = connectHeadsetMethod.invoke(headset, remoteDevice) as? Boolean ?: true
                    if (res) invoked = true
                } catch (_: Exception) {}
            }

            // 3. Fallback on Android 11+ via BluetoothDevice.connect()
            if (!invoked) {
                try {
                    val devConnectMethod = remoteDevice.javaClass.getMethod("connect")
                    devConnectMethod.isAccessible = true
                    val res = devConnectMethod.invoke(remoteDevice) as? Boolean ?: false
                    if (res) invoked = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return invoked
    }

    fun disconnectDevice(device: ConnectedBtDevice) {
        val adapter = bluetoothAdapter ?: return
        try {
            val remoteDevice = adapter.getRemoteDevice(device.address)
            var disconnected = false

            val a2dp = a2dpProfile
            if (a2dp != null) {
                try {
                    val disconnectA2dpMethod = a2dp.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    disconnectA2dpMethod.isAccessible = true
                    disconnectA2dpMethod.invoke(a2dp, remoteDevice)
                    disconnected = true
                } catch (_: Exception) {}
            }

            val headset = headsetProfile
            if (headset != null) {
                try {
                    val disconnectHeadsetMethod = headset.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    disconnectHeadsetMethod.isAccessible = true
                    disconnectHeadsetMethod.invoke(headset, remoteDevice)
                    disconnected = true
                } catch (_: Exception) {}
            }

            if (!disconnected) {
                try {
                    val devDisconnectMethod = remoteDevice.javaClass.getMethod("disconnect")
                    devDisconnectMethod.isAccessible = true
                    devDisconnectMethod.invoke(remoteDevice)
                } catch (_: Exception) {}
            }

            _uiState.update {
                it.copy(
                    isConnected = false,
                    bluetoothStatus = BluetoothConnectionStatus.DISCONNECTED,
                    connectedDeviceName = "Disconnected"
                )
            }
            refreshBluetoothState(keepDisconnectedStatus = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unpairDevice(device: ConnectedBtDevice) {
        val adapter = bluetoothAdapter ?: return
        try {
            val remoteDevice = adapter.getRemoteDevice(device.address)
            val removeBondMethod = remoteDevice.javaClass.getMethod("removeBond")
            removeBondMethod.isAccessible = true
            removeBondMethod.invoke(remoteDevice)
            refreshBluetoothState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initializeBluetoothReadiness() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            refreshBluetoothState()
            return
        }
        refreshBluetoothState()
        // If not connected to any device, prepare connection readiness
        if (!_uiState.value.isConnected) {
            val preferredDevice = _uiState.value.pairedDevices.firstOrNull { it.isHelmet }
                ?: _uiState.value.pairedDevices.firstOrNull()
            if (preferredDevice != null) {
                connectToDevice(preferredDevice, fromUserClick = false)
            }
            startDiscovery()
        }
    }

    private fun bindBluetoothProfiles() {
        try {
            bluetoothAdapter?.getProfileProxy(getApplication(), profileListener, BluetoothProfile.A2DP)
            bluetoothAdapter?.getProfileProxy(getApplication(), profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshBluetoothState(keepDisconnectedStatus: Boolean = false) {
        val adapter = bluetoothAdapter
        val isEnabled = adapter?.isEnabled == true
        val paired = mutableListOf<ConnectedBtDevice>()

        if (!isEnabled) {
            _uiState.update {
                it.copy(
                    isBluetoothEnabled = false,
                    isConnected = false,
                    isSearching = false,
                    bluetoothStatus = BluetoothConnectionStatus.BLUETOOTH_OFF,
                    connectedDeviceName = "Bluetooth OFF",
                    pairedDevices = emptyList(),
                    discoveredDevices = emptyList()
                )
            }
            return
        }

        var connectedDevice: BluetoothDevice? = null

        try {
            // Check actively connected A2DP and Headset devices
            val connectedA2dp = a2dpProfile?.connectedDevices.orEmpty()
            val connectedHeadset = headsetProfile?.connectedDevices.orEmpty()
            val allConnected = (connectedA2dp + connectedHeadset).distinctBy { it.address }
            connectedDevice = allConnected.firstOrNull()

            val bonded = adapter?.bondedDevices
            bonded?.forEach { dev ->
                val name = dev.name ?: "Bluetooth Device"
                val isHelmet = name.contains("Cardo", ignoreCase = true) ||
                        name.contains("Sena", ignoreCase = true) ||
                        name.contains("Helmet", ignoreCase = true) ||
                        name.contains("Headset", ignoreCase = true)
                val isConnected = allConnected.any { it.address == dev.address }

                paired.add(
                    ConnectedBtDevice(
                        name = name,
                        address = dev.address,
                        isConnected = isConnected,
                        isHelmet = isHelmet
                    )
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hasConnected = connectedDevice != null || paired.any { it.isConnected }
        val devName = when {
            hasConnected -> connectedDevice?.name ?: paired.firstOrNull { it.isConnected }?.name ?: "Connected"
            _uiState.value.bluetoothStatus == BluetoothConnectionStatus.PAIRING -> _uiState.value.connectedDeviceName
            keepDisconnectedStatus -> "Disconnected"
            paired.isNotEmpty() -> "Ready to Connect"
            else -> "Bluetooth ON"
        }

        val status = when {
            !isEnabled -> BluetoothConnectionStatus.BLUETOOTH_OFF
            hasConnected -> BluetoothConnectionStatus.CONNECTED
            _uiState.value.bluetoothStatus == BluetoothConnectionStatus.PAIRING -> BluetoothConnectionStatus.PAIRING
            _uiState.value.isSearching || adapter?.isDiscovering == true -> BluetoothConnectionStatus.SEARCHING
            keepDisconnectedStatus -> BluetoothConnectionStatus.DISCONNECTED
            paired.isNotEmpty() -> BluetoothConnectionStatus.READY_TO_CONNECT
            else -> BluetoothConnectionStatus.BLUETOOTH_ON
        }

        _uiState.update {
            it.copy(
                isBluetoothEnabled = true,
                isConnected = hasConnected,
                bluetoothStatus = status,
                connectedDeviceName = devName,
                pairedDevices = paired
            )
        }
    }

    private fun refreshNetworkState() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val hasNet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "5G"
            else -> "OFFLINE"
        }

        _uiState.update {
            it.copy(
                isNetworkConnected = hasNet,
                networkType = netType
            )
        }
    }

    private fun registerPhoneStateListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager?.registerTelephonyCallback(
                    getApplication<Application>().mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallState(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(
                    object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            handleCallState(state, phoneNumber)
                        }
                    },
                    PhoneStateListener.LISTEN_CALL_STATE
                )
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _uiState.update {
                    it.copy(
                        incomingCallerName = "Incoming Call",
                        incomingCallerNumber = phoneNumber ?: "Unknown Contact",
                        isCallActive = false
                    )
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                _uiState.update {
                    it.copy(
                        isCallActive = true
                    )
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                _uiState.update {
                    it.copy(
                        incomingCallerName = null,
                        incomingCallerNumber = null,
                        isCallActive = false
                    )
                }
            }
        }
    }

    fun makeQuickCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
        try {
            a2dpProfile?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProfile?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        } catch (_: Exception) {}
    }
}

