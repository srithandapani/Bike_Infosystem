package com.motodrive.tft.navigation

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class NavManeuver(
    val instruction: String,
    val roadName: String,
    val turnDirection: TurnDirection,
    val distanceMeters: Float,
    val durationSeconds: Float = 0f,
    val point: GeoPoint
)

data class InAppRoute(
    val destinationName: String,
    val destinationPoint: GeoPoint,
    val totalDistanceKm: Float,
    val estimatedMinutes: Int,
    val arrivalTime: String,
    val waypoints: List<GeoPoint>,
    val maneuvers: List<NavManeuver>
)

data class RouteSnapResult(
    val segmentIndex: Int,
    val snappedPoint: GeoPoint,
    val distanceToRouteMeters: Float
)

object InAppRouteManager {

    /**
     * Resolves destination coordinates using real Nominatim and local Geocoder.
     * Saved Home and Work are strictly resolved from saved preferences; POIs from proximity search.
     * Returns failure if destination cannot be found.
     */
    suspend fun resolveDestination(
        context: Context,
        query: String,
        origin: Location?,
        savedHome: Pair<GeoPoint, String>? = null,
        savedWork: Pair<GeoPoint, String>? = null
    ): Result<Pair<GeoPoint, String>> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase(Locale.ROOT)

        // 1. Saved Home check
        if (q == "home" || q == "saved home") {
            return@withContext savedHome?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Home location not set. Set your Home address in Settings or save current location."))
        }

        // 2. Saved Work check
        if (q == "work" || q == "office" || q == "saved work") {
            return@withContext savedWork?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Work location not set. Set your Work address in Settings or save current location."))
        }

        // 3. Nearby Fuel / Gas POI search
        if (q == "fuel station" || q == "gas station" || q == "petrol bunk" || q == "fuel") {
            if (origin != null) {
                val poiRes = NominatimClient.searchNearbyPoi("fuel", origin.latitude, origin.longitude)
                if (poiRes.isSuccess) return@withContext poiRes
            }
        }

        // 4. Nearby Motorcycle Service / Garage POI search
        if (q == "motorcycle service" || q == "authorized garage" || q == "service" || q == "garage") {
            if (origin != null) {
                val poiRes = NominatimClient.searchNearbyPoi("service", origin.latitude, origin.longitude)
                if (poiRes.isSuccess) return@withContext poiRes
            }
        }

        // 5. Real geocoding via Nominatim with Android Geocoder fallback
        NominatimClient.searchLocation(
            context = context,
            query = query,
            proximityLat = origin?.latitude,
            proximityLon = origin?.longitude
        )
    }

    /**
     * Builds real road routes via OSRM. If network is offline, generates direct line route
     * without inventing false turns or fabricated street names.
     */
    suspend fun buildRoute(
        origin: Location,
        destination: GeoPoint,
        destinationName: String
    ): Result<InAppRoute> = withContext(Dispatchers.IO) {
        val startPoint = GeoPoint(origin.latitude, origin.longitude)

        // Try real road routing via OSRM
        val osrmResult = OsrmClient.fetchRoute(startPoint, destination, destinationName)
        if (osrmResult.isSuccess) {
            val res = osrmResult.getOrThrow()
            val totalDistKm = round((res.totalDistanceMeters / 1000f) * 10f) / 10f
            val totalMin = max(1, (res.totalDurationSeconds / 60f).roundToInt())

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, totalMin)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val arrivalTime = timeFormat.format(calendar.time)

            val maneuvers = res.steps.map { step ->
                NavManeuver(
                    instruction = step.instruction,
                    roadName = step.roadName,
                    turnDirection = step.turnDirection,
                    distanceMeters = step.distanceMeters,
                    durationSeconds = step.durationSeconds,
                    point = step.point
                )
            }

            return@withContext Result.success(
                InAppRoute(
                    destinationName = destinationName,
                    destinationPoint = destination,
                    totalDistanceKm = totalDistKm,
                    estimatedMinutes = totalMin,
                    arrivalTime = arrivalTime,
                    waypoints = res.polylinePoints.ifEmpty { listOf(startPoint, destination) },
                    maneuvers = maneuvers
                )
            )
        }

        // Fallback: direct routing between actual coordinates (when offline)
        val directMeters = calculateHaversine(
            startPoint.latitude, startPoint.longitude,
            destination.latitude, destination.longitude
        )
        val distKm = round((directMeters / 1000.0) * 10.0).toFloat() / 10f
        // Estimate at 40 km/h average motorcycle speed
        val estMinutes = max(1, ((distKm / 40.0) * 60.0).roundToInt())

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, estMinutes)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val arrivalTime = timeFormat.format(calendar.time)

        val fallbackManeuvers = listOf(
            NavManeuver(
                instruction = "Proceed towards $destinationName",
                roadName = destinationName,
                turnDirection = TurnDirection.STRAIGHT,
                distanceMeters = directMeters.toFloat(),
                point = startPoint
            ),
            NavManeuver(
                instruction = "Arrive at $destinationName",
                roadName = destinationName,
                turnDirection = TurnDirection.DESTINATION,
                distanceMeters = 0f,
                point = destination
            )
        )

        Result.success(
            InAppRoute(
                destinationName = destinationName,
                destinationPoint = destination,
                totalDistanceKm = distKm,
                estimatedMinutes = estMinutes,
                arrivalTime = arrivalTime,
                waypoints = listOf(startPoint, destination),
                maneuvers = fallbackManeuvers
            )
        )
    }

    /**
     * Projects a point P onto segment AB, returning the snapped point and perpendicular distance in meters.
     */
    fun projectOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<GeoPoint, Float> {
        val latA = Math.toRadians(a.latitude)
        val lonA = Math.toRadians(a.longitude)
        val latB = Math.toRadians(b.latitude)
        val lonB = Math.toRadians(b.longitude)
        val latP = Math.toRadians(p.latitude)
        val lonP = Math.toRadians(p.longitude)

        // Equirectangular approximation for local projection
        val xA = (lonA - lonA) * cos((latA + latB) / 2.0)
        val yA = latA - latA
        val xB = (lonB - lonA) * cos((latA + latB) / 2.0)
        val yB = latB - latA
        val xP = (lonP - lonA) * cos((latA + latB) / 2.0)
        val yP = latP - latA

        val dx = xB - xA
        val dy = yB - yA
        val lenSq = dx * dx + dy * dy

        val t = if (lenSq > 0) {
            (((xP - xA) * dx + (yP - yA) * dy) / lenSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projLat = a.latitude + t * (b.latitude - a.latitude)
        val projLon = a.longitude + t * (b.longitude - a.longitude)
        val snapped = GeoPoint(projLat, projLon)

        val distMeters = calculateHaversine(p.latitude, p.longitude, projLat, projLon).toFloat()
        return Pair(snapped, distMeters)
    }

    /**
     * Finds the nearest route segment on the polyline and snaps the rider's position to it.
     */
    fun snapToRoute(currentLocation: Location, polyline: List<GeoPoint>): RouteSnapResult {
        if (polyline.size < 2) {
            val pt = polyline.firstOrNull() ?: GeoPoint(currentLocation.latitude, currentLocation.longitude)
            val d = calculateHaversine(currentLocation.latitude, currentLocation.longitude, pt.latitude, pt.longitude).toFloat()
            return RouteSnapResult(0, pt, d)
        }

        val curPt = GeoPoint(currentLocation.latitude, currentLocation.longitude)
        var minDistance = Float.MAX_VALUE
        var bestIndex = 0
        var bestSnapped = polyline[0]

        for (i in 0 until polyline.size - 1) {
            val (snapped, dist) = projectOnSegment(curPt, polyline[i], polyline[i + 1])
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
                bestSnapped = snapped
            }
        }

        return RouteSnapResult(bestIndex, bestSnapped, minDistance)
    }

    /**
     * Calculates the exact remaining distance along the road polyline from the snapped rider position to the destination.
     */
    fun calculateRemainingDistanceAlongRoute(
        currentLocation: Location,
        polyline: List<GeoPoint>
    ): Float {
        if (polyline.size < 2) return 0f

        val snap = snapToRoute(currentLocation, polyline)
        var remainingMeters = calculateHaversine(
            snap.snappedPoint.latitude, snap.snappedPoint.longitude,
            polyline[snap.segmentIndex + 1].latitude, polyline[snap.segmentIndex + 1].longitude
        )

        for (i in snap.segmentIndex + 1 until polyline.size - 1) {
            remainingMeters += calculateHaversine(
                polyline[i].latitude, polyline[i].longitude,
                polyline[i + 1].latitude, polyline[i + 1].longitude
            )
        }

        return (remainingMeters / 1000.0).toFloat()
    }

    fun calculateHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2.0).pow(2.0) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
