package com.motodrive.tft.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class OsrmStep(
    val instruction: String,
    val roadName: String,
    val turnDirection: TurnDirection,
    val distanceMeters: Float,
    val durationSeconds: Float,
    val point: GeoPoint
)

data class OsrmRouteResult(
    val destinationName: String,
    val destinationPoint: GeoPoint,
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Float,
    val polylinePoints: List<GeoPoint>,
    val steps: List<OsrmStep>
)

object OsrmClient {

    /**
     * Base URL for the OSRM routing engine.
     * Defaults to the public OSRM demo server for development.
     * Can be pointed to a self-hosted or dedicated motorcycle routing server.
     */
    var routingBaseUrl: String = "https://router.project-osrm.org"

    suspend fun fetchRoute(
        start: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<OsrmRouteResult> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$routingBaseUrl/route/v1/driving/${start.longitude},${start.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "BikeInfosystem/2.0 (Motorcycle Cockpit)")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val code = json.optString("code", "")
                if (code != "Ok") {
                    return@withContext Result.failure(Exception("OSRM routing returned code: $code"))
                }

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) {
                    return@withContext Result.failure(Exception("No route found between coordinates"))
                }

                val primaryRoute = routes.getJSONObject(0)
                val totalDistance = primaryRoute.getDouble("distance").toFloat()
                val totalDuration = primaryRoute.getDouble("duration").toFloat()

                // Polyline coordinates from GeoJSON
                val geometry = primaryRoute.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val polyline = mutableListOf<GeoPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    polyline.add(GeoPoint(lat, lon))
                }

                // Legs and Steps
                val legs = primaryRoute.getJSONArray("legs")
                val steps = mutableListOf<OsrmStep>()
                if (legs.length() > 0) {
                    val leg = legs.getJSONObject(0)
                    val jsonSteps = leg.getJSONArray("steps")
                    for (i in 0 until jsonSteps.length()) {
                        val stepObj = jsonSteps.getJSONObject(i)
                        val stepDist = stepObj.getDouble("distance").toFloat()
                        val stepDuration = stepObj.getDouble("duration").toFloat()
                        val roadName = stepObj.optString("name", "").ifBlank { "Road" }

                        val maneuver = stepObj.getJSONObject("maneuver")
                        val manType = maneuver.optString("type", "")
                        val manModifier = maneuver.optString("modifier", "")
                        val manLoc = maneuver.getJSONArray("location")
                        val stepPt = GeoPoint(manLoc.getDouble(1), manLoc.getDouble(0))

                        val turnDir = mapManeuverToTurnDirection(manType, manModifier)
                        val instruction = buildInstruction(turnDir, manType, roadName)

                        steps.add(
                            OsrmStep(
                                instruction = instruction,
                                roadName = roadName,
                                turnDirection = turnDir,
                                distanceMeters = stepDist,
                                durationSeconds = stepDuration,
                                point = stepPt
                            )
                        )
                    }
                }

                Result.success(
                    OsrmRouteResult(
                        destinationName = destinationName,
                        destinationPoint = destination,
                        totalDistanceMeters = totalDistance,
                        totalDurationSeconds = totalDuration,
                        polylinePoints = polyline,
                        steps = steps
                    )
                )
            } else {
                Result.failure(Exception("OSRM server returned HTTP ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapManeuverToTurnDirection(type: String, modifier: String): TurnDirection {
        val t = type.lowercase(Locale.ROOT)
        val m = modifier.lowercase(Locale.ROOT)

        return when {
            t == "arrive" -> TurnDirection.DESTINATION
            t == "roundabout" || t == "rotary" -> TurnDirection.ROUNDABOUT
            m.contains("uturn") -> TurnDirection.U_TURN
            m == "sharp left" -> TurnDirection.SHARP_LEFT
            m == "left" -> TurnDirection.TURN_LEFT
            m == "slight left" -> TurnDirection.SLIGHT_LEFT
            m == "sharp right" -> TurnDirection.SHARP_RIGHT
            m == "right" -> TurnDirection.TURN_RIGHT
            m == "slight right" -> TurnDirection.SLIGHT_RIGHT
            else -> TurnDirection.STRAIGHT
        }
    }

    private fun buildInstruction(dir: TurnDirection, type: String, road: String): String {
        return when (dir) {
            TurnDirection.DESTINATION -> "Arrive at $road"
            TurnDirection.ROUNDABOUT -> "Enter roundabout onto $road"
            TurnDirection.U_TURN -> "Make a U-turn onto $road"
            TurnDirection.SHARP_LEFT -> "Sharp left onto $road"
            TurnDirection.TURN_LEFT -> "Turn left onto $road"
            TurnDirection.SLIGHT_LEFT -> "Bear left onto $road"
            TurnDirection.SHARP_RIGHT -> "Sharp right onto $road"
            TurnDirection.TURN_RIGHT -> "Turn right onto $road"
            TurnDirection.SLIGHT_RIGHT -> "Bear right onto $road"
            TurnDirection.STRAIGHT -> if (type == "depart") "Head on $road" else "Continue straight on $road"
        }
    }
}
