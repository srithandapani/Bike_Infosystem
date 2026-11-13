package com.motodrive.tft.connectivity.apple

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
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

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "Connected to iPhone GATT server: ${gatt.device.name}")
                    _connectionState.update {
                        it.copy(
                            isConnected = true,
                            deviceName = gatt.device.name ?: "Apple iPhone",
                            deviceAddress = gatt.device.address,
                            statusMessage = "Connected. Discovering Apple Services..."
                        )
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "Disconnected from iPhone. Scheduling auto-reconnect...")
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
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
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
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
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
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            advanceGattQueue()
                        }
                    }

                    // 2. Subscribe to Track attributes: Title (2), Artist (0), Album (1), Duration (3)
                    enqueueGatt {
                        @Suppress("DEPRECATION")
                        entityUpdate.value = byteArrayOf(
                            AppleBleConstants.ENTITY_ID_TRACK,
                            AppleBleConstants.TRACK_ATTR_TITLE,
                            AppleBleConstants.TRACK_ATTR_ARTIST,
                            AppleBleConstants.TRACK_ATTR_ALBUM,
                            AppleBleConstants.TRACK_ATTR_DURATION
                        )
                        gatt.writeCharacteristic(entityUpdate)
                    }

                    // 3. Subscribe to Player attributes: PlaybackInfo (1)
                    enqueueGatt {
                        @Suppress("DEPRECATION")
                        entityUpdate.value = byteArrayOf(
                            AppleBleConstants.ENTITY_ID_PLAYER,
                            1.toByte() // PlaybackInfo
                        )
                        gatt.writeCharacteristic(entityUpdate)
                    }
                }
            }

            _connectionState.update {
                it.copy(
                    isAncsReady = ancsFound,
                    isAmsReady = amsFound,
                    statusMessage = if (ancsFound && amsFound) "iPhone ANCS Notifications & Music Ready" else "Connected to iPhone"
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return

            when (characteristic.uuid) {
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

            @Suppress("DEPRECATION")
            cp.value = cmd.array()
            gatt.writeCharacteristic(cp)
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
     * Connects to a paired iPhone from system Bluetooth settings
     */
    fun connectToBondedIphone(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val bonded = adapter.bondedDevices ?: return false

        val iphone = bonded.firstOrNull { dev ->
            val name = dev.name ?: ""
            name.contains("iPhone", ignoreCase = true) || dev.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
        }

        return if (iphone != null) {
            connectToDevice(iphone)
            true
        } else {
            _connectionState.update {
                it.copy(statusMessage = "No paired iPhone detected. Pair iPhone in Bluetooth settings first.")
            }
            false
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.update {
            it.copy(
                deviceName = device.name ?: "Apple iPhone",
                deviceAddress = device.address,
                statusMessage = "Connecting to ${device.name ?: "iPhone"}..."
            )
        }
        activeGatt?.close()
        activeGatt = device.connectGatt(context, true, gattCallback)
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
            @Suppress("DEPRECATION")
            rc.value = byteArrayOf(commandId)
            gatt.writeCharacteristic(rc)
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
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _connectionState.value = IphoneBleConnectionState()
    }
}

