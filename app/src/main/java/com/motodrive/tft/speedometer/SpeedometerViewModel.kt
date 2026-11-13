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
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    /** Moving average: trip distance / moving time (stops excluded). */
    val averageSpeedKmH: Float = 0f,
    val tripDistanceKm: Float = 0f,
    /** Total elapsed time since trip start / reset (includes stops). */
    val rideDurationSeconds: Long = 0L,
    /** Time spent actually moving. */
    val movingTimeSeconds: Long = 0L,
    val headingDegrees: Float = 0f,
    val headingCardinal: String = "N",
    val hasGpsFix: Boolean = false,
    val satelliteCount: Int = 0,
    val gpsAccuracyMeters: Float = 0f,
    val speedUnit: String = "km/h",
    val isRiding: Boolean = false,
    val lastLocation: Location? = null
)

class SpeedometerViewModel(application: Application) :
    AndroidViewModel(application), LocationListener, SensorEventListener {

    companion object {
        /** Reject fixes whose horizontal accuracy is worse than this (m). */
        private const val MAX_ACCURACY_M = 25f
        /** Reject fixes older than this (ms) – cached / delayed fixes give wrong speed. */
        private const val MAX_FIX_AGE_MS = 3000L
        /** GPS considered lost after this long without an accepted fix. */
        private const val GPS_TIMEOUT_MS = 3500L
        /** Below this raw speed (km/h) we treat the bike as stationary (Doppler noise floor). */
        private const val STATIONARY_KMH = 2.0f
        /** Physical upper bound for a 225 cc bike; anything above is a glitch (m/s). */
        private const val MAX_PLAUSIBLE_MS = 70f
        /** Max believable acceleration between two fixes (m/s^2). */
        private const val MAX_ACCEL_MS2 = 8f
    }

    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(SpeedometerUiState())
    val uiState: StateFlow<SpeedometerUiState> = _uiState.asStateFlow()

    private var previousFix: Location? = null
    private var totalDistanceMeters = 0.0
    private var movingTimeMs = 0L
    private var smoothedSpeed = 0f // km/h

    private var timerJob: Job? = null
    private var lastGpsFixElapsedMs = 0L
    private var lastDeclinationFix: Location? = null
    private var declinationDeg = 0f
    private var locationUpdatesStarted = false

    private val fusedLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleLocationFix(loc, isFused = true)
        }
    }

    // ---- Compass (rotation-vector based, landscape aware, true north) ----
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var compassSin = 0f
    private var compassCos = 1f
    private var hasCompass = false

    init {
        startRideTimer()
        registerSensors()
        startLocationUpdates()
    }

    private fun startRideTimer() {
        lastGpsFixElapsedMs = 0L
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val gpsActive = lastGpsFixElapsedMs != 0L && (now - lastGpsFixElapsedMs) < GPS_TIMEOUT_MS

                if (!gpsActive) {
                    // Signal lost: decay, and forget previous fix so the first fix after a
                    // tunnel doesn't add a huge straight-line "jump" to the odometer.
                    smoothedSpeed *= 0.6f
                    if (smoothedSpeed < 1f) smoothedSpeed = 0f
                    previousFix = null
                }

                _uiState.update { cur ->
                    cur.copy(
                        currentSpeedKmH = if (gpsActive) cur.currentSpeedKmH else smoothedSpeed,
                        hasGpsFix = gpsActive || cur.lastLocation != null,
                        isRiding = gpsActive && cur.currentSpeedKmH > 5f,
                        rideDurationSeconds = cur.rideDurationSeconds + 1
                    )
                }
            }
        }
    }

    fun startLocationUpdates() {
        try {
            // 1. Immediately seed last known location from Fused Location and Native Providers
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    handleInitialLocation(loc)
                }
            }

            try {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = when {
                    lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
                    lastGps != null -> lastGps
                    else -> lastNet
                }
                if (best != null) {
                    handleInitialLocation(best)
                }
            } catch (_: SecurityException) {}

            // 2. High Accuracy Continuous Fused Location Updates
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, fusedLocationCallback, Looper.getMainLooper())

            // 3. Native LocationManager updates for high precision GPS Doppler speed
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000L, 0f, this, Looper.getMainLooper()
                )
            }
            locationUpdatesStarted = true
        } catch (_: SecurityException) {
            // Permission not granted yet; MainActivity calls this again after grant.
            locationUpdatesStarted = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleInitialLocation(location: Location) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        refreshDeclination(location)
        _uiState.update { cur ->
            if (cur.lastLocation == null) {
                cur.copy(
                    lastLocation = location,
                    hasGpsFix = true,
                    gpsAccuracyMeters = if (location.hasAccuracy()) location.accuracy else cur.gpsAccuracyMeters
                )
            } else cur
        }
    }

    private fun registerSensors() {
        try {
            val rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rv != null) {
                sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = try {
        val wm = getApplication<Application>().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.rotation
    } catch (_: Exception) {
        Surface.ROTATION_90
    }

    // ----------------------------------------------------------------------------------
    // Location handling
    // ----------------------------------------------------------------------------------
    override fun onLocationChanged(location: Location) {
        handleLocationFix(location, isFused = false)
    }

    private fun handleLocationFix(location: Location, isFused: Boolean) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        lastGpsFixElapsedMs = nowElapsedMs

        refreshDeclination(location)

        // Always update device live location immediately for navigation & map display
        _uiState.update { cur ->
            cur.copy(
                lastLocation = location,
                hasGpsFix = true,
                gpsAccuracyMeters = if (location.hasAccuracy()) location.accuracy else cur.gpsAccuracyMeters
            )
        }

        // Precision Speedometer & Odometer Filtering
        // Only run speed/distance integration on high-confidence fixes (GPS or Doppler speed)
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        val hasGoodAccuracy = location.hasAccuracy() && location.accuracy <= MAX_ACCURACY_M
        val fixElapsedMs = location.elapsedRealtimeNanos / 1_000_000L
        val isFresh = (nowElapsedMs - fixElapsedMs) <= MAX_FIX_AGE_MS

        if ((isGps || location.hasSpeed()) && hasGoodAccuracy && isFresh) {
            val prev = previousFix
            val dtSec = if (prev != null) (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f else 0f
            if (prev != null && dtSec <= 0f) return

            // Doppler speed from the chipset is far more accurate than distance/time between fixes
            val dopplerOk = location.hasSpeed() &&
                    !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            location.hasSpeedAccuracy() && location.speedAccuracyMetersPerSecond > 3f)
            var rawMs: Float = if (dopplerOk) {
                location.speed
            } else if (prev != null && dtSec in 0.2f..5f) {
                prev.distanceTo(location) / dtSec
            } else 0f

            if (rawMs > MAX_PLAUSIBLE_MS) return // glitch

            if (prev != null && dtSec > 0f) {
                val accel = abs(rawMs - smoothedSpeed / 3.6f) / dtSec
                if (accel > MAX_ACCEL_MS2 && lastGpsFixElapsedMs != 0L &&
                    nowElapsedMs - lastGpsFixElapsedMs < GPS_TIMEOUT_MS
                ) {
                    rawMs = smoothedSpeed / 3.6f + sign(rawMs - smoothedSpeed / 3.6f) * MAX_ACCEL_MS2 * dtSec
                }
            }

            val rawKmH = (rawMs * 3.6f).coerceAtLeast(0f)

            if (rawKmH < STATIONARY_KMH) {
                smoothedSpeed = 0f
            } else {
                val alpha = if (abs(rawKmH - smoothedSpeed) > 7f) 0.65f else 0.25f
                smoothedSpeed = rawKmH * alpha + smoothedSpeed * (1f - alpha)
            }
            val moving = smoothedSpeed > 0f

            if (prev != null && moving) {
                val d = prev.distanceTo(location)
                val noiseFloor = max(1f, 0.5f * (prev.accuracy + location.accuracy) * 0.25f)
                if (d >= noiseFloor && d <= MAX_PLAUSIBLE_MS * dtSec) {
                    totalDistanceMeters += d.toDouble()
                }
                movingTimeMs += (dtSec * 1000f).toLong()
            }
            previousFix = location

            val bearingDeg = if (location.hasBearing() && smoothedSpeed > 5f) {
                location.bearing
            } else _uiState.value.headingDegrees

            val distKm = (totalDistanceMeters / 1000.0)
            val movingHours = movingTimeMs / 3_600_000.0
            val avg = if (movingHours > 0.0014) (distKm / movingHours).toFloat() else 0f

            _uiState.update { cur ->
                cur.copy(
                    currentSpeedKmH = smoothedSpeed,
                    maxSpeedKmH = max(cur.maxSpeedKmH, smoothedSpeed),
                    averageSpeedKmH = avg,
                    tripDistanceKm = distKm.toFloat(),
                    movingTimeSeconds = movingTimeMs / 1000L,
                    hasGpsFix = true,
                    gpsAccuracyMeters = location.accuracy,
                    headingDegrees = bearingDeg,
                    headingCardinal = cardinal(bearingDeg),
                    isRiding = smoothedSpeed > 5f,
                    lastLocation = location
                )
            }
        }
    }

    private fun refreshDeclination(loc: Location) {
        val last = lastDeclinationFix
        if (last == null || loc.distanceTo(last) > 20_000f) {
            declinationDeg = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(),
                loc.altitude.toFloat(), System.currentTimeMillis()
            ).declination
            lastDeclinationFix = loc
        }
    }

    // ----------------------------------------------------------------------------------
    // Compass (only shown/used when stationary; GPS bearing is used when moving)
    // ----------------------------------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (_uiState.value.currentSpeedKmH >= 3f) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        // The app is locked to landscape: remap axes for the actual display rotation,
        // otherwise heading is off by 90 degrees.
        val (axisX, axisY) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)

        // Magnetic -> true north
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat() + declinationDeg
        val rad = Math.toRadians(azimuth.toDouble())

        // Circular low-pass filter (averaging sin/cos avoids the 359 -> 0 wrap glitch)
        val a = 0.15f
        compassSin = if (hasCompass) compassSin + a * (sin(rad).toFloat() - compassSin) else sin(rad).toFloat()
        compassCos = if (hasCompass) compassCos + a * (cos(rad).toFloat() - compassCos) else cos(rad).toFloat()
        hasCompass = true

        var deg = Math.toDegrees(atan2(compassSin.toDouble(), compassCos.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        _uiState.update { it.copy(headingDegrees = deg, headingCardinal = cardinal(deg)) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            locationUpdatesStarted = false
            startLocationUpdates()
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            locationUpdatesStarted = false
            lastGpsFixElapsedMs = 0L
            _uiState.update { it.copy(hasGpsFix = false) }
        }
    }

    fun resetTrip() {
        totalDistanceMeters = 0.0
        movingTimeMs = 0L
        _uiState.update {
            it.copy(
                tripDistanceKm = 0f,
                rideDurationSeconds = 0L,
                movingTimeSeconds = 0L,
                averageSpeedKmH = 0f,
                maxSpeedKmH = 0f
            )
        }
    }

    fun setSpeedUnit(unit: String) {
        _uiState.update { it.copy(speedUnit = unit) }
    }

    private fun cardinal(degrees: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val n = ((degrees % 360f) + 360f) % 360f
        return names[((n + 22.5f) / 45f).toInt() % 8]
    }

    override fun onCleared() {
        super.onCleared()
        try {
            fusedLocationClient.removeLocationUpdates(fusedLocationCallback)
            locationManager.removeUpdates(this)
            sensorManager.unregisterListener(this)
            timerJob?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
