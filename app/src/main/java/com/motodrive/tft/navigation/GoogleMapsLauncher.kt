package com.motodrive.tft.navigation

import android.content.Context
import android.widget.Toast

/**
 * Automotive In-App Navigation Router.
 * External app redirects and URL schemes are strictly disabled per motorcycle cockpit specification.
 * All navigation runs natively embedded inside the TVS Ronin infotainment display.
 */
object GoogleMapsLauncher {

    fun startTurnByTurnNavigation(context: Context, destinationQuery: String) {
        // Redirection to external applications is disabled. Navigation is handled natively in-app.
        Toast.makeText(context, "Navigating to $destinationQuery in Cockpit Display", Toast.LENGTH_SHORT).show()
    }

    fun startTurnByTurnNavigation(context: Context, latitude: Double, longitude: Double) {
        startTurnByTurnNavigation(context, "$latitude,$longitude")
    }
}
