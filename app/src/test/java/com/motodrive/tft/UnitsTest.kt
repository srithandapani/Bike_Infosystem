package com.motodrive.tft

import com.motodrive.tft.core.util.Units
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun testSpeedConversionKmhToMph() {
        // 100 km/h is ~62.14 mph
        val mph = Units.speedToDisplay(100f, "mph")
        assertEquals(62.1371f, mph, 0.05f)

        // km/h returns unchanged
        val kmh = Units.speedToDisplay(100f, "km/h")
        assertEquals(100f, kmh, 0.001f)
    }

    @Test
    fun testDistanceConversion() {
        // 10 km is ~6.21 miles
        val miles = Units.distanceToDisplay(10f, "mph")
        assertEquals(6.2137f, miles, 0.05f)

        val km = Units.distanceToDisplay(10f, "km/h")
        assertEquals(10f, km, 0.001f)
    }

    @Test
    fun testLabels() {
        assertEquals("MPH", Units.speedLabel("mph"))
        assertEquals("KM/H", Units.speedLabel("km/h"))
        assertEquals("mi", Units.distanceLabel("mph"))
        assertEquals("km", Units.distanceLabel("km/h"))
    }

    @Test
    fun testGuidanceFormatting() {
        assertEquals("400 m", Units.formatGuidanceDistance(400f, "km/h"))
        assertEquals("1.5 km", Units.formatGuidanceDistance(1500f, "km/h"))
        assertEquals("650 ft", Units.formatGuidanceDistance(200f, "mph"))
    }
}
