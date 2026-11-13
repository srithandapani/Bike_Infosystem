package com.motodrive.tft.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** One routed step (maneuver) with the road geometry index where it happens. */
data class RouteStep(
    val instruction: String,
    val roadName: String,
    val turn: TurnDirection,
    /** Length of the road travelled in this step, metres. */
    val distanceMeters: Float,
    val durationSeconds: Float,
    /** Where the maneuver happens (start of the next step's road). */
    val point: GeoPoint,
    /** Index into [RoutedPath.geometry] of the maneuver point. */
    val geometryIndex: Int
)

data class RoutedPath(
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>
)

data class GeocodeResult(val point: GeoPoint, val title: String)

/**
 * Real road routing + geocoding over OpenStreetMap data.
 *
 * Defaults to the public OSRM demo server and Nominatim, which are fine for development but have no
 * SLA and usage limits. For production point [routingBaseUrl] at a self-hosted OSRM / GraphHopper /
 * OpenRouteService instance.
 */
object OsrmClient {
    var routingBaseUrl = "https://router.project-osrm.org"
    var nominatimBaseUrl = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "MotoDriveTFT/1.0 (motorcycle infotainment)"

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "GET"
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    /** Free-text geocoding, biased towards [near] (never returns invented coordinates). */
    suspend fun geocode(query: String, near: GeoPoint?): GeocodeResult? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val bias = near?.let {
                val d = 0.5 // ~50 km box: prefer nearby matches, not bounded
                "&viewbox=${it.longitude - d},${it.latitude + d},${it.longitude + d},${it.latitude - d}"
            } ?: ""
            val body = get("$nominatimBaseUrl/search?format=jsonv2&limit=1&q=$q$bias") ?: return@withContext null
            val arr = org.json.JSONArray(body)
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            GeocodeResult(
                GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble()),
                o.optString("name").ifBlank { o.optString("display_name").substringBefore(",") }
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Nearest place of an OSM amenity (fuel, motorcycle repair...) via Overpass, within [radiusM]. */
    suspend fun nearestAmenity(filter: String, near: GeoPoint, radiusM: Int = 10000): GeocodeResult? =
        withContext(Dispatchers.IO) {
            try {
                val ql = "[out:json][timeout:10];nwr$filter(around:$radiusM,${near.latitude},${near.longitude});out center 30;"
                val body = get("https://overpass-api.de/api/interpreter?data=${URLEncoder.encode(ql, "UTF-8")}")
                    ?: return@withContext null
                val els = JSONObject(body).getJSONArray("elements")
                var best: GeocodeResult? = null
                var bestD = Double.MAX_VALUE
                for (i in 0 until els.length()) {
                    val e = els.getJSONObject(i)
                    val lat = if (e.has("lat")) e.getDouble("lat") else e.optJSONObject("center")?.getDouble("lat") ?: continue
                    val lon = if (e.has("lon")) e.getDouble("lon") else e.optJSONObject("center")?.getDouble("lon") ?: continue
                    val d = GeoPoint(lat, lon).distanceToAsDouble(near)
                    if (d < bestD) {
                        bestD = d
                        val tags = e.optJSONObject("tags")
                        best = GeocodeResult(GeoPoint(lat, lon), tags?.optString("name").orEmpty().ifBlank {
                            tags?.optString("brand").orEmpty().ifBlank { "Nearest match" }
                        })
                    }
                }
                best
            } catch (_: Exception) {
                null
            }
        }

    /** Real road route from [from] to [to]; null when offline / no route. */
    suspend fun route(from: GeoPoint, to: GeoPoint): RoutedPath? = withContext(Dispatchers.IO) {
        try {
            val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
            val body = get("$routingBaseUrl/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true&annotations=false")
                ?: return@withContext null
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") return@withContext null
            val r = json.getJSONArray("routes").getJSONObject(0)

            val gj = r.getJSONObject("geometry").getJSONArray("coordinates")
            val geometry = ArrayList<GeoPoint>(gj.length())
            for (i in 0 until gj.length()) {
                val p = gj.getJSONArray(i)
                geometry.add(GeoPoint(p.getDouble(1), p.getDouble(0)))
            }

            val steps = ArrayList<RouteStep>()
            val legs = r.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val st = legs.getJSONObject(l).getJSONArray("steps")
                for (i in 0 until st.length()) {
                    val s = st.getJSONObject(i)
                    val m = s.getJSONObject("maneuver")
                    val loc = m.getJSONArray("location")
                    val pt = GeoPoint(loc.getDouble(1), loc.getDouble(0))
                    val road = s.optString("name").ifBlank { "the road" }
                    val type = m.optString("type")
                    val modifier = m.optString("modifier")
                    val turn = toTurn(type, modifier)
                    steps.add(
                        RouteStep(
                            instruction = describe(type, modifier, road),
                            roadName = road,
                            turn = turn,
                            distanceMeters = s.getDouble("distance").toFloat(),
                            durationSeconds = s.getDouble("duration").toFloat(),
                            point = pt,
                            geometryIndex = nearestIndex(geometry, pt)
                        )
                    )
                }
            }
            RoutedPath(geometry, r.getDouble("distance"), r.getDouble("duration"), steps)
        } catch (_: Exception) {
            null
        }
    }

    private fun nearestIndex(g: List<GeoPoint>, p: GeoPoint): Int {
        var bi = 0
        var bd = Double.MAX_VALUE
        for (i in g.indices) {
            val d = g[i].distanceToAsDouble(p)
            if (d < bd) { bd = d; bi = i }
        }
        return bi
    }

    private fun toTurn(type: String, modifier: String): TurnDirection = when {
        type == "arrive" -> TurnDirection.DESTINATION
        type == "roundabout" || type == "rotary" || type == "exit roundabout" -> TurnDirection.ROUNDABOUT
        modifier == "uturn" -> TurnDirection.U_TURN
        modifier == "sharp left" -> TurnDirection.SHARP_LEFT
        modifier == "left" -> TurnDirection.TURN_LEFT
        modifier == "slight left" -> TurnDirection.SLIGHT_LEFT
        modifier == "sharp right" -> TurnDirection.SHARP_RIGHT
        modifier == "right" -> TurnDirection.TURN_RIGHT
        modifier == "slight right" -> TurnDirection.SLIGHT_RIGHT
        else -> TurnDirection.STRAIGHT
    }

    private fun describe(type: String, modifier: String, road: String): String = when (type) {
        "depart" -> "Head out on $road"
        "arrive" -> "Arrive at your destination"
        "roundabout", "rotary" -> "Enter the roundabout towards $road"
        "merge" -> "Merge onto $road"
        "on ramp", "off ramp" -> "Take the ramp onto $road"
        "fork" -> "Keep ${modifier.ifBlank { "straight" }} onto $road"
        else -> if (modifier == "straight" || modifier.isBlank()) "Continue on $road"
        else "Turn $modifier onto $road"
    }
}
