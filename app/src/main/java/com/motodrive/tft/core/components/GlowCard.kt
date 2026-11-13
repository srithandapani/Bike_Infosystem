package com.motodrive.tft.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motodrive.tft.core.theme.LocalCockpitColors

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalCockpitColors.current
    val topBg = if (backgroundColor != Color.Unspecified) backgroundColor else colors.card
    val bottomBg = if (colors.isLight) colors.cardElevated else colors.panel
    val borderCol = if (borderColor != Color.Unspecified) borderColor else colors.border

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(topBg, bottomBg)
                )
            )
            .border(borderWidth, borderCol, shape),
        content = content
    )
}

