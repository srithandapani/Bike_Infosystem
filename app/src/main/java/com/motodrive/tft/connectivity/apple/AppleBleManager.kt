package com.motodrive.tft.connectivity.apple

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class AppleBleManager(private val context: Context) {

    private val tag = "AppleBleManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var activeGatt: BluetoothGatt? = null
    private var isSubscribedToAncs = false
    private var isSubscribedToAms = false

    private val _connectionState = MutableStateFlow(IphoneBleConnectionState())
    val connectionState: StateFlow<IphoneBleConnectionState> = _connectionState.asStateFlow()

    private val _notifications = MutableStateFlow<List<IphoneNotificationItem>>(emptyList())
    val notifications: StateFlow<List<IphoneNotificationItem>> = _notifications.asStateFlow()

    private val _trackInfo = MutableStateFlow(IphoneTrackInfo())
    val trackInfo: StateFlow<IphoneTrackInfo> = _trackInfo.asStateFlow()

    // Characteristic references
    private var ancsControlPointChar: BluetoothGattCharacteristic? = null
    private var amsRemoteCommandChar: BluetoothGattCharacteristic? = null
    private var amsEntityUpdateChar: BluetoothGattCharacteristic? = null

    // Buffer for ANCS Data Source multi-packet responses
    private val ancsDataBuffer = ByteArrayOutputStream()
    private var expectedAttributeBytes = 0

    // Sequential GATT execution queue
    private val gattQueue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
    @Volatile
    private var isQueueBusy = false

    private fun enqueueGatt(action: () -> Unit) {
        gattQueue.add(Runnable { action() })
        processNextGatt()
    }

    private fun processNextGatt() {
        synchronized(gattQueue) {
            if (isQueueBusy) return
            val next = gattQueue.poll() ?: return
            isQueueBusy = true
            try {
                next.run()
            } catch (e: Exception) {
                Log.e(tag, "Error running GATT operation", e)
                isQueueBusy = false
                processNextGatt()
            }
        }
    }

    private fun advanceGattQueue() {
        synchronized(gattQueue) {
            isQueueBusy = false
            processNextGatt()
        }
    }

    private fun writeDescriptorSafely(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: Exception) {
            Log.e(tag, "writeDescriptor error", e)
            advanceGattQueue()
        }
    }

    private fun writeCharacteristicSafely(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.e(tag, "writeCharacteristic error", e)
            advanceGattQueue()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "Connected to iPhone GATT server: ${gatt.device.name}")
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    _connectionState.update {
                        it.copy(
                            isConnected = true,
                            deviceName = gatt.device.name ?: "Apple iPhone",
                            deviceAddress = gatt.device.address,
                            statusMessage = "Connected. Setting up Apple ANCS & Music..."
                        )
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "Disconnected from iPhone. GATT status=$status")
                    _connectionState.update {
                        it.copy(
                            isConnected = false,
                            isAncsReady = false,
                            isAmsReady = false,
                            statusMessage = "iPhone disconnected. Ready to reconnect."
                        )
                    }
                    isSubscribedToAncs = false
                    isSubscribedToAms = false
                    synchronized(gattQueue) {
                        gattQueue.clear()
                        isQueueBusy = false
                    }
                    try {
                        gatt.close()
                    } catch (_: Exception) {}
                    if (activeGatt == gatt) {
                        activeGatt = null
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(tag, "Service discovery failed with status $status")
                return
            }

            Log.i(tag, "Services discovered. Setting up ANCS and AMS...")
            val ancsService = gatt.getService(AppleBleConstants.ANCS_SERVICE_UUID)
            val amsService = gatt.getService(AppleBleConstants.AMS_SERVICE_UUID)

            var ancsFound = false
            var amsFound = false

            if (ancsService != null) {
                ancsFound = true
                ancsControlPointChar = ancsService.getCharacteristic(AppleBleConstants.ANCS_CONTROL_POINT_UUID)
                val notifSource = ancsService.getCharacteristic(AppleBleConstants.ANCS_NOTIFICATION_SOURCE_UUID)
                val dataSource = ancsService.getCharacteristic(AppleBleConstants.ANCS_DATA_SOURCE_UUID)

                if (notifSource != null) {
                    enqueueGatt {
                        gatt.setCharacteristicNotification(notifSource, true)
                        val descriptor = notifSource.getDescriptor(AppleBleConstants.CCCD_UUID)
                        if (descriptor != null) {
                            writeDescriptorSafely(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            advanceGattQueue()
                        }
                    }
                }

                if (dataSource != null) {
                    enqueueGatt {
                        gatt.setCharacteristicNotification(dataSource, true)
                        val descriptor = dataSource.getDescriptor(AppleBleConstants.CCCD_UUID)
                        if (descriptor != null) {
                            writeDescriptorSafely(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            advanceGattQueue()
                        }
                    }
                }
            }

            if (amsService != null) {
                amsFound = true
                amsRemoteCommandChar = amsService.getCharacteristic(AppleBleConstants.AMS_REMOTE_COMMAND_UUID)
                val entityUpdate = amsService.getCharacteristic(AppleBleConstants.AMS_ENTITY_UPDATE_UUID)
                amsEntityUpdateChar = entityUpdate

                if (entityUpdate != null) {
                    // 1. Enable notifications on AMS Entity Update
                    enqueueGatt {
                        gatt.setCharacteristicNotification(entityUpdate, true)
                        val descriptor = entityUpdate.getDescriptor(AppleBleConstants.CCCD_UUID)
                        if (descriptor != null) {
                            writeDescriptorSafely(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            advanceGattQueue()
                        }
                    }

                    // 2. Subscribe to Track attributes: Title (2), Artist (0), Album (1), Duration (3)
                    enqueueGatt {
                        val trackSub = byteArrayOf(
                            AppleBleConstants.ENTITY_ID_TRACK,
                            AppleBleConstants.TRACK_ATTR_TITLE,
                            AppleBleConstants.TRACK_ATTR_ARTIST,
                            AppleBleConstants.TRACK_ATTR_ALBUM,
                            AppleBleConstants.TRACK_ATTR_DURATION
                        )
                        writeCharacteristicSafely(gatt, entityUpdate, trackSub)
                    }

                    // 3. Subscribe to Player attributes: PlaybackInfo (1)
                    enqueueGatt {
                        val playerSub = byteArrayOf(
                            AppleBleConstants.ENTITY_ID_PLAYER,
                            1.toByte() // PlaybackInfo
                        )
                        writeCharacteristicSafely(gatt, entityUpdate, playerSub)
                    }
                }
            }

            _connectionState.update {
                it.copy(
                    isConnected = true,
                    isAncsReady = ancsFound,
                    isAmsReady = amsFound,
                    statusMessage = if (ancsFound && amsFound) "iPhone Connected (ANCS & Music Ready)"
                                   else if (ancsFound) "iPhone Connected (ANCS Active)"
                                   else if (amsFound) "iPhone Connected (Music Active)"
                                   else "Connected to iPhone"
                )
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            advanceGattQueue()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            advanceGattQueue()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            dispatchCharacteristicChanged(gatt, characteristic.uuid, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            dispatchCharacteristicChanged(gatt, characteristic.uuid, value)
        }

        private fun dispatchCharacteristicChanged(
            gatt: BluetoothGatt,
            charUuid: UUID,
            value: ByteArray
        ) {
            when (charUuid) {
                AppleBleConstants.ANCS_NOTIFICATION_SOURCE_UUID -> {
                    handleAncsNotificationSource(gatt, value)
                }
                AppleBleConstants.ANCS_DATA_SOURCE_UUID -> {
                    handleAncsDataSource(value)
                }
                AppleBleConstants.AMS_ENTITY_UPDATE_UUID -> {
                    handleAmsEntityUpdate(value)
                }
            }
        }
    }

    /**
     * Parses incoming 8-byte ANCS Notification Source events
     */
    private fun handleAncsNotificationSource(gatt: BluetoothGatt, data: ByteArray) {
        if (data.size < 8) return

        val eventId = data[0].toInt()
        val categoryId = data[2].toInt()
        val notifUid = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

        when (eventId) {
            AppleBleConstants.EVENT_ID_NOTIFICATION_ADDED,
            AppleBleConstants.EVENT_ID_NOTIFICATION_MODIFIED -> {
                // Request detailed attributes (App ID, Title, Message) via Control Point
                requestNotificationAttributes(gatt, notifUid, categoryId)
            }
            AppleBleConstants.EVENT_ID_NOTIFICATION_REMOVED -> {
                _notifications.update { list ->
                    list.filterNot { it.notificationUid == notifUid }
                }
            }
        }
    }

    private fun requestNotificationAttributes(gatt: BluetoothGatt, uid: Int, categoryId: Int) {
        val cp = ancsControlPointChar ?: return
        try {
            val cmd = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
            cmd.put(AppleBleConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
            cmd.putInt(uid)

            // Attribute: App Identifier (ID 0)
            cmd.put(AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_APP_IDENTIFIER)
            // Attribute: Title (ID 1) with max length 64
            cmd.put(AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_TITLE)
            cmd.putShort(64.toShort())
            // Attribute: Message (ID 3) with max length 128
            cmd.put(AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_MESSAGE)
            cmd.putShort(128.toShort())

            writeCharacteristicSafely(gatt, cp, cmd.array())
        } catch (e: Exception) {
            Log.e(tag, "Failed to request notification attributes", e)
        }
    }

    /**
     * Handles incoming notification text attributes from ANCS Data Source
     */
    private fun handleAncsDataSource(data: ByteArray) {
        try {
            ancsDataBuffer.write(data)
            val fullData = ancsDataBuffer.toByteArray()
            if (fullData.size < 5) return

            val buffer = ByteBuffer.wrap(fullData).order(ByteOrder.LITTLE_ENDIAN)
            val cmdId = buffer.get()
            if (cmdId != AppleBleConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES) return

            val notifUid = buffer.int
            var appIdentifier = ""
            var title = "New Notification"
            var message = ""

            while (buffer.hasRemaining()) {
                val attrId = buffer.get()
                if (!buffer.hasRemaining()) break
                val attrLen = buffer.short.toInt()
                if (buffer.remaining() < attrLen) {
                    // Packet incomplete, wait for next fragment
                    return
                }
                val attrBytes = ByteArray(attrLen)
                buffer.get(attrBytes)
                val attrStr = String(attrBytes, Charsets.UTF_8)

                when (attrId) {
                    AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_APP_IDENTIFIER -> appIdentifier = attrStr
                    AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_TITLE -> title = attrStr
                    AppleBleConstants.NOTIFICATION_ATTRIBUTE_ID_MESSAGE -> message = attrStr
                }
            }

            ancsDataBuffer.reset()

            // Classify category by App Identifier
            val category = when {
                appIdentifier.contains("whatsapp", ignoreCase = true) -> IphoneNotificationCategory.WHATSAPP
                appIdentifier.contains("instagram", ignoreCase = true) -> IphoneNotificationCategory.INSTAGRAM
                appIdentifier.contains("mobilesms", ignoreCase = true) -> IphoneNotificationCategory.SMS
                appIdentifier.contains("snapchat", ignoreCase = true) -> IphoneNotificationCategory.SNAPCHAT
                appIdentifier.contains("mobilephone", ignoreCase = true) -> IphoneNotificationCategory.INCOMING_CALL
                else -> IphoneNotificationCategory.OTHER
            }

            val appName = when (category) {
                IphoneNotificationCategory.WHATSAPP -> "WhatsApp"
                IphoneNotificationCategory.INSTAGRAM -> "Instagram"
                IphoneNotificationCategory.SMS -> "Messages"
                IphoneNotificationCategory.SNAPCHAT -> "Snapchat"
                IphoneNotificationCategory.INCOMING_CALL -> "Phone"
                else -> if (appIdentifier.isNotEmpty()) appIdentifier.substringAfterLast('.') else "iPhone"
            }

            val item = IphoneNotificationItem(
                notificationUid = notifUid,
                category = category,
                appName = appName,
                sender = if (title.isNotBlank()) title else appName,
                messagePreview = message
            )

            _notifications.update { current ->
                listOf(item) + current.filterNot { it.notificationUid == notifUid }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing ANCS Data Source buffer", e)
        }
    }

    /**
     * Handles track and playback updates from Apple Media Service (AMS)
     */
    private fun handleAmsEntityUpdate(data: ByteArray) {
        if (data.size < 3) return
        val entityId = data[0]
        val attrId = data[1]
        val textValue = String(data, 3, data.size - 3, Charsets.UTF_8)

        if (entityId == AppleBleConstants.ENTITY_ID_TRACK) {
            _trackInfo.update { current ->
                when (attrId) {
                    AppleBleConstants.TRACK_ATTR_TITLE -> current.copy(title = textValue)
                    AppleBleConstants.TRACK_ATTR_ARTIST -> current.copy(artist = textValue)
                    AppleBleConstants.TRACK_ATTR_ALBUM -> current.copy(album = textValue)
                    AppleBleConstants.TRACK_ATTR_DURATION -> {
                        val dur = textValue.toDoubleOrNull()?.toInt() ?: current.durationSeconds
                        current.copy(durationSeconds = dur)
                    }
                    else -> current
                }
            }
        } else if (entityId == AppleBleConstants.ENTITY_ID_PLAYER) {
            // PlaybackInfo string: "PlaybackState,PlaybackRate,ElapsedTime"
            val parts = textValue.split(",")
            val playbackState = parts.firstOrNull()?.trim()?.toIntOrNull()
            val isPlaying = playbackState == 1
            _trackInfo.update { it.copy(isPlaying = isPlaying) }
        }
    }

    /**
     * Connects to a paired iPhone or initiates discovery for nearby iPhone
     */
    fun connectToBondedIphone(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val bonded = adapter.bondedDevices.orEmpty()

        val iphone = bonded.firstOrNull { dev ->
            val name = dev.name ?: ""
            name.contains("iPhone", ignoreCase = true) ||
            name.contains("Apple", ignoreCase = true) ||
            dev.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
        }

        return if (iphone != null) {
            connectToDevice(iphone)
            true
        } else {
            scanForNearbyIphone()
            false
        }
    }

    private var isScanning = false
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val dev = result?.device ?: return
            val name = dev.name ?: result.scanRecord?.deviceName ?: ""
            if (name.contains("iPhone", ignoreCase = true) || name.contains("Apple", ignoreCase = true)) {
                stopBleScan()
                connectToDevice(dev)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "BLE Scan failed with code $errorCode")
            isScanning = false
            _connectionState.update {
                it.copy(statusMessage = "Pair iPhone in Bluetooth settings to enable ANCS.")
            }
        }
    }

    fun scanForNearbyIphone() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || isScanning) return
        try {
            isScanning = true
            _connectionState.update {
                it.copy(statusMessage = "Searching for nearby iPhone...")
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, bleScanCallback)

            // Auto stop scan after 15 seconds
            scope.launch {
                kotlinx.coroutines.delay(15000)
                if (isScanning) {
                    stopBleScan()
                    if (!_connectionState.value.isConnected) {
                        _connectionState.update {
                            it.copy(statusMessage = "No iPhone found. Ensure Bluetooth is ON and iPhone is nearby.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start BLE scan", e)
            isScanning = false
        }
    }

    fun stopBleScan() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (_: Exception) {}
        isScanning = false
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopBleScan()
        _connectionState.update {
            it.copy(
                deviceName = device.name ?: "Apple iPhone",
                deviceAddress = device.address,
                statusMessage = "Connecting to ${device.name ?: "iPhone"}..."
            )
        }
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}

        // Immediate direct LE connection with TRANSPORT_LE
        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    // =========================================================================
    // Apple Media Service (AMS) Playback Control Methods
    // =========================================================================

    fun play() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_PLAY)
    fun pause() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_PAUSE)
    fun togglePlayPause() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_TOGGLE_PLAY_PAUSE)
    fun nextTrack() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_NEXT_TRACK)
    fun previousTrack() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_PREVIOUS_TRACK)
    fun volumeUp() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_VOLUME_UP)
    fun volumeDown() = sendAmsRemoteCommand(AppleBleConstants.REMOTE_CMD_VOLUME_DOWN)

    private fun sendAmsRemoteCommand(commandId: Byte) {
        val gatt = activeGatt ?: return
        val rc = amsRemoteCommandChar ?: return
        try {
            writeCharacteristicSafely(gatt, rc, byteArrayOf(commandId))
        } catch (e: Exception) {
            Log.e(tag, "Failed to send AMS command $commandId", e)
        }
    }

    // =========================================================================
    // Notification Management
    // =========================================================================

    fun dismissNotification(uid: Int) {
        _notifications.update { list -> list.filterNot { it.notificationUid == uid } }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    fun disconnect() {
        stopBleScan()
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
        _connectionState.value = IphoneBleConnectionState()
    }
}

