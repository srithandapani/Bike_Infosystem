package com.motodrive.tft.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motodrive.tft.R

@Composable
fun RoninEmblem(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_tvs_horse),
        contentDescription = "TVS Horse Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}
