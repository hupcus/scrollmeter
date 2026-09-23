package com.scrollmeter.app.measurement

import kotlin.math.hypot

/** Real display size in physical pixels (the unit of scroll deltas). */
data class DeviceGeometry(
    val widthPx: Int,
    val heightPx: Int,
) {
    val diagonalPx: Double get() = hypot(widthPx.toDouble(), heightPx.toDouble())

    /** The outlier limit of spec §11. */
    val maxEventDistancePx: Double get() = MeasurementConfig.MAX_EVENT_DISTANCE_FACTOR * diagonalPx
}
