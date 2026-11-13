package com.motodrive.tft.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CockpitColors(
    val background: Color,
    val panel: Color,
    val card: Color,
    val cardElevated: Color,
    val primaryAccent: Color,
    val electricAccent: Color,
    val warningRed: Color,
    val border: Color,
    val borderGlow: Color,
    val primaryText: Color,
    val secondaryText: Color
)

val RoninPurpleColors = CockpitColors(
    background = RoninPureBlack,
    panel = RoninPanelBackground,
    card = RoninCardBackground,
    cardElevated = RoninCardElevated,
    primaryAccent = RoninNeonPurple,
    electricAccent = RoninElectricViolet,
    warningRed = RoninRacingRed,
    border = RoninBorderPurple,
    borderGlow = RoninGlowPurple,
    primaryText = PrimaryText,
    secondaryText = RoninLavenderText
)

val MSportBlueColors = CockpitColors(
    background = PrimaryBackground,
    panel = PanelBackground,
    card = CardBackground,
    cardElevated = CardBackgroundElevated,
    primaryAccent = PrimaryBlue,
    electricAccent = ElectricBlue,
    warningRed = AlertRed,
    border = ThinBorderColor,
    borderGlow = ThinBorderGlow,
    primaryText = PrimaryText,
    secondaryText = SecondaryText
)

val LocalCockpitColors = staticCompositionLocalOf { RoninPurpleColors }

private fun getDarkColorScheme(colors: CockpitColors) = darkColorScheme(
    primary = colors.primaryAccent,
    secondary = colors.electricAccent,
    tertiary = colors.warningRed,
    background = colors.background,
    surface = colors.panel,
    surfaceVariant = colors.card,
    onPrimary = colors.primaryText,
    onSecondary = colors.background,
    onTertiary = colors.primaryText,
    onBackground = colors.primaryText,
    onSurface = colors.primaryText,
    onSurfaceVariant = colors.secondaryText,
    error = colors.warningRed
)

@Composable
fun MotoDriveTheme(
    themeStyle: String = "TVS_RONIN_PURPLE",
    content: @Composable () -> Unit
) {
    val activeColors = if (themeStyle == "M_SPORT_BLUE") MSportBlueColors else RoninPurpleColors
    val colorScheme = getDarkColorScheme(activeColors)

    CompositionLocalProvider(LocalCockpitColors provides activeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MotoDriveTypography,
            content = content
        )
    }
}
