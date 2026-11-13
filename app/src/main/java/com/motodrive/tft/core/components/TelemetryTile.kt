package com.motodrive.tft.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.theme.LocalCockpitColors
import com.motodrive.tft.core.theme.MotoDriveTheme
import com.motodrive.tft.core.theme.MotoDriveTypography

/**
 * Reusable glanceable metric tile for motorcycle telemetry.
 */
@Composable
fun TelemetryTile(
    label: String,
    value: String,
    unit: String = "",
    icon: ImageVector? = null,
    iconTint: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val tint = iconTint ?: colors.primaryAccent

    GlassCard(
        modifier = modifier,
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MotoDriveTypography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.secondaryText
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MotoDriveTypography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = colors.primaryText
                    )
                    if (unit.isNotBlank()) {
                        Text(
                            text = unit,
                            style = MotoDriveTypography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.electricAccent
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 180, heightDp = 70)
@Composable
fun TelemetryTilePreview() {
    MotoDriveTheme {
        TelemetryTile(
            label = "Trip Dist",
            value = "124.6",
            unit = "km",
            icon = Icons.Default.Speed
        )
    }
}

