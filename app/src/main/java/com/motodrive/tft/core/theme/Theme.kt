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

val RoninRedColors = CockpitColors(
    background = RoninPureBlack,
    panel = RoninPanelBackground,
    card = RoninCardBackground,
    cardElevated = RoninCardElevated,
    primaryAccent = RoninNeonRed,
    electricAccent = RoninElectricRed,
    warningRed = RoninRacingRed,
    border = RoninBorderRed,
    borderGlow = RoninGlowRed,
    primaryText = PrimaryText,
    secondaryText = RoninSilverText
)

// Alias for seamless backward compatibility
val RoninPurpleColors = RoninRedColors

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

val LocalCockpitColors = staticCompositionLocalOf { RoninRedColors }

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
    themeStyle: String = "TVS_RONIN_RED",
    content: @Composable () -> Unit
) {
    val activeColors = when (themeStyle) {
        "M_SPORT_BLUE" -> MSportBlueColors
        else -> RoninRedColors
    }
    val colorScheme = getDarkColorScheme(activeColors)

    CompositionLocalProvider(LocalCockpitColors provides activeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MotoDriveTypography,
            content = content
        )
    }
}
