package com.motodrive.tft.navigation

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.round

enum class TurnDirection {
    STRAIGHT,
    SLIGHT_LEFT,
    TURN_LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    TURN_RIGHT,
    SHARP_RIGHT,
    ROUNDABOUT,
    U_TURN,
    DESTINATION
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
    val destinationName: String = "",
    val destinationAddress: String = "",
    val destinationPoint: GeoPoint? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val maneuvers: List<NavManeuver> = emptyList(),
    val currentManeuverIndex: Int = 0,
    val nextTurnDirection: TurnDirection = TurnDirection.STRAIGHT,
    val distanceToNextTurn: String = "400 m",
    val nextRoadName: String = "Outer Ring Expressway",
    val remainingDistanceKm: Float = 0.0f,
    val estimatedMinutesRemaining: Int = 0,
    val estimatedArrivalTime: String = "--:--",
    val isOverviewMode: Boolean = false,
    val searchQuery: String = "",
    val quickDestinations: List<QuickDestination> = listOf(
        QuickDestination("Fuel Station", "Nearest Gas & Diesel", "Fuel Station", "GAS"),
        QuickDestination("Motorcycle Service", "TVS Ronin Authorized", "Motorcycle Service", "WRENCH"),
        QuickDestination("Home", "Saved Residence", "Home", "HOME"),
        QuickDestination("Work", "Office Campus", "Work", "WORK"),
        QuickDestination("Scenic Pass", "Mountain Twisties Route", "Scenic Pass", "MOUNTAIN")
    )
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var lastKnownLocation: Location? = null

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Fully in-app navigation launch: resolves destination, generates road geometry,
     * builds step-by-step maneuvers, and begins embedded navigation immediately.
     */
    fun startNavigation(destination: String, origin: Location? = null) {
        val app = getApplication<Application>()
        val startLoc = origin ?: lastKnownLocation ?: Location("default").apply {
            latitude = 12.9716
            longitude = 77.5946
        }

        _uiState.update {
            it.copy(
                isNavigating = true,
                isRouteLoading = true,
                destinationName = destination
            )
        }

        viewModelScope.launch {
            val (destPoint, resolvedTitle) = InAppRouteManager.resolveDestination(
                context = app,
                query = destination,
                origin = startLoc
            )

            val route = InAppRouteManager.buildRoute(
                origin = startLoc,
                destination = destPoint,
                destinationName = resolvedTitle
            )

            val firstManeuver = route.maneuvers.firstOrNull()

            _uiState.update {
                it.copy(
                    isNavigating = true,
                    isRouteLoading = false,
                    destinationName = route.destinationName,
                    destinationPoint = route.destinationPoint,
                    routePoints = route.waypoints,
                    maneuvers = route.maneuvers,
                    currentManeuverIndex = 0,
                    nextTurnDirection = firstManeuver?.turnDirection ?: TurnDirection.STRAIGHT,
                    distanceToNextTurn = formatDistance(firstManeuver?.distanceMeters ?: 400f),
                    nextRoadName = firstManeuver?.roadName ?: "Main Highway",
                    remainingDistanceKm = route.totalDistanceKm,
                    estimatedMinutesRemaining = route.estimatedMinutes,
                    estimatedArrivalTime = route.arrivalTime,
                    isOverviewMode = false
                )
            }
        }
    }

    fun cancelNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                isRouteLoading = false,
                destinationName = "",
                destinationPoint = null,
                routePoints = emptyList(),
                maneuvers = emptyList(),
                currentManeuverIndex = 0
            )
        }
    }

    fun toggleOverviewMode() {
        _uiState.update { it.copy(isOverviewMode = !it.isOverviewMode) }
    }

    /**
     * In-app location engine: updates rider position along the route in real time,
     * advances turn maneuvers as waypoints are reached, and computes remaining distance & ETA.
     */
    fun updateLocationForNavigation(location: Location) {
        lastKnownLocation = location
        val state = _uiState.value
        if (!state.isNavigating || state.routePoints.isEmpty()) return

        val destPoint = state.destinationPoint ?: return

        // Compute direct remaining distance to destination in meters
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            destPoint.latitude, destPoint.longitude,
            results
        )
        val remainingMeters = results[0]
        val remainingKm = round((remainingMeters * 1.25f / 1000f) * 10f) / 10f

        // Progress maneuvers
        val maneuvers = state.maneuvers
        var currentIndex = state.currentManeuverIndex

        if (currentIndex < maneuvers.size) {
            val targetManeuver = maneuvers[currentIndex]
            val stepResults = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                targetManeuver.point.latitude, targetManeuver.point.longitude,
                stepResults
            )
            val distToStep = stepResults[0]

            // Advance step if within 40m
            if (distToStep < 40f && currentIndex < maneuvers.size - 1) {
                currentIndex++
            }

            val currentManeuver = maneuvers[currentIndex]
            val activeDistToStep = if (currentIndex == state.currentManeuverIndex) distToStep else currentManeuver.distanceMeters

            // Calculate estimated minutes remaining at motorcycle speed (40 km/h)
            val minutesLeft = ((remainingKm / 40.0f) * 60f).toInt().coerceAtLeast(1)

            _uiState.update {
                it.copy(
                    remainingDistanceKm = remainingKm,
                    estimatedMinutesRemaining = minutesLeft,
                    currentManeuverIndex = currentIndex,
                    nextTurnDirection = currentManeuver.turnDirection,
                    distanceToNextTurn = formatDistance(activeDistToStep),
                    nextRoadName = currentManeuver.roadName
                )
            }
        }
    }

    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) {
            val km = round((meters / 1000f) * 10f) / 10f
            "$km km"
        } else {
            val roundedM = (round(meters / 50f) * 50).toInt().coerceAtLeast(50)
            "$roundedM m"
        }
    }
}
