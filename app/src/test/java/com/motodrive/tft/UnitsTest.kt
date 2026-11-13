package com.motodrive.tft

import com.motodrive.tft.core.util.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @Test
    fun testSpeedConversionKmhToMph() {
        // 100 km/h is ~62.137 mph
        val mph = Units.speed(100f, "mph")
        assertEquals(62.1371f, mph, 0.05f)

        // km/h returns unchanged
        val kmh = Units.speed(100f, "km/h")
        assertEquals(100f, kmh, 0.001f)
    }

    @Test
    fun testDistanceConversion() {
        // 10 km is ~6.2137 miles
        val miles = Units.distance(10f, "mph")
        assertEquals(6.2137f, miles, 0.05f)

        val km = Units.distance(10f, "km/h")
        assertEquals(10f, km, 0.001f)
    }

    @Test
    fun testLabels() {
        assertEquals("MPH", Units.speedLabel("mph"))
        assertEquals("KM/H", Units.speedLabel("km/h"))
        assertEquals("mi", Units.distanceLabel("mph"))
        assertEquals("km", Units.distanceLabel("km/h"))
        assertEquals("MILES", Units.distanceLabelLong("mph"))
        assertEquals("KILOMETERS", Units.distanceLabelLong("km/h"))
    }

    @Test
    fun testIsMph() {
        assertTrue(Units.isMph("mph"))
        assertTrue(Units.isMph("MPH"))
        assertFalse(Units.isMph("km/h"))
    }
}
