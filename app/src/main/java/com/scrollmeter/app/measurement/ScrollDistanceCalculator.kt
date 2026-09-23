package com.scrollmeter.app.measurement

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Physical length of one scroll step. The vector length is used, so a diagonal step is not
 * counted twice (300/400 px at 0.06 mm/px is 30 mm, not 42 mm — spec §59). Direction never
 * cancels: 10 cm down and 10 cm up is 20 cm of scroll distance (spec §7).
 */
object ScrollDistanceCalculator {
    fun distance(dxPx: Int, dyPx: Int, scale: PhysicalScale): ScrollDistance {
        val dxMm = dxPx.toDouble() * scale.mmPerPxX
        val dyMm = dyPx.toDouble() * scale.mmPerPxY
        return ScrollDistance(
            totalMm = hypot(dxMm, dyMm),
            horizontalMm = abs(dxMm),
            verticalMm = abs(dyMm),
        )
    }
}
