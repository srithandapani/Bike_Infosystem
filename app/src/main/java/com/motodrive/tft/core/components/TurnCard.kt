package com.motodrive.tft.core.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.theme.LocalCockpitColors
import com.motodrive.tft.core.theme.MotoDriveTheme
import com.motodrive.tft.core.theme.MotoDriveTypography
import com.motodrive.tft.navigation.TurnDirection

/**
 * High-contrast motorcycle turn card with dynamic proximity pulse and large glanceable type.
 */
@Composable
fun TurnCard(
    direction: TurnDirection,
    distanceToTurn: String,
    roadName: String,
    remainingKm: Float,
    eta: String,
    speedUnit: String = "km/h",
    isNavigating: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    // Pulsing animation when approaching turn (< 150m)
    val infiniteTransition = rememberInfiniteTransition(label = "TurnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val isClose = distanceToTurn.contains("m") && (distanceToTurn.filter { it.isDigit() }.toIntOrNull() ?: 500) < 200

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        borderColor = if (isClose) colors.primaryAccent else colors.border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Turn Direction Icon Circle
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(if (isClose) pulseScale else 1.0f)
                    .clip(CircleShape)
                    .background(colors.primaryAccent.copy(alpha = 0.2f))
                    .border(2.dp, if (isClose) colors.electricAccent else colors.primaryAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (direction) {
                        TurnDirection.TURN_LEFT -> Icons.Default.TurnLeft
                        TurnDirection.TURN_RIGHT -> Icons.Default.TurnRight
                        TurnDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
                        TurnDirection.SHARP_RIGHT -> Icons.Default.TurnSharpRight
                        TurnDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                        TurnDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                        TurnDirection.U_TURN -> Icons.Default.Refresh
                        TurnDirection.ROUNDABOUT -> Icons.Default.Refresh
                        TurnDirection.DESTINATION -> Icons.Default.CheckCircle
                        else -> Icons.Default.Straight
                    },
                    contentDescription = "Maneuver",
                    tint = if (isClose) colors.electricAccent else colors.primaryText,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Maneuver Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = distanceToTurn,
                    style = MotoDriveTypography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isClose) 26.sp else 22.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = if (isClose) colors.electricAccent else colors.primaryText
                )
                Text(
                    text = roadName.ifBlank { "Continue Straight" },
                    style = MotoDriveTypography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }

            // Route ETA & Remaining Distance Pill
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = eta,
                    style = MotoDriveTypography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = colors.primaryText
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.1f %s", remainingKm, if (speedUnit.equals("mph", true)) "mi" else "km"),
                    style = MotoDriveTypography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.electricAccent
                )
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 90)
@Composable
fun TurnCardPreview() {
    MotoDriveTheme {
        TurnCard(
            direction = TurnDirection.TURN_RIGHT,
            distanceToTurn = "150 m",
            roadName = "Kanakapura Highway",
            remainingKm = 14.2f,
            eta = "18:45"
        )
    }
}

