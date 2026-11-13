package com.motodrive.tft

import com.motodrive.tft.navigation.InAppRouteManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class InAppRouteProjectionTest {

    @Test
    fun testProjectOnSegmentCollinear() {
        val a = GeoPoint(12.0, 77.0)
        val b = GeoPoint(12.0, 77.1)
        val midpoint = GeoPoint(12.0, 77.05)

        val (snapped, dist) = InAppRouteManager.projectOnSegment(midpoint, a, b)
        assertEquals(12.0, snapped.latitude, 0.0001)
        assertEquals(77.05, snapped.longitude, 0.0001)
        assertTrue(dist < 5.0f) // virtually 0 distance
    }

    @Test
    fun testProjectOnSegmentPerpendicularOffset() {
        val a = GeoPoint(12.0, 77.0)
        val b = GeoPoint(12.0, 77.1)
        // Point is north of the midpoint by ~0.001 deg (~111 meters)
        val p = GeoPoint(12.001, 77.05)

        val (snapped, dist) = InAppRouteManager.projectOnSegment(p, a, b)
        assertEquals(12.0, snapped.latitude, 0.0001)
        assertEquals(77.05, snapped.longitude, 0.0001)
        assertTrue("Distance should be ~111m, was $dist", dist in 100f..125f)
    }

    @Test
    fun testHaversineDistanceAccuracy() {
        // Paris to London ~ 343 km
        val distMeters = InAppRouteManager.calculateHaversine(
            48.8566, 2.3522, // Paris
            51.5074, -0.1278 // London
        )
        val distKm = distMeters / 1000.0
        assertTrue("Distance should be ~343km, was $distKm", distKm in 340.0..345.0)
    }
}
