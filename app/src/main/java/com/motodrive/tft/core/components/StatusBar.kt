package com.motodrive.tft.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.connectivity.BluetoothConnectionStatus
import com.motodrive.tft.connectivity.apple.IphoneBleConnectionState
import com.motodrive.tft.core.theme.*
import com.motodrive.tft.weather.WeatherUiState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cinematic ultra-thin top status bar for motorcycle TFT dashboard.
 */
@Composable
fun StatusBar(
    currentTimeMillis: Long,
    is24Hour: Boolean,
    isGpsLocked: Boolean,
    satellitesCount: Int,
    iphoneBleState: IphoneBleConnectionState,
    weatherState: WeatherUiState,
    isRidingMode: Boolean,
    isBluetoothEnabled: Boolean = true,
    isBluetoothConnected: Boolean = false,
    bluetoothStatus: BluetoothConnectionStatus = BluetoothConnectionStatus.BLUETOOTH_OFF,
    connectedDeviceName: String? = null,
    onGpsClick: () -> Unit = {},
    onBluetoothClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    val timeFormat = if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault())
    else SimpleDateFormat("hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(currentTimeMillis))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(colors.background.copy(alpha = 0.85f))
            .border(0.5.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Clock & Cockpit Riding Mode Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = formattedTime,
                style = MotoDriveTypography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = colors.primaryText
            )

            // Mode Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isRidingMode) colors.primaryAccent.copy(alpha = 0.25f) else colors.cardElevated)
                    .border(1.dp, if (isRidingMode) colors.primaryAccent else colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isRidingMode) "RIDE MODE" else "URBAN 225",
                    style = MotoDriveTypography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = if (isRidingMode) colors.electricAccent else colors.secondaryText
                )
            }
        }

        // Center: TVS Ronin Signature
        Text(
            text = "TVS RONIN",
            style = MotoDriveTypography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp
            ),
            color = colors.electricAccent
        )

        // Right: Telemetry & Connectivity Status (Weather, GPS, iPhone BLE)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Weather
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Weather",
                    tint = WarningOrange,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${weatherState.temperatureCelsius.toInt()}${weatherState.temperatureUnit}",
                    style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = colors.primaryText
                )
            }

            // GPS Status (Interactive: tap to enable/check location settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onGpsClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isGpsLocked) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                    contentDescription = "GPS",
                    tint = if (isGpsLocked) SuccessGreen else AlertRed,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (isGpsLocked) "3D FIX" else "NO GPS",
                    style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = if (isGpsLocked) colors.primaryText else AlertRed
                )
            }

            // Bluetooth Connection Status (System Bluetooth / Helmet / Connected Phone / iPhone BLE)
            val isBtOn = isBluetoothEnabled && bluetoothStatus != BluetoothConnectionStatus.BLUETOOTH_OFF
            val isBtConnected = iphoneBleState.isConnected || isBluetoothConnected || bluetoothStatus == BluetoothConnectionStatus.CONNECTED
            val btLabel = when {
                !isBtOn -> "BT OFF"
                iphoneBleState.isConnected -> "iPHONE"
                isBluetoothConnected -> connectedDeviceName?.take(14)?.uppercase() ?: "CONNECTED"
                bluetoothStatus == BluetoothConnectionStatus.SEARCHING -> "SEARCHING..."
                bluetoothStatus == BluetoothConnectionStatus.PAIRING -> "PAIRING..."
                bluetoothStatus == BluetoothConnectionStatus.READY_TO_CONNECT -> "READY TO CONNECT"
                bluetoothStatus == BluetoothConnectionStatus.DISCONNECTED -> "DISCONNECTED"
                bluetoothStatus == BluetoothConnectionStatus.CONNECTED -> connectedDeviceName?.take(14)?.uppercase() ?: "CONNECTED"
                else -> "BT ON"
            }
            val btIcon = when {
                !isBtOn -> Icons.Default.BluetoothDisabled
                isBtConnected -> Icons.Default.BluetoothConnected
                bluetoothStatus == BluetoothConnectionStatus.SEARCHING ||
                bluetoothStatus == BluetoothConnectionStatus.PAIRING -> Icons.AutoMirrored.Filled.BluetoothSearching
                else -> Icons.Default.Bluetooth
            }
            val btTint = when {
                !isBtOn -> colors.secondaryText.copy(alpha = 0.4f)
                isBtConnected -> ElectricBlue
                bluetoothStatus == BluetoothConnectionStatus.SEARCHING ||
                bluetoothStatus == BluetoothConnectionStatus.PAIRING -> WarningOrange
                bluetoothStatus == BluetoothConnectionStatus.DISCONNECTED -> AlertRed
                else -> ElectricBlue.copy(alpha = 0.75f)
            }
            val btTextColor = when {
                !isBtOn -> colors.secondaryText.copy(alpha = 0.4f)
                isBtConnected -> colors.primaryText
                bluetoothStatus == BluetoothConnectionStatus.SEARCHING ||
                bluetoothStatus == BluetoothConnectionStatus.PAIRING -> WarningOrange
                bluetoothStatus == BluetoothConnectionStatus.DISCONNECTED -> AlertRed
                else -> colors.secondaryText
            }

            // Bluetooth Status (Interactive: tap to enable/prompt bluetooth)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBluetoothClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = btIcon,
                    contentDescription = "Bluetooth",
                    tint = btTint,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = btLabel,
                    style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = btTextColor
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 40)
@Composable
fun StatusBarPreview() {
    MotoDriveTheme {
        StatusBar(
            currentTimeMillis = System.currentTimeMillis(),
            is24Hour = false,
            isGpsLocked = true,
            satellitesCount = 14,
            iphoneBleState = IphoneBleConnectionState(isConnected = true, deviceName = "Prasad's iPhone"),
            weatherState = WeatherUiState(temperatureCelsius = 28f, temperatureUnit = "°C"),
            isRidingMode = false
        )
    }
}
