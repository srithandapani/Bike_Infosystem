package com.motodrive.tft.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    val secondaryText: Color,
    val isLight: Boolean = false
)

val CockpitLightColors = CockpitColors(
    background = LightCockpitBackground,
    panel = LightCockpitPanel,
    card = LightCockpitCard,
    cardElevated = LightCockpitCardElevated,
    primaryAccent = LightPrimaryAccent,
    electricAccent = LightElectricAccent,
    warningRed = LightWarningRed,
    border = LightCockpitBorder,
    borderGlow = LightCockpitBorderGlow,
    primaryText = LightPrimaryText,
    secondaryText = LightSecondaryText,
    isLight = true
)

val CockpitDarkColors = CockpitColors(
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
    secondaryText = SecondaryText,
    isLight = false
)

val MSportBlueColors = CockpitDarkColors

val LocalCockpitColors = staticCompositionLocalOf { CockpitLightColors }

private fun getLightColorScheme(colors: CockpitColors) = lightColorScheme(
    primary = colors.primaryAccent,
    secondary = colors.electricAccent,
    tertiary = colors.warningRed,
    background = colors.background,
    surface = colors.panel,
    surfaceVariant = colors.card,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = colors.primaryText,
    onSurface = colors.primaryText,
    onSurfaceVariant = colors.secondaryText,
    error = colors.warningRed
)

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
    themeStyle: String = "LIGHT_THEME",
    content: @Composable () -> Unit
) {
    val activeColors = when (themeStyle) {
        "DARK_THEME", "M_SPORT_BLUE" -> CockpitDarkColors
        else -> CockpitLightColors
    }
    val colorScheme = if (activeColors.isLight) getLightColorScheme(activeColors) else getDarkColorScheme(activeColors)

    CompositionLocalProvider(LocalCockpitColors provides activeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MotoDriveTypography,
            content = content
        )
    }
}
