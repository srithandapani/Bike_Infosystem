package com.motodrive.tft.navigation

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.navigation.*
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
     * Initializes Google Navigation SDK with terms acceptance and listener registration.
     */
    fun initNavigator(activity: Activity) {
        if (navigator != null) return

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
                        val errorDescription = when (errorCode) {
                            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> {
                                try {
                                    NavigationApi.showTermsAndConditionsDialog(
                                        activity,
                                        "TVS Ronin",
                                        "Navigation Terms",
                                        null,
                                        { accepted -> if (accepted) initNavigator(activity) },
                                        TermsAndConditionsCheckOption.ENABLED
                                    )
                                } catch (_: Exception) {}
                                "Google Navigation Terms not accepted. Please review and accept terms."
                            }
                            NavigationApi.ErrorCode.NOT_AUTHORIZED ->
                                "Google Navigation API key not authorized. Verify Navigation SDK is enabled in Google Cloud Console."
                            NavigationApi.ErrorCode.NETWORK_ERROR ->
                                "Network connection unavailable for Google Navigation."
                            else -> "Navigation SDK initialization error code: $errorCode"
                        }
                        _uiState.update {
                            it.copy(
                                isNavigatorReady = false,
                                navEngine = NavEngine.FALLBACK_ROUTES_API,
                                errorMessage = errorDescription
                            )
                        }
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isNavigatorReady = false,
                    navEngine = NavEngine.FALLBACK_ROUTES_API,
                    errorMessage = "Navigation init exception: ${e.message}"
                )
            }
        }
    }

    private fun setupNavigatorListeners(nav: Navigator, activity: Activity) {
        // 1. Register background foreground service for turn-by-turn guidance
        nav.registerServiceForNavUpdates(
            activity.packageName,
            GoogleNavigationService::class.java.name,
            5
        )

        // 2. Remaining Time / Distance Updates
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

        // 3. Arrival Listener
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
            GoogleNavigationService.stop(getApplication())
        }

        // 4. Rerouting Listener
        nav.addReroutingListener {
            _uiState.update { it.copy(isRouteLoading = true) }
        }
    }

    fun updateCurrentLocation(location: Location) {
        lastKnownLocation = location
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
            _uiState.update { it.copy(errorMessage = "Waiting for valid GPS location to save $type.") }
            return
        }
        val latLng = LatLng(loc.latitude, loc.longitude)
        GooglePlacesClient.savePlace(getApplication(), type, latLng)
        _uiState.update {
            it.copy(errorMessage = "Saved current location as ${type.replaceFirstChar { c -> c.uppercase() }}")
        }
    }

    /**
     * Starts Google Navigation to a destination string or saved shortcut.
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
            var targetLatLng: LatLng? = null
            var targetTitle = destination

            when (destKey) {
                "home", "saved residence" -> {
                    targetLatLng = GooglePlacesClient.getSavedPlace(context, "home")
                    targetTitle = "Home"
                    if (targetLatLng == null) {
                        _uiState.update {
                            it.copy(
                                isRouteLoading = false,
                                isNavigating = false,
                                errorMessage = "Home location not set yet. Tap 'Set as Home' to save current location."
                            )
                        }
                        return@launch
                    }
                }
                "work", "saved office campus", "office" -> {
                    targetLatLng = GooglePlacesClient.getSavedPlace(context, "work")
                    targetTitle = "Work"
                    if (targetLatLng == null) {
                        _uiState.update {
                            it.copy(
                                isRouteLoading = false,
                                isNavigating = false,
                                errorMessage = "Work location not set yet. Tap 'Set as Work' to save current location."
                            )
                        }
                        return@launch
                    }
                }
                "fuel station", "nearest gas & diesel", "petrol pump" -> {
                    val amenity = GooglePlacesClient.findQuickAmenity(context, "gas", loc)
                    if (amenity?.latLng != null) {
                        targetLatLng = amenity.latLng
                        targetTitle = amenity.primaryText
                    } else {
                        _uiState.update {
                            it.copy(
                                isRouteLoading = false,
                                isNavigating = false,
                                errorMessage = "No fuel stations found nearby via Places SDK."
                            )
                        }
                        return@launch
                    }
                }
                "motorcycle service", "tvs ronin authorized", "service" -> {
                    val amenity = GooglePlacesClient.findQuickAmenity(context, "wrench", loc)
                    if (amenity?.latLng != null) {
                        targetLatLng = amenity.latLng
                        targetTitle = amenity.primaryText
                    } else {
                        _uiState.update {
                            it.copy(
                                isRouteLoading = false,
                                isNavigating = false,
                                errorMessage = "No motorcycle service centers found nearby via Places SDK."
                            )
                        }
                        return@launch
                    }
                }
                else -> {
                    // Search place prediction
                    val predictions = GooglePlacesClient.searchDestinations(context, destination, loc)
                    val first = predictions.firstOrNull()
                    if (first != null) {
                        targetLatLng = GooglePlacesClient.fetchPlaceCoordinates(context, first.placeId)
                        targetTitle = first.primaryText
                    }
                }
            }

            if (targetLatLng == null) {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        isNavigating = false,
                        errorMessage = "Destination '$destination' could not be resolved."
                    )
                }
                return@launch
            }

            navigateWithGoogleSdk(targetLatLng, targetTitle)
        }
    }

    fun startNavigationToPlace(place: PlaceSearchResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRouteLoading = true, destinationName = place.primaryText) }
            val latLng = place.latLng ?: GooglePlacesClient.fetchPlaceCoordinates(getApplication(), place.placeId)
            if (latLng != null) {
                navigateWithGoogleSdk(latLng, place.primaryText)
            } else {
                _uiState.update {
                    it.copy(
                        isRouteLoading = false,
                        errorMessage = "Failed to fetch coordinates for ${place.primaryText}"
                    )
                }
            }
        }
    }

    private fun navigateWithGoogleSdk(destLatLng: LatLng, title: String) {
        val nav = navigator
        if (nav == null) {
            // Fallback: Launch Google Maps app via intent
            launchFallbackGoogleMaps(destLatLng, title)
            return
        }

        try {
            val waypoint = Waypoint.builder()
                .setLatLng(destLatLng.latitude, destLatLng.longitude)
                .setTitle(title)
                .build()

            // TWO_WHEELER Mode explicitly configured
            val routingOptions = RoutingOptions().apply {
                travelMode(RoutingOptions.TravelMode.TWO_WHEELER)
            }

            val routePendingResult = nav.setDestination(waypoint, routingOptions)
            routePendingResult.setOnResultListener { code ->
                if (code == Navigator.RouteStatus.OK) {
                    nav.startGuidance()
                    GoogleNavigationService.start(getApplication())
                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            isRouteLoading = false,
                            isArrived = false,
                            destinationName = title,
                            destinationLatLng = destLatLng,
                            travelMode = "TWO_WHEELER (Motorcycle)",
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isNavigating = false,
                            isRouteLoading = false,
                            errorMessage = "Google Navigation route failed: status code $code"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isNavigating = false,
                    isRouteLoading = false,
                    errorMessage = "Navigation error: ${e.message}"
                )
            }
            launchFallbackGoogleMaps(destLatLng, title)
        }
    }

    /**
     * Fallback C: Launch Google Maps with motorcycle two-wheeler travel mode.
     */
    private fun launchFallbackGoogleMaps(latLng: LatLng, title: String) {
        val app = getApplication<Application>()
        try {
            val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=l")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.google.android.apps.maps")
            }
            app.startActivity(intent)
            _uiState.update {
                it.copy(
                    isNavigating = true,
                    isRouteLoading = false,
                    navEngine = NavEngine.FALLBACK_MAPS_APP,
                    destinationName = title,
                    destinationLatLng = latLng,
                    errorMessage = "Navigation launched in Google Maps (Motorcycle Mode)"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isNavigating = false,
                    isRouteLoading = false,
                    errorMessage = "Could not launch Google Maps: ${e.message}"
                )
            }
        }
    }

    fun cancelNavigation() {
        navigator?.stopGuidance()
        navigator?.clearDestinations()
        GoogleNavigationService.stop(getApplication())
        _uiState.update {
            it.copy(
                isNavigating = false,
                isRouteLoading = false,
                isArrived = false,
                destinationName = "",
                destinationLatLng = null,
                distanceToNextTurn = "--",
                nextRoadName = "",
                remainingDistanceKm = 0f,
                estimatedMinutesRemaining = 0
            )
        }
    }
}
