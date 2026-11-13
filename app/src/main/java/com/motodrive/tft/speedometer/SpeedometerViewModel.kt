package com.motodrive.tft.speedometer

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
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
    val movingDurationSeconds: Long = 0L,
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
    private var movingDurationSeconds = 0L

    private var timerJob: Job? = null
    private var rideStartTime: Long = 0L
    private var lastGpsFixTimestamp: Long = 0L

    // Adaptive low-pass filter
    private var smoothedSpeed = 0f

    // Sensors & Compass
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var hasRotationVector = false

    // Circular low-pass filter for compass to avoid 0/360 wrap glitch
    private var smoothedSin = 0f
    private var smoothedCos = 1f
    private var geomagneticDeclination = 0f

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

                // If GPS signal lost for > 3.5s (e.g. tunnel), reset previousLocation to prevent straight-line jump
                if (!isGpsActive) {
                    previousLocation = null
                    if (smoothedSpeed > 0f) {
                        smoothedSpeed *= 0.6f
                        if (smoothedSpeed < 1.0f) smoothedSpeed = 0f
                    }
                } else if (smoothedSpeed >= 2.0f) {
                    movingDurationSeconds++
                }

                _uiState.update { current ->
                    val newDuration = current.rideDurationSeconds + 1
                    // Moving average speed: only accumulates while in motion (excluding stationary red lights)
                    val avgSpeed = if (movingDurationSeconds > 4L && totalDistanceMeters > 0) {
                        ((totalDistanceMeters / 1000.0) / (movingDurationSeconds / 3600.0)).toFloat()
                    } else {
                        current.averageSpeedKmH
                    }

                    current.copy(
                        currentSpeedKmH = if (isGpsActive) current.currentSpeedKmH else smoothedSpeed,
                        hasGpsFix = isGpsActive,
                        rideDurationSeconds = newDuration,
                        movingDurationSeconds = movingDurationSeconds,
                        averageSpeedKmH = avgSpeed
                    )
                }
            }
        }
    }

    fun startLocationUpdates() {
        try {
            // High-precision GPS Provider ONLY (Do NOT use network/cell-tower provider for vehicle odometer/speed)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L, // 500ms updates
                    0.5f,
                    this
                )
            }
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastGps != null && (SystemClock.elapsedRealtimeNanos() - lastGps.elapsedRealtimeNanos) < 5_000_000_000L) {
                onLocationChanged(lastGps)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerSensors() {
        try {
            val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotVector != null) {
                sensorManager.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)
                hasRotationVector = true
            } else {
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        // 1. Strict GPS provider check
        if (location.provider != LocationManager.GPS_PROVIDER) {
            return
        }

        // 2. Strict 25-meter accuracy gate
        if (location.hasAccuracy() && location.accuracy > 25f) {
            return
        }

        // 3. Stale fix rejection (> 2.0 seconds old) using monotonic clock
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (ageNanos > 2_000_000_000L) {
            return
        }

        lastGpsFixTimestamp = SystemClock.elapsedRealtime()

        // Update geomagnetic declination for true geographic north calculation
        updateDeclination(location)

        // Monotonic time delta in seconds
        val dtSeconds = if (previousLocation != null) {
            (location.elapsedRealtimeNanos - previousLocation!!.elapsedRealtimeNanos) / 1_000_000_000.0
        } else {
            0.0
        }

        // 4. Doppler speed with accuracy verification
        var candidateSpeedKmH: Float? = null
        if (location.hasSpeed()) {
            val speedAcc = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 0f
            if (!location.hasSpeedAccuracy() || speedAcc <= 2.5f) {
                candidateSpeedKmH = location.speed * 3.6f
            }
        }

        // Monotonic fallback if Doppler speed unavailable
        if (candidateSpeedKmH == null && previousLocation != null && dtSeconds in 0.2..4.0) {
            val dist = previousLocation!!.distanceTo(location)
            candidateSpeedKmH = ((dist / dtSeconds) * 3.6).toFloat()
        }

        var rawSpeedKmH = candidateSpeedKmH ?: 0f

        // 5. Stationary floor: <= 2.0 km/h is treated as 0 km/h (stops drift at red lights)
        if (rawSpeedKmH < 2.0f) {
            rawSpeedKmH = 0f
        }

        // 6. Plausibility & acceleration clamp (max motorcycle acceleration ~12 m/s² = 43.2 km/h/s)
        if (smoothedSpeed > 0f && dtSeconds in 0.1..3.0) {
            val maxDeltaKmH = (12.0f * 3.6f * dtSeconds.toFloat())
            val speedDelta = rawSpeedKmH - smoothedSpeed
            if (abs(speedDelta) > maxDeltaKmH) {
                rawSpeedKmH = smoothedSpeed + (sign(speedDelta) * maxDeltaKmH)
            }
        }

        // 7. Adaptive low-pass filter: alpha = 0.65 when accelerating/braking; 0.25 when cruising
        val speedDiff = abs(rawSpeedKmH - smoothedSpeed)
        val adaptiveFactor = if (speedDiff > 5.0f) 0.65f else 0.25f
        smoothedSpeed = (rawSpeedKmH * adaptiveFactor) + (smoothedSpeed * (1f - adaptiveFactor))
        if (smoothedSpeed < 1.5f) smoothedSpeed = 0f

        // 8. Odometer accumulation with jitter floor & speed-based jump limit
        if (previousLocation != null && dtSeconds in 0.2..4.0) {
            val dist = previousLocation!!.distanceTo(location)
            // Jitter floor: ignore displacements < 1.2 meters to prevent stationary odometer creep
            // Jump limit: max plausible motorcycle velocity (~200 km/h = 56 m/s) * dt + margin
            val maxPlausibleDist = (56.0 * dtSeconds) + 10.0
            if (dist >= 1.2 && dist <= maxPlausibleDist && smoothedSpeed >= 2.0f) {
                totalDistanceMeters += dist
            }
        }
        previousLocation = location

        // 9. Heading calculation: GPS ground track when riding (> 5 km/h) has priority
        val bearingDeg = if (location.hasBearing() && smoothedSpeed > 5.0f) {
            val b = location.bearing
            val rad = Math.toRadians(b.toDouble())
            smoothedSin = sin(rad).toFloat()
            smoothedCos = cos(rad).toFloat()
            b
        } else {
            _uiState.value.headingDegrees
        }

        _uiState.update { current ->
            val maxSpd = max(current.maxSpeedKmH, smoothedSpeed)
            current.copy(
                currentSpeedKmH = smoothedSpeed,
                maxSpeedKmH = maxSpd,
                tripDistanceKm = (totalDistanceMeters / 1000.0).toFloat(),
                hasGpsFix = true,
                headingDegrees = bearingDeg,
                headingCardinal = getCardinalDirection(bearingDeg),
                isRiding = smoothedSpeed > 5.0f,
                lastLocation = location
            )
        }
    }

    private fun updateDeclination(loc: Location) {
        try {
            val field = GeomagneticField(
                loc.latitude.toFloat(),
                loc.longitude.toFloat(),
                loc.altitude.toFloat(),
                loc.time
            )
            geomagneticDeclination = field.declination
        } catch (e: Exception) {
            geomagneticDeclination = 0f
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            applyOrientationMatrix()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            hasAccelerometer = true
            if (hasMagnetometer && !hasRotationVector) computeOrientationFromAccelMag()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            hasMagnetometer = true
            if (hasAccelerometer && !hasRotationVector) computeOrientationFromAccelMag()
        }
    }

    private fun computeOrientationFromAccelMag() {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        if (success) {
            applyOrientationMatrix()
        }
    }

    private fun applyOrientationMatrix() {
        // Landscape remap (Realme C83 landscape cockpit)
        val rotation = getDisplayRotation()
        when (rotation) {
            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remappedMatrix
                )
            }
            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remappedMatrix
                )
            }
            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    remappedMatrix
                )
            }
            else -> {
                System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
            }
        }

        SensorManager.getOrientation(remappedMatrix, orientationAngles)
        val rawAzimuthDeg = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + geomagneticDeclination + 360f) % 360f

        // Circular low-pass filter prevents 0/360 wrap jitter
        val rad = Math.toRadians(rawAzimuthDeg.toDouble())
        val alpha = 0.15f
        smoothedSin = (1f - alpha) * smoothedSin + alpha * sin(rad).toFloat()
        smoothedCos = (1f - alpha) * smoothedCos + alpha * cos(rad).toFloat()
        val smoothDeg = ((Math.toDegrees(atan2(smoothedSin.toDouble(), smoothedCos.toDouble())).toFloat() + 360f) % 360f)

        // Only update compass heading from magnetic sensor when vehicle is stationary or moving slow (< 5 km/h)
        if (_uiState.value.currentSpeedKmH <= 5.0f) {
            _uiState.update { current ->
                current.copy(
                    headingDegrees = smoothDeg,
                    headingCardinal = getCardinalDirection(smoothDeg)
                )
            }
        }
    }

    private fun getDisplayRotation(): Int {
        val windowManager = getApplication<Application>().getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                getApplication<Application>().display?.rotation ?: Surface.ROTATION_90
            } catch (e: Exception) {
                Surface.ROTATION_90
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_90
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _uiState.update { it.copy(hasGpsFix = true) }
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _uiState.update { it.copy(hasGpsFix = false) }
        }
    }

    fun resetTrip() {
        totalDistanceMeters = 0.0
        movingDurationSeconds = 0L
        rideStartTime = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                tripDistanceKm = 0f,
                rideDurationSeconds = 0L,
                movingDurationSeconds = 0L,
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
