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
    val colors = LocalCockpitColors.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Primary Temperature & Condition Display
        GlowCard(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
            backgroundColor = colors.card,
            borderColor = colors.primaryAccent
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
                        style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent)
                    )
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = colors.primaryAccent)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(if (colors.isLight) colors.primaryAccent.copy(alpha = 0.12f) else DarkBlueAccent)
                        .border(2.dp, colors.primaryAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.precipitationMm > 0f) Icons.Default.WaterDrop else Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (uiState.precipitationMm > 0f) colors.primaryAccent else WarningOrange,
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
                        color = colors.primaryText
                    )
                    Text(
                        text = uiState.conditionDescription,
                        style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryAccent
                    )
                }

                Text(
                    text = uiState.errorMessage ?: "Road Surface: Dry & Optimal Grip",
                    style = MotoDriveTypography.labelSmall.copy(
                        color = if (uiState.precipitationMm > 0f) colors.warningRed else SuccessGreen,
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
                style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Wind Speed
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = colors.card,
                    borderColor = colors.border
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Air, contentDescription = null, tint = colors.primaryAccent)
                        Text("WIND SPEED", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                        Text(
                            "${uiState.windSpeedKmH} km/h",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                }

                // Humidity
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = colors.card,
                    borderColor = colors.border
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = colors.primaryAccent)
                        Text("HUMIDITY", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                        Text(
                            "${uiState.humidityPercent}%",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
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
                    backgroundColor = colors.card,
                    borderColor = colors.border
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = WarningOrange)
                        Text("PRECIPITATION", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                        Text(
                            "${uiState.precipitationMm} mm/h",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.precipitationMm > 0f) WarningOrange else colors.primaryText
                        )
                    }
                }

                // Visor / Riding Advisory
                GlowCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = colors.card,
                    borderColor = colors.border
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen)
                        Text("RIDE SAFETY", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                        Text(
                            if (uiState.precipitationMm > 2f) "Wet Visor" else "Optimal Visor",
                            style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.precipitationMm > 2f) colors.warningRed else SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

