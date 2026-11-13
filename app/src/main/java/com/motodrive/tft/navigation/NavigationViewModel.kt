package com.motodrive.tft.navigation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.navigation.*
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

enum class NavEngine {
    GOOGLE_NAV_SDK,
    FALLBACK_ROUTES_API,
    FALLBACK_MAPS_APP
}

data class QuickDestination(
    val title: String,
    val subtitle: String,
    val query: String,
    val iconType: String
)

data class NavigationUiState(
    val isNavigating: Boolean = false,
    val isRouteLoading: Boolean = false,
    val isArrived: Boolean = false,
    val errorMessage: String? = null,
    val destinationName: String = "",
    val destinationAddress: String = "",
    val destinationLatLng: LatLng? = null,
    val destinationPoint: GeoPoint? = null,
    val snappedLocation: GeoPoint? = null,
    val navigationBearing: Float? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val nextTurnDirection: TurnDirection = TurnDirection.STRAIGHT,
    val distanceToNextTurn: String = "--",
    val nextRoadName: String = "",
    val remainingDistanceKm: Float = 0.0f,
    val estimatedMinutesRemaining: Int = 0,
    val estimatedArrivalTime: String = "--:--",
    val speedLimitKmH: Int? = null,
    val isOverviewMode: Boolean = false,
    val searchQuery: String = "",
    val speedUnit: String = "km/h",
    val travelMode: String = "TWO_WHEELER (Motorcycle)",
    val isTermsAccepted: Boolean = false,
    val isNavigatorReady: Boolean = false,
    val navEngine: NavEngine = NavEngine.GOOGLE_NAV_SDK,
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val quickDestinations: List<QuickDestination> = listOf(
        QuickDestination("Fuel Station", "Nearest Petrol Pump", "Fuel Station", "GAS"),
        QuickDestination("Motorcycle Service", "TVS Ronin Authorized", "Motorcycle Service", "WRENCH"),
        QuickDestination("Home", "Saved Residence", "Home", "HOME"),
        QuickDestination("Work", "Saved Office Campus", "Work", "WORK")
    )
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val settingsRepo = SettingsRepository(application)
    private var navigator: Navigator? = null
    private var lastKnownLocation: Location? = null
    private var activeRoute: ActiveRoute? = null
    private var lastSegmentIndex: Int = 0

    init {
        // Collect real-time updates forwarded by the Foreground Service
        viewModelScope.launch {
            GoogleNavigationService.navUpdates.collect { update ->
                if (update.isNavigating) {
                    val remainingKm = update.remainingDistanceMeters / 1000f
                    val minutesLeft = (update.remainingTimeSeconds / 60)

                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.SECOND, update.remainingTimeSeconds)
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val arrivalTime = timeFormat.format(calendar.time)

                    val formattedTurnDist = if (update.distanceToTurnMeters >= 1000) {
                        String.format(Locale.US, "%.1f km", update.distanceToTurnMeters / 1000f)
                    } else {
                        "${update.distanceToTurnMeters} m"
                    }

                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            nextTurnDirection = update.maneuverDirection,
                            distanceToNextTurn = formattedTurnDist,
                            nextRoadName = update.roadName,
                            remainingDistanceKm = remainingKm,
                            estimatedMinutesRemaining = minutesLeft,
                            estimatedArrivalTime = arrivalTime
                        )
                    }
                }
            }
        }

        // Sync Speed Unit with Settings
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                _uiState.update { it.copy(speedUnit = settings.speedUnit) }
            }
        }
    }

    /**
     * Attempts Google Navigation SDK initialization. If terms need acceptance or
     * SDK is unprovisioned, it handles it gracefully without crashing or blocking.
     */
    fun initNavigator(activity: Activity) {
        if (navigator != null) return

        try {
            if (!NavigationApi.areTermsAccepted(activity.application)) {
                activity.runOnUiThread {
                    try {
                        NavigationApi.showTermsAndConditionsDialog(
                            activity,
                            "TVS Ronin",
                            "Navigation Terms",
                            null,
                            { accepted ->
                                if (accepted) {
                                    requestNavigator(activity)
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            isNavigatorReady = false,
                                            navEngine = NavEngine.FALLBACK_ROUTES_API
                                        )
                                    }
                                }
                            },
                            TermsAndConditionsCheckOption.ENABLED
                        )
                    } catch (_: Exception) {
                        // If dialog fails or Headless, request navigator directly
                        requestNavigator(activity)
                    }
                }
            } else {
                requestNavigator(activity)
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isNavigatorReady = false,
                    navEngine = NavEngine.FALLBACK_ROUTES_API
                )
            }
        }
    }

    private fun requestNavigator(activity: Activity) {
        try {
            NavigationApi.getNavigator(
                activity,
                object : NavigationApi.NavigatorListener {
                    override fun onNavigatorReady(nav: Navigator) {
                        navigator = nav
                        _uiState.update {
                            it.copy(
                                isNavigatorReady = true,
                                isTermsAccepted = true,
                                navEngine = NavEngine.GOOGLE_NAV_SDK,
                                errorMessage = null
                            )
                        }
                        setupNavigatorListeners(nav, activity)
                    }

                    override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                        _uiState.update {
                            it.copy(
                                isNavigatorReady = false,
                                navEngine = NavEngine.FALLBACK_ROUTES_API
                            )
                        }
                    }
                }
            )
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isNavigatorReady = false,
                    navEngine = NavEngine.FALLBACK_ROUTES_API
                )
            }
        }
    }

    private fun setupNavigatorListeners(nav: Navigator, activity: Activity) {
        try {
            nav.registerServiceForNavUpdates(
                activity.packageName,
                GoogleNavigationService::class.java.name,
                5
            )
        } catch (_: Exception) {}

        try {
            nav.addRemainingTimeOrDistanceChangedListener(15, 50) {
                val td = nav.currentTimeAndDistance
                if (td != null) {
                    val remainingKm = td.meters / 1000f
                    val minutesLeft = (td.seconds / 60)
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.SECOND, td.seconds)
                    val arrival = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

                    _uiState.update {
                        it.copy(
                            remainingDistanceKm = remainingKm,
                            estimatedMinutesRemaining = minutesLeft,
                            estimatedArrivalTime = arrival
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            nav.addArrivalListener {
                _uiState.update {
                    it.copy(
                        isArrived = true,
                        isNavigating = false,
                        distanceToNextTurn = "0 m",
                        nextRoadName = "Destination Reached",
                        nextTurnDirection = TurnDirection.DESTINATION,
                        remainingDistanceKm = 0f,
                        estimatedMinutesRemaining = 0
                    )
                }
                activeRoute = null
                GoogleNavigationService.stop(getApplication())
            }
        } catch (_: Exception) {}

        try {
            nav.addReroutingListener {
                _uiState.update { it.copy(isRouteLoading = true) }
            }
        } catch (_: Exception) {}
    }

    /**
     * Updates real GPS location from Speedometer / Sensor fused pipeline.
     * Projects rider along the active route and updates turn distance, ETA, and reroutes if off route.
     */
    fun updateCurrentLocation(location: Location) {
        lastKnownLocation = location

        val route = activeRoute
        if (route != null && _uiState.value.isNavigating) {
            val riderPoint = GeoPoint(location.latitude, location.longitude)
            val progress = InAppRouteManager.project(route, riderPoint, lastSegmentIndex)
            lastSegmentIndex = progress.segmentIndex

            // Precision Map Matching: Lock marker to road corridor if within 30 metres
            val isCorridor = progress.offRouteMeters <= 30.0
            val snappedLoc = if (isCorridor) progress.snappedPoint else null
            val navBearing = if (isCorridor) progress.segmentBearing else null

            // 1. Off-Route detection (refined threshold: 45 metres for prompt re-routing)
            if (progress.offRouteMeters > 45.0) {
                viewModelScope.launch {
                    val destPt = route.destinationPoint
                    val destTitle = route.destinationName
                    val newRoute = InAppRouteManager.buildRoute(riderPoint, GeocodeResult(destPt, destTitle))
                    if (newRoute != null && newRoute.path.geometry.isNotEmpty()) {
                        activeRoute = newRoute
                        lastSegmentIndex = 0
                        _uiState.update { it.copy(routePoints = newRoute.path.geometry) }
                    }
                }
                return
            }

            // 2. Remaining Distance along the real road network
            val remainingMeters = (route.totalMeters - progress.alongMeters).coerceAtLeast(0.0)
            val remainingKm = (remainingMeters / 1000.0).toFloat()

            // 3. Arrival detection (within 20m of destination)
            if (remainingMeters < 20.0) {
                _uiState.update {
                    it.copy(
                        isArrived = true,
                        isNavigating = false,
                        snappedLocation = null,
                        navigationBearing = null,
                        distanceToNextTurn = "0 m",
                        nextRoadName = "Destination Reached",
                        nextTurnDirection = TurnDirection.DESTINATION,
                        remainingDistanceKm = 0f,
                        estimatedMinutesRemaining = 0
                    )
                }
                activeRoute = null
                GoogleNavigationService.stop(getApplication())
                return
            }

            // 4. Find next upcoming turn maneuver
            val nextStep = route.path.steps.firstOrNull { it.geometryIndex > progress.segmentIndex }
            val distanceToStepM = if (nextStep != null) {
                val stepAlong = route.cumulative.getOrElse(nextStep.geometryIndex) { route.totalMeters }
                (stepAlong - progress.alongMeters).coerceAtLeast(0.0).toFloat()
            } else {
                remainingMeters.toFloat()
            }

            // ETA calculation based on road duration proportion
            val progressFraction = if (route.totalMeters > 0) progress.alongMeters / route.totalMeters else 0.0
            val remainingSeconds = ((1.0 - progressFraction) * route.path.durationSeconds).roundToInt()
            val minutesLeft = (remainingSeconds / 60)

            val cal = Calendar.getInstance()
            cal.add(Calendar.SECOND, remainingSeconds)
            val arrival = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

            val turnDir = nextStep?.turn ?: TurnDirection.STRAIGHT
            val road = nextStep?.roadName ?: route.path.steps.lastOrNull()?.roadName ?: "the road"

            _uiState.update {
                it.copy(
                    snappedLocation = snappedLoc,
                    navigationBearing = navBearing,
                    nextTurnDirection = turnDir,
                    distanceToNextTurn = formatTurnDistance(distanceToStepM),
                    nextRoadName = road,
                    remainingDistanceKm = remainingKm,
                    estimatedMinutesRemaining = minutesLeft,
                    estimatedArrivalTime = arrival
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
        if (query.trim().length >= 2) {
            viewModelScope.launch {
                val results = GooglePlacesClient.searchDestinations(getApplication(), query, lastKnownLocation)
                _uiState.update { it.copy(searchResults = results) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList()) }
    }

    fun setSpeedUnit(unit: String) {
        _uiState.update { it.copy(speedUnit = unit) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleOverviewMode() {
        _uiState.update { it.copy(isOverviewMode = !it.isOverviewMode) }
    }

    /**
     * Saves rider's current location as Home or Work.
     */
    fun saveCurrentLocationAs(type: String) {
        val loc = lastKnownLocation
        if (loc == null) {
            _uiState.update { it.copy(errorMessage = "Waiting for GPS fix to save $type.") }
            return
        }
        val pt = GeoPoint(loc.latitude, loc.longitude)
        InAppRouteManager.savePlace(getApplication(), type, pt)
        GooglePlacesClient.savePlace(getApplication(), type, LatLng(loc.latitude, loc.longitude))
        _uiState.update {
            it.copy(errorMessage = "Saved current location as ${type.replaceFirstChar { c -> c.uppercase() }}")
        }
    }

    /**
     * Starts In-App Navigation to a destination string or saved shortcut.
     * Navigation runs 100% inside the app.
     */
    fun startNavigation(destination: String) {
        val context = getApplication<Application>()
        val loc = lastKnownLocation
        val destKey = destination.trim().lowercase(Locale.ROOT)

        _uiState.update {
            it.copy(
                isRouteLoading = true,
                errorMessage = null,
                destinationName = destination
            )
        }

        viewModelScope.launch {
            val resolved: GeocodeResult? = withContext(Dispatchers.IO) {
                when (destKey) {
                    "home", "saved residence" -> InAppRouteManager.resolveDestination(context, "home", loc)
                    "work", "saved office campus", "office" -> InAppRouteManager.resolveDestination(context, "work", loc)
                    "fuel station", "nearest gas & diesel", "petrol pump" -> InAppRouteManager.resolveDestination(context, "fuel station", loc)
                    "motorcycle service", "tvs ronin authorized", "service" -> InAppRouteManager.resolveDestination(context, "motorcycle service", loc)
                    else -> {
                        var placeResult: GeocodeResult? = null
                        try {
                            val predictions = GooglePlacesClient.searchDestinations(context, destination, loc)
                            val first = predictions.firstOrNull()
                            if (first != null) {
                                val latLng = GooglePlacesClient.fetchPlaceCoordinates(context, first.placeId)
                                if (latLng != null) {
                                    placeResult = GeocodeResult(GeoPoint(latLng.latitude, latLng.longitude), first.primaryText)
                                }
                            }
                        } catch (_: Exception) {}

                        placeResult ?: InAppRouteManager.resolveDestination(context, destination, loc)
                    }
                }
            }

            if (resolved == null) {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        isNavigating = false,
                        errorMessage = if (destKey == "home" || destKey == "work") {
                            "${destination.replaceFirstChar { c -> c.uppercase() }} not set yet. Tap 'Set as ${destination.replaceFirstChar { c -> c.uppercase() }}' to save."
                        } else {
                            "Destination '$destination' could not be resolved."
                        }
                    )
                }
                return@launch
            }

            if (loc == null) {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        isNavigating = false,
                        errorMessage = "Acquiring live GPS location... Please wait a moment."
                    )
                }
                return@launch
            }

            val originPoint = GeoPoint(loc.latitude, loc.longitude)
            startInAppGuidance(originPoint, resolved)
        }
    }

    fun startNavigationToPlace(place: PlaceSearchResult) {
        val loc = lastKnownLocation
        viewModelScope.launch {
            _uiState.update { it.copy(isRouteLoading = true, destinationName = place.primaryText) }
            val latLng = place.latLng ?: GooglePlacesClient.fetchPlaceCoordinates(getApplication(), place.placeId)
            val destPoint = if (latLng != null) {
                GeoPoint(latLng.latitude, latLng.longitude)
            } else {
                val res = InAppRouteManager.resolveDestination(getApplication(), place.primaryText, loc)
                res?.point
            }

            if (destPoint != null) {
                if (loc == null) {
                    _uiState.update {
                        it.copy(
                            isRouteLoading = false,
                            errorMessage = "Acquiring live GPS location... Please wait a moment."
                        )
                    }
                    return@launch
                }
                val origin = GeoPoint(loc.latitude, loc.longitude)
                startInAppGuidance(origin, GeocodeResult(destPoint, place.primaryText))
            } else {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        errorMessage = "Could not find coordinates for ${place.primaryText}"
                    )
                }
            }
        }
    }

    private suspend fun startInAppGuidance(origin: GeoPoint, dest: GeocodeResult) {
        val route = InAppRouteManager.buildRoute(origin, dest)
        if (route == null || route.path.geometry.isEmpty()) {
            _uiState.update {
                it.copy(
                    isRouteLoading = false,
                    isNavigating = false,
                    errorMessage = "No road route found to ${dest.title}."
                )
            }
            return
        }

        activeRoute = route
        lastSegmentIndex = 0

        val firstStep = route.path.steps.firstOrNull()
        val totalKm = (route.totalMeters / 1000.0).toFloat()
        val totalMinutes = (route.path.durationSeconds / 60.0).roundToInt()

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, route.path.durationSeconds.roundToInt())
        val arrivalTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

        _uiState.update {
            it.copy(
                isNavigating = true,
                isRouteLoading = false,
                isArrived = false,
                destinationName = dest.title,
                destinationPoint = dest.point,
                destinationLatLng = LatLng(dest.point.latitude, dest.point.longitude),
                routePoints = route.path.geometry,
                nextTurnDirection = firstStep?.turn ?: TurnDirection.STRAIGHT,
                distanceToNextTurn = formatTurnDistance(firstStep?.distanceMeters ?: 0f),
                nextRoadName = firstStep?.roadName ?: "the road",
                remainingDistanceKm = totalKm,
                estimatedMinutesRemaining = totalMinutes,
                estimatedArrivalTime = arrivalTime,
                errorMessage = null
            )
        }

        GoogleNavigationService.start(getApplication())

        // Also start Google Navigation SDK if licensed & ready
        navigator?.let { nav ->
            try {
                val waypoint = Waypoint.builder()
                    .setLatLng(dest.point.latitude, dest.point.longitude)
                    .setTitle(dest.title)
                    .build()
                val routingOptions = RoutingOptions().apply {
                    travelMode(RoutingOptions.TravelMode.TWO_WHEELER)
                }
                nav.setDestination(waypoint, routingOptions).setOnResultListener { code ->
                    if (code == Navigator.RouteStatus.OK) {
                        nav.startGuidance()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun cancelNavigation() {
        activeRoute = null
        try {
            navigator?.stopGuidance()
            navigator?.clearDestinations()
        } catch (_: Exception) {}
        GoogleNavigationService.stop(getApplication())
        _uiState.update {
            it.copy(
                isNavigating = false,
                isRouteLoading = false,
                isArrived = false,
                destinationName = "",
                destinationPoint = null,
                destinationLatLng = null,
                snappedLocation = null,
                navigationBearing = null,
                routePoints = emptyList(),
                distanceToNextTurn = "--",
                nextRoadName = "",
                remainingDistanceKm = 0f,
                estimatedMinutesRemaining = 0
            )
        }
    }

    private fun formatTurnDistance(meters: Float): String {
        return when {
            meters <= 12f -> "NOW"
            meters < 1000f -> "${meters.roundToInt()} m"
            else -> String.format(Locale.US, "%.1f km", meters / 1000f)
        }
    }
}
