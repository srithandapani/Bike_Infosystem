package com.motodrive.tft

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver
import com.motodrive.tft.navigation.GooglePlacesClient
import com.motodrive.tft.navigation.NavStepUpdate
import com.motodrive.tft.navigation.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNavigationTest {

    @Test
    fun testNavStepUpdateRemainingConversion() {
        val update = NavStepUpdate(
            instruction = "Turn left onto Outer Ring Road",
            roadName = "Outer Ring Road",
            maneuverDirection = TurnDirection.TURN_LEFT,
            distanceToTurnMeters = 450,
            remainingDistanceMeters = 12500,
            remainingTimeSeconds = 900,
            isNavigating = true
        )

        assertEquals("Turn left onto Outer Ring Road", update.instruction)
        assertEquals(TurnDirection.TURN_LEFT, update.maneuverDirection)
        assertEquals(12.5f, update.remainingDistanceMeters / 1000f, 0.001f)
        assertEquals(15, update.remainingTimeSeconds / 60)
    }

    @Test
    fun testManeuverMapping() {
        // Test mapping constants
        assertEquals(TurnDirection.TURN_LEFT, mapManeuverHelper(Maneuver.TURN_LEFT))
        assertEquals(TurnDirection.TURN_RIGHT, mapManeuverHelper(Maneuver.TURN_RIGHT))
        assertEquals(TurnDirection.U_TURN, mapManeuverHelper(Maneuver.TURN_U_TURN_CLOCKWISE))
        assertEquals(TurnDirection.ROUNDABOUT, mapManeuverHelper(Maneuver.ROUNDABOUT_CLOCKWISE))
        assertEquals(TurnDirection.DESTINATION, mapManeuverHelper(Maneuver.DESTINATION))
    }

    private fun mapManeuverHelper(maneuver: Int?): TurnDirection {
        if (maneuver == null) return TurnDirection.STRAIGHT
        return when (maneuver) {
            Maneuver.TURN_LEFT -> TurnDirection.TURN_LEFT
            Maneuver.TURN_SHARP_LEFT -> TurnDirection.SHARP_LEFT
            Maneuver.TURN_SLIGHT_LEFT -> TurnDirection.SLIGHT_LEFT
            Maneuver.TURN_RIGHT -> TurnDirection.TURN_RIGHT
            Maneuver.TURN_SHARP_RIGHT -> TurnDirection.SHARP_RIGHT
            Maneuver.TURN_SLIGHT_RIGHT -> TurnDirection.SLIGHT_RIGHT
            Maneuver.ROUNDABOUT_CLOCKWISE,
            Maneuver.ROUNDABOUT_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_EXIT_CLOCKWISE,
            Maneuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE -> TurnDirection.ROUNDABOUT
            Maneuver.TURN_U_TURN_CLOCKWISE,
            Maneuver.TURN_U_TURN_COUNTERCLOCKWISE -> TurnDirection.U_TURN
            Maneuver.DESTINATION,
            Maneuver.DESTINATION_LEFT,
            Maneuver.DESTINATION_RIGHT -> TurnDirection.DESTINATION
            else -> TurnDirection.STRAIGHT
        }
    }
}
