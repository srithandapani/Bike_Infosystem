package com.motodrive.tft.navigation

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.round
import kotlin.math.max
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

data class NavManeuver(
    val instruction: String,
    val roadName: String,
    val turnDirection: TurnDirection,
    val distanceMeters: Float,
    val point: GeoPoint
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
    val distanceToNextTurn: String = "--",
    val nextRoadName: String = "",
    val remainingDistanceKm: Float = 0.0f,
    val estimatedMinutesRemaining: Int = 0,
    val estimatedArrivalTime: String = "--:--",
    val isOverviewMode: Boolean = false,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val isRerouting: Boolean = false,
    val quickDestinations: List<QuickDestination> = listOf(
        QuickDestination("Fuel Station", "Nearest Gas & Diesel", "Fuel Station", "GAS"),
        QuickDestination("Motorcycle Service", "TVS Ronin Authorized", "Motorcycle Service", "WRENCH"),
        QuickDestination("Home", "Saved Residence", "Home", "HOME"),
        QuickDestination("Work", "Office Campus", "Work", "WORK")
    )
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val OFF_ROUTE_MIN_M = 40.0
        private const val OFF_ROUTE_FIXES = 3
        private const val REROUTE_COOLDOWN_MS = 15_000L
        private const val ARRIVAL_M = 25.0
    }

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var lastKnownLocation: Location? = null
    private var route: ActiveRoute? = null
    private var segmentHint = 0
    private var offRouteCount = 0
    private var lastRerouteAt = 0L
    private var rerouteJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveCurrentLocationAs(name: String) {
        val loc = lastKnownLocation ?: return
        InAppRouteManager.savePlace(getApplication(), name, GeoPoint(loc.latitude, loc.longitude))
    }

    /** Resolves the destination, fetches a real road route and starts guidance. */
    fun startNavigation(destination: String, origin: Location? = null) {
        val startLoc = origin ?: lastKnownLocation
        if (startLoc == null) {
            _uiState.update { it.copy(errorMessage = "Waiting for GPS fix - cannot start navigation yet") }
            return
        }

        _uiState.update { it.copy(isNavigating = true, isRouteLoading = true, destinationName = destination) }

        viewModelScope.launch {
            val dest = InAppRouteManager.resolveDestination(getApplication(), destination, startLoc)
            if (dest == null) {
                failNavigation(
                    if (destination.trim().lowercase() in listOf("home", "saved home", "work", "office", "office campus"))
                        "$destination is not saved yet"
                    else "Could not find \"$destination\" (check connection or spelling)"
                )
                return@launch
            }
            val r = InAppRouteManager.buildRoute(GeoPoint(startLoc.latitude, startLoc.longitude), dest)
            if (r == null) {
                failNavigation("No road route found to ${dest.title} (offline or unreachable)")
                return@launch
            }
            installRoute(r)
        }
    }

    private fun failNavigation(msg: String) {
        route = null
        _uiState.update {
            it.copy(
                isNavigating = false, isRouteLoading = false, isRerouting = false,
                destinationName = "", destinationPoint = null, routePoints = emptyList(),
                maneuvers = emptyList(), errorMessage = msg
            )
        }
    }

    private fun installRoute(r: ActiveRoute) {
        route = r
        segmentHint = 0
        offRouteCount = 0
        val maneuvers = r.path.steps.map {
            NavManeuver(it.instruction, it.roadName, it.turn, it.distanceMeters, it.point)
        }
        _uiState.update {
            it.copy(
                isNavigating = true, isRouteLoading = false, isRerouting = false,
                destinationName = r.destinationName,
                destinationPoint = r.destinationPoint,
                routePoints = r.path.geometry,
                maneuvers = maneuvers,
                currentManeuverIndex = 0,
                isOverviewMode = false
            )
        }
        lastKnownLocation?.let { updateLocationForNavigation(it) }
    }

    fun cancelNavigation() {
        rerouteJob?.cancel()
        route = null
        _uiState.update {
            it.copy(
                isNavigating = false, isRouteLoading = false, isRerouting = false,
                destinationName = "", destinationPoint = null, routePoints = emptyList(),
                maneuvers = emptyList(), currentManeuverIndex = 0
            )
        }
    }

    fun toggleOverviewMode() {
        _uiState.update { it.copy(isOverviewMode = !it.isOverviewMode) }
    }

    /**
     * Snaps the GPS fix to the route, then derives remaining distance, ETA and the next
     * maneuver from progress ALONG the route (not straight-line distance).
     */
    fun updateLocationForNavigation(location: Location) {
        lastKnownLocation = location
        val r = route ?: return
        if (!_uiState.value.isNavigating || _uiState.value.isRouteLoading) return

        val here = GeoPoint(location.latitude, location.longitude)
        val prog = InAppRouteManager.project(r, here, segmentHint)

        // Off-route detection with hysteresis scaled by GPS accuracy
        val tolerance = max(OFF_ROUTE_MIN_M, if (location.hasAccuracy()) location.accuracy * 2.0 else 0.0)
        if (prog.offRouteMeters > tolerance) {
            offRouteCount++
            if (offRouteCount >= OFF_ROUTE_FIXES) maybeReroute(location)
        } else {
            offRouteCount = 0
            segmentHint = prog.segmentIndex
        }

        val remainingM = max(0.0, r.totalMeters - prog.alongMeters)

        // Arrived?
        val destDist = haversine(here, r.destinationPoint)
        if (remainingM <= ARRIVAL_M || destDist <= ARRIVAL_M) {
            _uiState.update {
                it.copy(
                    remainingDistanceKm = 0f, estimatedMinutesRemaining = 0,
                    nextTurnDirection = TurnDirection.DESTINATION,
                    distanceToNextTurn = "0 m", nextRoadName = r.destinationName,
                    estimatedArrivalTime = timeFormat.format(Calendar.getInstance().time)
                )
            }
            return
        }

        // Next maneuver = first step (after "depart") whose maneuver point is still ahead
        val steps = r.path.steps
        var next = 1
        while (next < steps.size - 1 && r.cumulative[steps[next].geometryIndex] <= prog.alongMeters + 10.0) next++
        next = next.coerceAtMost(steps.size - 1)
        val step = steps[next]
        val distToTurn = max(0.0, r.cumulative[step.geometryIndex] - prog.alongMeters)

        // Time left: routing-engine duration scaled by remaining fraction of the route
        val frac = if (r.totalMeters > 0) remainingM / r.totalMeters else 0.0
        val secLeft = r.path.durationSeconds * frac
        val minutes = kotlin.math.ceil(secLeft / 60.0).toInt().coerceAtLeast(1)
        val cal = Calendar.getInstance().apply { add(Calendar.SECOND, secLeft.toInt()) }

        _uiState.update {
            it.copy(
                remainingDistanceKm = round((remainingM / 1000.0 * 10.0)).toFloat() / 10f,
                estimatedMinutesRemaining = minutes,
                estimatedArrivalTime = timeFormat.format(cal.time),
                currentManeuverIndex = next,
                nextTurnDirection = step.turn,
                distanceToNextTurn = formatDistance(distToTurn.toFloat()),
                nextRoadName = step.roadName
            )
        }
    }

    private fun maybeReroute(location: Location) {
        val r = route ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (rerouteJob?.isActive == true || now - lastRerouteAt < REROUTE_COOLDOWN_MS) return
        lastRerouteAt = now
        _uiState.update { it.copy(isRerouting = true) }
        rerouteJob = viewModelScope.launch {
            val nr = InAppRouteManager.buildRoute(
                GeoPoint(location.latitude, location.longitude),
                GeocodeResult(r.destinationPoint, r.destinationName)
            )
            if (nr != null) installRoute(nr) else _uiState.update { it.copy(isRerouting = false) }
        }
    }

    private fun formatDistance(meters: Float): String =
        if (meters >= 1000f) "${round(meters / 100f) / 10f} km"
        else "${(round(meters / 10f) * 10).toInt().coerceAtLeast(10)} m"
}
