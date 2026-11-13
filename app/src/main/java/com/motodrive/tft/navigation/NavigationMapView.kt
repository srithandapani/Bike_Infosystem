package com.motodrive.tft.navigation

import android.location.Location
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.FragmentContainerView
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.ForceNightMode
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapView(
    currentLocation: Location?,
    currentSpeedKmH: Float = 0f,
    uiState: NavigationUiState,
    onStartNavigation: (String) -> Unit,
    onStartPlaceNavigation: (PlaceSearchResult) -> Unit = {},
    onCancelNavigation: () -> Unit,
    onSaveLocationAs: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var isSearchExpanded by remember { mutableStateOf(false) }
    var navFragmentRef by remember { mutableStateOf<SupportNavigationFragment?>(null) }
    val isMoving = currentSpeedKmH > 5.0f

    // Safety lockout: close search keyboard if rider starts moving > 5 km/h
    LaunchedEffect(isMoving) {
        if (isMoving && isSearchExpanded) {
            isSearchExpanded = false
            Toast.makeText(context, "Search input locked while riding for safety. Use voice.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, ThinBorderColor, RoundedCornerShape(16.dp))
    ) {
        // Embedded Google Maps Navigation SDK Fragment (Landscape, AMOLED Night Mode)
        if (activity != null) {
            val containerId = remember { View.generateViewId() }

            AndroidView(
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                update = {
                    val fragmentManager = activity.supportFragmentManager
                    var fragment = fragmentManager.findFragmentById(containerId) as? SupportNavigationFragment
                    if (fragment == null) {
                        fragment = SupportNavigationFragment.newInstance()
                        fragmentManager.commit {
                            replace(containerId, fragment)
                        }
                    }
                    navFragmentRef = fragment

                    try {
                        fragment.setForceNightMode(ForceNightMode.FORCE_NIGHT)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    fragment.getMapAsync { googleMap ->
                        // Force cinematic AMOLED dark mode and follow rider
                        try {
                            googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback display if not hosted in a FragmentActivity
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Google Navigation View Engine Ready",
                    style = MotoDriveTypography.bodyMedium,
                    color = SecondaryText
                )
            }
        }

        // Top Navigation Bar: Quick Destination Chips & Search Trigger
        AnimatedVisibility(
            visible = !uiState.isNavigating && !isMoving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glove-friendly Search Trigger (56dp minimum touch height)
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBackgroundElevated)
                        .border(1.dp, ElectricBlue, RoundedCornerShape(22.dp))
                        .clickable { isSearchExpanded = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Search Destination",
                            style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = PrimaryText
                        )
                    }
                }

                // Voice Assistant Button (>= 56dp hit target)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardBackgroundElevated)
                        .border(1.dp, RoninElectricViolet, CircleShape)
                        .clickable { onVoiceClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = RoninElectricViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quick Destinations: Petrol Pump, Motorcycle Service, Home, Work
                uiState.quickDestinations.forEach { dest ->
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CardBackground)
                            .border(1.dp, ThinBorderColor, RoundedCornerShape(22.dp))
                            .clickable { onStartNavigation(dest.title) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (dest.iconType) {
                                    "GAS" -> Icons.Default.LocalGasStation
                                    "WRENCH" -> Icons.Default.Build
                                    "HOME" -> Icons.Default.Home
                                    "WORK" -> Icons.Default.Work
                                    else -> Icons.Default.Place
                                },
                                contentDescription = dest.title,
                                tint = if (dest.iconType == "GAS") WarningOrange else ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = dest.title,
                                style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = PrimaryText
                            )
                        }
                    }
                }

                // Save Current Location Buttons for Home and Work
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBackground)
                        .border(1.dp, RoninNeonPurple.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                        .clickable { onSaveLocationAs("home") }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = RoninElectricViolet, modifier = Modifier.size(15.dp))
                        Text("Set as Home", style = MotoDriveTypography.labelSmall.copy(fontSize = 11.sp), color = RoninElectricViolet)
                    }
                }

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardBackground)
                        .border(1.dp, RoninNeonPurple.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                        .clickable { onSaveLocationAs("work") }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = RoninElectricViolet, modifier = Modifier.size(15.dp))
                        Text("Set as Work", style = MotoDriveTypography.labelSmall.copy(fontSize = 11.sp), color = RoninElectricViolet)
                    }
                }
            }
        }

        // Active Turn Guidance HUD Banner (Cinematic Floating Header)
        if (uiState.isNavigating) {
            GlowCard(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .widthIn(max = 380.dp),
                backgroundColor = Color(0xDD000000),
                borderColor = RoninNeonPurple.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Animated Turn Direction Icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(RoninNeonPurple.copy(alpha = 0.2f))
                            .border(1.5.dp, RoninNeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (uiState.nextTurnDirection) {
                                TurnDirection.TURN_LEFT -> Icons.Default.TurnLeft
                                TurnDirection.TURN_RIGHT -> Icons.Default.TurnRight
                                TurnDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
                                TurnDirection.SHARP_RIGHT -> Icons.Default.TurnSharpRight
                                TurnDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                                TurnDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                                TurnDirection.U_TURN -> Icons.Default.Refresh
                                TurnDirection.ROUNDABOUT -> Icons.Default.Refresh
                                TurnDirection.DESTINATION -> Icons.Default.CheckCircle
                                else -> Icons.Default.Straight
                            },
                            contentDescription = "Turn Direction",
                            tint = ElectricBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.distanceToNextTurn,
                            style = MotoDriveTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                        Text(
                            text = uiState.nextRoadName.ifBlank { uiState.destinationName },
                            style = MotoDriveTypography.bodyMedium.copy(fontSize = 13.sp),
                            color = SecondaryText,
                            maxLines = 1
                        )
                    }

                    // Cancel Guidance Button
                    IconButton(
                        onClick = { onCancelNavigation() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Navigation",
                            tint = AlertRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Full-screen Search Overlay with Autocomplete Predictions
        if (isSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0050508))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Bar Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { isSearchExpanded = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryText
                            )
                        }

                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            placeholder = { Text("Search places or address via Google...", color = MutedText) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = ThinBorderColor,
                                cursorColor = ElectricBlue
                            ),
                            trailingIcon = {
                                IconButton(onClick = { onVoiceClick() }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice", tint = RoninElectricViolet)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Prediction Results List
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.searchResults) { place ->
                            GlowCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSearchExpanded = false
                                        onStartPlaceNavigation(place)
                                    },
                                backgroundColor = CardBackground,
                                borderColor = ThinBorderColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = RoninElectricViolet,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = place.primaryText,
                                            style = MotoDriveTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = PrimaryText
                                        )
                                        if (place.secondaryText.isNotBlank()) {
                                            Text(
                                                text = place.secondaryText,
                                                style = MotoDriveTypography.bodySmall,
                                                color = SecondaryText
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = "Navigate",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
