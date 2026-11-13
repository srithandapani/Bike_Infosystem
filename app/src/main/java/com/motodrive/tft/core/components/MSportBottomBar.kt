package com.motodrive.tft.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.theme.*

enum class MotoDriveTab(val title: String, val icon: ImageVector) {
    NAVIGATION("NAV", Icons.Default.Navigation),
    MEDIA("MEDIA", Icons.Default.MusicNote),
    ALERTS("ALERTS", Icons.Default.Notifications),
    PHONE("PHONE", Icons.Default.Phone),
    APPS("APPS", Icons.Default.Apps),
    TRIP("TRIP", Icons.Default.Speed),
    SETTINGS("SETTINGS", Icons.Default.Settings)
}

@Composable
fun MSportBottomBar(
    currentTab: MotoDriveTab,
    onTabSelected: (MotoDriveTab) -> Unit,
    isRidingMode: Boolean,
    onToggleRidingMode: () -> Unit,
    onTriggerVoice: () -> Unit,
    unreadAlertsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(colors.background)
            .border(width = 0.5.dp, color = colors.border, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Navigation Tabs Group
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MotoDriveTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val targetBg = if (isSelected) colors.cardElevated else Color.Transparent
                val targetBorder = if (isSelected) colors.electricAccent else Color.Transparent
                val targetContentColor = if (isSelected) colors.electricAccent else colors.secondaryText

                val animatedBg by animateColorAsState(targetBg, label = "tabBg")
                val animatedBorder by animateColorAsState(targetBorder, label = "tabBorder")
                val animatedColor by animateColorAsState(targetContentColor, label = "tabColor")

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedBg)
                        .border(1.dp, animatedBorder, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = animatedColor,
                                modifier = Modifier.size(16.dp)
                            )
                            if (tab == MotoDriveTab.ALERTS && unreadAlertsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(colors.warningRed)
                                )
                            }
                        }
                        Text(
                            text = tab.title,
                            style = MotoDriveTypography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = animatedColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Riding Mode Indicator / Toggle Pill
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isRidingMode) colors.cardElevated else colors.card)
                .border(
                    width = 1.dp,
                    color = if (isRidingMode) colors.electricAccent else colors.border,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onToggleRidingMode() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRidingMode) SuccessGreen else WarningOrange)
                )
                Text(
                    text = if (isRidingMode) "RIDING" else "PARKED",
                    style = MotoDriveTypography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isRidingMode) colors.electricAccent else colors.secondaryText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // TVS Voice Assistant Quick Action Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent)
                .border(1.5.dp, colors.electricAccent, CircleShape)
                .clickable { onTriggerVoice() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
