package com.motodrive.tft.core.util

import kotlin.math.round

object Units {
    const val MILES_PER_KM = 0.62137119f
    const val KM_PER_MILE = 1.609344f

    /**
     * Converts a speed in km/h to the selected unit (km/h or mph).
     */
    fun speedToDisplay(speedKmH: Float, unit: String): Float {
        return if (unit.equals("mph", ignoreCase = true)) {
            speedKmH * MILES_PER_KM
        } else {
            speedKmH
        }
    }

    /**
     * Converts a distance in km to the selected unit (km or miles).
     */
    fun distanceToDisplay(distanceKm: Float, unit: String): Float {
        return if (unit.equals("mph", ignoreCase = true) || unit.equals("miles", ignoreCase = true) || unit.equals("mi", ignoreCase = true)) {
            distanceKm * MILES_PER_KM
        } else {
            distanceKm
        }
    }

    /**
     * Returns the formatted speed unit label ("KM/H" or "MPH").
     */
    fun speedLabel(unit: String): String {
        return if (unit.equals("mph", ignoreCase = true)) "MPH" else "KM/H"
    }

    /**
     * Returns the distance unit label ("km" or "mi").
     */
    fun distanceLabel(unit: String): String {
        return if (unit.equals("mph", ignoreCase = true) || unit.equals("miles", ignoreCase = true) || unit.equals("mi", ignoreCase = true)) "mi" else "km"
    }

    /**
     * Formats distance with appropriate unit (e.g. "12.4 km" or "7.7 mi").
     */
    fun formatTripDistance(distanceKm: Float, unit: String): String {
        val converted = distanceToDisplay(distanceKm, unit)
        val lbl = distanceLabel(unit)
        return String.format("%.1f %s", converted, lbl)
    }

    /**
     * Formats turn guidance distance in meters/feet or km/miles.
     */
    fun formatGuidanceDistance(meters: Float, unit: String): String {
        val isImperial = unit.equals("mph", ignoreCase = true)
        return if (isImperial) {
            val feet = meters * 3.28084f
            if (feet >= 1000f) {
                val miles = (round((feet / 5280f) * 10f) / 10f)
                "$miles mi"
            } else {
                val roundedFeet = (round(feet / 50f) * 50).toInt().coerceAtLeast(50)
                "$roundedFeet ft"
            }
        } else {
            if (meters >= 1000f) {
                val km = round((meters / 1000f) * 10f) / 10f
                "$km km"
            } else {
                val roundedM = (round(meters / 50f) * 50).toInt().coerceAtLeast(50)
                "$roundedM m"
            }
        }
    }
}
