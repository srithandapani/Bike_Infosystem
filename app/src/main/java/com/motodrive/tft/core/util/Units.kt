package com.motodrive.tft.core.util

/** Single place for km/h <-> mph and km <-> mi conversion so every screen agrees. */
object Units {
    private const val KM_PER_MILE = 1.609344f

    fun isMph(unit: String) = unit.equals("mph", ignoreCase = true)

    fun speed(kmh: Float, unit: String): Float = if (isMph(unit)) kmh / KM_PER_MILE else kmh

    fun distance(km: Float, unit: String): Float = if (isMph(unit)) km / KM_PER_MILE else km

    fun distanceLabel(unit: String): String = if (isMph(unit)) "mi" else "km"

    fun distanceLabelLong(unit: String): String = if (isMph(unit)) "MILES" else "KILOMETERS"

    fun speedLabel(unit: String): String = if (isMph(unit)) "MPH" else "KM/H"
}
