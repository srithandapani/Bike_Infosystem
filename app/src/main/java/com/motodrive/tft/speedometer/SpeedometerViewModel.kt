package com.motodrive.tft.speedometer

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.*

data class SpeedometerUiState(
    val currentSpeedKmH: Float = 0f,
    val maxSpeedKmH: Float = 0f,
    val averageSpeedKmH: Float = 0f,
    val tripDistanceKm: Float = 0f,
    val rideDurationSeconds: Long = 0L,
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "N",
    val hasGpsFix: Boolean = false,
    val satelliteCount: Int = 0,
    val speedUnit: String = "km/h",
    val isRiding: Boolean = false,
    val lastLocation: Location? = null
)

class SpeedometerViewModel(application: Application) : AndroidViewModel(application), LocationListener, SensorEventListener {

    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _uiState = MutableStateFlow(SpeedometerUiState())
    val uiState: StateFlow<SpeedometerUiState> = _uiState.asStateFlow()

    private var previousLocation: Location? = null
    private var totalDistanceMeters = 0.0
    private var speedSamplesCount = 0
    private var speedSamplesSum = 0.0

    private var timerJob: Job? = null
    private var rideStartTime: Long = 0L
    private var lastGpsFixTimestamp: Long = 0L

    // Adaptive low-pass filter coefficients:
    // Snappy response during acceleration/braking; high smoothing during steady cruising
    private var smoothedSpeed = 0f

    // Sensors for Compass Heading
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    init {
        startRideTimer()
        registerSensors()
        startLocationUpdates()
    }

    private fun startRideTimer() {
        rideStartTime = SystemClock.elapsedRealtime()
        lastGpsFixTimestamp = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val isGpsActive = (now - lastGpsFixTimestamp) < 3500L

                // If GPS signal lost for > 3.5s, smoothly decay speed rather than freezing
                if (!isGpsActive && smoothedSpeed > 0f) {
                    smoothedSpeed *= 0.6f
                    if (smoothedSpeed < 1.0f) smoothedSpeed = 0f
                }

                _uiState.update { current ->
                    val newDuration = current.rideDurationSeconds + 1
                    val avgSpeed = if (newDuration > 0 && totalDistanceMeters > 0) {
                        ((totalDistanceMeters / 1000.0) / (newDuration / 3600.0)).toFloat()
                    } else {
                        current.averageSpeedKmH
                    }
                    current.copy(
                        currentSpeedKmH = if (isGpsActive) current.currentSpeedKmH else smoothedSpeed,
                        hasGpsFix = isGpsActive,
                        rideDurationSeconds = newDuration,
                        averageSpeedKmH = avgSpeed
                    )
                }
            }
        }
    }

    fun startLocationUpdates() {
        try {
            // Check GPS provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L, // 500 ms updates for responsive automotive gauge
                    0.5f, // 0.5 meter delta
                    this
                )
            }
            // Check Network provider as supplementary
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5f,
                    this
                )
            }
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastGps != null) {
                onLocationChanged(lastGps)
            }
        } catch (e: SecurityException) {
            // Location permission not yet granted; will update once granted
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerSensors() {
        try {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        // Discard poor accuracy fixes (> 35 meters) to prevent sudden speed jumps
        if (location.hasAccuracy() && location.accuracy > 35f) {
            return
        }

        lastGpsFixTimestamp = SystemClock.elapsedRealtime()

        val rawSpeedKmH = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else if (previousLocation != null) {
            val dist = previousLocation!!.distanceTo(location)
            val timeDiffSec = (location.time - previousLocation!!.time) / 1000f
            if (timeDiffSec > 0) (dist / timeDiffSec) * 3.6f else 0f
        } else {
            0f
        }

        // Dynamic Adaptive Low-Pass Filter:
        // Snappy alpha = 0.65 when accelerating or braking; steady alpha = 0.25 when cruising
        val speedDiff = abs(rawSpeedKmH - smoothedSpeed)
        val adaptiveFactor = if (speedDiff > 7.0f) 0.65f else 0.25f

        smoothedSpeed = (rawSpeedKmH * adaptiveFactor) + (smoothedSpeed * (1f - adaptiveFactor))
        if (smoothedSpeed < 1.0f) smoothedSpeed = 0f // Zero deadband at standstill

        // Accumulate distance if moving
        if (previousLocation != null && smoothedSpeed > 1.5f) {
            val dist = previousLocation!!.distanceTo(location)
            if (dist in 1.0..100.0) { // filter GPS jumps
                totalDistanceMeters += dist
            }
        }
        previousLocation = location

        if (smoothedSpeed > 0) {
            speedSamplesCount++
            speedSamplesSum += smoothedSpeed
        }

        val bearingDeg = if (location.hasBearing() && smoothedSpeed > 3f) {
            location.bearing
        } else {
            _uiState.value.headingDegrees
        }

        _uiState.update { current ->
            val maxSpd = max(current.maxSpeedKmH, smoothedSpeed)
            val avgSpd = if (speedSamplesCount > 0) (speedSamplesSum / speedSamplesCount).toFloat() else 0f
            current.copy(
                currentSpeedKmH = smoothedSpeed,
                maxSpeedKmH = maxSpd,
                averageSpeedKmH = avgSpd,
                tripDistanceKm = (totalDistanceMeters / 1000.0).toFloat(),
                hasGpsFix = true,
                headingDegrees = bearingDeg,
                headingCardinal = getCardinalDirection(bearingDeg),
                isRiding = smoothedSpeed > 5.0f,
                lastLocation = location
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            hasAccelerometer = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            hasMagnetometer = true
        }

        if (hasAccelerometer && hasMagnetometer && _uiState.value.currentSpeedKmH < 3.0f) {
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthInRadians = orientationAngles[0]
                var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                if (azimuthInDegrees < 0) azimuthInDegrees += 360f

                _uiState.update { current ->
                    current.copy(
                        headingDegrees = azimuthInDegrees,
                        headingCardinal = getCardinalDirection(azimuthInDegrees)
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        _uiState.update { it.copy(hasGpsFix = true) }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _uiState.update { it.copy(hasGpsFix = false) }
        }
    }

    fun resetTrip() {
        totalDistanceMeters = 0.0
        speedSamplesCount = 0
        speedSamplesSum = 0.0
        rideStartTime = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                tripDistanceKm = 0f,
                rideDurationSeconds = 0L,
                averageSpeedKmH = 0f,
                maxSpeedKmH = 0f
            )
        }
    }

    fun setSpeedUnit(unit: String) {
        _uiState.update { it.copy(speedUnit = unit) }
    }

    private fun getCardinalDirection(degrees: Float): String {
        val normalized = (degrees % 360 + 360) % 360
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "N"
            normalized >= 22.5 && normalized < 67.5 -> "NE"
            normalized >= 67.5 && normalized < 112.5 -> "E"
            normalized >= 112.5 && normalized < 157.5 -> "SE"
            normalized >= 157.5 && normalized < 202.5 -> "S"
            normalized >= 202.5 && normalized < 247.5 -> "SW"
            normalized >= 247.5 && normalized < 292.5 -> "W"
            normalized >= 292.5 && normalized < 337.5 -> "NW"
            else -> "N"
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            locationManager.removeUpdates(this)
            sensorManager.unregisterListener(this)
            timerJob?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

