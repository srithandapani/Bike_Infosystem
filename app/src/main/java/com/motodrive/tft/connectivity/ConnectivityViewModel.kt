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
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ConnectedBtDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val isHelmet: Boolean
)

data class ConnectivityUiState(
    val isBluetoothEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val connectedDeviceName: String = "No Device Connected",
    val pairedDevices: List<ConnectedBtDevice> = emptyList(),
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
                    _uiState.update {
                        it.copy(
                            isBluetoothEnabled = isEnabled,
                            isConnected = if (!isEnabled) false else it.isConnected,
                            connectedDeviceName = if (!isEnabled) "Bluetooth Disabled" else it.connectedDeviceName
                        )
                    }
                    if (isEnabled) {
                        refreshBluetoothState()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
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
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        getApplication<Application>().registerReceiver(btReceiver, filter)
    }

    private fun bindBluetoothProfiles() {
        try {
            bluetoothAdapter?.getProfileProxy(getApplication(), profileListener, BluetoothProfile.A2DP)
            bluetoothAdapter?.getProfileProxy(getApplication(), profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshBluetoothState() {
        val adapter = bluetoothAdapter
        val isEnabled = adapter?.isEnabled == true
        val paired = mutableListOf<ConnectedBtDevice>()

        if (!isEnabled) {
            _uiState.update {
                it.copy(
                    isBluetoothEnabled = false,
                    isConnected = false,
                    connectedDeviceName = "Bluetooth Off",
                    pairedDevices = emptyList()
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
        val devName = connectedDevice?.name
            ?: paired.firstOrNull { it.isConnected }?.name
            ?: if (paired.isNotEmpty()) "Ready to Connect" else "No Devices Paired"

        _uiState.update {
            it.copy(
                isBluetoothEnabled = true,
                isConnected = hasConnected,
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

