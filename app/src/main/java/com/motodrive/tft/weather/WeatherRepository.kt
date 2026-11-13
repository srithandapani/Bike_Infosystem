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
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

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

    private fun getWeatherCodeDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Mainly Clear / Partly Cloudy"
            45, 48 -> "Foggy Riding Conditions"
            51, 53, 55 -> "Light Drizzle"
            61, 63, 65 -> "Rain / Wet Roads"
            71, 73, 75 -> "Snowfall"
            80, 81, 82 -> "Rain Showers"
            95, 96, 99 -> "Thunderstorm Caution"
            else -> "Fair Conditions"
        }
    }
}

