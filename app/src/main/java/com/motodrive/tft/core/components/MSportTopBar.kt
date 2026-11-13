package com.motodrive.tft.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.motodrive.tft.core.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MSportTopBar(
    bluetoothConnected: Boolean = true,
    gpsFix: Boolean = true,
    batteryPercent: Int = 88,
    isCharging: Boolean = false,
    networkStatus: String = "5G",
    temperatureText: String = "26°C",
    is24Hour: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember { mutableStateOf("") }

    LaunchedEffect(is24Hour) {
        val formatPattern = if (is24Hour) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(SecondaryBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: TVS RONIN Branding with TVS Leaping Horse Emblem
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // TVS Leaping Horse Emblem
            RoninEmblem(height = 24.dp)

            // M Sport Tri-color accent bars
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MSportLightBlue)
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MSportDarkBlue)
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MSportRed)
                )
            }

            Text(
                text = "TVS RONIN",
                style = MotoDriveTypography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = PrimaryText
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkBlueAccent)
                    .border(0.5.dp, ElectricBlue, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TFT CONNECT",
                    style = MotoDriveTypography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                )
            }
        }

        // Center: Ambient Info & Digital Clock
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Weather temperature pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = temperatureText,
                    style = MotoDriveTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText
                )
            }

            // Central Digital Clock
            Text(
                text = currentTimeString.ifEmpty { "--:--" },
                style = MotoDriveTypography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = PrimaryText
            )
        }

        // Right: Telemetry & Connectivity Badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // GPS Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (gpsFix) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    contentDescription = "GPS Status",
                    tint = if (gpsFix) SuccessGreen else AlertRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (gpsFix) "GPS 3D" else "GPS --",
                    style = MotoDriveTypography.labelSmall,
                    color = if (gpsFix) SuccessGreen else AlertRed
                )
            }

            // Bluetooth Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (bluetoothConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = if (bluetoothConnected) ElectricBlue else SecondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (bluetoothConnected) "HELMET" else "DISC",
                    style = MotoDriveTypography.labelSmall,
                    color = if (bluetoothConnected) ElectricBlue else SecondaryText
                )
            }

            // Cellular/Network Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Network",
                    tint = PrimaryText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = networkStatus,
                    style = MotoDriveTypography.labelSmall,
                    color = PrimaryText
                )
            }

            // Battery Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    contentDescription = "Battery",
                    tint = if (batteryPercent > 20) SuccessGreen else AlertRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$batteryPercent%",
                    style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
            }
        }
    }
}

