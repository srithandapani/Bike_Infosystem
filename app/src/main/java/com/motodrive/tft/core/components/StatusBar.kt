package com.motodrive.tft.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

            // GPS Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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

            // iPhone Bluetooth Connection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (iphoneBleState.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = if (iphoneBleState.isConnected) ElectricBlue else colors.secondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (iphoneBleState.isConnected) "iPHONE" else "BT OFF",
                    style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = if (iphoneBleState.isConnected) colors.primaryText else colors.secondaryText.copy(alpha = 0.5f)
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
