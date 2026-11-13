package com.motodrive.tft.connectivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*

@Composable
fun PhoneConnectivityScreen(
    uiState: ConnectivityUiState,
    onQuickCall: (String) -> Unit,
    onConnectDevice: (ConnectedBtDevice) -> Unit = {},
    onDisconnectDevice: (ConnectedBtDevice) -> Unit = {},
    onUnpairDevice: (ConnectedBtDevice) -> Unit = {},
    onStartDiscovery: () -> Unit = {},
    onCancelDiscovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    var dialNumber by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Bluetooth Status, Scan Controls, Paired & Discovered Devices
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bluetooth Status Banner & Discovery Trigger
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.cardElevated,
                borderColor = when (uiState.bluetoothStatus) {
                    BluetoothConnectionStatus.CONNECTED -> SuccessGreen
                    BluetoothConnectionStatus.SEARCHING,
                    BluetoothConnectionStatus.PAIRING -> WarningOrange
                    BluetoothConnectionStatus.DISCONNECTED,
                    BluetoothConnectionStatus.BLUETOOTH_OFF -> AlertRed
                    else -> colors.primaryAccent
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val statusIcon = when (uiState.bluetoothStatus) {
                            BluetoothConnectionStatus.BLUETOOTH_OFF -> Icons.Default.BluetoothDisabled
                            BluetoothConnectionStatus.CONNECTED -> Icons.Default.BluetoothConnected
                            BluetoothConnectionStatus.SEARCHING,
                            BluetoothConnectionStatus.PAIRING -> Icons.AutoMirrored.Filled.BluetoothSearching
                            else -> Icons.Default.Bluetooth
                        }
                        val statusTint = when (uiState.bluetoothStatus) {
                            BluetoothConnectionStatus.CONNECTED -> SuccessGreen
                            BluetoothConnectionStatus.SEARCHING,
                            BluetoothConnectionStatus.PAIRING -> WarningOrange
                            BluetoothConnectionStatus.DISCONNECTED,
                            BluetoothConnectionStatus.BLUETOOTH_OFF -> AlertRed
                            else -> colors.primaryAccent
                        }

                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.bluetoothStatus.label.uppercase(),
                                style = MotoDriveTypography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = statusTint
                            )
                            if (uiState.isConnected || uiState.bluetoothStatus == BluetoothConnectionStatus.PAIRING) {
                                Text(
                                    text = uiState.connectedDeviceName,
                                    style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                                    color = colors.primaryText,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Scan / Stop Button
                    if (uiState.isBluetoothEnabled) {
                        if (uiState.isSearching) {
                            Button(
                                onClick = onCancelDiscovery,
                                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "STOP",
                                    style = MotoDriveTypography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        } else {
                            Button(
                                onClick = onStartDiscovery,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Scan",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "SCAN",
                                    style = MotoDriveTypography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable Devices List: Paired Devices + Discovered Devices
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section: Paired Devices
                item {
                    Text(
                        text = "PAIRED DEVICES (${uiState.pairedDevices.size})",
                        style = MotoDriveTypography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryText
                        )
                    )
                }

                if (uiState.pairedDevices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isBluetoothEnabled) "No paired devices. Tap SCAN to search." else "Bluetooth is turned off.",
                                style = MotoDriveTypography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                } else {
                    items(uiState.pairedDevices) { dev ->
                        GlowCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = if (dev.isConnected) colors.cardElevated else colors.card,
                            borderColor = if (dev.isConnected) SuccessGreen else colors.border
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (dev.isConnected) SuccessGreen.copy(alpha = 0.15f) else colors.cardElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (dev.isHelmet) Icons.Default.HeadsetMic else Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = if (dev.isConnected) SuccessGreen else colors.secondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name,
                                            style = MotoDriveTypography.titleMedium.copy(fontSize = 13.sp),
                                            color = colors.primaryText,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (dev.isConnected) "Connected Audio" else "Paired (Tap to Connect)",
                                            style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (dev.isConnected) SuccessGreen else colors.secondaryText
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (dev.isConnected) {
                                        Button(
                                            onClick = { onDisconnectDevice(dev) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.85f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                "DISCONNECT",
                                                style = MotoDriveTypography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { onConnectDevice(dev) },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                "CONNECT",
                                                style = MotoDriveTypography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onUnpairDevice(dev) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Unpair",
                                            tint = colors.secondaryText.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Discovered Nearby Devices
                if (uiState.discoveredDevices.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "NEARBY DISCOVERED DEVICES (${uiState.discoveredDevices.size})",
                            style = MotoDriveTypography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent
                            )
                        )
                    }

                    items(uiState.discoveredDevices) { dev ->
                        GlowCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !dev.isBonding) { onConnectDevice(dev) },
                            backgroundColor = colors.card,
                            borderColor = if (dev.isBonding) WarningOrange else colors.primaryAccent.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (dev.isHelmet) Icons.Default.HeadsetMic else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (dev.isBonding) WarningOrange else colors.primaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name,
                                            style = MotoDriveTypography.titleMedium.copy(fontSize = 12.sp),
                                            color = colors.primaryText,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (dev.isBonding) "Pairing in progress..." else "Tap to Pair & Connect",
                                            style = MotoDriveTypography.labelSmall.copy(fontSize = 9.sp),
                                            color = if (dev.isBonding) WarningOrange else colors.secondaryText
                                        )
                                    }
                                }

                                if (dev.isBonding) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = WarningOrange
                                        )
                                        Text(
                                            text = "PAIRING...",
                                            style = MotoDriveTypography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningOrange
                                            )
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { onConnectDevice(dev) },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "PAIR",
                                            style = MotoDriveTypography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Speed Dial & Hands-Free Keypad
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "HANDS-FREE SPEED DIAL",
                style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent)
            )

            // Speed Dial Preset Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Pair("Emergency Roadside", "112"),
                    Pair("Bike Service Garage", "1800-MOTOR"),
                    Pair("Home", "1234567890")
                ).forEach { contact ->
                    GlowCard(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onQuickCall(contact.second) },
                        backgroundColor = colors.card,
                        borderColor = colors.border
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = contact.first,
                                style = MotoDriveTypography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = colors.primaryText,
                                maxLines = 1
                            )
                            Text(
                                text = contact.second,
                                style = MotoDriveTypography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }

            // Number Display & Quick Dial
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.primaryAccent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialNumber.ifEmpty { "Enter Number..." },
                        style = MotoDriveTypography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (dialNumber.isNotEmpty()) colors.primaryText else colors.secondaryText
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dialNumber.isNotEmpty()) {
                            IconButton(onClick = { dialNumber = dialNumber.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = colors.secondaryText)
                            }
                        }

                        Button(
                            onClick = {
                                if (dialNumber.isNotEmpty()) onQuickCall(dialNumber)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CALL", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Compact Numeric Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val keyRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                keyRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.card)
                                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                    .clickable { dialNumber += digit },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit,
                                    style = MotoDriveTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

