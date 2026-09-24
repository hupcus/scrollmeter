package com.scrollmeter.app.insights

import kotlin.math.roundToInt

/** Illustrative real-world lengths (spec §22). They never change the measured data. */
enum class Reference(val metres: Double) {
    FOOTBALL_FIELD(105.0),
    EIFFEL_TOWER(330.0),
    RUNNING_TRACK_LAP(400.0),
    RUN_5K(5_000.0),
    HALF_MARATHON(21_097.5),
    MARATHON(42_195.0),
}

enum class Qualifier { ALMOST, ABOUT, MORE_THAN }

/** "[qualifier] [count ×] [reference]" — the UI turns it into a sentence with plurals. */
data class DistanceComparison(val reference: Reference, val qualifier: Qualifier, val count: Int)

/**
 * Picks the one comparison that fits the distance best (spec §22): the longest reference the
 * distance reaches at least 80 % of. Up to 95 % it is "almost", up to 110 % "about", up to 150 %
 * "more than" one; beyond that "about N ×", N rounded. Under 80 % of a football field: none.
 * 428 m → about 1 running-track lap; 4,2 km → almost a 5 km run; 42,3 km → about a marathon.
 */
object DistanceComparisonProvider {
    private const val REACH = 0.8

    fun compare(distanceMm: Double): DistanceComparison? {
        if (!distanceMm.isFinite() || distanceMm <= 0) return null
        val metres = distanceMm / 1_000.0
        val reference = Reference.entries.sortedByDescending { it.metres }.firstOrNull { metres / it.metres >= REACH } ?: return null
        val ratio = metres / reference.metres
        return when {
            ratio < 0.95 -> DistanceComparison(reference, Qualifier.ALMOST, 1)
            ratio <= 1.1 -> DistanceComparison(reference, Qualifier.ABOUT, 1)
            ratio < 1.5 -> DistanceComparison(reference, Qualifier.MORE_THAN, 1)
            else -> DistanceComparison(reference, Qualifier.ABOUT, ratio.roundToInt())
        }
    }
}
