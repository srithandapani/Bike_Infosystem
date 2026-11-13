package com.motodrive.tft.navigation

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class PlaceSearchResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val latLng: LatLng? = null
)

object GooglePlacesClient {

    private fun getClient(context: Context): PlacesClient? {
        return if (Places.isInitialized()) Places.createClient(context) else null
    }

    /**
     * Searches for autocomplete destination predictions near the motorcycle's current location.
     */
    suspend fun searchDestinations(
        context: Context,
        query: String,
        currentLocation: Location?
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val client = getClient(context) ?: return@withContext emptyList()
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        val builder = FindAutocompletePredictionsRequest.builder()
            .setQuery(q)

        // Bias predictions towards rider's current region (~50km bounds)
        if (currentLocation != null) {
            val delta = 0.45 // ~50 km
            builder.setLocationBias(
                RectangularBounds.newInstance(
                    LatLng(currentLocation.latitude - delta, currentLocation.longitude - delta),
                    LatLng(currentLocation.latitude + delta, currentLocation.longitude + delta)
                )
            )
        }

        suspendCancellableCoroutine { continuation ->
            client.findAutocompletePredictions(builder.build())
                .addOnSuccessListener { response ->
                    val results = response.autocompletePredictions.map { p: AutocompletePrediction ->
                        PlaceSearchResult(
                            placeId = p.placeId,
                            primaryText = p.getPrimaryText(null).toString(),
                            secondaryText = p.getSecondaryText(null).toString()
                        )
                    }
                    continuation.resume(results)
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }
    }

    /**
     * Resolves exact coordinates and details for a selected Place ID.
     */
    suspend fun fetchPlaceCoordinates(
        context: Context,
        placeId: String
    ): LatLng? = withContext(Dispatchers.IO) {
        val client = getClient(context) ?: return@withContext null
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        suspendCancellableCoroutine { continuation ->
            client.fetchPlace(request)
                .addOnSuccessListener { response ->
                    continuation.resume(response.place.latLng)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    /**
     * Quick shortcuts for Fuel Station & Motorcycle Service near the bike.
     */
    suspend fun findQuickAmenity(
        context: Context,
        type: String,
        currentLocation: Location?
    ): PlaceSearchResult? = withContext(Dispatchers.IO) {
        val query = when (type.lowercase(Locale.ROOT)) {
            "gas", "fuel", "fuel station" -> "Petrol Pump"
            "wrench", "service", "motorcycle service" -> "TVS Motorcycle Service"
            else -> type
        }
        val predictions = searchDestinations(context, query, currentLocation)
        val first = predictions.firstOrNull() ?: return@withContext null
        val latLng = fetchPlaceCoordinates(context, first.placeId)
        first.copy(latLng = latLng)
    }

    /**
     * Saved Places: Home & Work coordinates.
     */
    fun getSavedPlace(context: Context, key: String): LatLng? {
        val prefs = context.getSharedPreferences("nav_places", Context.MODE_PRIVATE)
        val latKey = "${key.lowercase(Locale.ROOT)}_lat"
        val lonKey = "${key.lowercase(Locale.ROOT)}_lon"
        return if (prefs.contains(latKey)) {
            val lat = java.lang.Double.longBitsToDouble(prefs.getLong(latKey, 0))
            val lon = java.lang.Double.longBitsToDouble(prefs.getLong(lonKey, 0))
            LatLng(lat, lon)
        } else null
    }

    fun savePlace(context: Context, key: String, latLng: LatLng, title: String = "") {
        context.getSharedPreferences("nav_places", Context.MODE_PRIVATE).edit()
            .putLong("${key.lowercase(Locale.ROOT)}_lat", java.lang.Double.doubleToRawLongBits(latLng.latitude))
            .putLong("${key.lowercase(Locale.ROOT)}_lon", java.lang.Double.doubleToRawLongBits(latLng.longitude))
            .putString("${key.lowercase(Locale.ROOT)}_title", title.ifBlank { key.replaceFirstChar { it.uppercase() } })
            .apply()
    }

    fun hasSavedPlace(context: Context, key: String): Boolean {
        return context.getSharedPreferences("nav_places", Context.MODE_PRIVATE)
            .contains("${key.lowercase(Locale.ROOT)}_lat")
    }
}

