package com.motodrive.tft.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val temperatureCelsius: Float = 26f,
    val conditionDescription: String = "Clear Sky",
    val humidityPercent: Int = 54,
    val windSpeedKmH: Float = 14f,
    val precipitationMm: Float = 0f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val temperatureUnit: String = "°C"
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository()

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        // Fetch default city weather or upon GPS coordinates
        fetchWeatherForCoordinates(12.9716, 77.5946)
    }

    fun fetchWeatherForCoordinates(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.fetchWeather(lat, lng)
            result.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        temperatureCelsius = data.temperatureCelsius,
                        conditionDescription = data.conditionDescription,
                        humidityPercent = data.humidityPercent,
                        windSpeedKmH = data.windSpeedKmH,
                        precipitationMm = data.precipitationMm,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Offline Weather (Cached)"
                    )
                }
            }
        }
    }

    fun setUnit(unit: String) {
        _uiState.update { it.copy(temperatureUnit = unit) }
    }
}

