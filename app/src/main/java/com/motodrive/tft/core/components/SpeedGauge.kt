package com.motodrive.tft.core.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic motorcycle speed gauge with startup needle sweep, spring physics,
 * and high-contrast glanceable typography.
 */
@Composable
fun SpeedGauge(
    speed: Float,
    speedUnit: String = "km/h",
    maxSpeed: Float = 180f,
    clusterStyle: String = "DIGITAL", // "DIGITAL", "ANALOG", "HYBRID"
    speedLimitKmH: Int? = null,
    tripDistance: Float = 0f,
    averageSpeed: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    // Startup sweep animation: 0 -> Max -> 0 on boot
    var isStartupComplete by remember { mutableStateOf(false) }
    val startupAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Startup sweep: 0 to 180 and back to 0
        startupAnim.animateTo(maxSpeed, tween(700, easing = FastOutSlowInEasing))
        startupAnim.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        isStartupComplete = true
    }

    // Active speed with spring physics
    val animatedSpeed by animateFloatAsState(
        targetValue = if (isStartupComplete) speed.coerceIn(0f, maxSpeed) else startupAnim.value,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "GaugeSpeed"
    )

    val sweepRatio = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)

    GlassCard(
        modifier = modifier.fillMaxHeight(),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Speed Limit Badge & Style Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Limit Badge (Indian / European Red Circle)
                if (speedLimitKmH != null && speedLimitKmH > 0) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(3.dp, AlertRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = speedLimitKmH.toString(),
                            style = MotoDriveTypography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            ),
                            color = Color.Black
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                Text(
                    text = "TVS RONIN 225",
                    style = MotoDriveTypography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = colors.electricAccent
                )

                Spacer(modifier = Modifier.size(36.dp))
            }

            // Center Gauge (Digital, Analog, or Hybrid)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Arc Gauge Background & Active Sweep Canvas
                Canvas(modifier = Modifier.size(220.dp)) {
                    val canvasWidth = size.width
                    val strokeWidthPx = 14.dp.toPx()
                    val arcSize = Size(canvasWidth - strokeWidthPx, canvasWidth - strokeWidthPx)
                    val arcOffset = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)

                    val startAngle = 135f
                    val sweepAngle = 270f

                    // 1. Inactive Track
                    drawArc(
                        color = colors.border.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // 2. Active Glowing Gradient Sweep
                    val activeBrush = Brush.sweepGradient(
                        0.0f to colors.primaryAccent,
                        0.75f to colors.electricAccent,
                        1.0f to colors.warningRed
                    )
                    drawArc(
                        brush = activeBrush,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * sweepRatio,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // 3. Analog Needle (If Analog or Hybrid Style)
                    if (clusterStyle == "ANALOG" || clusterStyle == "HYBRID") {
                        val currentAngle = Math.toRadians((startAngle + sweepAngle * sweepRatio).toDouble())
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val needleLength = (size.width / 2f) - strokeWidthPx - 10.dp.toPx()
                        val needleEnd = Offset(
                            (center.x + needleLength * cos(currentAngle)).toFloat(),
                            (center.y + needleLength * sin(currentAngle)).toFloat()
                        )
                        drawLine(
                            color = colors.warningRed,
                            start = center,
                            end = needleEnd,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = colors.warningRed,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                    }
                }

                // Center Digital Speed Display (Extra large, glanceable at speed)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = animatedSpeed.toInt().toString(),
                        style = MotoDriveTypography.displayLarge.copy(
                            fontSize = 68.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-2).sp
                        ),
                        color = colors.primaryText
                    )
                    Text(
                        text = speedUnit.uppercase(),
                        style = MotoDriveTypography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.5.sp
                        ),
                        color = colors.electricAccent
                    )
                }
            }

            // Bottom Telemetry Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("TRIP DIST", style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp), color = colors.secondaryText)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f %s", tripDistance, if (speedUnit.equals("mph", true)) "mi" else "km"),
                        style = MotoDriveTypography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = colors.primaryText
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("AVG SPEED", style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp), color = colors.secondaryText)
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f %s", averageSpeed, speedUnit),
                        style = MotoDriveTypography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = colors.primaryText
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 340)
@Composable
fun SpeedGaugePreview() {
    MotoDriveTheme {
        SpeedGauge(
            speed = 65f,
            speedUnit = "km/h",
            clusterStyle = "DIGITAL",
            speedLimitKmH = 80,
            tripDistance = 42.8f,
            averageSpeed = 48.2f
        )
    }
}

