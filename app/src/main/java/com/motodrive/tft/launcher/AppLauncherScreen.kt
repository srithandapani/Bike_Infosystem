package com.motodrive.tft.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*

data class LauncherAppItem(
    val title: String,
    val category: String,
    val icon: ImageVector,
    val isInternalNav: Boolean = false,
    val packageName: String? = null,
    val intentAction: String? = null
)

@Composable
fun AppLauncherScreen(
    onOpenNavigation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val apps = listOf(
        LauncherAppItem("TFT In-App Navigation", "Cockpit Map", Icons.Default.Navigation, isInternalNav = true),
        LauncherAppItem("Spotify", "Music", Icons.Default.MusicNote, packageName = "com.spotify.music"),
        LauncherAppItem("YouTube Music", "Music", Icons.Default.Headphones, packageName = "com.google.android.apps.youtube.music"),
        LauncherAppItem("Phone Dialer", "Communication", Icons.Default.Phone, intentAction = Intent.ACTION_DIAL),
        LauncherAppItem("WhatsApp", "Communication", Icons.AutoMirrored.Filled.Chat, packageName = "com.whatsapp"),
        LauncherAppItem("Device Settings", "Utilities", Icons.Default.Settings, intentAction = android.provider.Settings.ACTION_SETTINGS),
        LauncherAppItem("Bluetooth Setup", "Utilities", Icons.Default.Bluetooth, intentAction = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "APPLICATION LAUNCHER",
                style = MotoDriveTypography.labelLarge.copy(color = ElectricBlue)
            )
            Text(
                text = "PARKED MOTORCYCLE APPS",
                style = MotoDriveTypography.labelSmall,
                color = SecondaryText
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps) { app ->
                GlowCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (app.isInternalNav) {
                                onOpenNavigation()
                            } else {
                                launchAppOrIntent(context, app)
                            }
                        },
                    backgroundColor = CardBackgroundElevated,
                    borderColor = ThinBorderColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(DarkBlueAccent)
                                .border(1.dp, ElectricBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = app.icon,
                                contentDescription = app.title,
                                tint = ElectricBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = app.title,
                            style = MotoDriveTypography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = PrimaryText,
                            maxLines = 1
                        )

                        Text(
                            text = app.category,
                            style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                            color = SecondaryText,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun launchAppOrIntent(context: Context, app: LauncherAppItem) {
    try {
        if (app.packageName != null) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }

        if (app.intentAction != null) {
            val intent = Intent(app.intentAction).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }

        Toast.makeText(context, "${app.title} is not installed on this device", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open ${app.title}: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

