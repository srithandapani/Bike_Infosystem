package com.motodrive.tft.navigation

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

object NominatimClient {

    private const val NOMINATIM_BASE = "https://nominatim.openstreetmap.org"

    /**
     * Resolves a destination query using Nominatim OSM Search API with native Android Geocoder fallback.
     * Returns failure if location cannot be resolved (no fake coordinates).
     */
    suspend fun searchLocation(
        context: Context,
        query: String,
        proximityLat: Double? = null,
        proximityLon: Double? = null
    ): Result<Pair<GeoPoint, String>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Search query cannot be blank"))
        }

        // 1. Try Nominatim Search API
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = if (proximityLat != null && proximityLon != null) {
                // Biased search near current location (+/- 0.5 deg bbox)
                val left = proximityLon - 0.5
                val right = proximityLon + 0.5
                val top = proximityLat + 0.5
                val bottom = proximityLat - 0.5
                "$NOMINATIM_BASE/search?format=json&q=$encoded&viewbox=$left,$top,$right,$bottom&bounded=0&limit=1"
            } else {
                "$NOMINATIM_BASE/search?format=json&q=$encoded&limit=1"
            }

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "BikeInfosystem/2.0 (Motorcycle Infotainment System; contact=developer)")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val item = jsonArray.getJSONObject(0)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    val displayName = item.getString("display_name")
                    val shortName = displayName.split(",").firstOrNull()?.trim() ?: trimmed
                    return@withContext Result.success(Pair(GeoPoint(lat, lon), shortName))
                }
            }
        } catch (e: Exception) {
            // Fallback to Android Geocoder
        }

        // 2. Fallback to Android OS native Geocoder
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(trimmed, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val point = GeoPoint(addr.latitude, addr.longitude)
                    val title = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: trimmed
                    return@withContext Result.success(Pair(point, title))
                }
            }
        } catch (e: Exception) {
            // Geocoder unavailable
        }

        Result.failure(NoSuchElementException("Could not locate '$trimmed'. Please verify spelling or internet connection."))
    }

    /**
     * Finds nearest POI (e.g. Fuel, Motorcycle Garage) relative to current location.
     */
    suspend fun searchNearbyPoi(
        poiType: String,
        currentLat: Double,
        currentLon: Double
    ): Result<Pair<GeoPoint, String>> = withContext(Dispatchers.IO) {
        val queryTerm = when (poiType.lowercase(Locale.ROOT)) {
            "fuel", "gas", "fuel station" -> "fuel"
            "service", "motorcycle service", "garage" -> "motorcycle repair"
            else -> poiType
        }

        try {
            val encoded = URLEncoder.encode(queryTerm, "UTF-8")
            val left = currentLon - 0.25
            val right = currentLon + 0.25
            val top = currentLat + 0.25
            val bottom = currentLat - 0.25
            val urlString = "$NOMINATIM_BASE/search?format=json&q=$encoded&viewbox=$left,$top,$right,$bottom&bounded=1&limit=1"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "BikeInfosystem/2.0 (Motorcycle Infotainment System; contact=developer)")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val item = jsonArray.getJSONObject(0)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    val displayName = item.getString("display_name")
                    val shortName = displayName.split(",").take(2).joinToString(", ").trim()
                    return@withContext Result.success(Pair(GeoPoint(lat, lon), shortName))
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        Result.failure(NoSuchElementException("No nearby $poiType found within range."))
    }
}
