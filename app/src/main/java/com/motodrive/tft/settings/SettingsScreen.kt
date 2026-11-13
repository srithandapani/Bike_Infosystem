package com.motodrive.tft.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSpeedUnitChange: (String) -> Unit,
    onTempUnitChange: (String) -> Unit,
    on24HourChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBtAutoLaunchChange: (Boolean) -> Unit,
    onClusterStyleChange: (String) -> Unit = {},
    onThemeStyleChange: (String) -> Unit = {},
    onIphoneNotificationsChange: (Boolean) -> Unit = {},
    onShowNotificationPreviewsChange: (Boolean) -> Unit = {},
    onNotifyIncomingCallsChange: (Boolean) -> Unit = {},
    onNotifyWhatsAppChange: (Boolean) -> Unit = {},
    onNotifyInstagramChange: (Boolean) -> Unit = {},
    onNotifySmsChange: (Boolean) -> Unit = {},
    onNotifySnapchatChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Cockpit Instrument Cluster & Display Theme
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TFT DASHBOARD CONFIGURATION",
                style = MotoDriveTypography.labelLarge.copy(color = colors.electricAccent, fontWeight = FontWeight.Bold)
            )

            // Speedometer Cluster Mode (Digital, Analog, Hybrid)
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text("Speedometer Style", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Select gauge display mode", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("DIGITAL", "ANALOG", "HYBRID").forEach { style ->
                            FilterChip(
                                selected = settings.clusterStyle == style,
                                onClick = { onClusterStyleChange(style) },
                                label = { Text(style, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryAccent,
                                    selectedLabelColor = colors.primaryText,
                                    containerColor = colors.cardElevated,
                                    labelColor = colors.secondaryText
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Dashboard Theme Palette (Daylight Light vs TVS Ronin Red vs M Sport Blue)
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text("Cockpit Color Theme", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("High-visibility Daylight vs Dark themes", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = settings.themeStyle == "LIGHT_THEME" || (settings.themeStyle != "TVS_RONIN_RED" && settings.themeStyle != "M_SPORT_BLUE"),
                            onClick = { onThemeStyleChange("LIGHT_THEME") },
                            label = { Text("LIGHT THEME", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LightPrimaryAccent,
                                selectedLabelColor = Color.White,
                                containerColor = colors.cardElevated,
                                labelColor = colors.secondaryText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.themeStyle == "TVS_RONIN_RED" || settings.themeStyle == "TVS_RONIN_PURPLE",
                            onClick = { onThemeStyleChange("TVS_RONIN_RED") },
                            label = { Text("RONIN RED", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoninNeonRed,
                                selectedLabelColor = Color.White,
                                containerColor = colors.cardElevated,
                                labelColor = colors.secondaryText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.themeStyle == "M_SPORT_BLUE",
                            onClick = { onThemeStyleChange("M_SPORT_BLUE") },
                            label = { Text("M SPORT BLUE", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = colors.cardElevated,
                                labelColor = colors.secondaryText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Speed Unit Setting
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Speedometer Units", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Select GPS velocity unit", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = settings.speedUnit == "km/h",
                            onClick = { onSpeedUnitChange("km/h") },
                            label = { Text("KM/H") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryAccent,
                                selectedLabelColor = colors.primaryText
                            )
                        )
                        FilterChip(
                            selected = settings.speedUnit == "mph",
                            onClick = { onSpeedUnitChange("mph") },
                            label = { Text("MPH") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryAccent,
                                selectedLabelColor = colors.primaryText
                            )
                        )
                    }
                }
            }

            // 24-Hour Clock Toggle
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("24-Hour Clock", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Toggle 12h AM/PM vs 24h", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }
                    Switch(
                        checked = settings.is24HourClock,
                        onCheckedChange = on24HourChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryText,
                            checkedTrackColor = colors.primaryAccent
                        )
                    )
                }
            }

            // Keep Display Awake
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Keep Display Awake", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Motorcycle handlebar mount mode", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }
                    Switch(
                        checked = settings.keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryText,
                            checkedTrackColor = colors.primaryAccent
                        )
                    )
                }
            }
        }

        // Right Column: iPhone Connectivity, Notifications & Telemetry
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "IPHONE CONNECTIVITY & ANCS ALERTS",
                style = MotoDriveTypography.labelLarge.copy(color = colors.electricAccent, fontWeight = FontWeight.Bold)
            )

            // iPhone Notifications Master Switch
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("iPhone Notifications (ANCS)", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Sync iPhone notifications over BLE", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }
                    Switch(
                        checked = settings.iphoneNotificationsEnabled,
                        onCheckedChange = onIphoneNotificationsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryText,
                            checkedTrackColor = colors.primaryAccent
                        )
                    )
                }
            }

            // Notification Message Preview Privacy Switch
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Message Previews", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                        Text("Display text snippet on cockpit", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    }
                    Switch(
                        checked = settings.showNotificationPreviews,
                        onCheckedChange = onShowNotificationPreviewsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryText,
                            checkedTrackColor = colors.primaryAccent
                        )
                    )
                }
            }

            // Category Notification Toggles Card
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Notification Filters", style = MotoDriveTypography.titleLarge, color = colors.primaryText)

                    // Incoming Calls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Incoming Calls", style = MotoDriveTypography.bodyMedium, color = colors.primaryText)
                        Switch(checked = settings.notifyIncomingCalls, onCheckedChange = onNotifyIncomingCallsChange)
                    }

                    // WhatsApp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WhatsApp Messages", style = MotoDriveTypography.bodyMedium, color = colors.primaryText)
                        Switch(checked = settings.notifyWhatsApp, onCheckedChange = onNotifyWhatsAppChange)
                    }

                    // Instagram
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Instagram Messages", style = MotoDriveTypography.bodyMedium, color = colors.primaryText)
                        Switch(checked = settings.notifyInstagram, onCheckedChange = onNotifyInstagramChange)
                    }

                    // SMS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SMS / iMessage", style = MotoDriveTypography.bodyMedium, color = colors.primaryText)
                        Switch(checked = settings.notifySms, onCheckedChange = onNotifySmsChange)
                    }

                    // Snapchat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Snapchat Alerts", style = MotoDriveTypography.bodyMedium, color = colors.primaryText)
                        Switch(checked = settings.notifySnapchat, onCheckedChange = onNotifySnapchatChange)
                    }
                }
            }

            // Device Telemetry Info
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Hardware Cockpit Telemetry", style = MotoDriveTypography.titleLarge, color = colors.primaryText)
                    Text("Target Device: Realme C83 (RMX5256IN)", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    Text("OS Build: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MotoDriveTypography.bodyMedium, color = colors.secondaryText)
                    Text("Bike Target: TVS Ronin 225 Edition", style = MotoDriveTypography.bodyMedium, color = colors.electricAccent)
                }
            }
        }
    }
}
