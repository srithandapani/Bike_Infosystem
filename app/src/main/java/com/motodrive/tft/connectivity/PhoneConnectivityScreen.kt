package com.motodrive.tft.connectivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun PhoneConnectivityScreen(
    uiState: ConnectivityUiState,
    onQuickCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    var dialNumber by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left Column: Bluetooth Paired Devices & Helmet Headsets
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BLUETOOTH AUDIO & HELMETS",
                style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.pairedDevices) { dev ->
                    GlowCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (dev.isConnected) colors.cardElevated else colors.card,
                        borderColor = if (dev.isConnected) colors.primaryAccent else colors.border
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (dev.isConnected) colors.primaryAccent.copy(alpha = 0.15f) else colors.cardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (dev.isHelmet) Icons.Default.HeadsetMic else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (dev.isConnected) colors.primaryAccent else colors.secondaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = dev.name,
                                        style = MotoDriveTypography.titleLarge.copy(fontSize = 14.sp),
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = if (dev.isConnected) "Connected Audio" else "Available",
                                        style = MotoDriveTypography.bodyMedium,
                                        color = if (dev.isConnected) SuccessGreen else colors.secondaryText
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (dev.isConnected) SuccessGreen else if (colors.isLight) colors.border else GaugeTickColor)
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Speed Dial & Hands-Free Keypad
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "HANDS-FREE SPEED DIAL",
                style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent)
            )

            // Speed Dial Preset Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Pair("Emergency Roadside", "112"),
                    Pair("Bike Service Garage", "1800-MOTOR"),
                    Pair("Home", "1234567890")
                ).forEach { contact ->
                    GlowCard(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onQuickCall(contact.second) },
                        backgroundColor = colors.card,
                        borderColor = colors.border
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = contact.first,
                                style = MotoDriveTypography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = colors.primaryText,
                                maxLines = 1
                            )
                            Text(
                                text = contact.second,
                                style = MotoDriveTypography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }

            // Number Display & Quick Dial
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.card,
                borderColor = colors.primaryAccent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialNumber.ifEmpty { "Enter Number..." },
                        style = MotoDriveTypography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (dialNumber.isNotEmpty()) colors.primaryText else colors.secondaryText
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dialNumber.isNotEmpty()) {
                            IconButton(onClick = { dialNumber = dialNumber.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = colors.secondaryText)
                            }
                        }

                        Button(
                            onClick = {
                                if (dialNumber.isNotEmpty()) onQuickCall(dialNumber)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CALL", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Compact Numeric Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val keyRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                keyRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.card)
                                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                    .clickable { dialNumber += digit },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit,
                                    style = MotoDriveTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

