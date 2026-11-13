package com.motodrive.tft.trip

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.speedometer.SpeedometerUiState

@Composable
fun TripComputerScreen(
    speedUiState: SpeedometerUiState,
    isRidingMode: Boolean,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val unit = speedUiState.speedUnit
    val displayDist = Units.distanceToDisplay(speedUiState.tripDistanceKm, unit)
    val distLabel = if (unit.equals("mph", true)) "MILES" else "KILOMETERS"
    val displayAvg = Units.speedToDisplay(speedUiState.averageSpeedKmH, unit)
    val displayMax = Units.speedToDisplay(speedUiState.maxSpeedKmH, unit)
    val speedLabel = Units.speedLabel(unit)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = colors.electricAccent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "TRIP COMPUTER 1",
                    style = MotoDriveTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryText
                )
            }

            // Reset Trip Button (Only enabled in Parked mode for motorcycle safety)
            Button(
                onClick = onResetTrip,
                enabled = !isRidingMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.cardElevated,
                    disabledContainerColor = colors.card
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (!isRidingMode) colors.electricAccent else colors.border
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Trip",
                    tint = if (!isRidingMode) colors.electricAccent else colors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (!isRidingMode) "RESET TRIP" else "LOCKED (RIDING)",
                    style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (!isRidingMode) colors.primaryText else colors.secondaryText
                )
            }
        }

        // Metrics Grid (4 Main Automotive Tiles)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Trip Distance
            GlowCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = colors.cardElevated,
                borderColor = colors.electricAccent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TRIP DISTANCE",
                        style = MotoDriveTypography.labelSmall.copy(color = colors.electricAccent, letterSpacing = 1.sp)
                    )
                    Text(
                        text = String.format("%.1f", displayDist),
                        style = MotoDriveTypography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = colors.primaryText
                    )
                    Text(
                        text = distLabel,
                        style = MotoDriveTypography.labelSmall,
                        color = colors.secondaryText
                    )
                }
            }

            // 2. Ride Duration
            GlowCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RIDE TIME",
                        style = MotoDriveTypography.labelSmall.copy(color = colors.electricAccent, letterSpacing = 1.sp)
                    )
                    val h = speedUiState.rideDurationSeconds / 3600
                    val m = (speedUiState.rideDurationSeconds % 3600) / 60
                    val s = speedUiState.rideDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d:%02d", h, m, s),
                        style = MotoDriveTypography.displaySmall.copy(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.electricAccent
                    )
                    Text(
                        text = "ACTIVE DURATION",
                        style = MotoDriveTypography.labelSmall,
                        color = colors.secondaryText
                    )
                }
            }

            // 3. Average GPS Speed
            GlowCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AVG GPS SPEED",
                        style = MotoDriveTypography.labelSmall.copy(color = colors.electricAccent, letterSpacing = 1.sp)
                    )
                    Text(
                        text = String.format("%.1f", displayAvg),
                        style = MotoDriveTypography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = colors.primaryText
                    )
                    Text(
                        text = "$speedLabel AVERAGE",
                        style = MotoDriveTypography.labelSmall,
                        color = colors.secondaryText
                    )
                }
            }

            // 4. Max Speed & Heading
            GlowCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MAX SPEED & HEADING",
                        style = MotoDriveTypography.labelSmall.copy(color = colors.electricAccent, letterSpacing = 1.sp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${displayMax.toInt()}",
                            style = MotoDriveTypography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primaryText
                        )
                        Text(
                            text = "$speedLabel MAX",
                            style = MotoDriveTypography.labelSmall,
                            color = colors.warningRed
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(speedUiState.headingDegrees)
                        )
                        Text(
                            text = "${speedUiState.headingCardinal} ${speedUiState.headingDegrees.toInt()}° COMPASS",
                            style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricBlue
                        )
                    }
                }
            }
        }

        // Disclaimer Bar (Strict compliance with safety requirements)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PanelBackground)
                .border(0.5.dp, ThinBorderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SUPPLEMENTARY GPS TRIP TELEMETRY - DOES NOT REPLACE MOTORCYCLE CERTIFIED GAUGES",
                style = MotoDriveTypography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = SecondaryText,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

