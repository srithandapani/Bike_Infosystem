package com.motodrive.tft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sign

class SpeedFilterTest {

    /**
     * Simulates the adaptive speed filter used in SpeedometerViewModel
     */
    private fun applyAdaptiveFilter(rawSpeedKmH: Float, currentSmoothed: Float): Float {
        var raw = rawSpeedKmH
        // Stationary floor
        if (raw < 2.0f) raw = 0f

        val speedDiff = abs(raw - currentSmoothed)
        val alpha = if (speedDiff > 5.0f) 0.65f else 0.25f
        var smoothed = (raw * alpha) + (currentSmoothed * (1f - alpha))
        if (smoothed < 1.5f) smoothed = 0f
        return smoothed
    }

    @Test
    fun testStationaryJitterFloor() {
        var smoothed = 0f
        // Stationary GPS jitter values (0.4 km/h, 1.2 km/h, 1.8 km/h)
        val stationaryJitter = listOf(0.4f, 1.2f, 1.8f, 0.9f, 0.0f)
        for (speed in stationaryJitter) {
            smoothed = applyAdaptiveFilter(speed, smoothed)
            assertEquals("Stationary jitter should be floored to 0", 0f, smoothed, 0.001f)
        }
    }

    @Test
    fun testAccelerationClamping() {
        val currentSmoothed = 40.0f
        val dtSeconds = 1.0f
        val maxDelta = 12.0f * 3.6f * dtSeconds // 43.2 km/h/s clamp

        // Glitch spike to 120 km/h in 1 second (physically impossible for commuter bike)
        val glitchSpike = 120.0f
        val delta = glitchSpike - currentSmoothed
        val clampedRaw = if (abs(delta) > maxDelta) {
            currentSmoothed + (sign(delta) * maxDelta)
        } else {
            glitchSpike
        }

        assertEquals(40.0f + 43.2f, clampedRaw, 0.01f)
        assertTrue(clampedRaw < glitchSpike)
    }

    @Test
    fun testMovingAverageExcludesStops() {
        val totalDistanceKm = 10.0 // 10 km ridden
        val movingSeconds = 600L   // 10 minutes moving time (60 km/h average)
        val totalRideSeconds = 1200L // 20 minutes elapsed (includes 10 min at traffic lights)

        // Using moving time:
        val movingAverage = (totalDistanceKm / (movingSeconds / 3600.0)).toFloat()
        // Old conflicting formula using total time:
        val totalAverage = (totalDistanceKm / (totalRideSeconds / 3600.0)).toFloat()

        assertEquals(60.0f, movingAverage, 0.01f)
        assertEquals(30.0f, totalAverage, 0.01f)
        assertTrue("Moving average must reflect actual riding speed", movingAverage > totalAverage)
    }
}
