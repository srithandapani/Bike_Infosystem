package com.motodrive.tft.navigation

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import kotlin.math.*

private fun borderStroke(color: Color) = BorderStroke(1.dp, color)

/** Shortest-arc angular low-pass filter to prevent marker spinning and jitter. */
private fun smoothHeading(current: Float, target: Float, alpha: Float = 0.35f): Float {
    var diff = (target - current) % 360f
    if (diff < -180f) diff += 360f
    if (diff > 180f) diff -= 360f
    return (current + diff * alpha + 360f) % 360f
}

/** Computes forward look-ahead camera position in direction of travel. */
private fun computeLookAhead(origin: GeoPoint, bearingDeg: Float, distanceMeters: Double): GeoPoint {
    if (distanceMeters <= 0.0) return origin
    val r = 6371008.8
    val distRatio = distanceMeters / r
    val radBearing = Math.toRadians(bearingDeg.toDouble())
    val lat1 = Math.toRadians(origin.latitude)
    val lon1 = Math.toRadians(origin.longitude)
    val lat2 = asin(sin(lat1) * cos(distRatio) + cos(lat1) * sin(distRatio) * cos(radBearing))
    val lon2 = lon1 + atan2(sin(radBearing) * sin(distRatio) * cos(lat1), cos(distRatio) - sin(lat1) * sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

private fun createVehicleIcon(context: android.content.Context, primaryColor: Int): android.graphics.drawable.BitmapDrawable {
    val sizePx = (36 * context.resources.displayMetrics.density).toInt().coerceAtLeast(36)
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val center = sizePx / 2f

    // Outer glow ring
    paint.color = primaryColor
    paint.alpha = 75
    canvas.drawCircle(center, center, center - 2f, paint)

    // Inner filled circle
    paint.alpha = 255
    canvas.drawCircle(center, center, center - 7f, paint)

    // White directional arrow pointing UP
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    val path = android.graphics.Path().apply {
        moveTo(center, 9f)
        lineTo(center + 9f, sizePx - 10f)
        lineTo(center, sizePx - 14f)
        lineTo(center - 9f, sizePx - 10f)
        close()
    }
    canvas.drawPath(path, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapView(
    currentLocation: Location?,
    currentSpeedKmH: Float = 0f,
    headingDegrees: Float = 0f,
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
    val colors = LocalCockpitColors.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var userMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var destMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var routePolylineRef by remember { mutableStateOf<Polyline?>(null) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var isFollowingRider by remember { mutableStateOf(true) }

    val isMoving = currentSpeedKmH > 5.0f

    // Safety lockout: close search keyboard if rider starts moving > 5 km/h
    LaunchedEffect(isMoving) {
        if (isMoving && isSearchExpanded) {
            isSearchExpanded = false
            Toast.makeText(context, "Search input locked while riding for safety. Use voice.", Toast.LENGTH_SHORT).show()
        }
    }

    // Precision vehicle marker placement (map-matched when navigating) & automotive look-ahead tracking
    LaunchedEffect(currentLocation, uiState.snappedLocation, uiState.navigationBearing, headingDegrees, isFollowingRider) {
        val map = mapViewRef ?: return@LaunchedEffect
        val rawPoint = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
        val targetPoint = if (uiState.isNavigating && uiState.snappedLocation != null) {
            uiState.snappedLocation
        } else {
            rawPoint
        } ?: return@LaunchedEffect

        val targetHeading = when {
            uiState.isNavigating && uiState.navigationBearing != null -> uiState.navigationBearing
            currentSpeedKmH > 3.0f && currentLocation?.hasBearing() == true -> currentLocation.bearing
            else -> headingDegrees
        }

        if (userMarkerRef == null) {
            val marker = Marker(map).apply {
                position = targetPoint
                rotation = targetHeading
                icon = createVehicleIcon(map.context, colors.primaryAccent.toArgb())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "TVS Ronin 225"
            }
            map.overlays.add(marker)
            userMarkerRef = marker
            map.controller.setZoom(16.5)
            map.controller.setCenter(targetPoint)
        } else {
            userMarkerRef?.position = targetPoint
            val currentRot = userMarkerRef?.rotation ?: targetHeading
            userMarkerRef?.rotation = smoothHeading(currentRot, targetHeading, 0.40f)
        }

        if (isFollowingRider && !uiState.isOverviewMode) {
            // Automotive look-ahead: bias camera forward in motion direction
            val lookAheadMeters = if (currentSpeedKmH > 15f) (currentSpeedKmH * 0.4).coerceAtMost(35.0) else 0.0
            val cameraTarget = computeLookAhead(targetPoint, targetHeading, lookAheadMeters)
            map.controller.animateTo(cameraTarget)
        }
        map.invalidate()
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
            // Draw in-app route polyline matching cockpit theme
            val polyline = Polyline(map).apply {
                outlinePaint.color = colors.primaryAccent.toArgb()
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(uiState.routePoints)
            }
            map.overlays.add(0, polyline)
            routePolylineRef = polyline

            // Add in-app destination marker pin
            uiState.destinationPoint?.let { destPt ->
                val destMarker = Marker(map).apply {
                    position = destPt
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = uiState.destinationName
                }
                map.overlays.add(destMarker)
                destMarkerRef = destMarker
            }

            // Route Overview auto-fit
            if (uiState.isOverviewMode || uiState.isRouteLoading) {
                try {
                    val box = BoundingBox.fromGeoPoints(uiState.routePoints)
                    map.zoomToBoundingBox(box, true, 80)
                } catch (_: Exception) {}
            }
        }
        map.invalidate()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        // Embedded High-Performance In-App Map Canvas (Adaptive Daylight / AMOLED Dark Tiles)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    minZoomLevel = 4.0
                    maxZoomLevel = 20.0
                    controller.setZoom(16.0)

                    if (!colors.isLight) {
                        val matrix = ColorMatrix().apply {
                            setSaturation(0f)
                            val invert = ColorMatrix(floatArrayOf(
                                -0.82f, 0f, 0f, 0f, 225f,
                                0f, -0.82f, 0f, 0f, 225f,
                                0f, 0f, -0.82f, 0f, 225f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                            postConcat(invert)
                        }
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                    } else {
                        overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    val startLoc = currentLocation
                    var initialGeo: GeoPoint? = null
                    if (startLoc != null && startLoc.latitude != 0.0 && startLoc.longitude != 0.0) {
                        initialGeo = GeoPoint(startLoc.latitude, startLoc.longitude)
                    } else {
                        try {
                            val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                            val last = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
                                initialGeo = GeoPoint(last.latitude, last.longitude)
                            }
                        } catch (_: SecurityException) {}
                    }
                    if (initialGeo != null) {
                        controller.setCenter(initialGeo)
                    }

                    mapViewRef = this
                }
            },
            update = { map ->
                if (colors.isLight) {
                    map.overlayManager.tilesOverlay.setColorFilter(null)
                } else {
                    val matrix = ColorMatrix().apply {
                        setSaturation(0f)
                        val invert = ColorMatrix(floatArrayOf(
                            -0.82f, 0f, 0f, 0f, 225f,
                            0f, -0.82f, 0f, 0f, 225f,
                            0f, 0f, -0.82f, 0f, 225f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        postConcat(invert)
                    }
                    map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Navigation Bar: Quick Destination Chips & Search Trigger
        AnimatedVisibility(
            visible = !uiState.isNavigating && !isMoving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Trigger Pill
                Surface(
                    onClick = { isSearchExpanded = true },
                    shape = RoundedCornerShape(20.dp),
                    color = colors.cardElevated.copy(alpha = 0.95f),
                    border = borderStroke(colors.electricAccent),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.electricAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Search Destination",
                            style = MotoDriveTypography.labelMedium,
                            color = colors.primaryText
                        )
                    }
                }

                // Voice Command Button
                Surface(
                    onClick = onVoiceClick,
                    shape = CircleShape,
                    color = colors.primaryAccent.copy(alpha = 0.2f),
                    border = borderStroke(colors.primaryAccent),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick Action Chips
                uiState.quickDestinations.forEach { dest ->
                    val icon = when (dest.iconType) {
                        "GAS" -> Icons.Default.LocalGasStation
                        "WRENCH" -> Icons.Default.Build
                        "HOME" -> Icons.Default.Home
                        "WORK" -> Icons.Default.Work
                        else -> Icons.Default.Place
                    }
                    val iconColor = when (dest.iconType) {
                        "GAS" -> Color(0xFFF59E0B)
                        "WRENCH" -> Color(0xFF0284C7)
                        "HOME" -> Color(0xFF16A34A)
                        "WORK" -> colors.primaryAccent
                        else -> colors.primaryAccent
                    }

                    Surface(
                        onClick = {
                            isFollowingRider = true
                            onStartNavigation(dest.query)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = colors.cardElevated.copy(alpha = 0.95f),
                        border = borderStroke(colors.border),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dest.title, style = MotoDriveTypography.labelSmall, color = colors.primaryText)
                        }
                    }
                }

                // Save Location Chips
                Surface(
                    onClick = { onSaveLocationAs("home") },
                    shape = RoundedCornerShape(20.dp),
                    color = colors.cardElevated.copy(alpha = 0.95f),
                    border = borderStroke(colors.border),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set as Home", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                    }
                }

                Surface(
                    onClick = { onSaveLocationAs("work") },
                    shape = RoundedCornerShape(20.dp),
                    color = colors.cardElevated.copy(alpha = 0.95f),
                    border = borderStroke(colors.border),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set as Work", style = MotoDriveTypography.labelSmall, color = colors.secondaryText)
                    }
                }
            }
        }

        // Active Navigation Controls (Bottom of map)
        if (uiState.isNavigating) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Navigation Button
                Surface(
                    onClick = onCancelNavigation,
                    shape = RoundedCornerShape(12.dp),
                    color = colors.warningRed.copy(alpha = 0.2f),
                    border = borderStroke(colors.warningRed),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = colors.warningRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END NAV", style = MotoDriveTypography.labelMedium, color = colors.primaryText, fontWeight = FontWeight.Bold)
                    }
                }

                // Re-center Camera Button (shown if user panned away)
                if (!isFollowingRider) {
                    Surface(
                        onClick = {
                            isFollowingRider = true
                            currentLocation?.let { loc ->
                                mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.cardElevated.copy(alpha = 0.95f),
                        border = borderStroke(colors.primaryAccent),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RE-CENTER", style = MotoDriveTypography.labelMedium, color = colors.primaryText)
                        }
                    }
                }
            }
        }

        // Floating Re-Center / GPS Live Location Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            Surface(
                onClick = {
                    isFollowingRider = true
                    val targetLoc = currentLocation
                    if (targetLoc != null) {
                        mapViewRef?.controller?.setZoom(17.0)
                        mapViewRef?.controller?.animateTo(GeoPoint(targetLoc.latitude, targetLoc.longitude))
                    } else {
                        try {
                            val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                            fused.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    mapViewRef?.controller?.setZoom(17.0)
                                    mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                }
                            }
                        } catch (_: SecurityException) {}
                    }
                },
                shape = CircleShape,
                color = colors.cardElevated.copy(alpha = 0.95f),
                border = borderStroke(if (currentLocation != null) colors.primaryAccent else colors.border),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFollowingRider && currentLocation != null) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                        contentDescription = "Center on My Location",
                        tint = if (currentLocation != null) colors.primaryAccent else colors.secondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // In-App Search Dialog Overlay (Never leaves the app)
        if (isSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.92f))
                    .clickable { isSearchExpanded = false },
                contentAlignment = Alignment.TopCenter
            ) {
                GlowCard(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = colors.cardElevated,
                    borderColor = colors.border
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEARCH DESTINATION",
                                style = MotoDriveTypography.labelLarge.copy(color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { isSearchExpanded = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.secondaryText)
                            }
                        }

                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                                onSearchQueryChange(it)
                            },
                            placeholder = { Text("Search places, petrol pumps, addresses in India...", color = colors.secondaryText, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border,
                                cursorColor = colors.primaryAccent
                            )
                        )

                        // Autocomplete Search Results
                        if (uiState.searchResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                            ) {
                                items(uiState.searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isFollowingRider = true
                                                onStartPlaceNavigation(result)
                                                isSearchExpanded = false
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(result.primaryText, style = MotoDriveTypography.bodyMedium, color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                                            if (result.secondaryText.isNotBlank()) {
                                                Text(result.secondaryText, style = MotoDriveTypography.bodySmall, color = colors.secondaryText, maxLines = 1)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = colors.border)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isSearchExpanded = false }) {
                                Text("CANCEL", color = colors.secondaryText)
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
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("START NAVIGATION", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Overlay when acquiring live GPS fix
        if (currentLocation == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.card.copy(alpha = 0.92f))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.primaryAccent
                    )
                    Text(
                        text = "Acquiring live GPS...",
                        style = MotoDriveTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = colors.primaryText
                    )
                }
            }
        }
    }
}
