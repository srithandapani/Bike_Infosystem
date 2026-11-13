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
import androidx.compose.ui.graphics.Path
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
                color = if (colors.isLight) colors.border.copy(alpha = 0.7f) else colors.card,
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
 * 2. Premium Motorcycle Round Analog Chrono Speedometer with physical sweeping needle,
 * 3D machined dial bevels, precision graduations, luminescent needle with drop shadow,
 * multi-stage turned aluminum spindle hub, and integrated digital speed pod.
 */
@Composable
fun AnalogGaugeCanvas(
    animatedSpeed: Float,
    maxGaugeSpeed: Float,
    speedUnit: String,
    hasGpsFix: Boolean,
    colors: CockpitColors,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(size.width, size.height) * 0.96f
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f

            // Start at 135° (bottom-left) and sweep 270° to 405° (bottom-right)
            val startAngle = 135f
            val totalSweep = 270f
            val needleRatio = (animatedSpeed / maxGaugeSpeed).coerceIn(0f, 1f)
            val activeSweep = totalSweep * needleRatio

            // 1. Outer Machined Bezel Rim
            drawCircle(
                color = if (colors.isLight) colors.border.copy(alpha = 0.9f) else Color(0xFF1E293B),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = if (colors.isLight) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                radius = radius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. High-End Dial Face Gradient (Subtle depth vignette)
            val dialBrush = if (colors.isLight) {
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC),
                        Color(0xFFF1F5F9)
                    ),
                    center = center,
                    radius = radius
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF111827),
                        Color(0xFF0B0F19),
                        Color(0xFF06090E)
                    ),
                    center = center,
                    radius = radius
                )
            }
            drawCircle(
                brush = dialBrush,
                radius = radius - 3.dp.toPx(),
                center = center
            )

            // 3. Inactive Perimeter Track Ring
            val trackRadius = radius - 7.dp.toPx()
            val trackSize = Size(trackRadius * 2f, trackRadius * 2f)
            val trackOffset = Offset(center.x - trackRadius, center.y - trackRadius)

            drawArc(
                color = if (colors.isLight) colors.border.copy(alpha = 0.35f) else Color(0x33334155),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = trackOffset,
                size = trackSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Sport Redline Accent Arc (Upper 25% of the gauge, e.g. 135 km/h to 180 km/h)
            val redlineStartFraction = 0.75f
            val redlineStartAngle = startAngle + (totalSweep * redlineStartFraction)
            val redlineSweepAngle = totalSweep * (1f - redlineStartFraction)
            drawArc(
                color = colors.warningRed.copy(alpha = if (colors.isLight) 0.35f else 0.5f),
                startAngle = redlineStartAngle,
                sweepAngle = redlineSweepAngle,
                useCenter = false,
                topLeft = trackOffset,
                size = trackSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 5. Active Backlit Illuminated Speed Glow Arc (Dynamic LED light guide)
            if (activeSweep > 1f) {
                val activeSweepBrush = Brush.sweepGradient(
                    0.0f to colors.primaryAccent,
                    0.7f to colors.electricAccent,
                    1.0f to if (needleRatio >= 0.75f) colors.warningRed else colors.electricAccent
                )
                drawArc(
                    brush = activeSweepBrush,
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = trackOffset,
                    size = trackSize,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 6. Graduated Precision Ticks & Speed Numbers
            val textPaint = Paint().apply {
                textSize = 10.dp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            // 36 sub-intervals (every 5 km/h)
            val totalIntervals = 36
            for (i in 0..totalIntervals) {
                val fraction = i.toFloat() / totalIntervals
                val angleDeg = startAngle + (fraction * totalSweep)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = (i % 4 == 0)          // Every 20 km/h: 0, 20, 40, 60...
                val isMedium = (i % 2 == 0 && !isMajor) // Every 10 km/h: 10, 30, 50...
                val isRedline = fraction >= redlineStartFraction

                val tickLen = when {
                    isMajor -> 11.dp.toPx()
                    isMedium -> 7.dp.toPx()
                    else -> 4.dp.toPx()
                }

                val tickWidth = when {
                    isMajor -> 2.2.dp.toPx()
                    isMedium -> 1.4.dp.toPx()
                    else -> 0.9.dp.toPx()
                }

                val tickColor = when {
                    isRedline -> colors.warningRed
                    isMajor -> colors.primaryText
                    isMedium -> colors.secondaryText
                    else -> if (colors.isLight) colors.border else Color(0xFF475569)
                }

                val outerR = radius - 8.dp.toPx()
                val innerR = outerR - tickLen

                val startX = (center.x + innerR * cos(angleRad)).toFloat()
                val startY = (center.y + innerR * sin(angleRad)).toFloat()
                val endX = (center.x + outerR * cos(angleRad)).toFloat()
                val endY = (center.y + outerR * sin(angleRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )

                // Radial Major Speed Numbers
                if (isMajor) {
                    val speedVal = (fraction * maxGaugeSpeed).toInt()
                    val numRadius = radius - 26.dp.toPx()
                    val numX = (center.x + numRadius * cos(angleRad)).toFloat()
                    val numY = (center.y + numRadius * sin(angleRad)).toFloat() + 3.5.dp.toPx()

                    textPaint.color = if (isRedline) colors.warningRed.toArgb() else colors.primaryText.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("$speedVal", numX, numY, textPaint)
                }
            }

            // 7. TVS RONIN Brand Watermark (at 12 o'clock inside dial)
            val brandPaint = Paint().apply {
                color = colors.electricAccent.toArgb()
                textSize = 7.5.dp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.18f
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "TVS RONIN",
                center.x,
                center.y - radius * 0.38f,
                brandPaint
            )

            // 8. Sweeping Aerodynamic Sports Needle
            val needleAngleDeg = startAngle + (needleRatio * totalSweep)
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
            val dirX = cos(needleAngleRad).toFloat()
            val dirY = sin(needleAngleRad).toFloat()
            val normX = -dirY
            val normY = dirX

            val pointerLength = radius - 12.dp.toPx()
            val tailLength = 18.dp.toPx()
            val baseWidth = 3.5.dp.toPx()
            val tailWidth = 4.5.dp.toPx()

            // Needle points
            val tip = center + Offset(dirX * pointerLength, dirY * pointerLength)
            val baseL = center - Offset(normX * baseWidth, normY * baseWidth)
            val baseR = center + Offset(normX * baseWidth, normY * baseWidth)
            val tailTip = center - Offset(dirX * tailLength, dirY * tailLength)
            val tailL = tailTip - Offset(normX * tailWidth, normY * tailWidth)
            val tailR = tailTip + Offset(normX * tailWidth, normY * tailWidth)

            // A) Needle Drop Shadow (3D Depth onto dial face)
            val shadowOffset = Offset(2.dp.toPx(), 3.5.dp.toPx())
            val shadowPath = Path().apply {
                moveTo(tailTip.x + shadowOffset.x, tailTip.y + shadowOffset.y)
                lineTo(tailL.x + shadowOffset.x, tailL.y + shadowOffset.y)
                lineTo(baseL.x + shadowOffset.x, baseL.y + shadowOffset.y)
                lineTo(tip.x + shadowOffset.x, tip.y + shadowOffset.y)
                lineTo(baseR.x + shadowOffset.x, baseR.y + shadowOffset.y)
                lineTo(tailR.x + shadowOffset.x, tailR.y + shadowOffset.y)
                close()
            }
            drawPath(path = shadowPath, color = Color(0x33000000))

            // B) Needle Main Body (Racing Red / Warning Red)
            val needlePath = Path().apply {
                moveTo(tailTip.x, tailTip.y)
                lineTo(tailL.x, tailL.y)
                lineTo(baseL.x, baseL.y)
                lineTo(tip.x, tip.y)
                lineTo(baseR.x, baseR.y)
                lineTo(tailR.x, tailR.y)
                close()
            }
            drawPath(path = needlePath, color = colors.warningRed)

            // C) Needle Optical Luminescent Spine (Illuminated optical core)
            val spineStart = center + Offset(dirX * 5.dp.toPx(), dirY * 5.dp.toPx())
            val spineEnd = center + Offset(dirX * (pointerLength - 6.dp.toPx()), dirY * (pointerLength - 6.dp.toPx()))
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = spineStart,
                end = spineEnd,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 9. Multi-Stage Precision Turned Aluminum Center Spindle
            // Outer spindle rim
            drawCircle(
                color = if (colors.isLight) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                radius = 14.dp.toPx(),
                center = center
            )
            drawCircle(
                color = if (colors.isLight) colors.border else Color(0xFF334155),
                radius = 14.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            // Middle cap disc
            drawCircle(
                color = colors.cardElevated,
                radius = 10.dp.toPx(),
                center = center
            )
            // Center jewel rivet
            drawCircle(
                color = colors.primaryAccent,
                radius = 4.5.dp.toPx(),
                center = center
            )
            // Specular reflection highlight dot
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 1.2.dp.toPx(),
                center = center + Offset(-1.5.dp.toPx(), -1.5.dp.toPx())
            )
        }

        // 10. Integrated Digital Telemetry Pod (Bottom center of dial)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${animatedSpeed.toInt()}",
                    style = MotoDriveTypography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = colors.primaryText
                )
                Text(
                    text = speedUnit.uppercase(),
                    style = MotoDriveTypography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = colors.electricAccent
                )
            }
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
                color = if (colors.isLight) colors.border.copy(alpha = 0.7f) else colors.card,
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
