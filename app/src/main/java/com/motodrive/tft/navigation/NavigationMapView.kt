package com.motodrive.tft.navigation

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
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
import com.motodrive.tft.core.components.GlowCard
import com.motodrive.tft.core.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapView(
    currentLocation: Location?,
    headingDegrees: Float,
    uiState: NavigationUiState,
    onStartNavigation: (String) -> Unit,
    onCancelNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var userMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var destMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var routePolylineRef by remember { mutableStateOf<Polyline?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var isFollowingRider by remember { mutableStateOf(true) }

    // Update vehicle marker & camera tracking when location changes
    LaunchedEffect(currentLocation, headingDegrees, isFollowingRider) {
        val map = mapViewRef ?: return@LaunchedEffect
        if (currentLocation != null) {
            val geoPoint = GeoPoint(currentLocation.latitude, currentLocation.longitude)
            if (userMarkerRef == null) {
                val marker = Marker(map).apply {
                    position = geoPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "TVS Ronin Motorcycle"
                }
                map.overlays.add(marker)
                userMarkerRef = marker
                map.controller.setZoom(16.5)
                map.controller.setCenter(geoPoint)
            } else {
                userMarkerRef?.position = geoPoint
                userMarkerRef?.rotation = headingDegrees
            }

            if (isFollowingRider && !uiState.isOverviewMode) {
                map.controller.animateTo(geoPoint)
            }
            map.invalidate()
        }
    }

    // Update Route Polyline & Destination Marker on the embedded map
    LaunchedEffect(uiState.routePoints, uiState.destinationPoint) {
        val map = mapViewRef ?: return@LaunchedEffect

        // Remove previous route polyline if any
        routePolylineRef?.let { map.overlays.remove(it) }
        routePolylineRef = null

        // Remove previous destination marker if any
        destMarkerRef?.let { map.overlays.remove(it) }
        destMarkerRef = null

        if (uiState.isNavigating && uiState.routePoints.isNotEmpty()) {
            // 1. Draw glowing M Sport Blue in-app route polyline
            val polyline = Polyline(map).apply {
                outlinePaint.color = android.graphics.Color.parseColor("#009FE3")
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(uiState.routePoints)
            }
            map.overlays.add(0, polyline) // Underneath markers
            routePolylineRef = polyline

            // 2. Add in-app destination marker pin
            uiState.destinationPoint?.let { destPt ->
                val destMarker = Marker(map).apply {
                    position = destPt
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = uiState.destinationName
                }
                map.overlays.add(destMarker)
                destMarkerRef = destMarker
            }

            // 3. Zoom map to fit the full route overview initially
            val lats = uiState.routePoints.map { it.latitude }
            val lons = uiState.routePoints.map { it.longitude }
            val minLat = lats.minOrNull() ?: 12.97
            val maxLat = lats.maxOrNull() ?: 12.98
            val minLon = lons.minOrNull() ?: 77.59
            val maxLon = lons.maxOrNull() ?: 77.60

            val box = BoundingBox(maxLat + 0.005, maxLon + 0.005, minLat - 0.005, minLon - 0.005)
            map.zoomToBoundingBox(box, true, 80)
            map.invalidate()
        } else {
            map.invalidate()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBackground)
            .border(1.dp, ThinBorderColor, RoundedCornerShape(16.dp))
    ) {
        // Embedded Map View with Automotive Cockpit Night Matrix Filter
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(12.9716, 77.5946))

                    // Automotive dark mode color filter to invert bright map tiles
                    val inverseMatrix = ColorMatrix(
                        floatArrayOf(
                            -0.85f, 0f, 0f, 0f, 235f,
                            0f, -0.85f, 0f, 0f, 235f,
                            0f, -0.75f, 0f, 0f, 245f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))

                    mapViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top-Left: Next-Turn In-App Navigation Guidance HUD Card
        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        ) {
            GlowCard(
                shape = RoundedCornerShape(12.dp),
                backgroundColor = CardBackgroundElevated,
                borderColor = ElectricBlue,
                borderWidth = 1.5.dp,
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBlueAccent)
                            .border(1.dp, ElectricBlue, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (uiState.nextTurnDirection) {
                                TurnDirection.TURN_LEFT -> Icons.Default.TurnLeft
                                TurnDirection.TURN_RIGHT -> Icons.Default.TurnRight
                                TurnDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                                TurnDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                                TurnDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
                                TurnDirection.ROUNDABOUT -> Icons.Default.RoundaboutRight
                                TurnDirection.DESTINATION -> Icons.Default.Place
                                else -> Icons.Default.Straight
                            },
                            contentDescription = "Turn Instruction",
                            tint = ElectricBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.nextTurnDirection == TurnDirection.DESTINATION) "ARRIVING" else "IN ${uiState.distanceToNextTurn}",
                            style = MotoDriveTypography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = ElectricBlue
                            )
                        )
                        Text(
                            text = uiState.nextRoadName,
                            style = MotoDriveTypography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = PrimaryText,
                            maxLines = 1
                        )
                        Text(
                            text = "To: ${uiState.destinationName}",
                            style = MotoDriveTypography.bodyMedium.copy(fontSize = 11.sp),
                            color = SecondaryText,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = { onCancelNavigation() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop In-App Navigation",
                            tint = AlertRed
                        )
                    }
                }
            }
        }

        // Top-Right: Map Zoom, Route Overview & Recenter Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recenter Vehicle Location Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(1.dp, if (isFollowingRider) ElectricBlue else ThinBorderColor, CircleShape)
                    .clickable {
                        isFollowingRider = true
                        val loc = currentLocation
                        val map = mapViewRef
                        if (loc != null && map != null) {
                            map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            map.controller.setZoom(16.5)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter on Motorcycle",
                    tint = if (isFollowingRider) ElectricBlue else PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Route Overview / Full Route View Toggle (when route active)
            if (uiState.isNavigating && uiState.routePoints.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardBackgroundElevated)
                        .border(1.dp, ElectricBlue, CircleShape)
                        .clickable {
                            isFollowingRider = false
                            val map = mapViewRef ?: return@clickable
                            val lats = uiState.routePoints.map { it.latitude }
                            val lons = uiState.routePoints.map { it.longitude }
                            val minLat = lats.minOrNull() ?: 12.97
                            val maxLat = lats.maxOrNull() ?: 12.98
                            val minLon = lons.minOrNull() ?: 77.59
                            val maxLon = lons.maxOrNull() ?: 77.60
                            val box = BoundingBox(maxLat + 0.005, maxLon + 0.005, minLat - 0.005, minLon - 0.005)
                            map.zoomToBoundingBox(box, true, 80)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = "Full Route Overview",
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Zoom In Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(1.dp, ThinBorderColor, CircleShape)
                    .clickable {
                        isFollowingRider = false
                        mapViewRef?.controller?.zoomIn()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom Out Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(1.dp, ThinBorderColor, CircleShape)
                    .clickable {
                        isFollowingRider = false
                        mapViewRef?.controller?.zoomOut()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom Area: Quick In-App Destination Chips & Navigation Progress Strip
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick Destination Chips Bar (All launch in-app navigation directly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Trigger Chip
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBackgroundElevated)
                        .border(1.dp, ElectricBlue, RoundedCornerShape(18.dp))
                        .clickable { isSearchExpanded = !isSearchExpanded }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Search Destination",
                            style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryText
                        )
                    }
                }

                // Preset Destination Chips (Home, Work, Fuel Station, Service, Scenic Pass)
                uiState.quickDestinations.forEach { dest ->
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardBackground)
                            .border(1.dp, ThinBorderColor, RoundedCornerShape(18.dp))
                            .clickable {
                                // Starts fully integrated in-app navigation directly without external apps!
                                isFollowingRider = true
                                onStartNavigation(dest.title)
                            }
                            .padding(horizontal = 12.dp),
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
                                    else -> Icons.Default.Terrain
                                },
                                contentDescription = dest.title,
                                tint = if (dest.iconType == "GAS") WarningOrange else ElectricBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = dest.title,
                                style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryText
                            )
                        }
                    }
                }
            }

            // In-App Navigation Active Progress & Route Control Strip
            AnimatedVisibility(visible = uiState.isNavigating) {
                GlowCard(
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = PanelBackground,
                    borderColor = ElectricBlue,
                    borderWidth = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Remaining Distance
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = "Distance",
                                tint = ElectricBlue,
                                modifier = Modifier.size(17.dp)
                            )
                            Column {
                                Text(
                                    text = "DISTANCE",
                                    style = MotoDriveTypography.labelSmall.copy(fontSize = 9.sp),
                                    color = SecondaryText
                                )
                                Text(
                                    text = "${uiState.remainingDistanceKm} km",
                                    style = MotoDriveTypography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = PrimaryText
                                )
                            }
                        }

                        // ETA
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "ETA",
                                tint = SuccessGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Column {
                                Text(
                                    text = "ARRIVAL",
                                    style = MotoDriveTypography.labelSmall.copy(fontSize = 9.sp),
                                    color = SecondaryText
                                )
                                Text(
                                    text = "${uiState.estimatedArrivalTime} (${uiState.estimatedMinutesRemaining} min)",
                                    style = MotoDriveTypography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SuccessGreen
                                    )
                                )
                            }
                        }

                        // In-App Trip Termination Control
                        Button(
                            onClick = { onCancelNavigation() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkBlueAccent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "END NAV",
                                style = MotoDriveTypography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = PrimaryText
                            )
                        }
                    }
                }
            }
        }

        // In-App Search Dialog Overlay (Never leaves the app)
        if (isSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBackground.copy(alpha = 0.85f))
                    .clickable { isSearchExpanded = false },
                contentAlignment = Alignment.TopCenter
            ) {
                GlowCard(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = CardBackgroundElevated,
                    borderColor = ElectricBlue
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEARCH DESTINATION",
                                style = MotoDriveTypography.labelLarge.copy(color = ElectricBlue, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "IN-APP ROUTE ENGINE",
                                style = MotoDriveTypography.labelSmall.copy(fontSize = 10.sp),
                                color = SecondaryText
                            )
                        }

                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            placeholder = { Text("Search location, address, city...", color = SecondaryText) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = ThinBorderColor,
                                cursorColor = ElectricBlue
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isSearchExpanded = false }) {
                                Text("CANCEL", color = SecondaryText)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (searchInput.isNotBlank()) {
                                        isFollowingRider = true
                                        onStartNavigation(searchInput)
                                        isSearchExpanded = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("START IN-APP NAV", color = PrimaryText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
