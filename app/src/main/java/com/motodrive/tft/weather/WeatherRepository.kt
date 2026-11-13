package com.motodrive.tft.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class WeatherData(
    val temperatureCelsius: Float,
    val weatherCode: Int,
    val conditionDescription: String,
    val humidityPercent: Int,
    val windSpeedKmH: Float,
    val precipitationMm: Float,
    val isDay: Boolean
)

class WeatherRepository {

    suspend fun fetchWeather(latitude: Double, longitude: Double): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            // Explicitly request wind_speed_unit=kmh and timezone=auto
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&wind_speed_unit=kmh&timezone=auto"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "BikeInfosystem/2.0 (Motorcycle Cockpit)")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toFloat()
                val code = current.getInt("weather_code")
                val humidity = current.getInt("relative_humidity_2m")
                val wind = current.getDouble("wind_speed_10m").toFloat()
                val precip = current.getDouble("precipitation").toFloat()
                val isDay = current.getInt("is_day") == 1

                val description = getWeatherCodeDescription(code)

                Result.success(
                    WeatherData(
                        temperatureCelsius = temp,
                        weatherCode = code,
                        conditionDescription = description,
                        humidityPercent = humidity,
                        windSpeedKmH = wind,
                        precipitationMm = precip,
                        isDay = isDay
                    )
                )
            } else {
                Result.failure(Exception("HTTP error ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Standard WMO Weather Interpretation Codes (WW)
     */
    private fun getWeatherCodeDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy Riding Conditions"
            51 -> "Light Drizzle"
            53 -> "Moderate Drizzle"
            55 -> "Dense Drizzle"
            56, 57 -> "Freezing Drizzle / Icy Caution"
            61 -> "Slight Rain"
            63 -> "Moderate Rain"
            65 -> "Heavy Rain / Wet Roads"
            66, 67 -> "Freezing Rain / Skid Risk"
            71 -> "Slight Snowfall"
            73 -> "Moderate Snowfall"
            75 -> "Heavy Snowfall"
            77 -> "Snow Grains"
            80 -> "Slight Rain Showers"
            81 -> "Moderate Rain Showers"
            82 -> "Violent Rain Showers"
            85 -> "Slight Snow Showers"
            86 -> "Heavy Snow Showers"
            95 -> "Thunderstorm Caution"
            96, 99 -> "Thunderstorm with Hail"
            else -> "Fair Conditions"
        }
    }
}
