package com.motodrive.tft.connectivity.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IphoneNotificationCenterScreen(
    connectionState: IphoneBleConnectionState,
    notifications: List<IphoneNotificationItem>,
    showPreviews: Boolean,
    onConnectIphone: () -> Unit,
    onDismissNotification: (Int) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "CALLS" -> notifications.filter { it.category == IphoneNotificationCategory.INCOMING_CALL }
            "WHATSAPP" -> notifications.filter { it.category == IphoneNotificationCategory.WHATSAPP }
            "INSTAGRAM" -> notifications.filter { it.category == IphoneNotificationCategory.INSTAGRAM }
            "SMS" -> notifications.filter { it.category == IphoneNotificationCategory.SMS }
            "SNAPCHAT" -> notifications.filter { it.category == IphoneNotificationCategory.SNAPCHAT }
            else -> notifications
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header: iPhone Connection Status & Clear All Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (connectionState.isConnected) colors.cardElevated else colors.warningRed.copy(alpha = 0.2f))
                        .border(1.dp, if (connectionState.isConnected) colors.electricAccent else colors.warningRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneIphone,
                        contentDescription = "iPhone",
                        tint = if (connectionState.isConnected) colors.electricAccent else colors.warningRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "IPHONE NOTIFICATIONS",
                        style = MotoDriveTypography.labelLarge.copy(color = colors.electricAccent, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (connectionState.isConnected) "${connectionState.deviceName} (ANCS Active)" else connectionState.statusMessage,
                        style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                        color = if (connectionState.isConnected) SuccessGreen else colors.secondaryText
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connectionState.isConnected) {
                    Button(
                        onClick = onConnectIphone,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONNECT IPHONE", style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                if (notifications.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAll,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("CLEAR ALL", style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.primaryText)
                    }
                }
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf("ALL", "CALLS", "WHATSAPP", "INSTAGRAM", "SMS", "SNAPCHAT")
            categories.forEach { cat ->
                val isSelected = selectedFilter == cat
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.primaryAccent else colors.card)
                        .border(1.dp, if (isSelected) colors.electricAccent else colors.border, RoundedCornerShape(16.dp))
                        .clickable { selectedFilter = cat }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat,
                        style = MotoDriveTypography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = if (isSelected) Color.White else colors.secondaryText
                    )
                }
            }
        }

        // Notification List
        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (connectionState.isConnected) "No new iPhone notifications" else "Connect iPhone to view notifications",
                        style = MotoDriveTypography.bodyLarge,
                        color = colors.secondaryText
                    )
                    Text(
                        text = "Supports Calls, WhatsApp, Instagram, SMS, and Snapchat via Apple ANCS",
                        style = MotoDriveTypography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.secondaryText.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredNotifications, key = { it.notificationUid }) { item ->
                    IphoneNotificationCard(
                        item = item,
                        showPreview = showPreviews,
                        colors = colors,
                        onDismiss = { onDismissNotification(item.notificationUid) }
                    )
                }
            }
        }

        // Setup Helper Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = colors.electricAccent, modifier = Modifier.size(16.dp))
            Text(
                text = "iOS requirement: In iPhone Settings -> Bluetooth -> TVS RONIN -> Enable 'Share System Notifications'.",
                style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun IphoneNotificationCard(
    item: IphoneNotificationItem,
    showPreview: Boolean,
    colors: CockpitColors,
    onDismiss: () -> Unit
) {
    val icon = when (item.category) {
        IphoneNotificationCategory.INCOMING_CALL -> Icons.Default.Phone
        IphoneNotificationCategory.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
        IphoneNotificationCategory.INSTAGRAM -> Icons.Default.CameraAlt
        IphoneNotificationCategory.SMS -> Icons.Default.Sms
        IphoneNotificationCategory.SNAPCHAT -> Icons.Default.ChatBubble
        else -> Icons.Default.Notifications
    }

    val iconColor = when (item.category) {
        IphoneNotificationCategory.INCOMING_CALL -> SuccessGreen
        IphoneNotificationCategory.WHATSAPP -> Color(0xFF25D366)
        IphoneNotificationCategory.INSTAGRAM -> Color(0xFFE1306C)
        IphoneNotificationCategory.SMS -> colors.electricAccent
        IphoneNotificationCategory.SNAPCHAT -> Color(0xFFFFFC00)
        else -> colors.primaryAccent
    }

    val timeString = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    GlowCard(
        shape = RoundedCornerShape(10.dp),
        backgroundColor = colors.cardElevated,
        borderColor = colors.border,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, iconColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = item.appName, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.sender,
                        style = MotoDriveTypography.titleLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = colors.primaryText,
                        maxLines = 1
                    )
                    Text(
                        text = timeString,
                        style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.secondaryText
                    )
                }

                Text(
                    text = if (showPreview && item.messagePreview.isNotBlank()) item.messagePreview else "${item.appName} notification",
                    style = MotoDriveTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = colors.secondaryText,
                    maxLines = 2
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.secondaryText, modifier = Modifier.size(16.dp))
            }
        }
    }
}

