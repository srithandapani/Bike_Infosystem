package com.motodrive.tft.riding

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.motodrive.tft.media.MediaUiState
import com.motodrive.tft.navigation.NavigationMapView
import com.motodrive.tft.navigation.NavigationUiState
import com.motodrive.tft.navigation.TurnDirection
import com.motodrive.tft.speedometer.SpeedometerUiState

@Composable
fun RidingModeScreen(
    currentLocation: Location?,
    speedUiState: SpeedometerUiState,
    navUiState: NavigationUiState,
    mediaUiState: MediaUiState,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onExitRidingMode: () -> Unit,
    onStartNavigation: (String) -> Unit,
    onCancelNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
    ) {
        // 1. Full-Screen High-Contrast Navigation Map Background
        NavigationMapView(
            currentLocation = currentLocation,
            headingDegrees = speedUiState.headingDegrees,
            uiState = navUiState,
            onStartNavigation = onStartNavigation,
            onCancelNavigation = onCancelNavigation,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Status Bar Overlay (Dark Translucent Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(PrimaryBackground.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen)
                )
                Text(
                    text = "ACTIVE RIDING MODE",
                    style = MotoDriveTypography.labelLarge.copy(color = SuccessGreen, fontWeight = FontWeight.ExtraBold)
                )
            }

            // Quick Floating Exit Riding Mode Button
            Button(
                onClick = onExitRidingMode,
                colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SWITCH TO PARKED", style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
            }
        }

        // 3. Compact Floating Speedometer Overlay (Bottom-Left)
        GlowCard(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .width(220.dp),
            shape = RoundedCornerShape(16.dp),
            backgroundColor = CardBackgroundElevated.copy(alpha = 0.95f),
            borderColor = ElectricBlue,
            borderWidth = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS SPEED",
                        style = MotoDriveTypography.labelSmall.copy(color = ElectricBlue, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${speedUiState.headingCardinal} ${speedUiState.headingDegrees.toInt()}°",
                        style = MotoDriveTypography.labelSmall,
                        color = PrimaryText
                    )
                }

                Text(
                    text = "${speedUiState.currentSpeedKmH.toInt()}",
                    style = MotoDriveTypography.displayLarge.copy(
                        fontSize = 58.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = PrimaryText
                )

                Text(
                    text = speedUiState.speedUnit.uppercase(),
                    style = MotoDriveTypography.labelLarge.copy(color = ElectricBlue, fontWeight = FontWeight.Bold)
                )
            }
        }

        // 4. Floating Quick Media Control Pill (Bottom-Right)
        GlowCard(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .widthIn(max = 260.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = CardBackgroundElevated.copy(alpha = 0.92f),
            borderColor = ThinBorderColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaUiState.title,
                        style = MotoDriveTypography.titleLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = PrimaryText,
                        maxLines = 1
                    )
                    Text(
                        text = mediaUiState.artist,
                        style = MotoDriveTypography.bodyMedium.copy(fontSize = 10.sp),
                        color = SecondaryText,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = PrimaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onNextTrack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PrimaryText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

