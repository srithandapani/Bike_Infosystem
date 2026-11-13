package com.motodrive.tft.connectivity

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
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
    val isBluetoothEnabled: Boolean = true,
    val connectedDeviceName: String = "Cardo Packtalk Edge",
    val pairedDevices: List<ConnectedBtDevice> = listOf(
        ConnectedBtDevice("Cardo Packtalk Edge", "00:1B:44:11:3A:B7", true, true),
        ConnectedBtDevice("Sena 50S Helmet", "AA:BB:CC:DD:EE:01", false, true),
        ConnectedBtDevice("BMW OBDLink LX", "11:22:33:44:55:66", false, false)
    ),
    val networkType: String = "5G LTE",
    val isNetworkConnected: Boolean = true,
    val incomingCallerName: String? = null,
    val incomingCallerNumber: String? = null,
    val isCallActive: Boolean = false
)

class ConnectivityViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _uiState = MutableStateFlow(ConnectivityUiState())
    val uiState: StateFlow<ConnectivityUiState> = _uiState.asStateFlow()

    init {
        refreshBluetoothState()
        refreshNetworkState()
        registerPhoneStateListener()
    }

    fun refreshBluetoothState() {
        val adapter = bluetoothAdapter ?: return
        val isEnabled = adapter.isEnabled
        val paired = mutableListOf<ConnectedBtDevice>()

        try {
            if (isEnabled) {
                val bonded = adapter.bondedDevices
                bonded?.forEach { dev ->
                    val name = dev.name ?: "Bluetooth Device"
                    val isHelmet = name.contains("Cardo", ignoreCase = true) ||
                            name.contains("Sena", ignoreCase = true) ||
                            name.contains("Helmet", ignoreCase = true) ||
                            name.contains("Headset", ignoreCase = true)
                    paired.add(
                        ConnectedBtDevice(
                            name = name,
                            address = dev.address,
                            isConnected = false,
                            isHelmet = isHelmet
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        if (paired.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isBluetoothEnabled = isEnabled,
                    pairedDevices = paired,
                    connectedDeviceName = paired.firstOrNull()?.name ?: "Motorcycle Audio"
                )
            }
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
}

