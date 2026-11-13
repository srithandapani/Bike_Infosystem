package com.motodrive.tft.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motodrive.tft.core.theme.LocalCockpitColors
import com.motodrive.tft.core.theme.MotoDriveTheme

/**
 * Cinematic dark glass card panel with subtle vignette gradient and soft colored border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalCockpitColors.current
    val strokeColor = borderColor ?: colors.border.copy(alpha = 0.6f)

    val glassBrush = Brush.verticalGradient(
        colors = listOf(
            colors.cardElevated.copy(alpha = 0.75f),
            colors.card.copy(alpha = 0.85f),
            colors.background.copy(alpha = 0.95f)
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(glassBrush)
            .border(borderWidth, strokeColor, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Preview(widthDp = 300, heightDp = 150)
@Composable
fun GlassCardPreview() {
    MotoDriveTheme {
        Box(modifier = Modifier.background(Color.Black).padding(16.dp)) {
            GlassCard(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Cinematic Glass Panel Preview",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

