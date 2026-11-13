package com.motodrive.tft.riding

import android.location.Location
import androidx.compose.foundation.background
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
import com.motodrive.tft.core.components.GlassCard
import com.motodrive.tft.core.theme.*
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.media.MediaUiState
import com.motodrive.tft.navigation.NavigationMapView
import com.motodrive.tft.navigation.NavigationUiState
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
    val colors = LocalCockpitColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 1. Full-Screen Embedded Navigation Map View
        NavigationMapView(
            currentLocation = currentLocation,
            currentSpeedKmH = speedUiState.currentSpeedKmH,
            uiState = navUiState,
            onStartNavigation = onStartNavigation,
            onCancelNavigation = onCancelNavigation,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Riding Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xCC050508))
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
                    text = "TVS RONIN RIDING MODE",
                    style = MotoDriveTypography.labelLarge.copy(color = SuccessGreen, fontWeight = FontWeight.ExtraBold)
                )
            }

            // Exit Riding Mode Pill (>= 56dp touch friendly)
            Button(
                onClick = onExitRidingMode,
                colors = ButtonDefaults.buttonColors(containerColor = colors.cardElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.electricAccent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = colors.electricAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PARKED VIEW", style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.primaryText)
            }
        }

        // 3. Compact Floating Speedometer (Bottom-Left)
        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .width(220.dp),
            cornerRadius = 20.dp,
            borderColor = colors.electricAccent,
            borderWidth = 1.5.dp
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
                        style = MotoDriveTypography.labelSmall.copy(color = colors.electricAccent, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${speedUiState.headingCardinal} ${speedUiState.headingDegrees.toInt()}°",
                        style = MotoDriveTypography.labelSmall,
                        color = colors.primaryText
                    )
                }

                Text(
                    text = "${Units.speed(speedUiState.currentSpeedKmH, speedUiState.speedUnit).toInt()}",
                    style = MotoDriveTypography.displayLarge.copy(
                        fontSize = 58.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = colors.primaryText
                )

                Text(
                    text = speedUiState.speedUnit.uppercase(),
                    style = MotoDriveTypography.labelLarge.copy(color = colors.electricAccent, fontWeight = FontWeight.Bold)
                )
            }
        }

        // 4. Floating Quick Media Control Pill (Bottom-Right)
        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .widthIn(max = 260.dp),
            cornerRadius = 24.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.electricAccent,
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaUiState.title,
                        style = MotoDriveTypography.titleLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = colors.primaryText,
                        maxLines = 1
                    )
                    Text(
                        text = mediaUiState.artist,
                        style = MotoDriveTypography.bodyMedium.copy(fontSize = 10.sp),
                        color = colors.secondaryText,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.primaryAccent)
                        .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNextTrack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.primaryText, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
