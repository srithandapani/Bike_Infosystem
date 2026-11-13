package com.motodrive.tft

import com.motodrive.tft.navigation.ActiveRoute
import com.motodrive.tft.navigation.InAppRouteManager
import com.motodrive.tft.navigation.RoutedPath
import com.motodrive.tft.navigation.haversine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class InAppRouteProjectionTest {

    @Test
    fun testActiveRouteCumulativeDistances() {
        val geom = listOf(
            GeoPoint(12.0, 77.0),
            GeoPoint(12.0, 77.1),
            GeoPoint(12.0, 77.2)
        )
        val route = ActiveRoute("Test", geom.last(), RoutedPath(geom, 2000.0, 120.0, emptyList()))
        assertEquals(3, route.cumulative.size)
        assertEquals(0.0, route.cumulative[0], 0.001)
        assertTrue(route.cumulative[1] > 10000.0) // ~10.8 km per 0.1 deg lon at lat 12
        assertTrue(route.cumulative[2] > route.cumulative[1])
    }

    @Test
    fun testProjectOnCollinearPoint() {
        val geom = listOf(
            GeoPoint(12.0, 77.0),
            GeoPoint(12.0, 77.1)
        )
        val route = ActiveRoute("Test", geom.last(), RoutedPath(geom, 10800.0, 60.0, emptyList()))
        val midPoint = GeoPoint(12.0, 77.05)
        val progress = InAppRouteManager.project(route, midPoint, 0)

        assertEquals(0, progress.segmentIndex)
        assertTrue("Off-route distance should be ~0, was ${progress.offRouteMeters}", progress.offRouteMeters < 2.0)
        assertEquals(route.totalMeters / 2.0, progress.alongMeters, 20.0)
    }

    @Test
    fun testProjectWithPerpendicularOffset() {
        val geom = listOf(
            GeoPoint(12.0, 77.0),
            GeoPoint(12.0, 77.1)
        )
        val route = ActiveRoute("Test", geom.last(), RoutedPath(geom, 10800.0, 60.0, emptyList()))
        // Point is ~111m north of the midpoint (0.001 deg latitude)
        val offsetPoint = GeoPoint(12.001, 77.05)
        val progress = InAppRouteManager.project(route, offsetPoint, 0)

        assertEquals(0, progress.segmentIndex)
        assertTrue("Off route distance should be ~111m, was ${progress.offRouteMeters}", progress.offRouteMeters in 105.0..115.0)
    }

    @Test
    fun testHaversineDistanceAccuracy() {
        // Paris to London ~ 343 km
        val distMeters = haversine(
            GeoPoint(48.8566, 2.3522), // Paris
            GeoPoint(51.5074, -0.1278) // London
        )
        val distKm = distMeters / 1000.0
        assertTrue("Distance should be ~343km, was $distKm", distKm in 340.0..345.0)
    }
}
