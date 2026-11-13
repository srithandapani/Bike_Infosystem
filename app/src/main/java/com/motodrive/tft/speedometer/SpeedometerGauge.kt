package com.motodrive.tft.speedometer

import com.motodrive.tft.core.util.Units

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    speedKmH: Float,
    headingDegrees: Float,
    headingCardinal: String,
    tripDistanceKm: Float,
    rideDurationSeconds: Long,
    hasGpsFix: Boolean,
    speedUnit: String = "km/h",
    maxGaugeSpeed: Float = 180f,
    maxSpeedKmH: Float = 0f,
    averageSpeedKmH: Float = 0f,
    clusterStyle: String = "DIGITAL", // "DIGITAL", "ANALOG", "HYBRID"
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    // Smooth animated speed representation
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmH.coerceIn(0f, maxGaugeSpeed),
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "gaugeSpeed"
    )

    val sweepRatio = (animatedSpeed / maxGaugeSpeed).coerceIn(0f, 1f)

    GlowCard(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp)),
        backgroundColor = colors.panel,
        borderColor = colors.border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Compass & GPS Fix Quality Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compass Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.card)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Heading",
                        tint = colors.electricAccent,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(headingDegrees)
                    )
                    Text(
                        text = "$headingCardinal ${headingDegrees.toInt()}°",
                        style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                // Cluster Style Label Indicator
                Text(
                    text = clusterStyle,
                    style = MotoDriveTypography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.secondaryText
                    )
                )

                // GPS Quality Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hasGpsFix) colors.cardElevated else colors.warningRed.copy(alpha = 0.2f))
                        .border(0.5.dp, if (hasGpsFix) colors.electricAccent else colors.warningRed, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasGpsFix) "GPS LIVE" else "NO GPS",
                        style = MotoDriveTypography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (hasGpsFix) colors.electricAccent else colors.warningRed
                        )
                    )
                }
            }

            // Central Dynamic Gauge Viewport (Selectable: DIGITAL, ANALOG, or HYBRID)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when (clusterStyle) {
                    "ANALOG" -> {
                        AnalogGaugeCanvas(
                            animatedSpeed = animatedSpeed,
                            maxGaugeSpeed = maxGaugeSpeed,
                            speedUnit = speedUnit,
                            hasGpsFix = hasGpsFix,
                            colors = colors
                        )
                    }
                    "HYBRID" -> {
                        HybridGaugeCanvas(
                            animatedSpeed = animatedSpeed,
                            sweepRatio = sweepRatio,
                            maxGaugeSpeed = maxGaugeSpeed,
                            maxSpeedKmH = maxSpeedKmH,
                            averageSpeedKmH = averageSpeedKmH,
                            speedUnit = speedUnit,
                            hasGpsFix = hasGpsFix,
                            colors = colors
                        )
                    }
                    else -> {
                        // "DIGITAL" (Default motorcycle cockpit digital tachometer gauge)
                        DigitalGaugeCanvas(
                            animatedSpeed = animatedSpeed,
                            sweepRatio = sweepRatio,
                            speedUnit = speedUnit,
                            hasGpsFix = hasGpsFix,
                            colors = colors
                        )
                    }
                }
            }

            // Footer / Trip Distance & Ride Time Telemetry Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.card)
                    .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trip Distance
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "TRIP",
                        style = MotoDriveTypography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.secondaryText
                    )
                    Text(
                        text = String.format("%.1f %s", tripDistanceKm, Units.distanceLabel(speedUnit)),
                        style = MotoDriveTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                // Trip Duration
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TIME",
                        style = MotoDriveTypography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.secondaryText
                    )
                    val hours = rideDurationSeconds / 3600
                    val minutes = (rideDurationSeconds % 3600) / 60
                    val seconds = rideDurationSeconds % 60
                    val timeString = if (hours > 0) {
                        String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%02d:%02d", minutes, seconds)
                    }
                    Text(
                        text = timeString,
                        style = MotoDriveTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.electricAccent
                    )
                }
            }
        }
    }
}

/**
 * 1. Default Digital Cockpit Gauge with RPM-inspired tick ring
 */
@Composable
private fun DigitalGaugeCanvas(
    animatedSpeed: Float,
    sweepRatio: Float,
    speedUnit: String,
    hasGpsFix: Boolean,
    colors: CockpitColors
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height) * 0.94f
            val strokeWidth = 13.dp.toPx()
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

            val startAngle = 140f
            val totalSweepAngle = 260f

            // Background Arc Track
            drawArc(
                color = colors.card,
                startAngle = startAngle,
                sweepAngle = totalSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Illuminated Dynamic Arc
            val currentSweep = totalSweepAngle * sweepRatio
            if (currentSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(colors.primaryAccent, colors.electricAccent, colors.primaryAccent),
                        center = Offset(size.width / 2f, size.height / 2f)
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Ticks around the perimeter
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = diameter / 2f + 10.dp.toPx()
            val totalTicks = 36
            for (i in 0..totalTicks) {
                val tickRatio = i.toFloat() / totalTicks
                val angleDeg = startAngle + (tickRatio * totalSweepAngle)
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val isMajor = i % 4 == 0
                val isRedline = tickRatio >= 0.82f
                val tickLength = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                val tickColor = when {
                    isRedline -> colors.warningRed
                    tickRatio <= sweepRatio -> colors.electricAccent
                    isMajor -> colors.primaryText
                    else -> colors.border
                }

                val startX = (center.x + (outerRadius - tickLength) * cos(angleRad)).toFloat()
                val startY = (center.y + (outerRadius - tickLength) * sin(angleRad)).toFloat()
                val endX = (center.x + outerRadius * cos(angleRad)).toFloat()
                val endY = (center.y + outerRadius * sin(angleRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Central Large Digital Speed Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (hasGpsFix) "${animatedSpeed.toInt()}" else "--",
                style = MotoDriveTypography.displayLarge.copy(
                    fontSize = 68.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp
                ),
                color = colors.primaryText
            )

            Text(
                text = speedUnit.uppercase(),
                style = MotoDriveTypography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.electricAccent
            )

            Text(
                text = "GPS VELOCITY",
                style = MotoDriveTypography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                ),
                color = colors.secondaryText
            )
        }
    }
}

/**
 * 2. Classic Motorcycle Round Analog Speedometer with physical sweeping needle
 */
@Composable
private fun AnalogGaugeCanvas(
    animatedSpeed: Float,
    maxGaugeSpeed: Float,
    speedUnit: String,
    hasGpsFix: Boolean,
    colors: CockpitColors
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height) * 0.94f
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f

            // Start at 135° (bottom-left) and sweep 270° to 405° (bottom-right)
            val startAngle = 135f
            val totalSweep = 270f

            // Outer dial border ring
            drawCircle(
                color = colors.card,
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Graduated Dial Numbers (0, 30, 60, 90, 120, 150, 180)
            val textPaint = Paint().apply {
                color = colors.primaryText.toArgb()
                textSize = 10.dp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val stepCount = 6
            for (i in 0..stepCount) {
                val fraction = i.toFloat() / stepCount
                val angleDeg = startAngle + (fraction * totalSweep)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val speedVal = (fraction * maxGaugeSpeed).toInt()

                val numRadius = radius - 16.dp.toPx()
                val numX = (center.x + numRadius * cos(angleRad)).toFloat()
                val numY = (center.y + numRadius * sin(angleRad)).toFloat() + 4.dp.toPx()

                drawContext.canvas.nativeCanvas.drawText("$speedVal", numX, numY, textPaint)
            }

            // Dial Ticks
            val totalTicks = 36
            for (i in 0..totalTicks) {
                val fraction = i.toFloat() / totalTicks
                val angleDeg = startAngle + (fraction * totalSweep)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 6 == 0
                val isRedline = fraction >= 0.8f

                val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                val tickColor = if (isRedline) colors.warningRed else if (isMajor) colors.primaryText else colors.border

                val startX = (center.x + (radius - tickLen) * cos(angleRad)).toFloat()
                val startY = (center.y + (radius - tickLen) * sin(angleRad)).toFloat()
                val endX = (center.x + radius * cos(angleRad)).toFloat()
                val endY = (center.y + radius * sin(angleRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Sweeping Physical Red Needle
            val needleRatio = (animatedSpeed / maxGaugeSpeed).coerceIn(0f, 1f)
            val needleAngleDeg = startAngle + (needleRatio * totalSweep)
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())

            val needleLength = radius - 18.dp.toPx()
            val needleTip = Offset(
                (center.x + needleLength * cos(needleAngleRad)).toFloat(),
                (center.y + needleLength * sin(needleAngleRad)).toFloat()
            )

            // Needle glow / line
            drawLine(
                color = colors.warningRed,
                start = center,
                end = needleTip,
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Needle Pivot Hub
            drawCircle(color = colors.cardElevated, radius = 10.dp.toPx(), center = center)
            drawCircle(color = colors.electricAccent, radius = 4.dp.toPx(), center = center)
        }

        // Small digital odometer box at bottom of analog dial
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.card)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${animatedSpeed.toInt()} $speedUnit",
                style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )
        }
    }
}

/**
 * 3. Hybrid Sport Gauge (Tachometer Arc + Digital Readout + Max/Avg Speed Split)
 */
@Composable
private fun HybridGaugeCanvas(
    animatedSpeed: Float,
    sweepRatio: Float,
    maxGaugeSpeed: Float,
    maxSpeedKmH: Float,
    averageSpeedKmH: Float,
    speedUnit: String,
    hasGpsFix: Boolean,
    colors: CockpitColors
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height) * 0.94f
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

            val startAngle = 135f
            val totalSweepAngle = 270f

            // Background Track
            drawArc(
                color = colors.card,
                startAngle = startAngle,
                sweepAngle = totalSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Sweeping Arc
            val currentSweep = totalSweepAngle * sweepRatio
            if (currentSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(colors.primaryAccent, colors.electricAccent, colors.warningRed),
                        center = Offset(size.width / 2f, size.height / 2f)
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (hasGpsFix) "${animatedSpeed.toInt()}" else "--",
                style = MotoDriveTypography.displayLarge.copy(
                    fontSize = 58.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = colors.primaryText
            )

            Text(
                text = speedUnit.uppercase(),
                style = MotoDriveTypography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = colors.electricAccent
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Split Telemetry Badges (Max Speed & Avg Speed)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.card)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "MAX ${maxSpeedKmH.toInt()}",
                        style = MotoDriveTypography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.warningRed
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.card)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AVG ${averageSpeedKmH.toInt()}",
                        style = MotoDriveTypography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryText
                        )
                    )
                }
            }
        }
    }
}
