package com.motodrive.tft.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun WeatherScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Primary Temperature & Condition Display
        GlowCard(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
            backgroundColor = CardBackgroundElevated,
            borderColor = ElectricBlue
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE WEATHER RADAR",
                        style = MotoDriveTypography.labelLarge.copy(color = ElectricBlue)
                    )
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricBlue)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(DarkBlueAccent)
                        .border(2.dp, ElectricBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.precipitationMm > 0f) Icons.Default.WaterDrop else Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (uiState.precipitationMm > 0f) ElectricBlue else WarningOrange,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${uiState.temperatureCelsius.toInt()}${uiState.temperatureUnit}",
                        style = MotoDriveTypography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = PrimaryText
                    )
                    Text(
                        text = uiState.conditionDescription,
                        style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ElectricBlue
                    )
                }

                Text(
                    text = uiState.errorMessage ?: "Road Surface: Dry & Optimal Grip",
                    style = MotoDriveTypography.labelSmall.copy(
                        color = if (uiState.precipitationMm > 0f) AlertRed else SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Right: Detailed Atmospheric & Motorcycle Riding Metrics
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "RIDING ENVIRONMENT",
                style = MotoDriveTypography.labelLarge.copy(color = ElectricBlue)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Wind Speed
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = CardBackground,
                    borderColor = ThinBorderColor
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Air, contentDescription = null, tint = ElectricBlue)
                        Text("WIND SPEED", style = MotoDriveTypography.labelSmall, color = SecondaryText)
                        Text(
                            "${uiState.windSpeedKmH} km/h",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                    }
                }

                // Humidity
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = CardBackground,
                    borderColor = ThinBorderColor
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = ElectricBlue)
                        Text("HUMIDITY", style = MotoDriveTypography.labelSmall, color = SecondaryText)
                        Text(
                            "${uiState.humidityPercent}%",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Rain / Precipitation
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = CardBackground,
                    borderColor = ThinBorderColor
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = WarningOrange)
                        Text("PRECIPITATION", style = MotoDriveTypography.labelSmall, color = SecondaryText)
                        Text(
                            "${uiState.precipitationMm} mm/h",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.precipitationMm > 0f) WarningOrange else PrimaryText
                        )
                    }
                }

                // Visor / Riding Advisory
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = CardBackground,
                    borderColor = ThinBorderColor
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen)
                        Text("RIDE SAFETY", style = MotoDriveTypography.labelSmall, color = SecondaryText)
                        Text(
                            if (uiState.precipitationMm > 2f) "Wet Visor" else "Optimal Visor",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.precipitationMm > 2f) AlertRed else SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

