package com.motodrive.tft.navigation

import android.content.Context
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.*

/** A route snapped to a real road network, with cumulative along-route distances. */
class ActiveRoute(
    val destinationName: String,
    val destinationPoint: GeoPoint,
    val path: RoutedPath
) {
    /** cumulative[i] = metres along the geometry from its start to point i. */
    val cumulative: DoubleArray = DoubleArray(path.geometry.size).also { c ->
        for (i in 1 until c.size) c[i] = c[i - 1] + haversine(path.geometry[i - 1], path.geometry[i])
    }
    val totalMeters: Double get() = cumulative.lastOrNull() ?: 0.0
}

/** Result of projecting a GPS fix onto the route. */
data class RouteProgress(
    val alongMeters: Double,
    val offRouteMeters: Double,
    val segmentIndex: Int
)

fun haversine(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371008.8
    val p1 = Math.toRadians(a.latitude)
    val p2 = Math.toRadians(b.latitude)
    val dp = p2 - p1
    val dl = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(h)))
}

object InAppRouteManager {

    /**
     * Resolves a destination to real coordinates. Returns null (never a made-up location)
     * when it cannot be resolved.
     */
    suspend fun resolveDestination(
        context: Context,
        query: String,
        origin: Location?
    ): GeocodeResult? = withContext(Dispatchers.IO) {
        val near = origin?.let { GeoPoint(it.latitude, it.longitude) }
        val q = query.trim()
        val key = q.lowercase(Locale.ROOT)

        // Saved places
        val prefs = context.getSharedPreferences("nav_places", Context.MODE_PRIVATE)
        fun saved(name: String, title: String): GeocodeResult? =
            if (prefs.contains("${name}_lat"))
                GeocodeResult(
                    GeoPoint(
                        java.lang.Double.longBitsToDouble(prefs.getLong("${name}_lat", 0)),
                        java.lang.Double.longBitsToDouble(prefs.getLong("${name}_lon", 0))
                    ), title
                ) else null

        when (key) {
            "home", "saved home" -> return@withContext saved("home", "Home")
            "work", "office", "office campus" -> return@withContext saved("work", "Work")
        }

        // Real nearby-amenity search (nothing hard-coded)
        if (near != null) {
            when (key) {
                "fuel station", "nearest gas & diesel", "gas station", "petrol bunk" ->
                    return@withContext OsrmClient.nearestAmenity("[amenity=fuel]", near)
                "motorcycle service", "authorized garage", "service" ->
                    return@withContext OsrmClient.nearestAmenity("[shop=motorcycle]", near)
                        ?: OsrmClient.nearestAmenity("[shop=motorcycle_repair]", near)
            }
        }

        // Device geocoder first, then Nominatim
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val a = Geocoder(context, Locale.getDefault()).getFromLocationName(q, 1)
                if (!a.isNullOrEmpty()) {
                    val x = a[0]
                    return@withContext GeocodeResult(
                        GeoPoint(x.latitude, x.longitude),
                        x.featureName ?: x.thoroughfare ?: x.locality ?: q
                    )
                }
            }
        } catch (_: Exception) { /* fall through */ }

        OsrmClient.geocode(q, near)
    }

    fun savePlace(context: Context, name: String, point: GeoPoint) {
        context.getSharedPreferences("nav_places", Context.MODE_PRIVATE).edit()
            .putLong("${name}_lat", java.lang.Double.doubleToRawLongBits(point.latitude))
            .putLong("${name}_lon", java.lang.Double.doubleToRawLongBits(point.longitude))
            .apply()
    }

    suspend fun buildRoute(origin: GeoPoint, dest: GeocodeResult): ActiveRoute? {
        val path = OsrmClient.route(origin, dest.point) ?: return null
        if (path.geometry.size < 2) return null
        return ActiveRoute(dest.title, dest.point, path)
    }

    /**
     * Projects [p] onto the route polyline. Only searches a window around [hintSegment]
     * so a route that doubles back on itself cannot snap the rider to the wrong leg.
     */
    fun project(route: ActiveRoute, p: GeoPoint, hintSegment: Int, window: Int = 60): RouteProgress {
        val g = route.path.geometry
        val lo = max(0, hintSegment - 5)
        val hi = min(g.size - 2, hintSegment + window)
        var best = Double.MAX_VALUE
        var bestIdx = lo
        var bestAlong = route.cumulative[lo]

        // Local equirectangular projection around p (accurate to cm at segment scale)
        val cosLat = cos(Math.toRadians(p.latitude))
        fun x(q: GeoPoint) = Math.toRadians(q.longitude - p.longitude) * cosLat * 6371008.8
        fun y(q: GeoPoint) = Math.toRadians(q.latitude - p.latitude) * 6371008.8

        for (i in lo..hi) {
            val ax = x(g[i]); val ay = y(g[i])
            val bx = x(g[i + 1]); val by = y(g[i + 1])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy).div(len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = hypot(px, py)
            if (d < best) {
                best = d
                bestIdx = i
                bestAlong = route.cumulative[i] + t * (route.cumulative[i + 1] - route.cumulative[i])
            }
        }
        return RouteProgress(bestAlong, best, bestIdx)
    }
}
