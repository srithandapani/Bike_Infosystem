package com.motodrive.tft.navigation

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motodrive.tft.core.util.Units
import com.motodrive.tft.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

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
    val isArrived: Boolean = false,
    val errorMessage: String? = null,
    val destinationName: String = "",
    val destinationAddress: String = "",
    val destinationPoint: GeoPoint? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val maneuvers: List<NavManeuver> = emptyList(),
    val currentManeuverIndex: Int = 0,
    val nextTurnDirection: TurnDirection = TurnDirection.STRAIGHT,
    val distanceToNextTurn: String = "400 m",
    val nextRoadName: String = "Main Road",
    val remainingDistanceKm: Float = 0.0f,
    val estimatedMinutesRemaining: Int = 0,
    val estimatedArrivalTime: String = "--:--",
    val isOverviewMode: Boolean = false,
    val searchQuery: String = "",
    val speedUnit: String = "km/h",
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

    private val settingsRepo = SettingsRepository(application)
    private var lastKnownLocation: Location? = null

    // Off-route consecutive seconds counter
    private var offRouteSeconds = 0
    private var isRerouting = false

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun setSpeedUnit(unit: String) {
        _uiState.update { it.copy(speedUnit = unit) }
    }

    /**
     * Saves the current rider location as Home or Work in DataStore.
     */
    fun saveCurrentLocationAs(type: String) {
        val loc = lastKnownLocation ?: return
        viewModelScope.launch {
            val label = if (type.equals("home", true)) "Home (Saved Residence)" else "Work (Saved Office)"
            settingsRepo.saveLocation(type, loc.latitude, loc.longitude, label)
        }
    }

    /**
     * Fully in-app real navigation launch: resolves destination via Nominatim/Geocoder,
     * fetches real road geometry via OSRM, and begins embedded navigation immediately.
     */
    fun startNavigation(destination: String, origin: Location? = null) {
        val app = getApplication<Application>()
        val startLoc = origin ?: lastKnownLocation
        if (startLoc == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "Waiting for valid GPS location before routing..."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isNavigating = true,
                isRouteLoading = true,
                isArrived = false,
                errorMessage = null,
                destinationName = destination
            )
        }

        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            val savedHome = if (settings.homeLat != null && settings.homeLon != null) {
                Pair(GeoPoint(settings.homeLat, settings.homeLon), settings.homeName.ifBlank { "Home" })
            } else null

            val savedWork = if (settings.workLat != null && settings.workLon != null) {
                Pair(GeoPoint(settings.workLat, settings.workLon), settings.workName.ifBlank { "Work" })
            } else null

            val resolveResult = InAppRouteManager.resolveDestination(
                context = app,
                query = destination,
                origin = startLoc,
                savedHome = savedHome,
                savedWork = savedWork
            )

            if (resolveResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isNavigating = false,
                        isRouteLoading = false,
                        errorMessage = resolveResult.exceptionOrNull()?.message ?: "Location not found"
                    )
                }
                return@launch
            }

            val (destPoint, resolvedTitle) = resolveResult.getOrThrow()

            val routeResult = InAppRouteManager.buildRoute(
                origin = startLoc,
                destination = destPoint,
                destinationName = resolvedTitle
            )

            if (routeResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isNavigating = false,
                        isRouteLoading = false,
                        errorMessage = routeResult.exceptionOrNull()?.message ?: "Failed to generate route"
                    )
                }
                return@launch
            }

            val route = routeResult.getOrThrow()
            val firstManeuver = route.maneuvers.firstOrNull()

            _uiState.update {
                it.copy(
                    isNavigating = true,
                    isRouteLoading = false,
                    isArrived = false,
                    errorMessage = null,
                    destinationName = route.destinationName,
                    destinationPoint = route.destinationPoint,
                    routePoints = route.waypoints,
                    maneuvers = route.maneuvers,
                    currentManeuverIndex = 0,
                    nextTurnDirection = firstManeuver?.turnDirection ?: TurnDirection.STRAIGHT,
                    distanceToNextTurn = Units.formatGuidanceDistance(firstManeuver?.distanceMeters ?: 400f, it.speedUnit),
                    nextRoadName = firstManeuver?.roadName ?: "Main Road",
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
                isArrived = false,
                errorMessage = null,
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
     * In-app location engine: snaps to route, updates remaining distance along road polyline,
     * advances turn maneuvers, checks off-route conditions, and triggers auto-reroute.
     */
    fun updateLocationForNavigation(location: Location) {
        lastKnownLocation = location
        val state = _uiState.value
        if (!state.isNavigating || state.routePoints.isEmpty() || state.isRouteLoading || isRerouting) return

        val destPoint = state.destinationPoint ?: return

        // 1. Arrival Detection (< 30 meters from destination)
        val directDistanceToDest = InAppRouteManager.calculateHaversine(
            location.latitude, location.longitude,
            destPoint.latitude, destPoint.longitude
        )
        if (directDistanceToDest < 30.0) {
            _uiState.update {
                it.copy(
                    isArrived = true,
                    remainingDistanceKm = 0f,
                    estimatedMinutesRemaining = 0,
                    distanceToNextTurn = "0 m",
                    nextRoadName = "Destination Reached",
                    nextTurnDirection = TurnDirection.DESTINATION
                )
            }
            return
        }

        // 2. Snap to Route & Off-route Detection
        val snap = InAppRouteManager.snapToRoute(location, state.routePoints)
        if (snap.distanceToRouteMeters > 50f) {
            offRouteSeconds++
            // If rider is > 50m off route for > 5 consecutive seconds, trigger auto-reroute
            if (offRouteSeconds >= 5) {
                offRouteSeconds = 0
                isRerouting = true
                viewModelScope.launch {
                    try {
                        startNavigation(state.destinationName, location)
                    } finally {
                        isRerouting = false
                    }
                }
                return
            }
        } else {
            offRouteSeconds = 0
        }

        // 3. Exact remaining distance along road polyline
        val remainingKm = InAppRouteManager.calculateRemainingDistanceAlongRoute(location, state.routePoints)

        // 4. Advance maneuvers
        val maneuvers = state.maneuvers
        var currentIndex = state.currentManeuverIndex

        if (currentIndex < maneuvers.size) {
            val targetManeuver = maneuvers[currentIndex]
            val distToStep = InAppRouteManager.calculateHaversine(
                location.latitude, location.longitude,
                targetManeuver.point.latitude, targetManeuver.point.longitude
            ).toFloat()

            // Advance step if within 35m of maneuver waypoint
            if (distToStep < 35f && currentIndex < maneuvers.size - 1) {
                currentIndex++
            }

            val currentManeuver = maneuvers[currentIndex]
            val activeDistToStep = if (currentIndex == state.currentManeuverIndex) distToStep else currentManeuver.distanceMeters

            // Recalculate ETA based on actual remaining distance
            val riderSpeedKmh = if (location.hasSpeed() && location.speed > 2f) location.speed * 3.6f else 40f
            val minutesLeft = max(1, ((remainingKm / riderSpeedKmh) * 60f).roundToInt())

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, minutesLeft)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val arrivalTime = timeFormat.format(calendar.time)

            _uiState.update {
                it.copy(
                    remainingDistanceKm = remainingKm,
                    estimatedMinutesRemaining = minutesLeft,
                    estimatedArrivalTime = arrivalTime,
                    currentManeuverIndex = currentIndex,
                    nextTurnDirection = currentManeuver.turnDirection,
                    distanceToNextTurn = Units.formatGuidanceDistance(activeDistToStep, it.speedUnit),
                    nextRoadName = currentManeuver.roadName
                )
            }
        }
    }
}
